package com.gvcoder.nodpi.core

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ProxyServerNetworkResetTest {
    @Test fun resettingConnectionsKeepsListenerAndAcceptsNewFlows() {
        ServerSocket(0).use { destination ->
            destination.soTimeout = 3000
            val served = CompletableFuture.runAsync {
                repeat(2) {
                    destination.accept().use { socket ->
                        socket.soTimeout = 3000
                        socket.getOutputStream().write('x'.code)
                        socket.getInputStream().read()
                    }
                }
            }
            val config = ProxyConfig(port = ServerSocket(0).use { it.localPort }, disorder = false)
            val proxy = ProxyServer(config, AllDomainsBlacklist, ProxyStats(), ProxyLog())
            fun connect(): Socket {
                val socket = Socket("127.0.0.1", config.port).apply { soTimeout = 3000 }
                socket.getOutputStream().write("CONNECT 127.0.0.1:${destination.localPort} HTTP/1.1\r\n\r\n".toByteArray())
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val byte = socket.getInputStream().read()
                    check(byte >= 0)
                    head.append(byte.toChar())
                }
                assertTrue(head.startsWith("HTTP/1.1 200"))
                assertEquals('x'.code, socket.getInputStream().read())
                return socket
            }
            try {
                proxy.start()
                connect().use { old ->
                    proxy.resetConnections()
                    assertEquals(-1, old.getInputStream().read())
                }
                connect().close()
                served.get(3, TimeUnit.SECONDS)
            } finally { proxy.stop() }
        }
    }
}
