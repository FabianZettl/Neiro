package dev.neiro.desktop.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.neiro.desktop.ui.Screen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumScreen(
    albumId: String,
    onNavigate: (Screen) -> Unit,
    viewModel: AlbumViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(albumId) { viewModel.loadAlbum(albumId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigate(Screen.Home) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                }
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }

        val album = state.album ?: return@LazyColumn

        item {
            // Album art + info header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(28.dp))
                Column(modifier = Modifier.weight(1f).align(Alignment.Bottom)) {
                    Text(album.name, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    album.artist?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                album.artistId?.let { id -> onNavigate(Screen.ArtistDetail(id)) }
                            })
                    }
                    album.year?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text("$it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))

                    // LastFM tags
                    val tags = state.lastFmInfo?.tags?.tags?.map { it.name }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOfNotNull(album.genre?.takeIf { it.isNotBlank() })
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.take(5).forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.playTrackAtIndex(0) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                        OutlinedButton(onClick = { viewModel.shufflePlay() }) {
                            Icon(Icons.Default.Shuffle, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                        IconButton(onClick = { viewModel.toggleStar() }) {
                            Icon(
                                if (album.starred != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Star",
                                tint = if (album.starred != null) Color(0xFFE5484D)
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }

        itemsIndexed(album.song) { index, song ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { viewModel.playTrackAtIndex(index) }
                    .padding(horizontal = 32.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${song.track ?: (index + 1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!song.artist.isNullOrBlank() && song.artist != album.artist) {
                        Text(song.artist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val m = song.duration / 60; val s = song.duration % 60
                Text("%d:%02d".format(m, s), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            if (index < album.song.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f)
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
