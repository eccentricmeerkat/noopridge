package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** B2-4a-1: the Sleep header names the evening weekday-first, as the board does ("SAT 19 SEP" once capitalised). */
class SleepHeaderLabelTest {
    @Test
    fun weekdayFirstDayAndMonth() {
        assertEquals("Sat 19 Sep", dayLabelWithWeekday(LocalDate.of(2026, 9, 19), Locale.US))
        assertEquals("Wed 11 Nov", dayLabelWithWeekday(LocalDate.of(2026, 11, 11), Locale.US))
        assertEquals("Thu 1 Jan", dayLabelWithWeekday(LocalDate.of(2026, 1, 1), Locale.US))
    }
}
