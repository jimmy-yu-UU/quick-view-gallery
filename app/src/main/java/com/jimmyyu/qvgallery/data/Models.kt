package com.jimmyyu.qvgallery.data

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
        ALL -> "所有媒體"
        VIDEOS -> "所有影片"
        PHOTOS -> "所有相片"
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

enum class AlbumSort(val label: String) { NAME("名稱"), DATE("日期"), SIZE("大小"), COUNT("數量"), PATH("路徑") }
enum class MediaSort(val label: String) { DATE_TAKEN("拍攝日期"), DATE_MODIFIED("修改日期"), NAME("名稱"), SIZE("大小") }
