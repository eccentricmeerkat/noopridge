package com.noop.ui

import android.content.SharedPreferences
import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricsV2
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.V2Change
import com.noop.metrics.oneSleepNeedHours
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * R9a (`metricsV2`) on the screens: the Sleep tab's need, Consistency's moons, the plan line, the logical day and the
 * age source. Each change is checked off (v10) and on.
 */
class MetricsV2ScreensTest {

    @After
    fun reset() = MetricsV2.resetForTest()

    private val today = LocalDate.of(2026, 11, 12)
    private fun day(back: Int) = today.minusDays(back.toLong()).toString()

    /** 30 staged nights ending today: 6h 20m to 9h. */
    private fun nights(): List<DailyMetric> = (29 downTo 0).map { back ->
        val asleep = 380.0 + (back * 29 % 160)
        DailyMetric("d", day(back), totalSleepMin = asleep, deepMin = asleep * 0.2, remMin = asleep * 0.25, lightMin = asleep * 0.55,
            avgHrv = 60.0, restingHr = 52, recovery = 60.0, strain = 40.0)
    }

    // MARK: - V2-1 on the Sleep tab

    @Test
    fun theSleepTabMeasuresAgainstTheOneNeed() {
        val days = nights()
        val off = buildSleepModel(days, null, todayKey = today.toString(), oneNeed = false)!!
        val on = buildSleepModel(days, null, todayKey = today.toString(), oneNeed = true, oneNeedAge = 40)!!
        val need = oneSleepNeedHours(days.associate { it.day to it.totalSleepMin }, today, 40) * 60.0
        assertEquals(hoursVsNeededPct(days.last().totalSleepMin, need)!!, on.hoursVsNeeded.latest!!, 1e-9)
        assertEquals(hoursVsNeededPct(days.last().totalSleepMin, sleepTabNeedMin(days))!!, off.hoursVsNeeded.latest!!, 1e-9)
        assertEquals(need / 60.0, on.trendNeedHours.last(), 1e-9)
        // The tab and the registry agree with the change on.
        val reg = V10MetricRegistry(V10MetricInputs(days = days, today = today, age = 40, v2 = setOf(V2Change.ONE_SLEEP_NEED)))
        assertEquals(reg.value(com.noop.metrics.MetricId.SLEEP_DEBT, DayKey(today)).value, on.debtByDay[today.toString()])
    }

    // MARK: - V2-3 slept on target

    @Test
    fun aMoonMeansAsleepAtOrAboveThatNightsNeed() {
        val days = nights()
        val rest = days.associate { it.day to 90.0 }
        val reg = V10MetricRegistry(V10MetricInputs(days = days, restByDay = rest, today = today, age = 40,
            v2 = setOf(V2Change.ONE_SLEEP_NEED, V2Change.SLEPT_ON_TARGET)))
        val off = buildConsistency(reg, today, v2 = emptySet())
        val on = buildConsistency(reg, today, v2 = setOf(V2Change.ONE_SLEEP_NEED, V2Change.SLEPT_ON_TARGET))
        val asleep = days.associate { it.day to it.totalSleepMin!! }
        val past = on.cells.filterNot { it.future }
        assertEquals(off.trackedNights, past.count { it.onTarget != null })
        for (c in past) {
            val a = asleep[c.date.toString()] ?: continue
            val need = oneSleepNeedHours(asleep, c.date, 40) * 60.0
            assertEquals(c.date.toString(), a >= need, c.onTarget)
        }
        // Rest 90 every night: v10 marks every tracked night.
        assertEquals(off.trackedNights, off.onTarget)
        assertTrue(on.onTarget < off.onTarget)
        // Without the one need, the moons stay on Rest.
        assertEquals(off, buildConsistency(reg, today, v2 = setOf(V2Change.SLEPT_ON_TARGET)))
    }

    // MARK: - V2-2 the plan line

    @Test
    fun thePlanNamesTonightsOneNeed() {
        val days = nights()
        val need = planSleepNeedMin(days, today, 40)!!
        assertEquals(oneSleepNeedHours(days.associate { it.day to it.totalSleepMin }, today, 40) * 60.0, need, 1e-9)
        assertEquals(8.0 * 60.0, planSleepNeedMin(emptyList(), today, 40)!!, 1e-9)
    }

    // MARK: - V2-8 the logical day

    @Test
    fun theLogicalDayFollowsTheWallClockOnDstMornings() {
        val london = ZoneId.of("Europe/London")
        val springMorning = LocalDateTime.of(2026, 3, 29, 4, 30).atZone(london)
        MetricsV2.setForTest(emptySet())
        assertEquals(LocalDate.of(2026, 3, 28), logicalDay(springMorning))
        MetricsV2.setForTest(setOf(V2Change.LOGICAL_DAY))
        assertEquals(LocalDate.of(2026, 3, 29), logicalDay(springMorning))
        // An ordinary night is the same either way.
        assertEquals(LocalDate.of(2026, 6, 9), logicalDay(LocalDateTime.of(2026, 6, 10, 1, 0).atZone(london)))
    }

    // MARK: - V2-7 the age source

    @Test
    fun aFreshInstallHasNoAgeUntilOneIsEntered() {
        val prefs = FakeSharedPreferences()
        val profile = ProfileStore(prefs)
        profile.migrateAgeSource()
        MetricsV2.setForTest(emptySet())
        assertEquals(30, profile.age)
        assertFalse(profile.ageEntered)
        assertEquals("off: v10's 30", 30, profile.knownAge)
        assertTrue("a v10 backup still carries the age", "profile.age" in profile.backupSnapshot())
        MetricsV2.setForTest(setOf(V2Change.NO_ASSUMED_AGE))
        assertNull(profile.knownAge)
        assertFalse("an age nobody entered is not exported", "profile.age" in profile.backupSnapshot())
        profile.setAge(41)
        assertTrue(profile.ageEntered)
        assertEquals(41, profile.knownAge)
    }

    @Test
    fun anExistingInstallKeepsItsAge() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putInt("age", 44).apply()
        val profile = ProfileStore(prefs)
        profile.migrateAgeSource()
        MetricsV2.setForTest(setOf(V2Change.NO_ASSUMED_AGE))
        assertTrue(profile.ageEntered)
        assertEquals(44, profile.knownAge)
    }

    @Test
    fun aDateOfBirthOrARestoredAgeCountsAsEntered() {
        MetricsV2.setForTest(setOf(V2Change.NO_ASSUMED_AGE))
        val a = ProfileStore(FakeSharedPreferences()).apply { migrateAgeSource() }
        a.setDateOfBirth(LocalDate.now().minusYears(35))
        assertEquals(35, a.knownAge)
        val b = ProfileStore(FakeSharedPreferences()).apply { migrateAgeSource() }
        b.applyBackup(mapOf("profile.age" to 52))
        assertEquals(52, b.knownAge)
        // Migration runs once: a later launch keeps "none" even after the default age was persisted.
        val prefs = FakeSharedPreferences()
        val c = ProfileStore(prefs).apply { migrateAgeSource() }
        c.age
        ProfileStore(prefs).migrateAgeSource()
        assertNull(ProfileStore(prefs).knownAge)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = HashMap<String, Any?>()
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            map[key] as? MutableSet<String> ?: defValues
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = FakeEditor()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removed = HashSet<String>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { pending[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { pending[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { removed.add(key); return this }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun commit(): Boolean { flush(); return true }
            override fun apply() { flush() }
            private fun flush() {
                for (k in removed) map.remove(k)
                map.putAll(pending)
                pending.clear(); removed.clear()
            }
        }
    }
}
