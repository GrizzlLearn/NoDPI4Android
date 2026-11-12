package com.gvcoder.nodpi.core

/** Strict ClientHello parser for diagnostics, never used to redirect the TCP destination. */
internal object TlsServerName {
    fun read(body: ByteArray): String? = try {
        val input = Cursor(body)
        require(input.byte() == 1)
        val length = (input.byte() shl 16) or (input.byte() shl 8) or input.byte()
        require(length == body.size - 4)
        input.skip(34)
        input.skip(input.byte())
        val ciphers = input.short()
        require(ciphers >= 2 && ciphers % 2 == 0)
        input.skip(ciphers)
        input.skip(input.byte())
        val extensionsSize = input.short()
        val end = input.position + extensionsSize
        require(end == body.size)
        var name: String? = null
        while (input.position < end) {
            val type = input.short()
            val size = input.short()
            val extensionEnd = input.position + size
            require(extensionEnd <= end)
            if (type == 0) {
                val listSize = input.short()
                require(input.position + listSize == extensionEnd)
                while (input.position < extensionEnd) {
                    val nameType = input.byte()
                    val nameSize = input.short()
                    require(input.position + nameSize <= extensionEnd)
                    val bytes = input.bytes(nameSize)
                    if (nameType == 0) {
                        require(name == null && nameSize in 1..253)
                        require(bytes.all { (it.toInt() and 255) in 33..126 })
                        val candidate = bytes.toString(Charsets.US_ASCII).lowercase()
                        require(candidate.split('.').all { label ->
                            label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
                        })
                        name = candidate
                    }
                }
            } else input.skip(size)
            require(input.position == extensionEnd)
        }
        name
    } catch (_: IllegalArgumentException) { null }

    private class Cursor(private val body: ByteArray) {
        var position = 0
        fun byte(): Int { require(position < body.size); return body[position++].toInt() and 255 }
        fun short() = (byte() shl 8) or byte()
        fun skip(count: Int) { require(count >= 0 && count <= body.size - position); position += count }
        fun bytes(count: Int): ByteArray { val start = position; skip(count); return body.copyOfRange(start, position) }
    }
}
