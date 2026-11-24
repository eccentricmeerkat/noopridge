package com.noop.ui

import com.noop.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.RecoveryForecast
import com.noop.analytics.RecoveryForecaster
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Intelligence — NOOP's own Charge / Effort / Rest scores, presented with the
 * WHOOP-model explanation so the read-out is legible rather than a black box.
 *
 * Ports macOS Strand/Screens/IntelligenceView.swift. The macOS build runs an
 * on-device IntelligenceEngine that recomputes these scores from the strap's raw
 * streams (HR, R-R, accelerometer) using the WHOOP model shape. That raw-compute
 * port is later work on Android; until it lands this screen reads the cached
 * `DailyMetric` values the strap/store already provide and shows the same model
 * explainer + per-day breakdown — matching the macOS sparse-data contract of
 * surfacing real data with an honest note, never a fabricated score.
 */
@Composable
fun IntelligenceScreen(vm: AppViewModel, ridgeHeader: (@Composable () -> Unit)? = null) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    // PERF (#scroll-jank): the BLE live state ticks ~1Hz. This screen reads `live` ONLY for the
    // "syncing history" note (backfilling + the chunk count), so reading the whole `live` object at
    // body scope recomposed the entire Intelligence screen on every HR tick. Collapse it to the two
    // fields the note needs via a structural-equality snapshot: a 72→73 bpm tick produces an EQUAL
    // snapshot and the body is NOT recomposed; it only recomposes when the backfilling state / chunk
    // count actually changes. Mirrors the shipped Today liveSnap fix. Appearance-preserving.
    val live by vm.live.collectAsStateWithLifecycle()
    val backfillNote by remember {
        derivedStateOf {
            val s = live
            if (s.backfilling) s.syncChunksThisSession else null
        }
    }

    // Effort display scale (#268) — routes every Effort value/label on this screen. Display-only.
    val effortScale = UnitPrefs.effortScale(LocalContext.current)

    // Newest first for the per-day list (macOS ForEach renders most-recent at top).
    val ordered = remember(days) { days.reversed() }

    // Evening forecast of tomorrow-morning Charge from tonight's known levers. `days` is
    // already OLDEST→NEWEST (what the forecaster wants); today's Effort is the newest day.
    // null (and the card hidden) until there are enough scored nights to anchor honestly.
    // SQ9 S3 (the board's "If I sleep"): the forecast defaults to the typical sleep, as before; a chosen length (null =
    // typical) re-runs the same forecaster with that many hours, so only the user's pick moves the number.
    val typicalSleepHours = remember(days) {
        val sleeps = days.mapNotNull { it.totalSleepMin }
        if (sleeps.isEmpty()) RecoveryForecaster.defaultNeedHours else (sleeps.sum() / sleeps.size) / 60.0
    }
    var chosenSleepHours by remember { mutableStateOf<Double?>(null) }
    val forecast = remember(days, chosenSleepHours, typicalSleepHours) {
        val charge = days.mapNotNull { it.recovery }
        val effort = days.mapNotNull { it.strain }
        RecoveryForecaster.forecast(
            recentCharge = charge,
            recentEffort = effort,
            todayEffort = ordered.firstOrNull()?.strain,
            plannedSleepHours = chosenSleepHours ?: typicalSleepHours,
        )
    }
    val sleepOptions = remember(typicalSleepHours) { sleepScenarioHours(typicalSleepHours) }

    // Range + filtered day list are hoisted ABOVE the lazy scaffold: these are @Composable
    // state hooks (remember), which can't run inside the LazyListScope content lambda. The
    // filtering is a cheap in-memory predicate; the freeze (#345) was the eager BUILDING of
    // 800+ day cards, which the LazyColumn below now defers to what's on screen.
    var range by remember { mutableStateOf(IntelRange.Month) }
    val filtered = remember(ordered, range) {
        val n = range.days ?: return@remember ordered
        val cutoff = LocalDate.now().minusDays((n - 1).toLong()).toString()
        ordered.filter { it.day >= cutoff }
    }

    // R7b-1 (G-02): under the Ridge shell this screen is one mode of the Insights page: the shared header, Ridge
    // glass cards, the page backdrop.
    CompositionLocalProvider(LocalGlassCards provides (ridgeHeader != null)) {
    LazyScreenScaffold(
        topBackground = if (ridgeHeader != null) screenBackdropSlot(NoopPrefs.showDayCycleBackground(LocalContext.current), false) else null,

        title = if (ridgeHeader != null) null else uiString(R.string.l10n_intelligence_screen_intelligence_c698f940),
        subtitle = if (ridgeHeader != null) null else "Charge, effort and rest - scored with the model, explained in plain terms.",
    ) {
        ridgeHeader?.let { h -> item { h() } }
        if (ridgeHeader != null) {
            // B2 Hub2a: the board's Intelligence (Insights.dc.html): one forecast card, a plain "How this works" card, the
            // Charge model weights, then "By day" as one card of rows.
            forecast?.let { f ->
                item {
                    RidgeForecastCard(f, sleepOptions, chosenSleepHours ?: sleepOptions.typical) { h ->
                        chosenSleepHours = if (h == sleepOptions.typical) null else h
                    }
                }
            }
            item { com.noop.ui.RidgeAboutCard(stringResource(R.string.intel_ridge_how), stringResource(R.string.intel_ridge_how_body, UnitFormatter.effortScaleMax(effortScale))) }
            item { RidgeWeightsCard(effortScale) }
            if (ordered.isEmpty()) {
                item {
                    if (backfillNote != null) SyncingHistoryNote(chunks = backfillNote!!)
                    EmptyNote()
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                com.noop.ui.ridge.RidgeEyebrow(stringResource(R.string.intel_ridge_recent))
                                Text(stringResource(R.string.intel_ridge_by_day), style = RidgeType.titleSm.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Ridge.ink)
                            }
                            Text(uiPlural(R.plurals.intelligence_days_count, filtered.size, filtered.size), style = TRN_LEGEND, color = Ridge.inkMuted)
                        }
                        Box(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                            com.noop.ui.ridge.RidgeSegmentedPills(
                                items = IntelRange.entries.toList(), selected = range, onSelect = { range = it },
                                label = { it.label }, fill = false,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { RidgeDayNote(uiString(R.string.l10n_intelligence_screen_no_scored_days_in_this_window_03f1165b)) }
                } else {
                    // One card per week of rows, built lazily, so a long history is never composed at once.
                    itemsIndexed(filtered.chunked(7), key = { _, week -> week.first().day }) { _, week ->
                        RidgeDayCard(week, effortScale)
                    }
                }
            }
            return@LazyScreenScaffold
        }
        item {
            forecast?.let {
                ForecastCard(
                    it, sleepOptions = sleepOptions, chosenHours = chosenSleepHours ?: sleepOptions.typical,
                    onChoose = { h -> chosenSleepHours = if (h == sleepOptions.typical) null else h },
                )
            }
        }
        item { ExplainerCard(effortScale) }
        item { ModelBreakdownCard(effortScale) }

        if (ordered.isEmpty()) {
            item {
                // While the strap is mid-offload, say so — an empty list reads as final otherwise (#77).
                if (backfillNote != null) SyncingHistoryNote(chunks = backfillNote!!)
                EmptyNote()
            }
        } else {
            item {
                // Header row: section label left, range control right. Lets you narrow the
                // per-day list to a recent window (lexicographic YYYY-MM-DD compare == chronological).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.gap),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Overline("Recent")
                        Text(uiString(R.string.l10n_intelligence_screen_by_day_2e5d14ca), style = NoopType.title2, color = Palette.textPrimary)
                    }
                    SegmentedPillControl(
                        items = IntelRange.entries.toList(),
                        selection = range,
                        label = { it.label },
                        onSelect = { range = it },
                    )
                }
            }
            item {
                Text(
                    uiPlural(R.plurals.intelligence_days_count, filtered.size, filtered.size),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }

            if (filtered.isEmpty()) {
                item {
                    NoopCard(padding = 18.dp) {
                        Text(
                            uiString(R.string.l10n_intelligence_screen_no_scored_days_in_this_window_03f1165b),
                            style = NoopType.subhead,
                            color = Palette.textSecondary,
                        )
                    }
                }
            } else {
                // The lazily-built day list — only the visible cards are composed, so even an
                // 800+ day "ALL" range no longer blocks the main thread. Keyed by day so scroll
                // position and recomposition stay stable as the range changes.
                items(filtered, key = { it.day }) { day -> DayCard(day, effortScale) }
            }
        }
    }
    }
}

