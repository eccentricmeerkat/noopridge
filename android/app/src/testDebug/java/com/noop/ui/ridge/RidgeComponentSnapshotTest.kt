package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B-02 (P1-3): Roborazzi snapshots of the Ridge components, one image per gallery section, at default and 1.3x
 * font scale. Components only; screens are not snapshot-tested. Images live in `app/src/test/snapshots/ridge/`
 * (Git LFS).
 *
 * Record after a deliberate visual change, then review the diff:
 *   ./gradlew testFullDebugUnitTest --tests com.noop.ui.ridge.RidgeComponentSnapshotTest -Proborazzi.record=true
 * Otherwise every run compares and fails on a changed pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp-xxhdpi")
class RidgeComponentSnapshotTest {

    @get:Rule val compose = createComposeRule()

    private fun snap(section: GallerySection, suffix: String = "") {
        compose.setContent { RidgeGallerySection(section) }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/${section.name.lowercase()}$suffix.png")
    }

    @Test fun chips() = snap(GallerySection.Chips)
    @Test fun buttons() = snap(GallerySection.Buttons)
    @Test fun headers() = snap(GallerySection.Headers)
    @Test fun rows() = snap(GallerySection.Rows)
    @Test fun controls() = snap(GallerySection.Controls)
    @Test fun cards() = snap(GallerySection.Cards)
    @Test fun tabBar() = snap(GallerySection.TabBar)
    @Test fun icons() = snap(GallerySection.Icons)
    @Test fun hubParts() = snap(GallerySection.HubParts)

    /** SQ8a (A4): the strap battery orb in every state, at twice the header size and at the header's 31 dp. */
    @Test
    fun strapOrb() {
        compose.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier
                    .background(com.noop.ui.Ridge.bg100)
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                val levels: @androidx.compose.runtime.Composable (androidx.compose.ui.unit.Dp) -> Unit = { size ->
                    listOf(100, 56, 28, 9).forEach { StrapBatteryOrb(it, StrapOrbState.LEVEL, size) }
                }
                val others: @androidx.compose.runtime.Composable (androidx.compose.ui.unit.Dp) -> Unit = { size ->
                    StrapBatteryOrb(56, StrapOrbState.LEVEL, size, syncing = true)
                    StrapBatteryOrb(null, StrapOrbState.DISCONNECTED, size)
                    StrapBatteryOrb(null, StrapOrbState.CHARGING, size)
                    StrapBatteryOrb(null, StrapOrbState.UNKNOWN, size)
                }
                val gap = androidx.compose.foundation.layout.Arrangement.spacedBy(28.dp)
                androidx.compose.foundation.layout.Row(horizontalArrangement = gap) { levels(62.dp) }
                androidx.compose.foundation.layout.Row(horizontalArrangement = gap) { others(62.dp) }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) { levels(31.dp); others(31.dp) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/strap_orb.png")
    }

    @Test
    @Config(fontScale = 1.3f)
    fun largeFont() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                RidgeGallerySection(GallerySection.Buttons); RidgeGallerySection(GallerySection.TabBar)
                RidgeGallerySection(GallerySection.Rows)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/ridge/large_font.png")
    }
}
