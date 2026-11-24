package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.V10MetricRegistry
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import kotlin.math.roundToInt

// BS-x2-4 item 22: "How your scores work" rebuilt to ScoringGuide.dc.html. A row of pills (Core Metrics holds Charge, Effort and
// Rest; then Stress, Sleep debt, VO2 max, Body age, Fitness age and Baselines), and for the chosen score: the orb with today's
// value, what it is, its bands, what goes in, the confidence note and a one-line dictionary with today's values.
//
// Where the board and the app disagree the APP wins: the bands below come from the code's own thresholds (see
// ScoringGuideBands), and the board's personal example numbers (a body-age breakdown, "you are here: 32") are not copied.
// Differences are logged in docs/ridge-build/BS-x2/BS-x2-4/DIFFERENCES.md.

/** The pills along the top. [CORE] holds the three daily scores. */
internal enum class GuideTab(val label: String) {
    CORE("Core Metrics"), STRESS("Stress"), DEBT("Sleep debt"), VO2("VO₂ max"), BODY_AGE("Body age"), FIT_AGE("Fitness age"), BASELINES("Baselines"),
}

/** One band of a score: its word, its range, what it means for the day and the colour of its swatch. */
internal data class GuideBand(val name: String, val range: String, val note: String, val color: Color)

/** The scores, in the board's order. */
internal enum class GuideScore { CHARGE, EFFORT, REST, STRESS, DEBT, VO2, BODY_AGE, FIT_AGE, BASELINES }

/** The tab a score lives under. */
internal fun GuideScore.tab(): GuideTab = when (this) {
    GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST -> GuideTab.CORE
    GuideScore.STRESS -> GuideTab.STRESS
    GuideScore.DEBT -> GuideTab.DEBT
    GuideScore.VO2 -> GuideTab.VO2
    GuideScore.BODY_AGE -> GuideTab.BODY_AGE
    GuideScore.FIT_AGE -> GuideTab.FIT_AGE
    GuideScore.BASELINES -> GuideTab.BASELINES
}

/** The score a deep link from Today's (i) lands on. */
internal fun ScoreSection.guideScore(): GuideScore = when (this) {
    ScoreSection.CHARGE -> GuideScore.CHARGE
    ScoreSection.EFFORT -> GuideScore.EFFORT
    ScoreSection.REST -> GuideScore.REST
}

/**
 * The bands the app itself uses, so the page never states a threshold the code does not apply. Charge: [synthesisWord];
 * Effort: [effortBand]; Rest: [sleepScoreWordRes]; Stress: [StressBand.forScore].
 */
internal object ScoringGuideBands {
    fun charge() = listOf(
        GuideBand("Depleted", "0–24", "Prioritise rest and easy movement", Ridge.stateDepleted),
        GuideBand("Low", "25–49", "Light activity only", Ridge.stateLow),
        GuideBand("Steady", "50–69", "Train, but keep it moderate", Ridge.stateModerate),
        GuideBand("Strong", "70–87", "Ready for a hard session", Ridge.brand),
        GuideBand("Peak", "88–100", "A day to go for it", Ridge.effort),
    )

    fun effort() = listOf(
        GuideBand("Rest / light", "0–47", "Active recovery, daily movement", Ridge.dataBreath),
        GuideBand("Moderate", "48–66", "Steady training stimulus", Ridge.effort),
        GuideBand("High", "67–85", "Hard session, builds fitness", Ridge.stateModerate),
        GuideBand("All out", "86–100", "Needs a strong Charge to absorb", Ridge.stateDepleted),
    )

    fun rest() = listOf(
        GuideBand("Poor", "0–49", "Large shortfall, expect low Charge", Ridge.stateLow),
        GuideBand("Fair", "50–69", "Short of what you needed", Ridge.stateModerate),
        GuideBand("Good", "70–84", "Close to your need", Ridge.stateGood),
        GuideBand("Optimal", "85–100", "Fully met your need", Ridge.brand),
    )

