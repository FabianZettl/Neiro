package dev.neiro.app.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.neiro.app.R
import java.io.File

class NieroWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

@Composable
@GlanceComposable
private fun WidgetContent() {
    val title     = currentState(NieroWidgetKeys.TITLE) ?: "Nothing playing"
    val artist    = currentState(NieroWidgetKeys.ARTIST) ?: ""
    val playing   = currentState(NieroWidgetKeys.IS_PLAYING) ?: false
    val coverPath = currentState(NieroWidgetKeys.COVER_PATH)

    val coverBitmap = coverPath?.let {
        val file = File(it)
        if (file.exists()) BitmapFactory.decodeFile(it) else null
    }

    GlanceTheme {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            Box(
                modifier = GlanceModifier
                    .size(72.dp)
                    .cornerRadius(10.dp)
                    .background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        provider = ImageProvider(coverBitmap),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(10.dp)
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_music),
                        contentDescription = "Music",
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(36.dp)
                    )
                }
            }

            Spacer(GlanceModifier.width(12.dp))

            // Track info + controls
            Column(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight()
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth()
                )
                if (artist.isNotBlank()) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = artist,
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Play/Pause
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .cornerRadius(20.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionRunCallback<PlayPauseWidgetAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(
                                if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                            ),
                            contentDescription = if (playing) "Pause" else "Play",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                            modifier = GlanceModifier.size(22.dp)
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    // Skip next
                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .clickable(actionRunCallback<SkipNextWidgetAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_skip_next),
                            contentDescription = "Skip next",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                            modifier = GlanceModifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
