package com.gvcoder.nodpi.service

import android.content.pm.PackageManager
import android.net.VpnService
import android.net.Network
import android.net.ConnectivityManager
import com.gvcoder.nodpi.core.ConnectionLimits
import com.gvcoder.nodpi.core.DnsForwarder
import com.gvcoder.nodpi.core.NativeRouting
import android.os.Build
import android.os.ParcelFileDescriptor
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.LocalSocksBridge
import com.gvcoder.nodpi.core.ProxyConfig
import hev.htproxy.TProxyService
import java.io.Closeable
import java.io.File

class LocalVpn(private val service: VpnService) : Closeable {
    private var tun: ParcelFileDescriptor? = null
    private var bridge: LocalSocksBridge? = null
    private var dnsBridge: LocalSocksBridge? = null
    private var externalBridge: LocalSocksBridge? = null
    private var nativeStarted = false

    fun start(config: ProxyConfig) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { service.getString(R.string.vpn_android_required) }
        check(VpnService.prepare(service) == null) { service.getString(R.string.vpn_permission_needed) }
        val builder = service.Builder()
            .setSession("NoDPI")
            .setMtu(1500)
            .addAddress("198.18.0.1", 32)
            .addAddress("fd00:6e6f:6470::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("198.18.0.2")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        if (config.vpnSelectedOnly) {
            val packages = config.capturedPackages - service.packageName
            require(packages.isNotEmpty()) { service.getString(R.string.vpn_select_required) }
            var count = 0
            for (name in packages) {
                try { builder.addAllowedApplication(name); count++ } catch (_: PackageManager.NameNotFoundException) { }
            }
            require(count > 0) { service.getString(R.string.vpn_select_required) }
        } else {
            builder.addDisallowedApplication(service.packageName)
        }
        try {
            val bridgePassword = java.util.UUID.randomUUID().toString()
            val sessionSlots = java.util.concurrent.Semaphore(ConnectionLimits.TRAFFIC_SESSIONS)
            val udpSlots = java.util.concurrent.Semaphore(ConnectionLimits.UDP_ASSOCIATIONS)
            val forwarder = DnsForwarder(service.getSystemService(ConnectivityManager::class.java), config.dohEnabled, config.ipv4Only)
            val socks = LocalSocksBridge(config, dnsQuery = forwarder::query, bridgePassword = bridgePassword, sessionSlots = sessionSlots, udpSlots = udpSlots).also { bridge = it; it.start() }
            val external = if (config.upstream.enabled) {
                LocalSocksBridge(config, dnsQuery = forwarder::query, external = true, bridgePassword = bridgePassword, sessionSlots = sessionSlots, udpSlots = udpSlots).also {
                    externalBridge = it
                    it.start()
                }
            } else null
            val dns = LocalSocksBridge(config, dnsQuery = forwarder::query, bridgePassword = bridgePassword,
                sessionSlots = java.util.concurrent.Semaphore(ConnectionLimits.DNS_SESSIONS), udpSlots = java.util.concurrent.Semaphore(ConnectionLimits.DNS_SESSIONS), bridgeName = "DNS").also {
                dnsBridge = it
                it.start()
            }
            NativeRouting.configure(service, config, external?.port ?: 0, dns.port)
            val file = File(service.filesDir, "vpn-tunnel.yml")
            file.writeText("""
                tunnel:
                  mtu: 1500
                  ipv4: 198.18.0.1
                  ipv6: 'fd00:6e6f:6470::1'
                socks5:
                  address: 127.0.0.1
                  port: ${socks.port}
                  udp: udp
                  username: nodpi
                  password: '$bridgePassword'
                misc:
                  task-stack-size: 86016
                  tcp-buffer-size: 65536
                  max-session-count: ${ConnectionLimits.NATIVE_SESSIONS}
                  connect-timeout: 15000
                  log-level: warn
            """.trimIndent())
            val descriptor = checkNotNull(builder.establish()) { service.getString(R.string.vpn_permission_needed) }
            tun = descriptor
            check(TProxyService.TProxyStartService(file.absolutePath, descriptor.fd)) { "Cannot start VPN tunnel" }
            nativeStarted = true
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /** Keep the TUN and applied routing rules installed across physical network changes. */
    fun onUnderlyingNetworkChanged(networks: Array<Network>, reconnect: Boolean) {
        check(service.setUnderlyingNetworks(networks)) { "Cannot update VPN underlying networks" }
        if (reconnect) {
            dnsBridge?.resetConnections()
        bridge?.resetConnections()
            externalBridge?.resetConnections()
        }
    }

    fun isRunning() = nativeStarted && TProxyService.TProxyIsRunning()

    override fun close() {
        if (nativeStarted) {
            TProxyService.TProxyStopService()
            nativeStarted = false
        }
        NativeRouting.clear()
        tun?.close()
        tun = null
        dnsBridge?.close()
        dnsBridge = null
        externalBridge?.close()
        externalBridge = null
        bridge?.close()
        bridge = null
    }
}
