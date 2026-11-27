package com.noop.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.noop.ingest.CsvTable
import com.noop.ingest.WhoopCsvImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RS-3d: efficiency is stored as a fraction, whichever form the file used, and wrongly stored rows are repaired. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EfficiencyStorageTest {

    @Test
    fun aFractionOrAPercentIsStoredAsTheSameFraction() {
        assertEquals(0.97, EfficiencyStorage.fraction(97.0)!!, 1e-9)
        assertEquals(0.97, EfficiencyStorage.fraction(0.97)!!, 1e-9)
        assertEquals(1.0, EfficiencyStorage.fraction(100.0)!!, 1e-9)
        assertEquals(1.0, EfficiencyStorage.fraction(1.0)!!, 1e-9)
        assertNull(EfficiencyStorage.fraction(null))
    }

    /** The reported file: a WHOOP-format export with a fraction in the percent column on some rows and a percent on others. */
    @Test
    fun theWhoopImportReadsBothForms() {
        val csv = "Cycle start time,Sleep onset,Wake onset,Cycle timezone,Nap,Asleep duration (min),In bed duration (min),Sleep efficiency %\r\n" +
            "2026-06-19 00:00:00,2026-06-18 19:00:00,2026-06-19 03:00:00,UTC+00:00,false,450,470,0.9753\r\n" +
            "2026-06-20 00:00:00,2026-06-19 19:00:00,2026-06-20 03:00:00,UTC+00:00,false,440,480,91.5\r\n"
        val parsed = WhoopCsvImporter.parseSleeps(CsvTable.fromData(csv.toByteArray()), "my-whoop")
        assertEquals(listOf(0.9753, 0.915), parsed.daily.sortedBy { it.day }.map { Math.round(it.efficiency!! * 10_000) / 10_000.0 })
        assertEquals(listOf(0.9753, 0.915), parsed.sessions.sortedBy { it.startTs }.map { Math.round(it.efficiency!! * 10_000) / 10_000.0 })
    }

    @Test
    fun theRepairPutsRightOnlyWhatNoNightCanBeAndRunsTwiceSafely() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WhoopDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.whoopDao()
        // Twice divided, a percent, a good fraction, a poor but real night, a perfect night, a zero and no value.
        val stored = listOf(0.009753, 92.0, 0.915, 0.42, 1.0, 0.0, null)
        val fixed = listOf(0.9753, 0.92, 0.915, 0.42, 1.0, 0.0, null)
        dao.upsertDailyMetrics(stored.mapIndexed { i, e -> DailyMetric("d", "2026-11-%02d".format(i + 1), efficiency = e) })
        dao.upsertSleepSessions(stored.mapIndexed { i, e -> SleepSession(deviceId = "d", startTs = 1000L * (i + 1), endTs = 1000L * (i + 1) + 500, efficiency = e) })
        repeat(2) {
            EfficiencyStorage.repairStored(db)
            val daily = dao.dailyMetricsRange("d", "2026-11-01", "2026-11-30").sortedBy { it.day }.map { it.efficiency }
            val sessions = dao.sleepSessions("d", 0L, 100_000L, 100).sortedBy { it.startTs }.map { it.efficiency }
            for ((got, want) in listOf(daily, sessions).flatMap { it.zip(fixed) }) {
                if (want == null) assertNull(got) else assertEquals(want, got!!, 1e-9)
            }
            assertEquals(fixed.size, daily.size)
            assertEquals(fixed.size, sessions.size)
        }
        db.close()
    }
}
