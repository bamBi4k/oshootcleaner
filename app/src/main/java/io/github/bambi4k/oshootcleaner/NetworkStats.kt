package io.github.bambi4k.oshootcleaner

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process

data class NetworkUsage(
    val wifiBytes: Long,
    val mobileBytes: Long,
    val available: Boolean
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

object NetworkStats {

    fun deviceWide(context: Context): NetworkUsage {
        if (!hasUsageAccess(context)) return NetworkUsage(0, 0, available = false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return NetworkUsage(0, 0, available = false)

        val end = System.currentTimeMillis()
        val start = end - 30L * 24 * 60 * 60 * 1000

        val (mobileBytes, wifiBytes) = queryWindow(context, start, end)
        return NetworkUsage(wifiBytes, mobileBytes, available = true)
    }

    /**
     * Query mobile/wifi bytes over an explicit time window.
     * Returns Pair(mobileBytes, wifiBytes). Zeroes on failure.
     */
    fun queryWindow(context: Context, startMs: Long, endMs: Long): Pair<Long, Long> {
        if (!hasUsageAccess(context)) return 0L to 0L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0L to 0L

        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE)
                as NetworkStatsManager

        val mobile = try {
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE, null, startMs, endMs
            )
            bucket.rxBytes + bucket.txBytes
        } catch (_: Exception) { 0L }

        val wifi = try {
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI, null, startMs, endMs
            )
            bucket.rxBytes + bucket.txBytes
        } catch (_: Exception) { 0L }

        return mobile to wifi
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

fun formatBytesMbGb(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}