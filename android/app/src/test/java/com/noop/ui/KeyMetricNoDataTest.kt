package com.noop.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/** RS-2e: the Key Metrics editor's "No data" tags follow Insights' rule over each tile's catalogue entry. */
class KeyMetricNoDataTest {

    private val today = LocalDate.of(2026, 11, 26)

    @Test
    fun everyTileHasACatalogueEntryAndOnlyRestSharesOne() {
        val byKey = KeyMetric.entries.groupBy { m -> keyMetricEntry(m).also { assertNotNull(m.name, it) }!!.key }
        // Insights has no Rest metric of its own ("rest" is Sleep's alias): the score and the hours come from the same nights.
        assertEquals(mapOf("sleep" to listOf(KeyMetric.REST, KeyMetric.SLEEP_HOURS)), byKey.filterValues { it.size > 1 })
    }

    @Test
    fun theEditorTagsExactlyWhatInsightsTags() = runBlocking {
        // A phone with: a recent HRV, a very old SpO2 (any reading counts), a future-dated weight, a failing read, nothing else.
        val series = mapOf(
            "hrv" to listOf(SeriesPoint("2026-11-25", 60.0)),
            "spo2" to listOf(SeriesPoint("2025-01-03", 96.0)),
            "weight" to listOf(SeriesPoint("2026-12-01", 80.0)),
        )
        val load: suspend (String) -> List<SeriesPoint> = { key -> if (key == "mood") error("read failed") else series[key].orEmpty() }
        val editor = keyMetricsWithoutData(today) { load(it.key) }
        val insights = metricsWithoutRecentData(MetricCatalog.entries.map { it.key }, today, load)
        for (m in KeyMetric.entries) assertEquals(m.name, keyMetricEntry(m)!!.key in insights, m in editor)
        assertEquals(KeyMetric.entries.toSet() - KeyMetric.HRV - KeyMetric.BLOOD_OXYGEN, editor)
    }
}
