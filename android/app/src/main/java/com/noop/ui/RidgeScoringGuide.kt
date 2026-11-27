package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.noop.ui.ridge.RidgeEyebrow
import kotlin.math.roundToInt

// BS-x2-4 item 22, redrawn in RS-3g to ScoringGuide.dc.html. A row of pills (Core Metrics holds Readiness, Charge, Effort and
// Rest; the owner kept that group), and for the chosen score: the orb with today's value, an intro, what it measures, how it's
// captured, what it means with its bands, what goes in and the confidence note. The words are in RidgeScoringGuideText.kt,
// the cards in RidgeScoringGuideCards.kt.
//
// Where the board and the app disagree the APP wins: the bands come from the code's own thresholds (see ScoringGuideBands),
// and the board's personal example numbers are not copied.

/** The pills along the top. [CORE] holds the three daily scores. */
internal enum class GuideTab(val label: String) {
    CORE("Core Metrics"), SLEEP_SCORE("Sleep score"), STRESS("Stress"), DEBT("Sleep debt"), VO2("VO₂ max"), BODY_AGE("Body age"), FIT_AGE("Fitness age"), BASELINES("Baselines"),
}

/** One band of a score: its word, its range, what it means for the day and the colour of its swatch. */
internal data class GuideBand(val name: String, val range: String, val note: String, val color: Color)

/** The scores, in the board's order. */
internal enum class GuideScore { READINESS, CHARGE, EFFORT, REST, SLEEP_SCORE, STRESS, DEBT, VO2, BODY_AGE, FIT_AGE, BASELINES }

/** The tab a score lives under. */
internal fun GuideScore.tab(): GuideTab = when (this) {
    GuideScore.READINESS, GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST -> GuideTab.CORE
    GuideScore.SLEEP_SCORE -> GuideTab.SLEEP_SCORE
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

    /** RS-3b: Readiness's categories (Synthesis's five), judged on the rounded score. */
    fun readiness() = charge()

    /** RS-3b: with Readiness on, Charge is a number with no categories. */
    fun chargeShown(): List<GuideBand> = if (readinessOn()) emptyList() else charge()

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

private fun readinessOn() = com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.READINESS_SCORE)

/** The scores under Core Metrics, in order. Readiness leads while it is on. */
internal fun coreScores(readiness: Boolean = readinessOn()): List<GuideScore> =
    listOfNotNull(GuideScore.READINESS.takeIf { readiness }, GuideScore.CHARGE, GuideScore.EFFORT, GuideScore.REST)

/** The top row of tabs. RS-3e: the Sleep score has its own, shown while it is on. */
internal fun guideTabs(sleepScore: Boolean = sleepScoreOn()): List<GuideTab> =
    GuideTab.entries.filter { it != GuideTab.SLEEP_SCORE || sleepScore }

private fun sleepScoreOn() = com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.SLEEP_SCORE)

/** A one-shot "open the guide on this page" request, for a "How this works" link; read and cleared when the guide opens. */
internal object ScoringGuideLink {
    var pending: GuideScore? = null

    fun take(): GuideScore? = pending.also { pending = null }
}

/** Today's values for the orb and the Baselines ranges; null where the app has no value yet. */
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
    GuideScore.READINESS -> null
    GuideScore.CHARGE -> MetricId.CHARGE
    GuideScore.EFFORT -> MetricId.EFFORT
    GuideScore.REST -> MetricId.REST
    GuideScore.SLEEP_SCORE -> MetricId.SLEEP_SCORE
    GuideScore.STRESS -> MetricId.STRESS
    GuideScore.DEBT -> MetricId.SLEEP_DEBT
    GuideScore.VO2 -> MetricId.VO2_MAX
    GuideScore.BODY_AGE -> MetricId.BODY_AGE
    GuideScore.FIT_AGE -> MetricId.FITNESS_AGE
    GuideScore.BASELINES -> null
}

