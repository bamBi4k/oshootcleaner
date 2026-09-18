package io.github.bambi4k.oshootcleaner

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ThemeMode { DAY, DARK }

data class ThemeSpec(
    val id: String,
    val displayName: String,
    val mode: ThemeMode,

    val fontsPrimary: Color,
    val fontsSecondary: Color,
    val fontsHeadings: Color,
    val fontsLinks: Color,
    val fontsCode: Color,

    val bgBase: Color,
    val bgMantle: Color,
    val bgCrust: Color,
    val bgSurface: Color,
    val bgOverlay: Color,

    val buttonBg: Color,
    val buttonText: Color,
    val buttonPrimaryBg: Color,
    val buttonPrimaryText: Color,
    val buttonHover: Color,
    val buttonActive: Color,
    val buttonDisabledBg: Color,
    val buttonDisabledText: Color,

    val bevelHighlight: Color,
    val bevelShadow: Color,
    val bevelBorder: Color,

    val consoleBg: Color,
    val consoleText: Color,
    val consolePrompt: Color,
    val consoleError: Color,
    val consoleWarning: Color,
    val consoleSuccess: Color,
    val consoleInfo: Color,

    val accentMauve: Color,
    val accentPink: Color,
    val accentPeach: Color,
    val accentYellow: Color,
    val accentGreen: Color,
    val accentTeal: Color,
    val accentSky: Color,
    val accentLavender: Color
)

object ThemeCatalog {

    val latte = ThemeSpec(
        id = "latte", displayName = "Latte", mode = ThemeMode.DAY,
        fontsPrimary = Color(0xFF4C4F69), fontsSecondary = Color(0xFF6C6F85),
        fontsHeadings = Color(0xFF3B3D52), fontsLinks = Color(0xFF1E66F5), fontsCode = Color(0xFFD20F39),
        bgBase = Color(0xFFEFF1F5), bgMantle = Color(0xFFE6E9EF), bgCrust = Color(0xFFDCE0E8),
        bgSurface = Color(0xFFCCD0DA), bgOverlay = Color(0xFFBCC0CC),
        buttonBg = Color(0xFFACB0BE), buttonText = Color(0xFF4C4F69),
        buttonPrimaryBg = Color(0xFF1E66F5), buttonPrimaryText = Color(0xFFEFF1F5),
        buttonHover = Color(0xFF7287FD), buttonActive = Color(0xFF04A5E5),
        buttonDisabledBg = Color(0xFFCCD0DA), buttonDisabledText = Color(0xFF9CA0B0),
        bevelHighlight = Color(0xFFFFFFFF), bevelShadow = Color(0xFFBCC0CC), bevelBorder = Color(0xFFACB0BE),
        consoleBg = Color(0xFFE6E9EF), consoleText = Color(0xFF4C4F69), consolePrompt = Color(0xFF40A02B),
        consoleError = Color(0xFFD20F39), consoleWarning = Color(0xFFDF8E1D),
        consoleSuccess = Color(0xFF40A02B), consoleInfo = Color(0xFF1E66F5),
        accentMauve = Color(0xFF8839EF), accentPink = Color(0xFFEA76CB), accentPeach = Color(0xFFFE640B),
        accentYellow = Color(0xFFDF8E1D), accentGreen = Color(0xFF40A02B), accentTeal = Color(0xFF179299),
        accentSky = Color(0xFF04A5E5), accentLavender = Color(0xFF7287FD)
    )

