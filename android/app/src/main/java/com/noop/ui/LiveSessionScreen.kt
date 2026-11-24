package com.noop.ui

import com.noop.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.sp
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import com.noop.ui.ridge.RidgeChip
import com.noop.ui.ridge.RidgeChipKind
import com.noop.ui.ridge.RidgeEyebrow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.noop.analytics.LiveSessionEngine
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

// MARK: - LiveSessionScreen — the Live Session ("silent guardian") surface + end-of-session summary
//
// The deliberately NEAR-EMPTY session screen the design contract asks for
// (docs/superpowers/specs/2026-07-04-live-sessions-design.md): one breathing ring, one guarding line, a
// Charge sentence that fades after ~6s, and an End button. No live numbers by default — silence (and an
// empty screen) means you're on track; a long-press on the ring reveals the live bpm briefly for the
// curious. The ring encodes the engine state: in-band = lit teal breathing slowly, below = dim teal,
// above = hot orange, STALE = grey with coaching honestly declared paused (never a fabricated read).
// A thin outer arc fills with accrued in-band time toward a 60-minute session.
//
// Presented from Today as a full-screen Dialog (the same idiom the live-workout overlay and the Charge
// breakdown sheet use). Dismissing the dialog does NOT end the session — [LiveSessionRunner.active] keeps
// the guardian coaching (wrist-first) and Today's entry card becomes the way back in. The RUNNER owns the
// realtime-HR arming (start/end), so this screen deliberately does not touch requestRealtimeHr itself.

/** Session goal the outer in-band arc fills toward (display-only; nothing ends at the hour). */
private const val IN_BAND_GOAL_SEC: Double = 3600.0

/** How long the long-press bpm reveal stays up before the screen returns to silence. */
private const val BPM_REVEAL_MS: Long = 4_000L

/** How long the Charge sentence holds before fading out (design contract: ~6s). */
private const val CHARGE_SENTENCE_MS: Long = 6_000L

/**
 * Begin a Live Session (or return the one already running). Builds the recovery-gated
 * [LiveSessionEngine.Config] from the SAME sources the rest of the app reads — today's Charge
 * (recovery) and resting HR from the merged daily metrics (latest known resting HR as the fallback,
 * 60 bpm only when the app has never seen one), HR-max from the user's profile (manual override or
 * Tanaka) — and wires the runner's side effects to the existing app-wide singletons: the BLE live bpm,
 * the strap buzz, the repository upsert, and the ref-counted realtime-HR stream. The tick runs on the
 * app-wide [AppViewModel]'s viewModelScope, so navigating away never kills the guardian.
 */
fun startOrResumeLiveSession(vm: AppViewModel, context: Context): LiveSessionRunner {
    val today = vm.today.value
    // Latest known resting HR: today's row first, else the most recent scored day (recentDays is
    // oldest → newest). 60 bpm is the same neutral default AutoWorkoutDetector uses when nothing is known.
    val restingHr = today?.restingHr
        ?: vm.recentDays.value.lastOrNull { it.restingHr != null }?.restingHr
        ?: 60
    val profile = ProfileStore.from(context.applicationContext)
    val runner = LiveSessionRunner(
        config = LiveSessionEngine.Config(
            restingHR = restingHr.toDouble(),
            hrMax = profile.hrMax.toDouble(),
            charge = today?.recovery,
        ),
        deviceId = vm.activeStrapId,
        scope = vm.viewModelScope,
        readBpm = { vm.live.value.heartRate },
        buzz = { loops -> vm.buzz(loops, HapticPrefs.LIVE_SESSION) },
        persist = { row -> vm.repo.upsertLiveSession(row) },
        realtimeHr = { arm -> if (arm) vm.requestRealtimeHr() else vm.releaseRealtimeHr() },
    )
    // begin() is replace-guarded: an in-flight session is returned as-is and the fresh runner (which has
    // no side effects until started) is simply dropped, so a double-tap can never fork two sessions.
    return LiveSessionRunner.begin(runner)
}

/**
 * The full-screen Live Session surface. Renders the ACTIVE runner ([LiveSessionRunner.active]) — the
 * session itself belongs to the app, not to this dialog — and flips to the summary when the snapshot
 * reports ended (End tap here, or the runner's stale auto-end). If there is no active session (cleared
 * elsewhere, process restart), it closes out rather than showing an empty guardian.
 */
