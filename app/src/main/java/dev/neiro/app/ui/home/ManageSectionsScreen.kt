package dev.neiro.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageSectionsScreen(
    navController: NavController,
    viewModel: ManageSectionsViewModel = hiltViewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val hasLastFm by viewModel.hasLastFm.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Customize Home", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            itemsIndexed(sections, key = { _, s -> s.id }) { index, section ->
                SectionCard(
                    config = section,
                    hasLastFm = hasLastFm,
                    index = index,
                    total = sections.size,
                    onUpdate = { viewModel.update(section.id, it) },
                    onDelete = { viewModel.deleteSection(section.id) },
                    onMoveUp = { viewModel.moveUp(index) },
                    onMoveDown = { viewModel.moveDown(index) }
                )
                Spacer(Modifier.height(2.dp))
            }

            // Add section button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.addSection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Section")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCard(
    config: HomeSectionConfig,
    hasLastFm: Boolean,
    index: Int,
    total: Int,
    onUpdate: ((HomeSectionConfig) -> HomeSectionConfig) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var expanded by rememberSaveable(config.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder buttons
                Column {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down",
                            tint = if (index < total - 1) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Title + content type badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = buildSummary(config),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Enable toggle
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onUpdate { it.copy(enabled = !it.enabled) } },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Expand / collapse
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expanded filter panel ─────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Title field
                    OutlinedTextField(
                        value = config.title,
                        onValueChange = { v -> onUpdate { c -> c.copy(title = v) } },
                        label = { Text("Section Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Content Type
                    FilterLabel("Content Type")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionContentType.entries.forEach { type ->
                            FilterChip(
                                selected = config.contentType == type,
                                onClick = { onUpdate { it.copy(contentType = type) } },
                                label = { Text(type.displayName) },
                                colors = chipColors()
                            )
                        }
                    }

                    // Layout
                    FilterLabel("Layout")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLayout.entries.forEach { layout ->
                            FilterChip(
                                selected = config.layout == layout,
                                onClick = { onUpdate { it.copy(layout = layout) } },
                                label = { Text(layout.label) },
                                colors = chipColors()
                            )
                        }
                    }

                    // Item Size
                    FilterLabel("Item Size")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ItemSize.entries.forEach { size ->
                            FilterChip(
                                selected = config.itemSize == size,
                                onClick = { onUpdate { it.copy(itemSize = size) } },
                                label = { Text(size.label) },
                                colors = chipColors()
                            )
                        }
                    }

                    // Item Shape
                    FilterLabel("Item Shape")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ItemShape.entries.forEach { shape ->
                            FilterChip(
                                selected = config.itemShape == shape,
                                onClick = { onUpdate { it.copy(itemShape = shape) } },
                                label = { Text(shape.label) },
                                colors = chipColors()
                            )
                        }
                    }

                    // Items to Show
                    FilterLabel("Items to Show: ${config.size}")
                    Slider(
                        value = config.size.toFloat(),
                        onValueChange = { v -> onUpdate { it.copy(size = v.roundToInt().coerceIn(1, 50)) } },
                        valueRange = 1f..50f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── ALBUMS ────────────────────────────────────────────────
                    if (config.contentType == SectionContentType.ALBUMS) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text(
                            "SORT ORDER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AlbumSortType.entries.forEach { sort ->
                                val isLastFmSort = sort == AlbumSortType.MOST_PLAYED || sort == AlbumSortType.RECENTLY_PLAYED
                                FilterChip(
                                    selected = config.sortType == sort,
                                    onClick = { onUpdate { it.copy(sortType = sort) } },
                                    label = {
                                        Text(
                                            if (hasLastFm && isLastFmSort) "⬡ ${sort.displayName}"
                                            else sort.displayName,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = chipColors()
                                )
                            }
                        }
                        if (hasLastFm) {
                            Text(
                                "⬡ = uses Last.fm data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // LastFM period picker when sort uses LastFM
                        val albumUsesLastFm = hasLastFm && (
                            config.sortType == AlbumSortType.MOST_PLAYED ||
                            config.sortType == AlbumSortType.RECENTLY_PLAYED
                        )
                        if (albumUsesLastFm) {
                            FilterLabel("Last.fm Time Range")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LastFmPeriod.entries.forEach { period ->
                                    FilterChip(
                                        selected = config.lastFmPeriod == period,
                                        onClick = { onUpdate { it.copy(lastFmPeriod = period) } },
                                        label = { Text(period.displayName) },
                                        colors = chipColors()
                                    )
                                }
                            }
                        }

                        // Subsonic-only filters (genre, year, etc.)
                        if (!albumUsesLastFm) {
                            FilterLabel("Genre (leave empty for all)")
                            OutlinedTextField(
                                value = config.genre ?: "",
                                onValueChange = { v -> onUpdate { it.copy(genre = v.takeIf { it.isNotBlank() }) } },
                                label = { Text("Genre") },
                                placeholder = { Text("e.g. Rock, Jazz, Classical") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterLabel("Played in Last")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val dayOptions = listOf(null to "Any", 7 to "7 days", 14 to "14 days",
                                    30 to "30 days", 90 to "3 months", 180 to "6 months", 365 to "1 year")
                                dayOptions.forEach { (days, label) ->
                                    FilterChip(
                                        selected = config.playedInLastDays == days,
                                        onClick = { onUpdate { it.copy(playedInLastDays = days) } },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = chipColors()
                                    )
                                }
                            }
                            FilterLabel("Minimum Play Count")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val countOptions = listOf(null to "Any", 1 to "1+", 5 to "5+",
                                    10 to "10+", 25 to "25+", 50 to "50+")
                                countOptions.forEach { (count, label) ->
                                    FilterChip(
                                        selected = config.minPlayCount == count,
                                        onClick = { onUpdate { it.copy(minPlayCount = count) } },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = chipColors()
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Starred Only", style = MaterialTheme.typography.bodyMedium)
                                    Text("Show only starred/favorited albums",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = config.starredOnly,
                                    onCheckedChange = { onUpdate { it.copy(starredOnly = !it.starredOnly) } }
                                )
                            }
                            FilterLabel("Year Range")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = config.yearFrom?.toString() ?: "",
                                    onValueChange = { v -> onUpdate { it.copy(yearFrom = v.toIntOrNull()) } },
                                    label = { Text("From") },
                                    placeholder = { Text("1970") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = config.yearTo?.toString() ?: "",
                                    onValueChange = { v -> onUpdate { it.copy(yearTo = v.toIntOrNull()) } },
                                    label = { Text("To") },
                                    placeholder = { Text("2025") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // ── ARTISTS ───────────────────────────────────────────────
                    if (config.contentType == SectionContentType.ARTISTS) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text(
                            "SORT ORDER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArtistSortType.entries.forEach { sort ->
                                val isLastFmSort = sort == ArtistSortType.MOST_PLAYED || sort == ArtistSortType.RECENTLY_PLAYED
                                FilterChip(
                                    selected = config.artistSortType == sort,
                                    onClick = { onUpdate { it.copy(artistSortType = sort) } },
                                    label = {
                                        Text(
                                            if (hasLastFm && isLastFmSort) "⬡ ${sort.displayName}"
                                            else sort.displayName
                                        )
                                    },
                                    colors = chipColors()
                                )
                            }
                        }
                        if (hasLastFm) {
                            Text(
                                "⬡ = uses Last.fm data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val artistUsesLastFm = hasLastFm && (
                            config.artistSortType == ArtistSortType.MOST_PLAYED ||
                            config.artistSortType == ArtistSortType.RECENTLY_PLAYED
                        )
                        if (artistUsesLastFm) {
                            FilterLabel("Last.fm Time Range")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LastFmPeriod.entries.forEach { period ->
                                    FilterChip(
                                        selected = config.lastFmPeriod == period,
                                        onClick = { onUpdate { it.copy(lastFmPeriod = period) } },
                                        label = { Text(period.displayName) },
                                        colors = chipColors()
                                    )
                                }
                            }
                        } else {
                            FilterLabel("Genre (filters artists by their albums)")
                            OutlinedTextField(
                                value = config.artistGenre ?: "",
                                onValueChange = { v -> onUpdate { it.copy(artistGenre = v.takeIf { it.isNotBlank() }) } },
                                label = { Text("Genre") },
                                placeholder = { Text("e.g. Emo, Metal, Jazz") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // ── TRACKS ────────────────────────────────────────────────
                    if (config.contentType == SectionContentType.TRACKS) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        FilterLabel("Last.fm Time Range")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LastFmPeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = config.lastFmPeriod == period,
                                    onClick = { onUpdate { it.copy(lastFmPeriod = period) } },
                                    label = { Text(period.displayName) },
                                    colors = chipColors()
                                )
                            }
                        }
                    }

                    // Delete button
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Section")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

private fun buildSummary(config: HomeSectionConfig): String {
    val parts = mutableListOf<String>()
    parts += config.contentType.displayName
    val albumLastFm = config.sortType == AlbumSortType.MOST_PLAYED || config.sortType == AlbumSortType.RECENTLY_PLAYED
    val artistLastFm = config.artistSortType == ArtistSortType.MOST_PLAYED || config.artistSortType == ArtistSortType.RECENTLY_PLAYED
    when (config.contentType) {
        SectionContentType.ALBUMS -> {
            if (albumLastFm) {
                parts += "Last.fm · ${config.sortType.displayName} · ${config.lastFmPeriod.displayName}"
            } else {
                parts += config.sortType.displayName
                if (config.genre != null) parts += "Genre: ${config.genre}"
                if (config.starredOnly) parts += "Starred"
                if (config.playedInLastDays != null) parts += "Last ${config.playedInLastDays}d"
                if (config.minPlayCount != null) parts += "${config.minPlayCount}+ plays"
                if (config.yearFrom != null || config.yearTo != null) {
                    parts += "${config.yearFrom ?: "?"}–${config.yearTo ?: "?"}"
                }
            }
        }
        SectionContentType.ARTISTS -> {
            if (artistLastFm) {
                parts += "Last.fm · ${config.artistSortType.displayName} · ${config.lastFmPeriod.displayName}"
            } else {
                parts += config.artistSortType.displayName
                if (config.artistGenre != null) parts += "Genre: ${config.artistGenre}"
            }
        }
        SectionContentType.TRACKS -> {
            parts += "Last.fm · ${config.lastFmPeriod.displayName}"
        }
        SectionContentType.GENRES -> { /* no extra */ }
        else -> {}
    }
    parts += "${config.size} items · ${config.layout.label} · ${config.itemShape.label}"
    return parts.joinToString(" · ")
}
