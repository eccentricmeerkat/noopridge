package com.noop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.metrics.DayKey
import com.noop.metrics.MetricId
import com.noop.metrics.V10MetricRegistry
import kotlin.math.roundToInt

// RS-3c: the Sleep page's top card. A number in an orb, with no band word; the parts are explained in the guide.

/** The score of one night as the card has it: not read yet, or read ([value] null when the night can't be scored). */
internal data class SleepScoreReading(val value: Double?)

/** Reads [dayKey]'s Sleep score from the registry, the same figure Insights and Key Metrics chart. */
@Composable
internal fun RidgeSleepScoreCardHost(vm: AppViewModel, dayKey: String?, loadKey: Any?, onHow: () -> Unit) {
    var reading by remember(dayKey) { mutableStateOf<SleepScoreReading?>(null) }
    LaunchedEffect(dayKey, loadKey) {
        reading = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val day = dayKey?.let { runCatching { DayKey.of(it) }.getOrNull() } ?: return@withContext SleepScoreReading(null)
            SleepScoreReading(runCatching { V10MetricRegistry(registryInputsFor(vm)).value(MetricId.SLEEP_SCORE, day).value }.getOrNull())
        }
    }
    RidgeSleepScoreCard(reading) {
        ScoringGuideLink.pending = GuideScore.SLEEP_SCORE
        onHow()
    }
}

/** RS-3h: 15% under the design's 156 dp; the box around it keeps the card's height. */
private val SLEEP_SCORE_ORB = 133.dp

/**
 * RS-3g: the design's night-sky card. The "i" beside the title opens the guide on the Sleep score. The stars hold still
 * under reduced motion, battery saver and Quiet motion.
 */
@Composable
internal fun RidgeSleepScoreCard(reading: SleepScoreReading?, onHow: () -> Unit) {
    val score = reading?.value?.roundToInt()
    val shown = score?.toString() ?: "—"
    val title = stringResource(R.string.sleep_score_title)
    val a11y = stringResource(R.string.sleep_score_a11y, shown)
    val info = stringResource(R.string.sleep_score_info_a11y)
    NightSkyCard(Modifier.fillMaxWidth()) {
        Column(
            // RS-3h: tighter than the design above the title and under the orb.
            Modifier.fillMaxWidth().padding(start = Ridge.space5, top = 14.dp, end = Ridge.space5, bottom = Ridge.space2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title.uppercase(Locale.getDefault()),
                    style = RidgeType.eyebrow.copy(letterSpacing = 0.26.em), color = RidgeNightSky.label,
                )
                // A 20 dp mark; Compose widens its touch target to the 48 dp minimum.
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onHow)
                        .border(1.5.dp, RidgeNightSky.label.copy(alpha = 0.8f), CircleShape)
                        .semantics { contentDescription = info },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "i",
                        style = RidgeType.titleLg.copy(fontSize = 12.sp, lineHeight = 12.sp),
                        color = RidgeNightSky.label,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            Box(Modifier.size(168.dp).drawBehind { drawOrbHalo(78.dp.toPx()) }, contentAlignment = Alignment.Center) {
                LiquidOrb(
                    fraction = (reading?.value ?: 0.0) / 100.0, display = shown, liquid = RidgeLiquid.NIGHT, diameter = SLEEP_SCORE_ORB,
                    glass = true, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y },
                )
            }
            if (reading != null && score == null) {
                Text(stringResource(R.string.sleep_score_needs), style = RidgeType.bodySm, color = RidgeNightSky.label, textAlign = TextAlign.Center)
            }
        }
    }
}
