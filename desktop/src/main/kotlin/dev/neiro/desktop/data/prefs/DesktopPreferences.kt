package dev.neiro.desktop.data.prefs

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class NieroPrefs(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val streamingBitrate: Int = 0,
    val dynamicColor: Boolean = true,
    val accentColorHex: String = "#E5484D",
    val lastFmUsername: String = "",
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmSessionKey: String = "",
    val homeSectionsJson: String? = null
)

class DesktopPreferences {

    private val configDir = File(System.getProperty("user.home"), ".config/neiro")
    private val prefsFile = File(configDir, "preferences.json")
    private val gson = Gson()

    private val _prefsFlow = MutableStateFlow(loadFromFile())
    val prefsFlow: StateFlow<NieroPrefs> = _prefsFlow.asStateFlow()

    private fun loadFromFile(): NieroPrefs {
        return try {
            if (prefsFile.exists()) {
                gson.fromJson(prefsFile.readText(), NieroPrefs::class.java) ?: NieroPrefs()
            } else {
                NieroPrefs()
            }
        } catch (e: Exception) {
            NieroPrefs()
        }
    }

    suspend fun savePrefs(prefs: NieroPrefs) {
        try {
            configDir.mkdirs()
            prefsFile.writeText(gson.toJson(prefs))
        } catch (e: Exception) {
            // ignore write errors
        }
        _prefsFlow.value = prefs
    }

    fun savePrefsSync(prefs: NieroPrefs) {
        try {
            configDir.mkdirs()
            prefsFile.writeText(gson.toJson(prefs))
        } catch (e: Exception) {
            // ignore write errors
        }
        _prefsFlow.value = prefs
    }
}
