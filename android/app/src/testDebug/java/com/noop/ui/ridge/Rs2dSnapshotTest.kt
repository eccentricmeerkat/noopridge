package com.noop.ui.ridge

import androidx.compose.foundation.background
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
import com.noop.ui.Ridge
import com.noop.ui.RidgeConsistencyCard
import com.noop.ui.buildConsistency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** RS-2d: the Consistency calendar with a moon on every night whose Rest reads 85 or more. Fixed dates, so stable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class Rs2dSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun consistencyCard() {
        val today = LocalDate.of(2026, 11, 26)
        val keys = (0 until 80).map { today.minusDays(it.toLong()).toString() }
        val days = keys.mapIndexed { i, k -> DailyMetric("d", k, strain = listOf(20.0, 55.0, 72.0, 90.0)[i % 4]) }.sortedBy { it.day }
        // Rest cycles 78, 84, 85, 91, 100: three moons in every five nights.
        val rest = keys.mapIndexed { i, k -> k to listOf(78.0, 84.0, 85.0, 91.0, 100.0)[i % 5] }.toMap()
        val trend = buildConsistency(V10MetricRegistry(V10MetricInputs(days = days, restByDay = rest, today = today)), today)
        compose.setContent { Column(Modifier.background(Ridge.bg100).padding(16.dp)) { RidgeConsistencyCard(trend) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs2d_consistency.png")
    }
}
