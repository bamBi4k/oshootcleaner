package io.github.bambi4k.oshootcleaner

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

const val EXTRA_OPEN_TAB = "com.example.io.github.bambi4k.oshootcleaner.OPEN_TAB"
val EXTRA_OPEN_TAB_PARAM: ActionParameters.Key<String> = ActionParameters.Key(EXTRA_OPEN_TAB)

class DashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 160.dp),
            DpSize(250.dp, 120.dp),
            DpSize(250.dp, 250.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = WidgetStats.snapshot(context)
        provideContent {
            val theme = remember { ThemeStore.getSelectedTheme(context) }
            WidgetRoot(context, theme, stats)
        }
    }
}

data class WidgetStats(
    val memUsed: Long,
    val memTotal: Long,
    val memFraction: Float,
    val storageUsed: Long,
    val storageTotal: Long,
    val storageFraction: Float,
    val storageFree: Long,
    val batteryTempC: Double?,
) {
    companion object {
        fun snapshot(context: Context): WidgetStats {
            val mem = SystemStats.memory(context)
            val store = SystemStats.storage()
            val batt = SystemStats.battery(context)
            return WidgetStats(
                memUsed = mem.usedBytes,
                memTotal = mem.totalBytes,
                memFraction = mem.usedFraction,
                storageUsed = store.usedBytes,
                storageTotal = store.totalBytes,
                storageFraction = store.usedFraction,
                storageFree = store.freeBytes,
                batteryTempC = batt.temperatureCelsius,
            )
        }
    }
}

@Composable
private fun WidgetRoot(context: Context, theme: ThemeSpec, stats: WidgetStats) {
    val size = LocalSize.current
    val isRich = size.height.value >= 200f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(theme.bgBase)
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (isRich) {
                // Header only in 4×4 — the 2×2 and 4×2 skip it for space.
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_logo_clean),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.widget_app_name),
                        style = TextStyle(
                            color = ColorProvider(theme.fontsHeadings),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(GlanceModifier.height(10.dp))
            }

            // Four uniform cards: RAM / Storage / Battery temp / Free space
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricCard(
                    context = context,
                    title = context.getString(R.string.dash_memory),
                    value = "${(stats.memFraction * 100).toInt()}%",
                    subtitle = context.getString(
                        R.string.dash_tile_used_of,
                        formatBytesMbGb(stats.memUsed),
                        formatBytesMbGb(stats.memTotal)
                    ),
                    fraction = stats.memFraction,
                    barColor = theme.accentSky,
                    theme = theme,
                    tabHint = "DASHBOARD",
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    context = context,
                    title = context.getString(R.string.dash_storage),
                    value = "${(stats.storageFraction * 100).toInt()}%",
                    subtitle = context.getString(
                        R.string.dash_tile_used_of,
                        formatBytesGb(stats.storageUsed),
                        formatBytesGb(stats.storageTotal)
                    ),
                    fraction = stats.storageFraction,
                    barColor = theme.accentPeach,
                    theme = theme,
                    tabHint = "STORAGE",
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricCard(
                    context = context,
                    title = context.getString(R.string.dash_battery_temp),
                    value = stats.batteryTempC?.let { "%.1f°C".format(it) } ?: "—",
                    subtitle = context.getString(R.string.dash_battery_sensor),
                    fraction = stats.batteryTempC?.let { (it / 45.0).toFloat() } ?: 0f,
                    barColor = tempBarColor(theme, stats.batteryTempC),
                    theme = theme,
                    tabHint = "BATTERY",
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    context = context,
                    title = context.getString(R.string.dash_free_space),
                    value = formatBytesGb(stats.storageFree),
                    subtitle = context.getString(R.string.dash_free_space_subtitle),
                    // Free space: invert the fraction so a mostly-full disk shows
                    // a mostly-empty bar — visually reads as "you have room".
                    fraction = 1f - stats.storageFraction,
                    barColor = theme.accentGreen,
                    theme = theme,
                    tabHint = "STORAGE",
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            if (isRich) {
                Spacer(GlanceModifier.height(10.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .cornerRadius(14.dp)
                        .background(theme.buttonPrimaryBg)
                        .clickable(
                            actionStartActivity<MainActivity>(
                                actionParametersOf(EXTRA_OPEN_TAB_PARAM to "DASHBOARD")
                            )
                        )
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_open_dashboard),
                        style = TextStyle(
                            color = ColorProvider(theme.buttonPrimaryText),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    context: Context,
    title: String,
    value: String,
    subtitle: String,
    fraction: Float,
    barColor: Color,
    theme: ThemeSpec,
    tabHint: String,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .cornerRadius(14.dp)
            .background(theme.bgSurface)
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(EXTRA_OPEN_TAB_PARAM to tabHint)
                )
            )
            .padding(10.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(theme.fontsSecondary),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(theme.fontsHeadings),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = ColorProvider(theme.fontsSecondary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        ProgressBar(
            fraction = fraction,
            barColor = barColor,
            trackColor = theme.bgCrust
        )
    }
}

@Composable
private fun ProgressBar(
    fraction: Float,
    barColor: Color,
    trackColor: Color
) {
    val filled = (fraction.coerceIn(0f, 1f) * 10f).toInt().coerceIn(0, 10)
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(4.dp)
    ) {
        for (i in 0 until 10) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .cornerRadius(2.dp)
                    .background(if (i < filled) barColor else trackColor)
            ) { }
        }
    }
}

private fun tempBarColor(theme: ThemeSpec, tempC: Double?): Color = when {
    tempC == null -> theme.accentTeal
    tempC >= 42.0 -> theme.consoleError
    tempC >= 38.0 -> theme.consoleWarning
    else -> theme.accentTeal
}