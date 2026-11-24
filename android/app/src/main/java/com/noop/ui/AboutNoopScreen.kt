package com.noop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.BuildConfig
import com.noop.R
import com.noop.ui.ridge.GlassCard
import com.noop.ui.ridge.RidgeSegmentedPills

private enum class AboutTab { HOW, NEW, LIMITS }

/**
 * B2 Hub5b (`About.dc.html`): one About NOOP page with the board's three tabs. How it works, What's new and Limits are the
 * app's existing content (its own copy and release notes), hosted under the tabs; the page adds the version line, the
 * "never makes up a number" line, a door to Test Centre ("Lab tools") and the open-source footer.
 */
@Composable
fun AboutNoopScreen(onOpenTestCentre: () -> Unit) {
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
                AboutTab.NEW -> WhatsNewBody()
                AboutTab.LIMITS -> NoopLimitationsBody()
            }
            GlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpenTestCentre), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.about_ridge_lab), style = RidgeType.titleSm, color = Ridge.ink)
                        Text(stringResource(R.string.about_ridge_lab_sub), style = RidgeType.bodySm, color = Ridge.inkMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ridge.inkMuted)
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
        }
    }
}
