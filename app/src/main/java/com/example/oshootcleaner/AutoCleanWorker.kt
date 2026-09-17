package com.example.oshootcleaner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutoCleanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val clean = CleanupManager.runQuickClean(applicationContext)
        CleanupManager.restartBackgroundApps(applicationContext) { }

        // Only notify if we actually freed something meaningful.
        if (clean.freedBytes > 1024 * 1024) {  // > 1 MB
            notifyFreed(clean.freedBytes)
        }

        return Result.success()
    }

    private fun notifyFreed(bytes: Long) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        val channelId = "auto_clean"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notif_channel_auto_clean),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notif_channel_auto_clean_desc)
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle(applicationContext.getString(R.string.notif_auto_clean_title))
            .setContentText(
                applicationContext.getString(
                    R.string.notif_auto_clean_text,
                    formatBytesMbGb(bytes)
                )
            )
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notification)
    }
}

object AutoCleanScheduler {

    private const val WORK_NAME = "oh_shoot_auto_clean"

    /**
     * Schedules (or reschedules) the periodic cleanup. When disabled, cancels
     * the existing work. Interval is 24h minimum enforced by WorkManager, so
     * "weekly" here really means weekly.
     */
    fun setEnabled(context: Context, enabled: Boolean, intervalDays: Int = 7) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(
            intervalDays.toLong(), TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(computeInitialDelayMs(intervalDays), TimeUnit.MILLISECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * First run ~1 hour after scheduling, so the user sees an effect soon
     * without it firing immediately while they're still in the app.
     */
    private fun computeInitialDelayMs(intervalDays: Int): Long {
        // Simple: 1 hour from now. (Previously used a "same time next week"
        // calc, but the user pattern isn't clear enough to justify it.)
        return TimeUnit.HOURS.toMillis(1)
    }
}