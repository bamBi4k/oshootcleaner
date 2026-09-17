package com.example.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val PLAN_PRESETS_GB = listOf(0L, 1L, 3L, 5L, 10L, 15L, 20L, 30L, 50L, 100L, 200L)

@Composable
fun DataPlanPickerDialog(
    theme: ThemeSpec,
    current: DataPlan,
    onDismiss: () -> Unit,
    onSave: (DataPlan) -> Unit
) {
    var capGb by remember {
        mutableStateOf(if (current.capBytes <= 0) 0L
        else current.capBytes / (1024L * 1024L * 1024L))
    }
    var cycleDay by remember { mutableStateOf(current.cycleStartDay) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.bgBase)
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.data_plan_title),
                color = theme.fontsHeadings,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.data_plan_subtitle),
                color = theme.fontsSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.data_plan_cap_label),
                color = theme.fontsPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))

            val rows = PLAN_PRESETS_GB.chunked(4)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { gb ->
                        val selected = capGb == gb
                        val label = if (gb == 0L)
                            stringResource(R.string.data_plan_unlimited)
                        else "$gb GB"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) theme.buttonPrimaryBg else theme.bgSurface
                                )
                                .border(
                                    if (selected) 0.dp else 1.dp,
                                    theme.bevelBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { capGb = gb }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) theme.buttonPrimaryText else theme.fontsPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.data_plan_cycle_label),
                color = theme.fontsPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DayStepper(
                    value = cycleDay,
                    onChange = { cycleDay = it.coerceIn(1, 28) },
                    theme = theme
                )
                Text(
                    stringResource(R.string.data_plan_cycle_hint),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = theme.fontsSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = {
                    val bytes = capGb * 1024L * 1024L * 1024L
                    onSave(DataPlan(capBytes = bytes, cycleStartDay = cycleDay))
                }) {
                    Text(
                        stringResource(R.string.action_save),
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
private fun DayStepper(value: Int, onChange: (Int) -> Unit, theme: ThemeSpec) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bgSurface)
            .border(1.dp, theme.bevelBorder, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onChange(value - 1) }) {
            Text("−", color = theme.fontsPrimary, fontSize = 16.sp)
        }
        Text(
            value.toString(),
            color = theme.fontsPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        TextButton(onClick = { onChange(value + 1) }) {
            Text("+", color = theme.fontsPrimary, fontSize = 16.sp)
        }
    }
}