package com.jimmyyu.qvgallery.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jimmyyu.qvgallery.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String,
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
}

class ReleaseNotFound : Exception("no public release")

/** Looks up the latest GitHub release of this app. */
object UpdateChecker {
    /** Minimum time between automatic checks. */
    const val AUTO_INTERVAL_MS = 30 * 60 * 1000L

    suspend fun latest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "QVGallery/${BuildConfig.VERSION_NAME}")
                // GitHub answers 404 for private repositories and for repositories without releases.
                if (conn.responseCode == 404) throw ReleaseNotFound()
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                parse(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }))
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parse(json: JSONObject): ReleaseInfo {
        val tag = json.getString("tag_name")
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        var apkName = "QVGallery-${tag.removePrefix("v")}.apk"
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    apkName = a.getString("name")
                    break
                }
            }
        }
        return ReleaseInfo(
            versionCode = versionCodeOf(tag),
            versionName = tag.removePrefix("v"),
            notes = json.optString("body"),
            pageUrl = json.optString("html_url"),
            apkUrl = apkUrl,
            apkName = apkName,
        )
    }

    /** Tags are "v1.0.<build number>" and the build number is the versionCode. */
    fun versionCodeOf(tag: String): Int = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: 0

    /**
     * Release notes as plain text in the app's language: the "## 更新內容" section for Chinese,
     * "## What's new" otherwise; markdown markers and the commit footer are dropped.
     */
    fun plainNotes(notes: String, chinese: Boolean = Locale.getDefault().language == "zh"): String {
        val body = notes.lines().takeWhile { it.trim() != "---" }
        val zhStart = body.indexOfFirst { it.startsWith("## 更新內容") }
        val enStart = body.indexOfFirst { it.startsWith("## What") }
        val section = when {
            zhStart < 0 || enStart < 0 -> body
            chinese -> body.subList(zhStart + 1, if (enStart > zhStart) enStart else body.size)
            else -> body.subList(enStart + 1, if (zhStart > enStart) zhStart else body.size)
        }
        return section.map { it.replace(Regex("^#+\\s*"), "") }.joinToString("\n").trim()
    }
}

/** Downloads the APK with the system DownloadManager into app-private storage and launches the installer. */
class ApkDownloader(private val context: Context) {
    private val dm = context.getSystemService(DownloadManager::class.java)

    fun fileFor(info: ReleaseInfo) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), info.apkName)

    fun start(info: ReleaseInfo): Long {
        val file = fileFor(info)
        file.parentFile?.mkdirs()
        file.delete()
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("QV Gallery ${info.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        return dm.enqueue(request)
    }

    sealed interface State {
        data class Running(val fraction: Float?) : State
        data object Done : State
        data object Failed : State
    }

    fun state(id: Long): State {
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return State.Failed
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> State.Done
                DownloadManager.STATUS_FAILED -> State.Failed
                else -> State.Running(if (total > 0) done.toFloat() / total else null)
            }
        }
        return State.Failed
    }

    fun cancel(id: Long) {
        dm.remove(id)
    }

    /** Returns false when the user first has to allow installing from this app. */
    fun install(info: ReleaseInfo): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", fileFor(info))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
