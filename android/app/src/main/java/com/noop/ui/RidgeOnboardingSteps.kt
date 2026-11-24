package com.noop.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.noop.R
import com.noop.ble.WhoopModel
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeButtonKind
import com.noop.ui.ridge.RidgeChip
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeRowDivider

// MARK: - Ridge onboarding steps 3–5 and 7–12 (R8b, H-03 / H-04 / H-05, `Onboarding.dc.html`)
//
// Every line a step states about the app is v10's: the Charge, Effort and Rest lines are the scoring guide's text
// (Rest per D-04-R, v10's composite), the Synthesis bands are v10's (D-02), Charge calibrates over 4 nights and the
// normal ranges over 14 (`HRVReadiness.MIN_NIGHTS`). The strap step makes the same connect calls v10's did.

/** A Ridge content step: the "N of M · label" eyebrow, the title and line, then [content]. */
@Composable
private fun RidgeStep(
    index: Int,
    total: Int,
    @StringRes label: Int,
    @StringRes title: Int,
    @StringRes sub: Int,
    cta: String,
    onCta: () -> Unit,
    onBack: () -> Unit,
    onSkip: (() -> Unit)? = null,
    ctaEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    RidgeWizardShell(
        index = index, total = total,
        eyebrow = stringResource(R.string.onb_ridge_step_of_label, index + 1, total, stringResource(label)),
        title = stringResource(title), subtitle = stringResource(sub),
        cta = cta, onCta = onCta, onBack = onBack, onSkip = onSkip, ctaEnabled = ctaEnabled, content = content,
    )
}

/** One board row: a tinted 36 dp icon square, a semibold title and a muted line. */
@Composable
private fun RidgeInfoRow(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(Ridge.brand.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(18.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = STEP_ROW_TITLE, color = Ridge.ink)
            Text(body, style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
    }
}

private val STEP_ROW_TITLE = RidgeType.listTitle.copy(fontSize = 15.sp, lineHeight = 20.sp)

@Composable
private fun StepFootnote(text: String) {
    Text(text, style = RidgeType.bodySm.copy(fontSize = 12.sp), color = Ridge.inkSubtle)
}

// MARK: 3 · What Noop Ridge hopes to do

@Composable
internal fun RidgeAimsStep(index: Int, total: Int, onBack: () -> Unit, onNext: () -> Unit) {
    RidgeStep(
        index, total, R.string.onb_ridge_label_aims, R.string.onb_ridge_aims_title, R.string.onb_ridge_aims_sub,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onNext, onBack = onBack,
    ) {
        NoopCard(padding = 18.dp) {
            Column {
                RidgeInfoRow(Icons.Filled.AccessTime, stringResource(R.string.onb_ridge_aim_normal_t), stringResource(R.string.onb_ridge_aim_normal_d))
                RidgeRowDivider()
                RidgeInfoRow(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.onb_ridge_aim_plan_t), stringResource(R.string.onb_ridge_aim_plan_d))
                RidgeRowDivider()
                RidgeInfoRow(Icons.Filled.Shield, stringResource(R.string.onb_ridge_aim_data_t), stringResource(R.string.onb_ridge_aim_data_d))
            }
        }
        StepFootnote(stringResource(R.string.onb_ridge_aims_foot))
    }
}

// MARK: 4 · Your three numbers

/** The three scores the numbers step shows: the orb's liquid, an illustrative fill, its name and its v10 line. */
internal enum class OnboardingScore(val liquid: RidgeLiquid, val sample: Int, @StringRes val label: Int, @StringRes val line: Int) {
    CHARGE(RidgeLiquid.CHARGE, 72, R.string.trends_charge, R.string.scoring_guide_charge_body),
    EFFORT(RidgeLiquid.EFFORT, 45, R.string.trends_effort, R.string.scoring_guide_effort_body),
    REST(RidgeLiquid.REST, 90, R.string.trends_rest, R.string.scoring_guide_rest_body),
}

