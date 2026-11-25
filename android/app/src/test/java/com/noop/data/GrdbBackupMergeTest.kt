package com.noop.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.noop.ui.loadCaloriesByDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Importing a `.noopbak` that this app cannot simply swap in: a GRDB-layout file from another NOOP app or fork (merged
 * table by table), one written by a NEWER schema (refused before anything changes), and one from the schema just below
 * (upgraded by Room on open). Synthetic data only; the files are built from this app's own table definitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GrdbBackupMergeTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(ctx.cacheDir, "merge-test")
    private val device = "my-whoop"

    @Before fun clean() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME); dir.deleteRecursively(); dir.mkdirs() }
    @After fun tidy() { WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME); dir.deleteRecursively() }

    /** This app's own table and index definitions, as the schema a fresh install creates. */
    private fun ownDdl(): List<Triple<String, String, String>> {
        val db = WhoopDatabase.get(ctx).openHelper.writableDatabase
        return db.query("SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'")
            .use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) } }
    }

    private fun ownIdentityHash(): String =
        WhoopDatabase.get(ctx).openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table").use { it.moveToFirst(); it.getString(0) }

    /** A database file with this app's tables (minus [dropTables]), then whatever [fill] adds. */
    private fun build(name: String, userVersion: Int = 0, dropRoom: Boolean = true, fill: (SQLiteDatabase) -> Unit): File {
        val ddl = ownDdl(); val hash = ownIdentityHash()
        WhoopDatabase.close(); ctx.deleteDatabase(WhoopDatabase.DB_NAME)
        val f = File(dir, "$name.sqlite")
        val db = SQLiteDatabase.openOrCreateDatabase(f, null)
        try {
            for ((type, tbl, sql) in ddl) {
                if (dropRoom && tbl == "room_master_table") continue
                if (type == "table" || type == "index") db.execSQL(sql)
            }
            if (!dropRoom) db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$hash')")
            if (userVersion > 0) db.execSQL("PRAGMA user_version = $userVersion")
            fill(db)
        } finally { db.close() }
        return f
    }

    private fun zip(sqlite: File, name: String, settings: String? = null): File {
        val out = File(dir, "$name.noopbak")
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("noop-backup.sqlite")); sqlite.inputStream().use { it.copyTo(z) }; z.closeEntry()
            if (settings != null) { z.putNextEntry(ZipEntry("settings.json")); z.write(settings.toByteArray()); z.closeEntry() }
        }
        return out
    }

    private fun import(f: File): DataBackup.ImportResult =
        runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f)) } }

    private fun scalar(sql: String): Long? {
        val db = WhoopDatabase.get(ctx).openHelper.readableDatabase
        return db.query(sql).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    }

    /** A fork's GRDB-layout file: no Room table, a `grdb_migrations` table, an extra column and an extra table. */
    private fun forkFile(name: String = "fork"): File = build(name) { db ->
        db.execSQL("CREATE TABLE grdb_migrations (identifier TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO grdb_migrations VALUES ('v1'), ('v2')")
        db.execSQL("CREATE TABLE cursors (name TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("INSERT INTO cursors VALUES ('x', 'y')")
        db.execSQL("ALTER TABLE dailyMetric ADD COLUMN energyCoverageSeconds INTEGER")
        db.execSQL("INSERT INTO pairedDevice (id, brand, model, sourceKind, capabilities, status, addedAt, lastSeenAt) VALUES ('$device','WHOOP','FORK MODEL','live','','active',0,0)")
        for (i in 0 until 50) db.execSQL("INSERT INTO hrSample (deviceId, ts, bpm, synced) VALUES ('$device', ${1_700_000_000 + i}, ${60 + i % 5}, 0)")
        db.execSQL("INSERT INTO hrSample (deviceId, ts, bpm, synced) VALUES ('$device', 1700000100, 80, 0)")
        db.execSQL("INSERT INTO dailyMetric (deviceId, day, activeKcalEst, recovery, energyCoverageSeconds) VALUES ('$device','2026-10-01', 2400.0, 71.0, 86000)")
        db.execSQL("INSERT INTO dailyMetric (deviceId, day, activeKcalEst, recovery, energyCoverageSeconds) VALUES ('$device','2026-10-02', 2650.0, 64.0, 86000)")
        db.execSQL("INSERT INTO sleepSession (deviceId, startTs, endTs, userEdited) VALUES ('$device', 1700000000, 1700028800, 0)")
        db.execSQL("INSERT INTO rrInterval (deviceId, ts, rrMs, seq, synced, srcChannel) VALUES ('$device', 1700000100, 500, 0, 0, 5)")
        db.execSQL("INSERT INTO rrInterval (deviceId, ts, rrMs, seq, synced, srcChannel) VALUES ('$device', 1700000100, 800, 1, 0, 5)")
    }

    @Test fun aForkBackupIsMergedAndTheCaloriesColumnMapsAcross() {
        val r = import(zip(forkFile(), "fork", settings = null))
        assertTrue("got $r", r is DataBackup.ImportResult.NeedsRestart)
        assertEquals(51L, scalar("SELECT COUNT(*) FROM hrSample"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM dailyMetric"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM sleepSession"))
        // The fork's extra table and column are not copied, and this app's own device row was not replaced.
        assertNull(scalar("SELECT 1 FROM sqlite_master WHERE name = 'cursors'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM pairedDevice WHERE id = '$device' AND model <> 'FORK MODEL'"))
        // The v11-style daily energy column (shown there as Active Energy) is what Ridge's Calories reads.
        val calories = runBlocking { loadCaloriesByDay(WhoopRepository(WhoopDatabase.get(ctx)), device) }
        assertEquals(2400.0, calories["2026-10-01"]!!, 0.0)
        assertEquals(2650.0, calories["2026-10-02"]!!, 0.0)
    }

    @Test fun theSameForkFileTwiceAddsNothingTheSecondTime() {
        val f = zip(forkFile(), "fork")
        assertTrue(import(f) is DataBackup.ImportResult.NeedsRestart)
        val first = listOf("hrSample", "dailyMetric", "rrInterval", "sleepSession").map { scalar("SELECT COUNT(*) FROM $it") }
        assertTrue(import(f) is DataBackup.ImportResult.NeedsRestart)
        assertEquals(first, listOf("hrSample", "dailyMetric", "rrInterval", "sleepSession").map { scalar("SELECT COUNT(*) FROM $it") })
    }

    @Test fun rowsAlreadyInThisAppWinAndAreNeverOverwritten() {
        val f = zip(forkFile(), "fork")
        WhoopDatabase.get(ctx).openHelper.writableDatabase.execSQL(
            "INSERT INTO dailyMetric (deviceId, day, activeKcalEst, recovery) VALUES ('$device','2026-10-01', 111.0, 99.0)")
        assertTrue(import(f) is DataBackup.ImportResult.NeedsRestart)
        assertEquals(99L, scalar("SELECT CAST(recovery AS INTEGER) FROM dailyMetric WHERE day = '2026-10-01'"))
        assertEquals(2650L, scalar("SELECT CAST(activeKcalEst AS INTEGER) FROM dailyMetric WHERE day = '2026-10-02'"))
    }

    @Test fun fillBeatsInAMergedFileAreMarkedSuspectLikeTheMigrationDoes() {
        assertTrue(import(zip(forkFile(), "fork")) is DataBackup.ImportResult.NeedsRestart)
        // A 500 ms beat of channel 5 in a second whose stored heart rate (80) is under 100 bpm is marked; the 800 ms beat is not.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM rrInterval WHERE tsSuspect = 1 AND rrMs = 500"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM rrInterval WHERE tsSuspect = 1 AND rrMs = 800"))
    }

    @Test fun aGrdbFileWithNoNoopTablesIsRefusedAndNothingChanges() {
        val other = build("other") { db ->
            for (t in listOf("hrSample", "dailyMetric", "sleepSession", "workout", "rrInterval")) db.execSQL("DROP TABLE IF EXISTS $t")
            db.execSQL("CREATE TABLE grdb_migrations (identifier TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("CREATE TABLE somethingElse (a TEXT)")
        }
        val r = import(zip(other, "other"))
        assertTrue("got $r", r is DataBackup.ImportResult.Failed)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM hrSample"))
    }

    @Test fun aBackupFromANewerSchemaIsRefusedBeforeAnythingIsSwapped() {
        val newer = build("newer", userVersion = WhoopDatabase.SCHEMA_VERSION + 1, dropRoom = false) { }
        WhoopDatabase.get(ctx).openHelper.writableDatabase.execSQL(
            "INSERT INTO dailyMetric (deviceId, day, recovery) VALUES ('$device','2026-10-05', 55.0)")
        val r = import(zip(newer, "newer"))
        assertTrue("got $r", r is DataBackup.ImportResult.Failed)
        assertTrue((r as DataBackup.ImportResult.Failed).message.contains("newer version"))
        // The live database is untouched and still opens.
        assertEquals(55L, scalar("SELECT CAST(recovery AS INTEGER) FROM dailyMetric WHERE day = '2026-10-05'"))
        assertEquals(WhoopDatabase.SCHEMA_VERSION, WhoopDatabase.get(ctx).openHelper.readableDatabase.version)
    }

    @Test fun aBackupFromSchema40IsUpgradedToTheCurrentVersionOnOpen() {
        val old = build("v40", userVersion = 40, dropRoom = false) { db ->
            db.execSQL("INSERT INTO hrSample (deviceId, ts, bpm, synced) VALUES ('$device', 1700000100, 80, 0)")
            db.execSQL("INSERT INTO rrInterval (deviceId, ts, rrMs, seq, synced, srcChannel) VALUES ('$device', 1700000100, 500, 0, 0, 5)")
            db.execSQL("INSERT INTO rrInterval (deviceId, ts, rrMs, seq, synced, srcChannel) VALUES ('$device', 1700000100, 820, 1, 0, 5)")
        }
        val r = import(zip(old, "v40"))
        assertTrue("got $r", r is DataBackup.ImportResult.NeedsRestart)
        val db = WhoopDatabase.get(ctx).openHelper.writableDatabase
        assertEquals(WhoopDatabase.SCHEMA_VERSION, db.version)
        // The 40 -> 41 step marked the 500 ms fill beat (heart rate 80 in that second) and left the real beat alone.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM rrInterval WHERE tsSuspect = 1 AND rrMs = 500"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM rrInterval WHERE tsSuspect = 1 AND rrMs = 820"))
    }

    // --- Progress and cancelling ---

    private class Recorder(private val cancelAt: ImportStage? = null, private val cancelFromStart: Boolean = false) : ImportProgressSink {
        val reports = mutableListOf<ImportProgress>()
        private var cancel = cancelFromStart
        override fun report(progress: ImportProgress) {
            reports += progress
            if (cancelAt != null && progress.stage == cancelAt) cancel = true
        }
        override fun isCancelled() = cancel
        val stages get() = reports.map { it.stage }.fold(mutableListOf<ImportStage>()) { acc, st -> if (acc.lastOrNull() != st) acc.add(st); acc }
    }

    private fun importWith(f: File, sink: ImportProgressSink): DataBackup.ImportResult =
        runBlocking { withContext(Dispatchers.IO) { DataBackup.importFrom(ctx, Uri.fromFile(f), progress = sink) } }

    @Test fun aRestoreReportsItsSixStepsInOrderAndLocksCancelOnceDataChanges() {
        val old = build("v40p", userVersion = 40, dropRoom = false) { db ->
            db.execSQL("INSERT INTO hrSample (deviceId, ts, bpm, synced) VALUES ('$device', 1700000100, 80, 0)")
        }
        val rec = Recorder()
        assertTrue(importWith(zip(old, "v40p"), rec) is DataBackup.ImportResult.NeedsRestart)
        assertEquals(
            listOf(ImportStage.READING, ImportStage.UNPACKING, ImportStage.CHECKING, ImportStage.SAFETY_COPY, ImportStage.REPLACING, ImportStage.VERIFYING),
            rec.stages,
        )
        assertTrue("every report says 6 steps", rec.reports.all { it.steps == 6 })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rec.reports.map { it.step }.distinct())
        // Cancelling is offered while nothing has changed, and is withdrawn from the safety copy on (Room is closed by then).
        assertTrue(rec.reports.filter { it.step <= 3 }.all { it.cancellable })
        assertTrue(rec.reports.filter { it.step >= 4 }.none { it.cancellable })
        // The replace copy counts up to the size of the staged file and never backwards.
        val replacing = rec.reports.filter { it.stage == ImportStage.REPLACING }
        assertTrue(replacing.zipWithNext().all { (a, b) -> b.doneBytes >= a.doneBytes })
        assertEquals(replacing.last().totalBytes, replacing.last().doneBytes)
    }

    @Test fun aMergeReportsFiveStepsAndCountsTables() {
        val rec = Recorder()
        assertTrue(importWith(zip(forkFile(), "forkp"), rec) is DataBackup.ImportResult.NeedsRestart)
        assertEquals(
            listOf(ImportStage.READING, ImportStage.UNPACKING, ImportStage.CHECKING, ImportStage.MERGING, ImportStage.FINISHING),
            rec.stages,
        )
        assertTrue("from the check on, a merge has 5 steps", rec.reports.filter { it.step >= 4 }.all { it.steps == 5 })
        val merging = rec.reports.filter { it.stage == ImportStage.MERGING }
        assertTrue("table counts rise", merging.zipWithNext().all { (a, b) -> b.doneBytes >= a.doneBytes })
        assertTrue("the first report only says the merge began; the rest count tables", merging.drop(1).isNotEmpty() && merging.drop(1).all { it.totalBytes > 0 })
        assertTrue(merging.none { it.cancellable })
    }

    @Test fun cancellingBeforeTheSwapChangesNothing() {
        val old = build("v40c", userVersion = 40, dropRoom = false) { }
        WhoopDatabase.get(ctx).openHelper.writableDatabase.execSQL(
            "INSERT INTO dailyMetric (deviceId, day, recovery) VALUES ('$device','2026-10-07', 61.0)")
        val rec = Recorder(cancelAt = ImportStage.CHECKING)
        val r = importWith(zip(old, "v40c"), rec)
        assertTrue("got $r", r is DataBackup.ImportResult.Failed)
        assertEquals(IMPORT_CANCELLED_MESSAGE, (r as DataBackup.ImportResult.Failed).message)
        assertEquals(61L, scalar("SELECT CAST(recovery AS INTEGER) FROM dailyMetric WHERE day = '2026-10-07'"))
        assertTrue("stopped before the safety copy", ImportStage.SAFETY_COPY !in rec.stages)
    }

    @Test fun cancellingWhileUnpackingStopsTheRead() {
        val r = importWith(zip(forkFile(), "forkc"), Recorder(cancelFromStart = true))
        assertTrue("got $r", r is DataBackup.ImportResult.Failed)
        assertEquals(IMPORT_CANCELLED_MESSAGE, (r as DataBackup.ImportResult.Failed).message)
    }
}
