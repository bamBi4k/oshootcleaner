package com.example.oshootcleaner

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.security.MessageDigest

object DuplicateFinder {

    // How many bytes to hash from the START of each file. Larger = more
    // confidence, negligible performance cost on modern storage.
    private const val HEAD_HASH_BYTES = 256 * 1024

    // How many bytes to hash from the END of each file when the file is
    // larger than HEAD_HASH_BYTES * 2. Two files with the same size, same
    // head, AND same tail are essentially guaranteed to be identical.
    private const val TAIL_HASH_BYTES = 64 * 1024

    fun findDuplicates(
        context: Context,
        scope: DuplicateScope = DuplicateScope.DEFAULT,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> {
        val candidates = mutableListOf<BigFile>()

        if (scope.includeImages) {
            candidates += queryCollection(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                scope = scope,
                imageType = true,
                videoType = false,
                audioType = false
            )
        }
        if (scope.includeVideos) {
            candidates += queryCollection(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                scope = scope,
                imageType = false,
                videoType = true,
                audioType = false
            )
        }
        if (scope.includeAudio) {
            candidates += queryCollection(
                context = context,
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                scope = scope,
                imageType = false,
                videoType = false,
                audioType = true
            )
        }
        if (scope.includeDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            candidates += queryDownloads(context, scope)
        }

        val unique = candidates.distinctBy { it.uri }

        onProgress(0, unique.size)

        val bySize = unique.groupBy { it.sizeBytes }
            .filterValues { it.size >= 2 }

        val groups = mutableListOf<DuplicateGroup>()
        var done = 0
        for ((size, files) in bySize) {
            val byHash = files.groupBy { strongHash(context, it.uri, size) ?: "" }
                .filterKeys { it.isNotEmpty() }
                .filterValues { it.size >= 2 }
            for ((hash, sameHashFiles) in byHash) {
                // Sort within the group so the "keeper" (first element)
                // is deterministic across scans. Rule: prefer files in
                // the most "original"-looking location first. Files in
                // DCIM/, Pictures/, and Camera/ come before Download/
                // and duplicate-looking folders. Ties broken by name.
                val ordered = sameHashFiles.sortedWith(
                    compareBy(
                        { locationRank(it.location) },
                        { it.name.lowercase() }
                    )
                )
                groups += DuplicateGroup(hash = hash, sizeBytes = size, files = ordered)
            }
            done += files.size
            onProgress(done, unique.size)
        }

        return groups.sortedByDescending { it.wastedBytes }
    }

    /**
     * Queries one specific MediaStore collection (Images, Video, or Audio).
     * The collection URI determines both the visibility rules and the
     * columns available. We only project columns common to all three.
     */
    private fun queryCollection(
        context: Context,
        collection: Uri,
        scope: DuplicateScope,
        imageType: Boolean,
        videoType: Boolean,
        audioType: Boolean
    ): List<BigFile> {
        val out = mutableListOf<BigFile>()

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATA
            )
        }

        val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
        val args = arrayOf(scope.minSizeBytes.toString())
        val sort = "${MediaStore.MediaColumns.SIZE} DESC"

