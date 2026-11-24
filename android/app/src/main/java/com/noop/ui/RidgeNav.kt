package com.noop.ui

import com.noop.R
import com.noop.ui.ridge.RidgeIcons
import com.noop.ui.ridge.RidgeTab

/**
 * The Ridge bottom bar (A-01, R5-1): Today · Trends · Baseline · Sleep · Hub, the order and glyphs of the boards'
 * tab bar (`More.dc.html`). Hub opens the Ridge Hub ([RidgeHubScreen]); Coach leaves the bar and is the Hub's
 * AI-coach tile.
 */
internal val ridgeTabDestinations: List<Destination> = listOf(
    Destination.Today, Destination.Trends, Destination.Baseline, Destination.Sleep, Destination.More,
)

/** The label resource for each Ridge tab. */
internal fun ridgeTabLabel(dest: Destination): Int = when (dest) {
    Destination.Baseline -> R.string.nav_baseline
    Destination.More -> R.string.nav_hub
    else -> dest.titleRes
}

/** The Ridge tabs as the bar draws them, keyed by route. */
internal fun ridgeTabs(label: (Int) -> String): List<RidgeTab> = ridgeTabDestinations.map { d ->
    val icon = when (d) {
        Destination.Today -> RidgeIcons.sun
        Destination.Trends -> RidgeIcons.bars
        Destination.Baseline -> RidgeIcons.peak
        Destination.Sleep -> RidgeIcons.moon
        else -> RidgeIcons.hub
    }
    RidgeTab(d.route, label(ridgeTabLabel(d)), icon)
}

/**
 * The tab lit for [current]: its own tab, or Hub for anything reached through Hub, so a drilled-in screen never
 * reads as "nowhere" (the rule the v10 bar keeps for More).
 */
internal fun ridgeTabFor(current: Destination): Destination =
    if (current in ridgeTabDestinations) current else Destination.More

/**
 * #72: the tab whose back stack is on screen. Every tab switch pops to Today (the start) and pushes the tab, so the stack
 * holds Today plus at most one other tab: that tab owns it when present, else Today does. [inStack] says whether a
 * destination has an entry on the stack. A page opened from a tab (Insights from a Today orb) belongs to that tab.
 */
internal fun ridgeStackTab(inStack: (Destination) -> Boolean): Destination =
    ridgeTabDestinations.drop(1).firstOrNull(inStack) ?: Destination.Today

/** #72: what a tab tap does. Tapping the tab that owns the stack returns to its root; any other tab switches to it. */
internal enum class RidgeTabTap { STAY, POP_TO_ROOT, SWITCH }

/** #72: the decision for a tap on [tapped] while [current] shows and [owner] owns the stack. Pure, unit-tested. */
internal fun ridgeTabTap(tapped: Destination, current: Destination, owner: Destination): RidgeTabTap = when {
    tapped != owner -> RidgeTabTap.SWITCH
    current == tapped -> RidgeTabTap.STAY
    else -> RidgeTabTap.POP_TO_ROOT
}
