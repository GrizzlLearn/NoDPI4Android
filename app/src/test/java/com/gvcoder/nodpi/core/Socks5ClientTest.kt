package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class Socks5ClientTest {
    @Test fun authenticatesAndRequestsDestinationAtUpstreamWithoutLocalDns() {
        ServerSocket(0).use { server ->
            val received = CompletableFuture.supplyAsync {
                server.accept().use { client ->
                    client.soTimeout = 3000
                    val input = DataInputStream(client.getInputStream())
                    val output = client.getOutputStream()
                    val hello = ByteArray(3).also { input.readFully(it) }
                    assertArrayEquals(byteArrayOf(5, 1, 2), hello)
                    output.write(byteArrayOf(5, 2))
                    assertEquals(1, input.readUnsignedByte())
                    val username = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                    val password = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                    assertEquals("user", username); assertEquals("secret", password)
                    output.write(byteArrayOf(1, 0))
                    assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte())
                    assertEquals(3, input.readUnsignedByte())
                    val host = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                    val port = input.readUnsignedShort()
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 1))
                    "$host:$port"
                }
            }
            Socket("127.0.0.1", server.localPort).use { client ->
                client.soTimeout = 3000
                Socks5Client.connect(client, Socks5Config(true, "127.0.0.1", server.localPort, "user", "secret"), "does-not-resolve.invalid", 443)
            }
            assertEquals("does-not-resolve.invalid:443", received.get(3, TimeUnit.SECONDS))
        }
    }
    @Test fun authenticationFailureIsNotDowngradedToNoAuth() {
        ServerSocket(0).use { server ->
            val handled = CompletableFuture.runAsync {
                server.accept().use { client ->
                    val input = DataInputStream(client.getInputStream())
                    input.readFully(ByteArray(3))
                    client.getOutputStream().write(byteArrayOf(5, 2))
                    input.readUnsignedByte()
                    input.readFully(ByteArray(input.readUnsignedByte()))
                    input.readFully(ByteArray(input.readUnsignedByte()))
                    client.getOutputStream().write(byteArrayOf(1, 1))
                }
            }
            Socket("127.0.0.1", server.localPort).use { client ->
                client.soTimeout = 3000
                val failure = runCatching { Socks5Client.connect(client, Socks5Config(true, "127.0.0.1", server.localPort, "user", "wrong"), "example.com", 443) }.exceptionOrNull()
                assertTrue(failure is java.io.IOException)
                assertTrue(failure!!.message!!.contains("credentials rejected"))
            }
            handled.get(3, TimeUnit.SECONDS)
        }
    }
    @Test fun credentialsAreRedactedAndUtf8LimitsValidated() {
        val config = Socks5Config(true, "example.com", 1080, "user", "secret")
        assertFalse(config.toString().contains("secret"))
        assertTrue(runCatching { config.copy(username = "я".repeat(128)).validate() }.isFailure)
        assertTrue(runCatching { config.copy(host = "https://example.com").validate() }.isFailure)
    }
}