    val frappe = ThemeSpec(
        id = "frappe", displayName = "Matcha", mode = ThemeMode.DARK,
        fontsPrimary = Color(0xFFC6D0F5), fontsSecondary = Color(0xFFB5BFE2),
        fontsHeadings = Color(0xFFDCE2F7), fontsLinks = Color(0xFF8CAAEE), fontsCode = Color(0xFFE78284),
        bgBase = Color(0xFF303446), bgMantle = Color(0xFF292C3C), bgCrust = Color(0xFF232634),
        bgSurface = Color(0xFF414559), bgOverlay = Color(0xFF51576D),
        buttonBg = Color(0xFF51576D), buttonText = Color(0xFFC6D0F5),
        buttonPrimaryBg = Color(0xFFA6D189), buttonPrimaryText = Color(0xFF232634),
        buttonHover = Color(0xFF85C1DC), buttonActive = Color(0xFF99D1DB),
        buttonDisabledBg = Color(0xFF414559), buttonDisabledText = Color(0xFF737994),
        bevelHighlight = Color(0xFF626880), bevelShadow = Color(0xFF232634), bevelBorder = Color(0xFF51576D),
        consoleBg = Color(0xFF292C3C), consoleText = Color(0xFFC6D0F5), consolePrompt = Color(0xFFA6D189),
        consoleError = Color(0xFFE78284), consoleWarning = Color(0xFFE5C890),
        consoleSuccess = Color(0xFFA6D189), consoleInfo = Color(0xFF8CAAEE),
        accentMauve = Color(0xFFCA9EE6), accentPink = Color(0xFFF4B8E4), accentPeach = Color(0xFFEF9F76),
        accentYellow = Color(0xFFE5C890), accentGreen = Color(0xFFA6D189), accentTeal = Color(0xFF81C8BE),
        accentSky = Color(0xFF99D1DB), accentLavender = Color(0xFFBABBF1)
    )

    val macchiato = ThemeSpec(
        id = "macchiato", displayName = "Sakura", mode = ThemeMode.DARK,
        fontsPrimary = Color(0xFFCAD3F5), fontsSecondary = Color(0xFFB8C0E0),
        fontsHeadings = Color(0xFFE1E6FA), fontsLinks = Color(0xFF8AADF4), fontsCode = Color(0xFFED8796),
        bgBase = Color(0xFF24273A), bgMantle = Color(0xFF1E2030), bgCrust = Color(0xFF181926),
        bgSurface = Color(0xFF363A4F), bgOverlay = Color(0xFF494D64),
        buttonBg = Color(0xFF494D64), buttonText = Color(0xFFCAD3F5),
        buttonPrimaryBg = Color(0xFFF5BDE6), buttonPrimaryText = Color(0xFF181926),
        buttonHover = Color(0xFF7DC4E4), buttonActive = Color(0xFF91D7E3),
        buttonDisabledBg = Color(0xFF363A4F), buttonDisabledText = Color(0xFF6E738D),
        bevelHighlight = Color(0xFF5B6078), bevelShadow = Color(0xFF181926), bevelBorder = Color(0xFF494D64),
        consoleBg = Color(0xFF1E2030), consoleText = Color(0xFFCAD3F5), consolePrompt = Color(0xFFA6DA95),
        consoleError = Color(0xFFED8796), consoleWarning = Color(0xFFEED49F),
        consoleSuccess = Color(0xFFA6DA95), consoleInfo = Color(0xFF8AADF4),
        accentMauve = Color(0xFFC6A0F6), accentPink = Color(0xFFF5BDE6), accentPeach = Color(0xFFF5A97F),
        accentYellow = Color(0xFFEED49F), accentGreen = Color(0xFFA6DA95), accentTeal = Color(0xFF8BD5CA),
        accentSky = Color(0xFF91D7E3), accentLavender = Color(0xFFB7BDF8)
    )

    val mocha = ThemeSpec(
        id = "mocha", displayName = "Lavender", mode = ThemeMode.DARK,
        fontsPrimary = Color(0xFFCDD6F4), fontsSecondary = Color(0xFFBAC2DE),
        fontsHeadings = Color(0xFFE4E9FC), fontsLinks = Color(0xFF89B4FA), fontsCode = Color(0xFFF38BA8),
        bgBase = Color(0xFF1E1E2E), bgMantle = Color(0xFF181825), bgCrust = Color(0xFF11111B),
        bgSurface = Color(0xFF313244), bgOverlay = Color(0xFF45475A),
        buttonBg = Color(0xFF45475A), buttonText = Color(0xFFCDD6F4),
        buttonPrimaryBg = Color(0xFFCBA6F7), buttonPrimaryText = Color(0xFF11111B),
        buttonHover = Color(0xFF74C7EC), buttonActive = Color(0xFF89DCEB),
        buttonDisabledBg = Color(0xFF313244), buttonDisabledText = Color(0xFF6C7086),
        bevelHighlight = Color(0xFF585B70), bevelShadow = Color(0xFF11111B), bevelBorder = Color(0xFF45475A),
        consoleBg = Color(0xFF181825), consoleText = Color(0xFFCDD6F4), consolePrompt = Color(0xFFA6E3A1),
        consoleError = Color(0xFFF38BA8), consoleWarning = Color(0xFFF9E2AF),
        consoleSuccess = Color(0xFFA6E3A1), consoleInfo = Color(0xFF89B4FA),
        accentMauve = Color(0xFFCBA6F7), accentPink = Color(0xFFF5C2E7), accentPeach = Color(0xFFFAB387),
        accentYellow = Color(0xFFF9E2AF), accentGreen = Color(0xFFA6E3A1), accentTeal = Color(0xFF94E2D5),
        accentSky = Color(0xFF89DCEB), accentLavender = Color(0xFFB4BEFE)
    )

