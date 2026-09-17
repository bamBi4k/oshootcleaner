package io.github.bambi4k.oshootcleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsTab(
    theme: ThemeSpec,
    onModeChange: (ThemeMode) -> Unit,
    onFlavorChange: (ThemeSpec) -> Unit
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(LanguageStore.get(context)) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var autoCleanEnabled by remember { mutableStateOf(AutoCleanStore.isEnabled(context)) }
    var showWidgetHelp by remember { mutableStateOf(false) }
    OnResumeEffect {
        autoCleanEnabled = AutoCleanStore.isEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(20.dp))

        // =============================================================
        // Appearance section header with info bubble
        // =============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.settings_appearance), theme)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(theme.bgCrust)
                    .clickable { showWidgetHelp = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "i",
                    color = theme.fontsSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Mode toggle (Day / Dark)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.bgCrust)
                .padding(4.dp)
        ) {
            ModeSegment(
                stringResource(R.string.settings_mode_day),
                theme.mode == ThemeMode.DAY, theme, Modifier.weight(1f)
            ) { onModeChange(ThemeMode.DAY) }
            ModeSegment(
                stringResource(R.string.settings_mode_dark),
                theme.mode == ThemeMode.DARK, theme, Modifier.weight(1f)
            ) { onModeChange(ThemeMode.DARK) }
        }

        Spacer(Modifier.height(12.dp))

        // Theme cards
        val alphaUnlocked = AlphaUnlockStore.isUnlocked(context)
        val flavors = if (theme.mode == ThemeMode.DARK) {
            ThemeCatalog.darkFlavors.filter { flavor ->
                flavor.id != AlphaUnlockStore.THEME_ID || alphaUnlocked
            }
        } else {
            ThemeCatalog.lightFlavors
        }
        flavors.forEach { flavor ->
            FlavorCard(flavor, isSelected = flavor.id == theme.id) { onFlavorChange(flavor) }
            Spacer(Modifier.height(10.dp))
        }

        // =============================================================
        // Language section
        // =============================================================
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_language), theme)
        Spacer(Modifier.height(8.dp))

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.bgSurface)
                    .clickable { languageMenuOpen = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_language),
                    color = theme.fontsPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                val current = LanguageStore.choices
                    .firstOrNull { it.tag == language }?.displayName
                    ?: stringResource(R.string.settings_language_system)
                Text(current, color = theme.fontsSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = theme.fontsSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = languageMenuOpen,
                onDismissRequest = { languageMenuOpen = false },
                modifier = Modifier.background(theme.bgSurface)
            ) {
                LanguageStore.choices.forEach { choice ->
                    val displayName = if (choice.tag == LanguageStore.SYSTEM)
                        stringResource(R.string.settings_language_system)
                    else choice.displayName

                    DropdownMenuItem(
                        text = {
                            Text(
                                displayName,
                                color = if (choice.tag == language)
                                    theme.buttonPrimaryBg else theme.fontsPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (choice.tag == language)
                                    FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            languageMenuOpen = false
                            if (choice.tag != language) {
                                language = choice.tag
                                LanguageStore.set(context, choice.tag)
                                // Widget refresh is handled by MainActivity
                                // on next create, so it doesn't race with
                                // recreate().
                                (context as? android.app.Activity)?.recreate()
                            }
                        }
                    )
                }
            }
        }

        // =============================================================
        // Auto-clean section
        // =============================================================
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.auto_clean_title), theme)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.auto_clean_desc),
            color = theme.fontsSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            label = stringResource(R.string.auto_clean_title),
            description = stringResource(
                if (autoCleanEnabled) R.string.auto_clean_enabled
                else R.string.auto_clean_disabled
            ),
            checked = autoCleanEnabled,
            enabled = true,
            theme = theme
        ) {
            autoCleanEnabled = it
            AutoCleanStore.setEnabled(context, it)
        }

        // =============================================================
        // About section with hidden easter egg
        // =============================================================
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_about), theme)
        Spacer(Modifier.height(8.dp))

        var tapCount by remember { mutableStateOf(0) }
        var showEasterEgg by remember { mutableStateOf(false) }
        var showHint by remember { mutableStateOf(false) }

        LaunchedEffect(tapCount) {
            if (tapCount in 1..9) {
                delay(3000)
                tapCount = 0
                showHint = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.bgSurface)
                .clickable {
                    tapCount++
                    when {
                        tapCount >= 10 -> {
                            val vibrator = context.getSystemService(
                                android.content.Context.VIBRATOR_SERVICE
                            ) as android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(
                                    android.os.VibrationEffect.createOneShot(
                                        60L, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                    )
                                )
                            }
                            showEasterEgg = true
                            tapCount = 0
                            showHint = false
                        }
                        tapCount >= 5 -> showHint = true
                    }
                }
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.settings_about_name),
                color = theme.fontsHeadings,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_about_desc),
                color = theme.fontsSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_about_author),
                color = theme.fontsSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (showHint && tapCount in 5..9) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_about_hint, tapCount),
                    color = theme.fontsLinks,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showEasterEgg) {
            EasterEggDialog(
                theme = theme,
                onDismiss = { showEasterEgg = false },
                onApplyFounderTheme = {
                    onFlavorChange(ThemeCatalog.alphaFounder)
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // =============================================================
    // Widget help dialog — rendered outside the Column so it
    // overlays correctly.
    // =============================================================
    if (showWidgetHelp) {
        AlertDialog(
            onDismissRequest = { showWidgetHelp = false },
            title = {
                Text(
                    stringResource(R.string.widget_help_title),
                    color = theme.fontsHeadings,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.widget_help_body),
                    color = theme.fontsPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showWidgetHelp = false }) {
                    Text(
                        stringResource(R.string.info_got_it),
                        color = theme.buttonPrimaryBg,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = theme.bgSurface,
            titleContentColor = theme.fontsHeadings,
            textContentColor = theme.fontsPrimary
        )
    }
}

@Composable
private fun ModeSegment(
    label: String,
    selected: Boolean,
    theme: ThemeSpec,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.buttonPrimaryBg else theme.bgCrust)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) theme.buttonPrimaryText else theme.fontsSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun FlavorCard(
    flavor: ThemeSpec,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(flavor.bgBase)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = flavor.buttonPrimaryBg,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Preview
        Column(
            modifier = Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(flavor.bgSurface)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(flavor.fontsHeadings)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(flavor.fontsSecondary)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(flavor.buttonPrimaryBg)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    flavor.displayName,
                    color = flavor.fontsPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (flavor.id == AlphaUnlockStore.THEME_ID) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(flavor.buttonPrimaryBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "FOUNDER",
                            color = flavor.buttonPrimaryText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (flavor.mode == ThemeMode.DAY) R.string.settings_light
                    else R.string.settings_dark
                ),
                color = flavor.fontsSecondary,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(flavor.buttonPrimaryBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    color = flavor.buttonPrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}