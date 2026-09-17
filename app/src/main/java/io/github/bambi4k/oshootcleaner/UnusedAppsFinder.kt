package io.github.bambi4k.oshootcleaner

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

object UnusedAppsFinder {

    /**
     * Timestamps before 2010 are placeholder values written by OEM ROMs.
     */
    private const val MIN_VALID_MS = 1_262_304_000_000L

    /**
     * The yearly bucket is the most reliable source of "when was this app
     * last opened by the user." Android Settings uses the same source.
     *
     * It survives for as long as the app is installed. The event stream is
     * contaminated by system-initiated resumes (media resumption, share
     * sheets, edge lighting), so we do NOT let it override the yearly value
     * when the yearly value is significantly older.
     */
    fun findUnused(
        context: Context,
        thresholdDays: Int = 60
    ): UnusedAppsResult {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val cutoff = now - thresholdDays.toLong() * 24 * 60 * 60 * 1000

        // Single source of truth: yearly usage stats.
        val lastUsed = readYearlyLastUsed(usm, now)

        val installed = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList()
        }

        val out = mutableListOf<UnusedApp>()

        for (app in installed) {
            val pkg = app.packageName
            if (pkg == context.packageName) continue

            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue

            if (!hasLauncherIcon(pm, pkg)) continue

            val ts = lastUsed[pkg] ?: continue
            if (ts < MIN_VALID_MS || ts > now + 60_000L) continue
            if (ts >= cutoff) continue

            out += UnusedApp(
                packageName = pkg,
                label = pm.getApplicationLabel(app).toString(),
                lastUsedMs = ts
            )
        }

        return UnusedAppsResult(
            apps = out.sortedBy { it.lastUsedMs },
            insufficientHistory = false,
            daysOfHistoryAvailable = 365
        )
    }

    /**
     * Reads `lastTimeUsed` from the yearly aggregate for every package.
     * This is what Android Settings > Apps > Special access > Last used
     * reads, and it's the number users see and trust.
     */
    private fun readYearlyLastUsed(
        usm: UsageStatsManager,
        now: Long
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                now - 365L * 24 * 60 * 60 * 1000,
                now
            )
            stats?.forEach { s ->
                val ts = s.lastTimeUsed
                if (ts > 0L) {
                    // If we see the same package twice, keep the newest value.
                    val prev = result[s.packageName] ?: 0L
                    if (ts > prev) result[s.packageName] = ts
                }
            }
        } catch (_: Exception) { }
        return result
    }

    private fun hasLauncherIcon(pm: PackageManager, pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(pkg)
        return try {
            pm.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
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