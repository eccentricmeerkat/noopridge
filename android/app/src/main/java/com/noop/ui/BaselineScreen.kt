package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.NoopApplication
import com.noop.R
import com.noop.metrics.V10MetricInputs
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.loadV10MetricInputs
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeSegmentedPills
import com.noop.ui.ridge.chart.RidgePlot
import com.noop.ui.ridge.chart.ScrubTip
import com.noop.ui.ridge.chart.axisLabelSlots
import com.noop.ui.ridge.chart.ridgeBand
import com.noop.ui.ridge.chart.ridgeDots
import com.noop.ui.ridge.chart.ridgeEndRing
import com.noop.ui.ridge.chart.ridgeLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Ridge Baseline (R5-2, E-01, `Baseline.dc.html`)
//
// The slow-moving markers: body age (v10's weekly Vitality engine, ±5 years), VO₂ max estimates with monthly
// averages, fitness age and the max HR the zones use, and the long-run resting HR and HRV. All of it comes from
// [buildBaseline], which reads the registry over the same inputs the debug metric inspector loads.

@Composable
fun BaselineScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var inputs by remember { mutableStateOf<V10MetricInputs?>(null) }
    LaunchedEffect(Unit) {
        inputs = withContext(Dispatchers.IO) {
            runCatching { loadV10MetricInputs(context.applicationContext as NoopApplication) }.getOrNull()
        }
    }
    var range by rememberSaveable { mutableStateOf(BaselineRange.D180) }
    // R9a V2-7 (#27): null when no age was entered and the change is on. Drivers stay on the engine's age.
    val profile = remember { ProfileStore.from(context) }
    val chronoAge = remember { profile.knownAge }
    val engineAge = remember { profile.age }
    val model = inputs?.let { i ->
        remember(i, range) {
            buildBaseline(
                V10MetricRegistry(i), i.today, range, chronoAge, maxHrMeasured = i.maxHrOverride != null,
                earliest = earliestDay(i), sex = profile.sex,
            )
        }
    }
    // R5-3: HR recovery per session, from v10's own per-workout read (workoutHeartRateRecovery), over every source
    // deduplicated as Today's Activities. Capped at the 60 most recent sessions so a two-year range stays quick.
    var hrrSessions by remember { mutableStateOf<List<HrrSession>>(emptyList()) }
    LaunchedEffect(range, inputs) {
        val today = inputs?.today ?: return@LaunchedEffect
        hrrSessions = withContext(Dispatchers.IO) { loadHrrSessions(vm, today.minusDays((range.spanDays(inputs?.let(::earliestDay), today) - 1).toLong())) }
    }
    val drivers = inputs?.let { i -> remember(i) { buildDrivers(vitalityContributions(i.days.sortedBy { it.day }, engineAge)) } }
    // RS-1-3 item 8: Pace of aging and Trend View (V2Change.BODY_AGE_PACE), computed off the main thread; body age is unchanged.
    var trend by remember { mutableStateOf<BodyAgeTrend?>(null) }
    LaunchedEffect(inputs) {
        val i = inputs ?: return@LaunchedEffect
        if (chronoAge == null || !com.noop.metrics.MetricsV2.on(com.noop.metrics.V2Change.BODY_AGE_PACE)) return@LaunchedEffect
        trend = withContext(Dispatchers.Default) {
            runCatching {
                val reg = V10MetricRegistry(i)
                fun vo2Avg(n: Long) = reg.series(com.noop.metrics.MetricId.VO2_MAX, com.noop.metrics.DayKey(i.today.minusDays(n - 1)), com.noop.metrics.DayKey(i.today))
                    .mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()
                val dob = profile.dateOfBirthMillis
                val ageAt = { d: LocalDate ->
                    val born = java.time.Instant.ofEpochMilli(dob).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    java.time.temporal.ChronoUnit.DAYS.between(born, d) / 365.2425
                }
                BodyAgeTrendModel.build(
                    i.days, i.today, engineAge.toDouble(), ageAt, vo2Avg(BodyAgeTrendModel.SIX_MONTHS_DAYS.toLong()), vo2Avg(BodyAgeTrendModel.MONTH_DAYS.toLong()),
                    storedBodyAge = i.computedSeries["body_age"].orEmpty(),
                )
            }.getOrNull()
        }
    }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(context) }
    val skyBehindCards = remember { NoopPrefs.skyBehindCards(context) }

    LazyScreenScaffold(
        title = null,
        topBackground = screenBackdropSlot(showDayCycleBackground, skyBehindCards),
        fullBleedBackground = screenBackdropFullBleed(showDayCycleBackground, skyBehindCards),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                RidgeEyebrow(stringResource(R.string.baseline_eyebrow))
                Text(
                    stringResource(R.string.nav_baseline), style = RidgeType.titleXl, color = Ridge.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item { BaselinePills(range) { range = it } }
        val m = model ?: return@LazyScreenScaffold
        item { BodyAgeCard(m.bodyAge, chronoAge, onOpenSettings) }
        trend?.let { t -> item { PaceOfAgingCard(t, onOpenTrend = { showTrend = true }) } }
        item { CapacityCard(m, chronoAge, onOpenSettings) }
        item { LongRunHeartCard(m) }
        item { HrrCard(buildHrr(hrrSessions, m.vo2.days), m.range) }
        // R9c: the drivers explain body age, so with no age entered (body age asks for one) they are not shown.
        if (chronoAge != null) item { DriversCard(drivers.orEmpty()) }
    }
    trend?.takeIf { showTrend }?.let { t -> TrendViewSheet(t) { showTrend = false } }
}

