package com.jimmyyu.qvgallery.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import com.jimmyyu.qvgallery.BuildConfig
import com.jimmyyu.qvgallery.data.AlbumViewMode
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.LockManager
import com.jimmyyu.qvgallery.data.LockScope
import com.jimmyyu.qvgallery.data.LockType
import com.jimmyyu.qvgallery.data.SlideTransition
import com.jimmyyu.qvgallery.data.StartPage
import com.jimmyyu.qvgallery.data.ThemeMode
import com.jimmyyu.qvgallery.data.ViewerBackground
import com.jimmyyu.qvgallery.ui.lock.LockScreen
import com.jimmyyu.qvgallery.ui.lock.SetupLockScreen
import com.jimmyyu.qvgallery.ui.theme.AccentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    INTERFACE("介面", "外觀與介面行為", Icons.Filled.ToggleOn),
    ALBUMS("相簿", "掃描相簿、排序、顯示方式", Icons.Outlined.Image),
    VIEW("檢視", "瀏覽行為、控制、圖片背景", Icons.Outlined.RemoveRedEye),
    SECURITY("安全性", "設定保護密碼", Icons.Filled.Lock),
    SLIDESHOW("幻燈片", "動畫與切換效果", Icons.Filled.Slideshow),
    CACHE("快取", "快取載入與清除", Icons.Filled.Cached),
    ABOUT("關於", "這個 App 的資訊", Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text(title) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) { content() }
    }
}

@Composable
fun SettingsHome(onOpen: (SettingsSection) -> Unit, onBack: () -> Unit) {
    SettingsScaffold("設定", onBack) {
        SettingsSection.entries.forEach { s ->
            Pref(title = s.title, summary = s.subtitle, icon = s.icon, onClick = { onOpen(s) })
        }
    }
}

@Composable
fun SettingsPage(section: SettingsSection, vm: GalleryViewModel, settings: AppSettings, onBack: () -> Unit) {
    when (section) {
        SettingsSection.INTERFACE -> InterfaceSettings(vm, settings, onBack)
        SettingsSection.ALBUMS -> AlbumSettings(vm, settings, onBack)
        SettingsSection.VIEW -> ViewSettings(vm, settings, onBack)
        SettingsSection.SECURITY -> SecuritySettings(vm, settings, onBack)
        SettingsSection.SLIDESHOW -> SlideshowSettings(vm, settings, onBack)
        SettingsSection.CACHE -> CacheSettings(vm, settings, onBack)
        SettingsSection.ABOUT -> AboutPage(onBack)
    }
}

// ---------------- Sections ----------------

@Composable
private fun InterfaceSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    var accentDialog by remember { mutableStateOf(false) }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.updateSettings { it.copy(backgroundImage = uri.toString()) }
    }
    SettingsScaffold("介面", onBack) {
        Header("主題")
        ChoicePref("主題模式", s.themeMode, ThemeMode.entries, { it.label }) { v -> vm.updateSettings { it.copy(themeMode = v) } }
        SwitchPref("純黑背景", "深色模式使用純黑色 (AMOLED)", s.amoled) { v -> vm.updateSettings { it.copy(amoled = v) } }
        SwitchPref("Material You 動態配色", "跟隨系統桌布取色", s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
        Pref(
            "主題色",
            if (s.dynamicColor) "關閉動態配色後可選擇" else "點擊選擇顏色",
            enabled = !s.dynamicColor,
            trailing = { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(s.accentColor.toInt()))) },
            onClick = { accentDialog = true },
        )
        Header("相簿頁背景")
        Pref(
            "背景圖片",
            if (s.backgroundImage == null) "未設定" else "已設定，點擊更換",
            onClick = { pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            trailing = if (s.backgroundImage != null) {
                { IconButton(onClick = { vm.updateSettings { it.copy(backgroundImage = null) } }) { Icon(Icons.Filled.Close, "清除") } }
            } else null,
        )
        SliderPref("背景暗度", s.backgroundDim, 0f..0.9f, steps = 8, format = { "${(it * 100).toInt()}%" }) { v ->
            vm.updateSettings { it.copy(backgroundDim = v) }
        }
        Header("啟動")
        ChoicePref("啟動時顯示", s.startPage, StartPage.entries, { it.label }) { v -> vm.updateSettings { it.copy(startPage = v) } }
    }
    if (accentDialog) {
        AlertDialog(
            onDismissRequest = { accentDialog = false },
            title = { Text("主題色") },
            text = {
                LazyVerticalGrid(GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccentColors) { c ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Color(c.toInt()))
                                .clickable { vm.updateSettings { it.copy(accentColor = c) }; accentDialog = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == s.accentColor) Icon(Icons.Filled.Check, null, tint = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accentDialog = false }) { Text("關閉") } },
        )
    }
}

