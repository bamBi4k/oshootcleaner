package io.github.bambi4k.oshootcleaner

import android.content.Context

object AnalysisPrefs {
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_SORT = "analysis_sort_id"
    private const val KEY_LAST_SCAN_MS = "analysis_last_scan_ms"
    private const val KEY_STALE = "analysis_stale"

    fun sort(context: Context): AnalysisSort {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AnalysisSort.fromId(prefs.getString(KEY_SORT, null))
    }

    fun setSort(context: Context, sort: AnalysisSort) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SORT, sort.id)
            .apply()
    }

    fun lastScanMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SCAN_MS, 0L)
    }

    fun setLastScanNow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SCAN_MS, System.currentTimeMillis())
            .putBoolean(KEY_STALE, false)
            .commit()
    }

    /** Has anything happened since the last scan that would make it stale? */
    fun isStale(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_STALE, false)
    }

    /**
     * Marks the current scan as stale. Called by any cleanup action so the
     * Analysis tab knows to re-scan when the user comes back.
     */
    fun markStale(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_STALE, true)
            .apply()
    }
}

fun formatTimeAgo(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): TimeAgo {
    if (timestampMs <= 0L) return TimeAgo(0, TimeAgoUnit.NEVER)
    val delta = nowMs - timestampMs
    val sec = delta / 1000
    val min = sec / 60
    val hr = min / 60
    val day = hr / 24
    return when {
        day >= 7 -> TimeAgo((day / 7).toInt(), TimeAgoUnit.WEEKS)
        day >= 1 -> TimeAgo(day.toInt(), TimeAgoUnit.DAYS)
        hr >= 1 -> TimeAgo(hr.toInt(), TimeAgoUnit.HOURS)
        min >= 1 -> TimeAgo(min.toInt(), TimeAgoUnit.MINUTES)
        else -> TimeAgo(sec.toInt(), TimeAgoUnit.SECONDS)
    }
}

data class TimeAgo(val value: Int, val unit: TimeAgoUnit)

enum class TimeAgoUnit { NEVER, SECONDS, MINUTES, HOURS, DAYS, WEEKS }