/** The earliest day with a stored daily row: where the "All" range begins. */
private fun earliestDay(i: V10MetricInputs): LocalDate? =
    i.days.minOfOrNull { it.day }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private suspend fun loadHrrSessions(vm: AppViewModel, from: LocalDate): List<HrrSession> {
    val zone = java.time.ZoneId.systemDefault()
    val workouts = runCatching {
        WorkoutEditing.dedupCrossSource(
            vm.repo.workoutsAllSources(vm.deviceId, from.atStartOfDay(zone).toEpochSecond(), java.time.Instant.now().epochSecond),
        )
    }.getOrDefault(emptyList()).sortedBy { it.endTs }.takeLast(60)
    return workouts.mapNotNull { w ->
        val drop = runCatching { vm.workoutHeartRateRecovery(w.startTs, w.endTs, w.source, w.deviceId) }.getOrNull()?.after1Minute
            ?: return@mapNotNull null
        HrrSession(logicalDay(java.time.Instant.ofEpochSecond(w.startTs).atZone(zone)), w.endTs, drop)
    }
}

// MARK: - Heart-rate recovery (R5-3)

@Composable
private fun HrrCard(t: HrrTrend, range: BaselineRange) {
    val bpm = stringResource(R.string.trends_ridge_bpm)
    TrendCard(
        eyebrow = stringResource(R.string.baseline_hrr_eyebrow),
        title = when {
            t.latest == null || t.typical == null -> stringResource(R.string.baseline_hrr_none_title)
            // B2-3-4: the board's "Improving · latest drop", once there are enough sessions to call a direction.
            hrrDirection(t) != null -> stringResource(
                R.string.baseline_hrr_title_trend,
                stringResource(
                    when (hrrDirection(t)) {
                        HrrDirection.IMPROVING -> R.string.baseline_hrr_improving
                        HrrDirection.DECLINING -> R.string.baseline_hrr_declining
                        else -> R.string.baseline_hrr_steady
                    },
                ),
                t.latest,
            )
            else -> stringResource(R.string.baseline_hrr_title, t.latest, t.typical)
        },
        sub = stringResource(R.string.baseline_hrr_sub, t.sessions, rangeWord(range)),
    ) {
        if (t.sessions < 1) {
            NeedsData(stringResource(R.string.baseline_hrr_none), 110.dp)
            return@TrendCard
        }
        val values = t.drops.filterNotNull()
        val lo = (kotlin.math.floor((values.min() - 5) / 10) * 10).coerceAtLeast(0.0)
        val hi = kotlin.math.ceil((values.max() + 5) / 10) * 10
        val axis = com.noop.ui.ridge.chart.Axis(lo, hi, generateSequence(lo) { it + 10 }.takeWhile { it <= hi + 1e-9 }.toList())
        val long = t.days.size > 180
        val trend = hrrTrendLine(t)
        RidgePlot(
            n = t.days.size,
            axis = axis,
            height = 110.dp,
            yLabel = { it.roundToInt().toString() },
            xLabels = axisLabelSlots(t.days.size).map { it to slotLabel(t.days[it], long) },
            contentDescription = stringResource(R.string.baseline_hrr_a11y, t.sessions, t.latest ?: 0, t.typical ?: 0),
            accent = Ridge.dataHeart,
            scrubY = { i -> nearestEstimate(t.drops, i)?.let { t.drops[it] } },
            tip = { i -> nearestEstimate(t.drops, i)?.let { j -> ScrubTip(bpm.format(whole(t.drops[j])), dayLabel(t.days[j])) } },
        ) { g ->
            // B2-3-4: a dashed trend line through the sessions, each one a ring.
            trend?.let { ridgeLine(g, it, Ridge.inkMuted, width = 1.5.dp, dashed = true) }
            ridgeDots(g, t.drops, Ridge.dataHeart, radius = 4.dp, hollow = { _, _ -> true })
        }
        Text(
            if (t.typical != null) stringResource(R.string.baseline_hrr_note_typical, t.typical) else stringResource(R.string.baseline_hrr_note),
            style = TRN_LEGEND, color = Ridge.inkSubtle,
        )
    }
}

