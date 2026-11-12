package com.gvcoder.nodpi.ui.home

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.ui.components.ItemGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RouteOverview(config: ProxyConfig, active: Boolean) {
    if (!config.vpnEnabled) return
    val context = LocalContext.current
    val external = if (config.upstream.enabled) config.upstreamPackages else emptySet()
    val local = if (config.vpnSelectedOnly) config.vpnPackages - external else emptySet()
    val packages = local + external
    val names by produceState<Map<String, String>>(emptyMap(), packages) {
        value = withContext(Dispatchers.IO) {
            packages.associateWith { name ->
                try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(name, 0).loadLabel(context.packageManager).toString()
                } catch (_: PackageManager.NameNotFoundException) { name }
            }
        }
    }
    ItemGroup(title = stringResource(if (active) R.string.routes_active else R.string.routes_configured), items = buildList<@Composable () -> Unit> {
        if (!config.vpnSelectedOnly) add {
            ListItem(headlineContent = { Text(stringResource(R.string.routes_other_apps)) }, supportingContent = { Text(stringResource(R.string.route_local)) })
        }
        for (name in packages.sorted()) add {
            ListItem(
                headlineContent = { Text(names[name] ?: name) },
                supportingContent = { Text(stringResource(if (name in external) R.string.upstream_title else R.string.route_local)) },
            )
        }
        add { Text(stringResource(R.string.routes_not_connectivity), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
    })
}
