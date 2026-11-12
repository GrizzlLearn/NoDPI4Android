package com.gvcoder.nodpi.core

import java.io.Closeable
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.net.Proxy
import com.gvcoder.nodpi.service.ProxyController
import java.util.concurrent.Semaphore
import android.util.Base64

/** Loopback SOCKS5 adapter for the TUN engine. TCP goes through the existing HTTP proxy. */
class LocalSocksBridge(
    private val config: ProxyConfig,
    private val dnsQuery: ((ByteArray) -> ByteArray)? = null,
    private val external: Boolean = false,
    private val bridgePassword: String? = null,
    private val sessionSlots: Semaphore = Semaphore(ConnectionLimits.TRAFFIC_SESSIONS),
    private val udpSlots: Semaphore = Semaphore(ConnectionLimits.UDP_ASSOCIATIONS),
    private val udpIdleTimeoutMs: Int = 30_000,
    private val bridgeName: String = if (external) "EXTERNAL" else "LOCAL",
    private val tcpIdleTimeoutMs: Long = 60_000,
    private val pressureIdleTimeoutMs: Long = 5_000,
    private val maintenanceIntervalMs: Long = 1000,
) : Closeable {
    private val executor = ThreadPoolExecutor(0, ConnectionLimits.BRIDGE_WORKERS, 30, TimeUnit.SECONDS, SynchronousQueue(), { task ->
        Thread(task, if (external) "nodpi-socks-external" else "nodpi-socks-local").apply { isDaemon = true }
    })
    private class Session(val client: Socket) {
        @Volatile var phase = "HANDSHAKE"
        @Volatile var lastActivity = System.nanoTime()
    }
    private val sessions = ConcurrentHashMap<Socket, Session>()
    private val maintenance = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "nodpi-$bridgeName-idle").apply { isDaemon = true }
    }
    private val resources = ConcurrentHashMap.newKeySet<Closeable>()
    private val slots = sessionSlots
    private val rejected = java.util.concurrent.atomic.AtomicLong()
    private val lastLimitLog = java.util.concurrent.atomic.AtomicLong()
    private fun reportLimit(kind: String) {
        rejected.incrementAndGet()
        val now = System.nanoTime()
        val previous = lastLimitLog.get()
        if ((previous == 0L || now - previous >= 5_000_000_000L) && lastLimitLog.compareAndSet(previous, now)) {
            val active = sessions.values.asSequence().toList()
            val oldestMs = active.maxOfOrNull { (now - it.lastActivity) / 1_000_000 } ?: 0
            ProxyController.log.error("VPN", "LIMIT", "$kind; pool=$bridgeName; active=${active.size}; free=${slots.availablePermits()}; phases=${active.groupingBy { it.phase }.eachCount()}; maxIdleMs=$oldestMs; workers=${executor.poolSize}; rejected=${rejected.getAndSet(0)}")
        }
    }
    private val resolver = DomainResolver(config.dohEnabled, config.vpnEnabled && config.ipv4Only)
    private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = listener.localPort
    @Volatile private var closed = false

    fun start() {
        maintenance.scheduleWithFixedDelay({
            val now = System.nanoTime()
            sessions.values.forEach { session ->
                synchronized(session) {
                    val timeout = when (session.phase) { "TCP" -> tcpIdleTimeoutMs; "DNS_UDP" -> 1000L; else -> Long.MAX_VALUE }
                    if ((now - session.lastActivity) / 1_000_000 >= timeout) {
                        session.phase = "CLOSING"
                        runCatching { session.client.close() }
                    }
                }
            }
        }, maintenanceIntervalMs, maintenanceIntervalMs, TimeUnit.MILLISECONDS)
        executor.execute {
            while (!closed) {
                val client = try { listener.accept() } catch (_: Exception) { break }
                var admitted = slots.tryAcquire()
                if (!admitted && bridgeName == "DNS") {
                    // Let the previously delivered UDP reply drain before closing its control socket.
                    admitted = slots.tryAcquire(150, TimeUnit.MILLISECONDS)
                }
                if (!admitted) {
                    val now = System.nanoTime()
                    val desiredPhase = if (bridgeName == "DNS") "DNS_UDP" else "TCP"
                    val minimumIdleMs = if (bridgeName == "DNS") 100L else pressureIdleTimeoutMs
                    val idle = sessions.values.filter { it.phase == desiredPhase && (now - it.lastActivity) / 1_000_000 >= minimumIdleMs }.minByOrNull { it.lastActivity }
                    if (idle != null) {
                        val reclaimed = synchronized(idle) {
                            // Re-check while synchronized with DNS_QUERY; an in-flight query is never evicted.
                            if (idle.phase != desiredPhase || (System.nanoTime() - idle.lastActivity) / 1_000_000 < minimumIdleMs) false
                            else {
                                idle.phase = "CLOSING"
                                runCatching { idle.client.close() }
                                true
                            }
                        }
                        if (reclaimed) admitted = slots.tryAcquire(250, TimeUnit.MILLISECONDS)
                    }
                }
                if (!admitted) {
                    reportLimit("VPN bridge capacity reached")
                    client.close(); continue
                }
                sessions[client] = Session(client)
                resources += client
                try {
                    executor.execute {
                        try { handle(client) } catch (_: Exception) { } finally {
                            resources.remove(client)
                            sessions.remove(client)
                            runCatching { client.close() }
                            slots.release()
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    resources.remove(client)
                    sessions.remove(client)
                    client.close()
                    slots.release()
                }
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = DataInputStream(client.getInputStream())
        val out = client.getOutputStream()
        require(input.readUnsignedByte() == 5)
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val method = if (bridgePassword == null) 0 else 2
        if (method.toByte() !in methods) { out.write(byteArrayOf(5, -1)); return }
        out.write(byteArrayOf(5, method.toByte()))
        if (method == 2) {
            if (input.readUnsignedByte() != 1) return
            val username = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            val password = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            val valid = username.contentEquals("nodpi".toByteArray()) &&
                java.security.MessageDigest.isEqual(password, checkNotNull(bridgePassword).toByteArray())
            out.write(byteArrayOf(1, if (valid) 0 else 1))
            if (!valid) return
        }
        require(input.readUnsignedByte() == 5)
        val command = input.readUnsignedByte()
        require(input.readUnsignedByte() == 0)
        val target = SocksAddress.read(input)
        if (config.vpnEnabled && config.ipv4Only && target.host.contains(':')) {
            out.write(reply(8, 0))
            return
        }
        sessions[client]?.phase = if (command == 3) "UDP" else "CONNECTING"
        val internalDns = target.host == "198.18.0.2" && target.port == 53
        if (!internalDns) {
            VpnDestinationGuard.rejection(target.host)?.let { reason ->
                ProxyController.log.error(target.host, "VPN_DESTINATION", reason)
                out.write(reply(4, 0))
                return
            }
        }
        when (command) {
            1 -> if (internalDns && dnsQuery != null) dnsTcp(client, input) else tcp(client, input, target)
            3 -> if (external) {
                ProxyController.log.error("SOCKS5", "UDP", "EXTERNAL_UDP_UNSUPPORTED: blocked; no direct fallback")
                out.write(reply(7, 0))
            } else if (!udpSlots.tryAcquire()) {
                reportLimit("UDP association capacity reached; TCP and DNS capacity reserved")
                out.write(reply(1, 0))
            } else {
                try { udp(client, input) } finally { udpSlots.release() }
            }
            else -> out.write(reply(7, 0))
        }
    }

    private fun tcp(client: Socket, input: DataInputStream, target: SocksAddress) {
        require(target.port > 0)
        val proxy = Socket(Proxy.NO_PROXY)
        resources += proxy
        try {
            if (external) {
                config.upstream.validate()
                proxy.connect(InetSocketAddress(resolver.resolve(config.upstream.host.removePrefix("[").removeSuffix("]")), config.upstream.port), 10_000)
                proxy.soTimeout = 10_000
                Socks5Client.connect(proxy, config.upstream, target.host, target.port)
                ProxyController.log.info(target.host, "SOCKS5", "UPSTREAM_CONNECTED; destinationPort=${target.port}")
            } else {
                val host = if (config.host == "0.0.0.0" || config.host == "::") "127.0.0.1" else config.host
                proxy.connect(InetSocketAddress(host, config.port), 15_000)
                proxy.soTimeout = 15_000
                val authority = if (target.host.contains(':')) "[${target.host}]:${target.port}" else "${target.host}:${target.port}"
                val auth = if (config.authEnabled) {
                    val token = Base64.encodeToString("${config.username}:${config.password}".toByteArray(), Base64.NO_WRAP)
                    "Proxy-Authorization: Basic $token\r\n"
                } else ""
                proxy.getOutputStream().write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n${auth}\r\n".toByteArray())
                val response = StringBuilder()
                val remoteIn = proxy.getInputStream()
                while (!response.endsWith("\r\n\r\n") && response.length < 8192) {
                    val byte = remoteIn.read()
                    check(byte >= 0)
                    response.append(byte.toChar())
                }
                if (!response.startsWith("HTTP/1.1 200 ")) {
                    client.getOutputStream().write(reply(4, 0)); return
                }
            }
            val remoteIn = proxy.getInputStream()
            val session = checkNotNull(sessions[client])
            session.phase = "TCP"
            session.lastActivity = System.nanoTime()
            client.getOutputStream().write(reply(0, 0))
            client.soTimeout = 0
            proxy.soTimeout = 0
            executor.execute {
                try { copyActive(remoteIn, client.getOutputStream(), session) } catch (_: Exception) { } finally { client.close(); proxy.close() }
            }
            copyActive(input, proxy.getOutputStream(), session)
        } catch (error: Exception) {
            if (external) ProxyController.log.error(target.host, "SOCKS5", "UPSTREAM_FAILED: ${error.javaClass.simpleName}; ${error.message}; no direct fallback")
            throw error
        } finally { resources.remove(proxy); proxy.close() }
    }

    private fun copyActive(input: java.io.InputStream, output: java.io.OutputStream, session: Session) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            session.lastActivity = System.nanoTime()
            output.write(buffer, 0, count)
            session.lastActivity = System.nanoTime()
        }
    }

    private fun dnsTcp(client: Socket, input: DataInputStream) {
        client.getOutputStream().write(reply(0, 0))
        val output = java.io.DataOutputStream(client.getOutputStream())
        while (!closed) {
            val size = try { input.readUnsignedShort() } catch (_: java.io.EOFException) { break }
            require(size in 12..65535)
            val query = ByteArray(size).also { input.readFully(it) }
            val response = checkNotNull(dnsQuery).invoke(query)
            output.writeShort(response.size)
            output.write(response)
        }
    }

    private fun udp(client: Socket, input: DataInputStream) {
        val relay = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val upstream = DatagramSocket()
        relay.soTimeout = udpIdleTimeoutMs
        resources += relay
        resources += upstream
        val headers = ConcurrentHashMap<InetSocketAddress, ByteArray>()
        val peer = Peer()
        try {
            client.getOutputStream().write(reply(0, relay.localPort))
            client.soTimeout = 0
            executor.execute {
                val buffer = ByteArray(65535)
                try {
                    while (!relay.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        relay.receive(packet)
                        if (!packet.address.isLoopbackAddress) continue
                        val source = packet.socketAddress as InetSocketAddress
                        if (peer.address != null && peer.address != source) continue
                        peer.address = source
                        val data = DataInputStream(packet.data.inputStream(0, packet.length))
                        if (data.readUnsignedShort() != 0 || data.readUnsignedByte() != 0) continue
                        val target = SocksAddress.read(data)
                        val headerSize = packet.length - data.available()
                        if (target.host == "198.18.0.2" && target.port == 53 && dnsQuery != null) {
                            val session = sessions[client] ?: break
                            val active = synchronized(session) {
                                if (session.phase == "CLOSING" || client.isClosed) false
                                else { session.phase = "DNS_QUERY"; true }
                            }
                            if (!active) break
                            val query = ByteArray(data.available()).also { data.readFully(it) }
                            val response = try { dnsQuery.invoke(query) } catch (error: Exception) {
                                ProxyController.log.error("DNS", "QUERY", error.message ?: "DNS query failed")
                                if (query.size < 12) continue
                                query.copyOf().apply { this[2] = (this[2].toInt() or 0x80).toByte(); this[3] = ((this[3].toInt() and 0xf0) or 2).toByte() }
                            }
                            val body = packet.data.copyOfRange(0, headerSize) + response
                            relay.send(DatagramPacket(body, body.size, source))
                            synchronized(session) { session.lastActivity = System.nanoTime(); session.phase = "DNS_UDP" }
                            continue
                        }
                        if (config.vpnEnabled && config.ipv4Only && target.host.contains(':')) continue
                        if (VpnDestinationGuard.rejection(target.host) != null) continue
                        // QUIC cannot use TLS-record fragmentation; make HTTPS clients fall back to TCP.
                        if (target.port == 443) {
                            client.close()
                            break
                        }
                        if (target.port == 0) continue
                        val address = InetSocketAddress(resolver.resolve(target.host), target.port)
                        if (headers.size >= 128 && !headers.containsKey(address)) continue
                        headers[address] = packet.data.copyOfRange(0, headerSize)
                        val body = ByteArray(data.available()).also { data.readFully(it) }
                        upstream.send(DatagramPacket(body, body.size, address))
                    }
                } catch (_: Exception) { } finally { runCatching { client.close() } }
            }
            executor.execute {
                val buffer = ByteArray(65535)
                try {
                    while (!upstream.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        upstream.receive(packet)
                        val header = headers[packet.socketAddress] ?: continue
                        val destination = peer.address ?: continue
                        val body = header + packet.data.copyOfRange(0, packet.length)
                        relay.send(DatagramPacket(body, body.size, destination))
                    }
                } catch (_: Exception) { }
            }
            while (input.read() >= 0) { }
        } finally {
            relay.close(); upstream.close()
            resources.remove(relay); resources.remove(upstream)
        }
    }

    private class Peer { @Volatile var address: InetSocketAddress? = null }

    /** Active flows reconnect on the new network without recreating TUN or route ownership. */
    fun resetConnections() {
        resources.asSequence().toList().forEach { runCatching { it.close() } }
    }

    override fun close() {
        closed = true
        listener.close()
        resources.forEach { runCatching { it.close() } }
        resources.clear()
        maintenance.shutdownNow()
        executor.shutdownNow()
    }

    private fun reply(code: Int, port: Int) = byteArrayOf(5, code.toByte(), 0, 1, 127, 0, 0, 1, (port ushr 8).toByte(), port.toByte())
}

internal data class SocksAddress(val host: String, val port: Int) {
    companion object {
        fun read(input: DataInputStream): SocksAddress {
            val host = when (val type = input.readUnsignedByte()) {
                1, 4 -> InetAddress.getByAddress(ByteArray(if (type == 1) 4 else 16).also { input.readFully(it) }).hostAddress!!
                3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                else -> error("Unsupported SOCKS address")
            }
            require(host.isNotBlank() && host.none { it.isWhitespace() || it == '/' })
            return SocksAddress(host, input.readUnsignedShort())
        }
    }
}
