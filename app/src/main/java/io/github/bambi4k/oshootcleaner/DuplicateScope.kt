package io.github.bambi4k.oshootcleaner

/**
 * Which MediaStore collections the duplicate finder should scan, and the
 * minimum file size to consider. Persisted in SharedPreferences.
 */
data class DuplicateScope(
    val includeImages: Boolean,
    val includeVideos: Boolean,
    val includeAudio: Boolean,
    val includeDownloads: Boolean,
    val minSizeBytes: Long
) {
    companion object {
        val DEFAULT = DuplicateScope(
            includeImages = true,
            includeVideos = true,
            includeAudio = true,
            includeDownloads = false,
            minSizeBytes = 4L * 1024
        )

        private const val PREFS = "oh_shoot_prefs"
        private const val KEY_IMG = "dupe_img"
        private const val KEY_VID = "dupe_vid"
        private const val KEY_AUD = "dupe_aud"
        private const val KEY_DL = "dupe_dl"
        private const val KEY_SIZE = "dupe_min_size"

        fun load(context: android.content.Context): DuplicateScope {
            val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            return DuplicateScope(
                includeImages = p.getBoolean(KEY_IMG, true),
                includeVideos = p.getBoolean(KEY_VID, true),
                includeAudio = p.getBoolean(KEY_AUD, false),
                includeDownloads = p.getBoolean(KEY_DL, false),
                minSizeBytes = p.getLong(KEY_SIZE, DEFAULT.minSizeBytes)
            )
        }

        fun save(context: android.content.Context, scope: DuplicateScope) {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_IMG, scope.includeImages)
                .putBoolean(KEY_VID, scope.includeVideos)
                .putBoolean(KEY_AUD, scope.includeAudio)
                .putBoolean(KEY_DL, scope.includeDownloads)
                .putLong(KEY_SIZE, scope.minSizeBytes)
                .apply()
        }
    }
}