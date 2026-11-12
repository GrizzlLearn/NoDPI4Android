package com.gvcoder.nodpi.core

import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.data.SettingsTransfer
import org.junit.Assert.*
import org.junit.Test

class SettingsTransferTest {
    @Test fun exportOmitsCredentialsAndRestoresRoutesWithoutWeakeningAuthentication() {
        val original = AppSettings(ipv4Only = true, vpnEnabled = true, vpnSelectedOnly = true, vpnPackages = setOf("com.instagram.android"), upstreamPackages = setOf("org.telegram.messenger"), upstream = Socks5Config(true, "proxy.example", 1080, "private-user", "private-password"), authUsername = "local-user", authPassword = "local-password")
        val exported = SettingsTransfer.export(original, listOf("example.com"))
        for (secret in listOf("private-user", "private-password", "local-user", "local-password")) assertFalse(exported.contains(secret))
        val imported = SettingsTransfer.parse(exported)
        assertEquals(original.vpnPackages, imported.settings.vpnPackages)
        assertTrue(imported.settings.ipv4Only)
        assertTrue(imported.applyTo(AppSettings()).toProxyConfig().ipv4Only)
        assertEquals(original.upstreamPackages, imported.settings.upstreamPackages)
        assertTrue(imported.settings.upstream.enabled)
        assertTrue(imported.settings.upstream.credentialsRequired)
        assertTrue(runCatching { imported.settings.upstream.validate() }.isFailure)
        assertTrue(runCatching { imported.settings.toProxyConfig().validateRoutes() }.isFailure)
        assertEquals(listOf("example.com"), imported.domains)
        val current = AppSettings(host = "127.0.0.2", port = 1234, authPassword = "unchanged", disclaimerAccepted = true)
        val applied = imported.applyTo(current)
        assertEquals("unchanged", applied.authPassword)
        assertEquals(1234, applied.port)
        assertTrue(applied.disclaimerAccepted)
    }
    @Test fun noAuthEndpointRemainsNoAuthAndUnknownPasswordKeysCannotBeImported() {
        val text = SettingsTransfer.export(AppSettings(upstream = Socks5Config(true, "proxy.example", 1080)), emptyList()) + "\nsocks.password=injected\nsocks.username=evil\n"
        val value = SettingsTransfer.parse(text).settings.upstream
        assertEquals("", value.password)
        assertEquals("", value.username)
        value.validate()
    }
    @Test fun oldExportsWithoutIpv4SettingRemainCompatible() {
        val text = SettingsTransfer.export(AppSettings(), emptyList()).lineSequence().filterNot { it.startsWith("vpn.ipv4Only=") }.joinToString("\n")
        assertFalse(SettingsTransfer.parse(text).settings.ipv4Only)
    }
    @Test fun malformedAndNewerVersionsRejectedBeforeSettingsChange() {
        val text = SettingsTransfer.export(AppSettings(), emptyList())
        assertTrue(runCatching { SettingsTransfer.parse(text.replace("nodpi.format=1", "nodpi.format=9")) }.isFailure)
        assertTrue(runCatching { SettingsTransfer.parse(text.replace("socks.port=1080", "socks.port=70000")) }.isFailure)
        assertTrue(runCatching { SettingsTransfer.parse(text.replace("vpn.enabled=false", "vpn.enabled=maybe")) }.isFailure)
    }
}
