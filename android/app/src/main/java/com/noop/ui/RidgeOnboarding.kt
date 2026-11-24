package com.noop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.ui.ridge.RidgeButton
import com.noop.ui.ridge.RidgeEyebrow
import com.noop.ui.ridge.RidgeRowDivider

// MARK: - Ridge onboarding (R8a, H-01 / H-02, `Welcome.dc.html`, `Intro.dc.html`, `Onboarding.dc.html`)
//
// The Ridge first run: Welcome, the blurb, the aims, the three numbers and the plan, Terms, then the strap, history,
// about you, notifications, coach and first-nights steps (R8b). Terms comes before every step that asks for
// Bluetooth or notifications (D-09). The step is saved as it changes, so an interrupted run resumes where it was.

/** The Ridge first-run order. Terms precedes every permission step (D-09). */
internal val RIDGE_ONBOARDING_PAGES: List<OnboardingPage> = listOf(
    OnboardingPage.RidgeWelcome,
    OnboardingPage.RidgeIntro,
    OnboardingPage.RidgeAims,
    OnboardingPage.RidgeNumbers,
    OnboardingPage.RidgeSynthesis,
    OnboardingPage.RidgeTerms,
    OnboardingPage.RidgeStrap,
    OnboardingPage.Import,
    OnboardingPage.Profile,
    OnboardingPage.Notifications,
    OnboardingPage.RidgeCoach,
    OnboardingPage.RidgeFirstNights,
)

/** Where a run resumes: the saved step, never past Terms before acceptance, and past Terms once accepted. */
internal fun ridgeResumeIndex(saved: String?, pages: List<OnboardingPage>, termsAccepted: Boolean): Int {
    val found = pages.indexOfFirst { it.name == saved }
    val terms = pages.indexOf(OnboardingPage.RidgeTerms)
    // A step saved by an older build (R8a's v10 steps): restart at the top, or just past Terms once accepted.
    val i = if (found >= 0) found else if (termsAccepted && terms >= 0) terms + 1 else 0
    return when {
        terms < 0 -> i
        !termsAccepted && i > terms -> terms
        termsAccepted && i == terms -> terms + 1
        else -> i
    }
}

/** The next step: Terms is passed over once accepted, the bonded step when nothing is bonded. */
internal fun ridgeNextIndex(from: Int, pages: List<OnboardingPage>, termsAccepted: Boolean, bonded: Boolean): Int {
    var i = from + 1
    while (i < pages.lastIndex && ridgePassOver(pages[i], termsAccepted, bonded)) i++
    return i.coerceAtMost(pages.lastIndex)
}

/** The previous step, passing over the same steps as [ridgeNextIndex]. */
internal fun ridgeBackIndex(from: Int, pages: List<OnboardingPage>, termsAccepted: Boolean, bonded: Boolean): Int {
    var i = from - 1
    while (i > 0 && ridgePassOver(pages[i], termsAccepted, bonded)) i--
    return i.coerceAtLeast(0)
}

private fun ridgePassOver(page: OnboardingPage, termsAccepted: Boolean, bonded: Boolean): Boolean =
    (page == OnboardingPage.RidgeTerms && termsAccepted) || (page == OnboardingPage.Bonded && !bonded)

/** Skip is offered after Terms only (Terms cannot be skipped), and jumps to the last step. */
internal fun ridgeCanSkip(index: Int, pages: List<OnboardingPage>): Boolean =
    index > pages.indexOf(OnboardingPage.RidgeTerms) && index < pages.lastIndex

/** The steps the progress dots count: everything after Welcome and the blurb. */
private const val RIDGE_UNDOTTED = 2

/** The product name in the wizard's copy. A proper noun, English in every locale like `app_name`, which carries the
 *  build's launcher label instead ("Noop Ridge Demo"). */
internal const val RIDGE_BRAND = "Noop Ridge"

/** The saved step (resume after interruption, H-01). */
internal const val KEY_RIDGE_ONBOARDING_PAGE = "ridge.onboarding.page"

// MARK: - Shell

