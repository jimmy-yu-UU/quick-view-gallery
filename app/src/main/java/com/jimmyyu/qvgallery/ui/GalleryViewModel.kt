package com.jimmyyu.qvgallery.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jimmyyu.qvgallery.app
import com.jimmyyu.qvgallery.data.Album
import com.jimmyyu.qvgallery.data.AlbumKeys
import com.jimmyyu.qvgallery.data.AlbumSort
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.MediaSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.app
    private val repo = app.media
    val fileOps = app.fileOps

    val settings: StateFlow<AppSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Visible albums (hidden/excluded removed). */
    val albums: StateFlow<List<Album>> = combine(_media, app.settings.settings) { media, s -> buildAlbums(media, s, hidden = false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenAlbums: StateFlow<List<Album>> = combine(_media, app.settings.settings) { media, s -> buildAlbums(media, s, hidden = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }

    init {
        application.contentResolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        reload()
        // Rescan when the list of extra folders changes.
        viewModelScope.launch {
            app.settings.settings.map { it.extraFolders }.distinctUntilChanged().drop(1).collect { reload() }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val s = app.settings.settings.first()
            _media.value = repo.loadAll(s.extraFolders)
            _loading.value = false
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(800)
            val s = app.settings.settings.first()
            _media.value = repo.loadAll(s.extraFolders)
        }
    }

    fun mediaFor(key: String): Flow<List<MediaItem>> =
        combine(_media, app.settings.settings) { media, s -> filterAndSort(media, s, key) }

    fun snapshotFor(key: String): List<MediaItem> {
        val s = settings.value ?: return emptyList()
        return filterAndSort(_media.value, s, key)
    }

    private fun filterAndSort(media: List<MediaItem>, s: AppSettings, key: String): List<MediaItem> {
        val blocked = s.excludedAlbums + s.hiddenAlbums
        val filtered = when (key) {
            AlbumKeys.ALL -> media.filter { it.folder !in blocked && (s.showVideos || !it.isVideo) }
            AlbumKeys.VIDEOS -> media.filter { it.isVideo && it.folder !in blocked }
            AlbumKeys.PHOTOS -> media.filter { !it.isVideo && it.folder !in blocked }
            else -> media.filter { it.folder == key && (s.showVideos || !it.isVideo) }
        }
        return sortMedia(filtered, s.mediaSort, s.mediaSortAsc)
    }

    private fun buildAlbums(media: List<MediaItem>, s: AppSettings, hidden: Boolean): List<Album> {
        val groups = media.asSequence()
            .filter { s.showVideos || !it.isVideo }
            .groupBy { it.folder }
        val albums = groups.mapNotNull { (folder, items) ->
            if (folder in s.excludedAlbums) return@mapNotNull null
            val isHidden = folder in s.hiddenAlbums
            if (isHidden != hidden) return@mapNotNull null
            val sorted = sortMedia(items, s.mediaSort, s.mediaSortAsc)
            Album(
                path = folder,
                name = folder.substringAfterLast('/').ifEmpty { folder },
                count = items.size,
                cover = sorted.first(),
                latest = items.maxOf { it.dateTaken },
                totalSize = items.sumOf { it.size },
                videoCount = items.count { it.isVideo },
                isPinned = folder in s.pinnedAlbums,
                isHidden = isHidden,
            )
        }
        val cmp: Comparator<Album> = when (s.albumSort) {
            AlbumSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            AlbumSort.DATE -> compareBy { it.latest }
            AlbumSort.SIZE -> compareBy { it.totalSize }
            AlbumSort.COUNT -> compareBy { it.count }
            AlbumSort.PATH -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.path }
        }
        val ordered = albums.sortedWith(if (s.albumSortAsc) cmp else cmp.reversed())
        return ordered.sortedByDescending { it.isPinned }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    // ---- Album actions ----

    fun setHidden(paths: Collection<String>, hidden: Boolean) = updateSettings {
        it.copy(hiddenAlbums = if (hidden) it.hiddenAlbums + paths else it.hiddenAlbums - paths.toSet())
    }

    fun setExcluded(paths: Collection<String>, excluded: Boolean) = updateSettings {
        it.copy(excludedAlbums = if (excluded) it.excludedAlbums + paths else it.excludedAlbums - paths.toSet())
    }

    fun togglePinned(paths: Collection<String>) = updateSettings { s ->
        val allPinned = paths.all { it in s.pinnedAlbums }
        s.copy(pinnedAlbums = if (allPinned) s.pinnedAlbums - paths.toSet() else s.pinnedAlbums + paths)
    }

    fun mediaInAlbums(paths: Collection<String>): List<MediaItem> {
        val set = paths.toSet()
        return _media.value.filter { it.folder in set }
    }

    // ---- Media actions ----

    /** Returns items that could not be deleted directly (caller may ask the system to delete them). */
    suspend fun delete(items: List<MediaItem>): List<MediaItem> {
        val r = fileOps.delete(items)
        removeLocally(items - r.failed.toSet())
        if (r.done > 0) toast("已刪除 ${r.done} 個項目")
        return r.failed
    }

    fun removeLocally(items: Collection<MediaItem>) {
        if (items.isEmpty()) return
        val paths = items.mapTo(HashSet()) { it.path }
        _media.value = _media.value.filter { it.path !in paths }
    }

    fun copyOrMove(items: List<MediaItem>, dest: File, move: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val r = fileOps.copyOrMove(items, dest, move)
            if (move) removeLocally(items - r.failed.toSet())
            toast(
                (if (move) "已移動 " else "已複製 ") + "${r.done} 個項目" +
                    if (r.failed.isNotEmpty()) "，${r.failed.size} 個失敗" else ""
            )
            reload()
            onDone()
        }
    }

    fun rename(item: MediaItem, newName: String, onDone: (MediaItem?) -> Unit = {}) {
        viewModelScope.launch {
            val f = fileOps.rename(item, newName)
            if (f == null) toast("重新命名失敗") else reload()
            onDone(f?.let { repo.fileItem(it, item.mimeType) })
        }
    }

    fun renameAlbum(path: String, newName: String) {
        viewModelScope.launch {
            val f = fileOps.renameFolder(path, newName)
            if (f == null) {
                toast("重新命名失敗")
            } else {
                updateSettings { s ->
                    fun Set<String>.swap() = if (path in this) this - path + f.absolutePath else this
                    s.copy(
                        hiddenAlbums = s.hiddenAlbums.swap(),
                        pinnedAlbums = s.pinnedAlbums.swap(),
                        extraFolders = s.extraFolders.swap(),
                    )
                }
                reload()
            }
        }
    }

    companion object {
        fun sortMedia(items: List<MediaItem>, sort: MediaSort, asc: Boolean): List<MediaItem> {
            val cmp: Comparator<MediaItem> = when (sort) {
                MediaSort.DATE_TAKEN -> compareBy<MediaItem> { it.dateTaken }.thenBy { it.name }
                MediaSort.DATE_MODIFIED -> compareBy<MediaItem> { it.dateModified }.thenBy { it.name }
                MediaSort.NAME -> compareBy(NaturalOrder) { it.name }
                MediaSort.SIZE -> compareBy { it.size }
            }
            return items.sortedWith(if (asc) cmp else cmp.reversed())
        }
    }
}

/** Sorts "IMG_2.jpg" before "IMG_10.jpg". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
