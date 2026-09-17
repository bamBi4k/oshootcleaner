package com.example.oshootcleaner

import android.content.Context

data class DataPlan(
    val capBytes: Long,
    val cycleStartDay: Int  // 1..28
) {
    val hasCap: Boolean get() = capBytes > 0

    companion object {
        val UNLIMITED = DataPlan(capBytes = 0L, cycleStartDay = 1)

        private const val PREFS = "oh_shoot_prefs"
        private const val KEY_CAP = "data_plan_cap_bytes"
        private const val KEY_CYCLE_DAY = "data_plan_cycle_day"

        fun load(context: Context): DataPlan {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return DataPlan(
                capBytes = p.getLong(KEY_CAP, 0L),
                cycleStartDay = p.getInt(KEY_CYCLE_DAY, 1).coerceIn(1, 28)
            )
        }

        fun save(context: Context, plan: DataPlan) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CAP, plan.capBytes)
                .putInt(KEY_CYCLE_DAY, plan.cycleStartDay.coerceIn(1, 28))
                .commit()
        }
    }
}

data class DataPlanUsage(
    val usedBytes: Long,
    val capBytes: Long,
    val wifiBytes: Long,
    val available: Boolean
) {
    val hasCap: Boolean get() = capBytes > 0
    val fraction: Float
        get() = if (capBytes > 0) (usedBytes.toFloat() / capBytes).coerceIn(0f, 1f) else 0f
    val remainingBytes: Long get() = (capBytes - usedBytes).coerceAtLeast(0L)
    val overCap: Boolean get() = capBytes > 0 && usedBytes > capBytes

    companion object {
        val UNAVAILABLE = DataPlanUsage(0, 0, 0, available = false)
    }
}

object DataPlanQuerier {

    fun query(context: Context): DataPlanUsage {
        val plan = DataPlan.load(context)
        val net = NetworkStats.deviceWide(context)
        if (!net.available) return DataPlanUsage.UNAVAILABLE

        val (mobileBytes, wifiBytes) = queryCycle(context, plan.cycleStartDay)

        return DataPlanUsage(
            usedBytes = mobileBytes,
            capBytes = plan.capBytes,
            wifiBytes = wifiBytes,
            available = true
        )
    }

    private fun queryCycle(context: Context, cycleStartDay: Int): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
        if (today < cycleStartDay) {
            cal.add(java.util.Calendar.MONTH, -1)
        }
        cal.set(java.util.Calendar.DAY_OF_MONTH, cycleStartDay)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        val endMs = System.currentTimeMillis()

        return NetworkStats.queryWindow(context, startMs, endMs)
    }
}