// MARK: - Tomorrow's Charge forecast hero (ported from IntelligenceView.forecastCard)
//
// An evening ESTIMATE of tomorrow-morning Charge from tonight's known levers —
// today's Effort vs your norm, your typical sleep, and the recent recovery baseline.
// Design Reset (Today parity): a clean flat Charge ring on an opaque flat card — a
// [GlowRing] (bloom off) on a [NoopCard] surfaceRaised surface, NOT a layered bevel
// gauge floated over a scenic backdrop — with the ± band + state word beneath it and
// the plain-English read-out below. Labelled an estimate; the real Charge is scored
// from tomorrow's HRV. The number, band and copy are unchanged.

@Composable
private fun ForecastCard(f: RecoveryForecast, sleepOptions: SleepScenarios, chosenHours: Double, onChoose: (Double) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val charge = f.charge.roundToInt()
    val band = f.band.roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader("Tomorrow's Charge", overline = "Evening forecast", trailing = "Estimate")
        NoopCard(padding = 20.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // SQ9 S3: the same gauge Today's hero draws (HeroScoreVessel): the Ridge LiquidOrb, glass style, filled to the
                    // forecast in the Charge liquid; the ring only when the "ring gauges" setting is on, as on Today.
                    val ringGauges = remember { NoopPrefs.todayRingGauges(context) }
                    if (ridgeShell && !ringGauges) {
                        LiquidOrb(
                            fraction = (f.charge / 100.0).coerceIn(0.0, 1.0),
                            display = charge.toString(),
                            liquid = RidgeLiquid.CHARGE,
                            diameter = 168.dp,
                            glass = true,
                        )
                    } else GlowRing(
                        fraction = (f.charge / 100.0).coerceIn(0.0, 1.0).toFloat(),
                        value = f.charge,
                        color = Palette.recoveryColor(f.charge),
                        diameter = 168.dp,
                        lineWidth = 168.dp * 0.10f,
                    )
                    Text(
                        uiString(R.string.l10n_intelligence_screen_band_ec8a8993, band),
                        style = NoopType.captionNumber,
                        color = Palette.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        Palette.recoveryState(f.charge),
                        style = NoopType.overline,
                        color = Palette.recoveryColor(f.charge),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Overline(stringResource(R.string.intelligence_if_i_sleep))
                    if (ridgeShell) {
                        com.noop.ui.ridge.RidgeSegmentedPills(
                            items = sleepOptions.hours, selected = chosenHours, onSelect = onChoose,
                            label = { sleepHoursLabel(it) }, fill = true,
                        )
                    } else {
                        SegmentedPillControl(
                            items = sleepOptions.hours, selection = chosenHours, label = { sleepHoursLabel(it) }, onSelect = onChoose,
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        // R9d: one sentence per language. Gluing English "8h tonight." to a translated fragment
                        // dropped the space ("about8h") and left the Chinese sentence trailing English.
                        uiString(R.string.intelligence_forecast_sentence, charge, band, sleepHoursLabel(f.plannedSleepHours)),
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                    Text(
                        // R9d: one sentence per language (the fragment + English read "14-nightrecovery").
                        uiString(R.string.intelligence_forecast_footnote, f.nights),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
            }
        }
    }
}

/** The sleep lengths the forecast can be asked about: [hours] ascending, always including the [typical] length. */
internal data class SleepScenarios(val typical: Double, val hours: List<Double>)

/** 6 h, 7 h, 8 h and 9 h plus the typical sleep (rounded to the nearest 30 min), sorted and without a repeat. */
internal fun sleepScenarioHours(typicalHours: Double): SleepScenarios {
    val typical = (typicalHours * 2).roundToInt() / 2.0
    return SleepScenarios(typical, (listOf(6.0, 7.0, 8.0, 9.0) + typical).distinct().sorted())
}

/** "~7h" / "~7h 30m" for the planned-sleep assumption (rounded to the nearest 30 min). */
private fun sleepHoursLabel(hours: Double): String {
    val half = (hours * 2).roundToInt() / 2.0
    val h = half.toInt()
    val m = ((half - h) * 60).roundToInt()
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

// MARK: - Explainer (ported from IntelligenceView.explainerCard)

@Composable
private fun ExplainerCard(effortScale: EffortScale) {
    NoopCard(padding = 20.dp, tint = Palette.chargeColor) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Palette.chargeColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(uiString(R.string.l10n_intelligence_screen_how_this_works_b895a8c3), style = NoopType.headline, color = Palette.textPrimary)
            }
            Text(
                uiString(R.string.l10n_intelligence_screen_charge_weighs_your_heart_rate_variability_026745e6) +
                    " (~55%), resting heart rate (~20%), rest quality (~15%), respiration (~5%) " +
                    "and skin-temperature deviation (~5%). Effort is a 0 - ${UnitFormatter.effortScaleMax(effortScale)} " +
                    "cardiovascular load from time spent in each heart-rate zone. Rest is staged " +
                    "from movement and heart rate. The full on-device recompute from the strap's raw " +
                    "streams is a later port; the scores below are read from each day's cached metrics.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
        }
    }
}

// MARK: - Empty note

@Composable
private fun EmptyNote() {
    NoopCard(padding = 20.dp, tint = Palette.chargeColor) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Palette.chargeColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                uiString(R.string.l10n_intelligence_screen_no_scored_days_yet_sync_your_cdd47684) +
                    " scored once a day's data is in. Charge needs about four nights of sleep to learn your " +
                    "baseline (you'll see \"Calibrating\" until then), and keeps sharpening over your first " +
                    "couple of weeks. On a WHOOP 5 or MG the strap banks little history, so that night count " +
                    "can climb slowly or sit at 0 of 4 until you have worn it across a few nights. That's " +
                    "its sync limit, not a fault. Import your WHOOP export to skip the wait.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
        }
    }
}

