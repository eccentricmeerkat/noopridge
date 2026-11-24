package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.heading
import com.noop.ui.ridge.RidgeBackButton
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgePageBackdrop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import android.content.Context
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.analytics.ChargeDriver
import com.noop.analytics.ChargeDriverLabel
import com.noop.analytics.ChargeDriverUnit
import com.noop.analytics.ChargeDriverVerdict
import com.noop.analytics.ReadinessEngine
import com.noop.analytics.ScoreConfidence
import com.noop.data.DailyMetric
import com.noop.metrics.MetricsV2
import com.noop.metrics.V2Change
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A1/S4: the Charge breakdown sheet opened by tapping the hero Charge ring. A full-screen surface with a
 * titled top bar (Close) and a scrollable body hosting the existing What-shaped-it breakdown, the
 * Contributors bars and (S4) the folded Readiness card. Built only when shown (the caller gates on
 * showChargeBreakdown), so the heavy rows materialise on tap (#819). Nothing is recomputed here, it reuses
 * the existing sections, which read the SAME carried/today row the ring shows. Mirrors iOS chargeBreakdownSheet.
 * `internal` (not private) so the Coupled view's hero ring (task #43) opens THIS same sheet, one breakdown,
 * never a duplicate.
 */
@Composable
internal fun ChargeBreakdownSheet(
    days: List<DailyMetric>,
    displayDay: DailyMetric?,
    carriedDay: DailyMetric?,
    showReadiness: Boolean,
    onClose: () -> Unit,
    onHowCalculated: () -> Unit,
    // #1995: the Charge ring opens THIS sheet rather than the trend, because the sheet's other entry
    // (the Synthesis card) sits in a section the user can hide, so routing the ring away could orphan it.
    // The trend is therefore reachable from inside, which keeps every hero ring leading to its own
    // score's detail while Charge's stays the richer one.
    //
    // Null renders NO row, which is why CoupledScreen passes nothing: that screen has no metric
    // navigation at all, so a link there would be a dead one. A host that cannot go somewhere should
    // not offer to.
    onOpenTrend: (() -> Unit)? = null,
    // B2-1b: opens Sage from the Ridge page's "Try today" card. Null (Coach off, or a host with no Sage) hides the button.
    onOpenCoach: (() -> Unit)? = null,
) {
    if (ridgeShell) {
        RidgeChargeBreakdown(days, displayDay, carriedDay, showReadiness, onClose, onHowCalculated, onOpenTrend, onOpenCoach)
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Palette.surfaceBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.screenPadding, vertical = Metrics.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    uiString(R.string.l10n_today_screen_what_shaped_your_charge_9f53a2b3),
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = uiString(R.string.l10n_today_screen_close_bbfa773e), tint = Palette.textSecondary)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Metrics.screenPadding)
                    .padding(bottom = Metrics.sectionGap),
                verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
            ) {
                // The breakdown self-gates: a calibrating night (empty drivers) renders nothing here, the
                // Contributors + Readiness below still give an honest read, never a blank sheet.
                RecoveryDriversSection(days = days, displayDay = displayDay, carriedDay = carriedDay)
                RecoveryContributorsSection(day = displayDay, carriedDay = carriedDay)
                // S4: the SEPARATE Readiness block now lives here behind the Charge-ring tap (today-only,
                // matching the old inline gate). A one-word read (Push / Maintain / Rest) stays on the hero.
                if (showReadiness) ReadinessSection(days, carriedDay = carriedDay)
                onOpenTrend?.let { openTrend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                onClickLabel = uiString(R.string.l10n_today_screen_see_the_charge_trend_9dcbcff7),
                                onClick = openTrend,
                            )
                            .background(Palette.surfaceInset)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = DomainTheme.Charge.color,
                            modifier = Modifier.size(14.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                uiString(R.string.l10n_today_screen_see_the_charge_trend_9dcbcff7),
                                style = NoopType.subhead,
                                color = Palette.textPrimary,
                            )
                            Text(
                                uiString(R.string.l10n_today_screen_every_day_s_score_over_time_a00ea7c7),
                                style = NoopType.caption,
                                color = Palette.textTertiary,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Palette.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                // Everything above is what shaped YOUR Charge today; this opens the general METHOD behind the
                // score, so the two are clearly separated, not conflated. Opens the scoring guide at the
                // Charge section, the same target the per-ring ⓘ buttons use. Mirrors the iOS chargeBreakdown
                // "How Charge is calculated" NavigationLink to ScoringGuideView(initialSection: .charge).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            onClickLabel = uiString(R.string.today_action_how_charge),
                            onClick = onHowCalculated,
                        )
                        .background(Palette.surfaceInset)
                        .padding(14.dp)
                        .semantics {
                            contentDescription = uiString(R.string.l10n_today_screen_how_charge_is_calculated_the_method_3ea7548b)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.Functions,
                        contentDescription = null,
                        tint = DomainTheme.Charge.color,
                        modifier = Modifier.size(14.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            uiString(R.string.l10n_today_screen_how_charge_is_calculated_142d5e8e),
                            style = NoopType.subhead,
                            color = Palette.textPrimary,
                        )
                        Text(
                            uiString(R.string.l10n_today_screen_the_method_behind_the_score_not_5bc68508),
                            style = NoopType.caption,
                            color = Palette.textTertiary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Palette.textTertiary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * B2-1b-5 (ChargeBreakdown.dc.html): the Ridge page. A back arrow, the centred title and a mint "Guide" link, then the
 * Charge orb with its state pill and one-line read, and the drivers as the board's glass list. Everything the v10 sheet
 * carries (Contributors, Readiness, the trend link) stays below, on the same glass tokens.
 */
@Composable
private fun RidgeChargeBreakdown(
    days: List<DailyMetric>,
    displayDay: DailyMetric?,
    carriedDay: DailyMetric?,
    showReadiness: Boolean,
    onClose: () -> Unit,
    onHowCalculated: () -> Unit,
    onOpenTrend: (() -> Unit)?,
    onOpenCoach: (() -> Unit)?,
) {
    val readDay = carriedDay ?: displayDay
    CompositionLocalProvider(LocalGlassCards provides true) {
        Box(Modifier.fillMaxSize().background(Ridge.bg100)) {
            RidgePageBackdrop()
            Column(Modifier.fillMaxSize().pageStatusBarPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RidgeBackButton(uiString(R.string.l10n_today_screen_close_bbfa773e), onClose)
                    Text(
                        uiString(R.string.l10n_today_screen_what_shaped_your_charge_9f53a2b3),
                        style = RidgeType.body.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
                        color = Ridge.ink,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                    )
                    Text(
                        uiString(R.string.today_charge_guide),
                        style = RidgeType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = Ridge.brand,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Ridge.radiusSm))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onHowCalculated)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Ridge.space5)
                        .padding(bottom = Ridge.space6),
                    verticalArrangement = Arrangement.spacedBy(Ridge.space5),
                ) {
                    RidgeChargeHero(readDay)
                    RecoveryDriversSection(days = days, displayDay = displayDay, carriedDay = carriedDay)
                    if (showReadiness) RidgeTryToday(days, readDay, onOpenCoach)
                    RecoveryContributorsSection(day = displayDay, carriedDay = carriedDay)
                    if (showReadiness) ReadinessSection(days, carriedDay = carriedDay)
                    onOpenTrend?.let { RidgeBreakdownLink(uiString(R.string.l10n_today_screen_see_the_charge_trend_9dcbcff7), uiString(R.string.l10n_today_screen_every_day_s_score_over_time_a00ea7c7), it) }
                    RidgeBreakdownLink(
                        uiString(R.string.l10n_today_screen_how_charge_is_calculated_142d5e8e),
                        uiString(R.string.l10n_today_screen_the_method_behind_the_score_not_5bc68508),
                        onHowCalculated,
                    )
                }
            }
        }
    }
}

