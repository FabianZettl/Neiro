package dev.neiro.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neiro.app.ui.theme.ThemeMode

private val BITRATE_OPTIONS = listOf(
    0 to "Original (no limit)",
    320 to "320 kbps",
    256 to "256 kbps",
    192 to "192 kbps",
    128 to "128 kbps"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var bitrateMenuExpanded by remember { mutableStateOf(false) }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding)
    ) {
        // ── Page header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ── SERVER ────────────────────────────────────────────────────────
        SectionHeader("Server")

        AnimatedVisibility(
            visible = state.connectionState !is ConnectionState.Idle,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConnectionBanner(state.connectionState, modifier = Modifier.padding(bottom = 12.dp))
        }

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://music.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::connectAndTest,
            enabled = state.connectionState !is ConnectionState.Testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.connectionState is ConnectionState.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(if (state.connectionState is ConnectionState.Testing) "Connecting…" else "Save & Connect")
        }

        SettingsDivider()

        // ── PLAYBACK ──────────────────────────────────────────────────────
        SectionHeader("Playback")

        val selectedLabel = BITRATE_OPTIONS.find { it.first == state.streamingBitrate }?.second
            ?: "Original (no limit)"

        ExposedDropdownMenuBox(
            expanded = bitrateMenuExpanded,
            onExpandedChange = { bitrateMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Streaming Quality") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bitrateMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = bitrateMenuExpanded,
                onDismissRequest = { bitrateMenuExpanded = false }
            ) {
                BITRATE_OPTIONS.forEach { (bitrate, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.onBitrateChange(bitrate)
                            bitrateMenuExpanded = false
                        }
                    )
                }
            }
        }

        SettingsDivider()

        // ── APPEARANCE ────────────────────────────────────────────────────
        SectionHeader("Appearance")

        Text(
            "Theme",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
                .forEach { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.onThemeModeChange(mode) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
        }

        Spacer(Modifier.height(16.dp))

        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic Color", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Colors adapt to now-playing album art",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
            Switch(checked = state.dynamicColor, onCheckedChange = viewModel::onDynamicColorChange)
        }

        AnimatedVisibility(visible = !state.dynamicColor) {
            val accentColors = listOf(
                "#E5484D" to "Red",
                "#0A84FF" to "Blue",
                "#BF5AF2" to "Purple",
                "#30D158" to "Green",
                "#FF9F0A" to "Orange",
                "#FF375F" to "Pink",
                "#5AC8FA" to "Teal",
                "#FFFFFF" to "White"
            )
            Column {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Accent Color",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    accentColors.forEach { (hex, name) ->
                        val color = runCatching {
                            Color(android.graphics.Color.parseColor(hex))
                        }.getOrElse { Color.Red }
                        val selected = state.accentColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { viewModel.onAccentColorChange(hex) }
                                .then(
                                    if (selected)
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    else
                                        Modifier
                                )
                        )
                    }
                }
            }
        }

        SettingsDivider()

        // ── LAST.FM ───────────────────────────────────────────────────────
        SectionHeader("Last.fm")

        Text(
            "Shows play counts on artist/album pages, loved tracks in album view, " +
                    "and lets you love tracks from the player. " +
                    "Get your API key and secret at last.fm/api.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AnimatedVisibility(visible = state.lastFmAuthState !is ConnectionState.Idle) {
            ConnectionBanner(state.lastFmAuthState, modifier = Modifier.padding(bottom = 12.dp))
        }

        if (state.lastFmSessionKey.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Connected as ${state.lastFmUsername}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Session active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = viewModel::disconnectLastFm) {
                    Text("Disconnect")
                }
            }
        } else {
            OutlinedTextField(
                value = state.lastFmUsername,
                onValueChange = viewModel::onLastFmUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.lastFmApiKey,
                onValueChange = viewModel::onLastFmApiKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.lastFmApiSecret,
                onValueChange = viewModel::onLastFmApiSecretChange,
                label = { Text("API Secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.lastFmPassword,
                onValueChange = viewModel::onLastFmPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::connectToLastFm,
                enabled = state.lastFmAuthState !is ConnectionState.Testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.lastFmAuthState is ConnectionState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(if (state.lastFmAuthState is ConnectionState.Testing) "Connecting…" else "Connect to Last.fm")
            }
        }

        // ── SYNC WITH DESKTOP ─────────────────────────────────────────────
        if (state.serverUrl.isNotBlank() && state.username.isNotBlank()) {
            SettingsDivider()
            SectionHeader("Sync with Desktop App")

            Text(
                "Generate a sync code to connect the Neiro desktop app to this server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (state.syncCode.isNotBlank()) {
                val clipboardManager = LocalClipboardManager.current
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.syncCode,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(state.syncCode)) }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy sync code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = viewModel::generateSyncCode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.syncCode.isBlank()) "Generate Sync Code" else "Regenerate")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ConnectionBanner(state: ConnectionState, modifier: Modifier = Modifier) {
    val (icon, tint, text) = when (state) {
        is ConnectionState.Success -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "Connected — server v${state.serverVersion}"
        )
        is ConnectionState.Error -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            state.message
        )
        is ConnectionState.Testing -> return
        else -> return
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
