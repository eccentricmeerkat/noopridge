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
import com.noop.ui.BodyAgeTrend
import com.noop.ui.BodyAgeTrendModel
import com.noop.ui.PaceOfAgingCard
import com.noop.ui.Ridge
import com.noop.ui.TrendViewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** RS-1-3: the Baseline Pace of aging card (with pace, and before 30 days) and the Trend View sheet's content. Fixed data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2600dp-xxhdpi")
class Rs13SnapshotTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)

    /** Six months that improve steadily: resting HR 64 to 56, HRV 36 to 46, steps 6,000 to 8,500, steady sleep. */
    private fun trend(): BodyAgeTrend {
        val days = (0 until 200).map { back ->
            val f = 1.0 - back / 200.0
            DailyMetric(
                deviceId = "d", day = today.minusDays(back.toLong()).toString(),
                totalSleepMin = 440.0 + (back % 3) * 15, restingHr = (64 - 8 * f).toInt(), avgHrv = 36 + 10 * f,
                steps = (6000 + 2500 * f).toInt(),
            )
        }
        val born = LocalDate.of(1986, 3, 10)
        // Stored weekly body age falling from 36.6 to 34.0 over the 26 weeks, as the board draws it.
        val stored = (0 until 26).associate { w -> today.minusWeeks(w.toLong()).toString() to 34.0 + 2.6 * w / 25.0 }
        return BodyAgeTrendModel.build(days, today, 40.0, { java.time.temporal.ChronoUnit.DAYS.between(born, it) / 365.2425 }, 45.8, 47.4, stored)
    }

    @Test
    fun paceCard() {
        val t = trend()
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PaceOfAgingCard(t, onOpenTrend = {})
                PaceOfAgingCard(t.copy(pace = null, paceLastMonth = null), onOpenTrend = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs13_pace_card.png")
    }

    @Test
    fun birthdayPicker() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp)) {
                com.noop.ui.BirthdayPicker(LocalDate.of(1996, 11, 17), 13, 100, onCancel = {}, onDone = {}, today = today)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs13_birthday_picker.png")
    }

    @Test
    fun trendView() {
        val t = trend()
        compose.setContent {
            Column(Modifier.background(Ridge.sheetBottom).padding(20.dp)) { TrendViewContent(t, onDone = {}) }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs13_trend_view.png")
    }
}
