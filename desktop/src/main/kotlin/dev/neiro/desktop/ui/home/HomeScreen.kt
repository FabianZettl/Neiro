package dev.neiro.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.neiro.desktop.data.api.models.AlbumDto
import dev.neiro.desktop.data.api.models.ArtistDto
import dev.neiro.desktop.data.api.models.PlaylistDto
import dev.neiro.desktop.ui.Screen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            state.error is HomeError.NoServer -> {
                NoServerPlaceholder(
                    modifier = Modifier.align(Alignment.Center),
                    onOpenSettings = { onNavigate(Screen.Settings) }
                )
            }

            state.error is HomeError.ApiError -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Could not load content",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (state.error as HomeError.ApiError).message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.loadContent() }) { Text("Retry") }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    state.sections.forEach { sectionContent ->
                        if (sectionContent.items.isEmpty()) return@forEach

                        item(key = "header_${sectionContent.config.id}") {
                            val categoryLabel = when (sectionContent.config.dataSource) {
                                DataSource.LASTFM -> "LAST.FM \u00b7 ${sectionContent.config.contentType.displayName.uppercase()}"
                                DataSource.SUBSONIC -> sectionContent.config.contentType.displayName.uppercase()
                            }
                            SectionHeader(category = categoryLabel, title = sectionContent.config.title)
                        }

                        when (val items = sectionContent.items) {
                            is SectionItems.Albums -> item(key = "section_${sectionContent.config.id}") {
                                AlbumShelf(
                                    albums = items.items,
                                    itemShape = sectionContent.config.itemShape,
                                    itemSize = sectionContent.config.itemSize,
                                    onAlbumClick = { onNavigate(Screen.AlbumDetail(it.id)) }
                                )
                            }

                            is SectionItems.Artists -> item(key = "section_${sectionContent.config.id}") {
                                ArtistShelf(
                                    artists = items.items,
                                    itemShape = sectionContent.config.itemShape,
                                    itemSize = sectionContent.config.itemSize,
                                    onArtistClick = { onNavigate(Screen.ArtistDetail(it.id)) }
                                )
                            }

                            is SectionItems.Playlists -> item(key = "section_${sectionContent.config.id}") {
                                PlaylistShelf(
                                    playlists = items.items,
                                    onPlaylistClick = {}
                                )
                            }

                            is SectionItems.LastFmTopAlbums -> item(key = "section_${sectionContent.config.id}") {
                                LastFmAlbumShelf(
                                    albums = items.items,
                                    onAlbumClick = { id -> onNavigate(Screen.AlbumDetail(id)) }
                                )
                            }

                            is SectionItems.LastFmTopArtists -> item(key = "section_${sectionContent.config.id}") {
                                LastFmArtistShelf(
                                    artists = items.items,
                                    onArtistClick = { id -> onNavigate(Screen.ArtistDetail(id)) }
                                )
                            }

                            is SectionItems.LastFmTopTracks -> item(key = "section_${sectionContent.config.id}") {
                                LastFmTracksRow(
                                    tracks = items.items,
                                    onTrackClick = { viewModel.playTopTrack(it) }
                                )
                            }

                            is SectionItems.Genres -> item(key = "section_${sectionContent.config.id}") {
                                GenreShelf(genres = items.items)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SectionHeader(category: String, title: String) {
    Column(modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp)) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun AlbumShelf(
    albums: List<AlbumDto>,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onAlbumClick: (AlbumDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .width(itemSize.cardWidth())
                    .clickable { onAlbumClick(album) }
            ) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(itemShape.artAspectRatio())
                        .clip(itemShape.clipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        album.artist?.let { append(it) }
                        if (album.year != null && album.year > 0) {
                            if (isNotEmpty()) append(" \u2022 ")
                            append(album.year)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistShelf(
    artists: List<ArtistDto>,
    itemShape: ItemShape = ItemShape.CIRCLE,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onArtistClick: (ArtistDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                modifier = Modifier
                    .width(itemSize.cardWidth())
                    .clickable { onArtistClick(artist) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = artist.coverArtUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(itemShape.artAspectRatio())
                        .clip(itemShape.clipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    playlists: List<PlaylistDto>,
    onPlaylistClick: (PlaylistDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Column(
                modifier = Modifier.width(160.dp).clickable { onPlaylistClick(playlist) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LastFmAlbumShelf(
    albums: List<LastFmMatchedAlbum>,
    onAlbumClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { "${it.name}_${it.artistName}" }) { album ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .then(
                        if (album.subsonicId != null)
                            Modifier.clickable { onAlbumClick(album.subsonicId) }
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (album.subsonicId != null) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LastFmArtistShelf(
    artists: List<LastFmMatchedArtist>,
    onArtistClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .then(
                        if (artist.subsonicId != null)
                            Modifier.clickable { onArtistClick(artist.subsonicId) }
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = artist.coverArtUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (artist.subsonicId != null) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LastFmTracksRow(
    tracks: List<LastFmMatchedTrack>,
    onTrackClick: (LastFmMatchedTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks, key = { "${it.name}_${it.artistName}" }) { track ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .then(
                        if (track.subsonicId != null)
                            Modifier.clickable { onTrackClick(track) }
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = track.coverArtUrl,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (track.subsonicId != null) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GenreShelf(genres: List<GenreItem>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(genres, key = { it.name }) { genre ->
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = genre.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${genre.albumCount} albums",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoServerPlaceholder(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "No server configured",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your Navidrome / Subsonic server address in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) { Text("Open Settings") }
    }
}
