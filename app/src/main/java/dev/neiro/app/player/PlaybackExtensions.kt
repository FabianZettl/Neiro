package dev.neiro.app.player

import dev.neiro.app.data.api.models.SongDto

/** Play a queue starting at [startIndex]. No-op if songs is empty. */
suspend fun PlayerController.playQueue(songs: List<SongDto>, startIndex: Int = 0) {
    if (songs.isEmpty()) return
    val i = startIndex.coerceIn(songs.indices)
    playTrack(songs[i], songs, i)
}

/** Shuffle the list and start playback from the first shuffled item. No-op if songs is empty. */
suspend fun PlayerController.playShuffled(songs: List<SongDto>) {
    if (songs.isEmpty()) return
    val shuffled = songs.shuffled()
    playTrack(shuffled.first(), shuffled, 0)
}
