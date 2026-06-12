package dev.neiro.app.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.api.models.ArtistInfoDto
import dev.neiro.app.data.api.models.ArtistWithAlbumsDto
import dev.neiro.app.data.api.models.LastFmArtistInfo
import dev.neiro.app.data.api.models.LastFmTopTrack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistDetailScreen(
    navController: NavController,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.load() }) { Text("Retry") }
            }
            state.artist != null -> {
                ArtistDetailContent(
                    artist = state.artist!!,
                    artistInfo = state.artistInfo,
                    lastFmInfo = state.lastFmInfo,
                    topTracks = state.topTracks,
                    onBackClick = { navController.popBackStack() },
                    onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    onTrackClick = { track -> viewModel.playTopTrack(track) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistDetailContent(
    artist: ArtistWithAlbumsDto,
    artistInfo: ArtistInfoDto,
    lastFmInfo: LastFmArtistInfo?,
    topTracks: List<LastFmTopTrack>,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (LastFmTopTrack) -> Unit
) {
    val albums = artist.album.orEmpty()
    // Prefer Last.fm tags; fall back to ID3 genres from album metadata
    val tags = lastFmInfo?.tags?.tags?.map { it.name }?.take(6)
        ?.takeIf { it.isNotEmpty() }
        ?: albums.mapNotNull { it.genre }.distinct()
    val uriHandler = LocalUriHandler.current

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {

        // ── Item 1: Header ────────────────────────────────────────────────────
        item {
            ArtistHeader(
                artist = artist,
                artistInfo = artistInfo,
                onBackClick = onBackClick
            )
        }

        // ── Item 2: Genre/tag chips ───────────────────────────────────────────
        if (tags.isNotEmpty()) {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }

        // ── Item 3: Last.fm stats ─────────────────────────────────────────────
        if (lastFmInfo != null) {
            item {
                val userPlays = lastFmInfo.stats.userPlayCount.toLongOrNull() ?: 0L
                val globalPlays = lastFmInfo.stats.playCount.toLongOrNull() ?: 0L
                val listeners = lastFmInfo.stats.listeners.toLongOrNull() ?: 0L
                if (userPlays > 0 || globalPlays > 0 || listeners > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (userPlays > 0) {
                            Column {
                                Text(
                                    text = formatArtistPlayCount(userPlays),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Your scrobbles",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (listeners > 0) {
                            Column {
                                Text(
                                    text = formatArtistPlayCount(listeners),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Listeners",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (globalPlays > 0) {
                            Column {
                                Text(
                                    text = formatArtistPlayCount(globalPlays),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Scrobbles",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Item 4: Biography ─────────────────────────────────────────────────
        if (!artistInfo.biography.isNullOrBlank()) {
            item {
                BiographySection(biography = artistInfo.biography)
            }
        }

        // ── Item 4: External links ────────────────────────────────────────────
        item {
            val encodedName = java.net.URLEncoder.encode(artist.name, "UTF-8")
            val links = buildList {
                artistInfo.lastFmUrl?.takeIf { it.isNotBlank() }?.let {
                    add("Last.fm" to it)
                } ?: add("Last.fm" to "https://www.last.fm/music/$encodedName")
                add("Wikipedia" to "https://en.wikipedia.org/wiki/Special:Search?search=$encodedName")
                add("RateYourMusic" to "https://rateyourmusic.com/search?searchterm=$encodedName&searchtype=a")
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    links.forEach { (label, url) ->
                        OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label)
                        }
                    }
                }
            }
        }

        // ── Item 5: Top tracks ────────────────────────────────────────────────
        if (topTracks.isNotEmpty()) {
            item {
                Text(
                    "Top Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            item {
                // 2 columns × 5 rows
                val rows = topTracks.chunked(2)
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { track ->
                                TopTrackCell(
                                    rank = topTracks.indexOf(track) + 1,
                                    track = track,
                                    onClick = { onTrackClick(track) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Item 6: Albums header ─────────────────────────────────────────────
        item {
            Text(
                text = "${albums.size} Album${if (albums.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
        }

        // ── Items 6+: Album grid rows (2 columns) ─────────────────────────────
        val rows = albums.chunked(2)
        items(rows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { album ->
                    Box(modifier = Modifier.weight(1f)) {
                        AlbumGridCell(
                            album = album,
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
                // Fill empty slot if row has only 1 item
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ArtistHeader(
    artist: ArtistWithAlbumsDto,
    artistInfo: ArtistInfoDto,
    onBackClick: () -> Unit
) {
    val imageUrl = artistInfo.largeImageUrl
        ?: artistInfo.mediumImageUrl
        ?: artistInfo.smallImageUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback: gradient box using artist cover art or plain gradient
            val fallbackUrl = artist.album?.firstOrNull()?.coverArtUrl
            if (fallbackUrl != null) {
                AsyncImage(
                    model = fallbackUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        // Gradient overlay: fade bottom into background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.15f),
                            1.0f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Artist name at bottom
        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun BiographySection(biography: String) {
    val cleanBio = remember(biography) {
        biography
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = cleanBio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Read more")
        }
    }
}

@Composable
private fun TopTrackCell(rank: Int, track: LastFmTopTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatArtistPlayCount(track.playCountLong)} plays",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatArtistPlayCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

@Composable
private fun AlbumGridCell(album: AlbumDto, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            album.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            album.genre?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
