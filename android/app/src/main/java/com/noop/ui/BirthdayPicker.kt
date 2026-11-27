package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.noop.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/*
 * RS-1-3 item 9 (owner, 25 Nov): Profile's Age row becomes Date of Birth, read as "June 10th, 1990", and tapping it opens
 * a Birthday dialog of three wheels (month, day, year) with Cancel and Done, in Ridge colours. The date is stored through
 * ProfileStore.setDateOfBirth, the same path onboarding uses, so age and everything derived from it follow.
 */

/** "June 10th, 1990" in English; other languages get their own long date ("10 juin 1990"). */
internal fun birthdayText(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    if (locale.language != "en") return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    val d = date.dayOfMonth
    val suffix = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "${date.month.getDisplayName(DateTextStyle.FULL, locale)} $d$suffix, ${date.year}"
}

/** The day kept when the month or year changes: the same day, or the month's last day if it is shorter (31 Jan to Feb). */
internal fun clampDay(year: Int, month: Int, day: Int): Int = day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())

/** The years the wheel offers, newest first, so that the derived age stays within [minAge]..[maxAge]. */
internal fun birthdayYears(today: LocalDate, minAge: Int, maxAge: Int): List<Int> = (today.year - minAge downTo today.year - maxAge).toList()

private val WHEEL_ROW = 52.dp
private const val WHEEL_VISIBLE = 5

/**
 * One wheel: [labels] scroll vertically and snap, the middle row is the selection (on a darker rounded block, bold).
 * [selected] is the index shown first; [onSelected] fires when a scroll comes to rest. A tap on a row selects it too.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Wheel(labels: List<String>, selected: Int, description: String, modifier: Modifier, onSelected: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    var centre by remember { mutableIntStateOf(selected) }
    LaunchedEffect(state, labels.size) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }.collect { (moving, first) ->
            if (!moving) {
                val i = (first + if (state.firstVisibleItemScrollOffset > 0) 1 else 0).coerceIn(0, labels.size - 1)
                if (i != centre) { centre = i; onSelected(i) }
            }
        }
    }
    LaunchedEffect(selected) { if (selected != centre && selected in labels.indices) { centre = selected; state.scrollToItem(selected) } }
    Box(modifier.height(WHEEL_ROW * WHEEL_VISIBLE).semantics { contentDescription = description; stateDescription = labels.getOrElse(centre) { "" } }) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(WHEEL_ROW)
                .background(Ridge.glassFillStrong, RoundedCornerShape(Ridge.radiusSm))
                .border(1.dp, Ridge.brand.copy(alpha = 0.35f), RoundedCornerShape(Ridge.radiusSm)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = WHEEL_ROW * (WHEEL_VISIBLE / 2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(labels) { i, label ->
                val on = i == centre
                Box(
                    Modifier.fillMaxWidth().height(WHEEL_ROW).clickable(role = Role.Button) { centre = i; onSelected(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    val far = kotlin.math.abs(i - centre)
                    Text(
                        label,
                        style = if (on) RidgeType.numeralMd.copy(fontSize = 22.sp, lineHeight = 26.sp)
                        else TextStyle(fontFamily = RidgeType.sans, fontSize = 17.sp, fontWeight = FontWeight.Normal),
                        color = if (on) Ridge.ink else Ridge.inkMuted.copy(alpha = if (far == 1) 0.7f else 0.35f), maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The Birthday dialog: month, day and year wheels starting at [initial], Cancel and Done. [onDone] gets the picked date;
 * a day past the month's end is pulled back to its last day before it is returned.
 */
@Composable
internal fun BirthdayDialog(initial: LocalDate, minAge: Int, maxAge: Int, onDismiss: () -> Unit, onDone: (LocalDate) -> Unit) {
    Dialog(onDismissRequest = onDismiss) { BirthdayPicker(initial, minAge, maxAge, onDismiss, onDone) }
}

/** The dialog's panel (no window, so it has a snapshot). */
@Composable
internal fun BirthdayPicker(
    initial: LocalDate, minAge: Int, maxAge: Int, onCancel: () -> Unit, onDone: (LocalDate) -> Unit,
    today: LocalDate = remember { LocalDate.now() },
) {
    val years = remember(today) { birthdayYears(today, minAge, maxAge) }
    val locale = Locale.getDefault()
    val months = remember(locale) { (1..12).map { java.time.Month.of(it).getDisplayName(DateTextStyle.SHORT, locale) } }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var year by remember { mutableIntStateOf(initial.year.coerceIn(years.last(), years.first())) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    val dayCount = YearMonth.of(year, month).lengthOfMonth()
    val shape = RoundedCornerShape(Ridge.radiusXl)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            // The Trend View sheet's ground: near-black with a mint wash at the top (Baseline.dc.html sheet).
            .background(Brush.verticalGradient(listOf(lerp(Ridge.bg000, Ridge.brand, 0.14f), Ridge.sheetBottom, Ridge.bg000)), shape)
            .border(1.dp, Ridge.glassStroke, shape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            com.noop.ui.ridge.RidgeEyebrow(stringResource(R.string.profile_dob_label))
            Text(stringResource(R.string.dob_dialog_title), style = RidgeType.numeralMd.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Ridge.ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wheel(months, month - 1, stringResource(R.string.dob_month), Modifier.weight(1.3f)) { month = it + 1; day = clampDay(year, month, day) }
            Wheel((1..dayCount).map { it.toString() }, day.coerceAtMost(dayCount) - 1, stringResource(R.string.dob_day), Modifier.weight(0.9f)) { day = it + 1 }
            Wheel(years.map { it.toString() }, years.indexOf(year).coerceAtLeast(0), stringResource(R.string.dob_year), Modifier.weight(1.1f)) {
                year = years[it]; day = clampDay(year, month, day)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.noop.ui.ridge.RidgeButton(
                stringResource(R.string.dob_cancel), onClick = onCancel,
                kind = com.noop.ui.ridge.RidgeButtonKind.Secondary, modifier = Modifier.weight(1f),
            )
            com.noop.ui.ridge.RidgeButton(
                stringResource(R.string.dob_done), onClick = { onDone(LocalDate.of(year, month, clampDay(year, month, day))) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
