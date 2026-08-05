package com.ori.pivotboard_project.utilities

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Turns epoch millis into the short relative labels the feed cards show ("2h", "3d"). */
object TimeFormatter {

    private const val NOW_THRESHOLD_MINUTES = 1L
    private const val DAYS_BEFORE_ABSOLUTE_DATE = 7L

    private val absoluteFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /** e.g. "now", "5m ago", "2h ago", "3d ago", then falls back to "14 Mar 2026". */
    fun relative(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return ""

        val elapsed = nowMillis - timestampMillis
        if (elapsed < 0L) return "now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)

        return when {
            minutes < NOW_THRESHOLD_MINUTES -> "now"
            hours < 1L -> "${minutes}m ago"
            days < 1L -> "${hours}h ago"
            days < DAYS_BEFORE_ABSOLUTE_DATE -> "${days}d ago"
            else -> absolute(timestampMillis)
        }
    }

    fun absolute(timestampMillis: Long): String = absoluteFormat.format(Date(timestampMillis))
}
