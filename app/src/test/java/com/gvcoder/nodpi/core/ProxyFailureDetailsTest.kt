package com.gvcoder.nodpi.core

import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ProxyFailureDetailsTest {
    @Test fun actualTcpRefusalContainsAddressTimingsAndNetworkSnapshot() {
        val targetPort = ServerSocket(0).use { it.localPort }
        val log = ProxyLog()
        val proxy = ProxyServer(ProxyConfig(port = 0), AllDomainsBlacklist, ProxyStats(), log) { "network=TEST; validated=true; ipv4Default=true" }
        try {
            proxy.start()
            val field = ProxyServer::class.java.getDeclaredField("serverSocket").apply { isAccessible = true }
            val port = (field.get(proxy) as ServerSocket).localPort
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write("CONNECT 127.0.0.1:$targetPort HTTP/1.1\r\n\r\n".toByteArray())
                assertTrue(client.getInputStream().bufferedReader().readText().startsWith("HTTP/1.1 500"))
            }
            val entry = runBlocking { withTimeout(3000) { log.entries.first { it.any { value -> value.kind == LogKind.ERROR } }.first { it.kind == LogKind.ERROR } } }
            val message = entry.message!!
            assertTrue(message.startsWith("TCP_CONNECT:"))
            assertTrue(message.contains("failure=CONNECTION_REFUSED"))
            assertTrue(message.contains("ip=127.0.0.1; port=$targetPort; family=IPv4"))
            assertTrue(message.contains("dnsSource=CLIENT_IP"))
            assertTrue(Regex("connectMs=[0-9]+").containsMatchIn(message))
            assertTrue(message.contains("network=TEST; validated=true"))
        } finally { proxy.stop() }
    }
}
