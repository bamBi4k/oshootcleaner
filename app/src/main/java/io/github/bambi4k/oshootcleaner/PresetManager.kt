package io.github.bambi4k.oshootcleaner

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PresetManager {

    enum class Preset { POWER_SAVING, BALANCED, PERFORMANCE }

    fun hasWriteSettingsAccess(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun requestWriteSettingsAccess(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun requestNotificationPolicyAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun apply(context: Context, preset: Preset): List<String> {
        val lines = mutableListOf<String>()
        val resolver = context.contentResolver

        if (hasWriteSettingsAccess(context)) {
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val brightness = when (preset) {
                Preset.POWER_SAVING -> 40
                Preset.BALANCED -> 130
                Preset.PERFORMANCE -> 255
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            lines += "SCREEN_BRIGHTNESS -> $brightness/255 (manual mode)"

            val timeoutMs = when (preset) {
                Preset.POWER_SAVING -> 15_000
                Preset.BALANCED -> 30_000
                Preset.PERFORMANCE -> 600_000
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            lines += "SCREEN_OFF_TIMEOUT -> ${timeoutMs / 1000}s"

            val rotation = if (preset == Preset.POWER_SAVING) 0 else 1
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, rotation)
            lines += "ACCELEROMETER_ROTATION -> ${if (rotation == 1) "on" else "locked (saves a little power)"}"
        } else {
            lines += "Skipped brightness/timeout/rotation — \"Modify system settings\" not granted yet."
        }

        if (hasNotificationPolicyAccess(context)) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = if (preset == Preset.POWER_SAVING)
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else
                NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(filter)
            lines += "Do Not Disturb -> ${describeFilter(filter)}"
        } else {
            lines += "Skipped Do Not Disturb — notification policy access not granted yet."
        }

        lines += ""
        lines += "Needs one manual tap (Android reserves these for the Settings app itself):"
        when (preset) {
            Preset.POWER_SAVING -> {
                lines += " - Battery Saver: ON"
                lines += " - Refresh rate: Settings > Display > Smooth Display (lowest)"
                lines += "   (No public Android API exists for apps to change refresh rate.)"
            }
            Preset.PERFORMANCE -> {
                lines += " - Battery Saver: OFF"
                lines += " - Refresh rate: Settings > Display > Smooth Display (highest)"
                lines += "   (No public Android API exists for apps to change refresh rate.)"
                lines += " - Developer options -> Animator duration scale: .5x or off"
            }
            Preset.BALANCED -> {
                lines += " - Battery Saver: OFF"
                lines += " - Refresh rate: Settings > Display > Smooth Display (device default)"
                lines += "   (No public Android API exists for apps to change refresh rate.)"
            }
        }
        return lines
    }

    private fun describeFilter(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off (all notifications)"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority only"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms only"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "total silence"
        else -> "unknown"
    }
}