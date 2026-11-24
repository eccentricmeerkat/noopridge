package com.noop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noop.R

// MARK: - Noop Ridge design system (phase 1: the Today screen)
//
// The Android binding of the "Noop Ridge" design system: a calm, night-time look where every headline
// score is liquid in a glass orb, cards are frosted glass over a mountain-ridge photograph, headings and
// numerals are Playfair Display Bold, and UI text is Inter. Token names and values mirror the system's
// tokens.json one-for-one, so a value is changed in exactly one place. Source of truth:
// design/prototype/noop-tokens.css (B-01, Sprint 1).
//
// The system has a single Night theme, so these tokens do not flip with the light/dark setting. With
// BuildConfig.RIDGE_SHELL every [Palette] screen also reads them, through [RidgeTokens].

/** Noop Ridge colour, spacing and radius tokens (Night theme). */
object Ridge {
    // Ground.
    val bg000 = Color(0xFF0C161C)
    val bg100 = Color(0xFF131F26)
    val bg200 = Color(0xFF172630)
    val bg300 = Color(0xFF1F313B)
    val bg400 = Color(0xFF283D48)

    // Glass.
    val glassFill = Color(170, 205, 220).copy(alpha = 0.06f)
    val glassFillStrong = Color(170, 205, 220).copy(alpha = 0.10f)
    val glassStroke = Color(190, 220, 235).copy(alpha = 0.14f)
    val glassHighlight = Color.White.copy(alpha = 0.14f)
    /** The toggle's off track (`Settings.dc.html` inline style, not a named CSS token). */
    val toggleTrackOff = Color(190, 220, 235).copy(alpha = 0.22f)

    // Ink.
    val ink = Color(0xFFF5EFE4)
    val inkMuted = Color(0xFFB6C3CA)
    val inkSubtle = Color(0xFF9DADB6)
    val inkOnLiquid = Color(0xFF0C161C)

    // Liquids. Rest shares the brand mint.
    val brand = Color(0xFF59ECAA)
    val brandDeep = Color(0xFF1F8A5E)
    val charge = Color(0xFFE8B84F)
    val chargeDeep = Color(0xFF9A6A18)
    val effort = Color(0xFF6BA1FD)
    val effortDeep = Color(0xFF2C5BBD)
    val rest = brand
    val restDeep = brandDeep
    val battery = Color(0xFF2F9A62)
    val batteryDeep = Color(0xFF15512F)

    // Synthesis states, Depleted → Ready.
    val stateDepleted = Color(0xFFF7777D)
    val stateLow = Color(0xFFF39A5B)
    val stateModerate = Color(0xFFE8C46A)
    val stateGood = Color(0xFFA6DC7E)
    val stateReady = brand
    val states = listOf(stateDepleted, stateLow, stateModerate, stateGood, stateReady)

    // Secondary-metric data hues (icons, sparklines, bars only).
    val dataHrv = Color(0xFFB8A3FF)
    val dataHeart = Color(0xFFFF8198)

    /** The board's heart-rate zone colours (`WorkoutDetail.dc.html`): Z1 blue to Z5 red. */
    private val hrZoneRamp = listOf(Color(0xFF4F9DFF), Color(0xFF9FCF72), Color(0xFFF2D14B), Color(0xFFF08A2C), Color(0xFFE5383B))
    fun hrZone(zone: Int): Color = hrZoneRamp[(zone - 1).coerceIn(0, 4)]
    val dataEnergy = Color(0xFFFFB45E)
    val dataBreath = Color(0xFF58D7C9)
    /** The Calories card's "resting and everyday" base bar (Trends.dc.html `#9fb4c2`). */
    val dataBase = Color(0xFF9FB4C2)
    /** The Consistency card's "light–moderate" fill (Trends.dc.html `#3f5c9c`). */
    val dataConsistency = Color(0xFF3F5C9C)