// MARK: - Model weighting breakdown
//
// Makes the Charge formula concrete: the five weighted inputs plus the 0–100
// Effort scale. Pure presentation of the model the macOS engine uses — no per-day
// data, so it's always legible even before any day is scored.

@Composable
private fun ModelBreakdownCard(effortScale: EffortScale) {
    NoopCard(padding = 20.dp, tint = Palette.chargeColor) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Overline("Charge model")
            WeightRow("Heart-rate variability", "~55%", 0.55f, Palette.metricPurple)
            WeightRow("Resting heart rate", "~20%", 0.20f, Palette.metricRose)
            WeightRow("Rest quality", "~15%", 0.15f, Palette.metricCyan)
            WeightRow("Respiration", "~5%", 0.05f, Palette.accent)
            WeightRow("Skin-temperature deviation", "~5%", 0.05f, Palette.metricAmber)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    uiString(R.string.l10n_intelligence_screen_effort_8c974bc6),
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    uiString(R.string.l10n_intelligence_screen_0_unitformatter_effortscalemax_effortscale_scale_b5c43f56, UnitFormatter.effortScaleMax(effortScale)),
                    style = NoopType.captionNumber,
                    color = Palette.effortColor,
                )
            }
        }
    }
}

@Composable
private fun WeightRow(label: String, percent: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = NoopType.subhead,
                color = Palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(percent, style = NoopType.captionNumber, color = color)
        }
        Meter(fraction = fraction, color = color)
    }
}