    val monoLight = ThemeSpec(
        id = "mono_light", displayName = "Mono", mode = ThemeMode.DAY,
        fontsPrimary = Color(0xFF1A1A1A), fontsSecondary = Color(0xFF5A5A5A),
        fontsHeadings = Color(0xFF000000), fontsLinks = Color(0xFF333333), fontsCode = Color(0xFF333333),
        bgBase = Color(0xFFFAFAFA), bgMantle = Color(0xFFF0F0F0), bgCrust = Color(0xFFE4E4E4),
        bgSurface = Color(0xFFFFFFFF), bgOverlay = Color(0xFFD0D0D0),
        buttonBg = Color(0xFFE0E0E0), buttonText = Color(0xFF1A1A1A),
        buttonPrimaryBg = Color(0xFF333333), buttonPrimaryText = Color(0xFFFFFFFF),
        buttonHover = Color(0xFF555555), buttonActive = Color(0xFF1A1A1A),
        buttonDisabledBg = Color(0xFFE8E8E8), buttonDisabledText = Color(0xFFAAAAAA),
        bevelHighlight = Color(0xFFFFFFFF), bevelShadow = Color(0xFFD0D0D0), bevelBorder = Color(0xFFC0C0C0),
        consoleBg = Color(0xFFF0F0F0), consoleText = Color(0xFF1A1A1A), consolePrompt = Color(0xFF333333),
        consoleError = Color(0xFF8B0000), consoleWarning = Color(0xFF7A5C00),
        consoleSuccess = Color(0xFF2F5C2F), consoleInfo = Color(0xFF333333),
        accentMauve = Color(0xFF666666), accentPink = Color(0xFF777777), accentPeach = Color(0xFF888888),
        accentYellow = Color(0xFF999999), accentGreen = Color(0xFF555555), accentTeal = Color(0xFF4D4D4D),
        accentSky = Color(0xFF5A5A5A), accentLavender = Color(0xFF707070)
    )

    val monoDark = ThemeSpec(
        id = "mono_dark", displayName = "Mono", mode = ThemeMode.DARK,
        fontsPrimary = Color(0xFFE8E8E8), fontsSecondary = Color(0xFF9A9A9A),
        fontsHeadings = Color(0xFFFFFFFF), fontsLinks = Color(0xFFD0D0D0), fontsCode = Color(0xFFB0B0B0),
        bgBase = Color(0xFF0A0A0A), bgMantle = Color(0xFF141414), bgCrust = Color(0xFF000000),
        bgSurface = Color(0xFF1A1A1A), bgOverlay = Color(0xFF2A2A2A),
        buttonBg = Color(0xFF2A2A2A), buttonText = Color(0xFFE8E8E8),
        buttonPrimaryBg = Color(0xFFE8E8E8), buttonPrimaryText = Color(0xFF0A0A0A),
        buttonHover = Color(0xFFCFCFCF), buttonActive = Color(0xFFFFFFFF),
        buttonDisabledBg = Color(0xFF1F1F1F), buttonDisabledText = Color(0xFF5A5A5A),
        bevelHighlight = Color(0xFF3A3A3A), bevelShadow = Color(0xFF000000), bevelBorder = Color(0xFF2E2E2E),
        consoleBg = Color(0xFF141414), consoleText = Color(0xFFE8E8E8), consolePrompt = Color(0xFFCFCFCF),
        consoleError = Color(0xFFFF6B6B), consoleWarning = Color(0xFFE8C878),
        consoleSuccess = Color(0xFF8FD98F), consoleInfo = Color(0xFFCFCFCF),
        accentMauve = Color(0xFFB8B8B8), accentPink = Color(0xFFC0C0C0), accentPeach = Color(0xFFAAAAAA),
        accentYellow = Color(0xFFD0D0D0), accentGreen = Color(0xFF909090), accentTeal = Color(0xFFA0A0A0),
        accentSky = Color(0xFFB0B0B0), accentLavender = Color(0xFFBFBFBF)
    )

