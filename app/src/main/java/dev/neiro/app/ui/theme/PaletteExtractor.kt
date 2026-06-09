package dev.neiro.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
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

suspend fun extractPalette(context: Context, imageUrl: String?): NeiroPalette {
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
        buildNeiroPalette(bitmap)
    } catch (e: Exception) {
        DefaultNeiroPalette
    }
}

private fun buildNeiroPalette(bitmap: Bitmap): NeiroPalette {
    val palette = Palette.from(bitmap).generate()

    // Prefer dark-muted for the background so it stays legible
    val bgArgb  = palette.getDarkMutedColor(palette.getDominantColor(NieroBackground.hashCode()))
    val accArgb = palette.getVibrantColor(palette.getLightVibrantColor(NieroAccent.hashCode()))
    val txtArgb = palette.getLightMutedColor(NieroTextPrimary.hashCode())
    val secArgb = palette.getMutedColor(NieroTextSecondary.hashCode())

    val bg = Color(bgArgb)
    return NeiroPalette(
        background   = bg,
        accent       = Color(accArgb),
        textPrimary  = Color(txtArgb),
        textSecondary = Color(secArgb),
        surface      = darken(bg, 0.12f)
    )
}

internal fun darken(color: Color, amount: Float) = Color(
    red   = (color.red   * (1f - amount)).coerceIn(0f, 1f),
    green = (color.green * (1f - amount)).coerceIn(0f, 1f),
    blue  = (color.blue  * (1f - amount)).coerceIn(0f, 1f),
    alpha = color.alpha
)
