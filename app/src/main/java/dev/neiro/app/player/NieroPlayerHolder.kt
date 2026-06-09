package dev.neiro.app.player

import androidx.media3.exoplayer.ExoPlayer

/**
 * Process-local singleton that holds the live ExoPlayer instance created by NieroMediaService.
 *
 * NieroMediaService runs in the same process as the UI — there is no IPC boundary.
 * Using MediaController.seekTo() sends the command through an async IPC round-trip
 * (MediaController → Binder → MediaSession → ExoPlayer) which can drop seek commands
 * on streams that don't report Content-Length (e.g. live-transcoded Navidrome streams),
 * because ExoPlayer marks those items as non-seekable until the duration is known.
 *
 * By calling ExoPlayer directly we skip the IPC layer entirely and the seek always reaches
 * ExoPlayer. ExoPlayer is main-thread-safe when called from the thread it was created on
 * (onCreate runs on main), and all callers invoke seekTo() from the main thread.
 */
object NieroPlayerHolder {
    @Volatile var player: ExoPlayer? = null
}
