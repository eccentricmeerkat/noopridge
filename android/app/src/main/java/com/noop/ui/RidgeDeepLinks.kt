package com.noop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI
import java.net.URLDecoder

/**
 * Ridge deep links (R7a-2, A-02): `noop://` links, from a notification, widget or another app.
 *
 * - `noop://today|trends|baseline|sleep|hub` switch to that tab (its own back stack).
 * - `noop://trends?mode=7|14|30` also sets the Trends range.
 * - `noop://insights?m=hrv` opens Insights › Explore on that metric, pushed on the current tab, so Back returns to
 *   the caller. `noop://insights` alone opens the Insights page (Explore, R7b).
 * - `noop://workout/{startTs}` opens that workout's page (R7b-2); without a start time, the Workouts list.
 * - `noop://coach_settings` opens Coach (its settings need the conversation below them; R7c).
 * - any other `noop://<route>` that names a known screen is pushed. Unknown links do nothing.
 */
internal sealed interface DeepLinkTarget {
    data class Tab(val route: String) : DeepLinkTarget
    data class Push(val route: String) : DeepLinkTarget
}

/** The Ridge workout page's route, keyed by the session's start time (epoch seconds) (R7b-2, G-03). */
internal const val WORKOUT_ROUTE = "workout/{start}"

internal fun workoutRoute(startTs: Long): String = "workout/$startTs"

/** The route Insights › Explore registers with a preselected metric (A-03, A-02). */
internal const val EXPLORE_METRIC_ROUTE = "explore/{m}"

internal fun exploreRoute(metricKey: String): String = "explore/$metricKey"

/** Trends ranges a link may ask for. */
internal fun trendsRangeFor(mode: String?): RidgeTrendsRange? =
    RidgeTrendsRange.entries.firstOrNull { mode?.removeSuffix("d") == it.days.toString() }

internal fun parseNoopLink(link: String?): DeepLinkTarget? {
    val uri = runCatching { URI(link ?: return null) }.getOrNull() ?: return null
    if (uri.scheme != "noop") return null
    // The JVM's URI rejects "_" in a host (lab_book, coach_settings) and leaves host null; Android's accepts it. The
    // authority holds the same text on both.
    val host = (uri.host ?: uri.rawAuthority)?.lowercase() ?: return null
    val path = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val kv = it.split('=', limit = 2)
        if (kv.size == 2 && kv[0].isNotEmpty()) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
    }.toMap()
    return when (host) {
        "today" -> DeepLinkTarget.Tab(Destination.Today.route)
        "trends" -> DeepLinkTarget.Tab(Destination.Trends.route)
        "baseline" -> DeepLinkTarget.Tab(Destination.Baseline.route)
        "sleep" -> DeepLinkTarget.Tab(Destination.Sleep.route)
        "hub", "more" -> DeepLinkTarget.Tab(Destination.More.route)
        "insights" -> query["m"]?.takeIf { it.isNotBlank() }?.let { DeepLinkTarget.Push(exploreRoute(it)) }
            ?: DeepLinkTarget.Push(Destination.Explore.route)
        "workout" -> path.firstOrNull()?.toLongOrNull()?.let { DeepLinkTarget.Push(workoutRoute(it)) }
            ?: DeepLinkTarget.Push(Destination.Workouts.route)
        "workouts" -> DeepLinkTarget.Push(Destination.Workouts.route)
        // Coach settings shares the conversation's view model and needs Coach below it; a link opens Coach.
        "coach_settings" -> DeepLinkTarget.Push(Destination.Coach.route)
        else -> Destination.entries
            .firstOrNull { it.route == host && '{' !in it.route }
            ?.let { DeepLinkTarget.Push(it.route) }
    }
}

/** The hand-off from [MainActivity] (launch and new intents) to the app shell, which consumes it once. */
internal object RidgeDeepLinks {
    var pending by mutableStateOf<String?>(null)
        private set

    /** A Trends range a link asked for, read once by the Trends page. */
    var pendingTrendsRange by mutableStateOf<RidgeTrendsRange?>(null)

    fun offer(link: String?) {
        if (link?.startsWith("noop://") != true) return
        if (link.startsWith("noop://trends")) {
            pendingTrendsRange = trendsRangeFor(runCatching { URI(link) }.getOrNull()?.rawQuery
                ?.split('&')?.firstOrNull { it.startsWith("mode=") }?.removePrefix("mode="))
        }
        pending = link
    }

    fun consume(): String? = pending.also { pending = null }
}
