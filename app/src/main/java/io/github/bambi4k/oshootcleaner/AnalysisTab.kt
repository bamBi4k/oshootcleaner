package io.github.bambi4k.oshootcleaner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AnalysisTab(theme: ThemeSpec) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressLabel by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDeletion?>(null) }
    var previewFile by remember { mutableStateOf<BigFile?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var sortMode by remember { mutableStateOf(AnalysisPrefs.sort(context)) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    var lastScanMs by remember { mutableStateOf(AnalysisPrefs.lastScanMs(context)) }
    var isStale by remember { mutableStateOf(AnalysisPrefs.isStale(context)) }

    var deleteToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deleteToast) {
        if (deleteToast != null) {
            kotlinx.coroutines.delay(2200)
            deleteToast = null
        }
    }

    var dupeScope by remember { mutableStateOf(DuplicateScope.load(context)) }
    var scopePickerOpen by remember { mutableStateOf(false) }

    var selectedUris by remember { mutableStateOf(setOf<Uri>()) }

    OnResumeEffect {
        isStale = AnalysisPrefs.isStale(context)
    }

    fun toggleSelection(uri: Uri) {
        selectedUris = if (selectedUris.contains(uri)) selectedUris - uri else selectedUris + uri
    }

    fun clearSelection() { selectedUris = emptySet() }

    fun selectedFiles(): List<BigFile> {
        val r = result ?: return emptyList()
        val allBig = r.bigFiles
        val allDupes = r.duplicateGroups.flatMap { it.files }
        return (allBig + allDupes).filter { selectedUris.contains(it.uri) }.distinctBy { it.uri }
    }

    fun runScan() {
        val scopeSnapshot = dupeScope
        isScanning = true
        progress = 0f
        clearSelection()
        ScanService.start(context)
        scope.launch {
            try {
                val newResult = withContext(Dispatchers.IO) {
                    scanEverything(context, scopeSnapshot) { p, label ->
                        progress = p
                        progressLabel = label
                        ScanService.update(context, p, label)
                    }
                }
                result = newResult
                AnalysisPrefs.setLastScanNow(context)
                lastScanMs = System.currentTimeMillis()
                isStale = false
            } finally {
                isScanning = false
                ScanService.stop(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.analysis_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.fontsHeadings
                )
                Text(
                    lastScanLabel(lastScanMs),
                    color = theme.fontsSecondary,
                    fontSize = 11.sp
                )
            }
            if (result != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextToggleButton(
                        label = stringResource(R.string.analysis_btn_search),
                        active = searchOpen,
                        theme = theme
                    ) {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }
                    TextToggleButton(
                        label = stringResource(R.string.analysis_btn_sort),
                        active = sortMenuOpen,
                        theme = theme
                    ) {
                        sortMenuOpen = !sortMenuOpen
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.analysis_search_hint),
                        color = theme.fontsSecondary,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.fontsPrimary,
                    unfocusedTextColor = theme.fontsPrimary,
                    focusedBorderColor = theme.buttonPrimaryBg,
                    unfocusedBorderColor = theme.bevelBorder,
                    cursorColor = theme.buttonPrimaryBg,
                    focusedContainerColor = theme.bgSurface,
                    unfocusedContainerColor = theme.bgSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        AnimatedVisibility(visible = sortMenuOpen, enter = fadeIn(), exit = fadeOut()) {
            SortMenu(
                current = sortMode,
                theme = theme,
                onSelect = {
                    sortMode = it
                    AnalysisPrefs.setSort(context, it)
                    sortMenuOpen = false
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (isStale && result != null && !isScanning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.consoleWarning.copy(alpha = 0.18f))
                    .border(1.dp, theme.consoleWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.analysis_stale_message),
                    color = theme.fontsPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { runScan() }) {
                    Text(
                        stringResource(R.string.analysis_stale_action),
                        color = theme.buttonPrimaryBg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (selectedUris.isNotEmpty()) {
            SelectionActionBar(
                count = selectedUris.size,
                theme = theme,
                onDelete = {
                    val files = selectedFiles()
                    if (files.isNotEmpty()) {
                        pendingDelete = PendingDeletion.Multi(files)
                    }
                },
                onCancel = { clearSelection() }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (!isScanning && result == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.bgSurface)
                    .clickable { scopePickerOpen = !scopePickerOpen }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.analysis_scope_title),
                    color = theme.fontsPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    scopeSummary(dupeScope),
                    color = theme.fontsSecondary,
                    fontSize = 12.sp
                )
            }

            if (scopePickerOpen) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.bgSurface)
                        .padding(8.dp)
                ) {
                    ScopeToggle(
                        stringResource(R.string.analysis_scope_images),
                        dupeScope.includeImages, theme
                    ) { v -> dupeScope = dupeScope.copy(includeImages = v); DuplicateScope.save(context, dupeScope) }
                    ScopeToggle(
                        stringResource(R.string.analysis_scope_videos),
                        dupeScope.includeVideos, theme
                    ) { v -> dupeScope = dupeScope.copy(includeVideos = v); DuplicateScope.save(context, dupeScope) }
                    ScopeToggle(
                        stringResource(R.string.analysis_scope_audio),
                        dupeScope.includeAudio, theme
                    ) { v -> dupeScope = dupeScope.copy(includeAudio = v); DuplicateScope.save(context, dupeScope) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ScopeToggle(
                            stringResource(R.string.analysis_scope_downloads),
                            dupeScope.includeDownloads, theme
                        ) { v -> dupeScope = dupeScope.copy(includeDownloads = v); DuplicateScope.save(context, dupeScope) }
                    }
                    MinSizeRow(dupeScope.minSizeBytes, theme) { v ->
                        dupeScope = dupeScope.copy(minSizeBytes = v); DuplicateScope.save(context, dupeScope)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            PrimaryButton(
                label = stringResource(R.string.analysis_start),
                theme = theme,
                enabled = true
            ) { runScan() }
        }

        if (isScanning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.bgSurface)
                    .padding(16.dp)
            ) {
                Text(progressLabel, color = theme.fontsSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(theme.bgCrust)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.buttonPrimaryBg)
                    )
                }
            }
        }

        result?.let { r ->
            val query = searchQuery
            val filteredBig = r.bigFiles
                .filter { AnalysisSorter.matchesSearch(it.name, query) }
                .let { AnalysisSorter.sortBigFiles(it, sortMode) }

            val filteredDupeGroups = r.duplicateGroups
                .map { g ->
                    if (query.isBlank()) {
                        g
                    } else {
                        // Keep the group's designated keeper (files[0])
                        // in the list even if the search doesn't match it.
                        // Otherwise deleting through a search filter would
                        // delete the original instead of the duplicate.
                        val keeper = g.files.firstOrNull()
                        val matching = g.files.filter { AnalysisSorter.matchesSearch(it.name, query) }
                        val combined = if (keeper != null && !matching.contains(keeper)) {
                            listOf(keeper) + matching
                        } else {
                            matching
                        }
                        g.copy(files = combined)
                    }
                }
                .filter { it.files.size >= 2 || (query.isNotBlank() && it.files.isNotEmpty()) }
                .let { AnalysisSorter.sortDuplicates(it, sortMode) }

            val filteredUnused = r.unusedApps.apps
                .filter { AnalysisSorter.matchesSearch(it.label, it.packageName, query) }
                .let { AnalysisSorter.sortUnusedApps(it, sortMode) }

            val filteredForeign = r.foreignCaches
                .filter { AnalysisSorter.matchesSearch(it.label, it.packageName, query) }

            val anyMatches = filteredBig.isNotEmpty() || filteredDupeGroups.isNotEmpty() ||
                    filteredUnused.isNotEmpty() || filteredForeign.isNotEmpty()

            if (query.isNotBlank() && !anyMatches) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.analysis_no_matches),
                    color = theme.fontsSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.bgSurface)
                        .padding(16.dp)
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    label = stringResource(R.string.analysis_rescan),
                    theme = theme,
                    enabled = true
                ) {
                    clearSelection()
                    result = null
                }
                Spacer(Modifier.height(32.dp))
                return@Column
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.analysis_own_cache), theme)
            Spacer(Modifier.height(8.dp))
            AnalyzerRow(
                title = stringResource(R.string.analysis_app_cache),
                subtitle = stringResource(
                    R.string.analysis_files_count,
                    r.cache.totalFiles,
                    formatBytesSmart(r.cache.totalBytes)
                ),
                actionLabel = if (r.cache.totalBytes > 0)
                    stringResource(R.string.analysis_action_delete) else null,
                theme = theme,
                selected = false,
                selectionMode = selectedUris.isNotEmpty(),
                onClick = {},
                onLongClick = {},
                onAction = { pendingDelete = PendingDeletion.OwnCache(r.cache.totalBytes) }
            )

            if (filteredForeign.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.analysis_foreign_caches), theme)
                Spacer(Modifier.height(8.dp))
                filteredForeign.forEach { fc ->
                    AnalyzerRow(
                        title = fc.label,
                        subtitle = formatBytesSmart(fc.cacheBytes),
                        actionLabel = stringResource(R.string.analysis_action_open_info),
                        theme = theme,
                        selected = false,
                        selectionMode = false,
                        onClick = {},
                        onLongClick = {},
                        onAction = {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${fc.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
            }

            if (filteredDupeGroups.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.analysis_duplicates), theme)
                Spacer(Modifier.height(8.dp))

                val totalWasted = filteredDupeGroups.sumOf { it.wastedBytes }
                val totalDupes = filteredDupeGroups.sumOf { it.files.size - 1 }

                AnalyzerRow(
                    title = stringResource(
                        R.string.analysis_dupes_summary,
                        totalDupes,
                        filteredDupeGroups.size
                    ),
                    subtitle = stringResource(
                        R.string.analysis_dupes_wasted,
                        formatBytesSmart(totalWasted)
                    ),
                    actionLabel = if (totalWasted > 0)
                        stringResource(R.string.analysis_action_clean) else null,
                    theme = theme,
                    selected = false,
                    selectionMode = false,
                    onClick = {},
                    onLongClick = {},
                    onAction = {
                        pendingDelete = PendingDeletion.Duplicates(
                            groups = filteredDupeGroups,
                            wastedBytes = totalWasted
                        )
                    }
                )

                filteredDupeGroups.take(10).forEach { group ->
                    group.files.take(4).forEachIndexed { idx, f ->
                        val isSelected = selectedUris.contains(f.uri)
                        val isKeeper = idx == 0
                        AnalyzerRow(
                            title = trimFileNameForDisplay(f.name),
                            subtitle = formatBytesSmart(f.sizeBytes),
                            location = f.location,
                            keepBadge = if (isKeeper) stringResource(R.string.analysis_dupes_keep_first) else null,
                            actionLabel = null,
                            thumbnailUri = f.uri,
                            isMedia = f.isMedia,
                            theme = theme,
                            selected = isSelected,
                            selectionMode = selectedUris.isNotEmpty(),
                            onClick = { if (selectedUris.isNotEmpty()) toggleSelection(f.uri) },
                            onLongClick = { toggleSelection(f.uri) },
                            onThumbnailClick = { if (selectedUris.isEmpty()) previewFile = f },
                            onAction = {}
                        )
                    }
                }
            }

            if (filteredBig.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.analysis_big_files), theme)
                Spacer(Modifier.height(8.dp))
                filteredBig.take(30).forEach { f ->
                    val isSelected = selectedUris.contains(f.uri)
                    var menuOpen by remember(f.uri) { mutableStateOf(false) }

                    AnalyzerRow(
                        title = trimFileNameForDisplay(f.name),
                        subtitle = formatBytesSmart(f.sizeBytes),
                        location = f.location,
                        actionLabel = if (selectedUris.isEmpty())
                            stringResource(R.string.analysis_action_delete) else null,
                        thumbnailUri = f.uri,
                        isMedia = f.isMedia,
                        theme = theme,
                        selected = isSelected,
                        selectionMode = selectedUris.isNotEmpty(),
                        onClick = { if (selectedUris.isNotEmpty()) toggleSelection(f.uri) },
                        onLongClick = { toggleSelection(f.uri) },
                        onThumbnailClick = { if (selectedUris.isEmpty()) previewFile = f },
                        onAction = { pendingDelete = PendingDeletion.SingleFile(f) },
                        onMenuClick = { menuOpen = true }
                    )

                    if (menuOpen) {
                        AnalyzerRowMenu(
                            onPreview = { previewFile = f },
                            onOpenInFiles = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(f.uri, "*/*")
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        }
                                    )
                                } catch (_: Exception) {
                                    CleanupManager.openDownloads(context)
                                }
                            },
                            onCopyPath = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("path", f.path))
                                Haptics.tap(context)
                            },
                            theme = theme,
                            onDismiss = { menuOpen = false }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.analysis_downloads), theme)
            Spacer(Modifier.height(8.dp))
            AnalyzerRow(
                title = stringResource(R.string.analysis_downloads_count, r.downloads.count),
                subtitle = formatBytesSmart(r.downloads.totalBytes),
                actionLabel = stringResource(R.string.analysis_action_open_folder),
                theme = theme,
                selected = false,
                selectionMode = false,
                onClick = {},
                onLongClick = {},
                onAction = { CleanupManager.openDownloads(context) }
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.analysis_unused_apps), theme)
            Spacer(Modifier.height(8.dp))

            when {
                r.unusedApps.insufficientHistory -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.bgSurface)
                            .padding(14.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.analysis_unused_truncated,
                                r.unusedApps.daysOfHistoryAvailable
                            ),
                            color = theme.fontsSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                filteredUnused.isEmpty() -> {
                    Text(
                        stringResource(R.string.analysis_unused_none),
                        color = theme.fontsSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.bgSurface)
                            .padding(14.dp)
                    )
                }
                else -> {
                    val now = System.currentTimeMillis()
                    val cutoff = now - 60L * 24 * 60 * 60 * 1000
                    filteredUnused.filter { it.lastUsedMs in 1L until cutoff }
                        .take(15)
                        .forEach { app ->
                            val daysAgo = ((now - app.lastUsedMs) / (24 * 60 * 60 * 1000L)).toInt()
                            AnalyzerRow(
                                title = app.label,
                                subtitle = stringResource(R.string.analysis_last_used_days, daysAgo),
                                actionLabel = stringResource(R.string.analysis_action_open_info),
                                theme = theme,
                                selected = false,
                                selectionMode = false,
                                onClick = {},
                                onLongClick = {},
                                onAction = {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            )
                        }
                }
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                label = stringResource(R.string.analysis_rescan),
                theme = theme,
                enabled = true
            ) {
                clearSelection()
                result = null
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    previewFile?.let { file ->
        FilePreviewDialog(
            file = file,
            theme = theme,
            onDismiss = { previewFile = null },
            onDelete = { pendingDelete = PendingDeletion.SingleFile(file) }
        )
    }

    pendingDelete?.let { pd ->
        val confirm = pd.toConfirm(context)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(confirm.title) },
            text = { Text(confirm.message) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val confirmResult = confirm.onConfirm()
                        val deleted = confirmResult.deletedUris
                        if (deleted.isNotEmpty()) {
                            Haptics.tap(context)

                            deleteToast = if (deleted.size == 1) {
                                context.getString(
                                    R.string.delete_toast_single,
                                    formatBytesSmart(confirmResult.freedBytes)
                                )
                            } else {
                                context.getString(
                                    R.string.delete_toast_multi,
                                    deleted.size,
                                    formatBytesSmart(confirmResult.freedBytes)
                                )
                            }

                            result = pruneDeleted(result, deleted)
                            clearSelection()
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = theme.consoleError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = theme.fontsSecondary
                    )
                }
            }
        )
    }

    deleteToast?.let { msg ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                msg,
                color = theme.buttonPrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.buttonPrimaryBg.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun resolveFreedBytes(context: Context, file: BigFile): Long {
    if (file.sizeBytes > 0L) return file.sizeBytes
    return try {
        context.contentResolver.openAssetFileDescriptor(file.uri, "r")?.use { fd ->
            fd.length.takeIf { it > 0 } ?: 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

@Composable
private fun TextToggleButton(label: String, active: Boolean, theme: ThemeSpec, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) theme.buttonPrimaryBg else theme.bgSurface)
            .border(1.dp, if (active) theme.buttonPrimaryBg else theme.bevelBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) theme.buttonPrimaryText else theme.fontsPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun SortMenu(current: AnalysisSort, theme: ThemeSpec, onSelect: (AnalysisSort) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bgSurface)
            .border(1.dp, theme.bevelBorder, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        SortOption(stringResource(R.string.analysis_sort_size_desc), current == AnalysisSort.SIZE_DESC, theme) { onSelect(AnalysisSort.SIZE_DESC) }
        SortOption(stringResource(R.string.analysis_sort_size_asc), current == AnalysisSort.SIZE_ASC, theme) { onSelect(AnalysisSort.SIZE_ASC) }
        SortOption(stringResource(R.string.analysis_sort_name), current == AnalysisSort.NAME, theme) { onSelect(AnalysisSort.NAME) }
        SortOption(stringResource(R.string.analysis_sort_date), current == AnalysisSort.DATE, theme) { onSelect(AnalysisSort.DATE) }
    }
}

@Composable
private fun SortOption(label: String, selected: Boolean, theme: ThemeSpec, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.buttonPrimaryBg.copy(alpha = 0.12f) else theme.bgSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) theme.buttonPrimaryBg else theme.fontsPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text("✓", color = theme.buttonPrimaryBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AnalyzerRowMenu(
    onPreview: () -> Unit,
    onOpenInFiles: () -> Unit,
    onCopyPath: () -> Unit,
    theme: ThemeSpec,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bgSurface)
            .border(1.dp, theme.bevelBorder, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        MenuItem(stringResource(R.string.row_menu_preview), theme) { onPreview(); onDismiss() }
        MenuItem(stringResource(R.string.row_menu_open_in_files), theme) { onOpenInFiles(); onDismiss() }
        MenuItem(stringResource(R.string.row_menu_copy_path), theme) { onCopyPath(); onDismiss() }
    }
}

@Composable
private fun MenuItem(label: String, theme: ThemeSpec, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = theme.fontsPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun lastScanLabel(timestampMs: Long): String {
    val timeAgo = formatTimeAgo(timestampMs)
    return when (timeAgo.unit) {
        TimeAgoUnit.NEVER -> stringResource(R.string.analysis_last_scan_never)
        TimeAgoUnit.SECONDS -> stringResource(R.string.analysis_last_scan_seconds, timeAgo.value)
        TimeAgoUnit.MINUTES -> stringResource(R.string.analysis_last_scan_minutes, timeAgo.value)
        TimeAgoUnit.HOURS -> stringResource(R.string.analysis_last_scan_hours, timeAgo.value)
        TimeAgoUnit.DAYS -> stringResource(R.string.analysis_last_scan_days, timeAgo.value)
        TimeAgoUnit.WEEKS -> stringResource(R.string.analysis_last_scan_weeks, timeAgo.value)
    }
}

private fun pruneDeleted(current: AnalysisResult?, deletedUris: Set<Uri>): AnalysisResult? {
    if (current == null) return null
    val newBig = current.bigFiles.filter { !deletedUris.contains(it.uri) }
    val newGroups = current.duplicateGroups.mapNotNull { group ->
        val remaining = group.files.filter { !deletedUris.contains(it.uri) }
        // A group needs at least 2 files to still count as duplicates.
        // If the user manually selected the keeper for deletion, the
        // next file in the list becomes the new keeper — that's handled
        // implicitly because the list is order-preserving and the
        // remaining files keep their original relative order.
        if (remaining.size < 2) null
        else group.copy(files = remaining)
    }
    return current.copy(bigFiles = newBig, duplicateGroups = newGroups)
}

@Composable
private fun SelectionActionBar(count: Int, theme: ThemeSpec, onDelete: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.buttonPrimaryBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count " + stringResource(R.string.selection_selected),
            color = theme.buttonPrimaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.action_delete), color = theme.buttonPrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel), color = theme.buttonPrimaryText, fontSize = 13.sp)
        }
    }
}

