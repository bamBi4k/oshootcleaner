package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * Soft bevel used by all non-VGUI themes.
 */
fun Modifier.bevel(theme: ThemeSpec): Modifier =
    this.background(
        Brush.verticalGradient(
            colors = listOf(
                theme.bevelHighlight.copy(alpha = 0.08f),
                Color.Transparent,
                theme.bevelShadow.copy(alpha = 0.10f)
            )
        )
    )

/**
 * Outer 1px border used by all non-VGUI themes.
 */
fun Modifier.borderOnly(theme: ThemeSpec, shape: RoundedCornerShape): Modifier =
    this.border(1.dp, theme.bevelBorder, shape)

/**
 * VGUI OUTSET border — the classic 3D "raised" look.
 *
 * Matches this CSS from guh.txt:
 *   border-color: var(--border-light) var(--border-dark)
 *                 var(--border-dark)  var(--border-light);
 *
 * Order: top, right, bottom, left.
 * So: top & left = #8C9284, right & bottom = #292C21.
 */
fun Modifier.vguiOutset(theme: ThemeSpec): Modifier = this
    .drawWithContent {
        drawContent()

        val w = size.width
        val h = size.height
        val light = theme.bevelHighlight  // #8C9284
        val dark = theme.bevelShadow      // #292C21

        // Top — LIGHT
        drawLine(light, Offset(0f, 0f), Offset(w, 0f), 1f)
        // Left — LIGHT
        drawLine(light, Offset(0f, 0f), Offset(0f, h), 1f)
        // Right — DARK
        drawLine(dark, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
        // Bottom — DARK
        drawLine(dark, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
    }

/**
 * VGUI INSET border — the "pressed" or "content well" look.
 *
 * Matches this CSS from guh.txt:
 *   border-color: var(--border-dark) var(--border-light)
 *                 var(--border-light) var(--border-dark);
 *
 * Order: top, right, bottom, left.
 * So: top & left = #292C21, right & bottom = #8C9284.
 */
fun Modifier.vguiInset(theme: ThemeSpec): Modifier = this
    .drawWithContent {
        drawContent()

        val w = size.width
        val h = size.height
        val light = theme.bevelHighlight  // #8C9284
        val dark = theme.bevelShadow      // #292C21

        // Top — DARK
        drawLine(dark, Offset(0f, 0f), Offset(w, 0f), 1f)
        // Left — DARK
        drawLine(dark, Offset(0f, 0f), Offset(0f, h), 1f)
        // Right — LIGHT
        drawLine(light, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
        // Bottom — LIGHT
        drawLine(light, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
    }

/**
 * Theme-aware bevel dispatcher — always uses OUTSET for cards/buttons.
 */
fun ThemeSpec.themedBevel(shape: RoundedCornerShape): Modifier =
    if (id == "vgui") Modifier.vguiOutset(this)
    else Modifier.borderOnly(this, shape)

/**
 * Theme-aware surface brush.
 * VGUI uses a flat fill — no gradient.
 */
fun ThemeSpec.surfaceBrush(): Brush = SolidColor(bgSurface)

/**
 * Theme-aware inset for content wells (console, log, unavailability notes).
 */
fun ThemeSpec.insetBevel(): Modifier =
    if (id == "vgui") Modifier.vguiInset(this)
    else Modifier