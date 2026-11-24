package com.noop.ui

import com.noop.R
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.PuffinExperiment
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeRowDivider
import com.noop.ui.ridge.RidgeStepper
import com.noop.ui.ridge.RidgeToggle

/**
 * Smart alarm (#207) — Android phone-based wake, with a guaranteed hard-deadline fallback.
 *
 * The user picks the EARLIEST acceptable wake time and a window length. NOOP watches the overnight
 * strap stream and, if it spots a lighter sleep phase inside the window, wakes you then — but a
 * GUARANTEED exact OS alarm is always scheduled at the window's END (via AlarmManager), independent
 * of Bluetooth, the strap, or the app being alive. The smart logic can only ever move the alarm
 * EARLIER; it can never cancel or skip the fallback. So you're woken by the window's end no matter
 * what. This screen is explicit about that safety guarantee.
 *
 * This is the ONE alarm surface (#766). It hosts the phone-based Wake Window above, the strap's own
 * standalone firmware wake-alarm (moved here from Automations), and the cross-platform WIND-DOWN nudge,
 * so every wake/alarm control lives together instead of being split across two screens.
 */
@Composable
fun SmartAlarmScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val enabled by vm.phoneAlarmEnabled.collectAsStateWithLifecycle()
    val targetMinutes by vm.phoneAlarmTargetMinutes.collectAsStateWithLifecycle()
    val windowMinutes by vm.phoneAlarmWindowMinutes.collectAsStateWithLifecycle()
    val phoneAlarmWeekdays by vm.phoneAlarmWeekdays.collectAsStateWithLifecycle()
    val phoneAlarmDayOverrides by vm.phoneAlarmDayOverrides.collectAsStateWithLifecycle()
    val buzzWhoop4 by vm.buzzWhoop4Enabled.collectAsStateWithLifecycle()
    // #536: the hint adapts to bond state — the strap can only be armed when a WHOOP 4.0 is connected.
    val liveState = vm.live.collectAsStateWithLifecycle().value
    val bonded = liveState.bonded
    // #821: the strap-buzz row was hardcoded to "WHOOP 4", which reads wrong on a connected 5/MG (issue
    // #730 follow-up). Name the actual strap generation instead: a detected 5/MG says "WHOOP 5/MG", anything
    // else (a 4.0, or nothing connected yet) keeps "WHOOP 4.0", so the label never claims the wrong device.
    val strapName = if (liveState.whoop5Detected) "WHOOP 5/MG" else "WHOOP 4.0"

    // True when exact alarms are permitted. Re-read on each (re)composition because the user can grant
    // it in Settings and come back — there's no result callback for this special-access permission.
    var canSchedule by remember { mutableStateOf(vm.canScheduleExactAlarms()) }

    LazyScreenScaffold(
        // #766: "Alarms" because this screen now holds the phone Wake Window, the strap's firmware
        // wake-alarm (moved here from Automations), and the wind-down reminder, so the broader title fits.
        title = uiString(R.string.l10n_smart_alarm_screen_alarms_131dd3d6),
        subtitle = "Wake window, strap alarm and wind-down, in one place.",
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                // The board's one-line subtitle under the title (ScaffoldTitleBlock drops it on a Ridge tool page).
                if (ridgeShell) {
                    Text(
                        "Wake window, strap alarm and wind-down, in one place.",
                        style = RidgeType.bodySm, color = Ridge.inkMuted,
                    )
                }
                // #1858: the card names a specific time ("a backup alarm is set for 04:45"), so with per-day
                // wake times it has to show the NEXT one rather than the default — on a day whose time was
                // moved, the default is simply the wrong number, and this card is the one thing on the screen
                // that makes a promise. Falls back to the default when no day is reachable.
                val nextTargetMinutes = remember(
                    targetMinutes, windowMinutes, phoneAlarmWeekdays, phoneAlarmDayOverrides,
                ) {
                    com.noop.alarm.SmartAlarmScheduler.nextWindowStartMinutes(
                        now = java.util.Calendar.getInstance(),
                        weekdays = phoneAlarmWeekdays,
                        windowMinutes = windowMinutes,
                        defaultTarget = targetMinutes,
                    ) { phoneAlarmDayOverrides[it] ?: targetMinutes }
                }
                WakeWindowCard(
                    enabled = enabled,
                    targetMinutes = targetMinutes,
                    nextTargetMinutes = nextTargetMinutes,
                    windowMinutes = windowMinutes,
                    weekdays = phoneAlarmWeekdays,
                    overrides = phoneAlarmDayOverrides,
                    buzzWhoop4 = buzzWhoop4,
                    bonded = bonded,
                    strapName = strapName,
                    canSchedule = canSchedule,
                    onToggle = { want ->
                        if (want && !vm.canScheduleExactAlarms()) {
                            // No callback for this special-access grant — send the user to the system page,
                            // and re-read the state when they return (canSchedule recomputes on recompose).
                            requestExactAlarmAccess(context)
                            canSchedule = vm.canScheduleExactAlarms()
                        } else {
                            val ok = vm.setPhoneAlarmEnabled(want)
                            canSchedule = vm.canScheduleExactAlarms()
                            if (!ok) requestExactAlarmAccess(context)
                        }
                    },
                    onRequestExactAlarm = {
                        requestExactAlarmAccess(context)
                        canSchedule = vm.canScheduleExactAlarms()
                    },
                    onTarget = { vm.setPhoneAlarmTargetMinutes(it) },
                    onWindow = { vm.setPhoneAlarmWindowMinutes(it) },
                    onToggleWeekday = { dow -> vm.setPhoneAlarmWeekdays(toggledSmartAlarmWeekday(dow, phoneAlarmWeekdays)) },
                    onOverride = { dow, minutes -> vm.setPhoneAlarmDayOverride(dow, minutes) },
                    onBuzz = { vm.setBuzzWhoop4Enabled(it) },
                )
            }
        }

        // #766: the strap's own firmware wake-alarm (Morning) — its own time + weekdays + per-day overrides.
        item { StrapAlarmCard(vm) }

        // The cross-platform wind-down nudge (Evening) lives here too.
        item { WindDownCard(vm) }

        // #821: the "how the smart wake works" explainer belongs at the BOTTOM after every control.
        item { ExplanationCard() }
    }
}

