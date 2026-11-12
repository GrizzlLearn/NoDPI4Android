package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/** Private, bounded snapshot; rename keeps the previous journal intact if a write is interrupted. */
class ProxyLogStore(private val file: File) {
    fun load(): List<LogEntry> {
        if (!file.exists()) return emptyList()
        return DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == 1) { "Unknown journal version" }
            val count = input.readInt()
            require(count in 0..500) { "Invalid journal size" }
            List(count) {
                LogEntry(
                    id = input.readLong(), time = input.readLong(),
                    kind = LogKind.valueOf(input.readUTF()), host = input.readUTF(), method = input.readUTF(),
                    bytesIn = input.readLong(), bytesOut = input.readLong(),
                    message = input.readUTF().ifEmpty { null }, cloudflare = input.readBoolean(),
                )
            }
        }
    }

    fun save(entries: List<LogEntry>) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            val output = DataOutputStream(stream.buffered())
            output.writeInt(1)
            val retained = entries.takeLast(500)
            output.writeInt(retained.size)
            for (entry in retained) {
                output.writeLong(entry.id); output.writeLong(entry.time)
                output.writeUTF(entry.kind.name); output.writeUTF(entry.host.take(1000)); output.writeUTF(entry.method.take(100))
                output.writeLong(entry.bytesIn); output.writeLong(entry.bytesOut)
                output.writeUTF(entry.message.orEmpty().take(8000)); output.writeBoolean(entry.cloudflare)
            }
            output.flush()
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot replace journal" }
    }
}
