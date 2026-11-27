package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.github.takahirom.roborazzi.captureRoboImage
import com.noop.ui.Ridge
import com.noop.ui.SynthesisGlassCard
import com.noop.ui.TodayAlertCards
import com.noop.ui.TodayAlertsState
import com.noop.ui.readinessCardModel
import com.noop.ui.readinessCardUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-3e item 4: each alert layout is the same height as the closed Readiness card, on a 411 dp phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class Rs3eCardHeightTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun alertCardsMatchTheClosedReadinessCard() {
        val signals = "Resting HR +6 bpm, HRV −24%"
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.testTag("readiness")) {
                    SynthesisGlassCard("Readiness", readinessCardUi(readinessCardModel(65.0, null, 94.0, 18.6, null, true), null), false) {}
                }
                Box(Modifier.testTag("vitals")) { TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Skin Temp"), null), onOpenVitals = {}) }
                Box(Modifier.testTag("illness")) { TodayAlertCards(TodayAlertsState(emptyList(), signals), onOpenVitals = {}) }
                Box(Modifier.testTag("both")) { TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Skin Temp"), signals), onOpenVitals = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3e_card_heights.png")
        val readiness = compose.onNodeWithTag("readiness").getBoundsInRoot().height.value
        for (tag in listOf("vitals", "illness", "both")) {
            assertEquals(tag, readiness, compose.onNodeWithTag(tag).getBoundsInRoot().height.value, 0.6f)
        }
    }
}
