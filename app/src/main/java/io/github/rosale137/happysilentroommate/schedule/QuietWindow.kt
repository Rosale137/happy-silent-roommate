package io.github.rosale137.happysilentroommate.schedule

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val MINUTES_PER_DAY = 24 * 60
private const val MS_PER_MINUTE = 60_000L

/**
 * A daily quiet time from [startMin] to [endMin], in minutes after midnight. May run across
 * midnight (22:00 to 01:00). Start and end equal means no quiet time at all.
 * Pure logic, no Android, so it can be unit-tested.
 */
data class QuietWindow(val startMin: Int, val endMin: Int) {

    init {
        require(startMin in 0 until MINUTES_PER_DAY && endMin in 0 until MINUTES_PER_DAY)
    }

    val isEmpty: Boolean get() = startMin == endMin

    /** Start is included, end is not: 21:00 to 23:30 contains 21:00 but not 23:30. */
    fun contains(minuteOfDay: Int): Boolean = when {
        startMin < endMin -> minuteOfDay in startMin until endMin
        startMin > endMin -> minuteOfDay >= startMin || minuteOfDay < endMin
        else -> false
    }

    fun contains(millis: Long, zone: TimeZone): Boolean = contains(minuteOfDay(millis, zone))

    /** The next start or end strictly after [millis]. */
    fun nextBoundaryMillis(millis: Long, zone: TimeZone): Long =
        millis + minOf(msUntil(startMin, millis, zone), msUntil(endMin, millis, zone))

    /** The next end strictly after [millis]: for "stop for tonight". */
    fun nextEndMillis(millis: Long, zone: TimeZone): Long = millis + msUntil(endMin, millis, zone)

    companion object {
        fun minuteOfDay(millis: Long, zone: TimeZone): Int {
            val cal = Calendar.getInstance(zone).apply { timeInMillis = millis }
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        /** Time from [millis] to the next [targetMin] o'clock, always more than zero. */
        private fun msUntil(targetMin: Int, millis: Long, zone: TimeZone): Long {
            val cal = Calendar.getInstance(zone).apply { timeInMillis = millis }
            val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val intoMinute = cal.get(Calendar.SECOND) * 1000L + cal.get(Calendar.MILLISECOND)
            val minutes = ((targetMin - now) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
            val ms = minutes * MS_PER_MINUTE - intoMinute
            return if (ms > 0) ms else ms + MINUTES_PER_DAY * MS_PER_MINUTE
        }

        /** "21:00". */
        fun format(minuteOfDay: Int): String =
            String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
    }
}
