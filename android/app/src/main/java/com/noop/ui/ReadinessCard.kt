package com.noop.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.noop.R

// RS-3b: the Today card's Readiness: the saved "now" it eases from, and the words for each state.

/** The last "readiness now" shown, so it survives the app closing. Only Today shows "now", so only Today reads it. */
internal object ReadinessPrefs {
    private const val FILE = "noop_readiness"

    fun load(context: Context): ReadinessShown? {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val day = p.getString("day", null) ?: return null
        return ReadinessShown(
            day, p.getFloat("morning", 0f).toDouble(), p.getFloat("value", 0f).toDouble(), p.getInt("band", 0), p.getLong("atMs", 0L),
        )
    }

    fun save(context: Context, s: ReadinessShown) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("day", s.day).putFloat("morning", s.morning.toFloat()).putFloat("value", s.value.toFloat())
            .putInt("band", s.band).putLong("atMs", s.atMs).apply()
    }
}

/**
 * What the card draws. [band] null (no score) leaves it neutral with no scale. [sliderScore] is where the knob sits:
 * inside the held category, even while the score is within the smoothing's margin. RS-3f: no figure is written.
 */
internal data class ReadinessCardUi(
    val word: String,
    val detail: String,
    val band: Int? = null,
    val sliderScore: Double? = null,
)

/** The knob's place: the value, held inside [band]'s own stretch of the scale. */
internal fun sliderScoreIn(band: Int, value: Double): Double {
    val lo = SYNTHESIS_BAND_FLOORS[band]
    val hi = SYNTHESIS_BAND_FLOORS.getOrNull(band + 1)?.let { it - 0.01 } ?: 100.0
    return value.coerceIn(lo, hi)
}

/** The smoothed "now" for [model], re-read every minute while the card is on screen. Null unless the model is live. */
@Composable
internal fun rememberReadinessShown(model: ReadinessCardModel?, day: String): ReadinessShown? {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val live = model as? ReadinessCardModel.Live
    LaunchedEffect(live != null) {
        while (live != null) {
            kotlinx.coroutines.delay(60_000L)
            tick = System.currentTimeMillis()
        }
    }
    return remember(live, day, tick) {
        live?.let {
            val now = System.currentTimeMillis()
            // Until any Effort is known the reading is the morning score; it is not saved, so the first real Effort of the
            // day is not eased from a figure that never knew about it.
            if (it.effort == null) ReadinessShown(day, it.morning, it.reading, ReadinessScore.band(it.reading), now)
            else ReadinessSmoothing.step(ReadinessPrefs.load(context), day, it.morning, it.reading, now)
                .also { s -> ReadinessPrefs.save(context, s) }
        }
    }
}

private val READINESS_WORDS = listOf(
    R.string.today_synthesis_depleted, R.string.today_synthesis_low, R.string.today_synthesis_steady,
    R.string.today_synthesis_strong, R.string.today_synthesis_peak,
)

private val READINESS_TODAY = listOf(
    R.string.readiness_say_today_depleted, R.string.readiness_say_today_low, R.string.readiness_say_today_steady,
    R.string.readiness_say_today_strong, R.string.readiness_say_today_peak,
)

private val READINESS_PAST = listOf(
    R.string.readiness_say_past_depleted, R.string.readiness_say_past_low, R.string.readiness_say_past_steady,
    R.string.readiness_say_past_strong, R.string.readiness_say_past_peak,
)

private fun reasonText(reason: ReadinessReason) = when (reason) {
    ReadinessReason.LOW_CHARGE -> R.string.readiness_why_low_charge
    ReadinessReason.LOW_REST -> R.string.readiness_why_low_rest
    ReadinessReason.LOW_BOTH -> R.string.readiness_why_low_both
    ReadinessReason.MODERATE_CHARGE -> R.string.readiness_why_moderate_charge
    ReadinessReason.LOWER_REST -> R.string.readiness_why_lower_rest
    ReadinessReason.HIGH_CHARGE -> R.string.readiness_why_high_charge
    ReadinessReason.HIGH_BOTH -> R.string.readiness_why_high_both
    ReadinessReason.GOOD_REST -> R.string.readiness_why_good_rest
    ReadinessReason.SOLID_BOTH -> R.string.readiness_why_solid_both
    ReadinessReason.EFFORT -> R.string.readiness_why_effort
}

/** RS-3f: one plain sentence, "Today is a recovery day, driven by low Charge." [past] words a day already over. */
@Composable
private fun readinessSentence(charge: Double, rest: Double, morning: Double, band: Int, past: Boolean): String = uiString(
    R.string.readiness_say,
    uiString((if (past) READINESS_PAST else READINESS_TODAY)[band]),
    uiString(reasonText(readinessReason(charge, rest, morning, band))),
)

/** [model] in words. */
@Composable
internal fun readinessCardUi(model: ReadinessCardModel, shown: ReadinessShown?): ReadinessCardUi = when (model) {
    is ReadinessCardModel.Calibrating -> ReadinessCardUi(
        uiString(R.string.score_state_title_calibrating),
        uiString(R.string.readiness_detail_calibrating, model.nights, com.noop.analytics.Baselines.minNightsSeed),
    )
    ReadinessCardModel.NoCharge -> ReadinessCardUi(uiString(R.string.today_no_data), uiString(R.string.readiness_detail_no_charge))
    ReadinessCardModel.NoRest -> ReadinessCardUi(uiString(R.string.today_no_data), uiString(R.string.readiness_detail_no_rest))
    is ReadinessCardModel.Morning -> {
        val band = ReadinessScore.band(model.morning)
        val carried = model.carriedFrom
        // With nothing carried, a morning-only card is a past day.
        val say = readinessSentence(model.charge, model.rest, model.morning, band, past = carried == null)
        ReadinessCardUi(
            uiString(READINESS_WORDS[band]),
            carried?.let { uiString(R.string.today_synthesis_carried_detail, say, carriedCaption(it).localized()) } ?: say,
            band, sliderScoreIn(band, model.morning),
        )
    }
    is ReadinessCardModel.Live -> {
        val value = shown?.value ?: model.reading
        val band = shown?.band ?: ReadinessScore.band(value)
        ReadinessCardUi(
            uiString(READINESS_WORDS[band]),
            readinessSentence(model.charge, model.rest, model.morning, band, past = false),
            band, sliderScoreIn(band, value),
        )
    }
}
