package com.jimmyyu.qvgallery.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    suspend fun loadAll(extraFolders: Set<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<MediaItem>(4096)
        val seen = HashSet<String>(4096)
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(setOf(MediaStore.VOLUME_EXTERNAL))
        for (volume in volumes) {
            runCatching { queryVolume(volume, result, seen) }
                .onFailure { Log.w(TAG, "query $volume failed", it) }
        }
        if (Environment.isExternalStorageManager()) {
            for (folder in extraFolders) {
                runCatching { scanFolder(File(folder), result, seen, depth = 0) }
                    .onFailure { Log.w(TAG, "scan $folder failed", it) }
            }
        }
        result
    }

    private fun queryVolume(volume: String, out: MutableList<MediaItem>, seen: MutableSet<String>) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.ORIENTATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val imagesUri = MediaStore.Images.Media.getContentUri(volume)
        val videosUri = MediaStore.Video.Media.getContentUri(volume)
        context.contentResolver.query(MediaStore.Files.getContentUri(volume), projection, selection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(projection[0])
            val iData = c.getColumnIndexOrThrow(projection[1])
            val iName = c.getColumnIndexOrThrow(projection[2])
            val iMime = c.getColumnIndexOrThrow(projection[3])
            val iSize = c.getColumnIndexOrThrow(projection[4])
            val iTaken = c.getColumnIndexOrThrow(projection[5])
            val iModified = c.getColumnIndexOrThrow(projection[6])
            val iWidth = c.getColumnIndexOrThrow(projection[7])
            val iHeight = c.getColumnIndexOrThrow(projection[8])
            val iDuration = c.getColumnIndexOrThrow(projection[9])
            val iType = c.getColumnIndexOrThrow(projection[10])
            val iOrientation = c.getColumnIndexOrThrow(projection[11])
            while (c.moveToNext()) {
                val path = c.getStringOrNull(iData) ?: continue
                if (!seen.add(path)) continue
                val id = c.getLong(iId)
                val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val name = c.getStringOrNull(iName) ?: path.substringAfterLast('/')
                val mime = c.getStringOrNull(iMime) ?: MediaTypes.mimeFor(name) ?: if (isVideo) "video/*" else "image/*"
                val modified = c.getLong(iModified) * 1000
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: modified
                val rotated = c.getInt(iOrientation).let { it == 90 || it == 270 }
                val w = c.getInt(iWidth)
                val h = c.getInt(iHeight)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(if (isVideo) videosUri else imagesUri, id),
                    path = path,
                    name = name,
                    mimeType = mime,
                    size = c.getLong(iSize),
                    dateTaken = taken,
                    dateModified = modified,
                    width = if (rotated) h else w,
                    height = if (rotated) w else h,
                    duration = c.getLong(iDuration),
                )
            }
        }
    }

    /** Direct file-system scan for folders MediaStore skips (e.g. containing .nomedia) or USB drives. */
    private fun scanFolder(dir: File, out: MutableList<MediaItem>, seen: MutableSet<String>, depth: Int) {
        if (depth > 6) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (!f.name.startsWith('.') || depth == 0) scanFolder(f, out, seen, depth + 1)
                continue
            }
            val mime = MediaTypes.mimeFor(f.name) ?: continue
            if (!seen.add(f.absolutePath)) continue
            out += fileItem(f, mime)
        }
    }

    fun fileItem(f: File, mime: String = MediaTypes.mimeFor(f.name) ?: "image/*"): MediaItem = MediaItem(
        id = -1,
        uri = Uri.fromFile(f),
        path = f.absolutePath,
        name = f.name,
        mimeType = mime,
        size = f.length(),
        dateTaken = f.lastModified(),
        dateModified = f.lastModified(),
        width = 0,
        height = 0,
        duration = 0,
    )

    /** Human-friendly label for the storage volume a path lives on. */
    fun volumeLabel(path: String): String? {
        val sm = context.getSystemService(StorageManager::class.java) ?: return null
        val vol = sm.getStorageVolume(File(path)) ?: return null
        return if (vol.isPrimary) null else vol.getDescription(context)
    }

    private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    companion object {
        private const val TAG = "MediaRepository"
    }
}
