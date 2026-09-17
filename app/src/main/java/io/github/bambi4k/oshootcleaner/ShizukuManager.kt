package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Central manager for everything Shizuku-related.
 *
 * Shizuku is a separate app that, once started via ADB or wireless
 * debugging, lets OTHER apps use its elevated privileges. Our app does
 * NOT include Shizuku itself — it only talks to it if the user has
 * installed and started it.
 *
 * This object provides a simple, Compose-friendly API:
 *   - isInstalled / isRunning / hasPermission / isReady
 *   - requestPermission / openShizukuApp
 *   - add*Listener / remove*Listener
 *
 * Plus the actual privileged operations used by the app:
 *   - forceStopApp(packageName)
 *   - boostBackgroundApps(context, onProgress)
 *
 * NOTE ON SCOPE: this object deliberately does NOT expose any
 * cache-clearing function. Clearing another app's cache from the shell
 * user is not possible without root: direct filesystem deletion is
 * blocked by SELinux, and `pm clear --cache-only` takes the
 * PackageManagerService lock and deadlocks system_server when called in
 * a loop. Those paths were tried and removed. For cache cleaning, the
 * app still uses the Accessibility service in StorageTab.
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val PERMISSION_REQUEST_CODE = 10001
    private const val COMMAND_TIMEOUT_MS = 3_000L

    /**
     * Maximum number of apps the Shizuku Boost will force-stop in one
     * run. Hard ceiling — the framework can't handle much more without
     * lock contention, and the user's patience is finite anyway.
     */
    private const val BOOST_MAX_TARGETS = 30

    /**
     * Pause between each app in the boost loop. `am force-stop` takes
     * the ActivityManager lock in system_server; running dozens of
     * these back-to-back without breathing room can pile up requests
     * and destabilize the framework. 400ms is the safe interval we
     * measured on a Pixel 8.
     */
    private const val BOOST_INTER_APP_DELAY_MS = 400L

    // ---------------------------------------------------------------------
    // Listeners
    // ---------------------------------------------------------------------

    private val permissionResultListeners = mutableListOf<(Boolean) -> Unit>()
    private val binderReceivedListeners = mutableListOf<() -> Unit>()
    private val binderDeadListeners = mutableListOf<() -> Unit>()

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        try {
            Shizuku.addBinderReceivedListenerSticky {
                Log.d(TAG, "Shizuku binder received")
                binderReceivedListeners.forEach { it() }
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder died")
                binderDeadListeners.forEach { it() }
            }
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    Log.d(TAG, "Permission result: granted=$granted")
                    permissionResultListeners.forEach { it(granted) }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to init Shizuku listeners", e)
        }
    }

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun isRunning(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.pingBinder()
    } catch (e: Throwable) {
        Log.d(TAG, "isRunning() threw: ${e.message}")
        false
    }

    fun hasPermission(): Boolean {
        if (!isRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.d(TAG, "hasPermission() threw: ${e.message}")
            false
        }
    }

    fun shouldShowRequestPermission(): Boolean {
        if (!isRunning()) return false
        return try {
            !Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Throwable) {
            false
        }
    }

    fun isReady(): Boolean = isRunning() && hasPermission()

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    fun requestPermission() {
        if (!isRunning()) {
            Log.w(TAG, "Cannot request permission — Shizuku not running")
            return
        }
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    fun openShizukuApp(context: Context) {
        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Throwable) { }

        try {
            val storeIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=moe.shizuku.privileged.api")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(storeIntent)
        } catch (_: Throwable) {
            val webIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://shizuku.rikka.app/download/")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun addPermissionListener(listener: (Boolean) -> Unit) {
        permissionResultListeners.add(listener)
    }

    fun removePermissionListener(listener: (Boolean) -> Unit) {
        permissionResultListeners.remove(listener)
    }

    fun addBinderReceivedListener(listener: () -> Unit) {
        binderReceivedListeners.add(listener)
    }

    fun removeBinderReceivedListener(listener: () -> Unit) {
        binderReceivedListeners.remove(listener)
    }

    fun addBinderDeadListener(listener: () -> Unit) {
        binderDeadListeners.add(listener)
    }

    fun removeBinderDeadListener(listener: () -> Unit) {
        binderDeadListeners.remove(listener)
    }

    // ---------------------------------------------------------------------
    // Privileged operations
    // ---------------------------------------------------------------------

    /**
     * Result of a single shell command. stdout and stderr are captured
     * separately, exitCode is 0 on success.
     */
    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val ok: Boolean get() = exitCode == 0
    }

    /**
     * Runs a shell command through Shizuku with the default timeout.
     * Blocks the calling thread until the command exits. Callers MUST
     * run this on a background dispatcher (Dispatchers.IO) — it does
     * file I/O and waits on a remote process.
     *
     * Returns null if Shizuku isn't ready or the process couldn't be
     * started. Never throws.
     *
     * Uses reflection to invoke Shizuku.newProcess() because Kotlin's
     * synthetic accessor for that Java static method reports it as
     * private on some Shizuku/AGP combinations even though the method
     * itself is public.
     */
    fun runShellCommand(command: String): ShellResult? {
        if (!isReady()) {
            Log.w(TAG, "runShellCommand called without Shizuku ready")
            return null
        }

        var process: java.lang.Process? = null
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as java.lang.Process

            // ShizukuRemoteProcess.exitValue() throws IllegalArgumentException
            // when the process hasn't exited (instead of the standard
            // IllegalThreadStateException). This breaks Process.waitFor()
            // and Process.waitFor(timeout, unit), both of which expect the
            // standard exception. So we poll exitValue() ourselves and
            // treat both exception types as "still running".
            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
            var exitCode: Int? = null

            while (System.currentTimeMillis() < deadline) {
                try {
                    exitCode = process.exitValue()
                    break
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(10)
                } catch (_: IllegalArgumentException) {
                    Thread.sleep(10)
                }
            }

            if (exitCode == null) {
                Log.w(TAG, "Shell command timed out after ${COMMAND_TIMEOUT_MS}ms: $command")
                try { process.destroyForcibly() } catch (_: Throwable) { }
                return ShellResult(
                    exitCode = -1,
                    stdout = readAll(process.inputStream),
                    stderr = "timed out"
                )
            }

            val stdout = readAll(process.inputStream)
            val stderr = readAll(process.errorStream)
            ShellResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        } catch (e: Throwable) {
            Log.e(TAG, "runShellCommand failed: $command", e)
            null
        } finally {
            try { process?.destroy() } catch (_: Throwable) { }
        }
    }

    private fun readAll(stream: java.io.InputStream): String = try {
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line).append('\n')
            line = reader.readLine()
        }
        sb.toString()
    } catch (_: Throwable) {
        ""
    }

    /**
     * Force-stops a single app via `am force-stop`. This is the real
     * Android force-stop — the same one Settings' "Force stop" button
     * uses. Background services are killed and will be restarted by the
     * system when the app is next launched.
     */
    fun forceStopApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val result = runShellCommand("/system/bin/am force-stop $packageName") ?: return false
        return result.ok
    }

    /**
     * The Shizuku-powered One-Tap Boost.
     *
     * Iterates over a bounded subset of launchable, non-system,
     * non-sensitive apps and force-stops each one. This is the same
     * force-stop Android's Settings app performs when the user taps
     * "Force stop" — it stops every process the app has running and
     * forces a fresh start the next time it's opened.
     *
     * Does NOT clear caches. That operation cannot be performed safely
     * from the shell user without root; see the note at the top of this
     * file.
     *
     * Two hard safety limits:
     *   - At most [BOOST_MAX_TARGETS] apps are touched per run.
     *   - [BOOST_INTER_APP_DELAY_MS] delay between apps, to keep
     *     system_server from drowning in ActivityManager requests.
     */
    data class BoostResult(
        val considered: Int,
        val cacheCleared: Int,
        val forceStopped: Int,
        val skipped: Int,
        val durationMs: Long,
    )

    suspend fun boostBackgroundApps(
        context: Context,
        onProgress: suspend (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
    ): BoostResult {
        val startNs = System.nanoTime()

        if (!isReady()) {
            return BoostResult(0, 0, 0, 0, 0L)
        }

        val self = context.packageName
        val pm = context.packageManager

        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val targets = pm.queryIntentActivities(launcherIntent, 0)
            .filter { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == self) return@filter false
                if (SensitivePackages.isSensitive(pkg)) return@filter false

                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystem
            }
            .map { it.activityInfo.packageName }
            .distinct()
            .sorted()
            .take(BOOST_MAX_TARGETS)

        var forceStopped = 0
        var skipped = 0

        var done = 0
        for (pkg in targets) {
            onProgress(done, targets.size, pkg)

            // Space out force-stops. Without this, dozens of
            // ActivityManager requests pile up on system_server and can
            // deadlock the framework.
            kotlinx.coroutines.delay(BOOST_INTER_APP_DELAY_MS)

            val stopped = forceStopApp(pkg)
            if (stopped) {
                forceStopped++
            } else {
                skipped++
            }

            done++
        }

        onProgress(done, targets.size, "")

        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        return BoostResult(
            considered = targets.size,
            cacheCleared = 0,
            forceStopped = forceStopped,
            skipped = skipped,
            durationMs = durationMs,
        )
    }
}