@Composable
internal fun RidgeNumbersStep(index: Int, total: Int, onBack: () -> Unit, onNext: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf(OnboardingScore.CHARGE) }
    RidgeStep(
        index, total, R.string.onb_ridge_label_numbers, R.string.onb_ridge_numbers_title, R.string.onb_ridge_numbers_sub,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onNext, onBack = onBack,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingScore.entries.forEach { score ->
                val on = score == picked
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .border(1.dp, if (on) Ridge.brand.copy(alpha = 0.6f) else Ridge.glassStroke, shape)
                        .background(if (on) Ridge.brand.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent, shape)
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { picked = score }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiquidOrb(
                        fraction = score.sample / 100.0, display = score.sample.toString(), liquid = score.liquid,
                        diameter = 84.dp, glass = true, glow = false,
                    )
                    RidgeEyebrow(stringResource(score.label))
                }
            }
        }
        NoopCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(picked.label), style = RidgeType.numeralMd.copy(fontSize = 22.sp, lineHeight = 26.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.onb_ridge_scale_0_100), style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
                Text(stringResource(picked.line), style = RidgeType.body, color = Ridge.ink)
            }
        }
        StepFootnote(stringResource(R.string.onb_ridge_numbers_hint))
    }
}

// MARK: 5 · From numbers to a plan

/** The Synthesis bands as the onboarding draws them: v10's floors (D-02), each band's last whole score, its word. */
internal fun synthesisBandRanges(): List<IntRange> = SYNTHESIS_BAND_FLOORS.mapIndexed { i, lo ->
    val hi = SYNTHESIS_BAND_FLOORS.getOrNull(i + 1)?.let { it.toInt() - 1 } ?: 100
    lo.toInt()..hi
}

private val SYNTHESIS_WORDS = listOf(
    R.string.today_synthesis_depleted, R.string.today_synthesis_low, R.string.today_synthesis_steady,
    R.string.today_synthesis_strong, R.string.today_synthesis_peak,
)

/** The example Effort target the plan step shows. Labelled "for example"; not the user's number. */
private const val EXAMPLE_EFFORT_TARGET = 38

@Composable
internal fun RidgeSynthesisStep(index: Int, total: Int, onBack: () -> Unit, onNext: () -> Unit) {
    RidgeStep(
        index, total, R.string.onb_ridge_label_plan, R.string.onb_ridge_plan_title, R.string.onb_ridge_plan_sub,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onNext, onBack = onBack,
    ) {
        NoopCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RidgeEyebrow(stringResource(R.string.onb_ridge_plan_synth_eyebrow))
                Box(
                    Modifier.fillMaxWidth().height(10.dp).background(
                        Brush.horizontalGradient(SYNTHESIS_STATE_COLOURS), RoundedCornerShape(5.dp),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    synthesisBandRanges().forEachIndexed { i, range ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(SYNTHESIS_WORDS[i]), style = RidgeType.bodySm.copy(fontSize = 12.sp),
                                color = SYNTHESIS_STATE_COLOURS[i], maxLines = 1, textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.onb_ridge_band_range, range.first, range.last),
                                style = RidgeType.bodySm.copy(fontSize = 11.sp), color = Ridge.inkSubtle,
                            )
                        }
                    }
                }
            }
        }
        NoopCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.onb_ridge_plan_example), style = STEP_ROW_TITLE, color = Ridge.ink)
                }
                Text(
                    stringResource(R.string.today_plan_effort_under, EXAMPLE_EFFORT_TARGET.toString()),
                    style = RidgeType.body, color = Ridge.inkMuted,
                )
                RidgeRowDivider()
                Text(stringResource(R.string.onb_ridge_plan_how_t), style = STEP_ROW_TITLE, color = Ridge.ink)
                Text(stringResource(R.string.onb_ridge_plan_how_d), style = RidgeType.bodySm, color = Ridge.inkMuted)
            }
        }
    }
}

