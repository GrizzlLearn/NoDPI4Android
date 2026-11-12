package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BridgeIdleTest {
    private fun connect(bridge: LocalSocksBridge): Socket {
        val client = Socket("127.0.0.1", bridge.port).apply { soTimeout = 3000 }
        val input = DataInputStream(client.getInputStream())
        client.getOutputStream().write(byteArrayOf(5, 1, 0)); input.readFully(ByteArray(2))
        client.getOutputStream().write(byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, -69))
        val reply = ByteArray(10).also { input.readFully(it) }
        assertEquals(0, reply[1].toInt())
        return client
    }
    private fun accept(proxy: ServerSocket): Socket {
        val socket = proxy.accept().apply { soTimeout = 3000 }
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) { val byte = socket.getInputStream().read(); check(byte >= 0); head.append(byte.toChar()) }
        socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        return socket
    }
    @Test fun idleTcpReleasesCapacity() {
        ServerSocket(0).use { proxy ->
            val served = CompletableFuture.runAsync { accept(proxy).use { it.getInputStream().read() } }
            val permits = Semaphore(1)
            LocalSocksBridge(ProxyConfig(port = proxy.localPort), sessionSlots = permits, tcpIdleTimeoutMs = 100, maintenanceIntervalMs = 20).use { bridge ->
                bridge.start()
                connect(bridge).use { assertEquals(-1, it.getInputStream().read()) }
                assertTrue(permits.tryAcquire(3, TimeUnit.SECONDS)); permits.release()
            }
            served.get(3, TimeUnit.SECONDS)
        }
    }
    @Test fun incomingTrafficKeepsSessionAliveWhenUploadIsSilent() {
        ServerSocket(0).use { proxy ->
            val served = CompletableFuture.runAsync {
                accept(proxy).use { socket -> repeat(10) { socket.getOutputStream().write(42); Thread.sleep(40) } }
            }
            LocalSocksBridge(ProxyConfig(port = proxy.localPort), tcpIdleTimeoutMs = 200, maintenanceIntervalMs = 20).use { bridge ->
                bridge.start()
                connect(bridge).use { client -> repeat(10) { assertEquals(42, client.getInputStream().read()) } }
            }
            served.get(3, TimeUnit.SECONDS)
        }
    }
    @Test fun capacityPressureReclaimsIdleTcpWithoutRaisingLimit() {
        ServerSocket(0).use { proxy ->
            val served = CompletableFuture.runAsync { repeat(2) { accept(proxy).use { it.getInputStream().read() } } }
            LocalSocksBridge(ProxyConfig(port = proxy.localPort), sessionSlots = Semaphore(1), pressureIdleTimeoutMs = 0).use { bridge ->
                bridge.start()
                connect(bridge).use { first ->
                    connect(bridge).use { assertEquals(-1, first.getInputStream().read()) }
                }
            }
            served.get(3, TimeUnit.SECONDS)
        }
    }
}
