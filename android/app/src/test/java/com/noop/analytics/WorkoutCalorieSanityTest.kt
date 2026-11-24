package com.noop.analytics

import com.noop.data.HrSample
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

/**
 * Calorie sanity: the workout estimator on a typical run, 37 minutes at about 143 bpm average for an 80 kg, 30 year old.
 * About 400 to 500 kcal is expected; a figure far above it is known NOT to come from this code, but from stored or
 * imported energy (an earlier build, a WHOOP or Health Connect workout, or a merged row).
 *
 * Synthetic heart rate, deterministic. No real person's data.
 */
class WorkoutCalorieSanityTest {

    private fun run37min(): List<HrSample> = (0 until 37 * 60).map { i ->
        val m = i / 60.0
        val bpm = when {
            m < 4 -> 90 + (m / 4) * 50
            m < 32 -> 148 + 8 * sin(m * 1.3) + (if (m in 14.0..17.0) 14 else 0)
            else -> 135 + 6 * sin(m * 2.1)
        }
        HrSample("my-whoop", 1_700_000_000L + i, bpm.toInt())
    }

    private fun kcal(sex: String, resting: Double?, hrMax: Double?): Double =
        Calories.estimateBoutCalories(
            run37min(), UserProfile(weightKg = 80.0, heightCm = 175.0, age = 30.0, sex = sex), hrMax, resting,
        ).first

    @Test
    fun theEstimatorStaysNearTheExpectedRangeAcrossProfiles() {
        for (sex in listOf("male", "nonbinary")) for (resting in listOf<Double?>(null, 50.0, 45.0)) for (hrMax in listOf<Double?>(null, 188.0, 195.0)) {
            val k = kcal(sex, resting, hrMax)
            assertTrue("$sex rest=$resting max=$hrMax gave $k", k in 380.0..620.0)
        }
    }

    @Test
    fun noProfileOrHeartRateChoiceReachesSevenHundred() {
        for (sex in listOf("male", "female", "nonbinary")) for (resting in listOf<Double?>(null, 45.0, 55.0)) {
            assertTrue("$sex rest=$resting gave ${kcal(sex, resting, 188.0)}", kcal(sex, resting, 188.0) < 700.0)
        }
    }
}

/**
 * A live-recorded workout reads high when it holds several samples for one second. The Ridge live workout used to add a
 * sample for EVERY strap state update (battery, R-R, sync progress, not only a new heart rate), stamped to the whole
 * second, and the estimator credited each duplicate a full second, so the energy scaled with the update rate (a 37 minute run
 * scored about 1.4x). Fixed in two places: the recording keeps one sample a second ([LiveWorkoutSamples]) and the estimator
 * collapses repeated timestamps.
 */
class WorkoutCalorieDuplicateSampleTest {

    private fun stream(duplicateEvery: Int): List<HrSample> = buildList {
        for (i in 0 until 37 * 60) {
            val bpm = (145 + 8 * sin(i / 90.0)).toInt()
            add(HrSample("my-whoop", 1_700_000_000L + i, bpm))
            if (duplicateEvery > 0 && i % duplicateEvery == 0) add(HrSample("my-whoop", 1_700_000_000L + i, bpm))
        }
    }

    private fun kcal(samples: List<HrSample>) =
        Calories.estimateBoutCalories(samples, UserProfile(weightKg = 80.0, heightCm = 175.0, age = 30.0, sex = "male"), 188.0, 50.0).first

    @Test
    fun repeatedTimestampsDoNotInflateTheEstimate() {
        val clean = kcal(stream(duplicateEvery = 0))
        assertEquals("two samples every second", clean, kcal(stream(duplicateEvery = 1)), 1e-6)
        assertEquals("one extra every other second", clean, kcal(stream(duplicateEvery = 2)), 1e-6)
    }

    @Test
    fun theRecordingKeepsOneSamplePerSecond() {
        var samples = emptyList<HrSample>()
        // Five updates inside one second, then the next second: two samples, the latest of each.
        for (bpm in listOf(140, 141, 142, 143, 144)) samples = LiveWorkoutSamples.append(samples, HrSample("my-whoop", 100L, bpm))
        samples = LiveWorkoutSamples.append(samples, HrSample("my-whoop", 101L, 150))
        assertEquals(listOf(100L to 144, 101L to 150), samples.map { it.ts to it.bpm })
    }

    @Test
    fun aCleanStreamIsUnchangedByTheCollapse() {
        val one = kcal(stream(duplicateEvery = 0))
        assertTrue("a normal 37 minute run should stay in range, got $one", one in 400.0..650.0)
    }
}
