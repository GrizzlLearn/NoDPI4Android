package com.gvcoder.nodpi.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import java.io.Closeable

/** Observe physical networks only; never react to the VPN interface we create ourselves. */
internal class PhysicalNetworkMonitor(private val manager: ConnectivityManager, private val changed: () -> Unit) : Closeable {
    data class Snapshot(val networks: Array<Network>, val identity: String)
    private var registered = false
    private var defaultRegistered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = changed()
        override fun onLost(network: Network) = changed()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = changed()
    }

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = changed()
        override fun onLost(network: Network) = changed()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed()
    }

    fun start() {
        manager.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        registered = true
        manager.registerDefaultNetworkCallback(defaultCallback)
        defaultRegistered = true
        changed()
    }

    fun snapshot(): Snapshot {
        val defaultNetwork = manager.activeNetwork
        val candidates = manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            val link = manager.getLinkProperties(network) ?: return@mapNotNull null
            val score = (if (network == defaultNetwork) 1000 else 0) + (if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0) +
                (if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 10 else 0)
            Triple(network, score, "$network:${link.interfaceName}:${link.linkAddresses.sortedBy { it.toString() }}:${link.dnsServers.sortedBy { it.hostAddress }}")
        }.sortedWith(compareByDescending<Triple<Network, Int, String>> { it.second }.thenBy { it.first.toString() })
        val selected = candidates.firstOrNull()
        return Snapshot(selected?.let { arrayOf(it.first) } ?: emptyArray(), selected?.third ?: "offline")
    }

    override fun close() {
        if (defaultRegistered) {
            defaultRegistered = false
            runCatching { manager.unregisterNetworkCallback(defaultCallback) }
        }
        if (registered) {
            registered = false
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
}

/** Repeated capability callbacks must not reset connections; only physical identity changes do. */
internal class NetworkChangeTracker {
    private var identity: String? = null
    private var stopped = false
    fun stop() { stopped = true }
    fun needsReconnect(next: String) = !stopped && identity != null && identity != next
    fun applied(next: String) { if (!stopped) identity = next }
    fun changed(next: String): Boolean {
        val reconnect = needsReconnect(next)
        applied(next)
        return reconnect
    }
}
