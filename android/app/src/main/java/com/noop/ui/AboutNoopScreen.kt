package com.noop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.BuildConfig
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeRowDivider
import com.noop.ui.ridge.RidgeSegmentedPills

private enum class AboutTab { HOW, NEW, LIMITS }

/**
 * B2 Hub5b (`About.dc.html`): one About NOOP page with the board's three tabs. How it works, What's new and Limits are the
 * app's existing content (its own copy and release notes), hosted under the tabs; the page adds the version line, the
 * "never makes up a number" line, a door to Test Centre ("Lab tools") and the open-source footer.
 */
@Composable
fun AboutNoopScreen(onOpenTestCentre: () -> Unit, onOpenScoringGuide: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(AboutTab.HOW) }
    val labels = mapOf(
        AboutTab.HOW to stringResource(R.string.about_ridge_tab_how),
        AboutTab.NEW to stringResource(R.string.about_ridge_tab_new),
        AboutTab.LIMITS to stringResource(R.string.about_ridge_tab_limits),
    )
    val under: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            Text(stringResource(R.string.set_ridge_s_about, RidgeReleaseNotes.DISPLAY_VERSION), style = RidgeType.bodySm, color = Ridge.inkMuted)
            Text(stringResource(R.string.about_ridge_tagline), style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp), color = Ridge.inkMuted)
            Text(stringResource(R.string.about_ridge_never), style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp), color = Ridge.inkSubtle)
            RidgeSegmentedPills(
                items = AboutTab.entries.toList(), selected = tab, onSelect = { tab = it },
                label = { labels.getValue(it) }, fill = true, itemPadding = 6.dp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
    CompositionLocalProvider(
        LocalRidgeTitleOverride provides stringResource(R.string.hub_about_title),
        LocalRidgeUnderTitle provides under,
    ) {
        ScreenScaffold(title = stringResource(R.string.hub_about_title), subtitle = null) {
            when (tab) {
                AboutTab.HOW -> HowNoopWorksBody()
                AboutTab.NEW -> {
                    // RS-1-1 item 1: the Updates card heads the What's new tab, above the release notes (board).
                    AboutUpdatesCard()
                    WhatsNewBody()
                }
                AboutTab.LIMITS -> NoopLimitationsBody()
            }
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                Column {
                    // RS-1-1 item 3: the board's "How scores work" row, on the How tab only, opens the scoring guide.
                    if (tab == AboutTab.HOW) {
                        AboutLinkRow(
                            stringResource(R.string.about_ridge_scores),
                            stringResource(R.string.about_ridge_scores_sub),
                            onOpenScoringGuide,
                        )
                        RidgeRowDivider()
                    }
                    AboutLinkRow(stringResource(R.string.about_ridge_lab), stringResource(R.string.about_ridge_lab_sub), onOpenTestCentre)
                }
            }
            Text(stringResource(R.string.about_ridge_footer), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle)
            val uriHandler = LocalUriHandler.current
            Column(Modifier.clickable { runCatching { uriHandler.openUri(RidgeReleaseNotes.UPSTREAM_URL) } }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.about_ridge_built_on), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = Ridge.inkSubtle)
                Text(
                    stringResource(R.string.about_ridge_built_on_link),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, textDecoration = TextDecoration.Underline), color = Ridge.brand,
                )
            }
            // RS-1-1 (D3, item 2): the project-home link, the protocol credits and the full disclaimer, moved here from
            // Settings > About.
            AboutProjectLink()
            AboutCredits()
        }
    }
}

/** One row of the About page's link card: a title, a muted line and a chevron, 60 dp tall (board). */
@Composable
private fun AboutLinkRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyle(fontFamily = RidgeType.sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp), color = Ridge.ink)
            Text(sub, style = TextStyle(fontFamily = RidgeType.sans, fontSize = 12.sp, lineHeight = 16.sp), color = Ridge.inkMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkSubtle)
    }
}
