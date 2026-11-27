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
import com.noop.ui.RidgeSleepScoreCard
import com.noop.ui.ScoringGuideLink
import com.noop.ui.SleepScoreReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-3c: the Sleep page's score card (a night, and one that can't be scored) and the guide's Sleep score page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xxhdpi")
class Rs3cSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun sleepCard() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeSleepScoreCard(SleepScoreReading(83.6), onHow = {})
                RidgeSleepScoreCard(SleepScoreReading(null), onHow = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3c_sleep_card.png")
    }

    @Test
    fun guidePage() {
        ScoringGuideLink.pending = GuideScore.SLEEP_SCORE
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeScoringGuideContent(initial = null, vm = null)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs3c_guide.png")
    }
}
