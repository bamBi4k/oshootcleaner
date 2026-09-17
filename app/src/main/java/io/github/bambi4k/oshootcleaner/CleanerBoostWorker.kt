package io.github.bambi4k.oshootcleaner

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * Runs the heavy clean operation off the widget-action thread.
 *
 * Why this exists: Glance action callbacks run under a hard ~10-second
 * budget enforced by the widget host. The Shizuku boost path takes
 * roughly 12 seconds for 30 apps, so running it inline caused the OS to
 * kill our process mid-operation.
 *
 * By dispatching a WorkManager job instead, the widget action returns
 * immediately and the actual work runs under WorkManager's rules —
 * which give us minutes, not seconds. The worker updates the widget's
 * state directly as it progresses so the user sees live progress.
 *
 * The worker is enqueued with expedited policy where the OS allows it,
 * and falls back to non-expedited otherwise. Either way, the work
 * completes reliably.
 */
class CleanerBoostWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CleanerBoostWorker"
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            // Step 1 — our own quick clean. Always runs. Fast (~100ms).
            val clean = CleanupManager.runQuickClean(ctx)

            // Step 2 — background app handling. Branch on Shizuku.
            val shizukuReady = ShizukuManager.isReady()
            val appCount: Int = if (shizukuReady) {
                ShizukuManager.boostBackgroundApps(
                    context = ctx,
                    onProgress = { done, total, _ ->
                        pushRunningProgress(ctx, done, total)
                    }
                ).forceStopped
            } else {
                CleanupManager.restartBackgroundApps(ctx) { line ->
                    // Cheap progress: we don't know the total from this
                    // function, so we just show the running count in the
                    // "current" slot.
                    pushRunningProgress(ctx, 0, 0)
                }.requested
            }

            // Step 3 — publish DONE.
            pushDone(
                ctx = ctx,
                freedBytes = clean.freedBytes,
                appCount = appCount,
                durationMs = clean.durationMs,
                shizukuReady = shizukuReady
            )

            // Step 4 — hold DONE for a few seconds, then fade back.
            delay(4_000)
            pushIdle(ctx)

            return Result.success()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "doWork threw", e)
            // Best-effort reset so the user isn't stuck on a
            // half-finished RUNNING state.
            runCatching { pushIdle(ctx) }
            return Result.failure()
        }
    }

    // ---------------------------------------------------------------------
    // State pushers
    // ---------------------------------------------------------------------

    private suspend fun pushRunningProgress(
        ctx: Context,
        done: Int,
        total: Int
    ) {
        forEachWidget(ctx) { id ->
            updateAppWidgetState(ctx, id) {
                it[CleanerKeys.status] = CleanerState.RUNNING.name
                it[CleanerKeys.progressCurrent] = done.toLong()
                it[CleanerKeys.progressTotal] = total.toLong()
            }
            CleanerWidget().update(ctx, id)
        }
    }

    private suspend fun pushDone(
        ctx: Context,
        freedBytes: Long,
        appCount: Int,
        durationMs: Long,
        shizukuReady: Boolean
    ) {
        forEachWidget(ctx) { id ->
            updateAppWidgetState(ctx, id) {
                it[CleanerKeys.status] = CleanerState.DONE.name
                it[CleanerKeys.freedBytes] = freedBytes
                it[CleanerKeys.evictedCount] = appCount.toLong()
                it[CleanerKeys.durationMs] = durationMs
                it[CleanerKeys.usedShizuku] = shizukuReady.toString()
                it[CleanerKeys.progressCurrent] = 0L
                it[CleanerKeys.progressTotal] = 0L
            }
            CleanerWidget().update(ctx, id)
        }
    }

    private suspend fun pushIdle(ctx: Context) {
        forEachWidget(ctx) { id ->
            updateAppWidgetState(ctx, id) {
                it[CleanerKeys.status] = CleanerState.IDLE.name
                it[CleanerKeys.progressCurrent] = 0L
                it[CleanerKeys.progressTotal] = 0L
            }
            CleanerWidget().update(ctx, id)
        }
    }

    /**
     * Applies [block] to every live instance of the Cleaner widget.
     * Uses GlanceAppWidgetManager to enumerate ids so we don't depend on
     * a specific glanceId being passed in — the worker runs after the
     * action that enqueued it has already returned.
     */
    private suspend fun forEachWidget(
        ctx: Context,
        block: suspend (androidx.glance.GlanceId) -> Unit
    ) {
        try {
            val manager = GlanceAppWidgetManager(ctx)
            val ids = manager.getGlanceIds(CleanerWidget::class.java)
            for (id in ids) {
                runCatching { block(id) }
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "forEachWidget failed", e)
        }
    }
}