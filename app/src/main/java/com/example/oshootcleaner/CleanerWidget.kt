package com.example.oshootcleaner

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

// Internal widget-state keys. Internal (not private) so
// CleanerBoostWorker can write to the same state.
internal object CleanerKeys {
    val runStartedAtMs = longPreferencesKey("cleaner_run_started_at")
    val status = stringPreferencesKey("cleaner_status")
    val freedBytes = longPreferencesKey("cleaner_freed_bytes")
    val evictedCount = longPreferencesKey("cleaner_evicted_count")
    val durationMs = longPreferencesKey("cleaner_duration_ms")
    val usedShizuku = stringPreferencesKey("cleaner_used_shizuku")
    val progressCurrent = longPreferencesKey("cleaner_progress_current")
    val progressTotal = longPreferencesKey("cleaner_progress_total")
}

internal enum class CleanerState { IDLE, RUNNING, DONE }

class CleanerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(60.dp, 60.dp),     // 1×1 tiny
            DpSize(110.dp, 110.dp),   // 2×2
            DpSize(160.dp, 110.dp),   // 2×2 wide
            DpSize(250.dp, 120.dp),   // 4×2
            DpSize(250.dp, 250.dp),   // 4×4
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 1. Read state from widget preferences. Every value has a
            //    safe default so a fresh widget with no writes yet
            //    renders correctly.
            val prefs = currentState<Preferences>()
            val stateName = prefs[CleanerKeys.status] ?: CleanerState.IDLE.name
            val freed = prefs[CleanerKeys.freedBytes] ?: 0L
            val evicted = prefs[CleanerKeys.evictedCount] ?: 0L
            val durationMs = prefs[CleanerKeys.durationMs] ?: 0L
            val usedShizuku = prefs[CleanerKeys.usedShizuku] == "true"
            val progressCurrent = prefs[CleanerKeys.progressCurrent] ?: 0L
            val progressTotal = prefs[CleanerKeys.progressTotal] ?: 0L
            val runStartedAt = prefs[CleanerKeys.runStartedAtMs] ?: 0L

            // 2. Parse the state name into the enum.
            val rawState = runCatching { CleanerState.valueOf(stateName) }
                .getOrDefault(CleanerState.IDLE)

            // 3. Stale-run recovery: if a worker died without resetting
            //    the state, the widget would be stuck on RUNNING forever.
            //    Treat any RUNNING state older than 90 seconds as IDLE.
            val isStale = rawState == CleanerState.RUNNING &&
                    runStartedAt > 0 &&
                    System.currentTimeMillis() - runStartedAt > 90_000L
            val state = if (isStale) CleanerState.IDLE else rawState

            // 4. Resolve theme.
            val theme = remember { ThemeStore.getSelectedTheme(context) }

            WidgetContent(
                context = context,
                theme = theme,
                state = state,
                freedBytes = freed,
                evicted = evicted,
                durationMs = durationMs,
                usedShizuku = usedShizuku,
                progressCurrent = progressCurrent,
                progressTotal = progressTotal
            )
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    theme: ThemeSpec,
    state: CleanerState,
    freedBytes: Long,
    evicted: Long,
    durationMs: Long,
    usedShizuku: Boolean,
    progressCurrent: Long,
    progressTotal: Long
) {
    val size = LocalSize.current
    val heightDp = size.height.value
    val widthDp = size.width.value
    val isTiny = heightDp <= 70f
    val isWide = widthDp >= 200f && heightDp <= 140f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(theme.bgBase)
            .clickable(actionStartActivity<MainActivity>())
            .padding(6.dp)
    ) {
        when {
            isTiny -> TinyLayout(theme, state, progressCurrent, progressTotal)
            isWide -> WideLayout(
                context, theme, state,
                freedBytes, evicted, durationMs, usedShizuku,
                progressCurrent, progressTotal
            )
            else -> CompactLayout(
                context, theme, state,
                freedBytes, evicted, durationMs, usedShizuku,
                progressCurrent, progressTotal
            )
        }
    }
}

// --------------------------------------------------------------------- TINY

@Composable
private fun TinyLayout(
    theme: ThemeSpec,
    state: CleanerState,
    progressCurrent: Long,
    progressTotal: Long
) {
    val buttonColor = when (state) {
        CleanerState.IDLE -> theme.buttonPrimaryBg
        CleanerState.RUNNING -> theme.buttonHover
        CleanerState.DONE -> theme.consoleSuccess
    }
    val logoColor = theme.buttonPrimaryText

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(buttonColor)
            .clickable(actionRunCallback<RunCleanupWidgetAction>()),
        contentAlignment = Alignment.Center
    ) {
        if (state == CleanerState.RUNNING) {
            val label = if (progressTotal > 0) {
                "$progressCurrent/$progressTotal"
            } else {
                "…"
            }
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(logoColor),
                    fontSize = if (progressTotal > 0) 11.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_logo_white),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(logoColor)),
                modifier = GlanceModifier.size(32.dp)
            )
        }
    }
}

// --------------------------------------------------------------------- COMPACT

