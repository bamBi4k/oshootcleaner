package io.github.bambi4k.oshootcleaner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings

/**
 * Every toggle here is a REAL Settings.Global key. All three require
 * WRITE_SECURE_SETTINGS, which Android only grants via:
 *   adb shell pm grant com.example.io.github.bambi4k.oshootcleaner android.permission.WRITE_SECURE_SETTINGS
 * There is no in-app / Settings-app toggle that grants this — it's ADB or
 * root, no way around it. That's a one-time command on a computer, not
 * root and not Shizuku, but it's honest to say it's not "zero setup" either.
 *
 * Deliberately NOT included, because they aren't real Settings keys at all
 * (so no permission grant would make them work): "limit background
 * processes" (an internal ActivityManager call, not a Settings row) and
 * "show GPU overdraw" (routed through WindowManager internals in most
 * Android versions, not a stable public key). Those stay as a deep link
 * to Developer Options in the UI instead of a fake switch.
 */
object DevToolsManager {

    const val GRANT_COMMAND = "adb shell pm grant com.example.io.github.bambi4k.oshootcleaner android.permission.WRITE_SECURE_SETTINGS"

    fun hasSecureSettingsAccess(context: Context): Boolean {
        return try {
            // Re-write the current value of a real key we already use — a no-op
            // functionally, but it throws SecurityException without the
            // permission, which is all we need to detect. Avoids littering
            // Settings.Global with throwaway probe keys.
            val current = Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0)
            Settings.Global.putInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, current)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun copyGrantCommand(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("adb grant command", GRANT_COMMAND))
    }

    fun isDontKeepActivitiesOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0) == 1

    fun setDontKeepActivities(context: Context, enabled: Boolean): Boolean = writeGlobal(
        context, Settings.Global.ALWAYS_FINISH_ACTIVITIES, if (enabled) 1 else 0
    )

    fun isForceGpuRenderingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "force_gpu_rendering", 0) == 1

    fun setForceGpuRendering(context: Context, enabled: Boolean): Boolean = writeGlobal(
        context, "force_gpu_rendering", if (enabled) 1 else 0
    )

    fun isUsbDebuggingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun setUsbDebugging(context: Context, enabled: Boolean): Boolean = writeGlobal(
        context, Settings.Global.ADB_ENABLED, if (enabled) 1 else 0
    )

    /** Resets only the two non-security-sensitive toggles above back to Android defaults (off). */
    fun resetToDefaults(context: Context): Boolean {
        val a = setDontKeepActivities(context, false)
        val b = setForceGpuRendering(context, false)
        return a && b
    }

    private fun writeGlobal(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
