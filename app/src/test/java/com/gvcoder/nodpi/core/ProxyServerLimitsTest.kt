package com.gvcoder.nodpi.core

import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ProxyServerLimitsTest {
    private fun boundPort(proxy: ProxyServer): Int {
        val field = ProxyServer::class.java.getDeclaredField("serverSocket").apply { isAccessible = true }
        return (field.get(proxy) as ServerSocket).localPort
    }

    @Test fun clientBeyondConfiguredLimitIsRejectedAndStopReleasesAllClients() {
        val log = ProxyLog()
        val proxy = ProxyServer(ProxyConfig(port = 0), AllDomainsBlacklist, ProxyStats(), log)
        val clients = mutableListOf<Socket>()
        try {
            proxy.start()
            repeat(ConnectionLimits.TRAFFIC_SESSIONS + 1) { clients += Socket("127.0.0.1", boundPort(proxy)).apply { soTimeout = 4000 } }
            assertEquals(-1, clients.last().getInputStream().read())
            runBlocking { withTimeout(3000) { log.entries.first { entries -> entries.any { it.method == "LIMIT" } } } }
            proxy.stop()
            clients.dropLast(1).forEach { assertEquals(-1, it.getInputStream().read()) }
        } finally {
            proxy.stop()
            clients.forEach { it.close() }
        }
    }

    @Test fun connectResponseDoesNotCountAsRemoteBytes() {
        val log = ProxyLog()
        val proxy = ProxyServer(ProxyConfig(port = 0), AllDomainsBlacklist, ProxyStats(), log)
        ServerSocket(0).use { destination ->
            val sender = Thread { destination.accept().use { it.getOutputStream().write("bye".toByteArray()) } }.apply { start() }
            try {
                proxy.start()
                Socket("127.0.0.1", boundPort(proxy)).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().write("CONNECT 127.0.0.1:${destination.localPort} HTTP/1.1\r\n\r\n".toByteArray())
                    assertTrue(client.getInputStream().bufferedReader().readText().endsWith("bye"))
                }
                val entry = runBlocking { withTimeout(3000) { log.entries.first { it.isNotEmpty() }.last() } }
                assertEquals(3L, entry.bytesIn)
                assertEquals("REMOTE_DATA_RECEIVED", entry.message)
                sender.join(3000)
                assertFalse(sender.isAlive)
            } finally { proxy.stop() }
        }
    }
}
