package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.noop.ui.Ridge
import com.noop.ui.TodayAlertCards
import com.noop.ui.TodayAlertsState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-3i: with every signal and every vital firing, no line on the alert cards is cut off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp-xxhdpi")
class Rs3iAlertFitTest {

    @get:Rule val compose = createComposeRule()

    private val signals = "Resting HR +13 bpm, HRV −50%, skin temp +0.9°C, respiration up"
    private val vitals = listOf("Resp Rate", "Blood O₂", "Resting HR", "HRV", "Skin Temp")

    @Test
    fun nothingIsCutOff() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TodayAlertCards(TodayAlertsState(vitals, signals)) {}
                TodayAlertCards(TodayAlertsState(vitals.take(3), "Resting HR +13 bpm, HRV −50%, respiration up")) {}
                TodayAlertCards(TodayAlertsState(listOf("Resp Rate", "Blood O₂", "Resting HR", "Skin Temp"), signals)) {}
                TodayAlertCards(TodayAlertsState(emptyList(), signals)) {}
                TodayAlertCards(TodayAlertsState(vitals, null)) {}
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3i_alert_fit.png")
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        val cut = texts.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.takeIf { r -> (0 until r.lineCount).any { r.isLineEllipsized(it) } }?.layoutInput?.text?.text
        }
        assertTrue("cut off: $cut", cut.isEmpty())
    }
}
