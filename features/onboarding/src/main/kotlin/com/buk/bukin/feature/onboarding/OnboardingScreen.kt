package com.buk.bukin.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.buk.bukin.designsystem.R
// Strings come from the one shared file in :core:designsystem; the Lottie assets are this
// module's own, so they resolve through this module's R.
import com.buk.bukin.feature.onboarding.R as OnboardingR
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.animationsEnabled
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.component.rememberBukHaptics
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukBlueDeep
import com.buk.bukin.designsystem.theme.BukBorder
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukMotion
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.designsystem.theme.bukGutter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Four steps.
 *
 * The first three keep their copy **verbatim** — it was argued for in session 1, screen
 * three is deliberately honest about what v1 does, and it explains permissions without
 * requesting them. Step four is the name question, inline.
 *
 * That fourth step is why this exists in its current shape. First run used to be three
 * separate surfaces with a hard cut between each — onboarding, then name entry, then the
 * role picker — so a person set the app up by being handed between screens. Absorbing the
 * name question makes it one flow, and the progress indicator finally measures something
 * real.
 *
 * **Step four has no Skip.** Skip stays on steps 1–3, because those are explanation. The
 * app cannot function without a name, and a skippable step that then blocks you is worse
 * than no skip at all.
 *
 * @param nameStep supplied by `:app`, which owns identity. A feature module cannot reach
 *   `NameEntryViewModel`, and duplicating the call that issues an identity would be the
 *   worse of the two ways to solve that.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    nameStep: @Composable (onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    OnboardingScreen(
        onFinished = {
            viewModel.markSeen()
            onFinished()
        },
        nameStep = nameStep,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    nameStep: @Composable (onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { StepCount })
    val scope = rememberCoroutineScope()
    val haptics = rememberBukHaptics()
    val nameStepIndex = StepCount - 1
    val onNameStep = pagerState.currentPage == nameStepIndex

    // Settle, not scroll: a tick per frame of a drag would be noise.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { haptics.pageSettled() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BukField)
            .padding(horizontal = bukGutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BukMinTouchTarget),
        ) {
            // Skip means "skip the explanation", not "skip setting the app up", so it lands
            // on the name step rather than past it.
            if (!onNameStep) {
                val label = stringResource(R.string.onboarding_skip)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = BukInkMuted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(BukShape.full)
                        .bukPressable(
                            onClick = { scope.launch { pagerState.animateScrollToPage(nameStepIndex) } },
                            onClickLabel = label,
                        )
                        .padding(horizontal = BukSpacing.sm2, vertical = BukSpacing.sm2),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // The name step is a form; letting a stray horizontal swipe carry someone off
            // a text field they are typing into is worse than the flick being available.
            userScrollEnabled = !onNameStep,
        ) { page ->
            if (page == nameStepIndex) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.nombre_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = BukInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(BukSpacing.sm))
                    Text(
                        text = stringResource(R.string.nombre_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BukInkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(BukSpacing.xl))
                    nameStep(onFinished)
                }
            } else {
                StoryPage(page = page, pagerState = pagerState)
            }
        }

        PageIndicator(
            current = pagerState.currentPage,
            count = StepCount,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(BukSpacing.md2))

        // The name step supplies its own submit button, so this one steps aside for it
        // rather than sitting underneath a second call to action.
        if (!onNameStep) {
            val label = stringResource(R.string.onboarding_next)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(BukShape.lg)
                    .background(BukBlue)
                    .bukPressable(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        onClickLabel = label,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        } else {
            Spacer(Modifier.height(ButtonHeight))
        }

        Spacer(Modifier.height(BukSpacing.lg))
    }
}

