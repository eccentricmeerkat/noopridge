package com.noop.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Shadow
import kotlin.math.roundToInt
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.widthIn
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.data.DailyMetric
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricsV2
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.V2Change
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.util.Locale

/**
 * SQ8a (A1): Today's vertical rhythm, measured from `design reference/Today_page.png`. One place for the gaps, so they
 * aren't scattered as literals. The orb card to Synthesis gap keeps the app's 114 dp (owner), not the PNG's 78.
 */
internal object TodayRhythm {
    /** The list's gap between Today's sections (`LazyScreenScaffold` rowSpacing). */
    val sectionGap = 12.dp
    /** The orb card's bottom to the Synthesis card's top: was 114 dp (owner, 15 Nov 2026); 25 dp less now the orbs sit 25 dp
     *  lower (B2-1a, 19 Nov), so Synthesis and everything under it stay where they were. */
    val orbsToSynthesis = 89.dp
    /** Synthesis card to Today's plan. */
    val synthesisToPlan = Ridge.space6
    /** Today's plan to the next section (Key Metrics by default), a breathing space where the backdrop shows. */
    val planToNext = 50.dp
    /** Today's plan card's height at rest (measured on the 411 dp emulator, 15 Nov 2026), held when the plan is hidden. */
    val planSlot = 107.dp
}

/**
 * The plain-English Synthesis card, the Charge-tinted [InsightCard] read-out under the ring hero, with a
 * WHITE headline (the key iOS Design-Reset change, `statusColor: textPrimary`, not the recovery/charge
 * colour), carrying the greeting + the SOLID / CALIBRATING data-confidence pill in its top-right. Mirrors
 * the iOS Synthesis InsightCard (which moved here when the big RecoveryRing hero that owned the pill went).
 */
