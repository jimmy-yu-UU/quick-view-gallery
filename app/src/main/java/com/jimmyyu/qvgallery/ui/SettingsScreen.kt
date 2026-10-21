package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.content.Intent
import android.net.Uri
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
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

enum class SettingsSection(private val titleRes: Int, private val subtitleRes: Int, val icon: ImageVector) {
    INTERFACE(R.string.sec_interface, R.string.sec_interface_sub, Icons.Filled.ToggleOn),
    ALBUMS(R.string.albums, R.string.sec_albums_sub, Icons.Outlined.Image),
    VIEW(R.string.sec_view, R.string.sec_view_sub, Icons.Outlined.RemoveRedEye),
    SECURITY(R.string.sec_security, R.string.sec_security_sub, Icons.Filled.Lock),
    SLIDESHOW(R.string.slideshow, R.string.sec_slideshow_sub, Icons.Filled.Slideshow),
    CACHE(R.string.sec_cache, R.string.sec_cache_sub, Icons.Filled.Cached),
    ABOUT(R.string.sec_about, R.string.sec_about_sub, Icons.Outlined.Info);

    val title: String get() = tr(titleRes)
    val subtitle: String get() = tr(subtitleRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } },
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
    SettingsScaffold(tr(R.string.settings), onBack) {
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
    SettingsScaffold(tr(R.string.sec_interface), onBack) {
        Header(tr(R.string.theme))
        ChoicePref(tr(R.string.theme_mode), s.themeMode, ThemeMode.entries, { it.label }) { v -> vm.updateSettings { it.copy(themeMode = v) } }
        SwitchPref(tr(R.string.amoled), tr(R.string.amoled_sub), s.amoled) { v -> vm.updateSettings { it.copy(amoled = v) } }
        SwitchPref(tr(R.string.dynamic_color), tr(R.string.dynamic_color_sub), s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
        Pref(
            tr(R.string.accent),
            if (s.dynamicColor) tr(R.string.accent_disabled) else tr(R.string.accent_pick),
            enabled = !s.dynamicColor,
            trailing = { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(s.accentColor.toInt()))) },
            onClick = { accentDialog = true },
        )
        Header(tr(R.string.album_background))
        Pref(
            tr(R.string.background_image),
            if (s.backgroundImage == null) tr(R.string.not_set) else tr(R.string.set_tap_change),
            onClick = { pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            trailing = if (s.backgroundImage != null) {
                { IconButton(onClick = { vm.updateSettings { it.copy(backgroundImage = null) } }) { Icon(Icons.Filled.Close, tr(R.string.clear)) } }
            } else null,
        )
        SliderPref(tr(R.string.background_dim), s.backgroundDim, 0f..0.9f, steps = 8, format = { "${(it * 100).toInt()}%" }) { v ->
            vm.updateSettings { it.copy(backgroundDim = v) }
        }
        LanguagePref()
        Header(tr(R.string.startup))
        ChoicePref(tr(R.string.start_page), s.startPage, StartPage.entries, { it.label }) { v -> vm.updateSettings { it.copy(startPage = v) } }
    }
    if (accentDialog) {
        AlertDialog(
            onDismissRequest = { accentDialog = false },
            title = { Text(tr(R.string.accent)) },
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
            confirmButton = { TextButton(onClick = { accentDialog = false }) { Text(tr(R.string.close)) } },
        )
    }
}

