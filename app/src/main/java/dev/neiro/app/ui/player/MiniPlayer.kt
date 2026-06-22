package dev.neiro.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.neiro.app.data.repository.DesktopState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val MiniPlayerShape = RoundedCornerShape(22.dp)

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    onTap: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val desktopState by viewModel.desktopState.collectAsStateWithLifecycle()
    val song = state.currentSong
    val desktopPlaying = desktopState as? DesktopState.Playing

    // Nothing to show at all
    if (song == null && desktopPlaying == null) return

    val progress = if (song != null && state.durationMs > 0)
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    else 0f

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = MiniPlayerShape,
                    spotColor = Color.Black.copy(alpha = 0.3f),
                    ambientColor = Color.Black.copy(alpha = 0.15f)
                )
                .clip(MiniPlayerShape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        tints = listOf(
                            HazeTint(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                        ),
                        blurRadius = 32.dp,
                        noiseFactor = 0.05f
                    )
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = MiniPlayerShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap
                )
        ) {
            Column {
                // ── Local playback row ─────────────────────────────────────────
                if (song != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.coverArtUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
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

                        // Cast-to-desktop button when desktop is connected but idle
                        if (desktopState is DesktopState.Connected && state.isPlaying) {
                            IconButton(
                                onClick = { viewModel.castQueueToDesktop() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Cast to Desktop",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(
                                if (desktopPlaying != null)
                                    RoundedCornerShape(0.dp)
                                else
                                    RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                            ),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        strokeCap = StrokeCap.Square
                    )
                }

                // ── Desktop remote row ─────────────────────────────────────────
                if (desktopPlaying != null) {
                    val ds = desktopPlaying.song
                    val desktopProgress = if (ds.durationMs > 0)
                        (ds.positionMs.toFloat() / ds.durationMs).coerceIn(0f, 1f)
                    else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = "Desktop",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = ds.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ds.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Transfer to this device
                        IconButton(
                            onClick = { viewModel.transferFromDesktop() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdateAlt,
                                contentDescription = "Transfer to phone",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.sendCommandToDesktop(
                                    if (ds.isPlaying) "pause" else "play"
                                )
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (ds.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (ds.isPlaying) "Pause Desktop" else "Play Desktop",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.sendCommandToDesktop("next") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next on Desktop",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { desktopProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        strokeCap = StrokeCap.Square
                    )
                }
            }
        }
    }
}
