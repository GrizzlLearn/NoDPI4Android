package com.gvcoder.nodpi.core

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Classifies observable failures without treating a timeout as proof of filtering. */
object NetworkFailure {
    fun describe(error: Throwable): String {
        val chain = mutableListOf<Throwable>()
        var next: Throwable? = error
        while (next != null && chain.size < 8 && chain.none { it === next }) {
            chain += next
            next = next.cause
        }
        val messages = chain.mapNotNull { it.message }.joinToString(" | ").replace('\n', ' ').replace('\r', ' ').take(1200)
        val errno = listOf("ENETUNREACH", "EHOSTUNREACH", "ECONNREFUSED", "ETIMEDOUT", "ECONNRESET", "EACCES", "EPERM", "EADDRNOTAVAIL")
            .firstOrNull { Regex("\\b$it\\b").containsMatchIn(messages) }
        val category = when {
            chain.any { it is UnknownHostException } -> "DNS_FAILED"
            errno == "ENETUNREACH" -> "NETWORK_UNREACHABLE"
            errno == "EHOSTUNREACH" -> "HOST_UNREACHABLE"
            errno == "ECONNREFUSED" || messages.contains("connection refused", true) -> "CONNECTION_REFUSED"
            errno in setOf("EACCES", "EPERM") -> "LOCAL_PERMISSION_DENIED"
            errno == "EADDRNOTAVAIL" -> "LOCAL_ADDRESS_UNAVAILABLE"
            errno == "ETIMEDOUT" || chain.any { it is SocketTimeoutException } -> "TIMEOUT_UNDETERMINED"
            errno == "ECONNRESET" || messages.contains("connection reset", true) -> "CONNECTION_RESET"
            messages.contains("socket is closed", true) || messages.contains("socket closed", true) -> "CONNECTION_CLOSED"
            else -> "IO_FAILURE"
        }
        return "failure=$category; errno=${errno ?: "UNKNOWN"}; cause=${error.javaClass.simpleName}: $messages" +
            if (category == "TIMEOUT_UNDETERMINED") "; conclusion=NO_REPLY; filtering_vs_server_failure=UNKNOWN" else ""
    }
}
