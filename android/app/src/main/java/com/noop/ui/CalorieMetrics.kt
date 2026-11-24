package com.noop.ui

import com.noop.data.AppleDaily
import com.noop.data.WorkoutRow
import java.time.Instant
import java.time.ZoneId

/**
 * The three calorie metrics (owner, 21 Nov 2026), kept apart so each name means one thing:
 *  - **Calories**: total calories burned in a day (resting plus activity). The strap's own whole-day estimate first; a day
 *    with none takes an import's total, and only when the import gives both its resting and its active part.
 *  - **Active calories**: calories burned in the workouts saved in NOOP, summed per day.
 *  - **Active energy**: the general-activity figure Health Connect reports. It only ever comes from Health Connect.
 */
internal object CalorieMetrics {

    /** An import's whole-day total: resting plus active, and only when both are present. */
    fun importedTotal(r: AppleDaily): Double? {
        val active = r.activeKcal?.takeIf { it > 0.0 } ?: return null
        val basal = r.basalKcal?.takeIf { it > 0.0 } ?: return null
        return active + basal
    }

    /** Calories per day: the strap's estimate wins, an import's total fills a day the strap has none for. */
    fun totalByDay(strap: Map<String, Double>, imports: List<AppleDaily>): Map<String, Double> {
        val imported = LinkedHashMap<String, Double>()
        for (r in imports) importedTotal(r)?.let { imported.putIfAbsent(r.day, it) }
        return (strap.keys + imported.keys).mapNotNull { day -> (strap[day] ?: imported[day])?.let { day to it } }.toMap()
    }

    /** Active energy per day: Health Connect's own figure, nothing else. */
    fun hcActiveByDay(healthConnectRows: List<AppleDaily>): Map<String, Double> =
        healthConnectRows.mapNotNull { r -> r.activeKcal?.takeIf { it > 0.0 }?.let { r.day to it } }.toMap()

    /** Active calories per day: the energy recorded on the saved workouts (every source, de-duplicated), summed by logical day. */
    fun workoutKcalByDay(workouts: List<WorkoutRow>, zone: ZoneId = ZoneId.systemDefault()): Map<String, Double> =
        WorkoutEditing.dedupCrossSource(workouts)
            .filter { (it.energyKcal ?: 0.0) > 0.0 }
            .groupBy { logicalDay(Instant.ofEpochSecond(it.startTs).atZone(zone)).toString() }
            .mapValues { (_, ws) -> ws.sumOf { it.energyKcal ?: 0.0 } }
}