    // SQ8 header orbs (`Side Quests/Header Nav`, Main.dc.html): the round glass header controls and the strap battery orb.
    val headerIconActive = Color(0xFFC6F24E)
    val headerIconInactive = Color(0xFF9FB4C4)
    val headerDiscRim = Color(0x73BEEBEB)
    val headerDiscCentre = Color(0x8C146E82)
    val headerDiscMid = Color(0x382882AA)
    val headerDiscEdge = Color(0x2478C8D7)
    val headerDiscInnerGlow = Color(0x3896E1E1)
    val headerDiscShadow = Color(0x4D081824)
    val strapOrbGlassTop = Color(0xD13E5874)
    val strapOrbGlassBottom = Color(0xD11E3044)
    val strapOrbNumber = Color(0xFFFBF6EA)
    val strapOrbNumberShadow = Color(0x8C081824)
    val strapOrbOffRing = Color(0xB3A0B4C8)
    val strapOrbOffGlyph = Color(0xFFE9EFF5)
    val strapOrbChargingRing = Color(0xFF59ECAA)
    val strapOrbChargingGlow = Color(0x8C59ECAA)
    val strapOrbChargingBolt = Color(0xFF8FF34A)
    val strapOrbHighRing = Color(0xFF6FF0A8)
    val strapOrbHighLiquid = Color(0xFF2FBF72)
    val strapOrbHighGlow = Color(0x8C59ECAA)
    val strapOrbMediumRing = Color(0xFFF6CF55)
    val strapOrbMediumLiquid = Color(0xFFE0AE2C)
    val strapOrbMediumGlow = Color(0x80F6CF55)
    val strapOrbLowRing = Color(0xFFFF9A55)
    val strapOrbLowLiquid = Color(0xFFE0702F)
    val strapOrbLowGlow = Color(0x80FF9A55)
    val strapOrbCriticalRing = Color(0xFFFF5F6D)
    val strapOrbCriticalLiquid = Color(0xFFE8445A)
    val strapOrbCriticalGlow = Color(0x80FF5F6D)
    // The SQ8 bottom sheet (Main.dc.html): scrim rgba(6,11,15,0.62), #17242C to #0F1A20, handle cream at 25%.
    val sheetScrim = Color(0x9E060B0F)
    // SQ8b (More.dc.html): the notifications panel's top (the Hub's sheet starts bluer than Today's), its cards, icon
    // tiles, unread dot glow and the Mark all read pill's ink; the Sage tile's green border and the wizard's lime glow.
    val sheetTopHub = Color(0xFF17303A)
    val notifCardTop = Color(0x801E4248)
    val notifCardBottom = Color(0x8C122C34)
    val notifCardStroke = Color(0x4778DCBE)
    val notifIconTile = Color(0x473C826E)
    val notifDotGlow = Color(0xCC59ECAA)
    val markAllReadInk = Color(0xFF0B1A14)
    val sageTileStroke = Color(0x5959ECAA)
    val sageGlow = Color(0x66C6F75C)
    // The greeting's shadow over the photo (Main.dc.html .np-hero-greet: rgba(8,18,24,0.55), 14 px blur).
    val greetingShadow = Color(0x8C081218)
    // B2-1a-1 (Main.dc.html .np-greet-name): the cream second line of the greeting and the date line above it.
    val greetingName = Color(0xFFFBF6EA)
    val sheetTop = Color(0xFF17242C)
    val sheetBottom = Color(0xFF0F1A20)
    val sheetHandle = Color(0x40F5EFE4)
    // The day picker's chosen day (Main.dc.html): a mint hairline over mint at 16%.
    val dayPickOnFill = Color(0x2959ECAA)
    val dayPickOffFill = Color(0x0FAACDDC)
    val dayPickOffStroke = Color(0x24BEDCEB)

    val focusRing = ink

    // Sleep stages: one palette on Trends and Sleep, sampled from the owner's reference image
    // (`BS-x2/ref_sleep_stage_colours.png`, 21 Nov 2026, BS-x2-2 item 8): Deep dark indigo, Core purple, Light lavender,
    // REM green. This replaces the Sleep board's blue/teal/lavender set (owner D3, 20 Nov). The app's stage data is
    // Deep, Light and REM; [sleepCore] is defined for a source that supplies a Core stage and is drawn only then.
    // Segments are separated by a ground gap so each one meets 3:1 against the card rather than its neighbour.
    val sleepDeep = Color(0xFF4A2FB0)
    val sleepCore = Color(0xFF7B5CF0)
    val sleepLight = Color(0xFFAEB4F5)
    val sleepRem = Color(0xFF3FE3A0)
    val sleepAwake = Color(0xFFF39A5B)