@Composable
private fun AlbumSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var pickExtra by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("相簿", onBack) {
        Header("顯示")
        ChoicePref("相簿檢視", s.albumViewMode, AlbumViewMode.entries, { it.label }) { v -> vm.updateSettings { it.copy(albumViewMode = v) } }
        ChoicePref("相簿每列數量", s.albumColumns, (1..5).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(albumColumns = v) } }
        ChoicePref("相片每列數量", s.mediaColumns, (2..8).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(mediaColumns = v) } }
        SwitchPref("顯示影片", "在相簿中包含影片", s.showVideos) { v -> vm.updateSettings { it.copy(showVideos = v) } }
        SwitchPref("顯示相簿路徑", "清單檢視中顯示資料夾路徑", s.showAlbumPath) { v -> vm.updateSettings { it.copy(showAlbumPath = v) } }
        Header("掃描")
        Pref("額外掃描的資料夾", "${s.extraFolders.size} 個 · 用於含 .nomedia 的資料夾或 USB 隨身碟", onClick = { manage = "extra" })
        Pref("排除的資料夾", "${s.excludedAlbums.size} 個", onClick = { manage = "excluded" })
        Pref("隱藏的相簿", "${s.hiddenAlbums.size} 個", onClick = { manage = "hidden" })
        if (!Environment.isExternalStorageManager()) {
            Pref("授予「所有檔案存取權」", "移動、重新命名、掃描額外資料夾需要此權限", onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            })
        }
        Pref("重新掃描", "立即重新讀取所有媒體", onClick = { vm.reload(); vm.toast("重新掃描中…") })
    }
    if (pickExtra) {
        FolderPickerDialog(
            title = "選擇要掃描的資料夾",
            albums = emptyList(),
            onPick = { f -> vm.updateSettings { it.copy(extraFolders = it.extraFolders + f.absolutePath) } },
            onDismiss = { pickExtra = false },
        )
    }
    manage?.let { kind ->
        val (title, set) = when (kind) {
            "extra" -> "額外掃描的資料夾" to s.extraFolders
            "excluded" -> "排除的資料夾" to s.excludedAlbums
            else -> "隱藏的相簿" to s.hiddenAlbums
        }
        AlertDialog(
            onDismissRequest = { manage = null },
            title = { Text(title) },
            text = {
                if (set.isEmpty()) Text("(無)")
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(set.sorted()) { path ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayPath(path), Modifier.weight(1f).padding(vertical = 8.dp))
                            IconButton(onClick = {
                                vm.updateSettings {
                                    when (kind) {
                                        "extra" -> it.copy(extraFolders = it.extraFolders - path)
                                        "excluded" -> it.copy(excludedAlbums = it.excludedAlbums - path)
                                        else -> it.copy(hiddenAlbums = it.hiddenAlbums - path)
                                    }
                                }
                            }) { Icon(Icons.Filled.Close, "移除") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manage = null }) { Text("關閉") } },
            dismissButton = if (kind == "extra") {
                { TextButton(onClick = { pickExtra = true }) { Text("新增") } }
            } else null,
        )
    }
}

