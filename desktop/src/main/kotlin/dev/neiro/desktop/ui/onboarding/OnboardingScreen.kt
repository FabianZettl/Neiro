package dev.neiro.desktop.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.data.prefs.NieroPrefs
import dev.neiro.desktop.sync.SyncCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    preferences: DesktopPreferences,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var syncCode by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Neiro",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "音色",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Music streaming for Navidrome",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sync Code") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual Setup") }
                )
            }

            Spacer(Modifier.height(24.dp))

            if (selectedTab == 0) {
                // Sync Code tab
                Text(
                    text = "Enter the sync code from your Android Neiro app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = syncCode,
                    onValueChange = { syncCode = it; errorMessage = "" },
                    label = { Text("Sync Code") },
                    placeholder = { Text("Paste your sync code here") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val decoded = SyncCode.decode(syncCode.trim())
                        if (decoded == null) {
                            errorMessage = "Invalid sync code. Please check and try again."
                        } else {
                            scope.launch {
                                val prefs = preferences.prefsFlow.first().copy(
                                    serverUrl = decoded.serverUrl,
                                    username = decoded.username,
                                    password = decoded.password,
                                    homeSectionsJson = decoded.sectionsJson
                                )
                                preferences.savePrefs(prefs)
                                onComplete()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect with Sync Code")
                }
            } else {
                // Manual setup tab
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; errorMessage = "" },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://music.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = "" },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = "" },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (serverUrl.isBlank() || username.isBlank()) {
                            errorMessage = "Server URL and username are required."
                            return@Button
                        }
                        scope.launch {
                            val prefs = preferences.prefsFlow.first().copy(
                                serverUrl = serverUrl.trimEnd('/'),
                                username = username,
                                password = password
                            )
                            preferences.savePrefs(prefs)
                            onComplete()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Connect")
                }
            }

            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
