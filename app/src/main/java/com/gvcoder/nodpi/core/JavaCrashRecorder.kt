package com.gvcoder.nodpi.core

import java.io.File

/** Keep a separate synchronous crash record so the async journal writer cannot overwrite it. */
object JavaCrashRecorder {
    fun install(file: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val message = "thread=${thread.name}\n${error.stackTraceToString()}".take(8000)
                ProxyLogStore(file).save(listOf(LogEntry(
                    id = 0, time = System.currentTimeMillis(), kind = LogKind.ERROR,
                    host = "PROCESS", method = "JAVA_STACK", message = message,
                )))
            } catch (_: Throwable) {
                // Under memory pressure even recording the failure may be impossible.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
