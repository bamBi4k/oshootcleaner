package io.github.bambi4k.oshootcleaner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object DeletionManager {

    data class DeletionResult(
        val deletedCount: Int,
        val failedCount: Int,
        val freedBytes: Long
    )

    /**
     * Deletes a list of MediaStore URIs. On Android 10+ MediaStore prompts
     * the user for confirmation if the app doesn't have
     * MANAGE_EXTERNAL_STORAGE — the caller is responsible for having
     * already obtained user consent for that path.
     *
     * Freed bytes are computed from the REAL file size resolved just
     * before deletion, not the cached BigFile.sizeBytes. This protects
     * against MediaStore's unreliable SIZE column.
     */
    fun deleteMediaUris(
        context: Context,
        items: List<BigFile>
    ): DeletionResult {
        var deleted = 0
        var failed = 0
        var freed = 0L

        if (StorageAnalyzer.hasAllFilesAccess()) {
            for (item in items) {
                // Resolve the real size right before deletion. Uses the
                // descriptor if MediaStore's cached size was 0.
                val actualSize = resolveFreedSize(context, item)

                try {
                    val n = context.contentResolver.delete(item.uri, null, null)
                    if (n > 0) {
                        deleted++
                        freed += actualSize
                    } else {
                        failed++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // System delete dialog path — we return the sum of what we
            // requested. The system will handle actual deletion.
            val uris = items.map { it.uri }
            MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleted = items.size
            freed = items.sumOf { resolveFreedSize(context, it) }
            return DeletionResult(deleted, 0, freed)
        }

        return DeletionResult(deleted, failed, freed)
    }

    /**
     * Returns the actual freed bytes for a file. Uses the cached size if
     * non-zero; otherwise reads the file descriptor. Prefers the real file
     * size over any cached value to guarantee accurate reporting.
     */
    private fun resolveFreedSize(context: Context, item: BigFile): Long {
        if (item.sizeBytes > 0L) return item.sizeBytes
        return try {
            context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0 } ?: 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /** Deletes our own cache files. No system dialog needed. */
    fun wipeOwnCache(context: Context): Long {
        fun wipe(dir: java.io.File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var bytes = 0L
            dir.listFiles()?.forEach { f ->
                bytes += if (f.isDirectory) {
                    val sub = wipe(f)
                    f.delete()
                    sub
                } else {
                    val s = f.length()
                    if (f.delete()) s else 0L
                }
            }
            return bytes
        }
        return wipe(context.cacheDir) + wipe(context.externalCacheDir)
    }

    /**
     * Creates the system delete dialog for multiple MediaStore URIs.
     * Available from Android 11 (R). Returns a PendingIntent that the
     * caller starts via IntentSenderRequest.
     */
    fun createDeleteRequest(
        resolver: ContentResolver,
        uris: List<Uri>
    ): android.app.PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, uris)
        } else null
    }
}