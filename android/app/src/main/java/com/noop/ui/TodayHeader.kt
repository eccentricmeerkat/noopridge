package com.noop.ui

import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeSheet
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import com.noop.ui.ridge.StrapOrbState
import com.noop.ui.ridge.StrapBatteryOrb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import com.noop.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Customize Today (#2110): section order and visibility, on the trailing edge of the wordmark row.
 *
 *  Icon-only at the shared header-control size. The glyph and the accessibility-label-instead-of-text
 *  treatment are iOS's (`slider.horizontal.3` in a `nativeLiquidGlassHeaderButton`), but the PLACEMENT
 *  deliberately is not: iOS keeps this in the header control cluster, and Android cannot afford it
 *  there. See the cluster comment in `LiquidTodayHeader` — Android's cluster already carries a sync
 *  chip iOS has no equivalent of, and lacks iOS's measured title fade, so a control added there is
 *  width taken straight from the day title.
 *
 *  Icon-only is what answers BOTH reports: it still reads as a control (#2008 found the old tertiary
 *  text unreadable as one) while no longer out-shouting the hero rings (#2110), and dropping the
 *  visible label retires the locale-width problem, since the word ran 3 characters in zh to 13 in fr.
 *
 *  No new string: the existing "Customize Today" title is the accessibility label, so this ships in
 *  every locale that already had it. */
@Composable
private fun CustomizeDisc(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // Level with the avatar / + / battery ring (shared size), so the cluster reads as one row.
            .size(HeaderClusterControl)
            .liquidPress(interaction)
            .clip(CircleShape)
            // The same translucent-white disc the + uses, rather than the frosted card surface #2010
            // reached for: the point is to look like its siblings, not like a call to action.
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = uiString(R.string.today_customize_title) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The accent quick-action "+" in the Today header's top-right. Moved off the bottom bar (now four clean
 * tabs) to balance the header and open the existing quick-action sheet. A small CONTAINED accent disc,  * the accented primary among an otherwise-neutral icon set, ~36dp, no float and no glow: a flat reset-blue
 * accent fill with a hairline rim, the "+" glyph in crisp white. Mirrors the iOS quick-action + (a glyph on
 * Circle().fill(StrandPalette.accent)).
 */
@Composable
private fun QuickActionDisc(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // Sit level with the avatar / battery ring in the liquid header cluster (shared size).
            .size(HeaderClusterControl)
            .liquidPress(interaction)
            .clip(CircleShape)
            // A translucent-white disc so the + reads on the day-of-sky like the rest of the liquid cluster,
            // with a crisp white glyph. Mirrors iOS LiquidAddButton (a "plus" on Circle().fill(.white@0.16)).
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = uiString(R.string.l10n_today_screen_quick_actions_e47e8042) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

// MARK: - Scoring-guide affordances (ⓘ + first-run card)

/**
 * The small ⓘ that opens the scoring guide. Used on the Charge ring and the Effort / Rest tiles.
 * [section] only tunes the accessibility label; the deep-link target is carried by [onClick]'s
 * call site. Icon-only, so it always carries a content description. [compact] shrinks the hit-target
 * for the tile headers (where a full 36dp button would crowd the fixed-height tile).
 */
@Composable
internal fun ScoreInfoButton(
    section: ScoreSection?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val label = section?.let { uiString(R.string.today_how_score_named, uiString(it.labelRes)) }
        ?: uiString(R.string.today_how_score_generic)
    val button = if (compact) 24.dp else Metrics.iconButton
    val glyph = if (compact) 16.dp else Metrics.iconSmall
    IconButton(onClick = onClick, modifier = modifier.size(button)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = label,
            tint = Palette.textTertiary,
            modifier = Modifier.size(glyph),
        )
    }
}

/**
 * One-time "New here?" card pointing first-run users at the scoring guide. A NoopCard in the Today
 * flow, never a dialog, with a primary "See how it works" action and a ✕ dismiss; both set the
 * seen-flag at the call site so the card never returns. Copy verbatim from the approved source.
 */
@Composable
private fun ScoringGuideIntroCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Palette.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(uiString(R.string.l10n_today_screen_new_here_b81f2e17), style = NoopType.headline, color = Palette.textPrimary)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(Metrics.iconButton)
                        .semantics { contentDescription = uiString(R.string.l10n_today_screen_dismiss_70afe9ef) },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = Palette.textTertiary,
                        modifier = Modifier.size(Metrics.iconSmall),
                    )
                }
            }
            Text(
                uiString(R.string.l10n_today_screen_see_how_charge_effort_and_rest_80243197),
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) {
                    Text(uiString(R.string.l10n_today_screen_see_how_it_works_f7b38f07), style = NoopType.captionNumber, color = Palette.accent)
                }
            }
        }
    }
}

