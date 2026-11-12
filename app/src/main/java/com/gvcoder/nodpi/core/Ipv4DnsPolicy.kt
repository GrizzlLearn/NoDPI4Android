package com.gvcoder.nodpi.core

/** NOERROR/NODATA, not NXDOMAIN: applications may still request A for the same name. */
internal fun ipv4OnlyDnsReply(query: ByteArray): ByteArray? {
    require(query.size >= 12) { "Truncated DNS query" }
    if ((query[2].toInt() and 0xf8) != 0 || query[4].toInt() != 0 || query[5].toInt() != 1) return null
    var position = 12
    var nameLength = 0
    while (true) {
        require(position < query.size) { "Truncated DNS name" }
        val length = query[position++].toInt() and 255
        if (length == 0) break
        require(length <= 63 && position + length <= query.size) { "Invalid DNS question name" }
        nameLength += length + 1
        require(nameLength <= 254) { "DNS name too long" }
        position += length
    }
    require(position + 4 <= query.size) { "Truncated DNS question" }
    val type = ((query[position].toInt() and 255) shl 8) or (query[position + 1].toInt() and 255)
    val dnsClass = ((query[position + 2].toInt() and 255) shl 8) or (query[position + 3].toInt() and 255)
    if (dnsClass != 1 || type !in setOf(28, 64, 65)) return null
    // HTTPS/SVCB can carry IPv6 hints and alternate resolution paths; use ordinary A+TLS instead.
    return query.copyOf(position + 4).apply {
        this[2] = (0x80 or (query[2].toInt() and 1)).toByte()
        this[3] = (0x80 or (query[3].toInt() and 0x10)).toByte()
        for (index in 6..11) this[index] = 0
    }
}
