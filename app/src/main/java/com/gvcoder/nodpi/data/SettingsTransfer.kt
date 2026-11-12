package com.gvcoder.nodpi.data

import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainMatching
import com.gvcoder.nodpi.core.Socks5Config
import com.gvcoder.nodpi.core.normalizeDomain
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Explicit allowlist: account credentials and device-local service/consent settings never leave the app. */
object SettingsTransfer {
    data class Imported(val settings: AppSettings, val domains: List<String>) {
        fun applyTo(current: AppSettings) = current.copy(
            vpnEnabled = settings.vpnEnabled, vpnSelectedOnly = settings.vpnSelectedOnly,
            vpnPackages = settings.vpnPackages, upstreamPackages = settings.upstreamPackages,
            upstream = settings.upstream, dohEnabled = settings.dohEnabled, ipv4Only = settings.ipv4Only,
            blacklistMode = settings.blacklistMode, domainMatching = settings.domainMatching,
            disorder = settings.disorder,
        )
    }

    fun export(settings: AppSettings, domains: List<String>): String {
        require(domains.size <= 10000) { "Too many domains for settings transfer (maximum 10000)" }
        require(settings.vpnPackages.size <= 500 && settings.upstreamPackages.size <= 500) { "Too many applications for settings transfer" }
        val values = Properties().apply {
            setProperty("nodpi.format", "1")
            setProperty("vpn.enabled", settings.vpnEnabled.toString())
            setProperty("vpn.selectedOnly", settings.vpnSelectedOnly.toString())
            setProperty("vpn.packages", settings.vpnPackages.sorted().joinToString(","))
            setProperty("socks.packages", settings.upstreamPackages.sorted().joinToString(","))
            setProperty("socks.enabled", settings.upstream.enabled.toString())
            setProperty("socks.host", settings.upstream.host)
            setProperty("socks.port", settings.upstream.port.toString())
            setProperty("socks.credentialsRequired", (settings.upstream.credentialsRequired || settings.upstream.username.isNotEmpty() || settings.upstream.password.isNotEmpty()).toString())
            setProperty("vpn.ipv4Only", settings.ipv4Only.toString())
            setProperty("dns.doh", settings.dohEnabled.toString())
            setProperty("bypass.mode", settings.blacklistMode.name)
            setProperty("bypass.matching", settings.domainMatching.name)
            setProperty("bypass.disorder", settings.disorder.toString())
            setProperty("bypass.domains", domains.joinToString("\n"))
        }
        return StringWriter().also { values.store(it, "NoDPI settings v1; credentials are excluded") }.toString()
    }

    fun parse(text: String): Imported {
        require(text.length <= 1_048_576) { "Settings file exceeds 1 MiB" }
        val values = Properties().apply { load(StringReader(text)) }
        require(values.getProperty("nodpi.format") == "1") { "Unsupported NoDPI settings format" }
        fun boolean(key: String) = values.getProperty(key)?.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid setting: $key")
        fun packages(key: String): Set<String> {
            val names = values.getProperty(key, "").split(',').filter { it.isNotEmpty() }
            require(names.size <= 500 && names.all { it.length <= 255 && it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }) { "Invalid application list: $key" }
            return names.toSet()
        }
        val host = values.getProperty("socks.host", "")
        require(host.length <= 253 && host.none { it.isWhitespace() || it == '/' }) { "Invalid SOCKS5 server" }
        val port = values.getProperty("socks.port")?.toIntOrNull()
        require(port != null && port in 1..65535) { "Invalid SOCKS5 port" }
        val externalEnabled = boolean("socks.enabled")
        require(!externalEnabled || host.isNotBlank()) { "Missing SOCKS5 server" }
        val domains = values.getProperty("bypass.domains", "").lineSequence().filter { it.isNotBlank() }.toList()
        require(domains.size <= 10000 && domains.all { it.length <= 253 && normalizeDomain(it) == it }) { "Invalid domain list" }
        return Imported(AppSettings(
            vpnEnabled = boolean("vpn.enabled"), vpnSelectedOnly = boolean("vpn.selectedOnly"),
            vpnPackages = packages("vpn.packages"), upstreamPackages = packages("socks.packages"),
            upstream = Socks5Config(enabled = externalEnabled, host = host, port = port, credentialsRequired = boolean("socks.credentialsRequired")),
            ipv4Only = values.getProperty("vpn.ipv4Only", "false").toBooleanStrict(),
            dohEnabled = boolean("dns.doh"), blacklistMode = BlacklistMode.valueOf(values.getProperty("bypass.mode")),
            domainMatching = DomainMatching.valueOf(values.getProperty("bypass.matching")), disorder = boolean("bypass.disorder"),
        ), domains.distinct())
    }
}
