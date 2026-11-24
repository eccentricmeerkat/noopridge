package com.noop.ui

import androidx.annotation.StringRes
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.noop.ui.ridge.StrapOrbState
import com.noop.ui.ridge.StrapBatteryOrb
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.HydrationStore
import com.noop.ble.WhoopModel
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeBackButton
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeListRow
import java.text.NumberFormat
import kotlin.math.roundToInt

// MARK: - Ridge Hub (R7a-1: A-01 Hub, G-01, `More.dc.html`)
//
// The Hub replaces v10's More page under the Ridge shell: the header, three tiles (Profile, AI coach, Device) and the
// board's four groups. Insights opens the Insights page (R7b). Rows that bundle several v10 screens open a sub-list (`hub/{section}`), so every v10
// destination is still one or two taps away; [hubReachable] lists them for the test that keeps it so.

/** Where a Hub row leads: a v10 screen, a Hub sub-list, or one of the full-screen sheets. */
internal sealed interface HubTarget {
    data class Screen(val dest: Destination) : HubTarget
    data class Section(val key: HubSection) : HubTarget
    data class Sheet(val kind: HubSheet) : HubTarget
}

/** The three pages that were full-screen popups; each is a Hub route now (SQ9 S1), [route] is `hub/sheet/<key>`. */
internal enum class HubSheet(val key: String) {
    SCORING_GUIDE("scoring"), HOW_NOOP_WORKS("how"), WHATS_NEW("new");

    val route: String get() = "hub/sheet/$key"
}

/**
 * The Hub sub-lists. [route] is `hub/<key>`. [eyebrowRes] is the small line over the title: the Hub's own name by
 * default, the group the page came from where the board names it (`BodyMore.dc.html` reads "Body" over "More").
 */
internal enum class HubSection(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val eyebrowRes: Int = R.string.nav_hub,
) {
    BREATHE("breathe", R.string.hub_breathe_title),
    BODY_MORE("body_more", R.string.hub_body_more_title, R.string.more_group_body),
    BODY_MONITOR("body_monitor", R.string.hub_body_monitor_title, R.string.more_group_body),
    YOUR_DATA("your_data", R.string.hub_your_data_title),
    AUTOMATIONS("automations", R.string.hub_automations_title),
    NOTIFICATIONS("notifications", R.string.hub_notifications_title),
    ABOUT("about", R.string.hub_about_title);

    val route: String get() = "hub/$key"

    companion object {
        fun of(key: String?): HubSection? = entries.firstOrNull { it.key == key }
    }
}

/** The route pattern the Hub sub-lists register under. */
internal const val HUB_SECTION_ROUTE = "hub/{section}"

/**
 * One Hub row: its strings, where it leads, and its menu icon — the board's own `.np-row` glyph (`More.dc.html`,
 * `BodyMore.dc.html`). [iconTint] takes the row's liquid or data hue where the board gives the row one, and stays
 * ink-muted otherwise. [subtitle] is a subtitle computed when the list is built (today's hydration total, `1,250 ml
 * today`), which wins over the static [subtitleRes]; the icon is decorative — the row's [titleRes] names it.
 */
internal data class HubRow(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val target: HubTarget,
    val icon: ImageVector? = null,
    val iconTint: Color = Ridge.inkMuted,
    val subtitle: String? = null,
)

internal data class HubGroup(@StringRes val headerRes: Int, val rows: List<HubRow>)

/** The Hub's groups (`More.dc.html`), each row with the board's icon and hue. [devicesSub] names the connected
 *  strap on the Devices row (\"WHOOP 4.0\"); null leaves the row's static \"Straps and sensors\" subtitle. */
