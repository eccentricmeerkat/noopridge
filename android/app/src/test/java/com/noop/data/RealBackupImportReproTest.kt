package com.noop.data

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Local reproduction of reported `.noopbak` import failures, against REAL backup files a tester supplied. The files hold
 * personal data and are never committed: point `NOOP_BACKUP_FIXTURES` at a folder holding them, otherwise every test here
 * is skipped. Prints what the import returns; asserts only what a user would see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RealBackupImportReproTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir: File? = System.getenv("NOOP_BACKUP_FIXTURES")?.let(::File)?.takeIf { it.isDirectory }

    @Before fun clean() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME) }
    @After fun tidy() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME) }

    private fun fixture(name: String): File {
        assumeTrue("NOOP_BACKUP_FIXTURES not set", dir != null)
        val f = File(dir, name)
        assumeTrue("$name not found", f.isFile)
        return f
    }

    @Test fun forkBackupWithGrdbBookkeepingIsMergedInFull() {
        val f = fixture("NOOP-Forge-2026-11-25.noopbak")
        val r = runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f)) } }
        println("REPRO forge -> $r")
        val db = WhoopDatabase.get(ctx).openHelper.readableDatabase
        for (t in listOf("hrSample", "rrInterval", "dailyMetric", "sleepSession", "workout", "journal", "event", "battery", "gravitySample", "metricSeries", "liveSession")) {
            println("REPRO forge merged $t = " + db.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getLong(0) })
        }
        println("REPRO forge rr suspect = " + db.query("SELECT COUNT(*) FROM rrInterval WHERE tsSuspect = 1").use { it.moveToFirst(); it.getLong(0) } +
            ", db version " + db.version)
    }

    @Test fun backupOverTheSizeLimitAsksForAnOverride() {
        val f = fixture("noop-backup-2026-10-28.noopbak.zip")
        val r = runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f)) } }
        println("REPRO v10-file default -> $r")
    }

    @Test fun backupOverTheSizeLimitThenNewerSchemaOnceAllowed() {
        val f = fixture("noop-backup-2026-10-28.noopbak.zip")
        val r = runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f), allowOversize = true) } }
        println("REPRO v10-file allowOversize -> $r")
        val open = runCatching { WhoopDatabase.get(ctx).openHelper.writableDatabase.version }
        println("REPRO v10-file then open -> " + open.fold({ "opened at version $it" }, { "${it::class.java.simpleName}: ${it.message?.take(220)}" }))
    }

    @Test fun backupFromANewerSchemaThanThisApp() {
        val f = fixture("newer-schema-41.noopbak")
        val r = runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f)) } }
        println("REPRO newer-schema -> $r")
        val open = runCatching { WhoopDatabase.get(ctx).openHelper.writableDatabase.version }
        println("REPRO newer-schema then open -> " + open.fold({ "opened at version $it" }, { "${it::class.java.simpleName}: ${it.message?.take(260)}" }))
    }

    /** The generated original-Noop fixtures (Tools/synthetic_noopbak.py): import, open at the current schema, read it back. */
    private fun importSynthetic(name: String, expectedDays: Int) {
        val f = fixture(name)
        val r = runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f)) } }
        println("REPRO $name -> $r")
        org.junit.Assert.assertTrue("got $r", r is DataBackup.ImportResult.NeedsRestart)
        val db = WhoopDatabase.get(ctx).openHelper.writableDatabase
        fun n(sql: String) = db.query(sql).use { it.moveToFirst(); it.getLong(0) }
        println("REPRO $name opened at version ${db.version}; days=${n("SELECT COUNT(*) FROM dailyMetric")} hr=${n("SELECT COUNT(*) FROM hrSample")} " +
            "rr=${n("SELECT COUNT(*) FROM rrInterval")} sleeps=${n("SELECT COUNT(*) FROM sleepSession")} workouts=${n("SELECT COUNT(*) FROM workout")}")
        org.junit.Assert.assertEquals(WhoopDatabase.SCHEMA_VERSION, db.version)
        org.junit.Assert.assertEquals(expectedDays.toLong(), n("SELECT COUNT(*) FROM dailyMetric"))
        org.junit.Assert.assertEquals(expectedDays.toLong(), n("SELECT COUNT(*) FROM sleepSession"))
        org.junit.Assert.assertTrue(n("SELECT COUNT(*) FROM hrSample") > 100_000)
        // The original app's daily energy (its "Active Energy") is what Ridge shows as Calories.
        val kcal = db.query("SELECT day, activeKcalEst FROM dailyMetric WHERE deviceId = 'my-whoop' ORDER BY day").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getDouble(1)) }
        }
        val calories = runBlocking { com.noop.ui.loadCaloriesByDay(WhoopRepository(WhoopDatabase.get(ctx)), "my-whoop") }
        println("REPRO $name calories days=${calories.size}")
        org.junit.Assert.assertEquals(expectedDays, calories.size)
        for ((day, v) in kcal) org.junit.Assert.assertEquals(day, v, calories.getValue(day), 0.0)
    }

    @Test fun originalNoopV11_8BackupImports() = importSynthetic("noop-v11.8-synthetic-4wk.noopbak", 28)

    @Test fun originalNoopV12_0BackupImports() = importSynthetic("noop-v12.0-synthetic-4wk.noopbak", 28)
}
