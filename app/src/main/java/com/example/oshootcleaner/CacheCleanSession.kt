package com.example.oshootcleaner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A single app that has cache worth clearing. We snapshot the cache size
 * at the moment the guided session starts, so the "freed" total stays
 * accurate even if the app's actual cache changes during the flow.
 */
data class CacheCleanTarget(
    val packageName: String,
    val label: String,
    val cacheBytesAtStart: Long,
    val status: Status = Status.PENDING
) {
    enum class Status { PENDING, CLEARED, SKIPPED }
}

/**
 * A guided cache-clean session. Holds the ordered list of targets, tracks
 * which one is currently focused, and computes running totals.
 */
class CacheCleanSession(
    initialTargets: List<CacheCleanTarget>
) {
    val targets = mutableStateListOf<CacheCleanTarget>().also {
        it.addAll(initialTargets)
    }

    var currentIndex by mutableStateOf(0)
        private set

    /**
     * Restores the session to a specific index. Used by
     * CacheCleanSessionStore when rebuilding from disk after process
     * death. The index is clamped so an out-of-range value from a stale
     * file doesn't crash the UI.
     */
    internal fun restoreIndex(index: Int) {
        currentIndex = index.coerceIn(0, targets.size)
    }

    val isFinished: Boolean
        get() = currentIndex >= targets.size

    val current: CacheCleanTarget?
        get() = targets.getOrNull(currentIndex)

    val clearedCount: Int
        get() = targets.count { it.status == CacheCleanTarget.Status.CLEARED }

    val skippedCount: Int
        get() = targets.count { it.status == CacheCleanTarget.Status.SKIPPED }

    /** Total bytes we expected to be freed based on what was cleared. */
    val freedBytesAtStart: Long
        get() = targets
            .filter { it.status == CacheCleanTarget.Status.CLEARED }
            .sumOf { it.cacheBytesAtStart }

    /** Total bytes available in the session (used for the progress bar). */
    val totalBytesAtStart: Long
        get() = targets.sumOf { it.cacheBytesAtStart }

    fun markCleared() {
        val idx = currentIndex
        if (idx in targets.indices) {
            targets[idx] = targets[idx].copy(status = CacheCleanTarget.Status.CLEARED)
        }
        currentIndex++
    }

    fun markSkipped() {
        val idx = currentIndex
        if (idx in targets.indices) {
            targets[idx] = targets[idx].copy(status = CacheCleanTarget.Status.SKIPPED)
        }
        currentIndex++
    }

    fun goBack() {
        if (currentIndex > 0) currentIndex--
    }

    fun finishEarly() {
        currentIndex = targets.size
    }
}

/**
 * Builds a fresh CacheCleanSession by scanning every installed app's
 * cache via StorageStatsManager. Requires Usage Access (which the Storage
 * tab already requests for its breakdown).
 *
 * Returns null if Usage Access is not granted, the API is unavailable, or
 * no apps have cache above [minCacheBytes].
 */
object CacheCleanSessionBuilder {

    suspend fun build(
        context: Context,
        minCacheBytes: Long = 5L * 1024 * 1024
    ): CacheCleanSession? {
        val all = AppSizeScanner.scanAll(context)
        val targets = all
            .filter { it.cacheBytes >= minCacheBytes }
            .sortedByDescending { it.cacheBytes }
            .map { entry ->
                CacheCleanTarget(
                    packageName = entry.packageName,
                    label = entry.label,
                    cacheBytesAtStart = entry.cacheBytes
                )
            }
        return if (targets.isEmpty()) null else CacheCleanSession(targets)
    }
}