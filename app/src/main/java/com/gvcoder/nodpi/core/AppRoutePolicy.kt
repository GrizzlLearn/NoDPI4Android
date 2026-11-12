package com.gvcoder.nodpi.core

/** Destination-independent route decision, pinned by the native engine per original app flow. */
internal class AppRoutePolicy(private val externalUids: Set<Int>, private val externalPort: Int) {
    fun portForUid(uid: Int): Int = when {
        uid < 0 -> -1
        uid in externalUids -> externalPort
        else -> 0
    }
}
