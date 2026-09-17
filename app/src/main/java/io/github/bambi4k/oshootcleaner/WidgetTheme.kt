package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges the app's theme/language state to the widgets.
 *
 * Single worker, coalescing, rate-limited:
 *   - Any number of callers can call requestSync() as fast as they want.
 *   - Only one worker coroutine runs at a time.
 *   - Between pushes, the worker waits at least MIN_SYNC_INTERVAL_MS.
 *   - If new requests arrive during the wait, the worker pushes once
 *     with the latest state, not once per request.
 *   - doSync reads the current theme at execution time, so the last
 *     user choice always wins regardless of ordering.
 */
object WidgetTheme {

    private const val TAG = "WidgetTheme"
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_LAST_THEME = "widget_last_synced_theme_id"
    private const val KEY_LAST_LANGUAGE = "widget_last_synced_language"
    private const val MIN_SYNC_INTERVAL_MS = 500L

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workerRunning = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)
    private val lastSyncAt = AtomicLong(0L)

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Requests a widget refresh. Returns immediately. Extra calls are
     * coalesced — the user can spam theme changes as fast as they want
     * and the launcher will see at most one update every 500ms.
     */
    fun requestSync(context: Context) {
        val appContext = context.applicationContext
        dirty.set(true)

        if (workerRunning.compareAndSet(false, true)) {
            workerScope.launch {
                try {
                    while (dirty.get()) {
                        dirty.set(false)

                        val sinceLast = System.currentTimeMillis() - lastSyncAt.get()
                        if (sinceLast < MIN_SYNC_INTERVAL_MS) {
                            delay(MIN_SYNC_INTERVAL_MS - sinceLast)
                        }

                        if (dirty.get()) continue

                        lastSyncAt.set(System.currentTimeMillis())
                        try {
                            doSync(appContext)
                        } catch (e: Throwable) {
                            Log.e(TAG, "sync failed", e)
                        }
                    }
                } finally {
                    workerRunning.set(false)
                    if (dirty.get() && workerRunning.compareAndSet(false, true)) {
                        requestSync(appContext)
                    }
                }
            }
        }
    }

    /**
     * Called from MainActivity.onCreate after a language change has
     * caused a recreate(). Pushes a refresh only if the language
     * actually differs from what was last synced. Runs synchronously
     * because the caller awaits the result.
     */
    suspend fun syncLanguageIfChanged(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = LanguageStore.get(appContext)
        val last = prefs.getString(KEY_LAST_LANGUAGE, null)

        if (current == last) {
            Log.d(TAG, "syncLanguageIfChanged: no change ($current)")
            return
        }

        Log.d(TAG, "language changed ($last -> $current), refreshing widgets")
        prefs.edit().putString(KEY_LAST_LANGUAGE, current).apply()

        // Bypass the rate limiter — this is a one-shot event, not a
        // rapid-fire caller, and the caller wants it done now.
        try {
            doSync(appContext)
        } catch (e: Throwable) {
            Log.e(TAG, "language sync failed", e)
        }
    }

    // ---------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------

    private suspend fun doSync(context: Context) {
        val themeId = ThemeStore.getSelectedTheme(context).id
        val lang = LanguageStore.get(context)

        Log.d(TAG, "doSync: theme=$themeId language=$lang")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_THEME, themeId)
            .apply()

        WidgetSync.refreshAll(context)
    }
}