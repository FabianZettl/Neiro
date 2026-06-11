package dev.neiro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.ui.album.AlbumScreen
import dev.neiro.desktop.ui.artist.ArtistScreen
import dev.neiro.desktop.ui.home.HomeScreen
import dev.neiro.desktop.ui.onboarding.OnboardingScreen
import dev.neiro.desktop.ui.player.NowPlayingBar
import dev.neiro.desktop.ui.search.SearchScreen
import dev.neiro.desktop.ui.settings.SettingsScreen
import dev.neiro.desktop.ui.theme.NieroTheme
import org.koin.compose.koinInject

@Composable
fun NieroDesktopApp() {
    val prefs: DesktopPreferences = koinInject()
    val prefsState by prefs.prefsFlow.collectAsState()

    NieroTheme {
        if (prefsState.serverUrl.isBlank()) {
            OnboardingScreen(
                preferences = prefs,
                onComplete = {}
            )
        } else {
            MainLayout()
        }
    }
}

@Composable
private fun MainLayout() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    fun navigate(screen: Screen) { currentScreen = screen }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            Sidebar(
                currentScreen = currentScreen,
                onNavigate = ::navigate,
                modifier = Modifier.width(200.dp).fillMaxHeight()
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (val s = currentScreen) {
                    is Screen.Home        -> HomeScreen(onNavigate = ::navigate)
                    is Screen.Albums      -> HomeScreen(onNavigate = ::navigate) // reuse home with album filter
                    is Screen.Artists     -> HomeScreen(onNavigate = ::navigate) // reuse home
                    is Screen.Search      -> SearchScreen(onNavigate = ::navigate)
                    is Screen.Settings    -> SettingsScreen()
                    is Screen.AlbumDetail -> AlbumScreen(albumId = s.albumId, onNavigate = ::navigate)
                    is Screen.ArtistDetail -> ArtistScreen(artistId = s.artistId, onNavigate = ::navigate)
                }
            }
        }
        NowPlayingBar(modifier = Modifier.fillMaxWidth())
    }
}

private data class NavItem(val label: String, val icon: ImageVector, val screen: Screen)

private val navItems = listOf(
    NavItem("Home",     Icons.Default.Home,        Screen.Home),
    NavItem("Albums",   Icons.Default.Album,        Screen.Albums),
    NavItem("Artists",  Icons.Default.Person,       Screen.Artists),
    NavItem("Search",   Icons.Default.Search,       Screen.Search),
)

@Composable
private fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
            // App logo / title
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "音色",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Neiro",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(6.dp))

            navItems.forEach { item ->
                val selected = currentScreen == item.screen
                SidebarItem(
                    label = item.label,
                    icon = item.icon,
                    selected = selected,
                    onClick = { onNavigate(item.screen) }
                )
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(4.dp))

            SidebarItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                selected = currentScreen == Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
        )
        Spacer(Modifier.width(9.dp))
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}
