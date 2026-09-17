package com.example.oshootcleaner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BatteryTab(theme: ThemeSpec) {
    val context = LocalContext.current

    var battery by remember { mutableStateOf(SystemStats.battery(context)) }
    var writeSettingsGranted by remember { mutableStateOf(PresetManager.hasWriteSettingsAccess(context)) }
    var secureSettingsGranted by remember { mutableStateOf(DevToolsManager.hasSecureSettingsAccess(context)) }

    var autoBrightness by remember { mutableStateOf(BatteryTools.isAutoBrightnessOn(context)) }
    var hapticFeedback by remember { mutableStateOf(BatteryTools.isHapticFeedbackOn(context)) }
    var batterySaver by remember { mutableStateOf(BatteryTools.isBatterySaverOn(context)) }
    var darkMode by remember { mutableStateOf(BatteryTools.isDarkModeOn(context)) }

    OnResumeEffect {
        writeSettingsGranted = PresetManager.hasWriteSettingsAccess(context)
        secureSettingsGranted = DevToolsManager.hasSecureSettingsAccess(context)
        autoBrightness = BatteryTools.isAutoBrightnessOn(context)
        hapticFeedback = BatteryTools.isHapticFeedbackOn(context)
        batterySaver = BatteryTools.isBatterySaverOn(context)
        darkMode = BatteryTools.isDarkModeOn(context)
        battery = SystemStats.battery(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.battery_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(20.dp))

        StatCard(
            title = stringResource(R.string.dash_battery),
            valueText = "${battery.percent}%",
            subtitle = SystemStats.batteryStatusLabel(context) + " · " +
                    stringResource(R.string.battery_health, SystemStats.batteryHealth(context)),
            fraction = battery.percent / 100f,
            theme = theme
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.battery_quick_settings), theme)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.battery_quick_settings_desc),
            color = theme.fontsSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        if (!writeSettingsGranted) {
            TextButton(onClick = { PresetManager.requestWriteSettingsAccess(context) }) {
                Text(
                    stringResource(R.string.battery_grant_modify),
                    color = theme.fontsLinks,
                    fontSize = 12.sp
                )
            }
        }

        ToggleRow(
            label = stringResource(R.string.battery_auto_brightness),
            description = stringResource(R.string.battery_auto_brightness_desc),
            checked = autoBrightness,
            enabled = writeSettingsGranted,
            theme = theme
        ) { if (BatteryTools.setAutoBrightness(context, it)) autoBrightness = it }

        ToggleRow(
            label = stringResource(R.string.battery_haptic),
            description = stringResource(R.string.battery_haptic_desc),
            checked = hapticFeedback,
            enabled = writeSettingsGranted,
            theme = theme
        ) { if (BatteryTools.setHapticFeedback(context, it)) hapticFeedback = it }

        ActionRow(
            stringResource(R.string.battery_push_settings),
            theme
        ) { BatteryTools.openNotificationSettings(context) }

        ActionRow(
            stringResource(R.string.battery_screen_timeout),
            theme
        ) { BatteryTools.openScreenTimeoutSettings(context) }

        // Refresh rate has no app API — deep link to Display settings.
        ActionRow(
            stringResource(R.string.battery_open_display),
            theme
        ) { BatteryTools.openDisplaySettings(context) }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.battery_system_settings), theme)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.battery_system_settings_desc),
            color = theme.fontsSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        ToggleRow(
            label = stringResource(R.string.battery_saver),
            description = stringResource(R.string.battery_saver_desc),
            checked = batterySaver,
            enabled = secureSettingsGranted,
            theme = theme
        ) { if (BatteryTools.setBatterySaver(context, it)) batterySaver = it }

        ToggleRow(
            label = stringResource(R.string.battery_dark_mode),
            description = stringResource(R.string.battery_dark_mode_desc),
            checked = darkMode,
            enabled = secureSettingsGranted,
            theme = theme
        ) { if (BatteryTools.setDarkMode(context, it)) darkMode = it }

        ActionRow(
            stringResource(R.string.battery_usage),
            theme
        ) { CleanupManager.openBatteryUsage(context) }

        Spacer(Modifier.height(32.dp))
    }
}