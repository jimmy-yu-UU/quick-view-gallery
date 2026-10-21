package com.jimmyyu.qvgallery.ui.viewer

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.request.ImageRequest
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.ViewerBackground
import com.jimmyyu.qvgallery.image.RawPreview
import com.jimmyyu.qvgallery.ui.FolderPickerDialog
import com.jimmyyu.qvgallery.ui.GalleryViewModel
import com.jimmyyu.qvgallery.ui.TextInputDialog
import com.jimmyyu.qvgallery.ui.formatDate
import com.jimmyyu.qvgallery.ui.rememberDeleter
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    settings: AppSettings,
    items: List<MediaItem>,
    startPath: String?,
    startIndex: Int,
    canModify: Boolean,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    onSlideshow: (Int) -> Unit,
    jumpTo: String? = null,
    onJumped: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val initial = remember {
        startPath?.let { p -> items.indexOfFirst { it.path == p } }?.takeIf { it >= 0 }
            ?: startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
    val pager = rememberPagerState(initialPage = initial) { items.size }
    var currentPath by remember { mutableStateOf(items.getOrNull(initial)?.path) }
    var showUi by remember { mutableStateOf(!settings.hideBarsOnOpen) }
    var zoomState by remember { mutableStateOf<ZoomableState?>(null) }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<MediaItem?>(null) }
    var renaming by remember { mutableStateOf<MediaItem?>(null) }
    var moveTarget by remember { mutableStateOf<Boolean?>(null) }
    val deleter = rememberDeleter(vm, settings)

    // Keep the same file on screen when the list changes (rescan, sort); after a delete show the next one.
    LaunchedEffect(items) {
        if (items.isEmpty()) { onBack(); return@LaunchedEffect }
        val idx = items.indexOfFirst { it.path == currentPath }
        if (idx >= 0 && idx != pager.currentPage) pager.scrollToPage(idx)
        else if (idx < 0) currentPath = items.getOrNull(pager.currentPage.coerceAtMost(items.size - 1))?.path
    }
    // Wait until the target (e.g. a freshly saved edit) shows up in the list, then jump once.
    LaunchedEffect(jumpTo, items) {
        val idx = jumpTo?.let { p -> items.indexOfFirst { it.path == p } } ?: -1
        if (idx >= 0) {
            currentPath = jumpTo
            pager.scrollToPage(idx)
            onJumped()
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> items.getOrNull(page)?.let { currentPath = it.path } }
    }
    val current = items.getOrNull(pager.currentPage)

    // System bars follow our UI visibility.
    DisposableEffect(showUi) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showUi) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = false
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        val window = activity?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val oldBrightness = window?.attributes?.screenBrightness
        if (settings.maxBrightness && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (oldBrightness != null) window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val volumeNav by rememberUpdatedState(settings.volumeKeysNavigate)
    DisposableEffect(pager) {
        KeyEvents.handler = { code ->
            if (!volumeNav) false
            else when (code) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pager.pageCount - 1)) }; true }
                KeyEvent.KEYCODE_VOLUME_UP -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                else -> false
            }
        }
        onDispose { KeyEvents.handler = null }
    }
    BackHandler { onBack() }

    val background = when (settings.viewerBackground) {
        ViewerBackground.BLACK -> Color.Black
        ViewerBackground.WHITE -> Color.White
        ViewerBackground.THEME -> MaterialTheme.colorScheme.background
    }

    Box(Modifier.fillMaxSize().background(background)) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.path ?: it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val active = page == pager.currentPage
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    active = active && !pager.isScrollInProgress,
                    autoPlay = settings.autoPlayVideo,
                    loop = settings.loopVideo,
                    onControllerVisible = { showUi = it },
                )
            } else {
                val zoomable = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = settings.maxZoom))
                if (active) {
                    LaunchedEffect(zoomable) { zoomState = zoomable }
                } else {
                    // Reset zoom on pages scrolled away from.
                    LaunchedEffect(Unit) { zoomable.resetZoom(withAnimation = false) }
                }
                val model: Any = if (item.isRaw) RawPreview(item) else
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .memoryCacheKey("${item.uri}#${item.dateModified}#${item.size}")
                        .build()
                ZoomableAsyncImage(
                    model = model,
                    contentDescription = item.name,
                    state = rememberZoomableImageState(zoomable),
                    modifier = Modifier.fillMaxSize(),
                    onClick = { showUi = !showUi },
                )
            }
        }

        // Top bar
        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), tint = Color.White) }
                Text(
                    current?.name.orEmpty(),
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (current != null) {
                    if (canModify && !current.isVideo) {
                        IconButton(onClick = { onEdit(current) }) { Icon(Icons.Filled.Edit, tr(R.string.edit), tint = Color.White) }
                    }
                    if (canModify) {
                        IconButton(onClick = { deleter(listOf(current)) {} }) { Icon(Icons.Filled.Delete, tr(R.string.delete), tint = Color.White) }
                    }
                    IconButton(onClick = { context.startActivity(vm.fileOps.shareIntent(listOf(current))) }) {
                        Icon(Icons.Filled.Share, tr(R.string.share), tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr(R.string.more), tint = Color.White) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr(R.string.details)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { menu = false; details = current },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(R.string.slideshow)) },
                                leadingIcon = { Icon(Icons.Filled.Slideshow, null) },
                                onClick = { menu = false; onSlideshow(pager.currentPage) },
                            )
                            if (canModify && !current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(tr(R.string.rotate_right_90)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, null) },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            if (!rotateLossless(vm, current)) onEdit(current)
                                        }
                                    },
                                )
                            }
                            if (canModify) {
                                DropdownMenuItem(
                                    text = { Text(tr(R.string.rename)) },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                    onClick = { menu = false; renaming = current },
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
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(tr(R.string.set_as)) },
                                    leadingIcon = { Icon(Icons.Filled.Wallpaper, null) },
                                    onClick = {
                                        menu = false
                                        val intent = Intent(Intent.ACTION_ATTACH_DATA)
                                            .setDataAndType(vm.fileOps.shareUri(current), current.mimeType)
                                            .putExtra("mimeType", current.mimeType)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(Intent.createChooser(intent, tr(R.string.set_as_title)))
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(tr(R.string.open_with)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = {
                                    menu = false
                                    val intent = Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(vm.fileOps.shareUri(current), current.mimeType)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Bottom info bar
        if (current != null && settings.showInfoBar && !current.isVideo) {
            AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { details = current }) { Icon(Icons.Outlined.Info, tr(R.string.details), tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        val res = if (current.width > 0) "${current.width}×${current.height}" else ""
                        Text("$res   ${pager.currentPage + 1}/${items.size}", color = Color.White, fontSize = 12.sp)
                        Text(formatDate(current.dateTaken), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    IconButton(onClick = { scope.launch { zoomState?.zoomBy(1.5f) } }) { Icon(Icons.Filled.ZoomIn, tr(R.string.zoom_in), tint = Color.White) }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { scope.launch { zoomState?.zoomBy(1 / 1.5f) } }) { Icon(Icons.Filled.ZoomOut, tr(R.string.zoom_out), tint = Color.White) }
                }
            }
        }
    }

    details?.let { DetailsDialog(it, onDismiss = { details = null }) }
    renaming?.let { item ->
        TextInputDialog(
            title = tr(R.string.rename),
            initial = item.name,
            onConfirm = { name -> vm.rename(item, name) { renamed -> renamed?.let { currentPath = it.path } } },
            onDismiss = { renaming = null },
        )
    }
    moveTarget?.let { move ->
        val item = current
        if (item != null) {
            FolderPickerDialog(
                title = if (move) tr(R.string.move_to) else tr(R.string.copy_to),
                albums = vm.albums.value.map { it.path }.filter { it != item.folder },
                onPick = { dest -> vm.copyOrMove(listOf(item), dest, move) },
                onDismiss = { moveTarget = null },
            )
        }
    }
}
