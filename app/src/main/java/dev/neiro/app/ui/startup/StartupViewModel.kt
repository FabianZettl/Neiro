package dev.neiro.app.ui.startup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.prefs.NieroPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val preferences: NieroPreferences
) : ViewModel() {
    suspend fun isServerConfigured(): Boolean =
        preferences.prefsFlow.first().serverUrl.isNotBlank()
}
