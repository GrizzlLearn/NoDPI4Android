package com.gvcoder.nodpi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.Socks5Config
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.core.Socks5Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.Proxy
import java.net.InetSocketAddress

@Composable
internal fun UpstreamSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, chooseApps: () -> Unit) {
    var edit by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.upstream_title)) },
            supportingContent = { Text(stringResource(R.string.upstream_description)) },
            trailingContent = { Switch(settings.upstream.enabled, { enabled ->
                if (enabled) edit = true else update { it.copy(upstream = it.upstream.copy(enabled = false)) }
            }) },
        )
        if (settings.upstream.credentialsRequired) {
            Text(stringResource(R.string.settings_transfer_credentials_required), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        if (settings.upstream.host.isNotBlank()) {
            ListItem(
                headlineContent = { Text("${settings.upstream.host}:${settings.upstream.port}") },
                supportingContent = { Text(stringResource(R.string.upstream_edit)) },
                modifier = Modifier.clickable { edit = true },
            )
        }
        if (settings.upstream.enabled) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.upstream_apps, settings.upstreamPackages.size)) },
                supportingContent = { Text(settings.upstreamPackages.joinToString(", ").ifEmpty { stringResource(R.string.upstream_choose_required) }) },
                modifier = Modifier.clickable(onClick = chooseApps),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(enabled = !testing, onClick = {
                    testing = true
                    result = null
                    scope.launch {
                        result = withContext(Dispatchers.IO) {
                            runCatching {
                                settings.upstream.validate()
                                Socket(Proxy.NO_PROXY).use { socket ->
                                    socket.soTimeout = 8000
                                    socket.connect(InetSocketAddress(settings.upstream.host, settings.upstream.port), 8000)
                                    Socks5Client.connect(socket, settings.upstream, "telegram.org", 443)
                                }
                                "SOCKS5: TCP + AUTH + CONNECT telegram.org:443 OK"
                            }.getOrElse { "SOCKS5: ${it.javaClass.simpleName}: ${it.message}" }
                        }
                        testing = false
                    }
                }) { Text(stringResource(if (testing) R.string.upstream_testing else R.string.upstream_test)) }
            }
            result?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
    }
    if (edit) SocksEditor(settings.upstream, { upstream ->
        update { it.copy(upstream = upstream) }
        edit = false
    }, { edit = false })
}

@Composable
private fun SocksEditor(initial: Socks5Config, save: (Socks5Config) -> Unit, close: () -> Unit) {
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.upstream_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it.trim() }, label = { Text(stringResource(R.string.upstream_host)) }, singleLine = true)
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.upstream_port)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.upstream_username)) }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.upstream_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text(stringResource(R.string.upstream_password_storage))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val value = Socks5Config(true, host, port.toIntOrNull() ?: 0, username, password)
            val failure = runCatching { value.validate() }.exceptionOrNull()
            if (failure == null) save(value) else error = failure.message
        }) { Text(stringResource(R.string.vpn_save)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.vpn_cancel)) } },
    )
}