// MARK: 7 · Connect your strap (H-04)

/** Every state the strap step can be in, most-connected first. */
internal enum class StrapStepState { BONDED, LIVE_HR_UNPAIRED, PAIRING, NEEDS_PERMISSION, PERMISSION_DENIED, BLUETOOTH_OFF, SEARCHING, READY }

/** Which state the strap step shows. A connection wins over everything; then permission; then the radio; then the scan. */
internal fun strapStepState(
    permitted: Boolean,
    deniedOnce: Boolean,
    bluetoothOn: Boolean,
    scanning: Boolean,
    connected: Boolean,
    bonded: Boolean,
    encryptedBond: Boolean,
): StrapStepState = when {
    bonded && encryptedBond -> StrapStepState.BONDED
    bonded -> StrapStepState.LIVE_HR_UNPAIRED
    connected -> StrapStepState.PAIRING
    !permitted -> if (deniedOnce) StrapStepState.PERMISSION_DENIED else StrapStepState.NEEDS_PERMISSION
    !bluetoothOn -> StrapStepState.BLUETOOTH_OFF
    scanning -> StrapStepState.SEARCHING
    else -> StrapStepState.READY
}

/** Whether the phone's Bluetooth radio is on (null adapter: no Bluetooth, read as off). */
private fun bluetoothIsOn(context: Context): Boolean =
    runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

