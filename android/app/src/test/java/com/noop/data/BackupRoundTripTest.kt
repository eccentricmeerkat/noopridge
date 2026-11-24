package com.noop.data

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Final review (moving from the staging build to the release build): a `.noopbak` must carry everything Insights reads.
 *
 * Every Insights metric (`MetricCatalog`) reads a Room table: the daily row, the long-format series (nutrition, mood,
 * body, cycle), the Apple Health and Health Connect days, the saved workouts, or the heart-rate samples. The backup is the
 * whole database file. This test proves it rather than reads it: it fills those tables with synthetic rows, exports, wipes
 * the database, imports, and compares EVERY table, row for row. A second test does the same for the whitelisted profile
 * settings. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BackupRoundTripTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(ctx.cacheDir, "round-trip.noopbak")

    @Before fun clean() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME); file.delete() }
    @After fun tidy() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME); file.delete() }

    /** Every user table, as sorted row strings. Room's own bookkeeping tables are left out. */
    private fun dump(db: WhoopDatabase): Map<String, List<String>> {
        val sql = db.openHelper.readableDatabase
        val tables = buildList {
            sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name <> 'android_metadata'").use {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
        return tables.sorted().associateWith { t ->
            buildList {
                sql.query("SELECT * FROM \"$t\"").use { c ->
                    while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "∅" else c.getString(i) })
                }
            }.sorted()
        }
    }

    private suspend fun populate(repo: WhoopRepository) {
        val days = (1..30).map { "2026-10-%02d".format(it) }
        repo.upsertDailyMetrics(days.mapIndexed { i, d ->
            DailyMetric("my-whoop", d, totalSleepMin = 400.0 + i, restingHr = 50 + i % 7, avgHrv = 50.0 + i,
                recovery = 30.0 + i, strain = 20.0 + i, respRateBpm = 14.0, spo2Pct = 96.0, activeKcalEst = 2000.0 + i, steps = 8000 + i)
        })
        repo.upsertDailyMetrics(days.take(5).map { d -> DailyMetric("my-whoop-noop", d, activeKcalEst = 2100.0, steps = 9000) })
        val keys = listOf("mood", "calories_in", "protein_g", "carbs_g", "fat_g", "body_fat", "lean_mass", "bmi", "weight",
            "sleep_performance", "stress", "vitality", "fitness_age", "vo2max_est", "avg_hr", "max_hr")
        repo.upsertMetricSeries(keys.flatMap { k -> days.take(10).mapIndexed { i, d -> MetricSeriesRow("my-whoop", d, k, 10.0 + i) } })
        repo.upsertAppleDaily(days.take(10).map { d ->
            AppleDaily("health-connect", d, steps = 7000, activeKcal = 300.0, basalKcal = 1800.0, vo2max = 45.0, weightKg = 83.0)
        })
        repo.upsertWorkouts(listOf(
            WorkoutRow("my-whoop", 1_700_000_000L, 1_700_002_200L, "Running", "my-whoop", 2200.0, 450.0, 142, 179, 54.5),
        ))
        repo.insertHr((0 until 600).map { HrSample("my-whoop", 1_700_000_000L + it, 120 + it % 40) })
    }

    @Test
    fun everyTableInsightsReadsSurvivesAnExportAndImport() = runBlocking {
        val repo = WhoopRepository(WhoopDatabase.get(ctx))
        populate(repo)
        val before = dump(WhoopDatabase.get(ctx))
        for (t in listOf("dailyMetric", "metricSeries", "appleDaily", "workout", "hrSample")) {
            assertTrue("$t was not populated by the test", before[t].orEmpty().isNotEmpty())
        }

        withContext(Dispatchers.IO) { DataBackup.exportTo(ctx, Uri.fromFile(file)) }
        assertTrue("export wrote nothing", file.length() > 0)

        // A fresh, empty database, as on the release app's first launch.
        WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME)
        val empty = dump(WhoopDatabase.get(ctx))
        assertFalse("the wipe left data behind", empty == before)
        WhoopDatabase.close()

        val result = withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(file)) }
        assertTrue("import did not succeed: $result", result is DataBackup.ImportResult.NeedsRestart)

        val after = dump(WhoopDatabase.get(ctx))
        assertEquals("the same tables", before.keys, after.keys)
        for (t in before.keys) assertEquals("table $t", before.getValue(t), after.getValue(t))
        println("ROUNDTRIP compared " + before.size + " tables, " + before.values.sumOf { it.size } + " rows; non-empty: " + before.filterValues { it.isNotEmpty() }.map { it.key + "=" + it.value.size })
    }

    @Test
    fun theProfileAndUnitSettingsSurviveToo() {
        val profile = ProfileStore.from(ctx)
        profile.weightKg = 80.0
        profile.heightCm = 175.0
        profile.sex = "male"
        profile.setAge(30)
        val result = runBlocking {
            populate(WhoopRepository(WhoopDatabase.get(ctx)))
            withContext(Dispatchers.IO) { DataBackup.exportTo(ctx, Uri.fromFile(file)) }
            profile.weightKg = 60.0; profile.heightCm = 160.0; profile.sex = "female"; profile.setAge(50)
            WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME)
            withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(file)) }
        }
        assertTrue("import did not succeed: $result", result is DataBackup.ImportResult.NeedsRestart)

        val restored = ProfileStore.from(ctx)
        assertEquals(80.0, restored.weightKg, 0.0)
        assertEquals(175.0, restored.heightCm, 0.0)
        assertEquals("male", restored.sex)
        assertEquals(30, restored.age)
    }
}
