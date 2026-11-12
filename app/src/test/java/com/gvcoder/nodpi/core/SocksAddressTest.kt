package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Test

class SocksAddressTest {
    private fun parse(vararg bytes: Int) = SocksAddress.read(DataInputStream(bytes.map { it.toByte() }.toByteArray().inputStream()))

    @Test fun ipv4AndUnsignedPort() {
        assertEquals(SocksAddress("192.168.1.2", 65535), parse(1, 192, 168, 1, 2, 255, 255))
    }
    @Test fun domainPreservedForDnsAndBlacklist() {
        val name = "i.instagram.com".toByteArray()
        val bytes = byteArrayOf(3, name.size.toByte()) + name + byteArrayOf(1, -69)
        assertEquals(SocksAddress("i.instagram.com", 443), SocksAddress.read(DataInputStream(bytes.inputStream())))
    }
    @Test fun ipv6() {
        val bytes = byteArrayOf(4) + ByteArray(15) + byteArrayOf(1, 0, 80)
        assertEquals(80, SocksAddress.read(DataInputStream(bytes.inputStream())).port)
    }
    @Test(expected = EOFException::class) fun truncatedAddressRejected() { parse(1, 127, 0) }
    @Test(expected = IllegalStateException::class) fun unknownTypeRejected() { parse(9) }
    @Test fun zeroPortAllowedForUdpAssociate() { assertEquals(0, parse(1, 127, 0, 0, 1, 0, 0).port) }
    @Test(expected = IllegalArgumentException::class) fun emptyDomainRejected() { parse(3, 0, 1, 187) }
}
