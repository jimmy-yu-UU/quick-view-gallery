package com.jimmyyu.qvgallery.ui

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
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
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
                item(Icons.Filled.PhotoLibrary, "相簿", selected = true) {}
                item(Icons.Outlined.Collections, "所有媒體") { onOpenAlbum(AlbumKeys.ALL) }
                item(Icons.Outlined.Photo, "所有相片") { onOpenAlbum(AlbumKeys.PHOTOS) }
                item(Icons.Filled.Videocam, "所有影片") { onOpenAlbum(AlbumKeys.VIDEOS) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                item(Icons.Filled.VisibilityOff, "隱藏的相簿") { onOpenHidden() }
                item(Icons.Filled.Settings, "設定") { onOpenSettings() }
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
                            navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "取消") } },
                            title = { Text("已選取 ${selection.size}") },
                            actions = {
                                IconButton(onClick = { selection = shown.map { it.path }.toSet() }) { Icon(Icons.Filled.SelectAll, "全選") }
                                IconButton(onClick = { vm.togglePinned(selection); selection = emptySet() }) { Icon(Icons.Filled.PushPin, "釘選") }
                                IconButton(onClick = { vm.setHidden(selection, !hiddenMode); selection = emptySet() }) {
                                    Icon(if (hiddenMode) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (hiddenMode) "取消隱藏" else "隱藏")
                                }
                                var menu by remember { mutableStateOf(false) }
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    if (selection.size == 1) {
                                        DropdownMenuItem(
                                            text = { Text("重新命名") },
                                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                            onClick = { menu = false; renaming = albums.firstOrNull { it.path in selection } },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("移動內容到…") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                        onClick = { menu = false; moveTarget = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("複製內容到…") },
                                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                        onClick = { menu = false; moveTarget = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("排除 (不再掃描)") },
                                        leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                                        onClick = { menu = false; vm.setExcluded(selection, true); selection = emptySet() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("刪除") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { menu = false; confirmDelete = true },
                                    )
                                }
                            },
                        )
                        query != null -> TopAppBar(
                            colors = barColors,
                            navigationIcon = { IconButton(onClick = { query = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                            title = {
                                val focus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { focus.requestFocus() }
                                TextField(
                                    value = query.orEmpty(),
                                    onValueChange = { query = it },
                                    placeholder = { Text("搜尋相簿") },
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
                                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, "選單") }
                                }
                            },
                            title = { Text(if (hiddenMode) "隱藏的相簿" else "相簿") },
                            actions = {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.FilterList, "排序") }
                                AlbumSortMenu(sortMenu, settings, onDismiss = { sortMenu = false }, vm = vm)
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Search, "搜尋") }
                                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                                DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (settings.albumViewMode == AlbumViewMode.GRID) "清單檢視" else "格狀檢視") },
                                        onClick = {
                                            moreMenu = false
                                            vm.updateSettings {
                                                it.copy(albumViewMode = if (it.albumViewMode == AlbumViewMode.GRID) AlbumViewMode.LIST else AlbumViewMode.GRID)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("幻燈片播放全部") },
                                        leadingIcon = { Icon(Icons.Filled.Slideshow, null) },
                                        onClick = { moreMenu = false; onSlideshow(AlbumKeys.ALL) },
                                    )
                                    DropdownMenuItem(text = { Text("重新整理") }, onClick = { moreMenu = false; vm.reload() })
                                    if (!hiddenMode) {
                                        DropdownMenuItem(text = { Text("設定") }, onClick = { moreMenu = false; onOpenSettings() })
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
                        Text(if (hiddenMode) "沒有隱藏的相簿\n在相簿上長按即可隱藏" else "沒有找到相片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val list = settings.albumViewMode == AlbumViewMode.LIST
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (list) 1 else settings.albumColumns.coerceIn(1, 6)),
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
                                "${shown.size} 個相簿 · ${shown.sumOf { it.count }} 個項目",
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

    if (confirmDelete) {
        val count = vm.mediaInAlbums(selection).size
        ConfirmDialog(
            title = "刪除 ${selection.size} 個相簿？",
            text = "將永久刪除其中 $count 個檔案，無法復原。",
            confirm = "刪除",
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
            title = "重新命名相簿",
            initial = album.name,
            selectBaseName = false,
            onConfirm = { vm.renameAlbum(album.path, it); selection = emptySet() },
            onDismiss = { renaming = null },
        )
    }
    moveTarget?.let { move ->
        FolderPickerDialog(
            title = if (move) "移動到" else "複製到",
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
        Text("相簿排序", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        AlbumSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort.label) },
                leadingIcon = { RadioButton(selected = settings.albumSort == sort, onClick = null) },
                onClick = { vm.updateSettings { it.copy(albumSort = sort) } },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("遞增") },
            leadingIcon = { RadioButton(selected = settings.albumSortAsc, onClick = null) },
            onClick = { vm.updateSettings { it.copy(albumSortAsc = true) } },
        )
        DropdownMenuItem(
            text = { Text("遞減") },
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