@Composable
private fun AlbumSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var manage by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    SettingsScaffold(tr(R.string.albums), onBack) {
        Header(tr(R.string.display))
        ChoicePref(tr(R.string.album_view), s.albumViewMode, AlbumViewMode.entries, { it.label }) { v -> vm.updateSettings { it.copy(albumViewMode = v) } }
        ChoicePref(tr(R.string.album_columns), s.albumColumns, (1..5).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(albumColumns = v) } }
        ChoicePref(tr(R.string.media_columns), s.mediaColumns, (2..8).toList(), { it.toString() }) { v -> vm.updateSettings { it.copy(mediaColumns = v) } }
        SwitchPref(tr(R.string.show_videos), tr(R.string.show_videos_sub), s.showVideos) { v -> vm.updateSettings { it.copy(showVideos = v) } }
        SwitchPref(tr(R.string.show_album_path), tr(R.string.show_album_path_sub), s.showAlbumPath) { v -> vm.updateSettings { it.copy(showAlbumPath = v) } }
        Header(tr(R.string.scanning))
        Pref(tr(R.string.extra_folders), tr(R.string.extra_folders_sub, s.extraFolders.size), onClick = { manage = "extra" })
        Pref(tr(R.string.excluded_folders), tr(R.string.n_excluded, s.excludedAlbums.size), onClick = { manage = "excluded" })
        Pref(tr(R.string.hidden_albums), tr(R.string.n_hidden, s.hiddenAlbums.size), onClick = { manage = "hidden" })
        if (!Environment.isExternalStorageManager()) {
            Pref(tr(R.string.grant_all_files), tr(R.string.grant_all_files_sub), onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            })
        }
        Pref(tr(R.string.rescan), tr(R.string.rescan_sub), onClick = { vm.reload(); vm.toast(tr(R.string.rescanning)) })
    }
    pickFor?.let { kind ->
        // Picking replaces the list dialog; it comes back afterwards so the result is visible.
        val known = (vm.albums.value + vm.hiddenAlbums.value).map { it.path }
        FolderPickerDialog(
            title = tr(
                when (kind) {
                    "extra" -> R.string.pick_scan_folder
                    "excluded" -> R.string.add_excluded
                    else -> R.string.add_hidden
                }
            ),
            albums = when (kind) {
                "extra" -> emptyList()
                "excluded" -> known.filter { it !in s.excludedAlbums }
                else -> known.filter { it !in s.hiddenAlbums }
            },
            onPick = { f ->
                val path = f.absolutePath
                vm.updateSettings {
                    when (kind) {
                        "extra" -> it.copy(extraFolders = it.extraFolders + path)
                        "excluded" -> it.copy(excludedAlbums = it.excludedAlbums + path)
                        else -> it.copy(hiddenAlbums = it.hiddenAlbums + path)
                    }
                }
            },
            onDismiss = { pickFor = null; manage = kind },
        )
    }
    manage?.let { kind ->
        val (title, set) = when (kind) {
            "extra" -> tr(R.string.extra_folders) to s.extraFolders
            "excluded" -> tr(R.string.excluded_folders) to s.excludedAlbums
            else -> tr(R.string.hidden_albums) to s.hiddenAlbums
        }
        val help = tr(
            when (kind) {
                "extra" -> R.string.extra_help
                "excluded" -> R.string.excluded_help
                else -> R.string.hidden_help
            }
        )
        AlertDialog(
            onDismissRequest = { manage = null },
            title = { Text(title) },
            text = {
                Column {
                    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    if (set.isEmpty()) Text(tr(R.string.none_paren), Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
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
                                }) { Icon(Icons.Filled.Close, tr(R.string.remove)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manage = null }) { Text(tr(R.string.close)) } },
            dismissButton = { TextButton(onClick = { manage = null; pickFor = kind }) { Text(tr(R.string.add)) } },
        )
    }
}