@Composable
private fun ViewSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    SettingsScaffold("檢視", onBack) {
        Header("外觀")
        ChoicePref("圖片背景", s.viewerBackground, ViewerBackground.entries, { it.label }) { v -> vm.updateSettings { it.copy(viewerBackground = v) } }
        SwitchPref("顯示資訊列", "底部顯示解析度、日期與縮放按鈕", s.showInfoBar) { v -> vm.updateSettings { it.copy(showInfoBar = v) } }
        SwitchPref("開啟時隱藏工具列", "直接以全螢幕顯示圖片", s.hideBarsOnOpen) { v -> vm.updateSettings { it.copy(hideBarsOnOpen = v) } }
        SliderPref("最大縮放倍數", s.maxZoom, 2f..20f, steps = 17, format = { "${it.toInt()}x" }) { v -> vm.updateSettings { it.copy(maxZoom = v) } }
        Header("行為")
        SwitchPref("保持螢幕開啟", "瀏覽時不自動關閉螢幕", s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
        SwitchPref("最大亮度", "瀏覽時將螢幕亮度調到最高", s.maxBrightness) { v -> vm.updateSettings { it.copy(maxBrightness = v) } }
        SwitchPref("音量鍵切換", "音量下 = 下一張，音量上 = 上一張", s.volumeKeysNavigate) { v -> vm.updateSettings { it.copy(volumeKeysNavigate = v) } }
        SwitchPref("刪除前確認", null, s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
        Header("影片")
        SwitchPref("自動播放", "滑到影片時自動開始播放", s.autoPlayVideo) { v -> vm.updateSettings { it.copy(autoPlayVideo = v) } }
        SwitchPref("循環播放", null, s.loopVideo) { v -> vm.updateSettings { it.copy(loopVideo = v) } }
    }
}

@Composable
private fun SecuritySettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    var setupType by remember { mutableStateOf<LockType?>(null) }
    var verifyThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pending = verifyThen
    if (pending != null) {
        LockScreen(
            type = s.lockType,
            biometric = false,
            title = "輸入目前的密碼",
            onCancel = { verifyThen = null },
            check = { LockManager.verify(it, s) },
            onUnlocked = { verifyThen = null; pending() },
        )
        return
    }
    setupType?.let { type ->
        SetupLockScreen(
            type = type,
            onDone = { secret ->
                val salt = LockManager.newSalt()
                val hash = LockManager.hash(secret, salt)
                vm.updateSettings { it.copy(lockType = type, lockSalt = salt, lockHash = hash) }
                LockManager.markUnlocked()
                setupType = null
                vm.toast("已設定密碼")
            },
            onCancel = { setupType = null },
        )
        return
    }
    val hasLock = s.lockType != LockType.NONE && s.lockHash.isNotEmpty()
    fun guarded(action: () -> Unit) = if (hasLock) verifyThen = action else action()
    SettingsScaffold("安全性", onBack) {
        Header("密碼保護")
        ChoicePref("鎖定方式", s.lockType, LockType.entries, { it.label }) { v ->
            guarded {
                if (v == LockType.NONE) vm.updateSettings { it.copy(lockType = LockType.NONE, lockHash = "", lockSalt = "") }
                else setupType = v
            }
        }
        if (hasLock) {
            ChoicePref("保護範圍", s.lockScope, LockScope.entries, { it.label }) { v -> guarded { vm.updateSettings { it.copy(lockScope = v) } } }
            SwitchPref("生物辨識解鎖", "使用指紋或臉部解鎖", s.biometric) { v -> vm.updateSettings { it.copy(biometric = v) } }
            ChoicePref(
                "離開後重新鎖定",
                s.relockSeconds,
                listOf(0, 30, 60, 300, 1800),
                { if (it == 0) "立即" else if (it < 60) "$it 秒" else "${it / 60} 分鐘" },
            ) { v -> vm.updateSettings { it.copy(relockSeconds = v) } }
            Pref("立即鎖定", null, onClick = { LockManager.lock(); vm.toast("已鎖定") })
        }
        Text(
            "「僅隱藏相簿」：開啟 App 不需密碼，查看隱藏的相簿時才需要解鎖。\n「開啟 App 時」：每次開啟 App 都需要解鎖。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SlideshowSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    SettingsScaffold("幻燈片", onBack) {
        SliderPref("每張停留時間", s.slideInterval.toFloat(), 1f..30f, steps = 28, format = { "${it.toInt()} 秒" }) { v ->
            vm.updateSettings { it.copy(slideInterval = v.toInt()) }
        }
        ChoicePref("切換效果", s.slideTransition, SlideTransition.entries, { it.label }) { v -> vm.updateSettings { it.copy(slideTransition = v) } }
        SwitchPref("隨機順序", null, s.slideShuffle) { v -> vm.updateSettings { it.copy(slideShuffle = v) } }
        SwitchPref("循環播放", "播完後從頭開始", s.slideLoop) { v -> vm.updateSettings { it.copy(slideLoop = v) } }
    }
}

@Composable
private fun CacheSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableLongStateOf(-1L) }
    fun refresh() = scope.launch { size = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }
    SettingsScaffold("快取", onBack) {
        Pref("目前快取大小", if (size < 0) "計算中…" else formatSize(context, size))
        ChoicePref("圖片快取上限", s.diskCacheMb, listOf(128, 256, 512, 1024, 2048), { "$it MB" }) { v ->
            vm.updateSettings { it.copy(diskCacheMb = v) }
            vm.toast("重新啟動 App 後生效")
        }
        Pref("清除快取", "刪除縮圖與圖片快取", onClick = {
            scope.launch {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
                withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                refresh()
                vm.toast("快取已清除")
            }
        })
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SettingsScaffold("關於", onBack) {
        Pref("QV Gallery", "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Pref("說明", "受 QuickPic 啟發、為 64 位元 Android 重新撰寫的輕量圖片瀏覽器。無廣告、離線運作。")
        Pref("裝置", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        Pref("開源元件", "Jetpack Compose、Coil、Telephoto、Media3 (Apache 2.0)")
    }
}

// ---------------- Preference widgets ----------------

@Composable
fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun Pref(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            Spacer(Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchPref(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Pref(title, summary, trailing = { Switch(checked = checked, onCheckedChange = onChange) }, onClick = { onChange(!checked) })
}

@Composable
fun <T> ChoicePref(title: String, value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Pref(title, label(value), onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open = false; onChange(o) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == value, onClick = { open = false; onChange(o) })
                            Text(label(o))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

@Composable
fun SliderPref(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            steps = steps,
        )
    }
}
