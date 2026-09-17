package com.example.oshootcleaner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.delay
import java.io.File

data class CleanupResult(
    val freedBytes: Long,
    val filesCleared: Int,
    val webViewCleared: Boolean,
    val availMemBeforeMb: Long,
    val availMemAfterMb: Long,
    val totalMemMb: Long,
    val lowMemory: Boolean,
    val durationMs: Long,
) {
    val memFreedMb: Long get() = (availMemAfterMb - availMemBeforeMb).coerceAtLeast(0L)
}

data class EvictionResult(
    val requested: Int,
    val considered: Int,
)

object CleanupManager {

    private fun wipeDirContents(dir: File?): Pair<Long, Int> {
        if (dir == null || !dir.exists()) return 0L to 0
        var freed = 0L
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                val (sub, subCount) = wipeDirContents(f)
                f.delete()
                freed += sub
                count += subCount
            } else {
                val size = f.length()
                if (f.delete()) {
                    freed += size
                    count++
                }
            }
        }
        return freed to count
    }

    private fun memInfo(context: Context): ActivityManager.MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info
    }

    fun runQuickClean(context: Context): CleanupResult {
        val startNs = System.nanoTime()
        val before = memInfo(context)

        var freed = 0L
        var files = 0

        wipeDirContents(context.cacheDir).let { (b, c) -> freed += b; files += c }
        wipeDirContents(context.externalCacheDir).let { (b, c) -> freed += b; files += c }
        wipeDirContents(context.codeCacheDir).let { (b, c) -> freed += b; files += c }

        var webViewCleared = false
        try {
            WebView(context).clearCache(true)
            WebStorage.getInstance().deleteAllData()
            webViewCleared = true
        } catch (_: Exception) { }

        Runtime.getRuntime().gc()

        val after = memInfo(context)
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L

        return CleanupResult(
            freedBytes = freed,
            filesCleared = files,
            webViewCleared = webViewCleared,
            availMemBeforeMb = before.availMem / (1024 * 1024),
            availMemAfterMb = after.availMem / (1024 * 1024),
            totalMemMb = after.totalMem / (1024 * 1024),
            lowMemory = after.lowMemory,
            durationMs = durationMs,
        )
    }

    suspend fun restartBackgroundApps(
        context: Context,
        onLine: suspend (String) -> Unit
    ): EvictionResult {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val selfPackage = context.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val targets = pm.queryIntentActivities(launcherIntent, 0)
            .filter { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == selfPackage) return@filter false
                if (SensitivePackages.isSensitive(pkg)) return@filter false

                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystem
            }
            .map { it.activityInfo.packageName }
            .distinct()
            .sorted()

        onLine("Found ${targets.size} user-installed apps.")

        var requested = 0
        for (pkg in targets) {
            try {
                am.killBackgroundProcesses(pkg)
                onLine("kill_background_processes($pkg) -> requested")
                requested++
            } catch (_: SecurityException) {
                onLine("kill_background_processes($pkg) -> denied")
            }
            delay(12)
        }

        onLine("Done. $requested / ${targets.size} eviction requests sent.")
        return EvictionResult(requested, targets.size)
    }

    fun openSystemStorageSettings(context: Context) {
        launchOrFallback(
            context,
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launchOrFallback(context, intent, Intent(Settings.ACTION_SETTINGS))
    }

    fun openManageApplications(context: Context) {
        launchOrFallback(
            context,
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openDownloads(context: Context) {
        launchOrFallback(
            context,
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openBatteryUsage(context: Context) {
        launchOrFallback(
            context,
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openDeveloperOptions(context: Context) {
        launchOrFallback(
            context,
            Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun launchOrFallback(context: Context, primary: Intent, fallback: Intent) {
        primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(primary)
        } catch (_: Exception) {
            context.startActivity(fallback)
        }
    }
}