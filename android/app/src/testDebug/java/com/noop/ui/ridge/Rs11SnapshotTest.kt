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
import com.noop.ui.AboutUpdateStatus
import com.noop.ui.AboutUpdatesCardContent
import com.noop.ui.HeartCardState
import com.noop.ui.Ridge
import com.noop.ui.RidgeHeartCardContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RS-1-1: snapshots of the two new card faces in every state that does not depend on the clock: the Hub > About
 * Updates card (never checked, checking, up to date, failed, update available) and the Vital signs heart card (Live,
 * Waiting, Not connected). Same setup and record flag as [RidgeComponentSnapshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xxhdpi")
class Rs11SnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aboutUpdatesCard() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    AboutUpdateStatus.NeverChecked,
                    AboutUpdateStatus.Checking,
                    AboutUpdateStatus.UpToDate("12.1.2"),
                    AboutUpdateStatus.Failed,
                    AboutUpdateStatus.Available("12.2.0", "https://example.invalid/r", "Faster sync.\nA new Sleep card."),
                ).forEachIndexed { i, s ->
                    AboutUpdatesCardContent(s, "12.1.2", autoCheck = i % 2 == 0, onCheck = {}, onAutoCheck = {}, onDownload = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs11_about_updates.png")
    }

    @Test
    fun vitalsHeartCard() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RidgeHeartCardContent(HeartCardState.LIVE, 64, "WHOOP 4.0", animate = false)
                RidgeHeartCardContent(HeartCardState.WAITING, null, "WHOOP 4.0", animate = false)
                RidgeHeartCardContent(HeartCardState.NOT_CONNECTED, null, null, animate = false)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs11_vitals_heart.png")
    }
}
