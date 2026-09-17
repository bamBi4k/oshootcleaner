package com.example.oshootcleaner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import java.util.UUID

data class AppSizeEntry(
    val packageName: String,
    val label: String,
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isUserApp: Boolean
)

object AppSizeScanner {

    /**
     * Returns every installed app's on-disk footprint via StorageStatsManager.
     * Requires Usage Access. Empty list if not granted, or on Android < 8.
     *
     * The returned list includes a flag [AppSizeEntry.isUserApp] that
     * distinguishes user-installed apps (which have launcher icons and
     * are safe targets for the auto-clean automation) from system
     * services (which should never be touched by automation).
     */
    fun scanAll(context: Context): List<AppSizeEntry> {
        if (!StorageBreakdownManager.hasUsageAccess(context)) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE)
                as StorageStatsManager
        val pm = context.packageManager
        val uuid = StorageManager.UUID_DEFAULT

        val out = mutableListOf<AppSizeEntry>()
        for (app in pm.getInstalledApplications(0)) {
            try {
                val stats = statsManager.queryStatsForUid(uuid, app.uid)
                val total = stats.appBytes + stats.dataBytes + stats.cacheBytes
                if (total <= 0) continue

                out += AppSizeEntry(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    totalBytes = total,
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                    isUserApp = isUserApp(context, app, pm)
                )
            } catch (_: Exception) {
                // Skip apps we can't query — OEM restriction or mid-uninstall.
            }
        }
        return out.sortedByDescending { it.totalBytes }
    }

    /**
     * Convenience overload used by callers that only have the package
     * name. Re-queries the PackageManager to make the same decision.
     */
    fun isUserApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            isUserApp(context, info, pm)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True if the app is user-installed and safe to target for automated
     * cache cleaning. We require BOTH:
     *
     *   1. Not a system-only app — i.e., the FLAG_SYSTEM bit is clear, OR
     *      the FLAG_UPDATED_SYSTEM_APP bit is set (preinstalled app that
     *      the user has since updated, so it behaves like a user app).
     *
     *   2. Has a launcher icon — i.e., it responds to ACTION_MAIN +
     *      CATEGORY_LAUNCHER. This excludes system services, background
     *      providers, keyboards, launchers, wallpapers, etc.
     *
     * Together these filters exclude every system-only package that would
     * appear in StorageStatsManager results but shouldn't be cleaned.
     */
    private fun isUserApp(
        context: Context,
        info: ApplicationInfo,
        pm: PackageManager
    ): Boolean {
        val isSystemOnly = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isSystemOnly) return false

        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(info.packageName)

        return try {
            pm.queryIntentActivities(launcherIntent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}