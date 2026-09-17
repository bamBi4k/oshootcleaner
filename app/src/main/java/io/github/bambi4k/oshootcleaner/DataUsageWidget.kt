@file:Suppress("RestrictedApi")

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

class DataUsageWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 160.dp),   // 2×2 minimum
            DpSize(250.dp, 120.dp),   // 4×2
            DpSize(250.dp, 250.dp),   // 4×4
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usage = DataPlanQuerier.query(context)
        provideContent {
            val theme = remember { ThemeStore.getSelectedTheme(context) }
            WidgetRoot(context, theme, usage)
        }
    }
}

@Composable
private fun WidgetRoot(
    context: Context,
    theme: ThemeSpec,
    usage: DataPlanUsage
) {
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
                        text = context.getString(R.string.dash_network),
                        style = TextStyle(
                            color = ColorProvider(theme.fontsHeadings),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(10.dp))
            }

            // Row 1: Mobile / Wi-Fi
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricCard(
                    title = context.getString(R.string.data_widget_mobile_label),
                    value = formatBytesMbGb(usage.usedBytes),
                    subtitle = if (usage.hasCap)
                        context.getString(
                            R.string.dash_tile_used_of,
                            formatBytesMbGb(usage.usedBytes),
                            formatBytesMbGb(usage.capBytes)
                        )
                    else context.getString(R.string.data_widget_no_plan_short),
                    fraction = if (usage.hasCap) usage.fraction else 0f,
                    barColor = barColorFor(theme, usage),
                    theme = theme,
                    tabHint = "DASHBOARD",
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    title = context.getString(R.string.data_widget_wifi_label),
                    value = formatBytesMbGb(usage.wifiBytes),
                    subtitle = context.getString(R.string.data_widget_wifi_subtitle),
                    fraction = 0.6f,
                    barColor = theme.accentLavender,
                    theme = theme,
                    tabHint = "DASHBOARD",
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            // Row 2: Remaining / Plan
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricCard(
                    title = context.getString(R.string.data_widget_remaining_label),
                    value = if (usage.hasCap)
                        formatBytesMbGb(usage.remainingBytes)
                    else "—",
                    subtitle = if (usage.overCap)
                        context.getString(R.string.data_widget_over)
                    else context.getString(R.string.data_widget_remaining_subtitle),
                    fraction = if (usage.hasCap)
                        (1f - usage.fraction).coerceIn(0f, 1f)
                    else 0f,
                    barColor = if (usage.overCap) theme.consoleError else theme.accentGreen,
                    theme = theme,
                    tabHint = "DASHBOARD",
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                MetricCard(
                    title = context.getString(R.string.data_widget_plan_label),
                    value = if (usage.hasCap)
                        "${(usage.fraction * 100).toInt()}%"
                    else "—",
                    subtitle = if (usage.hasCap)
                        context.getString(
                            R.string.data_widget_of_cap,
                            formatBytesMbGb(usage.capBytes)
                        )
                    else context.getString(R.string.data_widget_no_plan_short),
                    fraction = usage.fraction,
                    barColor = barColorFor(theme, usage),
                    theme = theme,
                    tabHint = "DASHBOARD",
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

/**
 * Same visual signature as DashboardWidget's MetricCard — identical
 * paddings, fonts, and 10-segment bar. Ensures the two widgets feel
 * like siblings on the home screen.
 */
@Composable
private fun MetricCard(
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
    // Same 10-segment bar as DashboardWidget's ProgressBar.
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

private fun barColorFor(theme: ThemeSpec, usage: DataPlanUsage): Color = when {
    usage.overCap -> theme.consoleError
    usage.fraction >= 0.8f -> theme.consoleWarning
    else -> theme.accentGreen
}