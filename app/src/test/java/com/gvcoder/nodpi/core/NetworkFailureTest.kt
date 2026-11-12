package com.gvcoder.nodpi.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class NetworkFailureTest {
    @Test fun timeoutDoesNotClaimBlocking() {
        val value = NetworkFailure.describe(SocketTimeoutException("failed after 15000ms"))
        assertTrue(value.contains("failure=TIMEOUT_UNDETERMINED"))
        assertTrue(value.contains("filtering_vs_server_failure=UNKNOWN"))
    }
    @Test fun extractsSystemCauseInsteadOfConfusingMissingRouteWithTimeout() {
        val error = ConnectException("failed after 15000ms").apply { initCause(java.io.IOException("connect failed: ENETUNREACH")) }
        val value = NetworkFailure.describe(error)
        assertTrue(value.contains("failure=NETWORK_UNREACHABLE"))
        assertTrue(value.contains("errno=ENETUNREACH"))
        assertFalse(value.contains("TIMEOUT_UNDETERMINED"))
    }
    @Test fun refusalDnsAndPermissionHaveDistinctCategories() {
        assertTrue(NetworkFailure.describe(ConnectException("Connection refused")).contains("failure=CONNECTION_REFUSED"))
        assertTrue(NetworkFailure.describe(UnknownHostException("example.com")).contains("failure=DNS_FAILED"))
        assertTrue(NetworkFailure.describe(java.io.IOException("EACCES")).contains("failure=LOCAL_PERMISSION_DENIED"))
    }
}
