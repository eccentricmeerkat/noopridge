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
import com.noop.ui.AutomationsGroupBox
import com.noop.ui.Ridge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-2b: the Automations & Alarms group boxes, collapsed (the default), and Automations open with its sections nested as rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
class Rs2bSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun groupHeaders() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AutomationsGroupBox("Automations", "4 of 7 on", expanded = false, onToggle = {}) {}
                AutomationsGroupBox("Alarms", "Smart wake, strap buzz and wind-down · 1 of 3 on", expanded = false, onToggle = {}) {}
                // Open: the sections are rows inside the box, one of them open with its body.
                AutomationsGroupBox("Automations", "4 of 7 on", expanded = true, onToggle = {}) {
                    RidgeAccordionCard(eyebrow = "", title = "Haptics", icon = RidgeIcons.pulse, summary = "ON", nested = true, controlledExpanded = true, onToggle = {}) {
                        androidx.compose.material3.Text("Choose which in-session cues buzz your wrist.", color = Ridge.inkMuted)
                    }
                    RidgeAccordionCard(eyebrow = "", title = "Double-tap", icon = RidgeIcons.bolt, summary = "Off", nested = true, controlledExpanded = false, onToggle = {}) {}
                    RidgeAccordionCard(eyebrow = "", title = "Illness early-warning", icon = RidgeIcons.heart, summary = "ON", nested = true, controlledExpanded = false, onToggle = {}) {}
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs2b_group_headers.png")
    }
}
