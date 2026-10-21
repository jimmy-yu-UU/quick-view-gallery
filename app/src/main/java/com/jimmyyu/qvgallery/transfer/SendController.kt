package com.jimmyyu.qvgallery.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jimmyyu.qvgallery.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class SendFile(val uri: Uri, val name: String, val size: Long)

data class SendProgress(
    val peer: String,
    val index: Int,
    val count: Int,
    val fileName: String,
    val sentBytes: Long,
    val totalBytes: Long,
    val done: Boolean = false,
    val failed: Int = 0,
    val error: SendError? = null,
)

enum class SendError { WRONG_CODE, LOCKED, UNREACHABLE, INTERRUPTED }

/** Queue of files to send and the running upload job. Lives in the ViewModel so it survives navigation. */
class SendController(private val context: Context, private val scope: CoroutineScope) {
    private val _files = MutableStateFlow<List<SendFile>>(emptyList())
    val files: StateFlow<List<SendFile>> = _files.asStateFlow()

    private val _progress = MutableStateFlow<SendProgress?>(null)
    val progress: StateFlow<SendProgress?> = _progress.asStateFlow()

    private var job: Job? = null
    val isSending: Boolean get() = job?.isActive == true

    fun addItems(items: List<MediaItem>) = add(items.map { SendFile(it.uri, it.name, it.size) })

    fun addUris(uris: List<Uri>) {
        scope.launch(Dispatchers.IO) { add(uris.map { describe(it) }) }
    }

    private fun add(new: List<SendFile>) {
        _files.update { old -> (old + new).distinctBy { it.uri } }
    }

    fun remove(file: SendFile) = _files.update { it - file }
    fun clear() = _files.update { emptyList() }
    fun dismissProgress() { if (!isSending) _progress.value = null }

    fun cancel() {
        job?.cancel()
    }

    fun send(peer: Peer, code: String) {
        if (isSending) return
        val queue = _files.value
        if (queue.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            val client = TransferClient(peer.host, peer.port)
            val total = queue.sumOf { it.size }
            var sentBefore = 0L
            var failed = 0
            _progress.value = SendProgress(peer.name, 0, queue.size, queue[0].name, 0, total)
            val token = try {
                client.login(code)
            } catch (e: WrongCodeException) {
                _progress.value = _progress.value?.copy(error = SendError.WRONG_CODE)
                return@launch
            } catch (e: LockedException) {
                _progress.value = _progress.value?.copy(error = SendError.LOCKED)
                return@launch
            } catch (e: Exception) {
                _progress.value = _progress.value?.copy(error = SendError.UNREACHABLE)
                return@launch
            }
            val me = TransferState.deviceName(context)
            for ((i, f) in queue.withIndex()) {
                if (!isActive) break
                _progress.update { it?.copy(index = i, fileName = f.name, sentBytes = sentBefore) }
                val ok = runCatching { uploadOne(client, token, me, f) { sent -> _progress.update { it?.copy(sentBytes = sentBefore + sent) } } }.isSuccess
                if (!ok) failed++
                TransferState.record(TransferRecord(f.name, f.size, incoming = false, peer = peer.name, ok = ok))
                sentBefore += f.size
                if (ok) _files.update { it - f }
            }
            _progress.update {
                it?.copy(sentBytes = sentBefore, done = true, failed = failed, error = if (!isActive) SendError.INTERRUPTED else null)
            }
        }
    }

    private fun uploadOne(client: TransferClient, token: String, me: String, f: SendFile, onProgress: (Long) -> Unit) {
        val resolver = context.contentResolver
        if (f.size > 0) {
            resolver.openInputStream(f.uri)!!.use { client.upload(token, me, f.name, f.size, it, onProgress) }
            return
        }
        // Unknown size (some providers don't report it): stage a copy so the length is known.
        val tmp = File.createTempFile("send", null, context.cacheDir)
        try {
            resolver.openInputStream(f.uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.inputStream().use { client.upload(token, me, f.name, tmp.length(), it, onProgress) }
        } finally {
            tmp.delete()
        }
    }

    private fun describe(uri: Uri): SendFile {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return SendFile(uri, name, size)
    }
}
