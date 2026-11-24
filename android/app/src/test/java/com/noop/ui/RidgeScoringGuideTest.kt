package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** BS-x2-4 item 22: the guide's tabs, deep links and the bands it quotes agree with the code. */
class RidgeScoringGuideTest {
    @Test fun theThreeDailyScoresLiveUnderCoreMetrics() {
        listOf(GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST).forEach { assertEquals(GuideTab.CORE, it.tab()) }
        assertEquals(GuideTab.STRESS, GuideScore.STRESS.tab())
        assertEquals(GuideTab.BASELINES, GuideScore.BASELINES.tab())
    }

    @Test fun todayDeepLinksLandOnTheirScore() {
        assertEquals(GuideScore.CHARGE, ScoreSection.CHARGE.guideScore())
        assertEquals(GuideScore.EFFORT, ScoreSection.EFFORT.guideScore())
        assertEquals(GuideScore.REST, ScoreSection.REST.guideScore())
    }

    @Test fun everyTabExists() = assertEquals(
        listOf("Core Metrics", "Stress", "Sleep debt", "VO₂ max", "Body age", "Fitness age", "Baselines"),
        GuideTab.entries.map { it.label },
    )

    @Test fun effortBandsMatchTheCodesBoundaries() {
        val b = ScoringGuideBands.effort()
        assertEquals("0–47", b[0].range); assertEquals(EffortBand.REST, effortBand(47.0))
        assertEquals("48–66", b[1].range); assertEquals(EffortBand.MODERATE, effortBand(48.0)); assertEquals(EffortBand.MODERATE, effortBand(66.0))
        assertEquals("67–85", b[2].range); assertEquals(EffortBand.HIGH, effortBand(67.0)); assertEquals(EffortBand.HIGH, effortBand(85.0))
        assertEquals("86–100", b[3].range); assertEquals(EffortBand.ALL_OUT, effortBand(86.0))
    }

    @Test fun stressBandsMatchTheCodesBoundaries() {
        val b = ScoringGuideBands.stress()
        assertEquals(listOf("0–0.9", "1.0–1.9", "2.0–3.0"), b.map { it.range })
        assertEquals(StressBand.Low, StressBand.forScore(0.9)); assertEquals(StressBand.Medium, StressBand.forScore(1.0))
        assertEquals(StressBand.Medium, StressBand.forScore(1.9)); assertEquals(StressBand.High, StressBand.forScore(2.0))
    }

    @Test fun chargeAndRestHaveTheAppsBandCounts() {
        assertEquals(listOf("0–24", "25–49", "50–69", "70–87", "88–100"), ScoringGuideBands.charge().map { it.range })
        assertEquals(listOf("0–49", "50–69", "70–84", "85–100"), ScoringGuideBands.rest().map { it.range })
    }
}
