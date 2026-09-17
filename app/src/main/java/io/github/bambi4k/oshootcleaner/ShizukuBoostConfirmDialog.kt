package io.github.bambi4k.oshootcleaner

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Persists the user's choice to skip the "Enhanced Boost" confirmation
 * dialog on future runs.
 */
object ShizukuBoostPrefs {
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_DONT_ASK = "shizuku_boost_dont_ask"

    fun shouldSkipConfirmation(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONT_ASK, false)

    fun setSkipConfirmation(context: Context, skip: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONT_ASK, skip)
            .apply()
    }
}

/**
 * Shown the first time the user taps the One-Tap Boost button while
 * Shizuku is connected. Explains what the enhanced path does, and lets
 * them opt out of the dialog in the future.
 *
 * @param onConfirm called with `true` if the user toggled "Don't ask
 *        again"; the caller persists that preference.
 * @param onCancel called when the user dismisses without proceeding.
 */
@Composable
fun ShizukuBoostConfirmDialog(
    theme: ThemeSpec,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var dontAskAgain by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.bgBase)
                .padding(24.dp)
        ) {
            Text(
                text = "Enhanced Boost",
                color = theme.fontsHeadings,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Shizuku is connected, so this boost will do more than the regular one:",
                color = theme.fontsPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(10.dp))

            Bullet("Force-stop background apps so they restart fresh", theme)
            Bullet("Takes about 12 seconds for 30 apps", theme)

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Your data and logins are not touched — only caches. You can turn this off any time by stopping Shizuku in its own app.",
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = dontAskAgain,
                    onCheckedChange = { dontAskAgain = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.buttonPrimaryText,
                        checkedTrackColor = theme.buttonPrimaryBg,
                        uncheckedThumbColor = theme.fontsSecondary,
                        uncheckedTrackColor = theme.bgCrust
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Don't ask again",
                    color = theme.fontsPrimary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = "Cancel",
                        color = theme.fontsSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    // Pass the current switch state to the caller. The
                    // caller persists it and runs the boost.
                    onConfirm(dontAskAgain)
                }) {
                    Text(
                        text = "Boost",
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
private fun Bullet(text: String, theme: ThemeSpec) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "•",
            color = theme.buttonPrimaryBg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = theme.fontsPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}