@Composable
fun LiveSessionScreen(vm: AppViewModel, onClose: () -> Unit) {
    val active by LiveSessionRunner.active.collectAsStateWithLifecycle()
    val runner = active
    LaunchedEffect(runner == null) { if (runner == null) onClose() }
    if (runner == null) return

    val snap by runner.snapshot.collectAsStateWithLifecycle()
    // B2-Ra LS-1: on the Ridge shell the board's dark teal glow behind the page.
    val bg = if (ridgeShell) {
        Modifier.background(Ridge.bg000).background(
            Brush.radialGradient(listOf(Ridge.bg200, Ridge.bg000), radius = 1400f),
        )
    } else Modifier.background(Palette.surfaceBase)
    Box(modifier = Modifier.fillMaxSize().then(bg)) {
        if (ridgeShell) {
            if (snap.ended) {
                RidgeLiveSessionSummary(vm = vm, runner = runner, snap = snap, onDone = {
                    LiveSessionRunner.clear(runner)
                    onClose()
                })
            } else {
                RidgeLiveSessionBody(runner = runner, snap = snap, onEnd = { runner.end() }, onClose = onClose)
            }
        } else if (snap.ended) {
            LiveSessionSummary(
                vm = vm,
                runner = runner,
                snap = snap,
                onDone = {
                    LiveSessionRunner.clear(runner)
                    onClose()
                },
            )
        } else {
            LiveSessionBody(runner = runner, snap = snap, onEnd = { runner.end() })
        }
    }
}

// MARK: - The live session body (ring + guarding line + Charge sentence + End)

@Composable
private fun LiveSessionBody(
    runner: LiveSessionRunner,
    snap: LiveSessionRunner.Snapshot,
    onEnd: () -> Unit,
) {
    val out = snap.output
    val stale = out?.status == LiveSessionEngine.Status.STALE

    // Long-press bpm reveal: a one-shot timestamp so repeated long-presses re-arm the 4s window. The
    // reveal shows ONLY a real smoothed read — a stale stream reveals nothing (never fabricate).
    var revealTick by remember { mutableLongStateOf(0L) }
    var revealBpm by remember { mutableStateOf(false) }
    LaunchedEffect(revealTick) {
        if (revealTick > 0L) {
            revealBpm = true
            delay(BPM_REVEAL_MS)
            revealBpm = false
        }
    }

    // The Charge sentence: shown on entry, then fades away (~6s) so the screen settles to near-empty.
    var chargeSentenceVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(CHARGE_SENTENCE_MS)
        chargeSentenceVisible = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header — title + BETA pill (the contract labels the feature BETA at every surface).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(uiString(R.string.l10n_live_session_screen_live_session_73c925a5), style = NoopType.title1, color = Palette.textPrimary)
            StatePill("BETA", tone = StrandTone.Accent, showsDot = false)
        }

        Spacer(Modifier.weight(1f))

        GuardianRing(
            position = out?.position,
            stale = stale,
            settled = out != null,
            inBandFraction = ((out?.inBandSeconds ?: 0.0) / IN_BAND_GOAL_SEC).coerceIn(0.0, 1.0).toFloat(),
            revealedBpm = if (revealBpm && !stale) out?.smoothedBpm?.roundToInt() else null,
            onLongPress = { revealTick = System.currentTimeMillis() },
        )

        Spacer(Modifier.height(24.dp))

        // The one line of copy. STALE says so honestly (coaching paused, nothing accrues); otherwise the
        // guarding promise — the whole design is that this screen has nothing to watch.
        Text(
            if (stale) "Signal lost — coaching paused."
            else "Guarding your session. Silence means you're on track.",
            style = NoopType.subhead,
            color = if (stale) Palette.textTertiary else Palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // The Charge sentence — flavoured off the session's opening charge/band, fading after ~6s. The
        // fixed-height slot keeps the centred stack from jumping when it goes.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            // Fully qualified: inside this BoxScope the enclosing ColumnScope would otherwise be picked as
            // the (illegal) implicit receiver for the ColumnScope.AnimatedVisibility extension.
            androidx.compose.animation.AnimatedVisibility(visible = chargeSentenceVisible, exit = fadeOut(tween(900))) {
                Text(
                    liveSessionChargeSentence(runner.config.charge),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.statusCritical, contentColor = Palette.surfaceBase,
            ),
        ) { Text(uiString(R.string.l10n_live_session_screen_end_session_8c0f4c33), style = NoopType.headline) }
    }
}

