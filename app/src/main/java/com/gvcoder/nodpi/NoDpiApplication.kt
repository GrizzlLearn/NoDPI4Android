package com.gvcoder.nodpi

import android.app.Application
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.service.ProcessDiagnostics
import com.gvcoder.nodpi.data.BlacklistRepository
import com.gvcoder.nodpi.data.SettingsRepository
import com.gvcoder.nodpi.service.ProxyNotifications

class NoDpiApplication : Application() {
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ProxyController.log.attachPersistence(File(noBackupFilesDir, "proxy-journal.bin"), diagnosticsScope)
        diagnosticsScope.launch { ProcessDiagnostics.recordPreviousExit(this@NoDpiApplication, ProxyController.log) }
        com.gvcoder.nodpi.core.JavaCrashRecorder.install(File(noBackupFilesDir, "java-crash.bin"))
        AppGraph.init(this)
        ProxyNotifications.createChannel(this)
    }
}

/** Application-wide singletons */
object AppGraph {
    lateinit var settings: SettingsRepository
        private set
    lateinit var blacklist: BlacklistRepository
        private set

    fun init(app: Application) {
        settings = SettingsRepository(app)
        blacklist = BlacklistRepository(app)
    }
}
