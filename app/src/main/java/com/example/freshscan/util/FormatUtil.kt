package com.example.freshscan.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formatting utilities for timestamps, confidence scores, etc.
 */
object FormatUtil {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateWithoutYearFormat = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

    /**
     * Format a Unix timestamp (ms) to a human-readable relative time string.
     *
     * Examples: "刚刚", "3 分钟前", "今天 14:23", "昨天 09:15", "6月12日 09:15"
     */
    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000} 分钟前"
            isSameDay(timestamp) -> "今天 ${timeFormat.format(Date(timestamp))}"
            isYesterday(timestamp) -> "昨天 ${timeFormat.format(Date(timestamp))}"
            isSameYear(timestamp) -> dateWithoutYearFormat.format(Date(timestamp))
            else -> fullDateFormat.format(Date(timestamp))
        }
    }

    /**
     * Format a confidence value (0.0-1.0) as a percentage string.
     */
    fun formatConfidence(confidence: Float): String {
        return "${(confidence * 100).toInt()}%"
    }

    /**
     * Format inference time in ms.
     */
    fun formatInferenceTime(ms: Long): String = "${ms}ms"

    private fun isSameDay(timestamp: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(timestamp: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
        cal1.add(Calendar.DAY_OF_YEAR, -1)
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameYear(timestamp: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }
}
