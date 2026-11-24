package com.noop.ui.ridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noop.ui.Ridge
import com.noop.ui.RidgeType
import androidx.compose.material3.Text

/** The B-02 gallery sections; each renders one component family in its states. Shared by the `@Preview`s below
 *  and the Roborazzi snapshot tests, so the reviewed picture and the tested picture are the same composable. */
internal enum class GallerySection { Chips, Buttons, Headers, Rows, Controls, Cards, TabBar, Icons, HubParts }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RidgeGallerySection(section: GallerySection, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Ridge.bg100).padding(Ridge.space4).width(390.dp),
        verticalArrangement = Arrangement.spacedBy(Ridge.space4),
    ) {
        when (section) {
            GallerySection.Chips -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Ridge.space2), verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                RidgeChip("Maintain")
                RidgeChip("Solid", kind = RidgeChipKind.Solid)
                RidgeChip("WHOOP syncing", kind = RidgeChipKind.Status)
                RidgeChip("Beta", kind = RidgeChipKind.Alert)
                RidgeChip("vs typical", kind = RidgeChipKind.Dropdown, onClick = {})
            }
            GallerySection.Buttons -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Ridge.space2), verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                    RidgeButton("Start session", {}, icon = RidgeIcons.run)
                    RidgeButton("Log recovery", {}, kind = RidgeButtonKind.Charge)
                    RidgeButton("Edit metrics", {}, kind = RidgeButtonKind.Secondary)
                    RidgeButton("History", {}, kind = RidgeButtonKind.Text, icon = RidgeIcons.journal)
                    RidgeButton("Disabled", {}, enabled = false)
                }
                RidgeButton("Get started", {}, wizard = true)
            }
            GallerySection.Headers -> {
                RidgeSectionHeader("Key Metrics", eyebrow = "Today · Sun, 20 Sep") { RidgeHeaderNote("14-day trend") }
                RidgeSectionHeader("Latest Workouts")
            }
            GallerySection.Rows -> GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Ridge.space4)) {
                Column {
                    RidgeListRow("10K Run", icon = RidgeIcons.run, subtitle = "Sat, 16 Sep · 59:12 · 10.0 km", onClick = {})
                    RidgeRowDivider()
                    RidgeListRow("What Moves You", icon = RidgeIcons.pulse, iconTint = Ridge.brand, onClick = {})
                    RidgeRowDivider()
                    RidgeListRow("Devices", icon = RidgeIcons.hub, trailing = "2 paired", onClick = {})
                }
            }
            GallerySection.Controls -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Ridge.space4), verticalAlignment = Alignment.CenterVertically) {
                    RidgeToggle(checked = true, onCheckedChange = {})
                    RidgeToggle(checked = false, onCheckedChange = {})
                    RidgeToggle(checked = true, onCheckedChange = {}, enabled = false)
                }
                RidgeSegmentedPills(listOf(7, 14, 30), 14, {}, { "$it days" })
                RidgeStepper(value = 30, onValueChange = {}, range = 5..300, step = 5, label = "Work", unit = "s")
            }
            GallerySection.Cards -> {
                GlassCard(Modifier.fillMaxWidth()) { Text("Neutral · default glass over bg-100.", style = RidgeType.body, color = Ridge.ink) }
                GlassCard(Modifier.fillMaxWidth(), tone = GlassTone.Rest) { Text("Rest tone · a card about sleep.", style = RidgeType.body, color = Ridge.ink) }
                GlassCard(Modifier.fillMaxWidth(), tone = GlassTone.Alert) {
                    Column {
                        RidgeEyebrow("Alert tone")
                        Text("1h 19m", style = RidgeType.numeralMd, color = Ridge.stateDepleted)
                        Text("Sleep debt, below need", style = RidgeType.body, color = Ridge.ink)
                    }
                }
                GlassCard(Modifier.fillMaxWidth(), tone = GlassTone.Strong) { Text("Strong · stands forward.", style = RidgeType.body, color = Ridge.ink) }
            }
            GallerySection.TabBar -> {
                val tabs = galleryTabs
                RidgeTabBar(tabs, selectedKey = "today", onSelect = {})
                RidgeTabBar(tabs, selectedKey = "baseline", onSelect = {})
            }
            GallerySection.HubParts -> {
                // SQ9 S1: the Hub rebuild's shared parts.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RidgeBackButton("Back", {})
                    RidgeHeaderAction("Backup", {}, icon = RidgeIcons.journal)
                }
                RidgeAccordionCard("Alerts", "Wrist alerts", RidgeIcons.pulse, initiallyExpanded = true) {
                    Text("Buzz your strap when these apps notify you.", style = RidgeType.body, color = Ridge.inkMuted)
                }
                RidgeAccordionCard("Alerts", "Calls", RidgeIcons.run) {}
                GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Ridge.space4)) {
                    Column {
                        RidgeSettingRow("Only buzz when worn", subtitle = "Skip alerts while the strap is off") {
                            RidgeToggle(checked = true, onCheckedChange = {})
                        }
                        RidgeRowDivider()
                        RidgeSettingRow("Kick in at strap battery", subtitle = "Power saving starts here") {
                            RidgeStepper(value = 20, onValueChange = {}, range = 5..50, step = 5, label = "Battery", unit = "%")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { RidgeHeroOrb("0.8", "Settled", diameter = 150.dp) }
            }
            GallerySection.Icons -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Ridge.space4), verticalArrangement = Arrangement.spacedBy(Ridge.space4)) {
                listOf(
                    RidgeIcons.sun, RidgeIcons.bars, RidgeIcons.peak, RidgeIcons.moon, RidgeIcons.hub,
                    RidgeIcons.chevronRight, RidgeIcons.back, RidgeIcons.chevronDown, RidgeIcons.close,
                    RidgeIcons.check, RidgeIcons.plus, RidgeIcons.minus, RidgeIcons.sliders, RidgeIcons.pulse,
                    RidgeIcons.sparkle, RidgeIcons.checkCircle, RidgeIcons.journal, RidgeIcons.run,
                    // The Hub menu rows (`More.dc.html`, `BodyMore.dc.html`).
                    RidgeIcons.trendUp, RidgeIcons.dumbbell, RidgeIcons.wind, RidgeIcons.wave, RidgeIcons.ellipsis,
                    RidgeIcons.strap, RidgeIcons.database, RidgeIcons.bolt, RidgeIcons.bell, RidgeIcons.chartAxis,
                    RidgeIcons.info, RidgeIcons.flask, RidgeIcons.droplet,
                ).forEach { Icon(it, contentDescription = null, tint = Ridge.ink) }
            }
        }
    }
}

internal val galleryTabs = listOf(
    RidgeTab("today", "Today", RidgeIcons.sun),
    RidgeTab("trends", "Trends", RidgeIcons.bars),
    RidgeTab("baseline", "Baseline", RidgeIcons.peak),
    RidgeTab("sleep", "Sleep", RidgeIcons.moon),
    RidgeTab("hub", "Hub", RidgeIcons.hub),
)

@Preview(name = "Ridge gallery", widthDp = 390, heightDp = 2200)
@Preview(name = "Ridge gallery · large font", widthDp = 390, heightDp = 2600, fontScale = 1.3f)
@Composable
private fun RidgeGalleryPreview() {
    Column { GallerySection.entries.forEach { RidgeGallerySection(it) } }
}