// MARK: - The guardian ring

/**
 * The breathing state ring + the thin outer in-band arc. State encoding per the design contract:
 * in-band = lit teal breathing slowly, below = dim teal, above = hot orange, STALE = grey and still.
 * The outer arc fills with accrued in-band time toward the 60-minute goal (teal — it only ever counts
 * honest in-band seconds — greying with the rest of the ring when the signal is lost). A long-press
 * anywhere on the ring fires [onLongPress] (the bpm reveal); [revealedBpm] renders centred when set.
 * The breathing transition is only composed while it actually breathes (the ConnectionDot perf idiom).
 */
@Composable
private fun GuardianRing(
    position: LiveSessionEngine.Position?,
    stale: Boolean,
    settled: Boolean,
    inBandFraction: Float,
    revealedBpm: Int?,
    onLongPress: () -> Unit,
    ridge: Boolean = false,
) {
    // B2-Ra LS-4: Ridge maps the same states to the board's teal, orange and subtle grey.
    val teal = if (ridge) Ridge.dataBreath else Palette.metricCyan
    // Dim teal until the first engine output lands (settled=false, the first ~1s) — not grey, which
    // would flash "signal lost" before the stream has had a chance to say anything.
    val target = when {
        stale -> if (ridge) Ridge.inkSubtle.copy(alpha = 0.5f) else Palette.textTertiary
        !settled -> teal.copy(alpha = 0.30f)
        position == LiveSessionEngine.Position.ABOVE -> if (ridge) Ridge.stateLow else Palette.statusCritical
        position == LiveSessionEngine.Position.BELOW -> teal.copy(alpha = 0.30f)
        else -> teal
    }
    val ringColor by animateColorAsState(target, animationSpec = tween(600), label = uiString(R.string.l10n_live_session_screen_guardianringcolor_f20ad757))

    val renderStill = rememberPoseStill()
    val breathing = !stale && settled && position == LiveSessionEngine.Position.IN_BAND && !renderStill

    val stateLabel = when {
        stale -> "Signal lost, coaching paused"
        position == LiveSessionEngine.Position.ABOVE -> "Above today's band"
        position == LiveSessionEngine.Position.BELOW -> "Below today's band"
        else -> "In today's band"
    }
    Box(
        modifier = Modifier
            .size(260.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .semantics { contentDescription = uiString(R.string.l10n_live_session_screen_session_ring_statelabel_long_press_to_42ac1b3b, stateLabel) },
        contentAlignment = Alignment.Center,
    ) {
        if (breathing) {
            // Slow breath: ~5.2s per full cycle, composed only while in-band so an off-band or stale
            // ring keeps zero per-frame animation work.
            val breath by rememberInfiniteTransition(label = uiString(R.string.l10n_live_session_screen_guardianbreath_f76a0607)).animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2_600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = uiString(R.string.l10n_live_session_screen_guardianbreathscale_38b62ee7),
            )
            RingCanvas(ringColor, breath, inBandFraction, arcColor = if (stale) ringColor else if (ridge) Ridge.brand else teal, ridge = ridge)
        } else {
            RingCanvas(ringColor, 0f, inBandFraction, arcColor = if (stale) ringColor else if (ridge) Ridge.brand else teal, ridge = ridge)
        }
        // Long-press reveal — the ONLY number this screen can show, and only from a live smoothed read.
        if (revealedBpm != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    uiString(R.string.l10n_live_session_screen_revealedbpm_dbe24cbe, revealedBpm),
                    style = if (ridge) RidgeType.numeralXl else NoopType.number(44f), color = if (ridge) Ridge.ink else Palette.textPrimary,
                )
                Text("bpm", style = NoopType.caption, color = if (ridge) Ridge.inkMuted else Palette.textTertiary)
            }
        }
    }
}