internal fun ridgeHubGroups(devicesSub: String? = null): List<HubGroup> = listOf(
    HubGroup(
        R.string.more_group_insights,
        listOf(
            HubRow(
                R.string.hub_journal_title, R.string.hub_journal_sub, HubTarget.Screen(Destination.Insights),
                RidgeIcons.journal, Ridge.dataEnergy,
            ),
            HubRow(
                R.string.nav_insights_hub, R.string.hub_wmy_sub, HubTarget.Screen(Destination.InsightsHub),
                RidgeIcons.trendUp, Ridge.brand,
            ),
            HubRow(
                R.string.nav_insights, R.string.hub_insights_sub, HubTarget.Screen(Destination.Explore),
                RidgeIcons.bars, Ridge.effort,
            ),
        ),
    ),
    HubGroup(
        R.string.more_group_body,
        listOf(
            HubRow(
                R.string.nav_live, R.string.hub_live_sub, HubTarget.Screen(Destination.Live),
                RidgeIcons.pulse, Ridge.dataHeart,
            ),
            HubRow(
                R.string.nav_workouts, R.string.hub_workouts_sub, HubTarget.Screen(Destination.Workouts),
                RidgeIcons.dumbbell, Ridge.effort,
            ),
            HubRow(
                R.string.hub_breathe_title, R.string.hub_breathe_sub, HubTarget.Section(HubSection.BREATHE),
                RidgeIcons.wind, Ridge.dataBreath,
            ),
            // BodyMonitor.dc.html: Stress moves behind Body monitor (Stress and vital signs).
            HubRow(
                R.string.hub_body_monitor_row, R.string.hub_body_monitor_sub, HubTarget.Section(HubSection.BODY_MONITOR),
                RidgeIcons.heart, Ridge.dataHeart,
            ),
            HubRow(
                R.string.hub_body_more_title, R.string.hub_body_more_sub, HubTarget.Section(HubSection.BODY_MORE),
                RidgeIcons.ellipsis,
            ),
        ),
    ),
    HubGroup(
        R.string.more_group_data,
        listOf(
            HubRow(
                R.string.nav_devices, R.string.hub_devices_sub, HubTarget.Screen(Destination.Devices),
                RidgeIcons.strap, Ridge.battery, subtitle = devicesSub,
            ),
            HubRow(
                R.string.hub_your_data_title, R.string.hub_your_data_sub, HubTarget.Screen(Destination.YourData),
                RidgeIcons.database,
            ),
        ),
    ),
    HubGroup(
        R.string.more_group_app,
        listOf(
            HubRow(
                R.string.nav_settings, R.string.hub_settings_sub, HubTarget.Screen(Destination.Settings),
                RidgeIcons.sliders,
            ),
            HubRow(
                R.string.hub_automations_title, R.string.hub_automations_sub, HubTarget.Screen(Destination.Automations),
                RidgeIcons.bolt,
            ),
            HubRow(
                R.string.hub_notifications_title, R.string.hub_notifications_sub,
                HubTarget.Screen(Destination.Notifications), RidgeIcons.bell,
            ),
            HubRow(
                R.string.hub_scoring_title, R.string.hub_scoring_sub, HubTarget.Sheet(HubSheet.SCORING_GUIDE),
                RidgeIcons.chartAxis,
            ),
            HubRow(
                R.string.hub_about_title, R.string.hub_about_sub, HubTarget.Screen(Destination.About),
                RidgeIcons.info,
            ),
        ),
    ),
)

/**
 * A sub-list's rows, grouped. Test Centre sits behind About › Advanced (UX-HUB-2).
 *
 * [hydrationSub] is the Hydration row's subtitle on `BodyMore.dc.html` — today's water total ("1,250 ml today"), or the
 * board's off line while tracking is disabled; null leaves that row without a subtitle. The list stays a pure function
 * of its arguments so the tests can read it with no Android context.
 */