/** The metrics whose today-values the orbs read. */
internal val GUIDE_METRICS: List<MetricId> = listOf(
    MetricId.CHARGE, MetricId.EFFORT, MetricId.REST, MetricId.STRESS, MetricId.SLEEP_DEBT, MetricId.VO2_MAX,
    MetricId.BODY_AGE, MetricId.FITNESS_AGE, MetricId.SLEEP_SCORE,
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

/**
 * The Ridge page's content. [initial] opens the tab a Today (i) deep link points at; [vm] supplies today's values
 * (without it the orb shows a dash and the ranges read Calibrating).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RidgeScoringGuideContent(initial: ScoreSection?, vm: AppViewModel?) {
    // RS-3b: with no deep link the page opens on the first core score (Readiness while it is on).
    val start = remember { initial?.guideScore() ?: ScoringGuideLink.take() ?: coreScores().first() }
    var tab by rememberSaveable { mutableStateOf(start.tab().name) }
    // The core row's own choice; a link to a page outside Core Metrics leaves it on the first core score.
    var core by rememberSaveable { mutableStateOf((start.takeIf { it.tab() == GuideTab.CORE } ?: coreScores().first()).name) }
    val today = rememberGuideToday(vm)
    val selectedTab = GuideTab.valueOf(tab)
    val score = when (selectedTab) {
        GuideTab.CORE -> GuideScore.valueOf(core)
        GuideTab.SLEEP_SCORE -> GuideScore.SLEEP_SCORE
        GuideTab.STRESS -> GuideScore.STRESS
        GuideTab.DEBT -> GuideScore.DEBT
        GuideTab.VO2 -> GuideScore.VO2
        GuideTab.BODY_AGE -> GuideScore.BODY_AGE
        GuideTab.FIT_AGE -> GuideScore.FIT_AGE
        GuideTab.BASELINES -> GuideScore.BASELINES
    }
    val c = guideContent(score)

    val tabs = guideTabs()
    Pills(tabs.map { it.label to (it == selectedTab) }) { i -> tab = tabs[i].name }
    if (selectedTab == GuideTab.CORE) {
        val cores = coreScores()
        Pills(cores.map { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } to (it == score) }, small = true) { i -> core = cores[i].name }
    }

    val value = metricOf(score)?.let { today?.of(it) }
    val glow = glowOf(score)
    Row(
        // A soft light in the score's colour behind its name.
        Modifier.fillMaxWidth().drawBehind {
            val reach = size.width * 0.75f
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                Brush.radialGradient(0f to glow.copy(alpha = 0.16f), 1f to glow.copy(alpha = 0f), center = centre, radius = reach),
                radius = reach, center = centre,
            )
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Readiness has no orb: Today shows its word alone. The baselines orb holds no number.
        if (score != GuideScore.READINESS) {
            LiquidOrb(
                fraction = if (score == GuideScore.BASELINES) 0.5 else fractionOf(score, value),
                display = if (score == GuideScore.BASELINES) "" else displayOf(score, value),
                // The night blue takes the glass orb's white numeral; the dark one would be lost on it.
                liquid = c.liquid, diameter = 108.dp, glass = c.liquid == RidgeLiquid.NIGHT,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, style = RidgeType.titleXl.copy(fontSize = 32.sp, lineHeight = 36.sp), color = Ridge.ink, modifier = Modifier.semantics { heading() })
            Text(c.scale, style = RidgeType.bodySm.copy(fontWeight = FontWeight.Medium), color = Ridge.inkMuted)
        }
    }
    Text(c.intro, style = RidgeType.body.copy(fontSize = 16.sp, lineHeight = 25.sp), color = Ridge.ink)

    GuideTextCard(GuideIcons.MEASURES, "What it measures", c.measures)
    GuideTextCard(GuideIcons.CAPTURED, "How it’s captured", c.captured)
    val rows: (@Composable () -> Unit)? = when {
        score == GuideScore.BASELINES -> { -> BaselineRows(today) }
        c.bands.isNotEmpty() -> { -> c.bands.forEachIndexed { i, b -> GuideRow(i == 0, b.color, b.name, b.note, b.range) } }
        else -> null
    }
    GuideMeansCard(c.means, c.note, if (score == GuideScore.BASELINES) "Your normal" else "Bands", rows)

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

    GuideTextCard(GuideIcons.CONFIDENCE, "Confidence", GUIDE_CONFIDENCE, textColor = Ridge.inkMuted)
}

private fun glowOf(score: GuideScore): Color = when (score) {
    GuideScore.READINESS, GuideScore.CHARGE -> Ridge.charge
    GuideScore.EFFORT -> Ridge.effort
    GuideScore.SLEEP_SCORE -> RidgeNightSky.orbGlow
    GuideScore.STRESS -> Ridge.stateModerate
    GuideScore.BASELINES -> Ridge.dataHrv
    GuideScore.REST, GuideScore.DEBT, GuideScore.VO2, GuideScore.BODY_AGE, GuideScore.FIT_AGE -> Ridge.brand
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
private fun BaselineRows(today: GuideToday?) {
    val rows = listOf(
        Triple("HRV", "Overnight rMSSD; higher is usually better", MetricId.HRV),
        Triple("Resting heart rate", "Lowest steady overnight rate", MetricId.RESTING_HR),
        Triple("Breathing rate", "Very stable; a jump is worth noticing", MetricId.RESP_RATE),
        Triple("Skin temperature", "Relative to your own average", MetricId.SKIN_TEMP_DEV),
    )
    val colors = listOf(Ridge.dataHrv, Ridge.dataHeart, Ridge.dataBreath, Ridge.dataEnergy)
    val units = listOf(" ms", " bpm", " rpm", " °C")
    rows.forEachIndexed { i, (name, note, id) ->
        val band = today?.normal?.get(id)
        val shown = band?.let { "${fmtBand(it.start)}–${fmtBand(it.endInclusive)}${units[i]}" } ?: "Calibrating"
        GuideRow(i == 0, colors[i], name, note, shown)
    }
}

private fun fmtBand(v: Double): String = if (kotlin.math.abs(v) >= 10.0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
