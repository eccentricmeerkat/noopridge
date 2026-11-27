package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** RS-1-3 item 9: the Date of Birth row's text and the Birthday wheels' rules. */
class BirthdayPickerTest {
    @Test fun englishReadsMonthDayOrdinalYear() {
        val en = Locale.UK
        assertEquals("June 10th, 1990", birthdayText(LocalDate.of(1990, 6, 10), en))
        mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st")
            .forEach { (d, want) -> assertEquals("January $want, 2000", birthdayText(LocalDate.of(2000, 1, d), en)) }
    }

    @Test fun otherLanguagesUseTheirOwnLongDate() {
        assertEquals("10 juin 1990", birthdayText(LocalDate.of(1990, 6, 10), Locale.FRANCE))
    }

    @Test fun aDayPastTheMonthsEndIsPulledBack() {
        assertEquals(28, clampDay(2023, 2, 31))
        assertEquals(29, clampDay(2024, 2, 30))
        assertEquals(30, clampDay(1990, 4, 31))
        assertEquals(15, clampDay(1990, 6, 15))
    }

    @Test fun yearsRunNewestFirstWithinTheAgeLimits() {
        val y = birthdayYears(LocalDate.of(2026, 11, 25), minAge = 13, maxAge = 100)
        assertEquals(2013, y.first())
        assertEquals(1926, y.last())
        assertEquals(88, y.size)
    }
}
