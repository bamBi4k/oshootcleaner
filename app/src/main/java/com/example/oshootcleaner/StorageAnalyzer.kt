package com.example.oshootcleaner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class CacheInfo(
    val appCacheBytes: Long,
    val appCacheFiles: Int,
    val externalCacheBytes: Long,
    val externalCacheFiles: Int
) {
    val totalBytes: Long get() = appCacheBytes + externalCacheBytes
    val totalFiles: Int get() = appCacheFiles + externalCacheFiles
}

data class BigFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isMedia: Boolean,
    val location: String = ""
)

data class DownloadsInfo(
    val count: Int,
    val totalBytes: Long,
    val oldestTimestampMs: Long
)

data class UnusedAppsResult(
    val apps: List<UnusedApp>,
    val insufficientHistory: Boolean,
    val daysOfHistoryAvailable: Int
)

data class AnalysisResult(
    val cache: CacheInfo,
    val bigFiles: List<BigFile>,
    val downloads: DownloadsInfo,
    val duplicateGroups: List<DuplicateGroup>,
    val unusedApps: UnusedAppsResult,
    val foreignCaches: List<ForeignCacheInfo>,
    val hasAllFilesAccess: Boolean,
    val hasUsageAccess: Boolean
)

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val files: List<BigFile>
) {
    val wastedBytes: Long get() = sizeBytes * (files.size - 1)
}

data class UnusedApp(
    val packageName: String,
    val label: String,
    val lastUsedMs: Long
)

data class ForeignCacheInfo(
    val packageName: String,
    val label: String,
    val cacheBytes: Long
)

object StorageAnalyzer {

    // ------------------------------------------------------------------
    // Extension fallback list.
    //
    // MediaStore's MIME classification is unreliable on many OEM ROMs and
    // for many formats. When a file has a MIME type that doesn't match
    // image/*, video/*, or audio/* but its extension is in this list,
    // we still include it (subject to the user's scope filter).
    // ------------------------------------------------------------------
    private val MEDIA_EXTENSIONS = listOf(
        // Images
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tiff", ".tif",
        ".svg", ".ico", ".heic", ".heif", ".avif",
        // RAW
        ".raw", ".cr2", ".cr3", ".nef", ".nrw", ".arw", ".dng", ".orf", ".rw2",
        // Design
        ".psd", ".xcf",
        // Audio
        ".mp3", ".wav", ".flac", ".aac", ".m4a", ".ogg", ".oga", ".opus",
        ".wma", ".aiff", ".aif", ".alac", ".amr", ".mid", ".midi", ".mka",
        // Video
        ".mp4", ".mkv", ".mov", ".avi", ".wmv", ".flv", ".webm", ".3gp",
        ".m4v", ".mpg", ".mpeg", ".ts", ".m2ts", ".vob", ".rm", ".rmvb"
    )

