package com.noop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.noop.R

// MARK: - Advanced disclosure (S3, ports SettingsView's SettingsDisclosureGroup)

/**
 * A collapsible group that tucks the lower-frequency settings sections behind one tap. It is NOT a
 * section card itself (the cards it wraps keep their own [SettingsSection] chrome). It's a header row
 * plus a default-collapsed reveal, modelled on the Test Centre "Advanced" group. Nothing is removed:
 * collapsed simply means the wrapped sections aren't composed until the row is tapped open. A custom
 * header (not Material's ExposedDropdown / accordion) keeps it on NOOP's near-black instrument look.
 */
@Composable
internal fun SettingsDisclosureGroup(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = uiString(R.string.l10n_settings_screen_advancedchevron_f22dfa01),
    )
    val disclosureStateDescription = uiString(
        if (expanded) R.string.settings_disclosure_expanded else R.string.settings_disclosure_collapsed,
    )
    val headerInteraction = remember { MutableInteractionSource() }
    if (ridgeShell) {
        // B2 Hub5a: Advanced is one of the board's accordion cards (the content below is unchanged).
        com.noop.ui.ridge.RidgeAccordionCard(
            eyebrow = stringResource(R.string.set_ridge_e_advanced), title = title,
            icon = Icons.Filled.Tune,
            summary = stringResource(R.string.set_ridge_s_advanced),
        ) { content() }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.screenRowSpacing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPress(headerInteraction)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                    onClick = onToggle,
                )
                .semantics {
                    contentDescription = title
                    stateDescription = disclosureStateDescription
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NoopType.title2, color = Palette.textPrimary)
                Text(subtitle, style = NoopType.subhead, color = Palette.textSecondary)
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Palette.textTertiary,
                modifier = Modifier.size(22.dp).rotate(chevronRotation),
            )
        }
        if (expanded) {
            content()
        }
    }
}

// MARK: - Section card (ports SettingsView's private SettingsSection)

/** The small label over a settings card's title (SQ9 S2), grouping the cards the way the Hub board does. */
internal enum class SettingsGroup(@androidx.annotation.StringRes val labelRes: Int) {
    YOU(com.noop.R.string.settings_group_you),
    DISPLAY(com.noop.R.string.settings_group_display),
    DEVICE(com.noop.R.string.settings_group_device),
    DATA(com.noop.R.string.settings_group_data),
    HEALTH(com.noop.R.string.settings_group_health),
    APP(com.noop.R.string.settings_group_app),
}

/**
 * B2 Hub5a: a section folded into another card's body (Units inside Appearance, Diagnostics inside Advanced). Under
 * Ridge it is a hairline, a small heading, the blurb and the controls; otherwise it is the full [SettingsCard] it was.
 */
@Composable
internal fun SettingsSubSection(
    icon: ImageVector,
    title: String,
    blurb: String,
    eyebrow: SettingsGroup = SettingsGroup.APP,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!ridgeShell) {
        SettingsCard(icon = icon, title = title, blurb = blurb, eyebrow = eyebrow, initiallyExpanded = initiallyExpanded, content = content)
        return
    }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(Ridge.glassStroke))
        androidx.compose.material3.Text(
            title.uppercase(), style = RidgeType.eyebrow, color = Ridge.inkSubtle,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (blurb.isNotEmpty()) androidx.compose.material3.Text(blurb, style = RidgeType.bodySm, color = Ridge.inkMuted)
        content()
    }
}

/**
 * A grouped settings card: a "Settings" overline + icon + title header, an explanatory blurb, then
 * content. A faint brand-green wash anchors the card to NOOP's neutral chrome (mirrors macOS).
 */
