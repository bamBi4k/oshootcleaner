package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.content.Intent
import android.provider.Settings

object BatteryTools {

    // --- WRITE_SETTINGS tier ---

    fun isAutoBrightnessOn(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean = try {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        true
    } catch (e: SecurityException) { false }

    fun isHapticFeedbackOn(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1

    fun setHapticFeedback(context: Context, enabled: Boolean): Boolean = try {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            if (enabled) 1 else 0
        )
        true
    } catch (e: SecurityException) { false }

    fun setScreenTimeout(context: Context, millis: Int): Boolean = try {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, millis)
        true
    } catch (e: SecurityException) { false }

    fun currentScreenTimeoutMs(context: Context): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)

    // --- WRITE_SECURE_SETTINGS tier ---

    fun isBatterySaverOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1

    fun setBatterySaver(context: Context, enabled: Boolean): Boolean = try {
        Settings.Global.putInt(context.contentResolver, "low_power", if (enabled) 1 else 0)
        true
    } catch (e: SecurityException) { false }

    // ui_night_mode is a long-standing but UNDOCUMENTED Settings.Secure key.
    // Works on AOSP-based ROMs; some OEM skins may ignore it.
    fun isDarkModeOn(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, "ui_night_mode", 1) == 2

    fun setDarkMode(context: Context, enabled: Boolean): Boolean = try {
        Settings.Secure.putInt(context.contentResolver, "ui_night_mode", if (enabled) 2 else 1)
        true
    } catch (e: SecurityException) { false }

    /**
     * Best-effort peak refresh rate write. Requires WRITE_SECURE_SETTINGS.
     * NOTE: Even when this doesn't throw, most OEM ROMs (Samsung, Xiaomi,
     * Pixel, etc.) route refresh rate through a vendor display service and
     * ignore this key entirely. There is NO public Android API for an app
     * to change system-wide refresh rate. Treat any success as "requested,
     * not confirmed."
     */
    fun requestPeakRefreshRate(context: Context, hz: Float): Boolean = try {
        Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", hz)
        true
    } catch (e: SecurityException) { false }

    // --- Deep links ---

    fun openNotificationSettings(context: Context) {
        val intent = Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openScreenTimeoutSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Display settings — where refresh rate lives on virtually every device. */
    fun openDisplaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}