package dev.neiro.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

data class NeiroPalette(
    val background: Color   = NieroBackground,
    val accent: Color       = NieroAccent,
    val textPrimary: Color  = NieroTextPrimary,
    val textSecondary: Color = NieroTextSecondary,
    val surface: Color      = NieroSurface
)

val DefaultNeiroPalette = NeiroPalette()

suspend fun extractPalette(context: Context, imageUrl: String?, darkTheme: Boolean = true): NeiroPalette {
    if (imageUrl.isNullOrBlank()) return DefaultNeiroPalette
    return try {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(200, 200)
                .build()
        )
        val bitmap = (result as? SuccessResult)
            ?.drawable
            ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
            ?: return DefaultNeiroPalette
        buildNeiroPalette(bitmap, darkTheme)
    } catch (e: Exception) {
        DefaultNeiroPalette
    }
}

private fun buildNeiroPalette(bitmap: Bitmap, darkTheme: Boolean): NeiroPalette {
    val palette = Palette.from(bitmap).generate()

    // Accent color priority:
    //   1. Vibrant swatch (colorful images)
    //   2. Light vibrant swatch
    //   3. Dominant-derived neutral (B&W / desaturated covers — strips most saturation,
    //      keeps the image's actual hue/brightness rather than falling back to the app's default red)
    //   4. App default (empty palette — extremely rare)
    val accentArgb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.let { swatch ->
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(swatch.rgb, hsv)
            // Keep hue, remove most saturation → neutral gray matching the cover's tone
            hsv[1] = (hsv[1] * 0.25f).coerceAtMost(0.15f)
            hsv[2] = if (darkTheme) 0.70f else 0.42f
            android.graphics.Color.HSVToColor(hsv)
        }
        ?: NieroAccent.toArgb()

    return if (darkTheme) {
        // Dark mode: only the accent color adapts — background, surface and text stay fixed
        // so the UI stays dark and legible regardless of album art
        NeiroPalette(
            background    = NieroBackground,
            accent        = Color(accentArgb),
            textPrimary   = NieroTextPrimary,
            textSecondary = NieroTextSecondary,
            surface       = NieroSurface
        )
    } else {
        // Light mode: allow a fuller palette shift (background tints lightly)
        val bgArgb  = palette.getDarkMutedColor(palette.getDominantColor(NieroBackground.toArgb()))
        val txtArgb = palette.getLightMutedColor(NieroTextPrimary.toArgb())
        val secArgb = palette.getMutedColor(NieroTextSecondary.toArgb())
        val bg = Color(bgArgb)
        NeiroPalette(
            background    = bg,
            accent        = Color(accentArgb),
            textPrimary   = Color(txtArgb),
            textSecondary = Color(secArgb),
            surface       = darken(bg, 0.12f)
        )
    }
}

internal fun darken(color: Color, amount: Float) = Color(
    red   = (color.red   * (1f - amount)).coerceIn(0f, 1f),
    green = (color.green * (1f - amount)).coerceIn(0f, 1f),
    blue  = (color.blue  * (1f - amount)).coerceIn(0f, 1f),
    alpha = color.alpha
)