    // Spacing.
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp

    // Radii.
    val radiusSm = 10.dp
    val radiusMd = 18.dp
    val radiusLg = 24.dp
    val radiusXl = 32.dp
    val radiusPill = 999.dp
}

/** Noop Ridge elevation: the card shadow and the three liquid glows (`--shadow-card`, `--glow-*`). Compose
 *  draws these with a blurred shadow or a radial brush; the colours and radii are the tokens. */
object RidgeElevation {
    val cardShadow = Color(2, 6, 16).copy(alpha = 0.45f)
    val cardShadowOffset = 12.dp
    val cardShadowBlur = 32.dp
    val cardInnerHighlight = Color.White.copy(alpha = 0.06f)
    val glowCharge = Ridge.charge.copy(alpha = 0.35f)
    val glowEffort = Ridge.effort.copy(alpha = 0.35f)
    val glowRest = Ridge.rest.copy(alpha = 0.35f)
    val glowBlur = 28.dp
    /** `--blur-glass`. Compose has no cheap backdrop blur, so this is reference only (see [ridgeGlass]). */
    val glassBlur = 20.dp
}

/** Noop Ridge chart chrome (`Trends.dc.html` inline styles, R-01): grid and axis hairlines, the scrub line and
 *  the scrub tooltip. Data marks take their metric hue from [Ridge]. */
object RidgeChart {
    val grid = Color(190, 220, 235).copy(alpha = 0.12f)
    val axis = Color(190, 220, 235).copy(alpha = 0.22f)
    val band = Color(190, 220, 235).copy(alpha = 0.08f)
    val scrubLine = Ridge.ink.copy(alpha = 0.55f)
    val tooltipFill = Ridge.bg000.copy(alpha = 0.94f)
    val tooltipStroke = Color(190, 220, 235).copy(alpha = 0.28f)
    /** The unstaged-night bar and the untracked outline. */
    val neutral = Ridge.inkSubtle
}

/** One of the three score liquids: the surface colour, the deep colour at the bottom of the gradient
 *  (and the back wave), and the glow under the orb. A metric keeps its liquid on every surface. */
enum class RidgeLiquid(val top: Color, val deep: Color) {
    CHARGE(Ridge.charge, Ridge.chargeDeep),
    EFFORT(Ridge.effort, Ridge.effortDeep),
    REST(Ridge.rest, Ridge.restDeep),
    /** The strap battery orb, the one orb that is not a score (design system p. 4): darker, quieter green. */
    BATTERY(Ridge.battery, Ridge.batteryDeep),
}

/** Noop Ridge type: Playfair Display for headings and numerals, Inter for UI text, Allura for the one
 *  hand-set script line per header. All three are bundled (SIL OFL 1.1, see NOTICE) so the app stays
 *  offline; Playfair and Inter are variable fonts, pinned to each weight the system uses. */
