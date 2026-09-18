package io.github.bambi4k.oshootcleaner

import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardTab(theme: ThemeSpec) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var memory by remember { mutableStateOf(SystemStats.memory(context)) }
    var storage by remember { mutableStateOf(SystemStats.storage()) }
    var battery by remember { mutableStateOf(SystemStats.battery(context)) }
    var thermal by remember { mutableStateOf(SystemStats.thermal(context)) }
    var batteryTime by remember { mutableStateOf(BatteryTimeEstimator.compute(context)) }
    var planPickerOpen by remember { mutableStateOf(false) }

    var cpu by remember { mutableStateOf<CpuStats?>(null) }
    var network by remember { mutableStateOf<NetworkUsage?>(null) }

    var isBoosting by remember { mutableStateOf(false) }
    var boostResult by remember { mutableStateOf<String?>(null) }
    var boostProgress by remember { mutableStateOf<String?>(null) }

    var showShizukuConfirm by remember { mutableStateOf(false) }
    var shizukuReady by remember { mutableStateOf(ShizukuManager.isReady()) }

    LaunchedEffect(Unit) {
        cpu = withContext(Dispatchers.Default) { SystemStats.cpuLoad() }
        network = withContext(Dispatchers.IO) { NetworkStats.deviceWide(context) }
    }

    OnResumeEffect {
        memory = SystemStats.memory(context)
        storage = SystemStats.storage()
        battery = SystemStats.battery(context)
        thermal = SystemStats.thermal(context)
        shizukuReady = ShizukuManager.isReady()
        scope.launch {
            network = withContext(Dispatchers.IO) { NetworkStats.deviceWide(context) }
        }
        scope.launch {
            delay(2000)
            batteryTime = BatteryTimeEstimator.compute(context)
        }
    }

    fun refreshStats() {
        memory = SystemStats.memory(context)
        storage = SystemStats.storage()
        battery = SystemStats.battery(context)
        thermal = SystemStats.thermal(context)
        batteryTime = BatteryTimeEstimator.compute(context)
    }

    fun doBoost() {
        if (isBoosting) return
        isBoosting = true
        boostResult = null
        boostProgress = null
        scope.launch {
            val clean = withContext(Dispatchers.IO) {
                CleanupManager.runQuickClean(context)
            }

            if (ShizukuManager.isReady()) {
                val boost = withContext(Dispatchers.IO) {
                    ShizukuManager.boostBackgroundApps(
                        context = context,
                        onProgress = { done, total, current ->
                            scope.launch {
                                boostProgress = if (current.isBlank()) {
                                    null
                                } else {
                                    "Working on $done of $total…"
                                }
                            }
                        }
                    )
                }
                refreshStats()
                boostResult = buildShizukuBoostSummary(context, clean, boost)
            } else {
                val eviction = CleanupManager.restartBackgroundApps(context) { }
                refreshStats()
                boostResult = buildBoostSummary(context, clean, eviction)
            }
            boostProgress = null
            isBoosting = false

            AnalysisPrefs.markStale(context)
            WidgetSync.refreshCleaner(context)
        }
    }

    fun requestBoost() {
        val needsConfirm = ShizukuManager.isReady() &&
                !ShizukuBoostPrefs.shouldSkipConfirmation(context)
        if (needsConfirm) {
            showShizukuConfirm = true
        } else {
            doBoost()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.dash_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(20.dp))

        // --- Memory ---
        StatCard(
            title = stringResource(R.string.dash_memory),
            valueText = "${formatBytesGb(memory.usedBytes)} / ${formatBytesGb(memory.totalBytes)}",
            subtitle = stringResource(R.string.dash_memory_free, formatBytesGb(memory.availBytes)) +
                    if (memory.lowMemory) "  ·  " + stringResource(R.string.dash_low_memory) else "",
            fraction = memory.usedFraction,
            theme = theme,
            barColor = if (memory.lowMemory) theme.consoleError else theme.buttonPrimaryBg,
            onClick = { requestBoost() }
        )
        Spacer(Modifier.height(12.dp))

        // --- Storage ---
        StatCard(
            title = stringResource(R.string.dash_storage),
            valueText = "${formatBytesGb(storage.usedBytes)} / ${formatBytesGb(storage.totalBytes)}",
            subtitle = stringResource(R.string.dash_memory_free, formatBytesGb(storage.freeBytes)),
            fraction = storage.usedFraction,
            theme = theme,
            onClick = { openTab(context, "STORAGE") }
        )
        Spacer(Modifier.height(12.dp))

        // --- Battery + Temperature ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                StatCard(
                    title = stringResource(R.string.dash_battery_short),
                    valueText = "${battery.percent}%",
                    subtitle = stringResource(
                        if (battery.isCharging) R.string.charged else R.string.not_charging
                    ),
                    fraction = battery.percent / 100f,
                    theme = theme,
                    onClick = { openTab(context, "BATTERY") }
                )
            }
            Box(Modifier.weight(1f)) {
                StatCard(
                    title = stringResource(R.string.dash_temperature_short),
                    valueText = battery.temperatureCelsius?.let { "%.1f\u00b0C".format(it) }
                        ?: stringResource(R.string.unavailable),
                    subtitle = stringResource(R.string.dash_battery_sensor),
                    fraction = battery.temperatureCelsius?.let { (it / 45.0).toFloat() } ?: 0f,
                    theme = theme,
                    barColor = theme.consoleWarning,
                    onClick = { openTab(context, "BATTERY") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // --- Thermal ---
        val thermalLabel = stringResource(
            when (thermal.status) {
                PowerManager.THERMAL_STATUS_NONE -> R.string.dash_thermal_nominal
                PowerManager.THERMAL_STATUS_LIGHT -> R.string.dash_thermal_light
                PowerManager.THERMAL_STATUS_MODERATE -> R.string.dash_thermal_moderate
                PowerManager.THERMAL_STATUS_SEVERE -> R.string.dash_thermal_severe
                PowerManager.THERMAL_STATUS_CRITICAL -> R.string.dash_thermal_critical
                PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.dash_thermal_emergency
                PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.dash_thermal_shutdown
                else -> R.string.dash_thermal_unknown
            }
        )
        val thermalFraction = when (thermal.status) {
            PowerManager.THERMAL_STATUS_NONE -> 0.05f
            PowerManager.THERMAL_STATUS_LIGHT -> 0.25f
            PowerManager.THERMAL_STATUS_MODERATE -> 0.5f
            PowerManager.THERMAL_STATUS_SEVERE -> 0.75f
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> 1f
            else -> 0f
        }
        StatCard(
            title = stringResource(R.string.dash_thermal),
            valueText = thermalLabel,
            subtitle = stringResource(R.string.dash_thermal_subtitle),
            fraction = thermalFraction,
            theme = theme,
            barColor = if (thermalFraction >= 0.75f) theme.consoleError else theme.accentTeal,
            infoTitleRes = R.string.info_thermal_title,
            infoBodyRes = R.string.info_thermal_body
        )
        Spacer(Modifier.height(12.dp))

        // --- Battery Time ---
        val btFraction = batteryTime.fractionForBar
        StatCard(
            title = stringResource(R.string.dash_battery_time),
            valueText = batteryTime.primaryLabel(context),
            subtitle = batteryTime.secondaryLabel(context),
            fraction = btFraction,
            theme = theme,
            barColor = when {
                btFraction <= 0.15f -> theme.consoleError
                btFraction <= 0.30f -> theme.consoleWarning
                else -> theme.accentGreen
            },
            onClick = { openTab(context, "BATTERY") },
            infoTitleRes = R.string.info_battery_time_title,
            infoBodyRes = R.string.info_battery_time_body
        )
        Spacer(Modifier.height(12.dp))

        // --- Network ---
        val net = network
        if (net == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
        } else if (net.available) {
            val planUsage = DataPlanQuerier.query(context)
            NetworkCard(
                theme = theme,
                usage = planUsage,
                onSetPlan = { planPickerOpen = true }
            )
        } else {
            ActionRow(
                stringResource(R.string.dash_network_unavailable_action),
                theme
            ) { StorageBreakdownManager.requestUsageAccess(context) }
        }
        Spacer(Modifier.height(12.dp))

        // --- CPU ---
        val cpuValue = cpu?.loadFraction
        if (cpuValue != null) {
            StatCard(
                title = stringResource(R.string.dash_cpu),
                valueText = "${(cpuValue * 100).toInt()}%",
                subtitle = stringResource(R.string.dash_cpu_subtitle),
                fraction = cpuValue,
                theme = theme,
                infoTitleRes = R.string.info_cpu_title,
                infoBodyRes = R.string.info_cpu_body
            )
            Spacer(Modifier.height(12.dp))
        } else {
            ActionRow(
                stringResource(R.string.dash_cpu_unavailable_action),
                theme
            ) { CleanupManager.openBatteryUsage(context) }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.dash_quick_action), theme)
        Spacer(Modifier.height(8.dp))

        ShizukuBadgeFrame(
            enabled = shizukuReady && !isBoosting,
            theme = theme
        ) {
            PrimaryButton(
                label = stringResource(
                    if (isBoosting) R.string.dash_boosting else R.string.dash_boost
                ),
                theme = theme,
                enabled = !isBoosting
            ) { requestBoost() }
        }

        boostProgress?.let { progress ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress,
                color = theme.fontsSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        boostResult?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = theme.consoleSuccess,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (planPickerOpen) {
        DataPlanPickerDialog(
            theme = theme,
            current = DataPlan.load(context),
            onDismiss = { planPickerOpen = false },
            onSave = { plan ->
                DataPlan.save(context, plan)
                planPickerOpen = false
                scope.launch {
                    network = withContext(Dispatchers.IO) { NetworkStats.deviceWide(context) }
                }
            }
        )
    }

    if (showShizukuConfirm) {
        ShizukuBoostConfirmDialog(
            theme = theme,
            onConfirm = { dontAskAgain ->
                if (dontAskAgain) {
                    ShizukuBoostPrefs.setSkipConfirmation(context, true)
                }
                showShizukuConfirm = false
                doBoost()
            },
            onCancel = {
                showShizukuConfirm = false
            }
        )
    }
}

private fun openTab(context: android.content.Context, tabName: String) {
    val ordinal = runCatching { AppTab.valueOf(tabName).ordinal }.getOrNull()
    if (ordinal != null && TabNavigator.navigate(ordinal)) {
        return
    }

    context.startActivity(
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, tabName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    )
}

private fun buildBoostSummary(
    context: android.content.Context,
    clean: CleanupResult,
    eviction: EvictionResult
): String {
    val durationUs = clean.durationMs * 1000L
    val durationText = when {
        durationUs < 1000 -> "$durationUs µs"
        durationUs < 1_000_000 -> "%.1f ms".format(durationUs / 1000.0)
        else -> "%.2f s".format(durationUs / 1_000_000.0)
    }

    val parts = mutableListOf<String>()
    if (clean.freedBytes > 50L * 1024) {
        parts += context.getString(R.string.boost_freed, formatBytesMbGb(clean.freedBytes))
    } else {
        parts += context.getString(R.string.boost_no_cache)
    }
    if (eviction.requested > 0) {
        parts += context.getString(R.string.boost_evicted, eviction.requested)
    }
    if (clean.memFreedMb > 20) {
        parts += context.getString(R.string.boost_mem, clean.memFreedMb)
    }
    parts += context.getString(R.string.boost_duration, durationText)
    return parts.joinToString("  ·  ")
}

private fun buildShizukuBoostSummary(
    context: android.content.Context,
    clean: CleanupResult,
    boost: ShizukuManager.BoostResult
): String {
    val durationText = when {
        boost.durationMs < 1000L -> "${boost.durationMs} ms"
        boost.durationMs < 60_000L -> "%.1f s".format(boost.durationMs / 1000.0)
        else -> "%.1f min".format(boost.durationMs / 60_000.0)
    }

    val parts = mutableListOf<String>()
    if (clean.freedBytes > 50L * 1024) {
        parts += context.getString(R.string.boost_freed, formatBytesMbGb(clean.freedBytes))
    } else {
        parts += context.getString(R.string.boost_no_cache)
    }
    if (boost.cacheCleared > 0) {
        parts += context.getString(R.string.boost_evicted, boost.cacheCleared)
    }
    if (boost.forceStopped > 0) {
        parts += "${boost.forceStopped} apps restarted"
    }
    parts += context.getString(R.string.boost_duration, durationText)
    return parts.joinToString("  ·  ")
}

@Composable
private fun NetworkCard(
    theme: ThemeSpec,
    usage: DataPlanUsage,
    onSetPlan: () -> Unit
) {
    val fraction = usage.fraction
    val barColor = when {
        !usage.hasCap -> theme.accentLavender
        fraction >= 1f -> theme.consoleError
        fraction >= 0.8f -> theme.consoleWarning
        else -> theme.accentGreen
    }

    val cardShape = theme.cornerShape(16)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(cardShape))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dash_network).uppercase(),
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            InfoGlyph(
                titleRes = R.string.info_network_title,
                bodyRes = R.string.info_network_body,
                theme = theme
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onSetPlan) {
                Text(
                    stringResource(
                        if (usage.hasCap) R.string.network_edit_plan
                        else R.string.network_set_plan
                    ),
                    color = theme.fontsLinks,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (usage.hasCap) {
            Text(
                text = stringResource(
                    R.string.network_mobile_of_cap,
                    formatBytesMbGb(usage.usedBytes),
                    formatBytesMbGb(usage.capBytes)
                ),
                color = theme.fontsHeadings,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = formatBytesMbGb(usage.usedBytes),
                color = theme.fontsHeadings,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.network_no_plan_set),
                color = theme.fontsSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        if (usage.hasCap) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(theme.cornerShape(4))
                    .background(theme.bgCrust)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(theme.cornerShape(4))
                        .background(barColor)
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (usage.overCap)
                        stringResource(R.string.network_over_cap)
                    else
                        stringResource(
                            R.string.network_remaining,
                            formatBytesMbGb(usage.remainingBytes)
                        ),
                    color = if (usage.overCap) theme.consoleError else theme.fontsSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.network_wifi,
                        formatBytesMbGb(usage.wifiBytes)
                    ),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp
                )
            }
        } else {
            Text(
                text = stringResource(
                    R.string.network_wifi,
                    formatBytesMbGb(usage.wifiBytes)
                ),
                color = theme.fontsSecondary,
                fontSize = 11.sp
            )
        }
    }
}