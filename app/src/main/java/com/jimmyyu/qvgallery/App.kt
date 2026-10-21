package com.jimmyyu.qvgallery

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import java.lang.ref.WeakReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.jimmyyu.qvgallery.data.FileOps
import com.jimmyyu.qvgallery.data.LockManager
import com.jimmyyu.qvgallery.data.MediaRepository
import com.jimmyyu.qvgallery.data.SettingsRepository
import com.jimmyyu.qvgallery.image.RawFetcher
import com.jimmyyu.qvgallery.image.RawKeyer
import com.jimmyyu.qvgallery.image.ThumbFetcher
import com.jimmyyu.qvgallery.image.ThumbKeyer
import com.jimmyyu.qvgallery.image.ThumbMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

class App : Application(), SingletonImageLoader.Factory {

    lateinit var settings: SettingsRepository
        private set
    lateinit var media: MediaRepository
        private set
    lateinit var fileOps: FileOps
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        media = MediaRepository(this)
        fileOps = FileOps(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = LockManager.onAppBackground()
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheMb = runCatching { runBlocking { settings.settings.first().diskCacheMb } }.getOrDefault(512)
        return ImageLoader.Builder(context)
            .components {
                add(ThumbMapper())
                add(ThumbKeyer())
                add(RawKeyer())
                add(ThumbFetcher.Factory(this@App))
                add(RawFetcher.Factory(this@App))
                add(AnimatedImageDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir(this).toOkioPath())
                    .maxSizeBytes(cacheMb * 1024L * 1024L)
                    .build()
            }
            .crossfade(150)
            .build()
    }

    companion object {
        lateinit var instance: App
            private set

        /** The foreground activity; its resources carry the current per-app language. */
        @Volatile
        var uiContext: WeakReference<Context>? = null

        fun imageCacheDir(context: Context) = context.cacheDir.resolve("image_cache")
    }
}

val Context.app: App get() = applicationContext as App

/** Localised string; usable from composables, callbacks and view models alike. */
fun tr(@StringRes id: Int, vararg args: Any): String {
    val context = App.uiContext?.get() ?: App.instance
    return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}