/**
 * The wizard frame: back arrow, progress dots and Skip along the top; the eyebrow, title and line; the step's content
 * scrolling between; the fixed 280 x 52 CTA at 72 dp above the gesture inset, in the same place on every step.
 * [photo] puts the step on the Welcome photograph; otherwise it sits on the ground with the brand glow at the top.
 */
@Composable
internal fun RidgeWizardShell(
    index: Int,
    total: Int,
    eyebrow: String?,
    title: String?,
    subtitle: String?,
    cta: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    ctaEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    photo: Boolean = false,
    note: String? = null,
    riseIn: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(Ridge.bg100)) {
        if (photo) RidgeWelcomePhoto(dim = eyebrow != null) else RidgeWizardGlow()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 28.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.l10n_onboarding_screen_back_b52b36b7),
                            tint = Ridge.ink,
                        )
                    }
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (index >= RIDGE_UNDOTTED) RidgeWizardDots(index - RIDGE_UNDOTTED, total - RIDGE_UNDOTTED)
                }
                if (onSkip != null) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.onb_ridge_skip), style = RidgeType.bodySm, color = Ridge.inkMuted)
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // B2-W: the photograph steps sit lower; Intro uses 40 dp side margins, Welcome 24 dp.
                    .padding(start = if (photo) (if (eyebrow == null) 24.dp else 40.dp) else 28.dp, end = if (photo) (if (eyebrow == null) 24.dp else 40.dp) else 28.dp, top = if (photo) 40.dp else 18.dp, bottom = 160.dp + navInset),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (eyebrow != null) {
                    if (photo) {
                        // Intro.dc.html: 12 sp semi-bold, 0.28 em spacing, cream at 80 %.
                        Text(
                            eyebrow.uppercase(),
                            style = RidgeType.eyebrow.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.28.em),
                            color = Ridge.ink.copy(alpha = 0.8f),
                        )
                    } else RidgeEyebrow(eyebrow)
                }
                if (title != null) {
                    Text(
                        title,
                        style = if (photo) RIDGE_WIZARD_TITLE.copy(fontSize = 30.sp, lineHeight = 37.sp, shadow = PHOTO_TITLE_SHADOW) else RIDGE_WIZARD_TITLE,
                        color = Ridge.ink, modifier = Modifier.semantics { heading() },
                    )
                }
                if (subtitle != null) Text(subtitle, style = RidgeType.body, color = Ridge.inkMuted)
                CompositionLocalProvider(LocalGlassCards provides true) { content() }
            }
        }
        if (!photo) {
            // Solid behind the whole CTA (its top is 124 dp above the inset), so a dimmed CTA never shows text through.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(210.dp + navInset)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Ridge.bg100)),
            )
        }
        RidgeWizardCta(cta, onCta, ctaEnabled, navInset, note, riseIn)
    }
}

@Composable
private fun BoxScope.RidgeWizardCta(cta: String, onCta: () -> Unit, enabled: Boolean, navInset: androidx.compose.ui.unit.Dp, note: String?, riseIn: Boolean = false) {
    // Welcome.dc.html's `np-rise`: the button and note rise in over about 400 ms (no waiting first).
    var shown by remember { mutableStateOf(!riseIn) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(if (shown) 1f else 0f, animationSpec = androidx.compose.animation.core.tween(400), label = "rise")
    val riseMod = Modifier.graphicsLayer { alpha = rise; translationY = (1f - rise) * 24.dp.toPx() }
    RidgeButton(
        text = cta, onClick = onCta, enabled = enabled, wizard = true,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp + navInset).then(riseMod),
    )
    if (note != null) {
        Text(
            note, style = RidgeType.bodySm, color = Ridge.ink.copy(alpha = 0.6f), textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 40.dp + navInset).then(riseMod),
        )
    }
}

