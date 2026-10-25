package com.jimmyyu.qvgallery.data

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(private val labelRes: Int) {
    SYSTEM(R.string.follow_system), LIGHT(R.string.light), DARK(R.string.dark);

    val label: String get() = tr(labelRes)
}
enum class AlbumViewMode(private val labelRes: Int) {
    GRID(R.string.grid), LIST(R.string.list);

    val label: String get() = tr(labelRes)
}
enum class LockType(private val labelRes: Int) {
    NONE(R.string.none), PATTERN(R.string.pattern), PIN(R.string.pin);

    val label: String get() = tr(labelRes)
}
enum class LockScope(private val labelRes: Int) {
    APP(R.string.scope_app), HIDDEN(R.string.scope_hidden);

    val label: String get() = tr(labelRes)
}
enum class SlideTransition(private val labelRes: Int) {
    NONE(R.string.none), FADE(R.string.tr_fade), SLIDE(R.string.tr_slide), ZOOM(R.string.tr_zoom), KEN_BURNS(R.string.tr_kenburns);

    val label: String get() = tr(labelRes)
}
enum class ViewerBackground(private val labelRes: Int) {
    BLACK(R.string.black), THEME(R.string.follow_theme), WHITE(R.string.white);

    val label: String get() = tr(labelRes)
}
enum class StartPage(private val labelRes: Int) {
    ALBUMS(R.string.albums), ALL(R.string.all_media);

    val label: String get() = tr(labelRes)
}