    // ====================================================================
// VGUI — exact colors from guh.txt (Overlay Configurator stylesheet).
// ====================================================================
    val vgui = ThemeSpec(
        id = "vgui",
        displayName = "VGUI",
        mode = ThemeMode.DARK,

        // Fonts
        fontsPrimary   = Color(0xFFDEDFD6),  // --text
        fontsSecondary = Color(0xFFD8DED3),  // --secondary-text
        fontsHeadings  = Color(0xFFFFFFFF),  // .cfg-title-text uses #fff
        fontsLinks     = Color(0xFFC4B550),  // --accent
        fontsCode      = Color(0xFFC4B550),

        // Backgrounds
        bgBase    = Color(0xFF4A5942),  // --bg
        bgMantle  = Color(0xFF444F3C),
        bgCrust   = Color(0xFF3E4637),  // --secondary-bg
        bgSurface = Color(0xFF4A5942),  // --bg (same as base)
        bgOverlay = Color(0xFF5A6A50),  // --scrollbar-track

        // Buttons
        buttonBg           = Color(0xFF4A5942),
        buttonText         = Color(0xFFDEDFD6),
        buttonPrimaryBg    = Color(0xFF615820),  // --accent-dim
        buttonPrimaryText  = Color(0xFFC4B550),  // --accent
        buttonHover        = Color(0xFF958831),  // --secondary-accent
        buttonActive       = Color(0xFF3E4637),
        buttonDisabledBg   = Color(0xFF4A5942),
        buttonDisabledText = Color(0xFF292C21),  // --disabled-text

        // Bevels — the EXACT values from guh.txt
        bevelHighlight = Color(0xFF8C9284),  // --border-light
        bevelShadow    = Color(0xFF292C21),  // --border-dark
        bevelBorder    = Color(0xFF292C21),  // --border-dark

        // Console
        consoleBg      = Color(0xFF000000),  // .fw-code-block pre
        consoleText    = Color(0xFFD4D4D4),
        consolePrompt  = Color(0xFFDCDCAA),  // .pwsh-cmd
        consoleError   = Color(0xFFE05252),  // --red
        consoleWarning = Color(0xFFE8A838),  // .cfg-auth-warning
        consoleSuccess = Color(0xFF3DBA6B),  // --green
        consoleInfo    = Color(0xFFA0AA95),  // --text-3

        // Accents
        accentMauve    = Color(0xFFA0AA95),  // --text-3
        accentPink     = Color(0xFFC4B550),  // --accent
        accentPeach    = Color(0xFF958831),  // --secondary-accent
        accentYellow   = Color(0xFFC4B550),  // --accent
        accentGreen    = Color(0xFF3DBA6B),  // --green
        accentTeal     = Color(0xFF7F8C7F),  // --slider
        accentSky      = Color(0xFFA0AA95),  // --text-3
        accentLavender = Color(0xFF8C9284)   // --border-light
    )