@Composable
private fun CompactLayout(
    context: Context,
    theme: ThemeSpec,
    state: CleanerState,
    freedBytes: Long,
    evicted: Long,
    durationMs: Long,
    usedShizuku: Boolean,
    progressCurrent: Long,
    progressTotal: Long
) {
    val buttonColor = when (state) {
        CleanerState.IDLE -> theme.buttonPrimaryBg
        CleanerState.RUNNING -> theme.buttonHover
        CleanerState.DONE -> theme.consoleSuccess
    }
    val logoColor = theme.buttonPrimaryText
    val statusText = statusLine(context, state, freedBytes, evicted, durationMs, usedShizuku)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(
                    if (statusText == null) GlanceModifier.fillMaxHeight()
                    else GlanceModifier.height(78.dp)
                )
                .cornerRadius(16.dp)
                .background(buttonColor)
                .clickable(actionRunCallback<RunCleanupWidgetAction>()),
            contentAlignment = Alignment.Center
        ) {
            if (state == CleanerState.RUNNING) {
                val label = if (progressTotal > 0) {
                    "$progressCurrent/$progressTotal"
                } else {
                    "…"
                }
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(logoColor),
                        fontSize = if (progressTotal > 0) 16.sp else 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_logo_white),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(logoColor)),
                    modifier = GlanceModifier.size(40.dp)
                )
            }
        }

        if (statusText != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = statusText,
                style = TextStyle(
                    color = ColorProvider(theme.fontsSecondary),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}

// --------------------------------------------------------------------- WIDE

@Composable
private fun WideLayout(
    context: Context,
    theme: ThemeSpec,
    state: CleanerState,
    freedBytes: Long,
    evicted: Long,
    durationMs: Long,
    usedShizuku: Boolean,
    progressCurrent: Long,
    progressTotal: Long
) {
    val buttonColor = when (state) {
        CleanerState.IDLE -> theme.buttonPrimaryBg
        CleanerState.RUNNING -> theme.buttonHover
        CleanerState.DONE -> theme.consoleSuccess
    }
    val logoColor = theme.buttonPrimaryText

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .height(72.dp)
                .width(72.dp)
                .cornerRadius(16.dp)
                .background(buttonColor)
                .clickable(actionRunCallback<RunCleanupWidgetAction>()),
            contentAlignment = Alignment.Center
        ) {
            if (state == CleanerState.RUNNING) {
                val label = if (progressTotal > 0) {
                    "$progressCurrent/$progressTotal"
                } else {
                    "…"
                }
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(logoColor),
                        fontSize = if (progressTotal > 0) 14.sp else 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_logo_white),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(logoColor)),
                    modifier = GlanceModifier.size(36.dp)
                )
            }
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = context.getString(R.string.widget_app_name),
                style = TextStyle(
                    color = ColorProvider(theme.fontsHeadings),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            val statusText = statusLine(context, state, freedBytes, evicted, durationMs, usedShizuku)
            Text(
                text = statusText ?: context.getString(R.string.widget_status_idle),
                style = TextStyle(
                    color = ColorProvider(theme.fontsSecondary),
                    fontSize = 10.sp
                ),
                maxLines = 3
            )
        }
    }
}

// --------------------------------------------------------------------- helpers

private fun statusLine(
    context: Context,
    state: CleanerState,
    freedBytes: Long,
    evicted: Long,
    durationMs: Long,
    usedShizuku: Boolean
): String? = when (state) {
    CleanerState.IDLE -> null
    CleanerState.RUNNING -> null
    CleanerState.DONE -> {
        val durationUs = durationMs * 1000L
        val durationText = when {
            durationUs < 1000 -> "$durationUs µs"
            durationUs < 1_000_000 -> "%.1f ms".format(durationUs / 1000.0)
            else -> "%.2f s".format(durationUs / 1_000_000.0)
        }
        val appWord = if (usedShizuku) "restarted" else "re-hinted"
        if (freedBytes > 50L * 1024) {
            "${formatBytesShort(freedBytes)} · $evicted $appWord · $durationText"
        } else {
            "$evicted $appWord · $durationText"
        }
    }
}

private fun formatBytesShort(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

/**
 * Widget action: dispatches the actual work to a WorkManager job and
 * returns immediately. The widget flips to RUNNING within ~100ms and
 * the worker updates progress as it goes.
 *
 * This indirection is required because Glance action callbacks have a
 * hard ~10-second budget enforced by the widget host. The Shizuku boost
 * takes ~12 seconds, which would kill the process if run inline.
 */
class RunCleanupWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            // Immediately flip the widget to RUNNING so the user sees
            // instant feedback. Record the start time so the stale-run
            // recovery in provideGlance can identify hung runs.
            updateAppWidgetState(context, glanceId) {
                it[CleanerKeys.status] = CleanerState.RUNNING.name
                it[CleanerKeys.usedShizuku] = "false"
                it[CleanerKeys.progressCurrent] = 0L
                it[CleanerKeys.progressTotal] = 0L
                it[CleanerKeys.runStartedAtMs] = System.currentTimeMillis()
            }
            CleanerWidget().update(context, glanceId)
        } catch (e: Throwable) {
            android.util.Log.e("CleanerWidgetAction", "immediate update threw", e)
        }

        try {
            val work = androidx.work.OneTimeWorkRequestBuilder<CleanerBoostWorker>()
                .setExpedited(
                    androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                )
                .addTag(CleanerBoostWorker.TAG)
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueue(work)
        } catch (e: Throwable) {
            android.util.Log.e("CleanerWidgetAction", "enqueue threw", e)
            runCatching {
                updateAppWidgetState(context, glanceId) {
                    it[CleanerKeys.status] = CleanerState.IDLE.name
                }
                CleanerWidget().update(context, glanceId)
            }
        }
    }
}