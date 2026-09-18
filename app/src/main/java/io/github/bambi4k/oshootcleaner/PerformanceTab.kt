package io.github.bambi4k.oshootcleaner

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PerformanceTab(theme: ThemeSpec) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Existing preset state ---
    var writeSettingsGranted by remember { mutableStateOf(PresetManager.hasWriteSettingsAccess(context)) }
    var dndGranted by remember { mutableStateOf(PresetManager.hasNotificationPolicyAccess(context)) }
    var lastLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // --- Enhanced mode state ---
    var shizukuReady by remember { mutableStateOf(ShizukuManager.isReady()) }

    // --- Enhanced control values, read from settings when Shizuku is ready ---
    var animationSpeed by remember { mutableStateOf<ShizukuPerformance.AnimationSpeed?>(null) }
    var processLimit by remember { mutableStateOf<ShizukuPerformance.ProcessLimit?>(null) }
    var dontKeepActivities by remember { mutableStateOf<Boolean?>(null) }
    var processPickerOpen by remember { mutableStateOf(false) }
    var enhancedStatus by remember { mutableStateOf<String?>(null) }

    fun runPreset(preset: PresetManager.Preset) {
        lastLog = PresetManager.apply(context, preset)
        writeSettingsGranted = PresetManager.hasWriteSettingsAccess(context)
        dndGranted = PresetManager.hasNotificationPolicyAccess(context)
    }

    // Load enhanced values on first composition when Shizuku is ready.
    LaunchedEffect(shizukuReady) {
        if (shizukuReady) {
            withContext(Dispatchers.IO) {
                animationSpeed = ShizukuPerformance.getAnimationSpeed()
                processLimit = ShizukuPerformance.getProcessLimit()
                dontKeepActivities = ShizukuPerformance.getDontKeepActivities()
            }
        }
    }

    // Refresh grant status when the user returns from Settings.
    OnResumeEffect {
        writeSettingsGranted = PresetManager.hasWriteSettingsAccess(context)
        dndGranted = PresetManager.hasNotificationPolicyAccess(context)
        shizukuReady = ShizukuManager.isReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.perf_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(20.dp))

        // --- Three big preset cards ---
        PresetCard(
            label = stringResource(R.string.perf_power_saver),
            description = stringResource(R.string.perf_power_saver_desc),
            accentColor = theme.accentGreen,
            theme = theme,
            onClick = { runPreset(PresetManager.Preset.POWER_SAVING) }
        )
        Spacer(Modifier.height(12.dp))

        PresetCard(
            label = stringResource(R.string.perf_balanced),
            description = stringResource(R.string.perf_balanced_desc),
            accentColor = theme.accentSky,
            theme = theme,
            onClick = { runPreset(PresetManager.Preset.BALANCED) }
        )
        Spacer(Modifier.height(12.dp))

        PresetCard(
            label = stringResource(R.string.perf_performance),
            description = stringResource(R.string.perf_performance_desc),
            accentColor = theme.consoleWarning,
            theme = theme,
            onClick = { runPreset(PresetManager.Preset.PERFORMANCE) }
        )

        // --- Permission warning, only when actually needed ---
        if (!writeSettingsGranted || !dndGranted) {
            Spacer(Modifier.height(16.dp))
            val warnShape = theme.cornerShape(12)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(warnShape)
                    .background(theme.surfaceBrush())
                    .then(theme.themedBevel(warnShape))
                    .padding(14.dp)
            ) {
                Text(
                    stringResource(R.string.perf_needs_access),
                    color = theme.fontsSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!writeSettingsGranted) {
                        SmallLinkButton(
                            label = stringResource(R.string.perf_grant_modify),
                            theme = theme
                        ) { PresetManager.requestWriteSettingsAccess(context) }
                    }
                    if (!dndGranted) {
                        SmallLinkButton(
                            label = stringResource(R.string.perf_grant_dnd),
                            theme = theme
                        ) { PresetManager.requestNotificationPolicyAccess(context) }
                    }
                }
            }
        }

        // --- Result log, only if a preset was actually applied ---
        if (lastLog.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val logShape = theme.cornerShape(12)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(logShape)
                    .background(theme.consoleBg)
                    .then(theme.themedBevel(logShape))
                    .padding(14.dp)
            ) {
                lastLog.forEach {
                    Text(it, color = theme.consoleText, fontSize = 11.sp)
                }
            }
        }

        // =================================================================
        // ENHANCED MODE
        // =================================================================

        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.perf_enhanced_title), theme)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.perf_enhanced_subtitle),
            color = theme.fontsSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))

        ShizukuCard(
            theme = theme,
            onReady = {
                shizukuReady = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        animationSpeed = ShizukuPerformance.getAnimationSpeed()
                        processLimit = ShizukuPerformance.getProcessLimit()
                        dontKeepActivities = ShizukuPerformance.getDontKeepActivities()
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {

            // --- Animation speed ---
            EnhancedAnimSpeedControl(
                current = animationSpeed,
                enabled = shizukuReady,
                theme = theme,
                onSelect = { speed ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            ShizukuPerformance.setAnimationSpeed(speed)
                        }
                        if (ok) {
                            animationSpeed = speed
                            enhancedStatus = context.getString(R.string.perf_reset_enhanced_done)
                        }
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            // --- Background process limit ---
            EnhancedProcessLimitRow(
                current = processLimit,
                enabled = shizukuReady,
                theme = theme,
                pickerOpen = processPickerOpen,
                onPickerOpenChange = { processPickerOpen = it },
                onSelect = { limit ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            ShizukuPerformance.setProcessLimit(limit)
                        }
                        if (ok) {
                            processLimit = limit
                            enhancedStatus = context.getString(R.string.perf_reset_enhanced_done)
                        }
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            // --- Don't Keep Activities ---
            val dkaShape = theme.cornerShape(12)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dkaShape)
                    .background(theme.surfaceBrush())
                    .then(theme.themedBevel(dkaShape))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.perf_dont_keep_title),
                        color = if (shizukuReady) theme.fontsPrimary
                        else theme.fontsSecondary.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.perf_dont_keep_desc),
                        color = theme.fontsSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = dontKeepActivities == true,
                    onCheckedChange = { checked ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                ShizukuPerformance.setDontKeepActivities(checked)
                            }
                            if (ok) {
                                dontKeepActivities = checked
                                enhancedStatus = context.getString(R.string.perf_reset_enhanced_done)
                            }
                        }
                    },
                    enabled = shizukuReady,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.buttonPrimaryText,
                        checkedTrackColor = theme.buttonPrimaryBg,
                        uncheckedThumbColor = theme.fontsSecondary,
                        uncheckedTrackColor = theme.bgCrust,
                        disabledCheckedThumbColor = theme.fontsSecondary,
                        disabledCheckedTrackColor = theme.bgCrust,
                        disabledUncheckedThumbColor = theme.fontsSecondary.copy(alpha = 0.5f),
                        disabledUncheckedTrackColor = theme.bgCrust
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- Clear Background Processes ---
            ShizukuBadgeFrame(
                enabled = shizukuReady,
                theme = theme
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(theme.cornerShape(12))
                        .background(
                            if (shizukuReady) theme.buttonPrimaryBg
                            else theme.buttonDisabledBg
                        )
                        .clickable(enabled = shizukuReady) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ShizukuPerformance.killAllBackgroundApps()
                                }
                                if (ok) {
                                    enhancedStatus = context.getString(R.string.perf_kill_all_done)
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.perf_kill_all_button),
                        color = if (shizukuReady) theme.buttonPrimaryText
                        else theme.buttonDisabledText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.perf_kill_all_desc),
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(16.dp))

            // --- Restore defaults ---
            val restoreShape = theme.cornerShape(12)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(restoreShape)
                    .background(theme.surfaceBrush())
                    .then(theme.themedBevel(restoreShape))
                    .clickable(enabled = shizukuReady) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                ShizukuPerformance.resetAll()
                            }
                            if (ok) {
                                animationSpeed = ShizukuPerformance.AnimationSpeed.NORMAL
                                processLimit = ShizukuPerformance.ProcessLimit.SYSTEM_DEFAULT
                                dontKeepActivities = false
                                enhancedStatus = context.getString(R.string.perf_reset_enhanced_done)
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.perf_reset_enhanced),
                    color = if (shizukuReady) theme.fontsLinks
                    else theme.fontsSecondary.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Hint text shown when Shizuku isn't connected.
        if (!shizukuReady) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.perf_enhanced_unavailable),
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        // Status confirmation of the last action.
        enhancedStatus?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = theme.consoleSuccess,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // =================================================================
        // END ENHANCED MODE
        // =================================================================

        // --- Developer tools ---
        Spacer(Modifier.height(32.dp))
        SectionLabel(stringResource(R.string.perf_devtools), theme)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.perf_devtools_hint),
            color = theme.fontsSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(
            stringResource(R.string.perf_open_dev_options),
            theme
        ) { CleanupManager.openDeveloperOptions(context) }

        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
