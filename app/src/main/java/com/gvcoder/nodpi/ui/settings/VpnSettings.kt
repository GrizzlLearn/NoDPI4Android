package com.gvcoder.nodpi.ui.settings

import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainMatching
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.ui.components.ItemGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VpnSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, applyNetwork: ((AppSettings) -> AppSettings) -> Unit) {
    var showApps by remember { mutableStateOf(false) }
    var showExternalApps by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedNames by produceState<List<String>>(emptyList(), settings.vpnPackages) {
        value = withContext(Dispatchers.IO) {
            settings.vpnPackages.map { name ->
                try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(name, 0).loadLabel(context.packageManager).toString()
                } catch (_: PackageManager.NameNotFoundException) { name }
            }.sorted()
        }
    }
    ItemGroup(
        title = stringResource(R.string.vpn_title),
        items = buildList<@Composable () -> Unit> {
            add {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.vpn_enabled)) },
                    supportingContent = { Text(stringResource(if (Build.VERSION.SDK_INT >= 29) R.string.vpn_description else R.string.vpn_android_required)) },
                    trailingContent = { Switch(settings.vpnEnabled, { enabled -> update { it.copy(vpnEnabled = enabled) } }, enabled = Build.VERSION.SDK_INT >= 29) },
                )
            }
            if (settings.vpnEnabled) {
                add {
                    ChoiceItem(
                        title = stringResource(R.string.vpn_scope),
                        description = stringResource(R.string.vpn_processing_description),
                        options = listOf(stringResource(R.string.vpn_all), stringResource(R.string.vpn_selected)),
                        selected = if (settings.vpnSelectedOnly) 1 else 0,
                        onSelect = { index -> update { it.copy(vpnSelectedOnly = index == 1) } },
                    )
                }
                if (settings.vpnSelectedOnly) {
                    add {
                        ListItem(
                            headlineContent = { Text(stringResource(if (settings.upstream.enabled) R.string.vpn_apps_local_count else R.string.vpn_apps_count, settings.vpnPackages.size)) },
                            supportingContent = {
                                if (settings.vpnPackages.isEmpty()) {
                                    Text(stringResource(if (settings.upstream.enabled && settings.upstreamPackages.isNotEmpty()) R.string.vpn_external_only else R.string.vpn_select_required), color = if (settings.upstream.enabled && settings.upstreamPackages.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                                } else {
                                    Text(selectedNames.joinToString(", "))
                                }
                            },
                            modifier = Modifier.clickable { showApps = true },
                        )
                    }
                }
            } else {
                add {
                    ChoiceItem(
                        title = stringResource(R.string.settings_mode),
                        description = stringResource(when (settings.blacklistMode) {
                            BlacklistMode.STANDARD -> R.string.settings_mode_standard_desc
                            BlacklistMode.ALL -> R.string.settings_mode_all_desc
                            BlacklistMode.AUTO -> R.string.settings_mode_auto_desc
                        }),
                        options = listOf(stringResource(R.string.settings_mode_standard), stringResource(R.string.settings_mode_all), stringResource(R.string.settings_mode_auto)),
                        selected = settings.blacklistMode.ordinal,
                        onSelect = { index -> update { it.copy(blacklistMode = BlacklistMode.entries[index]) } },
                    )
                }
                add {
                    ChoiceItem(
                        title = stringResource(R.string.settings_matching),
                        description = stringResource(if (settings.domainMatching == DomainMatching.STRICT) R.string.settings_matching_strict_desc else R.string.settings_matching_loose_desc),
                        options = listOf(stringResource(R.string.settings_matching_strict), stringResource(R.string.settings_matching_loose)),
                        selected = settings.domainMatching.ordinal,
                        onSelect = { index -> update { it.copy(domainMatching = DomainMatching.entries[index]) } },
                    )
                }
            }
            if (settings.vpnEnabled) add {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.vpn_ipv4_only)) },
                    supportingContent = { Text(stringResource(R.string.vpn_ipv4_only_description)) },
                    trailingContent = { Switch(settings.ipv4Only, { enabled -> update { it.copy(ipv4Only = enabled) } }) },
                )
            }
            if (settings.vpnEnabled) add { UpstreamSettings(settings, update, { showExternalApps = true }) }
            add {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.doh_title)) },
                    supportingContent = { Text(stringResource(R.string.doh_description)) },
                    trailingContent = { Switch(settings.dohEnabled, { enabled -> update { it.copy(dohEnabled = enabled) } }) },
                )
            }
            add { DiagnosticsSettings(settings) }
            add {
                SwitchItem(
                    icon = Icons.Rounded.Shuffle,
                    title = stringResource(R.string.settings_disorder),
                    description = stringResource(R.string.settings_disorder_desc),
                    checked = settings.disorder,
                    onCheckedChange = { enabled -> update { it.copy(disorder = enabled) } },
                )
            }
        },
    )
    if (showExternalApps) AppPicker(settings.upstreamPackages, { packages -> applyNetwork { it.copy(upstreamPackages = packages) } }, { showExternalApps = false })
    if (showApps) AppPicker(settings.vpnPackages, { packages -> applyNetwork { it.copy(vpnPackages = packages) } }, { showApps = false })
}

private data class AppChoice(val name: String, val packageName: String)

@Composable
private fun AppPicker(selected: Set<String>, onSave: (Set<String>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf<List<AppChoice>?>(null) }
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        choices = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps = pm.queryIntentActivities(intent, 0).map {
                AppChoice(it.loadLabel(pm).toString(), it.activityInfo.packageName)
            }.filter { it.packageName != context.packageName }.distinctBy { it.packageName }
            val missing = selected - apps.map { it.packageName }.toSet()
            (apps + missing.map { name ->
                val label = try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(name, 0).loadLabel(pm).toString()
                } catch (_: PackageManager.NameNotFoundException) { name }
                AppChoice(label, name)
            }).sortedBy { it.name.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.vpn_apps_title)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.vpn_apps_search)) }, singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (choices == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    items(choices.orEmpty().filter { it.name.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.name) },
                            supportingContent = { Text(app.packageName) },
                            trailingContent = { Checkbox(app.packageName in picked, null) },
                            modifier = Modifier.toggleable(value = app.packageName in picked, role = Role.Checkbox) { checked ->
                                picked = if (checked) picked + app.packageName else picked - app.packageName
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { onSave(picked); onClose() }) { Text(stringResource(R.string.action_save_apply)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.vpn_cancel)) } },
    )
}
