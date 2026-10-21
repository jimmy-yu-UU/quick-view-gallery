package com.jimmyyu.qvgallery.ui

import com.jimmyyu.qvgallery.tr
import com.jimmyyu.qvgallery.R
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jimmyyu.qvgallery.BuildConfig
import com.jimmyyu.qvgallery.data.ApkDownloader
import com.jimmyyu.qvgallery.data.ReleaseInfo
import com.jimmyyu.qvgallery.data.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Offers a new release: shows its notes, downloads the APK in-app and opens the installer. */
@Composable
fun UpdateDialog(info: ReleaseInfo, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloader = remember { ApkDownloader(context) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var state by remember { mutableStateOf<ApkDownloader.State?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val s = downloader.state(id)
            state = s
            if (s !is ApkDownloader.State.Running) break
            delay(400)
        }
        if (state == ApkDownloader.State.Done) downloader.install(info)
    }

    fun openPage() = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)))

    AlertDialog(
        onDismissRequest = { if (downloadId == null || state != null && state !is ApkDownloader.State.Running) onDismiss() },
        title = { Text(tr(R.string.update_available, info.versionName)) },
        text = {
            Column {
                Text(tr(R.string.current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    UpdateChecker.plainNotes(info.notes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
                when (val s = state) {
                    is ApkDownloader.State.Running -> {
                        Spacer(Modifier.height(12.dp))
                        Text(tr(R.string.downloading), style = MaterialTheme.typography.bodySmall)
                        if (s.fraction != null) {
                            LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ApkDownloader.State.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(tr(R.string.download_failed), color = MaterialTheme.colorScheme.error)
                    }
                    ApkDownloader.State.Done -> {
                        Spacer(Modifier.height(12.dp))
                        Text(tr(R.string.installing_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    null -> Unit // Not downloading yet: nothing extra to show.
                }
            }
        },
        confirmButton = {
            when (state) {
                null -> TextButton(onClick = {
                    if (info.apkUrl != null) downloadId = downloader.start(info) else openPage()
                }) { Text(tr(R.string.download_and_update)) }
                is ApkDownloader.State.Running -> TextButton(onClick = {
                    downloadId?.let { downloader.cancel(it) }
                    downloadId = null
                    state = null
                }) { Text(tr(R.string.cancel_download)) }
                ApkDownloader.State.Done -> TextButton(onClick = { scope.launch { downloader.install(info) } }) { Text(tr(R.string.install)) }
                ApkDownloader.State.Failed -> TextButton(onClick = { openPage() }) { Text(tr(R.string.open_in_browser)) }
            }
        },
        dismissButton = {
            if (state == null) {
                Row {
                    TextButton(onClick = onSkip) { Text(tr(R.string.skip_version)) }
                    TextButton(onClick = onDismiss) { Text(tr(R.string.later)) }
                }
            }
        },
    )
}