@Composable
internal fun SynthesisHeroCard(
    day: DailyMetric?,
    recoveryCalibration: Int?,
    carriedDay: DailyMetric? = null,
    // S4: the day history (for the one-word readiness read), whether the Synthesis card is expanded, and the
    // taps to toggle it / open the Charge breakdown (where the full Readiness card lives). Defaults keep old
    // call sites compiling; the Today call site supplies them.
    days: List<DailyMetric> = emptyList(),
    synthesisExpanded: Boolean = false,
    onToggleSynthesis: () -> Unit = {},
    // C-03: Today's plan card. Shown only for today (it is a plan for today), with a scored or carried Charge.
    isToday: Boolean = false,
    effortScale: EffortScale = EffortScale.HUNDRED,
    onOpenCoach: (() -> Unit)? = null,
    // SQ5 (V2-11): today's nap asleep minutes, taken off tonight's sleep target.
    todayNapAsleepMin: Double = 0.0,
    /** RS-3b: when set, the card reads Readiness instead of Synthesis. The plan below still follows Charge. */
    readiness: ReadinessCardUi? = null,
    /** RS-3i: the alert cards are right above, so the card follows them at the list's own gap. */
    followsAlerts: Boolean = false,
) {
    // The row the synthesis reads from: today's own when it carries recovery, else the carried-over last
    // scored day (#543) so the card mirrors the carried Charge ring instead of blanking to "No Data". When
    // carrying, the detail line gets a "Last night · <date>" provenance so the prior read isn't passed off
    // as today's. today's own read wins the instant tonight is scored.
    val readDay = carriedDay ?: day
    val recovery = readDay?.recovery
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        // SQ8a (owner, 15 Nov 2026): the greeting moved into the header and the readiness pill (Push / Maintain / Rest)
        // and the Calibrating chip beside it are gone (Synthesis itself says Calibrating). Their space stays, so the
        // orb card and Synthesis keep their 114 dp gap.
        if (!followsAlerts) Spacer(Modifier.height(TodayRhythm.orbsToSynthesis - TodayRhythm.sectionGap - Metrics.gap))
        // S4: the Synthesis card collapses to a one-liner that expands on tap. The headline (the status) is
        // the SAME in both states, only the detail body and chrome fold, never the read (#506).
        val status = if (recoveryCalibration != null) uiString(R.string.score_state_title_calibrating)
        else synthesisWord(recovery)
        val detail = if (recoveryCalibration != null) {
            // #612: if the baseline aged out silently — connected, but no new night for > staleDays — say WHY
            // it's calibrating instead of only "learning your baseline". `stale` is always > staleDays (14).
            val stale = Baselines.nightsSinceNewestValidNight(days.map { it.day }, days.map { it.avgHrv }, logicalDayKeyNow())
            if (stale != null && stale > Baselines.staleDays) {
                uiString(R.string.l10n_today_screen_no_new_nights_from_your_strap_for_stale_days_8863bcfe, stale)
            } else {
                // Comma (not the old em-dash) to match the Swift canonical synthesis copy VERBATIM
                // (TodayView "Learning your baseline, N of M nights.") and the no-em-dash standing rule.
                // #612 above covers a TOTAL drought. The common shape is the other one: nights arriving,
                // most of them empty. Five days in with three HRV-less nights sits at "2 of 4" with no
                // reason given, which reads as a stuck counter rather than as missing data. Name the
                // missing nights so a wearer has something to act on instead of something to wait for.
                // Swift twin: TodayView.calibrationDetail.
                val cov = Baselines.recentHrvCoverage(
                    days.map { it.day }, days.map { it.avgHrv }, logicalDayKeyNow(),
                )
                val base = uiString(
                    R.string.today_synthesis_learning_baseline, recoveryCalibration, Baselines.minNightsSeed,
                )
                if (cov.missing > 0 && cov.observed > 0) {
                    base + " " + uiString(R.string.today_synthesis_nights_without_hrv, cov.missing, cov.observed)
                } else {
                    base
                }
            }
        } else if (carriedDay != null) {
            // Carried prior-day read, summarise that day + stamp it so it isn't passed off as today's.
            uiString(
                R.string.today_synthesis_carried_detail,
                synthesisDetail(carriedDay), carriedCaption(carriedDay.day).localized(),
            )
        } else {
            synthesisDetail(day)
        }
        // The slider places a SCORED day on its band; while calibrating there is no score to place.
        val scored = recovery.takeIf { recoveryCalibration == null }
        SynthesisGlassCard(
            eyebrow = uiString(if (readiness != null) R.string.readiness_title else R.string.l10n_today_screen_synthesis_876bc749),
            card = readiness ?: ReadinessCardUi(status, detail, band = scored?.let(::synthesisBand), sliderScore = scored),
            expanded = synthesisExpanded,
            onToggle = onToggleSynthesis,
        )
        // C-03 Today's plan (Main.dc.html), Effort part only: the sleep figure waits for one agreed sleep
        // need (S0-3, product owner 2026-11-12). Hidden while calibrating and for past days.
        val ceiling = if (isToday && recoveryCalibration == null) planEffortCeiling(recovery, effortScale) else null
        // R9a V2-2: with the one sleep need approved, the plan names tonight's need (S0-3).
        val context = LocalContext.current
        val tonightNeed = if (ceiling != null && MetricsV2.on(V2Change.PLAN_SLEEP_HALF)) {
            remember(days) { planSleepNeedMin(days, logicalDayNow(), ProfileStore.from(context).knownAge) }
        } else {
            null
        }
        if (ceiling != null) {
            // SQ8a: the column's gap stands for the rest of the card; the plan gets the PNG's 24 above and 50 below.
            Box(
                Modifier.padding(
                    top = TodayRhythm.synthesisToPlan - Metrics.gap,
                    bottom = TodayRhythm.planToNext - TodayRhythm.sectionGap,
                ),
            ) {
                // SQ5 (V2-11): after a nap the target is the need less the nap, and the line says so.
                val nap = todayNapAsleepMin.takeIf { it >= 1.0 && MetricsV2.on(V2Change.NAP_PLAN_TARGET) }
                if (tonightNeed != null && nap != null) {
                    TodayPlanCard(
                        ceiling = ceiling,
                        need = hoursMinutes(planSleepTargetMin(tonightNeed, nap)),
                        onOpenCoach = onOpenCoach,
                        pushUp = planEffortPushesUp(recovery),
                        napNote = hoursMinutes(tonightNeed) to napDuration(nap),
                    )
                } else {
                    TodayPlanCard(
                        ceiling = ceiling, need = tonightNeed?.let(::hoursMinutes), onOpenCoach = onOpenCoach,
                        pushUp = planEffortPushesUp(recovery),
                    )
                }
            }
        } else {
            // Owner (15 Nov 2026): the plan stays hidden on a past day, while Charge calibrates and with no Charge, but its
            // space is held so the sections below keep their place (Key Metrics just above the bar).
            Spacer(
                Modifier.height(
                    TodayRhythm.synthesisToPlan - Metrics.gap + TodayRhythm.planSlot + TodayRhythm.planToNext - TodayRhythm.sectionGap,
                ),
            )
        }
    }
}