/**
 * The Wake window card (`Alarms.dc.html`): the board's single card for the phone-based smart alarm — an
 * eyebrow + title + toggle header, then (when on) the "No earlier than" and "Window length" steppers, the
 * day pills, the per-day override picker and the green guarantee callout, with the companion strap-buzz row
 * beneath. Replaces the v10 split (a scenic guarantee hero + a separate "Wake alarm" card).
 */
@Composable
private fun WakeWindowCard(
    enabled: Boolean,
    targetMinutes: Int,
    nextTargetMinutes: Int,
    windowMinutes: Int,
    weekdays: Set<Int>,
    overrides: Map<Int, Int>,
    buzzWhoop4: Boolean,
    bonded: Boolean,
    strapName: String,
    canSchedule: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
    onTarget: (Int) -> Unit,
    onWindow: (Int) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onOverride: (Int, Int?) -> Unit,
    onBuzz: (Boolean) -> Unit,
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            // Card header: eyebrow + title + toggle, as on the board.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RidgeEyebrow(uiString(R.string.alarms_eyebrow_wake_window))
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_wake_me_with_a_smart_alarm_bbbd082d),
                        style = RidgeType.titleSm.copy(fontSize = 18.sp, lineHeight = 22.sp), color = Ridge.ink,
                    )
                }
                Spacer(Modifier.width(Ridge.space3))
                RidgeToggle(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    contentDescription = uiString(R.string.l10n_smart_alarm_screen_wake_me_with_a_smart_alarm_bbbd082d),
                )
            }

            if (enabled) {
                RidgeRowDivider()
                // No earlier than — the earliest NOOP will wake you, in ±5-minute steps.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            uiString(R.string.alarms_no_earlier_than),
                            style = RidgeType.body, color = Ridge.ink,
                        )
                        Text(
                            uiString(R.string.l10n_smart_alarm_screen_the_earliest_noop_will_wake_you_b641a2d4),
                            style = RidgeType.bodySm, color = Ridge.inkMuted,
                        )
                    }
                    Spacer(Modifier.width(Ridge.space3))
                    TimeStepper(minutes = targetMinutes, onChange = onTarget)
                }

                RidgeRowDivider()
                // Window length — the backup alarm fires this long after, 5–60 min in 5-min steps.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(uiString(R.string.l10n_smart_alarm_screen_window_length_46179fcb), style = RidgeType.body, color = Ridge.ink)
                        Text(
                            uiString(R.string.alarms_window_sub),
                            style = RidgeType.bodySm, color = Ridge.inkMuted,
                        )
                    }
                    Spacer(Modifier.width(Ridge.space3))
                    RidgeStepper(
                        value = windowMinutes,
                        onValueChange = onWindow,
                        range = 5..60,
                        step = 5,
                        unit = "min",
                        label = uiString(R.string.l10n_smart_alarm_screen_window_length_46179fcb),
                    )
                }

                RidgeRowDivider()
                // Days this alarm fires on — the board's seven equal pills, same empty-means-every-day contract.
                Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                    Text(uiString(R.string.alarms_days), style = RidgeType.body, color = Ridge.ink)
                    RidgeDayPills(selected = weekdays, onToggle = onToggleWeekday)
                    Text(smartAlarmWeekdaySummary(weekdays), style = RidgeType.bodySm, color = Ridge.inkSubtle)
                }

                RidgeRowDivider()
                // #1858: per-day wake times — the SAME picker and contract the strap alarm below uses.
                AlarmDayOverridePicker(
                    defaultMinutes = targetMinutes,
                    enabledDays = weekdays,
                    overrides = overrides,
                    onSetOverride = onOverride,
                )

                if (!canSchedule) {
                    RidgeRowDivider()
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_noop_doesn_t_have_permission_to_5b67cef0) +
                            " Tap to allow it in system settings.",
                        style = RidgeType.bodySm,
                        color = Ridge.stateDepleted,
                        modifier = Modifier.fillMaxWidth().clickable { onRequestExactAlarm() },
                    )
                }
            }

            // The safety promise is always visible (the board shows it only while on; the app keeps it on
            // screen either way so the guarantee is the first thing read).
            RidgeRowDivider()
            GuaranteeCallout(enabled = enabled, backupMinutes = (nextTargetMinutes + windowMinutes) % (24 * 60))

            // #536/#821: companion strap-buzz — arms the strap at the PHONE alarm's time, as the board's
            // Morning card is the strap's standalone schedule, so the companion lives with the wake window.
            RidgeRowDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_buzz_strapname_813772f4, strapName),
                        style = RidgeType.body, color = Ridge.ink,
                    )
                    Text(
                        if (bonded)
                            "Also arms your $strapName to buzz at your earliest wake time, so the strap wakes you first and the phone alarm is the guaranteed backup."
                        else
                            "Connect your strap to use this. It arms the strap to buzz at your earliest wake time as a gentler first wake-up.",
                        style = RidgeType.bodySm, color = Ridge.inkMuted,
                    )
                }
                Spacer(Modifier.width(Ridge.space3))
                RidgeToggle(
                    checked = buzzWhoop4,
                    onCheckedChange = onBuzz,
                    contentDescription = uiString(R.string.l10n_smart_alarm_screen_buzz_strapname_813772f4, strapName),
                )
            }
        }
    }
}

