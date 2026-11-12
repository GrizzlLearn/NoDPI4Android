package com.gvcoder.nodpi.core

import android.net.ConnectivityManager
import android.net.VpnService
import android.content.pm.PackageManager
import android.os.Process
import com.gvcoder.nodpi.service.ProxyController
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/** Called at original TUN flow creation, before the engine replaces its source socket. */
object NativeRouting {
    private data class Routing(val manager: ConnectivityManager, val externalUids: Set<Int>, val externalPort: Int, val names: Map<Int, String>) { val policy = AppRoutePolicy(externalUids, externalPort) }
    @Volatile private var routing: Routing? = null
    @Volatile private var dnsPort: Int = 0
    private val lastUnknown = AtomicLong()

    fun configure(service: VpnService, config: ProxyConfig, externalPort: Int, dnsBridgePort: Int = 0) {
        dnsPort = dnsBridgePort
        if (!config.upstream.enabled) { routing = null; return }
        config.upstream.validate()
        require(externalPort in 1..65535) { "SOCKS5 bridge is unavailable" }
        require(config.upstreamPackages.isNotEmpty()) { "Choose applications for external SOCKS5" }
        val names = mutableMapOf<Int, String>()
        for (name in config.upstreamPackages) {
            try {
                @Suppress("DEPRECATION")
                val info = service.packageManager.getApplicationInfo(name, 0)
                require(info.uid != Process.myUid()) { "NoDPI itself cannot use its own SOCKS5 route" }
                val siblings = service.packageManager.getPackagesForUid(info.uid).orEmpty().toSet()
                require(config.upstreamPackages.containsAll(siblings)) {
                    "SOCKS5: shared Android UID; select all packages in this group: ${siblings.joinToString()}"
                }
                names[info.uid] = name
            } catch (_: PackageManager.NameNotFoundException) { }
        }
        require(names.isNotEmpty()) { "No installed applications selected for external SOCKS5" }
        routing = Routing(service.getSystemService(ConnectivityManager::class.java), names.keys, externalPort, names)
    }

    fun clear() { routing = null; dnsPort = 0 }

    @JvmStatic
    fun selectPort(protocol: Int, localAddress: String, localPort: Int, remoteAddress: String, remotePort: Int): Int {
        if (remoteAddress == "198.18.0.2" && remotePort == 53) return dnsPort
        val active = routing ?: return 0
        if (android.os.Build.VERSION.SDK_INT < 29) return -1
        return try {
            val uid = active.manager.getConnectionOwnerUid(protocol,
                InetSocketAddress(InetAddress.getByName(localAddress), localPort),
                InetSocketAddress(InetAddress.getByName(remoteAddress), remotePort))
            if (uid < 0) {
                val now = System.currentTimeMillis()
                if (now - lastUnknown.getAndSet(now) > 5000) ProxyController.log.error("VPN", "ROUTE", "OWNER_UNAVAILABLE: flow blocked; no direct fallback")
                -1
            } else if (active.policy.portForUid(uid) > 0) {
                ProxyController.log.info(active.names[uid] ?: "uid=$uid", "ROUTE", "EXTERNAL_SOCKS5; protocol=$protocol; remotePort=$remotePort")
                active.externalPort
            } else 0
        } catch (error: Exception) {
            ProxyController.log.error("VPN", "ROUTE", "OWNER_LOOKUP_FAILED: ${error.javaClass.simpleName}; flow blocked")
            -1
        }
    }
}
