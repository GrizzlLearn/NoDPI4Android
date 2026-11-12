package com.gvcoder.nodpi.service

import android.content.Intent
import android.net.VpnService
import com.gvcoder.nodpi.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import androidx.core.content.ContextCompat
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.core.ProxyLog
import com.gvcoder.nodpi.core.StatsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProxyStatus {
    data object Stopped : ProxyStatus
    data object Starting : ProxyStatus

    /** [blacklistRevision] is the blacklist revision the proxy was started with */
    data class Running(val config: ProxyConfig, val blacklistRevision: Int) : ProxyStatus
    data class Failed(val message: String) : ProxyStatus
}

/** State of the proxy shared between the service, the UI and the tile */
object ProxyController {

    private val _status = MutableStateFlow<ProxyStatus>(ProxyStatus.Stopped)
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    val log = ProxyLog()

    val isActive: Boolean
        get() = status.value.let { it is ProxyStatus.Running || it is ProxyStatus.Starting }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context) = requestStart(context, ProxyService.ACTION_START)

    private fun requestStart(context: Context, action: String) {
        val application = context.applicationContext
        scope.launch {
            try {
                val settings = AppGraph.settings.current()
                if (settings.vpnEnabled && VpnService.prepare(application) != null) {
                    application.startActivity(Intent(application, VpnPermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("action", action))
                } else {
                    ContextCompat.startForegroundService(application, ProxyService.intent(application, action))
                }
            } catch (error: Exception) {
                setStatus(ProxyStatus.Failed(error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun restart(context: Context) {
        requestStart(context, ProxyService.ACTION_RESTART)
    }

    fun stop(context: Context) {
        if (!isActive) {
            return
        }
        runCatching { context.startService(ProxyService.intent(context, ProxyService.ACTION_STOP)) }
    }

    fun toggle(context: Context) {
        if (isActive) stop(context) else start(context)
    }

    internal fun setStatus(status: ProxyStatus) {
        _status.value = status
    }

    internal fun setStats(stats: StatsSnapshot) {
        _stats.value = stats
    }
}
