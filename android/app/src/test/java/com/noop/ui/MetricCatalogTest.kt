package com.noop.ui

import com.noop.metrics.MetricId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2-X: the catalogue is complete, unambiguous, and the key-metric tiles and the weight check follow it. */
class MetricCatalogTest {

    @Test
    fun keysAndAliasesAreUnique() {
        val all = MetricCatalog.entries.flatMap { it.aliases + it.key }
        assertEquals("a key or alias is used twice: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}", all.size, all.toSet().size)
    }

    @Test
    fun everyRegistryMetricIsChartedOrHasAReason() {
        val covered = MetricCatalog.registryIds + MetricCatalog.notCharted.keys
        val missing = MetricId.entries.filter { it !in covered }
        assertTrue("registry metrics neither charted nor explained: $missing", missing.isEmpty())
        assertTrue("charted and excluded at once", MetricCatalog.registryIds.intersect(MetricCatalog.notCharted.keys).isEmpty())
    }

    @Test
    fun theFirstEightExploreKeysAndTheDeepLinkKeysResolve() {
        listOf("recovery", "strain", "hrv", "rhr", "sleep", "efficiency", "spo2", "resp").forEach { assertNotNull(it, MetricCatalog.forKey(it)) }
        // Today's deep-link keys: Rest opens Sleep, Steps the estimate, Calories active kcal, Skin the skin temperature.
        assertEquals("sleep", MetricCatalog.forKey("rest")?.key)
        assertEquals("steps", MetricCatalog.forKey("steps_est")?.key)
        assertEquals("active_kcal", MetricCatalog.forKey("active_kcal")?.key)
        assertEquals("skin", MetricCatalog.forKey("skin")?.key)
        assertEquals("weight", MetricCatalog.forKey("weight")?.key)
    }

    @Test
    fun everyKeyMetricTileNamesARealEntryOrIsOneOfTheOriginalEleven() {
        val original = setOf(
            KeyMetric.CHARGE, KeyMetric.EFFORT, KeyMetric.REST, KeyMetric.HRV, KeyMetric.RESTING_HR, KeyMetric.BLOOD_OXYGEN,
            KeyMetric.RESPIRATORY, KeyMetric.STEPS, KeyMetric.WEIGHT, KeyMetric.CALORIES, KeyMetric.SKIN_TEMP,
        )
        KeyMetric.entries.filter { it !in original }.forEach { m ->
            assertNotNull("${m.raw} has no catalogue entry", MetricCatalog.forKey(m.catalogKey))
        }
        assertEquals("a saved-layout key is used twice", KeyMetric.entries.size, KeyMetric.entries.map { it.raw }.toSet().size)
    }

    @Test
    fun everyTileHasAnExploreIconForTheEditor() {
        val missing = KeyMetric.entries.filter { keyMetricEntry(it) == null }
        assertTrue("tiles with no catalogue icon: $missing", missing.isEmpty())
    }

    @Test
    fun theDefaultLayoutIsNineTilesAndTheCapIsNine() {
        assertEquals(
            listOf("charge", "effort", "rest", "hrv", "restingHr", "bloodOxygen", "respiratory", "steps", "calories"),
            KeyMetric.defaultOrder.map { it.raw },
        )
        assertEquals(9, KeyMetric.MAX_SHOWN)
        assertEquals(KeyMetric.MAX_SHOWN, KeyMetric.defaultOrder.size)
        // A saved layout from before B2-X still decodes to the same tiles.
        assertEquals(listOf(KeyMetric.HRV, KeyMetric.STEPS), KeyMetricPrefs.decodeEnabled("hrv,steps"))
        assertEquals(listOf(KeyMetric.SDNN, KeyMetric.WEIGHT), KeyMetricPrefs.decodeEnabled("sdnn,weight"))
    }

    @Test
    fun theWeightCheckFiresOnlyBeyondTwoKilograms() {
        assertFalse(WeightCheck.differs(75.0, null))
        assertFalse(WeightCheck.differs(75.0, 75.0))
        assertFalse(WeightCheck.differs(75.0, 77.0))      // exactly 2 kg is not "more than"
        assertTrue(WeightCheck.differs(75.0, 77.1))
        assertTrue(WeightCheck.differs(80.0, 77.0))
    }
}
