package com.example.oshootcleaner

import android.content.Context

/**
 * Persists the "alpha founder" theme unlock. Set once when the user finds
 * the easter egg; never cleared by the app.
 *
 * We store it in a separate SharedPreferences file to make it robust
 * against accidental clearing when the user's theme preferences are reset.
 */
object AlphaUnlockStore {

    private const val PREFS = "oh_shoot_alpha"
    private const val KEY_FOUND_AT = "alpha_found_at_ms"

    /** Theme id reserved for alpha founders. Must match ThemeCatalog.alphaFounder.id. */
    const val THEME_ID = "alpha_founder"

    /**
     * Returns true if the user has found the easter egg at any point.
     * This is a one-way latch — once true, always true, across app updates.
     */
    fun isUnlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_FOUND_AT, 0L) > 0L
    }

    /**
     * Records the unlock. Returns the timestamp of the FIRST unlock so the
     * caller can distinguish "just unlocked" from "already unlocked."
     * If already unlocked, returns the original timestamp.
     */
    fun unlock(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY_FOUND_AT, 0L)
        if (existing > 0L) return existing

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_FOUND_AT, now).commit()
        return now
    }

    /** Timestamp of the first unlock, or 0 if not unlocked. */
    fun unlockTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_FOUND_AT, 0L)
    }
}