internal fun hubSection(
    section: HubSection,
    hydrationSub: String? = null,
    stressSub: String? = null,
    vitalsSub: String? = null,
): List<HubGroup> = when (section) {
    HubSection.BODY_MONITOR -> listOf(
        HubGroup(
            R.string.hub_right_now,
            listOf(
                HubRow(R.string.nav_stress, null, HubTarget.Screen(Destination.Stress), RidgeIcons.wave, Ridge.stateModerate, subtitle = stressSub ?: "", ),
                HubRow(R.string.hub_monitor_vital_row, null, HubTarget.Screen(Destination.VitalSigns), RidgeIcons.heart, Ridge.dataHeart, subtitle = vitalsSub ?: ""),
            ),
        ),
    )
    HubSection.BREATHE -> listOf(
        HubGroup(
            R.string.hub_breathe_title,
            listOf(
                HubRow(R.string.nav_breathe, null, HubTarget.Screen(Destination.Breathe)),
                HubRow(R.string.nav_intervals, null, HubTarget.Screen(Destination.Intervals)),
            ),
        ),
    )
    // BodyMore.dc.html: one "Body tools" card, each row carrying the board's icon, hue and one-line subtitle.
    HubSection.BODY_MORE -> listOf(
        HubGroup(
            R.string.hub_body_tools,
            listOf(
                HubRow(
                    R.string.nav_lab_book, R.string.hub_lab_book_sub, HubTarget.Screen(Destination.LabBook),
                    RidgeIcons.flask, Ridge.dataHeart,
                ),
                HubRow(
                    R.string.nav_hydration, null, HubTarget.Screen(Destination.Hydration),
                    RidgeIcons.droplet, Ridge.dataBreath,
                    subtitle = hydrationSub,
                ),
                HubRow(
                    R.string.nav_rhythm, R.string.hub_rhythm_sub, HubTarget.Screen(Destination.Rhythm),
                    RidgeIcons.pulse, Ridge.dataHrv,
                ),
            ),
        ),
    )
    HubSection.YOUR_DATA -> listOf(
        HubGroup(
            R.string.hub_your_data_title,
            listOf(
                HubRow(R.string.nav_data_sources, null, HubTarget.Screen(Destination.DataSources)),
                HubRow(R.string.nav_apple_health, null, HubTarget.Screen(Destination.AppleHealth)),
                HubRow(R.string.nav_fused_record, null, HubTarget.Screen(Destination.FusedRecord)),
                HubRow(R.string.nav_backup_sync, null, HubTarget.Screen(Destination.BackupSync)),
            ),
        ),
    )
    HubSection.AUTOMATIONS -> listOf(
        HubGroup(
            R.string.hub_automations_title,
            listOf(
                HubRow(R.string.nav_automations, null, HubTarget.Screen(Destination.Automations)),
                HubRow(R.string.nav_alarms, null, HubTarget.Screen(Destination.SmartAlarm)),
            ),
        ),
    )
    HubSection.NOTIFICATIONS -> listOf(
        HubGroup(
            R.string.hub_notifications_title,
            listOf(
                HubRow(R.string.nav_notifications, null, HubTarget.Screen(Destination.Notifications)),
                HubRow(R.string.nav_power_saving, null, HubTarget.Screen(Destination.PowerSaving)),
            ),
        ),
    )
    HubSection.ABOUT -> listOf(
        HubGroup(
            R.string.hub_about_title,
            listOf(
                HubRow(R.string.hub_how_works, null, HubTarget.Sheet(HubSheet.HOW_NOOP_WORKS)),
                HubRow(R.string.hub_whats_new, null, HubTarget.Sheet(HubSheet.WHATS_NEW)),
                HubRow(R.string.nav_noop_limitations, null, HubTarget.Screen(Destination.NoopLimitations)),
            ),
        ),
        HubGroup(R.string.hub_advanced, listOf(HubRow(R.string.nav_test_centre, null, HubTarget.Screen(Destination.TestCentre)))),
    )
}

/** Every v10 screen the Hub reaches, directly or through a sub-list, plus the tiles' Settings, Coach and Devices. */
internal fun hubReachable(): Set<Destination> {
    val rows = ridgeHubGroups().flatMap { it.rows } + HubSection.entries.flatMap { s -> hubSection(s).flatMap { it.rows } }
    // Compare and Intelligence are modes of the Insights page (R7b), reached from Explore's header.
    return rows.mapNotNull { (it.target as? HubTarget.Screen)?.dest }.toSet() +
        setOf(Destination.Settings, Destination.Coach, Destination.Devices, Destination.Compare, Destination.Intelligence)
}

/**
 * The Hub page: "Everything else / Hub" with the notifications and alarm controls (SQ8b, B1), the three tiles (B4), then
 * the groups. [unreadCount] is the Updates inbox's; [onOpenNotifications] opens it as the notifications panel.
 */