@Composable
private fun RingCanvas(
    ringColor: Color,
    breath: Float,
    inBandFraction: Float,
    arcColor: Color,
    ridge: Boolean = false,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val outerR = min(size.width, size.height) / 2f - 3.dp.toPx()

        // Thin outer arc — the in-band hour filling up. Track first, then the honest progress.
        val arcStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = if (ridge) Ridge.glassStroke else Palette.hairline,
            radius = outerR,
            center = centre,
            style = Stroke(width = 3.dp.toPx()),
        )
        if (inBandFraction > 0f) {
            // B2-Ra LS-3: the Ridge arc glows, a wider faint stroke under the line.
            if (ridge) drawArc(
                color = arcColor.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f * inBandFraction,
                useCenter = false,
                topLeft = Offset(centre.x - outerR, centre.y - outerR),
                size = Size(outerR * 2f, outerR * 2f),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * inBandFraction,
                useCenter = false,
                topLeft = Offset(centre.x - outerR, centre.y - outerR),
                size = Size(outerR * 2f, outerR * 2f),
                style = arcStroke,
            )
        }

        // The main state ring, breathing scale ±4%, with a faint inner wash that swells with the breath.
        val ringR = (outerR - 26.dp.toPx()) * (1f + 0.04f * breath)
        drawCircle(
            color = ringColor.copy(alpha = ringColor.alpha * (0.05f + 0.06f * breath)),
            radius = ringR - 10.dp.toPx(),
            center = centre,
        )
        drawCircle(
            color = ringColor,
            radius = ringR,
            center = centre,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// MARK: - The end-of-session summary

@Composable
private fun LiveSessionSummary(
    vm: AppViewModel,
    runner: LiveSessionRunner,
    snap: LiveSessionRunner.Snapshot,
    onDone: () -> Unit,
) {
    val inBandSec = snap.output?.inBandSeconds ?: 0.0
    val teal = Palette.metricCyan

    // The streak line, from the banked rows (this session's end row is already upserted by end()).
    // "N sessions guarded" counts COMPLETED sessions; the day-streak rides along when it's a real run.
    var guardedCount by remember { mutableStateOf<Int?>(null) }
    var streakDays by remember { mutableStateOf(0) }
    LaunchedEffect(runner) {
        val rows = runCatching { vm.repo.recentLiveSessions(runner.deviceId, 365) }
            .getOrDefault(emptyList())
            .filter { it.endTs != null }
        guardedCount = rows.size
        val zone = ZoneId.systemDefault()
        streakDays = liveSessionStreakDays(
            sessionDays = rows.map { Instant.ofEpochSecond(it.startTs).atZone(zone).toLocalDate() },
            today = LocalDate.now(zone),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline("Live Session", color = teal)
                Text(uiString(R.string.l10n_live_session_screen_session_summary_f9418e16), style = NoopType.title1, color = Palette.textPrimary)
            }
            StatePill("BETA", tone = StrandTone.Accent, showsDot = false)
        }

        // The stale auto-end declares itself — an unexplained early end would read as a bug.
        if (snap.endedAutomatically) {
            Text(
                uiString(R.string.l10n_live_session_screen_the_strap_signal_was_gone_for_57a2fead),
                style = NoopType.footnote,
                color = Palette.statusWarning,
            )
        }

        // The one plain-English verdict (pure helper, honest tiers — see LiveSessionRunner.kt).
        Text(
            liveSessionVerdict(inBandSec, snap.belowSec, snap.aboveSec),
            style = NoopType.title2,
            color = Palette.textPrimary,
        )
        Text(
            uiString(R.string.l10n_live_session_screen_guarded_for_elapsedclock_snap_elapsedsec_tolong_a61f2d32, elapsedClock(snap.elapsedSec.toLong())),
            style = NoopType.subhead,
            color = Palette.textSecondary,
        )

        // Where the time went — the three accrued buckets, on the shared StatTile.
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                modifier = Modifier.weight(1f), label = uiString(R.string.l10n_live_session_screen_in_band_2c23d86e),
                value = elapsedClock(inBandSec.toLong()), accent = teal,
            )
            StatTile(
                modifier = Modifier.weight(1f), label = uiString(R.string.l10n_live_session_screen_below_5ba07a31),
                value = elapsedClock(snap.belowSec.toLong()), accent = Palette.textSecondary,
            )
            StatTile(
                modifier = Modifier.weight(1f), label = uiString(R.string.l10n_live_session_screen_above_6370c271),
                value = elapsedClock(snap.aboveSec.toLong()), accent = Palette.statusCritical,
            )
        }

        // What the wrist heard: the two cue counts (usually zero — that's the feature working).
        Text(
            liveSessionCueLine(snap.pushCount, snap.easeCount),
            style = NoopType.subhead,
            color = Palette.textSecondary,
        )

        // The streak line — only once the rows have loaded (no placeholder number, ever).
        guardedCount?.let { n ->
            val sessions = "$n session${if (n == 1) "" else "s"} guarded"
            val run = if (streakDays >= 2) " · $streakDays days in a row" else ""
            Text(sessions + run, style = NoopType.subhead, color = teal)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.accent, contentColor = Palette.surfaceBase,
            ),
        ) { Text(uiString(R.string.l10n_live_session_screen_done_e9b450d1), style = NoopType.headline) }
    }
}

