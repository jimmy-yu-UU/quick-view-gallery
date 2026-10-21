package com.jimmyyu.qvgallery.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jimmyyu.qvgallery.MainActivity
import com.jimmyyu.qvgallery.R
import com.jimmyyu.qvgallery.app
import com.jimmyyu.qvgallery.tr

/** Keeps the receiver (HTTP server + mDNS announcement) running while the app is in the background. */
class TransferService : Service() {
    private var advertiser: Advertiser? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Stops the receiver once the transfer screen is closed and nothing is being transferred. */
    private val idleCheck = object : Runnable {
        override fun run() {
            if (TransferState.stopWhenIdle && TransferState.isIdle()) {
                stopSelf()
            } else {
                handler.postDelayed(this, 2_000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (TransferState.running == null) {
            val server = TransferServer(app) { record ->
                TransferState.record(record)
                updateNotification()
            }
            val port = server.start()
            TransferState.running = server
            TransferState.setServer(ServerInfo(server.code, port, TransferState.localAddresses()))
            advertiser = Advertiser(this).also { it.start(TransferState.deviceName(this), port) }
            handler.postDelayed(idleCheck, 2_000)
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        return START_NOT_STICKY
    }

    // Android 15 limits data-sync services; stop cleanly when the system says time is up.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        advertiser?.stop()
        TransferState.running?.stop()
        TransferState.running = null
        TransferState.setServer(null)
        super.onDestroy()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.transfer_title), NotificationManager.IMPORTANCE_LOW))
        }
        val info = TransferState.server.value
        val received = TransferState.history.value.count { it.incoming }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TRANSFER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TransferService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(tr(R.string.transfer_receiving))
            .setContentText(
                listOfNotNull(
                    info?.let { tr(R.string.pairing_code_fmt, it.code) },
                    info?.urls?.firstOrNull(),
                    if (received > 0) tr(R.string.n_received, received) else null,
                ).joinToString(" · ")
            )
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, tr(R.string.stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "transfer"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "stop"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, TransferService::class.java))
    }
}
