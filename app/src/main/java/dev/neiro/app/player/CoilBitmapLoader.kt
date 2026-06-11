package dev.neiro.app.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.BitmapLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class CoilBitmapLoader(private val context: Context) : BitmapLoader {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IllegalArgumentException("Could not decode bitmap from byte array")
                future.set(bmp)
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(512, 512)         // notification large icon size
                    .allowHardware(false)   // must be software bitmap for notification use
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                val bmp = (drawable as? BitmapDrawable)?.bitmap
                    ?: throw IllegalStateException("Could not load bitmap from $uri")
                future.set(bmp)
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
