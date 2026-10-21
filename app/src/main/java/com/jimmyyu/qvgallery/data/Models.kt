package com.jimmyyu.qvgallery.data

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.net.Uri
import java.io.File

data class MediaItem(
    /** MediaStore id, or -1 for files found by scanning folders directly. */
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    /** Milliseconds since epoch. */
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    /** Milliseconds, videos only. */
    val duration: Long,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isGif: Boolean get() = mimeType == "image/gif"
    val isRaw: Boolean get() = MediaTypes.isRaw(name, mimeType)
    val folder: String get() = path.substringBeforeLast('/', "")
    val file: File get() = File(path)
}

data class Album(
    val path: String,
    val name: String,
    val count: Int,
    val cover: MediaItem,
    val latest: Long,
    val totalSize: Long,
    val videoCount: Int,
    val isPinned: Boolean,
    val isHidden: Boolean,
)

/** Identifies a list of media shown in a grid / viewer. */
object AlbumKeys {
    const val ALL = "::all"
    const val VIDEOS = "::videos"
    const val PHOTOS = "::photos"

    fun title(key: String): String = when (key) {
        ALL -> tr(R.string.all_media)
        VIDEOS -> tr(R.string.all_videos)
        PHOTOS -> tr(R.string.all_photos)
        else -> key.substringAfterLast('/').ifEmpty { key }
    }
}

object MediaTypes {
    private val rawExt = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "rw2", "orf", "pef", "srw", "raf", "raw", "3fr", "erf", "kdc", "mrw", "x3f")
    private val imageExt = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
        "avif" to "image/avif", "ico" to "image/x-icon", "jxl" to "image/jxl",
    )
    private val videoExt = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "3gp" to "video/3gpp", "mov" to "video/quicktime", "avi" to "video/avi", "ts" to "video/mp2ts",
        "flv" to "video/x-flv", "wmv" to "video/x-ms-wmv",
    )

    fun isRaw(name: String, mime: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in rawExt || mime == "image/x-adobe-dng" || mime.contains("raw")

    fun mimeFor(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return imageExt[ext] ?: videoExt[ext] ?: if (ext in rawExt) "image/x-raw-$ext" else null
    }
}

enum class AlbumSort(private val labelRes: Int) {
    NAME(R.string.name), DATE(R.string.date), SIZE(R.string.size), COUNT(R.string.count), PATH(R.string.path);

    val label: String get() = tr(labelRes)
}
enum class MediaSort(private val labelRes: Int) {
    DATE_TAKEN(R.string.date_taken), DATE_MODIFIED(R.string.date_modified), NAME(R.string.name), SIZE(R.string.size);

    val label: String get() = tr(labelRes)
}

/** True when [folder] is one of [roots] or anywhere below one of them. */
fun isUnderAny(folder: String, roots: Set<String>): Boolean =
    roots.isNotEmpty() && roots.any { folder == it || folder.startsWith("$it/") }
