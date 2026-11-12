package com.gvcoder.nodpi.core

/** Credentials are never included in diagnostic representations. */
data class Socks5Config(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val credentialsRequired: Boolean = false,
) {
    fun validate() {
        require(!credentialsRequired) { "SOCKS5: re-enter credentials after settings import" }
        require(host.isNotBlank() && host.none { it.isWhitespace() || it == '/' }) { "SOCKS5: specify a hostname or IP without a URL scheme" }
        require(port in 1..65535) { "SOCKS5: port must be 1–65535" }
        if (username.isNotEmpty()) {
            require(username.toByteArray().size in 1..255 && password.toByteArray().size in 1..255) { "SOCKS5: username and password must each contain 1–255 UTF-8 bytes" }
        } else require(password.isEmpty()) { "SOCKS5: a password requires a username" }
    }
    override fun toString() = "Socks5Config(enabled=$enabled, host=$host, port=$port, authenticated=${username.isNotEmpty()})"
}
