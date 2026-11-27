package com.noop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.GlassCard

/**
 * The What's New content: one hero image, then one card per major.minor release (RS-2c), newest first, four at most.
 * Compiled from the sprint notes under `docs/ridge-build` (`RELEASE_NOTES.md`, `RIDGE_V1_RELEASE_DETAILS.md`,
 * `Build 2/BUILD2_CONSOLIDATED_NOTES.md`, `BS-x2/`). English only, like the changelog entries it replaces.
 */
internal object RidgeReleaseNotes {
    const val HERO_TITLE = "Welcome to\nNoop Ridge"
    /** The same title on one line, for the changelog entry and the Updates inbox. */
    const val TITLE_ONE_LINE = "Welcome to Noop Ridge"
    /** The version string the changelog, the auto-shown sheet and the Updates inbox stamp. */
    const val VERSION = "12.2.3"
    const val DATE = "November 2026"
    const val ENTRY_SUBTITLE = "Ridge v1.0"
    /** This project's home, and the project Ridge is built on (credited in the About footer). */
    const val PROJECT_URL = "https://github.com/eccentricmeerkat/noopridge"
    const val UPSTREAM_URL = "https://github.com/ryanbr/noop"
    /** The version the About header shows. */
    const val DISPLAY_VERSION = "12.2"

    /**
     * RS-2c: one entry per release, newest first. The page groups them by major.minor (12.2, 12.1 with 12.1.2, 12.0) into one
     * card each, shows the newest [MAX_CARDS] and links to GitHub for the rest. Everything before 12.1 is the Ridge launch,
     * grouped as 12.0 (owner, 26 Nov).
     */
    val releases: List<RidgeRelease> = listOf(
        RidgeRelease(
            "12.2.3",
            listOf(
                "**Readiness, said plainly.** The card shows the word on its own. Open it for one sentence on what kind of day it is and why.",
                "**A new look for the Sleep score.** A night-sky card with a deep blue orb. The \"i\" beside its name explains the score.",
                "**How your numbers work, rewritten.** Each score now says what it measures, how it's captured and what it means. Several explanations were wrong and now match how the app calculates, including VO\u2082 max, body age, fitness age and sleep debt. No scores changed. The page is in English.",
                "**Illness heads-up fixed.** With imported WHOOP history, your skin temperature was counted as a warning sign even when it was normal. It now counts only when it rises. The alert cards on Today fit their text, and the gap under them is gone.",
                "**Smaller fixes.** Vital signs shows your latest HRV with its date when last night has none. The Vitality tile opens Insights. The Sleep score has its own tab in the guide, Trends' Effort and Charge charts have a y axis, and \"No data\" lines up in the Key Metrics editor.",
            ),
        ),
        RidgeRelease(
            "12.2.2",
            listOf(
                "**Readiness replaces Synthesis.** Each morning it blends Charge (80%) and Rest (20%), then comes down as you build Effort towards your target. It keeps the five words, from Depleted to Peak, and Trends has a Daily Readiness calendar. Charge is now a number with no word of its own.",
                "**A sleep score.** A new card at the top of Sleep shows how well the night went: hours against what you needed, steady bed and wake times, and time in bed spent asleep. It is in Insights and Key Metrics too. It sits beside Rest and changes no other score.",
                "**Alerts on Today.** A card says when any of your five vitals is outside your range, and another when the Illness heads-up fires. Body Monitor now counts Blood oxygen like Vital signs does.",
                "**Tidier pages.** Automations & Alarms is two groups that start closed. What's new no longer pops up after an update. The heart charts on Baseline are taller, and Key Metrics marks tiles with no data. The orbs no longer print the night under Charge.",
                "**Consistency moons.** A moon now marks every night with a Rest of 85 or more, not only nights that met your full sleep need.",
                "**Sleep efficiency fixed.** It no longer shows 1% for nights it should read in the 90s. Imports store it correctly, and figures an earlier import stored wrongly are repaired when the app starts.",
            ),
        ),
        RidgeRelease(
            "12.2.0",
            listOf(
                "Baseline has a Pace of aging card, and Trend View shows the six-month trends behind your body age: each measure against its six-month and 30-day averages, your pace over six months, and body age against calendar age. On Sleep, Hours vs Needed breaks tonight's need into base sleep, recent Effort and sleep debt, and a new Hours asleep chart shows your last 14 nights. Hub > About now holds Check for updates (Settings > About is gone), Vital signs draws a heartbeat while it is live, and your date of birth is set on Profile with a Birthday picker.",
            ),
        ),
        RidgeRelease(
            "12.1.2",
            listOf(
                "Backups import more widely. A backup from the original Noop 12 now restores here, and one from Noop Forge is merged into your data without replacing anything you already have. A backup made by a newer version of the app is now refused with a clear message instead of leaving the app unable to open its data. Restoring from Settings now restarts the app by itself, so Today shows your restored history straight away, and the setup wizard has an Import backup option on the Your history page.",
            ),
        ),
        RidgeRelease(
            "12.1.0",
            listOf(
                "Settings is simpler: Appearance keeps language, clock and units, and Backup & restore is redrawn with an icon on Export, Import and CSV. The recommended bedtime now repays part of your sleep debt, and says how much it added. Vital signs starts your live heart rate when you open it. A workout recorded in the app no longer counts extra heart-rate samples, which could overstate its calories.",
            ),
        ),
        RidgeRelease(
            "12.0.0",
            listOf(
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
            ),
            subtitle = ENTRY_SUBTITLE,
        ),
    )