    // ====================================================================
    // ALPHA FOUNDER — exclusive theme, unlocked only by finding the easter
    // egg. Do not remove from ThemeCatalog.all; the picker filters by
    // unlock state, not by absence.
    // ====================================================================
    val alphaFounder = ThemeSpec(
        id = "alpha_founder",
        displayName = "Alpha Founder",
        mode = ThemeMode.DARK,

        fontsPrimary = Color(0xFFF5E6D3),
        fontsSecondary = Color(0xFFB8A88F),
        fontsHeadings = Color(0xFFFFF5E1),
        fontsLinks = Color(0xFFFFB454),
        fontsCode = Color(0xFFFF8A65),

        bgBase = Color(0xFF14100B),
        bgMantle = Color(0xFF1C1712),
        bgCrust = Color(0xFF0B0805),
        bgSurface = Color(0xFF221B14),
        bgOverlay = Color(0xFF2D241A),

        buttonBg = Color(0xFF2D241A),
        buttonText = Color(0xFFF5E6D3),
        buttonPrimaryBg = Color(0xFFFFB454),
        buttonPrimaryText = Color(0xFF14100B),
        buttonHover = Color(0xFFFFC670),
        buttonActive = Color(0xFFFFD89C),
        buttonDisabledBg = Color(0xFF221B14),
        buttonDisabledText = Color(0xFF6E5F4A),

        bevelHighlight = Color(0xFF3E3020),
        bevelShadow = Color(0xFF0B0805),
        bevelBorder = Color(0xFF3A2E1F),

        consoleBg = Color(0xFF1C1712),
        consoleText = Color(0xFFF5E6D3),
        consolePrompt = Color(0xFFFFB454),
        consoleError = Color(0xFFFF6B6B),
        consoleWarning = Color(0xFFFFD54F),
        consoleSuccess = Color(0xFFB9F18A),
        consoleInfo = Color(0xFFFFB454),

        accentMauve = Color(0xFFD4A5E6),
        accentPink = Color(0xFFFF9EB8),
        accentPeach = Color(0xFFFFAB7B),
        accentYellow = Color(0xFFFFD54F),
        accentGreen = Color(0xFFB9F18A),
        accentTeal = Color(0xFF7BD9B9),
        accentSky = Color(0xFF7FC8E8),
        accentLavender = Color(0xFFC4B5FF)
    )

    // Order = display order in the picker.
    val darkFlavors = listOf(frappe, macchiato, mocha, monoDark,vgui, alphaFounder)
    val lightFlavors = listOf(latte, monoLight)
    val all = listOf(latte, monoLight, frappe, macchiato, mocha, monoDark,vgui, alphaFounder)
    val default = monoDark

    fun byId(id: String): ThemeSpec = all.find { it.id == id } ?: default
}

/** Persists theme preferences: Day/Dark mode + selected flavor in each mode. */
/** Persists theme preferences: Day/Dark mode + selected flavor in each mode. */
object ThemeStore {
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_DARK_FLAVOR = "dark_flavor_id"
    private const val KEY_LIGHT_FLAVOR = "light_flavor_id"

    /**
     * In-memory cache of the selected theme. Eliminates a SharedPreferences
     * read on every widget refresh and every tab composition. Invalidated
     * whenever the mode or flavor is written.
     */
    @Volatile
    private var cached: ThemeSpec? = null

    fun getSelectedTheme(context: Context): ThemeSpec {
        cached?.let { return it }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = ThemeMode.valueOf(
            prefs.getString(KEY_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        )
        val result = if (mode == ThemeMode.DAY) {
            val id = prefs.getString(KEY_LIGHT_FLAVOR, "latte") ?: "latte"
            ThemeCatalog.lightFlavors.find { it.id == id } ?: ThemeCatalog.latte
        } else {
            val id = prefs.getString(KEY_DARK_FLAVOR, ThemeCatalog.default.id)
                ?: ThemeCatalog.default.id
            ThemeCatalog.darkFlavors.find { it.id == id } ?: ThemeCatalog.default
        }
        cached = result
        return result
    }

    fun setMode(context: Context, mode: ThemeMode) {
        cached = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .commit()
    }

    fun setDarkFlavor(context: Context, theme: ThemeSpec) {
        cached = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DARK_FLAVOR, theme.id)
            .putString(KEY_MODE, ThemeMode.DARK.name)
            .commit()
    }

    fun setLightFlavor(context: Context, theme: ThemeSpec) {
        cached = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIGHT_FLAVOR, theme.id)
            .putString(KEY_MODE, ThemeMode.DAY.name)
            .commit()
    }
}