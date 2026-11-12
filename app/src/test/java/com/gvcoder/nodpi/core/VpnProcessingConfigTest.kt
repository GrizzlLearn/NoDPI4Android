package com.gvcoder.nodpi.core

import com.gvcoder.nodpi.data.AppSettings
import org.junit.Assert.*
import org.junit.Test

class VpnProcessingConfigTest {
    @Test fun vpnAppliesBypassToChosenApplicationsWithoutASecondDomainSwitch() {
        val settings = AppSettings(vpnEnabled = true, vpnSelectedOnly = true, vpnPackages = setOf("com.instagram.android"), blacklistMode = BlacklistMode.STANDARD)
        val config = settings.toProxyConfig()
        assertEquals(BlacklistMode.ALL, config.blacklistMode)
        assertTrue(config.vpnSelectedOnly)
        assertEquals(setOf("com.instagram.android"), config.vpnPackages)
        assertEquals(BlacklistMode.STANDARD, settings.copy(vpnEnabled = false).toProxyConfig().blacklistMode)
    }
    @Test fun externalAppsAreCapturedEvenWithoutLocalSelection() {
        val config = ProxyConfig(vpnEnabled = true, vpnSelectedOnly = true, upstream = Socks5Config(enabled = true), upstreamPackages = setOf("org.telegram.messenger"))
        assertEquals(setOf("org.telegram.messenger"), config.capturedPackages)
        assertTrue(config.copy(upstream = Socks5Config(enabled = false)).capturedPackages.isEmpty())
    }
    @Test fun manualProxyKeepsEachSavedDomainMode() {
        for (mode in BlacklistMode.entries) {
            assertEquals(mode, AppSettings(vpnEnabled = false, blacklistMode = mode).toProxyConfig().blacklistMode)
        }
    }
}
