package com.example.oshootcleaner

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a thumbnail for a MediaStore URI using ContentResolver.loadThumbnail().
 * Returns null on failure (non-media files, corrupted, missing permission).
 * Callers should show a placeholder icon in that case.
 */
suspend fun loadThumbnail(context: Context, uri: Uri, sizePx: Int = 200): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                // Pre-Q fallback via MediaStore.Images.Media.getBitmap is deprecated
                // and slow; rather than risk a jank, return null and let the
                // caller render a placeholder.
                null
            }
        } catch (_: Exception) {
            null
        }
    }

/**
 * Small square thumbnail used inside list rows in the Analysis tab.
 */
@Composable
fun FileThumbnail(
    uri: Uri,
    isMedia: Boolean,
    theme: ThemeSpec,
    size: Int = 48,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uri, isMedia) {
        if (isMedia) bitmap = loadThumbnail(context, uri, sizePx = size * 4)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bgCrust)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder: first letter of the file extension, or a generic dot
            Text(
                text = if (isMedia) "◍" else "▤",
                color = theme.fontsSecondary,
                fontSize = (size * 0.4f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Full-screen preview. Shows the image large, with a delete / close action
 * bar at the bottom. Uses Dialog (not DialogFragment) so it can be triggered
 * from any composable without an Activity reference.
 */
@Composable
fun FilePreviewDialog(
    file: BigFile,
    theme: ThemeSpec,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(file.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(file.uri) { mutableStateOf(true) }

    LaunchedEffect(file.uri) {
        loading = true
        // Try a larger thumbnail first; fall back to the small one.
        bitmap = loadThumbnail(context, file.uri, sizePx = 1024)
            ?: loadThumbnail(context, file.uri, sizePx = 512)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.bgBase)
                .padding(16.dp)
        ) {
            // Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.bgCrust),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                when {
                    loading -> Text(
                        "…",
                        color = theme.fontsSecondary,
                        fontSize = 32.sp
                    )
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "▤",
                            color = theme.fontsSecondary,
                            fontSize = 56.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.preview_no_thumbnail),
                            color = theme.fontsSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // File info
            Text(
                file.name,
                color = theme.fontsHeadings,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatBytesGb(file.sizeBytes),
                color = theme.fontsSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.action_close),
                        color = theme.fontsSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    onDismiss()
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = theme.consoleError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}