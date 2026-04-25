package com.fynnix.app.core.analytics

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.util.Calendar

/** Inclusive time-window helper. All times are in epoch millis. */
data class TimeRange(val start: Long, val end: Long)

object TimeRanges {

    fun today(): TimeRange = withDayBoundary { it }

    fun thisWeek(): TimeRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return withDayBoundary(cal) { Calendar.getInstance() }
    }

    fun thisMonth(): TimeRange {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        return withDayBoundary(cal) { Calendar.getInstance() }
    }

    fun thisYear(): TimeRange {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }
        return withDayBoundary(cal) { Calendar.getInstance() }
    }

    private inline fun withDayBoundary(transform: (Calendar) -> Calendar): TimeRange {
        val start = transform(Calendar.getInstance()).startOfDay()
        val end = System.currentTimeMillis()
        return TimeRange(start, end)
    }

    private inline fun withDayBoundary(start: Calendar, end: () -> Calendar): TimeRange =
        TimeRange(start.startOfDay(), end().timeInMillis)

    private fun Calendar.startOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }

    /** Hot Flow that re-emits the result of [supplier] every minute. Use to keep
     *  date-window-derived UI fresh across midnight / month boundaries. */
    fun ticking(supplier: () -> TimeRange): Flow<TimeRange> = flow {
        while (true) {
            emit(supplier())
            delay(60_000L)
        }
    }.distinctUntilChanged()
}
