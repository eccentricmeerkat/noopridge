package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.analytics.StrainScorer
import com.noop.data.DailyMetric
import com.noop.data.WhoopRepository
import com.noop.data.WorkoutRow
import java.util.Locale
import kotlin.math.roundToInt

// ── COMPONENT 4, provenance badge ───────────────────────────────────────────────────────────────────

// NOTE: the blanket day-level `TodayProvenanceBadge` was removed. Today provenance now resolves the real
// per-metric field-by-field winners, deduplicates them, and renders one card-level SourceBadge aligned to
// the Rest ring (see heroSourceLabel + ScoreHeroRow). The pure `dayOwnerSource` /
// `provenanceBadgeLabel` By-Day mappers are kept (Intelligence/Trends + tests still use that vocabulary).

/**
 * The skin-temp reading a Today surface should LEAD with (#1844): the first of today / the recovery carry
 * / the per-field carry that holds EITHER number, resolved to absolute-or-deviation by
 * [com.noop.analytics.SkinTempDisplay.leadReading].
 *
 * Both numbers are read off the SAME row, so an absolute is never paired with another night's deviation.
 * Replaces `resolveSkinTempDevC`, which had no caller left once both Today surfaces moved onto
 * this one; its carry-order tests were repinned here rather than dropped.
 */
internal fun resolveSkinTempReading(
    d: DailyMetric?,
    carriedDay: DailyMetric?,
    skinTempCarryDay: DailyMetric?,
    prefer: com.noop.analytics.SkinTempDisplay.Kind = com.noop.analytics.SkinTempDisplay.Kind.ABSOLUTE,
): com.noop.analytics.SkinTempDisplay.Reading? =
    listOfNotNull(d, carriedDay, skinTempCarryDay)
        .firstOrNull { it.skinTempC != null || it.skinTempDevC != null }
        ?.let { com.noop.analytics.SkinTempDisplay.leadReading(it.skinTempC, it.skinTempDevC, prefer) }

/** The numbers the HRV, Resting HR, Respiratory and Blood Oxygen tiles show (R9b). */
internal data class VitalTileValues(
    val hrv: Double?,
    val restingHr: Int?,
    val resp: Double?,
    val spo2: Double?,
    /** Blood Oxygen shows the strap estimate, not a measured reading. */
    val spo2IsEstimate: Boolean,
)

/**
 * The vitals tiles' rule (R9b), pulled out of [MetricGrid] so `TodayRegistryIdentityTest` can hold it equal to the
 * registry: the day's own field, then the Charge carry ([carriedDay]), then for respiratory rate and SpO₂ their
 * per-field carries; SpO₂ falls back last to the strap estimate for the day (#1599).
 */
internal fun vitalTileValues(
    d: DailyMetric?,
    carriedDay: DailyMetric?,
    respCarryDay: DailyMetric?,
    spo2CarryDay: DailyMetric?,
    spo2CandidateByDay: Map<String, Double>,
): VitalTileValues {
    val calibratedSpo2 = d?.spo2Pct ?: carriedDay?.spo2Pct ?: spo2CarryDay?.spo2Pct
    val candidateToday = d?.day?.let { spo2CandidateByDay[it] }
    return VitalTileValues(
        hrv = d?.avgHrv ?: carriedDay?.avgHrv,
        restingHr = d?.restingHr ?: carriedDay?.restingHr,
        resp = d?.respRateBpm ?: carriedDay?.respRateBpm ?: respCarryDay?.respRateBpm,
        spo2 = calibratedSpo2 ?: candidateToday,
        spo2IsEstimate = spo2UsingCandidate(calibratedSpo2, candidateToday),
    )
}

