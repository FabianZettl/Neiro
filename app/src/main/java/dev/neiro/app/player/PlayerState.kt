package dev.neiro.app.player

import dev.neiro.app.data.api.models.SongDto

enum class RepeatMode { OFF, ONE, ALL }

data class PlayerState(
    val currentSong: SongDto? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<SongDto> = emptyList(),
    val queueIndex: Int = 0,
    val isConnected: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val error: String? = null
)
