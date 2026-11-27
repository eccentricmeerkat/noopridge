package com.noop.data

/*
 * RS-3d: sleep efficiency is STORED as a fraction (0 to 1). Two importers did not hold to that:
 *  - the WHOOP CSV import divided its percent column by 100 even when the file held a fraction (an older NOOP export did on
 *    some rows), storing about 0.0097, which read as 1 % and dragged down Rest and the Sleep score for those nights;
 *  - the wearable-export and Xiaomi imports stored percents (92.0), which Rest's recalculation reads as 9200 %, clipped to 100.
 * [fraction] is what every importer now stores through; [repairStored] puts right the rows already on a phone.
 */
object EfficiencyStorage {
    /** At or below this, a stored value is a fraction that was divided by 100 twice: no night is 1.5 % efficient. */
    const val TWICE_DIVIDED_MAX = 0.015
    /** Above this, a stored value is a percent: no night is 150 % efficient. */
    const val PERCENT_MIN = 1.5

    /** An imported figure as a fraction, whether the file gave a fraction (0.97) or a percent (97). */
    fun fraction(imported: Double?): Double? = imported?.let { if (it > PERCENT_MIN) it / 100.0 else it }

    private val TABLES = listOf("dailyMetric", "sleepSession")

    /**
     * The two repairs, for each table that stores efficiency. Idempotent, and each touches only values no real night can
     * have, so a correct fraction is never changed. The percent repair runs first: a percent of 1.5 or less is not told apart
     * from a fraction and is left alone.
     */
    val repairStatements: List<String> = TABLES.flatMap { table ->
        listOf(
            "UPDATE $table SET efficiency = efficiency / 100.0 WHERE efficiency > $PERCENT_MIN",
            "UPDATE $table SET efficiency = efficiency * 100.0 WHERE efficiency > 0 AND efficiency <= $TWICE_DIVIDED_MAX",
        )
    }

    /** Runs [repairStatements] in one transaction, so Room tells open screens. Safe on every start, restored backups included. */
    fun repairStored(db: WhoopDatabase) {
        db.runInTransaction { repairStatements.forEach { db.openHelper.writableDatabase.execSQL(it) } }
    }
}