    fun stress() = listOf(
        GuideBand("Settled", "0–0.9", "Close to your usual resting state", Ridge.brand),
        GuideBand("Raised", "1.0–1.9", "Busier than usual; a short breathing session helps", Ridge.stateModerate),
        GuideBand("High", "2.0–3.0", "Sustained load; worth easing off if it lasts", Ridge.stateLow),
    )
}

/** Everything the page shows for one score. [bands] is empty where the app has no threshold of its own to quote. */
internal class GuideContent(
    val score: GuideScore,
    val label: String,
    val title: String,
    val scale: String,
    val what: String,
    val bands: List<GuideBand>,
    val inputs: List<String>,
    val liquid: RidgeLiquid,
)

/** Today's values for the orb and the dictionary; null where the app has no value yet. */
internal class GuideToday(val values: Map<MetricId, Double?>, val normal: Map<MetricId, ClosedFloatingPointRange<Double>?> = emptyMap()) {
    fun of(id: MetricId): Double? = values[id]
}

private fun fractionOf(score: GuideScore, v: Double?): Double = when {
    v == null -> 0.0
    score == GuideScore.STRESS -> v / 3.0
    score == GuideScore.DEBT -> v / 180.0
    score == GuideScore.VO2 -> v / 70.0
    score == GuideScore.BODY_AGE || score == GuideScore.FIT_AGE -> v / 100.0
    else -> v / 100.0
}.coerceIn(0.0, 1.0)

private fun displayOf(score: GuideScore, v: Double?): String = when {
    v == null -> "–"
    score == GuideScore.STRESS -> String.format(java.util.Locale.US, "%.1f", v)
    score == GuideScore.DEBT -> durationText(v)
    score == GuideScore.VO2 -> String.format(java.util.Locale.US, "%.1f", v)
    score == GuideScore.REST -> "${v.roundToInt()}%"
    else -> "${v.roundToInt()}"
}

private fun metricOf(score: GuideScore): MetricId? = when (score) {
    GuideScore.CHARGE -> MetricId.CHARGE
    GuideScore.EFFORT -> MetricId.EFFORT
    GuideScore.REST -> MetricId.REST
    GuideScore.STRESS -> MetricId.STRESS
    GuideScore.DEBT -> MetricId.SLEEP_DEBT
    GuideScore.VO2 -> MetricId.VO2_MAX
    GuideScore.BODY_AGE -> MetricId.BODY_AGE
    GuideScore.FIT_AGE -> MetricId.FITNESS_AGE
    GuideScore.BASELINES -> null
}

/** The metrics whose today-values the page reads (the orb and the dictionary). */
internal val GUIDE_METRICS: List<MetricId> = listOf(
    MetricId.CHARGE, MetricId.EFFORT, MetricId.REST, MetricId.STRESS, MetricId.SLEEP_DEBT, MetricId.VO2_MAX,
    MetricId.BODY_AGE, MetricId.FITNESS_AGE, MetricId.HRV, MetricId.RESTING_HR,
)

/** The overnight signals the Baselines tab shows a normal band for. */
internal val BASELINE_METRICS: List<MetricId> = listOf(MetricId.HRV, MetricId.RESTING_HR, MetricId.RESP_RATE, MetricId.SKIN_TEMP_DEV)

/** Reads today's value for each guide metric from the registry, on a background thread. */
@Composable
private fun rememberGuideToday(vm: AppViewModel?): GuideToday? {
    var today by remember { mutableStateOf<GuideToday?>(null) }
    LaunchedEffect(vm) {
        if (vm == null) return@LaunchedEffect
        today = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val inputs = registryInputsFor(vm)
                val registry = V10MetricRegistry(inputs)
                val day = DayKey(inputs.today)
                GuideToday(
                    GUIDE_METRICS.associateWith { id -> runCatching { registry.value(id, day).value }.getOrNull() },
                    BASELINE_METRICS.associateWith { id -> runCatching { registry.normalRange(id, day)?.let { it.low..it.high } }.getOrNull() },
                )
            }.getOrNull()
        }
    }
    return today
}

