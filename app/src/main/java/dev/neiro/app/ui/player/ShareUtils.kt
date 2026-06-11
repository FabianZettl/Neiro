package dev.neiro.app.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import dev.neiro.app.data.api.models.SongDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

suspend fun shareNowPlayingCard(context: Context, song: SongDto) {
    val bitmap = withContext(Dispatchers.IO) {
        buildShareCard(context, song)
    } ?: return

    val file = File(context.cacheDir, "now_playing_share.png")
    withContext(Dispatchers.IO) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
    }

    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "🎵 ${song.title} — ${song.artist ?: ""}\nNow playing on Neiro 音色")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Now Playing"))
}

private suspend fun buildShareCard(context: Context, song: SongDto): Bitmap? {
    val size = 1080
    val card = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(card)
    val density = context.resources.displayMetrics.density

    // ── 1. Download cover art ────────────────────────────────────────────────
    val coverBitmap: Bitmap? = if (song.coverArtUrl != null) {
        try {
            val request = ImageRequest.Builder(context)
                .data(song.coverArtUrl)
                .size(size, size)
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) { null }
    } else null

    // ── 2. Blurred background ────────────────────────────────────────────────
    if (coverBitmap != null) {
        val scaledBg = Bitmap.createScaledBitmap(coverBitmap, size, size, true)
        canvas.drawBitmap(scaledBg, 0f, 0f, null)
        // Blur via RenderScript
        try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, scaledBg)
            val output = Allocation.createTyped(rs, input.type)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(25f)
            blur.setInput(input)
            blur.forEach(output)
            output.copyTo(scaledBg)
            canvas.drawBitmap(scaledBg, 0f, 0f, null)
            rs.destroy()
        } catch (_: Exception) { /* fallback: just draw unblurred */ }
    } else {
        // Dark gradient background
        val gradPaint = Paint()
        gradPaint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(),
            intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradPaint)
    }

    // ── 3. Dark scrim overlay ────────────────────────────────────────────────
    val scrimPaint = Paint().apply { color = 0xB0000000.toInt() }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), scrimPaint)

    // ── 4. Album art square ──────────────────────────────────────────────────
    val artSize = (size * 0.52f)
    val artLeft = (size - artSize) / 2f
    val artTop = size * 0.14f
    val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
    val radius = artSize * 0.08f

    if (coverBitmap != null) {
        val scaled = Bitmap.createScaledBitmap(coverBitmap, artSize.toInt(), artSize.toInt(), true)
        val clipPath = android.graphics.Path().apply {
            addRoundRect(artRect, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(scaled, artLeft, artTop, null)
        canvas.restore()
    } else {
        val placePaint = Paint().apply { color = 0xFF2A2A4A.toInt() }
        canvas.drawRoundRect(artRect, radius, radius, placePaint)
    }

    // ── 5. Text: title ───────────────────────────────────────────────────────
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 52f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val titleY = artTop + artSize + 64f * density
    canvas.drawText(
        song.title.take(28) + if (song.title.length > 28) "…" else "",
        size / 2f, titleY, titlePaint
    )

    // ── 6. Text: artist ──────────────────────────────────────────────────────
    val artistName = song.artist ?: ""
    if (artistName.isNotBlank()) {
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 36f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            artistName.take(32) + if (artistName.length > 32) "…" else "",
            size / 2f, titleY + 52f * density, artistPaint
        )
    }

    // ── 7. Branding ──────────────────────────────────────────────────────────
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        textSize = 24f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Now Playing • Neiro 音色", size / 2f, size * 0.95f, brandPaint)

    return card
}
