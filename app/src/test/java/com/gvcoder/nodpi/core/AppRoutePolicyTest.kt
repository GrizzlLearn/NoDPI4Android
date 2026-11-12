package com.gvcoder.nodpi.core

import org.junit.Assert.*
import org.junit.Test

class AppRoutePolicyTest {
    @Test fun appsSharingDestinationStillUseDifferentRoutes() {
        val policy = AppRoutePolicy(setOf(1002), 5000)
        assertEquals(0, policy.portForUid(1001))
        assertEquals(5000, policy.portForUid(1002))
        assertEquals(-1, policy.portForUid(-1))
    }
    @Test fun sharedUidUsesOneConsistentRoute() {
        val policy = AppRoutePolicy(setOf(1002), 5000)
        repeat(20) { assertEquals(5000, policy.portForUid(1002)) }
    }
}