private fun contentFor(score: GuideScore, coreBody: (GuideScore) -> String): GuideContent = when (score) {
    GuideScore.CHARGE -> GuideContent(
        score, "Charge", "Charge", "0–100 · how ready your body is", coreBody(score), ScoringGuideBands.charge(),
        listOf("HRV", "Resting HR", "Sleep performance", "Respiratory rate", "Skin temperature"), RidgeLiquid.CHARGE,
    )
    GuideScore.EFFORT -> GuideContent(
        score, "Effort", "Effort", "0–100 · cardiovascular load today", coreBody(score), ScoringGuideBands.effort(),
        listOf("Heart rate", "HR zones", "Duration", "Max HR"), RidgeLiquid.EFFORT,
    )
    GuideScore.REST -> GuideContent(
        score, "Rest", "Rest", "0–100 · sleep you got vs needed", coreBody(score), ScoringGuideBands.rest(),
        listOf("Sleep duration", "Sleep need", "Sleep debt", "Consistency", "Disturbances"), RidgeLiquid.REST,
    )
    GuideScore.STRESS -> GuideContent(
        score, "Stress", "Stress", "0–3 · daytime load on your nervous system",
        "Stress reads your heart rate and HRV while you are still during the day, against your own 30-day baseline. Workouts are left out, so exercise never counts as stress. It reflects physical and mental load together and is not a diagnosis.",
        ScoringGuideBands.stress(), listOf("Heart rate at rest", "HRV", "Motion (to exclude workouts)", "30-day baseline"), RidgeLiquid.CHARGE,
    )
    GuideScore.DEBT -> GuideContent(
        score, "Sleep debt", "Sleep debt", "minutes · rolling 14 tracked nights",
        "Each tracked night adds what you needed minus what you slept. Extra sleep pays it back. It never goes below zero, and nights without data are skipped, not counted as zero.",
        emptyList(), listOf("Sleep need", "Time asleep", "14 tracked nights"), RidgeLiquid.REST,
    )
    GuideScore.VO2 -> GuideContent(
        score, "VO₂ max", "VO₂ max", "ml/kg/min · wrist estimate, ±3–5",
        "VO₂ max is how much oxygen your body can use at full effort. NOOP estimates it weekly from how your heart rate responds to pace on outdoor runs and walks with GPS, plus resting heart rate, age and sex. Small week-to-week changes are within the margin of error; the monthly average is the number to watch.",
        emptyList(), listOf("Heart rate vs pace", "Phone GPS", "Resting HR", "Age and sex", "Max HR"), RidgeLiquid.REST,
    )
    GuideScore.BODY_AGE -> GuideContent(
        score, "Body age", "Body age", "years · shown as a range",
        "Body age compares 28 measures with age norms and turns the gaps into years. A handful of drivers carry most of the weight, and each can move it by up to 5 years either way. It updates weekly and needs at least 14 of the 28 inputs; below that you see a first-week state instead of a number.",
        emptyList(), listOf("VO₂ max", "Resting HR", "HRV", "Sleep regularity", "Weekly activity", "and more"), RidgeLiquid.REST,
    )
    GuideScore.FIT_AGE -> GuideContent(
        score, "Fitness age", "Fitness age", "years · from VO₂ max alone",
        "Fitness age is the age at which your VO₂ max would be typical. It uses one input, so it moves with your cardio fitness and can differ from body age, which blends 14 or more.",
        emptyList(), listOf("VO₂ max", "Age", "Sex"), RidgeLiquid.REST,
    )
    GuideScore.BASELINES -> GuideContent(
        score, "Baselines", "Your normal", "HRV, resting HR, breathing · last 60 nights",
        "Your normal range for each overnight signal comes from your own last 60 nights, not population averages. It is the shaded band on every chart. A single night outside it is common; several in a row is what moves Charge.",
        emptyList(), listOf("60 nights of data", "Tracked nights only", "Calibrating under 14 nights"), RidgeLiquid.CHARGE,
    )
}