/**
 * B2-1b (ChargeBreakdown.dc.html "Try today"): the Effort target and tonight's sleep need, from the same sources as Today's
 * plan, an "Ask Sage about today" button when Sage is on, and a footnote with how many nights of HRV data back the read.
 * Today only, and only with a scored Charge (no target is ever guessed). The board's third line (hydration) is not built.
 */
@Composable
private fun RidgeTryToday(days: List<DailyMetric>, day: DailyMetric?, onOpenCoach: (() -> Unit)?) {
    val context = LocalContext.current
    val recovery = day?.recovery
    val ceiling = planEffortCeiling(recovery, UnitPrefs.effortScale(context)) ?: return
    val need = if (MetricsV2.on(V2Change.PLAN_SLEEP_HALF)) {
        remember(days) { planSleepNeedMin(days, logicalDayNow(), ProfileStore.from(context).knownAge) }
    } else null
    val nights = remember(days) {
        val ordered = days.sortedBy { it.day }
        Baselines.foldHistory(ordered.map { it.avgHrv }, ordered.map { it.day }, Baselines.hrvCfg, 0.0).nValid
    }
    val shape = RoundedCornerShape(Ridge.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ridge.glassFillStrong)
            .border(1.dp, Ridge.brand.copy(alpha = 0.35f), shape)
            .padding(Ridge.space5),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RidgeEyebrow(uiString(R.string.today_try_title))
        val lines = buildList {
            add(uiString(if (planEffortPushesUp(recovery)) R.string.today_plan_push_under else R.string.today_plan_effort_under, ceiling))
            if (need != null) add(uiString(R.string.today_try_sleep, hoursMinutes(need)))
        }
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(RidgeIcons.check, contentDescription = null, tint = Ridge.brand, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Text(line, style = RidgeType.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = Ridge.ink)
            }
        }
        onOpenCoach?.let {
            com.noop.ui.ridge.RidgeButton(uiString(R.string.today_try_ask_sage), it, modifier = Modifier.fillMaxWidth(), kind = com.noop.ui.ridge.RidgeButtonKind.Secondary)
        }
        Text(uiString(R.string.today_try_footnote, nights), style = RidgeType.bodySm, color = Ridge.inkSubtle)
    }
}

/** The orb, the state pill and the one-line read at the head of the Ridge breakdown. Nothing when Charge has no score. */
@Composable
private fun RidgeChargeHero(day: DailyMetric?) {
    val score = day?.recovery ?: return
    val band = SYNTHESIS_STATE_COLOURS.let { colours ->
        val i = listOf(0.0, 25.0, 50.0, 70.0, 88.0).indexOfLast { score >= it }.coerceAtLeast(0)
        colours[i]
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LiquidOrb(
            fraction = score / 100.0, display = score.roundToInt().toString(), liquid = RidgeLiquid.CHARGE,
            diameter = 166.dp, glass = true, modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            synthesisWord(score).uppercase(Locale.getDefault()),
            style = RidgeType.eyebrow.copy(fontSize = 13.sp, letterSpacing = 0.2.em),
            color = band,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, band.copy(alpha = 0.7f), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            synthesisDetail(day),
            style = RidgeType.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = Ridge.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** A glass row that opens something (the board's trend and method links). */
@Composable
private fun RidgeBreakdownLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ridge.radiusMd))
            .background(Ridge.glassFillStrong)
            .border(1.dp, Ridge.glassStroke, RoundedCornerShape(Ridge.radiusMd))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(Ridge.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold), color = Ridge.ink)
            Text(subtitle, style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(18.dp))
    }
}

