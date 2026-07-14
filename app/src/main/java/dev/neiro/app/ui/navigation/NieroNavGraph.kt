package dev.neiro.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.neiro.app.ui.album.AlbumScreen
import dev.neiro.app.ui.albums.AlbumsListScreen
import dev.neiro.app.ui.artist.ArtistDetailScreen
import dev.neiro.app.ui.artists.ArtistsListScreen
import dev.neiro.app.ui.home.HomeScreen
import dev.neiro.app.ui.home.ManageSectionsScreen
import dev.neiro.app.ui.library.LibraryScreen
import dev.neiro.app.ui.player.FullscreenPlayer
import dev.neiro.app.ui.player.MiniPlayer
import dev.neiro.app.ui.playlists.PlaylistDetailScreen
import dev.neiro.app.ui.playlists.PlaylistsListScreen
import dev.neiro.app.ui.podcasts.PodcastDetailScreen
import dev.neiro.app.ui.podcasts.PodcastsScreen
import dev.neiro.app.ui.radio.RadioScreen
import dev.neiro.app.ui.search.SearchScreen
import dev.neiro.app.ui.settings.SettingsScreen
import dev.neiro.app.ui.onboarding.OnboardingScreen
import dev.neiro.app.ui.startup.StartupViewModel
import dev.neiro.app.ui.starred.StarredScreen
import dev.neiro.app.ui.theme.LocalNeiroPalette
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val noMiniPlayerRoutes = setOf("fullscreen_player", "onboarding")
private val noDrawerRoutes = setOf(
    "fullscreen_player", "manage_sections", "onboarding",
    "album/{albumId}", "artist/{artistId}", "playlist/{playlistId}",
    "podcast_detail/{subscriptionId}"
)

@Composable
fun NieroNavGraph(
    startupViewModel: StartupViewModel = hiltViewModel()
) {
    val startDestination by produceState<String?>(initialValue = null) {
        value = if (startupViewModel.isServerConfigured()) "home" else "onboarding"
    }

    // Wait until we've read the preferences before rendering NavHost
    if (startDestination == null) return

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val onOpenDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val onCloseDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val hazeState = remember { HazeState() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute !in noDrawerRoutes,
        drawerContent = {
            NieroDrawerSheet(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    onCloseDrawer()
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 8 } },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 8 } }
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onComplete = {
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }
                composable("home") {
                    HomeScreen(navController = navController, onOpenDrawer = onOpenDrawer)
                }
                composable("library") {
                    LibraryScreen(navController = navController, onOpenDrawer = onOpenDrawer)
                }
                composable("search") {
                    SearchScreen(navController = navController, onOpenDrawer = onOpenDrawer)
                }
                composable("settings") {
                    SettingsScreen(onOpenDrawer = onOpenDrawer)
                }
                composable(
                    route = "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                ) { AlbumScreen(navController = navController) }
                composable(
                    route = "albums_list?albumType={albumType}",
                    arguments = listOf(navArgument("albumType") {
                        type = NavType.StringType
                        defaultValue = "alphabeticalByName"
                    })
                ) { AlbumsListScreen(navController = navController) }
                composable("artists_list") { ArtistsListScreen(navController = navController) }
                composable(
                    route = "artist/{artistId}",
                    arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                ) { ArtistDetailScreen(navController = navController) }
                composable("playlists_list") { PlaylistsListScreen(navController = navController) }
                composable(
                    route = "playlist/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) { PlaylistDetailScreen(navController = navController) }
                composable("starred") { StarredScreen(navController = navController) }
                composable("radio") { RadioScreen(navController = navController) }
                composable("podcasts") { PodcastsScreen(navController = navController) }
                composable(
                    route = "podcast_detail/{subscriptionId}",
                    arguments = listOf(navArgument("subscriptionId") { type = NavType.StringType })
                ) { PodcastDetailScreen(navController = navController) }
                composable("fullscreen_player") { FullscreenPlayer(navController = navController) }
                composable("manage_sections") { ManageSectionsScreen(navController = navController) }
            }

            if (currentRoute !in noMiniPlayerRoutes) {
                MiniPlayer(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onTap = { navController.navigate("fullscreen_player") }
                )
            }
        }
    }
}

// ── Drawer ────────────────────────────────────────────────────────────────────

@Composable
private fun NieroDrawerSheet(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    // Use palette surface/text directly so the drawer stays dark in both
    // dark and light theme modes (NieroTheme's lightColorScheme hardcodes
    // surface = Color.White, which would make the drawer all-white).
    val palette = LocalNeiroPalette.current
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(palette.surface)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── App name header ──────────────────────────────────────────
            Text(
                text = "音色  Neiro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 20.dp, end = 20.dp)
            )

            // ── Main navigation ──────────────────────────────────────────
            DrawerItem(Icons.Default.Search,      "Search",   "search",   currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Home,         "Home",     "home",     currentRoute, onNavigate, palette)

            Spacer(Modifier.height(8.dp))
            DrawerSectionHeader("LIBRARY", palette)

            DrawerItem(Icons.Default.Audiotrack,   "Recently Added",  "albums_list?albumType=newest",               currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Person,       "Artists",         "artists_list",                               currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Album,        "Albums",          "albums_list?albumType=alphabeticalByName",   currentRoute, onNavigate, palette)
            DrawerItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlists",       "playlists_list", currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Favorite,     "Starred",         "starred",        currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Radio,        "Internet Radio",  "radio",          currentRoute, onNavigate, palette)
            DrawerItem(Icons.Default.Podcasts,     "Podcasts",        "podcasts",       currentRoute, onNavigate, palette)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = palette.textSecondary.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(8.dp))

            DrawerItem(Icons.Default.Settings, "Settings", "settings", currentRoute, onNavigate, palette)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerSectionHeader(label: String, palette: dev.neiro.app.ui.theme.NeiroPalette) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textSecondary.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    route: String,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    palette: dev.neiro.app.ui.theme.NeiroPalette
) {
    val selected = currentRoute == route
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected)
                    Modifier.background(palette.accent)
                else
                    Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null) { onNavigate(route) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) palette.textPrimary else palette.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.textPrimary else palette.textPrimary
        )
    }
}