@OptIn(ExperimentalTextApi::class)
object RidgeType {
    private fun playfair(weight: Int) = Font(
        R.font.playfair_display, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
    private fun inter(weight: Int) = Font(
        R.font.inter, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val display = FontFamily(playfair(400), playfair(700))
    val sans = FontFamily(inter(300), inter(400), inter(500), inter(600))
    val script = FontFamily(Font(R.font.allura, FontWeight.Normal))

    private fun serif(size: Float, line: Float) = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = (-0.01).em,
        // Lining figures: Playfair defaults to old-style digits; the design system (p. 16) and the boards
        // (`font-variant-numeric: lining-nums`) set headings and numerals in lining figures.
        fontFeatureSettings = "lnum",
    )

    val numeralXl = serif(60f, 60f)
    val numeralLg = serif(44f, 44f)
    val titleXl = serif(40f, 44f)
    val titleLg = serif(30f, 34f)
    val titleSm = serif(22f, 26f)
    val numeralMd = serif(28f, 32f)

    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySm = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp,
        letterSpacing = 0.14.em,
    )
    val eyebrow = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.2.em,
    )
    val button = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        letterSpacing = 0.08.em,
    )
    val listTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val tab = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    /** The orb-card labels (CHARGE · EFFORT · REST, `Main.dc.html`): 11/14 Inter semibold, 0.18 em tracking. */
    val orbLabel = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.18.em,
    )
    val wordmark = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 22.sp, lineHeight = 24.sp,
        letterSpacing = 0.55.em,
    )
    val tagline = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp,
        letterSpacing = 0.34.em,
    )
    val scriptLine = TextStyle(fontFamily = script, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 26.sp)
}

// MARK: - Glass

/**
 * A Noop Ridge glass surface: [Ridge.glassFill] (or [Ridge.glassFillStrong] for the one card that stands
 * forward), a 1dp [Ridge.glassStroke] hairline and [radius] corners. [tint] washes the top-left corner in
 * that colour and tints the hairline, for a card that is ABOUT one liquid or state. Compose has no cheap
 * backdrop blur, so the fill alone stands in for `blur-glass`. Draw-only: add clip/padding at the call site.
 */
fun Modifier.ridgeGlass(
    radius: Dp = Ridge.radiusLg,
    strong: Boolean = false,
    tint: Color? = null,
    tintWash: Float = 0.10f,
    tintBorder: Float = 0.35f,
): Modifier = drawBehind {
    val r = radius.toPx()
    val corner = CornerRadius(r, r)
    drawRoundRect(color = if (strong) Ridge.glassFillStrong else Ridge.glassFill, cornerRadius = corner)
    if (tint != null) {
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to tint.copy(alpha = tintWash),
                0.6f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            cornerRadius = corner,
        )
    }
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = tint?.copy(alpha = tintBorder) ?: Ridge.glassStroke,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r - stroke / 2f, r - stroke / 2f),
        style = Stroke(width = stroke),
    )
}

// MARK: - Backdrop

/**
 * The Today backdrop: the morning ridge photograph (`drawable-nodpi/ridge_hero_morning.webp`, side quest #2) full width and
 * top-anchored, bled up behind the status bar. [scrollPx] is how far the screen has scrolled and [exitPx] the scroll
 * distance at which the Synthesis card reaches the top (SQ3-2): the photo rises faster than the content, in
 * proportion, so it is completely gone at exactly that point whatever the device. Below the photo, and across its
 * last stretch, the ground fades to [Ridge.bg100]. Decorative, hidden from TalkBack.
 * The offset is read inside `graphicsLayer`, so scrolling moves a layer and never recomposes.
 */
@Composable
fun RidgeBackdrop(scrollPx: () -> Float, exitPx: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ridge.bg100)
            .clearAndSetSemantics {},
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -size.height * backdropExitProgress(scrollPx(), exitPx()) },
        ) {
            // The photo's own aspect, so FillWidth draws it uncropped at this height.
            val photoHeight = maxWidth * (RIDGE_PHOTO_H / RIDGE_PHOTO_W)
            // BS-x2-1 3.1: the photograph follows the device clock; a cross-fade of about 600 ms, or an instant
            // swap under reduced motion.
            val slot by rememberHeroSlot()
            val reduce = rememberReduceMotion()
            Crossfade(
                targetState = slot,
                animationSpec = if (reduce) snap() else tween(600),
                label = "heroSlot",
            ) { shown ->
                Image(
                    painter = painterResource(shown.drawable),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().height(photoHeight),
                )
                // BS-x2-1 item 3: a soft scrim over the top third, per photograph, behind the greeting.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(photoHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to Ridge.greetingShadow.copy(alpha = shown.topScrimAlpha),
                                0.4f to Color.Transparent,
                            ),
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(photoHeight)
                    .background(
                        Brush.verticalGradient(
                            0.62f to Color.Transparent,
                            1f to Ridge.bg100,
                        ),
                    ),
            )
        }
    }
}