// MARK: - "What shaped it" the engine-computed Charge driver breakdown
//
// The SHARED-CONTRACT driver rows under the Charge ring: one row per REAL term the recovery scorer used,
// each carrying its signed point contribution (deltaPoints), the night's value, the personal baseline it
// was scored against, and a short plain-English verdict. Computed by RecoveryDrivers.chargeDrivers from
// the SAME inputs the Charge ring reads, so a row can never describe a term the score did not use; a
// missing input yields NO row (never a faked zero). The confidence dot + tier tag SURFACE the existing
// ScoreConfidence.forCharge: they are read, not recomputed. Hidden entirely when the day can't score
// (cold-start / no drivers). Byte-aligned with the iOS "What shaped it" section. No em-dashes.

@Composable
internal fun RecoveryDriversSection(
    days: List<DailyMetric>,
    displayDay: DailyMetric?,
    carriedDay: DailyMetric? = null,
) {
    // #2315: the same recalibration epoch the engine folds with. Read here rather than threaded from the
    // caller because this section is the only consumer, and the pref read is one getLong behind remember.
    val context = LocalContext.current
    val hrvEpoch = remember { NoopPrefs.of(context).getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble() }
    // Read the row the Charge ring itself reads: today's own when scored, else the carried last-scored
    // day (#543) so the breakdown matches the carried ring instead of vanishing at the rollover.
    val readDay = carriedDay ?: displayDay
    val drivers = remember(days, readDay, hrvEpoch) { recoveryChargeDrivers(days, readDay, hrvEpoch) }
    if (drivers.isEmpty()) return

    val tier = remember(days, readDay, hrvEpoch) { chargeConfidenceTier(days, readDay, hrvEpoch) }
    val overline = carriedDay?.let { uiString(R.string.today_charge_carried, carriedCaption(it.day).localized()) }
        ?: uiString(R.string.trends_charge)

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        // Header row: section title + the SURFACED confidence pill (dot + tier tag) on the right.
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space8),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(uiString(R.string.today_what_shaped_it), overline = overline, trailing = uiString(R.string.today_vs_your_baseline))
            }
            // R3b: only Calibrating is a user-facing confidence tag (06; Solid pill removed). Building and Solid
            // read as settled and show nothing.
            if (tier == ScoreConfidence.CALIBRATING) ChargeConfidencePill(tier)
        }
        NoopCard {
            Column(verticalArrangement = Arrangement.spacedBy(if (LocalGlassCards.current) 0.dp else Metrics.space16)) {
                val maxPts = drivers.maxOf { kotlin.math.abs(it.deltaPoints) }.coerceAtLeast(1)
                drivers.forEachIndexed { i, d ->
                    if (LocalGlassCards.current) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(Metrics.divider).background(Ridge.glassStroke))
                        RidgeDriverRow(d, maxPts)
                    } else DriverRow(d)
                }
                Text(
                    uiString(R.string.today_charge_driver_guidance),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/** The SURFACED Charge confidence pill (dot + tier tag). Reads the existing ScoreConfidence, never
 *  recomputes it. SOLID = gold/accent, BUILDING = the blue warning tone, CALIBRATING = neutral slate. */
@Composable
private fun ChargeConfidencePill(tier: ScoreConfidence) {
    val (label, tone) = when (tier) {
        ScoreConfidence.SOLID -> uiString(R.string.today_confidence_solid) to StrandTone.Accent
        ScoreConfidence.BUILDING -> uiString(R.string.today_confidence_building) to StrandTone.Warning
        ScoreConfidence.CALIBRATING -> uiString(R.string.today_confidence_calibrating) to StrandTone.Neutral
    }
    StatePill(title = label, tone = tone)
}

/** The strings one driver row shows, shared by the v10 row and the Ridge row so they cannot differ. */
private class DriverText(val label: String, val valueText: String, val baselineText: String, val verdict: String, val signed: String)

@Composable
private fun driverText(driver: ChargeDriver): DriverText {
    val signed = if (driver.deltaPoints > 0) "+${driver.deltaPoints}" else "${driver.deltaPoints}"
    val label = when (driver.label) {
        ChargeDriverLabel.HEART_RATE_VARIABILITY -> uiString(R.string.today_driver_hrv)
        ChargeDriverLabel.RESTING_HEART_RATE -> uiString(R.string.today_driver_resting_hr)
        ChargeDriverLabel.SLEEP_QUALITY -> uiString(R.string.today_driver_sleep_quality)
        ChargeDriverLabel.RESPIRATORY_RATE -> uiString(R.string.today_driver_respiratory)
        ChargeDriverLabel.SKIN_TEMPERATURE -> uiString(R.string.today_driver_skin_temperature)
    }
    val valueText = when (driver.unit) {
        ChargeDriverUnit.MILLISECONDS -> uiString(R.string.today_driver_value_ms, driver.value.roundToInt())
        ChargeDriverUnit.BEATS_PER_MINUTE -> uiString(R.string.today_driver_value_bpm, driver.value.roundToInt())
        ChargeDriverUnit.PERCENT -> uiString(R.string.today_driver_value_percent, driver.value.roundToInt())
        ChargeDriverUnit.BREATHS_PER_MINUTE -> uiString(
            R.string.today_driver_value_br_min, String.format(Locale.getDefault(), "%.1f", driver.value),
        )
        ChargeDriverUnit.CELSIUS_DEVIATION -> uiString(
            R.string.today_driver_value_temp_deviation,
            String.format(Locale.getDefault(), "%+.1f", driver.value),
        )
    }
    val baselineText = driver.baseline?.let { baseline -> when (driver.unit) {
        ChargeDriverUnit.MILLISECONDS -> uiString(R.string.today_driver_baseline_ms, baseline.roundToInt())
        ChargeDriverUnit.BEATS_PER_MINUTE -> uiString(R.string.today_driver_baseline_bpm, baseline.roundToInt())
        ChargeDriverUnit.BREATHS_PER_MINUTE -> uiString(
            R.string.today_driver_baseline_br_min, String.format(Locale.getDefault(), "%.1f", baseline),
        )
        ChargeDriverUnit.PERCENT, ChargeDriverUnit.CELSIUS_DEVIATION -> ""
    } } ?: ""
    val verdict = when (driver.verdict) {
        ChargeDriverVerdict.ABOVE_BASELINE_SUPPORTING -> uiString(R.string.today_driver_above_supporting)
        ChargeDriverVerdict.BELOW_BASELINE_SUPPORTING -> uiString(R.string.today_driver_below_supporting)
        ChargeDriverVerdict.ABOVE_BASELINE_LIMITING -> uiString(R.string.today_driver_above_limiting)
        ChargeDriverVerdict.BELOW_BASELINE_LIMITING -> uiString(R.string.today_driver_below_limiting)
        ChargeDriverVerdict.AT_BASELINE -> uiString(R.string.today_driver_at_baseline)
        ChargeDriverVerdict.HRV_SATURATION_LIMITING -> uiString(R.string.today_driver_hrv_saturation)
        ChargeDriverVerdict.STRONG_NIGHT_SUPPORTING -> uiString(R.string.today_driver_strong_night)
        ChargeDriverVerdict.BELOW_GOOD_NIGHT_LIMITING -> uiString(R.string.today_driver_below_good_night)
        ChargeDriverVerdict.TYPICAL_NIGHT -> uiString(R.string.today_driver_typical_night)
        ChargeDriverVerdict.NEAR_BASELINE -> uiString(R.string.today_driver_near_baseline)
        ChargeDriverVerdict.WARMER_THAN_BASELINE_LIMITING -> uiString(R.string.today_driver_warmer_limiting)
        ChargeDriverVerdict.COOLER_THAN_BASELINE_LIMITING -> uiString(R.string.today_driver_cooler_limiting)
    }
    return DriverText(label, valueText, baselineText, verdict, signed)
}

/** One Ridge driver row (ChargeBreakdown.dc.html): a signal dot, the name over "value · baseline", the signed points in the serif,
 *  a chevron that opens the plain-English verdict, and a bar of the row's share of the largest pull. */
@Composable
private fun RidgeDriverRow(driver: ChargeDriver, maxPts: Int) {
    val t = driverText(driver)
    var open by remember { mutableStateOf(false) }
    val tone = when {
        driver.deltaPoints > 0 -> Ridge.stateGood
        driver.deltaPoints < 0 -> Ridge.stateDepleted
        else -> Ridge.inkSubtle
    }
    val dot = when (driver.label) {
        ChargeDriverLabel.HEART_RATE_VARIABILITY -> Ridge.dataHrv
        ChargeDriverLabel.RESTING_HEART_RATE -> Ridge.dataHeart
        ChargeDriverLabel.SLEEP_QUALITY -> Ridge.rest
        ChargeDriverLabel.RESPIRATORY_RATE -> Ridge.dataBreath
        ChargeDriverLabel.SKIN_TEMPERATURE -> Ridge.dataEnergy
    }
    val minus = t.signed.replace("-", "\u2212")
    val a11y = uiString(R.string.today_driver_a11y, t.label, t.valueText, t.baselineText, t.signed, t.verdict)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = t.label) { open = !open }
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.label, style = RidgeType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = Ridge.ink)
                Text(
                    if (t.baselineText.isBlank()) t.valueText else t.valueText + " \u00b7 " + t.baselineText,
                    style = RidgeType.bodySm, color = Ridge.inkMuted,
                )
            }
            Text(minus, style = RidgeType.titleSm.copy(fontSize = 24.sp, lineHeight = 28.sp), color = tone)
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = Ridge.inkSubtle, modifier = Modifier.size(20.dp),
            )
        }
        // The bar: a faint track with the fill anchored to the right, as long as this pull is against the largest.
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ridge.glassStroke)) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(fraction = (kotlin.math.abs(driver.deltaPoints).toFloat() / maxPts).coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(tone, RoundedCornerShape(2.dp)),
            )
        }
        if (open) Text(t.verdict, style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = Ridge.inkMuted)
    }
}

