package com.jimmyyu.qvgallery

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.MediaTypes
import com.jimmyyu.qvgallery.ui.EditorScreen
import com.jimmyyu.qvgallery.ui.GalleryViewModel
import com.jimmyyu.qvgallery.ui.SlideshowScreen
import com.jimmyyu.qvgallery.ui.theme.QvTheme
import com.jimmyyu.qvgallery.ui.viewer.ViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Handles ACTION_VIEW from other apps; shows the whole folder when the file's location is known. */
class ViewActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val mimeHint = intent.type
        setContent {
            val vm: GalleryViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }
            QvTheme(s) {
                AppGate(vm, s) { ExternalViewer(vm, s, uri, mimeHint, onFinish = { finish() }) }
            }
        }
    }
}

private sealed interface Target {
    data object Loading : Target
    /** File on disk inside a folder we can list. */
    data class Folder(val folder: String, val path: String, val inMediaStore: Boolean) : Target
    data class Single(val item: MediaItem) : Target
}

@Composable
private fun ExternalViewer(vm: GalleryViewModel, s: AppSettings, uri: Uri, mimeHint: String?, onFinish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val target by produceState<Target>(Target.Loading, uri) {
        value = withContext(Dispatchers.IO) { resolve(context, uri, mimeHint) }
    }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    var slideshowFrom by remember { mutableStateOf<Int?>(null) }
    var jumpTo by remember { mutableStateOf<String?>(null) }

    val t = target
    if (t == Target.Loading) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val listed: List<MediaItem> = when (t) {
        is Target.Folder -> if (t.inMediaStore) {
            val flow = remember(t) { vm.mediaFor(t.folder) }
            flow.collectAsState(initial = remember(t) { vm.snapshotFor(t.folder) }).value
        } else {
            remember(t) { listFolder(context, t.folder, s) }
        }
        is Target.Single -> listOf(t.item)
        Target.Loading -> emptyList()
    }
    val loading by vm.loading.collectAsState()
    // MediaStore may not list the file (e.g. videos hidden in settings); fall back to reading the folder.
    val items = remember(listed, loading) {
        if (t is Target.Folder && t.inMediaStore && !loading && listed.none { it.path == t.path }) listFolder(context, t.folder, s)
        else listed
    }
    if (items.isEmpty()) {
        if (!loading) LaunchedEffect(Unit) { onFinish() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    editing?.let { item ->
        EditorScreen(vm, item, onDone = { saved -> editing = null; if (saved != null) jumpTo = saved })
        return
    }
    slideshowFrom?.let { start ->
        SlideshowScreen(s, items, start, onExit = { last -> slideshowFrom = null; jumpTo = last })
        return
    }
    val canModify = t is Target.Folder && Environment.isExternalStorageManager()
    ViewerScreen(
        vm = vm,
        settings = s,
        items = items,
        startPath = (t as? Target.Folder)?.path,
        startIndex = 0,
        canModify = canModify,
        onBack = onFinish,
        onEdit = { editing = it },
        onSlideshow = { slideshowFrom = it },
        jumpTo = jumpTo,
        onJumped = { jumpTo = null },
    )
}

private fun resolve(context: Context, uri: Uri, mimeHint: String?): Target {
    val path = pathFor(context, uri)
    if (path != null) {
        val f = File(path)
        if (f.canRead()) {
            val inStore = isInMediaStore(context, path)
            return Target.Folder(f.parent ?: "/", f.absolutePath, inStore)
        }
    }
    // Unknown location: show just this one item via its content URI.
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                size = c.getLong(1)
            }
        }
    }
    val mime = context.contentResolver.getType(uri) ?: mimeHint ?: MediaTypes.mimeFor(name) ?: "image/*"
    val now = System.currentTimeMillis()
    return Target.Single(
        MediaItem(
            id = -1, uri = uri, path = path ?: "", name = name, mimeType = mime, size = size,
            dateTaken = now, dateModified = now, width = 0, height = 0, duration = 0,
        )
    )
}

private fun pathFor(context: Context, uri: Uri): String? = when (uri.scheme) {
    "file" -> uri.path
    "content" -> runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: externalStoragePath(uri)
    else -> null
}

/** Decodes common file-manager URIs such as content://com.android.externalstorage.documents/document/primary%3ADCIM%2Fa.jpg */
private fun externalStoragePath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = uri.lastPathSegment ?: return null
    val volume = docId.substringBefore(':')
    val rel = docId.substringAfter(':', "")
    val root = if (volume == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
    return "$root/$rel"
}

private fun isInMediaStore(context: Context, path: String): Boolean = runCatching {
    context.contentResolver.query(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DATA} = ?",
        arrayOf(path),
        null,
    )?.use { it.count > 0 } ?: false
}.getOrDefault(false)

private fun listFolder(context: Context, folder: String, s: AppSettings): List<MediaItem> {
    val repo = context.app.media
    val files = File(folder).listFiles().orEmpty().filter { it.isFile && MediaTypes.mimeFor(it.name) != null }
    return GalleryViewModel.sortMedia(files.map { repo.fileItem(it) }, s.mediaSort, s.mediaSortAsc)
}
