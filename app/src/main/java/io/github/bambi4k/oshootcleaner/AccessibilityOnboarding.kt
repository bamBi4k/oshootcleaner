package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Card shown in the Storage tab when the accessibility service is not
 * enabled. Explains the trade-off honestly and provides a one-tap way
 * to open the accessibility settings.
 */
@Composable
fun AccessibilityOnboarding(
    theme: ThemeSpec,
    onEnable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.bgSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(
                    id = if (theme.mode == ThemeMode.DARK)
                        R.drawable.ic_logo_white
                    else
                        R.drawable.ic_logo_black
                ),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.a11y_onboarding_title),
                    color = theme.fontsHeadings,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.a11y_onboarding_subtitle),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.a11y_onboarding_explain),
            color = theme.fontsSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(16.dp))

        // The critical action — full-width primary button so it can't be
        // mistaken for helper text.
        PrimaryButton(
            label = stringResource(R.string.a11y_onboarding_action),
            theme = theme,
            enabled = true,
            onClick = onEnable
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.a11y_onboarding_privacy),
            color = theme.fontsSecondary.copy(alpha = 0.7f),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}