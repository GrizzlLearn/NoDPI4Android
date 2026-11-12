package com.gvcoder.nodpi.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class JavaCrashRecorderTest {
    @Test fun recordsStackSeparatelyAndPreservesOriginalHandler() {
        val file = File.createTempFile("nodpi-crash", ".bin").also { it.delete() }
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegated: Throwable? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> delegated = error }
            JavaCrashRecorder.install(file)
            val error = IllegalStateException("test failure")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertSame(error, delegated)
            val saved = ProxyLogStore(file).load().single()
            assertEquals("JAVA_STACK", saved.method)
            assertTrue(saved.message!!.contains("IllegalStateException: test failure"))
            assertTrue(saved.message.contains("JavaCrashRecorderTest"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
            file.delete()
        }
    }
}
