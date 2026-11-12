package com.gvcoder.nodpi.core

/** Addresses owned by our TUN/mapdns must never be attempted on the physical network. */
object VpnDestinationGuard {
    fun rejection(host: String): String? {
        val octets = host.split('.')
        val ipv4 = if (octets.size == 4) octets.map { it.toIntOrNull() } else emptyList()
        if (ipv4.size == 4 && ipv4.all { it != null && it in 0..255 }) {
            if (ipv4[0] == 198 && ipv4[1] == 19) {
                return "Stale VPN DNS mapping: restart the affected app to refresh its DNS cache"
            }
            if (ipv4[0] == 198 && ipv4[1] == 18 && ipv4[2] == 0 && ipv4[3] in listOf(1, 2)) {
                return "Internal VPN address is not an upstream server (DNS is available on UDP port 53 only)"
            }
        }
        if (host.equals("fd00:6e6f:6470::1", true) || host.equals("fd00:6e6f:6470:0:0:0:0:1", true)) {
            return "Internal VPN interface is not an upstream server"
        }
        return null
    }
}
