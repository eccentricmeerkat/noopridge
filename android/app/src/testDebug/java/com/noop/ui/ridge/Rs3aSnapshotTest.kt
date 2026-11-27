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
import com.noop.ui.BaselineRange
import com.noop.ui.LongRunHeartCard
import com.noop.ui.Ridge
import com.noop.ui.buildBaseline
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** RS-3a: the taller heart-signals card, over six months, over a year and with a single night. Fixed dates, so stable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp-xxhdpi")
class Rs3aSnapshotTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 26)

    private fun model(nights: Int, range: BaselineRange = BaselineRange.D180) = (0 until nights).map { back ->
        // A slow drift with night-to-night scatter, one night in nine missing.
        val wobble = (back * 37 % 11) - 5
        DailyMetric(
            "d", today.minusDays(back.toLong()).toString(), totalSleepMin = 430.0,
            restingHr = if (back % 9 == 8) null else 58 - back / 60 + wobble / 2,
            avgHrv = if (back % 9 == 8) null else 47.0 + back / 15.0 + wobble,
        )
    }.sortedBy { it.day }.let { days ->
        buildBaseline(V10MetricRegistry(V10MetricInputs(days = days, today = today, age = 40)), today, range, 40, false)
    }

    @Test
    fun sixMonths() {
        val m = model(180)
        compose.setContent { Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { LongRunHeartCard(m) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3a_heart_six_months.png")
    }

    @Test
    fun oneYear() {
        val m = model(365, BaselineRange.D365)
        compose.setContent { Column(Modifier.background(Ridge.bg100).padding(16.dp)) { LongRunHeartCard(m) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3a_heart_one_year.png")
    }

    @Test
    fun oneNight() {
        val m = model(1)
        compose.setContent { Column(Modifier.background(Ridge.bg100).padding(16.dp)) { LongRunHeartCard(m) } }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3a_heart_one_night.png")
    }
}