private sealed interface PendingDeletion {
    data class OwnCache(val bytes: Long) : PendingDeletion
    data class Duplicates(val groups: List<DuplicateGroup>, val wastedBytes: Long) : PendingDeletion
    data class SingleFile(val file: BigFile) : PendingDeletion
    data class Multi(val files: List<BigFile>) : PendingDeletion
}

private data class ConfirmResult(
    val deletedUris: Set<Uri>,
    val freedBytes: Long
)

private data class ConfirmTriple(
    val title: String,
    val message: String,
    val onConfirm: suspend () -> ConfirmResult
)

@Composable
private fun PendingDeletion.toConfirm(context: Context): ConfirmTriple = when (this) {
    is PendingDeletion.OwnCache -> ConfirmTriple(
        title = stringResource(R.string.confirm_cache_title),
        message = stringResource(R.string.confirm_cache_message, formatBytesSmart(bytes)),
        onConfirm = {
            val freed = DeletionManager.wipeOwnCache(context)
            ConfirmResult(deletedUris = emptySet(), freedBytes = freed)
        }
    )

    is PendingDeletion.Duplicates -> ConfirmTriple(
        title = stringResource(R.string.confirm_dupes_title),
        message = stringResource(R.string.confirm_dupes_message, formatBytesSmart(wastedBytes)),
        onConfirm = {
            // Guard: only ever delete files at index >= 1 within each
            // group. Index 0 is the keeper. Groups with fewer than 2
            // files are malformed and are skipped entirely.
            val toDelete = groups
                .filter { it.files.size >= 2 }
                .flatMap { g -> g.files.drop(1) }

            val freedBytes = toDelete.sumOf { resolveFreedBytes(context, it) }
            DeletionManager.deleteMediaUris(context, toDelete)
            ConfirmResult(
                deletedUris = toDelete.map { it.uri }.toSet(),
                freedBytes = freedBytes
            )
        }
    )

    is PendingDeletion.SingleFile -> ConfirmTriple(
        title = stringResource(R.string.confirm_file_title),
        message = stringResource(
            R.string.confirm_file_message,
            file.name,
            formatBytesSmart(file.sizeBytes)
        ),
        onConfirm = {
            val freedBytes = resolveFreedBytes(context, file)
            DeletionManager.deleteMediaUris(context, listOf(file))
            ConfirmResult(
                deletedUris = setOf(file.uri),
                freedBytes = freedBytes
            )
        }
    )

    is PendingDeletion.Multi -> ConfirmTriple(
        title = stringResource(R.string.confirm_multi_title, files.size),
        message = stringResource(
            R.string.confirm_multi_message,
            files.size,
            formatBytesSmart(files.sumOf { resolveFreedBytes(context, it) })
        ),
        onConfirm = {
            val freedBytes = files.sumOf { resolveFreedBytes(context, it) }
            DeletionManager.deleteMediaUris(context, files)
            ConfirmResult(
                deletedUris = files.map { it.uri }.toSet(),
                freedBytes = freedBytes
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnalyzerRow(
    title: String,
    subtitle: String,
    actionLabel: String?,
    theme: ThemeSpec,
    thumbnailUri: Uri? = null,
    isMedia: Boolean = false,
    location: String = "",
    keepBadge: String? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onThumbnailClick: (() -> Unit)? = null,
    onAction: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) theme.buttonPrimaryBg.copy(alpha = 0.20f) else theme.bgSurface)
            .then(
                if (selected)
                    Modifier.border(1.dp, theme.buttonPrimaryBg, RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = { if (selectionMode) onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbnailUri != null) {
            FileThumbnail(thumbnailUri, isMedia, theme, size = 48, onClick = onThumbnailClick)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = theme.fontsPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = theme.fontsSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
            if (location.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    location,
                    color = theme.fontsSecondary.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (keepBadge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.consoleSuccess.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    keepBadge,
                    color = theme.consoleSuccess,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = theme.buttonPrimaryBg, fontSize = 12.sp)
            }
        }
        if (onMenuClick != null && !selectionMode) {
            TextButton(onClick = onMenuClick) {
                Text("⋮", color = theme.fontsSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ScopeToggle(label: String, checked: Boolean, theme: ThemeSpec, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = theme.fontsPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.buttonPrimaryText,
                checkedTrackColor = theme.buttonPrimaryBg,
                uncheckedThumbColor = theme.fontsSecondary,
                uncheckedTrackColor = theme.bgCrust
            )
        )
    }
}

@Composable
private fun MinSizeRow(value: Long, theme: ThemeSpec, onChange: (Long) -> Unit) {
    val steps = listOf(0L, 4L * 1024, 64L * 1024, 512L * 1024, 1L * 1024 * 1024)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.analysis_scope_min_size, formatBytesSmart(value)),
            color = theme.fontsPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = {
            val idx = steps.indexOfFirst { it > value }
            val next = if (idx < 0) steps.first() else steps[idx]
            onChange(next)
        }) {
            Text("+", color = theme.fontsLinks, fontSize = 16.sp)
        }
    }
}

private fun scopeSummary(scope: DuplicateScope): String {
    val parts = mutableListOf<String>()
    if (scope.includeImages) parts += "img"
    if (scope.includeVideos) parts += "vid"
    if (scope.includeAudio) parts += "aud"
    if (scope.includeDownloads) parts += "dl"
    return if (parts.isEmpty()) "none" else parts.joinToString("+")
}

private suspend fun scanEverything(
    context: Context,
    scope: DuplicateScope,
    onProgress: (Float, String) -> Unit
): AnalysisResult {
    onProgress(0f, context.getString(R.string.analysis_progress_own_cache))
    val cache = StorageAnalyzer.ownCache(context)

    onProgress(0.15f, context.getString(R.string.analysis_progress_big))
    val big = StorageAnalyzer.bigFiles(context, scope = scope)

    onProgress(0.35f, context.getString(R.string.analysis_progress_downloads))
    val downloads = StorageAnalyzer.downloadsInfo(context)

    onProgress(0.5f, context.getString(R.string.analysis_progress_dupes))
    val dupesRaw = DuplicateFinder.findDuplicates(
        context = context,
        scope = scope,
        onProgress = { scanned, total ->
            val sub = if (total > 0) scanned.toFloat() / total else 0f
            onProgress(
                0.5f + 0.35f * sub,
                context.getString(R.string.analysis_progress_dupes) + " $scanned/$total"
            )
        }
    )

    // Post-process: any file whose MediaStore size was 0 gets its real size
    // resolved via the file descriptor. This fixes the "0 B freed" bug on
    // OEMs (Samsung, etc.) where the SIZE column is unreliable.
    onProgress(0.85f, context.getString(R.string.analysis_progress_dupes))
    val dupes = dupesRaw.map { group ->
        val resolvedFiles = group.files.map { f ->
            if (f.sizeBytes > 0L) f
            else f.copy(sizeBytes = resolveFreedBytes(context, f))
        }
        val realGroupSize = resolvedFiles.firstOrNull()?.sizeBytes ?: 0L
        group.copy(sizeBytes = realGroupSize, files = resolvedFiles)
    }

    onProgress(0.88f, context.getString(R.string.analysis_progress_unused))
    val unused = UnusedAppsFinder.findUnused(context, 60)

    onProgress(0.95f, context.getString(R.string.analysis_progress_foreign))
    val foreign = StorageAnalyzer.foreignCaches(context)

    onProgress(1f, context.getString(R.string.analysis_progress_done))
    return AnalysisResult(
        cache = cache,
        bigFiles = big,
        downloads = downloads,
        duplicateGroups = dupes,
        unusedApps = unused,
        foreignCaches = foreign,
        hasAllFilesAccess = StorageAnalyzer.hasAllFilesAccess(),
        hasUsageAccess = UnusedAppsFinder.hasUsageAccess(context)
    )
}