package com.noop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.analytics.VitalBands
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeIcons

// RS-2a: Today's two alert cards, between the orbs and Synthesis. The Vitals card counts the five Vital signs tiles outside the
// wearer's range; the Illness heads-up card shows when the Illness heads-up rule (IllnessWatch, unchanged) fires. Both open
// Hub > Vital signs, and neither can be dismissed: they leave when the reading does. Side by side when both show.
// RS-3e: compressed to the closed Readiness card's height.

/** The Vital signs page shows five vitals; the Vitals card counts against all five, missing ones included (owner, 26 Nov). */
internal const val RIDGE_VITALS_TOTAL = 5

/** How the five vitals sit: how many have a reading, how many of those are in range, and the ones outside it. */
internal data class VitalsSummary(val read: Int, val inRange: Int, val outOfRange: List<Vital>)

/** Counts [vitals] the way the Vital signs tiles band them, so Body Monitor, Today and the page agree. */
internal fun summariseVitals(vitals: List<Vital>): VitalsSummary {
    val read = vitals.filter { it.banding.band != VitalBands.Band.NO_DATA }
    return VitalsSummary(
        read = read.size,
        inRange = read.count { it.banding.band == VitalBands.Band.IN_RANGE },
        outOfRange = read.filter { it.banding.band == VitalBands.Band.OUT_OF_RANGE },
    )
}

/** What Today's alert slot shows: the vitals outside range (labels, page order) and the heads-up's fired signals. */
internal data class TodayAlertsState(
    val vitalsOut: List<String>,
    /** The Illness heads-up's signal line ("Resting HR +6 bpm, HRV −24%"), "" when it fired without a parsable list; null = quiet. */
    val illnessSignals: String?,
) {
    val showVitals: Boolean get() = vitalsOut.isNotEmpty()
    val showIllness: Boolean get() = illnessSignals != null
    val sideBySide: Boolean get() = showVitals && showIllness
    val isEmpty: Boolean get() = !showVitals && !showIllness
}

/** Builds the alert slot from the five vitals and the Illness heads-up message ([AppViewModel.healthAlert], null when quiet or off). */
internal fun todayAlertsState(vitals: List<Vital>, illnessMessage: String?): TodayAlertsState = TodayAlertsState(
    vitalsOut = summariseVitals(vitals).outOfRange.map { it.label },
    illnessSignals = illnessMessage?.let(::illnessSignalsOf),
)

/**
 * The signal list inside an IllnessWatch message ("Your body looks strained - resting HR +6 bpm, HRV −24%. Consider taking it
 * easy."), first letter capitalised. "" when the message is not in that shape, so the card still shows without a list.
 */
internal fun illnessSignalsOf(message: String): String {
    val start = message.indexOf(" - ")
    val end = message.lastIndexOf(". Consider")
    if (start < 0 || end <= start) return ""
    return message.substring(start + 3, end).trim().replaceFirstChar { it.uppercase() }
}

/**
 * RS-3i: the fired signals by name alone, for the half-width card: "Resting HR +13 bpm, HRV −50%, respiration up" reads
 * "Resting HR, HRV, Breathing". The screen-reader label keeps the figures.
 */
internal fun illnessSignalNames(signals: String): String = signals.split(", ").filter { it.isNotBlank() }.joinToString(", ") {
    if (it.startsWith("respiration", ignoreCase = true)) "Breathing"
    else it.substringBefore(" +").substringBefore(" −").replaceFirstChar { ch -> ch.uppercase() }
}

/**
 * RS-3i: whether the alert cards, drawn right under the orbs, sit directly above the Readiness card, so the space the
 * card normally keeps below the orbs is not left under them. The live-session section between them is empty at rest.
 */
internal fun alertsSitAboveSynthesis(order: List<TodaySection>, alertsShown: Boolean): Boolean {
    val orbs = order.indexOf(TodaySection.HERO)
    val synthesis = order.indexOf(TodaySection.SYNTHESIS)
    return alertsShown && orbs >= 0 && synthesis > orbs &&
        order.subList(orbs + 1, synthesis).all { it == TodaySection.LIVE_SESSION }
}