    /** Every release's lines, newest first (the legacy changelog entry reads them flat). */
    val items: List<String> get() = releases.flatMap { it.items }

    /** The page shows at most this many release cards, then a link to the rest on GitHub. */
    const val MAX_CARDS = 4

    /** Where the older releases live. */
    const val RELEASES_URL = "$PROJECT_URL/releases"
}

/** One release: its full version ("12.1.2"), its lines, and an optional subtitle ("Ridge v1.0" for the launch). */
internal data class RidgeRelease(val version: String, val items: List<String>, val subtitle: String? = null)

/** A What's new card: every release sharing a major.minor ("12.1" holds 12.1.2 and 12.1.0), newest first. */
internal data class RidgeReleaseGroup(val minor: String, val releases: List<RidgeRelease>) {
    /** Patch headings ("12.1.2", "12.1") only when the card holds more than one release. */
    val showsPatchHeadings: Boolean get() = releases.size > 1
}

/** The major.minor of a version: "12.1.2" → "12.1", "12.0.0" → "12.0", "12" → "12". */
internal fun minorOf(version: String): String = version.split('.').take(2).joinToString(".")

/** "12.1.0" → "12.1", "12.1.2" → "12.1.2": the version as a heading, without a trailing ".0" patch. */
internal fun releaseHeading(version: String): String = version.split('.').let { p -> if (p.size == 3 && p[2] == "0") "${p[0]}.${p[1]}" else version }

/** Groups [releases] (newest first) by major.minor, keeping order; [limit] cards at most. */
internal fun groupReleases(releases: List<RidgeRelease>, limit: Int = RidgeReleaseNotes.MAX_CARDS): List<RidgeReleaseGroup> =
    releases.groupBy { minorOf(it.version) }.map { (minor, rs) -> RidgeReleaseGroup(minor, rs) }.take(limit)

/** True when there are more major.minor groups than the page shows, so the GitHub link says "older releases". */
internal fun hasOlderReleases(releases: List<RidgeRelease>, limit: Int = RidgeReleaseNotes.MAX_CARDS): Boolean =
    releases.map { minorOf(it.version) }.distinct().size > limit

/** The What's New body: the hero image with its title, the release cards, then the link to older releases on GitHub. */
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
        // RS-2c: one card per major.minor, newest first, at most MAX_CARDS; the rest are on GitHub.
        groupReleases(RidgeReleaseNotes.releases).forEach { group -> RidgeReleaseCard(group) }
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val older = stringResource(
            if (hasOlderReleases(RidgeReleaseNotes.releases)) R.string.about_ridge_older_releases else R.string.about_ridge_all_releases,
        )
        Text(
            older, style = RidgeType.bodySm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Ridge.brand,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ridge.radiusSm))
                .clickable { runCatching { uriHandler.openUri(RidgeReleaseNotes.RELEASES_URL) } }
                .padding(vertical = Ridge.space2),
        )
    }
}

/** One release card: "Noop v12.1", the launch's subtitle, then each release's lines, under its patch heading when it shares the card. */
@Composable
private fun RidgeReleaseCard(group: RidgeReleaseGroup) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Ridge.space5)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.about_ridge_release_title, group.minor), style = RidgeType.titleSm, color = Ridge.ink)
                group.releases.firstNotNullOfOrNull { it.subtitle }?.let { Text(it, style = RidgeType.bodySm, color = Ridge.inkMuted) }
            }
            group.releases.forEach { release ->
                if (group.showsPatchHeadings) {
                    Text(releaseHeading(release.version), style = RidgeType.eyebrow, color = Ridge.brand)
                }
                release.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(Ridge.brand))
                        Text(inlineBold(item), style = RidgeType.bodySm, color = Ridge.inkMuted, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