/** One "What shaped it" driver row: an up/down delta chip (signed points, green up / red down),
 *  the label + verdict, and the value over its baseline. Mirrors the iOS driver row layout. */
@Composable
private fun DriverRow(driver: ChargeDriver) {
    val positive = driver.deltaPoints >= 0
    // A zero delta reads neutral (no green/red), not a misleading "good".
    val tone = when {
        driver.deltaPoints > 0 -> Palette.statusPositive
        driver.deltaPoints < 0 -> Palette.statusCritical
        else -> Palette.textTertiary
    }
    val t = driverText(driver)
    val label = t.label; val valueText = t.valueText; val baselineText = t.baselineText; val verdict = t.verdict; val signed = t.signed
    val driverA11y = uiString(R.string.today_driver_a11y, label, valueText, baselineText, signed, verdict)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = driverA11y },
    ) {
        // Signed-point delta chip with a direction glyph.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(Metrics.cornerPill))
                .background(tone.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (driver.deltaPoints != 0) {
                Icon(
                    if (positive) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(uiString(R.string.l10n_today_screen_signed_pts_5ea85678, signed), style = NoopType.captionNumber, color = tone)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = NoopType.headline, color = Palette.textPrimary)
            Text(verdict, style = NoopType.footnote, color = Palette.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(valueText, style = NoopType.captionNumber, color = Palette.textPrimary)
            Text(baselineText, style = NoopType.footnote, color = Palette.textTertiary)
        }
    }
}

// MARK: - Recovery contributors (README screen #5), labelled progress bars
//
// "CONTRIBUTORS", what drove today's Charge, each as a labelled progress bar in the shared stage/zone
// bar style (inset track, round-capped metric-hue fill, right-aligned read-out). Design-Reset tokens
// (iOS RecoveryContributorsSection parity): HRV reads teal (metricCyan), Resting HR the recovery/Charge
// world (chargeColor), Sleep and Respiratory the blue sleep world. Each bar's fraction is a
// presentation-only normalisation of the day's value to a typical adult span, no scoring/logic change.
// Suppressed entirely until at least one contributor has a value.

@Composable
private fun RecoveryContributorsSection(day: DailyMetric?, carriedDay: DailyMetric? = null) {
    // The row the contributors read from: today's own when it carries recovery, else the carried last
    // scored day (#543) so the bars don't all read "No Data" at the rollover while live HR ticks. The
    // overline stamps "Last night · <date>" when carrying so the prior read isn't passed off as today's.
    val cd = carriedDay ?: day
    val hrv = cd?.avgHrv
    val rhr = cd?.restingHr?.toDouble()
    val sleepMin = cd?.totalSleepMin
    val resp = cd?.respRateBpm
    if (hrv == null && rhr == null && sleepMin == null && resp == null) return

    val overline = carriedDay?.let { uiString(R.string.today_recovery_carried, carriedCaption(it.day).localized()) }
        ?: uiString(R.string.today_metric_charge)
    SectionHeader(uiString(R.string.today_contributors), overline = overline, trailing = uiString(R.string.today_what_drove_charge))
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space16)) {
            // HRV, higher is better; map a typical 20–120 ms span. Teal (its biometric hue; iOS metricCyan).
            ContributorBar(
                label = uiString(R.string.today_metric_hrv),
                readout = hrv?.let { "${it.roundToInt()} ms" } ?: NO_DATA,
                fraction = hrv?.let { ((it - 20.0) / 100.0) },
                color = Palette.metricCyan,
            )
            // Resting HR, lower is better, so invert a typical 40–80 bpm span. Charge/recovery world (iOS
            // chargeColor, the recovery contributor reads on the WHOOP-green Charge world, not gold).
            ContributorBar(
                label = uiString(R.string.l10n_today_screen_resting_hr_26677094),
                readout = rhr?.let { "${it.roundToInt()} bpm" } ?: NO_DATA,
                fraction = rhr?.let { 1.0 - ((it - 40.0) / 40.0) },
                color = Palette.chargeColor,
            )
            // Sleep, hours in bed against an 8h target. Blue (sleep world).
            ContributorBar(
                label = uiString(R.string.l10n_today_screen_sleep_3cac34e6),
                readout = sleepMin?.let { sleepValue(cd) } ?: NO_DATA,
                fraction = sleepMin?.let { (it / 60.0) / 8.0 },
                color = Palette.sleepLight,
            )
            // Respiratory, stability around a typical 12–20 rpm span. Deep blue (sleep world).
            ContributorBar(
                label = uiString(R.string.l10n_today_screen_respiratory_1cd8c175),
                readout = resp?.let { String.format(Locale.getDefault(), "%.1f rpm", it) } ?: NO_DATA,
                fraction = resp?.let { 1.0 - ((it - 12.0) / 8.0) },
                color = Palette.sleepDeep,
            )
            Text(
                uiString(R.string.l10n_today_screen_baselines_learned_on_device_over_14_359f6812) +
                    " signal against a typical adult range, not medical advice.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

/** One labelled contributor bar: a label + right-aligned read-out over a liquid TUBE filled to [fraction].
 *  These ARE genuine single-value progress bars (each signal against a typical adult span), so the liquid
 *  finish reads well here (matching how the iOS liquid Today draws its single-value goal/strain bars as
 *  tubes). Static (not per-frame) — they sit in the tapped-open Charge breakdown, not a live surface, so
 *  `animated = false` keeps the sheet cheap. A null fraction renders an empty tube. */
@Composable
private fun ContributorBar(label: String, readout: String, fraction: Double?, color: Color) {
    val fillFrac = fraction?.coerceIn(0.0, 1.0) ?: 0.0
    val displayReadout = localizedMetricValue(readout)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space6)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(label, modifier = Modifier.weight(1f))
            Text(displayReadout, style = NoopType.captionNumber, color = Palette.textPrimary)
        }
        LiquidTube(
            frac = fillFrac,
            tint = color,
            height = Metrics.progressHeight,
            animated = false,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = uiString(R.string.l10n_today_screen_label_readout_3f166607, label, displayReadout) },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════════════════
// Explainability layer, COMPONENTS 2, 3, 4 (spec: 2026-06-20-sleep-guidance-explainability.md)
//
// "No bare number without a STATE, a REASON, and a NEXT STEP." Every uncertain or derived read-out on
// Today gets a clear state, a plain-English reason and a next step, and we NEVER fabricate a number:
// calibrating / needs-strap show NO value, carried values are always stamped with their date, and the
// provenance badge reflects the REAL per-day merge winner. The copy here is VERBATIM and must match the
// Swift today lane word-for-word (ScoreState / RecordingState). No em-dashes anywhere.
// ════════════════════════════════════════════════════════════════════════════════════════════════════

// ── COMPONENT 2, explained score states ─────────────────────────────────────────────────────────────

/** The score-state title, LOCALIZED — same split as [recordingTitle] and for the same reason:
 *  [ScoreState.title] is the English parity contract asserted verbatim against Swift in
 *  `TodayExplainabilityTest`, so the resource lookup lives here rather than in the pure mapper. */
@Composable
internal fun scoreStateTitle(state: ScoreState): String = when (state) {
    is ScoreState.Scored -> ""
    is ScoreState.Calibrating -> uiString(state.titleRes!!)
    is ScoreState.CarriedLastNight ->
        uiString(state.titleRes!!, state.dateLabel)
    ScoreState.NeedsStrap -> uiString(state.titleRes!!)
}

/** The score-state detail line, LOCALIZED.
 *
 *  The calibrating countdown becomes a real `<plurals>`. Kotlin picked the noun with
 *  `if (nightsRemaining == 1) "night" else "nights"`, which bakes in English's TWO categories — the
 *  exact hand-rolling [uiPlural]'s doc warns about. `getQuantityString` applies the LOCALE's own rules
 *  instead. (Swift splits the same sentence into two catalogue keys, so it shares the assumption; both
 *  values here come from those keys. The one-forms carry `%1$d` where the Swift copy spelled the number
 *  out — pt-PT said "mais uma noite" — because `i18n_audit` requires every quantity form of a plural to
 *  share its siblings' placeholders, the check that catches a `%1$d` dropped from just one form.) */
@Composable
internal fun scoreStateDetail(state: ScoreState): String = when (state) {
    is ScoreState.Scored -> ""
    is ScoreState.Calibrating ->
        uiPlural(R.plurals.score_state_detail_calibrating, state.nightsRemaining, state.nightsRemaining)
    is ScoreState.CarriedLastNight ->
        if (state.stale) uiString(R.string.score_state_detail_carried_stale)
        else uiString(R.string.score_state_detail_carried_fresh)
    ScoreState.NeedsStrap -> uiString(R.string.score_state_detail_needs_strap)
}

/** The honest score-state note shown in the Today flow when there is no own number to render, the
 *  state title + one what-to-do line, no fabricated value. [ScoreState.Scored] renders nothing (the
 *  tiles carry the real number). The whole card is the spec's "never a bare blank". Mirrors the iOS
 *  ScoreStateNote. */
@Composable
internal fun ScoreStateNote(state: ScoreState, restartCause: String? = null) {
    if (state is ScoreState.Scored) return
    val icon = when (state) {
        is ScoreState.Calibrating -> Icons.Filled.Tune
        is ScoreState.CarriedLastNight -> Icons.Filled.History
        ScoreState.NeedsStrap -> Icons.Filled.Warning
        is ScoreState.Scored -> Icons.Filled.Info
    }
    val tint = when (state) {
        ScoreState.NeedsStrap -> Palette.statusWarning
        else -> Palette.textTertiary
    }
    // Resolved before the Row: `semantics { }` is not a composable scope, so uiString/uiPlural cannot be
    // called inside it, and reading them once keeps the card and its a11y label on one string.
    val title = scoreStateTitle(state)
    val detail = scoreStateDetail(state)
    val noteA11y = uiString(R.string.l10n_today_screen_state_title_state_detail_f5380609, title, detail)
    NoopCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = noteA11y },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(Metrics.iconSmall),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NoopType.headline, color = Palette.textPrimary)
                Text(detail, style = NoopType.subhead, color = Palette.textSecondary)
                // #731: when the countdown restarted because the user tapped "Recalibrate
                // baseline", say so - otherwise the natural response to a fresh countdown is to
                // tap it again, resetting it once more. null (no line) if never recalibrated.
                restartCause?.let {
                    Text(it, style = NoopType.footnote, color = Palette.textTertiary)
                }
            }
        }
    }
}

