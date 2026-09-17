package com.example.oshootcleaner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*

class DataUsageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usage = DataPlanQuerier.query(applicationContext)
        if (!usage.hasCap || !usage.available) return Result.success()

        val prefs = applicationContext.getSharedPreferences("oh_shoot_prefs", Context.MODE_PRIVATE)
        val lastNotifiedThreshold = prefs.getInt("data_usage_last_threshold", 0)

        val currentThreshold = when {
            usage.fraction >= 1f -> 100
            usage.fraction >= 0.8f -> 80
            else -> 0
        }

        if (currentThreshold > lastNotifiedThreshold) {
            notify(usage, currentThreshold)
            prefs.edit().putInt("data_usage_last_threshold", currentThreshold).apply()
        }

        // Reset once the cycle rolls over and usage drops back down.
        if (currentThreshold == 0 && lastNotifiedThreshold > 0) {
            prefs.edit().putInt("data_usage_last_threshold", 0).apply()
        }

        return Result.success()
    }

    private fun notify(usage: DataPlanUsage, threshold: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        val channelId = "data_usage"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    applicationContext.getString(R.string.notif_channel_data_usage),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = applicationContext.getString(R.string.notif_channel_data_usage_desc)
                }
            )
        }

        val title = if (threshold >= 100)
            applicationContext.getString(R.string.notif_data_over_cap)
        else
            applicationContext.getString(R.string.notif_data_near_cap)

        val text = applicationContext.getString(
            R.string.notif_data_usage_body,
            formatBytesMbGb(usage.usedBytes),
            formatBytesMbGb(usage.capBytes)
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        nm.notify(2001, notification)
    }
}