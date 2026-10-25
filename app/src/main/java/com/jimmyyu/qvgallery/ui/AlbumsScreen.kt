package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jimmyyu.qvgallery.data.Album
import com.jimmyyu.qvgallery.data.AlbumKeys
import com.jimmyyu.qvgallery.data.AlbumSort
import com.jimmyyu.qvgallery.data.AlbumViewMode
import com.jimmyyu.qvgallery.data.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    settings: AppSettings,
    hiddenMode: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenTransfer: () -> Unit,
    onSlideshow: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val albums by (if (hiddenMode) vm.hiddenAlbums else vm.albums).collectAsState()
    val loading by vm.loading.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Album?>(null) }
    var moveTarget by remember { mutableStateOf<Boolean?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    val shown = remember(albums, query) {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) albums else albums.filter { it.name.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
    }
    val selecting = selection.isNotEmpty()
    BackHandler(selecting) { selection = emptySet() }
    BackHandler(!selecting && query != null) { query = null }
    BackHandler(!selecting && query == null && drawerState.isOpen) { scope.launch { drawerState.close() } }

    val bg = settings.backgroundImage
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !hiddenMode,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    "QV Gallery",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                @Composable
                fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
                    NavigationDrawerItem(
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                        selected = selected,
                        onClick = { scope.launch { drawerState.close() }; onClick() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                item(Icons.Filled.PhotoLibrary, tr(R.string.albums), selected = true) {}
                item(Icons.Outlined.Collections, tr(R.string.all_media)) { onOpenAlbum(AlbumKeys.ALL) }
                item(Icons.Outlined.Photo, tr(R.string.all_photos)) { onOpenAlbum(AlbumKeys.PHOTOS) }
                item(Icons.Filled.Videocam, tr(R.string.all_videos)) { onOpenAlbum(AlbumKeys.VIDEOS) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                item(Icons.Filled.VisibilityOff, tr(R.string.hidden_albums)) { onOpenHidden() }
                if (!hiddenMode) item(Icons.Filled.Wifi, tr(R.string.transfer_title)) { onOpenTransfer() }
                item(Icons.Filled.Settings, tr(R.string.settings)) { onOpenSettings() }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (bg != null && !hiddenMode) {
                AsyncImage(model = bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.backgroundDim)))
            }
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    val barColors = if (bg != null && !hiddenMode) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        )
                    } else TopAppBarDefaults.topAppBarColors()
                    when {
                        selecting -> TopAppBar(
                            colors = barColors,
                            navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, tr(R.string.cancel)) } },
                            title = { Text(tr(R.string.n_selected, selection.size)) },
                            actions = {
                                IconButton(onClick = { selection = shown.map { it.path }.toSet() }) { Icon(Icons.Filled.SelectAll, tr(R.string.select_all)) }
                                IconButton(onClick = { vm.togglePinned(selection); selection = emptySet() }) { Icon(Icons.Filled.PushPin, tr(R.string.pin_to_top)) }
                                IconButton(onClick = { vm.setHidden(selection, !hiddenMode); selection = emptySet() }) {
                                    Icon(if (hiddenMode) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (hiddenMode) tr(R.string.unhide) else tr(R.string.hide))
                                }
                                var menu by remember { mutableStateOf(false) }
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr(R.string.more)) }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.details)) },
                                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                        onClick = { menu = false; showDetails = true },
                                    )
                                    if (selection.size == 1) {
                                        DropdownMenuItem(
                                            text = { Text(tr(R.string.rename)) },
                                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                            onClick = { menu = false; renaming = albums.firstOrNull { it.path in selection } },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.move_content_to)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                        onClick = { menu = false; moveTarget = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.copy_content_to)) },
                                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                        onClick = { menu = false; moveTarget = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.exclude_no_scan)) },
                                        leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                                        onClick = { menu = false; vm.setExcluded(selection, true); selection = emptySet() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.delete)) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { menu = false; confirmDelete = true },
                                    )
                                }
                            },
                        )
                        query != null -> TopAppBar(
                            colors = barColors,
                            navigationIcon = { IconButton(onClick = { query = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } },
                            title = {
                                val focus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { focus.requestFocus() }
                                TextField(
                                    value = query.orEmpty(),
                                    onValueChange = { query = it },
                                    placeholder = { Text(tr(R.string.search_albums)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            },
                        )
                        else -> TopAppBar(
                            colors = barColors,
                            navigationIcon = {
                                if (hiddenMode) {
                                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, tr(R.string.menu)) }
                                }
                            },
                            title = { Text(if (hiddenMode) tr(R.string.hidden_albums) else tr(R.string.albums)) },
                            actions = {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.FilterList, tr(R.string.sort)) }
                                AlbumSortMenu(sortMenu, settings, onDismiss = { sortMenu = false }, vm = vm)
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Search, tr(R.string.search)) }
                                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, tr(R.string.more)) }
                                DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (settings.albumViewMode == AlbumViewMode.GRID) tr(R.string.list_view) else tr(R.string.grid_view)) },
                                        onClick = {
                                            moreMenu = false
                                            vm.updateSettings {
                                                it.copy(albumViewMode = if (it.albumViewMode == AlbumViewMode.GRID) AlbumViewMode.LIST else AlbumViewMode.GRID)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr(R.string.slideshow_all)) },
                                        leadingIcon = { Icon(Icons.Filled.Slideshow, null) },
                                        onClick = { moreMenu = false; onSlideshow(AlbumKeys.ALL) },
                                    )
                                    DropdownMenuItem(text = { Text(tr(R.string.refresh)) }, onClick = { moreMenu = false; vm.reload() })
                                    if (!hiddenMode) {
                                        DropdownMenuItem(text = { Text(tr(R.string.settings)) }, onClick = { moreMenu = false; onOpenSettings() })
                                    }
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                if (loading && albums.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(if (hiddenMode) tr(R.string.no_hidden_albums) else tr(R.string.no_photos), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val list = settings.albumViewMode == AlbumViewMode.LIST
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (list) 1 else gridColumns(settings.albumColumns.coerceIn(1, 6), min = 1)),
                        contentPadding = PaddingValues(
                            start = 2.dp, end = 2.dp,
                            top = padding.calculateTopPadding() + 2.dp,
                            bottom = padding.calculateBottomPadding() + 2.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.path }) { album ->
                            val selected = album.path in selection
                            val mod = Modifier.combinedClickable(
                                onClick = {
                                    if (selecting) selection = if (selected) selection - album.path else selection + album.path
                                    else onOpenAlbum(album.path)
                                },
                                onLongClick = { selection = if (selected) selection - album.path else selection + album.path },
                            )
                            if (list) AlbumRow(album, settings, selected.takeIf { selecting }, bg != null && !hiddenMode, mod)
                            else AlbumCell(album, selected.takeIf { selecting }, mod)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                tr(R.string.albums_footer, shown.size, shown.sumOf { it.count }),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bg != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        AlbumSelectionDetailsDialog(albums.filter { it.path in selection }, onDismiss = { showDetails = false })
    }
    if (confirmDelete) {
        val count = vm.mediaInAlbums(selection).size
        ConfirmDialog(
            title = tr(R.string.delete_n_albums, selection.size),
            text = tr(R.string.delete_n_albums_body, count),
            confirm = tr(R.string.delete),
            onConfirm = {
                val items = vm.mediaInAlbums(selection)
                selection = emptySet()
                scope.launch { vm.delete(items) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    renaming?.let { album ->
        TextInputDialog(
            title = tr(R.string.rename_album),
            initial = album.name,
            selectBaseName = false,
            onConfirm = { vm.renameAlbum(album.path, it); selection = emptySet() },
            onDismiss = { renaming = null },
        )
    }
    moveTarget?.let { move ->
        FolderPickerDialog(
            title = if (move) tr(R.string.move_to) else tr(R.string.copy_to),
            albums = vm.albums.value.map { it.path }.filter { it !in selection },
            onPick = { dest ->
                val items = vm.mediaInAlbums(selection)
                selection = emptySet()
                vm.copyOrMove(items, dest, move)
            },
            onDismiss = { moveTarget = null },
        )
    }
}

@Composable
private fun AlbumSortMenu(expanded: Boolean, settings: AppSettings, vm: GalleryViewModel, onDismiss: () -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        Text(tr(R.string.album_sort), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        AlbumSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort.label) },
                leadingIcon = { RadioButton(selected = settings.albumSort == sort, onClick = null) },
                onClick = { vm.updateSettings { it.copy(albumSort = sort) } },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(tr(R.string.ascending)) },
            leadingIcon = { RadioButton(selected = settings.albumSortAsc, onClick = null) },
            onClick = { vm.updateSettings { it.copy(albumSortAsc = true) } },
        )
        DropdownMenuItem(
            text = { Text(tr(R.string.descending)) },
            leadingIcon = { RadioButton(selected = !settings.albumSortAsc, onClick = null) },
            onClick = { vm.updateSettings { it.copy(albumSortAsc = false) } },
        )
    }
}

@Composable
private fun AlbumCell(album: Album, selected: Boolean?, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).clip(MaterialTheme.shapes.extraSmall)) {
        MediaThumbnail(album.cover, Modifier.fillMaxSize(), selected = selected, showBadges = false)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (album.isPinned) Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                Text(
                    album.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(album.count.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumRow(album: Album, settings: AppSettings, selected: Boolean?, onImage: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val primary = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (onImage) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(album.cover, Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall), showBadges = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (album.isPinned) Icon(Icons.Filled.PushPin, null, tint = primary, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                Text(album.name, color = primary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ${album.count}", color = secondary, fontSize = 13.sp)
            }
            if (settings.showAlbumPath) {
                Text(displayPath(album.path), color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatShortDate(album.latest) + " · " + formatSize(context, album.totalSize), color = secondary, fontSize = 12.sp)
        }
    }
}
