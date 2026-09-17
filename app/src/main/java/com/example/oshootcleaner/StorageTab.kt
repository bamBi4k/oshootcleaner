package com.example.oshootcleaner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageTab(theme: ThemeSpec) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storage by remember { mutableStateOf(SystemStats.storage()) }
    var breakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
    var appSizes by remember { mutableStateOf<List<AppSizeEntry>>(emptyList()) }
    var showAllApps by remember { mutableStateOf(false) }

    // Guided cache clean session state
    var cacheCleanSession by remember { mutableStateOf<CacheCleanSession?>(null) }
    var isBuildingSession by remember { mutableStateOf(false) }
    var sessionBuildFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        breakdown = withContext(Dispatchers.IO) { StorageCache.get(context) }
        appSizes = withContext(Dispatchers.IO) { AppSizeScanner.scanAll(context) }
    }

    OnResumeEffect {
        scope.launch {
            StorageCache.invalidate()
            breakdown = withContext(Dispatchers.IO) { StorageCache.get(context) }
            appSizes = withContext(Dispatchers.IO) { AppSizeScanner.scanAll(context) }
            storage = SystemStats.storage()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            breakdown = withContext(Dispatchers.IO) { StorageCache.get(context) }
        }
    }

    // --- Auto clean completion dialog ---
    val automationState = CacheCleanAutomation.state
    if (automationState.finished) {
        AlertDialog(
            onDismissRequest = { CacheCleanAutomation.resetFinished() },
            title = { Text(stringResource(R.string.auto_clean_done_title)) },
            text = {
                if (automationState.massFailure) {
                    Text(stringResource(R.string.auto_clean_mass_failure))
                } else {
                    Text(
                        stringResource(
                            R.string.auto_clean_done_body,
                            automationState.cleared,
                            automationState.skipped
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CacheCleanAutomation.resetFinished()
                    scope.launch {
                        StorageCache.invalidate()
                        breakdown = withContext(Dispatchers.IO) { StorageCache.get(context) }
                        appSizes = withContext(Dispatchers.IO) { AppSizeScanner.scanAll(context) }
                        storage = SystemStats.storage()
                    }
                }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.storage_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(20.dp))

        StatCard(
            title = stringResource(R.string.storage_device),
            valueText = stringResource(R.string.storage_used, formatBytesGb(storage.usedBytes)),
            subtitle = stringResource(
                R.string.storage_free,
                formatBytesGb(storage.freeBytes),
                formatBytesGb(storage.totalBytes)
            ),
            fraction = storage.usedFraction,
            theme = theme
        )

        Spacer(Modifier.height(16.dp))

        // --- Auto clean (accessibility-based, the only reliable path) ---
        val automationEnabled = CacheCleanAutomation.isServiceEnabled(context)

        if (!automationEnabled) {
            AccessibilityOnboarding(
                theme = theme,
                onEnable = { CacheCleanAutomation.openAccessibilitySettings(context) }
            )
        } else {
            PrimaryButton(
                label = if (automationState.running) {
                    automationState.progress.ifEmpty {
                        stringResource(R.string.storage_auto_clean_working)
                    }
                } else {
                    stringResource(R.string.storage_auto_clean)
                },
                theme = theme,
                enabled = !automationState.running && appSizes.isNotEmpty()
            ) {
                val targets = appSizes
                    .filter { it.isUserApp }
                    .filter { it.cacheBytes > 10L * 1024 * 1024 }
                    .take(20)
                    .map { it.packageName }
                if (targets.isNotEmpty()) {
                    CacheCleanAutomation.start(context, targets)
                }
            }

            // Cancel button while running
            if (automationState.running) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    label = stringResource(R.string.storage_auto_clean_cancel),
                    theme = theme,
                    enabled = true
                ) {
                    CacheCleanAutomation.stop(context)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Guided cache clean ---
        PrimaryButton(
            label = stringResource(
                if (isBuildingSession) R.string.storage_preparing
                else R.string.storage_guided_clean
            ),
            theme = theme,
            enabled = !isBuildingSession && !automationState.running
        ) {
            isBuildingSession = true
            sessionBuildFailed = false
            scope.launch {
                val session = withContext(Dispatchers.IO) {
                    CacheCleanSessionBuilder.build(context)
                }
                if (session != null) {
                    cacheCleanSession = session
                } else {
                    sessionBuildFailed = true
                }
                isBuildingSession = false
            }
        }

        if (sessionBuildFailed) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.storage_no_cache_to_clean),
                color = theme.fontsSecondary,
                fontSize = 12.sp
            )
            TextButton(onClick = {
                sessionBuildFailed = false
                scope.launch {
                    val session = withContext(Dispatchers.IO) {
                        CacheCleanSessionBuilder.build(
                            context,
                            minCacheBytes = 1L * 1024 * 1024
                        )
                    }
                    if (session != null) cacheCleanSession = session
                }
            }) {
                Text(
                    stringResource(R.string.storage_try_again_lower_threshold),
                    color = theme.fontsLinks,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.storage_breakdown), theme)
        Spacer(Modifier.height(8.dp))

        val bd = breakdown
        if (bd == null) {
            Text(
                stringResource(R.string.storage_scanning),
                color = theme.fontsSecondary, fontSize = 12.sp
            )
        } else {
            if (!bd.hasUsageAccess) {
                PermissionNote(
                    stringResource(R.string.storage_grant_usage),
                    stringResource(R.string.storage_grant_usage_btn),
                    theme
                ) { StorageBreakdownManager.requestUsageAccess(context) }
            }
            if (!bd.hasMediaAccess) {
                PermissionNote(
                    stringResource(R.string.storage_grant_media),
                    stringResource(R.string.storage_grant_media_btn),
                    theme
                ) { mediaPermissionLauncher.launch(StorageBreakdownManager.mediaPermissions()) }
            }

            Spacer(Modifier.height(8.dp))
            BreakdownRow(stringResource(R.string.storage_apps), bd.appsBytes, storage.usedBytes, theme, theme.accentSky)
            BreakdownRow(stringResource(R.string.storage_cache), bd.cacheBytes, storage.usedBytes, theme, theme.accentPeach)
            BreakdownRow(stringResource(R.string.storage_photos_videos), bd.photosVideosBytes, storage.usedBytes, theme, theme.accentGreen)
            BreakdownRow(stringResource(R.string.storage_downloads), bd.downloadsBytes, storage.usedBytes, theme, theme.accentYellow)
            BreakdownRow(stringResource(R.string.storage_other), bd.otherBytes, storage.usedBytes, theme, theme.fontsSecondary)
        }

        // --- Top apps by size ---
        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.storage_top_apps), theme)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.storage_top_apps_subtitle),
            color = theme.fontsSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        if (appSizes.isEmpty()) {
            PermissionNote(
                stringResource(R.string.storage_top_apps_needs_usage),
                stringResource(R.string.storage_grant_usage_btn),
                theme
            ) { StorageBreakdownManager.requestUsageAccess(context) }
        } else {
            val visible = if (showAllApps) appSizes else appSizes.take(8)
            visible.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.bgSurface)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.label,
                            color = theme.fontsPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatBytesGb(entry.totalBytes),
                            color = theme.fontsSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.storage_top_apps_breakdown,
                            formatBytesMbGb(entry.appBytes),
                            formatBytesMbGb(entry.dataBytes),
                            formatBytesMbGb(entry.cacheBytes)
                        ),
                        color = theme.fontsSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (appSizes.size > 8) {
                TextButton(onClick = { showAllApps = !showAllApps }) {
                    Text(
                        stringResource(
                            if (showAllApps) R.string.storage_show_less
                            else R.string.storage_show_more
                        ),
                        color = theme.fontsLinks,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.storage_quick_tools), theme)
        Spacer(Modifier.height(8.dp))

        ActionRow(
            stringResource(R.string.storage_clear_all_cache),
            theme
        ) { CleanupManager.openSystemStorageSettings(context) }
        ActionRow(
            stringResource(R.string.storage_manage_apps),
            theme
        ) { CleanupManager.openManageApplications(context) }
        ActionRow(
            stringResource(R.string.storage_manager),
            theme
        ) { CleanupManager.openSystemStorageSettings(context) }
        ActionRow(
            stringResource(R.string.storage_open_downloads),
            theme
        ) { CleanupManager.openDownloads(context) }

        Spacer(Modifier.height(32.dp))
    }

    // --- Guided cache clean dialog ---
    cacheCleanSession?.let { session ->
        CacheCleanFlow(
            session = session,
            theme = theme,
            onDismiss = {
                scope.launch {
                    StorageCache.invalidate()
                    breakdown = withContext(Dispatchers.IO) { StorageCache.get(context) }
                    storage = SystemStats.storage()
                    appSizes = withContext(Dispatchers.IO) { AppSizeScanner.scanAll(context) }
                }
                cacheCleanSession = null
            }
        )
    }
}

@Composable
private fun PermissionNote(text: String, actionLabel: String, theme: ThemeSpec, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bgSurface)
            .padding(12.dp)
    ) {
        Text(text, color = theme.fontsSecondary, fontSize = 12.sp)
        TextButton(onClick = onClick) {
            Text(actionLabel, color = theme.fontsLinks, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BreakdownRow(label: String, bytes: Long, totalUsed: Long, theme: ThemeSpec, dotColor: Color) {
    val fraction = if (totalUsed > 0) (bytes.toFloat() / totalUsed).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = theme.fontsPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatBytesGb(bytes), color = theme.fontsSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text("${(fraction * 100).toInt()}%", color = theme.fontsSecondary, fontSize = 12.sp)
    }
}