@Composable
internal fun RidgeStrapStep(
    viewModel: AppViewModel,
    index: Int,
    total: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val context = LocalContext.current
    val live by viewModel.live.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val perms = remember { blePermissions() }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    // Re-read permission and the radio whenever the app returns to the front (the user may have changed either in
    // Settings) and whenever Android announces a Bluetooth state change.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { tick++ }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    @Suppress("UNUSED_VARIABLE") val readTick = tick
    val permitted = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    val bluetoothOn = bluetoothIsOn(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            deniedOnce = false
            if (bluetoothIsOn(context)) viewModel.connect(promoteService = false)
        } else {
            deniedOnce = true
        }
        tick++
    }
    // v10's Connect step: start looking as soon as the step appears, but only with permission already in hand and the
    // radio on, so no prompt lands on top of the step's own explanation.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permitted, bluetoothOn) {
        if (!autoStarted && permitted && bluetoothOn && !live.bonded && !live.connected && !live.scanning) {
            autoStarted = true
            viewModel.connect(promoteService = false)
        }
    }
    val state = strapStepState(permitted, deniedOnce, bluetoothOn, live.scanning, live.connected, live.bonded, live.encryptedBond)

    val (title, note) = when (state) {
        StrapStepState.BONDED -> stringResource(R.string.onboarding_state_bonded_streaming) to
            (live.batteryPct?.let { stringResource(R.string.onboarding_strap_bonded_battery, it.toInt()) }
                ?: stringResource(R.string.onboarding_strap_bonded_ready))
        StrapStepState.LIVE_HR_UNPAIRED -> stringResource(R.string.onboarding_state_live_hr_unpaired) to stringResource(R.string.onboarding_whoop_pairing_mode)
        StrapStepState.PAIRING -> stringResource(R.string.onboarding_state_connected_pairing) to stringResource(R.string.onboarding_connect_searching_subtitle)
        StrapStepState.NEEDS_PERMISSION -> stringResource(R.string.onb_ridge_strap_perm_t) to stringResource(R.string.onb_ridge_strap_perm_d)
        StrapStepState.PERMISSION_DENIED -> stringResource(R.string.onb_ridge_strap_denied_t) to stringResource(R.string.onb_ridge_strap_denied_d)
        StrapStepState.BLUETOOTH_OFF -> stringResource(R.string.onb_ridge_strap_btoff_t) to stringResource(R.string.onb_ridge_strap_btoff_d)
        StrapStepState.SEARCHING -> stringResource(R.string.onboarding_state_searching) to stringResource(R.string.onb_ridge_strap_searching_d)
        StrapStepState.READY -> stringResource(R.string.onboarding_state_ready_scan) to stringResource(R.string.onb_ridge_strap_ready_d)
    }
    val action: Pair<String, () -> Unit>? = when (state) {
        StrapStepState.NEEDS_PERMISSION -> stringResource(R.string.onb_ridge_strap_find) to { launcher.launch(perms) }
        StrapStepState.PERMISSION_DENIED -> stringResource(R.string.onb_ridge_strap_open_settings) to {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        StrapStepState.BLUETOOTH_OFF -> stringResource(R.string.onb_ridge_strap_open_bt) to {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        StrapStepState.READY -> stringResource(R.string.onb_ridge_strap_find) to { viewModel.connect(promoteService = false) }
        StrapStepState.SEARCHING -> stringResource(R.string.onboarding_rescan) to { viewModel.connect(promoteService = false) }
        else -> null
    }

    RidgeStep(
        index, total, R.string.onb_ridge_label_strap, R.string.onb_ridge_strap_title, R.string.onb_ridge_strap_sub,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onNext, onBack = onBack, onSkip = onSkip,
    ) {
        NoopCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val pct = live.batteryPct?.takeIf { state == StrapStepState.BONDED || state == StrapStepState.LIVE_HR_UNPAIRED }
                LiquidOrb(
                    fraction = (pct ?: 0.0) / 100.0, display = pct?.toInt()?.toString().orEmpty(),
                    liquid = RidgeLiquid.BATTERY, diameter = 64.dp, glow = false,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = STEP_ROW_TITLE, color = Ridge.ink)
                    Text(note, style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
                if (state == StrapStepState.SEARCHING || state == StrapStepState.PAIRING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ridge.brand)
                }
            }
        }
        if (!live.bonded) {
            // WHOOP 4.0 or 5.0 / MG: the same choice as v10's Connect step, and the same restart of the search.
            SegmentedPillControl(
                items = WhoopModel.entries.toList(),
                selection = selectedModel,
                label = { it.displayName },
                onSelect = {
                    viewModel.setSelectedModel(it)
                    if (permitted && bluetoothOn) {
                        viewModel.disconnect()
                        viewModel.connect(promoteService = false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                adaptsToAvailableWidth = true,
            )
        }
        action?.let { (label, run) ->
            RidgeButton(text = label, onClick = run, kind = RidgeButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
        }
        NoopCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.onboarding_wear_snug, R.string.onboarding_wear_nearby, R.string.onboarding_wear_charge).forEach {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Text(stringResource(it), style = RidgeType.bodySm, color = Ridge.inkMuted)
                    }
                }
            }
        }
        StepFootnote(stringResource(R.string.onboarding_bluetooth_local_body))
        if (!live.bonded) StepFootnote(stringResource(R.string.l10n_onboarding_screen_no_whoop_you_can_still_continue_ec58d88d))
    }
}

// MARK: 11 · Meet your coach (named Coach until R10a)

@Composable
internal fun RidgeCoachStep(index: Int, total: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: (() -> Unit)?) {
    RidgeStep(
        index, total, R.string.onb_ridge_label_coach, R.string.onb_ridge_coach_title, R.string.onb_ridge_coach_sub,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onNext, onBack = onBack, onSkip = onSkip,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(96.dp).background(Ridge.brand.copy(alpha = 0.12f), RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(44.dp)) }
        }
        NoopCard(padding = 18.dp) {
            Column {
                RidgeInfoRow(Icons.Filled.Key, stringResource(R.string.onb_ridge_coach_key_t), stringResource(R.string.onb_ridge_coach_key_d))
                RidgeRowDivider()
                RidgeInfoRow(Icons.Filled.UploadFile, stringResource(R.string.onb_ridge_coach_sent_t), stringResource(R.string.onb_ridge_coach_sent_d))
                RidgeRowDivider()
                RidgeInfoRow(Icons.Filled.PowerSettingsNew, stringResource(R.string.onb_ridge_coach_off_t), stringResource(R.string.onb_ridge_coach_off_d))
            }
        }
        StepFootnote(stringResource(R.string.onb_ridge_coach_foot))
    }
}