/** #1505: the note shown instead of a bare blank when the selected night's beats predate WHOOP 5
 *  transport labelling and so cannot be scored. Same card shape as [ScoreStateNote] it sits beside.
 *  Twin of the iOS `chargeLegacyRRGapNote`. */
@Composable
internal fun ChargeLegacyRrGapNote() {
    val title = uiString(R.string.charge_legacy_rr_gap_title)
    val detail = uiString(R.string.charge_legacy_rr_gap_detail)
    // Resolved before the Row: `semantics { }` is not a composable scope, so uiString cannot be called
    // inside it, and reading them once keeps the card and its a11y label on one string.
    val noteA11y = uiString(R.string.l10n_today_screen_state_title_state_detail_f5380609, title, detail)
    NoopCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = noteA11y },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.MonitorHeart,
                contentDescription = null,
                tint = Palette.chargeColor,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(Metrics.iconSmall),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NoopType.headline, color = Palette.textPrimary)
                Text(detail, style = NoopType.subhead, color = Palette.textSecondary)
            }
        }
    }
}

// ── COMPONENT 3, recording status ───────────────────────────────────────────────────────────────────

/** The chip's status word, LOCALIZED.
 *
 *  [RecordingState.title] stays the English parity contract — it is asserted verbatim against the Swift
 *  twin in `TodayExplainabilityTest` and must not become resource-backed, which would drag a `Context`
 *  into a pure mapper and break those tests. So the resource lookup lives HERE, at the render site,
 *  exactly like the Swift side: `RecordingState.label` is a `LocalizedStringKey`, so its literals are
 *  catalogue KEYS that SwiftUI resolves. Kotlin's are plain `String`, so rendering `state.title`
 *  directly shipped English to every locale — the chip read "Not recording. Strap not connected."
 *  on a German phone while the iPhone read "Strap nicht verbunden. Tippe zum Verbinden."
 *
 *  Invisible to `i18n_audit`: the literals sit in a sealed-class getter, not a Compose call argument,
 *  so the scanner never saw them. Translations here are the Swift catalogue's own, copied 1:1.
 */