/**
 * The strap's standalone silent wake-alarm (#766, moved from AutomationsScreen). Arms the strap's own
 * firmware alarm at the chosen time/weekdays over BLE, so it buzzes even if NOOP is closed. Reuses the
 * shared [AlarmWeekdayPicker] / [AlarmDayOverridePicker] behaviour (same calls, a new Ridge home).
 */
@Composable
private fun StrapAlarmCard(vm: AppViewModel) {
    val context = LocalContext.current
    val smartAlarm by vm.smartAlarmEnabled.collectAsStateWithLifecycle()
    val alarmMinutes by vm.smartAlarmMinutes.collectAsStateWithLifecycle()
    val alarmWeekdays by vm.smartAlarmWeekdays.collectAsStateWithLifecycle()
    val alarmDayOverrides by vm.smartAlarmDayOverrides.collectAsStateWithLifecycle()
    val live = vm.live.collectAsStateWithLifecycle().value
    // The firmware alarm is EXPERIMENTAL on a WHOOP 5/MG: it only arms when Experimental probes are on,
    // otherwise enabling it silently arms nothing (#111), so the UI says so instead of promising a wake.
    val experimentalOn = PuffinExperiment.from(context).isEnabled

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RidgeEyebrow("Morning")
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_wake_me_with_a_strap_buzz_1681ba1d),
                        style = RidgeType.titleSm.copy(fontSize = 18.sp, lineHeight = 22.sp), color = Ridge.ink,
                    )
                }
                Spacer(Modifier.width(Ridge.space3))
                RidgeToggle(
                    checked = smartAlarm,
                    onCheckedChange = { vm.setSmartAlarmEnabled(it) },
                    contentDescription = uiString(R.string.l10n_smart_alarm_screen_wake_me_with_a_strap_buzz_1681ba1d),
                )
            }
            if (smartAlarm) {
                RidgeRowDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiString(R.string.l10n_smart_alarm_screen_wake_at_49089991), style = RidgeType.body, color = Ridge.ink)
                    Spacer(Modifier.weight(1f))
                    TimeStepper(minutes = alarmMinutes, onChange = { vm.setSmartAlarmMinutes(it) })
                }
                RidgeRowDivider()
                Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                    RidgeDayPills(selected = alarmWeekdays, onToggle = { dow -> vm.setSmartAlarmWeekdays(toggledSmartAlarmWeekday(dow, alarmWeekdays)) })
                    Text(smartAlarmWeekdaySummary(alarmWeekdays), style = RidgeType.bodySm, color = Ridge.inkSubtle)
                }
                RidgeRowDivider()
                // Per-weekday wake-time OVERRIDES (#554): a different time for any day the alarm fires on.
                AlarmDayOverridePicker(
                    defaultMinutes = alarmMinutes,
                    enabledDays = alarmWeekdays,
                    overrides = alarmDayOverrides,
                    onSetOverride = { dow, minutes -> vm.setSmartAlarmDayOverride(dow, minutes) },
                )
                RidgeRowDivider()
                if (live.whoop5Detected && !experimentalOn) {
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_your_whoop_5_mg_won_t_75029bae) +
                            " Experimental). Right now your wake time is saved but the strap is NOT armed.",
                        style = RidgeType.bodySm, color = Ridge.stateDepleted,
                    )
                } else if (live.whoop5Detected) {
                    // 5/MG with Experimental ON: the strap IS armed (experimental rev-4 payload) but a
                    // strap-driven wake has NEVER been captured on 5/MG, so the "confirmed on 4.0" copy must
                    // NOT show here (#864 honesty). Byte-identical wording to the Swift SmartAlarmView twin.
                    Text(
                        if (live.bonded)
                            "Armed on the strap itself with the experimental 5/MG command. A strap-driven wake is still unconfirmed on 5/MG on our side (confirmed only on WHOOP 4.0), so keep a backup alarm for anything you truly can't miss."
                        else
                            "Connect your strap to arm this; it's set on the strap's own firmware alarm. Confirmed working on WHOOP 4.0; still experimental on 5.0 and MG. Keep a backup alarm for anything you truly can't miss.",
                        style = RidgeType.bodySm, color = Ridge.inkMuted,
                    )
                } else {
                    Text(
                        if (live.bonded)
                            // Truth-sync (#535): confirmed buzzing on a real WHOOP 4.0; byte-identical
                            // wording to the Swift SmartAlarmView.
                            "Armed on the strap itself, so it can buzz at your wake time even if your phone is asleep or NOOP is closed. Sends the exact alarm command the official app sends, confirmed buzzing on a real WHOOP 4.0 (community wire capture + on-device test, #535). Keep a backup alarm for anything you truly can't miss."
                        else
                            "Connect your strap to arm this; it's set on the strap's own firmware alarm. Confirmed working on WHOOP 4.0; still experimental on 5.0 and MG. Keep a backup alarm for anything you truly can't miss.",
                        style = RidgeType.bodySm, color = Ridge.inkMuted,
                    )
                    // #1706: ask the strap what it actually has stored (the board's "Check what the strap has
                    // stored" link). The readback was previously only reachable by ARMING. 4.0 only — 5/MG is not decoded.
                    if (live.bonded) {
                        Text(
                            uiString(R.string.smart_alarm_check_strap_alarm),
                            style = RidgeType.label,
                            color = Ridge.brand,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Ridge.radiusSm))
                                .clickable { vm.ble.getStrapAlarm() }
                                .padding(vertical = Ridge.space2),
                        )
                        Text(uiString(R.string.smart_alarm_check_strap_alarm_help), style = RidgeType.bodySm, color = Ridge.inkMuted)
                    }
                }
            }
        }
    }
}

