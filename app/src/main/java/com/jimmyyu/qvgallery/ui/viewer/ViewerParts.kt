package com.jimmyyu.qvgallery.ui.viewer

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.image.Thumb
import com.jimmyyu.qvgallery.ui.GalleryViewModel
import com.jimmyyu.qvgallery.ui.formatDate
import com.jimmyyu.qvgallery.ui.formatDuration
import com.jimmyyu.qvgallery.ui.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem as ExoMediaItem

@OptIn(UnstableApi::class)
@Composable
fun VideoPage(
    item: MediaItem,
    active: Boolean,
    autoPlay: Boolean,
    loop: Boolean,
    onControllerVisible: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!active) {
            AsyncImage(
                model = Thumb(item, 1024),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(72.dp))
            return@Box
        }
        val player = remember(item.uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(item.uri))
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                playWhenReady = autoPlay
                prepare()
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        val visibleCallback by rememberUpdatedState(onControllerVisible)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 3000
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        visibleCallback(visibility == View.VISIBLE)
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Rotates a JPEG by rewriting its EXIF orientation (no re-encoding). */
suspend fun rotateLossless(vm: GalleryViewModel, item: MediaItem): Boolean {
    if (item.mimeType != "image/jpeg") return false
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            val exif = ExifInterface(item.path)
            exif.rotate(90)
            exif.saveAttributes()
            true
        }.getOrDefault(false)
    }
    if (ok) {
        vm.fileOps.scan(listOf(item.path))
        vm.reload()
    } else {
        vm.toast(tr(R.string.rotate_fallback))
    }
    return ok
}

@Composable
fun DetailsDialog(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exif by produceState<List<Pair<String, String>>>(emptyList(), item) {
        value = withContext(Dispatchers.IO) { readExif(context, item) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.details)) },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    val rows = buildList {
                        add(tr(R.string.name) to item.name)
                        add(tr(R.string.path) to item.path)
                        add(tr(R.string.size) to formatSize(context, item.size))
                        if (item.width > 0) add(tr(R.string.resolution) to "${item.width} × ${item.height}")
                        if (item.isVideo && item.duration > 0) add(tr(R.string.duration) to formatDuration(item.duration))
                        add(tr(R.string.date_taken) to formatDate(item.dateTaken))
                        add(tr(R.string.date_modified) to formatDate(item.dateModified))
                        add(tr(R.string.type) to item.mimeType)
                        addAll(exif)
                    }
                    rows.forEach { (k, v) -> DetailRow(k, v) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.close)) } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun readExif(context: android.content.Context, item: MediaItem): List<Pair<String, String>> {
    if (item.isVideo) return emptyList()
    return runCatching {
        // Reading the file directly keeps GPS tags, which MediaStore redacts from content streams.
        val stream = if (item.file.canRead()) item.file.inputStream() else context.contentResolver.openInputStream(item.uri)
        stream?.use { input ->
            val e = ExifInterface(input)
            buildList {
                val make = e.getAttribute(ExifInterface.TAG_MAKE)
                val model = e.getAttribute(ExifInterface.TAG_MODEL)
                if (make != null || model != null) add(tr(R.string.camera) to listOfNotNull(make, model).joinToString(" "))
                e.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { add(tr(R.string.lens) to it) }
                e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { add(tr(R.string.aperture) to "f/%.1f".format(it)) }
                e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                    add(tr(R.string.shutter) to if (it < 1) "1/${(1 / it).toInt()} s" else "%.1f s".format(it))
                }
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO" to it) }
                e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { add(tr(R.string.focal_length) to "%.1f mm".format(it)) }
                e.latLong?.let { (lat, lon) -> add(tr(R.string.location) to "%.5f, %.5f".format(lat, lon)) }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
