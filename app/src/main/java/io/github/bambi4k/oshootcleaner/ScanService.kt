package io.github.bambi4k.oshootcleaner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that hosts the "analysis in progress" notification.
 *
 * It doesn't actually run the scan — the scan runs in the AnalysisTab's
 * coroutine. This service just keeps the app process alive at higher
 * priority while that coroutine is working, and shows live progress.
 *
 * Lifecycle:
 *   start(context)              -> service starts, notification shows
 *   update(context, pct, label) -> notification progress bar updates
 *   stop(context)               -> service stops, notification dismisses
 */
class ScanService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification(0f, ""))
            }
            ACTION_UPDATE -> {
                val pct = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(pct, label))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(progress: Float, label: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(getString(R.string.scan_notif_title))
            .setContentText(label.ifEmpty { getString(R.string.scan_notif_starting) })
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        if (progress > 0f) {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.scan_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.scan_notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "oh_shoot_scan"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_START = "com.example.io.github.bambi4k.oshootcleaner.scan.START"
        private const val ACTION_UPDATE = "com.example.io.github.bambi4k.oshootcleaner.scan.UPDATE"
        private const val ACTION_STOP = "com.example.io.github.bambi4k.oshootcleaner.scan.STOP"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, progress: Float, label: String) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_LABEL, label)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}