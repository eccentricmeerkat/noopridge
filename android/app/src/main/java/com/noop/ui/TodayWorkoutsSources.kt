package com.noop.ui

import com.noop.data.SleepSession
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeSectionHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.noop.R
import com.noop.data.WorkoutRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Today footer sections

@Composable
internal fun TodayWorkoutsSection(
    day: DayActivity?,
    onSelect: (WorkoutRow) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenWorkouts: () -> Unit,
) {
    // C-06 (Main.dc.html "Today · Activities"): the selected day's activities only, product owner C06-1.
    // Sleep counts as an activity, so a day with a night is never empty. Chronological, sleep first.
    val activities = day?.let { todayActivities(it) }.orEmpty()
    if (activities.isEmpty()) return

    RidgeSectionHeader(
        title = stringResource(R.string.today_activities),
    ) {
        Text(
            stringResource(R.string.today_all_workouts) + " ›",
            style = RidgeType.bodySm,
            color = Ridge.brand,
            modifier = Modifier
                .clip(RoundedCornerShape(Ridge.radiusSm))
                .clickable(onClick = onOpenWorkouts)
                .padding(vertical = Ridge.space2),
        )
    }
    // #1694: the feed was read-only, so the only way to a session's detail was More > Workouts. The
    // tap opens the SAME read-only WorkoutDetailSheet the Workouts list uses — nothing there can edit
    // or delete, so a tap from Today carries no risk that list does not already carry.
    // Noop Ridge: the feed is ListRows inside one neutral glass card, hairlines between rows.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ridge.radiusLg))
            .ridgeGlass(radius = Ridge.radiusLg)
            .padding(horizontal = Ridge.space5, vertical = Ridge.space1),
    ) {
        activities.forEachIndexed { i, activity ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(Metrics.divider).background(Ridge.glassStroke))
            }
            val workout = (activity as? TodayActivity.Workout)?.row
            if (workout == null) {
                when (activity) {
                    is TodayActivity.Nap -> ActivitySleepRow(activity.session, onOpenSleep, nap = true)
                    is TodayActivity.Sleep -> ActivitySleepRow(activity.session, onOpenSleep)
                    is TodayActivity.Workout -> Unit
                }
                return@forEachIndexed
            }
            val interaction = remember(workout.startTs, workout.source) { MutableInteractionSource() }
            val meta = listOfNotNull(
                workoutCaption(workout),
                localizedMetricValue(workoutDuration(workout)),
                workout.energyKcal?.let { "${it.roundToInt()} kcal" },
            ).joinToString("  ·  ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidPress(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = uiString(R.string.today_action_show_workout),
                        onClick = { onSelect(workout) },
                    )
                    .padding(vertical = ACTIVITY_ROW_PAD),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ridge.space4),
            ) {
                ActivityTile(sportIcon(workout.sport), Ridge.effort)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        WorkoutEditing.displaySport(workout.sport),
                        style = RidgeType.listTitle,
                        color = Ridge.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(meta, style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Ridge.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TodaySourcesSection(
    footer: TodayFooterState,
    strapBatteryPct: Int? = null,
    strapBatteryEstimate: String? = null,
    // S5: collapse to a single "Synced from: ..." summary line by default; tapping expands the full
    // per-source rows + strap battery inline. Nothing is removed, only folded behind a tap.
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
) {
    SectionHeader(uiString(R.string.today_data_sources), overline = uiString(R.string.today_provenance))
    Spacer(Modifier.height(Metrics.gap))
    val whoopPresent = (footer.whoopDays ?: 0) > 0 || strapBatteryPct != null
    val applePresent = (footer.appleDays ?: 0) > 0 || (footer.appleWorkouts ?: 0) > 0
    val hcPresent = (footer.hcDays ?: 0) > 0 || (footer.hcWorkouts ?: 0) > 0
    if (!expanded) {
        // Collapsed: one tappable "Synced from: ..." line. Each source is named for what it is —
        // Health Connect must NOT fold under "Apple Watch" (issue #176: Health-Connect-only users
        // saw "Synced from: Apple Watch"); the expanded card lists every source by name too.
        val collapsedInteraction = remember { MutableInteractionSource() }
        NoopCard(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPress(collapsedInteraction)
                .clickable(
                    interactionSource = collapsedInteraction,
                    indication = null,
                    onClickLabel = uiString(R.string.today_action_show_sources),
                    onClick = onToggle,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    syncedFromSummary(hasWhoop = whoopPresent, hasApple = applePresent, hasHealthConnect = hcPresent, hasXiaomi = false)
                        .let { names ->
                            if (names.isEmpty()) uiString(R.string.today_no_sources_yet)
                            else uiString(R.string.today_synced_from, names.localizedSourceList())
                        },
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Palette.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        return
    }
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // A header row to collapse it back, an obvious "less" cue on the expanded card.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = uiString(R.string.today_action_hide_sources), onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(uiString(R.string.l10n_today_screen_synced_from_2aa7258b), style = NoopType.overline, color = Palette.textTertiary, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Palette.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Palette.hairline))
            SourceRow(
                badge = uiString(R.string.today_source_whoop),
                tint = Palette.accent,
                // A live battery reading means the strap IS connected, even before the first banked
                // night, don't contradict it with "Not connected" (#159).
                present = whoopPresent,
                detail = countDetail(footer.whoopDays, footer.whoopWorkouts),
                batteryPct = strapBatteryPct,
                batteryEstimate = strapBatteryEstimate,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.hairline),
            )
            SourceRow(
                badge = uiString(R.string.today_source_apple_health),
                tint = Palette.metricCyan,
                present = applePresent,
                detail = countDetail(footer.appleDays, footer.appleWorkouts),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.hairline),
            )
            SourceRow(
                badge = uiString(R.string.today_source_health_connect),
                tint = Palette.metricPurple,
                present = hcPresent,
                detail = countDetail(footer.hcDays, footer.hcWorkouts),
            )
        }
    }
}

