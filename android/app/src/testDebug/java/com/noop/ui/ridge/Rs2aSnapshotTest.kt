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
import com.noop.ui.Ridge
import com.noop.ui.TodayAlertCards
import com.noop.ui.TodayAlertsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-2a: Today's alert slot. The Vitals card alone (two and five outside), the Illness heads-up alone, and both side by side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
class Rs2aSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun todayAlertCards() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Skin Temp"), null), onOpenVitals = {})
                TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Blood O₂", "Resting HR", "HRV", "Skin Temp"), null), onOpenVitals = {})
                TodayAlertCards(TodayAlertsState(emptyList(), "Resting HR +6 bpm, HRV −24%"), onOpenVitals = {})
                TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Skin Temp"), "Resting HR +6 bpm, HRV −24%"), onOpenVitals = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs2a_today_alerts.png")
    }
}
