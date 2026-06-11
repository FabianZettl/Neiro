package dev.neiro.desktop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.neiro.desktop.data.repository.LastFmRepository
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    lastFmRepository: LastFmRepository = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // ── Server ──────────────────────────────────────────────────────────
        SettingsSection("Server") {
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("https://navidrome.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Bitrate
            val bitrateOptions = listOf(0 to "Original", 320 to "320 kbps", 256 to "256 kbps",
                192 to "192 kbps", 128 to "128 kbps")
            Text("Streaming Quality", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bitrateOptions.forEach { (bitrate, label) ->
                    FilterChip(
                        selected = state.streamingBitrate == bitrate,
                        onClick = { viewModel.onBitrateChange(bitrate) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = viewModel::connectAndTest) {
                    Text("Save & Test Connection")
                }
                when (val cs = state.connectionState) {
                    is ConnectionState.Testing -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    is ConnectionState.Success -> Text("✓ Connected (v${cs.serverVersion})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                    is ConnectionState.Error -> Text("✗ ${cs.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    else -> {}
                }
            }
        }

        // ── Last.fm ──────────────────────────────────────────────────────────
        SettingsSection("Last.fm") {
            if (state.lastFmSessionKey.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connected as ${state.lastFmUsername}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = viewModel::disconnectLastFm) {
                        Text("Disconnect")
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.lastFmUsername,
                    onValueChange = viewModel::onLastFmUsernameChange,
                    label = { Text("Last.fm Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.lastFmApiKey,
                    onValueChange = viewModel::onLastFmApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.lastFmApiSecret,
                    onValueChange = viewModel::onLastFmApiSecretChange,
                    label = { Text("API Secret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.lastFmPassword,
                    onValueChange = viewModel::onLastFmPasswordChange,
                    label = { Text("Last.fm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.connectToLastFm(lastFmRepository) }) {
                        Text("Connect")
                    }
                    when (val cs = state.lastFmAuthState) {
                        is ConnectionState.Testing -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        is ConnectionState.Success -> Text("✓ Authenticated",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                        is ConnectionState.Error -> Text("✗ ${cs.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        else -> {}
                    }
                }
            }
        }

        // ── Sync Code ────────────────────────────────────────────────────────
        if (state.serverUrl.isNotBlank() && state.username.isNotBlank()) {
            SettingsSection("Sync with Android App") {
                Text(
                    "Generate a sync code to quickly connect your Android app to the same server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.syncCode.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = state.syncCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                Button(onClick = viewModel::generateSyncCode) {
                    Text(if (state.syncCode.isBlank()) "Generate Sync Code" else "Regenerate")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