/** One of the three explanation pages: an animation, a headline, a paragraph. */
@Composable
private fun StoryPage(page: Int, pagerState: PagerState) {
    val story = Stories[page]
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StoryAnimation(
            story = story,
            modifier = Modifier
                .size(IllustrationSize)
                .graphicsLayer {
                    // Frame-rate value, read inside the layer block. Reading
                    // `currentPageOffsetFraction` in composition recomposes the page on
                    // every pixel of a drag, which is the single most likely place in this
                    // app to hide a jank source.
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    translationX = -offset * size.width * ParallaxFactor
                },
        )
        Spacer(Modifier.height(BukSpacing.xl))
        Text(
            text = stringResource(story.title),
            style = MaterialTheme.typography.headlineLarge,
            color = BukInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BukSpacing.md))
        Text(
            text = stringResource(story.body),
            style = MaterialTheme.typography.bodyLarge,
            color = BukInkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The bundled animation, recoloured from the theme.
 *
 * **Nothing reads a colour out of the JSON.** Every paint is overridden at runtime through
 * `LottieDynamicProperties`, which is what keeps `Color.kt` the single source of colour and
 * what stops three third-party files from looking like three third-party files. The
 * keypaths are per-layer rather than a blanket `"**"` because two of these animations carry
 * a knocked-out glyph that has to stay light against the shape behind it — flattening
 * everything to one brand colour would erase it.
 */
@Composable
private fun StoryAnimation(story: Story, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(story.raw))
    // Read once. `animationsEnabled()` is an observed value, and letting it flip from its
    // initial emission mid-composition restarted the clip, which is half of the flicker.
    val motion = animationsEnabled()
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        // These clips are 1–2.6s at source speed and loop with a hard cut, so they read as
        // hurried and twitchy on a page a person is reading. Slowed, and never restarted
        // from zero on recomposition.
        speed = LoopSpeed,
        restartOnPlay = false,
        isPlaying = motion,
    )

    val properties = rememberLottieDynamicProperties(
        *story.paints.flatMap { paint ->
            listOf(
                rememberLottieDynamicProperty(
                    property = LottieProperty.COLOR,
                    value = paint.color().toArgb(),
                    keyPath = arrayOf(paint.layer, "**"),
                ),
                rememberLottieDynamicProperty(
                    property = LottieProperty.STROKE_COLOR,
                    value = paint.color().toArgb(),
                    keyPath = arrayOf(paint.layer, "**"),
                ),
            )
        }.toTypedArray(),
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = properties,
        modifier = modifier,
    )
}

@Composable
private fun PageIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(BukSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            // A spring, not a default tween — the indicator is the one piece of chrome
            // that moves on every page turn, so it sets the tone for everything else.
            val width by animateDpAsState(
                targetValue = if (selected) DotWide else DotSmall,
                animationSpec = BukMotion.dpDefault,
                label = "dotWidth",
            )
            Box(
                Modifier
                    .size(width = width, height = DotSmall)
                    .clip(BukShape.full)
                    .background(if (selected) BukBlue else BukBorder),
            )
        }
    }
}

/** One explanation page. */
private class Story(val title: Int, val body: Int, val raw: Int, val paints: List<Paint>)

/**
 * A layer in the animation and the token its paint comes from.
 *
 * The colour is a lambda so it is read inside composition against the current theme rather
 * than captured into a top-level `val` at class-load time.
 */
private class Paint(val layer: String, val color: () -> Color)

private val Stories = listOf(
    // "Tu asistencia, en un toque" — a hand tapping. One flat colour throughout.
    Story(
        title = R.string.onboarding_one_title,
        body = R.string.onboarding_one_body,
        raw = OnboardingR.raw.onboarding_uno,
        paints = listOf(
            Paint("hand") { BukBlue },
            Paint("touch1") { BukBlue },
            Paint("touch2") { BukBlue },
        ),
    ),
    // "Tu teléfono encuentra a tu anfitrión" — a Bluetooth glyph inside expanding rings,
    // which is the app's own halo idea arriving one screen early.
    Story(
        title = R.string.onboarding_two_title,
        body = R.string.onboarding_two_body,
        raw = OnboardingR.raw.onboarding_dos,
        paints = listOf(
            Paint("bg_cover") { BukBlue },
            Paint("bg_cover_shadow") { BukBlueDeep },
            // Knocked out of the disc. Stays light, or it disappears into it.
            Paint("bluetooth") { Color.White },
            Paint("wave01") { BukBlue },
            Paint("wave02") { BukBlue },
            Paint("wave03") { BukBlue },
        ),
    ),
    // "Por ahora, registramos tu entrada" — a check landing in a list.
    Story(
        title = R.string.onboarding_three_title,
        body = R.string.onboarding_three_body,
        raw = OnboardingR.raw.onboarding_tres,
        paints = listOf(
            Paint("Cricle") { BukSuccessInk },
            Paint("Checklist") { Color.White },
            Paint("Line") { BukSuccessInk.copy(alpha = 0.45f) },
            Paint("Shadow") { BukSuccessInk.copy(alpha = 0.10f) },
        ),
    ),
)

private const val StepCount = 4
private val IllustrationSize = 200.dp
private val ButtonHeight = 56.dp
private val DotSmall = 8.dp
private val DotWide = 22.dp
private const val ParallaxFactor = 0.35f

/** Slow enough that the loop reads as ambient rather than as a stutter. */
private const val LoopSpeed = 0.5f

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun OnboardingPreview() {
    BukInTheme {
        OnboardingScreen(onFinished = {}, nameStep = {})
    }
}
