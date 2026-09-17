package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The order in which permissions are requested during the guided flow.
 * Each step stores its own state check and its own "open the OS screen"
 * action, so the guide is just a switcher.
 */
private data class GuideStep(
    val id: String,
    val titleRes: Int,
    val whyRes: Int,
    val isGranted: (Context) -> Boolean,
    val openScreen: (Context) -> Unit,
)

private fun steps(): List<GuideStep> = listOf(
    GuideStep(
        id = "usage",
        titleRes = R.string.perm_usage_title,
        whyRes = R.string.perm_usage_why,
        isGranted = { StorageBreakdownManager.hasUsageAccess(it) },
        openScreen = { StorageBreakdownManager.requestUsageAccess(it) }
    ),
    GuideStep(
        id = "notifications",
        titleRes = R.string.perm_notif_title,
        whyRes = R.string.perm_notif_why,
        isGranted = { ctx ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        },
        openScreen = { ctx ->
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ctx.startActivity(intent) }
            catch (_: Exception) { /* notifications may already be set */ }
        }
    ),
    GuideStep(
        id = "write_settings",
        titleRes = R.string.perm_write_title,
        whyRes = R.string.perm_write_why,
        isGranted = { PresetManager.hasWriteSettingsAccess(it) },
        openScreen = { PresetManager.requestWriteSettingsAccess(it) }
    ),
    GuideStep(
        id = "dnd",
        titleRes = R.string.perm_dnd_title,
        whyRes = R.string.perm_dnd_why,
        isGranted = { PresetManager.hasNotificationPolicyAccess(it) },
        openScreen = { PresetManager.requestNotificationPolicyAccess(it) }
    ),
    GuideStep(
        id = "battery_exempt",
        titleRes = R.string.battery_exempt_title,
        whyRes = R.string.battery_exempt_desc,
        isGranted = { BatteryOptimizationHelper.isExempt(it) },
        openScreen = { BatteryOptimizationHelper.requestExemption(it) }
    ),
    GuideStep(
        id = "all_files",
        titleRes = R.string.perm_files_title,
        whyRes = R.string.perm_files_why,
        isGranted = { StorageAnalyzer.hasAllFilesAccess() },
        openScreen = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { ctx.startActivity(intent) }
                catch (_: Exception) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    ),
    GuideStep(
        id = "media",
        titleRes = R.string.perm_media_title,
        whyRes = R.string.perm_media_why,
        isGranted = { StorageBreakdownManager.hasMediaAccess(it) },
        openScreen = { _ -> /* handled via runtime launcher in the caller */ }
    ),
)

@Composable
fun PermissionGuideDialog(
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Current step index. Auto-advances when the user returns from the OS screen.
    var stepIndex by remember { mutableIntStateOf(0) }

    // The media step needs a runtime permission launcher, which can't live
    // in the step's own lambda (Compose callback). We special-case it below.
    var pendingMediaStep by remember { mutableStateOf(false) }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the runtime dialog closes, advance
        stepIndex++
    }

    // Re-check grants every time the dialog is recomposed after resume.
    OnResumeEffect {
        // Auto-advance past any already-granted steps
        while (stepIndex < steps().size && steps()[stepIndex].isGranted(context)) {
            stepIndex++
        }
    }

    val allSteps = steps()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.bgBase)
                .padding(24.dp)
        ) {
            if (stepIndex >= allSteps.size) {
                // All steps handled
                Text(
                    stringResource(R.string.perm_guide_done_title),
                    color = theme.fontsHeadings,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_guide_done_body),
                    color = theme.fontsSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.action_close),
                            color = theme.buttonPrimaryBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                val step = allSteps[stepIndex]

                // Progress indicator
                Text(
                    stringResource(R.string.perm_guide_step, stepIndex + 1, allSteps.size),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(step.titleRes),
                    color = theme.fontsHeadings,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(step.whyRes),
                    color = theme.fontsPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        // Skip this step
                        stepIndex++
                    }) {
                        Text(
                            stringResource(R.string.perm_guide_skip),
                            color = theme.fontsSecondary,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = {
                        if (step.id == "media") {
                            mediaLauncher.launch(StorageBreakdownManager.mediaPermissions())
                        } else {
                            step.openScreen(context)
                        }
                    }) {
                        Text(
                            stringResource(R.string.perm_guide_open),
                            color = theme.buttonPrimaryBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}