/** Progress dots: the current step is a 22 dp bar, done steps brand, the rest faint. */
@Composable
private fun RidgeWizardDots(current: Int, count: Int) {
    val label = stringResource(R.string.onb_ridge_progress, current + 1, count)
    Row(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 6.dp, label = "dot")
            Box(
                Modifier
                    .size(width = w, height = 6.dp)
                    .background(if (i <= current) Ridge.brand else Ridge.glassStroke.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** The steps' ground: bg-100 with the board's brand glow at the top. */
@Composable
private fun RidgeWizardGlow() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                0f to Ridge.brand.copy(alpha = 0.12f), 0.7f to Color.Transparent,
                center = androidx.compose.ui.geometry.Offset(540f, 0f), radius = 1300f,
            ),
        ),
    )
}

/** The Welcome photograph (the board's `welcome-bg.jpg`) under its shading; [dim] adds the blurb's darker layer. */
@Composable
private fun RidgeWelcomePhoto(dim: Boolean) {
    Image(
        painterResource(R.drawable.ridge_welcome), contentDescription = null,
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Ridge.bg100.copy(alpha = 0.15f), 0.4f to Color.Transparent, 1f to Ridge.bg100.copy(alpha = 0.55f),
            ),
        ),
    )
    if (dim) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Ridge.bg000.copy(alpha = 0.55f), 0.55f to Ridge.bg000.copy(alpha = 0.15f), 1f to Ridge.bg000.copy(alpha = 0.6f),
                ),
            ),
        )
    }
}

private val RIDGE_WIZARD_TITLE = RidgeType.titleLg.copy(fontSize = 34.sp, lineHeight = 40.sp)

/** `text-shadow: 0 2px 20px rgba(10, 20, 30, .4)` on the Intro headline. */
private val PHOTO_TITLE_SHADOW = androidx.compose.ui.graphics.Shadow(
    color = Ridge.bg000.copy(alpha = 0.55f), offset = androidx.compose.ui.geometry.Offset(0f, 5f), blurRadius = 50f,
)

/** `text-shadow: 0 1px 12px rgba(10, 20, 30, .5)` from the Intro board. */
private val PHOTO_TEXT_SHADOW = androidx.compose.ui.graphics.Shadow(
    color = Ridge.bg000.copy(alpha = 0.7f), offset = androidx.compose.ui.geometry.Offset(0f, 3f), blurRadius = 32f,
)

// MARK: - Steps

/** 1 · Welcome (`Welcome.dc.html`): "Welcome to", the logo, the tagline; then Get started rises in. */
@Composable
internal fun RidgeWelcomeStep(total: Int, onGetStarted: () -> Unit) {
    RidgeWizardShell(
        index = 0, total = total, eyebrow = null, title = null, subtitle = null,
        cta = stringResource(R.string.onb_ridge_get_started), onCta = onGetStarted,
        photo = true, note = stringResource(R.string.onb_ridge_welcome_note), riseIn = true,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.onb_ridge_welcome_eyebrow),
                style = RidgeType.titleSm.copy(fontSize = 26.sp, lineHeight = 32.sp, shadow = PHOTO_TITLE_SHADOW),
                color = Ridge.ink.copy(alpha = 0.92f), textAlign = TextAlign.Center,
            )
            Image(
                painterResource(R.drawable.ridge_logo), contentDescription = RIDGE_BRAND,
                contentScale = ContentScale.FillWidth, modifier = Modifier.width(310.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.onb_ridge_welcome_tagline),
                style = RidgeType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = Ridge.ink, textAlign = TextAlign.Center,
            )
        }
    }
}

/** 2 · The blurb (`Intro.dc.html`). */
@Composable
internal fun RidgeIntroStep(total: Int, onBack: () -> Unit, onContinue: () -> Unit) {
    RidgeWizardShell(
        index = 1, total = total,
        eyebrow = stringResource(R.string.onb_ridge_step_of_label, 2, total, RIDGE_BRAND),
        title = stringResource(R.string.onb_ridge_intro_title), subtitle = null,
        cta = stringResource(R.string.onboarding_cta_continue), onCta = onContinue, onBack = onBack, photo = true,
    ) {
        // The board's text shadow keeps the copy legible over the sunlit part of the photograph.
        Text(
            stringResource(R.string.onb_ridge_intro_body),
            style = RidgeType.body.copy(fontSize = 16.sp, lineHeight = 25.sp, shadow = PHOTO_TEXT_SHADOW), color = Ridge.ink.copy(alpha = 0.88f),
        )
        Text(
            stringResource(R.string.onb_ridge_intro_tagline),
            style = RidgeType.listTitle.copy(fontSize = 16.sp, lineHeight = 24.sp, shadow = PHOTO_TEXT_SHADOW), color = Ridge.charge,
        )
    }
}

