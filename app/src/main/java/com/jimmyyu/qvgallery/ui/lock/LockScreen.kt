package com.jimmyyu.qvgallery.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jimmyyu.qvgallery.data.LockType
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Full screen unlock prompt.
 * @param check returns true when the entered secret is correct.
 */
@Composable
fun LockScreen(
    type: LockType,
    biometric: Boolean,
    title: String = "請解鎖",
    onCancel: (() -> Unit)? = null,
    check: (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun fail() {
        error = if (type == LockType.PATTERN) "圖形錯誤，請再試一次" else "密碼錯誤，請再試一次"
        scope.launch {
            for (x in listOf(18f, -18f, 12f, -12f, 6f, 0f)) shake.animateTo(x)
        }
    }

    val activity = context as? FragmentActivity
    val canBiometric = biometric && activity != null &&
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    fun promptBiometric() {
        if (!canBiometric) return
        val prompt = BiometricPrompt(
            activity!!,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解鎖 QV Gallery")
                .setNegativeButtonText(if (type == LockType.PATTERN) "使用圖形" else "使用密碼")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
    LaunchedEffect(Unit) { promptBiometric() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.graphicsLayer { translationX = shake.value }) {
                SecretInput(type) { secret -> if (check(secret)) onUnlocked() else fail() }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") }
                if (canBiometric) {
                    FilledTonalIconButton(onClick = { promptBiometric() }) { Icon(Icons.Filled.Fingerprint, "生物辨識") }
                }
            }
        }
    }
}

/** Asks for a new secret twice. */
@Composable
fun SetupLockScreen(type: LockType, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val noun = if (type == LockType.PATTERN) "圖形" else "PIN 碼"
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Text(if (first == null) "設定新的$noun" else "再次輸入以確認", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                error ?: if (type == LockType.PATTERN) "至少連接 4 個點" else "至少 4 位數字",
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            SecretInput(type, key = first) { secret ->
                when {
                    secret.length < 4 -> error = "太短了"
                    first == null -> { first = secret; error = null }
                    first == secret -> onDone(secret)
                    else -> { first = null; error = "兩次輸入不一致，請重新設定" }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun SecretInput(type: LockType, key: Any? = null, onEntered: (String) -> Unit) {
    when (type) {
        LockType.PATTERN -> PatternLock(Modifier.widthIn(max = 320.dp).fillMaxWidth(), resetKey = key) { cells ->
            onEntered(cells.joinToString(""))
        }
        else -> PinPad(resetKey = key, onEntered = onEntered)
    }
}

@Composable
fun PatternLock(modifier: Modifier = Modifier, resetKey: Any? = null, onComplete: (List<Int>) -> Unit) {
    val selected = remember(resetKey) { mutableStateListOf<Int>() }
    var pointer by remember(resetKey) { mutableStateOf<Offset?>(null) }
    val dot = MaterialTheme.colorScheme.onSurface
    val active = MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(resetKey) {
                fun center(i: Int): Offset {
                    val cell = size.width / 3f
                    return Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                }
                fun hit(p: Offset) {
                    val radius = size.width / 3f * 0.35f
                    for (i in 0 until 9) {
                        val c = center(i)
                        if (i !in selected && hypot(p.x - c.x, p.y - c.y) < radius) {
                            // Include a skipped middle dot, as Android's pattern lock does.
                            selected.lastOrNull()?.let { last ->
                                val mid = (last + i) / 2
                                val aligned = (last % 3 + i % 3) % 2 == 0 && (last / 3 + i / 3) % 2 == 0
                                if (aligned && mid !in selected && mid != i && (last + i) % 2 == 0) selected.add(mid)
                            }
                            selected.add(i)
                        }
                    }
                }
                detectDragGestures(
                    onDragStart = { selected.clear(); pointer = it; hit(it) },
                    onDrag = { change, _ -> pointer = change.position; hit(change.position) },
                    onDragEnd = {
                        pointer = null
                        val result = selected.toList()
                        if (result.isNotEmpty()) onComplete(result)
                        selected.clear()
                    },
                    onDragCancel = { pointer = null; selected.clear() },
                )
            }
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        for (k in 1 until selected.size) {
            drawLine(active.copy(alpha = 0.6f), center(selected[k - 1]), center(selected[k]), strokeWidth = 10f, cap = StrokeCap.Round)
        }
        val last = selected.lastOrNull()
        val p = pointer
        if (last != null && p != null) {
            drawLine(active.copy(alpha = 0.4f), center(last), p, strokeWidth = 10f, cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val on = i in selected
            drawCircle(if (on) active else dot, radius = if (on) 22f else 12f, center = center(i))
        }
    }
}

@Composable
fun PinPad(resetKey: Any? = null, onEntered: (String) -> Unit) {
    var pin by remember(resetKey) { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(pin.length) {
                Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Spacer(Modifier.height(24.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (k in row) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (k) {
                            "⌫" -> IconButton(onClick = { pin = pin.dropLast(1) }, modifier = Modifier.size(64.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, "刪除")
                            }
                            "OK" -> TextButton(onClick = { if (pin.isNotEmpty()) { onEntered(pin); pin = "" } }) {
                                Text("確定", fontWeight = FontWeight.Bold)
                            }
                            else -> TextButton(onClick = { if (pin.length < 16) pin += k }, modifier = Modifier.size(64.dp)) {
                                Text(k, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