/** A thin, rounded proportional meter on the inset well. */
@Composable
private fun Meter(fraction: Float, color: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(Palette.surfaceInset),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(shape)
                .background(color),
        )
    }
}

// MARK: - Per-day card (ported from IntelligenceView.dayCard)
//
// Header = the day + a NOOP-computed source badge; a row of the five headline
// scores (Charge / Effort / Rest / HRV / RHR) tinted to the design-system metric
// colors, then a thin Effort meter for at-a-glance load.

@Composable
private fun DayCard(d: DailyMetric, effortScale: EffortScale) {
    NoopCard(padding = 18.dp, tint = Palette.chargeColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prettyDay(d.day),
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                // The REAL source of this day's dashboard headline, not a hard-coded "NOOP-computed".
                // The merged DailyMetric carries the WINNING row's deviceId (mergeDaily: an import wins
                // over the computed "-noop" row), so a strap-scored night reads "On-device" while a day an
                // import covers reads "Whoop" / "Apple Health". Computed rows keep the charge tint; imports
                // use the accent tint to stand out. Mirrors macOS IntelligenceEngine.DaySource. (Sleep §2.6.)
                val src = daySourceBadge(d.deviceId)
                SourceBadge(src.first, tint = src.second)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                DayStat(
                    "Charge",
                    d.recovery?.let { "${it.roundToInt()}%" } ?: "—",
                    d.recovery?.let { Palette.recoveryColor(it) } ?: Palette.textSecondary,
                    Modifier.weight(1f),
                )
                DayStat(
                    "Effort",
                    d.strain?.let { UnitFormatter.effortDisplay(it, effortScale) } ?: "—",
                    d.strain?.let { Palette.strainColor(it) } ?: Palette.textSecondary,
                    Modifier.weight(1f),
                )
                DayStat(
                    // R9d: this column is time asleep, not the Rest score, so it is labelled Sleep.
                    uiString(R.string.nav_sleep),
                    sleepValue(d.totalSleepMin),
                    Palette.restColor,
                    Modifier.weight(1f),
                )
                DayStat(
                    "HRV",
                    d.avgHrv?.let { "${it.roundToInt()}" } ?: "—",
                    Palette.metricPurple,
                    Modifier.weight(1f),
                )
                DayStat(
                    "RHR",
                    d.restingHr?.toString() ?: "—",
                    Palette.metricRose,
                    Modifier.weight(1f),
                )
            }

            // Effort load meter (0–100), tinted along the strain ramp.
            d.strain?.let { s ->
                Meter(
                    fraction = (s / 100.0).toFloat(),
                    color = Palette.strainColor(s),
                )
            }
        }
    }
}

