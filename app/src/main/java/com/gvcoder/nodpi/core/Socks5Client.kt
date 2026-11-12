package com.gvcoder.nodpi.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.IDN
import java.net.InetAddress
import java.net.Socket
import java.io.IOException

/** Negotiates one upstream connection. The caller owns the socket on every failure. */
object Socks5Client {
    fun connect(socket: Socket, config: Socks5Config, host: String, port: Int) {
        config.validate()
        require(port in 1..65535)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val method = if (config.username.isEmpty()) 0 else 2
        output.write(byteArrayOf(5, 1, method.toByte()))
        if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != method) throw IOException("SOCKS5_AUTH: server rejected authentication method")
        if (method == 2) {
            val user = config.username.toByteArray(Charsets.UTF_8)
            val password = config.password.toByteArray(Charsets.UTF_8)
            output.writeByte(1); output.writeByte(user.size); output.write(user)
            output.writeByte(password.size); output.write(password)
            if (input.readUnsignedByte() != 1 || input.readUnsignedByte() != 0) throw IOException("SOCKS5_AUTH: credentials rejected")
        }
        output.write(byteArrayOf(5, 1, 0))
        val literal = host.contains(':') || host.matches(Regex("[0-9.]+"))
        if (literal) {
            val bytes = InetAddress.getByName(host).address
            output.writeByte(if (bytes.size == 4) 1 else 4); output.write(bytes)
        } else {
            val name = IDN.toASCII(host).toByteArray(Charsets.US_ASCII)
            require(name.size in 1..255)
            output.writeByte(3); output.writeByte(name.size); output.write(name)
        }
        output.writeShort(port)
        if (input.readUnsignedByte() != 5) throw IOException("SOCKS5_REPLY: invalid version")
        val result = input.readUnsignedByte()
        if (input.readUnsignedByte() != 0) throw IOException("SOCKS5_REPLY: invalid reserved field")
        when (input.readUnsignedByte()) {
            1 -> input.readFully(ByteArray(4))
            4 -> input.readFully(ByteArray(16))
            3 -> input.readFully(ByteArray(input.readUnsignedByte()))
            else -> throw IOException("SOCKS5_REPLY: invalid address")
        }
        input.readUnsignedShort()
        if (result != 0) throw IOException("SOCKS5_CONNECT: server reply=$result")
    }
}
