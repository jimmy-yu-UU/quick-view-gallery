package com.jimmyyu.qvgallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jimmyyu.qvgallery.R
import com.jimmyyu.qvgallery.app
import com.jimmyyu.qvgallery.data.Album
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.ui.viewer.DetailsDialog

/** Details for the current selection in an album grid: full details for one item, a summary for several. */
@Composable
fun MediaSelectionDetailsDialog(items: List<MediaItem>, onDismiss: () -> Unit) {
    if (items.size == 1) {
        DetailsDialog(items[0], onDismiss)
        return
    }
    val context = LocalContext.current
    val videos = items.count { it.isVideo }
    val folders = items.map { it.folder }.distinct()
    val rows = buildList {
        add(tr(R.string.items_count) to items.size.toString())
        add(tr(R.string.photos) to (items.size - videos).toString())
        add(tr(R.string.videos) to videos.toString())
        add(tr(R.string.total_size) to formatSize(context, items.sumOf { it.size }))
        if (items.isNotEmpty()) add(tr(R.string.date_range) to dateRange(items.minOf { it.dateTaken }, items.maxOf { it.dateTaken }))
        add(tr(R.string.in_folders) to folderList(folders))
    }
    DetailsTable(rows, onDismiss)
}

/** Details for selected albums: one album in full, or totals for several. */
@Composable
fun AlbumSelectionDetailsDialog(albums: List<Album>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rows = buildList {
        if (albums.size == 1) {
            val a = albums[0]
            add(tr(R.string.name) to a.name)
            add(tr(R.string.path) to a.path)
            add(tr(R.string.storage_location) to (context.app.media.volumeLabel(a.path) ?: tr(R.string.internal_storage)))
        } else {
            add(tr(R.string.albums_count) to albums.size.toString())
        }
        val total = albums.sumOf { it.count }
        val videos = albums.sumOf { it.videoCount }
        add(tr(R.string.items_count) to total.toString())
        add(tr(R.string.photos) to (total - videos).toString())
        add(tr(R.string.videos) to videos.toString())
        add(tr(R.string.total_size) to formatSize(context, albums.sumOf { it.totalSize }))
        if (albums.isNotEmpty()) add(tr(R.string.latest_item) to formatDate(albums.maxOf { it.latest }))
        if (albums.size > 1) add(tr(R.string.in_folders) to folderList(albums.map { it.path }))
    }
    DetailsTable(rows, onDismiss)
}

private fun dateRange(from: Long, to: Long): String {
    val a = formatShortDate(from)
    val b = formatShortDate(to)
    return if (a == b) a else "$a – $b"
}

private fun folderList(folders: List<String>): String {
    val shown = folders.take(8).joinToString("\n") { displayPath(it) }
    return if (folders.size > 8) "$shown\n… (+${folders.size - 8})" else shown
}

@Composable
private fun DetailsTable(rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.details)) },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    rows.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(96.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.close)) } },
    )
}