// MARK: 12 · Your first 14 nights (H-05)

/** The unlocks the last step lists, in order. Each is a fact about v10: Charge calibrates over 4 nights, the normal
 *  ranges over 14 (`HRVReadiness.MIN_NIGHTS`), training load compares a week with four. */
internal val FIRST_NIGHTS_UNLOCKS = listOf(
    Triple(R.string.onb_ridge_cal_n1_when, R.string.onb_ridge_cal_n1_t, R.string.onb_ridge_cal_n1_d),
    Triple(R.string.onb_ridge_cal_n4_when, R.string.onb_ridge_cal_n4_t, R.string.onb_ridge_cal_n4_d),
    Triple(R.string.onb_ridge_cal_n14_when, R.string.onb_ridge_cal_n14_t, R.string.onb_ridge_cal_n14_d),
    Triple(R.string.onb_ridge_cal_w4_when, R.string.onb_ridge_cal_w4_t, R.string.onb_ridge_cal_w4_d),
)

@Composable
internal fun RidgeFirstNightsStep(index: Int, total: Int, onBack: () -> Unit, onFinish: () -> Unit) {
    RidgeStep(
        index, total, R.string.onb_ridge_label_cal, R.string.onb_ridge_cal_title, R.string.onb_ridge_cal_sub,
        cta = stringResource(R.string.onb_ridge_go_today), onCta = onFinish, onBack = onBack,
    ) {
        NoopCard(padding = 18.dp) {
            Column {
                FIRST_NIGHTS_UNLOCKS.forEachIndexed { i, (whenRes, t, d) ->
                    if (i > 0) RidgeRowDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(whenRes), style = RidgeType.numeralMd.copy(fontSize = 15.sp, lineHeight = 18.sp),
                            color = Ridge.brand, modifier = Modifier.width(78.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(t), style = STEP_ROW_TITLE, color = Ridge.ink)
                            Text(stringResource(d), style = RidgeType.bodySm, color = Ridge.inkMuted)
                        }
                    }
                }
            }
        }
        NoopCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RidgeChip(text = stringResource(R.string.today_calibrating))
                Text(stringResource(R.string.onb_ridge_cal_tag_d), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
            }
        }
        NoopCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Ridge.brand, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.onb_ridge_cal_tip), style = RidgeType.bodySm, color = Ridge.inkMuted)
            }
        }
    }
}

// MARK: 9 · About you: the date of birth (J-05, D-06)

/** The optional exact date of birth, under the age row. Picking a date stores it; the age stepper re-anchors it. */
@Composable
internal fun RidgeDateOfBirthRow(profile: ProfileStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    val zone = java.time.ZoneId.systemDefault()
    val dob = java.time.Instant.ofEpochMilli(profile.dateOfBirthMillis).atZone(zone).toLocalDate()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.onb_ridge_dob), style = NoopType.subhead, color = Palette.textPrimary)
            Text(
                if (profile.dateOfBirthExact) {
                    dob.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
                } else {
                    stringResource(R.string.onb_ridge_dob_from_age)
                },
                style = RidgeType.bodySm, color = Ridge.inkMuted,
            )
            Text(stringResource(R.string.onb_ridge_dob_hint), style = RidgeType.bodySm.copy(fontSize = 12.sp), color = Ridge.inkSubtle)
        }
        RidgeButton(
            text = stringResource(R.string.onb_ridge_dob_set), kind = RidgeButtonKind.Secondary,
            onClick = {
                android.app.DatePickerDialog(
                    context,
                    { _, y, m, d -> profile.setDateOfBirth(java.time.LocalDate.of(y, m + 1, d)); onChanged() },
                    dob.year, dob.monthValue - 1, dob.dayOfMonth,
                ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            },
        )
    }
}
