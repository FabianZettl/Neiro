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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import dev.neiro.app.ui.theme.DefaultNeiroPalette
import dev.neiro.app.ui.theme.NieroTheme
import dev.neiro.app.ui.theme.extractPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayer(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val lastFm by viewModel.lastFmState.collectAsStateWithLifecycle()
    val song = state.currentSong ?: run {
        navController.popBackStack()
        return
    }

    val context = LocalContext.current
    var palette by remember { mutableStateOf(DefaultNeiroPalette) }
    LaunchedEffect(song.coverArtUrl) {
        palette = extractPalette(context, song.coverArtUrl)
    }

    val progress = if (state.durationMs > 0)
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    else 0f

    val scope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    // Single source of truth for the slider; synced from progress when not seeking
    var seekValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) {
        if (!isSeeking) seekValue = progress
    }

    // Album art scale animation: slightly smaller when paused
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1.0f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artScale"
    )

    NieroTheme(palette = palette, darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Layer 1: Blurred album art background ──────────────────
            AsyncImage(
                model = song.coverArtUrl,
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
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
                    )
                    // Queue stub icon — mirrors back button width
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // ── Album art — animated crossfade on song change ──────
                AnimatedContent(
                    targetState = song.id,
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
                            model = song.coverArtUrl,
                            contentDescription = song.album,
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
                                text = song.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!song.artist.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = song.artistId != null) {
                                        song.artistId?.let { navController.navigate("artist/$it") }
                                    }
                                )
                            }
                            if (!song.album.isNullOrBlank()) {
                                Text(
                                    text = song.album,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = song.albumId != null) {
                                        song.albumId?.let { navController.navigate("album/$it") }
                                    }
                                )
                            }
                            // Last.fm play count
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
                        // Love button — active when session connected
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = seekValue,
                            onValueChange = { v ->
                                seekJob?.cancel()
                                isSeeking = true
                                seekValue = v
                            },
                            onValueChangeFinished = {
                                viewModel.seekTo((seekValue * state.durationMs).toLong())
                                // Hold off syncing from player for 600 ms so the position
                                // update from the player has time to arrive before we let
                                // progress override seekValue again.
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
                                text = formatMs(if (isSeeking) (seekValue * state.durationMs).toLong() else state.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatMs(state.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Playback controls ──────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle (stub)
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White.copy(alpha = 0.6f),
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

                        // Play / Pause — large filled circle
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
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
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

                        // Repeat (stub)
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = Color.White.copy(alpha = 0.6f),
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
        }
    } // NieroTheme
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
