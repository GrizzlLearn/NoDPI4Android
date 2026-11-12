package com.gvcoder.nodpi.core

/** Which domains get their ClientHello fragmented */
enum class BlacklistMode {
    /** Only domains from the blacklist */
    STANDARD,

    /** Every domain, the blacklist is ignored */
    ALL,

    /** Blacklist plus domains detected automatically by a TLS handshake timeout */
    AUTO,
}

/** How a requested domain is compared with blacklist entries */
enum class DomainMatching {
    /** Exact domain or any of its parent domains */
    STRICT,

    /** Additionally, any domain that contains a blacklist entry as a substring */
    LOOSE,
}

/** Configuration of a single proxy server run */
data class ProxyConfig(
    val upstream: Socks5Config = Socks5Config(),
    val upstreamPackages: Set<String> = emptySet(),
    val vpnEnabled: Boolean = false,
    val vpnSelectedOnly: Boolean = false,
    val vpnPackages: Set<String> = emptySet(),
    val dohEnabled: Boolean = false,
    val ipv4Only: Boolean = false,
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val blacklistMode: BlacklistMode = BlacklistMode.STANDARD,
    val domainMatching: DomainMatching = DomainMatching.STRICT,
    val disorder: Boolean = true,
    val username: String? = null,
    val password: String? = null,
) {
    fun validateRoutes() {
        if (vpnEnabled && upstream.enabled) {
            upstream.validate()
            require(upstreamPackages.isNotEmpty()) { "Choose applications for external SOCKS5" }
        }
    }

    val capturedPackages: Set<String> get() = vpnPackages + if (upstream.enabled) upstreamPackages else emptySet()
    val authEnabled: Boolean get() = username != null && password != null

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8881
    }
}