@Composable
internal fun RidgeHubScreen(
    vm: AppViewModel,
    onNavigate: (String) -> Unit,
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val live by vm.live.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // An alarm is set when either the phone's guaranteed wake or the strap's buzz is on (the Alarms page's two switches).
    val strapAlarm by vm.smartAlarmEnabled.collectAsStateWithLifecycle()
    val alarmActive = strapAlarm || remember { com.noop.alarm.SmartAlarmStore.from(context).enabled }
    val firstName = remember { ProfileStore.from(context).firstName }
    // B2-5: the Devices row names the connected strap (\"WHOOP 4.0\" / \"WHOOP 5.0 / MG\") instead of the static
    // \"Straps and sensors\". The saved last-bonded model is the canonical answer; before a first bond the live 5/MG
    // flag disambiguates. Only while connected — otherwise the row falls back to the static subtitle.
    val devicesSub = if (live.connected) {
        NoopPrefs.lastDevice(context)?.second?.displayName
            ?: if (live.whoop5Detected) WhoopModel.WHOOP5_MG.displayName else WhoopModel.WHOOP4.displayName
    } else null
    HubScaffold(
        eyebrow = stringResource(R.string.hub_eyebrow),
        title = stringResource(R.string.nav_hub),
        actions = {
            RidgeGlassOrb(
                icon = R.drawable.ic_ridge_tower,
                tint = if (unreadCount > 0) Ridge.headerIconActive else Ridge.headerIconInactive,
                description = if (unreadCount > 0) {
                    androidx.compose.ui.res.pluralStringResource(R.plurals.hub_notif_unread, unreadCount, unreadCount)
                } else {
                    stringResource(R.string.hub_notif_none)
                },
                onClick = onOpenNotifications,
            )
            RidgeGlassOrb(
                icon = R.drawable.ic_ridge_alarm,
                tint = if (alarmActive) Ridge.headerIconActive else Ridge.headerIconInactive,
                description = stringResource(if (alarmActive) R.string.hub_alarm_on else R.string.hub_alarm_off),
                onClick = { onNavigate(Destination.SmartAlarm.route) },
            )
        },
    ) {
        item {
            // B4: three equal tiles, one height (at least 118 dp), so label and value sit on one baseline across the row.
            Row(
                Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
            ) {
                HubTile(
                    label = stringResource(R.string.hub_tile_profile),
                    value = firstName.ifBlank { stringResource(R.string.profile_ridge_empty_tile) },
                    modifier = Modifier.weight(1f),
                    icon = { ProfileInitial(firstName) },
                ) { onNavigate(Destination.Profile.route) }
                // The coach tile only while AI Coach is on (A-01: "AI Coach off → hide Sage").
                if (BottomBarStyleStore.coachEnabled) {
                    HubTile(
                        label = stringResource(R.string.hub_tile_coach),
                        value = stringResource(R.string.nav_coach),
                        modifier = Modifier.weight(1f),
                        valueColor = Ridge.brand,
                        stroke = Ridge.sageTileStroke,
                        icon = { SageWizard() },
                    ) { onNavigate(Destination.Coach.route) }
                }
                // B3: the shared strap orb at 34 dp; the percentage, or "Offline" with the Bluetooth orb.
                val pct = if (live.connected) live.batteryPct?.roundToInt() else null
                val state = when {
                    !live.connected -> StrapOrbState.DISCONNECTED
                    live.charging == true -> StrapOrbState.CHARGING
                    pct != null -> StrapOrbState.LEVEL
                    else -> StrapOrbState.UNKNOWN
                }
                HubTile(
                    label = stringResource(R.string.hub_tile_device),
                    value = when {
                        !live.connected -> stringResource(R.string.hub_tile_offline)
                        pct != null -> stringResource(R.string.hub_battery, pct)
                        else -> "—"
                    },
                    modifier = Modifier.weight(1f),
                    icon = { StrapBatteryOrb(pct, state, 34.dp) },
                ) { onNavigate(Destination.Devices.route) }
            }
        }
        hubGroupItems(ridgeHubGroups(devicesSub), onNavigate)
    }
}

/**
 * B2 Hub4 (owner edit): titles in the Hub menu capitalise each word ("Vital Signs", "Body Monitor"). English only: the
 * other languages keep their own casing rules. Short joining words stay lower case ("Backup and Sync").
 */