        try {
            context.contentResolver.query(collection, projection, selection, args, sort)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = Uri.withAppendedPath(collection, id.toString())

                        val reported = c.getLong(sizeCol)
                        val real = if (reported > 0L) reported else resolveSize(context, uri)
                        if (real < scope.minSizeBytes) continue

                        val name = c.getString(nameCol) ?: continue

                        val location = StorageAnalyzer.extractLocation(c)

                        out += BigFile(
                            uri = uri,
                            name = name,
                            path = uri.toString(),
                            sizeBytes = real,
                            isMedia = imageType || videoType,
                            location = location
                        )
                    }
                }
        } catch (_: Exception) { }

        return out
    }

    /**
     * Downloads collection lives under MediaStore.Downloads on Android 10+.
     * On older Android there is no Downloads collection, so it returns empty.
     */
    private fun queryDownloads(
        context: Context,
        scope: DuplicateScope
    ): List<BigFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val out = mutableListOf<BigFile>()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.RELATIVE_PATH
        )
        val selection = "${MediaStore.Downloads.SIZE} >= ?"
        val args = arrayOf(scope.minSizeBytes.toString())
        val sort = "${MediaStore.Downloads.SIZE} DESC"

        try {
            context.contentResolver.query(collection, projection, selection, args, sort)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = Uri.withAppendedPath(collection, id.toString())

                        val reported = c.getLong(sizeCol)
                        val real = if (reported > 0L) reported else resolveSize(context, uri)
                        if (real < scope.minSizeBytes) continue

                        val name = c.getString(nameCol) ?: continue

                        val location = StorageAnalyzer.extractLocation(c)

                        out += BigFile(
                            uri = uri,
                            name = name,
                            path = uri.toString(),
                            sizeBytes = real,
                            isMedia = false,
                            location = location
                        )
                    }
                }
        } catch (_: Exception) { }

        return out
    }

    /**
     * Lower rank = more likely to be the "original". Directories where
     * the camera saves photos come first, then user-organized folders,
     * then Downloads and messaging-app folders.
     */
    private fun locationRank(location: String): Int {
        val l = location.lowercase()
        return when {
            l.contains("/dcim/camera") -> 0
            l.contains("/dcim/") -> 1
            l.contains("/pictures/") -> 2
            l.contains("/movies/") -> 3
            l.contains("/music/") -> 4
            l.isEmpty() -> 5
            l.contains("/download/") -> 8
            l.contains("/downloads/") -> 8
            l.contains("/whatsapp") -> 9
            l.contains("/telegram") -> 9
            else -> 6
        }
    }

    /**
     * Two-phase hash that is both fast and reliable:
     *
     *   1. Hash the first HEAD_HASH_BYTES (256 KB) of the file.
     *   2. If the file is larger than HEAD_HASH_BYTES * 2, also hash the
     *      last TAIL_HASH_BYTES (64 KB) and combine both digests.
     *
     * Two files with the same declared MediaStore SIZE, the same head
     * hash, AND the same tail hash are, for all practical purposes,
     * identical. This avoids the class of false positives where two
     * unrelated videos share a container header but have different
     * content.
     *
     * The file SIZE is included in the hash input so the digest is
     * unique per (size, head, tail) tuple — this makes the hash
     * self-sufficient even if the caller's size filter changes.
     */
    private fun strongHash(context: Context, uri: Uri, size: Long): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")

            // Include size to make the digest unique per (size, head, tail).
            md.update(size.toString().toByteArray(Charsets.UTF_8))
            md.update(0.toByte())

            var hashed = false

            context.contentResolver.openInputStream(uri)?.use { input ->
                // Head
                val head = ByteArray(HEAD_HASH_BYTES)
                var headRead = 0
                while (headRead < HEAD_HASH_BYTES) {
                    val n = input.read(head, headRead, HEAD_HASH_BYTES - headRead)
                    if (n <= 0) break
                    headRead += n
                }
                if (headRead <= 0) {
                    return null
                }
                md.update(head, 0, headRead)
                hashed = true
            }

            if (!hashed) return null

            // Tail, if the file is large enough for it to be distinct
            // from the head.
            if (size > HEAD_HASH_BYTES.toLong() * 2) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { tailInput ->
                        // Skip to the tail. We can't seek in a generic
                        // InputStream, so we skip forward. On MediaStore
                        // file-backed streams this is efficient.
                        val toSkip = size - TAIL_HASH_BYTES
                        var skipped = 0L
                        while (skipped < toSkip) {
                            val n = tailInput.skip(toSkip - skipped)
                            if (n <= 0) break
                            skipped += n
                        }
                        if (skipped == toSkip) {
                            val tail = ByteArray(TAIL_HASH_BYTES)
                            var tailRead = 0
                            while (tailRead < TAIL_HASH_BYTES) {
                                val n = tailInput.read(tail, tailRead, TAIL_HASH_BYTES - tailRead)
                                if (n <= 0) break
                                tailRead += n
                            }
                            if (tailRead > 0) {
                                md.update(0x01)
                                md.update(tail, 0, tailRead)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Tail read failed — head hash alone is still a
                    // reasonable proxy. Not as strong, but no worse than
                    // the previous implementation.
                }
            }

            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
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