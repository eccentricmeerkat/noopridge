package com.noop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.noop.R
import com.noop.ui.ridge.RidgeBackButton
import com.noop.ui.ridge.RidgeSegmentedPills

// MARK: - Ridge Insights (R7b-1, G-02, `Insights.dc.html`)
//
// One Insights page with three modes, built on v10's three screens: Explore (one metric, one dropdown), Compare (2–4
// signals overlaid) and Intelligence (the model, the forecast, by day). Each screen draws this header under the Ridge
// shell; picking a mode replaces the screen, so Back returns to whoever opened Insights.

internal enum class InsightsMode(val route: String, @StringRes val labelRes: Int, @StringRes val subRes: Int) {
    EXPLORE("explore", R.string.nav_explore, R.string.insights_ridge_sub_explore),
    COMPARE("compare", R.string.nav_compare, R.string.insights_ridge_sub_compare),
    INTELLIGENCE("intelligence", R.string.nav_intelligence, R.string.insights_ridge_sub_intelligence),
}

/** The board's header (a back chevron beside "Insights"; this page keeps the mode's line under it), then the three modes as full-width pills. */
@Composable
internal fun RidgeInsightsHeader(mode: InsightsMode, onMode: (InsightsMode) -> Unit) {
    val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Column(verticalArrangement = Arrangement.spacedBy(Ridge.space3)) {
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ridge.space2)) {
                RidgeBackButton(stringResource(R.string.l10n_onboarding_screen_back_b52b36b7), onClick = { back?.onBackPressed() })
                Text(
                    stringResource(R.string.nav_insights), style = RidgeType.titleLg, color = Ridge.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(stringResource(mode.subRes), style = RidgeType.bodySm, color = Ridge.inkMuted)
        }
        val labels = InsightsMode.entries.associateWith { stringResource(it.labelRes) }
        RidgeSegmentedPills(
            items = InsightsMode.entries.toList(),
            selected = mode,
            onSelect = { if (it != mode) onMode(it) },
            label = { labels.getValue(it) },
            fill = true,
        )
    }
}

/** The metrics Explore draws itself (its built-ins); a Today tile with one of these opens Explore on it. */
internal val EXPLORE_METRIC_KEYS = setOf("recovery", "strain", "hrv", "rhr", "sleep", "efficiency", "resp", "spo2")

/**
 * Where a Today tile or card goes under the Ridge shell (A-03 as refined in R7b): Explore on the metric when Explore
 * has it; Baseline for the slow markers that moved there; Sleep for Rest; otherwise v10's own focused trend, so a
 * tile never opens a different metric.
 */
internal fun ridgeMetricRoute(key: String): String = when (key) {
    in EXPLORE_METRIC_KEYS -> exploreRoute(key)
    "fitness_age", "vo2max_est", "vitality" -> Destination.Baseline.route
    "rest" -> Destination.Sleep.route
    "insights" -> Destination.Insights.route
    // B2-X: any other catalogue metric opens Insights on that metric (Fix 5: Calories, Skin temperature and Steps too; they used
    // to open v10's empty vital_detail page).
    else -> MetricCatalog.forKey(key)?.let { exploreRoute(it.key) } ?: "vital_detail/$key"
}