@Composable
private fun DayStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label.uppercase(),
            style = NoopType.footnote,
            color = Palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = NoopType.number(19f),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Derived helpers

private fun sleepValue(totalMin: Double?): String {
    val m = totalMin ?: return "—"
    val total = m.roundToInt()
    return "${total / 60}h ${total % 60}m"
}

/**
 * The By-Day source badge (label + tint) for a merged [DailyMetric], from the WINNING row's [deviceId].
 * The By-Day numbers are always NOOP's on-device scores, but when an import covers the day it wins the
 * dashboard merge (mergeDaily), so the badge says so instead of the old hard-coded "NOOP-computed".
 * A computed row's id ends in "-noop"; imports keep their source id ("my-whoop" export, "apple-health" /
 * "health-connect"). Brand wording matches the rest of the app (macOS DaySource: "On-device"/"Whoop"/
 * "Apple Health"); imports use the accent tint, computed rows the charge tint. (Sleep overhaul §2.6.)
 */
internal fun daySourceBadge(deviceId: String): Pair<String, Color> = when {
    deviceId.endsWith("-noop") -> "On-device" to Palette.chargeColor
    deviceId == com.noop.data.WhoopRepository.APPLE_HEALTH_SOURCE ||
        deviceId == com.noop.data.WhoopRepository.HEALTH_CONNECT_SOURCE -> "Apple Health" to Palette.accent
    // An Oura night is persisted under the ring's "oura-<uuid>" id (the ring PROVIDES its own SleepNet
    // hypnogram, banked as the merge-winning session) — name it "Oura", not the generic "Whoop" the
    // else-branch would give a non-"-noop" id. Resolved off the canonical brand table, not an "oura" literal.
    com.noop.data.DeviceBrandCatalog.isOura(deviceId) -> "Oura" to Palette.restColor
    else -> "Whoop" to Palette.accent
}

