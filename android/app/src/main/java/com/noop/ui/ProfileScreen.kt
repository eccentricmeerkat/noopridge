package com.noop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeSegmentedPills
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * B2 Hub1c (`Profile.dc.html`): the Profile page, reached from the Hub's Profile tile. One glass card of rows divided
 * by hairlines, as the board draws it: a value with its unit and two round step buttons, a segmented Sex row, a
 * switch for Custom HR zones and a link row for the steps estimate, each with its small note beneath. One row is
 * the owner's addition to the board: the first name Today greets you by. Every control writes the
 * same [ProfileStore] fields the Settings Profile card does, so the two never disagree.
 */
@Composable
fun ProfileScreen(vm: AppViewModel, onOpenStepsCalibration: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStore.from(context) }
    var rev by remember { mutableStateOf(0) }
    fun mutate(block: () -> Unit) { block(); rev++ }
    val unitSystem = remember { UnitPrefs.system(context) }

    LazyScreenScaffold(title = stringResource(R.string.hub_tile_profile), subtitle = null, rowSpacing = 14.dp) {
        @Suppress("UNUSED_VARIABLE") val tick = rev
        item { Text(stringResource(R.string.profile_ridge_intro), style = PF_BODY, color = Ridge.inkMuted) }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    // --- the owner's addition to the board: the first name Today greets you by ---
                    PfRow(first = true) {
                        var nameDraft by remember { mutableStateOf(profile.firstName) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.profile_ridge_name), style = PF_LABEL, color = Ridge.ink)
                            BasicTextField(
                                value = nameDraft,
                                onValueChange = { v -> nameDraft = v.take(40); mutate { profile.firstName = nameDraft } },
                                singleLine = true,
                                textStyle = PF_VALUE.copy(color = Ridge.ink, textAlign = TextAlign.End),
                                cursorBrush = SolidColor(Ridge.brand),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterEnd) {
                                        if (nameDraft.isEmpty()) Text(stringResource(R.string.settings_name_hint), style = PF_VALUE.copy(fontWeight = FontWeight.Medium), color = Ridge.inkMuted)
                                        inner()
                                    }
                                },
                            )
                        }
                        PfSub(stringResource(R.string.settings_name_blurb))
                    }
                    // RS-1-3 item 9 (owner, 25 Nov): Age becomes Date of Birth ("June 10th, 1990"); a tap opens the Birthday
                    // wheels. Stored through setDateOfBirth, as onboarding does, so the age and everything from it follow.
                    PfRow {
                        var showBirthday by remember { mutableStateOf(false) }
                        val zone = java.time.ZoneId.systemDefault()
                        val dob = java.time.Instant.ofEpochMilli(profile.dateOfBirthMillis).atZone(zone).toLocalDate()
                        val known = profile.knownAge != null
                        val label = stringResource(R.string.profile_dob_label)
                        val value = if (known) birthdayText(dob) else stringResource(R.string.profile_ridge_add)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { showBirthday = true }
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(label, style = PF_LABEL, color = Ridge.ink, modifier = Modifier.weight(1f))
                            // Owner, 25 Nov: the date a size down from the stepper values (it is long), 2 dp from the chevron.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(value, style = PF_VALUE.copy(fontSize = 16.sp, lineHeight = 20.sp), color = if (known) Ridge.ink else Ridge.inkMuted, maxLines = 1)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkSubtle)
                            }
                        }
                        if (showBirthday) {
                            BirthdayDialog(
                                initial = dob, minAge = ProfileStore.AGE_MIN, maxAge = ProfileStore.AGE_MAX,
                                onDismiss = { showBirthday = false },
                                onDone = { picked -> showBirthday = false; mutate { profile.setDateOfBirth(picked) } },
                            )
                        }
                    }
                    PfRow {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.l10n_settings_screen_sex_e301dd60), style = PF_LABEL, color = Ridge.ink)
                            Spacer(Modifier.weight(1f))
                            val sexLabels = SEX_OPTIONS.associateWith { it.label }
                            RidgeSegmentedPills(
                                items = SEX_OPTIONS,
                                selected = SEX_OPTIONS.firstOrNull { it.tag == profile.sex } ?: SEX_OPTIONS[0],
                                onSelect = { o -> mutate { profile.sex = o.tag } },
                                label = { sexLabels.getValue(it) },
                                // Owner, 25 Nov: the pills keep their own width at the right, with space after "Sex".
                                fill = false,
                                itemPadding = 12.dp,
                                // Owner, 25 Nov: the same 36 dp height (and so the same end radius) as the +/- buttons.
                                height = 36.dp,
                            )
                        }
                    }
                    PfRow {
                        // B2-X: the latest imported weight, to compare with what was entered.
                        val importedKg by produceState<Double?>(null, vm) { value = latestImportedWeightKg(vm) }
                        if (unitSystem == UnitSystem.IMPERIAL) {
                            val lb = UnitFormatter.kgToPounds(profile.weightKg)
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_weight_69c0b815), value = "%.0f".format(lb), unit = "lb",
                                decDesc = "Decrease weight", incDesc = "Increase weight",
                                onMinus = { mutate { profile.weightKg = (lb - 1) / UnitFormatter.POUNDS_PER_KILOGRAM } },
                                onPlus = { mutate { profile.weightKg = (lb + 1) / UnitFormatter.POUNDS_PER_KILOGRAM } },
                            )
                        } else {
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_weight_69c0b815), value = "%.1f".format(profile.weightKg), unit = "kg",
                                decDesc = "Decrease weight", incDesc = "Increase weight",
                                onMinus = { mutate { profile.weightKg -= 0.5 } },
                                onPlus = { mutate { profile.weightKg += 0.5 } },
                            )
                        }
                        if (WeightCheck.differs(profile.weightKg, importedKg)) {
                            // B2-X: red when the entered weight is more than 2 kg from the imported one. A prompt, not a block.
                            PfSub(
                                stringResource(
                                    R.string.wc_ridge_line,
                                    UnitFormatter.massFromKilograms(profile.weightKg, unitSystem),
                                    UnitFormatter.massFromKilograms(importedKg ?: 0.0, unitSystem),
                                ),
                                color = Ridge.stateDepleted,
                            )
                        }
                    }
                    PfRow {
                        if (unitSystem == UnitSystem.IMPERIAL) {
                            val (ft, inch) = UnitFormatter.cmToFeetInches(profile.heightCm)
                            val totalInches = UnitFormatter.cmToInches(profile.heightCm).roundToInt()
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_height_3f608b49), value = "$ft′ $inch″", unit = "",
                                decDesc = "Decrease height", incDesc = "Increase height",
                                onMinus = { mutate { profile.heightCm = (totalInches - 1) * UnitFormatter.CENTIMETERS_PER_INCH } },
                                onPlus = { mutate { profile.heightCm = (totalInches + 1) * UnitFormatter.CENTIMETERS_PER_INCH } },
                            )
                        } else {
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_height_3f608b49), value = "%.0f".format(profile.heightCm), unit = "cm",
                                decDesc = "Decrease height", incDesc = "Increase height",
                                onMinus = { mutate { profile.heightCm -= 1 } },
                                onPlus = { mutate { profile.heightCm += 1 } },
                            )
                        }
                    }
                    PfRow {
                        val hasWaist = profile.waistCm > 0.0
                        val add = stringResource(R.string.profile_ridge_add)
                        if (unitSystem == UnitSystem.IMPERIAL) {
                            val totalInches = UnitFormatter.cmToInches(profile.waistCm).roundToInt()
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_waist_optional_d5356703),
                                value = if (hasWaist) "%d″".format(totalInches) else add, unit = "",
                                valueColor = if (hasWaist) Ridge.ink else Ridge.inkMuted, valueWeight = if (hasWaist) FontWeight.SemiBold else FontWeight.Medium,
                                decDesc = "Decrease waist", incDesc = "Increase waist",
                                onMinus = { mutate { profile.waistCm = waistInchesStep(profile.waistCm, up = false) } },
                                onPlus = { mutate { profile.waistCm = waistInchesStep(profile.waistCm, up = true) } },
                            )
                        } else {
                            PfStep(
                                label = stringResource(R.string.l10n_settings_screen_waist_optional_d5356703),
                                value = if (hasWaist) "%.0f".format(profile.waistCm) else add, unit = if (hasWaist) "cm" else "",
                                valueColor = if (hasWaist) Ridge.ink else Ridge.inkMuted, valueWeight = if (hasWaist) FontWeight.SemiBold else FontWeight.Medium,
                                decDesc = "Decrease waist", incDesc = "Increase waist",
                                onMinus = { mutate { profile.waistCm = waistCmStep(profile.waistCm, up = false) } },
                                onPlus = { mutate { profile.waistCm = waistCmStep(profile.waistCm, up = true) } },
                            )
                        }
                        PfSub(stringResource(if (hasWaist) R.string.settings_waist_footnote_set else R.string.settings_waist_footnote_unset))
                    }
                    PfRow {
                        val manual = profile.hrMaxOverride > 0
                        PfStep(
                            label = stringResource(R.string.l10n_settings_screen_max_heart_rate_3d4ed858),
                            value = if (manual) profile.hrMaxOverride.toString() else stringResource(R.string.profile_ridge_auto), unit = "bpm",
                            valueColor = if (manual) Ridge.ink else Ridge.brand,
                            decDesc = "Decrease max heart rate", incDesc = "Increase max heart rate",
                            onMinus = { mutate { profile.hrMaxOverride -= 1 } },
                            onPlus = { mutate { profile.hrMaxOverride += 1 } },
                        )
                        PfSub(
                            if (manual) stringResource(R.string.profile_ridge_manual_hint) else stringResource(R.string.profile_ridge_auto_max, profile.hrMaxAuto),
                            end = true,
                        )
                    }
                    PfRow {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.l10n_settings_screen_custom_hr_zones_84736ca5), style = PF_LABEL, color = Ridge.ink, modifier = Modifier.weight(1f))
                            PfSwitch(profile.hasCustomHrZones, stringResource(R.string.l10n_settings_screen_custom_hr_zones_84736ca5)) {
                                mutate { profile.setCustomHrZonesEnabled(it) }
                            }
                        }
                        if (profile.hasCustomHrZones) {
                            PfSub(stringResource(R.string.l10n_settings_screen_custom_hr_zones_hint_f7fa3bae))
                            profile.hrZoneThresholds?.forEachIndexed { index, value ->
                                PfStep(
                                    label = stringResource(R.string.l10n_settings_screen_zone_starts_a0a60d45, index + 1), value = value.toString(), unit = "bpm",
                                    decDesc = "Decrease zone ${index + 1}", incDesc = "Increase zone ${index + 1}",
                                    onMinus = { mutate { profile.stepHrZoneThreshold(index, up = false) } },
                                    onPlus = { mutate { profile.stepHrZoneThreshold(index, up = true) } },
                                )
                            }
                        }
                    }
                    PfRow {
                        PfStep(
                            label = stringResource(R.string.l10n_settings_screen_step_calibration_351c09bf),
                            value = "%.1f".format(profile.stepTicksPerStep), unit = "",
                            decDesc = "Decrease step calibration", incDesc = "Increase step calibration",
                            onMinus = { mutate { profile.stepTicksPerStep = ProfileStore.steppedStepScale(profile.stepTicksPerStep, up = false) } },
                            onPlus = { mutate { profile.stepTicksPerStep = ProfileStore.steppedStepScale(profile.stepTicksPerStep, up = true) } },
                        )
                        PfSub(stringResource(R.string.l10n_settings_screen_counter_ticks_per_step_leave_at_3ce8c1d5))
                    }
                    PfRow {
                        val stepsSummary = when {
                            profile.stepsManualCoefficient > 0 -> stringResource(R.string.profile_ridge_manual)
                            profile.stepsCalibrationCoefficient > 0 ->
                                stringResource(R.string.profile_ridge_auto_conf, StepsCalibrationFormat.confidenceLabel(profile.stepsCalibrationConfidence))
                            else -> stringResource(R.string.profile_ridge_not_calibrated)
                        }
                        val stepsCd = stringResource(R.string.profile_ridge_steps_cd, stepsSummary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onOpenStepsCalibration() }
                                .semantics { contentDescription = stepsCd },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.l10n_settings_screen_steps_estimate_ce7a604d), style = PF_LABEL, color = Ridge.ink, modifier = Modifier.weight(1f))
                            Text(stepsSummary, style = PF_SMALL, color = Ridge.inkMuted)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkMuted, modifier = Modifier.size(18.dp))
                        }
                        PfSub(stringResource(R.string.l10n_settings_screen_for_a_whoop_4_0_which_df865854))
                    }
                }
            }
        }
    }
}

