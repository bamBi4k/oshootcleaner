package io.github.bambi4k.oshootcleaner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CacheCleanFlow(
    session: CacheCleanSession,
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(session.currentIndex, session.targets.map { it.status }) {
        if (session.isFinished) {
            CacheCleanSessionStore.clear(context)
        } else {
            CacheCleanSessionStore.save(context, session)
        }
    }

    var backFromSettings by remember { mutableStateOf(false) }
    OnResumeEffect {
        if (session.currentIndex in session.targets.indices) {
            backFromSettings = true
        }
    }

    Dialog(
        onDismissRequest = { /* block accidental dismissal */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.bgBase)
        ) {
            if (session.isFinished) {
                SessionCompleteView(session, theme, onDismiss)
            } else {
                CurrentTargetView(
                    session = session,
                    theme = theme,
                    backFromSettings = backFromSettings,
                    onOpenAppInfo = {
                        backFromSettings = false
                        openAppInfo(context, session.current?.packageName ?: return@CurrentTargetView)
                    },
                    onMarkCleared = {
                        backFromSettings = false
                        session.markCleared()
                    },
                    onMarkSkipped = {
                        backFromSettings = false
                        session.markSkipped()
                    },
                    onGoBack = {
                        backFromSettings = false
                        session.goBack()
                    },
                    onFinishEarly = {
                        session.finishEarly()
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrentTargetView(
    session: CacheCleanSession,
    theme: ThemeSpec,
    backFromSettings: Boolean,
    onOpenAppInfo: () -> Unit,
    onMarkCleared: () -> Unit,
    onMarkSkipped: () -> Unit,
    onGoBack: () -> Unit,
    onFinishEarly: () -> Unit
) {
    val target = session.current ?: return
    val progressFraction = if (session.targets.isEmpty()) 0f
    else session.currentIndex.toFloat() / session.targets.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))

        // ---- Header ----
        Text(
            stringResource(R.string.cache_clean_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fontsHeadings
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.cache_clean_step,
                session.currentIndex + 1,
                session.targets.size
            ),
            color = theme.fontsSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        // ---- Progress bar ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(theme.cornerShape(3))
                .background(theme.bgCrust)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .clip(theme.cornerShape(3))
                    .background(theme.buttonPrimaryBg)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ---- Current app card ----
        val cardShape = theme.cornerShape(20)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(theme.surfaceBrush())
                .then(theme.themedBevel(cardShape))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                target.label,
                color = theme.fontsHeadings,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                formatBytesSmart(target.cacheBytesAtStart),
                color = theme.buttonPrimaryBg,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cache_clean_of_cache),
                color = theme.fontsSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Instructions ----
        if (!backFromSettings) {
            InstructionStep(
                number = "1",
                text = stringResource(R.string.cache_clean_step1),
                theme = theme
            )
            Spacer(Modifier.height(8.dp))
            InstructionStep(
                number = "2",
                text = stringResource(R.string.cache_clean_step2),
                theme = theme
            )
            Spacer(Modifier.height(8.dp))
            InstructionStep(
                number = "3",
                text = stringResource(R.string.cache_clean_step3),
                theme = theme
            )
        } else {
            Text(
                stringResource(R.string.cache_clean_did_you_clear),
                color = theme.fontsPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Actions ----
        if (!backFromSettings) {
            PrimaryButton(
                label = stringResource(R.string.cache_clean_open_info),
                theme = theme,
                enabled = true,
                onClick = onOpenAppInfo
            )
        } else {
            PrimaryButton(
                label = stringResource(R.string.cache_clean_mark_cleared),
                theme = theme,
                enabled = true,
                onClick = onMarkCleared
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = stringResource(R.string.cache_clean_skip),
                        theme = theme,
                        onClick = onMarkSkipped
                    )
                }
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = stringResource(R.string.cache_clean_open_again),
                        theme = theme,
                        onClick = onOpenAppInfo
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Session controls ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (session.currentIndex > 0) {
                TextButton(onClick = onGoBack) {
                    Text(
                        stringResource(R.string.cache_clean_back),
                        color = theme.fontsSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }
            TextButton(onClick = onFinishEarly) {
                Text(
                    stringResource(R.string.cache_clean_finish_early),
                    color = theme.fontsSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InstructionStep(number: String, text: String, theme: ThemeSpec) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(theme.bgCrust),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = theme.fontsPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = theme.fontsSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SessionCompleteView(
    session: CacheCleanSession,
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.cache_clean_done_title),
            color = theme.fontsHeadings,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            formatBytesSmart(session.freedBytesAtStart),
            color = theme.buttonPrimaryBg,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.cache_clean_done_freed),
            color = theme.fontsSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        val summaryShape = theme.cornerShape(16)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(summaryShape)
                .background(theme.surfaceBrush())
                .then(theme.themedBevel(summaryShape))
                .padding(16.dp)
        ) {
            ResultRow(
                label = stringResource(R.string.cache_clean_done_cleared),
                value = session.clearedCount.toString(),
                theme = theme
            )
            Spacer(Modifier.height(6.dp))
            ResultRow(
                label = stringResource(R.string.cache_clean_done_skipped),
                value = session.skippedCount.toString(),
                theme = theme
            )
        }

        Spacer(Modifier.height(32.dp))

        PrimaryButton(
            label = stringResource(R.string.cache_clean_done_close),
            theme = theme,
            enabled = true,
            onClick = onDismiss
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, theme: ThemeSpec) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.fontsSecondary, fontSize = 13.sp)
        Text(value, color = theme.fontsPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(label: String, theme: ThemeSpec, onClick: () -> Unit) {
    val shape = theme.cornerShape(14)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceBrush())
            .then(theme.themedBevel(shape))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = theme.fontsPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Opens the system App Info screen for the given package. */
private fun openAppInfo(context: android.content.Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}