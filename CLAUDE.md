# Neiro (音色) – Android Music Streaming App

Neiro is an Android music streaming app for OpenSubsonic/Navidrome servers.
Inspired by Apple Music's UI with dynamic album-cover-based color theming.
The name "Neiro" (音色) means "timbre" or "tone color" in Japanese — reflecting
both the sound and the dynamic color system at the core of the app.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material3)
- **Min SDK:** 26 | **Target SDK:** 35
- **Player:** AndroidX Media3 / ExoPlayer
- **Networking:** Retrofit2 + OkHttp3
- **Images:** Coil (with Palette extraction)
- **Color Theming:** AndroidX Palette API
- **DI:** Hilt
- **Storage/Prefs:** DataStore Preferences
- **Navigation:** Compose Navigation

---

## Core Principles

- **NO offline downloads** — streaming only, always
- **Dynamic color** — every screen's color scheme is derived from the current album art
  - Dominant color → background gradient
  - Vibrant color → accent, buttons, interactive elements
  - LightMuted / DarkMuted → text colors
  - Animated transitions when the song changes
- **Streaming quality** — configurable (original bitrate preferred, transcoding optional)
- **UI philosophy** — Apple Music: spacious, clean, smooth 60fps animations
- **Customization** — user can tune layout, theme intensity, player style

---

## OpenSubsonic API

- **Spec:** https://opensubsonic.netlify.app/
- **Auth method:** MD5 token + random salt (NOT plain password)
  ```
  token = md5(password + salt)
  params: u=user&t=token&s=salt&v=1.16.1&c=neiro&f=json
  ```
- **Key endpoints used:**
  - `getAlbumList2` — browse albums
  - `getArtists` / `getArtist` — artist browser
  - `getAlbum` — album detail with track list
  - `getSong` — single track metadata
  - `stream` — audio stream URL (append auth params)
  - `getCoverArt` — album artwork
  - `search3` — unified search
  - `getPlaylists` / `getPlaylist` — playlist management
  - `createPlaylist` / `updatePlaylist` — playlist editing
  - `scrobble` — Last.fm-compatible scrobbling
  - `getStarred2` — favorites

---

## Project Structure

```
app/src/main/java/dev/neiro/
├── data/
│   ├── api/
│   │   ├── SubsonicApi.kt          # Retrofit interface
│   │   ├── SubsonicAuthInterceptor.kt
│   │   └── models/                 # API response DTOs
│   ├── repository/
│   │   ├── MusicRepository.kt
│   │   └── PlayerRepository.kt
│   └── prefs/
│       └── NieroPreferences.kt     # DataStore
├── player/
│   ├── NieroMediaService.kt        # Media3 foreground service
│   ├── PlayerController.kt         # App-side player interface
│   └── QueueManager.kt
├── ui/
│   ├── theme/
│   │   ├── NieroTheme.kt           # Dynamic MaterialTheme wrapper
│   │   ├── PaletteExtractor.kt     # Coil + Palette → ColorScheme
│   │   └── Color.kt                # Fallback colors
│   ├── navigation/
│   │   └── NieroNavGraph.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── library/
│   │   ├── LibraryScreen.kt        # Albums, Artists, Playlists, Genres
│   │   └── LibraryViewModel.kt
│   ├── album/
│   │   ├── AlbumScreen.kt
│   │   └── AlbumViewModel.kt
│   ├── artist/
│   │   └── ArtistScreen.kt
│   ├── player/
│   │   ├── MiniPlayer.kt           # Bottom bar mini player
│   │   ├── FullscreenPlayer.kt     # Fullscreen now playing
│   │   ├── PlayerViewModel.kt
│   │   └── QueueSheet.kt           # Swipeable queue bottom sheet
│   ├── search/
│   │   └── SearchScreen.kt
│   └── settings/
│       ├── SettingsScreen.kt       # All UI + server settings
│       └── SettingsViewModel.kt
└── di/
    └── AppModule.kt                # Hilt modules
```

---

## Dynamic Color System

```kotlin
// PaletteExtractor extracts from album bitmap loaded by Coil:
data class NeiroPalette(
    val background: Color,      // Palette.dominantSwatch
    val accent: Color,          // Palette.vibrantSwatch
    val textPrimary: Color,     // Palette.lightMutedSwatch
    val textSecondary: Color,   // Palette.mutedSwatch
    val surface: Color          // darkened dominant
)
// NieroTheme wraps MaterialTheme, animates between palettes with
// animateColorAsState(tween(600)) on every color slot.
```

---

## Player Architecture

- `NieroMediaService` extends `MediaSessionService` (Media3)
- Runs as foreground service with persistent notification
- `PlayerController` exposes `StateFlow<PlayerState>` to UI
- Stream URL = `{serverUrl}/rest/stream?id={trackId}&{authParams}&maxBitRate={pref}`
- No caching, no downloads — direct HTTP stream only

---

## Settings / Customization Options

| Setting | Options |
|---|---|
| Server URL | Free text |
| Username / Password | Stored encrypted in DataStore |
| Streaming quality | Original / 320 / 256 / 192 / 128 kbps |
| Crossfade | 0–12 seconds slider |
| Theme intensity | Subtle / Medium / Vivid (palette saturation) |
| Player layout | Classic / Minimal / Immersive |
| Color dark mode | Auto / Force dark / Force light |
| ReplayGain | Off / Track / Album |
| Scrobble | On / Off |

---

## Build & APK

```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned)
./gradlew assembleRelease
```

---

## Coding Conventions

- All UI in Jetpack Compose — no XML layouts
- ViewModels use `StateFlow`, not `LiveData`
- Repository pattern — ViewModels never call API directly
- Coroutines + Flow throughout (no RxJava)
- String resources for all user-visible text (res/values/strings.xml)
- All colors via NieroTheme — no hardcoded hex in composables