/** The cross-platform evening wind-down nudge — a gentle reminder, not an alarm. */
@Composable
private fun WindDownCard(vm: AppViewModel) {
    val enabled by vm.windDownEnabled.collectAsStateWithLifecycle()
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RidgeEyebrow("Evening")
                Text(uiString(R.string.l10n_smart_alarm_screen_remind_me_to_wind_down_4839f0d0), style = RidgeType.titleSm.copy(fontSize = 18.sp, lineHeight = 22.sp), color = Ridge.ink)
                Text(
                    "A gentle evening notification, timed from your wake time and usual sleep need, so you can settle in time. It's a suggestion, not an alarm.",
                    style = RidgeType.bodySm, color = Ridge.inkMuted,
                )
            }
            Spacer(Modifier.width(Ridge.space3))
            RidgeToggle(
                checked = enabled,
                onCheckedChange = { vm.setWindDownEnabled(it) },
                contentDescription = uiString(R.string.l10n_smart_alarm_screen_remind_me_to_wind_down_4839f0d0),
            )
        }
    }
}

/** The "How the smart wake works" explainer — the board's eyebrow over one reference paragraph. */
@Composable
private fun ExplanationCard() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            RidgeEyebrow(uiString(R.string.l10n_smart_alarm_screen_how_the_smart_wake_works_8cf34930))
            Text(
                uiString(R.string.l10n_smart_alarm_screen_while_you_re_inside_the_window_8700ca3b) +
                    " sleep sits near your nightly low and stays steady; when your heart rate lifts above " +
                    "that (a sign you're sleeping more lightly or starting to stir), NOOP wakes you a " +
                    "little early so you come up from a lighter phase. " +
                    uiString(R.string.l10n_smart_alarm_screen_this_is_a_coarse_cue_from_d6bbabe7) +
                    " isn't streaming (Bluetooth off, not worn, app killed), no early wake happens and the " +
                    "guaranteed alarm at the window's end still wakes you.",
                style = RidgeType.bodySm, color = Ridge.inkMuted,
            )
        }
    }
}

