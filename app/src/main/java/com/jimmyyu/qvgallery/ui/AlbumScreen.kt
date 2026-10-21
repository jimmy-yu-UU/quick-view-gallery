package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimmyyu.qvgallery.data.AlbumKeys
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.MediaSort
import kotlinx.coroutines.launch

/**
 * Deletes media, falling back to the system confirmation dialog for files we may not delete directly.
 * Returns a function that asks for confirmation (when enabled) and then deletes.
 */
@Composable
fun rememberDeleter(vm: GalleryViewModel, settings: AppSettings): (List<MediaItem>, () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<List<MediaItem>, () -> Unit>?>(null) }
    var systemPending by remember { mutableStateOf<Pair<List<MediaItem>, () -> Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val p = systemPending
        systemPending = null
        if (result.resultCode == Activity.RESULT_OK && p != null) {
            vm.removeLocally(p.first)
            p.second()
        }
    }
    fun run(items: List<MediaItem>, onDone: () -> Unit) {
        scope.launch {
            val failed = vm.delete(items)
            if (failed.isEmpty()) {
                onDone()
            } else {
                val withIds = failed.filter { it.id >= 0 }
                if (withIds.isNotEmpty()) {
                    systemPending = withIds to onDone
                    launcher.launch(IntentSenderRequest.Builder(vm.fileOps.deleteRequest(withIds).intentSender).build())
                } else {
                    vm.toast(tr(R.string.n_cannot_delete, failed.size))
                    onDone()
                }
            }
        }
    }
    pending?.let { (items, onDone) ->
        ConfirmDialog(
            title = if (items.size == 1) tr(R.string.delete_this_file) else tr(R.string.delete_n_items, items.size),
            text = if (items.size == 1) items[0].name else tr(R.string.delete_permanent),
            confirm = tr(R.string.delete),
            onConfirm = { run(items, onDone) },
            onDismiss = { pending = null },
        )
    }
    return { items, onDone ->
        if (settings.confirmDelete) pending = items to onDone else run(items, onDone)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    vm: GalleryViewModel,
    settings: AppSettings,
    albumKey: String,
    onOpen: (Int) -> Unit,
    onSendNearby: (List<MediaItem>) -> Unit,
    onSlideshow: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val flow = remember(albumKey) { vm.mediaFor(albumKey) }
    val items by flow.collectAsState(initial = remember(albumKey) { vm.snapshotFor(albumKey) })
    val loading by vm.loading.collectAsState()
    var selection by remember(albumKey) { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Boolean?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    val deleter = rememberDeleter(vm, settings)
    val gridState = rememberLazyGridState()
    val selecting = selection.isNotEmpty()
    val selectedItems = { items.filter { it.path in selection } }
    BackHandler(selecting) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, tr(R.string.cancel)) } },
                    title = { Text(tr(R.string.n_selected, selection.size)) },
                    actions = {
                        IconButton(onClick = { selection = items.map { it.path }.toSet() }) { Icon(Icons.Filled.SelectAll, tr(R.string.select_all)) }
                        IconButton(onClick = { context.startActivity(vm.fileOps.shareIntent(selectedItems())) }) { Icon(Icons.Filled.Share, tr(R.string.share)) }
                        IconButton(onClick = { deleter(selectedItems()) { selection = emptySet() } }) { Icon(Icons.Filled.Delete, tr(R.string.delete)) }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr(R.string.more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr(R.string.details)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { menu = false; showDetails = true },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.send_nearby)) },
                                leadingIcon = { Icon(Icons.Filled.Wifi, null) },
                                onClick = { menu = false; onSendNearby(selectedItems()); selection = emptySet() },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.move_to_ellipsis)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                onClick = { menu = false; moveTarget = true },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.copy_to_ellipsis)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = { menu = false; moveTarget = false },
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } },
                    title = {
                        Column {
                            Text(AlbumKeys.title(albumKey), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                tr(R.string.n_items, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.FilterList, tr(R.string.sort)) }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            MediaSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    leadingIcon = { RadioButton(selected = settings.mediaSort == sort, onClick = null) },
                                    onClick = { vm.updateSettings { it.copy(mediaSort = sort) } },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr(R.string.ascending)) },
                                leadingIcon = { RadioButton(selected = settings.mediaSortAsc, onClick = null) },
                                onClick = { vm.updateSettings { it.copy(mediaSortAsc = true) } },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.descending)) },
                                leadingIcon = { RadioButton(selected = !settings.mediaSortAsc, onClick = null) },
                                onClick = { vm.updateSettings { it.copy(mediaSortAsc = false) } },
                            )
                        }
                        IconButton(onClick = onSlideshow, enabled = items.isNotEmpty()) { Icon(Icons.Filled.Slideshow, tr(R.string.slideshow)) }
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, tr(R.string.more)) }
                        DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text(tr(R.string.select_everything)) }, onClick = { moreMenu = false; selection = items.map { it.path }.toSet() })
                            DropdownMenuItem(
                                text = { Text(tr(R.string.more_columns)) },
                                onClick = { vm.updateSettings { it.copy(mediaColumns = (it.mediaColumns + 1).coerceAtMost(8)) } },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.fewer_columns)) },
                                onClick = { vm.updateSettings { it.copy(mediaColumns = (it.mediaColumns - 1).coerceAtLeast(2)) } },
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text(tr(R.string.empty_here), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(settings.mediaColumns.coerceIn(2, 8)),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, it -> it.path }) { index, item ->
                val selected = item.path in selection
                MediaThumbnail(
                    item,
                    selected = selected.takeIf { selecting },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .combinedClickable(
                            onClick = {
                                if (selecting) selection = if (selected) selection - item.path else selection + item.path
                                else onOpen(index)
                            },
                            onLongClick = { selection = if (selected) selection - item.path else selection + item.path },
                        ),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val photos = items.count { !it.isVideo }
                Text(
                    tr(R.string.album_footer, photos, items.size - photos, formatSize(context, items.sumOf { it.size })),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    if (showDetails) {
        MediaSelectionDetailsDialog(selectedItems(), onDismiss = { showDetails = false })
    }
    moveTarget?.let { move ->
        FolderPickerDialog(
            title = if (move) tr(R.string.move_to) else tr(R.string.copy_to),
            albums = vm.albums.value.map { it.path }.filter { it != albumKey },
            onPick = { dest ->
                val chosen = selectedItems()
                selection = emptySet()
                vm.copyOrMove(chosen, dest, move)
            },
            onDismiss = { moveTarget = null },
        )
    }
}
