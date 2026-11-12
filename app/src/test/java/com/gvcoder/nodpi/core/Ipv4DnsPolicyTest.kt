package com.gvcoder.nodpi.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class Ipv4DnsPolicyTest {
    private fun query(type: Int): ByteArray = ByteArrayOutputStream().also { buffer ->
        val output = DataOutputStream(buffer)
        output.write(byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in listOf("i", "ytimg", "com")) { output.writeByte(label.length); output.writeBytes(label) }
        output.writeByte(0); output.writeShort(type); output.writeShort(1)
    }.toByteArray()

    @Test fun aQueriesRemainUnchanged() { assertNull(ipv4OnlyDnsReply(query(1))) }
    @Test fun ipv6AndServiceHintsReturnNoDataButKeepNameValidForA() {
        for (type in listOf(28, 64, 65)) {
            val request = query(type)
            val response = checkNotNull(ipv4OnlyDnsReply(request))
            validateDnsReply(request, response)
            assertArrayEquals(request.copyOfRange(12, request.size), response.copyOfRange(12, response.size))
            assertEquals(0, response[3].toInt() and 15) // NOERROR, not NXDOMAIN
            assertEquals(1, response[5].toInt())
            assertTrue(response.copyOfRange(6, 12).all { it == 0.toByte() })
        }
    }
    @Test fun additionalRecordsAreNotCopiedWithZeroCounts() {
        val question = query(28)
        val withAdditional = (question + byteArrayOf(0, 0, 41, 4, -48, 0, 0, 0, 0, 0, 0)).apply { this[11] = 1 }
        assertEquals(question.size, checkNotNull(ipv4OnlyDnsReply(withAdditional)).size)
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedQuestionRejected() { ipv4OnlyDnsReply(query(28).dropLast(1).toByteArray()) }
    @Test fun cachedIpv6DestinationIsRejectedBeforeOpeningAnyRemoteSocket() {
        LocalSocksBridge(ProxyConfig(vpnEnabled = true, ipv4Only = true)).use { bridge ->
            bridge.start()
            Socket("127.0.0.1", bridge.port).use { client ->
                client.soTimeout = 3000
                val input = DataInputStream(client.getInputStream())
                client.getOutputStream().write(byteArrayOf(5, 1, 0)); input.readFully(ByteArray(2))
                client.getOutputStream().write(byteArrayOf(5, 1, 0, 4) + ByteArray(15) + byteArrayOf(1, 1, -69))
                val reply = ByteArray(10).also { input.readFully(it) }
                assertEquals(8, reply[1].toInt())
            }
        }
    }
}
