package com.noop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.noop.R
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - LiquidScreenSky — the reusable liquid Today/liquid-screen sky backdrop
//
// THE ESTABLISHED ANDROID LIQUID SKY-BACKDROP PATTERN (the pilot from the liquid Today; the other liquid
// screens copy this verbatim). It is the Android equivalent of the iOS
// `ScreenScaffold(topBackground: liquidScaffoldSky())` — the day-of-sky settles into the theme canvas
// behind the screen's top content and the cards float OVER it on the flat canvas below.
//
// HOW IT PLUGS IN: pass this as the scaffold's `topBackground` slot:
//
//   LazyScreenScaffold(
//       ...
//       topBackground = if (showDayCycleBackground) { { LiquidScreenSky() } } else null,
//   ) { ... }
//
// The existing ScreenScaffold / LazyScreenScaffold `topBackground` machinery (Components.kt) already does
// the screen-level plumbing this backdrop needs — it anchors the slot to the TOP, bleeds it full-width UP
// behind the status bar (offset by the status-bar inset), and promotes it to its OWN compositing layer (an
// empty `graphicsLayer {}`) so a static backdrop rasterises ONCE and replays as a texture on every scroll
// frame. So this composable only has to paint the two layers, top-aligned, at a header height.
//
// WHY LiquidSkyStatic (not the animated LiquidSky): Today is a long, scroll-heavy LazyColumn; an
// always-animating Canvas behind it steals frame headroom and stutters the scroll. LiquidSkyStatic renders
// ONCE (no per-frame clock), matching the iOS choice of `LiquidSkyStatic` for the scaffold sky and the
// classic Android scene's static-image treatment. It settles into `Palette.surfaceBase` internally, so the
// sky dissolves into the page with no seam (the sky owns its own fade — no extra scrim needed here).
//
// WHY the surfaceBase fill under it: the sky band is only [height] tall; the canvas fill guarantees the
// region ABOVE the fold and any sub-pixel gap reads as the theme canvas, exactly like the iOS backdrop's
// `ZStack { surfaceBase; sky }`.
//
// Non-interactive + accessibility-hidden — it is pure decoration (the scaffold slot never receives taps).

// MARK: - Screen backdrop precedence (#custom-background)
//
// The ONE decision every liquid screen shares: which backdrop goes in the scaffold's `topBackground` slot.
// Precedence is custom image > day-cycle sky > flat canvas. A custom background image (BackgroundImage.kt)
// OVERRIDES the sky and always bleeds full-screen, so it reads identically behind every tab (the same
// decode-once cached bitmap on each) — seamless at the tab crossfade. When no image is active this is
// exactly the previous `showDayCycleBackground ? sky : null` behavior. Routing every screen through these
// two helpers is what keeps the image seamless across tabs including More.

/** The scaffold `topBackground` slot honouring the image > sky > canvas precedence. `isActive` is
 *  snapshot-backed, so toggling/removing the image in Settings re-picks the backdrop live. */
fun screenBackdropSlot(
    showDayCycleBackground: Boolean,
    skyBehindCards: Boolean,
): (@Composable () -> Unit)? = when {
    BackgroundImageStore.isActive -> { { BackgroundImageBackdrop() } }
    // Side quest #2: under the Ridge shell every page but Today (its hero photo) and Onboarding (its welcome
    // photo) draws the generated mountain backdrop, seed 42, in place of the time-of-day sky.
    showDayCycleBackground && ridgeShell -> { { com.noop.ui.ridge.RidgePageBackdrop() } }
    showDayCycleBackground -> { { LiquidScreenSky(fillHeight = skyBehindCards) } }
    else -> null
}

/** `fullBleedBackground` for the same precedence: a custom image always bleeds behind the whole scroll;
 *  the sky only when "Sky behind cards" is on. */
fun screenBackdropFullBleed(
    showDayCycleBackground: Boolean,
    skyBehindCards: Boolean,
): Boolean = BackgroundImageStore.isActive || (showDayCycleBackground && skyBehindCards)

/** Today's scaffold `topBackground` slot. Same precedence as [screenBackdropSlot], except the backdrop
 *  that the "Day-cycle background" setting turns on is the Noop Ridge photograph ([RidgeBackdrop]),
 *  leaving by [exitPx] of scroll. A custom image still wins. Setting off paints the plain Ridge ground rather
 *  than the theme canvas, since Today's cards are drawn for the night ground either way. */
fun todayBackdropSlot(
    showDayCycleBackground: Boolean,
    scrollPx: () -> Float,
    exitPx: () -> Float,
): (@Composable () -> Unit)? = when {
    BackgroundImageStore.isActive -> { { BackgroundImageBackdrop() } }
    showDayCycleBackground -> { { RidgeBackdrop(scrollPx = scrollPx, exitPx = exitPx) } }
    else -> { { Box(modifier = Modifier.fillMaxSize().background(Ridge.bg100)) } }
}

/** `fullBleedBackground` for [todayBackdropSlot]: every branch covers the whole viewport behind the
 *  scroll, so the glass cards show it all the way down. */
@Suppress("UNUSED_PARAMETER")
fun todayBackdropFullBleed(showDayCycleBackground: Boolean): Boolean = true

/** The glass Today redesign's backdrop (docs/assets/noop_bckgrnd.jpg, bundled as
 *  `drawable-nodpi/today_backdrop.webp`): the blue field with amber light ribbons, cropped to fill the
 *  viewport. Static, so the scaffold rasterises it once like the sky. Decorative — hidden from TalkBack. */
@Composable
fun TodayGlassBackdrop(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.today_backdrop),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {},
    )
}

/** The reusable liquid sky backdrop for a liquid screen's top region. Drop it into a scaffold's
 *  `topBackground` slot. [height] is the sky band; the sky fades into the theme canvas within it, so the
 *  cards below sit on the flat surface. Mirrors the iOS `liquidScaffoldSky`. */
@Composable
fun LiquidScreenSky(height: Dp = 340.dp, fillHeight: Boolean = false) {
    // "Sky behind cards" (opt-in): fill the whole viewport and hold the atmosphere with a softer settle so
    // the sky still reads UNDER the lower cards, instead of the default top-band that dissolves to canvas.
    val sizeMod = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height)
    Box(
        modifier = sizeMod
            .background(Palette.surfaceBase)
            .clearAndSetSemantics {}, // decorative — invisible to TalkBack
    ) {
        // The static time-of-day sky, top-aligned, settling into Palette.surfaceBase over its lower half.
        LiquidSkyStatic(
            hour = null, // live local hour (hour + minute/60)
            modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height),
            // A partial settle in fill-height mode keeps the horizon tint alive behind the scroll.
            settleStrength = if (fillHeight) 0.78f else 1f,
        )
    }
}
