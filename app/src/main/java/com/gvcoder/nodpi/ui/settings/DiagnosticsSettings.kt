package com.gvcoder.nodpi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.NetworkProbe
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.service.ProxyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DiagnosticsSettings(settings: AppSettings) {
    var domain by remember { mutableStateOf("www.instagram.com") }
    var external by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.diagnostics_description))
        OutlinedTextField(domain, { domain = it.trim() }, label = { Text(stringResource(R.string.diagnostics_domain)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (settings.upstream.enabled) ChoiceItem(
            title = stringResource(R.string.diagnostics_route), description = null,
            options = listOf(stringResource(R.string.route_local), stringResource(R.string.upstream_title)),
            selected = if (external) 1 else 0, onSelect = { external = it == 1 },
        )
        Button(enabled = !running && domain.isNotBlank(), onClick = {
            running = true
            result = ""
            val config = (ProxyController.status.value as? ProxyStatus.Running)?.config ?: settings.toProxyConfig()
            val viaExternal = external && settings.upstream.enabled
            val target = domain
            scope.launch {
                result = withContext(Dispatchers.IO) { runCatching { NetworkProbe.check(target, config, viaExternal) }.getOrElse { it.message.orEmpty() } }
                running = false
            }
        }) { Text(stringResource(if (running) R.string.upstream_testing else R.string.diagnostics_run)) }
        if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodySmall)
    }
}