private const val RIDGE_PHOTO_W = 1080f
private const val RIDGE_PHOTO_H = 2430f

// MARK: - LiquidOrb

private val OrbPour = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * The signature Noop Ridge element: a glass sphere holding [liquid] filled to [fraction] of its height,
 * with [display] centred on it. The numeral is drawn twice, [Ridge.ink] above the surface and
 * [Ridge.inkOnLiquid] below it, so it reads as the level rises through it. [glass] switches to the glassy Fable
 * restyle (white shadowed numeral), which only Today's three main orbs use (FAB-2). The liquid sloshes in two
 * layers (3.6s and 5.2s, opposite directions) and a new value pours in over 900ms with a small overshoot.
 * Both stop when [animated] is false or the system asks for reduced motion; the slosh, an unbounded frame
 * loop, also stops under battery saver and Quiet motion ([rememberPoseStill]). The pour settles on its own,
 * so it follows reduced motion only, like the other one-shot tweens.
 *
 * Draw-only: it takes no pointer input, so a clickable parent receives the tap.
 */
@Composable
fun LiquidOrb(
    fraction: Double,
    display: String,
    liquid: RidgeLiquid,
    diameter: Dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    glow: Boolean = true,
    glass: Boolean = false,
) {
    val instantPour = rememberReduceMotion() || !animated
    val still = rememberPoseStill() || !animated
    val target = fraction.coerceIn(0.0, 1.0).toFloat()
    val frac by animateFloatAsState(
        targetValue = target,
        animationSpec = if (instantPour) tween(0) else tween(900, easing = OrbPour),
    )
    val front: Float
    val back: Float
    if (still) {
        front = 0f
        back = 0f
    } else {
        val slosh = rememberInfiniteTransition()
        val f by slosh.animateFloat(
            0f, 100f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        )
        val b by slosh.animateFloat(
            100f, 0f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        )
        front = f
        back = b
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Numeral size in the orb's 100-unit box, as the system sets it: a long value drops to 24.
    val units = if (display.length > 3) 24f else if (diameter <= 64.dp) 34f else 36f
    val numeralSp = with(density) { (diameter.toPx() * units / 100f).toSp() }
    val numeralStyle = RidgeType.numeralXl.copy(fontSize = numeralSp, lineHeight = numeralSp)

    Canvas(modifier = modifier.size(diameter)) {
        if (glass) {
            drawGlassOrb(frac, front, back, display, liquid, glow, measurer, numeralStyle)
        } else {
            drawClassicOrb(frac, front, back, display, liquid, glow, measurer, numeralStyle)
        }
    }
}

/** The Ridge orb as the design system draws it (B-01): the battery orb and any orb without `glass`. */
private fun DrawScope.drawClassicOrb(
    frac: Float,
    front: Float,
    back: Float,
    display: String,
    liquid: RidgeLiquid,
    glow: Boolean,
    measurer: TextMeasurer,
    numeralStyle: TextStyle,
) {
    val unit = size.minDimension / 100f
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = 50f * unit
    val rim = 1.6f * unit
    if (glow) drawOrbGlow(liquid.top, r)
    drawCircle(color = Ridge.bg200, radius = r - rim, center = c)

    // Surface height in the 100-unit box: 100 is empty, 0 is full.
    val level = 100f - 100f * frac
    val amp = if (frac <= 0.02f || frac >= 0.98f) 0f else 3.2f
    val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - rim)) }
    clipPath(body) {
        if (frac > 0f) {
            translate(left = back * unit) {
                drawPath(wavePath(level - 1.5f, amp, 1, unit), color = liquid.deep.copy(alpha = 0.85f))
            }
            translate(left = front * unit) {
                drawPath(
                    wavePath(level, amp, 0, unit),
                    brush = Brush.verticalGradient(
                        0f to liquid.top,
                        0.6f to liquid.top,
                        1f to liquid.deep,
                        startY = level * unit,
                        endY = 100f * unit,
                    ),
                )
            }
        }
        if (display.isNotEmpty()) {
            val laid = measurer.measure(display, numeralStyle)
            val at = Offset(c.x - laid.size.width / 2f, c.y - laid.size.height / 2f)
            drawText(laid, color = Ridge.ink, topLeft = at)
            clipRect(top = level * unit) {
                drawText(laid, color = Ridge.inkOnLiquid, topLeft = at)
            }
        }
        // Specular crescent top-left, and a small fleck under it.
        rotate(-35f, pivot = Offset(34f * unit, 22f * unit)) {
            drawOval(
                color = Ridge.glassHighlight,
                topLeft = Offset((34f - 14f) * unit, (22f - 7f) * unit),
                size = Size(28f * unit, 14f * unit),
            )
        }
        rotate(25f, pivot = Offset(20f * unit, 36f * unit)) {
            drawOval(
                color = Ridge.glassHighlight.copy(alpha = Ridge.glassHighlight.alpha * 0.7f),
                topLeft = Offset((20f - 3.2f) * unit, (36f - 5f) * unit),
                size = Size(6.4f * unit, 10f * unit),
            )
        }
    }
    // Rim: the liquid's colour bottom-left, fading to the glass hairline top-right.
    drawCircle(
        brush = Brush.linearGradient(
            0f to liquid.top,
            0.55f to liquid.top.copy(alpha = 0.55f),
            1f to Ridge.glassStroke,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
        ),
        radius = r - rim / 2f,
        center = c,
        style = Stroke(width = rim),
    )
}

