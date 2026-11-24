package com.noop.analytics

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** SQ5-a: nap detection is on for a fresh Ridge install, and a wearer's own choice is kept. */
@RunWith(RobolectricTestRunner::class)
class NapPrefsDefaultTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aFreshInstallDetectsNaps() {
        context.getSharedPreferences("noop_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(NapPrefs.enabled(context))
        assertTrue(NapPrefs.config(context).enabled)
    }

    @Test
    fun aWearerWhoTurnedItOffStaysOff() {
        NapPrefs.setEnabled(context, false)
        assertFalse(NapPrefs.enabled(context))
        NapPrefs.setEnabled(context, true)
        assertTrue(NapPrefs.enabled(context))
    }
}
