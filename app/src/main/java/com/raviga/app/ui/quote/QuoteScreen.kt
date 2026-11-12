package com.raviga.app.ui.quote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineNotice
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import com.raviga.app.ui.components.PrivacyPromise
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.util.Money
import com.raviga.app.ui.components.NumberedSteps
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

@Composable
fun QuoteScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: QuoteViewModel = viewModel(key = "quote_$projectId") { QuoteViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Back on the screen: a quote may have run out meanwhile.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.tick() }
    val quote = state.quote
    val config = state.config

    // AI consent round trip (R-056 order): opened right before the AI call, which carries on when it's allowed.
    val aiConsent = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("ai_consent_granted", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(aiConsent?.value) {
        if (aiConsent?.value == true) {
            nav.currentBackStackEntry?.savedStateHandle?.set("ai_consent_granted", false)
            vm.aiConsentGranted()
        }
    }
    LaunchedEffect(state.needsAiConsent) {
        if (state.needsAiConsent) {
            vm.aiConsentRequested()
            nav.navigate(Routes.AI_CONSENT)
        }
    }

    LaunchedEffect(state.submittedProjectId) {
        val id = state.submittedProjectId ?: return@LaunchedEffect
        nav.navigate(Routes.status(id)) { popUpTo(Routes.HOME); launchSingleTop = true }
    }

    // Review 2026-11-03 (Android): leaving cancels the submit in flight (the ViewModel's scope) after the server may
    // have charged it; the screen stays until the answer is in, as Capture does while it writes.
    androidx.activity.compose.BackHandler(enabled = state.submitting) {}

    ScreenScaffold(
        topBar = { RaTopBar(title = "Quote", onBack = { if (!state.submitting) nav.popBackStack() }) },
        bottomBar = {
            if (quote == null || state.quoting || state.rejected) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                if (state.needsNewQuote) {
                    PrimaryButton("Get a new quote", onClick = { vm.requestQuote() })
                } else {
                    // R-103 (contract v0.10.0; the PM's copy, as iOS): submitting is free; paying comes after acceptance.
                    Text(
                        FREE_TO_SUBMIT, style = RaType.caption, color = Ink.graphite, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    )
                    PrimaryButton("Submit", loading = state.submitting, onClick = { vm.submit() })
                }
                Spacer(Modifier.height(10.dp))
                // R-121 (her rule 2026-11-12, the PM's line as iOS): nothing about keeping or deleting the brief.
                LockedCaption(LOCK_LINE)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            if (state.quoting || (quote == null && state.error == null)) {
                Spacer(Modifier.height(24.dp))
                SpotIllustration(Picture.Writing, size = 180.dp, modifier = Modifier.offset(x = (-14).dp))
                Spacer(Modifier.height(8.dp))
                Text("Pricing your brief", style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Text(state.progressMessage ?: "Sizing the work", style = RaType.secondary, color = Ink.link)
                Spacer(Modifier.height(20.dp))
                ProgressRule(progress = state.progress?.takeIf { it > 0f })
                Spacer(Modifier.height(14.dp))
                Text("A quote takes about half a minute.", style = RaType.secondary, color = Ink.graphite)
                return@Column
            }
            if (state.rejected) {
                Spacer(Modifier.height(32.dp))
                Text("We can't take this project on", style = RaType.title, color = Ink.ink)
                InlineNotice(state.error ?: state.refusalReason, color = Ink.graphite)
                Spacer(Modifier.height(16.dp))
                TertiaryButton("Back to the brief", onClick = { nav.popBackStack() })
                return@Column
            }
            if (quote == null) {
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                TertiaryButton("Try again", onClick = { vm.requestQuote() })
                return@Column
            }
            val usd = quote.usd ?: (quote.credits * config.credits.creditValueUsd)

            Spacer(Modifier.height(4.dp))
            Illustration(Picture.Quote, height = 190.dp, radius = Ra.tileRadius)
            Spacer(Modifier.height(18.dp))
            val title = state.draft?.displayTitle.orEmpty()
            if (title.isNotBlank()) {
                Text(title, style = RaType.secondaryMedium, color = Ink.graphite)
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Money.credits(quote.credits), style = RaType.display, color = Ink.ink, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(12.dp))
                Text("about ${Money.usd(usd)}", style = RaType.secondary, color = Ink.graphite, modifier = Modifier.alignByBaseline())
            }
            // R-121 (contract v0.15.0, the PM's calls 2026-11-12): one price; no size chip.
            Spacer(Modifier.height(20.dp))
            state.staleLine?.let { line ->
                Text(line, style = RaType.secondary, color = Ink.amber)
                Spacer(Modifier.height(20.dp))
            }
            // R-097 #5: a submit already sent with this quote goes again exactly as it was.
            state.heldLine?.let { line ->
                Text(line, style = RaType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(20.dp))
            }

            val weeks = timelineText(quote.timeline?.minWeeks ?: 0, quote.timeline?.maxWeeks ?: 0, quote.estimatedWorkingDays)
            Row {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Ink.link, modifier = Modifier.padding(top = 3.dp).size(17.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Ready in $weeks after payment", style = RaType.bodyMedium, color = Ink.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(config.quote.timelineNote, style = RaType.secondary, color = Ink.graphite)
                }
            }
            Spacer(Modifier.height(20.dp))

            // R-121 (contract v0.15.0, her words 2026-11-12): no breakdown, no drivers line, no Assumes list (the
            // breakdown and drivers come empty; the free-fix line, the only assumption shown, is under the ownership line).
            Hairline()
            Spacer(Modifier.height(20.dp))
            Text("What happens next", style = RaType.bodyMedium, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            NumberedSteps(nextSteps())
            Spacer(Modifier.height(20.dp))

            // R-109 (B): nothing is asked before paying; "Where to deliver" is gone.
            PrivacyPromise()
            // R-100: the price's date, small and grey, at the end of the quote (as iOS a5f8fc9).
            state.priceLine?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, style = RaType.caption, color = Ink.ash)
            }
            // R-113 (her pick, 2026-11-10; as built on iOS): right below the validity line, same caption, always shown.
            Spacer(Modifier.height(if (state.priceLine != null) 8.dp else 20.dp))
            Text(OWNERSHIP_LINE, style = RaType.caption, color = Ink.ash)
            // R-121 (the PM's OK, as iOS): the free-fix line under the ownership line, since the assumptions aren't shown.
            Spacer(Modifier.height(8.dp))
            Text(freeFixesLine(config), style = RaType.caption, color = Ink.ash)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** "1 to 2 weeks", "about 3 weeks", "about a week" (never "about 1 weeks"). */
internal fun timelineText(minWeeks: Int, maxWeeks: Int, workingDays: Int): String {
    fun about(weeks: Int) = if (weeks <= 1) "about a week" else "about $weeks weeks"
    return when {
        minWeeks > 0 && maxWeeks > minWeeks -> "$minWeeks to $maxWeeks weeks"
        maxWeeks > 0 -> about(maxWeeks)
        else -> about((workingDays + 4) / 5)
    }
}

/** R-121 (her rule 2026-11-12: no keep or delete words; the PM's line, as iOS): under Submit, beside the lock. */
internal const val LOCK_LINE = "Private. Only our team sees your brief."

/** The free-fix rule under the ownership line (contract v0.13.0: `config.report.freeFixesLine`). */
internal fun freeFixesLine(config: com.raviga.app.data.api.AppConfig): String =
    config.report.freeFixesLine.ifBlank { com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE }

/** R-113 (her pick, 2026-11-10; also in How it works, step 3). */
internal const val OWNERSHIP_LINE = "Your published app is yours. The editable code stays with us, so we can keep improving it for you."

/** R-103: above Submit (the PM's final copy, as iOS). */
internal const val FREE_TO_SUBMIT = "Free to submit. You pay only once we accept it."

/**
 * "What happens next", contract v0.12.0 (R-109, B): the demo on the phone, then we publish it to the client's stores.
 * R-121 (the PM's call with her word "demo", 2026-11-12, as iOS): step 4 names no TestFlight or Google Play.
 */
internal fun nextSteps(): List<String> = listOf(
    "We check your brief against what we build.",
    "Once it's accepted, you pay the quoted credits and we start building.",
    "We build and test it. This screen shows each step.",
    "You get a demo on your phone to try. Approve it, tell us what's wrong, or ask for a change.",
    "Once you approve it, we publish it to your own App Store and Google Play accounts.",
)

/**
 * The lock line under the submit button (her review of the Play captures, 2026-11-03). One line, as on iOS: the lock
 * sits inline before the first word and the line is centred with it. A phone narrower than iOS's widest takes the
 * caption down half a point at a time to 12sp; if it still doesn't fit (a small phone or a large font setting) it wraps
 * into two balanced, centred lines rather than shrinking further. The old Row pushed the icon to the left edge while
 * the wrapped text centred in the rest.
 */
@Composable
private fun LockedCaption(text: String) {
    val lock = "lock"
    val annotated = androidx.compose.runtime.remember(text) {
        androidx.compose.ui.text.buildAnnotatedString { appendInlineContent(lock, "[lock]"); append(" "); append(text) }
    }
    val placeholder = androidx.compose.ui.text.Placeholder(12.sp, 12.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val oneLine = androidx.compose.runtime.remember(annotated, maxPx, density) {
            listOf(13f, 12.5f, 12f).firstOrNull { size ->
                measurer.measure(
                    annotated, RaType.caption.copy(fontSize = size.sp), softWrap = false, maxLines = 1,
                    placeholders = listOf(androidx.compose.ui.text.AnnotatedString.Range(placeholder, 0, "[lock]".length)),
                    density = density,
                ).size.width <= maxPx
            }
        }
        Text(
            text = annotated,
            inlineContent = mapOf(
                lock to androidx.compose.foundation.text.InlineTextContent(placeholder) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Ink.ash, modifier = Modifier.fillMaxSize())
                },
            ),
            style = if (oneLine != null) RaType.caption.copy(fontSize = oneLine.sp)
            else RaType.caption.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            maxLines = if (oneLine != null) 1 else Int.MAX_VALUE,
            color = Ink.ash,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