/**
 * The glassy Fable restyle (sprint-1/Fable UI, 2026-11-13), for Today's three main orbs only (FAB-2): a lit glass
 * dome with a double-walled rim, a wide sheen and twin catchlights, a luminous liquid with a meniscus and pooled
 * light, and a white numeral with a soft shadow. Colours derive from the liquid and glass tokens.
 */
private fun DrawScope.drawGlassOrb(
    frac: Float,
    front: Float,
    back: Float,
    display: String,
    liquid: RidgeLiquid,
    glow: Boolean,
    measurer: TextMeasurer,
    numeralStyle: TextStyle,
) {
    val unit = size.minDimension / 100f
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = 50f * unit
    val rim = 2.6f * unit
    if (glow) drawOrbGlow(liquid.top, r)
    // Glass interior: a TRANSLUCENT dome (SQ3-3, doc 4) lit from above. A low-opacity navy tint, faintly liquid-coloured
    // at the top and a little deeper toward the rim, so the mountains and the panel show through the empty glass
    // above the liquid and the sphere still reads as volume. No opaque fill behind the liquid.
    drawCircle(
        brush = Brush.radialGradient(
            0f to liquid.top.copy(alpha = 0.10f),
            0.55f to Ridge.bg100.copy(alpha = 0.16f),
            1f to Ridge.bg000.copy(alpha = 0.34f),
            center = Offset(c.x, c.y - 18f * unit),
            radius = 1.5f * r,
        ),
        radius = r - rim,
        center = c,
    )

    // Surface height in the 100-unit box: 100 is empty, 0 is full. The level is the score itself
    // (FAB-1 (b), product owner 2026-11-13: no visual cap), so 93 fills 93% of the sphere.
    val level = 100f - 100f * frac
    val amp = if (frac <= 0.02f || frac >= 0.98f) 0f else 4.6f
    val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - rim)) }
    clipPath(body) {
        if (frac > 0f) {
            translate(left = back * unit) {
                drawPath(wavePath(level - 1.5f, amp, 1, unit), color = liquid.deep.copy(alpha = 0.9f))
            }
            translate(left = front * unit) {
                // Luminous fill (Fable render): a bright crest band melting into the saturated
                // body colour, then the deep shade — the liquid glows instead of sitting flat.
                // The crest band is ~5 units tall regardless of fill, so a nearly full orb
                // keeps its depth instead of flooding white.
                val crest = (5f / (100f - level).coerceAtLeast(1f)).coerceAtMost(0.3f)
                drawPath(
                    wavePath(level, amp, 0, unit),
                    brush = Brush.verticalGradient(
                        0f to lerp(liquid.top, Color.White, 0.24f),
                        crest to liquid.top,
                        1f to liquid.deep,
                        startY = level * unit,
                        endY = 100f * unit,
                    ),
                )
                // Meniscus: the surface line itself catches the light.
                drawPath(
                    waveLine(level, amp, 0, unit),
                    color = lerp(liquid.top, Color.White, 0.6f).copy(alpha = 0.9f),
                    style = Stroke(width = 1.8f * unit),
                )
            }
            // Light pooling where the liquid meets the glass floor.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to liquid.top.copy(alpha = 0.25f),
                    1f to liquid.top.copy(alpha = 0f),
                    center = Offset(c.x, 96f * unit),
                    radius = 40f * unit,
                ),
                radius = 40f * unit,
                center = Offset(c.x, 96f * unit),
            )
        }
        if (display.isNotEmpty()) {
            // Fable render: the numeral stays white over the liquid, lifted by a soft dark
            // shadow, rather than flipping to the dark on-liquid ink below the surface.
            val lifted = numeralStyle.copy(
                shadow = Shadow(
                    color = Ridge.inkOnLiquid.copy(alpha = 0.5f),
                    offset = Offset(0f, 1.2f * unit),
                    blurRadius = 5f * unit,
                ),
            )
            val laid = measurer.measure(display, lifted)
            val at = Offset(c.x - laid.size.width / 2f, c.y - laid.size.height / 2f)
            drawText(laid, color = Ridge.ink, topLeft = at)
        }
        // Specular (Fable render): a wide soft sheen across the upper dome, plus one hard
        // fleck top-left — a glass ball under a sky light, not a plastic sticker.
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.15f),
                1f to Color.White.copy(alpha = 0f),
                center = Offset(50f * unit, 13f * unit),
                radius = 32f * unit,
            ),
            topLeft = Offset(18f * unit, 4f * unit),
            size = Size(64f * unit, 22f * unit),
        )
        rotate(-30f, pivot = Offset(27f * unit, 15f * unit)) {
            drawOval(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset((27f - 6f) * unit, (15f - 2.6f) * unit),
                size = Size(12f * unit, 5.2f * unit),
            )
        }
        rotate(30f, pivot = Offset(73f * unit, 15f * unit)) {
            drawOval(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset((73f - 3.5f) * unit, (15f - 1.6f) * unit),
                size = Size(7f * unit, 3.2f * unit),
            )
        }
    }
    // Rim (Fable render): the glass edge is lit all round — white-hot at the very top,
    // the liquid's colour down the sides, brightening again where light pools at the base.
    drawCircle(
        brush = Brush.verticalGradient(
            0f to lerp(liquid.top, Color.White, 0.65f),
            0.4f to liquid.top.copy(alpha = 0.75f),
            0.8f to liquid.top,
            1f to lerp(liquid.top, Color.White, 0.3f),
        ),
        radius = r - rim / 2f,
        center = c,
        style = Stroke(width = rim),
    )
    // The inner glass wall: a soft second ring, so the sphere reads double-walled.
    drawCircle(
        color = liquid.top.copy(alpha = 0.16f),
        radius = r - rim - 2.5f * unit,
        center = c,
        style = Stroke(width = 5f * unit),
    )
}