/** The dictionary's rows: name, definition, and the score it reads today's value from. */
private val DICTIONARY: List<Triple<String, String, GuideScore?>> = listOf(
    Triple("Charge", "0–100 · Depleted, Low, Steady, Strong, Peak · vs your own 30-day baseline", GuideScore.CHARGE),
    Triple("Effort", "0–100 · Rest / light, Moderate, High, All out · resets at midnight", GuideScore.EFFORT),
    Triple("Rest", "Sleep you got compared with sleep you needed · Poor, Fair, Good, Optimal", GuideScore.REST),
    Triple("Sleep debt", "Need minus sleep over 14 tracked nights, never below zero", GuideScore.DEBT),
    Triple("HRV", "Overnight rMSSD · your normal band comes from your last 60 nights", null),
    Triple("Resting HR", "Lowest steady overnight heart rate · your normal comes from your last 60 nights", null),
    Triple("Stress", "0–3 · Settled, Raised, High · daytime, workouts excluded, vs 30 days", GuideScore.STRESS),
    Triple("Body age", "Model estimate from at least 14 of 28 inputs · weekly", GuideScore.BODY_AGE),
    Triple("Fitness age", "From VO₂ max alone · weekly · differs from body age", GuideScore.FIT_AGE),
)

/**
 * The Ridge page's content. [initial] opens the tab a Today (i) deep link points at; [vm] supplies today's values
 * (without it the orb and the dictionary show dashes). The three daily scores keep the app's own approved description.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RidgeScoringGuideContent(initial: ScoreSection?, vm: AppViewModel?) {
    var tab by rememberSaveable { mutableStateOf((initial?.guideScore() ?: GuideScore.CHARGE).tab().name) }
    var core by rememberSaveable { mutableStateOf((initial?.guideScore() ?: GuideScore.CHARGE).name) }
    val today = rememberGuideToday(vm)
    val chargeBody = stringResource(R.string.scoring_guide_charge_body)
    val effortBody = stringResource(R.string.scoring_guide_effort_body)
    val restBody = stringResource(R.string.scoring_guide_rest_body)
    val selectedTab = GuideTab.valueOf(tab)
    val score = when (selectedTab) {
        GuideTab.CORE -> GuideScore.valueOf(core)
        GuideTab.STRESS -> GuideScore.STRESS
        GuideTab.DEBT -> GuideScore.DEBT
        GuideTab.VO2 -> GuideScore.VO2
        GuideTab.BODY_AGE -> GuideScore.BODY_AGE
        GuideTab.FIT_AGE -> GuideScore.FIT_AGE
        GuideTab.BASELINES -> GuideScore.BASELINES
    }
    val c = contentFor(score) { s -> when (s) { GuideScore.CHARGE -> chargeBody; GuideScore.EFFORT -> effortBody; else -> restBody } }

    Pills(GuideTab.entries.map { it.label to (it == selectedTab) }) { i -> tab = GuideTab.entries[i].name }
    if (selectedTab == GuideTab.CORE) {
        val cores = listOf(GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST)
        Pills(cores.map { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } to (it == score) }, small = true) { i -> core = cores[i].name }
    }

    val value = metricOf(score)?.let { today?.of(it) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (score != GuideScore.BASELINES) LiquidOrb(fractionOf(score, value), displayOf(score, value), c.liquid, 108.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, style = RidgeType.titleXl.copy(fontSize = 32.sp, lineHeight = 36.sp), color = Ridge.ink, modifier = Modifier.semantics { heading() })
            Text(c.scale, style = RidgeType.bodySm.copy(fontWeight = FontWeight.Medium), color = Ridge.inkMuted)
        }
    }
    Text(c.what, style = RidgeType.body.copy(fontSize = 16.sp, lineHeight = 25.sp), color = Ridge.ink)

    if (score == GuideScore.BASELINES) {
        BaselinesCard(today)
    } else if (c.bands.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            Column {
                c.bands.forEachIndexed { i, b ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(width = 12.dp, height = 32.dp).background(b.color, RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.name, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Ridge.ink)
                            Text(b.note, style = RidgeType.bodySm.copy(fontSize = 13.sp), color = Ridge.inkMuted)
                        }
                        Text(b.range, style = RidgeType.titleSm.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Ridge.ink, maxLines = 1)
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RidgeEyebrow("What goes in", Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.inputs.forEach { i ->
                Text(
                    i, style = RidgeType.bodySm.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = Ridge.ink,
                    modifier = Modifier
                        .background(Ridge.glassFill, RoundedCornerShape(Ridge.radiusPill))
                        .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusPill))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RidgeEyebrow("Confidence")
            Text(
                "A Calibrating tag means NOOP is still learning your baseline (fewer than 14 nights of full data) or the estimate is not final yet, so it can swing more. No tag means the number is settled.",
                style = RidgeType.bodySm.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted,
            )
        }
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)) {
        Column {
            RidgeEyebrow("One number, one meaning")
            Text(
                "Every screen, Sage and export uses these definitions and today's values.",
                style = RidgeType.bodySm.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Ridge.inkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
            DICTIONARY.forEachIndexed { i, (name, def, s) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
                val id = s?.let { metricOf(it) } ?: when (name) { "HRV" -> MetricId.HRV; "Resting HR" -> MetricId.RESTING_HR; else -> null }
                val v = id?.let { today?.of(it) }
                val shown = when {
                    s != null -> displayOf(s, v)
                    v == null -> "–"
                    name == "HRV" -> "${v.roundToInt()} ms"
                    else -> "${v.roundToInt()} bpm"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = Ridge.ink)
                        Text(def, style = RidgeType.bodySm.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Ridge.inkMuted)
                    }
                    Text(shown, style = RidgeType.titleSm.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Ridge.ink, maxLines = 1)
                }
            }
        }
    }
}

/** The board's pill row: scrolls sideways, the chosen pill filled in the brand green. [small] is the second row under Core Metrics. */
@Composable
private fun Pills(items: List<Pair<String, Boolean>>, small: Boolean = false, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, (label, on) ->
            val shape = RoundedCornerShape(Ridge.radiusPill)
            Text(
                label,
                style = RidgeType.bodySm.copy(fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (on) Ridge.inkOnLiquid else Ridge.inkMuted,
                maxLines = 1,
                modifier = Modifier
                    .heightIn(min = if (small) 32.dp else 36.dp)
                    .background(if (on) Ridge.brand else Ridge.glassFill, shape)
                    .border(1.dp, if (on) Ridge.brand else Ridge.glassStroke, shape)
                    .clickable { onPick(i) }
                    .semantics { role = Role.Tab; selected = on }
                    .padding(horizontal = 14.dp, vertical = if (small) 7.dp else 9.dp),
            )
        }
    }
}

