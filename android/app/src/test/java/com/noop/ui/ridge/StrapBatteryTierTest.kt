package com.noop.ui.ridge

import org.junit.Assert.assertEquals
import org.junit.Test

/** SQ8a (A4): the strap battery orb's tiers, at every boundary of the spec's table. */
class StrapBatteryTierTest {

    @Test
    fun tiersSplitAtTheSpecBoundaries() {
        val expected = listOf(
            0 to StrapBatteryTier.CRITICAL,
            9 to StrapBatteryTier.CRITICAL,
            10 to StrapBatteryTier.LOW,
            39 to StrapBatteryTier.LOW,
            40 to StrapBatteryTier.MEDIUM,
            69 to StrapBatteryTier.MEDIUM,
            70 to StrapBatteryTier.HIGH,
            99 to StrapBatteryTier.HIGH,
            100 to StrapBatteryTier.FULL,
        )
        expected.forEach { (pct, tier) -> assertEquals("$pct%", tier, strapBatteryTier(pct)) }
    }

    @Test
    fun fullAndHighShareTheirColours() {
        assertEquals(StrapBatteryTier.HIGH.ring, StrapBatteryTier.FULL.ring)
        assertEquals(StrapBatteryTier.HIGH.liquid, StrapBatteryTier.FULL.liquid)
        assertEquals(StrapBatteryTier.HIGH.glow, StrapBatteryTier.FULL.glow)
    }
}