/** The green guarantee callout (`Alarms.dc.html`): a shield over the board's brand-tinted fill. */
@Composable
private fun GuaranteeCallout(enabled: Boolean, backupMinutes: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ridge.brand.copy(alpha = 0.08f))
            .border(1.dp, Ridge.brand.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Ridge.space3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Ridge.space3), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (enabled) {
                    Text(
                        uiString(R.string.l10n_smart_alarm_screen_a_backup_alarm_is_set_for_cf8b94fb, hhmm(backupMinutes)),
                        style = RidgeType.bodySm, color = Ridge.ink,
                    )
                } else {
                    Text(uiString(R.string.l10n_smart_alarm_screen_off_e3de5ab0), style = RidgeType.body, color = Ridge.ink)
                    Text(uiString(R.string.l10n_smart_alarm_screen_turn_on_the_smart_alarm_to_65700430), style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
            }
        }
    }
}

// MARK: - Shared Ridge controls (this screen's own, matching the board)

/** Monday-first weekday order, matching [SMART_ALARM_WEEKDAY_ORDER] in AutomationsScreen (Calendar DAY_OF_WEEK). */
private val ALARM_WEEKDAY_ORDER = intArrayOf(2, 3, 4, 5, 6, 7, 1)

private fun weekdayInitial(dow: Int): String = when (dow) {
    1 -> "S"; 2 -> "M"; 3 -> "T"; 4 -> "W"; 5 -> "T"; 6 -> "F"; 7 -> "S"; else -> "?"
}

private fun weekdayFullName(dow: Int): String = when (dow) {
    1 -> "Sunday"; 2 -> "Monday"; 3 -> "Tuesday"; 4 -> "Wednesday"; 5 -> "Thursday"; 6 -> "Friday"; 7 -> "Saturday"; else -> "?"
}

/** The board's seven equal day pills: 40 dp tall, 12 dp radius, mint when on, one 48 dp target each. */
@Composable
private fun RidgeDayPills(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        for (dow in ALARM_WEEKDAY_ORDER) {
            val on = smartAlarmWeekdayIsSelected(dow, selected)
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ridge.brand.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) Ridge.brand else Ridge.glassStroke, RoundedCornerShape(12.dp))
                    .selectable(
                        selected = on, role = Role.Checkbox, interactionSource = interaction,
                        indication = null, onClick = { onToggle(dow) },
                    )
                    .semantics { contentDescription = weekdayFullName(dow) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    weekdayInitial(dow),
                    style = RidgeType.label,
                    color = if (on) Ridge.brand else Ridge.inkSubtle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The board's circular −/+ step button (`.np-step`), 48 dp target around a 40 dp glass disc. */
@Composable
private fun AlarmStepButton(plus: Boolean, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = false, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Ridge.glassFillStrong)
                .border(1.dp, Ridge.glassStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (plus) RidgeIcons.plus else RidgeIcons.minus, contentDescription = null, tint = Ridge.ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** The board's time stepper: − hh:mm + in the Playfair title size, 5-minute steps wrapping the day. */
@Composable
private fun TimeStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.semantics { stateDescription = hhmm(minutes) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space1),
    ) {
        AlarmStepButton(plus = false, description = "Earlier") { onChange((minutes - 5 + 1440) % 1440) }
        Text(
            hhmm(minutes),
            style = RidgeType.titleSm,
            color = Ridge.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        AlarmStepButton(plus = true, description = "Later") { onChange((minutes + 5) % 1440) }
    }
}

// MARK: - Helpers

private fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Open the system page where the user grants the exact-alarm special-access permission (API 31+).
 *  There's no runtime dialog for this; the user toggles it in Settings and returns. */
private fun requestExactAlarmAccess(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        // Fall back to the app-details page if the OEM lacks the specific action.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
