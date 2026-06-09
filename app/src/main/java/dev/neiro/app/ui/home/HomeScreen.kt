package dev.neiro.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.api.models.ArtistDto
import dev.neiro.app.data.api.models.PlaylistDto

@Composable
fun HomeScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    onOpenSettings = { navController.navigate("settings") }
                )
            }

            state.error is HomeError.ApiError -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Could not load albums",
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
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // ── Page header ──────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                            IconButton(onClick = { navController.navigate("manage_sections") }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Customize",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── Dynamic sections ─────────────────────────────────
                    if (state.sections.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "No sections enabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { navController.navigate("manage_sections") }) {
                                    Text("Customize Home")
                                }
                            }
                        }
                    }

                    state.sections.forEach { sectionContent ->
                        if (sectionContent.items.isEmpty()) return@forEach

                        // Section header
                        item(key = "header_${sectionContent.config.id}") {
                            SectionHeader(
                                category = "LIBRARY",
                                title = sectionContent.config.title
                            )
                        }

                        when (val items = sectionContent.items) {
                            is SectionItems.Albums -> when (sectionContent.config.layout) {
                                SectionLayout.SHELF -> when (sectionContent.config.itemSize) {
                                    ItemSize.LARGE -> item(key = "hero_${sectionContent.config.id}") {
                                        HeroShelfRow(
                                            albums = items.items,
                                            itemShape = sectionContent.config.itemShape,
                                            onAlbumClick = { navController.navigate("album/${it.id}") }
                                        )
                                    }
                                    else -> item(key = "shelf_${sectionContent.config.id}") {
                                        ShelfRow(
                                            albums = items.items,
                                            itemShape = sectionContent.config.itemShape,
                                            itemSize = sectionContent.config.itemSize,
                                            onAlbumClick = { navController.navigate("album/${it.id}") }
                                        )
                                    }
                                }

                                SectionLayout.GRID -> {
                                    val cols = when (sectionContent.config.itemSize) { ItemSize.SMALL -> 3; ItemSize.LARGE -> 1; else -> 2 }
                                    val rows = items.items.chunked(cols)
                                    items(rows, key = { "grid_${sectionContent.config.id}_${it.first().id}" }) { row ->
                                        GridRow(
                                            row = row,
                                            cols = cols,
                                            itemShape = sectionContent.config.itemShape,
                                            onAlbumClick = { navController.navigate("album/${it.id}") }
                                        )
                                    }
                                }

                                SectionLayout.LIST -> {
                                    items(
                                        items.items,
                                        key = { "list_${sectionContent.config.id}_${it.id}" }
                                    ) { album ->
                                        AlbumListRow(
                                            album = album,
                                            itemShape = sectionContent.config.itemShape,
                                            onAlbumClick = { navController.navigate("album/${album.id}") }
                                        )
                                    }
                                }
                            }

                            is SectionItems.Playlists -> when (sectionContent.config.layout) {
                                SectionLayout.SHELF -> item(key = "shelf_${sectionContent.config.id}") {
                                    PlaylistShelfRow(
                                        playlists = items.items,
                                        itemShape = sectionContent.config.itemShape,
                                        itemSize = sectionContent.config.itemSize,
                                        onPlaylistClick = { pl -> navController.navigate("playlist/${pl.id}") }
                                    )
                                }

                                SectionLayout.GRID -> {
                                    val cols = when (sectionContent.config.itemSize) { ItemSize.SMALL -> 3; ItemSize.LARGE -> 1; else -> 2 }
                                    val rows = items.items.chunked(cols)
                                    items(rows, key = { "grid_${sectionContent.config.id}_${it.first().id}" }) { row ->
                                        PlaylistGridRow(
                                            row = row,
                                            cols = cols,
                                            itemShape = sectionContent.config.itemShape,
                                            onPlaylistClick = { pl -> navController.navigate("playlist/${pl.id}") }
                                        )
                                    }
                                }

                                SectionLayout.LIST -> {
                                    items(
                                        items.items,
                                        key = { "list_${sectionContent.config.id}_${it.id}" }
                                    ) { playlist ->
                                        PlaylistListRow(
                                            playlist = playlist,
                                            itemShape = sectionContent.config.itemShape,
                                            onPlaylistClick = { pl -> navController.navigate("playlist/${pl.id}") }
                                        )
                                    }
                                }
                            }

                            is SectionItems.Artists -> when (sectionContent.config.layout) {
                                SectionLayout.SHELF -> item(key = "shelf_${sectionContent.config.id}") {
                                    ArtistShelfRow(
                                        artists = items.items,
                                        itemShape = sectionContent.config.itemShape,
                                        itemSize = sectionContent.config.itemSize,
                                        onArtistClick = { navController.navigate("artist/${it.id}") }
                                    )
                                }

                                SectionLayout.GRID -> {
                                    val cols = when (sectionContent.config.itemSize) { ItemSize.SMALL -> 3; ItemSize.LARGE -> 1; else -> 2 }
                                    val rows = items.items.chunked(cols)
                                    items(rows, key = { "grid_${sectionContent.config.id}_${it.first().id}" }) { row ->
                                        ArtistGridRow(
                                            row = row,
                                            cols = cols,
                                            itemShape = sectionContent.config.itemShape,
                                            onArtistClick = { navController.navigate("artist/${it.id}") }
                                        )
                                    }
                                }

                                SectionLayout.LIST -> {
                                    items(
                                        items.items,
                                        key = { "list_${sectionContent.config.id}_${it.id}" }
                                    ) { artist ->
                                        ArtistListRow(
                                            artist = artist,
                                            itemShape = sectionContent.config.itemShape,
                                            onArtistClick = { navController.navigate("artist/${artist.id}") }
                                        )
                                    }
                                }
                            }

                            is SectionItems.LastFmTopArtists -> item(key = "lfm_artists_${sectionContent.config.id}") {
                                LastFmArtistShelfRow(
                                    artists = items.items,
                                    itemShape = sectionContent.config.itemShape,
                                    itemSize = sectionContent.config.itemSize,
                                    onArtistClick = { subsonicId -> navController.navigate("artist/$subsonicId") }
                                )
                            }

                            is SectionItems.LastFmTopAlbums -> item(key = "lfm_albums_${sectionContent.config.id}") {
                                LastFmAlbumShelfRow(
                                    albums = items.items,
                                    itemShape = sectionContent.config.itemShape,
                                    itemSize = sectionContent.config.itemSize,
                                    onAlbumClick = { subsonicId -> navController.navigate("album/$subsonicId") }
                                )
                            }

                            is SectionItems.LastFmTopTracks -> item(key = "lfm_tracks_${sectionContent.config.id}") {
                                LastFmTopTracksList(
                                    tracks = items.items,
                                    onTrackClick = { track -> viewModel.playTopTrack(track) }
                                )
                            }

                            is SectionItems.Genres -> item(key = "genres_${sectionContent.config.id}") {
                                GenreShelfRow(genres = items.items)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Item shape helpers ────────────────────────────────────────────────────────

private fun ItemShape.clipShape(): Shape = when (this) {
    ItemShape.SQUARE   -> RoundedCornerShape(0.dp)
    ItemShape.ROUNDED  -> RoundedCornerShape(10.dp)
    ItemShape.CIRCLE   -> CircleShape
    ItemShape.PORTRAIT -> RoundedCornerShape(10.dp)
    ItemShape.WIDE     -> RoundedCornerShape(10.dp)
}

private fun ItemShape.artAspectRatio(): Float = when (this) {
    ItemShape.PORTRAIT -> 0.75f
    ItemShape.WIDE     -> 16f / 9f
    else               -> 1f
}

// ── Item size helpers ─────────────────────────────────────────────────────────

private fun ItemSize.shelfCardWidth(): Dp = when (this) {
    ItemSize.SMALL  -> 90.dp
    ItemSize.MEDIUM -> 160.dp
    ItemSize.LARGE  -> 0.dp  // hero mode, full-width — not used directly
}

private fun ItemSize.gridMinCellSize(): Dp = when (this) {
    ItemSize.SMALL  -> 100.dp
    ItemSize.MEDIUM -> 160.dp
    ItemSize.LARGE  -> 400.dp  // forces single column
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(category: String, title: String) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 12.dp)
    ) {
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

// ── SHELF layout — Albums ─────────────────────────────────────────────────────

@Composable
private fun ShelfRow(
    albums: List<AlbumDto>,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onAlbumClick: (AlbumDto) -> Unit
) {
    val spacing = if (itemSize == ItemSize.SMALL) 8.dp else 12.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(albums, key = { it.id }) { album ->
            ShelfCard(album = album, itemShape = itemShape, itemSize = itemSize, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun ShelfCard(
    album: AlbumDto,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(itemSize.shelfCardWidth())
            .clickable(onClick = onClick)
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
                    if (isNotEmpty()) append(" • ")
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

// ── HERO layout — Albums (LARGE size) ────────────────────────────────────────

@Composable
private fun HeroShelfRow(
    albums: List<AlbumDto>,
    itemShape: ItemShape = ItemShape.ROUNDED,
    onAlbumClick: (AlbumDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .aspectRatio(1f)
                    .clip(itemShape.clipShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onAlbumClick(album) }
            ) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient overlay + text at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(album.artist, album.year?.toString()).joinToString("  •  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── GRID layout — Albums ──────────────────────────────────────────────────────

@Composable
private fun GridRow(row: List<AlbumDto>, cols: Int = 2, itemShape: ItemShape = ItemShape.ROUNDED, onAlbumClick: (AlbumDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        row.forEach { album ->
            Column(
                modifier = Modifier
                    .weight(1f)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Fill empty cell if odd number
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

// ── LIST layout — Albums ──────────────────────────────────────────────────────

@Composable
private fun AlbumListRow(album: AlbumDto, itemShape: ItemShape = ItemShape.ROUNDED, onAlbumClick: (AlbumDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAlbumClick(album) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(itemShape.clipShape())
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!album.artist.isNullOrBlank()) {
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = listOfNotNull(
                album.year?.takeIf { it > 0 }?.toString(),
                album.genre?.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── SHELF layout — Playlists ──────────────────────────────────────────────────

@Composable
private fun PlaylistShelfRow(
    playlists: List<PlaylistDto>,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onPlaylistClick: (PlaylistDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (itemSize == ItemSize.SMALL) 8.dp else 12.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistShelfCard(playlist = playlist, itemShape = itemShape, itemSize = itemSize, onClick = { onPlaylistClick(playlist) })
        }
    }
}

@Composable
private fun PlaylistShelfCard(
    playlist: PlaylistDto,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(itemSize.shelfCardWidth())
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(itemShape.artAspectRatio())
                .clip(itemShape.clipShape())
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
            text = "${playlist.songCount} Songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── GRID layout — Playlists ───────────────────────────────────────────────────

@Composable
private fun PlaylistGridRow(row: List<PlaylistDto>, cols: Int = 2, itemShape: ItemShape = ItemShape.ROUNDED, onPlaylistClick: (PlaylistDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        row.forEach { playlist ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlaylistClick(playlist) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(itemShape.artAspectRatio())
                        .clip(itemShape.clipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} Songs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

// ── LIST layout — Playlists ───────────────────────────────────────────────────

@Composable
private fun PlaylistListRow(playlist: PlaylistDto, itemShape: ItemShape = ItemShape.ROUNDED, onPlaylistClick: (PlaylistDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlaylistClick(playlist) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(itemShape.clipShape())
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.songCount} Songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── SHELF layout — Artists ────────────────────────────────────────────────────

@Composable
private fun ArtistShelfRow(
    artists: List<ArtistDto>,
    itemShape: ItemShape = ItemShape.CIRCLE,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onArtistClick: (ArtistDto) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (itemSize == ItemSize.SMALL) 8.dp else 12.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            ArtistShelfCard(artist = artist, itemShape = itemShape, itemSize = itemSize, onClick = { onArtistClick(artist) })
        }
    }
}

@Composable
private fun ArtistShelfCard(
    artist: ArtistDto,
    itemShape: ItemShape = ItemShape.CIRCLE,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(itemSize.shelfCardWidth())
            .clickable(onClick = onClick),
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

// ── GRID layout — Artists ─────────────────────────────────────────────────────

@Composable
private fun ArtistGridRow(row: List<ArtistDto>, cols: Int = 2, itemShape: ItemShape = ItemShape.CIRCLE, onArtistClick: (ArtistDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        row.forEach { artist ->
            Column(
                modifier = Modifier
                    .weight(1f)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

// ── LIST layout — Artists ─────────────────────────────────────────────────────

@Composable
private fun ArtistListRow(artist: ArtistDto, itemShape: ItemShape = ItemShape.CIRCLE, onArtistClick: (ArtistDto) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onArtistClick(artist) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.coverArtUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(itemShape.clipShape())
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.albumCount} Albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── SHELF layout — Last.fm Artists ───────────────────────────────────────────

@Composable
private fun LastFmArtistShelfRow(
    artists: List<LastFmMatchedArtist>,
    itemShape: ItemShape = ItemShape.CIRCLE,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onArtistClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (itemSize == ItemSize.SMALL) 8.dp else 12.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                modifier = Modifier
                    .width(itemSize.shelfCardWidth())
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
                        .clip(itemShape.clipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (artist.subsonicId != null)
                        MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── SHELF layout — Last.fm Albums ─────────────────────────────────────────────

@Composable
private fun LastFmAlbumShelfRow(
    albums: List<LastFmMatchedAlbum>,
    itemShape: ItemShape = ItemShape.ROUNDED,
    itemSize: ItemSize = ItemSize.MEDIUM,
    onAlbumClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (itemSize == ItemSize.SMALL) 8.dp else 12.dp)
    ) {
        items(albums, key = { "${it.name}_${it.artistName}" }) { album ->
            Column(
                modifier = Modifier
                    .width(itemSize.shelfCardWidth())
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
                        .clip(itemShape.clipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (album.subsonicId != null)
                        MaterialTheme.colorScheme.onBackground
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Last.fm Top Tracks list ───────────────────────────────────────────────────

@Composable
private fun LastFmTopTracksList(
    tracks: List<LastFmMatchedTrack>,
    onTrackClick: (LastFmMatchedTrack) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        tracks.forEachIndexed { index, track ->
            val isMatched = track.subsonicId != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isMatched) Modifier.clickable { onTrackClick(track) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                AsyncImage(
                    model = track.coverArtUrl,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMatched) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${track.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Genres shelf ──────────────────────────────────────────────────────────────

@Composable
private fun GenreShelfRow(genres: List<GenreItem>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
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

// ── Error / empty states ──────────────────────────────────────────────────────

@Composable
private fun NoServerPlaceholder(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            "No server configured",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your Navidrome / Subsonic server address in Settings and tap Save & Connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) { Text("Open Settings") }
    }
}