internal fun hubTitleCase(title: String): String {
    if (java.util.Locale.getDefault().language != "en") return title
    val small = setOf("and", "of", "the", "a", "to", "or", "in", "for")
    return title.split(" ").mapIndexed { i, w ->
        if (w.isEmpty() || (i > 0 && w in small)) w else w.replaceFirstChar { it.uppercaseChar() }
    }.joinToString(" ")
}

/** The Profile tile's avatar (More.dc.html): a 34 dp glass circle with the first name's initial, or a person glyph. */
@Composable
private fun ProfileInitial(firstName: String) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Ridge.glassFillStrong).border(1.dp, Ridge.glassStroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = firstName.trim().firstOrNull()?.uppercaseChar()
        if (initial != null) {
            Text(initial.toString(), style = RidgeType.titleLg.copy(fontSize = 16.sp, lineHeight = 18.sp), color = Ridge.ink)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = Ridge.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Sage's wizard (34 dp) with the prototype's soft lime glow (drop-shadow 0 0 6px rgba(198,247,92,0.4)). */
@Composable
private fun SageWizard() {
    androidx.compose.foundation.Image(
        painterResource(R.drawable.ridge_sage), contentDescription = null,
        modifier = Modifier.size(34.dp).drawBehind {
            drawCircle(
                Brush.radialGradient(0.45f to Ridge.sageGlow, 1f to Color.Transparent, center = center, radius = size.minDimension * 0.75f),
                radius = size.minDimension * 0.75f,
            )
        },
    )
}

/** "Settled · 0.8 of 3": the latest Stress day score and its band word, or the Stress row's plain line with no reading yet. */
@Composable
private fun monitorStressLine(days: List<com.noop.data.DailyMetric>): String {
    val latest = remember(days) {
        runCatching {
            val today = logicalDayNow()
            com.noop.metrics.V10MetricRegistry(com.noop.metrics.V10MetricInputs(days = days, today = today))
                .series(com.noop.metrics.MetricId.STRESS, com.noop.metrics.DayKey(today.minusDays(13)), com.noop.metrics.DayKey(today))
                .mapNotNull { it.value }.lastOrNull()
        }.getOrNull()
    }
    return if (latest == null) stringResource(R.string.hub_stress_sub)
    else stringResource(R.string.hub_monitor_stress, StressBand.forScore(latest).title.lowercase().replaceFirstChar { it.uppercase() }, String.format(java.util.Locale.US, "%.1f", latest))
}

/** "4 of 5 in your range" across the latest day's vitals that have a reading, or a plain line with none. */
@Composable
private fun monitorVitalsLine(days: List<com.noop.data.DailyMetric>): String {
    val context = LocalContext.current
    val vitals = remember(days) { ridgeLatestVitals(context, days) }
    val read = vitals.filter { it.banding.band != com.noop.analytics.VitalBands.Band.NO_DATA }
    return if (read.isEmpty()) stringResource(R.string.hub_monitor_vitals_none)
    else stringResource(R.string.hub_monitor_vitals, read.count { it.banding.band == com.noop.analytics.VitalBands.Band.IN_RANGE }, read.size)
}

/**
 * A Hub sub-list (`hub/{section}`): the board's page header — a back chevron to the Hub, the group's eyebrow over the
 * title (`BodyMore.dc.html`: "Body" over "More") — then its groups. The Hub's own page has no back chevron.
 */
@Composable
internal fun RidgeHubSectionScreen(section: HubSection, vm: AppViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    // BodyMore.dc.html gives the Hydration row a subtitle: today's total ("1,250 ml today"), or the off line while
    // hydration tracking is switched off (its default — it is opt-in). Hydration writes never touch the flows the Hub
    // collects, so re-read on the store's mutation counter (#989, as the Today card does) while tracking is on.
    val hydrationTracking = remember { NoopPrefs.hydrationTracking(context) }
    val hydrationSeq by HydrationStore.mutationSeq.collectAsStateWithLifecycle()
    var hydrationSub by remember(section) { mutableStateOf<String?>(null) }
    LaunchedEffect(section, hydrationTracking, hydrationSeq) {
        hydrationSub = when {
            section != HubSection.BODY_MORE -> null
            !hydrationTracking -> context.getString(R.string.hub_hydration_off)
            else -> runCatching { HydrationStore.total(vm.repo) }.getOrNull()?.let { ml ->
                context.getString(
                    R.string.hub_hydration_sub,
                    context.getString(
                        R.string.l10n_hydration_screen_totalml_toint_ml_522b262a,
                        NumberFormat.getIntegerInstance().format(ml.roundToInt()),
                    ),
                )
            }
        }
    }
    // BodyMonitor.dc.html's two rows each carry a live line: Stress's latest reading, and how many of the latest vitals sit in
    // their range. Both read the same sources as Trends and the old Health grid.
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val stressSub = if (section == HubSection.BODY_MONITOR) monitorStressLine(days) else null
    val vitalsSub = if (section == HubSection.BODY_MONITOR) monitorVitalsLine(days) else null
    HubScaffold(
        eyebrow = stringResource(section.eyebrowRes),
        title = hubTitleCase(stringResource(section.titleRes)),
        backDescription = stringResource(R.string.l10n_onboarding_screen_back_b52b36b7),
    ) {
        hubGroupItems(hubSection(section, hydrationSub, stressSub, vitalsSub), onNavigate)
    }
}

/** The shared Hub page frame, on the generated page backdrop like the other Ridge pages. */
@Composable
private fun HubScaffold(
    eyebrow: String,
    title: String,
    // A sub-list opens under the board's back chevron (BodyMore.dc.html's "Back to Hub"); the Hub's own page passes null.
    backDescription: String? = null,
    // SQ8b (B1): round controls beside the title, level with its baseline (the prototype's 4 dp bottom offset).
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(ctx) }
    val skyBehindCards = remember { NoopPrefs.skyBehindCards(ctx) }
    LazyScreenScaffold(
        title = null,
        topBackground = screenBackdropSlot(showDayCycleBackground, skyBehindCards),
        fullBleedBackground = screenBackdropFullBleed(showDayCycleBackground, skyBehindCards),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
                if (backDescription != null) {
                    // The board hangs the chevron off the title block's foot; the 40 dp target is shared with the tool pages.
                    RidgeBackButton(backDescription, onClick = { back?.onBackPressed() })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
                    RidgeEyebrow(eyebrow)
                    Text(title, style = RidgeType.titleXl, color = Ridge.ink, modifier = Modifier.semantics { heading() })
                }
                if (actions != null) {
                    Row(
                        Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
                        content = actions,
                    )
                }
            }
        }
        content()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hubGroupItems(
    groups: List<HubGroup>,
    onNavigate: (String) -> Unit,
) {
    groups.forEach { g ->
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                RidgeEyebrow(stringResource(g.headerRes))
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Ridge.space4)) {
                    Column {
                        g.rows.forEachIndexed { i, r ->
                            RidgeListRow(
                                title = hubTitleCase(stringResource(r.titleRes)),
                                // A computed subtitle (today's hydration total) wins over the static resource.
                                subtitle = r.subtitle ?: r.subtitleRes?.let { stringResource(it) },
                                icon = r.icon,
                                iconTint = r.iconTint,
                                onClick = {
                                    when (val t = r.target) {
                                        is HubTarget.Screen -> onNavigate(t.dest.route)
                                        is HubTarget.Section -> onNavigate(t.key.route)
                                        is HubTarget.Sheet -> onNavigate(t.kind.route)
                                    }
                                },
                            )
                            if (i < g.rows.lastIndex) HorizontalDivider(color = Ridge.glassStroke)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A Hub tile (SQ8b, B4; More.dc.html): the icon at the top, the eyebrow label and the value at the bottom, so labels and
 * values sit level across the row whatever the icon's height. At least 118 dp tall, 14 dp × 12 dp padding, the whole tile
 * one target. The value is one line of the serif at 20/24; a long name ellipsises, the tile keeps its shape.
 */
@Composable
private fun HubTile(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = Ridge.ink,
    stroke: Color? = null,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Ridge.radiusMd)
    GlassCard(
        radius = Ridge.radiusMd,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 118.dp)
            .then(if (stroke != null) Modifier.border(1.dp, stroke, shape) else Modifier)
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            icon()
            Column(Modifier.padding(top = Ridge.space2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RidgeEyebrow(label)
                Text(
                    value,
                    style = RidgeType.titleLg.copy(fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "lnum"),
                    color = valueColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
