package com.gvcoder.nodpi.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ProxyServerCloseTest {
    @Test fun alreadyClosedRemoteCompletesBothPipesWithoutUncaughtException() {
        val log = ProxyLog()
        val proxy = ProxyServer(ProxyConfig(), AllDomainsBlacklist, ProxyStats(), log)
        val client = Socket()
        val remote = Socket().apply { close() }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val uncaught = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
        try {
            // Enter the exact race from the crash report with the remote already closed.
            val infoClass = Class.forName("com.gvcoder.nodpi.core.ProxyServer\$ConnectionInfo")
            val constructor = infoClass.getDeclaredConstructor(String::class.java, String::class.java).apply { isAccessible = true }
            val info = constructor.newInstance("example.com", "CONNECT")
            val method = ProxyServer::class.java.getDeclaredMethod("pipeBothWays", Socket::class.java, InputStream::class.java, Socket::class.java, infoClass)
                .apply { isAccessible = true }
            method.invoke(proxy, client, ByteArrayInputStream(byteArrayOf()), remote, info)
            val completed = runBlocking { withTimeout(3000) { log.entries.first { it.isNotEmpty() } } }
            assertEquals(1, completed.size)
            assertTrue(client.isClosed)
            assertTrue(remote.isClosed)
            assertNull(uncaught.get())
        } finally {
            proxy.stop()
            client.close()
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}