// MARK: - Drivers (R5-3)

@Composable
private fun driverLabel(key: String): String = stringResource(
    when (key) {
        "rhr" -> R.string.baseline_driver_rhr
        "vo2max" -> R.string.baseline_driver_vo2
        "sleep" -> R.string.baseline_driver_sleep
        "consistency" -> R.string.baseline_driver_regularity
        "hrv" -> R.string.baseline_driver_hrv
        else -> R.string.baseline_driver_steps
    },
)

@Composable
private fun DriversCard(drivers: List<Driver>) {
    TrendCard(
        eyebrow = stringResource(R.string.baseline_drivers_eyebrow),
        title = stringResource(R.string.baseline_drivers_title),
        // B2-3-5: the board's "net" line: the drivers' years added up (they sum to the engine's own body-age shift).
        sub = if (drivers.isEmpty()) stringResource(R.string.baseline_drivers_sub)
        else stringResource(R.string.baseline_drivers_sub_net, signedYears(drivers.sumOf { it.years })),
    ) {
        if (drivers.isEmpty()) {
            NeedsData(stringResource(R.string.baseline_body_pending), 80.dp)
        } else {
            drivers.forEach { d ->
                val word = stringResource(
                    when (d.direction) {
                        -1 -> R.string.baseline_driver_helping
                        1 -> R.string.baseline_driver_holding
                        else -> R.string.baseline_driver_neutral
                    },
                )
                val color = when (d.direction) {
                    -1 -> Ridge.brand
                    1 -> Ridge.stateLow
                    else -> Ridge.inkSubtle
                }
                val label = driverLabel(d.key)
                // B2-3-5: the board's row: name and its word on the left, a mini bar centred on a baseline marker (towards
                // younger on the left, older on the right), and the driver's years in serif on the right.
                val years = signedYears(d.years)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
                ) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = TRN_SUB, color = Ridge.ink)
                        Text(word, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = color)
                    }
                    Box(Modifier.weight(1f).height(6.dp).background(Ridge.glassFill, RoundedCornerShape(3.dp))) {
                        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                            val mid = size.width / 2
                            val w = (kotlin.math.abs(d.share) * mid).toFloat().coerceAtLeast(2f)
                            val left = if (d.share < 0) mid - w else mid
                            drawRoundRect(color, androidx.compose.ui.geometry.Offset(left, 0f),
                                androidx.compose.ui.geometry.Size(w, size.height),
                                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                            drawLine(Ridge.inkSubtle, androidx.compose.ui.geometry.Offset(mid, 0f),
                                androidx.compose.ui.geometry.Offset(mid, size.height), 1.dp.toPx())
                        }
                    }
                    Text(
                        stringResource(R.string.baseline_driver_years, years),
                        style = TRN_H2.copy(fontSize = 15.sp), color = color,
                        modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.End, maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(color = Ridge.glassStroke)
        Text(stringResource(R.string.baseline_how_title), style = TRN_SUB.copy(fontWeight = FontWeight.SemiBold), color = Ridge.ink)
        Text(stringResource(R.string.baseline_how_body), style = TRN_LEGEND, color = Ridge.inkMuted)
        Text(stringResource(R.string.l10n_health_screen_a_wellness_estimate_from_your_habits_d00f36de), style = TRN_LEGEND, color = Ridge.inkSubtle)
    }
}