// Enhanced mode — individual controls
// =====================================================================

@Composable
private fun EnhancedAnimSpeedControl(
    current: ShizukuPerformance.AnimationSpeed?,
    enabled: Boolean,
    theme: ThemeSpec,
    onSelect: (ShizukuPerformance.AnimationSpeed) -> Unit
) {
    val cardShape = theme.cornerShape(12)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(cardShape))
            .padding(14.dp)
    ) {
        Text(
            stringResource(R.string.perf_anim_title),
            color = if (enabled) theme.fontsPrimary
            else theme.fontsSecondary.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.perf_anim_desc),
            color = theme.fontsSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(theme.cornerShape(8))
                .background(theme.bgCrust)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnimSegment(
                label = stringResource(R.string.perf_anim_speed_off),
                selected = current == ShizukuPerformance.AnimationSpeed.OFF,
                enabled = enabled,
                theme = theme,
                modifier = Modifier.weight(1f)
            ) { onSelect(ShizukuPerformance.AnimationSpeed.OFF) }
            AnimSegment(
                label = stringResource(R.string.perf_anim_speed_half),
                selected = current == ShizukuPerformance.AnimationSpeed.HALF,
                enabled = enabled,
                theme = theme,
                modifier = Modifier.weight(1f)
            ) { onSelect(ShizukuPerformance.AnimationSpeed.HALF) }
            AnimSegment(
                label = stringResource(R.string.perf_anim_speed_normal),
                selected = current == ShizukuPerformance.AnimationSpeed.NORMAL,
                enabled = enabled,
                theme = theme,
                modifier = Modifier.weight(1f)
            ) { onSelect(ShizukuPerformance.AnimationSpeed.NORMAL) }
        }
    }
}

