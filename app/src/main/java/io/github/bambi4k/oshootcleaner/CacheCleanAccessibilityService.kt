package io.github.bambi4k.oshootcleaner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Automated cache-cleaning accessibility service.
 *
 * Navigation:
 *   1. Open Android App Info for the target package
 *   2. Locate the Storage / Storage & cache row
 *   3. Open the storage screen
 *   4. Locate Clear cache
 *   5. Click Clear cache only
 *   6. Return to the app and continue
 *
 * IMPORTANT:
 * This service deliberately prefers skipping a package over making an
 * uncertain click. "Clear storage" / "Clear data" must never be clicked
 * accidentally.
 *
 * The service uses several detection layers:
 *   1. Resource ID
 *   2. Exact normalized text
 *   3. Exact normalized content description
 *   4. Structural/contextual scoring
 *
 * It also supports scrolling when the desired control is below the
 * visible area of the Settings page.
 *
 * Safety: any package that looks like a banking, finance, wallet, 2FA,
 * password-manager, crypto, or identity app is refused outright in
 * nextPackage(). This is defense in depth on top of the filter applied
 * by CacheCleanSessionBuilder.
 */
class CacheCleanAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CacheCleanA11y"

        const val ACTION_START = "com.example.io.github.bambi4k.oshootcleaner.a11y.START"
        const val ACTION_STOP = "com.example.io.github.bambi4k.oshootcleaner.a11y.STOP"
        const val ACTION_PROGRESS = "com.example.io.github.bambi4k.oshootcleaner.a11y.PROGRESS"
        const val ACTION_DONE = "com.example.io.github.bambi4k.oshootcleaner.a11y.DONE"

        const val EXTRA_PACKAGES = "packages"

        // Stage timing — adaptive. We start tight and let retries push
        // the settle window outward up to ADAPTIVE_SETTLE_MAX_MS. This
        // gives fast devices the speed of ~450ms settles and slow devices
        // the safety of ~1100ms, without having to choose one or the other.
        private const val STAGE_TIMEOUT_MS = 13_000L
        private const val ADAPTIVE_SETTLE_MIN_MS = 450L
        private const val ADAPTIVE_SETTLE_MAX_MS = 1_100L
        private const val ADAPTIVE_SETTLE_STEP_MS = 150L
        private const val RETRY_MS = 350L
        private const val CLICK_SETTLE_MS = 950L
        private const val BACK_DELAY_MS = 550L

        // Maximum number of scroll attempts per stage. Most phones will
        // need zero. Some OEMs put Storage / Clear cache below the fold.
        private const val MAX_SCROLL_ATTEMPTS = 3

        // Maximum number of nodes inspected in one tree traversal.
        private const val MAX_NODES = 800

        // How close (in pixels) a clickable node must be to the label
        // for us to treat it as its companion button. Pixel 8's Settings
        // puts the trash-icon button directly above its "Clear cache"
        // text label, so we need a generous vertical tolerance.
        private const val PROXIMITY_CLICK_MAX_PX = 260

        // -----------------------------------------------------------------
        // TEMPORARY Pixel-8 fractional-tap fallback.
        //
        // Some Pixel 8 builds do not expose the "Clear cache" trash-icon
        // button as a clickable node in the accessibility tree at all —
        // the icon button is marked importantForAccessibility=no on those
        // Settings builds, so no tree walk can find it. For those devices
        // only, we fall back to a coordinate tap computed from the screen
        // resolution.
        //
        // The button's on-screen position was measured from a
        // 1080×2400 Pixel 8 running Android 14:
        //   - Horizontal center ≈ 66% of screen width
        //   - Vertical center   ≈ 28% of screen height
        //
        // The fallback only fires when:
        //   1. We are on a Google-Pixel device (manufacturer + model),
        //   2. The screen resolution matches a verified Pixel layout, and
        //   3. Every tree-based click strategy already failed.
        //
        // Remove this whole block once Pixel 8 Settings exposes the button
        // properly (or leave it in place forever; it's a no-op on other
        // devices).
        // -----------------------------------------------------------------
        private const val PIXEL_FALLBACK_REFERENCE_W = 1080f
        private const val PIXEL_FALLBACK_REFERENCE_H = 2400f
        private const val PIXEL_FALLBACK_X_PX = 778f
        private const val PIXEL_FALLBACK_Y_PX = 833f

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentProgress: String = ""
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var queue: MutableList<String> = mutableListOf()
    private var currentPackage: String? = null
    private var stage: Stage = Stage.IDLE

    private var clearedCount = 0
    private var skippedCount = 0
    private var sensitiveSkippedCount = 0
    private var totalTargets = 0

    private var settleRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null
    private var stageStartMs: Long = 0L

    // Adaptive settle: starts at ADAPTIVE_SETTLE_MIN_MS. Every time a
    // retry fires (because the tree wasn't ready), we step it up until
    // it caps at ADAPTIVE_SETTLE_MAX_MS. Reset to MIN at each new stage
    // and each new package so a slow app doesn't drag down the whole
    // session.
    private var adaptiveSettleMs: Long = ADAPTIVE_SETTLE_MIN_MS

    // Number of scroll attempts made during the current stage.
    private var scrollAttempts = 0

    // Tracks whether we already did a "disabled button" grace retry during
    // the current storage-screen visit. Reset at every beginStage().
    private val hasRetriedDisabled = AtomicBoolean(false)

    // Package of the Settings Activity that Android resolved when launching
    // ACTION_APPLICATION_DETAILS_SETTINGS. Safer than blindly accepting
    // every package containing the word "settings".
    private var expectedSettingsPackage: String? = null

    // Used to prevent repeated identical tree dumps from flooding Logcat.
    private val dumpedOnce = mutableSetOf<String>()

    private enum class Stage {
        IDLE,
        WAITING_FOR_APP_INFO,
        WAITING_FOR_STORAGE_SCREEN,
        RETURNING_HOME,
        DONE
    }

    /**
     * Result of a node search.
     * @param score higher = stronger match
     * @param reason useful for Logcat diagnostics
     */
    private data class NodeMatch(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val reason: String
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 50
        }

        Log.d(TAG, "Service connected. package=$packageName")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return

        // During a running session we only care about the Settings activity
        // that Android actually opened for us.
        if (!isSettingsLike(pkg)) return

        // Every content/window event resets the settle timer. This prevents
        // us from inspecting the tree while Settings is still building its UI.
        settleRunnable?.let { mainHandler.removeCallbacks(it) }
        settleRunnable = Runnable { onScreenSettled() }
        mainHandler.postDelayed(settleRunnable!!, adaptiveSettleMs)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        stopSession(broadcast = false)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES)
                    ?: return START_NOT_STICKY
                startSession(packages)
            }
            ACTION_STOP -> stopSession(broadcast = true)
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------------
    // Session management
    // ---------------------------------------------------------------------

    private fun startSession(packages: List<String>) {
        if (packages.isEmpty()) {
            broadcastDone()
            return
        }

        queue = packages.toMutableList()
        clearedCount = 0
        skippedCount = 0
        sensitiveSkippedCount = 0
        totalTargets = packages.size
        currentPackage = null
        expectedSettingsPackage = null
        isRunning = true
        dumpedOnce.clear()
        adaptiveSettleMs = ADAPTIVE_SETTLE_MIN_MS

        Log.d(TAG, "==================================================")
        Log.d(TAG, "SESSION START")
        Log.d(TAG, "Packages: ${packages.size}")
        Log.d(TAG, "==================================================")

        nextPackage()
    }

    private fun stopSession(broadcast: Boolean) {
        isRunning = false
        currentProgress = ""
        queue.clear()
        currentPackage = null
        expectedSettingsPackage = null
        stage = Stage.IDLE
        scrollAttempts = 0

        settleRunnable?.let { mainHandler.removeCallbacks(it) }
        settleRunnable = null
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null

        if (broadcast) broadcastDone()
    }

    private fun nextPackage() {
        if (queue.isEmpty()) {
            stage = Stage.DONE
            Log.d(TAG, "==================================================")
            Log.d(TAG, "SESSION FINISHED")
            Log.d(TAG, "Cleared=$clearedCount Skipped=$skippedCount " +
                    "SensitiveSkipped=$sensitiveSkippedCount")
            Log.d(TAG, "==================================================")

            returnToApp()
            stopSession(broadcast = true)
            return
        }

        currentPackage = queue.removeAt(0)
        scrollAttempts = 0
        adaptiveSettleMs = ADAPTIVE_SETTLE_MIN_MS

        val pkg = currentPackage ?: return

        // Defense in depth: never open Settings for a sensitive app, even
        // if something upstream put it in the queue. Skip silently and
        // move on. The user never sees their bank/2FA/wallet open.
        if (SensitivePackages.isSensitive(pkg)) {
            Log.w(TAG, "SKIPPING sensitive package: $pkg")
            skippedCount++
            sensitiveSkippedCount++
            val processed = clearedCount + skippedCount
            currentProgress = "$processed/$totalTargets $pkg"
            broadcastProgress()
            nextPackage()
            return
        }

        val processed = clearedCount + skippedCount + 1
        currentProgress = "$processed/$totalTargets $pkg"
        broadcastProgress()

        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "Opening App Info")
        Log.d(TAG, "Target package: $pkg")

        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Resolve the actual Activity/package Android will use. Important
        // for OEM Settings implementations.
        try {
            val resolved = packageManager.resolveActivity(intent, 0)
            expectedSettingsPackage = resolved?.activityInfo?.packageName
            Log.d(TAG, "Resolved Settings package: $expectedSettingsPackage")
        } catch (e: Exception) {
            expectedSettingsPackage = null
            Log.w(TAG, "Could not resolve Settings package", e)
        }

        try {
            startActivity(intent)
            beginStage(Stage.WAITING_FOR_APP_INFO)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open App Info for $pkg", e)
            skippedCount++
            nextPackage()
        }
    }

    private fun returnToApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
            Log.d(TAG, "Returned to Oh Shoot Cleaner")
        } catch (e: Exception) {
            Log.w(TAG, "Could not return to app", e)
        }
    }

    // ---------------------------------------------------------------------
    // Settings package detection
    // ---------------------------------------------------------------------

    /**
     * Returns true when the package belongs to the Settings UI we
     * intentionally launched. The resolved Settings package is preferred;
     * the additional package-name checks exist for OEM/helper accessibility
     * windows where the root package can differ.
     */
    private fun isSettingsLike(pkg: String): Boolean {
        if (pkg.isBlank()) return false

        val normalized = pkg.lowercase(Locale.ROOT)
        val expected = expectedSettingsPackage?.lowercase(Locale.ROOT)

        if (expected != null && normalized == expected) return true
        if (normalized == "com.android.settings") return true
        if (normalized == "com.samsung.android.settings") return true
        if (normalized.contains("settings")) return true

        if (normalized == "com.google.android.permissioncontroller") return true

        return false
    }

    // ---------------------------------------------------------------------
    // Stage handling
    // ---------------------------------------------------------------------

    private fun beginStage(newStage: Stage) {
        stage = newStage
        stageStartMs = System.currentTimeMillis()
        scrollAttempts = 0
        hasRetriedDisabled.set(false)
        adaptiveSettleMs = ADAPTIVE_SETTLE_MIN_MS

        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = Runnable { onStageTimeout() }
        mainHandler.postDelayed(watchdogRunnable!!, STAGE_TIMEOUT_MS)

        settleRunnable?.let { mainHandler.removeCallbacks(it) }
        settleRunnable = Runnable { onScreenSettled() }
        mainHandler.postDelayed(settleRunnable!!, adaptiveSettleMs)

        Log.d(TAG, "Stage -> $stage")
    }

    private fun onStageTimeout() {
        if (!isRunning) return

        val elapsed = System.currentTimeMillis() - stageStartMs

        Log.w(TAG, "==================================================")
        Log.w(TAG, "STAGE TIMEOUT")
        Log.w(TAG, "Stage: $stage")
        Log.w(TAG, "Package: $currentPackage")
        Log.w(TAG, "Elapsed: ${elapsed}ms")
        Log.w(TAG, "Scroll attempts: $scrollAttempts")

        val root = rootInActiveWindow
        if (root != null) {
            Log.w(TAG, "Root package: ${root.packageName}")
            dumpTextTree(root, "TIMEOUT_${currentPackage}_$stage")
        } else {
            Log.w(TAG, "rootInActiveWindow == null")
        }
        Log.w(TAG, "==================================================")

        skippedCount++
        returnHomeAndAdvance()
    }

    private fun onScreenSettled() {
        if (!isRunning) return

        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow == null. stage=$stage")
            retryCurrentStage()
            return
        }

        val rootPkg = root.packageName?.toString() ?: ""

        Log.d(TAG, "Screen settled")
        Log.d(TAG, "stage=$stage")
        Log.d(TAG, "target=$currentPackage")
        Log.d(TAG, "rootPkg=$rootPkg")

        if (!isSettingsLike(rootPkg)) {
            Log.d(TAG, "Root is not accepted Settings package")
            retryCurrentStage()
            return
        }

        when (stage) {
            Stage.WAITING_FOR_APP_INFO -> handleAppInfoScreen(root)
            Stage.WAITING_FOR_STORAGE_SCREEN -> handleStorageScreen(root)
            else -> { /* Nothing to do. */ }
        }
    }

    // ---------------------------------------------------------------------
    // App Info screen
    // ---------------------------------------------------------------------

    private fun handleAppInfoScreen(root: AccessibilityNodeInfo) {
        val match = findStorageNode(root)

        if (match == null) {
            Log.d(TAG, "Storage row not found")

            if (scrollSettingsPage(root)) return

            Log.w(TAG, "Storage row unavailable after scrolling")
            dumpTextTree(root, "APPINFO_${currentPackage}")
            retryCurrentStage()
            return
        }

        val target = match.node
        Log.d(TAG, "Storage match found")
        Log.d(TAG, "score=${match.score}")
        Log.d(TAG, "reason=${match.reason}")
        logNode("STORAGE_TARGET", target)

        if (!isSafeStorageTarget(target)) {
            Log.w(TAG, "Storage match failed safety validation")
            skippedCount++
            returnHomeAndAdvance()
            return
        }

        if (clickNode(target)) {
            beginStage(Stage.WAITING_FOR_STORAGE_SCREEN)
        } else {
            Log.w(TAG, "Click failed on Storage row")
            skippedCount++
            returnHomeAndAdvance()
        }
    }

    // ---------------------------------------------------------------------
    // Storage screen
    // ---------------------------------------------------------------------

    private fun handleStorageScreen(root: AccessibilityNodeInfo) {
        val match = findClearCacheNode(root)

        if (match == null) {
            Log.d(TAG, "Clear cache control not found")

            // NEW: on Pixel 8+ the label may be found but the button may
            // not be a clickable node at all. Before we burn scroll attempts
            // on it, try the fractional fallback immediately.
            if (isPixelFallbackApplicable()) {
                Log.w(TAG, "Clear cache label missing on Pixel fallback device — trying fractional tap directly")
                if (tapPixelClearCacheButton()) {
                    Log.d(TAG, "Pixel fractional tap dispatched (label missing path)")
                    clearedCount++
                    mainHandler.postDelayed({ returnHomeAndAdvance() }, CLICK_SETTLE_MS)
                    return
                }
            }

            if (scrollSettingsPage(root)) return

            Log.w(TAG, "Clear cache unavailable after scrolling")
            dumpTextTree(root, "STORAGE_${currentPackage}")
            retryCurrentStage()
            return
        }

        val target = match.node
        Log.d(TAG, "Clear cache match found")
        Log.d(TAG, "score=${match.score}")
        Log.d(TAG, "reason=${match.reason}")
        logNode("CLEAR_CACHE_TARGET", target)

        // Extremely important: never click a node that looks like a
        // destructive storage/data operation.
        if (isForbiddenNode(target)) {
            Log.e(TAG, "SAFETY BLOCK: target resembles destructive action")
            skippedCount++
            returnHomeAndAdvance()
            return
        }

        if (!target.isEnabled) {
            // Some Settings builds render the button before enabling it.
            // Give it one grace retry before deciding the cache is empty.
            if (!hasRetriedDisabled.get()) {
                Log.d(TAG, "Clear cache disabled on first look — retrying once")
                hasRetriedDisabled.set(true)
                mainHandler.postDelayed({ onScreenSettled() }, 700L)
                return
            }
            Log.d(TAG, "Clear cache still disabled after retry. Treating as empty.")
            skippedCount++
            returnHomeAndAdvance()
            return
        }

        Log.d(TAG, "Attempting Clear cache click")

        var success = clickNode(target)

        // NEW: if all tree-based click strategies fail, try the Pixel
        // fractional-tap fallback on supported devices only.
        if (!success && isPixelFallbackApplicable()) {
            Log.w(TAG, "Tree-based clicks failed — trying Pixel fractional fallback")
            success = tapPixelClearCacheButton()
        }

        if (success) {
            Log.d(TAG, "Clear cache click dispatched successfully")
            clearedCount++
        } else {
            Log.w(TAG, "All Clear cache click strategies failed")
            skippedCount++
        }

        mainHandler.postDelayed({ returnHomeAndAdvance() }, CLICK_SETTLE_MS)
    }

    // ---------------------------------------------------------------------
    // Pixel-8 fractional fallback
    // ---------------------------------------------------------------------

    /**
     * Returns true only on Google Pixel-family devices whose screen
     * resolution matches a layout we've verified. This guard prevents
     * the fractional tap from ever firing on a device whose Settings
     * layout we haven't measured — where it could hit the wrong button.
     */
    private fun isPixelFallbackApplicable(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)

        val isPixelFamily = manufacturer.contains("google") &&
                model.contains("pixel")
        if (!isPixelFamily) {
            Log.d(TAG, "Pixel fallback skipped — not a Pixel device " +
                    "(manufacturer=$manufacturer model=$model)")
            return false
        }

        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        // Verified layouts. Add more as testers confirm.
        val knownRes = (w == 1080 && h == 2400) ||   // Pixel 5 / 6 / 7 / 8
                (w == 1344 && h == 2992) ||           // Pixel 8 Pro
                (w == 1080 && h == 2340)              // Pixel 5 (smaller variant)
        if (!knownRes) {
            Log.d(TAG, "Pixel fallback skipped — unverified resolution ${w}x$h")
            return false
        }

        Log.d(TAG, "Pixel fallback applicable (${w}x$h on $model)")
        return true
    }

    /**
     * TEMPORARY Pixel-8 fallback: computes and taps the "Clear cache"
     * trash-icon button on the storage screen by fractional screen
     * coordinates. Only called after isPixelFallbackApplicable() returns
     * true AND all tree-based click strategies have failed.
     *
     * Coordinates measured from a 1080×2400 Pixel 8 screenshot:
     *   - The button is centred horizontally on the right half of the
     *     screen, at roughly 66% of the width.
     *   - Vertically it sits at roughly 28% of the height.
     */
    /**
     * TEMPORARY Pixel-8 fallback: dispatches a gesture tap at the
     * verified "Clear cache" trash-icon position on the tester's Pixel 8
     * (778, 833 on a 1080×2400 screen). Coordinates are proportionally
     * scaled to the runtime display size, so a Pixel 8 Pro or a Pixel 8
     * running with a different display setting still lands on the same
     * relative position — not the same absolute pixel.
     *
     * Only called after isPixelFallbackApplicable() returns true AND all
     * tree-based click strategies have failed.
     */
    private fun tapPixelClearCacheButton(): Boolean {
        val metrics = resources.displayMetrics
        val runtimeW = metrics.widthPixels.toFloat()
        val runtimeH = metrics.heightPixels.toFloat()

        val scaleX = runtimeW / PIXEL_FALLBACK_REFERENCE_W
        val scaleY = runtimeH / PIXEL_FALLBACK_REFERENCE_H

        val cx = PIXEL_FALLBACK_X_PX * scaleX
        val cy = PIXEL_FALLBACK_Y_PX * scaleY

        Log.w(
            TAG,
            "Pixel fallback tap: reference (${PIXEL_FALLBACK_X_PX.toInt()}, ${PIXEL_FALLBACK_Y_PX.toInt()}) " +
                    "on ${PIXEL_FALLBACK_REFERENCE_W.toInt()}x${PIXEL_FALLBACK_REFERENCE_H.toInt()} " +
                    "scaled to (${cx.toInt()}, ${cy.toInt()}) on ${runtimeW.toInt()}x${runtimeH.toInt()}"
        )

        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val dispatched = dispatchGesture(gesture, null, null)
            Log.w(TAG, "Pixel fallback tap dispatched=$dispatched")
            dispatched
        } catch (e: Exception) {
            Log.w(TAG, "Pixel fallback tap failed", e)
            false
        }
    }

    // ---------------------------------------------------------------------
    // Scrolling
    // ---------------------------------------------------------------------

    private fun scrollSettingsPage(root: AccessibilityNodeInfo): Boolean {
        if (scrollAttempts >= MAX_SCROLL_ATTEMPTS) {
            Log.d(TAG, "Maximum scroll attempts reached")
            return false
        }

        val scrollable = findScrollableNode(root)
        if (scrollable == null) {
            Log.d(TAG, "No scrollable Settings container found")
            return false
        }

        scrollAttempts++
        Log.d(TAG, "Scrolling Settings page. attempt=$scrollAttempts")

        val success = try {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (e: Exception) {
            Log.w(TAG, "Scroll action failed", e)
            false
        }

        if (success) {
            settleRunnable?.let { mainHandler.removeCallbacks(it) }
            settleRunnable = Runnable { onScreenSettled() }
            mainHandler.postDelayed(settleRunnable!!, adaptiveSettleMs)
        }

        return success
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = collectNodes(root)

        var best: AccessibilityNodeInfo? = null
        var bestArea = -1L

        for (node in nodes) {
            if (!node.isScrollable || !node.isVisibleToUser) continue

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val area = rect.width().toLong() * rect.height().toLong()

            if (area > bestArea) {
                bestArea = area
                best = node
            }
        }

        return best
    }

    // ---------------------------------------------------------------------
    // Storage detection
    // ---------------------------------------------------------------------

    private fun findStorageNode(root: AccessibilityNodeInfo): NodeMatch? {
        val nodes = collectNodes(root)
        var best: NodeMatch? = null

        for (node in nodes) {
            if (!node.isVisibleToUser) continue
            val match = scoreStorageNode(node) ?: continue
            if (best == null || match.score > best!!.score) best = match
        }

        best?.let {
            Log.d(TAG, "Best Storage candidate: score=${it.score} reason=${it.reason}")
        }

        return best
    }

    private fun scoreStorageNode(node: AccessibilityNodeInfo): NodeMatch? {
        val text = normalize(node.text?.toString())
        val description = normalize(node.contentDescription?.toString())
        val resourceId = normalizeResourceId(node.viewIdResourceName)

        val resourceScore = when {
            resourceId in STORAGE_RESOURCE_IDS_STRONG -> 100
            resourceId in STORAGE_RESOURCE_IDS_MEDIUM -> 85
            else -> 0
        }

        val textScore = if (text in STORAGE_ROW_LABELS_EXACT) 80 else 0
        val descScore = if (description in STORAGE_ROW_LABELS_EXACT) 78 else 0

        val genericStorageScore = when {
            text == "storage" -> 55
            description == "storage" -> 53
            else -> 0
        }

        var score = maxOf(resourceScore, textScore, descScore, genericStorageScore)
        if (score == 0) return null

        if (node.isClickable) score += 12
        if (node.isEnabled) score += 5
        if (isPreferenceLike(node)) score += 5

        if (containsForbiddenConcept(text) || containsForbiddenConcept(description)) return null

        val reason = buildString {
            if (resourceScore > 0) append("resourceId+$resourceScore ")
            if (textScore > 0) append("text+$textScore ")
            if (descScore > 0) append("description+$descScore ")
            if (genericStorageScore > 0) append("genericStorage+$genericStorageScore ")
            if (node.isClickable) append("clickable+12 ")
            if (node.isEnabled) append("enabled+5 ")
            if (isPreferenceLike(node)) append("preference+5")
        }.trim()

        return NodeMatch(node, score, reason)
    }

    // ---------------------------------------------------------------------
    // Clear cache detection
    // ---------------------------------------------------------------------

    private fun findClearCacheNode(root: AccessibilityNodeInfo): NodeMatch? {
        val nodes = collectNodes(root)
        var best: NodeMatch? = null

        for (node in nodes) {
            if (!node.isVisibleToUser) continue
            val match = scoreClearCacheNode(node) ?: continue
            if (best == null || match.score > best!!.score) best = match
        }

        best?.let {
            Log.d(TAG, "Best Clear cache candidate: score=${it.score} reason=${it.reason}")
        }

        return best
    }

    private fun scoreClearCacheNode(node: AccessibilityNodeInfo): NodeMatch? {
        val text = normalize(node.text?.toString())
        val description = normalize(node.contentDescription?.toString())
        val resourceId = normalizeResourceId(node.viewIdResourceName)

        if (isForbiddenNode(node)) return null

        val resourceScore = when {
            resourceId in CLEAR_CACHE_RESOURCE_IDS_STRONG -> 110
            resourceId in CLEAR_CACHE_RESOURCE_IDS_MEDIUM -> 95
            else -> 0
        }
        val textScore = if (text in CLEAR_CACHE_LABELS_EXACT) 90 else 0
        val descScore = if (description in CLEAR_CACHE_LABELS_EXACT) 88 else 0

        var score = maxOf(resourceScore, textScore, descScore)
        if (score == 0) return null

        if (node.isClickable) score += 15
        if (node.isEnabled) score += 5
        if (isButtonLike(node)) score += 5

        val reason = buildString {
            if (resourceScore > 0) append("resourceId+$resourceScore ")
            if (textScore > 0) append("text+$textScore ")
            if (descScore > 0) append("description+$descScore ")
            if (node.isClickable) append("clickable+15 ")
            if (node.isEnabled) append("enabled+5 ")
            if (isButtonLike(node)) append("button+5")
        }.trim()

        return NodeMatch(node, score, reason)
    }

    // ---------------------------------------------------------------------
    // Safety validation
    // ---------------------------------------------------------------------

    private fun isSafeStorageTarget(node: AccessibilityNodeInfo): Boolean {
        val text = normalize(node.text?.toString())
        val desc = normalize(node.contentDescription?.toString())

        if (containsForbiddenConcept(text) || containsForbiddenConcept(desc)) {
            Log.w(TAG, "Storage target contains forbidden wording")
            return false
        }

        val id = normalizeResourceId(node.viewIdResourceName)
        val knownId = id in STORAGE_RESOURCE_IDS_STRONG || id in STORAGE_RESOURCE_IDS_MEDIUM
        val knownText = text in STORAGE_ROW_LABELS_EXACT || desc in STORAGE_ROW_LABELS_EXACT

        if (!knownId && !knownText) {
            Log.w(TAG, "Storage target failed positive identification")
            return false
        }

        return true
    }

    private fun isForbiddenNode(node: AccessibilityNodeInfo): Boolean {
        val text = normalize(node.text?.toString())
        val desc = normalize(node.contentDescription?.toString())
        val id = normalizeResourceId(node.viewIdResourceName)

        if (id in FORBIDDEN_RESOURCE_IDS) return true
        if (text in FORBIDDEN_LABELS_EXACT) return true
        if (desc in FORBIDDEN_LABELS_EXACT) return true

        if (containsDestructiveStorageCombination(text) ||
            containsDestructiveStorageCombination(desc)
        ) {
            return true
        }

        return false
    }

    private fun containsForbiddenConcept(value: String): Boolean {
        if (value.isBlank()) return false
        return value in FORBIDDEN_LABELS_EXACT || containsDestructiveStorageCombination(value)
    }

    private fun containsDestructiveStorageCombination(value: String): Boolean {
        if (value.isBlank()) return false

        if (value.contains("clear storage") || value.contains("clear data") ||
            value.contains("delete data") || value.contains("erase data")
        ) return true

        if (value.contains("speicher löschen") || value.contains("speicherplatz löschen") ||
            value.contains("daten löschen")
        ) return true

        if (value.contains("borrar almacenamiento") || value.contains("borrar datos")) return true
        if (value.contains("effacer le stockage") || value.contains("effacer les données")) return true
        if (value.contains("cancella archiviazione") || value.contains("cancella dati")) return true
        if (value.contains("limpar armazenamento") || value.contains("limpar dados")) return true
        if (value.contains("gegevens wissen") || value.contains("opslag wissen")) return true
        if (value.contains("wyczyść dane") || value.contains("wyczyść pamięć")) return true

        return false
    }

    // ---------------------------------------------------------------------
    // Node traversal
    // ---------------------------------------------------------------------

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            try {
                node.refresh()
            } catch (_: Exception) {
                // Some OEM AccessibilityNodeInfo implementations can throw
                // during refresh. The existing snapshot is still useful.
            }

            result.add(node)

            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }

        return result
    }

    // ---------------------------------------------------------------------
    // Click handling
    // ---------------------------------------------------------------------

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // Strategy 1: direct click.
        if (node.isClickable && node.isEnabled &&
            try { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        ) {
            Log.d(TAG, "Click strategy 1: direct ACTION_CLICK")
            return true
        }

        // Strategy 2: clickable ancestor.
        val ancestor = findClickableAncestor(node)
        if (ancestor != null && ancestor.isEnabled) {
            val success = try {
                ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) { false }

            if (success) {
                Log.d(TAG, "Click strategy 2: clickable ancestor")
                return true
            }
        }

        // Strategy 3: clickable sibling that geometrically contains the
        // label. Works when the button wraps its text.
        val sibling = findClickableSibling(node)
        if (sibling != null && sibling.isEnabled) {
            val success = try {
                sibling.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) { false }

            if (success) {
                Log.d(TAG, "Click strategy 3: clickable sibling/row")
                return true
            }
        }

        // Strategy 4: proximity click. Pixel 8 & newer Material You
        // Settings render icon buttons with the label as a SEPARATE
        // sibling underneath. The button is close by, but doesn't contain
        // the text. Find the nearest clickable node and click it.
        val near = findNearestClickable(node)
        if (near != null && near.isEnabled) {
            val success = try {
                near.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) { false }

            if (success) {
                Log.d(TAG, "Click strategy 4: proximity click on sibling button")
                return true
            }
            // If ACTION_CLICK fails, tap its center coordinates.
            if (tapCenterOf(near)) {
                Log.d(TAG, "Click strategy 4b: proximity gesture on sibling button")
                return true
            }
        }

        // Strategy 5: coordinate gesture on the label itself.
        Log.d(TAG, "Click strategy 5: coordinate gesture on label")
        return tapCenterOf(node)
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0

        while (current != null && depth < 10) {
            if (current.isClickable && current.isEnabled) return current
            current = try { current.parent } catch (_: Exception) { null }
            depth++
        }

        return null
    }

    private fun findClickableSibling(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targetRect = Rect()
        node.getBoundsInScreen(targetRect)
        if (targetRect.isEmpty) return null

        val parent = try { node.parent } catch (_: Exception) { null } ?: return null
        val childCount = try { parent.childCount } catch (_: Exception) { 0 }

        var best: AccessibilityNodeInfo? = null
        var bestArea = Long.MAX_VALUE

        for (i in 0 until childCount) {
            val child = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
            if (child == node) continue
            if (!child.isClickable || !child.isEnabled) continue

            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (rect.isEmpty) continue

            if (rect.contains(targetRect.centerX(), targetRect.centerY())) {
                val area = rect.width().toLong() * rect.height().toLong()
                if (area < bestArea) {
                    bestArea = area
                    best = child
                }
            }
        }

        return best
    }

    /**
     * Finds the closest enabled, clickable, visible node to [node] within
     * PROXIMITY_CLICK_MAX_PX. Used on Pixel 8 (and likely future Material
     * You Settings) where the icon button and its text label are siblings
     * that don't geometrically contain each other.
     *
     * Distance metric: vertical gap + horizontal gap between the two
     * bounding boxes, in pixels. Smaller = closer. We prefer nodes that
     * overlap horizontally (i.e. lie roughly above/below the label).
     */
    private fun findNearestClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targetRect = Rect()
        node.getBoundsInScreen(targetRect)
        if (targetRect.isEmpty) return null

        val root = rootInActiveWindow ?: return null
        val all = collectNodes(root)

        var best: AccessibilityNodeInfo? = null
        var bestScore = Long.MAX_VALUE

        for (candidate in all) {
            if (candidate == node) continue
            if (!candidate.isClickable || !candidate.isEnabled) continue
            if (!candidate.isVisibleToUser) continue
            // Skip nodes that are themselves the "Clear storage" button or
            // any other destructive-looking clickable.
            if (isForbiddenNode(candidate)) continue

            val rect = Rect()
            candidate.getBoundsInScreen(rect)
            if (rect.isEmpty) continue

            // Horizontal overlap check: the candidate's x-range must
            // intersect the target's x-range. This avoids matching a
            // button on the opposite side of the screen.
            val xOverlap = minOf(targetRect.right, rect.right) - maxOf(targetRect.left, rect.left)
            if (xOverlap <= 0) continue

            // Vertical gap between the two rectangles. Zero if they touch.
            val vGap = when {
                rect.bottom < targetRect.top -> targetRect.top - rect.bottom
                rect.top > targetRect.bottom -> rect.top - targetRect.bottom
                else -> 0
            }.toLong()

            if (vGap > PROXIMITY_CLICK_MAX_PX) continue

            // Prefer smaller vertical gap; break ties by smaller area
            // (icon buttons are small, and we don't want a huge row).
            val area = rect.width().toLong() * rect.height().toLong()
            val score = vGap * 1_000_000L + area
            if (score < bestScore) {
                bestScore = score
                best = candidate
            }
        }

        best?.let { b ->
            val r = Rect()
            b.getBoundsInScreen(r)
            Log.d(TAG, "Nearest clickable to label: bounds=$r class=${b.className}")
        }
        return best
    }

    private fun tapCenterOf(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) {
            Log.w(TAG, "Cannot gesture-click empty bounds: $rect")
            return false
        }

        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val dispatched = dispatchGesture(gesture, null, null)
            Log.d(TAG, "Gesture dispatched=$dispatched at ($cx,$cy) bounds=$rect")
            dispatched
        } catch (e: Exception) {
            Log.w(TAG, "dispatchGesture failed", e)
            false
        }
    }

    // ---------------------------------------------------------------------
    // Return navigation
    // ---------------------------------------------------------------------

    private fun returnHomeAndAdvance() {
        if (!isRunning) return

        stage = Stage.RETURNING_HOME
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null

        Log.d(TAG, "Returning from Settings")

        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({
                    stage = Stage.IDLE
                    nextPackage()
                }, BACK_DELAY_MS)
            }, BACK_DELAY_MS)
        }, BACK_DELAY_MS)
    }

    // ---------------------------------------------------------------------
    // Retry handling
    // ---------------------------------------------------------------------

    private fun retryCurrentStage() {
        if (!isRunning) return

        // A retry means the tree wasn't ready last time. Step the settle
        // window up so the next event-driven inspection has more room.
        adaptiveSettleMs = (adaptiveSettleMs + ADAPTIVE_SETTLE_STEP_MS)
            .coerceAtMost(ADAPTIVE_SETTLE_MAX_MS)

        settleRunnable?.let { mainHandler.removeCallbacks(it) }
        settleRunnable = Runnable { onScreenSettled() }
        mainHandler.postDelayed(settleRunnable!!, RETRY_MS)
    }

    // ---------------------------------------------------------------------
    // Node utility helpers
    // ---------------------------------------------------------------------

    private fun normalize(value: String?): String {
        if (value == null) return ""
        return value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    private fun normalizeResourceId(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.substringAfterLast('/').trim().lowercase(Locale.ROOT)
    }

    private fun isPreferenceLike(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        return className.contains("preference") ||
                className.contains("recyclerview") ||
                className.contains("linearlayout")
    }

    private fun isButtonLike(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        return className.contains("button") ||
                className.contains("textview") ||
                className.contains("preference")
    }

    private fun logNode(prefix: String, node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        Log.d(
            TAG,
            "$prefix text='${node.text}' desc='${node.contentDescription}' " +
                    "id='${node.viewIdResourceName}' class='${node.className}' " +
                    "click=${node.isClickable} enabled=${node.isEnabled} " +
                    "visible=${node.isVisibleToUser} scroll=${node.isScrollable} bounds=$rect"
        )
    }

    // ---------------------------------------------------------------------
    // Diagnostic tree dump
    // ---------------------------------------------------------------------

    private fun dumpTextTree(root: AccessibilityNodeInfo, key: String) {
        if (!dumpedOnce.add(key)) {
            Log.d(TAG, "Tree already dumped for $key")
            return
        }

        Log.d(TAG, "==================================================")
        Log.d(TAG, "TREE DUMP: $key")
        Log.d(TAG, "==================================================")

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        var emitted = 0
        var visited = 0

        while (queue.isNotEmpty() && emitted < 400 && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            try {
                node.refresh()
            } catch (_: Exception) { }

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val id = normalizeResourceId(node.viewIdResourceName)
            val className = node.className?.toString() ?: ""

            if (!text.isNullOrEmpty() || !desc.isNullOrEmpty() || id.isNotEmpty()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val indent = "  ".repeat(depth.coerceAtMost(10))

                Log.d(
                    TAG,
                    "$indent" +
                            "t='${text ?: ""}' d='${desc ?: ""}' id='$id' class='$className' " +
                            "click=${node.isClickable} en=${node.isEnabled} " +
                            "visible=${node.isVisibleToUser} scroll=${node.isScrollable} bounds=$rect"
                )
                emitted++
            }

            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child to depth + 1)
            }
        }

        Log.d(TAG, "TREE DUMP END: visited=$visited emitted=$emitted")
        Log.d(TAG, "==================================================")
    }

    // ---------------------------------------------------------------------
    // Broadcasts
    // ---------------------------------------------------------------------

    private fun broadcastProgress() {
        sendBroadcast(
            Intent(ACTION_PROGRESS).apply {
                setPackage(packageName)
                putExtra("progress", currentProgress)
                putExtra("cleared", clearedCount)
                putExtra("skipped", skippedCount)
                putExtra("sensitive_skipped", sensitiveSkippedCount)
            }
        )
    }

    private fun broadcastDone() {
        val total = clearedCount + skippedCount
        val failureRate = if (total > 0) skippedCount.toFloat() / total else 0f

        sendBroadcast(
            Intent(ACTION_DONE).apply {
                setPackage(packageName)
                putExtra("cleared", clearedCount)
                putExtra("skipped", skippedCount)
                putExtra("sensitive_skipped", sensitiveSkippedCount)
                putExtra("mass_failure", failureRate > 0.66f && total >= 3)
            }
        )

        currentProgress = ""
    }

    // ---------------------------------------------------------------------
    // Resource IDs
    // ---------------------------------------------------------------------

    private val STORAGE_RESOURCE_IDS_STRONG = setOf(
        "storage_settings", "storage_preference", "storage_usage",
        "storage_and_cache", "storage_cache", "app_storage",
        "app_storage_settings", "storage_section"
    )

    private val STORAGE_RESOURCE_IDS_MEDIUM = setOf(
        "storage_category", "storage_used", "storage_summary",
        "storage_info", "storage_row"
    )

    private val CLEAR_CACHE_RESOURCE_IDS_STRONG = setOf(
        "clear_cache", "clear_cache_button", "button_clear_cache",
        "clearcache", "clearcachebutton", "cache_clear",
        "cache_clear_button", "clear_cache_btn", "clear_cache_button_text"
    )

    private val CLEAR_CACHE_RESOURCE_IDS_MEDIUM = setOf(
        "clear_cache_btn_text", "cache_clear_text", "cache_button", "cache_clear_action"
    )

    private val FORBIDDEN_RESOURCE_IDS = setOf(
        "clear_storage", "clear_storage_button", "button_clear_storage",
        "clear_data", "clear_data_button", "button_clear_data",
        "delete_data", "delete_storage", "erase_data"
    )

    // ---------------------------------------------------------------------
    // Localized Storage labels
    // ---------------------------------------------------------------------

    private val STORAGE_ROW_LABELS_EXACT = setOf(
        "storage & cache", "storage and cache", "storage",
        "speicherplatz", "speicher", "speicher & cache", "speicher und cache",
        "almacenamiento y caché", "almacenamiento",
        "stockage et cache", "stockage",
        "archiviazione e cache", "archiviazione",
        "armazenamento e cache", "armazenamento",
        "opslag en cache", "opslag",
        "pamięć i pamięć podręczna", "pamięć",
        "depolama ve önbellek", "depolama",
        "stocare și cache", "stocare",
        "úložiště a mezipaměť", "úložiště",
        "úložisko a vyrovnávacia pamäť", "úložisko",
        "tárhely és gyorsítótár", "tárhely",
        "lagring och cache", "lagring",
        "lager og cache", "lager",
        "lagring og buffer", "lagring",
        "tallennustila ja välimuisti", "tallennustila",
        "αποθηκευτικός χώρος και προσωρινή μνήμη", "αποθηκευτικός χώρος",
        "память и кеш", "хранилище", "память",
        "пам’ять і кеш", "пам'ять і кеш", "сховище", "пам’ять", "пам'ять",
        "ストレージとキャッシュ", "ストレージ",
        "저장공간 및 캐시", "저장공간",
        "存储和缓存", "存储空间", "存储",
        "儲存空間與快取", "儲存空間", "儲存",
        "التخزين وذاكرة التخزين المؤقت", "التخزين",
        "אחסון ומטמון", "אחסון",
        "penyimpanan dan cache", "penyimpanan",
        "bộ nhớ và bộ nhớ đệm", "bộ nhớ",
        "พื้นที่เก็บข้อมูลและแคช", "พื้นที่เก็บข้อมูล"
    )

    // ---------------------------------------------------------------------
    // Localized Clear Cache labels
    // ---------------------------------------------------------------------

    private val CLEAR_CACHE_LABELS_EXACT = setOf(
        "clear cache",
        "cache leeren", "cache löschen",
        "borrar caché", "limpiar caché",
        "vider le cache", "effacer le cache",
        "cancella cache", "svuota cache",
        "limpar cache", "apagar cache",
        "cache wissen", "cache legen",
        "wyczyść pamięć podręczną",
        "önbelleği temizle",
        "șterge memoria cache", "golește memoria cache",
        "vymazat mezipaměť",
        "vymazať vyrovnávaciu pamäť",
        "gyorsítótár törlése",
        "rensa cache",
        "ryd cache",
        "tøm hurtigbuffer",
        "tyhjennä välimuisti",
        "εκκαθάριση προσωρινής μνήμης",
        "очистить кеш",
        "очистити кеш",
        "キャッシュを削除",
        "캐시 삭제",
        "清除缓存",
        "清除快取",
        "مسح ذاكرة التخزين المؤقت",
        "נקה מטמון",
        "hapus cache",
        "xóa bộ nhớ đệm",
        "ล้างแคช"
    )

    // ---------------------------------------------------------------------
    // Destructive labels
    // ---------------------------------------------------------------------

    private val FORBIDDEN_LABELS_EXACT = setOf(
        "clear storage", "clear data", "delete data", "erase data",
        "speicher löschen", "speicherplatz löschen", "daten löschen",
        "borrar almacenamiento", "borrar datos",
        "effacer le stockage", "effacer les données",
        "cancella archiviazione", "cancella dati",
        "limpar armazenamento", "limpar dados",
        "opslag wissen", "gegevens wissen",
        "wyczyść pamięć", "wyczyść dane",
        "depolamayı temizle", "verileri temizle",
        "șterge spațiul de stocare", "șterge datele",
        "vymazat úložiště", "vymazat data",
        "vymazať úložisko", "vymazať údaje",
        "tárhely törlése", "adatok törlése",
        "rensa lagring", "rensa data",
        "ryd lager", "ryd data",
        "tøm lagring", "slett data",
        "tyhjennä tallennustila", "poista tiedot",
        "διαγραφή αποθηκευτικού χώρου", "διαγραφή δεδομένων",
        "очистить хранилище", "удалить данные",
        "очистити сховище", "видалити дані",
        "ストレージを消去", "データを消去",
        "저장공간 삭제", "데이터 삭제",
        "清除存储空间", "清除数据",
        "清除儲存空間", "清除資料",
        "مسح مساحة التخزين", "مسح البيانات",
        "נקה אחסון", "נקה נתונים",
        "hapus penyimpanan", "hapus data",
        "xóa bộ nhớ", "xóa dữ liệu",
        "ล้างพื้นที่เก็บข้อมูล", "ล้างข้อมูล"
    )
}