/** 30 days is shown but disabled (too short for slow markers, UX-BAS-4); the other four scope every chart. */
@Composable
private fun BaselinePills(range: BaselineRange, onSelect: (BaselineRange) -> Unit) {
    val labels = mapOf(
        BaselineRange.D90 to stringResource(R.string.baseline_range_90),
        BaselineRange.D180 to stringResource(R.string.baseline_range_180),
        BaselineRange.D365 to stringResource(R.string.baseline_range_365),
        BaselineRange.ALL to stringResource(R.string.baseline_range_all),
    )
    // RS-1-3 item 7: the disabled "30d" pill is gone (board); the four live ranges fill the row.
    Row(verticalAlignment = Alignment.CenterVertically) {
        RidgeSegmentedPills(
            items = BaselineRange.entries.toList(),
            selected = range,
            onSelect = onSelect,
            label = { labels.getValue(it) },
            fill = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rangeWord(r: BaselineRange): String = stringResource(
    when (r) {
        BaselineRange.D90 -> R.string.baseline_word_90
        BaselineRange.D180 -> R.string.baseline_word_180
        BaselineRange.D365 -> R.string.baseline_word_365
        BaselineRange.ALL -> R.string.baseline_word_all
    },
)

private fun slotLabel(d: LocalDate, long: Boolean): String =
    d.format(DateTimeFormatter.ofPattern(if (long) "MMM yyyy" else "d MMM", Locale.getDefault()))

// MARK: - Body age

/**
 * SQ10: body age as a picture rather than a row. The eyebrow and the "updated" note share the card's top line,
 * the numeral sits inside the drifting contours ([BodyAgeHero]), and the calendar-age footer is unchanged. The
 * old serif "Body age 31" heading is gone — the numeral is the heading now — but the states before there is a
 * number still need one, so they keep the serif title.
 */
@Composable
private fun BodyAgeCard(b: BodyAge?, chronoAge: Int?, onOpenSettings: () -> Unit) {
    // B2-3-1: the board's "Next update Saturday". v10 refreshes body age weekly, keyed to Saturday, so the next
    // refresh is the next Saturday after today.
    val nextUpdate = b?.let {
        val next = nextBodyAgeUpdate(LocalDate.now())
        stringResource(R.string.baseline_body_next, next.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .ridgeGlass(radius = Ridge.radiusLg)
            .clip(RoundedCornerShape(Ridge.radiusLg))
            // The board's card sits darker than plain glass, which is what lets the contours' mint read.
            .background(Ridge.bg000.copy(alpha = 0.45f)),
    ) {
        BodyAgeCardRidges(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp))
        Column(
            // 18 dp matches TrendCard's inner padding, so this card's eyebrow lines up with the others.
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(Ridge.space3),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RidgeEyebrow(
                    stringResource(R.string.baseline_body_eyebrow),
                    Modifier.weight(1f).semantics { heading() },
                )
                if (nextUpdate != null) {
                    Text(nextUpdate, style = TRN_LEGEND, color = Ridge.inkMuted, maxLines = 1, textAlign = TextAlign.End)
                }
            }
            when {
                chronoAge == null -> BodyAgeWaiting(
                    stringResource(R.string.baseline_age_needed_title),
                    stringResource(R.string.baseline_age_needed_body),
                )
                b == null -> BodyAgeWaiting(
                    stringResource(R.string.baseline_body_pending_title),
                    stringResource(R.string.baseline_body_pending),
                )
                else -> {
                    val band = b.bandYears.roundToInt().toString()
                    // Calendar age minus body age, to a tenth: the card's tagline reads "6.0 years younger".
                    val tenths = Math.round((b.chronoAge - b.bodyAge) * 10).toInt()
                    BodyAgeHero(
                        bodyAge = b.bodyAge,
                        label = stringResource(R.string.baseline_body_label),
                        detail = when {
                            tenths > 0 -> stringResource(R.string.baseline_body_younger, tenths / 10.0)
                            tenths < 0 -> stringResource(R.string.baseline_body_older, abs(tenths) / 10.0)
                            else -> stringResource(R.string.baseline_body_same, band, b.chronoAge)
                        },
                        a11y = stringResource(
                            R.string.baseline_body_a11y,
                            b.bodyAge.roundToInt().toString(), band, b.chronoAge,
                        ),
                    )
                }
            }
            if (chronoAge == null) {
                SettingsLine(stringResource(R.string.baseline_age_not_set), stringResource(R.string.baseline_age_add), onOpenSettings)
            }
        }
    }
}

/** The card before the engine has a number: a serif title, and one line saying what it is waiting for. */
@Composable
private fun BodyAgeWaiting(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        Text(title, style = TRN_H2, color = Ridge.ink, modifier = Modifier.semantics { heading() })
        Text(body, style = TRN_SUB, color = Ridge.inkMuted)
    }
}

/** A quiet "fact · action ›" line that opens Settings (age, max HR). */
@Composable
private fun SettingsLine(fact: String, action: String, onClick: () -> Unit) {
    HorizontalDivider(color = Ridge.glassStroke)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fact, style = TRN_LEGEND, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
        Text(action, style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.brand)
    }
}

