package com.jimmyyu.qvgallery.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.SlideTransition
import com.jimmyyu.qvgallery.image.RawPreview
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(settings: AppSettings, source: List<MediaItem>, startIndex: Int, onExit: (lastPath: String?) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val order = remember(source) {
        val photos = source.filter { !it.isVideo }
        val start = source.getOrNull(startIndex)?.takeIf { !it.isVideo }
        if (settings.slideShuffle) {
            val shuffled = photos.shuffled().toMutableList()
            if (start != null) { shuffled.remove(start); shuffled.add(0, start) }
            shuffled
        } else {
            val first = start?.let { photos.indexOf(it) }?.takeIf { it >= 0 }
                ?: source.drop(startIndex).firstOrNull { !it.isVideo }?.let { photos.indexOf(it) } ?: 0
            photos.drop(first) + photos.take(first)
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(false) }
    val intervalMs = settings.slideInterval.coerceAtLeast(1) * 1000L

    fun exit() = onExit(order.getOrNull(index)?.path)
    BackHandler { exit() }
    LaunchedEffect(order) { if (order.isEmpty()) onExit(null) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(index, paused, order) {
        if (paused || order.isEmpty()) return@LaunchedEffect
        // Warm up the next picture so the transition doesn't wait on decoding.
        order.getOrNull((index + 1) % order.size)?.let { next ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(modelFor(next)).build())
        }
        delay(intervalMs)
        if (index + 1 < order.size) index++
        else if (settings.slideLoop) index = 0
        else exit()
    }

    val transition = settings.slideTransition
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showUi = !showUi },
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState || (initialState == order.size - 1 && targetState == 0)
                when (transition) {
                    SlideTransition.NONE -> ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
                    SlideTransition.FADE, SlideTransition.KEN_BURNS -> fadeIn(tween(700)) togetherWith fadeOut(tween(700))
                    SlideTransition.SLIDE -> {
                        val dir = if (forward) 1 else -1
                        slideInHorizontally(tween(500)) { it * dir } togetherWith slideOutHorizontally(tween(500)) { -it * dir }
                    }
                    SlideTransition.ZOOM -> (scaleIn(tween(600), initialScale = 0.85f) + fadeIn(tween(600))) togetherWith
                        (scaleOut(tween(600), targetScale = 1.15f) + fadeOut(tween(600)))
                }
            },
            label = "slideshow",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            val item = order.getOrNull(i) ?: return@AnimatedContent
            val zoom = remember(i) { Animatable(1f) }
            if (transition == SlideTransition.KEN_BURNS) {
                LaunchedEffect(i) { zoom.animateTo(1.15f, tween((intervalMs + 700).toInt(), easing = LinearEasing)) }
            }
            AsyncImage(
                model = modelFor(item),
                contentDescription = item.name,
                contentScale = if (transition == SlideTransition.KEN_BURNS) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
            )
        }

        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { exit() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "結束", tint = Color.White) }
                Text(
                    "${index + 1} / ${order.size}   ${order.getOrNull(index)?.name.orEmpty()}",
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { index = if (index > 0) index - 1 else order.size - 1 }) {
                    Icon(Icons.Filled.SkipPrevious, "上一張", tint = Color.White)
                }
                IconButton(onClick = { paused = !paused }) {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "播放" else "暫停", tint = Color.White)
                }
                IconButton(onClick = { index = if (index + 1 < order.size) index + 1 else 0 }) {
                    Icon(Icons.Filled.SkipNext, "下一張", tint = Color.White)
                }
            }
        }
    }
}

private fun modelFor(item: MediaItem): Any = if (item.isRaw) RawPreview(item) else item.uri
