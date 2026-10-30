package com.raviga.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.ChipRows
import com.raviga.app.ui.components.ChipSpec
import com.raviga.app.ui.components.HeroIllustration
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.Wordmark
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import kotlinx.coroutines.launch

/** One step of the flow on Welcome: its picture, a title and one line. */
private data class Step(val title: String, val body: String, val picture: Picture)

private val steps = listOf(
    Step(
        "Describe it your way",
        "Say it, type it, or upload a document you already have. We turn it into a clear brief you can edit.",
        Picture.Describe,
    ),
    Step("See the price first", "You get a quote and a timeline before anything starts. Nothing is charged until you submit.", Picture.Quote),
    // The founder's exact wording (iOS 489412c); don't edit it without her approval.
    Step(
        "Built by AI, checked by experts",
        "AI builds your project at speed. Experts recommend the right tech stack, or use yours, and review the work before it reaches you.",
        Picture.AIBuild,
    ),
    Step(
        "Ready before you know it",
        "A working project, ready for your review, with the code, a handover guide and simple steps to make it live.",
        Picture.Delivered,
    ),
)

/**
 * First launch. Page one says what Raviga does; four more show the flow with its pictures (describe,
 * see the price, built by AI and checked by experts, ready), as on iOS. Who-builds copy exists only here,
 * in the founder's approved words.
 *
 * R-066 (founder, 2026-10-30): "Get started" goes to step 1; steps 1 to 3 have "Next" and a small "Skip";
 * step 4's "Start" closes onboarding. Swiping still works. [replay] is Settings > How Raviga works: the same
 * five pages, and closing them goes back to Settings.
 */
@Composable
fun WelcomeScreen(nav: NavController, replay: Boolean = false) {
    val container = LocalAppContainer.current
    val config by container.session.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { steps.size + 1 })
    val last = steps.size
    val finish: () -> Unit = {
        scope.launch {
            if (replay) {
                nav.popBackStack()
            } else {
                container.prefs.setWelcomeDone()
                val me = container.session.me.value
                // R-056: a first-time client goes straight to Home; only updated terms ask here.
                val next = if (container.session.agreed && container.session.needsLegal(me, container.session.config.value)) Routes.TERMS else Routes.HOME
                nav.navigate(next) { popUpTo(Routes.WELCOME) { inclusive = true } }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.paper)) {
        // Heights count from the top of the screen: the pictures run under the status bar.
        val hero = (maxHeight * 0.46f).coerceIn(240.dp, 440.dp)
        val picture = (maxHeight * 0.34f).coerceIn(200.dp, 320.dp)
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page == 0) Intro(hero) else StepPage(steps[page - 1], number = page, pictureHeight = picture)
            }
            PageDots(
                count = steps.size + 1,
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 12.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(horizontal = Ra.gutter)
                    .padding(bottom = 8.dp),
            ) {
                val page = pager.currentPage
                PrimaryButton(
                    when (page) {
                        0 -> "Get started"
                        last -> "Start"
                        else -> "Next"
                    },
                    onClick = { if (page == last) finish() else scope.launch { pager.animateScrollToPage(page + 1) } },
                )
                Spacer(Modifier.height(4.dp))
                // One fixed-height line under the button, so the pages never shift: "Skip" wherever there is a "Next".
                Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                    if (page in 1 until last) {
                        InlineAction("Skip", onClick = finish, color = Ink.graphite)
                    } else {
                        Text(
                            "By ${config.legal.companyName}",
                            style = RaType.caption,
                            color = Ink.ash,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The picture gives way to the words: the text and the four promises are measured first and the
 * picture takes what is left of the page (between [MIN_HERO] and [heroMax]), so nothing hides
 * below the page dots on a short phone. Taller text than that (large font settings) scrolls.
 */
@Composable
private fun Intro(heroMax: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val page = constraints.maxHeight
        SubcomposeLayout(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { c ->
            val width = Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth)
            val words = subcompose("words") { IntroWords() }.map { it.measure(width) }
            val wordsHeight = words.sumOf { it.height }
            val hero = (page - wordsHeight).coerceIn(MIN_HERO.roundToPx(), heroMax.roundToPx())
            // R-066: the floating brief's top corner sits 89 px down the picture at 1080 px wide, so only the top of
            // the picture (focus 0) keeps it below the status bar; a higher focus crops more off the top and lifts it under.
            val picture = subcompose("hero") { HeroIllustration(Picture.Welcome, height = hero.toDp(), focusY = 0f) }.map { it.measure(width) }
            layout(c.maxWidth, picture.sumOf { it.height } + wordsHeight) {
                var y = 0
                (picture + words).forEach { it.place(0, y); y += it.height }
            }
        }
    }
}

private val MIN_HERO = 200.dp

@Composable
private fun IntroWords() {
    Column(Modifier.padding(horizontal = Ra.gutterWide).padding(top = 26.dp, bottom = 16.dp)) {
        Wordmark(Modifier.height(23.dp))
        Spacer(Modifier.height(16.dp))   // the 14dp stack spacing plus 2dp, as on iOS
        Text("Say what you want built.", style = RaType.hero, color = Ink.ink)
        Spacer(Modifier.height(14.dp))
        Text(
            "Describe it in your own words. We write the brief, quote it and build it.",
            style = RaType.body,
            color = Ink.graphite,
        )
        Spacer(Modifier.height(18.dp))
        // Two fixed rows in the founder's order; on a narrow phone the text shrinks a little instead of wrapping.
        ChipRows(
            listOf(
                listOf(ChipSpec("You approve the quote", dot = Ink.marigold), ChipSpec("Super fast delivery", icon = Icons.Outlined.Bolt)),
                listOf(ChipSpec("Working project delivered", icon = Icons.Outlined.Check), ChipSpec("Code handed to you", dot = Ink.blue)),
            ),
        )
    }
}

@Composable
private fun StepPage(step: Step, number: Int, pictureHeight: Dp) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ra.gutterWide)
            .padding(top = statusBar + 24.dp, bottom = 16.dp),
    ) {
        Illustration(step.picture, height = pictureHeight, radius = Ra.tileRadius)
        Spacer(Modifier.height(28.dp))
        Text("Step $number of ${steps.size}", style = RaType.secondaryMedium, color = Ink.link)
        Spacer(Modifier.height(8.dp))
        Text(step.title, style = RaType.hero, color = Ink.ink)
        Spacer(Modifier.height(12.dp))
        Text(step.body, style = RaType.body, color = Ink.graphite)
    }
}

/** Where you are in a short sequence: the current page is a blue pill, the rest small dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val on = index == current
            val width by animateDpAsState(if (on) 18.dp else 6.dp, tween(200), label = "dot")
            Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(if (on) Ink.blue else Ink.ruleStrong))
        }
    }
}