/** Recent-window options for the By Day list. `days == null` means show everything. */
private enum class IntelRange(val days: Int?, val label: String) {
    Week(7, "W"), Month(30, "M"), Quarter(90, "3M"),
    Half(180, "6M"), Year(365, "1Y"), All(null, "ALL"),
}

/** "YYYY-MM-DD" → "Mon 5 Jun"; falls back to the raw key if it doesn't parse. */
private fun prettyDay(day: String): String {
    return try {
        val parts = day.split("-")
        val y = parts[0].toInt()
        val mo = parts[1].toInt()
        val da = parts[2].toInt()
        val cal = Calendar.getInstance().apply { set(y, mo - 1, da) }
        val dow = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[
            cal.get(Calendar.DAY_OF_WEEK) - 1,
        ]
        val month = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )[mo - 1]
        "$dow $da $month"
    } catch (_: Exception) {
        day
    }
}


// MARK: - Ridge Intelligence (B2 Hub2a)

/** One gold-bordered card: the title, the Estimate chip, a 110 dp Charge orb beside the sentence, "If I sleep", the footnote. */
@Composable
private fun RidgeForecastCard(f: RecoveryForecast, sleepOptions: SleepScenarios, chosenHours: Double, onChoose: (Double) -> Unit) {
    val charge = f.charge.roundToInt()
    val band = f.band.roundToInt()
    com.noop.ui.ridge.GlassCard(Modifier.fillMaxWidth(), tone = com.noop.ui.ridge.GlassTone.Charge, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    com.noop.ui.ridge.RidgeEyebrow(stringResource(R.string.intel_ridge_evening))
                    Text(stringResource(R.string.intel_ridge_tomorrow), style = RidgeType.titleSm.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Ridge.ink)
                }
                Text(
                    stringResource(R.string.intel_ridge_estimate), style = TRN_LEGEND, color = Ridge.inkMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Ridge.radiusPill))
                        .background(Ridge.glassFillStrong)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LiquidOrb(
                    fraction = (f.charge / 100.0).coerceIn(0.0, 1.0),
                    display = charge.toString(),
                    liquid = RidgeLiquid.CHARGE,
                    diameter = 110.dp,
                    glass = true,
                )
                Text(
                    uiString(R.string.intelligence_forecast_sentence, charge, band, sleepHoursLabel(f.plannedSleepHours)),
                    style = RidgeType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = Ridge.ink,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.noop.ui.ridge.RidgeEyebrow(stringResource(R.string.intelligence_if_i_sleep))
                com.noop.ui.ridge.RidgeSegmentedPills(
                    items = sleepOptions.hours, selected = chosenHours, onSelect = onChoose,
                    label = { sleepHoursLabel(it) }, fill = true,
                )
            }
            Text(uiString(R.string.intelligence_forecast_footnote, f.nights), style = TRN_LEGEND.copy(lineHeight = 17.sp), color = Ridge.inkSubtle)
        }
    }
}

