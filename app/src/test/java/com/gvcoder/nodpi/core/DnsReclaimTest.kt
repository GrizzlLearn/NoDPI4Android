package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DnsReclaimTest {
    private fun associate(bridge: LocalSocksBridge): Pair<Socket, Int> {
        val client = Socket("127.0.0.1", bridge.port).apply { soTimeout = 3000 }
        val input = DataInputStream(client.getInputStream())
        client.getOutputStream().write(byteArrayOf(5, 1, 0)); input.readFully(ByteArray(2))
        client.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
        val reply = ByteArray(10).also { input.readFully(it) }
        return client to (((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255))
    }
    private fun send(socket: DatagramSocket, port: Int) {
        val bytes = byteArrayOf(0, 0, 0, 1, -58, 18, 0, 2, 0, 53) + ByteArray(12)
        socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), port))
    }
    @Test fun completedDnsAssociationIsReclaimedForNewRequestBeforePeriodicCleanup() {
        LocalSocksBridge(ProxyConfig(), dnsQuery = { it.copyOf().apply { this[2] = -127 } }, sessionSlots = Semaphore(1), udpSlots = Semaphore(1), bridgeName = "DNS", maintenanceIntervalMs = 60_000).use { bridge ->
            bridge.start()
            val (first, port) = associate(bridge)
            first.use {
                DatagramSocket().use { sender ->
                    sender.soTimeout = 3000
                    send(sender, port)
                    val answer = DatagramPacket(ByteArray(256), 256)
                    sender.receive(answer)
                    assertEquals(22, answer.length)
                    val (second, nextPort) = associate(bridge)
                    second.use {
                        assertTrue(nextPort > 0)
                        assertEquals(-1, first.getInputStream().read())
                        send(sender, nextPort)
                        sender.receive(answer)
                        assertEquals(22, answer.length)
                    }
                }
            }
        }
    }
    @Test fun inFlightDnsQueryIsNotEvictedUnderPressure() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        LocalSocksBridge(ProxyConfig(), dnsQuery = { request ->
            entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); request.copyOf().apply { this[2] = -127 }
        }, sessionSlots = Semaphore(1), udpSlots = Semaphore(1), bridgeName = "DNS", maintenanceIntervalMs = 20).use { bridge ->
            bridge.start()
            val (first, port) = associate(bridge)
            first.use {
                DatagramSocket().use { sender ->
                    sender.soTimeout = 3000
                    send(sender, port)
                    assertTrue(entered.await(3, TimeUnit.SECONDS))
                    Socket("127.0.0.1", bridge.port).use { contender ->
                        contender.soTimeout = 3000
                        assertEquals(-1, contender.getInputStream().read())
                    }
                    release.countDown()
                    val answer = DatagramPacket(ByteArray(256), 256)
                    sender.receive(answer)
                    assertEquals(22, answer.length)
                    assertFalse(first.isClosed)
                }
            }
        }
    }
}