@Composable
private fun SourceRow(
    badge: String,
    tint: Color,
    present: Boolean,
    detail: String,
    batteryPct: Int? = null,
    batteryEstimate: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceBadge(badge, tint = if (present) tint else Palette.textTertiary)
        // Compact strap-battery readout beside the source badge, same pill + tone bands as the
        // Settings Strap section; absent entirely when there's no live reading (#159).
        batteryPct?.let { pct ->
            Spacer(Modifier.width(8.dp))
            StatePill(title = uiString(R.string.l10n_today_screen_pct_ee63e247, pct), tone = batteryPillTone(pct), showsDot = false)
            // The "~X left" runtime estimate sits beside the %, dimmer, only when we have a trusted one (#713).
            batteryEstimate?.let { est ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = est,
                    style = NoopType.captionNumber,
                    color = Palette.textTertiary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (present) detail else uiString(R.string.today_not_connected),
            style = NoopType.captionNumber,
            color = if (present) Palette.textSecondary else Palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val workoutDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault())
private val workoutTimeFmt: DateTimeFormatter =
    // Respect the device's 12-/24-hour locale (#337): "7:10 AM" where 12-hour is preferred, "19:10"
    // where 24-hour is, instead of forcing 24-hour on everyone.
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())

@Composable
private fun countDetail(days: Int?, workouts: Int?): String {
    if (days == null || workouts == null) return uiString(R.string.today_source_counting)
    return uiString(
        R.string.today_source_count_joiner,
        uiPlural(R.plurals.today_source_days, days, todayGrouped(days)),
        uiPlural(R.plurals.today_source_workouts, workouts, todayGrouped(workouts)),
    )
}

/** Same bands as the Settings Strap battery pill, so the % reads the same colour everywhere (#159). */
private fun batteryPillTone(pct: Int): StrandTone = when {
    pct <= 15 -> StrandTone.Critical
    pct <= 30 -> StrandTone.Warning
    else -> StrandTone.Positive
}

private fun workoutDuration(row: WorkoutRow): String {
    val seconds = row.durationS ?: (row.endTs - row.startTs).coerceAtLeast(0L).toDouble()
    if (seconds <= 0.0) return NO_DATA
    val totalMinutes = (seconds / 60.0).roundToInt()
    return if (totalMinutes >= 60) {
        "${totalMinutes / 60}h ${totalMinutes % 60}m"
    } else {
        "${totalMinutes}m"
    }
}

/** "d MMM · HH:mm–HH:mm" (#157); start-only when the end isn't after the start (zero/unknown span). */
private fun workoutCaption(row: WorkoutRow): String {
    val date = workoutDateFmt.format(Instant.ofEpochSecond(row.startTs))
    val start = workoutTimeFmt.format(Instant.ofEpochSecond(row.startTs))
    return if (row.endTs > row.startTs) {
        "$date · $start - ${workoutTimeFmt.format(Instant.ofEpochSecond(row.endTs))}"
    } else {
        "$date · $start"
    }
}

/** One Today activity (C-06): the day's main sleep, a nap (SQ5) or one workout. */
internal sealed interface TodayActivity {
    val startTs: Long
    data class Sleep(val session: SleepSession) : TodayActivity { override val startTs: Long get() = session.startTs }
    data class Nap(val session: SleepSession) : TodayActivity { override val startTs: Long get() = session.effectiveStartTs }
    data class Workout(val row: WorkoutRow) : TodayActivity { override val startTs: Long get() = row.startTs }
}

/** The selected day's activities, oldest first: its main sleep (if any), its naps (SQ5) and the workouts that START
 *  inside the day window, deduplicated across sources like the old feed ([WorkoutEditing.dedupCrossSource]). Pure. */
internal fun todayActivities(day: DayActivity): List<TodayActivity> {
    val workouts = WorkoutEditing.dedupCrossSource(day.workouts.filter { it.startTs in day.start..day.end })
    return (
        listOfNotNull(day.sleep?.let { TodayActivity.Sleep(it) }) +
            day.naps.map { TodayActivity.Nap(it) } +
            workouts.map { TodayActivity.Workout(it) }
        ).sortedBy { it.startTs }
}

/** B2-1b (owner, 19 Nov 2026): the Activities rows are a few dp shorter than the 76 dp they were (tile 52 to 44 dp, padding
 *  12 to 10 dp). */
private val ACTIVITY_ROW_PAD = 10.dp

/** The type-tinted icon tile of an Activities row (Main.dc.html: a 10% fill and a 35% edge in the type's colour). */
@Composable
private fun ActivityTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val shape = RoundedCornerShape(Ridge.radiusSm)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** The sleep row in the Activities feed: moon tile, "Sleep", "10:54 PM - 5:55 AM · 7h 1m"; opens Sleep. With [nap]
 *  (SQ5) the same row reads "Nap" with the nap's own window, from its effective onset. */
@Composable
private fun ActivitySleepRow(sleep: SleepSession, onOpenSleep: () -> Unit, nap: Boolean = false) {
    val from = if (nap) sleep.effectiveStartTs else sleep.startTs
    val minutes = ((sleep.endTs - from).coerceAtLeast(0L) / 60).toInt()
    val duration = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    val span = workoutTimeFmt.format(Instant.ofEpochSecond(from)) + " - " +
        workoutTimeFmt.format(Instant.ofEpochSecond(sleep.endTs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.nav_sleep), onClick = onOpenSleep)
            .padding(vertical = ACTIVITY_ROW_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space4),
    ) {
        ActivityTile(RidgeIcons.moon, if (nap) Ridge.dataHrv else Ridge.rest)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(if (nap) R.string.today_activity_nap else R.string.nav_sleep), style = RidgeType.listTitle, color = Ridge.ink, maxLines = 1)
            Text(listOf(span, localizedMetricValue(duration)).joinToString("  ·  "), style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 2)
        }
        Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.inkMuted, modifier = Modifier.size(20.dp))
    }
}
