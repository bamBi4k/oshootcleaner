package com.example.oshootcleaner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.io.RandomAccessFile

data class MemoryStats(
    val usedBytes: Long,
    val availBytes: Long,
    val totalBytes: Long,
    val lowMemory: Boolean
) {
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class StorageStats(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long
) {
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class BatteryStats(
    val percent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Double?
)

/** Null loadFraction means genuinely unavailable, not zero. */
data class CpuStats(val loadFraction: Float?)

data class ThermalInfo(val status: Int, val label: String)

/**
 * Battery charge state. `chargeCounterUah` is what's currently in the
 * battery; `fullCapacityUah` is inferred from the current percentage so we
 * can draw a "how full" bar. Both null when the OS doesn't expose them.
 */
data class ChargeInfo(
    val chargeCounterUah: Long?,
    val fullCapacityUah: Long?,
    val currentMa: Int?
) {
    val fractionRemaining: Float
        get() {
            val c = chargeCounterUah
            val f = fullCapacityUah
            if (c == null || f == null || f <= 0L) return 0f
            return (c.toFloat() / f.toFloat()).coerceIn(0f, 1f)
        }
}

object SystemStats {

    fun memory(context: Context): MemoryStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        return MemoryStats(used, info.availMem, info.totalMem, info.lowMemory)
    }

    fun storage(): StorageStats {
        val stat = StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageStats(total - free, free, total)
    }

    fun battery(context: Context): BatteryStats {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let {
            if (it in 0..100) it else {
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            }
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempTenths >= 0) tempTenths / 10.0 else null

        return BatteryStats(percent, isCharging, tempC)
    }

    /**
     * Battery charge state. `chargeCounterUah` is the current charge in
     * microamp-hours (what's physically in the battery right now).
     *
     * `fullCapacityUah` is inferred: if the counter says X mAh and the OS
     * says we're at P%, then full ≈ X * 100 / P. This is close enough for a
     * progress bar but not a substitute for a hardware fuel gauge reading.
     *
     * Both values are null on devices that don't expose them.
     */
    fun chargeInfo(context: Context): ChargeInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val counterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0L }

        val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE && it != 0 }

        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val inferredFull: Long? = if (counterUah != null && percent in 1..100) {
            (counterUah * 100L / percent.toLong()).takeIf { it > 0L }
        } else null

        return ChargeInfo(
            chargeCounterUah = counterUah,
            fullCapacityUah = inferredFull,
            currentMa = currentNowUa?.let { it / 1000 }
        )
    }

    /**
     * Best-effort system-wide CPU load. Since Android 8, /proc/stat is
     * unreadable to non-system apps on virtually every production device,
     * so this returns null far more often than a value.
     */
    fun cpuLoad(): CpuStats {
        return try {
            val first = readProcStatCpuLine() ?: return CpuStats(null)
            Thread.sleep(360)
            val second = readProcStatCpuLine() ?: return CpuStats(null)

            val idleDelta = second.idle - first.idle
            val totalDelta = second.total - first.total
            if (totalDelta <= 0) return CpuStats(null)

            val load = 1f - (idleDelta.toFloat() / totalDelta.toFloat())
            CpuStats(load.coerceIn(0f, 1f))
        } catch (e: Exception) {
            CpuStats(null)
        }
    }

    private data class ProcStatSample(val idle: Long, val total: Long)

    private fun readProcStatCpuLine(): ProcStatSample? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                val line = file.readLine() ?: return null
                if (!line.startsWith("cpu ")) return null
                val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (fields.size < 4) return null
                val idle = fields[3] + (fields.getOrElse(4) { 0L })
                val total = fields.sum()
                ProcStatSample(idle, total)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun batteryHealth(context: Context): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter)
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }

    fun batteryStatusLabel(context: Context): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter)
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Charging (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
            else -> "On battery"
        }
    }

    /**
     * System thermal status. Available API 29+. This is a far more useful
     * "is my phone slow right now" signal than CPU% — it's what Android
     * itself uses to decide when to throttle.
     */
    fun thermal(context: Context): ThermalInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalInfo(-1, "Not supported")
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = pm.currentThermalStatus
        val label = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
        return ThermalInfo(status, label)
    }

    /** Device uptime in milliseconds since boot (includes deep sleep). */
    fun uptimeMs(): Long = SystemClock.elapsedRealtime()
}

fun formatBytesGb(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return "%.1f GB".format(gb)
}

/**
 * Human-readable size that picks the right unit for the magnitude.
 *   0–1023 B:      "512 B"
 *   1 KB – 1 MB:   "714 KB"
 *   1 MB – 1 GB:   "7.4 MB"
 *   1 GB+:         "0.71 GB"
 */
fun formatBytesSmart(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}