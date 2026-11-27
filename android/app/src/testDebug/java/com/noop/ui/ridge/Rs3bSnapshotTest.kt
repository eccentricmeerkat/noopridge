package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.noop.data.DailyMetric
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.ui.ReadinessCardModel
import com.noop.ui.Ridge
import com.noop.ui.RidgeScoringGuideContent
import com.noop.ui.RidgeSynthesisCard
import com.noop.ui.SynthesisGlassCard
import com.noop.ui.buildSynthesis
import com.noop.ui.readinessCardModel
import com.noop.ui.readinessCardUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** RS-3b: the Today Readiness card in each state, the Daily Readiness calendar and the guide's Readiness page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp-xxhdpi")
class Rs3bSnapshotTest {

    @get:Rule val compose = createComposeRule()

    private fun model(effort: Double? = 3.9 * 100 / 21, calibrating: Int? = null, rest: Double? = 94.0, today: Boolean = true) =
        readinessCardModel(65.0, null, rest, effort, calibrating, today)

    @Test
    fun todayCards() {
        val states: List<Pair<ReadinessCardModel, Boolean>> = listOf(
            model() to false, model() to true, model(effort = null) to true, model(today = false) to true,
            model(calibrating = 2) to true, model(rest = null) to true,
        )
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                states.forEach { (m, open) -> SynthesisGlassCard("Readiness", readinessCardUi(m, null), open) {} }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3b_today_cards.png")
    }

    @Test
    fun calendar() {
        val today = LocalDate.of(2026, 11, 26)
        val keys = (0 until 140).map { today.minusDays(it.toLong()).toString() }
        val days = keys.mapIndexed { i, k -> DailyMetric("d", k, recovery = (i * 37 % 101).toDouble()) }.sortedBy { it.day }
        val rest = keys.mapIndexed { i, k -> k to 70.0 + i % 25 }.toMap()
        val s = buildSynthesis(V10MetricRegistry(V10MetricInputs(days = days, restByDay = rest, today = today)), today, readiness = true)
        compose.setContent { Column(Modifier.background(Ridge.bg100).padding(16.dp)) { RidgeSynthesisCard(s, readiness = true) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3b_calendar.png")
    }

    @Test
    fun guide() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeScoringGuideContent(initial = null, vm = null)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3b_guide.png")
    }
}