/**
 * The full 14-day metric grid, mirroring the macOS LazyVGrid order:
 * Charge, Effort, Rest, HRV, Resting HR, Blood Oxygen, Respiratory,
 * Steps, Weight, Calories. Each tile is a fixed-height [SparkStatTile] so the
 * grid tiles perfectly with no empty cells.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun MetricGrid(
    d: DailyMetric?,
    w: Window,
    recoveryCalibration: Int? = null,
    lastScoredCharge: LastCharge? = null,
    carriedDay: DailyMetric? = null,
    // PER-FIELD SpO₂ carry (see lastSpo2Row): carriedDay is recovery-gated and lands on rows whose
    // spo2Pct is null (computed rows never carry one), so the Blood Oxygen tile falls through to the
    // last row that actually has a reading. Mirrors iOS TodayView.lastSpo2Day (carriedVital's per-field fallback).
    spo2CarryDay: DailyMetric? = null,
    // PER-FIELD respiratory carry (#1331): same reason as spo2CarryDay — respiratory needs a longer
    // clean sleep R-R segment than HRV, so carriedDay can land on a night with HRV but no breaths/min.
    respCarryDay: DailyMetric? = null,
    // #1599: the same candidate map the tile's sparkline is built from, so the number and the line under
    // it draw on one source rather than two. They are not guaranteed EQUAL — the value carries from
    // outside the window and the line cannot — but neither can now show data the other has no access to,
    // which is what made this tile render a number above a blank panel.
    spo2CandidateByDay: Map<String, Double> = emptyMap(),
    // J-06 (R9b): the registry's reason Blood Oxygen is blank, said under the tile.
    spo2Missing: com.noop.metrics.MissingReason? = null,
    // PER-FIELD skin-temp carry (ryanbr review, PR #1589): same reason as spo2CarryDay/respCarryDay —
    // carriedDay (lastScoredRecoveryDay) is a whole-row carry that lands on rows with null
    // skinTempDevC, so the Key Metrics tile falls through to the last row that actually has a
    // reading. Mirrors the "Your Cards" DashboardCard.SKIN_TEMP path's skinTempDay fallback and iOS
    // TodayView's lastSkinTempDay chain.
    skinTempCarryDay: DailyMetric? = null,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    effortScale: EffortScale = EffortScale.HUNDRED,
    // #1001: the day's resolved Effort (live-preferring for today, floored at the stored row). Threaded
    // like caloriesForDay/importedStepsForDay because, like those, it is not simply the DailyMetric column
    // — reading `d.strain` here is what left this tile behind the hero ring on an active morning.
    effortForDay: Double? = null,
    latestWeightKg: Double? = null,
    profileWeightKg: Double = 75.0,
    importedStepsForDay: Int? = null,
    estimatedStepsForDay: Int? = null,
    // #616: the selected day's calorie value resolved imported-first (imported Apple/Health-Connect
    // activeKcal ?: NOOP's on-device estimate), so the Calories tile matches the card + detail instead of
    // reading the on-device estimate alone (which left it NO_DATA / inconsistent). Mirrors the steps params.
    caloriesForDay: Double? = null,
    // #616: the Calories tile's imported-first 14-day trend (see caloriesSpark above) — threaded like
    // restSpark because it isn't a plain DailyMetric column (it unions the imported + on-device series).
    caloriesSpark: List<Double> = emptyList(),
    // Fix 5: the Steps tile's trend from the registry (strap, else the phone import, else the estimate), like the Steps page.
    stepsSpark: List<Double> = emptyList(),
    // #316 / @63, the selected day's representative activity class (0=still, 1=walk, 2=run), shown as a small
    // still/walk/run glyph on a REAL (measured) Steps tile. null hides the icon (no classed sample for the day).
    stepActivityClassForDay: Int? = null,
    // #760/#792: the caption under an ESTIMATED Steps tile: the engine's STATUS line (manual k, or
    // k=… from N days + confidence tier) so a frozen-looking estimate self-explains. Built from the SAME
    // persisted calibration the estimate used; defaults to a bare "est." for callers that don't supply it.
    stepsEstimateCaption: String = "est.",
    // #1491: the blank-tile line for a strap that ESTIMATES steps — null for a 5/MG (its strap reports a
    // real count) and for a user who has never selected a model, so neither is told to calibrate anything.
    stepsCalibrationPrompt: String? = null,
    restScore: Double? = null,
    // The Rest tile's sparkline: the trailing-window Rest composite (0–100, `sleep_performance`), so the
    // mini-graph tracks the Rest SCORE rather than raw sleep minutes (#614 follow-up). Other tiles still
    // read their series off `w` (the DailyMetric windows).
    restSpark: List<Double> = emptyList(),
    enabledMetrics: List<KeyMetric> = KeyMetric.defaultOrder,
    // B2-X: the catalogue series the opt-in tiles (and the Weight trend) read, loaded by the caller by catalogue key.
    catalog: Map<String, List<SeriesPoint>> = emptyMap(),
    isToday: Boolean = false,
    // #1164: today's Rest is provisional while the strap has banked records not yet offloaded — the Rest
    // tile shows "Pending sync" instead of a number that will change. Past days are final.
    restPendingSync: Boolean = false,
    onScoreInfo: (ScoreSection) -> Unit = {},
    // S5: cap the grid to the first METRICS_COLLAPSED_CAP tiles behind a "Show all metrics" expander,
    // collapsing OVERFLOW only (never dropping or reordering a user-selected tile, #251). Defaults keep the
    // grid fully expanded for any caller that doesn't opt into the cap.
    metricsExpanded: Boolean = true,
    onToggleMetrics: () -> Unit = {},
    // Detailed tiles (the #251 editor's switch): squarer tiles with a trend graph under the bar, drawn
    // over the editor's chosen window (7 / 14 / 30 days).
    detailed: Boolean = false,
    // The editor's trailing-window choice (7 / 14 / 30). Caps each tile's sparkline to that many trailing
    // points so "1 month" draws its full span instead of a fixed 14 (matches the iOS windowedSpark cutoff).
    windowDays: Int = 14,
    // Tile drill-ins: every tile opens its focused trend timeline (vital_detail/<key>, the Sleep
    // night-detail pattern) via [onOpenMetric].
    onOpenMetric: (String) -> Unit = {},
    // Exception for the actionable blank WHOOP 4.0 state: it opens the canonical calibration screen.
    onOpenStepsCalibration: () -> Unit = {},
) {
    val vitals = vitalTileValues(d, carriedDay, respCarryDay, spo2CarryDay, spo2CandidateByDay)
    val realStepsForDay = d?.steps ?: importedStepsForDay
    val stepsOpenCalibration = stepsTileShouldOpenCalibration(
        realSteps = realStepsForDay,
        estimatedSteps = estimatedStepsForDay,
        calibrationPrompt = stepsCalibrationPrompt,
    )

    // FIX 3 (iOS `keyMetricsSection` parity): a 3-COLUMN grid of COMPACT liquid tiles, each an iOS `ktile`
    // — a 9sp/+1.2 overline label, a value + small unit, and a thin 8dp LiquidTube fill bar — REPLACING the
    // old 2-column large sparkline cards. One descriptor per KeyMetric, carrying the SAME value/tint reads
    // the old builders used PLUS the tile's LiquidTube fraction (mirroring the iOS ktile frac). The #251
    // editor + enabled-order + collapse expander are all preserved; only the tile look changes.
    val descriptors: Map<KeyMetric, KeyTileData> = mapOf(
        KeyMetric.CHARGE to run {
            // C-04: the same Charge the hero orb shows ([shownCharge]); calibration keeps its "N/4" copy.
            val v = shownCharge(d?.recovery, recoveryCalibration != null, lastScoredCharge)
            KeyTileData(
                label = uiString(R.string.today_metric_charge),
                value = d?.recovery?.let { "${it.roundToInt()}" }
                    ?: recoveryCalibration?.let { "$it/${Baselines.minNightsSeed}" }
                    ?: v?.let { "${it.roundToInt()}" } ?: NO_DATA,
                // D-15: Charge is an integer score, shown without "%" (as on its orb).
                unit = "",
                tint = v?.let { Palette.recoveryColor(it) } ?: Palette.chargeColor,
                frac = v?.let { (it / 100.0).coerceIn(0.0, 1.0) },
                spark = w.recovery,
            )
        },
        KeyMetric.EFFORT to KeyTileData(
            label = uiString(R.string.today_metric_effort),
            // #1001: the resolved Effort, falling back to the stored column only when the caller passes
            // none (previews/tests). Reading `d.strain` here is what left this tile behind the hero ring.
            value = (effortForDay ?: d?.strain)?.let { UnitFormatter.effortDisplay(it, effortScale) } ?: NO_DATA,
            // #492: Strain/Effort is a load index (0–21 WHOOP / 0–100 NOOP), NOT a percentage — the "%"
            // was wrong (esp. on the 0–21 scale). Recovery/Rest ARE 0–100 % and keep it. iOS shows the
            // strain axis as an "of 21"/"of 100" caption with no % (TodayView effort tile); match that.
            unit = "",
            // Same Effort-world blue as the hero orb, so the tile and the orb read as one signal.
            tint = (effortForDay ?: d?.strain)?.let { Palette.sample(Palette.effortGradientStops, (it / StrainScorer.maxStrain).toFloat()) } ?: Palette.effortColor,
            frac = (effortForDay ?: d?.strain)?.let { (it / 100.0).coerceIn(0.0, 1.0) },
            spark = w.strain,
        ),
        KeyMetric.REST to KeyTileData(
            label = uiString(R.string.l10n_today_screen_rest_b79e5f48),
            // #1164/#2012: a provisional Rest is now SAID to be provisional rather than withheld. The
            // caption below carries that; blanking the number as well left a user who had slept, and
            // whose score was computed, looking at "—" for as long as the strap had anything left to
            // send — which on a continuously-banking strap is most of the day.
            value = restScore?.let { "${it.roundToInt()}" } ?: NO_DATA,
            // D-15: Rest is a 0-100 score like Charge, so no "%". Blood oxygen keeps "%", a real unit.
            unit = "",
            tint = restScore?.let { Palette.recoveryColor(it) } ?: Palette.restColor,
            frac = restScore?.let { (it / 100.0).coerceIn(0.0, 1.0) },
            spark = restSpark,
            // Composed from the two shipped strings rather than a third one needing four translations.
            // Matches the single iOS caption "Pending sync · strap history still offloading": the second
            // half alone read as a fragment, and more so now that a real number sits above it (#2012).
            caption = if (restPendingSync) {
                uiString(R.string.l10n_today_screen_pending_sync_cbe01f9e) + " · " +
                    uiString(R.string.l10n_today_screen_strap_history_still_offloading_80140264)
            } else {
                null
            },
        ),
        KeyMetric.HRV to run {
            val v = vitals.hrv
            KeyTileData(
                label = "HRV",
                value = v?.let { "${it.roundToInt()}" } ?: NO_DATA,
                unit = if (v != null) "ms" else "",
                tint = Palette.metricCyan,
                frac = v?.let { (it / 120.0).coerceIn(0.0, 1.0) },
                spark = w.hrv,
            )
        },
        KeyMetric.RESTING_HR to run {
            val v = vitals.restingHr
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_rest_hr_04005617),
                value = v?.toString() ?: NO_DATA,
                unit = if (v != null) "bpm" else "",
                tint = Palette.metricRose,
                frac = v?.let { (it / 100.0).coerceIn(0.0, 1.0) },
                spark = w.rhr,
            )
        },
        KeyMetric.BLOOD_OXYGEN to run {
            // Candidate LAST, after the carries — the exact precedence the dashboard Blood Oxygen card
            // uses two sections down this same screen, and the one the Apple tile uses. The candidate is
            // a fallback for having nothing, never an override for having something stale.
            //
            // For the strap-only install this issue is about there are no calibrated readings at all, so
            // both carries are null and every surface resolves to the candidate regardless.
            val onCandidate = vitals.spo2IsEstimate
            val v = vitals.spo2
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_blood_oxygen_a8ad9ff5),
                value = v?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: NO_DATA,
                unit = if (v != null) "%" else "",
                tint = Palette.metricCyan,
                frac = v?.let { (it / 100.0).coerceIn(0.0, 1.0) },
                // Say WHOSE number this is. The Apple tile has carried this caption all along; Android
                // showed an unlabelled figure, which is the harder of the two to argue with.
                caption = when {
                    onCandidate -> uiString(R.string.spo2_strap_estimate_caption)
                    v != null -> null
                    spo2Missing == com.noop.metrics.MissingReason.DEVICE_UNSUPPORTED -> uiString(R.string.today_spo2_not_from_whoop4)
                    spo2Missing == com.noop.metrics.MissingReason.ESTIMATOR_OFF -> uiString(R.string.today_spo2_estimate_off)
                    else -> null
                },
                spark = spo2SparkSeries(w.spo2, w.spo2Candidate),
            )
        },
        KeyMetric.RESPIRATORY to run {
            val v = vitals.resp
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_respiratory_1cd8c175),
                value = v?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: NO_DATA,
                unit = if (v != null) "rpm" else "",
                tint = Palette.accent,
                frac = v?.let { (it / 24.0).coerceIn(0.0, 1.0) },
                spark = w.resp,
            )
        },
        KeyMetric.STEPS to run {
            // Steps precedence (unchanged): on-device count → imported → estimate. (#107/#150)
            val steps = realStepsForDay ?: estimatedStepsForDay
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_steps_cdde4f20),
                value = steps?.let { todayIntString(it.toDouble()) } ?: NO_DATA,
                unit = "",
                tint = Palette.metricCyan,
                frac = steps?.let { (it / 10000.0).coerceIn(0.0, 1.0) },
                spark = stepsSpark.ifEmpty { w.steps },   // #616; Fix 5: the registry series, so a phone-only steps day draws too
                // A measured count needs no explanation; an ESTIMATE says what it was fitted from
                // (#760/#792); a BLANK tile on a strap that estimates says what would unblock it (#1491).
                caption = when {
                    realStepsForDay != null -> null
                    estimatedStepsForDay != null -> stepsEstimateCaption
                    else -> stepsCalibrationPrompt
                },
            )
        },
        KeyMetric.WEIGHT to run {
            val weight = weightTile(latestWeightKg, profileWeightKg, unitSystem)
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_weight_69c0b815),
                value = weight.value,
                unit = "",
                tint = Palette.accent,
                frac = null,
                // B2-X: the imported weight's trend (none until a weight is imported).
                spark = catalog["weight"].orEmpty().takeLast(windowDays).map { it.value },
            )
        },
        KeyMetric.CALORIES to run {
            // #616: the per-day resolved calorie value (caloriesForDay = imported Apple/Health-Connect
            // first, else NOOP's on-device estimate) — one number across tile, card and detail.
            val kcal = caloriesForDay
            KeyTileData(
                label = uiString(R.string.l10n_today_screen_calories_3e62ecfe),
                value = kcal?.let { todayIntString(it) } ?: NO_DATA,
                unit = if (kcal != null) "kcal" else "",
                tint = Palette.metricAmber,
                frac = kcal?.let { (it / 800.0).coerceIn(0.0, 1.0) },
                spark = caloriesSpark,   // #616: imported-first trend (was missing → no trend line)
            )
        },
        KeyMetric.SKIN_TEMP to run {
            // Added 2026-08-24 (queue 11c follow-up): first Key Metrics appearance for Skin Temp — was
            // already a "Your Cards" tile (DashboardCard.SKIN_TEMP), never a Key Metrics one. Same
            // `d ?: carriedDay` idiom every other simple tile above uses (HRV/RESTING_HR), and the SAME
            // `SkinTempDisplay` formatter the DashboardCard.SKIN_TEMP branch uses so a deviation reads
            // "+0.1 Δ°C" identically on both surfaces (#622: bimodal absolute-vs-deviation field).
            val reading = resolveSkinTempReading(
                d, carriedDay, skinTempCarryDay,
                com.noop.ui.UnitPrefs.skinTempPreferred(LocalContext.current),
            )
            val fahrenheit = UnitPrefs.temperature(LocalContext.current) == TemperatureUnit.FAHRENHEIT
            KeyTileData(
                label = uiString(R.string.today_card_skin_temp),
                // The value carries its own "°C"/"Δ°F" (SkinTempDisplay.format), so unit stays empty —
                // same as the DashboardCard.SKIN_TEMP card and the classic TodayView Skin Temp tile.
                value = reading?.let { com.noop.analytics.SkinTempDisplay.formatReading(it, fahrenheit = fahrenheit) } ?: NO_DATA,
                unit = "",
                tint = Palette.metricAmber,
                frac = null,
                spark = w.skinTemp,
            )
        },
    )

    // Resolve the enabled tiles to their descriptors (keeping the metric for the tap mapping), dropping
    // any unknown key defensively.
    // B2-X: an opt-in catalogue tile: the entry's latest value to the selected day, in its own unit and precision, with
    // its trailing trend. No data reads like the other tiles do.
    val catalogDescriptors: Map<KeyMetric, KeyTileData> = enabledMetrics
        .filter { it.catalogKey != null && descriptors[it] == null }
        .mapNotNull { m ->
            val entry = MetricCatalog.forKey(m.catalogKey) ?: return@mapNotNull null
            val series = catalog[entry.key].orEmpty()
            val dayKey = d?.day
            val latest = (if (dayKey != null) series.lastOrNull { it.day <= dayKey } else null) ?: series.lastOrNull()
            m to KeyTileData(
                label = uiString(m.titleRes),
                value = latest?.let { p -> String.format(java.util.Locale.US, "%.${entry.decimals}f", p.value) } ?: NO_DATA,
                unit = if (latest != null) entry.unit else "",
                tint = entry.accent(),
                frac = null,
                spark = series.takeLast(windowDays).map { it.value },
            )
        }.toMap()
    val allTiles = enabledMetrics.mapNotNull { m -> (descriptors[m] ?: catalogDescriptors[m])?.let { m to it } }
    // Tile tap -> its focused trend TIMELINE (the Sleep night-detail pattern), uniformly for every tile
    // with a windowed series: Recovery/Effort/Rest open their new trend details; the vitals +
    // Steps/Calories open the same vital_detail trends the Health cards use. Today's Charge DRIVERS stay
    // on the hero ring's breakdown sheet (its existing home) — the tile is the history view.
    // Weight has no windowed detail yet -> not tappable (null keeps the tile inert rather than lying).
    fun tapFor(metric: KeyMetric): (() -> Unit)? = when (metric) {
        KeyMetric.CHARGE -> ({ onOpenMetric("recovery") })
        KeyMetric.EFFORT -> ({ onOpenMetric("strain") })
        KeyMetric.REST -> ({ onOpenMetric("rest") })
        KeyMetric.HRV -> ({ onOpenMetric("hrv") })
        KeyMetric.RESTING_HR -> ({ onOpenMetric("rhr") })
        KeyMetric.BLOOD_OXYGEN -> ({ onOpenMetric("spo2") })
        KeyMetric.RESPIRATORY -> ({ onOpenMetric("resp") })
        KeyMetric.STEPS -> if (stepsOpenCalibration) onOpenStepsCalibration else ({ onOpenMetric("steps_est") })
        KeyMetric.CALORIES -> ({ onOpenMetric("active_kcal") })
        KeyMetric.WEIGHT -> null
        // Same "skin" vital_detail key `dashboardCardMetricKey(DashboardCard.SKIN_TEMP)` already routes
        // to — confirmed a working destination there, so this tile opens the SAME screen "Your Cards"
        // already does, not a new/unverified route.
        KeyMetric.SKIN_TEMP -> ({ onOpenMetric("skin") })
        // B2-X: the catalogue tiles open Insights on their metric.
        else -> metric.catalogKey?.let { key -> ({ onOpenMetric(key) }) }
    }
    // S5: slice from the FRONT of the saved order so a pinned/selected tile is never dropped or reordered
    // (#251); only the tail folds behind the expander. Mirrors the iOS visibleKeyMetrics prefix(cap).
    val hasOverflow = allTiles.size > METRICS_COLLAPSED_CAP
    val tiles = if (metricsExpanded || !hasOverflow) allTiles else allTiles.take(METRICS_COLLAPSED_CAP)

    // iOS `keyMetricsSection` LazyVGrid: 3 columns, spacing 8. Build from rows so tile heights tile uniformly
    // and a partial last row pads with empty weight so the columns stay aligned.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(3).forEach { rowTiles ->
            // Detailed rows equalise heights (IntrinsicSize.Max + fillMaxHeight, the #399 idiom): a
            // graph-less tile (Steps/Weight/Calories) sharing a row with graphed neighbours must not
            // shrink its card. Compact rows keep the plain layout, byte-identical to before.
            Row(
                modifier = if (detailed) Modifier.height(IntrinsicSize.Max) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTiles.forEach { (metric, tile) ->
                    LiquidKeyTile(
                        tile,
                        icon = keyMetricIcon(metric),
                        accent = ridgeAccent(metric),
                        detailed = detailed,
                        windowDays = windowDays,
                        onClick = tapFor(metric),
                        modifier = Modifier.weight(1f).then(if (detailed) Modifier.fillMaxHeight() else Modifier),
                    )
                }
                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // S5: the "Show all metrics" / "Show fewer" expander — a centered link like iOS. Toggles visibility
        // only, never WHICH tiles are enabled or their order (that stays the #251 editor's job).
        if (hasOverflow) {
            val hidden = allTiles.size - METRICS_COLLAPSED_CAP
            TextButton(
                onClick = onToggleMetrics,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = Ridge.ink),
            ) {
                Text(
                    if (metricsExpanded) uiString(R.string.today_show_fewer_metrics)
                    else uiString(R.string.today_show_all_metrics, hidden),
                    style = RidgeType.body,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (metricsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** True only for the actionable #1515 state: the tile has no measured/imported count, no estimate, and
 *  carries the WHOOP 4.0 calibration prompt. Real or estimated Steps always retain their trend drill-in;
 *  a blank 5/MG or already-calibrated quiet day has no prompt and retains it too. */
