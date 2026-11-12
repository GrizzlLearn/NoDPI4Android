package com.gvcoder.nodpi.core

import org.junit.Assert.*
import org.junit.Test

class VpnDestinationGuardTest {
    @Test fun rejectsStaleMappingsAcrossEntireConfiguredSubnet() {
        assertNotNull(VpnDestinationGuard.rejection("198.19.0.0"))
        assertNotNull(VpnDestinationGuard.rejection("198.19.255.255"))
    }
    @Test fun rejectsInternalDnsAndInterfaceAddresses() {
        assertNotNull(VpnDestinationGuard.rejection("198.18.0.2"))
        assertNotNull(VpnDestinationGuard.rejection("198.18.0.1"))
        assertNotNull(VpnDestinationGuard.rejection("fd00:6e6f:6470::1"))
    }
    @Test fun permitsInternetHostsAndAvoidsPrefixFalsePositives() {
        for (host in listOf("i.instagram.com", "149.154.167.41", "198.190.0.2", "198.18.1.2", "2001:4860::1")) {
            assertNull(host, VpnDestinationGuard.rejection(host))
        }
    }
}
