package dev.neiro.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.neiro.app.data.api.models.StructuredLyrics
import dev.neiro.app.data.repository.DesktopState
import dev.neiro.app.ui.player.shareNowPlayingCard
import dev.neiro.app.player.RepeatMode
import dev.neiro.app.ui.components.AddToPlaylistDialog
import dev.neiro.app.ui.playlists.PlaylistActionViewModel
import dev.neiro.app.ui.theme.DefaultNeiroPalette
import dev.neiro.app.ui.theme.NieroTheme
import dev.neiro.app.ui.theme.extractPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayer(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel(),
    playlistActionViewModel: PlaylistActionViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val lastFm by viewModel.lastFmState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val playlists by playlistActionViewModel.playlists.collectAsStateWithLifecycle()
    val isRemoteMode by viewModel.isRemoteMode.collectAsStateWithLifecycle()
    val desktopState by viewModel.desktopState.collectAsStateWithLifecycle()
    val desktopCoverArtUrl by viewModel.desktopCoverArtUrl.collectAsStateWithLifecycle()
    val ds = (desktopState as? DesktopState.Playing)?.song

    val song = state.currentSong ?: run {
        navController.popBackStack()
        return
    }

    // In remote mode, display desktop song info instead of local
    val displayTitle      = if (isRemoteMode && ds != null) ds.title      else song.title
    val displayArtist     = if (isRemoteMode && ds != null) ds.artist      else song.artist ?: ""
    val displayAlbum      = if (isRemoteMode && ds != null) ds.album       else song.album ?: ""
    val displayCoverUrl   = if (isRemoteMode && ds != null) desktopCoverArtUrl ?: song.coverArtUrl else song.coverArtUrl
    val displayDurationMs = if (isRemoteMode && ds != null) ds.durationMs  else state.durationMs
    val displayPositionMs = if (isRemoteMode && ds != null) ds.positionMs  else state.positionMs
    val displayIsPlaying  = if (isRemoteMode && ds != null) ds.isPlaying   else state.isPlaying
    val displaySongKey    = if (isRemoteMode && ds != null) ds.songId      else song.id
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Tick every second so the sleep timer countdown stays fresh
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickMs = System.currentTimeMillis()
        }
    }

    val sleepRemainingMs = state.sleepTimerEndMs?.let { it - tickMs }?.coerceAtLeast(0L)
    val sleepRemainingText = sleepRemainingMs?.let {
        val mins = (it / 60000).toInt()
        val secs = ((it % 60000) / 1000).toInt()
        if (mins > 0) "${mins}m" else "${secs}s"
    }

    val context = LocalContext.current
    var palette by remember { mutableStateOf(DefaultNeiroPalette) }
    LaunchedEffect(displayCoverUrl) {
        if (displayCoverUrl != null) palette = extractPalette(context, displayCoverUrl)
    }

    val progress = if (displayDurationMs > 0)
        (displayPositionMs.toFloat() / displayDurationMs).coerceIn(0f, 1f)
    else 0f

    val scope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) {
        if (!isSeeking) seekValue = progress
    }

    val artScale by animateFloatAsState(
        targetValue = if (displayIsPlaying) 1.0f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artScale"
    )

    var showQueue by remember { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NieroTheme(palette = palette, darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Layer 1: Blurred album art background ──────────────────
            AsyncImage(
                model = displayCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radiusX = 40.dp, radiusY = 40.dp)
            )

            // ── Layer 2: Dark scrim overlay ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            // ── Layer 3: Foreground UI ─────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Top bar ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = if (isRemoteMode) "DESKTOP FERNSTEUERUNG" else "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRemoteMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
                    )
                    if (lyrics != null) {
                        IconButton(onClick = { viewModel.toggleLyrics() }) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Lyrics",
                                tint = if (showLyrics) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    // Sleep timer button
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (state.sleepTimerEndMs != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                            if (sleepRemainingText != null) {
                                Text(
                                    text = sleepRemainingText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (state.queue.size > 1) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = {
                                    showMoreMenu = false
                                    playlistActionViewModel.loadPlaylists()
                                    showPlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Now Playing") },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch { shareNowPlayingCard(context, song) }
                                }
                            )
                        }
                    }
                }

                // ── Album art — animated crossfade on song change ──────
                AnimatedContent(
                    targetState = displaySongKey,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "albumArtCrossfade"
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(artScale)
                            .shadow(
                                elevation = 40.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = Color.Black.copy(alpha = 0.6f),
                                spotColor = Color.Black.copy(alpha = 0.8f)
                            )
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = displayCoverUrl,
                            contentDescription = displayAlbum,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                // ── Bottom glass card ──────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {

                    // ── Song info row ──────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (displayArtist.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = displayArtist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = !isRemoteMode && song.artistId != null) {
                                        if (!isRemoteMode) song.artistId?.let { navController.navigate("artist/$it") }
                                    }
                                )
                            }
                            if (displayAlbum.isNotBlank()) {
                                Text(
                                    text = displayAlbum,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = !isRemoteMode && song.albumId != null) {
                                        if (!isRemoteMode) song.albumId?.let { navController.navigate("album/$it") }
                                    }
                                )
                            }
                            // ── Audio quality badges ───────────────────
                            val formatLabel = when (song.suffix?.lowercase()) {
                                "flac"       -> "FLAC"
                                "opus"       -> "OPUS"
                                "ogg", "oga" -> "OGG"
                                "aac", "m4a" -> "AAC"
                                "wav"        -> "WAV"
                                "wma"        -> "WMA"
                                "mp3"        -> null  // too common to show
                                else         -> song.suffix?.uppercase()
                            }
                            val bitrateLabel = song.bitRate?.takeIf { it > 0 }
                                ?.let { if (it >= 1000) "${it / 1000}.${(it % 1000) / 100}M" else "${it}k" }
                            if (formatLabel != null || bitrateLabel != null) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (formatLabel != null) {
                                        QualityBadge(
                                            label = formatLabel,
                                            highlight = formatLabel == "FLAC" || formatLabel == "WAV"
                                        )
                                    }
                                    if (bitrateLabel != null) {
                                        QualityBadge(label = bitrateLabel)
                                    }
                                }
                            }
                            val userPlays = lastFm.info?.userPlayCountLong ?: 0L
                            if (userPlays > 0) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$userPlays scrobbles",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                        if (lastFm.hasSession) {
                            IconButton(onClick = { viewModel.toggleLove() }) {
                                Icon(
                                    imageVector = if (lastFm.isLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (lastFm.isLoved) "Unlove" else "Love",
                                    tint = if (lastFm.isLoved) Color(0xFFE5484D) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Seek bar ───────────────────────────────────────
                    if (state.isLiveStream) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFFE5484D),
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE5484D).copy(alpha = 0.15f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = seekValue,
                                onValueChange = { v ->
                                    seekJob?.cancel()
                                    isSeeking = true
                                    seekValue = v
                                },
                                onValueChangeFinished = {
                                    viewModel.seekTo((seekValue * displayDurationMs).toLong())
                                    seekJob = scope.launch {
                                        delay(600)
                                        isSeeking = false
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMs(if (isSeeking) (seekValue * displayDurationMs).toLong() else displayPositionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = formatMs(displayDurationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Playback controls ──────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Skip previous
                        IconButton(
                            onClick = { viewModel.skipPrev() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Play / Pause
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    imageVector = if (displayIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (displayIsPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        // Skip next
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Repeat — cycles OFF → ALL → ONE
                        IconButton(onClick = { viewModel.cycleRepeat() }) {
                            val (icon, tint) = when (state.repeatMode) {
                                RepeatMode.OFF -> Icons.Default.Repeat to Color.White.copy(alpha = 0.5f)
                                RepeatMode.ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                                RepeatMode.ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                icon,
                                contentDescription = "Repeat",
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // ── Queue info ─────────────────────────────────────
                    if (state.queue.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Track ${state.queueIndex + 1} of ${state.queue.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // ── Error display ──────────────────────────────────
                    state.error?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // ── Layer 4: Lyrics overlay ────────────────────────────────
            if (showLyrics && lyrics != null) {
                LyricsOverlay(
                    lyrics = lyrics!!,
                    positionMs = state.positionMs,
                    onDismiss = { viewModel.toggleLyrics() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Add to Playlist dialog ─────────────────────────────────────
        if (showPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showPlaylistDialog = false },
                onAddToPlaylist = { playlistId ->
                    playlistActionViewModel.addToPlaylist(playlistId, listOf(song.id))
                },
                onCreateNewPlaylist = { name ->
                    playlistActionViewModel.createAndAddToPlaylist(name, listOf(song.id))
                }
            )
        }

        // ── Sleep Timer dialog ─────────────────────────────────────────
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                currentEndMs = state.sleepTimerEndMs,
                remainingMs = sleepRemainingMs,
                onSetTimer = { durationMs ->
                    viewModel.setSleepTimer(durationMs)
                    showSleepTimerDialog = false
                },
                onCancel = {
                    viewModel.cancelSleepTimer()
                    showSleepTimerDialog = false
                },
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        // ── Queue bottom sheet ─────────────────────────────────────────
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                sheetState = queueSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                QueueSheet(
                    queue = state.queue,
                    currentIndex = state.queueIndex,
                    autoDjEnabled = state.autoDjEnabled,
                    onToggleAutoDj = { viewModel.toggleAutoDj() },
                    onTrackClick = { index ->
                        viewModel.seekToQueueItem(index)
                    },
                    onDismiss = { showQueue = false }
                )
            }
        }
    }
}

@Composable
private fun LyricsOverlay(
    lyrics: StructuredLyrics,
    positionMs: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = lyrics.line
    if (lines.isEmpty()) return

    val currentIndex = if (lyrics.synced) {
        val adjustedPos = positionMs + lyrics.offset
        lines.indexOfLast { (it.start ?: 0L) <= adjustedPos }.coerceAtLeast(0)
    } else 0

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (lyrics.synced && lines.isNotEmpty()) {
            listState.animateScrollToItem(
                index = currentIndex.coerceIn(0, lines.size - 1),
                scrollOffset = -200
            )
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = lyrics.synced && index == currentIndex
                Text(
                    text = line.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }

        // ── Close / dismiss hint ───────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close lyrics",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun QueueSheet(
    queue: List<dev.neiro.app.data.api.models.SongDto>,
    currentIndex: Int,
    autoDjEnabled: Boolean,
    onToggleAutoDj: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (queue.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceAtMost(queue.lastIndex))
        }
    }

    Column(modifier = Modifier.navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AutoDJ",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (autoDjEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Switch(
                    checked = autoDjEnabled,
                    onCheckedChange = { onToggleAutoDj() }
                )
            }
        }
        HorizontalDivider()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(queue) { index, song ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(index); onDismiss() }
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!song.artist.isNullOrBlank()) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isCurrent) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentEndMs: Long?,
    remainingMs: Long?,
    onSetTimer: (durationMs: Long) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "5 min" to 5 * 60_000L,
        "10 min" to 10 * 60_000L,
        "15 min" to 15 * 60_000L,
        "20 min" to 20 * 60_000L,
        "30 min" to 30 * 60_000L,
        "45 min" to 45 * 60_000L,
        "60 min" to 60 * 60_000L
    )

    val remainingText = remainingMs?.let {
        val mins = (it / 60000).toInt()
        val secs = ((it % 60000) / 1000).toInt()
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = null,
                tint = if (currentEndMs != null) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(text = "Sleep Timer")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentEndMs != null && remainingText != null) {
                    Text(
                        text = "Timer active: $remainingText remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                }
                presets.forEach { (label, durationMs) ->
                    TextButton(
                        onClick = { onSetTimer(durationMs) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (currentEndMs != null) {
                TextButton(onClick = onCancel) {
                    Text("Cancel Timer", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun QualityBadge(label: String, highlight: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = if (highlight) Color(0xFF6DD5FA) else Color.White.copy(alpha = 0.55f),
        modifier = Modifier
            .background(
                color = if (highlight) Color(0xFF6DD5FA).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
