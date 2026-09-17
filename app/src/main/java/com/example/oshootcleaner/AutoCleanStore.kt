package com.example.oshootcleaner

import android.content.Context

object AutoCleanStore {
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_ENABLED = "auto_clean_enabled"
    private const val KEY_DAYS = "auto_clean_days"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun days(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAYS, 7)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        AutoCleanScheduler.setEnabled(context, enabled, days(context))
    }
}