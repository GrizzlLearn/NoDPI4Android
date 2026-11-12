package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class LocalSocksBridgeTest {
    private fun connect(bridge: LocalSocksBridge): Socket {
        val client = Socket("127.0.0.1", bridge.port)
        client.soTimeout = 3000
        client.getOutputStream().write(byteArrayOf(5, 1, 0))
        val input = DataInputStream(client.getInputStream())
        assertEquals(5, input.readUnsignedByte())
        assertEquals(0, input.readUnsignedByte())
        return client
    }

    @Test fun tcpUsesExistingProxyAndSupportsServerFirstProtocols() {
        ServerSocket(0).use { destination ->
            val responder = Thread {
                destination.accept().use { socket ->
                    socket.getOutputStream().write("hello".toByteArray())
                    socket.soTimeout = 3000
                    socket.getOutputStream().write(socket.getInputStream().read())
                }
            }.apply { start() }
            val config = ProxyConfig(port = ServerSocket(0).use { it.localPort }, disorder = false)
            val proxy = ProxyServer(config, AllDomainsBlacklist, ProxyStats(), ProxyLog())
            try {
                proxy.start()
                LocalSocksBridge(config).use { bridge ->
                    bridge.start()
                    connect(bridge).use { client ->
                        val port = destination.localPort
                        client.getOutputStream().write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, (port ushr 8).toByte(), port.toByte()))
                        val input = DataInputStream(client.getInputStream())
                        val reply = ByteArray(10).also { input.readFully(it) }
                        assertEquals(0, reply[1].toInt())
                        val greeting = ByteArray(5).also { input.readFully(it) }
                        assertEquals("hello", String(greeting))
                        client.getOutputStream().write(42)
                        assertEquals(42, input.read())
                    }
                }
                responder.join(3000)
                assertFalse(responder.isAlive)
            } finally { proxy.stop(); destination.close() }
        }
    }

    @Test fun udpAssociationRelaysDatagramsAndPreservesSocksHeader() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { destination ->
            destination.soTimeout = 3000
            val responder = Thread {
                val packet = DatagramPacket(ByteArray(256), 256)
                destination.receive(packet)
                destination.send(packet)
            }.apply { start() }
            LocalSocksBridge(ProxyConfig()).use { bridge ->
                bridge.start()
                connect(bridge).use { control ->
                    control.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
                    val input = DataInputStream(control.getInputStream())
                    val reply = ByteArray(10).also { input.readFully(it) }
                    val relayPort = ((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255)
                    DatagramSocket().use { client ->
                        client.soTimeout = 3000
                        val port = destination.localPort
                        val body = byteArrayOf(0, 0, 0, 1, 127, 0, 0, 1, (port ushr 8).toByte(), port.toByte(), 42)
                        client.send(DatagramPacket(body, body.size, InetAddress.getByName("127.0.0.1"), relayPort))
                        val response = DatagramPacket(ByteArray(256), 256)
                        client.receive(response)
                        assertArrayEquals(body, response.data.copyOf(response.length))
                    }
                }
            }
            responder.join(3000)
            assertFalse(responder.isAlive)
        }
    }

    @Test fun unsupportedAuthenticationIsRejected() {
        LocalSocksBridge(ProxyConfig()).use { bridge ->
            bridge.start()
            Socket("127.0.0.1", bridge.port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(byteArrayOf(5, 1, 2))
                assertEquals(5, client.getInputStream().read())
                assertEquals(255, client.getInputStream().read())
            }
        }
    }
}
