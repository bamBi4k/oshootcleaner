package io.github.bambi4k.oshootcleaner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun EasterEggDialog(
    theme: ThemeSpec,
    onDismiss: () -> Unit,
    onApplyFounderTheme: () -> Unit
) {
    val context = LocalContext.current

    // Was the egg already unlocked before this dialog appeared?
    // Remembered once, so the "first discovery" UI doesn't flash mid-dialog.
    val justUnlocked = remember { !AlphaUnlockStore.isUnlocked(context) }

    // On first-ever appearance, record the unlock immediately.
    LaunchedEffect(Unit) {
        if (justUnlocked) {
            AlphaUnlockStore.unlock(context)
        }
    }

    val infinite = rememberInfiniteTransition(label = "ee")
    val bounce by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 1200, easing = LinearEasing))
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(theme.bgSurface, theme.bgBase)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✨",
                    fontSize = 56.sp,
                    modifier = Modifier
                        .scale(bounce)
                        .alpha(reveal.value)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.easter_egg_title),
                    color = theme.fontsHeadings,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(reveal.value)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.easter_egg_body),
                    color = theme.fontsPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(reveal.value)
                )

                if (justUnlocked) {
                    Spacer(Modifier.height(20.dp))

                    // Gold-bordered unlock notice
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.buttonPrimaryBg.copy(alpha = 0.15f))
                            .padding(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.easter_egg_unlock_title),
                                color = theme.buttonPrimaryBg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.alpha(reveal.value)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.easter_egg_unlock_body),
                                color = theme.fontsPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.alpha(reveal.value)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.easter_egg_signature),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(reveal.value)
                )

                Spacer(Modifier.height(24.dp))

                if (justUnlocked) {
                    TextButton(onClick = {
                        AlphaUnlockStore.unlock(context)
                        onApplyFounderTheme()
                        onDismiss()
                    }) {
                        Text(
                            stringResource(R.string.easter_egg_apply_theme),
                            color = theme.buttonPrimaryBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.easter_egg_close),
                        color = theme.fontsSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (justUnlocked) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}