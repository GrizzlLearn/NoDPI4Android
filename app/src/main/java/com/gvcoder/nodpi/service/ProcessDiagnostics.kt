package com.gvcoder.nodpi.service

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import com.gvcoder.nodpi.core.ProxyLog

object ProcessDiagnostics {
    fun recordPreviousExit(app: Application, log: ProxyLog) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val preferences = app.getSharedPreferences("process_diagnostics", 0)
            val since = preferences.getLong("last_exit", 0)
            val manager = app.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, 5)
                .filter { it.timestamp > since }.sortedBy { it.timestamp }
            for (exit in exits) {
                val reason = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                    ApplicationExitInfo.REASON_ANR -> "ANR"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                    ApplicationExitInfo.REASON_SIGNALED -> "SIGNAL"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCES"
                    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "APP_UPDATED"
                    else -> "REASON_${exit.reason}"
                }
                log.error("PROCESS", "EXIT", "$reason; timestamp=${exit.timestamp}; status=${exit.status}; pssKb=${exit.pss}; rssKb=${exit.rss}; ${exit.description.orEmpty()}")
            }
            exits.lastOrNull()?.let { preferences.edit().putLong("last_exit", it.timestamp).apply() }
        }.onFailure { log.error("PROCESS", "EXIT", "Exit information unavailable: ${it.message}") }
    }
}
