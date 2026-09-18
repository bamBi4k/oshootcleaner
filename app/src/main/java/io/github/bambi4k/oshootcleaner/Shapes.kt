package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Returns the corner shape for a given theme.
 *
 * VGUI uses square corners (radius 0) — every other theme keeps its
 * original rounded corner radius.
 *
 * Usage:
 *   .clip(theme.cornerShape(14))
 *   .clip(theme.cornerShape(12))
 *   .clip(theme.cornerShape(16))
 *
 * Passing the original radius keeps a single swap point in the UI code
 * so reverting is trivial — just change the call back to
 * RoundedCornerShape(N.dp).
 */
fun ThemeSpec.cornerShape(radiusDp: Int): RoundedCornerShape =
    if (id == "vgui") RoundedCornerShape(0.dp)
    else RoundedCornerShape(radiusDp.dp)