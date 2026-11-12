package com.gvcoder.nodpi.service

import org.junit.Assert.*
import org.junit.Test

class NetworkChangeTrackerTest {
    @Test fun ignoresInitialAndRepeatedCallbacks() {
        val tracker = NetworkChangeTracker()
        assertFalse(tracker.changed("wifi"))
        assertFalse(tracker.changed("wifi"))
    }
    @Test fun stoppedGenerationIgnoresLateCallbacks() {
        val previous = NetworkChangeTracker()
        previous.changed("wifi")
        previous.stop()
        assertFalse(previous.changed("mobile"))
        val restarted = NetworkChangeTracker()
        assertFalse(restarted.changed("mobile"))
        assertTrue(restarted.changed("wifi"))
    }
    @Test fun failedNetworkUpdateIsNotCommittedAndCanBeRetried() {
        val tracker = NetworkChangeTracker()
        tracker.applied("wifi")
        assertTrue(tracker.needsReconnect("mobile"))
        assertTrue(tracker.needsReconnect("mobile"))
        tracker.applied("mobile")
        assertFalse(tracker.needsReconnect("mobile"))
    }
    @Test fun reconnectsAcrossWifiMobileLossAndRecovery() {
        val tracker = NetworkChangeTracker()
        tracker.changed("wifi")
        assertTrue(tracker.changed("mobile"))
        assertTrue(tracker.changed("offline"))
        assertFalse(tracker.changed("offline"))
        assertTrue(tracker.changed("wifi-new-address"))
    }
}
