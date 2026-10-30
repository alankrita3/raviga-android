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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BodyText
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineNotice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import com.raviga.app.ui.components.PrivacyPromise
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.raviga.app.ui.components.Chip
import com.raviga.app.ui.components.ChipTone
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.tile
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
import com.raviga.app.util.Time

@Composable
fun QuoteScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: QuoteViewModel = viewModel(key = "quote_$projectId") { QuoteViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val quote = state.quote
    val config = state.config

    LaunchedEffect(state.submittedProjectId) {
        val id = state.submittedProjectId ?: return@LaunchedEffect
        nav.navigate(Routes.status(id)) { popUpTo(Routes.HOME); launchSingleTop = true }
    }

    ScreenScaffold(
        topBar = { RaTopBar(title = if (state.isResubmit) "New quote" else "Quote", onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (quote == null || state.quoting || state.rejected) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when {
                    state.needsNewQuote -> PrimaryButton("Get a new quote", onClick = { vm.requestQuote() })
                    state.shortBy > 0 -> PrimaryButton("Buy credits", onClick = { nav.navigate(Routes.CREDITS) })
                    state.isResubmit -> PrimaryButton(
                        when {
                            state.due > 0 -> "Resubmit and pay ${state.due} more ${if (state.due == 1) "credit" else "credits"}"
                            state.due < 0 -> "Resubmit and get ${-state.due} credits back"
                            else -> "Resubmit"
                        },
                        loading = state.submitting, onClick = { vm.submit() },
                    )
                    else -> PrimaryButton("Submit and pay ${quote.credits} credits", loading = state.submitting, onClick = { vm.submit() })
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.isResubmit) "Your brief locks again until the team replies." else "Private. We delete your brief from our servers after delivery.",
                        style = RaType.caption, color = Ink.ash,
                        textAlign = TextAlign.Center,
                    )
                }
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
            val bracket = config.bracket(quote.bracketId)
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
            Spacer(Modifier.height(14.dp))
            if (bracket != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(bracket.name, tone = ChipTone.Marigold)
                    if (bracket.blurb.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(bracket.blurb, style = RaType.secondary, color = Ink.graphite)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (state.isResubmit && state.alreadyCharged > 0) {
                Text(settlementLine(quote.credits, state.alreadyCharged), style = RaType.body, color = Ink.ink)
                Spacer(Modifier.height(20.dp))
            }
            if (state.shortBy > 0) {
                Text(
                    "You have ${Money.credits(state.balance)}. You need ${state.shortBy} more.",
                    style = RaType.body, color = Ink.ink,
                )
                Spacer(Modifier.height(20.dp))
            }
            if (state.needsNewQuote) {
                Text("The brief changed since this quote.", style = RaType.secondary, color = Ink.amber)
                Spacer(Modifier.height(20.dp))
            }

            val weeks = timelineText(quote.timeline?.minWeeks ?: 0, quote.timeline?.maxWeeks ?: 0, quote.estimatedWorkingDays)
            Row {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Ink.link, modifier = Modifier.padding(top = 3.dp).size(17.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Ready in $weeks after approval", style = RaType.bodyMedium, color = Ink.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(config.quote.timelineNote, style = RaType.secondary, color = Ink.graphite)
                    quote.expiresAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Quote valid until ${Time.shortDate(it)}.", style = RaType.caption, color = Ink.graphite)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            quote.complexity?.drivers?.takeIf { it.isNotEmpty() }?.let { drivers ->
                Hairline()
                Spacer(Modifier.height(20.dp))
                Text(complexitySentence(drivers), style = RaType.body, color = Ink.graphite)
                Spacer(Modifier.height(20.dp))
            }

            if (quote.breakdown.isNotEmpty()) {
                BreakdownTile(quote.breakdown.map { it.area to it.credits })
                Spacer(Modifier.height(20.dp))
            }

            if (quote.assumptions.isNotEmpty()) {
                Hairline()
                Spacer(Modifier.height(20.dp))
                Text("Assumes", style = RaType.bodyMedium, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                BodyText(quote.assumptions.joinToString("\n") { "- $it" })
                Spacer(Modifier.height(20.dp))
            }

            Hairline()
            Spacer(Modifier.height(20.dp))
            Text("What happens next", style = RaType.bodyMedium, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            NumberedSteps(nextSteps(config.revisions.includedRounds))
            Spacer(Modifier.height(20.dp))

            Hairline()
            Spacer(Modifier.height(20.dp))
            Text("Where to deliver", style = RaType.bodyMedium, color = Ink.ink)
            Spacer(Modifier.height(14.dp))
            Text("GitHub username", style = RaType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(6.dp))
            RaTextField(
                value = state.github,
                onValueChange = { vm.setGithub(it) },
                placeholder = "octocat",
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            Spacer(Modifier.height(6.dp))
            Text("We transfer the finished repository to this account.", style = RaType.caption, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            val aws = state.me?.deliveryTargets?.aws
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AWS account", style = RaType.secondary, color = Ink.graphite)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            aws == null -> "Not connected"
                            aws.verified -> "Connected, ${aws.accountId}"
                            else -> "${aws.accountId}, not verified yet"
                        },
                        style = RaType.body,
                        color = if (aws?.verified == true) Ink.moss else Ink.ink,
                    )
                }
                InlineAction(if (aws == null) "Connect AWS" else "Manage", onClick = { nav.navigate(Routes.CONNECT_AWS) })
            }
            Spacer(Modifier.height(6.dp))
            Text("Only needed if you want us to deploy the backend into your own AWS account.", style = RaType.caption, color = Ink.graphite)
            Spacer(Modifier.height(20.dp))
            PrivacyPromise()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** What the credits pay for: one bar split by area, then each area with its share. */
@Composable
private fun BreakdownTile(parts: List<Pair<String, Int>>) {
    val colors = listOf(Ink.ink, Ink.blue, Ink.marigold, Ink.ash, Ink.ruleStrong, Ink.moss)
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Column(Modifier.tile(padding = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEachIndexed { i, (_, credits) ->
                Box(
                    Modifier
                        .weight(credits.coerceAtLeast(1).toFloat() / total)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors[i % colors.size]),
                )
            }
        }
        parts.forEachIndexed { i, (area, credits) ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors[i % colors.size]))
                Spacer(Modifier.width(10.dp))
                Text(area, style = RaType.body, color = Ink.ink, modifier = Modifier.weight(1f))
                Text(Money.credits(credits), style = RaType.bodyMedium, color = Ink.ink)
            }
        }
    }
}

/** "You already paid 40 credits. This adds 8 credits." (same wording as iOS) */
internal fun settlementLine(newCredits: Int, alreadyPaid: Int): String {
    val diff = newCredits - alreadyPaid
    val paid = "You already paid ${Money.credits(alreadyPaid)}."
    return when {
        diff > 0 -> "$paid This adds ${Money.credits(diff)}."
        diff < 0 -> "$paid This returns ${Money.credits(-diff)} to your balance."
        else -> "$paid No change."
    }
}

/**
 * "Sized for payments, an Android app and two integrations." A leading capital is lowered unless
 * the word is a name: another capital inside it ("iOS app", "WhatsApp") or a platform or brand.
 */
internal fun complexitySentence(drivers: List<String>): String {
    val items = drivers.map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }
    if (items.isEmpty()) return ""
    val lowered = items.map { item ->
        val first = item.substringBefore(' ')
        val isName = first.drop(1).any(Char::isUpperCase) || first.lowercase() in NAMES
        if (item.first().isUpperCase() && !isName) item.replaceFirstChar { it.lowercase() } else item
    }
    return "Sized for " + joinWords(lowered) + "."
}

private val NAMES = setOf(
    "android", "apple", "google", "aws", "amazon", "stripe", "razorpay", "paypal", "shopify", "slack",
    "firebase", "whatsapp", "instagram", "facebook", "twilio", "microsoft", "windows", "mac", "macos",
    "openai", "salesforce", "hubspot", "zapier", "zoom", "upi", "sms", "api", "iphone", "ipad",
)

/** "payments, two platforms and an admin panel" */
private fun joinWords(words: List<String>): String = when (words.size) {
    0 -> ""
    1 -> words[0]
    else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
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

/** "What happens next" (same wording as iOS). */
internal fun nextSteps(includedRounds: Int): List<String> = listOf(
    "We review your brief within a working day.",
    "You get an approval, or comments to answer here.",
    "We build it and keep you posted on this screen.",
    "You get the code with a handover guide and steps to make it live. Accept it, or ask for a revision. " +
        (if (includedRounds == 1) "One round is" else "$includedRounds rounds are") + " included.",
)
