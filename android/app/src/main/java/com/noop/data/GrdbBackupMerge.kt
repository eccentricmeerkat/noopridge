package com.noop.data

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Brings the data of a GRDB-layout `.noopbak` (the layout the Mac and iOS NOOP apps write, and some Android forks use
 * too: it carries a `grdb_migrations` table instead of Room's `room_master_table`) into this app's Room database.
 *
 * Such a file cannot REPLACE the live database: Room would not open it. But the two layouts share most tables with the
 * same names and columns, so the data is MERGED instead, table by table, into the database this app already has:
 *  - only the columns both sides have are copied (a column one side lacks is dropped, never invented);
 *  - `INSERT OR IGNORE`: a row already in this app wins, so nothing here is ever overwritten or removed, and importing
 *    the same file twice adds nothing the second time;
 *  - one transaction, so a failure leaves the database exactly as it was.
 *
 * Only tables on [MERGEABLE] are touched, in that order. The `lift*` tables are not on it: their integer ids point at
 * each other, so copying them without remapping the ids would link rows to the wrong parents.
 */
internal object GrdbBackupMerge {

    /** Tables keyed by a natural key (device + time, or device + day), safe to copy with their keys intact. */
    private val MERGEABLE: List<String> = listOf(
        "device", "pairedDevice",
        "hrSample", "rrInterval", "battery", "event", "gravitySample", "respSample", "skinTempSample", "spo2Sample",
        "ppgHrSample", "ppgWaveformSample", "stepSample", "sleepStateSample", "v18AuxSample",
        "sleepSession", "workout", "dailyMetric", "metricSeries", "journal", "liveSession",
        "scoreInputProvenance", "dayOwnership", "dismissedSleep", "dismissedWorkout",
        "appleDaily", "appleStepHour",
    )

    /** Tables with an integer `id` of no meaning outside the row: the id is left out so this app assigns a fresh one. */
    private val AUTO_ID: List<String> = listOf("coachMessage", "labMarker")

    /** A table that proves the file really holds NOOP data, as opposed to some other app's GRDB database. */
    private val CORE: Set<String> = setOf("hrSample", "dailyMetric", "sleepSession", "workout", "rrInterval")

    /** Columns this app requires that a GRDB file may lack; the value used for the rows that arrive without it. */
    private val FALLBACK: Map<String, String> = mapOf("synced" to "0")

    data class TableReport(val table: String, val offered: Long, val added: Long)

    data class Report(val tables: List<TableReport>, val skipped: List<String>) {
        val rowsAdded: Long get() = tables.sumOf { it.added }
        val rowsOffered: Long get() = tables.sumOf { it.offered }
    }

    /** True when [tableNames] looks like a NOOP GRDB database with data this app can use. */
    fun isMergeable(tableNames: Set<String>): Boolean = tableNames.any { it in CORE }

    /**
     * Merge [source] into the database file [live]. The caller has closed Room and made sure [live] exists at the current
     * schema. Throws on any failure; the transaction is then rolled back and [live] is unchanged.
     */
    fun merge(live: File, source: File, onTable: (done: Int, total: Int) -> Unit = { _, _ -> }): Report {
        val db = SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("ATTACH DATABASE '" + source.path.replace("'", "''") + "' AS src")
            db.beginTransaction()
            try {
                val reports = ArrayList<TableReport>()
                val skipped = ArrayList<String>()
                val all = MERGEABLE + AUTO_ID
                for ((index, table) in all.withIndex()) {
                    onTable(index, all.size)
                    val plan = plan(db, table, dropId = table in AUTO_ID)
                    if (plan == null) { if (existsIn(db, "src", table)) skipped += table; continue }
                    val offered = count(db, "src", table)
                    if (offered == 0L) continue
                    val before = count(db, "main", table)
                    db.execSQL("INSERT OR IGNORE INTO main.\"$table\" (${plan.targets}) SELECT ${plan.sources} FROM src.\"$table\"")
                    reports += TableReport(table, offered, count(db, "main", table) - before)
                }
                // Same data-only rule as the 40 -> 41 migration, over the rows that just arrived.
                if (reports.any { it.table == "rrInterval" }) db.execSQL(WHOOP5_RR_FILL_MIGRATION_SQL)
                db.setTransactionSuccessful()
                return Report(reports, skipped)
            } finally {
                db.endTransaction()
            }
        } finally {
            runCatching { db.execSQL("DETACH DATABASE src") }
            db.close()
        }
    }

    private class Plan(val targets: String, val sources: String)

    /**
     * The column lists for one table, or null when it is missing on either side or a column this app requires has neither
     * a source column nor a [FALLBACK].
     */
    private fun plan(db: SQLiteDatabase, table: String, dropId: Boolean): Plan? {
        val live = columns(db, "main", table)
        val src = columns(db, "src", table).map { it.name }.toSet()
        if (live.isEmpty() || src.isEmpty()) return null
        val targets = ArrayList<String>()
        val sources = ArrayList<String>()
        for (c in live) {
            when {
                dropId && c.name == "id" -> Unit
                c.name in src -> { targets += "\"${c.name}\""; sources += "\"${c.name}\"" }
                c.required && FALLBACK.containsKey(c.name) -> { targets += "\"${c.name}\""; sources += FALLBACK.getValue(c.name) }
                c.required -> return null
            }
        }
        return if (targets.isEmpty()) null else Plan(targets.joinToString(","), sources.joinToString(","))
    }

    private class Column(val name: String, val required: Boolean)

    /** A column is required when it is NOT NULL, has no default and is not the primary key (`id` aside, which is dropped). */
    private fun columns(db: SQLiteDatabase, schema: String, table: String): List<Column> =
        db.rawQuery("PRAGMA $schema.table_info(\"$table\")", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val notNull = c.getInt(3) == 1
                    val hasDefault = !c.isNull(4)
                    val isPk = c.getInt(5) > 0
                    add(Column(c.getString(1), required = notNull && !hasDefault && !isPk))
                }
            }
        }

    private fun existsIn(db: SQLiteDatabase, schema: String, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM $schema.sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun count(db: SQLiteDatabase, schema: String, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $schema.\"$table\"", null).use { it.moveToFirst(); it.getLong(0) }
}
