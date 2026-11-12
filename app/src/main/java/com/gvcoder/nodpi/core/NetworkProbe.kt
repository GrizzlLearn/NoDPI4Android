package com.gvcoder.nodpi.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.io.IOException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import android.util.Base64

/** Explicit user-triggered TLS check, distinct from pass-through traffic counters. */
object NetworkProbe {
    fun check(host: String, config: ProxyConfig, external: Boolean): String {
        require(host.length in 1..253 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) { "Enter a domain without URL/path" }
        val result = StringBuilder()
        var phase = "DNS_PROXY"
        try {
            val resolver = DomainResolver(config.dohEnabled, config.vpnEnabled && config.ipv4Only)
            val address = resolver.resolve(if (external) config.upstream.host else config.host.takeUnless { it.isBlank() || it == "0.0.0.0" || it == "::" } ?: "127.0.0.1")
            result.append("DNS_PROXY: OK ($address)\n")
            phase = "TCP_PROXY"
            Socket(Proxy.NO_PROXY).use { socket ->
                socket.soTimeout = 8000
                socket.connect(InetSocketAddress(address, if (external) config.upstream.port else config.port), 8000)
                result.append("TCP_PROXY: OK\n")
                phase = "DESTINATION_CONNECT"
                if (external) Socks5Client.connect(socket, config.upstream, host, 443)
                else {
                    val auth = if (config.authEnabled) {
                        "Proxy-Authorization: Basic " + Base64.encodeToString("${config.username}:${config.password}".toByteArray(), Base64.NO_WRAP) + "\r\n"
                    } else ""
                    socket.getOutputStream().write("CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\n${auth}\r\n".toByteArray())
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n") && head.length < 8192) {
                        val byte = socket.getInputStream().read()
                        if (byte < 0) throw IOException("Proxy closed connection")
                        head.append(byte.toChar())
                    }
                    if (!head.startsWith("HTTP/1.1 200 ")) throw IOException(head.lineSequence().first())
                }
                result.append("DESTINATION_CONNECT: OK\n")
                phase = "TLS_VERIFIED"
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (factory.createSocket(socket, host, 443, true) as SSLSocket).use { tls ->
                    tls.soTimeout = 8000
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    tls.startHandshake()
                    result.append("TLS_VERIFIED: OK (${tls.session.protocol})\n")
                    phase = "HTTP_RESPONSE"
                    tls.getOutputStream().write("HEAD / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                    val line = StringBuilder()
                    val input = tls.getInputStream()
                    while (line.length < 4096) {
                        val byte = input.read()
                        if (byte < 0 || byte == 10) break
                        if (byte != 13) line.append(byte.toChar())
                    }
                    if (line.isEmpty()) throw IOException("No response")
                    result.append("HTTP_RESPONSE: $line")
                }
            }
        } catch (error: Exception) {
            result.append("$phase: FAILED (${error.javaClass.simpleName}: ${error.message})")
        }
        return result.toString()
    }
}