@Composable
internal fun SettingsCard(
    icon: ImageVector,
    title: String,
    blurb: String,
    eyebrow: SettingsGroup = SettingsGroup.APP,
    initiallyExpanded: Boolean = false,
    ridgeEyebrow: String? = null,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    if (ridgeShell) {
        // SQ9 S2: under the Ridge shell each settings card is a Ridge accordion (closed until tapped); the blurb and the
        // controls are its body. Everything v10 offered is still here, one tap down.
        com.noop.ui.ridge.RidgeAccordionCard(
            eyebrow = ridgeEyebrow ?: stringResource(eyebrow.labelRes), title = title, icon = icon, initiallyExpanded = initiallyExpanded,
            summary = summary,
        ) {
            if (blurb.isNotEmpty()) Text(blurb, style = RidgeType.bodySm, color = Ridge.inkMuted)
            content()
        }
        return
    }
    NoopCard(padding = 20.dp, tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline("Settings")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                    Text(title, style = NoopType.title2, color = Palette.textPrimary)
                }
            }
            // An empty blurb draws nothing (Ridge's Appearance card, whose v10 blurb offers a Light theme).
            if (blurb.isNotEmpty()) Text(blurb, style = NoopType.subhead, color = Palette.textSecondary)
            content()
        }
    }
}

// MARK: - Labelled toggle row (title + detail + trailing Switch)

/**
 * A title + explanatory detail on the left with a trailing [Switch], matching the in-section toggle idiom
 * the Strap/Health Connect sections already use. Used by the v5 Health & wellness group so every opt-in
 * reads consistently. The switch colours mirror the rest of Settings (gold track when on).
 */
@Composable
internal fun SettingsToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = NoopType.subhead, color = Palette.textPrimary)
            Text(detail, style = NoopType.footnote, color = Palette.textTertiary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.surfaceBase,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textSecondary,
                uncheckedTrackColor = Palette.surfaceInset,
                uncheckedBorderColor = Palette.hairline,
            ),
        )
    }
}

// MARK: - Two-column form row (ports SettingsView's private FormRow)

/**
 * Label on the left, control on the right — the two-column form feel.
 *
 * KEEP THE CONTROL NARROW. Only the label is weighted, so Compose measures [control] FIRST against the
 * full row width and the label gets whatever is left. A control that wants the whole width (e.g. a
 * full-sentence helper text stacked under a stepper) starves the label to ~0 width, where it wraps one
 * character per line — rendering blank while inflating the row into a screen of dead space. Put
 * sentence-length copy in a sibling `Text` BELOW the row instead (see the Step-calibration and Waist
 * rows in SettingsScreen); a SHORT right-aligned sub-value under the control (e.g. "Auto · 173 bpm") is
 * fine.
 */
@Composable
internal fun SettingsFormRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            style = NoopType.body,
            color = Palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

// MARK: - Stacked action button (Settings.dc.html `isBtn`)

private val ICON_SIZE = 20.dp   // the label's line height
private val ICON_GAP = 8.dp

/**
 * The Ridge Settings board's action row (B2 Settings refinement): a full-width outlined button with the action on the
 * first line and what it does underneath, left aligned, at least 64 dp tall. Used by Backup & restore, where three of
 * them stack under a note. Ridge only: callers keep their older layout for the v10 shell.
 */
@Composable
internal fun SettingsActionButton(
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** An icon drawn left of the label at the label's line height (20 dp), or null for none. */
    icon: ImageVector? = null,
    iconTint: Color = Ridge.ink,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Ridge.radiusMd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.5.dp, Ridge.inkMuted, shape)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ICON_GAP)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(ICON_SIZE))
            Text(label, style = RidgeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp), color = Ridge.ink)
        }
        // The description lines up with the label, not the icon, so the text reads as one block.
        if (sub.isNotEmpty()) Text(sub, style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.padding(start = if (icon != null) ICON_SIZE + ICON_GAP else 0.dp))
    }
}

// MARK: - Shared bits

@Composable
internal fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Palette.hairline),
    )
}

@Composable
internal fun SettingsNoteRow(icon: ImageVector, iconTint: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(text, style = NoopType.footnote, color = Palette.textSecondary)
    }
}

@Composable
internal fun SettingsAttributionRow(repo: String, note: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            contentDescription = uiString(R.string.l10n_settings_screen_repo_note_0a694b50, repo, note)
        },
    ) {
        Text("›", style = NoopType.headline, color = Palette.accent)
        Text(repo, style = NoopType.mono(12f), color = Palette.textPrimary)
        Text(
            uiString(R.string.l10n_settings_screen_note_a2481d6c, note),
            style = NoopType.footnote,
            color = Palette.textTertiary,
        )
    }
}
