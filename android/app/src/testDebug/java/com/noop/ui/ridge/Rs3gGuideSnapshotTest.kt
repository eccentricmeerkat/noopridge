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
import com.noop.ui.GuideScore
import com.noop.ui.Ridge
import com.noop.ui.RidgeScoringGuideContent
import com.noop.ui.ScoringGuideLink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-3g: the guide's three layouts: with bands (Stress), without (Sleep score), and with the reader's ranges (Baselines). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp-xxhdpi")
class Rs3gGuideSnapshotTest {

    @get:Rule val compose = createComposeRule()

    private fun capture(score: GuideScore, name: String) {
        ScoringGuideLink.pending = score
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                RidgeScoringGuideContent(initial = null, vm = null)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3g_guide_$name.png")
    }

    @Test fun bands() = capture(GuideScore.STRESS, "stress")

    @Test fun noBands() = capture(GuideScore.SLEEP_SCORE, "sleep_score")

    @Test fun baselines() = capture(GuideScore.BASELINES, "baselines")
}