internal fun stepsTileShouldOpenCalibration(
    realSteps: Int?,
    estimatedSteps: Int?,
    calibrationPrompt: String?,
): Boolean = realSteps == null && estimatedSteps == null && calibrationPrompt != null

/** One compact Key-Metrics tile's data: iOS `ktile`(label, value, unit, tint, frac). [spark] is the
 *  trailing trend series (oldest→newest) the DETAILED tile style graphs, capped at render to the editor's
 *  chosen window; empty hides the graph (a metric with no windowed series — Steps/Weight/Calories —
 *  stays tube-only even in detailed mode). */
private data class KeyTileData(
    val label: String,
    val value: String,
    val unit: String,
    val tint: Color,
    val frac: Double?,
    /** #1491: an optional one-line subtitle under the value. Only the Steps tile sets one today — the
     *  estimate's calibration status, or why a blank tile is blank. Null leaves the tile exactly as it was. */
    val caption: String? = null,
    val spark: List<Double> = emptyList(),
)

/** The per-metric glyph shown beside a Key-Metric tile's label — the Android twin of iOS
 *  `LiquidTodayView.keyMetricIcon`, using Material equivalents of its SF Symbols: heart / bolt / moon /
 *  trend line / heart-monitor / drop / air (≈lungs) / walk / scale / flame. Tinted to the tile colour at
 *  render, so the icon reads as the same signal as the bar. */
