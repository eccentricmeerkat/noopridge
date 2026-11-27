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
import com.noop.ui.EffortScale
import com.noop.ui.Ridge
import com.noop.ui.RidgeCaloriesCard
import com.noop.ui.RidgeChargeCard
import com.noop.ui.RidgeEffortCard
import com.noop.ui.RidgeTrendsRange
import com.noop.ui.buildTrendsRidge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** RS-3e item 5: Trends' Effort and Charge cards with their y axes, beside Calories. Made-up numbers, fixed dates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp-xxhdpi")
class Rs3eTrendsSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun effortCaloriesCharge() {
        val today = LocalDate.of(2026, 11, 26)
        val rows = (0 until 40).map {
            DailyMetric("d", today.minusDays(it.toLong()).toString(), strain = 8.0 + (it * 23 % 41), recovery = 35.0 + (it * 37 % 60), activeKcalEst = 1900.0 + (it * 131 % 900))
        }.sortedBy { it.day }
        val m = buildTrendsRidge(
            V10MetricRegistry(V10MetricInputs(days = rows, today = today, caloriesByDay = rows.associate { it.day to it.activeKcalEst!! })),
            rows.associateBy { it.day }, today, RidgeTrendsRange.entries[1],
        )
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeEffortCard(m, EffortScale.HUNDRED)
                RidgeCaloriesCard(m)
                RidgeChargeCard(m)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3e_trends_cards.png")
    }
}
