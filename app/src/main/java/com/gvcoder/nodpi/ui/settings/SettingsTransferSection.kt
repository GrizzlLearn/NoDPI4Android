package com.gvcoder.nodpi.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.data.SettingsTransfer
import com.gvcoder.nodpi.ui.components.ItemGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SettingsTransferSection(settings: AppSettings) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<SettingsTransfer.Imported?>(null) }
    var busy by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val text = SettingsTransfer.export(settings, AppGraph.blacklist.domains.value)
                    require(text.length <= 1_048_576) { "Settings file exceeds 1 MiB" }
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open destination" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                resources.getString(R.string.settings_transfer_exported)
            }.getOrElse { resources.getString(R.string.settings_transfer_failed, it.message.orEmpty()) }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open settings file" }
                        .use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (output.size() <= 1_048_576) {
                                val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    require(bytes.size <= 1_048_576) { "Settings file exceeds 1 MiB" }
                    SettingsTransfer.parse(bytes.toString(Charsets.UTF_8))
                }
            }.onSuccess { pending = it }
                .onFailure { message = resources.getString(R.string.settings_transfer_failed, it.message.orEmpty()) }
            busy = false
        }
    }
    ItemGroup(title = stringResource(R.string.settings_transfer_title), items = listOf<@Composable () -> Unit> {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_transfer_description))
            Row {
                TextButton(enabled = !busy, onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                    save.launch("nodpi-settings-$stamp.properties")
                }) { Text(stringResource(R.string.settings_transfer_export)) }
                TextButton(enabled = !busy, onClick = { open.launch(arrayOf("text/*", "application/octet-stream", "application/x-java-properties")) }) {
                    Text(stringResource(R.string.settings_transfer_import))
                }
            }
            message?.let { Text(it) }
        }
    })
    pending?.let { imported ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_transfer_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_transfer_confirm, imported.settings.capturedPackageCount(), imported.domains.size))
                    if (imported.settings.upstream.enabled) Text("SOCKS5: ${imported.settings.upstream.host}:${imported.settings.upstream.port}")
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    message = runCatching {
                        withContext(Dispatchers.IO) {
                            val previous = AppGraph.blacklist.domains.value
                            AppGraph.blacklist.replaceAll(imported.domains)
                            try { AppGraph.settings.update { imported.applyTo(it) } }
                            catch (error: Exception) {
                                runCatching { AppGraph.blacklist.replaceAll(previous) }.exceptionOrNull()?.let { error.addSuppressed(it) }
                                throw error
                            }
                        }
                        resources.getString(R.string.settings_transfer_imported)
                    }.getOrElse { resources.getString(R.string.settings_transfer_failed, it.message.orEmpty()) }
                    pending = null
                    busy = false
                }
            }) { Text(stringResource(R.string.settings_transfer_import)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(stringResource(R.string.vpn_cancel)) } },
        )
    }
}

private fun AppSettings.capturedPackageCount() = (vpnPackages + upstreamPackages).size
