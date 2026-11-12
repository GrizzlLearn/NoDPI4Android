package com.gvcoder.nodpi.service

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** A visible activity is required for Android's VPN consent dialog, including starts from the tile. */
class VpnPermissionActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startService() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val request = VpnService.prepare(this)
            if (request == null) startService() else consent.launch(request)
        }
    }

    private fun startService() {
        val action = intent.getStringExtra("action") ?: ProxyService.ACTION_START
        ContextCompat.startForegroundService(this, ProxyService.intent(this, action))
        finish()
    }
}