/**
 * Terms (H-02): v10's five points and four attestations, word for word (the clickwrap is legal text), in the board's
 * layout. Accept stays off until every attestation is ticked; [onAccept] saves the version and the time.
 */
@Composable
internal fun RidgeTermsStep(
    index: Int,
    total: Int,
    onAccept: () -> Unit,
    onBack: (() -> Unit)?,
    showStep: Boolean = true,
) {
    val checks = remember { mutableStateListOf(*Array(Terms.attestations.size) { false }) }
    RidgeWizardShell(
        index = index, total = total,
        eyebrow = if (showStep) {
            stringResource(R.string.onb_ridge_step_of_label, index + 1, total, stringResource(R.string.onb_ridge_terms_label))
        } else {
            stringResource(R.string.onb_ridge_terms_label)
        },
        title = stringResource(R.string.onb_ridge_terms_title),
        subtitle = stringResource(R.string.onb_ridge_terms_sub),
        cta = stringResource(R.string.terms_accept), onCta = onAccept, ctaEnabled = checks.all { it }, onBack = onBack,
    ) {
        NoopCard(padding = 18.dp) {
            Column {
                Terms.points.forEachIndexed { i, (head, body) ->
                    if (i > 0) RidgeRowDivider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(head), style = RidgeType.listTitle.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Ridge.ink)
                        Text(stringResource(body), style = RidgeType.bodySm, color = Ridge.inkMuted)
                    }
                }
            }
        }
        Text(stringResource(R.string.terms_attest_head), style = RidgeType.bodySm, color = Ridge.inkMuted)
        Column(verticalArrangement = Arrangement.spacedBy(Ridge.space1)) {
            Terms.attestations.forEachIndexed { i, res ->
                RidgeCheckRow(stringResource(res), checks[i]) { checks[i] = it }
            }
        }
        Text(stringResource(R.string.terms_footer), style = RidgeType.bodySm, color = Ridge.inkSubtle)
    }
}

/** A board checkbox: a 22 dp rounded box, brand-filled with a dark tick when on; the whole row toggles. */
@Composable
private fun RidgeCheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = Ridge.space2),
        horizontalArrangement = Arrangement.spacedBy(Ridge.space3),
    ) {
        val box = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(22.dp)
                .background(if (checked) Ridge.brand else Color.Transparent, box)
                .border(1.5.dp, if (checked) Ridge.brand else Ridge.inkMuted, box),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Ridge.inkOnLiquid, modifier = Modifier.size(14.dp))
        }
        Text(text, style = RidgeType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Ridge.ink, modifier = Modifier.weight(1f))
    }
}

/** An onboarded user re-prompted for new terms: the Terms step alone, with no back or progress. */
@Composable
internal fun RidgeTermsGate(onAccept: () -> Unit) {
    RidgeTermsStep(index = 0, total = 1, onAccept = onAccept, onBack = null, showStep = false)
}

/** v10's steps inside the Ridge shell: the eyebrow counts the step; the step draws its own title. */
@Composable
internal fun RidgeHostedStep(
    index: Int,
    total: Int,
    cta: String,
    onCta: () -> Unit,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
    @androidx.annotation.StringRes label: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    RidgeWizardShell(
        index = index, total = total,
        eyebrow = if (label != null) {
            stringResource(R.string.onb_ridge_step_of_label, index + 1, total, stringResource(label))
        } else {
            stringResource(R.string.onb_ridge_step_of, index + 1, total)
        },
        title = null, subtitle = null,
        cta = cta, onCta = onCta, onBack = onBack, onSkip = onSkip, content = content,
    )
}
