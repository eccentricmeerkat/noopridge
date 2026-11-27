package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.noop.ui.KeyMetricEditorTitle
import com.noop.ui.Ridge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-2e: Key Metrics editor names with and without the "No data" tag, a long name included (the tag stays visible). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-xxhdpi")
class Rs2eSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun editorTitles() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KeyMetricEditorTitle("HRV", false, Ridge.ink, Modifier.fillMaxWidth())
                KeyMetricEditorTitle("Body fat", true, Ridge.ink, Modifier.fillMaxWidth())
                KeyMetricEditorTitle("VO₂ max", true, Ridge.inkSubtle, Modifier.fillMaxWidth())
                KeyMetricEditorTitle("A very long metric name that cannot fit on one line of the sheet", true, Ridge.ink, Modifier.fillMaxWidth())
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs2e_editor_titles.png")
    }
}