@Composable
private fun recordingTitle(state: RecordingState): String = when (state) {
    RecordingState.Recording -> uiString(state.titleRes)
    is RecordingState.LastSynced -> uiString(state.titleRes, state.minutesAgo)
    RecordingState.NotRecording -> uiString(state.titleRes)
    // Both connected states share the same word, as they do on Swift.
    RecordingState.HistoryExperimental, RecordingState.ConnectedNoData ->
        uiString(state.titleRes)
}

/** The chip's one-line detail, LOCALIZED. Same split as [recordingTitle]. */
@Composable
private fun recordingDetail(state: RecordingState): String = when (state) {
    RecordingState.Recording -> uiString(state.detailRes)
    is RecordingState.LastSynced -> uiString(state.detailRes)
    RecordingState.NotRecording -> uiString(state.detailRes)
    RecordingState.HistoryExperimental -> uiString(state.detailRes)
    RecordingState.ConnectedNoData -> uiString(state.detailRes)
}

/** The Today/Live recording chip: a tinted StatePill with the status word (a pulsing dot while live),
 *  plus the one-line what-it-means below. Honest, never claims "Recording" without a live stream.
 *  Tapping a not-recording chip routes to connect (Settings). Mirrors the iOS RecordingStatusChip. */
@Composable
private fun RecordingStatusChip(state: RecordingState, onConnect: () -> Unit) {
    val clickable = state is RecordingState.NotRecording || state is RecordingState.LastSynced
    // Resolved BEFORE the Row: `semantics { }` is not a composable scope, so uiString cannot be called
    // inside it. Reading them once also keeps the pill, the detail line and the a11y label on one string.
    val title = recordingTitle(state)
    val detail = recordingDetail(state)
    val chipA11y = uiString(R.string.l10n_today_screen_state_title_state_detail_f5380609, title, detail)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onConnect,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = chipA11y },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatePill(
            title = title,
            tone = state.tone,
            showsDot = true,
            pulsing = state is RecordingState.Recording,
        )
        Text(
            detail,
            style = NoopType.footnote,
            color = Palette.textTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Readiness card (ported from TodayView.swift readinessSection)
//
// On-device training-readiness synthesis. Calls the analytics ReadinessEngine over the
// view model's day history and renders the macOS card: a colored level dot + headline,
// an optional acute:chronic "load X.XX" read-out, the plain-English summary, then one
// row per driving signal (a small flag-colored dot + label + detail). The whole card is
// suppressed until there is enough history (level == INSUFFICIENT), matching macOS.

@Composable
private fun readinessEvidenceText(evidence: ReadinessEngine.Evidence): String {
    fun number(value: Double, decimals: Int): String = if (decimals == 0) {
        Math.round(value).toString()
    } else {
        String.format(Locale.getDefault(), "%.${decimals}f", value)
    }
    return when (evidence) {
        is ReadinessEngine.Evidence.MetricVsBaseline -> {
            val unit = when (evidence.unit) {
                ReadinessEngine.MetricUnit.MS -> uiString(R.string.today_unit_ms)
                ReadinessEngine.MetricUnit.BPM -> uiString(R.string.today_unit_bpm)
                ReadinessEngine.MetricUnit.RPM -> uiString(R.string.today_unit_rpm)
            }
            uiString(
                R.string.today_readiness_evidence_metric,
                number(evidence.value, evidence.decimals), number(evidence.baseline, evidence.decimals), unit,
            )
        }
        is ReadinessEngine.Evidence.Monotony ->
            uiString(R.string.today_readiness_evidence_monotony, number(evidence.value, 1))
        is ReadinessEngine.Evidence.TrainingLoad ->
            uiString(
                R.string.today_readiness_evidence_load,
                number(evidence.acute, 1), number(evidence.chronic, 1),
            )
    }
}

@Composable
private fun readinessCopy(copy: ReadinessEngine.Copy): String = uiString(
    when (copy) {
        ReadinessEngine.Copy.TODAY_READINESS_BALANCED -> R.string.today_readiness_balanced
        ReadinessEngine.Copy.TODAY_READINESS_BALANCED_SUMMARY -> R.string.today_readiness_balanced_summary
        ReadinessEngine.Copy.TODAY_READINESS_HRV_BAD -> R.string.today_readiness_hrv_bad
        ReadinessEngine.Copy.TODAY_READINESS_HRV_GOOD -> R.string.today_readiness_hrv_good
        ReadinessEngine.Copy.TODAY_READINESS_HRV_WATCH -> R.string.today_readiness_hrv_watch
        ReadinessEngine.Copy.TODAY_READINESS_LOAD_BUILDING_FAST -> R.string.today_readiness_load_building_fast
        ReadinessEngine.Copy.TODAY_READINESS_LOAD_RAMPING_DOWN -> R.string.today_readiness_load_ramping_down
        ReadinessEngine.Copy.TODAY_READINESS_LOAD_SPIKING -> R.string.today_readiness_load_spiking
        ReadinessEngine.Copy.TODAY_READINESS_LOAD_SWEET_SPOT -> R.string.today_readiness_load_sweet_spot
        ReadinessEngine.Copy.TODAY_READINESS_MONOTONY_WATCH -> R.string.today_readiness_monotony_watch
        ReadinessEngine.Copy.TODAY_READINESS_MORE_NIGHTS -> R.string.today_readiness_more_nights
        ReadinessEngine.Copy.TODAY_READINESS_NORMAL_RANGE -> R.string.today_readiness_normal_range
        ReadinessEngine.Copy.TODAY_READINESS_PRIMED -> R.string.today_readiness_primed
        ReadinessEngine.Copy.TODAY_READINESS_PRIMED_SUMMARY -> R.string.today_readiness_primed_summary
        ReadinessEngine.Copy.TODAY_READINESS_RESP_BAD -> R.string.today_readiness_resp_bad
        ReadinessEngine.Copy.TODAY_READINESS_RESP_WATCH -> R.string.today_readiness_resp_watch
        ReadinessEngine.Copy.TODAY_READINESS_RHR_BAD -> R.string.today_readiness_rhr_bad
        ReadinessEngine.Copy.TODAY_READINESS_RHR_GOOD -> R.string.today_readiness_rhr_good
        ReadinessEngine.Copy.TODAY_READINESS_RHR_WATCH -> R.string.today_readiness_rhr_watch
        ReadinessEngine.Copy.TODAY_READINESS_RUN_DOWN -> R.string.today_readiness_run_down
        ReadinessEngine.Copy.TODAY_READINESS_RUN_DOWN_SUMMARY -> R.string.today_readiness_run_down_summary
        ReadinessEngine.Copy.TODAY_READINESS_SIGNAL_HRV -> R.string.today_readiness_signal_hrv
        ReadinessEngine.Copy.TODAY_READINESS_SIGNAL_RESPIRATORY -> R.string.today_readiness_signal_respiratory
        ReadinessEngine.Copy.TODAY_READINESS_SIGNAL_RESTING_HR -> R.string.today_readiness_signal_resting_hr
        ReadinessEngine.Copy.TODAY_READINESS_SIGNAL_TRAINING_LOAD -> R.string.today_readiness_signal_training_load
        ReadinessEngine.Copy.TODAY_READINESS_SIGNAL_TRAINING_VARIETY -> R.string.today_readiness_signal_training_variety
        ReadinessEngine.Copy.TODAY_READINESS_STRAINED -> R.string.today_readiness_strained
        ReadinessEngine.Copy.TODAY_READINESS_STRAINED_SUMMARY -> R.string.today_readiness_strained_summary
        ReadinessEngine.Copy.TODAY_READINESS_TITLE -> R.string.today_readiness_title
        ReadinessEngine.Copy.TODAY_READINESS_WEAR_FOR_NIGHTS -> R.string.today_readiness_wear_for_nights
    },
)

@Composable
private fun readinessSignalDetail(signal: ReadinessEngine.Signal): String {
    val load = signal.evidence as? ReadinessEngine.Evidence.TrainingLoad
    return if (load != null && load.chronic > 0.0) {
        uiString(
            when (signal.detailRes) {
                ReadinessEngine.Copy.TODAY_READINESS_LOAD_RAMPING_DOWN -> R.string.today_readiness_load_ramping_down
                ReadinessEngine.Copy.TODAY_READINESS_LOAD_SWEET_SPOT -> R.string.today_readiness_load_sweet_spot
                ReadinessEngine.Copy.TODAY_READINESS_LOAD_BUILDING_FAST -> R.string.today_readiness_load_building_fast
                ReadinessEngine.Copy.TODAY_READINESS_LOAD_SPIKING -> R.string.today_readiness_load_spiking
                else -> error("Training-load evidence requires a training-load detail")
            },
            String.format(Locale.getDefault(), "%.2f", load.acute / load.chronic),
        )
    } else {
        readinessCopy(signal.detailRes)
    }
}

@Composable
private fun ReadinessSection(days: List<DailyMetric>, carriedDay: DailyMetric? = null) {
    // Logical day (rolls at 04:00 local), so readiness keeps reading the evening's row in the small
    // hours instead of an empty new-calendar-day row (#144). Mirrors the Today-row resolution.
    //
    // Carry-over (#543): Readiness anchors on the day whose row carries today's vitals. Right after the
    // rollover today has no scored row, so `evaluate` would read INSUFFICIENT and the whole card would
    // VANISH while live HR ticks, the same blank the carried Charge/Synthesis avoid. So when carrying,
    // anchor on the last scored day's key instead, and stamp the overline "Last night · <date>". Honest:
    // it's the real prior read; today's own readiness wins the instant tonight is scored.
    val anchorKey = carriedDay?.day ?: logicalDayKeyNow()
    val readiness = remember(days, anchorKey) { ReadinessEngine.evaluate(days, today = anchorKey) }
    if (readiness.level == ReadinessEngine.Level.INSUFFICIENT) return

    val overline = carriedDay?.let { carriedCaption(it.day).localized() }
        ?: uiString(R.string.today_should_you_push)
    SectionHeader(uiString(R.string.today_readiness_title), overline = overline)
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Headline row: level dot + headline, then the ACWR load read-out.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(readinessColor(readiness.level)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    readinessCopy(readiness.headlineRes),
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                readiness.acwr?.let { acwr ->
                    Text(
                        uiString(R.string.today_load_ratio, String.format(Locale.getDefault(), "%.2f", acwr)),
                        style = NoopType.captionNumber,
                        color = Palette.textTertiary,
                    )
                }
            }

            // #1405: mark this card as a DIFFERENT axis from the home Synthesis word (which bands the
            // Charge %). Stated at the point the two get compared, so "Primed" here vs "Steady" there
            // doesn't read as one value contradicting itself. Raw string, matching this card's copy.
            Text(
                uiString(R.string.today_training_read_explanation),
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )

            // Plain-English summary.
            Text(
                readinessCopy(readiness.summaryRes),
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )

            // Per-signal rows: flag dot + fixed-width label + detail.
            if (readiness.signals.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Palette.hairline),
                )
                readiness.signals.forEach { signal ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(flagColor(signal.flag)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            readinessCopy(signal.labelRes),
                            style = NoopType.caption,
                            color = Palette.textSecondary,
                            modifier = Modifier.width(104.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                readinessSignalDetail(signal),
                                style = NoopType.caption,
                                color = Palette.textTertiary,
                            )
                            // The numbers behind the read (e.g. "48 vs 55 ms"), as a small mono caption,                             // mirrors the macOS readiness card and the "load X.XX" numeric readout above.
                            signal.evidence?.let { evidence ->
                                Text(
                                    readinessEvidenceText(evidence),
                                    style = NoopType.captionNumber,
                                    color = Palette.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Level → color, mirroring TodayView.readinessColor. */
private fun readinessColor(level: ReadinessEngine.Level): Color = when (level) {
    ReadinessEngine.Level.PRIMED -> Palette.accent
    ReadinessEngine.Level.BALANCED -> Palette.statusPositive
    ReadinessEngine.Level.STRAINED -> Palette.statusWarning
    ReadinessEngine.Level.RUNDOWN -> Palette.metricRose
    ReadinessEngine.Level.INSUFFICIENT -> Palette.textTertiary
}

/** Flag → color, mirroring TodayView.flagColor. */
private fun flagColor(flag: ReadinessEngine.Flag): Color = when (flag) {
    ReadinessEngine.Flag.GOOD -> Palette.accent
    ReadinessEngine.Flag.NEUTRAL -> Palette.textTertiary
    ReadinessEngine.Flag.WATCH -> Palette.statusWarning
    ReadinessEngine.Flag.BAD -> Palette.metricRose
}

