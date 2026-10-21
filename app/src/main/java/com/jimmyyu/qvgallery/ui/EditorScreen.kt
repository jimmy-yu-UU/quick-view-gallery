package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.jimmyyu.qvgallery.data.FileOps
import com.jimmyyu.qvgallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Normalised crop rectangle (0..1) in the rotated/flipped image space. */
data class CropRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    companion object { val FULL = CropRect(0f, 0f, 1f, 1f) }
}

data class EditState(val rotation: Int = 0, val flipH: Boolean = false, val flipV: Boolean = false, val crop: CropRect = CropRect.FULL) {
    val isIdentity get() = rotation == 0 && !flipH && !flipV && crop == CropRect.FULL
}

private enum class Ratio(private val labelRes: Int?, private val fixedLabel: String?, val value: Float?) {
    FREE(R.string.free, null, null), ORIGINAL(R.string.original, null, -1f), SQUARE(null, "1:1", 1f),
    R43(null, "4:3", 4f / 3f), R34(null, "3:4", 3f / 4f), R169(null, "16:9", 16f / 9f), R916(null, "9:16", 9f / 16f);

    val label: String get() = labelRes?.let { tr(it) } ?: fixedLabel.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: GalleryViewModel, item: MediaItem, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by produceState<Bitmap?>(null, item) {
        value = withContext(Dispatchers.IO) { runCatching { decode(context, item, maxSide = 2048, crop = null) }.getOrNull() }
    }
    var state by remember { mutableStateOf(EditState()) }
    var ratio by remember { mutableStateOf(Ratio.FREE) }
    var askSave by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    BackHandler { if (state.isIdentity) onDone(null) else askDiscard = true }

    val transformed = remember(preview, state.rotation, state.flipH, state.flipV) {
        preview?.let { applyTransform(it, state.rotation, state.flipH, state.flipV).asImageBitmap() }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (state.isIdentity) onDone(null) else askDiscard = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back))
                    }
                },
                title = { Text(tr(R.string.edit)) },
                actions = {
                    IconButton(onClick = { state = EditState(); ratio = Ratio.FREE }) { Icon(Icons.Filled.Restore, tr(R.string.reset)) }
                    IconButton(enabled = !state.isIdentity && !saving, onClick = { askSave = true }) { Icon(Icons.Filled.Check, tr(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                val img = transformed
                if (img == null || saving) {
                    CircularProgressIndicator()
                } else {
                    CropArea(img, state.crop, ratio.value?.let { if (it < 0) img.width.toFloat() / img.height else it }) {
                        state = state.copy(crop = it)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ratio.entries.forEach { r ->
                    FilterChip(
                        selected = ratio == r,
                        onClick = {
                            ratio = r
                            val img = transformed ?: return@FilterChip
                            val value = r.value?.let { if (it < 0) img.width.toFloat() / img.height else it }
                            if (value != null) state = state.copy(crop = fitRatio(value, img.width.toFloat() / img.height))
                        },
                        label = { Text(r.label) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { state = state.copy(rotation = (state.rotation + 270) % 360, crop = CropRect.FULL) }) {
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, tr(R.string.rotate_left), tint = Color.White)
                }
                IconButton(onClick = { state = state.copy(rotation = (state.rotation + 90) % 360, crop = CropRect.FULL) }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, tr(R.string.rotate_right), tint = Color.White)
                }
                // Flips are stored in original-image space, so on a sideways image the screen axes swap.
                val sideways = state.rotation % 180 != 0
                IconButton(onClick = {
                    state = (if (sideways) state.copy(flipV = !state.flipV) else state.copy(flipH = !state.flipH))
                        .copy(crop = state.crop.let { CropRect(1 - it.r, it.t, 1 - it.l, it.b) })
                }) {
                    Icon(Icons.Filled.Flip, tr(R.string.flip_horizontal), tint = Color.White)
                }
                IconButton(onClick = {
                    state = (if (sideways) state.copy(flipH = !state.flipH) else state.copy(flipV = !state.flipV))
                        .copy(crop = state.crop.let { CropRect(it.l, 1 - it.b, it.r, 1 - it.t) })
                }) {
                    Icon(Icons.Filled.Flip, tr(R.string.flip_vertical), tint = Color.White, modifier = Modifier.rotate(90f))
                }
            }
        }
    }

    fun save(overwrite: Boolean) {
        saving = true
        scope.launch {
            val target = if (overwrite) item.file else {
                val base = item.name.substringBeforeLast('.')
                val ext = outputExtension(item.mimeType)
                FileOps.uniqueFile(item.file.parentFile ?: File(item.folder), "${base}_edit.$ext")
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val full = renderFull(context, item, state)
                    val exif = runCatching { ExifInterface(item.path) }.getOrNull()
                    val mime = if (overwrite) item.mimeType else "image/" + outputExtension(item.mimeType).replace("jpg", "jpeg")
                    val saved = vm.fileOps.saveBitmap(full, target, mime)
                    full.recycle()
                    if (saved && exif != null) copyExif(exif, target)
                    saved
                }.getOrDefault(false)
            }
            saving = false
            if (ok) {
                vm.fileOps.scan(listOf(target.absolutePath))
                vm.reload()
                vm.toast(if (overwrite) tr(R.string.saved) else tr(R.string.saved_as, target.name))
                onDone(target.absolutePath)
            } else {
                vm.toast(tr(R.string.save_failed))
            }
        }
    }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text(tr(R.string.save_changes)) },
            text = { Text(tr(R.string.save_question)) },
            confirmButton = { TextButton(onClick = { askSave = false; save(overwrite = false) }) { Text(tr(R.string.save_copy)) } },
            dismissButton = { TextButton(onClick = { askSave = false; save(overwrite = true) }) { Text(tr(R.string.overwrite)) } },
        )
    }
    if (askDiscard) {
        ConfirmDialog(
            title = tr(R.string.discard_changes),
            text = tr(R.string.discard_body),
            confirm = tr(R.string.discard),
            onConfirm = { onDone(null) },
            onDismiss = { askDiscard = false },
        )
    }
}

