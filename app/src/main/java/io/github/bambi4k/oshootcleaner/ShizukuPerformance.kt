package io.github.bambi4k.oshootcleaner

import android.util.Log

/**
 * Shizuku-only privileged settings for the Performance tab.
 *
 * Everything here requires an active Shizuku connection. Without it
 * every function returns null or false, and the UI should render the
 * corresponding control as disabled.
 *
 * Each setter writes a single Settings.Global key via `settings put
 * global ...` through the shell, and reads it back to confirm the write
 * took effect. Getters use `settings get global ...` and parse the
 * output.
 *
 * Nothing here touches user data. All keys are the same ones Android's
 * own Developer Options screen controls — we're just writing them
 * without requiring the user to open that screen.
 */
object ShizukuPerformance {

    private const val TAG = "ShizukuPerformance"

    // Keys, matching Android's own Developer Options.
    private const val KEY_WINDOW_ANIM = "window_animation_scale"
    private const val KEY_TRANSITION_ANIM = "transition_animation_scale"
    private const val KEY_ANIMATOR_DURATION = "animator_duration_scale"
    private const val KEY_BG_PROCESS_LIMIT = "background_process_limit"
    private const val KEY_ALWAYS_FINISH = "always_finish_activities"

    // ---------------------------------------------------------------------
    // Animation scale
    // ---------------------------------------------------------------------

    /**
     * Animation speeds the user can pick. The labels are resolved
     * against string resources in the UI layer; the values here are
     * what we actually write to settings.
     */
    enum class AnimationSpeed(val scale: Float) {
        OFF(0f),
        HALF(0.5f),
        NORMAL(1f)
    }

    /**
     * Reads the current window animation scale. Returns null if Shizuku
     * isn't connected or the shell call fails.
     */
    fun getAnimationSpeed(): AnimationSpeed? {
        val raw = readSetting(KEY_WINDOW_ANIM) ?: return null
        val value = raw.toFloatOrNull() ?: return null
        return when {
            value <= 0.01f -> AnimationSpeed.OFF
            value <= 0.75f -> AnimationSpeed.HALF
            else -> AnimationSpeed.NORMAL
        }
    }

    /**
     * Sets all three animation scales — window, transition, and
     * animator duration. Android expects all three to move together;
     * setting only one leaves the OS in an inconsistent state.
     *
     * Returns true on success (verified by reading back the window
     * value).
     */
    fun setAnimationSpeed(speed: AnimationSpeed): Boolean {
        val s = speed.scale
        val result = ShizukuManager.runShellCommand(
            "settings put global $KEY_WINDOW_ANIM $s; " +
                    "settings put global $KEY_TRANSITION_ANIM $s; " +
                    "settings put global $KEY_ANIMATOR_DURATION $s"
        ) ?: return false
        if (!result.ok) {
            Log.w(TAG, "setAnimationSpeed failed: ${result.stderr.trim()}")
            return false
        }
        // Verify by reading back the window value.
        val check = readSetting(KEY_WINDOW_ANIM)?.toFloatOrNull()
        return check != null && kotlin.math.abs(check - s) < 0.01f
    }

    // ---------------------------------------------------------------------
    // Background process limit
    // ---------------------------------------------------------------------

    /**
     * Values Android accepts for background_process_limit:
     *   -1 = system default (no explicit limit)
     *    0 = standard limit (the pre-Android-O default)
     *    1 = at most 1 background process
     *    2, 3, 4 = at most that many
     *
     * Values above 4 aren't honoured by Android; the framework clamps
     * them back to 4.
     */
    enum class ProcessLimit(val value: Int) {
        SYSTEM_DEFAULT(-1),
        STANDARD(0),
        AT_MOST_1(1),
        AT_MOST_2(2),
        AT_MOST_3(3),
        AT_MOST_4(4),
    }

    fun getProcessLimit(): ProcessLimit? {
        val raw = readSetting(KEY_BG_PROCESS_LIMIT) ?: return null
        val value = raw.toIntOrNull() ?: return null
        return ProcessLimit.values().firstOrNull { it.value == value }
            ?: ProcessLimit.SYSTEM_DEFAULT
    }

    fun setProcessLimit(limit: ProcessLimit): Boolean {
        val result = ShizukuManager.runShellCommand(
            "settings put global $KEY_BG_PROCESS_LIMIT ${limit.value}"
        ) ?: return false
        if (!result.ok) {
            Log.w(TAG, "setProcessLimit failed: ${result.stderr.trim()}")
            return false
        }
        val check = readSetting(KEY_BG_PROCESS_LIMIT)?.toIntOrNull()
        return check == limit.value
    }

    // ---------------------------------------------------------------------
    // Don't Keep Activities
    // ---------------------------------------------------------------------

    fun getDontKeepActivities(): Boolean? {
        val raw = readSetting(KEY_ALWAYS_FINISH) ?: return null
        return raw.trim() == "1"
    }

    fun setDontKeepActivities(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        val result = ShizukuManager.runShellCommand(
            "settings put global $KEY_ALWAYS_FINISH $value"
        ) ?: return false
        if (!result.ok) {
            Log.w(TAG, "setDontKeepActivities failed: ${result.stderr.trim()}")
            return false
        }
        return getDontKeepActivities() == enabled
    }

    // ---------------------------------------------------------------------
    // Kill all background apps
    // ---------------------------------------------------------------------

    /**
     * Runs `am kill-all`, which asks Android to terminate every
     * background process that isn't currently visible and isn't
     * running a foreground service.
     *
     * Unlike force-stop, `am kill-all` cannot affect an app the user
     * is actively using, cannot affect a foreground service (music,
     * nav, calls), and cannot affect a system app. It's the safest
     * possible "clear background processes" primitive.
     *
     * Returns true if the command was accepted by Android. It returns
     * true even if there was nothing to kill — that's a success case,
     * not a failure.
     */
    fun killAllBackgroundApps(): Boolean {
        val result = ShizukuManager.runShellCommand(
            "/system/bin/am kill-all"
        ) ?: return false
        if (!result.ok) {
            Log.w(TAG, "killAllBackgroundApps failed: ${result.stderr.trim()}")
        }
        return result.ok
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------

    /**
     * Restores every setting we manage to Android's default value.
     * Used for the "reset to defaults" button.
     */
    fun resetAll(): Boolean {
        val cmd = buildString {
            append("settings put global $KEY_WINDOW_ANIM 1; ")
            append("settings put global $KEY_TRANSITION_ANIM 1; ")
            append("settings put global $KEY_ANIMATOR_DURATION 1; ")
            append("settings put global $KEY_BG_PROCESS_LIMIT -1; ")
            append("settings put global $KEY_ALWAYS_FINISH 0")
        }
        val result = ShizukuManager.runShellCommand(cmd) ?: return false
        return result.ok
    }

    // ---------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------

    /**
     * Reads a global setting value via `settings get global <key>`.
     * Returns null if the command fails, the value is "null", or the
     * output is empty. Trims trailing newline.
     */
    private fun readSetting(key: String): String? {
        val result = ShizukuManager.runShellCommand(
            "/system/bin/settings get global $key"
        ) ?: return null
        if (!result.ok) return null
        val trimmed = result.stdout.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        return trimmed
    }
}