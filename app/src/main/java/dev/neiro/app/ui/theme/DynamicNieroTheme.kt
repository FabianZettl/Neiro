package dev.neiro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    preferences: NieroPreferences
) : ViewModel() {
    val themeMode = preferences.themeModeFlow
        .map { runCatching { ThemeMode.valueOf(it) }.getOrElse { ThemeMode.DARK } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val dynamicColor = preferences.prefsFlow
        .map { it.dynamicColor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val accentColorHex = preferences.prefsFlow
        .map { it.accentColorHex }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "#E5484D")
}

/**
 * Root theme wrapper: observes the currently playing song's cover art
 * and animates the color scheme, while respecting the user's dark/light/system setting.
 */
@Composable
fun DynamicNieroTheme(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val playerState  by playerViewModel.playerState.collectAsStateWithLifecycle()
    val themeMode    by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by themeViewModel.dynamicColor.collectAsStateWithLifecycle()
    val accentHex    by themeViewModel.accentColorHex.collectAsStateWithLifecycle()
    val systemDark   = isSystemInDarkTheme()
    val context      = LocalContext.current

    var palette by remember { mutableStateOf(DefaultNeiroPalette) }
    LaunchedEffect(playerState.currentSong?.coverArtUrl) {
        if (dynamicColor) palette = extractPalette(context, playerState.currentSong?.coverArtUrl)
    }

    val effectivePalette = if (dynamicColor) {
        palette
    } else {
        val accent = runCatching { android.graphics.Color.parseColor(accentHex) }
            .map { Color(it) }.getOrElse { Color(0xFFE5484D) }
        DefaultNeiroPalette.copy(accent = accent)
    }

    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    NieroTheme(palette = effectivePalette, darkTheme = darkTheme, content = content)
}
