package com.gvcoder.nodpi.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

enum class LogKind {
    /** TLS connection passed as is: domain is not in the blacklist */
    DIRECT,

    /** TLS connection whose ClientHello was fragmented */
    UNBLOCKED,

    /** Plain HTTP request or a non-TLS tunnel */
    PLAIN,

    ERROR,
    INFO,
}

data class LogEntry(
    val id: Long,
    val time: Long,
    val kind: LogKind,
    val host: String,
    val method: String,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val message: String? = null,
    /** Blocked domain on Cloudflare sent without fragmentation, see [CloudflareRanges] */
    val cloudflare: Boolean = false,
)

/** Access and error journal with an optional private on-disk snapshot. */
class ProxyLog(private val capacity: Int = 500) {

    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Entries from the oldest to the newest */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun attachPersistence(file: File, scope: CoroutineScope) {
        val store = ProxyLogStore(file)
        val previous = runCatching { store.load() }.getOrDefault(emptyList())
        val crashFile = File(file.parentFile, "java-crash.bin")
        val crashes = runCatching { ProxyLogStore(crashFile).load() }.getOrDefault(emptyList())
        var nextId = previous.maxOfOrNull { it.id } ?: 0
        val restored = (previous + crashes.map { it.copy(id = ++nextId) }).takeLast(capacity)
        if (crashes.isNotEmpty()) {
            runCatching { store.save(restored) }.onSuccess { crashFile.delete() }
        }
        _entries.value = restored
        ids.set(restored.maxOfOrNull { it.id } ?: 0)
        scope.launch(Dispatchers.IO) {
            entries.collect { snapshot ->
                runCatching { store.save(snapshot) }
                    .onFailure { android.util.Log.w("NoDPI", "Cannot save journal", it) }
            }
        }
    }

    fun access(kind: LogKind, host: String, method: String, bytesIn: Long, bytesOut: Long, cloudflare: Boolean = false, message: String? = null) {
        append(
            LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), kind, host, method, bytesIn, bytesOut, message = message, cloudflare = cloudflare),
        )
    }

    fun info(host: String, method: String, message: String) {
        append(LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), LogKind.INFO, host, method, message = message))
    }

    fun error(host: String, method: String, message: String) {
        append(LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), LogKind.ERROR, host, method, message = message))
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(entry: LogEntry) {
        _entries.update { current ->
            if (current.size >= capacity) current.drop(current.size - capacity + 1) + entry else current + entry
        }
    }
}
