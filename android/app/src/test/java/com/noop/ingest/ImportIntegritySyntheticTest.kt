package com.noop.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Final review (data integrity): the WHOOP CSV importer against synthetic files with deliberate faults. These pin the
 * behaviour that is GOOD today. The gaps the same probe found (no plausibility bounds, no future-date check, first
 * duplicate wins) are in `docs/ridge-build/Final review/DATA_INTEGRITY_REPORT.md`, not asserted here.
 */
class ImportIntegritySyntheticTest {

    private val head = "Cycle start time,Cycle end time,Cycle timezone,Recovery score %,Resting heart rate (bpm),Day strain,Wake onset"

    private fun cycles(body: String) =
        WhoopCsvImporter.parseCycles(CsvTable.fromData((head + "\n" + body.trimIndent()).toByteArray()), "my-whoop")

    @Test
    fun anIdenticalRowRepeatedInTheFileBecomesOneDay() {
        val rows = cycles(
            """
            2024-01-01 22:10:00,2024-01-02 22:30:00,UTC+01:00,66,52,8.4,2024-01-02 06:30:00
            2024-01-01 22:10:00,2024-01-02 22:30:00,UTC+01:00,66,52,8.4,2024-01-02 06:30:00
            """,
        )
        assertEquals(2, rows.size)
        val merged = WhoopCsvImporter.mergeDaily(rows, emptyList())
        assertEquals(1, merged.size)
        assertEquals("2024-01-02", merged.single().day)
        assertEquals(66.0, merged.single().recovery!!, 0.0)
    }

    @Test
    fun rowOrderDoesNotChangeTheImportedDays() {
        val inOrder = cycles(
            """
            2024-01-01 22:10:00,2024-01-02 22:30:00,UTC+01:00,60,52,8,2024-01-02 06:30:00
            2024-01-02 22:10:00,2024-01-03 22:30:00,UTC+01:00,70,51,9,2024-01-03 06:30:00
            """,
        )
        val reversed = cycles(
            """
            2024-01-02 22:10:00,2024-01-03 22:30:00,UTC+01:00,70,51,9,2024-01-03 06:30:00
            2024-01-01 22:10:00,2024-01-02 22:30:00,UTC+01:00,60,52,8,2024-01-02 06:30:00
            """,
        )
        assertEquals(
            WhoopCsvImporter.mergeDaily(inOrder, emptyList()).toSet(),
            WhoopCsvImporter.mergeDaily(reversed, emptyList()).toSet(),
        )
    }

    @Test
    fun aRealZeroIsKeptAndBlankOrJunkCellsAreGapsNotZeros() {
        val r = cycles(
            """
            2024-01-01 22:10:00,2024-01-02 22:30:00,UTC+01:00,0,60,0,2024-01-02 06:30:00
            2024-01-02 22:10:00,2024-01-03 22:30:00,UTC+01:00,,,,2024-01-03 06:30:00
            2024-01-03 22:10:00,2024-01-04 22:30:00,UTC+01:00,N/A,--,abc,2024-01-04 06:30:00
            """,
        ).associateBy { it.day }
        assertEquals(0.0, r.getValue("2024-01-02").recovery!!, 0.0)
        assertEquals(0.0, r.getValue("2024-01-02").strain!!, 0.0)
        for (d in listOf("2024-01-03", "2024-01-04")) {
            assertNull("$d recovery", r.getValue(d).recovery)
            assertNull("$d resting HR", r.getValue(d).restingHr)
            assertNull("$d strain", r.getValue(d).strain)
        }
    }

    @Test
    fun aNightAcrossTheSpringClockChangeIsKeyedToItsWakeDay() {
        val r = cycles(
            """
            2024-03-30 22:30:00,2024-03-31 22:30:00,UTC+01:00,60,55,7,2024-03-31 07:30:00
            2024-03-31 22:30:00,2024-04-01 22:30:00,UTC+01:00,62,54,7,2024-04-01 07:30:00
            """,
        )
        assertEquals(listOf("2024-03-31", "2024-04-01"), r.map { it.day })
    }

    @Test
    fun aNapIsNotAddedToTheMainSleepTotal() {
        val s = WhoopCsvImporter.parseSleeps(
            CsvTable.fromData(
                """
                Cycle start time,Cycle timezone,Sleep onset,Wake onset,Nap,Asleep duration (min),Light sleep duration (min),Deep (SWS) duration (min),REM duration (min)
                2024-01-01 22:10:00,UTC+01:00,2024-01-01 23:15:00,2024-01-02 06:30:00,false,420,210,90,120
                2024-01-01 22:10:00,UTC+01:00,2024-01-02 14:00:00,2024-01-02 14:40:00,true,35,20,5,10
                """.trimIndent().toByteArray(),
            ),
            "my-whoop",
        )
        assertEquals(1, s.daily.size)
        assertNotNull(s.daily.single().totalSleepMin)
        assertEquals(420.0, s.daily.single().totalSleepMin!!, 0.0)
    }
}
