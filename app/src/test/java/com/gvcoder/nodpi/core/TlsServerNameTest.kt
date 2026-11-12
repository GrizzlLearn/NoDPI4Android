package com.gvcoder.nodpi.core

import org.junit.Assert.*
import org.junit.Test

class TlsServerNameTest {
    private fun short(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
    private fun hello(name: String): ByteArray {
        val bytes = name.toByteArray()
        val list = byteArrayOf(0) + short(bytes.size) + bytes
        val extension = short(0) + short(list.size + 2) + short(list.size) + list
        val data = byteArrayOf(3, 3) + ByteArray(32) + byteArrayOf(0) + short(2) + byteArrayOf(0x13, 1) + byteArrayOf(1, 0) + short(extension.size) + extension
        return byteArrayOf(1, (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()) + data
    }
    @Test fun readsRealClientHelloStructure() { assertEquals("i.instagram.com", TlsServerName.read(hello("I.Instagram.com"))) }
    @Test fun rejectsMalformedAndLogInjection() {
        for (name in listOf("bad\nname.com", "-bad.com", "bad..com", "é.com", "x".repeat(64)+".com")) assertNull(TlsServerName.read(hello(name)))
        val valid = hello("example.com")
        for (size in 0 until valid.size) assertNull(TlsServerName.read(valid.copyOf(size)))
    }
    @Test fun ignoresSniLikeBytesOutsideClientHello() { assertNull(TlsServerName.read(byteArrayOf(0,0,0,0,0,0,0))) }

    @Test fun numericTargetUsesSniForBlacklistButNeverChangesRemoteAddress() {
        verifyProxySni("127.0.0.1", LogKind.UNBLOCKED)
    }
    @Test fun namedManualConnectRetainsOriginalBlacklistDecision() {
        verifyProxySni("other.example", LogKind.DIRECT)
    }
    private fun verifyProxySni(originalHost: String, expectedKind: LogKind) {
        val proxy = ProxyServer(ProxyConfig(disorder = false), DomainBlacklist(listOf("i.instagram.com"), DomainMatching.STRICT), ProxyStats(), ProxyLog())
        java.net.ServerSocket(0).use { listener ->
            java.net.Socket("127.0.0.1", listener.localPort).use { remote ->
                listener.accept().use { peer ->
                    peer.soTimeout = 3000
                    val originalAddress = remote.remoteSocketAddress
                    val body = hello("i.instagram.com")
                    val header = byteArrayOf(22, 3, 1, (body.size ushr 8).toByte(), body.size.toByte())
                    val infoClass = Class.forName("com.gvcoder.nodpi.core.ProxyServer\$ConnectionInfo")
                    val info = infoClass.getDeclaredConstructor(String::class.java, String::class.java).apply { isAccessible = true }.newInstance(originalHost, "CONNECT")
                    val method = ProxyServer::class.java.getDeclaredMethod("sendInitialTlsData", java.io.InputStream::class.java, java.net.Socket::class.java, infoClass).apply { isAccessible = true }
                    assertEquals(true, method.invoke(proxy, (header + body).inputStream(), remote, info))
                    val hostField = infoClass.getDeclaredField("host").apply { isAccessible = true }
                    val kindField = infoClass.getDeclaredField("kind").apply { isAccessible = true }
                    assertEquals("i.instagram.com", hostField.get(info))
                    assertEquals(expectedKind, kindField.get(info))
                    assertEquals(originalAddress, remote.remoteSocketAddress)
                    val expected = if (expectedKind == LogKind.UNBLOCKED) TlsFragmenter.fragment(header, body).join() else header + body
                    val actual = ByteArray(expected.size)
                    java.io.DataInputStream(peer.getInputStream()).readFully(actual)
                    assertArrayEquals(expected, actual)
                }
            }
        }
        proxy.stop()
    }
}