/** The liquid's halo, spilling ~28dp past the orb. */
private fun DrawScope.drawOrbGlow(color: Color, r: Float) {
    val spread = 28.dp.toPx()
    val outer = r + spread
    drawCircle(
        brush = Brush.radialGradient(
            (r * 0.9f / outer) to color.copy(alpha = 0.35f),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = outer,
        ),
        radius = outer,
    )
}

/** The open sine surface line of [wavePath] — same geometry, nothing below it — for the meniscus stroke. */
private fun waveLine(level: Float, amp: Float, phase: Int, unit: Float): Path = Path().apply {
    val seg = 50f
    var x = -100f
    moveTo(x * unit, level * unit)
    for (i in 0 until 8) {
        val up = if ((i + phase) % 2 == 0) -amp else amp
        quadraticBezierTo((x + seg / 2f) * unit, (level + up) * unit, (x + seg) * unit, level * unit)
        x += seg
    }
}

/** A sine band twice the orb's width (so it can slide one period and loop), starting at x = -100 and
 *  closed down to the orb's bottom. [phase] flips which way the first crest bends. */
private fun wavePath(level: Float, amp: Float, phase: Int, unit: Float): Path = Path().apply {
    val seg = 50f
    var x = -100f
    moveTo(x * unit, level * unit)
    for (i in 0 until 8) {
        val up = if ((i + phase) % 2 == 0) -amp else amp
        quadraticBezierTo((x + seg / 2f) * unit, (level + up) * unit, (x + seg) * unit, level * unit)
        x += seg
    }
    lineTo(x * unit, 100f * unit)
    lineTo(-100f * unit, 100f * unit)
    close()
}

