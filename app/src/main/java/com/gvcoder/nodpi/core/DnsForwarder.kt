package com.gvcoder.nodpi.core

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Forwards real DNS answers; no synthetic addresses survive in application caches. */
class DnsForwarder(private val connectivity: ConnectivityManager, private val doh: Boolean, private val ipv4Only: Boolean = false) {
    fun query(request: ByteArray): ByteArray {
        require(request.size in 12..65535) { "Invalid DNS query length" }
        if (ipv4Only) ipv4OnlyDnsReply(request)?.let { return it }
        return if (doh) https(request) else system(request)
    }

    private fun https(request: ByteArray): ByteArray {
        val connection = URL("https://1.1.1.1/dns-query").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/dns-message")
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            check(connection.responseCode == 200) { "DNS over HTTPS HTTP ${connection.responseCode}" }
            val answer = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 65535) { "DNS response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            validateDnsReply(request, answer)
            return answer
        } finally { connection.disconnect() }
    }

    private fun system(request: ByteArray): ByteArray {
        var failure: Exception? = null
        for (network in connectivity.allNetworks) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val servers = connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
            for (server in servers.take(2)) {
                try { return udp(network, server, request) } catch (error: Exception) { failure = error }
            }
        }
        throw java.io.IOException("No reachable underlying network DNS server", failure)
    }

    private fun udp(network: Network, server: InetAddress, request: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            network.bindSocket(socket)
            socket.soTimeout = 3000
            socket.connect(server, 53)
            socket.send(DatagramPacket(request, request.size))
            val answer = DatagramPacket(ByteArray(65535), 65535)
            socket.receive(answer)
            val bytes = answer.data.copyOfRange(answer.offset, answer.offset + answer.length)
            validateDnsReply(request, bytes)
            if ((bytes[2].toInt() and 0x02) != 0) return tcp(network, server, request)
            return bytes
        }
    }
    private fun tcp(network: Network, server: InetAddress, request: ByteArray): ByteArray {
        network.socketFactory.createSocket().use { socket ->
            socket.connect(java.net.InetSocketAddress(server, 53), 3000)
            socket.soTimeout = 3000
            val output = java.io.DataOutputStream(socket.getOutputStream())
            output.writeShort(request.size)
            output.write(request)
            output.flush()
            val input = java.io.DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            require(length >= 12) { "Truncated DNS TCP response" }
            val answer = ByteArray(length).also { input.readFully(it) }
            validateDnsReply(request, answer)
            return answer
        }
    }

}

internal fun validateDnsReply(request: ByteArray, answer: ByteArray) {
    require(request.size >= 12 && answer.size >= 12) { "Truncated DNS message" }
    require(answer[0] == request[0] && answer[1] == request[1]) { "DNS response ID mismatch" }
    require((answer[2].toInt() and 0x80) != 0) { "DNS message is not a response" }
}
