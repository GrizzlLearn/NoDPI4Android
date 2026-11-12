package com.gvcoder.nodpi.core

/** Keep admission and duplex worker capacities aligned across VPN/proxy layers. */
object ConnectionLimits {
    const val TRAFFIC_SESSIONS = 128
    const val UDP_ASSOCIATIONS = 16
    const val DNS_SESSIONS = 16
    const val NATIVE_SESSIONS = 256
    const val BRIDGE_WORKERS = TRAFFIC_SESSIONS * 2 + UDP_ASSOCIATIONS + 1
}
