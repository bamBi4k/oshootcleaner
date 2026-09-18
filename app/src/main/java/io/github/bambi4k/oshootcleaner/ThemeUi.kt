package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// NOTE: bevel(), borderOnly(), vguiBevel(), themedBevel(), and
// surfaceBrush() now live in Bevel.kt. Do not redeclare them here.

@Composable
fun SectionLabel(text: String, theme: ThemeSpec) {
    val isVgui = theme.id == "vgui"
    Text(
        text = text.uppercase(),
        color = theme.fontsSecondary,
        fontSize = if (isVgui) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (isVgui) 1.6.sp else 1.2.sp,
        fontFamily = if (isVgui) FontFamily.Monospace else FontFamily.Default
    )
}

@Composable
fun StatCard(
    title: String,
    valueText: String,
    subtitle: String,
    fraction: Float,
    theme: ThemeSpec,
    barColor: Color = theme.buttonPrimaryBg,
    onClick: (() -> Unit)? = null,
    infoTitleRes: Int? = null,
    infoBodyRes: Int? = null
) {
    val shape = theme.cornerShape(16)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(shape))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp)
    ) {
        // ---- Title row: title | info glyph | value | chevron ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (infoTitleRes != null && infoBodyRes != null) {
                InfoGlyph(
                    titleRes = infoTitleRes,
                    bodyRes = infoBodyRes,
                    theme = theme
                )
            }

            Spacer(Modifier.width(8.dp))
            Text(
                valueText,
                color = theme.fontsHeadings,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "›",
                    color = theme.fontsSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Progress bar ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(theme.bgCrust)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.75f), barColor)
                        )
                    )
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- Subtitle ----
        Text(
            subtitle,
            color = theme.fontsSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    theme: ThemeSpec,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = theme.cornerShape(14)
    val isVgui = theme.id == "vgui"

    val bgColor = when {
        !enabled -> theme.buttonDisabledBg
        isVgui -> theme.buttonPrimaryBg      // #615820 accent-dim
        else -> theme.buttonPrimaryBg
    }
    val textColor = when {
        !enabled -> theme.buttonDisabledText
        isVgui -> theme.buttonPrimaryText    // #C4B550 accent
        else -> theme.buttonPrimaryText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(if (isVgui) Modifier.vguiOutset(theme) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (isVgui) 10.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Normal,
            fontSize = if (isVgui) 12.sp else 15.sp,
            letterSpacing = if (isVgui) 0.4.sp else 0.3.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    theme: ThemeSpec,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = theme.cornerShape(14)
    val isVgui = theme.id == "vgui"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.buttonBg)      // --bg #4A5942
            .then(
                if (isVgui) Modifier.vguiOutset(theme)
                else Modifier.border(1.dp, theme.bevelBorder, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (isVgui) 10.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) theme.fontsPrimary else theme.fontsSecondary.copy(alpha = 0.5f),
            fontWeight = FontWeight.Normal,
            fontSize = if (isVgui) 12.sp else 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ActionRow(label: String, theme: ThemeSpec, onClick: () -> Unit) {
    val shape = theme.cornerShape(12)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = theme.fontsPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text("→", color = theme.buttonPrimaryBg, fontSize = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    theme: ThemeSpec,
    onToggle: (Boolean) -> Unit
) {
    val shape = theme.cornerShape(12)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(shape))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) theme.fontsPrimary else theme.fontsSecondary.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
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
    Spacer(Modifier.height(8.dp))
}