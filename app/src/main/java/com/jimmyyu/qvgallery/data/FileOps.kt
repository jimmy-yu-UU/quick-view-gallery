package com.jimmyyu.qvgallery.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** File operations. Relies on "All files access" for direct file manipulation. */
class FileOps(private val context: Context) {

    val hasAllFilesAccess: Boolean get() = Environment.isExternalStorageManager()

    data class Result(val done: Int, val failed: List<MediaItem>)

    suspend fun delete(items: List<MediaItem>): Result = withContext(Dispatchers.IO) {
        val failed = ArrayList<MediaItem>()
        val removedPaths = ArrayList<String>()
        for (item in items) {
            val ok = runCatching {
                if (item.id >= 0 && context.contentResolver.delete(item.uri, null, null) > 0) {
                    true
                } else {
                    val f = item.file
                    f.delete() || !f.exists()
                }
            }.getOrElse { e ->
                Log.w(TAG, "delete ${item.path} via MediaStore failed", e)
                runCatching { item.file.delete() }.getOrDefault(false)
            }
            if (ok) removedPaths += item.path else failed += item
        }
        scan(removedPaths)
        Result(items.size - failed.size, failed)
    }

    /** System-confirmed delete for when direct deletion isn't permitted. */
    fun deleteRequest(items: List<MediaItem>) =
        MediaStore.createDeleteRequest(context.contentResolver, items.filter { it.id >= 0 }.map { it.uri })

    suspend fun copyOrMove(items: List<MediaItem>, destDir: File, move: Boolean): Result = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val failed = ArrayList<MediaItem>()
        val touched = ArrayList<String>()
        for (item in items) {
            try {
                if (item.folder == destDir.absolutePath) {
                    if (!move) {
                        val target = uniqueFile(destDir, item.name)
                        copyContent(item, target)
                        touched += target.absolutePath
                    }
                    continue
                }
                val target = uniqueFile(destDir, item.name)
                if (move && item.file.renameTo(target)) {
                    touched += item.path
                    touched += target.absolutePath
                    continue
                }
                copyContent(item, target)
                if (!target.setLastModified(item.dateModified)) Log.d(TAG, "could not keep the date of ${target.name}")
                touched += target.absolutePath
                if (move) {
                    if (item.id >= 0) runCatching { context.contentResolver.delete(item.uri, null, null) }
                    if (item.file.exists() && !item.file.delete()) Log.w(TAG, "moved but could not remove ${item.path}")
                    touched += item.path
                }
            } catch (e: Exception) {
                Log.w(TAG, "copy ${item.path} failed", e)
                failed += item
            }
        }
        scan(touched)
        Result(items.size - failed.size, failed)
    }

    private fun copyContent(item: MediaItem, target: File) {
        val input = context.contentResolver.openInputStream(item.uri) ?: error("cannot open ${item.uri}")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
    }

    suspend fun rename(item: MediaItem, newName: String): File? = withContext(Dispatchers.IO) {
        val target = File(item.folder, newName)
        if (target.exists()) return@withContext null
        if (item.file.renameTo(target)) {
            scan(listOf(item.path, target.absolutePath))
            target
        } else null
    }

    suspend fun renameFolder(path: String, newName: String): File? = withContext(Dispatchers.IO) {
        val src = File(path)
        val target = File(src.parentFile, newName)
        if (target.exists() || !src.renameTo(target)) return@withContext null
        val paths = ArrayList<String>()
        src.listFiles()?.forEach { paths += it.absolutePath }
        target.listFiles()?.forEach { paths += it.absolutePath }
        scan(paths)
        target
    }

    suspend fun saveBitmap(bitmap: Bitmap, target: File, mime: String, quality: Int = 95): Boolean = withContext(Dispatchers.IO) {
        val format = when (mime) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        val ok = runCatching {
            tmp.outputStream().use { bitmap.compress(format, quality, it) }
            check(!target.exists() || target.delete()) { "cannot replace ${target.name}" }
            tmp.renameTo(target)
        }.getOrDefault(false)
        if (!ok && tmp.exists() && !tmp.delete()) Log.w(TAG, "could not remove ${tmp.name}")
        if (ok) scan(listOf(target.absolutePath))
        ok
    }

    /** Waits until MediaStore has picked up the given paths (added, changed or removed). */
    suspend fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    fun shareUri(item: MediaItem): Uri =
        if (item.id >= 0 || item.uri.scheme == "content") item.uri
        else FileProvider.getUriForFile(context, context.packageName + ".files", item.file)

    fun shareIntent(items: List<MediaItem>): Intent {
        val uris = ArrayList(items.map { shareUri(it) })
        val mime = items.map { it.mimeType.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, null)
    }

    companion object {
        private const val TAG = "FileOps"

        fun uniqueFile(dir: File, name: String): File {
            var f = File(dir, name)
            if (!f.exists()) return f
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            var i = 1
            while (f.exists()) f = File(dir, "$base ($i)$ext").also { i++ }
            return f
        }
    }
}