// MARK: - Capacity · VO₂ max

@Composable
private fun CapacityCard(m: BaselineModel, chronoAge: Int?, onOpenSettings: () -> Unit) {
    val v = m.vo2
    val fmt = { x: Double -> String.format(Locale.getDefault(), "%.1f", x) }
    TrendCard(
        eyebrow = stringResource(R.string.baseline_vo2_eyebrow),
        title = if (m.needsAge) stringResource(R.string.baseline_age_needed_title)
        else v.latest?.let { stringResource(R.string.baseline_vo2_title, fmt(it)) } ?: stringResource(R.string.baseline_vo2_none),
        sub = stringResource(R.string.baseline_vo2_sub),
    ) {
        if (m.needsAge) {
            NeedsData(stringResource(R.string.baseline_age_needed_short), 140.dp)
        } else if (v.estimates.count { it != null } == 0) {
            NeedsData(stringResource(R.string.baseline_vo2_none_in_range), 140.dp)
        } else {
            val long = m.spanDays > 180
            val tipFmt = stringResource(R.string.baseline_vo2_tip)
            RidgePlot(
                n = v.days.size,
                axis = v.axis,
                height = 140.dp,
                yLabel = { it.roundToInt().toString() },
                xLabels = axisLabelSlots(v.days.size).map { it to slotLabel(v.days[it], long) },
                contentDescription = stringResource(R.string.baseline_vo2_a11y, rangeWord(m.range), v.latest?.let(fmt) ?: "—"),
                accent = Ridge.brand,
                scrubY = { i -> nearestEstimate(v.estimates, i)?.let { v.estimates[it] } },
                tip = { i ->
                    nearestEstimate(v.estimates, i)?.let { j -> ScrubTip(tipFmt.format(fmt(v.estimates[j]!!)), dayLabel(v.days[j])) }
                },
            ) { g ->
                v.typical?.let { ridgeBand(g, it.start, it.endInclusive, Ridge.brand.copy(alpha = 0.08f)) }
                ridgeLine(g, v.monthly, Ridge.brand, width = 2.5.dp)
                ridgeDots(g, v.estimates, Ridge.brand, radius = 3.dp, alpha = { _, _ -> 0.6f })
                // B2-3-2: the latest estimate is a ring, the board's "today".
                val last = v.estimates.indexOfLast { it != null }
                if (last >= 0) ridgeEndRing(g, last, v.estimates[last]!!, Ridge.brand)
            }
            Legend(
                listOfNotNull(
                    Swatch.Dot(Ridge.brand.copy(alpha = 0.6f)) to stringResource(R.string.baseline_vo2_weekly),
                    Swatch.Line(Ridge.brand) to stringResource(R.string.baseline_vo2_monthly),
                    if (v.typical != null && chronoAge != null) Swatch.Square(Ridge.brand.copy(alpha = 0.25f)) to stringResource(R.string.baseline_vo2_typical, chronoAge) else null,
                ),
            )
        }
        m.maxHr?.let { hr ->
            DashedLine(
                stringResource(if (m.maxHrMeasured) R.string.baseline_maxhr_measured else R.string.baseline_maxhr_est, hr.roundToInt().toString()),
                stringResource(R.string.baseline_maxhr_set),
                onOpenSettings,
            )
        }
    }
}

/** The board's dashed "Zones use estimated max HR 188 · Set measured max ›" banner (B2-3-2); it opens Settings. */
@Composable
private fun DashedLine(fact: String, action: String, onClick: () -> Unit) {
    val stroke = Ridge.glassStroke
    val radius = Ridge.radiusMd
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(radius))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
            .padding(horizontal = Ridge.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fact, style = TRN_LEGEND, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.baseline_action_more, action), style = TRN_LEGEND.copy(fontWeight = FontWeight.SemiBold), color = Ridge.brand)
    }
}