/** "Weights / Charge model": the five inputs with serif percentages and soft-glow bars, then the Effort scale. */
@Composable
private fun RidgeWeightsCard(effortScale: EffortScale) {
    com.noop.ui.ridge.GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                com.noop.ui.ridge.RidgeEyebrow(stringResource(R.string.intel_ridge_weights))
                Text(stringResource(R.string.intel_ridge_model), style = RidgeType.titleSm, color = Ridge.ink)
            }
            listOf(
                Triple("Heart-rate variability", 55, Ridge.dataHrv),
                Triple("Resting heart rate", 20, Ridge.dataHeart),
                Triple("Rest quality", 15, Ridge.rest),
                Triple("Respiration", 5, Ridge.dataBreath),
                Triple("Skin-temperature deviation", 5, Ridge.dataEnergy),
            ).forEach { (label, pct, color) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = RidgeType.body.copy(fontSize = 14.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.intel_ridge_pct, pct), style = RidgeType.titleSm.copy(fontSize = 15.sp, lineHeight = 18.sp), color = color)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ridge.glassFillStrong)) {
                        Box(Modifier.fillMaxWidth(pct / 55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.l10n_intelligence_screen_effort_8c974bc6), style = RidgeType.body.copy(fontSize = 14.sp), color = Ridge.inkMuted, modifier = Modifier.weight(1f))
                Text(
                    uiString(R.string.l10n_intelligence_screen_0_unitformatter_effortscalemax_effortscale_scale_b5c43f56, UnitFormatter.effortScaleMax(effortScale)),
                    style = TRN_LEGEND, color = Ridge.effort,
                )
            }
        }
    }
}

@Composable
private fun RidgeDayNote(text: String) {
    com.noop.ui.ridge.GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text(text, style = RidgeType.bodySm, color = Ridge.inkMuted)
    }
}

/**
 * One day of the board's single "By day" card: the date, then Charge, Effort and Sleep in the serif. The rows join into one
 * card (the first and last round their outer corners). Tapping a row opens the rest of the day's figures (HRV, resting
 * HR and where the day came from), so nothing the old per-day card showed is lost.
 */
@Composable
private fun RidgeDayCard(days: List<DailyMetric>, effortScale: EffortScale) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Ridge.radiusLg)).background(Ridge.glassFill).padding(horizontal = 16.dp)) {
        days.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
            RidgeDayRow(d, effortScale)
        }
    }
}

@Composable
private fun RidgeDayRow(d: DailyMetric, effortScale: EffortScale) {
    var open by remember(d.day) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(prettyDay(d.day), style = RidgeType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp), color = Ridge.ink, modifier = Modifier.width(74.dp))
            RidgeDayFigure(d.recovery?.let { "${it.roundToInt()}" } ?: "—", uiString(R.string.l10n_trends_explore_screen_charge_d4e1aee4), Ridge.charge, Modifier.weight(1f))
            RidgeDayFigure(d.strain?.let { UnitFormatter.effortDisplay(it, effortScale) } ?: "—", uiString(R.string.l10n_intelligence_screen_effort_8c974bc6), Ridge.effort, Modifier.weight(1f))
            RidgeDayFigure(sleepValue(d.totalSleepMin), uiString(R.string.nav_sleep), Ridge.rest, Modifier.weight(1f))
        }
        if (open) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(74.dp))
                RidgeDayFigure(d.avgHrv?.let { "${it.roundToInt()}" } ?: "—", "HRV", Ridge.dataHrv, Modifier.weight(1f))
                RidgeDayFigure(d.restingHr?.toString() ?: "—", "RHR", Ridge.dataHeart, Modifier.weight(1f))
                val src = daySourceBadge(d.deviceId)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { SourceBadge(src.first, tint = src.second) }
            }
        }
    }
}

@Composable
private fun RidgeDayFigure(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = RidgeType.titleSm.copy(fontSize = 17.sp, lineHeight = 20.sp), color = color, maxLines = 1)
        Text(label, style = TRN_LEGEND.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Ridge.inkSubtle, maxLines = 1)
    }
}
