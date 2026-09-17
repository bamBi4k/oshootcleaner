package com.example.oshootcleaner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption. Being exempted means Android is far less
 * likely to kill our background worker / widget actions. Without this, Doze
 * and App Standby can defer our WorkManager jobs by hours.
 *
 * Two paths:
 *  - isIgnoringBatteryOptimizations() — tells us the current state
 *  - requestIgnoreBatteryOptimizations() — opens the system dialog
 *
 * The system dialog itself (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 * is only shown if the app declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 * in the manifest. Without that permission declared, this Intent is a
 * no-op on some Android versions.
 */
object BatteryOptimizationHelper {

    fun isExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open the general battery-optimization list, where the
            // user can pick the app manually.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}