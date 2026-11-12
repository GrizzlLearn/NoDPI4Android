package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class ExternalBridgeTest {
    @Test fun unavailableExternalProxyNeverFallsBackToReachableLocalProxy() {
        ServerSocket(0).use { local ->
            local.soTimeout = 500
            val unavailable = ServerSocket(0).use { it.localPort }
            val config = ProxyConfig(port = local.localPort, upstream = Socks5Config(true, "127.0.0.1", unavailable))
            LocalSocksBridge(config, external = true).use { bridge ->
                bridge.start()
                Socket("127.0.0.1", bridge.port).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().write(byteArrayOf(5, 1, 0))
                    val input = DataInputStream(client.getInputStream())
                    assertEquals(5, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte())
                    client.getOutputStream().write(byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, -69))
                    assertEquals(-1, input.read())
                    assertTrue(runCatching { local.accept().close() }.exceptionOrNull() is java.net.SocketTimeoutException)
                }
            }
        }
    }
    @Test fun externalUdpIsRejectedAndPrivateBridgeRequiresItsToken() {
        LocalSocksBridge(ProxyConfig(), external = true).use { bridge ->
            bridge.start()
            Socket("127.0.0.1", bridge.port).use { client ->
                client.soTimeout = 3000
                val input = DataInputStream(client.getInputStream())
                client.getOutputStream().write(byteArrayOf(5, 1, 0)); input.readFully(ByteArray(2))
                client.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
                assertEquals(5, input.readUnsignedByte())
                assertEquals(7, input.readUnsignedByte())
            }
        }
        LocalSocksBridge(ProxyConfig(), bridgePassword = "private-token").use { bridge ->
            bridge.start()
            Socket("127.0.0.1", bridge.port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertEquals(5, client.getInputStream().read())
                assertEquals(255, client.getInputStream().read())
            }
        }
    }
    @Test fun internalDnsUsesWireCallbackInsteadOfPhysicalNetwork() {
        var called = false
        LocalSocksBridge(ProxyConfig(), dnsQuery = { query -> called = true; query.copyOf().apply { this[2] = -127 } }).use { bridge ->
            bridge.start()
            Socket("127.0.0.1", bridge.port).use { client ->
                client.soTimeout = 3000
                val output = java.io.DataOutputStream(client.getOutputStream())
                val input = DataInputStream(client.getInputStream())
                output.write(byteArrayOf(5, 1, 0)); input.readFully(ByteArray(2))
                output.write(byteArrayOf(5, 1, 0, 1, -58, 18, 0, 2, 0, 53))
                val reply = ByteArray(10).also { input.readFully(it) }
                assertEquals(0, reply[1].toInt())
                output.writeShort(12); output.write(ByteArray(12))
                assertEquals(12, input.readUnsignedShort())
                input.readFully(ByteArray(12))
                assertTrue(called)
            }
        }
    }
}
