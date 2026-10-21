package com.jimmyyu.qvgallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jimmyyu.qvgallery.data.AlbumKeys
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.LockManager
import com.jimmyyu.qvgallery.data.LockScope
import com.jimmyyu.qvgallery.data.LockType
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.StartPage
import com.jimmyyu.qvgallery.ui.AlbumScreen
import com.jimmyyu.qvgallery.ui.AlbumsScreen
import com.jimmyyu.qvgallery.ui.EditorScreen
import com.jimmyyu.qvgallery.ui.GalleryViewModel
import com.jimmyyu.qvgallery.ui.SettingsHome
import com.jimmyyu.qvgallery.ui.SettingsPage
import com.jimmyyu.qvgallery.ui.SettingsSection
import com.jimmyyu.qvgallery.ui.SlideshowScreen
import com.jimmyyu.qvgallery.ui.lock.LockScreen
import com.jimmyyu.qvgallery.ui.theme.QvTheme
import com.jimmyyu.qvgallery.ui.viewer.KeyEvents
import com.jimmyyu.qvgallery.ui.viewer.ViewerScreen
import java.io.File

open class BaseActivity : FragmentActivity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handler = KeyEvents.handler
            if (handler != null) {
                // Consume both down and up so the volume doesn't change; act on key down only.
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handler(code)) return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: GalleryViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }
            QvTheme(s) {
                AppGate(vm, s) { GalleryNavHost(vm, s) }
            }
        }
    }
}

fun hasMediaPermission(context: Context): Boolean =
    Environment.isExternalStorageManager() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/** Shows the permission request and the app lock before the real content. */
@Composable
fun AppGate(vm: GalleryViewModel, s: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasMediaPermission(context)) }
    var lockTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        val now = hasMediaPermission(context)
        if (now && !permitted) vm.reload()
        permitted = now
        LockManager.onAppForeground(s.relockSeconds)
        lockTick++
        onPauseOrDispose {}
    }
    if (!permitted) {
        PermissionScreen(onGranted = { permitted = true; vm.reload() })
        return
    }
    val needsLock = s.lockType != LockType.NONE && s.lockScope == LockScope.APP
    // Read lockTick so we re-check after returning from background.
    if (needsLock && lockTick >= 0 && !LockManager.isUnlocked(s)) {
        LockScreen(
            type = s.lockType,
            biometric = s.biometric,
            check = { LockManager.verify(it, s) },
            onUnlocked = { LockManager.markUnlocked(); lockTick++ },
        )
        return
    }
    content()
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val requestMedia = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onGranted()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("需要存取你的相片", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "建議授予「所有檔案存取權」，才能完整使用移動、複製、重新命名、刪除、SD 卡與 USB 隨身碟等功能。\n所有資料都只在手機上處理，不會上傳。",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("授予所有檔案存取權 (建議)") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    requestMedia.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("只允許讀取相片與影片") }
        }
    }
}

private object Routes {
    const val ALBUMS = "albums"
    const val HIDDEN = "hidden"
    const val ALBUM = "album/{key}"
    const val VIEWER = "viewer/{key}/{index}"
    const val EDITOR = "editor/{path}"
    const val SLIDESHOW = "slideshow/{key}/{index}"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{section}"

    fun album(key: String) = "album/${Uri.encode(key)}"
    fun viewer(key: String, index: Int) = "viewer/${Uri.encode(key)}/$index"
    fun editor(path: String) = "editor/${Uri.encode(path)}"
    fun slideshow(key: String, index: Int) = "slideshow/${Uri.encode(key)}/$index"
}

private fun findItem(vm: GalleryViewModel, path: String, context: Context): MediaItem =
    vm.media.value.firstOrNull { it.path == path } ?: context.app.media.fileItem(File(path))

