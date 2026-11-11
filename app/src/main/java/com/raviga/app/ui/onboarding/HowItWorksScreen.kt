package com.raviga.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.raviga.app.ui.components.Chip
import com.raviga.app.ui.components.ChipTone
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType

/**
 * R-113 item 6 (her yes, 2026-11-11): "How it works", the client-facing flow she approved ("Your App Journey",
 * client-flow-approved-2026-11-11.html), word for word, as a native screen. Entry points: Home before the first
 * project, a link by the project timeline, and Settings. Not in onboarding.
 */
object HowItWorks {
    data class Choice(val label: String, val detail: String, val free: Boolean = false)

    data class Step(
        val who: String,
        val title: String,
        val body: String,
        val note: String? = null,
        val ours: Boolean = false,
        val choices: List<Choice> = emptyList(),
    )

    const val EYEBROW = "How Raviga works"
    const val TITLE = "From your idea to your app in the stores"
    const val LEDE =
        "Eight steps. You describe what you want, we build it, you try the demo on your own phone, and it goes live under your name."
    const val YOUR_TURN = "Your turn"
    const val OUR_TURN = "Our turn"
    const val FIXED_FREE = "Fixed free"

    val steps = listOf(
        Step("You", "Tell us your idea", "Type it, say it out loud, or add files. A few sentences are enough to start."),
        Step(
            "We", "We write your brief",
            "Your idea becomes a clear plan: the screens, the features and what the app does. Read it and change anything that isn't right.",
            ours = true,
        ),
        Step(
            "We", "You see the price", "One price in credits, with what it covers and how long it's valid.",
            note = "Your published app is yours. The editable code stays with us, so we can keep improving it for you.",
            ours = true,
        ),
        Step(
            "You", "Submit, then pay when we accept",
            "Our team reviews your brief. You pay only once we accept it, in credits you buy through the App Store or Google Play.",
        ),
        Step(
            "We", "We build your app", "You'll see the progress and an estimated delivery date.",
            note = "One thing from you while we build: the email addresses where we should send your demo invites.",
            ours = true,
        ),
        Step(
            "You", "Try the demo on your phone",
            "Install the test version on your iPhone or Android phone and use it like the real thing. Then tell us one of three things.",
            choices = listOf(
                Choice("It works, approved", "We move on to going live."),
                Choice("Something's wrong", "A bug, or it doesn't match the brief. We fix it and send a new demo.", free = true),
                Choice("Request a change", "Something new. You get a price in credits first, and decide then."),
            ),
        ),
        Step(
            "You, then we", "Go live under your name",
            "Your app goes into your own App Store and Google Play accounts. You create the accounts and invite us; we publish and handle the store reviews.",
            note = "Apple and Google charge their own account fees. If your app needs a backend, you connect your AWS account once and we set it up there.",
        ),
        Step(
            "We", "Your app is live",
            "For 7 days after launch, bugs and anything that doesn't match the brief are still fixed free.",
            note = "Want something new later? Request a change any time. You always see the price before you pay.",
            ours = true,
        ),
    )
}

@Composable
fun HowItWorksScreen(nav: NavController) {
    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text(HowItWorks.EYEBROW.uppercase(), style = RaType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = Ink.link)
            Spacer(Modifier.height(8.dp))
            Text(HowItWorks.TITLE, style = RaType.title, color = Ink.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Text(HowItWorks.LEDE, style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Key(HowItWorks.YOUR_TURN, ours = false)
                Key(HowItWorks.OUR_TURN, ours = true)
            }
            Spacer(Modifier.height(22.dp))
            HowItWorks.steps.forEachIndexed { i, step ->
                StepRow(i + 1, step)
                Spacer(Modifier.height(Ra.tileGap))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Key(label: String, ours: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).then(cardLook(ours)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = RaType.caption, color = Ink.graphite)
    }
}

/** "Your turn" is a white tile with its rule; "Our turn" the blue wash, no border (as the approved page). */
private fun cardLook(ours: Boolean): Modifier =
    if (ours) Modifier.background(Ink.blueWash) else Modifier.background(Ink.surface).border(1.dp, Ink.rule, RoundedCornerShape(4.dp))

@Composable
private fun StepRow(number: Int, step: HowItWorks.Step) {
    val shape = RoundedCornerShape(Ra.tileRadius)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 14.dp).size(28.dp).clip(CircleShape).background(Ink.ink), contentAlignment = Alignment.Center) {
            Text("$number", style = RaType.chip, color = Ink.surface)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).clip(shape)
                .then(if (step.ours) Modifier.background(Ink.blueWash) else Modifier.background(Ink.surface).border(1.dp, Ink.rule, shape))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val accent = if (step.ours) Ink.blueInk else Ink.graphite
            Text(step.who.uppercase(), style = RaType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = accent)
            Spacer(Modifier.height(4.dp))
            Text(step.title, style = RaType.heading, color = Ink.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(step.body, style = RaType.secondary, color = Ink.ink)
            step.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = RaType.secondary, color = accent)
            }
            step.choices.forEach { choice ->
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(choice.label, style = RaType.bodyMedium, color = Ink.ink)
                        if (choice.free) {
                            Spacer(Modifier.width(8.dp))
                            Chip(HowItWorks.FIXED_FREE.uppercase(), tone = ChipTone.Marigold)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(choice.detail, style = RaType.secondary, color = Ink.graphite)
                }
            }
        }
    }
}