// MARK: - Noop Ridge Today header
//
// The connect disc (only while the strap is away, #2169), the calendar (the day picker, now the only way to
// another day), the quick actions "+", and the strap battery ring (→ Devices), on the trailing edge over the
// photo. No wordmark, tagline or script line (SQ3-1). Android only for now: the iOS/macOS Today headers are
// unchanged.

/** The uniform diameter of the round Today-header controls — calendar, connect and the strap battery orb — so they sit
 *  level in the cluster. Single source of truth for all of them. SQ8a (A2): 31 dp (owner, 15 Nov 2026), 15% under the
 *  old 36 dp; glyphs and the number scale from the prototype's 37 dp. The touch target stays 48 dp (Compose widens a
 *  small clickable's hit area). */
internal val HeaderClusterControl = 31.dp

/** The prototype's header control diameter (Main.dc.html); glyph sizes are given against it and scaled to [HeaderClusterControl]. */
private const val PROTOTYPE_CONTROL_DP = 37f

@Composable
internal fun RidgeTodayHeader(
    /** B2-1a-1: the first greeting line ("Good evening," with a name, "Good evening" without) and the name under it. */
    greetingLead: String,
    greetingName: String,
    /** The tracked date line over the greeting, for the day on screen ("Wed – Nov 11"). */
    dateLine: String,
    dayTitle: String,
    humanDate: String,
    selectedDay: LocalDate,
    batteryPct: Double?,
    /** Whether the STRAP is the active device. Separate from [batteryPct] on purpose: that says what
     *  the control reads, this says whether the control should exist. A null percentage while the strap
     *  IS active means "connected, no reading yet" and is worth drawing; a strap that is not the active
     *  device has nothing to say and is not drawn at all. (#2208) */
    strapIsActiveDevice: Boolean,
    // #2169: whether a scan is already running, and how to ask for one. Null while connected.
    scanning: Boolean = false,
    onRescan: (() -> Unit)? = null,
    // C-02: the sync state rides the battery orb (Main.dc.html), which replaced the orb-card sync chip.
    sync: HeaderSyncStatus = HeaderSyncStatus.None,
    // SQ8a (A4): the orb's disconnected and charging states.
    connected: Boolean = true,
    charging: Boolean = false,
    onPickDay: (Int) -> Unit,
    onQuickActions: () -> Unit,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    // SQ8a (owner, 15 Nov 2026): the prototype's day-picker sheet, in place of the platform date dialog.
    if (showPicker) TodayDayPickerSheet(selectedDay = selectedDay, onPickDay = onPickDay, onClose = { showPicker = false })

    // B2-1a-1 (Main.dc.html header): the controls sit alone on the first row, right-aligned; below them the tracked date
    // line and the two-line greeting, 14 dp apart.
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ridge.space2, Alignment.End),
        ) {
            // SQ8a (A3): the calendar orb (Main.dc.html). The day on screen stays in the description, so TalkBack says
            // which day a tap would change.
            RidgeGlassOrb(
                icon = R.drawable.ic_ridge_calendar,
                tint = Ridge.headerIconActive,
                description = uiString(R.string.l10n_today_screen_daytitle_humandate_tap_to_pick_a_7e12ce96, dayTitle, humanDate),
                onClick = { showPicker = true },
            )
            // SQ8a: the quick-actions "+" is off the header (owner: not needed). Not drawn when the strap is not the
            // active device (#2208). The connect disc is folded into the orb: away, it shows Bluetooth and a tap scans.
            if (strapIsActiveDevice) {
                HeaderStrapOrb(
                    batteryPct = batteryPct, connected = connected, charging = charging, sync = sync,
                    scanning = scanning, onRescan = onRescan, onOpenDevices = onOpenDevices,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 2.dp)) {
            Text(
                dateLine.uppercase(Locale.getDefault()),
                style = RidgeType.eyebrow.copy(
                    fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.24.em, fontWeight = FontWeight.Medium,
                    shadow = Shadow(Ridge.greetingShadow.copy(alpha = 0.65f), Offset(0f, 2f), blurRadius = 18f),
                ),
                color = Ridge.greetingName.copy(alpha = 0.94f),
            )
            // Main.dc.html .np-greet-tone / .np-greet-name: Playfair 700 36/40, the first line a mint to gold sweep, the
            // name below in cream. With no name set the greeting is the sweep alone.
            val sweep = Brush.horizontalGradient(listOf(Ridge.brand, Ridge.stateGood, Ridge.stateModerate))
            // BS-x2-1 item 3: a soft, wide, darker shadow so the greeting reads over the bright fog on every
            // time-of-day photograph (the old 0.22 alpha / 6 px lift was too faint over the morning fog).
            val shadow = Shadow(Ridge.greetingShadow.copy(alpha = 0.6f), Offset(0f, 3f), blurRadius = 16f)
            val greetingStyle = RidgeType.titleLg.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, shadow = shadow)
            Column(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(greetingLead, style = greetingStyle.copy(brush = sweep), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (greetingName.isNotBlank()) {
                    Text(greetingName, style = greetingStyle, color = Ridge.greetingName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * SQ8a (owner, 15 Nov 2026): the Today day picker as the prototype draws it (Main.dc.html "Pick a day"): a [RidgeSheet]
 * with the title, the 04:00 rollover line, a week of days (oldest on the left, the chosen day in mint) and Done. The
 * platform dialog reached any past day, so arrows step a week back or forward; nothing after today is offered. A tap
 * moves Today to that day at once; Done closes.
 */
@Composable
private fun TodayDayPickerSheet(selectedDay: LocalDate, onPickDay: (Int) -> Unit, onClose: () -> Unit) {
    // The LOGICAL day (selectedDayOffset's anchor), so a picked day lands on the row the header labels.
    val anchor = remember { logicalDayNow() }
    var picked by remember { mutableStateOf(selectedDay) }
    var week by remember { mutableStateOf((ChronoUnit.DAYS.between(selectedDay, anchor).coerceAtLeast(0) / 7).toInt()) }
    val end = anchor.minusWeeks(week.toLong())
    val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
    val locale = Locale.getDefault()
    val rangeFmt = remember(locale) { java.time.format.DateTimeFormatter.ofPattern("d MMM", locale) }
    val fullFmt = remember(locale) { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale) }
    RidgeSheet(onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(uiString(R.string.day_picker_title), style = RidgeType.titleLg.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Ridge.ink)
            Text(uiString(R.string.day_picker_sub), style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.inkMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { week += 1 }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = uiString(R.string.trends_previous_week), tint = Ridge.ink)
                }
                Text(
                    uiString(R.string.day_picker_range, rangeFmt.format(days.first()), rangeFmt.format(days.last())),
                    style = RidgeType.bodySm, color = Ridge.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { week -= 1 }, enabled = week > 0) {
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = uiString(R.string.trends_next_week),
                        tint = if (week > 0) Ridge.ink else Ridge.inkSubtle.copy(alpha = 0.4f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { day ->
                    val on = day == picked
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (on) Ridge.dayPickOnFill else Ridge.dayPickOffFill)
                            .border(1.dp, if (on) Ridge.brand else Ridge.dayPickOffStroke, shape)
                            .clickable {
                                picked = day
                                onPickDay(ChronoUnit.DAYS.between(day, anchor).toInt().coerceAtLeast(0))
                            }
                            .padding(vertical = 10.dp)
                            .semantics {
                                contentDescription = fullFmt.format(day)
                                selected = on
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                            style = RidgeType.bodySm.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Ridge.ink, maxLines = 1,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            style = RidgeType.titleLg.copy(fontSize = 18.sp, lineHeight = 22.sp, fontFeatureSettings = "lnum"),
                            color = Ridge.ink,
                        )
                    }
                }
            }
            RidgeButton(text = uiString(R.string.l10n_today_screen_done_e9b450d1), onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * SQ8a (A3): a round glass header control (Main.dc.html's calendar): a 1 dp pale rim, a soft teal radial fill, a faint
 * inner glow and a small drop shadow, with a 20 dp glyph. SQ8b's Hub notification and alarm controls use it too.
 */
@Composable
internal fun RidgeGlassOrb(icon: Int, tint: Color, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(HeaderClusterControl)
            .liquidPress(interaction)
            .drawBehind {
                // box-shadow 0 2px 10px: a soft shadow just under the disc, half strength at the rim, gone 5 dp out.
                val r = size.minDimension / 2f
                val outer = r + 5.dp.toPx()
                val under = center.copy(y = center.y + 2.dp.toPx())
                val rim = Ridge.headerDiscShadow.copy(alpha = Ridge.headerDiscShadow.alpha * 0.5f)
                drawCircle(
                    Brush.radialGradient(0f to rim, r / outer to rim, 1f to Color.Transparent, center = under, radius = outer),
                    radius = outer, center = under,
                )
            }
            .clip(CircleShape)
            .drawBehind {
                val r = size.minDimension / 2f
                // radial-gradient(70% 70% at 50% 55%, centre, mid 70%, edge).
                drawRect(
                    Brush.radialGradient(
                        0f to Ridge.headerDiscCentre, 0.7f to Ridge.headerDiscMid, 1f to Ridge.headerDiscEdge,
                        center = Offset(size.width / 2f, size.height * 0.55f), radius = size.width * 0.7f,
                    ),
                )
                // inset 0 0 12px: a faint glow from the rim inwards.
                drawCircle(
                    Brush.radialGradient(
                        (1f - 12.dp.toPx() / r).coerceIn(0f, 1f) to Color.Transparent, 1f to Ridge.headerDiscInnerGlow,
                        center = center, radius = r,
                    ),
                    radius = r,
                )
            }
            .border(1.dp, Ridge.headerDiscRim, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // 20 dp inside the prototype's 37 dp disc, scaled with it.
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp * (HeaderClusterControl.value / PROTOTYPE_CONTROL_DP)))
    }
}

/** #245: compact sync-status chip for the Today top bar, shown to EVERY user. The full-width
 *  SyncingHistoryNote is gated on `recovery == null`, so an established user (and especially a WHOOP 5/MG
 *  owner, whose history offloads are rare) saw no sync feedback on Today. THREE states so the ABSENCE of
 *  active syncing reads as "caught up", not "missing indicator" (the real #245 confusion): actively
 *  offloading → ⟳ N; idle with a known last-sync → ✓ Xm; a 5/MG whose history sync is experimental
 *  (live-connected, no completed offload yet) → ✓ live. Nothing shows only on a true cold start (the
 *  building-scores note owns that). Twin of iOS SyncStatusChip. DRAFT (#245): final styling/wording TBD. */
@Composable
internal fun SyncStatusChip(
    backfilling: Boolean,
    chunks: Int,
    lastSyncAt: Long?,
    historySyncExperimental: Boolean,
    pagesBehind: Int? = null,
    // #2169: the chip is the one thing in the header that reports sync state and took no tap at all,
    // so asking it for a sync is the gesture a reader tries first. Routed through the caller rather
    // than calling connect() here, because the permission gate has to come first: on Android 12+ a
    // direct connect() silently does nothing when the Bluetooth permission was denied or revoked (#1).
    // Null leaves the chip exactly as it was, which is what the other callers of it want.
    scanning: Boolean = false,
    onRescan: (() -> Unit)? = null,
) {
    // The clock and the translated "now" word are resolved HERE, in the composable that already depends
    // on both, and handed down — so `SyncChipState.resolve` stays a genuinely pure decision that a plain
    // JVM unit test can call with no attached Application. Reading the clock at composition time (rather
    // than snapshotting it) is unchanged behaviour: `shortSyncAgo` did exactly this on every recomposition.
    val state = SyncChipState.resolve(
        backfilling = backfilling,
        chunks = chunks,
        lastSyncAtSec = lastSyncAt,
        historySyncExperimental = historySyncExperimental,
        nowSec = System.currentTimeMillis() / 1000L,
        pagesBehind = pagesBehind,
    )
    // Offered only when there is an action to run and no scan already running, the same rule the
    // Settings and Live buttons follow. `Syncing` deliberately keeps its tap: a sync in flight is not
    // a scan in flight, and asking to reconnect mid-backfill is a reasonable thing to want.
    val tap = onRescan?.takeIf { !scanning }
    when (state) {
        is SyncChipState.Syncing -> {
            // Both counts are inflected, so "1 chunk" and "1 page" read correctly. Android <plurals>
            // inflects one quantity per resource, so each count resolves separately and a template
            // sentence joins them — the joiner sits inside the template, never on a resource edge,
            // because aapt2 strips edge whitespace (#2041).
            val chunksText = uiPlural(R.plurals.sync_chip_chunks_count, state.chunks, state.chunks)
            val pagesText = state.pagesBehind?.let {
                uiPlural(R.plurals.sync_chip_pages_behind_count, it, it)
            }
            ChipCapsule(
                Icons.Filled.Autorenew, "${state.chunks}", Ridge.brand,
                desc = if (pagesText != null) {
                    uiString(R.string.l10n_today_screen_sync_chip_syncing_pages_desc_ad763b39, chunksText, pagesText)
                } else {
                    uiString(R.string.l10n_today_screen_sync_chip_syncing_desc_92daf60c, chunksText)
                },
                detail = pagesText,
                onClick = tap,
            )
        }
        is SyncChipState.Synced -> ChipCapsule(
            Icons.Filled.Check, state.agoText, Ridge.inkMuted,
            uiString(R.string.l10n_today_screen_sync_chip_synced_desc_4d255944, state.agoText),
            onClick = tap)
        SyncChipState.ExperimentalLive -> ChipCapsule(
            Icons.Filled.Check, uiString(R.string.l10n_today_screen_sync_chip_live_98aadb37), Ridge.inkMuted,
            uiString(R.string.l10n_today_screen_sync_chip_experimental_desc_3de06a70),
            onClick = tap)
        SyncChipState.Hidden -> Unit
        // cold start — render nothing; the building-scores note covers it.
    }
}

/** The shared sync-chip capsule (icon + terse label). Twin of the iOS `SyncStatusChip.chip`. Drawn as
 *  the Noop Ridge live chip: strong glass, a hairline, tracked uppercase text. */
@Composable
private fun ChipCapsule(
    icon: ImageVector,
    text: String,
    tint: Color,
    desc: String,
    detail: String? = null,
    // #2169: a second way to the same action as the disc, on the one header element that already
    // reports sync state and took no tap at all. Costs no width, and null leaves the pill inert.
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            // Match the round header controls' height so the pill sits the same size in the cluster
            // (#1207 follow-up); width stays content-driven. Content centres via the fixed height.
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(Ridge.glassFillStrong)
            .border(1.dp, Ridge.glassStroke, RoundedCornerShape(50))
            // After the clip, so the ripple stays inside the capsule.
            // onClickLabel rather than a contentDescription: the icon already describes the STATE
            // ("synced 3 minutes ago"), and overriding that would trade the information for the action.
            // A click label names the action alongside it instead of in place of it.
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClickLabel = uiString(R.string.l10n_today_screen_scan_and_connect_40157030),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Ridge.space3),
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text.uppercase(Locale.getDefault()),
            style = RidgeType.eyebrow.copy(letterSpacing = 0.16.em),
            color = Ridge.inkMuted,
            maxLines = 1,
        )
        // #689/#815: the optional secondary line (the backlog figure). Dimmer than the count it follows,
        // because the count is the live thing and this is a fixed sample taken at connect. maxLines = 1
        // with ellipsis so a long localisation cannot push the header cluster off a narrow screen
        // (#1835); the accessibility description carries the full sentence either way.
        if (detail != null) {
            Text(
                detail,
                style = RidgeType.eyebrow.copy(letterSpacing = 0.16.em),
                color = Ridge.inkSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * SQ8a (A4): the header's strap battery orb ([StrapBatteryOrb], 31 dp), tap → Devices, or scan and connect while the
 * strap is away (owner, 15 Nov 2026: the Bluetooth disc is folded in). Disconnected, charging, the level
 * with its number, or a plain orb while a connected strap has no reading yet. Syncing spins an arc inside the ring; the
 * old "synced" dot is gone. TalkBack reads the state and the destination, then the sync line as the state description.
 */
@Composable
private fun HeaderStrapOrb(
    batteryPct: Double?,
    connected: Boolean,
    charging: Boolean,
    sync: HeaderSyncStatus,
    scanning: Boolean,
    onRescan: (() -> Unit)?,
    onOpenDevices: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pct = batteryPct?.roundToInt()
    val state = when {
        !connected -> StrapOrbState.DISCONNECTED
        charging -> StrapOrbState.CHARGING
        pct != null -> StrapOrbState.LEVEL
        else -> StrapOrbState.UNKNOWN
    }
    // Away with a scan on offer, the orb is the connect control the header's Bluetooth disc used to be (#2169).
    val scanTap = if (state == StrapOrbState.DISCONNECTED) onRescan else null
    val description = when (state) {
        StrapOrbState.DISCONNECTED -> when {
            scanTap != null && scanning -> uiString(R.string.sq8_strap_orb_scanning)
            scanTap != null -> uiString(R.string.sq8_strap_orb_disconnected_scan)
            else -> uiString(R.string.sq8_strap_orb_disconnected)
        }
        StrapOrbState.CHARGING -> uiString(R.string.sq8_strap_orb_charging)
        StrapOrbState.LEVEL -> uiString(R.string.sq8_strap_orb_level, pct ?: 0)
        StrapOrbState.UNKNOWN -> uiString(R.string.sq8_strap_orb_unknown)
    }
    val syncLine = sync.description
    StrapBatteryOrb(
        pct = pct,
        state = state,
        size = HeaderClusterControl,
        syncing = sync is HeaderSyncStatus.Syncing,
        scanning = scanning,
        modifier = Modifier
            .liquidPress(interaction)
            .clickable(
                interactionSource = interaction, indication = null,
                // While a scan runs a second tap would do nothing, as the old disc's did not.
                enabled = !(scanTap != null && scanning),
                onClick = scanTap ?: onOpenDevices,
            )
            .semantics {
                contentDescription = description
                if (syncLine != null) stateDescription = syncLine
            },
    )
}

/** The header's sync state (C-02): what the orb-card sync chip used to show, now carried by the battery orb. */
internal sealed interface HeaderSyncStatus {
    val description: String?
    data class Syncing(override val description: String) : HeaderSyncStatus
    data class Synced(override val description: String) : HeaderSyncStatus
    data object None : HeaderSyncStatus { override val description: String? = null }
}

/** Resolves [HeaderSyncStatus] with the SAME [SyncChipState] rule and strings the sync chip used. */
@Composable
internal fun rememberHeaderSyncStatus(
    backfilling: Boolean,
    chunks: Int,
    lastSyncAt: Long?,
    historySyncExperimental: Boolean,
    pagesBehind: Int? = null,
): HeaderSyncStatus {
    val state = SyncChipState.resolve(
        backfilling = backfilling,
        chunks = chunks,
        lastSyncAtSec = lastSyncAt,
        historySyncExperimental = historySyncExperimental,
        nowSec = System.currentTimeMillis() / 1000L,
        pagesBehind = pagesBehind,
    )
    return when (state) {
        is SyncChipState.Syncing -> {
            val chunksText = uiPlural(R.plurals.sync_chip_chunks_count, state.chunks, state.chunks)
            val pagesText = state.pagesBehind?.let { uiPlural(R.plurals.sync_chip_pages_behind_count, it, it) }
            HeaderSyncStatus.Syncing(
                if (pagesText != null) {
                    uiString(R.string.l10n_today_screen_sync_chip_syncing_pages_desc_ad763b39, chunksText, pagesText)
                } else {
                    uiString(R.string.l10n_today_screen_sync_chip_syncing_desc_92daf60c, chunksText)
                },
            )
        }
        is SyncChipState.Synced ->
            HeaderSyncStatus.Synced(uiString(R.string.l10n_today_screen_sync_chip_synced_desc_4d255944, state.agoText))
        SyncChipState.ExperimentalLive ->
            HeaderSyncStatus.Synced(uiString(R.string.l10n_today_screen_sync_chip_experimental_desc_3de06a70))
        SyncChipState.Hidden -> HeaderSyncStatus.None
    }
}

