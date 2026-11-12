package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class VpnCapacityTest {
    private fun open(bridge: LocalSocksBridge, command: Int, dns: Boolean = false): Pair<Socket, ByteArray> {
        val client = Socket("127.0.0.1", bridge.port).apply { soTimeout = 3000 }
        val input = DataInputStream(client.getInputStream())
        client.getOutputStream().write(byteArrayOf(5, 1, 0))
        input.readFully(ByteArray(2))
        val address = if (dns) byteArrayOf(-58, 18, 0, 2, 0, 53) else byteArrayOf(0, 0, 0, 0, 0, 0)
        client.getOutputStream().write(byteArrayOf(5, command.toByte(), 0, 1) + address)
        val reply = ByteArray(10).also { input.readFully(it) }
        return client to reply
    }
    @Test fun blockedQuicReleasesSessionInsteadOfHoldingCapacity() {
        val slots = Semaphore(1)
        LocalSocksBridge(ProxyConfig(), sessionSlots = slots).use { bridge ->
            bridge.start()
            val (client, reply) = open(bridge, 3)
            client.use {
                val port = ((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255)
                DatagramSocket().use { sender ->
                    val body = byteArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 1, -69, 0)
                    sender.send(DatagramPacket(body, body.size, InetAddress.getByName("127.0.0.1"), port))
                }
                assertEquals(-1, client.getInputStream().read())
                assertTrue(slots.tryAcquire(3, TimeUnit.SECONDS))
                slots.release()
            }
        }
    }
    @Test fun idleUdpAssociationReleasesAllPermits() {
        val slots = Semaphore(1)
        val udp = Semaphore(1)
        LocalSocksBridge(ProxyConfig(), sessionSlots = slots, udpSlots = udp, udpIdleTimeoutMs = 100).use { bridge ->
            bridge.start()
            val (client, _) = open(bridge, 3)
            client.use { assertEquals(-1, it.getInputStream().read()) }
            assertTrue(slots.tryAcquire(3, TimeUnit.SECONDS))
            assertEquals(1, udp.availablePermits())
            slots.release()
        }
    }
    @Test fun udpQuotaCannotConsumeTcpDnsCapacity() {
        LocalSocksBridge(ProxyConfig(), dnsQuery = { it.copyOf().apply { this[2] = -127 } }, sessionSlots = Semaphore(3), udpSlots = Semaphore(1)).use { bridge ->
            bridge.start()
            val (udp, _) = open(bridge, 3)
            udp.use {
                val (rejected, reply) = open(bridge, 3)
                rejected.use { assertEquals(1, reply[1].toInt()) }
                val (dns, accepted) = open(bridge, 1, dns = true)
                dns.use {
                    assertEquals(0, accepted[1].toInt())
                    val output = java.io.DataOutputStream(dns.getOutputStream())
                    output.writeShort(12); output.write(ByteArray(12))
                    assertEquals(12, DataInputStream(dns.getInputStream()).readUnsignedShort())
                }
            }
        }
    }
}
