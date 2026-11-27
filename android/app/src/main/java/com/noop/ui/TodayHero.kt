package com.noop.ui

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.analytics.StrainScorer
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/// Vital-detail keys the hero rings open (#1995).
///
/// These are the keys `VitalDetailScreen`'s `when` accepts, NOT the metricSeries keys. Rest is the
/// difference that bites: its detail key is "rest" while the series underneath it is
/// "sleep_performance", which is what iOS routes on. An unknown key falls through that `when` to a null
/// model and renders an empty screen, so the pairing is pinned by `HeroRingMetricKeyTest`.
internal const val HERO_CHARGE_METRIC_KEY = "recovery"
internal const val HERO_EFFORT_METRIC_KEY = "strain"
internal const val HERO_REST_METRIC_KEY = "rest"

// MARK: - Score hero row, three Charge / Effort / Rest score rings
//
// The ring-style Today hero: three equal daily-score GlowRings in Charge / Effort / Rest order, with a
// tappable label beneath each one and one card-level provenance badge aligned to the Rest ring's trailing
// edge. Same GlowRing primitive the Intelligence forecast card uses, with its spring arc + count-up anim.

@Composable
internal fun ScoreHeroRow(
    day: DailyMetric?,
    restScore: Double?,
    recoveryCalibration: Int?,
    lastScoredCharge: LastCharge? = null,
    effortScale: EffortScale,
    liveTodayStrain: Double? = null,
    // One card-level provenance label derived from the three REAL per-metric merge winners upstream.
    heroSourceLabel: String? = null,
    // A1 (#514/#706): tapping the Charge ring opens the breakdown sheet. A small chevron cue overlays the
    // ring's bottom edge INSIDE the ring frame, so it adds no stacked height (the #762 self-sizing parity).
    onChargeTap: (() -> Unit)? = null,
    // #1164: pending-sync state for today's Rest (strap has banked records not yet offloaded). When true
    // the Rest ring shows "Pending sync" instead of a provisional number that will change.
    restPendingSync: Boolean = false,
    // R10c: true until Today's Rest series has been read once. An empty series then means "not loaded", not "no
    // tracked night", so the orb shows neither a number nor the needs-a-tracked-night overlay for those seconds.
    restLoading: Boolean = false,
    // #1995: tapping the Effort or Rest ring opens that score's own vital detail, the same trend the
    // metric cards below already push, so the ring and its card can never lead to different screens.
    // Charge keeps `onChargeTap` (its breakdown sheet), which is richer than a trend and has no twin on
    // the iOS liquid Today. Defaulted to a no-op so the ring stays inert where a host does not bind it.
    onOpenMetric: ((String) -> Unit)? = null,
    // C-02 (Main.dc.html): the Rest orb opens Sleep. Null keeps the older Rest trend detail.
    onOpenSleep: (() -> Unit)? = null,
) {
    val recovery = day?.recovery
    // Prefer the live in-progress Effort for today, but never BELOW the day's already-earned strain
    // (#489/#506: a live under-read replaced today's real Effort with 0). The effective value drives the
    // gauge number AND the has-data / "No Data" branch, so the ring only reads "No Data" when neither
    // exists. Mirrors the iOS live-Effort gauge. (#402)
    // #1001: the shared rule, so this ring and the Key Metrics tile / chart badge cannot disagree.
    val strain = StrainScorer.effectiveEffort(live = liveTodayStrain, stored = day?.strain)
    // Effort honours the 0–100 / WHOOP-0–21 toggle (#313). The stored strain is on NOOP's 0–100 Effort
    // axis; render it on the user's selected scale so the arc and centre number match the app's Effort.
    val effortOutOf = if (effortScale == EffortScale.WHOOP) 21.0 else 100.0
    val effortVal = strain?.let { UnitFormatter.effortValue(it, effortScale) } ?: 0.0

    // Vessel path only: the vessels run LIVE (per-frame slosh + tilt) once the row has any real score to
    // show; a wholly empty/calibrating hero poses them static so a brand-new user's launch churn isn't
    // fighting live canvases (the Android equivalent of the iOS `dataLoaded` gate on HeroScoreCell).
    // LiquidVessel + the count-up both honour Reduce Motion internally, so this is purely a "don't animate
    // an empty hero" cost gate. A carried Charge counts as data (its dimmed vessel should slosh like the
    // Rest one). The ring rendering ignores it: GlowRing springs once to its target and then sits still,
    // so there is no per-frame cost to gate.
    val heroVesselsAnimated =
        recovery != null || strain != null || restScore != null || lastScoredCharge != null

    Box(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // iOS parity: the hero rings float DIRECTLY on the SCREEN-level day-cycle scene (the scaffold's
        // topBackground), not on any per-hero atmosphere or the old scenic indigo gradient, matching
        // TodayView, which moved the scene to a screen-level SceneScreenBackground and dropped the
        // per-hero scene/ScenicHeroBackground.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // 24 dp above the orbs (doc 4: 20–24). The sync chip and source row that once needed more room are gone.
                .padding(start = Ridge.space4, end = Ridge.space4, top = Ridge.space6, bottom = Ridge.space5),
        ) {
            // iOS parity (TodayView.scoreHeroRow): three EQUAL rings in CHARGE · EFFORT · REST order, no
            // enlarged centre, filling the width as one balanced row. Ring stroke 0.10 (WHOOP weight).
            val ringGap = 14.dp
            // #1502: ONE width for all three columns. Charge and Effort used to size to their own label
            // rows while Rest alone was pinned to `ring` (its box anchors the source badge), so the three
            // columns came out different widths — read as "the rings aren't the same size" — and Rest's
            // label had the least room of the three despite REST being the shortest word, which is why it
            // was the one ellipsising to "R…". Three of these plus two gaps is exactly maxWidth.
            val col = (maxWidth - ringGap * 2) / 3
            // The 90.dp floor can exceed `col` once the hero is under ~298.dp wide — a small phone, a
            // split-screen pane, a foldable's cover display. Unbounded, that overflowed the column; bounded,
            // the ring simply shrinks with its column instead of spilling out of it.
            val ring = ((maxWidth - ringGap * 2) / 3.1f).coerceIn(90.dp, 112.dp).coerceAtMost(col)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ringGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                // CHARGE, recovery 0–100, as a GlowRing with the value counting up in the centre. Honest
                // empty / calibrating overlay; badges its recovery winner.
                val effortRingTap = onOpenMetric?.let { open -> { open(HERO_EFFORT_METRIC_KEY) } }
                val restRingTap = onOpenSleep ?: onOpenMetric?.let { open -> { open(HERO_REST_METRIC_KEY) } }
                // C-04 carried state: a prior night's Charge shown on today is dimmed. RS-3d (owner, 26 Nov): no "Last night ·
                // date" under the orb; the Readiness card's detail line still dates the night it reads.
                val carriedCharge = lastScoredCharge?.takeIf {
                    recovery == null && shownCharge(recovery, recoveryCalibration != null, it) != null
                }
                HeroRingColumn(
                    modifier = Modifier.width(col),
                    domain = DomainTheme.Charge,
                    onRingTap = onChargeTap,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // #802: when today has no Charge yet but a prior night's value is carried, draw a
                        // DIMMED (0.8 opacity) REAL ring filled to the carried value, matching the Rest
                        // ring, rather than a bare number on an empty ring (which read as broken). Same
                        // diameter so the self-sizing hero row is untouched; the dim + the carried "Last
                        // night · <date>" caption mark it as carried, not today's fresh score. Mirrors iOS.
                        val carried = carriedCharge
                        if (carried != null) {
                            HeroScoreVessel(
                                // Dim WITHOUT an offscreen layer: Modifier.alpha composites through one, which
                                // clips the orb's outer glow to a visible square (C-04 finding).
                                modifier = Modifier.graphicsLayer {
                                    alpha = 0.8f
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                },
                                fraction = carried.value / 100.0,
                                value = carried.value,
                                tint = Palette.recoveryColor(carried.value),
                                liquid = RidgeLiquid.CHARGE,
                                diameter = ring,
                                showsValue = true,
                                animated = heroVesselsAnimated,
                            )
                        } else {
                            HeroScoreVessel(
                                fraction = (recovery ?: 0.0) / 100.0,
                                value = recovery ?: 0.0,
                                tint = Palette.recoveryColor(recovery ?: 0.0),
                                liquid = RidgeLiquid.CHARGE,
                                diameter = ring,
                                showsValue = recovery != null,
                                animated = heroVesselsAnimated,
                            )
                            // Empty ring + calibrating / no-data overlay (the carried case is above).
                            if (recovery == null) RingEmptyOverlay(recoveryCalibration, diameter = ring)
                        }
                        // No in-ring tap cue: the single tap affordance is the CHARGE-label chevron below
                        // the ring (HeroRingColumn), matching iOS where the in-ring cue was removed.
                    }
                }
                // EFFORT, strain on the gauge, on the user's selected scale, as a GlowRing.
                HeroRingColumn(
                    modifier = Modifier.width(col),
                    domain = DomainTheme.Effort,
                    onRingTap = effortRingTap,
                    ringTapLabel = uiString(
                        R.string.today_action_open_detail,
                        uiString(R.string.today_metric_effort),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HeroScoreVessel(
                            fraction = if (effortOutOf > 0) effortVal / effortOutOf else 0.0,
                            value = effortVal,
                            // The Effort colour world (blue), graded deep → bright by load, as the glass
                            // redesign draws it; the amber strain ramp stays on the Effort detail charts.
                            tint = Palette.sample(Palette.effortGradientStops, ((strain ?: 0.0) / 100.0).toFloat()),
                            liquid = RidgeLiquid.EFFORT,
                            diameter = ring,
                            showsValue = strain != null,
                            format = { if (effortScale == EffortScale.WHOOP) String.format(Locale.getDefault(), "%.1f", it) else it.toInt().toString() },
                            animated = heroVesselsAnimated,
                        )
                        if (strain == null) RingNoData(diameter = ring)
                    }
                }
                // REST, sleep composite 0–100. Its fixed-width box also anchors the card-level source badge:
                // the badge may grow leftward, but its trailing edge always matches the Rest ring.
                Box(modifier = Modifier.width(col)) {
                    HeroRingColumn(
                        modifier = Modifier.width(col),
                        domain = DomainTheme.Rest,
                        onRingTap = restRingTap,
                        ringTapLabel = uiString(
                            R.string.today_action_open_detail,
                            uiString(if (onOpenSleep != null) R.string.nav_sleep else R.string.today_metric_rest),
                        ),
                        // #1164/#2012: the provisional state is now said BESIDE the number instead of
                        // replacing it. See [HeroRingColumn]'s caption for why.
                        caption = if (restPendingSync) {
                            uiString(R.string.l10n_today_screen_pending_sync_cbe01f9e)
                        } else {
                            null
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            HeroScoreVessel(
                                fraction = (restScore ?: 0.0) / 100.0,
                                value = restScore ?: 0.0,
                                tint = Palette.recoveryColor(restScore ?: 0.0),
                                liquid = RidgeLiquid.REST,
                                diameter = ring,
                                showsValue = restScore != null,
                                animated = heroVesselsAnimated,
                            )
                            if (restScore == null && !restLoading) {
                                // #898: an aggregate-import user (a daily HRV/RHR import, no in-bed session) gets a
                                // Charge from WatchRecovery but NO sleep_performance, so Rest used to read a bare
                                // "No Data" next to a lit Charge , reading as broken. When a Charge IS present for the
                                // day but Rest is absent, say WHY honestly ("Needs a tracked night") instead. We do
                                // NOT fabricate a Rest number , an aggregate genuinely has no scored night. A day with
                                // no Charge either (truly empty) keeps the plain "No Data". Mirrors iOS restRing.
                                if (recovery != null) RingNeedsTrackedNight() else RingNoData(diameter = ring)
                            }
                        }
                    }
                    if (heroSourceLabel != null) {
                        SourceBadge(
                            text = heroSourceLabel,
                            // #1160: the hero is theme-aware now, so its badge uses the flip-able text token.
                            tint = Palette.textSecondary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // Measure the full label even when it is wider than the Rest ring, then
                                // let it overflow left while preserving the ring-aligned trailing edge.
                                .wrapContentWidth(unbounded = true, align = Alignment.End)
                                // #1502: the box is now the shared column width rather than the ring's,
                                // so inset by the slack to keep the badge's trailing edge on the RING —
                                // the alignment this anchor exists for.
                                .padding(end = ((col - ring) / 2).coerceAtLeast(0.dp))
                                // Match iOS: centre the pill on the card border, aligned with the Rest ring.
                                .offset(y = -(Metrics.space16 + Metrics.sourceBadgeHeight / 2))
                                .semantics { contentDescription = uiString(R.string.l10n_today_screen_source_herosourcelabel_d3363687, heroSourceLabel) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One hero ring column: the orb, with its UPPERCASE domain label beneath it (C-02: plain, no chevron). Provenance belongs to the whole hero
 * card and is rendered once by [ScoreHeroRow], so this column only owns score content and navigation.
 */
@Composable
private fun HeroRingColumn(
    domain: DomainTheme,
    modifier: Modifier = Modifier,
    // A1: when non-null the ring is tappable. Charge opens the breakdown sheet; Effort and Rest open
    // their metric detail (#1995). The chevron cue is overlaid by the caller INSIDE the ring box so it
    // adds no stacked height (#762 self-sizing parity).
    //
    // This wires the tap for the ring's SURROUND and for TalkBack (which activates the semantics node
    // directly). A touch that lands on the ring itself never reaches here: see the onTap the callers
    // hand to HeroScoreVessel.
    onRingTap: (() -> Unit)? = null,
    // Charge's ring opens a breakdown, so "see what shaped" is honest there. Effort and Rest open a
    // trend detail instead, so they override it rather than announce a breakdown they do not show.
    ringTapLabel: String? = null,
    // #2012: an optional one-line note under the domain label — currently Rest's "Pending sync".
    //
    // It lives HERE, under the label, rather than over the ring, for two reasons. It cannot cover the
    // score, which is what made the old overlay hide a number the user had every right to see. And it is
    // laid out at the COLUMN's width rather than the ring's, so it has room to render: the overlay was
    // measured against the circle and ellipsised its own explanation mid-word ("strap history still o...")
    // while spilling past the ring's edge.
    caption: String? = null,
    ring: @Composable () -> Unit,
) {
    val domainLabel = uiString(
        when (domain) {
            DomainTheme.Charge -> R.string.today_metric_charge
            DomainTheme.Effort -> R.string.today_metric_effort
            DomainTheme.Rest -> R.string.today_metric_rest
            DomainTheme.Stress -> R.string.today_card_stress
        },
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onRingTap != null) {
            // liquidPress on the tappable Charge ring so it settles inward on press. Same interactionSource
            // on the clickable + press. No CircleShape clip — the GlowRing is already round, and the clip
            // cut the outer bloom off at the arc edge (visible as a hard ring boundary on the hero row).
            val ringInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .liquidPress(ringInteraction)
                    .clickable(
                        interactionSource = ringInteraction,
                        indication = null,
                        onClickLabel = ringTapLabel
                            ?: uiString(R.string.today_action_see_what_shaped, domainLabel),
                        onClick = onRingTap,
                    ),
            ) { ring() }
        } else {
            ring()
        }
        // C-02 (Main.dc.html): a plain label under the orb, 11 sp semibold, 0.18 em tracking, ink-muted, with no
        // chevron and no tap of its own; the orb above is the control. The full column width keeps longer
        // translations (for example ERHOLUNG) centred without clipping.
        AutoSizeValue(
            text = domainLabel.uppercase(),
            style = RidgeType.orbLabel,
            color = Ridge.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            minScale = 0.7f,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (caption != null) {
            // A plain wrapping Text, NOT AutoSizeValue: that one is maxLines = 1 with softWrap off and an
            // ellipsis, so a longer translation would shrink to its floor and then cut itself off
            // mid-word. Truncating its own explanation is precisely what the old in-ring overlay did and
            // what moving the caption out here was meant to stop, so it must not come back through the
            // component. Two lines at the column's width holds every locale we ship.
            Text(
                text = caption,
                style = RidgeType.bodySm,
                color = Ridge.inkMuted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.space2),
            )
        }
    }
}

/**
 * One hero score, drawn as whichever gauge the Today preference selects.
 *
 * DEFAULT, and what #2311 shipped: a [GlowRing] — the ring-style gauge shared with the Intelligence
 * forecast card, the same crisp-arc + count-up primitive the rest of the app already uses: a
 * 10%-of-diameter stroke, spring arc from 12 o'clock, and a tween count-up number at `diameter * 0.36`
 * Bold (the [glowRingCenterTextStyle] house numeral).
 *
 * OFF (the default): the Noop Ridge [LiquidOrb] — a glass sphere filled to [fraction] in the metric's
 * fixed liquid, with the value centred on it (cream above the surface, deep ink below it).
 *
 * Both renderings take the SAME fraction and value, so whichever one is on agrees with every other
 * surface that shows the metric. The ring keeps the value-sampled [tint]; the orb uses [liquid]. The caller overlays the
 * calibrating / No-Data / Needs-a-Tracked-Night text when [showsValue] is false, for either gauge.
 *
 * The preference is read on entry via [remember], the convention every other Today-screen display toggle
 * follows (day-cycle background, sky behind cards, hydration). Settings is its own NavHost destination, so
 * Today leaves composition while it is open and reads the pref again on the way back: a flip is live the
 * next time Today is opened, which is the first moment it could be seen anyway.
 */
@Composable
private fun HeroScoreVessel(
    fraction: Double,
    value: Double,
    tint: Color,
    // The Noop Ridge liquid this score always pours in: Charge gold, Effort blue, Rest mint.
    liquid: RidgeLiquid,
    diameter: Dp,
    modifier: Modifier = Modifier,
    showsValue: Boolean = true,
    format: (Double) -> String = { it.roundToInt().toString() },
    // Orb only: whether the liquid sloshes. The caller passes its empty-hero cost gate, so a brand-new
    // user's launch churn isn't fighting live canvases. GlowRing needs no equivalent.
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val ringGauges = remember { NoopPrefs.todayRingGauges(context) }
    if (ringGauges) {
        GlowRing(
            fraction = fraction.coerceIn(0.0, 1.0).toFloat(),
            value = value,
            color = tint,
            diameter = diameter,
            lineWidth = diameter * 0.10f,
            modifier = modifier,
            showsLabel = showsValue,
            format = format,
        )
    } else {
        // The Noop Ridge LiquidOrb: the fill level IS the score, in the metric's own liquid. It takes no
        // pointer input, so a tap lands on HeroRingColumn's clickable around it.
        LiquidOrb(
            fraction = fraction,
            display = if (showsValue) format(value) else "",
            liquid = liquid,
            diameter = diameter,
            animated = animated,
            // FAB-2: the glassy Fable restyle is for Today's three main orbs only; the battery orb stays classic.
            glass = true,
            modifier = modifier.clearAndSetSemantics {},
        )
    }
}

/** Honest overlay shown over the Charge ring when today's recovery is null: either the calibrating count
 *  or No data. The carried last-scored Charge case is NOT handled here anymore: it's intercepted earlier
 *  and drawn as a dimmed FILLED ring in the carried branch (matching iOS chargeRing), so this overlay only
 *  covers the calibrating and no-data cases. Mirrors iOS TodayView.ringEmptyOverlay. */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun RingEmptyOverlay(
    calibratingNights: Int?,
    diameter: Dp,
) {
    if (calibratingNights != null) {
        // AutoSizeValue shrink-to-fit + the centring Column, same as RingNeedsTrackedNight (#1168): a longer
        // localized "N of M nights" would otherwise fill the narrow ring and left-align/clip like the Rest tile.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AutoSizeValue(
                uiString(R.string.l10n_today_screen_calibrating_37c2c9bd),
                style = RidgeType.label,
                color = Ridge.inkMuted,
                minScale = 0.7f,
            )
            AutoSizeValue(
                uiString(R.string.l10n_today_screen_calibratingnights_of_baselines_minnightsseed_3b76e55c, calibratingNights, Baselines.minNightsSeed),
                style = RidgeType.bodySm,
                color = Ridge.inkMuted,
            )
        }
    } else {
        RingNoData(diameter = diameter)
    }
}

@Composable
private fun RingNoData(diameter: Dp) {
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Text(
            text = uiString(R.string.today_no_data),
            style = RidgeType.label,
            color = Ridge.inkMuted,
            modifier = Modifier.width(diameter * 0.78f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

/** #898: the Rest ring's overlay when a Charge exists for the day but there's no scored sleep (the
 *  aggregate-import case , a daily HRV/RHR import carries no in-bed session). Says WHY Rest is blank
 *  instead of a bare "No Data", without fabricating a number. Mirrors iOS restRing's needs-a-night branch. */
@Composable
private fun RingNeedsTrackedNight() {
    // AutoSizeValue (not plain Text): the subtext "needs a tracked night" is wider than the narrow ring,
    // so a maxLines=1 Text filled the width and left-aligned + clipped the tail (#1168). Shrink-to-fit and
    // let the centering Column place the content-sized line — the Android mirror of iOS ringNeedsTrackedNight's
    // .minimumScaleFactor + centred VStack.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AutoSizeValue(
            uiString(R.string.l10n_today_screen_calibrating_37c2c9bd),
            style = RidgeType.label,
            color = Ridge.inkMuted,
            minScale = 0.7f,
        )
        AutoSizeValue(
            uiString(R.string.l10n_today_screen_needs_a_tracked_night_ccfd532a),
            style = RidgeType.bodySm,
            color = Ridge.inkMuted,
        )
    }
}

/** #1164: the Rest ring's overlay when today's score is provisional because the strap still has banked
 *  records not yet offloaded. Shows "Pending sync" instead of a number that will change once the full
 *  night lands and `analyzeRecent` re-scores it. Mirrors iOS ringPendingSync. */
// MARK: - Hero vitals metric rows, HRV / Resting HR / Respiratory, re-homed below the ring hero
//
// The WHOOP-style redesign (#23) dropped the big gold RecoveryRing hero that used to carry these; the
// three vitals now read directly below the three-ring hero + Synthesis card. [HeroMetricRows] is the
// README "Metric row" card; the SOLID/CALIBRATING pill + Synthesis insight moved into [SynthesisHeroCard].

/**
 * Whether the Recovery Vitals card should explain its blanks with the HR-only note (#1801).
 *
 * Reads the flag from the SAME row the shown vitals came from. [carriedFromVitals] already decides that
 * for the date caption, so reusing it keeps the note and the numbers describing ONE night;
 * `day?.sleepHrOnly ?: vitalsDay?.sleepHrOnly` would instead fall through whenever today's flag is merely
 * unknown — every row scored before v36 — and caption today's blanks with a different night's staging.
 *
 * Gated on something actually being blank so a night that kept its vitals stays quiet, and on `== true`
 * so the tri-state reads correctly: null means "not known", which earns no claim either way.
 */
internal fun showsHrOnlyNote(
    day: DailyMetric?,
    vitalsDay: DailyMetric?,
    carriedFromVitals: Boolean,
    hrv: Double?,
    rhr: Int?,
): Boolean {
    val source = if (carriedFromVitals) vitalsDay else day
    return source?.sleepHrOnly == true && (hrv == null || rhr == null)
}

/** The three hero vitals as README metric rows, HRV (teal) · Resting HR (rose) · Respiratory (blue).
 *  Reads PER-FIELD today-first with a recovery-INDEPENDENT vitals carry ([vitalsDay]) as the fallback
 *  (#543 follow-up), so a night whose recovery was nulled post-update still shows its OWN preserved HRV /
 *  RHR / respiratory rather than an older recovery-scored day's numbers (or "No Data"). This aligns the
 *  card to the Key-Metrics tiles, which already read per-field. Each row still falls through to "No Data"
 *  for a vital neither today nor the carry supplies. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun HeroMetricRows(
    day: DailyMetric?,
    carriedDay: DailyMetric? = null,
    vitalsDay: DailyMetric? = null,
    // #706/#684: the same `vital_detail/<key>` trends the HRV / Resting HR / Respiratory dashboard cards
    // open. These three rows show the SAME metrics and had no way through, so the summary card was the one
    // place on Today where a metric was a dead end. Keys come from `dashboardCardMetricKey`, so the two
    // surfaces cannot drift onto different destinations for the same vital.
    onOpenMetric: (String) -> Unit = {},
) {
    // Per-field, today-first: today's own value wins; the vitals carry only fills a field today lacks.
    val hrv = day?.avgHrv ?: vitalsDay?.avgHrv
    val rhr = day?.restingHr ?: vitalsDay?.restingHr
    val resp = day?.respRateBpm ?: vitalsDay?.respRateBpm
    // The caption reflects the row the shown vitals actually came from: if today supplied ANY of them the
    // values are today's own, so don't stamp them as a prior "Last night · <date>"; only when EVERY shown
    // vital is carried do we stamp the carry's date (relabelled "Latest sleep · <date>" when weeks-old).
    val carriedFromVitals = day?.avgHrv == null && day?.restingHr == null && day?.respRateBpm == null &&
        (hrv != null || rhr != null || resp != null) && vitalsDay != null
    // #1801: why the two blanks below are blank. Read from the SAME row the shown vitals came from —
    // `carriedFromVitals` already decides that for the date caption, so reusing it keeps the note and the
    // values describing one night. `day?.sleepHrOnly ?: vitalsDay?.sleepHrOnly` would instead fall through
    // on a pre-v36 row whose flag is merely unknown, and explain a different night's numbers.
    // Gated on something actually being blank, so a night that recovered its vitals stays quiet.
    val hrOnlyNight = showsHrOnlyNote(day, vitalsDay, carriedFromVitals, hrv, rhr)
    // iOS `recoveryVitalsSection`: a frosted card with a "RECOVERY VITALS" header + a "last night · <date>"
    // on the right, then three `vitalRow`s (26dp mini LIQUID RING + label + value). NoopCard supplies the
    // same neutral surfaceRaised + hairline as iOS's frosted card. Inner spacing 12, matching iOS.
    NoopCard(padding = Metrics.space16) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Metrics.space12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(uiString(R.string.today_section_recovery_vitals), modifier = Modifier.weight(1f))
                // iOS `lastNightLine` — today's own "Last night · <date>" unless the shown vitals are a carry.
                Text(
                    if (carriedFromVitals) carriedCaption(vitalsDay!!.day).localized() else heroVitalsLastNightLine(),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
            HeroVitalRow(
                label = uiString(R.string.l10n_today_screen_heart_rate_variability_a137586d),
                value = hrv?.let { "${it.roundToInt()} ms" } ?: NO_DATA,
                tint = Palette.metricCyan,
                fraction = hrv?.let { (it / 120.0).coerceIn(0.0, 1.0) },
                metricKey = dashboardCardMetricKey(DashboardCard.HRV),
                onOpenMetric = onOpenMetric,
            )
            HeroVitalRow(
                label = uiString(R.string.l10n_today_screen_resting_heart_rate_348928d6),
                value = rhr?.let { "$it bpm" } ?: NO_DATA,
                tint = Palette.metricRose,
                fraction = rhr?.let { (it / 100.0).coerceIn(0.0, 1.0) },
                metricKey = dashboardCardMetricKey(DashboardCard.RESTING_HR),
                onOpenMetric = onOpenMetric,
            )
            HeroVitalRow(
                label = uiString(R.string.l10n_today_screen_breaths_per_minute_2b197c54),
                value = resp?.let { String.format(Locale.getDefault(), "%.1f rpm", it) } ?: NO_DATA,
                tint = Palette.accent,
                fraction = resp?.let { (it / 24.0).coerceIn(0.0, 1.0) },
                metricKey = dashboardCardMetricKey(DashboardCard.RESPIRATORY),
                onOpenMetric = onOpenMetric,
            )
            if (hrOnlyNight) {
                Text(
                    uiString(R.string.l10n_today_screen_this_night_was_staged_from_heart_rate_4d0f1f9b),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/** iOS `lastNightLine` — "Last night · <date>" where <date> is yesterday in "d MMM" form. */
@Composable
private fun heroVitalsLastNightLine(): String {
    val d = LocalDate.now().minusDays(1)
    return uiString(R.string.today_last_night_date, d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))
}

/** One iOS `vitalRow`: a 26dp mini liquid RING filled to [fraction] in [tint], the label (subhead,
 *  secondary), a spacer, and the value (number 15, primary). Replaces the old flat-Material-icon row. */
@Composable
private fun HeroVitalRow(
    label: String,
    value: String,
    tint: Color,
    fraction: Double?,
    // The metric-detail key this row opens, from `dashboardCardMetricKey` so the row and its dashboard-card
    // twin cannot drift onto different trends for the same vital. NULL means the row simply does not
    // navigate: it loses the tap AND the chevron together, which is the honest degradation. An earlier
    // draft asserted the key was present, which would have turned a missing destination into a crash on
    // tap - a worse outcome than a row that quietly does not move.
    metricKey: String? = null,
    onOpenMetric: (String) -> Unit = {},
) {
    val onClick: (() -> Unit)? = metricKey?.let { key -> { onOpenMetric(key) } }
    val hasValue = value != NO_DATA
    val displayValue = localizedMetricValue(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .semantics { contentDescription = uiString(R.string.l10n_today_screen_label_value_b781d590, label, displayValue) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space12),
    ) {
        // Ring or vessel per the Today gauge preference; both are static here.
        val gaugeCtx = LocalContext.current
        if (remember { NoopPrefs.todayRingGauges(gaugeCtx) }) {
            GlowRing(
                fraction = (fraction ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
                value = 0.0,
                color = tint,
                diameter = 26.dp,
                lineWidth = 2.6.dp,
                showsLabel = false,
            )
        } else {
            LiquidVessel(
                value = fraction,
                tint = tint,
                animated = false,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(label, style = NoopType.subhead, color = Palette.textSecondary, modifier = Modifier.weight(1f))
        Text(
            displayValue,
            style = NoopType.number(15f),
            color = if (hasValue) Palette.textPrimary else Palette.textTertiary,
        )
        // Only when the row actually goes somewhere. `dashboardCardDestination` states the rule this
        // follows - every card resolves to a destination, so the chevron is always honest (#706/#684) -
        // and the inverse is what was wrong here: three rows that go somewhere and did not say so.
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Palette.textTertiary,
                modifier = Modifier.size(Metrics.iconSmall),
            )
        }
    }
}

// MARK: - "Your cards" dashboard (WHOOP "My Dashboard"), iOS yourCardsSection parity
//
// A persisted, reorderable selection of metric cards surfaced on Today as flat WHOOP metric ROWS. The
// section header carries the "Your cards" overline + a right-aligned BLUE "CUSTOMISE" text action; each row
// is a leading tinted icon tile + UPPERCASE tracked label over a grey baseline caption on the left, and the
// big white value + small unit + chevron on the right. A card with no value yet renders a dash rather than
// vanishing. Mirrors iOS TodayView.yourCardsSection / pinnedCardRow / dashboardValue / dashboardTint.

/** Shared Today section edit affordance. The 48dp box keeps the whole control easy to tap while its
 *  visible content stays pinned to the overline instead of centring against a two-line header. */
@Composable
internal fun TodayEditAction(
    contentDescription: String,
    onClick: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = Metrics.space12),
    ) {
        Row(
            modifier = Modifier.align(contentAlignment),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // B2-1a-4 (Main.dc.html "Edit"): sentence case, 13/18 semibold in the brand mint, a 16 dp sliders glyph 5 dp before it.
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = Ridge.brand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                uiString(R.string.l10n_today_screen_edit_5301648d),
                style = RidgeType.body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = Ridge.brand,
            )
        }
    }
}