/**
 * The D-08 plan target: the TOP of v10's recovery-derived optimal Effort band ([optimalStrainRange]: Charge >= 67
 * -> 14-18, 34-66 -> 10-14, < 34 -> 4-10, on the 0-21 axis), shown on the user's Effort scale. On 0-100 it is the
 * band top x 100 / 21, rounded. Null when there is no Charge, so no target is ever guessed. Pure, unit-tested.
 */
internal fun planEffortCeiling(recovery: Double?, scale: EffortScale): String? {
    val band = optimalStrainRange(recovery) ?: return null
    return if (scale == EffortScale.WHOOP) band.high.toString()
    else (band.high * 100.0 / 21.0).roundToInt().toString()
}

/** The plan's wording threshold (owner, 19 Nov 2026): a target above 50 on the 0-100 Effort axis reads "Effort can push up
 *  to N", a lower one keeps "Keep Effort under N". Judged on the 0-100 equivalent so both Effort scales word alike. */
internal const val PLAN_PUSH_UP_ABOVE = 50.0

/** True when today's Effort target is above [PLAN_PUSH_UP_ABOVE] (0-100 equivalent of the band top); false with no Charge. */
internal fun planEffortPushesUp(recovery: Double?): Boolean {
    val band = optimalStrainRange(recovery) ?: return false
    return band.high * 100.0 / 21.0 > PLAN_PUSH_UP_ABOVE
}

/** Tonight's sleep need for the plan line (R9a V2-2), in minutes: the registry's one need (V2-1) for today. */
internal fun planSleepNeedMin(days: List<DailyMetric>, today: LocalDate, age: Int?): Double? =
    V10MetricRegistry(V10MetricInputs(days = days, today = today, age = age))
        .value(MetricId.SLEEP_NEED, DayKey(today)).value?.times(60.0)

/** A nap's length as the Activities row writes it: "40m" under an hour, "1h 5m" from an hour. */
@Composable
private fun napDuration(minutes: Double): String {
    val m = minutes.roundToInt()
    return localizedMetricValue(if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m")
}

/** SQ5-c (V2-11): tonight's sleep target, the one need less today's nap asleep minutes, never below zero. Pure. */
internal fun planSleepTargetMin(needMin: Double, napAsleepMin: Double): Double = (needMin - napAsleepMin).coerceAtLeast(0.0)

/**
 * B2-1a-4 (Main.dc.html "Ask Sage →"): a 40 dp pill with a 12 dp radius, the strong glass fill and hairline, and a 13/16
 * semibold label in the brand mint followed by an arrow. The arrow is a glyph, so the string stays the plain label.
 */
@Composable
private fun AskSagePill(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(Ridge.glassFillStrong)
            .border(1.dp, Ridge.glassStroke, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = RidgeType.body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), color = Ridge.brand, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(14.dp))
    }
}

/** The Today's plan card (Main.dc.html): a glass row with the check-circle icon, "Today's plan", the Effort
 *  ceiling highlighted in the Charge liquid's moderate gold, and an "Ask Coach" button while Coach is on. */
