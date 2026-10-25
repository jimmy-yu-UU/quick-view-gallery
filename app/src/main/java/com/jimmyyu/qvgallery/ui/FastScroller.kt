package com.jimmyyu.qvgallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val HandleWidth = 44.dp
private val HandleHeight = 60.dp
// The touch area reaches past the visible handle so a finger near it still catches it.
private val TouchWidth = 64.dp
private val TouchHeight = 80.dp

/**
 * Draggable scroll thumb on the right edge of a grid. It appears while the grid scrolls and fades out
 * shortly after; dragging it jumps straight through long lists. [label] names the item at the thumb
 * (e.g. its month) and is shown in a bubble while dragging.
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    label: (Int) -> String? = { null },
) {
    val total by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val visibleCount by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.size } }
    // Only worth it when the list is several screens long.
    if (visibleCount == 0 || total <= visibleCount * 3) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) shown = true
        else if (shown) { delay(1500); shown = false }
    }
    val maxFirst = (total - visibleCount).coerceAtLeast(1)
    val scrollFraction by remember(maxFirst) {
        derivedStateOf { (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f) }
    }
    val fraction = if (dragging) dragFraction else scrollFraction

    BoxWithConstraints(modifier.fillMaxSize().padding(top = topPadding, bottom = bottomPadding)) {
        val density = LocalDensity.current
        val trackPx = with(density) { (maxHeight - TouchHeight).toPx() }.coerceAtLeast(1f)
        val thumbY = (fraction * trackPx).roundToInt()

        AnimatedVisibility(
            shown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbY) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragging) {
                    val index = (dragFraction * maxFirst).roundToInt()
                    label(index)?.let {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .size(TouchWidth, TouchHeight)
                        .pointerInput(maxFirst, trackPx) {
                            // Starts on touch-down, without the usual drag slop, so the handle feels held at once.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                dragFraction = scrollFraction
                                dragging = true
                                try {
                                    drag(down.id) { change ->
                                        val dy = change.positionChange().y
                                        change.consume()
                                        dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                                        state.requestScrollToItem((dragFraction * maxFirst).roundToInt())
                                    }
                                } finally {
                                    dragging = false
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 3.dp,
                        modifier = Modifier.padding(end = 6.dp).size(HandleWidth, HandleHeight),
                    ) {
                        // Grip lines mark it as something to drag.
                        val grip = if (dragging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            repeat(3) { Box(Modifier.size(20.dp, 2.dp).background(grip, RoundedCornerShape(1.dp))) }
                        }
                    }
                }
            }
        }
    }
}
