package com.example.freshscan.util

import java.util.Calendar

/**
 * Time-related utility functions (v4.2).
 */
object TimeUtils {

    /**
     * Get the start of the current week (Monday 00:00:00) in milliseconds.
     */
    fun getWeekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        val daysSinceMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        return cal.timeInMillis
    }
}
