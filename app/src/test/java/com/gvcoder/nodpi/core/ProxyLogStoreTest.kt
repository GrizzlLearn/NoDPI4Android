package com.gvcoder.nodpi.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ProxyLogStoreTest {
    @Test fun journalSurvivesReopeningAndClear() {
        val file = File.createTempFile("nodpi-log", ".bin").also { it.delete() }
        try {
            val entry = LogEntry(7, 1234, LogKind.ERROR, "PROCESS", "EXIT", message = "NATIVE_CRASH", cloudflare = true)
            ProxyLogStore(file).save(listOf(entry))
            assertEquals(listOf(entry), ProxyLogStore(file).load())
            ProxyLogStore(file).save(emptyList())
            assertTrue(ProxyLogStore(file).load().isEmpty())
        } finally { file.delete() }
    }
    @Test fun journalIsBoundedAndIncompleteTemporaryWriteDoesNotReplaceIt() {
        val file = File.createTempFile("nodpi-log", ".bin").also { it.delete() }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            val entries = (0L..599L).map { LogEntry(it, it, LogKind.DIRECT, "example.com", "CONNECT", 12, 34) }
            ProxyLogStore(file).save(entries)
            temporary.writeText("interrupted write")
            assertEquals(entries.takeLast(500), ProxyLogStore(file).load())
        } finally { file.delete(); temporary.delete() }
    }
}