/**
 * RS-3e (owner, 26 Nov): the height Today's glance cards share, which is the closed Readiness card's. A minimum, so large
 * system text grows a card instead of cutting it off.
 */
internal val TODAY_GLANCE_CARD_HEIGHT = 104.dp

/** The alert slot: nothing, one full-width card, or the Vitals and Illness heads-up tiles side by side. */
@Composable
internal fun TodayAlertCards(state: TodayAlertsState, onOpenVitals: () -> Unit) {
    if (state.isEmpty) return
    val vitalsTitle = stringResource(R.string.today_alert_vitals_count, state.vitalsOut.size, RIDGE_VITALS_TOTAL)
    val vitalsBody = state.vitalsOut.joinToString(", ")
    val vitalsEyebrow = stringResource(R.string.today_alert_vitals_eyebrow)
    val illnessEyebrow = stringResource(R.string.today_alert_illness_eyebrow)
    val signals = state.illnessSignals.orEmpty()
    val advice = stringResource(R.string.today_alert_illness_advice)
    if (state.sideBySide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            AlertCard(
                eyebrow = vitalsEyebrow, title = vitalsTitle,
                // RS-3i: all five names don't fit the half-width card.
                body = if (state.vitalsOut.size == RIDGE_VITALS_TOTAL) stringResource(R.string.today_alert_vitals_all) else vitalsBody,
                spoken = vitalsBody, compact = true,
                modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onOpenVitals,
            )
            AlertCard(
                eyebrow = illnessEyebrow, title = stringResource(R.string.today_alert_illness_title_short), body = illnessSignalNames(signals),
                spoken = "$signals. $advice", compact = true, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onOpenVitals,
            )
        }
    } else if (state.showVitals) {
        AlertCard(
            eyebrow = vitalsEyebrow, title = vitalsTitle, body = vitalsBody,
            spoken = stringResource(R.string.today_alert_vitals_body, vitalsBody), compact = false,
            modifier = Modifier.fillMaxWidth(), onClick = onOpenVitals,
        )
    } else {
        // The signals with their figures, or the advice when the message carries no list. The screen-reader
        // label keeps both, with the "not a diagnosis" note.
        AlertCard(
            eyebrow = stringResource(R.string.today_alert_illness_eyebrow_estimate), title = stringResource(R.string.today_alert_illness_title),
            body = signals.ifEmpty { advice }, spoken = if (signals.isEmpty()) advice else "$signals. $advice",
            compact = false, modifier = Modifier.fillMaxWidth(), onClick = onOpenVitals,
        )
    }
}

/** One alert: an amber eyebrow, the headline and one line, with a chevron when there is room. The whole card is the tap target. */
@Composable
private fun AlertCard(
    eyebrow: String,
    title: String,
    body: String,
    /** What a screen reader says for the card's body: the full wording, which the one line may cut short. */
    spoken: String,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val action = stringResource(R.string.today_alert_view_vitals)
    val a11y = listOf(eyebrow, title, spoken, action.trimEnd('→', ' ')).filter { it.isNotBlank() }.joinToString(". ")
    val shape = RoundedCornerShape(Ridge.radiusLg)
    GlassCard(
        modifier = modifier.heightIn(min = TODAY_GLANCE_CARD_HEIGHT).clip(shape).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else Ridge.space5, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TODAY_GLANCE_CARD_HEIGHT - 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(eyebrow.uppercase(), style = RidgeType.eyebrow, color = Ridge.stateModerate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AutoSizeValue(
                    text = title,
                    style = if (compact) RidgeType.body.copy(fontWeight = FontWeight.SemiBold) else RidgeType.titleSm.copy(fontSize = 19.sp, lineHeight = 24.sp),
                    color = Ridge.ink, minScale = 0.7f,
                )
                if (body.isNotBlank()) {
                    Text(body, style = RidgeType.bodySm, color = Ridge.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!compact) {
                Icon(RidgeIcons.chevronRight, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(20.dp))
            }
        }
    }
}
