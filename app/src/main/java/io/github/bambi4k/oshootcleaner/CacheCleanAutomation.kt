package io.github.bambi4k.oshootcleaner

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-side controller for the accessibility-based cache clean.
 *
 * Responsibilities:
 *   1. Detect whether the accessibility service is enabled.
 *   2. Send START / STOP broadcasts to the service.
 *   3. Listen to PROGRESS / DONE broadcasts and expose state to Compose.
 */
object CacheCleanAutomation {

    /** State of an automation session, observed by the UI. */
    data class State(
        val running: Boolean = false,
        val progress: String = "",
        val cleared: Int = 0,
        val skipped: Int = 0,
        val finished: Boolean = false,
        val massFailure: Boolean = false
    )

    // Compose-observable state. Updated by the broadcast receiver.
    var state by mutableStateOf(State())
        private set

    private var receiver: BroadcastReceiver? = null

    /**
     * True if the accessibility service is enabled in the system settings.
     */
    fun isServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context, CacheCleanAccessibilityService::class.java)
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    /** Opens the system's accessibility settings so the user can enable us. */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Starts an automated clean of the given packages.
     */
    fun start(context: Context, packages: List<String>) {
        if (packages.isEmpty()) return

        state = State(running = true, progress = "0/${packages.size}")

        // Register a receiver for progress + done broadcasts.
        unregisterReceiver(context)
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    CacheCleanAccessibilityService.ACTION_PROGRESS -> {
                        state = state.copy(
                            running = true,
                            progress = intent.getStringExtra("progress") ?: "",
                            cleared = intent.getIntExtra("cleared", 0),
                            skipped = intent.getIntExtra("skipped", 0)
                        )
                    }
                    CacheCleanAccessibilityService.ACTION_DONE -> {
                        state = State(
                            running = false,
                            progress = "",
                            cleared = intent.getIntExtra("cleared", 0),
                            skipped = intent.getIntExtra("skipped", 0),
                            finished = true,
                            massFailure = intent.getBooleanExtra("mass_failure", false)
                        )
                    }
                }
            }
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(CacheCleanAccessibilityService.ACTION_PROGRESS)
            addAction(CacheCleanAccessibilityService.ACTION_DONE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, filter)
        }

        // Send the START intent to the service.
        val intent = Intent(context, CacheCleanAccessibilityService::class.java).apply {
            action = CacheCleanAccessibilityService.ACTION_START
            putStringArrayListExtra(
                CacheCleanAccessibilityService.EXTRA_PACKAGES,
                ArrayList(packages)
            )
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Non-fatal — the service will be started by the OS when the
            // user returns to our app.
        }
    }

    /** Stops an in-flight session. */
    fun stop(context: Context) {
        val intent = Intent(context, CacheCleanAccessibilityService::class.java).apply {
            action = CacheCleanAccessibilityService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Non-fatal.
        }
        state = state.copy(running = false, finished = false)
    }

    /** Unregisters the receiver. Call from onDispose. */
    fun unregisterReceiver(context: Context) {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
                // Already unregistered — safe to ignore.
            }
        }
        receiver = null
    }

    /** Resets the finished flag so the summary dialog disappears. */
    fun resetFinished() {
        state = State()
    }
}