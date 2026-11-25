package com.noop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard

/**
 * The What's New content for Noop 12.1 (Ridge 1.0): one hero image and one release entry, "Noop v12.0 / Ridge v1.0".
 * Compiled from the sprint notes under `docs/ridge-build` (`RELEASE_NOTES.md`, `RIDGE_V1_RELEASE_DETAILS.md`,
 * `Build 2/BUILD2_CONSOLIDATED_NOTES.md`, `BS-x2/`). English only, like the changelog entries it replaces.
 */
internal object RidgeReleaseNotes {
    const val HERO_TITLE = "Welcome to\nNoop Ridge"
    /** The same title on one line, for the changelog entry and the Updates inbox. */
    const val TITLE_ONE_LINE = "Welcome to Noop Ridge"
    /** The version string the changelog, the auto-shown sheet and the Updates inbox stamp. */
    const val VERSION = "12.1.2"
    const val DATE = "November 2026"
    const val ENTRY_TITLE = "Noop v12.1"
    const val ENTRY_SUBTITLE = "Ridge v1.0"
    /** This project's home, and the project Ridge is built on (credited in the About footer). */
    const val PROJECT_URL = "https://github.com/eccentricmeerkat/noopridge"
    const val UPSTREAM_URL = "https://github.com/ryanbr/noop"
    /** The version the About header shows. */
    const val DISPLAY_VERSION = "12.1"

    val items: List<String> = listOf(
        "**New in 12.1.2.** Backups import more widely. A backup from the original Noop 12 now restores here, and one from Noop Forge is merged into your data without replacing anything you already have. A backup made by a newer version of the app is now refused with a clear message instead of leaving the app unable to open its data. Restoring from Settings now restarts the app by itself, so Today shows your restored history straight away, and the setup wizard has an Import backup option on the Your history page.",
        "**New in 12.1.** Settings is simpler: Appearance keeps language, clock and units, and Backup & restore is redrawn with an icon on Export, Import and CSV. The recommended bedtime now repays part of your sleep debt, and says how much it added. Vital signs starts your live heart rate when you open it. A workout recorded in the app no longer counts extra heart-rate samples, which could overstate its calories.",
        "**A new shell with five tabs.** Today, Trends, Baseline, Sleep and Hub. The Hub holds Settings, Sage and Devices, then Insights, Body, Data and App. One dark appearance, mountain photography and glass cards throughout. Every earlier screen is still reachable.",
        "**Today.** Charge, Effort and Rest orbs, the day's call and Synthesis, and a plan that names the Effort to keep under and tonight's sleep. Key Metrics, a smoothed heart-rate line with sleep and workouts marked, and activities including nights detected on the phone. Tiles for Calories, Skin temperature and Steps now open Insights, and the page no longer flashes a new user's state while your numbers load.",
        "**Trends.** Seven, fourteen or thirty days of Charge, Effort, Calories, heart health against your normal band, sleep against your need, a Synthesis calendar, daily signals and twelve weeks of consistency. Charge against earlier Effort shows how yesterday's load shapes today.",
        "**Baseline.** Body age, VO₂ max with a typical-for-your-age band, fitness age, the long-run heart and heart-rate recovery, and the drivers behind them. With no date of birth set they ask for one instead of assuming an age.",
        "**Sleep.** One stage palette, sleep debt first, hours against your need, tonight's smart-wake window, the whole night's stages in one view, the stage breakdown, and bedtime and wake. Recommended bedtime comes from your sleep need and the alarm, and says \"Needs 4 nights of sleep\" until it has them.",
        "**Sage, your coach.** Off until you add your own provider key. \"Test key\" sends one request for the model list with no question and no data, and \"Preview what Sage sends\" shows the exact text a question would carry. The key is encrypted on the phone, kept out of backups and deleted on Disconnect.",
        "**Numbers from the right source.** Your strap's calorie estimate wins over Health Connect's flat figures, and calories are split into total, active (saved workouts) and active energy (Health Connect). Blood oxygen takes the newest reading everywhere, tagged Estimate when it comes from the strap. Average and Max heart rate in Insights now fill in.",
        "**Health Connect that explains itself.** The card says Permission needed, Connected, No records found, or why a read was refused. Ridge asks once for history access so older records such as weight come through. Ridge has its own app id, so grant Health Connect access again.",
        "**The metricsV2 build.** v10's formulas are unchanged and frozen by tests, and a switchable set of corrections sits on top: one sleep need across the app, a normal band from your last 60 nights, \"slept on target\" meaning asleep for your need, no assumed age, a day that turns correctly on daylight-saving mornings, Explore windows counted in calendar days, and a past day's load that no longer leaks later days.",
        "**One repository for every metric.** Each metric is read once, from one source and in one unit, so a score is the same number on Today, Trends, Insights and the scoring guide. Strap, Health Connect and imports follow a fixed order of precedence, and a debug inspector shows where any figure came from.",
        "**Expanded Key Metrics and Insights.** The Key Metrics editor now offers around thirty more tiles, grouped by category, from Stress, Sleep debt and VO\u2082 max to body fat and training load, with up to nine shown on Today at once. Insights charts every metric in the same catalogue through Explore, tags the ones with no data, and fills in Average and Max heart rate and Steps.",
    )
}

/** The What's New body: the hero image with its title, then the single release card. */
@Composable
internal fun RidgeReleaseNotesBody() {
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space4)) {
        Box(
            Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(Ridge.radiusLg)),
        ) {
            Image(
                painterResource(R.drawable.ridge_whats_new), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, 0.25f), modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier.fillMaxWidth().padding(top = Ridge.space5, start = Ridge.space5, end = Ridge.space5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Ridge.space3),
            ) {
                Image(
                    painterResource(R.drawable.ridge_logo), contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.width(220.dp),
                )
                Text(
                    RidgeReleaseNotes.HERO_TITLE, style = RidgeType.titleLg.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = Color.White, textAlign = TextAlign.Center,
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Ridge.space5)) {
            Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(RidgeReleaseNotes.ENTRY_TITLE, style = RidgeType.titleSm, color = Ridge.ink)
                    Text(RidgeReleaseNotes.ENTRY_SUBTITLE, style = RidgeType.bodySm, color = Ridge.inkMuted)
                }
                RidgeReleaseNotes.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(Ridge.brand))
                        Text(inlineBold(item), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