@Composable
private fun ViewSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    SettingsScaffold(tr(R.string.sec_view), onBack) {
        Header(tr(R.string.appearance))
        ChoicePref(tr(R.string.viewer_background), s.viewerBackground, ViewerBackground.entries, { it.label }) { v -> vm.updateSettings { it.copy(viewerBackground = v) } }
        SwitchPref(tr(R.string.show_info_bar), tr(R.string.show_info_bar_sub), s.showInfoBar) { v -> vm.updateSettings { it.copy(showInfoBar = v) } }
        SwitchPref(tr(R.string.hide_bars_on_open), tr(R.string.hide_bars_on_open_sub), s.hideBarsOnOpen) { v -> vm.updateSettings { it.copy(hideBarsOnOpen = v) } }
        SliderPref(tr(R.string.max_zoom), s.maxZoom, 2f..20f, steps = 17, format = { "${it.toInt()}x" }) { v -> vm.updateSettings { it.copy(maxZoom = v) } }
        Header(tr(R.string.behavior))
        SwitchPref(tr(R.string.keep_screen_on), tr(R.string.keep_screen_on_sub), s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
        SwitchPref(tr(R.string.max_brightness), tr(R.string.max_brightness_sub), s.maxBrightness) { v -> vm.updateSettings { it.copy(maxBrightness = v) } }
        SwitchPref(tr(R.string.volume_keys), tr(R.string.volume_keys_sub), s.volumeKeysNavigate) { v -> vm.updateSettings { it.copy(volumeKeysNavigate = v) } }
        SwitchPref(tr(R.string.confirm_delete), null, s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
        Header(tr(R.string.videos))
        SwitchPref(tr(R.string.autoplay), tr(R.string.autoplay_sub), s.autoPlayVideo) { v -> vm.updateSettings { it.copy(autoPlayVideo = v) } }
        SwitchPref(tr(R.string.loop), null, s.loopVideo) { v -> vm.updateSettings { it.copy(loopVideo = v) } }
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
            title = tr(R.string.enter_current_password),
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
                vm.toast(tr(R.string.password_set))
            },
            onCancel = { setupType = null },
        )
        return
    }
    val hasLock = s.lockType != LockType.NONE && s.lockHash.isNotEmpty()
    fun guarded(action: () -> Unit) = if (hasLock) verifyThen = action else action()
    SettingsScaffold(tr(R.string.sec_security), onBack) {
        Header(tr(R.string.password_protection))
        ChoicePref(tr(R.string.lock_type), s.lockType, LockType.entries, { it.label }) { v ->
            guarded {
                if (v == LockType.NONE) vm.updateSettings { it.copy(lockType = LockType.NONE, lockHash = "", lockSalt = "") }
                else setupType = v
            }
        }
        if (hasLock) {
            ChoicePref(tr(R.string.lock_scope), s.lockScope, LockScope.entries, { it.label }) { v -> guarded { vm.updateSettings { it.copy(lockScope = v) } } }
            SwitchPref(tr(R.string.biometric_unlock), tr(R.string.biometric_unlock_sub), s.biometric) { v -> vm.updateSettings { it.copy(biometric = v) } }
            ChoicePref(
                tr(R.string.relock_after),
                s.relockSeconds,
                listOf(0, 30, 60, 300, 1800),
                { if (it == 0) tr(R.string.immediately) else if (it < 60) tr(R.string.n_seconds, it) else tr(R.string.n_minutes, it / 60) },
            ) { v -> vm.updateSettings { it.copy(relockSeconds = v) } }
            Pref(tr(R.string.lock_now), null, onClick = { LockManager.lock(); vm.toast(tr(R.string.locked)) })
        }
        Text(
            tr(R.string.lock_scope_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SlideshowSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    SettingsScaffold(tr(R.string.slideshow), onBack) {
        SliderPref(tr(R.string.slide_duration), s.slideInterval.toFloat(), 1f..30f, steps = 28, format = { tr(R.string.n_seconds_short, it.toInt()) }) { v ->
            vm.updateSettings { it.copy(slideInterval = v.toInt()) }
        }
        ChoicePref(tr(R.string.transition), s.slideTransition, SlideTransition.entries, { it.label }) { v -> vm.updateSettings { it.copy(slideTransition = v) } }
        SwitchPref(tr(R.string.shuffle), null, s.slideShuffle) { v -> vm.updateSettings { it.copy(slideShuffle = v) } }
        SwitchPref(tr(R.string.loop), tr(R.string.loop_sub), s.slideLoop) { v -> vm.updateSettings { it.copy(slideLoop = v) } }
    }
}

@Composable
private fun CacheSettings(vm: GalleryViewModel, s: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableLongStateOf(-1L) }
    fun refresh() = scope.launch { size = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }
    SettingsScaffold(tr(R.string.sec_cache), onBack) {
        Pref(tr(R.string.cache_size), if (size < 0) tr(R.string.calculating) else formatSize(context, size))
        ChoicePref(tr(R.string.cache_limit), s.diskCacheMb, listOf(128, 256, 512, 1024, 2048), { "$it MB" }) { v ->
            vm.updateSettings { it.copy(diskCacheMb = v) }
            vm.toast(tr(R.string.restart_to_apply))
        }
        Pref(tr(R.string.clear_cache), tr(R.string.clear_cache_sub), onClick = {
            scope.launch {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
                withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                refresh()
                vm.toast(tr(R.string.cache_cleared))
            }
        })
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SettingsScaffold(tr(R.string.sec_about), onBack) {
        Pref("QV Gallery", tr(R.string.version_fmt, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Pref(tr(R.string.description), tr(R.string.about_body))
        Pref(tr(R.string.device), "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        Pref(tr(R.string.open_source), "Jetpack Compose, Coil, Telephoto, Media3 (Apache 2.0)")
    }
}

/** Languages with translations, shown in their own names. Empty tag = follow the system. */
private val languages = listOf(
    "en" to "English",
    "zh-TW" to "繁體中文 (台灣)",
    "zh-HK" to "繁體中文 (香港)",
    "zh-CN" to "简体中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "es" to "Español",
    "pt-BR" to "Português (Brasil)",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "ru" to "Русский",
    "pl" to "Polski",
    "tr" to "Türkçe",
    "vi" to "Tiếng Việt",
    "th" to "ไทย",
    "id" to "Bahasa Indonesia",
    "hi" to "हिन्दी",
    "ar" to "العربية",
)

/** Per-app language (Android 13+), the same setting as System settings > Apps > Language. */
@Composable
private fun LanguagePref() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Pref(tr(R.string.language), tr(R.string.language_unsupported))
        return
    }
    val manager = context.getSystemService(LocaleManager::class.java)
    val current = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)
    val currentTag = current?.let { locale ->
        val tag = locale.toLanguageTag()
        languages.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.first
            ?: when {
                locale.language == "zh" && (locale.country == "CN" || locale.script == "Hans") -> "zh-CN"
                locale.language == "zh" && locale.country == "HK" -> "zh-HK"
                locale.language == "zh" -> "zh-TW"
                else -> languages.firstOrNull { it.first.substringBefore('-') == locale.language }?.first
            }
    } ?: ""
    val names = languages.toMap()
    ChoicePref(tr(R.string.language), currentTag, listOf("") + languages.map { it.first }, { tag ->
        if (tag.isEmpty()) tr(R.string.follow_system) else names[tag] ?: tag
    }) { tag ->
        // The system recreates the activity with the new locale.
        manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
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
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr(R.string.cancel)) } },
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
