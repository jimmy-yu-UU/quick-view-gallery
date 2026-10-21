package com.jimmyyu.qvgallery.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jimmyyu.qvgallery.R
import com.jimmyyu.qvgallery.transfer.Browser
import com.jimmyyu.qvgallery.transfer.Peer
import com.jimmyyu.qvgallery.transfer.SendError
import com.jimmyyu.qvgallery.transfer.TransferClient
import com.jimmyyu.qvgallery.transfer.TransferRecord
import com.jimmyyu.qvgallery.transfer.TransferServer
import com.jimmyyu.qvgallery.transfer.TransferService
import com.jimmyyu.qvgallery.transfer.TransferState
import com.jimmyyu.qvgallery.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(vm: GalleryViewModel, startOnReceive: Boolean, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(if (startOnReceive || vm.sender.files.value.isEmpty() && TransferState.server.value != null) 1 else 0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } },
                    title = {
                        Column {
                            Text(tr(R.string.transfer_title))
                            Text(TransferState.deviceName(LocalContext.current), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr(R.string.transfer_send)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(R.string.transfer_receive)) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) SendTab(vm) else ReceiveTab()
        }
    }
}

// ---------------- Send ----------------

@Composable
private fun SendTab(vm: GalleryViewModel) {
    val context = LocalContext.current
    val sender = vm.sender
    val files by sender.files.collectAsState()
    val progress by sender.progress.collectAsState()
    val browser = remember { Browser(context) }
    val peers by browser.peers.collectAsState()
    val history by TransferState.history.collectAsState()
    var codeFor by remember { mutableStateOf<Peer?>(null) }
    var manual by remember { mutableStateOf(false) }
    var lastPeer by remember { mutableStateOf<Peer?>(null) }

    DisposableEffect(Unit) {
        browser.start(ownName = TransferState.deviceName(context).takeIf { TransferState.server.value != null })
        onDispose { browser.stop() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) sender.addUris(uris)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(R.string.files_to_send) + if (files.isNotEmpty()) " · ${files.size} · ${formatSize(context, files.sumOf { it.size })}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Icon(Icons.Filled.Add, tr(R.string.add))
                }
                IconButton(onClick = { sender.clear() }, enabled = files.isNotEmpty() && !sender.isSending) {
                    Icon(Icons.Filled.Delete, tr(R.string.clear))
                }
            }
        }
        if (files.isEmpty()) {
            item {
                Text(
                    tr(R.string.no_files_to_send),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(files.take(50), key = { it.uri.toString() }) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatSize(context, f.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { sender.remove(f) }, enabled = !sender.isSending) { Icon(Icons.Filled.Close, tr(R.string.remove)) }
            }
        }
        if (files.size > 50) item { Text("… +${files.size - 50}", Modifier.padding(horizontal = 16.dp)) }

        progress?.let { p ->
            item { SendProgressCard(p, onRetry = { lastPeer?.let { codeFor = it } }, onCancel = { sender.cancel() }, onDismiss = { sender.dismissProgress() }) }
        }

        item {
            HorizontalDivider(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.nearby_devices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { manual = true }) { Text(tr(R.string.enter_address)) }
            }
            Text(
                tr(R.string.nearby_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(peers, key = { it.name }) { peer ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = files.isNotEmpty() && !sender.isSending) { lastPeer = peer; codeFor = peer }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, fontWeight = FontWeight.Medium)
                    Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (files.isNotEmpty()) Text(tr(R.string.transfer_send), color = MaterialTheme.colorScheme.primary)
            }
        }
        item { HistoryList(history.filter { !it.incoming }) }
    }

    codeFor?.let { peer ->
        PairingCodeDialog(
            peer = peer,
            onConfirm = { code -> codeFor = null; sender.send(peer, code) },
            onDismiss = { codeFor = null },
        )
    }
    if (manual) {
        ManualAddressDialog(onFound = { peer -> manual = false; lastPeer = peer; if (files.isNotEmpty()) codeFor = peer }, onDismiss = { manual = false })
    }
}

