package com.jimmyyu.qvgallery.transfer

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.jimmyyu.qvgallery.App
import com.jimmyyu.qvgallery.BuildConfig
import com.jimmyyu.qvgallery.data.AlbumKeys
import com.jimmyyu.qvgallery.data.MediaItem
import com.jimmyyu.qvgallery.data.MediaTypes
import com.jimmyyu.qvgallery.ui.GalleryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Minimal LAN HTTP server used by both the browser page and other phones.
 * Every API call except /api/login needs a token obtained with the pairing code shown on screen.
 * Only media that is visible in the gallery (not hidden or excluded) is served.
 */
class TransferServer(private val app: App, private val onReceived: (TransferRecord) -> Unit) {

    val code: String = (SecureRandom().nextInt(900_000) + 100_000).toString()
    var port: Int = 0
        private set

    private val tokens = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newCachedThreadPool()
    private var socket: ServerSocket? = null
    @Volatile private var failedLogins = 0
    @Volatile private var lockedUntil = 0L

    fun start(): Int {
        val s = ServerSocket()
        s.reuseAddress = true
        try {
            s.bind(InetSocketAddress(PREFERRED_PORT))
        } catch (e: Exception) {
            s.bind(InetSocketAddress(0))
        }
        socket = s
        port = s.localPort
        pool.execute {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: SocketException) { break }
                pool.execute { serve(client) }
            }
        }
        return port
    }

    fun stop() {
        runCatching { socket?.close() }
        pool.shutdownNow()
        tokens.clear()
    }

    // ---------------- HTTP plumbing ----------------

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: InputStream,
    ) {
        val contentLength: Long get() = headers["content-length"]?.toLongOrNull() ?: 0
        fun cookie(name: String): String? = headers["cookie"]?.split(';')
            ?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
    }

    private fun serve(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 60_000
            val input = BufferedInputStream(sock.getInputStream(), 64 * 1024)
            val out = sock.getOutputStream().buffered(64 * 1024)
            try {
                val req = readRequest(input) ?: return
                route(req, out)
            } catch (e: SocketException) {
                // Client went away.
            } catch (e: Exception) {
                Log.w(TAG, "request failed", e)
                runCatching { json(out, 500, JSONObject().put("error", e.message ?: "error")) }
            } finally {
                runCatching { out.flush() }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16 * 1024) throw IllegalStateException("header too long")
        }
        return buf.toString(Charsets.UTF_8.name())
    }

    private fun readRequest(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        return Request(parts[0].uppercase(), path, query, headers, input)
    }

    private fun head(out: OutputStream, status: Int, type: String, length: Long?, extra: Map<String, String> = emptyMap()) {
        val sb = StringBuilder("HTTP/1.1 $status ${statusText(status)}\r\n")
        sb.append("Content-Type: $type\r\n")
        if (length != null) sb.append("Content-Length: $length\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("X-Content-Type-Options: nosniff\r\n")
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun statusText(s: Int) = when (s) {
        200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
        403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 415 -> "Unsupported Media Type"
        416 -> "Range Not Satisfiable"; 429 -> "Too Many Requests"; else -> "Error"
    }

    private fun json(out: OutputStream, status: Int, body: Any, extra: Map<String, String> = emptyMap()) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        head(out, status, "application/json; charset=utf-8", bytes.size.toLong(), extra)
        out.write(bytes)
    }

    // ---------------- Routing ----------------

    private fun route(req: Request, out: OutputStream) {
        if (req.path == "/" || req.path == "/index.html") return page(out)
        if (req.path == "/api/login" && req.method == "POST") return login(req, out)
        if (req.path == "/api/info") {
            return json(out, 200, JSONObject().put("app", "QV Gallery").put("name", TransferState.deviceName(app)).put("version", BuildConfig.VERSION_NAME))
        }
        val token = req.headers["x-qv-token"] ?: req.cookie("qvt") ?: req.query["t"]
        if (token == null || token !in tokens) return json(out, 401, JSONObject().put("error", "unauthorized"))
        when (req.path) {
            "/api/albums" -> albums(out)
            "/api/media" -> media(req, out)
            "/api/thumb" -> thumb(req, out)
            "/api/file" -> file(req, out)
            "/api/zip" -> zip(req, out)
            "/api/upload" -> if (req.method == "PUT" || req.method == "POST") upload(req, out) else json(out, 405, JSONObject())
            else -> json(out, 404, JSONObject().put("error", "not found"))
        }
    }

    private fun page(out: OutputStream) {
        val bytes = app.assets.open("web/index.html").use { it.readBytes() }
        head(out, 200, "text/html; charset=utf-8", bytes.size.toLong())
        out.write(bytes)
    }

    private fun login(req: Request, out: OutputStream) {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return json(out, 429, JSONObject().put("error", "locked").put("retryMs", lockedUntil - now))
        val body = readUpTo(req.body, min(req.contentLength, 4096L).toInt()).toString(Charsets.UTF_8)
        val given = runCatching { JSONObject(body).optString("code") }.getOrDefault("")
        if (given != code) {
            failedLogins++
            if (failedLogins >= MAX_ATTEMPTS) {
                lockedUntil = now + LOCK_MS
                failedLogins = 0
            }
            return json(out, 403, JSONObject().put("error", "wrong code"))
        }
        failedLogins = 0
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        tokens += token
        json(out, 200, JSONObject().put("token", token), mapOf("Set-Cookie" to "qvt=$token; Path=/; HttpOnly; SameSite=Strict"))
    }

    // ---------------- Library ----------------

    private class Library(val albums: List<com.jimmyyu.qvgallery.data.Album>, val byPath: Map<String, MediaItem>, val byAlbum: Map<String, List<MediaItem>>)

    @Volatile private var cached: Pair<Long, Library>? = null

    private fun library(): Library {
        val now = SystemClock.elapsedRealtime()
        cached?.let { (at, lib) -> if (now - at < 15_000) return lib }
        val lib = runBlocking {
            val s = app.settings.settings.first()
            val media = app.media.loadAll(s.extraFolders)
            val albums = GalleryViewModel.buildAlbums(media, s, hidden = false)
            val visible = GalleryViewModel.filterAndSort(media, s, AlbumKeys.ALL)
            Library(albums, visible.associateBy { it.path }, visible.groupBy { it.folder })
        }
        cached = now to lib
        return lib
    }

    private fun albums(out: OutputStream) {
        val arr = JSONArray()
        for (a in library().albums) {
            arr.put(
                JSONObject().put("path", a.path).put("name", a.name).put("count", a.count)
                    .put("size", a.totalSize).put("cover", a.cover.path).put("latest", a.latest)
            )
        }
        json(out, 200, arr)
    }

    private fun media(req: Request, out: OutputStream) {
        val items = library().byAlbum[req.query["album"]].orEmpty()
        val arr = JSONArray()
        for (m in items) {
            arr.put(
                JSONObject().put("path", m.path).put("name", m.name).put("size", m.size).put("date", m.dateTaken)
                    .put("w", m.width).put("h", m.height).put("video", m.isVideo).put("mime", m.mimeType)
            )
        }
        json(out, 200, arr)
    }

    private fun item(req: Request): MediaItem? = req.query["path"]?.let { library().byPath[it] }

    private fun thumb(req: Request, out: OutputStream) {
        val item = item(req) ?: return json(out, 404, JSONObject())
        val bmp: Bitmap = runCatching { app.contentResolver.loadThumbnail(item.uri, Size(320, 320), null) }.getOrNull()
            ?: runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, item.uri)) { d, info, _ ->
                    val scale = maxOf(info.size.width, info.size.height) / 320f
                    if (scale > 1) d.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            ?: return json(out, 404, JSONObject())
        val buf = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, buf)
        head(out, 200, "image/jpeg", buf.size().toLong())
        buf.writeTo(out)
    }

    /** Streams one file; supports a single byte range so browsers can seek in videos. */
    private fun file(req: Request, out: OutputStream) {
        val item = item(req) ?: return json(out, 404, JSONObject())
        val total = item.file.length().takeIf { it > 0 } ?: item.size
        val disposition = (if (req.query["dl"] == "1") "attachment" else "inline") + "; filename*=UTF-8''" + encodeRfc5987(item.name)
        val range = req.headers["range"]?.removePrefix("bytes=")?.split('-')
        var start = 0L
        var end = total - 1
        var status = 200
        if (range != null && range.size == 2 && total > 0) {
            start = range[0].toLongOrNull() ?: 0
            end = range[1].toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            if (start > end) return head(out, 416, "text/plain", 0, mapOf("Content-Range" to "bytes */$total"))
            status = 206
        }
        val length = end - start + 1
        val extra = mutableMapOf("Content-Disposition" to disposition, "Accept-Ranges" to "bytes")
        if (status == 206) extra["Content-Range"] = "bytes $start-$end/$total"
        head(out, status, item.mimeType, length, extra)
        if (req.method == "HEAD") return
        app.contentResolver.openInputStream(item.uri)?.use { input ->
            skipFully(input, start)
            copy(input, out, length)
        }
    }

    /** ZIP of a whole album (GET ?album=) or of the paths posted as a JSON array. */
    private fun zip(req: Request, out: OutputStream) {
        val lib = library()
        val items = if (req.method == "POST") {
            var body = readUpTo(req.body, min(req.contentLength, 4L * 1024 * 1024).toInt()).toString(Charsets.UTF_8)
            // The browser page submits a form (paths=<JSON>) so the download is handled natively.
            if (req.headers["content-type"]?.startsWith("application/x-www-form-urlencoded") == true) {
                body = URLDecoder.decode(body.substringAfter("paths=").substringBefore('&'), "UTF-8")
            }
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { lib.byPath[arr.getString(it)] }
        } else {
            lib.byAlbum[req.query["album"]].orEmpty()
        }
        if (items.isEmpty()) return json(out, 404, JSONObject())
        val name = (req.query["album"]?.substringAfterLast('/') ?: "QV Gallery") + ".zip"
        // Close-delimited body: no Content-Length since the archive is built on the fly.
        head(out, 200, "application/zip", null, mapOf("Content-Disposition" to "attachment; filename*=UTF-8''" + encodeRfc5987(name)))
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.NO_COMPRESSION) // Photos and videos are already compressed.
        val used = HashSet<String>()
        for (item in items) {
            var entry = item.name
            var i = 1
            while (!used.add(entry)) entry = item.name.substringBeforeLast('.') + " (${i++})." + item.name.substringAfterLast('.', "")
            zip.putNextEntry(ZipEntry(entry).apply { time = item.dateModified })
            app.contentResolver.openInputStream(item.uri)?.use { it.copyTo(zip, 256 * 1024) }
            zip.closeEntry()
        }
        zip.finish()
    }

    /** Saves an uploaded photo/video into Pictures/QV Transfer through MediaStore. */
    private fun upload(req: Request, out: OutputStream) {
        val rawName = req.query["name"]?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val name = rawName.replace(Regex("[\\u0000-\\u001f<>:\"|?*]"), "_").ifEmpty { "file" }
        val mime = MediaTypes.mimeFor(name) ?: return json(out, 415, JSONObject().put("error", "only photos and videos"))
        val length = req.contentLength
        val isVideo = mime.startsWith("video/")
        val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (mime.startsWith("image/x-raw")) "image/*" else mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RECEIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = app.contentResolver.insert(collection, values) ?: return json(out, 500, JSONObject().put("error", "cannot create file"))
        val ok = runCatching {
            app.contentResolver.openOutputStream(uri)?.use { o -> copy(req.body, o, length) } ?: error("no stream")
        }.isSuccess
        if (!ok) {
            app.contentResolver.delete(uri, null, null)
            return json(out, 500, JSONObject().put("error", "write failed"))
        }
        app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        cached = null
        onReceived(TransferRecord(name, length, incoming = true, peer = req.headers["x-qv-device"] ?: req.headers["user-agent"]?.let { browserName(it) }))
        json(out, 200, JSONObject().put("ok", true))
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long) {
        val buf = ByteArray(256 * 1024)
        var left = length
        while (left > 0) {
            val n = input.read(buf, 0, min(buf.size.toLong(), left).toInt())
            if (n < 0) throw SocketException("unexpected end of stream")
            out.write(buf, 0, n)
            left -= n
        }
    }

    // InputStream.readNBytes / skipNBytes need Android 13/14, but minSdk is Android 11.
    private fun readUpTo(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r < 0) break
            read += r
        }
        return if (read == n) buf else buf.copyOf(read)
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() < 0) return
                left--
            } else left -= skipped
        }
    }

    private fun encodeRfc5987(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun browserName(ua: String) = when {
        "Edg/" in ua -> "Edge"
        "Chrome/" in ua -> "Chrome"
        "Firefox/" in ua -> "Firefox"
        "Safari/" in ua -> "Safari"
        else -> "Browser"
    }

    companion object {
        private const val TAG = "TransferServer"
        const val PREFERRED_PORT = 8899
        const val RECEIVE_DIR = "Pictures/QV Transfer"
        private const val MAX_ATTEMPTS = 5
        private const val LOCK_MS = 60_000L
    }
}
