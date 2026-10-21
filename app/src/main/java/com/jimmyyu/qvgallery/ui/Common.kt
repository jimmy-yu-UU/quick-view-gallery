package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.image.Thumb
import java.io.File
import java.text.DateFormat
import java.util.Date

// ---------- Formatting ----------

fun formatSize(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes)

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun formatShortDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Path shown under album names, relative to its storage root. */
fun displayPath(path: String): String {
    val primary = Environment.getExternalStorageDirectory().absolutePath
    return when {
        path.startsWith(primary) -> path.removePrefix(primary).ifEmpty { "/" }
        path.startsWith("/storage/") -> {
            val rest = path.removePrefix("/storage/")
            val volume = rest.substringBefore('/')
            "[$volume]" + rest.removePrefix(volume)
        }
        else -> path
    }
}

// ---------- Thumbnail ----------

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    showBadges: Boolean = true,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = Thumb(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showBadges && item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(12.dp))
                if (item.duration > 0) {
                    Text(
                        " " + formatDuration(item.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        } else if (showBadges && item.isGif) {
            Icon(
                Icons.Filled.Gif, null, tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
            )
        }
        if (selected != null) {
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f), CircleShape),
            )
        }
    }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = tr(R.string.ok),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String = tr(R.string.name),
    selectBaseName: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        val end = if (selectBaseName && initial.contains('.')) initial.lastIndexOf('.') else initial.length
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, end)))
    }
    val valid = text.text.isNotBlank() && !text.text.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                isError = !valid,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onDismiss(); onConfirm(text.text.trim()) }) { Text(tr(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}

data class StorageRoot(val path: String, val label: String, val removable: Boolean, val usb: Boolean)

fun storageRoots(context: Context): List<StorageRoot> {
    val sm = context.getSystemService(StorageManager::class.java)
    val roots = sm.storageVolumes.mapNotNull { v ->
        val dir = v.directory ?: return@mapNotNull null
        val desc = v.getDescription(context)
        StorageRoot(
            path = dir.absolutePath,
            label = desc,
            removable = v.isRemovable,
            usb = v.isRemovable && desc.contains("USB", ignoreCase = true),
        )
    }
    return roots.ifEmpty {
        listOf(StorageRoot(Environment.getExternalStorageDirectory().absolutePath, tr(R.string.internal_storage), false, false))
    }
}

/**
 * Picks a destination folder: shows existing albums first, and a browser for any directory on any volume.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    albums: List<String>,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var browsing by remember { mutableStateOf<File?>(null) }
    var creating by remember { mutableStateOf(false) }
    val roots = remember { storageRoots(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (browsing != null) {
                    IconButton(onClick = {
                        val cur = browsing!!
                        browsing = if (roots.any { it.path == cur.absolutePath }) null else cur.parentFile
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.up)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title)
                    browsing?.let {
                        Text(
                            displayPath(it.absolutePath),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                        )
                    }
                }
                if (browsing != null) {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Filled.CreateNewFolder, tr(R.string.new_folder)) }
                }
            }
        },
        text = {
            val dir = browsing
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (dir == null) {
                    item { SectionLabel(tr(R.string.storage)) }
                    items(roots) { root ->
                        FolderRow(
                            icon = { Icon(if (root.usb) Icons.Filled.Usb else if (root.removable) Icons.Filled.SdStorage else Icons.Filled.Smartphone, null) },
                            name = root.label,
                            sub = root.path,
                            onClick = { browsing = File(root.path) },
                        )
                    }
                    if (albums.isNotEmpty()) {
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); SectionLabel(tr(R.string.albums)) }
                        items(albums) { path ->
                            FolderRow(
                                icon = { Icon(Icons.Filled.Folder, null) },
                                name = path.substringAfterLast('/'),
                                sub = displayPath(path),
                                onClick = { onDismiss(); onPick(File(path)) },
                            )
                        }
                    }
                } else {
                    val listed = dir.listFiles()
                    val children = listed
                        ?.filter { it.isDirectory && !it.name.startsWith('.') }
                        ?.sortedWith(compareBy(NaturalOrder) { it.name })
                        .orEmpty()
                    if (listed == null) {
                        item {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(tr(R.string.no_folder_access), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                }) { Text(tr(R.string.grant_all_files)) }
                            }
                        }
                    } else if (children.isEmpty()) {
                        item { Text(tr(R.string.empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    items(children, key = { it.absolutePath }) { child ->
                        FolderRow(
                            icon = { Icon(Icons.Filled.Folder, null) },
                            name = child.name,
                            sub = null,
                            onClick = { browsing = child },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (browsing != null) {
                TextButton(onClick = { val d = browsing!!; onDismiss(); onPick(d) }) { Text(tr(R.string.select_this_folder)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )

    if (creating) {
        TextInputDialog(
            title = tr(R.string.new_folder),
            initial = "",
            selectBaseName = false,
            onConfirm = { name ->
                val f = File(browsing, name)
                if (f.mkdirs() || f.isDirectory) browsing = f
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun FolderRow(icon: @Composable () -> Unit, name: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column {
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