private fun keyMetricIcon(metric: KeyMetric): ImageVector = when (metric) {
    KeyMetric.CHARGE -> Icons.Filled.Favorite
    KeyMetric.EFFORT -> Icons.Filled.Bolt
    KeyMetric.REST -> Icons.Filled.Bedtime
    KeyMetric.HRV -> Icons.Filled.Timeline
    KeyMetric.RESTING_HR -> Icons.Filled.MonitorHeart
    KeyMetric.BLOOD_OXYGEN -> Icons.Filled.WaterDrop
    KeyMetric.RESPIRATORY -> Icons.Filled.Air
    KeyMetric.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    KeyMetric.WEIGHT -> Icons.Filled.MonitorWeight
    KeyMetric.CALORIES -> Icons.Filled.LocalFireDepartment
    // Same glyph the sibling "Your Cards" tile (DashboardCard.SKIN_TEMP) already uses.
    KeyMetric.SKIN_TEMP -> Icons.Filled.Thermostat
    else -> MetricCatalog.forKey(metric.catalogKey)?.icon ?: Icons.Filled.Timeline
}

/** The Noop Ridge accent each Key-Metric tile carries on its icon, trend and bar. The three scores keep
 *  their liquids; secondary metrics take a `data-*` hue. Fixed per metric, never graded by value: in Noop
 *  Ridge a metric's colour identifies it, and state is the Synthesis card's job. */
