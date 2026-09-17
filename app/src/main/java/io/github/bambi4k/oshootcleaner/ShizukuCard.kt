package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * The four possible states of Shizuku from this app's point of view.
 * Everything the card shows is derived from one of these.
 */
private enum class ShizukuState {
    /** Shizuku app is not installed on the device. */
    NOT_INSTALLED,
    /** Installed, but the Shizuku service isn't running (user hasn't started it). */
    NOT_RUNNING,
    /** Service is running, but our app hasn't been granted permission. */
    PERMISSION_REQUIRED,
    /** Service running AND our app has permission. All features unlocked. */
    READY
}

/**
 * A self-contained Shizuku connection card. Drop it anywhere in a
 * scrolling column. It queries [ShizukuManager] for live state, listens
 * for binder and permission changes, and updates in place — no activity
 * recreation needed.
 *
 * The card renders four different layouts depending on state, all sharing
 * the same overall shape:
 *
 *   ●  Shizuku (Enhanced Mode)             ⓘ
 *      <status line>
 *
 *      <optional explanation>
 *
 *      [ primary action button ]
 *
 * @param theme the current ThemeSpec
 * @param onReady an optional callback fired the moment the card reaches
 *        ShizukuState.READY. Useful for screens that want to refresh
 *        their enabled features when the user connects.
 */
@Composable
fun ShizukuCard(
    theme: ThemeSpec,
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current

    // Live state. Recomputed from ShizukuManager on every relevant event.
    var state by remember { mutableStateOf(currentState(context)) }
    var infoOpen by remember { mutableStateOf(false) }

    // Recompute the state from scratch. Called on bind/dead/permission
    // events and on first composition.
    fun refresh() {
        state = currentState(context)
    }

    // Register Shizuku listeners for the duration the card is composed.
    // On dispose, unregister so we don't leak.
    DisposableEffect(Unit) {
        val onBinderReceived: () -> Unit = { refresh() }
        val onBinderDead: () -> Unit = { refresh() }
        val onPermission: (Boolean) -> Unit = { refresh() }

        ShizukuManager.addBinderReceivedListener(onBinderReceived)
        ShizukuManager.addBinderDeadListener(onBinderDead)
        ShizukuManager.addPermissionListener(onPermission)

        // Also refresh immediately in case Shizuku started between screen
        // composition and listener registration.
        refresh()

        onDispose {
            ShizukuManager.removeBinderReceivedListener(onBinderReceived)
            ShizukuManager.removeBinderDeadListener(onBinderDead)
            ShizukuManager.removePermissionListener(onPermission)
        }
    }

    // Poll lightly every 2 seconds while the card is in NOT_RUNNING or
    // PERMISSION_REQUIRED. The Shizuku API doesn't emit an event when the
    // user starts Shizuku from its own app — we'd otherwise wait for them
    // to come back to our app to update the UI.
    LaunchedEffect(state) {
        if (state == ShizukuState.READY || state == ShizukuState.NOT_INSTALLED) return@LaunchedEffect
        while (true) {
            delay(2000)
            val newState = currentState(context)
            if (newState != state) {
                state = newState
            }
        }
    }

    // Fire the ready callback whenever we transition into READY. The
    // effect's key is the state, so it fires exactly on transitions.
    LaunchedEffect(state) {
        if (state == ShizukuState.READY) {
            onReady()
        }
    }

    // ---- The card UI itself ----
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.bgSurface)
            .border(1.dp, theme.bevelBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        // Header row: dot + title + info button
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state = state, theme = theme)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.shizuku_title),
                color = theme.fontsHeadings,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Info button — a small circle with an "i"
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(theme.bgCrust)
                    .clickable { infoOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "i",
                    color = theme.fontsSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Status line
        Text(
            text = stringResource(stateLabelRes(state)),
            color = theme.fontsPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        // Explanation block — only shown in states where there's an action
        // the user needs to take. In READY state we just show the benefits.
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(stateBodyRes(state)),
            color = theme.fontsSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        // Primary action button — only shown when there's something to do
        val actionLabel = stateActionRes(state)
        if (actionLabel != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.buttonPrimaryBg)
                    .clickable { performAction(context, state) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(actionLabel),
                    color = theme.buttonPrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ---- Info dialog ----
    if (infoOpen) {
        ShizukuInfoDialog(theme = theme, onDismiss = { infoOpen = false })
    }
}

// ---------------------------------------------------------------------
// State helpers
// ---------------------------------------------------------------------

private fun currentState(context: android.content.Context): ShizukuState {
    if (!ShizukuManager.isInstalled(context)) return ShizukuState.NOT_INSTALLED
    if (!ShizukuManager.isRunning()) return ShizukuState.NOT_RUNNING
    if (!ShizukuManager.hasPermission()) return ShizukuState.PERMISSION_REQUIRED
    return ShizukuState.READY
}

private fun stateLabelRes(state: ShizukuState): Int = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.shizuku_state_not_installed
    ShizukuState.NOT_RUNNING -> R.string.shizuku_state_not_running
    ShizukuState.PERMISSION_REQUIRED -> R.string.shizuku_state_permission_required
    ShizukuState.READY -> R.string.shizuku_state_ready
}

private fun stateBodyRes(state: ShizukuState): Int = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.shizuku_body_not_installed
    ShizukuState.NOT_RUNNING -> R.string.shizuku_body_not_running
    ShizukuState.PERMISSION_REQUIRED -> R.string.shizuku_body_permission_required
    ShizukuState.READY -> R.string.shizuku_body_ready
}

private fun stateActionRes(state: ShizukuState): Int? = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.shizuku_action_install
    ShizukuState.NOT_RUNNING -> R.string.shizuku_action_open
    ShizukuState.PERMISSION_REQUIRED -> R.string.shizuku_action_grant
    ShizukuState.READY -> null
}

private fun performAction(context: android.content.Context, state: ShizukuState) {
    when (state) {
        ShizukuState.NOT_INSTALLED -> ShizukuManager.openShizukuApp(context)
        ShizukuState.NOT_RUNNING -> ShizukuManager.openShizukuApp(context)
        ShizukuState.PERMISSION_REQUIRED -> ShizukuManager.requestPermission()
        ShizukuState.READY -> { /* nothing to do */ }
    }
}

// ---------------------------------------------------------------------
// Visual helpers
// ---------------------------------------------------------------------

@Composable
private fun StatusDot(state: ShizukuState, theme: ThemeSpec) {
    val color: Color = when (state) {
        ShizukuState.NOT_INSTALLED -> theme.fontsSecondary.copy(alpha = 0.5f)
        ShizukuState.NOT_RUNNING -> theme.consoleWarning
        ShizukuState.PERMISSION_REQUIRED -> theme.consoleWarning
        ShizukuState.READY -> theme.consoleSuccess
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ---------------------------------------------------------------------
// Info dialog
// ---------------------------------------------------------------------

@Composable
private fun ShizukuInfoDialog(
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.bgBase)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.shizuku_info_title),
                color = theme.fontsHeadings,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.shizuku_info_body_1),
                color = theme.fontsPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.shizuku_info_unlocks),
                color = theme.fontsSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            BulletLine(stringResource(R.string.shizuku_info_unlock_1), theme)
            BulletLine(stringResource(R.string.shizuku_info_unlock_2), theme)
            BulletLine(stringResource(R.string.shizuku_info_unlock_3), theme)

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.shizuku_info_body_2),
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.info_got_it),
                        color = theme.buttonPrimaryBg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String, theme: ThemeSpec) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "•",
            color = theme.buttonPrimaryBg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = theme.fontsPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}