@Composable
private fun SendProgressCard(p: com.jimmyyu.qvgallery.transfer.SendProgress, onRetry: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val title = when {
                p.error == SendError.WRONG_CODE -> tr(R.string.wrong_pairing_code)
                p.error == SendError.LOCKED -> tr(R.string.pairing_locked)
                p.error == SendError.UNREACHABLE -> tr(R.string.peer_unreachable, p.peer)
                p.error == SendError.INTERRUPTED -> tr(R.string.send_cancelled)
                p.done && p.failed == 0 -> tr(R.string.send_done, p.count, p.peer)
                p.done -> tr(R.string.send_partly_failed, p.failed)
                else -> tr(R.string.sending_to, p.peer)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (p.error != null && p.error != SendError.INTERRUPTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (p.error == null || p.error == SendError.INTERRUPTED) {
                Spacer(Modifier.height(8.dp))
                if (!p.done) Text("${p.index + 1}/${p.count}  ${p.fileName}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { if (p.totalBytes > 0) (p.sentBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Text("${formatSize(context, p.sentBytes)} / ${formatSize(context, p.totalBytes)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (p.error == SendError.WRONG_CODE) TextButton(onClick = onRetry) { Text(tr(R.string.retry)) }
                if (!p.done && p.error == null) TextButton(onClick = onCancel) { Text(tr(R.string.cancel)) }
                else TextButton(onClick = onDismiss) { Text(tr(R.string.close)) }
            }
        }
    }
}

@Composable
private fun PairingCodeDialog(peer: Peer, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.send_to, peer.name)) },
        text = {
            Column {
                Text(tr(R.string.enter_pairing_code_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = code.length == 6, onClick = { onConfirm(code) }) { Text(tr(R.string.transfer_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}

@Composable
private fun ManualAddressDialog(onFound: (Peer) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.enter_address)) },
        text = {
            Column {
                Text(tr(R.string.enter_address_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim(); error = false },
                    singleLine = true,
                    placeholder = { Text("192.168.1.20:${TransferServer.PREFERRED_PORT}") },
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) Text(tr(R.string.peer_not_found), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotEmpty() && !checking, onClick = {
                val cleaned = text.removePrefix("http://").trimEnd('/')
                val host = cleaned.substringBefore(':')
                val port = cleaned.substringAfter(':', "").toIntOrNull() ?: TransferServer.PREFERRED_PORT
                checking = true
                scope.launch {
                    val name = withContext(Dispatchers.IO) { runCatching { TransferClient(host, port).info() }.getOrNull() }
                    checking = false
                    if (name == null) error = true else onFound(Peer(name.ifEmpty { host }, host, port))
                }
            }) { Text(if (checking) tr(R.string.checking) else tr(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}

// ---------------- Receive ----------------

@Composable
private fun ReceiveTab() {
    val context = LocalContext.current
    val server by TransferState.server.collectAsState()
    val history by TransferState.history.collectAsState()
    // Notifications show the running receiver; ask once, but start either way.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { TransferService.start(context) }
    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        else TransferService.start(context)
    }
    // Addresses change when the phone switches networks; refresh while visible.
    var addresses by remember { mutableStateOf(TransferState.localAddresses()) }
    LaunchedEffect(server) {
        while (true) {
            addresses = TransferState.localAddresses()
            delay(5_000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val s = server
        if (s == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(tr(R.string.receive_intro), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { start() }) { Text(tr(R.string.start_receiving)) }
                    if (addresses.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(tr(R.string.no_network), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr(R.string.pairing_code), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        s.code.chunked(3).joinToString(" "),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(16.dp))
                    val urls = addresses.map { "http://$it:${s.port}/" }
                    val first = urls.firstOrNull()
                    if (first != null) {
                        val qr = remember(first) { qrBitmap(first, 512) }
                        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Image(qr.asImageBitmap(), first, Modifier.size(200.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        urls.forEach { Text(it, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace) }
                    } else {
                        Text(tr(R.string.no_network), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr(R.string.receive_running_hint), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { TransferService.stop(context) }) { Text(tr(R.string.stop_receiving)) }
                }
            }
        }
        item { HistoryList(history.filter { it.incoming }) }
    }
}

@Composable
private fun HistoryList(records: List<TransferRecord>) {
    if (records.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(tr(R.string.transfer_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        records.take(100).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        !r.ok -> Icons.Filled.ErrorOutline
                        r.incoming -> Icons.Filled.ArrowDownward
                        else -> Icons.Filled.ArrowUpward
                    },
                    null,
                    tint = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(r.peer, formatSize(context, r.size), formatDate(r.time)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            tr(R.string.received_location, TransferServer.RECEIVE_DIR),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