@Composable
private fun TodayPlanCard(
    ceiling: String,
    need: String?,
    onOpenCoach: (() -> Unit)?,
    // Owner (19 Nov 2026): a target above 50 reads "Effort can push up to N"; otherwise "Keep Effort under N".
    pushUp: Boolean = false,
    // SQ5 (V2-11): the night's full need and today's nap, when the target was reduced by it.
    napNote: Pair<String, String>? = null,
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Ridge.space4, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                    Icon(RidgeIcons.checkCircle, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(18.dp))
                    Text(uiString(R.string.today_plan_title), style = RidgeType.listTitle.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Ridge.ink)
                }
                val full = when {
                    need != null && napNote != null -> uiString(
                        if (pushUp) R.string.today_plan_push_and_sleep_nap else R.string.today_plan_effort_and_sleep_nap,
                        ceiling, need, napNote.first, napNote.second,
                    )
                    need != null -> uiString(
                        if (pushUp) R.string.today_plan_push_and_sleep else R.string.today_plan_effort_and_sleep, ceiling, need,
                    )
                    else -> uiString(if (pushUp) R.string.today_plan_push_under else R.string.today_plan_effort_under, ceiling)
                }
                val at = full.indexOf(ceiling)
                val needAt = need?.let { full.indexOf(it, startIndex = maxOf(0, at + ceiling.length)) } ?: -1
                Text(
                    buildAnnotatedString {
                        append(full)
                        if (at >= 0) addStyle(SpanStyle(color = Ridge.stateModerate, fontWeight = FontWeight.SemiBold), at, at + ceiling.length)
                        if (need != null && needAt >= 0) {
                            addStyle(SpanStyle(color = Ridge.rest, fontWeight = FontWeight.SemiBold), needAt, needAt + need.length)
                        }
                    },
                    style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = Ridge.inkMuted,
                )
            }
            if (onOpenCoach != null) {
                AskSagePill(uiString(R.string.today_plan_ask_coach), onOpenCoach)
            }
        }
    }
}

/**
 * The Noop Ridge SynthesisScale: a glass card washed and ringed in the colour of the day's state, the
 * "SYNTHESIS" eyebrow over the state word in Playfair, one line of guidance beside it behind a hairline, and
 * a chevron. Expanded, [SynthesisBandSlider] places the Charge on the five-state scale. The word and the
 * guidance are the SAME in both states; only the scale folds, never the read (#506). [recovery] null (still
 * calibrating) leaves the card neutral and hides the scale, since there is no score to place.
 */
@Composable
internal fun SynthesisGlassCard(
    eyebrow: String,
    card: ReadinessCardUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state = card.band?.let { SYNTHESIS_STATE_COLOURS[it] }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // RS-3e: the closed card sets the height Today's alert cards match.
            .heightIn(min = TODAY_GLANCE_CARD_HEIGHT)
            .liquidPress(interaction)
            .clip(RoundedCornerShape(Ridge.radiusLg))
            .ridgeGlass(radius = Ridge.radiusLg, tint = state, tintWash = 0.16f, tintBorder = 0.45f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = uiString(
                    if (expanded) R.string.today_action_collapse else R.string.today_action_expand_full_read,
                ),
                onClick = onToggle,
            )
            .padding(horizontal = Ridge.space5, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Ridge.space5, Alignment.CenterVertically),
    ) {
        // Main.dc.html: collapsed shows the eyebrow, the state word in its colour with a soft glow, and the
        // chevron; expanded adds the guidance sentence and the five-stage scale. The word is the same in both
        // states, only the detail folds, never the read (#506).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    eyebrow.uppercase(Locale.getDefault()),
                    style = RidgeType.eyebrow,
                    color = Ridge.inkSubtle,
                )
                AutoSizeValue(
                    text = card.word,
                    style = RidgeType.titleLg.copy(
                        fontSize = 22.sp, lineHeight = 26.sp,
                        shadow = state?.let { Shadow(color = it.copy(alpha = 0.55f), blurRadius = 14f) },
                    ),
                    color = state ?: Ridge.ink,
                    minScale = 0.6f,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Ridge.inkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Text(card.detail, style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted)
        }
        if (expanded && card.band != null && card.sliderScore != null) {
            SynthesisBandSlider(card.sliderScore, card.band)
        }
    }
}

