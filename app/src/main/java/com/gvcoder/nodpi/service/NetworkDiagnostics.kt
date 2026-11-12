package com.gvcoder.nodpi.service

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address

/** Snapshot on failure only. No SSID, subscriber identity or active reachability probe. */
internal fun networkDiagnostics(manager: ConnectivityManager): String = runCatching {
    val active = manager.activeNetwork
    val network = (listOfNotNull(active) + manager.allNetworks).distinct().firstOrNull {
        manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
    } ?: return@runCatching "network=NONE; validated=false"
    val capabilities = manager.getNetworkCapabilities(network)
    val properties = manager.getLinkProperties(network)
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "MOBILE"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
        else -> "OTHER"
    }
    val routes = properties?.routes.orEmpty()
    val ipv4 = routes.any { it.destination.prefixLength == 0 && it.destination.address is Inet4Address }
    val ipv6 = routes.any { it.destination.prefixLength == 0 && it.destination.address is Inet6Address }
    "network=$transport; validated=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}; ipv4Default=$ipv4; ipv6Default=$ipv6"
}.getOrDefault("network=UNKNOWN")
