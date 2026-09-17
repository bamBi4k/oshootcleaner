package com.example.oshootcleaner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * Records the last time the phone reached 100% charge. Persisted in
 * SharedPreferences, survives reboots (unlike battery status which resets).
 *
 * The receiver registers itself on app start and stays alive for the
 * process lifetime. Every BATTERY_CHANGED broadcast (which Android sends
 * roughly every 60s) is checked for level == 100.
 */
object LastFullChargeStore {

    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_LAST_FULL_MS = "battery_last_full_ms"

    fun lastFullMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_FULL_MS, 0L)
    }

    private fun record(context: Context, ts: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_FULL_MS, ts)
            .apply()
    }

    /**
     * Registers a receiver that listens for the battery hitting 100%.
     * Safe to call multiple times — Android deduplicates identical
     * (receiver, filter) pairs. The receiver lives until the app process
     * is killed, which is fine: on next app start we re-register.
     */
    fun startMonitoring(context: Context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val pct = level * 100 / scale
                if (pct >= 100) {
                    record(ctx.applicationContext, System.currentTimeMillis())
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
            // Registration failure shouldn't crash the app.
        }
    }
}