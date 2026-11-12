package com.gvcoder.nodpi.service

import android.net.VpnService
import android.net.ConnectivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.AllDomainsBlacklist
import com.gvcoder.nodpi.core.AutoBlacklist
import com.gvcoder.nodpi.core.Blacklist
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainBlacklist
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.core.ProxyServer
import com.gvcoder.nodpi.core.ProxyStats
import com.gvcoder.nodpi.core.StatsSnapshot
import com.gvcoder.nodpi.data.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.BindException

/** Foreground service that owns the running [ProxyServer] */
class ProxyService : VpnService() {

    // All proxy start/stop operations run one at a time on this dispatcher
    private val control = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + control)

    private val resourceLock = Any()
    private var destroyed = false
    private var server: ProxyServer? = null
    @Volatile private var vpn: LocalVpn? = null
    private var statsJob: Job? = null
    private var networkMonitor: PhysicalNetworkMonitor? = null
    private var networkTracker: NetworkChangeTracker? = null
    private var reconnectJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onRevoke() {
        ProxyController.log.info("VPN", "LIFECYCLE", "VPN permission revoked or another VPN took over")
        scope.launch { shutdown(null) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { shutdown(null) }
                return START_NOT_STICKY
            }

            ACTION_RESTART -> {
                if (!promote()) return START_NOT_STICKY
                scope.launch {
                    try { AppGraph.settings.current().toProxyConfig().validateRoutes() }
                    catch (error: Exception) {
                        ProxyController.log.error("CONFIG", "APPLY", "Configuration rejected; running VPN retained: ${error.message}")
                        return@launch
                    }
                    stopProxy()
                    startProxy()
                }
            }

            // ACTION_START or a restart of the sticky service by the system (null intent)
            else -> {
                if (!promote()) return START_NOT_STICKY
                scope.launch {
                    if (server == null) startProxy()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ProxyController.log.info("SERVICE", "LIFECYCLE", "Service destroyed by Android")
        synchronized(resourceLock) {
            destroyed = true
            stopProxy()
        }
        scope.cancel()
        if (ProxyController.status.value !is ProxyStatus.Failed) {
            ProxyController.setStatus(ProxyStatus.Stopped)
        }
        super.onDestroy()
    }

    /** Puts the service into the foreground; false if the system refused */
    private fun promote(): Boolean {
        val running = ProxyController.status.value as? ProxyStatus.Running
        val notification = ProxyNotifications.build(this, running?.config?.address(), null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, ProxyNotifications.NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            ProxyController.setStatus(ProxyStatus.Failed(e.message ?: e.javaClass.simpleName))
            stopSelf()
            false
        }
    }

    private suspend fun startProxy() {
        ProxyController.setStatus(ProxyStatus.Starting)

        val config = AppGraph.settings.current().toProxyConfig()
        ProxyController.log.info("CONFIG", "START", "vpn=${config.vpnEnabled}; selectedOnly=${config.vpnSelectedOnly}; apps=${config.vpnPackages.size}; mode=${config.blacklistMode}; doh=${config.dohEnabled}; ipv4Only=${config.ipv4Only}")
        val blacklistRepository = AppGraph.blacklist
        val revision = blacklistRepository.revision.value
        val blacklist: Blacklist = when (config.blacklistMode) {
            BlacklistMode.STANDARD -> DomainBlacklist(blacklistRepository.domains.value, config.domainMatching)
            BlacklistMode.ALL -> AllDomainsBlacklist
            BlacklistMode.AUTO -> AutoBlacklist(
                known = blacklistRepository.domains.value,
                matching = config.domainMatching,
                onDetected = blacklistRepository::addDetected,
                probe = { com.gvcoder.nodpi.core.TlsProbe.isHandshakeTimingOut(it, com.gvcoder.nodpi.core.DomainResolver(config.dohEnabled)) },
            )
        }

        val stats = ProxyStats()
        val proxy = ProxyServer(config, blacklist, stats, ProxyController.log) {
            networkDiagnostics(getSystemService(ConnectivityManager::class.java))
        }
        try {
            synchronized(resourceLock) {
                if (destroyed) return
                proxy.start()
                server = proxy
                if (config.vpnEnabled) {
                    vpn = LocalVpn(this).also { it.start(config) }
                }
                ProxyController.setStats(StatsSnapshot())
                ProxyController.setStatus(ProxyStatus.Running(config, revision))
            }
        } catch (e: Throwable) {
            proxy.stop()
            val message = if (e is BindException) {
                getString(R.string.error_address_in_use, config.address())
            } else {
                getString(R.string.error_start_failed, e.message ?: e.javaClass.simpleName)
            }
            shutdown(ProxyStatus.Failed(message))
            return
        }

        if (config.vpnEnabled) monitorPhysicalNetworks(proxy)
        val address = config.address()
        ProxyNotifications.update(this, ProxyNotifications.build(this, address, null))
        statsJob = scope.launch(Dispatchers.Default) {
            var ticks = 0
            while (isActive) {
                delay(1_000)
                if (config.vpnEnabled && vpn?.isRunning() != true) {
                    scope.launch { shutdown(ProxyStatus.Failed(getString(R.string.vpn_tunnel_stopped))) }
                    break
                }
                val snapshot = stats.tick()
                ProxyController.setStats(snapshot)
                if (++ticks % NOTIFICATION_UPDATE_TICKS == 0) {
                    ProxyNotifications.update(this@ProxyService, ProxyNotifications.build(this@ProxyService, address, snapshot))
                }
            }
        }
    }

    private fun monitorPhysicalNetworks(appliedServer: ProxyServer) {
        val tracker = NetworkChangeTracker()
        lateinit var monitor: PhysicalNetworkMonitor
        monitor = PhysicalNetworkMonitor(getSystemService(ConnectivityManager::class.java)) {
            synchronized(resourceLock) {
                if (destroyed || server !== appliedServer) return@PhysicalNetworkMonitor
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    repeat(3) { attempt ->
                        delay(if (attempt == 0) 750 else 2000)
                        try {
                            synchronized(resourceLock) {
                                if (destroyed || server !== appliedServer || networkMonitor !== monitor) return@launch
                                val snapshot = monitor.snapshot()
                                val reconnect = tracker.needsReconnect(snapshot.identity)
                                vpn?.onUnderlyingNetworkChanged(snapshot.networks, reconnect)
                                if (reconnect) {
                                    appliedServer.resetConnections()
                                    ProxyController.log.info("VPN", "NETWORK", if (snapshot.networks.isEmpty())
                                        "NETWORK_UNAVAILABLE; VPN retained; applied routes unchanged" else
                                        "NETWORK_CHANGED; connections reset; VPN and applied routes retained")
                                }
                                tracker.applied(snapshot.identity)
                            }
                            return@launch
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            ProxyController.log.error("VPN", "NETWORK", "NETWORK_UPDATE_FAILED; attempt=${attempt + 1}; VPN retained; ${error.javaClass.simpleName}")
                        }
                    }
                }
            }
        }
        synchronized(resourceLock) {
            if (destroyed || server !== appliedServer) return
            networkTracker = tracker
            networkMonitor = monitor
            try { monitor.start() } catch (error: Exception) {
                monitor.close()
                networkMonitor = null
                ProxyController.log.error("VPN", "NETWORK", "NETWORK_MONITOR_FAILED: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun stopProxy() = synchronized(resourceLock) {
        networkTracker?.stop()
        networkTracker = null
        networkMonitor?.close()
        networkMonitor = null
        reconnectJob?.cancel()
        reconnectJob = null
        vpn?.close()
        vpn = null
        statsJob?.cancel()
        statsJob = null
        server?.stop()
        server = null
    }

    private suspend fun shutdown(status: ProxyStatus.Failed?) {
        if (status != null) ProxyController.log.error("SERVICE", "STOP", status.message)
        else ProxyController.log.info("SERVICE", "STOP", "Stop requested or VPN revoked")
        stopProxy()
        ProxyController.setStatus(status ?: ProxyStatus.Stopped)
        withContext(Dispatchers.Main) {
            ServiceCompat.stopForeground(this@ProxyService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START = "com.gvcoder.nodpi.action.START"
        const val ACTION_STOP = "com.gvcoder.nodpi.action.STOP"
        const val ACTION_RESTART = "com.gvcoder.nodpi.action.RESTART"

        private const val NOTIFICATION_UPDATE_TICKS = 3

        fun intent(context: Context, action: String): Intent =
            Intent(context, ProxyService::class.java).setAction(action)
    }
}

fun ProxyConfig.address(): String = if (host.contains(':')) "[$host]:$port" else "$host:$port"
