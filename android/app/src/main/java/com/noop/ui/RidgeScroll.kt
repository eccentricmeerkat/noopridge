package com.noop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// MARK: - Backdrop scroll (SQ3-2, side quest #3 doc 3)
//
// The mountain is part of the first screen of a page, not a wallpaper. On every page but Today it moves up by exactly
// the distance the content has scrolled (1:1, no parallax); the navy canvas under it never moves. On Today it exits
// at its own pace: slower than the content at first (parallax, so the orbs travel over the mountains), then 1:1 once Synthesis is almost at the top. Both read the list's own scroll state: one
// source of truth, nothing synchronised by hand.

/** How far the page's content has scrolled, in pixels, for a backdrop that follows it. Provided by the two shared
 *  scaffolds; the Ridge page backdrop reads it inside `graphicsLayer`, so scrolling never recomposes. */
internal val LocalPageScrollPx = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * The absolute scroll distance of a lazy list from its top: the sizes (plus [spacingPx]) of every item above the first
 * visible one, plus how far that item has scrolled. [sizeOf] gives an item's measured size, or null if it was never on
 * screen (a list restored part-way down); those fall back to [fallbackSizePx], the average measured size.
 */
internal fun listScrollPx(
    firstIndex: Int,
    firstOffsetPx: Int,
    spacingPx: Float,
    sizeOf: (Int) -> Int?,
    fallbackSizePx: Float,
): Float {
    var total = firstOffsetPx.toFloat().coerceAtLeast(0f)
    for (i in 0 until firstIndex.coerceAtLeast(0)) total += (sizeOf(i)?.toFloat() ?: fallbackSizePx) + spacingPx
    return total
}

/** B2-1a (owner, 19 Nov 2026): how much of the page's scroll the Today photo follows at first. Low, so the orbs travel
 *  well over the mountains; the photo is much slower than the content. */
internal const val TODAY_PARALLAX_FOLLOW = 0.35f

/** The parallax ends, and the photo goes back to moving 1:1 with the content, once Synthesis is this far from the top
 *  of the screen ("almost at the top"). */
internal const val TODAY_PARALLAX_END_BELOW_TOP_DP = 100f

/** The stretch of scroll over which the photo's speed eases from [TODAY_PARALLAX_FOLLOW] up to 1:1, so it never jerks. */
internal const val TODAY_PARALLAX_EASE_DP = 220f

/**
 * How far the Today photo has moved up, in pixels, after the page has scrolled [scrollPx]. The photo follows
 * [TODAY_PARALLAX_FOLLOW] of the scroll (parallax), eases to the content's own speed as Synthesis nears the top, and from
 * then on moves 1:1. [synthTopAtRestPx] is the Synthesis card's top with the list at rest (0 while unmeasured, or while
 * Customize Today hides the card; the list's own height stands in). The speed is a smoothstep, so this is its integral.
 */
internal fun todayPhotoOffsetPx(scrollPx: Float, synthTopAtRestPx: Float, listViewportPx: Int, density: Float): Float {
    if (scrollPx <= 0f) return 0f
    val synthTop = if (synthTopAtRestPx > 0f) synthTopAtRestPx else listViewportPx * 0.45f
    val ease = TODAY_PARALLAX_EASE_DP * density
    val end = (synthTop - TODAY_PARALLAX_END_BELOW_TOP_DP * density).coerceAtLeast(ease + 1f)
    val start = end - ease
    val k = TODAY_PARALLAX_FOLLOW
    // The extra distance (beyond k x scroll) the photo has gained: nothing before the ease, a smoothstep integral in it,
    // and the scroll itself afterwards.
    val gain = when {
        scrollPx <= start -> 0f
        scrollPx >= end -> ease * 0.5f + (scrollPx - end)
        else -> {
            val t = (scrollPx - start) / ease
            ease * (t * t * t - t * t * t * t * 0.5f)
        }
    }
    return k * scrollPx + (1f - k) * gain
}

/** How far through its exit the backdrop is: 0 at the top of the page, 1 once [scrollPx] reaches [exitDistancePx]. */
internal fun backdropExitProgress(scrollPx: Float, exitDistancePx: Float): Float {
    if (scrollPx <= 0f) return 0f
    if (exitDistancePx <= 1f) return 1f
    return (scrollPx / exitDistancePx).coerceIn(0f, 1f)
}

/**
 * The scroll distance of [state] as a lazy read: item sizes are remembered as they are laid out, so the distance stays
 * exact however far the user scrolls and however the list is restored. Read it only inside a draw or layer lambda.
 */
@Composable
internal fun rememberListScrollPx(state: LazyListState, spacing: Dp): () -> Float {
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val sizes = remember(state) { HashMap<Int, Int>() }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { seen -> for ((i, size) in seen) sizes[i] = size }
    }
    val px = remember(state, spacingPx) {
        derivedStateOf {
            val average = if (sizes.isEmpty()) 0f else sizes.values.average().toFloat()
            listScrollPx(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, spacingPx, { sizes[it] }, average)
        }
    }
    return { px.value }
}
