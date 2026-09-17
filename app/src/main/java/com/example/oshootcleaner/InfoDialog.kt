package com.example.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun InfoGlyph(
    titleRes: Int,
    bodyRes: Int,
    theme: ThemeSpec
) {
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { open = true }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "ⓘ",
            color = theme.fontsSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }

    if (open) {
        InfoDialog(
            titleRes = titleRes,
            bodyRes = bodyRes,
            theme = theme,
            onDismiss = { open = false }
        )
    }
}

// InfoDialog composable stays as before

@Composable
private fun InfoDialog(
    titleRes: Int,
    bodyRes: Int,
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.bgBase)
                .padding(20.dp)
        ) {
            Text(
                stringResource(titleRes),
                color = theme.fontsHeadings,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(bodyRes),
                color = theme.fontsPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.info_got_it),
                        color = theme.buttonPrimaryBg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}