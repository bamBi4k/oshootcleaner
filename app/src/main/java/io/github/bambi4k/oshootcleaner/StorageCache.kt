package io.github.bambi4k.oshootcleaner

import android.content.Context

object StorageCache {
    private const val TTL_MS = 60_000L

    @Volatile private var cached: StorageBreakdown? = null
    @Volatile private var timestamp: Long = 0L

    fun get(context: Context): StorageBreakdown {
        val now = System.currentTimeMillis()
        val hit = cached
        if (hit != null && now - timestamp < TTL_MS) return hit

        val fresh = StorageBreakdownManager.compute(context)
        cached = fresh
        timestamp = now
        return fresh
    }

    fun invalidate() {
        cached = null
        timestamp = 0L
    }
}