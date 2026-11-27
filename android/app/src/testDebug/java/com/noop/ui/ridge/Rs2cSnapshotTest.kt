package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.noop.ui.Ridge
import com.noop.ui.RidgeReleaseNotesBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** RS-2c: What's new with one card per minor release (12.2, 12.1 with its patch headings, 12.0) and the GitHub link. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h4200dp-xxhdpi")
class Rs2cSnapshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun whatsNewCards() {
        compose.setContent {
            Column(Modifier.background(Ridge.bg100).padding(16.dp).verticalScroll(rememberScrollState())) { RidgeReleaseNotesBody() }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/rs2c_whats_new.png")
    }
}
