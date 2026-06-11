package dev.neiro.desktop.ui.theme

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

data class NeiroPalette(
    val background: Color = NieroBackground,
    val accent: Color = NieroAccent,
    val textPrimary: Color = NieroTextPrimary,
    val textSecondary: Color = NieroTextSecondary,
    val surface: Color = NieroSurface
)

val DefaultNeiroPalette = NeiroPalette()

val LocalNeiroPalette = compositionLocalOf { DefaultNeiroPalette }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private const val ANIM_MS = 600

fun darken(color: Color, amount: Float) = Color(
    red = (color.red * (1f - amount)).coerceIn(0f, 1f),
    green = (color.green * (1f - amount)).coerceIn(0f, 1f),
    blue = (color.blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = color.alpha
)

/**
 * Extract a dominant seed color from pixel sampling.
 * Averages pixel colors in a 4x4 grid sample from the image.
 * Returns null if no image.
 */
fun extractDominantColor(pixels: IntArray, width: Int, height: Int): Color? {
    if (pixels.isEmpty()) return null
    var r = 0L; var g = 0L; var b = 0L
    val step = maxOf(1, pixels.size / 64)
    var count = 0
    for (i in pixels.indices step step) {
        val p = pixels[i]
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
        count++
    }
    if (count == 0) return null
    return Color(
        red = (r / count).toInt(),
        green = (g / count).toInt(),
        blue = (b / count).toInt()
    )
}

@Composable
fun NieroTheme(
    palette: NeiroPalette = DefaultNeiroPalette,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg by animateColorAsState(palette.background, tween(ANIM_MS), label = "bg")
    val acc by animateColorAsState(palette.accent, tween(ANIM_MS), label = "acc")
    val txt by animateColorAsState(palette.textPrimary, tween(ANIM_MS), label = "txt")
    val sec by animateColorAsState(palette.textSecondary, tween(ANIM_MS), label = "sec")
    val surf by animateColorAsState(palette.surface, tween(ANIM_MS), label = "surf")

    val animated = NeiroPalette(bg, acc, txt, sec, surf)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = acc,
            onPrimary = Color.Black,
            primaryContainer = darken(acc, 0.35f),
            onPrimaryContainer = txt,
            secondary = darken(acc, 0.2f),
            onSecondary = txt,
            background = bg,
            onBackground = txt,
            surface = surf,
            onSurface = txt,
            surfaceVariant = NieroSurface,
            onSurfaceVariant = sec,
            outline = sec.copy(alpha = 0.4f),
            error = ErrorColor,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = acc,
            onPrimary = Color.White,
            primaryContainer = acc.copy(alpha = 0.12f),
            onPrimaryContainer = darken(acc, 0.4f),
            secondary = darken(acc, 0.15f),
            onSecondary = Color.White,
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF1C1C1E),
            surface = Color.White,
            onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFE5E5EA),
            onSurfaceVariant = Color(0xFF6C6C70),
            outline = Color(0xFFC7C7CC),
            error = ErrorColor,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalNeiroPalette provides animated) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