    private val IMAGE_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tiff", ".tif",
        ".svg", ".ico", ".heic", ".heif", ".avif", ".raw", ".cr2", ".cr3",
        ".nef", ".nrw", ".arw", ".dng", ".orf", ".rw2", ".psd", ".xcf"
    )
    private val VIDEO_EXTENSIONS = listOf(
        ".mp4", ".mkv", ".mov", ".avi", ".wmv", ".flv", ".webm", ".3gp",
        ".m4v", ".mpg", ".mpeg", ".ts", ".m2ts", ".vob", ".rm", ".rmvb",
        ".asf", ".divx"
    )
    private val AUDIO_EXTENSIONS = listOf(
        ".mp3", ".wav", ".flac", ".aac", ".m4a", ".ogg", ".oga", ".opus",
        ".wma", ".aiff", ".aif", ".alac", ".amr", ".mid", ".midi", ".mka"
    )

    private fun dirStats(dir: File?): Pair<Long, Int> {
        if (dir == null || !dir.exists()) return 0L to 0
        var bytes = 0L
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                val (b, c) = dirStats(f)
                bytes += b
                count += c
            } else {
                bytes += f.length()
                count += 1
            }
        }
        return bytes to count
    }

    fun ownCache(context: Context): CacheInfo {
        val (appBytes, appCount) = dirStats(context.cacheDir)
        val (extBytes, extCount) = dirStats(context.externalCacheDir)
        return CacheInfo(appBytes, appCount, extBytes, extCount)
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else false

    /**
     * Returns large files (> [thresholdBytes]) from MediaStore.Files.
     *
     * Applies two filters:
     *   1. MIME type starts with image/, video/, or audio/ — catches
     *      everything MediaStore has classified properly.
     *   2. Display name ends with one of [MEDIA_EXTENSIONS] — catches
     *      everything MediaStore hasn't classified but we still care
     *      about (.svg, .psd, .xcf, .opus, RAW formats, etc.).
     *
     * If [scope] is provided, further filters by the user's media-type
     * selection (images / videos / audio).
     */
    fun bigFiles(
        context: Context,
        thresholdBytes: Long = 100L * 1024 * 1024,
        scope: DuplicateScope? = null
    ): List<BigFile> {
        val out = mutableListOf<BigFile>()

        // Query each collection separately so Android 13+'s permission
        // model exposes the correct files.
        if (scope == null || scope.includeImages) {
            out += queryMediaCollection(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                thresholdBytes,
                isMediaType = true
            )
        }
        if (scope == null || scope.includeVideos) {
            out += queryMediaCollection(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                thresholdBytes,
                isMediaType = true
            )
        }
        if (scope == null || scope.includeAudio) {
            out += queryMediaCollection(
                context,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                thresholdBytes,
                isMediaType = false
            )
        }

        return out.distinctBy { it.uri }.sortedByDescending { it.sizeBytes }
    }

    private fun queryMediaCollection(
        context: Context,
        collection: Uri,
        thresholdBytes: Long,
        isMediaType: Boolean
    ): List<BigFile> {
        val out = mutableListOf<BigFile>()

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA
            )
        }

        val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
        val args = arrayOf(thresholdBytes.toString())
        val sort = "${MediaStore.MediaColumns.SIZE} DESC"

        try {
            context.contentResolver.query(collection, projection, selection, args, sort)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = Uri.withAppendedPath(collection, id.toString())

                        val reported = c.getLong(sizeCol)
                        val real = if (reported > 0L) reported else resolveSize(context, uri)
                        if (real < thresholdBytes) continue

                        val name = c.getString(nameCol) ?: continue

                        out += BigFile(
                            uri = uri,
                            name = name,
                            path = uri.toString(),
                            sizeBytes = real,
                            isMedia = isMediaType,
                            location = extractLocation(c)
                        )
                    }
                }
        } catch (_: Exception) { }

        return out
    }

    fun downloadsInfo(context: Context): DownloadsInfo {
        var count = 0
        var total = 0L
        var oldest = Long.MAX_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_ADDED
            )
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                    while (c.moveToNext()) {
                        val s = c.getLong(sizeCol)
                        val d = c.getLong(dateCol) * 1000L
                        count++
                        total += s
                        if (d < oldest) oldest = d
                    }
                }
            } catch (_: Exception) { }
        }
        return DownloadsInfo(
            count = count,
            totalBytes = total,
            oldestTimestampMs = if (oldest == Long.MAX_VALUE) 0L else oldest
        )
    }

    fun foreignCaches(
        context: Context,
        minBytes: Long = 50L * 1024 * 1024
    ): List<ForeignCacheInfo> {
        if (!StorageBreakdownManager.hasUsageAccess(context)) return emptyList()
        val sm = context.getSystemService(Context.STORAGE_STATS_SERVICE)
                as android.app.usage.StorageStatsManager
        val stm = context.getSystemService(Context.STORAGE_SERVICE)
                as android.os.storage.StorageManager
        val pm = context.packageManager
        val uuidStr = stm.primaryStorageVolume.uuid
        val uuid: java.util.UUID = if (uuidStr == null) {
            java.util.UUID(0L, 0L)
        } else {
            java.util.UUID.fromString(uuidStr)
        }

        val out = mutableListOf<ForeignCacheInfo>()
        for (app in pm.getInstalledApplications(0)) {
            if (app.packageName == context.packageName) continue
            try {
                val stats = sm.queryStatsForUid(uuid, app.uid)
                if (stats.cacheBytes >= minBytes) {
                    out += ForeignCacheInfo(
                        packageName = app.packageName,
                        label = pm.getApplicationLabel(app).toString(),
                        cacheBytes = stats.cacheBytes
                    )
                }
            } catch (_: Exception) { }
        }
        return out.sortedByDescending { it.cacheBytes }
    }

    /**
     * Extracts a human-readable folder path from a MediaStore cursor row.
     * Android 10+: MediaStore.MediaColumns.RELATIVE_PATH gives the folder
     * relative to the storage root (e.g. "Download/"). Android 9-:
     * MediaStore.MediaColumns.DATA gives the full path; we trim it to the
     * folder.
     *
     * The returned string is prefixed with /sdcard/ for familiarity.
     */
    internal fun extractLocation(c: Cursor): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            if (relIdx < 0) return ""
            val rel = c.getString(relIdx) ?: return ""
            "/sdcard/$rel"
        } else {
            @Suppress("DEPRECATION")
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (dataIdx < 0) return ""
            @Suppress("DEPRECATION")
            val full = c.getString(dataIdx) ?: return ""
            val dir = full.substringBeforeLast('/', "")
            if (dir.isEmpty()) "" else dir + "/"
        }
    }

    private fun resolveSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0 } ?: 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}