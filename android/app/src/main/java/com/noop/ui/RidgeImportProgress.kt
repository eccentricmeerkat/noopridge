package com.noop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noop.R
import com.noop.data.ImportProgress
import com.noop.data.ImportStage
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The backup import progress (Ridge): "Importing your backup", the step it is on out of however many this import has, a
 * segmented bar, how far through the step it is (megabytes read or copied, or tables merged), a time estimate when one
 * can be given, and a Cancel that is offered only until the live data starts to change. Put once, near the root, so it
 * shows over whichever screen started the import ([BackupImportRunner]).
 */
@Composable
internal fun BackupImportHost() {
    val state by BackupImportRunner.state.collectAsState()
    val s = state ?: return
    Dialog(
        onDismissRequest = {}, // modal: only Cancel (while it can) or the end of the import closes it
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        BackupImportProgressCard(s.progress, s.stageStartedAtMs, s.cancelling, onCancel = BackupImportRunner::cancel)
    }
}

@StringRes
internal fun importStageLabel(stage: ImportStage): Int = when (stage) {
    ImportStage.READING -> R.string.import_stage_reading
    ImportStage.UNPACKING -> R.string.import_stage_unpacking
    ImportStage.CHECKING -> R.string.import_stage_checking
    ImportStage.SAFETY_COPY -> R.string.import_stage_safety
    ImportStage.REPLACING -> R.string.import_stage_replacing
    ImportStage.MERGING -> R.string.import_stage_merging
    ImportStage.VERIFYING -> R.string.import_stage_verifying
    ImportStage.FINISHING -> R.string.import_stage_finishing
}

/** The fraction of the current step done, or null when the step has no measurable length. */
internal fun importStepFraction(p: ImportProgress): Float? =
    if (p.totalBytes > 0L) (p.doneBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else null

@Composable
internal fun BackupImportProgressCard(p: ImportProgress, stageStartedAtMs: Long, cancelling: Boolean, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(Ridge.radiusLg)
    // A clock tick so the estimate moves while the byte count does not (the integrity check has no count at all).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.stage) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val stage = stringResource(importStageLabel(p.stage))
    val stepLine = stringResource(R.string.import_progress_step, p.step, p.steps, stage)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Ridge.sheetTop, Ridge.sheetBottom)))
            .border(1.dp, Ridge.glassStroke, shape)
            .padding(Ridge.space6),
        verticalArrangement = Arrangement.spacedBy(Ridge.space3),
    ) {
        Text(
            stringResource(R.string.import_progress_title),
            style = RidgeType.titleSm, color = Ridge.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stepLine, style = RidgeType.listTitle, color = Ridge.ink)
        SegmentedBar(p, Modifier.semantics { contentDescription = stepLine })

        val detail = importDetailLine(p)
        if (detail != null) Text(detail, style = RidgeType.bodySm, color = Ridge.inkMuted)
        val secondsLeft = importSecondsLeft(now - stageStartedAtMs, p.doneBytes, p.totalBytes)
        val eta = when {
            secondsLeft == null -> null
            secondsLeft < 60 -> stringResource(R.string.import_progress_eta_short)
            else -> stringResource(R.string.import_progress_eta_min, (secondsLeft + 59) / 60)
        }
        if (eta != null) Text(eta, style = RidgeType.bodySm, color = Ridge.inkMuted)
        if (p.totalBytes <= 0L && p.stage in QUIET_STAGES) {
            Text(stringResource(R.string.import_progress_quiet), style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
        Text(stringResource(R.string.import_progress_note), style = RidgeType.bodySm, color = Ridge.inkSubtle)

        if (p.cancellable) {
            RidgeButton(
                text = stringResource(if (cancelling) R.string.import_progress_cancelling else android.R.string.cancel),
                onClick = onCancel,
                enabled = !cancelling,
                kind = RidgeButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(stringResource(R.string.import_progress_nocancel), style = RidgeType.bodySm, color = Ridge.inkSubtle)
        }
    }
}

private val QUIET_STAGES = setOf(ImportStage.CHECKING, ImportStage.VERIFYING)

/** One segment per step: finished steps full, the current one filled as far as it has got, the rest empty. */
@Composable
private fun SegmentedBar(p: ImportProgress, modifier: Modifier = Modifier) {
    val current = importStepFraction(p) ?: 0.45f // a step with no length shows part-filled rather than looking stuck at empty
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ridge.space1)) {
        for (i in 1..p.steps) {
            val fill = when {
                i < p.step -> 1f
                i == p.step -> current
                else -> 0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(Ridge.radiusPill))
                    .background(Ridge.glassFillStrong),
            ) {
                if (fill > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Ridge.brand))
            }
        }
    }
}

/** "85.0 of 193.1 MB · 44%" for a copy, "Table 4 of 28" for a merge, nothing for a step with no count. */
@Composable
private fun importDetailLine(p: ImportProgress): String? {
    if (p.totalBytes <= 0L) return null
    val pct = (p.doneBytes * 100 / p.totalBytes).toInt().coerceIn(0, 100)
    return when (p.stage) {
        ImportStage.MERGING -> stringResource(R.string.import_progress_tables, (p.doneBytes + 1).coerceAtMost(p.totalBytes).toInt(), p.totalBytes.toInt())
        ImportStage.UNPACKING, ImportStage.SAFETY_COPY, ImportStage.REPLACING ->
            stringResource(R.string.import_progress_bytes, megabytes(p.doneBytes), megabytes(p.totalBytes), pct)
        else -> null
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1_048_576.0)