// MARK: - Small pieces

/** A glowing status dot (live sync, live data). */
@Composable
fun RidgeDot(color: Color = Ridge.brand, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = 0.6f),
                        1f to color.copy(alpha = 0f),
                        radius = this.size.minDimension,
                    ),
                    radius = this.size.minDimension,
                )
            }
            .clip(CircleShape)
            .background(color),
    )
}

/** A Noop Ridge chip. [solid] is the current selection (mint liquid, [Ridge.inkOnLiquid] text); otherwise
 *  an outline chip. Uppercase, tracked. */
@Composable
fun RidgeChip(
    text: String,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    dot: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (solid) Modifier.background(Ridge.brand)
                else Modifier.border(1.5.dp, Ridge.inkMuted, shape)
            )
            .padding(horizontal = Ridge.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ridge.space2),
    ) {
        if (dot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (solid) Ridge.inkOnLiquid else Ridge.brand),
            )
        }
        Text(text.uppercase(), style = RidgeType.label, color = if (solid) Ridge.inkOnLiquid else Ridge.ink, maxLines = 1)
    }
}

/** A thin progress bar: a dark pill track with the [accent] fill glowing softly. */
@Composable
fun RidgeBar(progress: Double, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = Ridge.bg000.copy(alpha = 0.7f), cornerRadius = corner)
        val w = size.width * progress.coerceIn(0.0, 1.0).toFloat()
        if (w > 0f) {
            drawRoundRect(
                color = accent.copy(alpha = 0.35f),
                topLeft = Offset(0f, -2.dp.toPx()),
                size = Size(w, h + 4.dp.toPx()),
                cornerRadius = CornerRadius(h, h),
            )
            drawRoundRect(color = accent, size = Size(w.coerceAtLeast(h), h), cornerRadius = corner)
        }
    }
}

/**
 * The full-bleed tile trend: a smooth area in [color] stretched across the whole box, its curve held
 * between [top] and [bottom] (fractions of the height) so a figure at the top stays clear. The line glows
 * softly; the area fades to nothing at the bottom. No axes, dots or labels — it is atmosphere.
 */
@Composable
fun RidgeTrendFill(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    top: Float = 0.56f,
    bottom: Float = 0.80f,
) {
    if (points.size < 2) return
    Canvas(modifier = modifier) {
        val min = points.min()
        val span = (points.max() - min).takeIf { it > 0.0 } ?: 1.0
        val yTop = size.height * top
        val yBot = size.height * bottom
        val pts = points.mapIndexed { i, v ->
            Offset(
                size.width * i / (points.size - 1),
                (yBot - ((v - min) / span) * (yBot - yTop)).toFloat(),
            )
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts.getOrElse(i - 1) { pts[i] }
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts.getOrElse(i + 2) { p2 }
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, brush = Brush.verticalGradient(0f to color.copy(alpha = 0.32f), 1f to color.copy(alpha = 0f)))
        // B2-1b (owner, 19 Nov 2026): the glow 1 dp narrower (6 to 5) and the line 1 dp thinner (2 to 1).
        drawPath(line, color = color.copy(alpha = 0.35f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, color = color, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
    }
}