@Composable
private fun AnimSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    theme: ThemeSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(theme.cornerShape(6))
            .background(
                when {
                    !enabled -> theme.bgCrust
                    selected -> theme.buttonPrimaryBg
                    else -> theme.bgCrust
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> theme.fontsSecondary.copy(alpha = 0.4f)
                selected -> theme.buttonPrimaryText
                else -> theme.fontsSecondary
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun EnhancedProcessLimitRow(
    current: ShizukuPerformance.ProcessLimit?,
    enabled: Boolean,
    theme: ThemeSpec,
    pickerOpen: Boolean,
    onPickerOpenChange: (Boolean) -> Unit,
    onSelect: (ShizukuPerformance.ProcessLimit) -> Unit
) {
    val rowShape = theme.cornerShape(12)
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(theme.surfaceBrush())
                .then(theme.themedBevel(rowShape))
                .clickable(enabled = enabled) { onPickerOpenChange(true) }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.perf_bg_limit_title),
                        color = if (enabled) theme.fontsPrimary
                        else theme.fontsSecondary.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.perf_bg_limit_desc),
                        color = theme.fontsSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "›",
                    color = theme.fontsSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = current?.let { processLimitLabel(it) } ?: "—",
                color = if (enabled) theme.fontsLinks
                else theme.fontsSecondary.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        DropdownMenu(
            expanded = pickerOpen,
            onDismissRequest = { onPickerOpenChange(false) },
            modifier = Modifier.background(theme.bgSurface)
        ) {
            ShizukuPerformance.ProcessLimit.values().forEach { limit ->
                DropdownMenuItem(
                    text = {
                        Text(
                            processLimitLabel(limit),
                            color = if (limit == current) theme.buttonPrimaryBg
                            else theme.fontsPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (limit == current) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onPickerOpenChange(false)
                        onSelect(limit)
                    }
                )
            }
        }
    }
}

@Composable
private fun processLimitLabel(limit: ShizukuPerformance.ProcessLimit): String = when (limit) {
    ShizukuPerformance.ProcessLimit.SYSTEM_DEFAULT -> stringResource(R.string.perf_bg_limit_default)
    ShizukuPerformance.ProcessLimit.STANDARD -> stringResource(R.string.perf_bg_limit_standard)
    ShizukuPerformance.ProcessLimit.AT_MOST_1 -> stringResource(R.string.perf_bg_limit_1)
    ShizukuPerformance.ProcessLimit.AT_MOST_2 -> stringResource(R.string.perf_bg_limit_2)
    ShizukuPerformance.ProcessLimit.AT_MOST_3 -> stringResource(R.string.perf_bg_limit_3)
    ShizukuPerformance.ProcessLimit.AT_MOST_4 -> stringResource(R.string.perf_bg_limit_4)
}

// =====================================================================
// Existing composables — VGUI-aware
// =====================================================================

@Composable
private fun PresetCard(
    label: String,
    description: String,
    accentColor: androidx.compose.ui.graphics.Color,
    theme: ThemeSpec,
    onClick: () -> Unit
) {
    val shape = theme.cornerShape(16)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(shape))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(64.dp)
                .padding(end = 16.dp)
                .fillMaxWidth(0.012f)
                .clip(theme.cornerShape(2))
                .background(accentColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = theme.fontsHeadings,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = theme.fontsSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Start
            )
        }
        Text(
            "›",
            color = theme.fontsSecondary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun SmallLinkButton(
    label: String,
    theme: ThemeSpec,
    onClick: () -> Unit
) {
    val shape = theme.cornerShape(8)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(theme.bgCrust)
            .then(theme.themedBevel(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = theme.fontsLinks,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}