/** The five Synthesis bands, as [synthesisWord] draws them: each band's lower bound on the 0–100 Charge
 *  scale and its word. The slider gives every band an equal fifth of the track so all five words fit. */
internal val SYNTHESIS_BAND_FLOORS = listOf(0.0, 25.0, 50.0, 70.0, 88.0)

/** The five Synthesis state colours, Depleted -> Peak. D-02 (Checkpoint B): v10 bands with the Ridge colours, so
 *  Strong is the brand mint `#59ecaa` and Peak the Effort blue `#6ba1fd` (`Main.dc.html` gradient). Synthesis
 *  only: the shared [Ridge.states] tokens keep `noop-tokens.css` values. */
internal val SYNTHESIS_STATE_COLOURS = listOf(
    Ridge.stateDepleted, Ridge.stateLow, Ridge.stateModerate, Ridge.brand, Ridge.effort,
)

/** Which of the five bands (0 = Depleted … 4 = the top band) a Charge [score] falls in. */
private fun synthesisBand(score: Double): Int =
    SYNTHESIS_BAND_FLOORS.indexOfLast { score.coerceIn(0.0, 100.0) >= it }.coerceAtLeast(0)

/** Where a Charge [score] sits on the equal-fifths slider track (0..1): the band's fifth, plus the score's
 *  progress through that band. Monotonic, so a higher Charge is never drawn left of a lower one. */
internal fun synthesisSliderPosition(score: Double): Float {
    val s = score.coerceIn(0.0, 100.0)
    val band = SYNTHESIS_BAND_FLOORS.indexOfLast { s >= it }.coerceAtLeast(0)
    val lo = SYNTHESIS_BAND_FLOORS[band]
    val hi = SYNTHESIS_BAND_FLOORS.getOrNull(band + 1) ?: 100.0
    val within = if (hi > lo) ((s - lo) / (hi - lo)).coerceIn(0.0, 1.0) else 0.0
    return ((band + within) / SYNTHESIS_BAND_FLOORS.size).toFloat()
}

/**
 * The expanded Synthesis scale: an 8dp track running through the five state colours, a glowing thumb in the
 * current state's colour (ringed in the page ground) placed by [synthesisSliderPosition], and the five band
 * words beneath with the current one lit. Display-only — the Charge is computed elsewhere.
 */
@Composable
private fun SynthesisBandSlider(score: Double, activeBand: Int) {
    val pos = synthesisSliderPosition(score)
    val thumb = SYNTHESIS_STATE_COLOURS[activeBand]
    val words = listOf(
        uiString(R.string.today_synthesis_depleted),
        uiString(R.string.today_synthesis_low),
        uiString(R.string.today_synthesis_steady),
        uiString(R.string.today_synthesis_strong),
        uiString(R.string.today_synthesis_peak),
    )
    val stops = SYNTHESIS_STATE_COLOURS.mapIndexed { i, c -> (i / (SYNTHESIS_STATE_COLOURS.size - 1f)) to c }
    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(Ridge.space3),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val knobR = 11.dp.toPx()
            val left = knobR
            val right = size.width - knobR
            val cy = size.height / 2f
            drawLine(
                brush = Brush.horizontalGradient(*stops.toTypedArray(), startX = left, endX = right),
                start = Offset(left, cy),
                end = Offset(right, cy),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val x = left + (right - left) * pos
            drawCircle(
                brush = Brush.radialGradient(
                    0f to thumb.copy(alpha = 0.6f),
                    1f to thumb.copy(alpha = 0f),
                    center = Offset(x, cy),
                    radius = knobR * 2f,
                ),
                radius = knobR * 2f,
                center = Offset(x, cy),
            )
            drawCircle(color = Ridge.bg100, radius = knobR, center = Offset(x, cy))
            drawCircle(color = thumb, radius = knobR - 3.dp.toPx(), center = Offset(x, cy))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            words.forEachIndexed { i, word ->
                Text(
                    word,
                    style = if (i == activeBand) RidgeType.bodySm.copy(fontWeight = FontWeight.Bold) else RidgeType.bodySm,
                    color = if (i == activeBand) thumb else Ridge.inkSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = when (i) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        words.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

