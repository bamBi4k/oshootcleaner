package io.github.bambi4k.oshootcleaner

/**
 * Trims a filename for display, keeping the start, the end, and the
 * extension visible. Preserves at most [maxChars] characters. Middle
 * characters are replaced with a single ellipsis.
 *
 * The goal is that the file extension (".mp4", ".jpg", ".apk", etc.) is
 * ALWAYS visible even when the name is long, and the name never wraps to
 * a second line.
 *
 * Example:
 *   "42535636_5435_video_final_export_v2.mp4"
 *     with maxChars = 30
 *     -> "42535636_54…_export_v2.mp4"
 */
fun trimFileNameForDisplay(name: String, maxChars: Int = 34): String {
    if (name.length <= maxChars) return name

    val dotIndex = name.lastIndexOf('.')
    val (base, ext) = if (dotIndex > 0 && dotIndex < name.length - 1) {
        name.substring(0, dotIndex) to name.substring(dotIndex)
    } else {
        name to ""
    }

    val ellipsis = "…"
    val room = maxChars - ext.length - ellipsis.length
    if (room <= 0) {
        return name.take((maxChars - 1).coerceAtLeast(1)) + ellipsis
    }

    val headLen = (room * 6) / 10
    val tailLen = room - headLen

    val head = base.take(headLen)
    val tail = if (tailLen > 0) base.takeLast(tailLen) else ""

    return "$head$ellipsis$tail$ext"
}