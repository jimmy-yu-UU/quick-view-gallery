package com.jimmyyu.qvgallery.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.Options
import com.jimmyyu.qvgallery.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Grid thumbnail. Uses the system thumbnail cache for MediaStore items (fast, handles video/HEIC/RAW). */
data class Thumb(val item: MediaItem, val sizePx: Int = 384) {
    /** Memory cache key; the viewer uses it to show the grid thumbnail while the full image loads. */
    val cacheKey: String get() = "thumb:${item.uri}:${item.dateModified}:${item.size}:$sizePx"
}

/** Full-size preview of a RAW photo, taken from the embedded JPEG. */
data class RawPreview(val item: MediaItem)

class ThumbFetcher(private val data: Thumb, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val item = data.item
        val size = data.sizePx
        val bitmap = runCatching { context.contentResolver.loadThumbnail(item.uri, Size(size, size), null) }.getOrNull()
            ?: (if (item.isRaw) RawDecoder.decode(context, item.uri, size) else null)
            ?: (if (item.isVideo) videoFrame(item.uri) else sampledDecode(item.uri, size))
            ?: throw IllegalStateException("no thumbnail for ${item.uri}")
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun sampledDecode(uri: Uri, size: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(info.size.width, info.size.height).toFloat() / size
            if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    private fun videoFrame(uri: Uri): Bitmap? = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, data.sizePx, data.sizePx)
        }
    }.getOrNull()

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, context)
    }
}

/** Files outside MediaStore have no system thumbnail; send them through Coil's regular file pipeline. */
class ThumbMapper : Mapper<Thumb, File> {
    override fun map(data: Thumb, options: Options): File? =
        if (data.item.id < 0 && data.item.uri.scheme == "file" && !data.item.isRaw) data.item.file else null
}

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = data.cacheKey
}

class RawFetcher(private val data: RawPreview, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val item = data.item
        val cached = RawCache.file(context, item)
        val bitmap = RawCache.read(cached)
            ?: RawDecoder.decode(context, item.uri, 4096)?.also { RawCache.write(cached, it) }
            ?: throw IllegalStateException("cannot decode RAW ${item.path}")
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<RawPreview> {
        override fun create(data: RawPreview, options: Options, imageLoader: ImageLoader): Fetcher =
            RawFetcher(data, context)
    }
}

class RawKeyer : Keyer<RawPreview> {
    override fun key(data: RawPreview, options: Options) = "raw:${data.item.uri}:${data.item.dateModified}"
}

/**
 * Decoded RAW previews saved as JPEG. Developing a RAW file (e.g. a 50 MP Pixel DNG) takes seconds;
 * reading the saved JPEG back takes a fraction of that.
 */
object RawCache {
    private const val MAX_FILES = 300
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun file(context: Context, item: MediaItem): File {
        val key = "${item.uri}:${item.dateModified}:${item.size}"
        return context.cacheDir.resolve("raw_preview").resolve("${UUID.nameUUIDFromBytes(key.toByteArray())}.jpg")
    }

    fun read(file: File): Bitmap? =
        if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null

    /** Saves in the background so the first view isn't held up by JPEG encoding. */
    fun write(file: File, bitmap: Bitmap) {
        scope.launch {
            runCatching {
                val dir = file.parentFile ?: return@runCatching
                dir.mkdirs()
                val tmp = File(dir, "${file.name}.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                if (!tmp.renameTo(file) && !tmp.delete()) tmp.deleteOnExit()
                dir.listFiles { f -> f.extension == "jpg" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_FILES)
                    ?.forEach { if (!it.delete()) it.deleteOnExit() }
            }
        }
    }
}

object RawDecoder {
    /**
     * DNG decodes natively; other RAW formats use the embedded JPEG preview that cameras write.
     */
    fun decode(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        runCatching {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val s = info.size
                val scale = max(s.width, s.height).toFloat() / maxSize
                if (scale > 1f) decoder.setTargetSize((s.width / scale).toInt(), (s.height / scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val bytes = exif.thumbnailBytes ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return null
                val degrees = exif.rotationDegrees
                if (degrees == 0) bmp
                else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            }
        }.getOrNull()
    }
}