/** The estimate nearest slot [i] within a week, so a scrub over the gaps between weekly estimates still reads one. */
private fun nearestEstimate(xs: List<Double?>, i: Int): Int? =
    (0..7).asSequence().flatMap { d -> sequenceOf(i - d, i + d) }.firstOrNull { it in xs.indices && xs[it] != null }

// MARK: - Long-run heart

private val LONG_RUN_PLOT_HEIGHT = 135.dp

/** Resting HR and HRV over the range, both in one card. */
@Composable
internal fun LongRunHeartCard(m: BaselineModel) {
    val rhrTitle = when (m.rhr.position) {
        NormalPosition.ABOVE -> stringResource(R.string.trends_ridge_rhr_above)
        NormalPosition.BELOW -> stringResource(R.string.trends_ridge_rhr_below)
        else -> null
    }
    val hrvTitle = when (m.hrv.position) {
        NormalPosition.ABOVE -> stringResource(R.string.trends_ridge_hrv_above)
        NormalPosition.BELOW -> stringResource(R.string.trends_ridge_hrv_below)
        else -> null
    }
    val title = when {
        rhrTitle != null && hrvTitle != null -> stringResource(R.string.trends_ridge_join, rhrTitle, hrvTitle)
        rhrTitle != null -> rhrTitle
        hrvTitle != null -> hrvTitle
        m.rhr.position == NormalPosition.NO_BAND && m.hrv.position == NormalPosition.NO_BAND ->
            stringResource(R.string.trends_ridge_heart_building)
        else -> stringResource(R.string.trends_ridge_heart_within)
    }
    TrendCard(
        eyebrow = stringResource(R.string.baseline_heart_eyebrow, rangeWord(m.range)),
        title = title,
        sub = stringResource(R.string.baseline_heart_sub),
    ) {
        LongRunPlot(m, m.rhr, Ridge.dataHeart, stringResource(R.string.trends_ridge_rhr_label), R.string.trends_ridge_bpm, dates = false)
        LongRunPlot(m, m.hrv, Ridge.dataHrv, stringResource(R.string.trends_ridge_hrv_label), R.string.trends_ridge_ms, dates = true)
    }
}

@Composable
private fun LongRunPlot(m: BaselineModel, s: LongRunSeries, color: Color, label: String, unitRes: Int, dates: Boolean) {
    val unitFmt = stringResource(unitRes)
    val now = s.latest30?.let { unitFmt.format(whole(it)) } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = RidgeType.bodySm.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = color, modifier = Modifier.weight(1f))
            Text(
                s.band?.let { stringResource(R.string.baseline_heart_row_band, now, whole(it.start), whole(it.endInclusive)) } ?: now,
                style = TRN_LEGEND, color = Ridge.ink,
            )
        }
        if (s.nightly.count { it != null } < 2) {
            NeedsData(stringResource(R.string.trends_ridge_needs_days, s.nightly.count { it != null }), 60.dp)
            return@Column
        }
        val notTracked = stringResource(R.string.trends_ridge_not_tracked)
        val long = m.spanDays > 180
        val last = s.mean30.indexOfLast { it != null }
        RidgePlot(
            n = m.slots.size,
            axis = s.axis,
            // RS-3a (owner, 26 Nov): taller than the board's 60 dp, which read as flat lines; about the height of Trends'
            // Calories chart, with y labels and gridlines so the values can be read off it.
            height = LONG_RUN_PLOT_HEIGHT,
            yLabel = { it.roundToInt().toString() },
            // RS-3a: four of the longer "Aug 2026" labels over the long ranges; five touch now the y labels take their column.
            xLabels = if (dates) axisLabelSlots(m.slots.size, if (long) 4 else 5).map { it to slotLabel(m.slots[it], long) } else emptyList(),
            contentDescription = stringResource(R.string.baseline_heart_a11y, label, rangeWord(m.range), now),
            accent = color,
            scrubY = { s.mean30[it] ?: s.nightly[it] },
            tip = { i ->
                val v = s.mean30[i] ?: s.nightly[i]
                ScrubTip(if (v == null) notTracked else unitFmt.format(whole(v)), dayLabel(m.slots[i]))
            },
        ) { g ->
            s.band?.let { ridgeBand(g, it.start, it.endInclusive, color.copy(alpha = 0.08f)) }
            ridgeDots(g, s.nightly, color, radius = 2.dp, alpha = { _, _ -> 0.3f })
            ridgeLine(g, s.mean30, color, width = 2.5.dp)
            if (last >= 0) ridgeEndRing(g, last, s.mean30[last]!!, color)
        }
    }
}
