package dev.neiro.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

val LocalNeiroPalette = compositionLocalOf { DefaultNeiroPalette }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private const val ANIM_MS = 600

@Composable
fun NieroTheme(
    palette: NeiroPalette = DefaultNeiroPalette,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // All color animations at top level (Rules of Hooks)
    val bg   by animateColorAsState(palette.background,    tween(ANIM_MS), label = "bg")
    val acc  by animateColorAsState(palette.accent,        tween(ANIM_MS), label = "acc")
    val txt  by animateColorAsState(palette.textPrimary,   tween(ANIM_MS), label = "txt")
    val sec  by animateColorAsState(palette.textSecondary, tween(ANIM_MS), label = "sec")
    val surf by animateColorAsState(palette.surface,       tween(ANIM_MS), label = "surf")

    val animated = NeiroPalette(bg, acc, txt, sec, surf)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary             = acc,
            onPrimary           = Color.Black,
            primaryContainer    = darken(acc, 0.35f),
            onPrimaryContainer  = txt,
            secondary           = darken(acc, 0.2f),
            onSecondary         = txt,
            background          = bg,
            onBackground        = txt,
            surface             = surf,
            onSurface           = txt,
            surfaceVariant      = NieroSurface,
            onSurfaceVariant    = sec,
            outline             = sec.copy(alpha = 0.4f),
            error               = ErrorColor,
            onError             = Color.White
        )
    } else {
        lightColorScheme(
            primary             = acc,
            onPrimary           = Color.White,
            primaryContainer    = acc.copy(alpha = 0.12f),
            onPrimaryContainer  = darken(acc, 0.4f),
            secondary           = darken(acc, 0.15f),
            onSecondary         = Color.White,
            background          = Color(0xFFF2F2F7),   // iOS light gray
            onBackground        = Color(0xFF1C1C1E),
            surface             = Color.White,
            onSurface           = Color(0xFF1C1C1E),
            surfaceVariant      = Color(0xFFE5E5EA),
            onSurfaceVariant    = Color(0xFF6C6C70),
            outline             = Color(0xFFC7C7CC),
            error               = ErrorColor,
            onError             = Color.White
        )
    }

    CompositionLocalProvider(LocalNeiroPalette provides animated) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
