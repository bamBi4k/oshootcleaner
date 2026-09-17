package io.github.bambi4k.oshootcleaner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.os.storage.StorageManager

data class StorageBreakdown(
    val appsBytes: Long,
    val cacheBytes: Long,
    val photosVideosBytes: Long,
    val downloadsBytes: Long,
    val otherBytes: Long, // remainder = total used - everything counted above
    val hasUsageAccess: Boolean,
    val hasMediaAccess: Boolean
)

object StorageBreakdownManager {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccess(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** The permission string(s) to request at runtime for media size totals — varies by API level. */
    fun mediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaAccess(context: Context): Boolean {
        return mediaPermissions().all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Sums app + cache bytes across every installed app's own storage volume via StorageStatsManager. */
    private fun appsAndCacheBytes(context: Context): Pair<Long, Long> {
        if (!hasUsageAccess(context)) return 0L to 0L
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = context.packageManager

        var appTotal = 0L
        var cacheTotal = 0L
        val uuid = StorageManager.UUID_DEFAULT

        val apps = pm.getInstalledApplications(0)
        for (appInfo in apps) {
            try {
                val stats = statsManager.queryStatsForUid(uuid, appInfo.uid)
                appTotal += stats.appBytes
                cacheTotal += stats.cacheBytes
            } catch (e: Exception) {
                // Per-app query can fail (uninstalled mid-scan, no access) — skip, don't fabricate.
            }
        }
        return appTotal to cacheTotal
    }

    private fun sumMediaCollection(context: Context, uri: Uri): Long {
        if (!hasMediaAccess(context)) return 0L
        var total = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    total += cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            // Query can fail on some OEM ROMs — treat as unavailable, not zero-and-silent.
        }
        return total
    }

    fun compute(context: Context): StorageBreakdown {
        val storage = SystemStats.storage()
        val (apps, cache) = appsAndCacheBytes(context)

        val photosVideos = sumMediaCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) +
            sumMediaCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        val downloads = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sumMediaCollection(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        } else 0L

        val accountedFor = apps + cache + photosVideos + downloads
        val other = (storage.usedBytes - accountedFor).coerceAtLeast(0L)

        return StorageBreakdown(
            appsBytes = apps,
            cacheBytes = cache,
            photosVideosBytes = photosVideos,
            downloadsBytes = downloads,
            otherBytes = other,
            hasUsageAccess = hasUsageAccess(context),
            hasMediaAccess = hasMediaAccess(context)
        )
    }
}
