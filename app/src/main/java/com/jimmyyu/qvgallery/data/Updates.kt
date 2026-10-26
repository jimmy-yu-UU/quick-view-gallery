package com.jimmyyu.qvgallery.data

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.jimmyyu.qvgallery.R
import com.jimmyyu.qvgallery.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
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

    /**
     * Installs the downloaded APK through a PackageInstaller session; the system shows its usual
     * confirmation screen. Installing silently (USER_ACTION_NOT_REQUIRED) made Play Protect flag the
     * app as harmful, since downloading and installing APKs without asking is how droppers behave.
     * Returns false when the user first has to allow installing from this app.
     */
    suspend fun install(info: ReleaseInfo): Boolean = withContext(Dispatchers.IO) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return@withContext false
        }
        val file = fileFor(info)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
        true
    }
}

/** Receives the install session result and shows the system's confirmation screen when asked to. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The app is replaced and this process ends.
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, tr(R.string.install_failed, msg), Toast.LENGTH_LONG).show()
            }
        }
    }
}
