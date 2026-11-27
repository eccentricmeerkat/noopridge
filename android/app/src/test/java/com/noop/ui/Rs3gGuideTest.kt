package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RS-3g: every score fills the guide's layout, and its bands are the code's own. */
class Rs3gGuideTest {

    @Test
    fun everyScoreHasEveryPart() {
        GuideScore.entries.forEach { score ->
            val c = guideContent(score)
            assertEquals(score, c.score)
            listOf(c.title, c.scale, c.intro, c.measures, c.captured, c.means, c.note).forEach { assertTrue("$score", it.isNotBlank()) }
            assertTrue("$score", c.inputs.isNotEmpty())
        }
    }

    @Test
    fun bandsComeFromTheCodesThresholds() {
        fun ranges(bands: List<GuideBand>) = bands.map { it.range }
        assertEquals(ranges(ScoringGuideBands.readiness()), ranges(guideContent(GuideScore.READINESS).bands))
        assertEquals(ranges(ScoringGuideBands.chargeShown()), ranges(guideContent(GuideScore.CHARGE).bands))
        assertEquals(ranges(ScoringGuideBands.effort()), ranges(guideContent(GuideScore.EFFORT).bands))
        assertEquals(ranges(ScoringGuideBands.rest()), ranges(guideContent(GuideScore.REST).bands))
        assertEquals(ranges(ScoringGuideBands.stress()), ranges(guideContent(GuideScore.STRESS).bands))
        listOf(GuideScore.SLEEP_SCORE, GuideScore.DEBT, GuideScore.VO2, GuideScore.BODY_AGE, GuideScore.FIT_AGE, GuideScore.BASELINES)
            .forEach { assertTrue("$it", guideContent(it).bands.isEmpty()) }
    }

    /** The statements that were wrong before RS-3g stay gone. */
    @Test
    fun theOldClaimsAreNotRepeated() {
        val all = GuideScore.entries.joinToString(" ") { s -> guideContent(s).run { "$intro $measures $captured $means ${inputs.joinToString()}" } }
        listOf("28 measures", "Phone GPS", "from VO₂ max alone").forEach { assertTrue(it, !all.contains(it, ignoreCase = true)) }
    }
}