@Composable
private fun CropArea(image: ImageBitmap, crop: CropRect, ratio: Float?, onChange: (CropRect) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / image.width, boxH / image.height)
        val dispW = image.width * scale
        val dispH = image.height * scale
        val handle = with(density) { 28.dp.toPx() }
        val current by androidx.compose.runtime.rememberUpdatedState(crop)
        val imageRatio = image.width.toFloat() / image.height

        Box(Modifier.size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() })) {
            Image(image, null, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(ratio, dispW, dispH) {
                        var mode = 0 // 0 move, 1 TL, 2 TR, 3 BL, 4 BR
                        detectDragGestures(
                            onDragStart = { p ->
                                val c = current
                                val corners = listOf(
                                    Offset(c.l * dispW, c.t * dispH), Offset(c.r * dispW, c.t * dispH),
                                    Offset(c.l * dispW, c.b * dispH), Offset(c.r * dispW, c.b * dispH),
                                )
                                val hit = corners.indexOfFirst { abs(it.x - p.x) < handle && abs(it.y - p.y) < handle }
                                mode = if (hit >= 0) hit + 1 else 0
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val dx = drag.x / dispW
                                val dy = drag.y / dispH
                                onChange(dragCrop(current, mode, dx, dy, ratio?.let { it / imageRatio }))
                            },
                        )
                    }
            ) {
                val l = crop.l * size.width
                val t = crop.t * size.height
                val r = crop.r * size.width
                val b = crop.b * size.height
                val shade = Color.Black.copy(alpha = 0.55f)
                drawRect(shade, Offset.Zero, Size(size.width, t))
                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(shade, Offset(0f, t), Size(l, b - t))
                drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                for (i in 1..2) {
                    val x = l + (r - l) * i / 3
                    val y = t + (b - t) * i / 3
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(x, t), Offset(x, b), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(l, y), Offset(r, y), 1.dp.toPx())
                }
                for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
                    drawCircle(Color.White, 6.dp.toPx(), p)
                }
            }
        }
    }
}

/**
 * @param ratio required w/h in normalised units (aspect / imageAspect), or null for free.
 */
private fun dragCrop(c: CropRect, mode: Int, dx: Float, dy: Float, ratio: Float?): CropRect {
    val minSize = 0.05f
    if (mode == 0) {
        val nx = dx.coerceIn(-c.l, 1 - c.r)
        val ny = dy.coerceIn(-c.t, 1 - c.b)
        return CropRect(c.l + nx, c.t + ny, c.r + nx, c.b + ny)
    }
    var l = c.l
    var t = c.t
    var r = c.r
    var b = c.b
    when (mode) {
        1 -> { l += dx; t += dy }
        2 -> { r += dx; t += dy }
        3 -> { l += dx; b += dy }
        4 -> { r += dx; b += dy }
    }
    l = l.coerceIn(0f, r - minSize)
    r = r.coerceIn(l + minSize, 1f)
    t = t.coerceIn(0f, b - minSize)
    b = b.coerceIn(t + minSize, 1f)
    if (ratio != null) {
        // Keep the corner opposite to the dragged one fixed and fit the ratio inside the bounds.
        var w = r - l
        var h = w / ratio
        val maxH = if (mode <= 2) b else 1f - t
        if (h > maxH) { h = maxH; w = h * ratio }
        val maxW = if (mode == 1 || mode == 3) r else 1f - l
        if (w > maxW) { w = maxW; h = w / ratio }
        when (mode) {
            1 -> { l = r - w; t = b - h }
            2 -> { r = l + w; t = b - h }
            3 -> { l = r - w; b = t + h }
            4 -> { r = l + w; b = t + h }
        }
    }
    return CropRect(l, t, r, b)
}

