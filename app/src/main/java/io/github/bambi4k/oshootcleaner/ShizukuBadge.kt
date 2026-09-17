package io.github.bambi4k.oshootcleaner

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShizukuBadgeConfig {
    // Ambient pulsation glow behind frame
    const val PULSE_DURATION_MS = 2200
    const val GLOW_MIN_ALPHA = 0.18f
    const val GLOW_MAX_ALPHA = 0.42f

    const val GLOW_SCALE_MIN = 1.00f
    const val GLOW_SCALE_MAX = 1.03f
    const val GLOW_RADIUS_DP = 16f

    // Press interaction
    const val PRESSED_SCALE = 0.98f

    // Visual dimensions
    const val BUTTON_RADIUS_DP = 16f
    const val BADGE_ICON_SIZE_DP = 13f
    const val BORDER_ALPHA = 0.30f
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Pulsating ambient glow color matching each theme's core signature.
 */
private fun getPulseGlowColor(theme: ThemeSpec): Color {
    return when (theme.id) {
        "latte" -> theme.accentLavender
        "frappe" -> theme.accentGreen
        "macchiato" -> theme.accentPink
        "mocha" -> theme.accentMauve
        "mono_light", "mono_dark" -> theme.fontsPrimary.copy(alpha = 0.50f)
        "alpha_founder" -> theme.accentPeach
        else -> theme.accentLavender
    }
}

/**
 * Clean, subtle badge background that sits harmoniously on `theme.buttonPrimaryBg`.
 * Uses dark tinted surfaces on filled buttons, or elevated surfaces with high readability.
 */
private fun getBadgeBackgroundColor(theme: ThemeSpec): Color {
    return when (theme.mode) {
        ThemeMode.DAY -> theme.bgBase.copy(alpha = 0.30f)
        ThemeMode.DARK -> theme.bgCrust.copy(alpha = 0.45f)
    }
}

/**
 * Primary accent color for the badge text and subtle rim border to give a premium pop.
 */
private fun getBadgeAccentColor(theme: ThemeSpec): Color {
    return when (theme.id) {
        "latte" -> theme.buttonPrimaryText
        "frappe" -> theme.accentGreen
        "macchiato" -> theme.accentPink
        "mocha" -> theme.accentMauve
        "mono_light" -> theme.buttonPrimaryText
        "mono_dark" -> theme.fontsPrimary
        "alpha_founder" -> theme.accentPeach
        else -> theme.buttonPrimaryText
    }
}

/**
 * Clean Button Container featuring a 360° ambient pulse and a refined, uniform Shizuku pill.
 */
@Composable
fun ShizukuBadgeFrame(
    enabled: Boolean,
    theme: ThemeSpec,
    pillLabel: String = "SHIZUKU",
    badgeIcon: ImageVector? = null,
    disabled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled) {
        Box(modifier = Modifier.fillMaxWidth()) { content() }
        return
    }

    val shape = RoundedCornerShape(ShizukuBadgeConfig.BUTTON_RADIUS_DP.dp)
    val hapticFeedback = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()

    var isPressed by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }

    // AMBIENT PULSE
    val infiniteTransition = rememberInfiniteTransition(label = "shizuku_badge_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = ShizukuBadgeConfig.GLOW_MIN_ALPHA,
        targetValue = ShizukuBadgeConfig.GLOW_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(ShizukuBadgeConfig.PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shizuku_pulse_alpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = ShizukuBadgeConfig.GLOW_SCALE_MIN,
        targetValue = ShizukuBadgeConfig.GLOW_SCALE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(ShizukuBadgeConfig.PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shizuku_pulse_scale"
    )

    // PRESS SCALE
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !disabled) ShizukuBadgeConfig.PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
        label = "shizuku_button_scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !disabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val glowAlpha = if (disabled || reducedMotion) 0f else pulseAlpha
    val glowColor = getPulseGlowColor(theme)

    // UNIFORM BADGE STYLING
    val badgeBg = if (disabled) theme.buttonDisabledBg else getBadgeBackgroundColor(theme)
    val badgeAccent = if (disabled) theme.buttonDisabledText else getBadgeAccentColor(theme)
    val badgeBorderColor = if (disabled) Color.Transparent else badgeAccent.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(enabled, disabled) {
                if (!enabled || disabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                            PointerEventType.Press -> isPressed = true
                            PointerEventType.Release -> isPressed = false
                        }
                    }
                }
            }
    ) {
        // 360° SURROUNDING AMBIENT PULSE GLOW
        if (!disabled && !reducedMotion) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(
                        scaleX = pressScale * pulseScale * 1.04f,
                        scaleY = pressScale * pulseScale * 1.18f
                    )
                    .clip(shape)
                    .background(glowColor.copy(alpha = glowAlpha))
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(
                                radius = ShizukuBadgeConfig.GLOW_RADIUS_DP.dp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                            )
                        } else Modifier
                    )
            )
        }

        // BUTTON SURFACE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pressScale)
                .clip(shape)
                .background(if (disabled) theme.buttonDisabledBg else theme.buttonPrimaryBg)
                .border(
                    width = 1.dp,
                    color = if (disabled) theme.bevelBorder.copy(alpha = 0.20f)
                    else theme.bevelBorder.copy(alpha = ShizukuBadgeConfig.BORDER_ALPHA),
                    shape = shape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 16.dp, end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Main Button Label / Custom Content
                content()

                // RIGHT-ALIGNED MINIMAL BADGE
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .border(
                                width = 1.dp,
                                color = badgeBorderColor,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (badgeIcon != null) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = badgeAccent,
                                    modifier = Modifier.size(ShizukuBadgeConfig.BADGE_ICON_SIZE_DP.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = pillLabel,
                                color = badgeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }
        }
    }
}