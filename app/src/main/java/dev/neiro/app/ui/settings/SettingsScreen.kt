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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.OpenInBrowser
import dev.neiro.app.BuildConfig
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
    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrScanScreen(
            onScanned = { url ->
                showQrScanner = false
                viewModel.onDesktopQrScanned(url)
            },
            onBack = { showQrScanner = false }
        )
        return
    }
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
                        val selected = state.accentColorHex.equals(hex, ignoreCase = true)
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

                Spacer(Modifier.height(14.dp))

                val isPreset = accentColors.any { it.first.equals(state.accentColorHex, ignoreCase = true) }
                var hexInput by remember(state.accentColorHex) {
                    mutableStateOf(if (isPreset) "" else state.accentColorHex.removePrefix("#"))
                }
                var hexError by remember { mutableStateOf(false) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previewColor = runCatching {
                        Color(android.graphics.Color.parseColor(state.accentColorHex))
                    }.getOrElse { Color.Red }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val cleaned = input.removePrefix("#").take(6)
                                .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                            hexInput = cleaned.uppercase()
                            if (cleaned.length == 6) {
                                hexError = false
                                viewModel.onAccentColorChange("#$cleaned")
                            } else {
                                hexError = cleaned.isNotEmpty()
                            }
                        },
                        label = { Text("Hex") },
                        prefix = { Text("#") },
                        isError = hexError,
                        singleLine = true,
                        modifier = Modifier.width(160.dp)
                    )
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
                label = { Text("Last.fm Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.lastFmPassword,
                onValueChange = viewModel::onLastFmPasswordChange,
                label = { Text("Last.fm Password") },
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
            SectionHeader("Connect Desktop App")

            Text(
                "Open the Neiro Desktop app, go to setup, and switch to \"Scan with Neiro App\". " +
                "Then scan the QR code shown on your desktop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AnimatedVisibility(visible = state.desktopSyncState !is ConnectionState.Idle) {
                ConnectionBanner(state.desktopSyncState, modifier = Modifier.padding(bottom = 12.dp))
            }

            Button(
                onClick = { showQrScanner = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan Desktop QR Code")
            }
        }

        // ── ABOUT ─────────────────────────────────────────────────────────────
        SettingsDivider()
        SectionHeader("About")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Version", style = MaterialTheme.typography.bodyMedium)
            Text(
                BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))

        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/FabianZettl/Neiro") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("GitHub — Source Code")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Privacy",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Neiro does not collect or transmit any personal data to third parties. " +
            "All music data is streamed directly from your own server. " +
            "If you connect a Last.fm account, your username is sent to last.fm " +
            "only to fetch your personal statistics and enable scrobbling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Last.fm",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Last.fm features powered by Audioscrobbler. Data provided by Last.fm Ltd.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { uriHandler.openUri("https://www.last.fm") },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("last.fm", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "License",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "MIT License — free to use, modify and distribute.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
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
