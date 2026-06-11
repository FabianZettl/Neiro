package dev.neiro.desktop.ui

sealed class Screen {
    object Home : Screen()
    object Albums : Screen()
    object Artists : Screen()
    object Search : Screen()
    object Settings : Screen()
    data class AlbumDetail(val albumId: String) : Screen()
    data class ArtistDetail(val artistId: String) : Screen()
}
