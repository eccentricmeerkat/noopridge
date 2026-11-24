package com.noop.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noop.NoopApplication
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.MetricUnit
import com.noop.metrics.MetricValue
import com.noop.metrics.V10MetricRegistry
import com.noop.metrics.loadV10MetricInputs
import com.noop.metrics.MetricsV2
import com.noop.metrics.V2Change
import com.noop.metrics.dayKeyFor
import com.noop.ui.ProfileStore
import android.content.Intent
import android.os.Process
import androidx.compose.material3.Switch
import com.noop.ui.NoopTheme
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import com.noop.ui.ridge.RidgeRowDivider
import java.util.Locale

/**
 * Debug-only metric inspector (R1a, J-01e; 13-release-plan R1). Lists every [MetricId] the registry serves for one
 * day: value and unit, band, carry / confidence / missing reason, the v10 source it reads and its frozen formula
 * page, so the registry can be checked against what the app shows. Lives in the debug source set, with its own
 * launcher entry; release builds do not contain it. Reads through [loadV10MetricInputs], never its own queries.
 */
class MetricInspectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NoopApplication
        setContent { NoopTheme { MetricInspector(app, ::restartWith) } }
    }

    /** Save one metricsV2 switch and restart the process, so every screen reads it from launch (R9a). */
    private fun restartWith(change: V2Change, on: Boolean) {
        MetricsV2.save(this, change, on)
        startActivity(
            Intent(this, MetricInspectorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Process.killProcess(Process.myPid())
    }
}

@Composable
private fun MetricInspector(app: NoopApplication, onSwitch: (V2Change, Boolean) -> Unit) {
    var registry by remember { mutableStateOf<V10MetricRegistry?>(null) }
    var compare by remember { mutableStateOf<Pair<V10MetricRegistry, V10MetricRegistry>?>(null) }
    var today by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var offset by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val inputs = loadV10MetricInputs(app)
        today = inputs.today
        registry = V10MetricRegistry(inputs)
        // R9a: the same inputs with every correction off (v10) and on (v2), side by side.
        val profile = ProfileStore.from(app)
        compare = V10MetricRegistry(inputs.copy(v2 = emptySet(), age = profile.age)) to
            V10MetricRegistry(inputs.copy(v2 = V2Change.entries.toSet(), age = if (profile.ageEntered) profile.age else null))
    }
    Column(
        Modifier.fillMaxSize().background(Ridge.bg100).safeDrawingPadding().padding(horizontal = 16.dp),
    ) {
        Text("Metric inspector", style = RidgeType.titleLg, color = Ridge.ink, modifier = Modifier.padding(top = 12.dp))
        Text("Debug build only · values from MetricRegistry · metricsV2: " + MetricsV2.active.size + " of " + V2Change.entries.size + " on",
            style = RidgeType.bodySm, color = Ridge.inkMuted)
        val reg = registry
        val base = today
        if (reg == null || base == null) {
            Text("Loading…", style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        val day = DayKey(base.minusDays(offset))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("‹ earlier", style = RidgeType.bodySm, color = Ridge.brand,
                modifier = Modifier.clickable { offset += 1 }.padding(8.dp))
            Text(if (offset == 0L) "Today · $day" else "$day", style = RidgeType.listTitle, color = Ridge.ink)
            Text(if (offset > 0) "later ›" else "", style = RidgeType.bodySm, color = Ridge.brand,
                modifier = Modifier.clickable(enabled = offset > 0) { offset -= 1 }.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "v2-title") {
                Text("metricsV2 · R9a", style = RidgeType.listTitle, color = Ridge.ink, modifier = Modifier.padding(top = 8.dp))
                Text("Each switch saves and restarts the app. v10 → v2 for $day.", style = RidgeType.bodySm, color = Ridge.inkMuted)
            }
            compare?.let { (old, new) ->
                items(V2Change.entries.toList(), key = { "v2-" + it.name }) { change ->
                    V2Row(change, v2Comparison(change, old, new, day), MetricsV2.on(change)) { onSwitch(change, it) }
                    RidgeRowDivider()
                }
            }
            item(key = "v1-title") {
                Text("Registry (as the app reads it now)", style = RidgeType.listTitle, color = Ridge.ink, modifier = Modifier.padding(top = 16.dp))
            }
            items(MetricId.entries.toList(), key = { it.name }) { id ->
                InspectorRow(reg, reg.value(id, day))
                RidgeRowDivider()
            }
        }
    }
}

@Composable
private fun InspectorRow(reg: V10MetricRegistry, v: MetricValue) {
    val def = reg.definition(v.id)
    val shown = v.value?.let { String.format(Locale.ROOT, "%.${def.decimals}f", it) + unitLabel(v.unit) } ?: "—"
    val band = v.value?.let { reg.band(v.id, it)?.key }
    val notes = listOfNotNull(
        band?.let { "band $it" },
        v.carriedFrom?.let { "carried from $it" },
        v.progress?.let { "${it.done} of ${it.total}" },
        v.confidence.name.lowercase().takeIf { it != "settled" },
        v.missing?.let { "missing: ${it.name.lowercase()}" },
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(v.id.name, style = RidgeType.listTitle, color = Ridge.ink)
            Text(V10MetricRegistry.SOURCES[v.id].orEmpty(), style = RidgeType.bodySm, color = Ridge.inkMuted)
            Text(def.formulaPage + if (notes.isNotEmpty()) " · $notes" else "", style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Text(shown, style = RidgeType.listTitle, color = if (v.value == null) Ridge.inkMuted else Ridge.brand)
    }
}

@Composable
private fun V2Row(change: V2Change, comparison: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(change.name + " · " + change.decision, style = RidgeType.listTitle, color = Ridge.ink)
            Text(change.summary, style = RidgeType.bodySm, color = Ridge.inkMuted)
            Text(comparison, style = RidgeType.bodySm, color = Ridge.brand)
            change.requires?.let { Text("needs " + it.name, style = RidgeType.bodySm, color = Ridge.inkSubtle) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

/** The v10 and v2 values one change moves, for [day]. */
private fun v2Comparison(change: V2Change, old: V10MetricRegistry, new: V10MetricRegistry, day: DayKey): String {
    fun f(r: V10MetricRegistry, id: MetricId): String {
        val v = r.value(id, day)
        return v.value?.let { String.format(Locale.ROOT, "%.${r.definition(id).decimals}f", it) + unitLabel(v.unit) }
            ?: "— (" + (v.missing?.name?.lowercase() ?: "none") + ")"
    }
    fun pair(id: MetricId) = id.name.lowercase() + " " + f(old, id) + " → " + f(new, id)
    fun band(r: V10MetricRegistry, id: MetricId) = r.normalRange(id, day)?.let {
        String.format(Locale.ROOT, "%.1f–%.1f %s/%d", it.low, it.high, it.basis.name.lowercase().take(4), it.nights)
    } ?: "none"
    return when (change) {
        V2Change.ONE_SLEEP_NEED -> listOf(
            "need " + f(old, MetricId.SLEEP_NEED_SLEEP_TAB) + " (Sleep tab) / " + f(old, MetricId.SLEEP_NEED_DEBT) +
                " (debt) → " + f(new, MetricId.SLEEP_NEED),
            pair(MetricId.HOURS_VS_NEEDED), pair(MetricId.SLEEP_DEBT),
        ).joinToString("\n")
        V2Change.PLAN_SLEEP_HALF -> "plan: Effort only → aim for " + f(new, MetricId.SLEEP_NEED)
        V2Change.SLEPT_ON_TARGET -> {
            val rest = old.value(MetricId.REST, day).value
            val asleep = new.value(MetricId.SLEEP_DURATION, day).value
            val need = new.value(MetricId.SLEEP_NEED, day).value
            "on target: " + (rest?.let { (it >= 85.0).toString() + " (Rest " + it.toInt() + ")" } ?: "untracked") +
                " → " + (if (asleep != null && asleep > 0 && need != null) (asleep >= need * 60).toString() else "untracked")
        }
        V2Change.NORMAL_BAND_60 -> listOf(MetricId.RESTING_HR, MetricId.HRV, MetricId.RESP_RATE, MetricId.SKIN_TEMP_DEV)
            .joinToString("\n") { it.name.lowercase() + " " + band(old, it) + " → " + band(new, it) }
        V2Change.PAST_DAY_LOAD -> pair(MetricId.ACWR) + "\n" + pair(MetricId.READINESS)
        V2Change.REST_CARRY_BOUND -> pair(MetricId.REST)
        V2Change.NO_ASSUMED_AGE -> listOf(MetricId.MAX_HR, MetricId.VO2_MAX, MetricId.FITNESS_AGE, MetricId.BODY_AGE)
            .joinToString("\n", transform = ::pair)
        V2Change.CONSISTENCY_PER_NIGHT -> "Sleep › Consistency tile and the bed/wake card's score (not a registry metric)"
        V2Change.EXPLORE_CALENDAR_WINDOW -> "Insights › Explore: W/M/3M… by calendar days from the latest reading; widens below two readings"
        V2Change.SPO2_ONE_SOURCE -> pair(MetricId.SPO2)
        V2Change.HR_DAILY_STATS -> "Insights › Average / Max heart rate: empty → each day's mean / peak of the heart-rate samples (not registry metrics)"
        V2Change.BEDTIME_DEBT_PAYBACK -> {
            val debt = new.value(MetricId.SLEEP_DEBT, day).value
            "bedtime: wake − need (" + f(new, MetricId.SLEEP_NEED) + ") → wake − (need + " +
                com.noop.ui.RecommendedBedtime.paybackMin(debt) + " min of the " + (debt?.toInt() ?: 0) + " min debt)"
        }
        V2Change.NAP_PLAN_TARGET -> "plan: aim for " + f(new, MetricId.SLEEP_NEED) +
            " → that, less today's nap asleep minutes (Today's plan card; naps are not a registry metric)"
        V2Change.LOGICAL_DAY -> {
            val now = java.time.ZonedDateTime.now()
            "now " + now.minusHours(4).toLocalDate() + " → " + dayKeyFor(now.toInstant(), now.zone) +
                " (they differ only on a DST morning)"
        }
    }
}

private fun unitLabel(u: MetricUnit): String = when (u) {
    MetricUnit.PERCENT -> " %"
    MetricUnit.MILLISECONDS -> " ms"
    MetricUnit.BPM -> " bpm"
    MetricUnit.BREATHS_PER_MIN -> " /min"
    MetricUnit.CELSIUS_DELTA -> " °C"
    MetricUnit.MINUTES -> " min"
    MetricUnit.HOURS -> " h"
    MetricUnit.KCAL -> " kcal"
    MetricUnit.ML_KG_MIN -> " ml/kg/min"
    MetricUnit.YEARS -> " yr"
    MetricUnit.STEPS -> " steps"
    else -> ""
}
