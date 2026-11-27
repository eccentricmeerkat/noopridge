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
import com.noop.ui.HoursAsleepChart
import com.noop.ui.Ridge
import com.noop.ui.RidgeHoursAsleepCard
import com.noop.ui.RidgeHoursVsNeededCard
import com.noop.ui.SleepNeedSplit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * RS-1-2: snapshots of the Sleep page's Hours vs Needed card (all three parts; no Effort history and no debt; a night past
 * the need) and the Hours asleep card (full, with gaps and a short night, and the empty state). Fixed dates, so stable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
class Rs12SnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun hoursVsNeededCard() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The board's night: 7h 18m against 6h 12m + 54m + 1h.
                RidgeHoursVsNeededCard(438.0, SleepNeedSplit(372.0, 54, 60), 90.0, "", onHowRestWorks = {})
                // No Effort history ("—"), no debt, no Rest score.
                RidgeHoursVsNeededCard(400.0, SleepNeedSplit(480.0, null, 0), null, "Whoop", onHowRestWorks = {})
                // Slept past the need: the bar fills the track, the label keeps the real %.
                RidgeHoursVsNeededCard(560.0, SleepNeedSplit(480.0, 0, 0), 97.0, "", onHowRestWorks = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs12_hours_vs_needed.png")
    }

    @Test
    fun hoursAsleepCard() {
        val last = LocalDate.of(2026, 11, 24)
        fun k(back: Int) = last.minusDays(back.toLong()).toString()
        val full = (0 until 14).associate { k(it) to listOf(444.0, 396.0, 450.0, 498.0, 462.0, 462.0, 444.0, 312.0, 426.0, 420.0, 474.0, 492.0, 414.0, 438.0)[it] }
        val gaps = full - k(0) - k(5) - k(9)
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeHoursAsleepCard(HoursAsleepChart.build(full, last), 486.0)
                RidgeHoursAsleepCard(HoursAsleepChart.build(gaps, last), 486.0)
                RidgeHoursAsleepCard(HoursAsleepChart.build(mapOf(k(0) to 420.0), last), 486.0)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs12_hours_asleep.png")
    }
}