/** The Baselines tab's four rows: each overnight signal's own normal band from the last 60 nights, read live. */
@Composable
private fun BaselinesCard(today: GuideToday?) {
    val rows = listOf(
        Triple("HRV", "Overnight rMSSD; higher is usually better", MetricId.HRV),
        Triple("Resting heart rate", "Lowest steady overnight rate", MetricId.RESTING_HR),
        Triple("Breathing rate", "Very stable; a jump is worth noticing", MetricId.RESP_RATE),
        Triple("Skin temperature", "Relative to your own average", MetricId.SKIN_TEMP_DEV),
    )
    val colors = listOf(Ridge.dataHrv, Ridge.dataHeart, Ridge.dataBreath, Ridge.dataEnergy)
    val units = listOf(" ms", " bpm", " rpm", " °C")
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        Column {
            rows.forEachIndexed { i, (name, note, id) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
                val band = today?.normal?.get(id)
                val shown = band?.let { "${fmtBand(it.start)}–${fmtBand(it.endInclusive)}${units[i]}" } ?: "Calibrating"
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(width = 12.dp, height = 32.dp).background(colors[i], RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Ridge.ink)
                        Text(note, style = RidgeType.bodySm.copy(fontSize = 13.sp), color = Ridge.inkMuted)
                    }
                    Text(shown, style = RidgeType.titleSm.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Ridge.ink, maxLines = 1)
                }
            }
        }
    }
}

private fun fmtBand(v: Double): String = if (kotlin.math.abs(v) >= 10.0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
