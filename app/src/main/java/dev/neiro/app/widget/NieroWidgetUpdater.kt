package dev.neiro.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import coil.imageLoader
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NieroWidgetKeys {
    val TITLE     = stringPreferencesKey("widget_title")
    val ARTIST    = stringPreferencesKey("widget_artist")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val COVER_PATH = stringPreferencesKey("widget_cover_path")
}

/** Call this from PlayerController whenever the player state changes. */
suspend fun updateNieroWidget(
    context: Context,
    title: String,
    artist: String?,
    isPlaying: Boolean,
    coverArtUrl: String?
) {
    // Download cover art and save to file on IO thread
    val coverPath = if (coverArtUrl != null) {
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .size(256, 256)
                    .allowHardware(false)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val file = File(context.filesDir, "widget_cover.png")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 85, it) }
                    file.absolutePath
                } else null
            } catch (e: Exception) {
                null
            }
        }
    } else null

    // Update all Glance widget instances
    val manager = GlanceAppWidgetManager(context)
    val widget = NieroWidget()
    val glanceIds = manager.getGlanceIds(NieroWidget::class.java)
    glanceIds.forEach { glanceId ->
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[NieroWidgetKeys.TITLE] = title
                this[NieroWidgetKeys.ARTIST] = artist ?: ""
                this[NieroWidgetKeys.IS_PLAYING] = isPlaying
                if (coverPath != null) this[NieroWidgetKeys.COVER_PATH] = coverPath
                else remove(NieroWidgetKeys.COVER_PATH)
            }
        }
        widget.update(context, glanceId)
    }
}
