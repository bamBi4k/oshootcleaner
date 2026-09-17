package com.example.oshootcleaner

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Refreshes widgets after an in-app action that changed system state
 * (cleanup, theme change, language change, etc.).
 *
 * All calls are wrapped in runCatching so a broken widget instance
 * never crashes the app. Every refresh is logged per-widget with
 * elapsed time so we can diagnose syncing issues from logcat.
 */
object WidgetSync {

    private const val TAG = "WidgetSync"

    suspend fun refreshAll(context: Context) {
        val startNs = System.nanoTime()
        log("refreshAll START (pid=${android.os.Process.myPid()})")

        coroutineScope {
            val dashboard = async {
                timeAndLog("DashboardWidget") {
                    runCatching { DashboardWidget().updateAll(context) }
                }
            }
            val cleaner = async {
                timeAndLog("CleanerWidget") {
                    runCatching { CleanerWidget().updateAll(context) }
                }
            }
            val dataUsage = async {
                timeAndLog("DataUsageWidget") {
                    runCatching { DataUsageWidget().updateAll(context) }
                }
            }

            dashboard.await()
            cleaner.await()
            dataUsage.await()
        }

        val ms = (System.nanoTime() - startNs) / 1_000_000
        log("refreshAll END (${ms}ms)")
    }

    suspend fun refreshCleaner(context: Context) {
        timeAndLog("CleanerWidget(single)") {
            runCatching { CleanerWidget().updateAll(context) }
        }
    }

    suspend fun refreshDashboard(context: Context) {
        timeAndLog("DashboardWidget(single)") {
            runCatching { DashboardWidget().updateAll(context) }
        }
    }

    suspend fun refreshDataUsage(context: Context) {
        timeAndLog("DataUsageWidget(single)") {
            runCatching { DataUsageWidget().updateAll(context) }
        }
    }

    // ---------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------

    private suspend inline fun <T> timeAndLog(
        label: String,
        block: () -> Result<T>
    ): Result<T> {
        val startNs = System.nanoTime()
        log("$label -> start")
        val result = block()
        val ms = (System.nanoTime() - startNs) / 1_000_000
        result.fold(
            onSuccess = { log("$label -> OK (${ms}ms)") },
            onFailure = { e ->
                log("$label -> FAIL (${ms}ms): ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "$label stack", e)
            }
        )
        return result
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}