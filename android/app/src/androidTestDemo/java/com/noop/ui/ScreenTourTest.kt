package com.noop.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noop.R
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screen tour: walks every bottom-bar tab and every More-page destination of the Demo flavor (which
 * seeds 120 days of synthetic data) and saves one full-screen PNG per screen. Not an assertion test —
 * it exists to produce a visual record of the app.
 *
 * Run:   ./gradlew connectedDemoDebugAndroidTest \
 *          -Pandroid.testInstrumentationRunnerArguments.class=com.noop.ui.ScreenTourTest
 * Pull:  adb pull /sdcard/Android/data/com.noop.app.ridge.demo.debug/files/screen-tour
 */
@RunWith(AndroidJUnit4::class)
class ScreenTourTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val outDir = File(context.getExternalFilesDir(null), "screen-tour")

    @Test
    fun tourEveryScreen() {
        prepareFirstRunState()
        grantPermissions()
        outDir.deleteRecursively()
        outDir.mkdirs()

        // The day-cycle sky and other ambient animations never finish, so Compose never reports idle.
        // Pause the test clock and advance it by hand in [settle] instead.
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use {
            // The demo seeder runs on IO after launch; give it time to land before the first capture.
            settle(8_000)

            var index = 0
            fun shot(name: String) {
                index++
                capture("%02d_%s".format(index, name))
            }

            // Bottom-bar tabs.
            listOf(R.string.nav_today, R.string.nav_trends, R.string.nav_sleep, R.string.nav_coach)
                .forEach { res ->
                    if (clickBarTab(context.getString(res))) shot(routeName(res))
                }

            // More page, then every grouped destination listed on it.
            clickBarTab(context.getString(R.string.nav_more))
            shot("more")
            drawerGroups.flatMap { it.items }.forEach { dest ->
                clickBarTab(context.getString(R.string.nav_more))
                val title = context.getString(dest.titleRes)
                val row = compose.onAllNodes(
                    hasText(title) and hasClickAction() and hasAnyAncestor(hasScrollAction()),
                ).onFirst()
                runCatching {
                    row.performScrollTo()
                    row.performClick()
                    settle(2_500)
                    shot(dest.route)
                }.onFailure { android.util.Log.w(TAG, "skipped ${dest.route}: ${it.message}") }
            }
        }
        android.util.Log.i(TAG, "screens saved to ${outDir.absolutePath}")
    }

    /** Accept the terms, skip onboarding and What's New, and open every More group. */
    private fun prepareFirstRunState() {
        NoopPrefs.of(context).edit()
            .putString(NoopPrefs.KEY_ACCEPTED_TERMS_VERSION, Terms.CURRENT_VERSION)
            .putBoolean(NoopPrefs.KEY_ONBOARDED, true)
            .putString(NoopPrefs.KEY_LAST_SEEN_CHANGELOG, AppChangelog.CURRENT_VERSION)
            .commit()
        MoreSectionPrefs.write(NoopPrefs.of(context), drawerGroups.map { it.header }.toSet())
    }

    /** Pre-grant runtime permissions so no system dialog covers a capture. */
    private fun grantPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.forEach { p ->
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, p) }
        }
    }

    /** Clicks the bottom-bar slot labelled [label]; false when the bar has no such tab (e.g. Coach off). */
    private fun clickBarTab(label: String): Boolean {
        val nodes = compose.onAllNodes(
            hasText(label) and hasClickAction() and !hasAnyAncestor(hasScrollAction()),
        )
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes.onFirst().performClick()
        settle(1_500)
        return true
    }

    /** Let navigation fades and Room-backed loads finish before capturing. */
    private fun settle(ms: Long) {
        // Advance the paused clock in step with real time, so IO loads land while frames keep flowing.
        var left = ms
        while (left > 0) {
            Thread.sleep(FRAME_STEP_MS)
            compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
            left -= FRAME_STEP_MS
        }
    }

    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun routeName(res: Int): String =
        context.resources.getResourceEntryName(res).removePrefix("nav_")

    private companion object {
        const val TAG = "ScreenTour"
        const val FRAME_STEP_MS = 100L
    }
}
