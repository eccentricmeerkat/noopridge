package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import com.noop.ui.ridge.RidgeBackButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.noop.R
import com.noop.ui.ridge.RidgePageBackdrop

// MARK: - Ridge tool pages (R7c, B-03)
//
// Every Hub tool (Live, Breathe, Settings, Devices, …) keeps v10's content and is restyled, visual only, by one
// wrapper: glass cards and section headers ([LocalGlassCards]), Ridge pills, the Ridge page backdrop when the screen
// paints none of its own, and the page title in the Ridge type under a "Hub" eyebrow. A screen opts in at its
// NavHost registration ([ridgeTool]), so the restyle lands one Hub group at a time.

/** True inside a Hub tool page drawn in the Ridge style; read by the two scaffolds. */
internal val LocalRidgeToolPage = staticCompositionLocalOf { false }

/** The action a Ridge tool page shows at the right of its header (the board's "Timer", "Experimental"), or null. */
internal val LocalRidgeHeaderAction = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** A page that hosts another screen under its own title (Your data hosts Sources, Fused, Imported): the title to show. */
internal val LocalRidgeTitleOverride = staticCompositionLocalOf<String?> { null }

/** Content a hosting page draws under the title block (the Your data tabs), or null. */
internal val LocalRidgeUnderTitle = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** Restyles [content] as a Ridge tool page under the Ridge shell; v10 is untouched. [headerAction] fills the header's
 *  right-hand slot. */
@Composable
internal fun RidgeToolPage(headerAction: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    if (!ridgeShell) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalGlassCards provides true, LocalRidgeToolPage provides true, LocalRidgeHeaderAction provides headerAction,
    ) { content() }
}

/** A NavHost destination drawn as a Ridge tool page (see [RidgeToolPage]); [headerAction] is its header's right-hand slot. */
internal fun NavGraphBuilder.ridgeTool(
    route: String,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route) { entry -> RidgeToolPage(headerAction) { content(entry) } }
}

/**
 * The scaffold's backdrop: the screen's own when it has one; on a Ridge tool page with none, the Ridge page
 * backdrop, unless the "Day-cycle background" setting is off.
 */
@Composable
internal fun ridgeToolBackdropOr(topBackground: (@Composable () -> Unit)?): (@Composable () -> Unit)? {
    if (topBackground != null || !LocalRidgeToolPage.current) return topBackground
    val context = LocalContext.current
    val show = remember { NoopPrefs.showDayCycleBackground(context) }
    return if (show) { { RidgePageBackdrop() } } else null
}

/**
 * The scaffold's title block: v10's, or on a Ridge tool page the board's header (SQ9 S2): a back chevron beside the serif
 * title on one line, and the page's header action, when it has one, at the right. The "Hub" eyebrow and the muted line
 * under the title are the v10-era header; the boards have neither.
 */
@Composable
internal fun ScaffoldTitleBlock(title: String?, subtitle: String?, modifier: Modifier = Modifier) {
    if (LocalRidgeToolPage.current) {
        val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val action = LocalRidgeHeaderAction.current
        val shownTitle = LocalRidgeTitleOverride.current ?: title
        val under = LocalRidgeUnderTitle.current
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Ridge.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                RidgeBackButton(stringResource(R.string.l10n_onboarding_screen_back_b52b36b7), onClick = { back?.onBackPressed() })
                if (shownTitle != null) {
                    Text(
                        hubTitleCase(shownTitle), style = RidgeType.titleLg, color = Ridge.ink,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                action?.invoke()
            }
            under?.invoke()
        }
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) Text(title, style = NoopType.title1, color = Palette.textPrimary)
        if (subtitle != null) Text(subtitle, style = NoopType.subhead, color = Palette.textSecondary)
    }
}
