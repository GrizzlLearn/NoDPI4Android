package com.gvcoder.nodpi.core

import org.junit.Test

class DnsForwarderTest {
    private val query = ByteArray(12).apply { this[0] = 12; this[1] = 34 }
    private val reply get() = query.copyOf().apply { this[2] = 0x80.toByte() }
    @Test fun acceptsMatchingResponse() { validateDnsReply(query, reply) }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongId() {
        validateDnsReply(query, reply.apply { this[1] = 35 })
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedResponse() {
        validateDnsReply(query, ByteArray(11))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsQueryAsResponse() {
        validateDnsReply(query, query)
    }
}