/** Largest centred crop with the given aspect. */
private fun fitRatio(aspect: Float, imageAspect: Float): CropRect {
    val n = aspect / imageAspect
    return if (n >= 1f) {
        val h = 1f / n
        CropRect(0f, (1 - h) / 2, 1f, (1 + h) / 2)
    } else {
        CropRect((1 - n) / 2, 0f, (1 + n) / 2, 1f)
    }
}

private fun outputExtension(mime: String) = when (mime) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
}

/** Decodes with EXIF orientation applied. [crop] is in oriented pixel coordinates. */
private fun decode(context: Context, item: MediaItem, maxSide: Int, crop: Rect?): Bitmap {
    val src = ImageDecoder.createSource(context.contentResolver, item.uri)
    return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val w = info.size.width
        val h = info.size.height
        val s = max(w, h).toFloat() / maxSide
        if (s > 1f) {
            val tw = (w / s).roundToInt()
            val th = (h / s).roundToInt()
            decoder.setTargetSize(tw, th)
            if (crop != null) decoder.crop = Rect((crop.left / s).toInt(), (crop.top / s).toInt(), (crop.right / s).toInt(), (crop.bottom / s).toInt())
        } else if (crop != null) {
            decoder.crop = crop
        }
    }
}

private fun orientedSize(context: Context, item: MediaItem): Pair<Int, Int> {
    var size = 0 to 0
    val src = ImageDecoder.createSource(context.contentResolver, item.uri)
    // Decoding just the header: throw from the listener once the size is known.
    runCatching {
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            size = info.size.width to info.size.height
            throw StopDecode()
        }
    }
    return size
}

private class StopDecode : RuntimeException()

private fun applyTransform(bmp: Bitmap, rotation: Int, flipH: Boolean, flipV: Boolean): Bitmap {
    if (rotation == 0 && !flipH && !flipV) return bmp
    val m = Matrix()
    m.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
    m.postRotate(rotation.toFloat())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/** Maps a point from the transformed (flip then rotate) normalised space back to the original. */
private fun inverse(x: Float, y: Float, rotation: Int, flipH: Boolean, flipV: Boolean): Pair<Float, Float> {
    // Undo rotation (clockwise) first.
    var (px, py) = when (rotation) {
        90 -> y to 1 - x
        180 -> 1 - x to 1 - y
        270 -> 1 - y to x
        else -> x to y
    }
    if (flipH) px = 1 - px
    if (flipV) py = 1 - py
    return px to py
}

private fun renderFull(context: Context, item: MediaItem, s: EditState): Bitmap {
    val (w, h) = orientedSize(context, item)
    require(w > 0 && h > 0)
    val c = s.crop
    val corners = listOf(c.l to c.t, c.r to c.t, c.l to c.b, c.r to c.b).map { (x, y) -> inverse(x, y, s.rotation, s.flipH, s.flipV) }
    val rect = Rect(
        (corners.minOf { it.first } * w).roundToInt().coerceIn(0, w - 1),
        (corners.minOf { it.second } * h).roundToInt().coerceIn(0, h - 1),
        (corners.maxOf { it.first } * w).roundToInt().coerceIn(1, w),
        (corners.maxOf { it.second } * h).roundToInt().coerceIn(1, h),
    )
    // Cap the output at ~100MP to stay within memory.
    val maxSide = if (w.toLong() * h > 100_000_000L) 10_000 else max(w, h)
    val cropped = decode(context, item, maxSide, rect)
    val out = applyTransform(cropped, s.rotation, s.flipH, s.flipV)
    if (out !== cropped) cropped.recycle()
    return out
}

private fun copyExif(from: ExifInterface, target: File) {
    runCatching {
        val to = ExifInterface(target.absolutePath)
        val tags = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
        for (tag in tags) from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        to.saveAttributes()
    }
}