// MARK: - B2-Ra: the Ridge Live session (LS-1 to LS-8), from `design/prototype/LiveSession.dc.html`

/** The live screen on the Ridge shell: the board's header, the ring, the in-band line, and an outlined End. */
@Composable
private fun RidgeLiveSessionBody(
    runner: LiveSessionRunner,
    snap: LiveSessionRunner.Snapshot,
    onEnd: () -> Unit,
    onClose: () -> Unit,
) {
    val out = snap.output
    val stale = out?.status == LiveSessionEngine.Status.STALE
    var revealTick by remember { mutableLongStateOf(0L) }
    var revealBpm by remember { mutableStateOf(false) }
    LaunchedEffect(revealTick) {
        if (revealTick > 0L) {
            revealBpm = true
            delay(BPM_REVEAL_MS)
            revealBpm = false
        }
    }
    var chargeSentenceVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(CHARGE_SENTENCE_MS)
        chargeSentenceVisible = false
    }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RidgeLiveHeader(onClose = onClose)
        Spacer(Modifier.weight(1f))
        // LS-5: the Charge sentence above the ring, in the Charge amber, fading after ~6 s.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = chargeSentenceVisible, exit = fadeOut(tween(1200))) {
                Text(
                    liveSessionChargeSentence(runner.config.charge),
                    style = RidgeType.bodySm.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.charge,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        GuardianRing(
            position = out?.position,
            stale = stale,
            settled = out != null,
            inBandFraction = ((out?.inBandSeconds ?: 0.0) / IN_BAND_GOAL_SEC).coerceIn(0.0, 1.0).toFloat(),
            revealedBpm = if (revealBpm && !stale) out?.smoothedBpm?.roundToInt() else null,
            onLongPress = { revealTick = System.currentTimeMillis() },
            ridge = true,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            if (stale) "Signal lost — coaching paused."
            else "Guarding your session. Silence means you're on track.",
            style = RidgeType.body.copy(fontSize = 16.sp), color = Ridge.inkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        // LS-6: minutes in band toward the hour, and how to see the heart rate.
        Text(
            stringResource(R.string.live_ridge_in_band_line, ((out?.inBandSeconds ?: 0.0) / 60.0).toInt()),
            style = RidgeType.bodySm.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkSubtle,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        // LS-7: ending saves the session, so it is the board's secondary button, not a red one.
        RidgeButton(
            text = uiString(R.string.l10n_live_session_screen_end_session_8c0f4c33), onClick = onEnd,
            kind = RidgeButtonKind.Secondary, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** LS-2: the close cross, the serif title and the Beta chip. */
@Composable
private fun RidgeLiveHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = uiString(R.string.l10n_whats_new_sheet_close_bbfa773e), tint = Ridge.ink)
        }
        Text(
            uiString(R.string.l10n_live_session_screen_live_session_73c925a5),
            style = RidgeType.titleSm, color = Ridge.ink, modifier = Modifier.weight(1f),
        )
        RidgeChip(stringResource(R.string.live_ridge_beta), kind = RidgeChipKind.Alert)
    }
}

