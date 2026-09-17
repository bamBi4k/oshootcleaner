package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PermissionEntry(
    val id: String,
    val titleRes: Int,
    val whyRes: Int,
    val grantLabelRes: Int,
    val isGranted: (Context) -> Boolean,
    val request: (Context) -> Unit
)

@Composable
fun PermissionsTab(theme: ThemeSpec) {
    val context = LocalContext.current

    // Bumped on every resume and every runtime-permission callback to force
    // recomposition of the rows so `isGranted` is re-evaluated.
    var refreshTick by remember { mutableIntStateOf(0) }

    // Runtime launcher for media permissions (READ_MEDIA_IMAGES / VIDEO / READ_EXTERNAL_STORAGE).
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    // Runtime launcher for POST_NOTIFICATIONS on Android 13+.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshTick++
    }

    // Auto-refresh every time we come back to the foreground (user may have
    // just flipped a special-access toggle in the system Settings app).
    OnResumeEffect { refreshTick++ }

    val entries = remember {
        buildPermissionList(mediaLauncher::launch, notifLauncher::launch)
    }

    // Guided flow dialog state
    var guideOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.perm_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.perm_subtitle),
            color = theme.fontsSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        // ---- Guided grant-all button ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.buttonPrimaryBg)
                .clickable { guideOpen = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.perm_guide_start),
                color = theme.buttonPrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        entries.forEach { entry ->
            key(entry.id, refreshTick) {
                val granted = entry.isGranted(context)
                PermissionRow(
                    entry = entry,
                    granted = granted,
                    theme = theme,
                    onGrant = { entry.request(context) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Enhanced Mode", theme)
        Spacer(Modifier.height(8.dp))
        ShizukuCard(theme = theme)

        Spacer(Modifier.height(32.dp))

    }

    // Guided permission flow dialog
    if (guideOpen) {
        PermissionGuideDialog(
            theme = theme,
            onDismiss = { guideOpen = false }
        )
    }
}

@Composable
private fun PermissionRow(
    entry: PermissionEntry,
    granted: Boolean,
    theme: ThemeSpec,
    onGrant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bgSurface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(entry.titleRes),
                color = theme.fontsPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(
                    if (granted) R.string.perm_granted else R.string.perm_not_granted
                ),
                color = if (granted) theme.consoleSuccess else theme.fontsSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(entry.whyRes),
            color = theme.fontsSecondary,
            fontSize = 11.sp
        )
        if (!granted) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onGrant) {
                    Text(
                        stringResource(entry.grantLabelRes),
                        color = theme.fontsLinks,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun buildPermissionList(
    requestMedia: (Array<String>) -> Unit,
    requestNotif: (String) -> Unit
): List<PermissionEntry> = listOf(
    PermissionEntry(
        id = "notifications",
        titleRes = R.string.perm_notif_title,
        whyRes = R.string.perm_notif_why,
        grantLabelRes = R.string.perm_notif_grant,
        isGranted = { ctx ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        },
        request = { _ -> requestNotif(android.Manifest.permission.POST_NOTIFICATIONS) }
    ),
    PermissionEntry(
        id = "usage",
        titleRes = R.string.perm_usage_title,
        whyRes = R.string.perm_usage_why,
        grantLabelRes = R.string.perm_usage_grant,
        isGranted = { StorageBreakdownManager.hasUsageAccess(it) },
        request = { StorageBreakdownManager.requestUsageAccess(it) }
    ),
    PermissionEntry(
        id = "all_files",
        titleRes = R.string.perm_files_title,
        whyRes = R.string.perm_files_why,
        grantLabelRes = R.string.perm_files_grant,
        isGranted = { StorageAnalyzer.hasAllFilesAccess() },
        request = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    ),
    PermissionEntry(
        id = "write_settings",
        titleRes = R.string.perm_write_title,
        whyRes = R.string.perm_write_why,
        grantLabelRes = R.string.perm_write_grant,
        isGranted = { PresetManager.hasWriteSettingsAccess(it) },
        request = { PresetManager.requestWriteSettingsAccess(it) }
    ),
    PermissionEntry(
        id = "dnd",
        titleRes = R.string.perm_dnd_title,
        whyRes = R.string.perm_dnd_why,
        grantLabelRes = R.string.perm_dnd_grant,
        isGranted = { PresetManager.hasNotificationPolicyAccess(it) },
        request = { PresetManager.requestNotificationPolicyAccess(it) }
    ),
    PermissionEntry(
        id = "battery_exempt",
        titleRes = R.string.battery_exempt_title,
        whyRes = R.string.battery_exempt_desc,
        grantLabelRes = R.string.battery_exempt_grant,
        isGranted = { BatteryOptimizationHelper.isExempt(it) },
        request = { BatteryOptimizationHelper.requestExemption(it) }
    ),
    PermissionEntry(
        id = "media",
        titleRes = R.string.perm_media_title,
        whyRes = R.string.perm_media_why,
        grantLabelRes = R.string.perm_media_grant,
        isGranted = { StorageBreakdownManager.hasMediaAccess(it) },
        request = { _ -> requestMedia(StorageBreakdownManager.mediaPermissions()) }
    )
)