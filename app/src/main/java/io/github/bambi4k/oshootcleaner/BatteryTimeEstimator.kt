package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.os.BatteryManager

/**
 * Result of the battery-time estimation. Only one of the two modes is
 * ever active at a time:
 *
 *   - [estimateAvailable] == true: shows an estimate of how long until
 *     the battery runs out (or is full). Derived from a live fuel gauge
 *     reading, averaged over a short rolling window.
 *   - [estimateAvailable] == false: shows how long ago the phone was last
 *     at 100%. Fallback for devices without a working fuel gauge.
 */
data class BatteryTimeEstimate(
    val estimateAvailable: Boolean,
    val minutesRemaining: Int,
    val isCharging: Boolean,
    val minutesToFull: Int,
    val lastFullChargeMs: Long,
    val batteryPercent: Int
) {
    val fractionForBar: Float
        get() = (batteryPercent.coerceIn(0, 100)) / 100f

    fun primaryLabel(context: Context): String {
        if (estimateAvailable) {
            val minutes = if (isCharging) minutesToFull else minutesRemaining
            return "≈ " + formatMinutes(minutes) + " " + context.getString(
                if (isCharging) R.string.battery_time_to_full
                else R.string.battery_time_to_empty
            )
        }
        if (lastFullChargeMs > 0L) {
            return formatLastFullCharge(context, lastFullChargeMs)
        }
        return context.getString(R.string.battery_time_na)
    }

    fun secondaryLabel(context: Context): String {
        return if (estimateAvailable) {
            context.getString(R.string.battery_time_live_hint)
        } else if (lastFullChargeMs > 0L) {
            context.getString(R.string.battery_time_last_full_subtitle)
        } else {
            context.getString(R.string.battery_time_last_full_unavailable)
        }
    }
}

private fun formatMinutes(min: Int): String {
    if (min <= 0) return "—"
    val h = min / 60
    val m = min % 60
    return when {
        h <= 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun formatLastFullCharge(context: Context, ts: Long): String {
    val delta = System.currentTimeMillis() - ts
    val min = delta / 60_000
    val hr = min / 60
    val day = hr / 24
    val ago = context.getString(R.string.battery_time_ago)
    return when {
        day >= 2 -> "$day d $ago"
        day >= 1 -> "1 d $ago"
        hr >= 1 -> "$hr h $ago"
        min >= 1 -> "$min min $ago"
        else -> context.getString(R.string.battery_time_just_now)
    }
}

object BatteryTimeEstimator {

    /**
     * Time-weighted moving average of recent CURRENT_NOW readings.
     * Samples older than SMOOTHING_WINDOW_MS are dropped on each compute().
     * The newest sample gets weight 1.0, the oldest gets weight ~0.05.
     */
    private const val SMOOTHING_WINDOW_MS = 30_000L

    private data class Sample(val ua: Long, val atMs: Long)
    private val samples = ArrayDeque<Sample>()

    @Volatile private var lastCounterSampleMs: Long = 0L
    @Volatile private var lastCounterUah: Long = 0L

    /**
     * Returns a conservative time estimate. Real estimate only when the
     * device's fuel gauge is genuinely usable:
     *   1. CHARGE_COUNTER implies a plausible design capacity (2-8 Ah).
     *   2. CURRENT_NOW is in a plausible range (50 mA to 15 A).
     *   3. The counter has moved between the two most recent samples
     *      (i.e., it's not frozen).
     */
    fun compute(context: Context): BatteryTimeEstimate {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)

        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargeCounterUah = bm.getLongProperty(
            BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
        )
        val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val lastFull = LastFullChargeStore.lastFullMs(context)

        // Check 1: counter implies a plausible design capacity (2-8 Ah)
        val inferredFullUah: Long? = if (chargeCounterUah > 0 && percent > 0) {
            val full = chargeCounterUah * 100L / percent
            if (full in 2_000_000L..8_000_000L) full else null
        } else null

        // Check 2: current in physically plausible range
        val absCurrentUa = kotlin.math.abs(currentNowUa.toLong())
        val currentSane = currentNowUa != Int.MIN_VALUE &&
                absCurrentUa in 50_000L..15_000_000L

        // Check 3: counter must be moving (not frozen)
        val now = System.currentTimeMillis()
        val counterMoving = isCounterMoving(chargeCounterUah, now)

        val estimable = inferredFullUah != null && currentSane && counterMoving

        if (!estimable) {
            return BatteryTimeEstimate(
                estimateAvailable = false,
                minutesRemaining = 0,
                isCharging = charging,
                minutesToFull = 0,
                lastFullChargeMs = lastFull,
                batteryPercent = percent
            )
        }

        // ---- Time-weighted smoothing ----
        samples.addLast(Sample(absCurrentUa, now))
        while (samples.isNotEmpty() && now - samples.first().atMs > SMOOTHING_WINDOW_MS) {
            samples.removeFirst()
        }

        val smoothedCurrentUa = if (samples.isEmpty()) {
            absCurrentUa
        } else {
            var weightedSum = 0.0
            var weightTotal = 0.0
            val newestMs = samples.last().atMs
            samples.forEach { s ->
                val ageMs = newestMs - s.atMs
                val rawWeight = 1.0 - (ageMs.toDouble() / SMOOTHING_WINDOW_MS.toDouble())
                val safeWeight = rawWeight.coerceAtLeast(0.05)
                weightedSum += s.ua.toDouble() * safeWeight
                weightTotal += safeWeight
            }
            if (weightTotal > 0.0) (weightedSum / weightTotal).toLong() else absCurrentUa
        }.coerceAtLeast(50_000L)

        val fullUah = inferredFullUah!!

        val minutesRemaining = (chargeCounterUah.toDouble() / smoothedCurrentUa.toDouble() * 60.0)
            .toInt()
            .coerceIn(0, 60 * 24 * 7)

        val toFullUah = (fullUah - chargeCounterUah).coerceAtLeast(0L)
        val minutesToFull = (toFullUah.toDouble() / smoothedCurrentUa.toDouble() * 60.0)
            .toInt()
            .coerceIn(0, 60 * 24)

        return BatteryTimeEstimate(
            estimateAvailable = true,
            minutesRemaining = minutesRemaining,
            isCharging = charging,
            minutesToFull = minutesToFull,
            lastFullChargeMs = lastFull,
            batteryPercent = percent
        )
    }

    /**
     * Compares the charge counter against a sample taken at least 3
     * seconds ago. If they differ, the counter is live. If they're
     * identical, we assume the fuel gauge is frozen.
     */
    private fun isCounterMoving(currentUah: Long, now: Long): Boolean {
        val prevMs = lastCounterSampleMs
        val prevUah = lastCounterUah
        lastCounterSampleMs = now
        lastCounterUah = currentUah
        if (prevMs == 0L) return true
        val elapsed = now - prevMs
        if (elapsed < 3_000L) return true
        return currentUah != prevUah
    }
}