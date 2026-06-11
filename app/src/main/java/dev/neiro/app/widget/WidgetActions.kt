package dev.neiro.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.neiro.app.player.NieroPlayerHolder

class PlayPauseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val player = NieroPlayerHolder.player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }
}

class SkipNextWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        NieroPlayerHolder.player?.seekToNextMediaItem()
    }
}
