package com.jimmyyu.qvgallery.transfer

import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WrongCodeException : Exception("wrong pairing code")
class LockedException(val retryMs: Long) : Exception("too many attempts")

/** Talks to another device's [TransferServer]. All calls block; run them off the main thread. */
class TransferClient(private val host: String, private val port: Int) {
    private fun open(path: String, method: String): HttpURLConnection =
        (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 60_000
        }

    /** Returns the device name if a QV Gallery receiver answers at this address. */
    fun info(): String {
        val c = open("/api/info", "GET")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            check(json.optString("app") == "QV Gallery") { "not a QV Gallery receiver" }
            return json.optString("name")
        } finally {
            c.disconnect()
        }
    }

    fun login(code: String): String {
        val c = open("/api/login", "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
            return when (c.responseCode) {
                200 -> JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getString("token")
                403 -> throw WrongCodeException()
                429 -> throw LockedException(JSONObject(c.errorStream.bufferedReader().use { it.readText() }).optLong("retryMs"))
                else -> error("HTTP ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    /** Uploads one file of exactly [size] bytes; [onProgress] gets the bytes sent so far. */
    fun upload(token: String, device: String, name: String, size: Long, input: InputStream, onProgress: (Long) -> Unit) {
        val c = open("/api/upload?name=" + URLEncoder.encode(name, "UTF-8"), "PUT")
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(size)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setRequestProperty("X-QV-Token", token)
            c.setRequestProperty("X-QV-Device", device)
            c.outputStream.use { out ->
                val buf = ByteArray(256 * 1024)
                var sent = 0L
                while (sent < size) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), size - sent).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    onProgress(sent)
                }
            }
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
        } finally {
            c.disconnect()
        }
    }
}
