package dev.neiro.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import dev.neiro.app.ui.theme.DefaultNeiroPalette
import dev.neiro.app.ui.theme.NieroTheme
import dev.neiro.app.ui.theme.ThemeMode
import dev.neiro.app.ui.theme.ThemeViewModel
import dev.neiro.app.ui.theme.extractPalette
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.neiro.app.data.api.models.PlaylistDto
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.ui.components.AddToPlaylistDialog
import dev.neiro.app.ui.player.PlayerViewModel
import dev.neiro.app.ui.playlists.PlaylistActionViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    playlistActionViewModel: PlaylistActionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastFm = state.lastFmInfo
    val lovedKeys = state.lovedTrackKeys
    val playlists by playlistActionViewModel.playlists.collectAsStateWithLifecycle()
    // Scoped to just (currentSongId, isPlaying) — playerState itself ticks every 500ms during
    // playback (position/duration), and collecting the whole object here would recompose every
    // visible TrackRow on each tick instead of only when the playing track actually changes.
    val nowPlaying by remember { playerViewModel.playerState.map { it.currentSong?.id to it.isPlaying }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null to false)

    val context = LocalContext.current
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }
    var albumPalette by remember { mutableStateOf(DefaultNeiroPalette) }
    LaunchedEffect(state.album?.coverArtUrl, darkTheme) {
        state.album?.coverArtUrl?.let { url ->
            albumPalette = extractPalette(context, url, darkTheme)
        }
    }

    NieroTheme(palette = albumPalette, darkTheme = darkTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            actions = {
                val album = state.album
                if (album != null) {
                    val isStarred = album.starred != null
                    IconButton(onClick = { viewModel.toggleStar() }) {
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isStarred) "Unstar" else "Star",
                            tint = if (isStarred) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.album != null -> {
                val album = state.album!!
                val bottomPadding = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 88.dp

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {

                    // ── Compact header ─────────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AsyncImage(
                                model = album.coverArtUrl,
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = album.artist ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        if (!album.artistId.isNullOrBlank()) navController.navigate("artist/${album.artistId}")
                                    }
                                )
                                album.year?.let {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (lastFm != null) {
                                    val userPlays = lastFm.userPlayCount.toLongOrNull() ?: 0L
                                    if (userPlays > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "${formatPlayCount(userPlays)} plays",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Play + Shuffle row ─────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.playTrackAtIndex(0) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.shufflePlay() },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // ── LastFM genre tags ──────────────────────────────────────
                    val albumTags = lastFm?.tags?.tags?.map { it.name }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOfNotNull(album.genre?.takeIf { it.isNotBlank() })
                    if (albumTags.isNotEmpty()) {
                        item {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                albumTags.take(6).forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Track list ─────────────────────────────────────────────
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    itemsIndexed(album.song, key = { _, song -> song.id }) { index, song ->
                        val isLoved = lovedKeys.contains(
                            "${song.title.lowercase()}_${(song.artist ?: "").lowercase()}"
                        )
                        val isCurrentSong = nowPlaying.first == song.id
                        TrackRow(
                            song = song,
                            trackNumber = song.track ?: (index + 1),
                            isLoved = isLoved,
                            isCurrentSong = isCurrentSong,
                            isPlaying = isCurrentSong && nowPlaying.second,
                            onClick = { viewModel.playTrackAtIndex(index) },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onAddToQueue = { playerViewModel.addToQueue(song) },
                            playlists = playlists,
                            onAddToPlaylist = { playlistId ->
                                playlistActionViewModel.addToPlaylist(playlistId, listOf(song.id))
                            },
                            onCreateAndAddToPlaylist = { name ->
                                playlistActionViewModel.createAndAddToPlaylist(name, listOf(song.id))
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Failed to load album", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    } // NieroTheme
}

@Composable
private fun TrackRow(
    song: SongDto,
    trackNumber: Int,
    isLoved: Boolean = false,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    playlists: List<PlaylistDto> = emptyList(),
    onAddToPlaylist: (playlistId: String) -> Unit = {},
    onCreateAndAddToPlaylist: (name: String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number / now-playing indicator
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrentSong) {
                if (isPlaying) {
                    EqualizerBars(color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!song.artist.isNullOrBlank()) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Last.fm loved indicator
        if (isLoved) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Loved on Last.fm",
                tint = Color(0xFFE5484D),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(13.dp)
            )
        }

        // Duration
        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )

        // Context menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    onClick = { menuExpanded = false; onPlayNext() }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    onClick = { menuExpanded = false; onAddToQueue() }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    onClick = { menuExpanded = false; showPlaylistDialog = true }
                )
            }
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onAddToPlaylist = onAddToPlaylist,
            onCreateNewPlaylist = onCreateAndAddToPlaylist
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatPlayCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

@Composable
private fun EqualizerBars(color: Color, barWidth: Dp = 2.5.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val delays = listOf(0, 200, 100)
    val heights = delays.map { delay ->
        transition.animateFloat(
            initialValue = 0.2f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar"
        )
    }
    Canvas(modifier = modifier.size(width = 14.dp, height = 14.dp)) {
        val totalWidth = size.width
        val barPx = barWidth.toPx()
        val gap = (totalWidth - 3 * barPx) / 2
        heights.forEachIndexed { i, h ->
            val barHeight = size.height * h.value
            val x = i * (barPx + gap)
            drawRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barPx, barHeight)
            )
        }
    }
}