private val PF_BODY = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
private val PF_LABEL = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
private val PF_VALUE = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
private val PF_SMALL = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
private val PF_NOTE = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)

/** One row of the card: a hairline above every row but the first, and the board's 6 dp (10 dp on the first) of air. */
@Composable
private fun PfRow(first: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
        Column(Modifier.fillMaxWidth().padding(top = if (first) 10.dp else 6.dp, bottom = 6.dp)) { content() }
    }
}

/** The small note under a row (the board's 12/17 muted line), optionally right-aligned. */
@Composable
private fun PfSub(text: String, end: Boolean = false, color: Color = Ridge.inkSubtle) {
    Text(
        text, style = PF_NOTE, color = color,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

/** label, value + unit (min 76 dp, end aligned) and two round step buttons: the board's `isStep` row. */
@Composable
private fun PfStep(
    label: String, value: String, unit: String,
    decDesc: String, incDesc: String,
    onMinus: () -> Unit, onPlus: () -> Unit,
    valueColor: Color = Ridge.ink, valueWeight: FontWeight = FontWeight.SemiBold,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = PF_LABEL, color = Ridge.ink, modifier = Modifier.weight(1f))
        Row(Modifier.widthIn(min = 76.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
            Text(value, style = PF_VALUE.copy(fontWeight = valueWeight), color = valueColor)
            if (unit.isNotEmpty()) Text(unit, style = PF_SMALL, color = Ridge.inkMuted, modifier = Modifier.padding(start = 6.dp, bottom = 1.dp))
        }
        PfStepButton("−", decDesc, onMinus)
        PfStepButton("+", incDesc, onPlus)
    }
}

@Composable
private fun PfStepButton(glyph: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Ridge.glassFill)
            .border(BorderStroke(1.dp, Color(190, 220, 235).copy(alpha = 0.22f)), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = PF_VALUE, color = Ridge.ink) }
}

/** The board's 52 x 32 switch: brand track with a dark knob when on, a pale track with a cream knob when off. */
@Composable
private fun PfSwitch(on: Boolean, desc: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Ridge.brand else Color(190, 220, 235).copy(alpha = 0.22f))
            .clickable { onChange(!on) }
            .semantics { contentDescription = desc },
    ) {
        Box(
            Modifier
                .padding(start = if (on) 24.dp else 4.dp, top = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) Ridge.bg000 else Ridge.ink),
        )
    }
}