private fun ridgeAccent(metric: KeyMetric): Color = when (metric) {
    KeyMetric.CHARGE -> Ridge.charge
    KeyMetric.EFFORT -> Ridge.effort
    KeyMetric.REST -> Ridge.rest
    KeyMetric.HRV -> Ridge.dataHrv
    KeyMetric.RESTING_HR -> Ridge.dataHeart
    KeyMetric.BLOOD_OXYGEN -> Ridge.dataBreath
    KeyMetric.RESPIRATORY -> Ridge.dataBreath
    KeyMetric.STEPS -> Ridge.effort
    KeyMetric.WEIGHT -> Ridge.dataHrv
    KeyMetric.CALORIES -> Ridge.dataEnergy
    KeyMetric.SKIN_TEMP -> Ridge.dataEnergy
    else -> MetricCatalog.forKey(metric.catalogKey)?.accent?.invoke() ?: Ridge.dataHrv
}

/**
 * One Noop Ridge MetricTile: a glass tile tinted in [accent], its trend filling the whole tile edge to edge
 * behind the content (the curve held low so the figure stays clear), with the label, the Playfair figure
 * and the progress bar floating on top. A No-Data value falls back to Inter in muted ink so it doesn't read
 * as a number.
 *
 * [detailed] (the #251 editor's "Detailed tiles" switch) draws the trend, over the editor's [windowDays]
 * window (7 / 14 / 30). A metric with no windowed series (Steps/Weight/Calories) or fewer than two points
 * draws none, so no tile ever shows a fake flat line.
 */