@Composable
private fun GalleryNavHost(vm: GalleryViewModel, s: AppSettings) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val keyArg = navArgument("key") { type = NavType.StringType }
    val indexArg = navArgument("index") { type = NavType.IntType }

    var openedStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!openedStart && s.startPage == StartPage.ALL) nav.navigate(Routes.album(AlbumKeys.ALL))
        openedStart = true
    }

    NavHost(nav, startDestination = Routes.ALBUMS) {
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                vm = vm,
                settings = s,
                hiddenMode = false,
                onOpenAlbum = { nav.navigate(Routes.album(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenHidden = { nav.navigate(Routes.HIDDEN) },
                onSlideshow = { nav.navigate(Routes.slideshow(it, 0)) },
                onBack = {},
            )
        }
        composable(Routes.HIDDEN) {
            HiddenGate(s, onCancel = { nav.popBackStack() }) {
                AlbumsScreen(
                    vm = vm,
                    settings = s,
                    hiddenMode = true,
                    onOpenAlbum = { nav.navigate(Routes.album(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenHidden = {},
                    onSlideshow = { nav.navigate(Routes.slideshow(it, 0)) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.ALBUM, arguments = listOf(keyArg)) { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            val content = @Composable {
                AlbumScreen(
                    vm = vm,
                    settings = s,
                    albumKey = key,
                    onOpen = { nav.navigate(Routes.viewer(key, it)) },
                    onSlideshow = { nav.navigate(Routes.slideshow(key, 0)) },
                    onBack = { nav.popBackStack() },
                )
            }
            if (key in s.hiddenAlbums) HiddenGate(s, onCancel = { nav.popBackStack() }) { content() } else content()
        }
        composable(Routes.VIEWER, arguments = listOf(keyArg, indexArg)) { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            val index = entry.arguments?.getInt("index") ?: 0
            val flow = remember(key) { vm.mediaFor(key) }
            val items by flow.collectAsState(initial = remember(key) { vm.snapshotFor(key) })
            val jumpTo by entry.savedStateHandle.getStateFlow<String?>("jumpTo", null).collectAsState()
            if (items.isEmpty()) {
                LaunchedEffect(Unit) { if (!vm.loading.value) nav.popBackStack() }
                return@composable
            }
            ViewerScreen(
                vm = vm,
                settings = s,
                items = items,
                startPath = null,
                startIndex = index,
                canModify = true,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editor(it.path)) },
                onSlideshow = { nav.navigate(Routes.slideshow(key, it)) },
                jumpTo = jumpTo,
                onJumped = { entry.savedStateHandle["jumpTo"] = null },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            val item = remember(path) { findItem(vm, path, context) }
            EditorScreen(vm, item, onDone = { saved ->
                if (saved != null) nav.previousBackStackEntry?.savedStateHandle?.set("jumpTo", saved)
                nav.popBackStack()
            })
        }
        composable(Routes.SLIDESHOW, arguments = listOf(keyArg, indexArg)) { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            val index = entry.arguments?.getInt("index") ?: 0
            val items = remember(key) { vm.snapshotFor(key) }
            SlideshowScreen(s, items, index, onExit = { last ->
                nav.previousBackStackEntry?.savedStateHandle?.set("jumpTo", last)
                nav.popBackStack()
            })
        }
        composable(Routes.SETTINGS) {
            SettingsHome(onOpen = { nav.navigate("settings/${it.name}") }, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_PAGE, arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
            val section = SettingsSection.valueOf(entry.arguments?.getString("section") ?: SettingsSection.INTERFACE.name)
            SettingsPage(section, vm, s, onBack = { nav.popBackStack() })
        }
    }
}

/** Requires unlocking before showing hidden albums when a password is set. */
@Composable
private fun HiddenGate(s: AppSettings, onCancel: () -> Unit, content: @Composable () -> Unit) {
    var unlocked by remember { mutableStateOf(LockManager.isUnlocked(s)) }
    if (!unlocked) {
        LockScreen(
            type = s.lockType,
            biometric = s.biometric,
            title = "解鎖以查看隱藏的相簿",
            onCancel = onCancel,
            check = { LockManager.verify(it, s) },
            onUnlocked = { LockManager.markUnlocked(); unlocked = true },
        )
    } else {
        content()
    }
}