/** LS-8: the board's summary: eyebrow, serif headline, the in-band / below / above bar, three glass tiles, Done. */
@Composable
private fun RidgeLiveSessionSummary(
    vm: AppViewModel,
    runner: LiveSessionRunner,
    snap: LiveSessionRunner.Snapshot,
    onDone: () -> Unit,
) {
    val inBandSec = snap.output?.inBandSeconds ?: 0.0
    var guardedCount by remember { mutableStateOf<Int?>(null) }
    var streakDays by remember { mutableStateOf(0) }
    LaunchedEffect(runner) {
        val rows = runCatching { vm.repo.recentLiveSessions(runner.deviceId, 365) }
            .getOrDefault(emptyList())
            .filter { it.endTs != null }
        guardedCount = rows.size
        val zone = ZoneId.systemDefault()
        streakDays = liveSessionStreakDays(
            sessionDays = rows.map { Instant.ofEpochSecond(it.startTs).atZone(zone).toLocalDate() },
            today = LocalDate.now(zone),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RidgeEyebrow(uiString(R.string.l10n_live_session_screen_session_summary_f9418e16))
            Text(
                uiString(R.string.l10n_live_session_screen_guarded_for_elapsedclock_snap_elapsedsec_tolong_a61f2d32, elapsedClock(snap.elapsedSec.toLong())),
                style = RidgeType.titleLg.copy(fontSize = 34.sp, lineHeight = 38.sp), color = Ridge.ink, textAlign = TextAlign.Center,
            )
            Text(
                liveSessionVerdict(inBandSec, snap.belowSec, snap.aboveSec),
                style = RidgeType.body, color = Ridge.inkMuted, textAlign = TextAlign.Center,
            )
        }
        if (snap.endedAutomatically) {
            Text(
                uiString(R.string.l10n_live_session_screen_the_strap_signal_was_gone_for_57a2fead),
                style = RidgeType.bodySm, color = Ridge.stateLow,
            )
        }
        // Where the time went, as one bar: in band, below (faint teal), above (orange).
        val total = (inBandSec + snap.belowSec + snap.aboveSec).coerceAtLeast(0.0)
        if (total > 0.0) Row(
            modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(
                inBandSec to Ridge.dataBreath,
                snap.belowSec to Ridge.dataBreath.copy(alpha = 0.4f),
                snap.aboveSec to Ridge.stateLow,
            ).filter { it.first > 0.0 }.forEach { (sec, color) ->
                Box(Modifier.weight((sec / total).toFloat()).fillMaxHeight().background(color))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RidgeLiveTile(uiString(R.string.l10n_live_session_screen_in_band_2c23d86e), elapsedClock(inBandSec.toLong()), Ridge.dataBreath, Modifier.weight(1f))
            RidgeLiveTile(uiString(R.string.l10n_live_session_screen_below_5ba07a31), elapsedClock(snap.belowSec.toLong()), Ridge.ink, Modifier.weight(1f))
            RidgeLiveTile(uiString(R.string.l10n_live_session_screen_above_6370c271), elapsedClock(snap.aboveSec.toLong()), Ridge.stateLow, Modifier.weight(1f))
        }
        Text(liveSessionCueLine(snap.pushCount, snap.easeCount), style = RidgeType.bodySm, color = Ridge.inkMuted)
        guardedCount?.let { n ->
            val sessions = "$n session${if (n == 1) "" else "s"} guarded"
            val run = if (streakDays >= 2) " · $streakDays days in a row" else ""
            Text(sessions + run, style = RidgeType.bodySm, color = Ridge.dataBreath)
        }
        Spacer(Modifier.height(8.dp))
        RidgeButton(
            text = uiString(R.string.l10n_live_session_screen_done_e9b450d1), onClick = onDone,
            kind = RidgeButtonKind.Primary, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One summary tile: an eyebrow over a serif value in its colour, on glass. */
@Composable
private fun RidgeLiveTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Ridge.radiusMd)
    Column(
        modifier.clip(shape).background(Ridge.glassFill).border(1.dp, Ridge.glassStroke, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RidgeEyebrow(label)
        Text(value, style = RidgeType.titleSm, color = valueColor, maxLines = 1)
    }
}

// MARK: - Pure copy helpers (Context-free, JVM-testable — same style as the runner's verdict/streak)

/**
 * The one Charge sentence the session screen shows before settling to silence, flavoured per the
 * session's opening Charge (the same value that gated the band). Null Charge says so honestly — the
 * engine coaches to its middle-of-the-road default band, and we never invent a percentage.
 */
internal fun liveSessionChargeSentence(charge: Double?): String {
    if (charge == null) return "No Charge yet today — guarding a middle-of-the-road band."
    val pct = charge.roundToInt()
    return when {
        pct < 34 -> "Today's ceiling is lower — Charge is $pct%."
        pct < 67 -> "A middling day — Charge is $pct%, so the band sits mid-range."
        else -> "Plenty in the tank — Charge is $pct%, so today's ceiling is higher."
    }
}

/** The summary's cue-count line. Zero cues is the headline case (silence IS the coaching). */
internal fun liveSessionCueLine(pushCount: Int, easeCount: Int): String {
    if (pushCount == 0 && easeCount == 0) return "No buzzes sent."
    val push = "$pushCount push nudge${if (pushCount == 1) "" else "s"}"
    val ease = "$easeCount ease-off${if (easeCount == 1) "" else "s"}"
    return "$push · $ease"
}