@Composable
private fun LiquidKeyTile(
    data: KeyTileData,
    icon: ImageVector,
    accent: Color,
    detailed: Boolean = false,
    windowDays: Int = 14,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hasValue = data.value != NO_DATA
    val displayValue = localizedMetricValue(data.value)
    // Tap -> the tile's focused trend detail: liquidPress on the tappable tile, indication = null so only
    // the liquid settle shows. A null onClick keeps the tile inert.
    val interaction = remember { MutableInteractionSource() }
    val base = if (onClick != null) {
        modifier
            .liquidPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        modifier
    }
    Box(
        modifier = base
            .clip(RoundedCornerShape(Ridge.radiusMd))
            .ridgeGlass(radius = Ridge.radiusMd, tint = accent, tintWash = 0.08f, tintBorder = 0.22f)
            .semantics { contentDescription = uiString(R.string.l10n_today_screen_data_label_data_value_data_unit_27f6fd6b, data.label, displayValue, data.unit).trim() },
    ) {
        // The trend IS the tile's background. Cap to the editor's chosen window so "1 month" draws its full
        // span; the calories/rest sparks run longer than the others, so this trims them to match.
        if (detailed) {
            RidgeTrendFill(points = data.spark.takeLast(windowDays), color = accent, modifier = Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Detailed rows equalise heights (IntrinsicSize.Max), so fill the row and pin the bar to
                // the bottom edge, level with the neighbours' bars.
                .then(if (detailed) Modifier.fillMaxHeight() else Modifier)
                .heightIn(min = 112.dp)
                .padding(Ridge.space3),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Text(
                        data.label.uppercase(),
                        style = RidgeType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.16.em),
                        color = Ridge.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    // The figure floats over the trend, so a soft ground-coloured halo keeps it clear of
                    // the line wherever the curve runs.
                    val halo = Shadow(color = Ridge.bg100, offset = Offset.Zero, blurRadius = 24f)
                    Text(
                        displayValue,
                        style = if (hasValue) RidgeType.numeralMd.copy(shadow = halo)
                        else RidgeType.bodySm.copy(fontSize = 15.sp),
                        color = if (hasValue) Ridge.ink else Ridge.inkMuted,
                        maxLines = 1,
                    )
                    if (data.unit.isNotEmpty() && hasValue) {
                        Text(
                            data.unit,
                            style = RidgeType.bodySm.copy(fontSize = 13.sp, shadow = halo),
                            color = Ridge.inkMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(start = Ridge.space1, bottom = 4.dp),
                        )
                    }
                }
                // #1491: the Steps subtitle — the calibration status behind an estimate, or, on a blank
                // tile, what actually unblocks it. Only rendered when a tile supplies one.
                data.caption?.let { cap ->
                    Text(
                        cap,
                        style = RidgeType.bodySm.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = Ridge.inkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // B2-1b (owner, 19 Nov 2026): no progress bar in the tile; Main.dc.html draws the trend alone.
        }
    }
}

// Workouts across every recorded + imported source over [from, to]. Recorded sessions live under
// the ACTIVE strap id — "whoop-<id>" after a re-pair — unioned with the canonical legacy "my-whoop"
// via [WhoopRepository.workoutsUnion] (#814): this read was pinned to the literal "my-whoop", which
// stranded a re-paired strap's fresh recordings, so the "Latest Workouts" feed and the HR-graph
// glyphs silently dropped the newest sessions while the Workouts screen (already on the union, #28)
// still showed them. Apple Health and Health Connect imports are stored under their own device ids
// (since #34/#53). Both the "Last Workouts" feed and the HR-graph sport glyphs need the SAME union,
// or Health-Connect-imported sessions get no glyph on the Today trend, so they share this one seam.
// Deduped here (not per-consumer) with the Workouts screen's #687 semantics: a live strap recording
// and its thin Health Connect import collapse to the richer row, so neither the feed shows a
// duplicate card nor the HR trend a doubled sport glyph. dropDetectedShadows/filterDismissed are
// deliberately absent: `detected` rows live under `<deviceId>-noop`, which this union never queries.
internal suspend fun WhoopRepository.workoutsAllSources(
    activeDeviceId: String,
    from: Long,
    to: Long,
): List<WorkoutRow> =
    WorkoutEditing.dedupCrossSource(
        workoutsUnion(activeDeviceId, from, to) +
            workouts("apple-health", from, to) +
            workouts("health-connect", from, to)
    )

// MARK: - SparkStatTile
//
// A fixed-height metric tile: overline label, big value + caption, and a 14-day
// Sparkline anchored along the bottom edge. Mirrors the macOS StatTile-with-sparkline
// while reusing the locked surfaces/typography (NoopCard, Overline, NoopType). Built
// here rather than mutating the shared StatTile so other screens keep the plain tile.

@Composable
private fun SparkStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color = Palette.textPrimary,
    spark: List<Double> = emptyList(),
    sparkColor: Color = Palette.accent,
    onInfo: (() -> Unit)? = null,
    badge: String? = null,
    // #316 / @63, an optional activity-class code (0=still, 1=walk, 2=run) rendered as a small still/walk/run
    // glyph in the label row, tinted with the tile accent. null = no icon. Used by the Steps tile.
    trailingIcon: Int? = null,
) {
    val displayValue = localizedMetricValue(value)
    NoopCard(modifier = modifier.height(Metrics.tileHeight), padding = Metrics.space14) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Label row carries the overline, an optional low-confidence [badge] (H9, e.g. "Estimated"
            // stages), an optional activity glyph ([trailingIcon], #316), and, for the three headline scores
            // only, a trailing ⓘ that opens the scoring guide at this score. Other tiles render as before.
            if (onInfo != null || badge != null || trailingIcon != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline(label)
                    if (badge != null) {
                        Spacer(Modifier.width(Metrics.space6))
                        // A tertiary-tinted pill, honest "this is estimated, not measured" signal, the same
                        // muted treatment as a provenance badge so it informs without alarming. (H9)
                        SourceBadge(badge, tint = Palette.textTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    // #316, still/walk/run glyph, before the optional ⓘ. Self-hides for an unknown code.
                    if (trailingIcon != null) {
                        val (vector, descRes) = stepActivityIconFor(trailingIcon)
                        if (vector != null && descRes != null) {
                            Icon(
                                vector,
                                contentDescription = uiString(descRes),
                                tint = accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (onInfo != null) ScoreInfoButton(section = null, onClick = onInfo, compact = true)
                }
            } else {
                Overline(label)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Shrink-to-fit (down to 0.6×) so a value never ellipsizes to "100%"→"10…" /
                    // "15.5"→"15…" next to the inline sparkline, matching the Swift tile's
                    // minimumScaleFactor (#332). fillMaxWidth() is load-bearing: AutoSizeValue only
                    // shrinks when its Text is given a hard width to overflow against, without it the
                    // single-line Text takes its intrinsic width, `didOverflowWidth` never trips, and
                    // the value silently truncates at full size. The plain StatTile worked because it
                    // passes weight(1f) (a hard width); this column-child needs fillMaxWidth instead.
                    AutoSizeValue(
                        displayValue,
                        style = NoopType.tileValueLarge,
                        color = accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (caption != null) {
                        Text(
                            caption,
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Metrics.space2),
                        )
                    }
                }
                if (spark.size >= 2) {
                    // Sparkline forces fillMaxWidth + a fixed height internally, so we
                    // bound it in a sized Box to keep it a compact inline trend.
                    SparkTailBox(wide = true) {
                        Sparkline(values = spark, color = sparkColor)
                    }
                }
            }
        }
    }
}

/** The group a tile is listed under in the editor's Available list: its catalogue category (the original eleven map to theirs). */
private fun keyMetricGroup(m: KeyMetric, titles: Map<CatCategory, String>): String {
    val cat = when (m) {
        KeyMetric.CHARGE, KeyMetric.HRV, KeyMetric.RESTING_HR, KeyMetric.RESPIRATORY -> CatCategory.RECOVERY
        KeyMetric.EFFORT, KeyMetric.STEPS, KeyMetric.CALORIES -> CatCategory.EFFORT
        KeyMetric.REST -> CatCategory.SLEEP
        KeyMetric.BLOOD_OXYGEN, KeyMetric.SKIN_TEMP -> CatCategory.HEALTH
        KeyMetric.WEIGHT -> CatCategory.BODY
        else -> MetricCatalog.forKey(m.catalogKey)?.category ?: CatCategory.HEALTH
    }
    return titles.getValue(cat)
}

@Composable
internal fun KeyMetricsEditorDialog(
    initial: List<KeyMetric>,
    initialDetailed: Boolean = false,
    initialWindowDays: Int = 14,
    onDismiss: () -> Unit,
    onSave: (List<KeyMetric>, Boolean, Int) -> Unit,
    /** RS-2e: tiles the Ridge editor tags "No data". */
    noData: Set<KeyMetric> = emptySet(),
) {
    // B2-X: under Ridge the editor is the board-style sheet (RidgeKeyMetricsEditor); v10 keeps this dialog.
    if (ridgeShell) {
        RidgeKeyMetricsEditor(initial, initialDetailed, initialWindowDays, onDismiss, onSave, noData)
        return
    }
    val context = LocalContext.current
    val titles = KeyMetric.entries.associateWith { uiString(it.titleRes) }
    val groupTitles = CatCategory.entries.associateWith { uiString(it.labelRes) }
    // Detailed tiles: taller/squarer with a trend graph under the fill bar (display-only), over the
    // chosen trailing window (1 week / 2 weeks / 1 month).
    var detailed by remember { mutableStateOf(initialDetailed) }
    var windowDays by remember { mutableStateOf(initialWindowDays) }
    val shown = remember { mutableStateListOf<KeyMetric>().apply { addAll(initial) } }
    val hidden = remember {
        mutableStateListOf<KeyMetric>().apply {
            // B2-X: every tile (the 11 and the catalogue's), not only the default ten.
            addAll(KeyMetric.entries.filter { it !in initial })
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Palette.surfaceOverlay,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(uiString(R.string.l10n_today_screen_edit_key_metrics_f95e61a4), style = NoopType.title2, color = Palette.textPrimary)
                    Text(
                        uiString(R.string.l10n_today_screen_choose_which_tiles_show_on_your_a4e3acfb),
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                }

                // Detailed tiles: the tile-style option (compact ktile vs squarer tile + 14-day graph).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(uiString(R.string.l10n_today_screen_detailed_tiles_0801721b), style = NoopType.body, color = Palette.textPrimary)
                        Text(
                            uiString(R.string.l10n_today_screen_squarer_tiles_with_a_trend_graph_3c297dec),
                            style = NoopType.caption,
                            color = Palette.textSecondary,
                        )
                    }
                    Switch(
                        checked = detailed,
                        onCheckedChange = { detailed = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Palette.surfaceBase,
                            checkedTrackColor = Palette.accent,
                            uncheckedThumbColor = Palette.textSecondary,
                            uncheckedTrackColor = Palette.surfaceInset,
                            uncheckedBorderColor = Palette.hairline,
                        ),
                        modifier = Modifier.semantics { contentDescription = uiString(R.string.l10n_today_screen_detailed_tiles_0801721b) },
                    )
                }
                // The detailed graphs' trailing window — 1 week / 2 weeks / 1 month (the NOOP signature
                // segmented pill, same control the trend screens use). Only shown while Detailed is on.
                if (detailed) {
                    SegmentedPillControl(
                        items = listOf(7, 14, 30),
                        selection = windowDays,
                        label = { when (it) {
                            7 -> context.getString(R.string.today_range_1_week)
                            14 -> context.getString(R.string.today_range_2_weeks)
                            else -> context.getString(R.string.today_range_1_month)
                        } },
                        onSelect = { windowDays = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(color = Palette.hairline, thickness = 1.dp)

                EditableVisibilityRows(
                    shown = shown,
                    hidden = hidden,
                    itemTitle = { titles.getValue(it) },
                    // B2-X: Available tiles grouped by catalogue category; at most KeyMetric.MAX_SHOWN shown.
                    hiddenGroup = { keyMetricGroup(it, groupTitles) },
                    maxShown = KeyMetric.MAX_SHOWN,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            shown.clear()
                            shown.addAll(KeyMetric.defaultOrder)
                            hidden.clear()
                            detailed = false
                            windowDays = 14
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Palette.textSecondary),
                    ) { Text(uiString(R.string.l10n_today_screen_reset_44c57abd), style = NoopType.body) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSave(shown.toList(), detailed, windowDays) },
                        enabled = shown.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.accent,
                            contentColor = Palette.surfaceBase,
                        ),
                    ) { Text(uiString(R.string.l10n_today_screen_done_e9b450d1), style = NoopType.captionNumber) }
                }
            }
        }
    }
}