data class AppSettings(
    // Interface
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val accentColor: Long = 0xFF5C6BC0,
    val backgroundImage: String? = null,
    val backgroundDim: Float = 0.45f,
    val startPage: StartPage = StartPage.ALBUMS,
    // Albums
    val albumViewMode: AlbumViewMode = AlbumViewMode.GRID,
    val albumColumns: Int = 2,
    val mediaColumns: Int = 3,
    val albumSort: AlbumSort = AlbumSort.NAME,
    val albumSortAsc: Boolean = true,
    val mediaSort: MediaSort = MediaSort.DATE_TAKEN,
    val mediaSortAsc: Boolean = false,
    val showVideos: Boolean = true,
    val showAlbumPath: Boolean = true,
    val hiddenAlbums: Set<String> = emptySet(),
    val excludedAlbums: Set<String> = emptySet(),
    val pinnedAlbums: Set<String> = emptySet(),
    val extraFolders: Set<String> = emptySet(),
    // View
    val viewerBackground: ViewerBackground = ViewerBackground.BLACK,
    val keepScreenOn: Boolean = true,
    val maxBrightness: Boolean = false,
    val volumeKeysNavigate: Boolean = true,
    val showInfoBar: Boolean = true,
    val hideBarsOnOpen: Boolean = false,
    val maxZoom: Float = 8f,
    val doubleTapZoom: Float = 2.5f,
    val autoPlayVideo: Boolean = true,
    val loopVideo: Boolean = true,
    val confirmDelete: Boolean = true,
    // Security
    val lockType: LockType = LockType.NONE,
    val lockHash: String = "",
    val lockSalt: String = "",
    val biometric: Boolean = false,
    val lockScope: LockScope = LockScope.HIDDEN,
    val relockSeconds: Int = 30,
    // Slideshow
    val slideInterval: Int = 4,
    val slideTransition: SlideTransition = SlideTransition.FADE,
    val slideShuffle: Boolean = false,
    val slideLoop: Boolean = true,
    val slideIncludeVideos: Boolean = false,
    // Cache
    val diskCacheMb: Int = 512,
    // Updates
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: Int = 0,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val themeMode = stringPreferencesKey("themeMode")
        val amoled = booleanPreferencesKey("amoled")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val accentColor = longPreferencesKey("accentColor")
        val backgroundImage = stringPreferencesKey("backgroundImage")
        val backgroundDim = floatPreferencesKey("backgroundDim")
        val startPage = stringPreferencesKey("startPage")
        val albumViewMode = stringPreferencesKey("albumViewMode")
        val albumColumns = intPreferencesKey("albumColumns")
        val mediaColumns = intPreferencesKey("mediaColumns")
        val albumSort = stringPreferencesKey("albumSort")
        val albumSortAsc = booleanPreferencesKey("albumSortAsc")
        val mediaSort = stringPreferencesKey("mediaSort")
        val mediaSortAsc = booleanPreferencesKey("mediaSortAsc")
        val showVideos = booleanPreferencesKey("showVideos")
        val showAlbumPath = booleanPreferencesKey("showAlbumPath")
        val hiddenAlbums = stringSetPreferencesKey("hiddenAlbums")
        val excludedAlbums = stringSetPreferencesKey("excludedAlbums")
        val pinnedAlbums = stringSetPreferencesKey("pinnedAlbums")
        val extraFolders = stringSetPreferencesKey("extraFolders")
        val viewerBackground = stringPreferencesKey("viewerBackground")
        val keepScreenOn = booleanPreferencesKey("keepScreenOn")
        val maxBrightness = booleanPreferencesKey("maxBrightness")
        val volumeKeysNavigate = booleanPreferencesKey("volumeKeysNavigate")
        val showInfoBar = booleanPreferencesKey("showInfoBar")
        val hideBarsOnOpen = booleanPreferencesKey("hideBarsOnOpen")
        val maxZoom = floatPreferencesKey("maxZoom")
        val doubleTapZoom = floatPreferencesKey("doubleTapZoom")
        val autoPlayVideo = booleanPreferencesKey("autoPlayVideo")
        val loopVideo = booleanPreferencesKey("loopVideo")
        val confirmDelete = booleanPreferencesKey("confirmDelete")
        val lockType = stringPreferencesKey("lockType")
        val lockHash = stringPreferencesKey("lockHash")
        val lockSalt = stringPreferencesKey("lockSalt")
        val biometric = booleanPreferencesKey("biometric")
        val lockScope = stringPreferencesKey("lockScope")
        val relockSeconds = intPreferencesKey("relockSeconds")
        val slideInterval = intPreferencesKey("slideInterval")
        val slideTransition = stringPreferencesKey("slideTransition")
        val slideShuffle = booleanPreferencesKey("slideShuffle")
        val slideLoop = booleanPreferencesKey("slideLoop")
        val slideIncludeVideos = booleanPreferencesKey("slideIncludeVideos")
        val diskCacheMb = intPreferencesKey("diskCacheMb")
        val autoCheckUpdates = booleanPreferencesKey("autoCheckUpdates")
        val lastUpdateCheck = longPreferencesKey("lastUpdateCheck")
        val skippedVersion = intPreferencesKey("skippedVersion")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enum(K.themeMode, d.themeMode),
            amoled = this[K.amoled] ?: d.amoled,
            dynamicColor = this[K.dynamicColor] ?: d.dynamicColor,
            accentColor = this[K.accentColor] ?: d.accentColor,
            backgroundImage = this[K.backgroundImage]?.takeIf { it.isNotEmpty() },
            backgroundDim = this[K.backgroundDim] ?: d.backgroundDim,
            startPage = enum(K.startPage, d.startPage),
            albumViewMode = enum(K.albumViewMode, d.albumViewMode),
            albumColumns = this[K.albumColumns] ?: d.albumColumns,
            mediaColumns = this[K.mediaColumns] ?: d.mediaColumns,
            albumSort = enum(K.albumSort, d.albumSort),
            albumSortAsc = this[K.albumSortAsc] ?: d.albumSortAsc,
            mediaSort = enum(K.mediaSort, d.mediaSort),
            mediaSortAsc = this[K.mediaSortAsc] ?: d.mediaSortAsc,
            showVideos = this[K.showVideos] ?: d.showVideos,
            showAlbumPath = this[K.showAlbumPath] ?: d.showAlbumPath,
            hiddenAlbums = this[K.hiddenAlbums] ?: d.hiddenAlbums,
            excludedAlbums = this[K.excludedAlbums] ?: d.excludedAlbums,
            pinnedAlbums = this[K.pinnedAlbums] ?: d.pinnedAlbums,
            extraFolders = this[K.extraFolders] ?: d.extraFolders,
            viewerBackground = enum(K.viewerBackground, d.viewerBackground),
            keepScreenOn = this[K.keepScreenOn] ?: d.keepScreenOn,
            maxBrightness = this[K.maxBrightness] ?: d.maxBrightness,
            volumeKeysNavigate = this[K.volumeKeysNavigate] ?: d.volumeKeysNavigate,
            showInfoBar = this[K.showInfoBar] ?: d.showInfoBar,
            hideBarsOnOpen = this[K.hideBarsOnOpen] ?: d.hideBarsOnOpen,
            maxZoom = this[K.maxZoom] ?: d.maxZoom,
            doubleTapZoom = this[K.doubleTapZoom] ?: d.doubleTapZoom,
            autoPlayVideo = this[K.autoPlayVideo] ?: d.autoPlayVideo,
            loopVideo = this[K.loopVideo] ?: d.loopVideo,
            confirmDelete = this[K.confirmDelete] ?: d.confirmDelete,
            lockType = enum(K.lockType, d.lockType),
            lockHash = this[K.lockHash] ?: d.lockHash,
            lockSalt = this[K.lockSalt] ?: d.lockSalt,
            biometric = this[K.biometric] ?: d.biometric,
            lockScope = enum(K.lockScope, d.lockScope),
            relockSeconds = this[K.relockSeconds] ?: d.relockSeconds,
            slideInterval = this[K.slideInterval] ?: d.slideInterval,
            slideTransition = enum(K.slideTransition, d.slideTransition),
            slideShuffle = this[K.slideShuffle] ?: d.slideShuffle,
            slideLoop = this[K.slideLoop] ?: d.slideLoop,
            slideIncludeVideos = this[K.slideIncludeVideos] ?: d.slideIncludeVideos,
            diskCacheMb = this[K.diskCacheMb] ?: d.diskCacheMb,
            autoCheckUpdates = this[K.autoCheckUpdates] ?: d.autoCheckUpdates,
            lastUpdateCheck = this[K.lastUpdateCheck] ?: d.lastUpdateCheck,
            skippedVersion = this[K.skippedVersion] ?: d.skippedVersion,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[K.themeMode] = s.themeMode.name
        this[K.amoled] = s.amoled
        this[K.dynamicColor] = s.dynamicColor
        this[K.accentColor] = s.accentColor
        this[K.backgroundImage] = s.backgroundImage ?: ""
        this[K.backgroundDim] = s.backgroundDim
        this[K.startPage] = s.startPage.name
        this[K.albumViewMode] = s.albumViewMode.name
        this[K.albumColumns] = s.albumColumns
        this[K.mediaColumns] = s.mediaColumns
        this[K.albumSort] = s.albumSort.name
        this[K.albumSortAsc] = s.albumSortAsc
        this[K.mediaSort] = s.mediaSort.name
        this[K.mediaSortAsc] = s.mediaSortAsc
        this[K.showVideos] = s.showVideos
        this[K.showAlbumPath] = s.showAlbumPath
        this[K.hiddenAlbums] = s.hiddenAlbums
        this[K.excludedAlbums] = s.excludedAlbums
        this[K.pinnedAlbums] = s.pinnedAlbums
        this[K.extraFolders] = s.extraFolders
        this[K.viewerBackground] = s.viewerBackground.name
        this[K.keepScreenOn] = s.keepScreenOn
        this[K.maxBrightness] = s.maxBrightness
        this[K.volumeKeysNavigate] = s.volumeKeysNavigate
        this[K.showInfoBar] = s.showInfoBar
        this[K.hideBarsOnOpen] = s.hideBarsOnOpen
        this[K.maxZoom] = s.maxZoom
        this[K.doubleTapZoom] = s.doubleTapZoom
        this[K.autoPlayVideo] = s.autoPlayVideo
        this[K.loopVideo] = s.loopVideo
        this[K.confirmDelete] = s.confirmDelete
        this[K.lockType] = s.lockType.name
        this[K.lockHash] = s.lockHash
        this[K.lockSalt] = s.lockSalt
        this[K.biometric] = s.biometric
        this[K.lockScope] = s.lockScope.name
        this[K.relockSeconds] = s.relockSeconds
        this[K.slideInterval] = s.slideInterval
        this[K.slideTransition] = s.slideTransition.name
        this[K.slideShuffle] = s.slideShuffle
        this[K.slideLoop] = s.slideLoop
        this[K.slideIncludeVideos] = s.slideIncludeVideos
        this[K.diskCacheMb] = s.diskCacheMb
        this[K.autoCheckUpdates] = s.autoCheckUpdates
        this[K.lastUpdateCheck] = s.lastUpdateCheck
        this[K.skippedVersion] = s.skippedVersion
    }
}
