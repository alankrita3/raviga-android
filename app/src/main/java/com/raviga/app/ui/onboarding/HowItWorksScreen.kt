package com.raviga.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.Chip
import com.raviga.app.ui.components.ChipTone
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType

/**
 * R-113 item 6 (her yes, 2026-11-11): "How it works", the client flow she approved (client-flow-approved-2026-11-11.html),
 * word for word, as a native screen, built to iOS docs/walkthrough-bundle-2026-11-10.md item 6. Entry points: Home's
 * "See how it works" before the first project, "How it works" under the project timeline, and Settings' "How Raviga
 * works". Not in onboarding. Sentence case throughout (the app has no all-caps labels).
 */
object HowItWorks {
    data class Choice(val label: String, val detail: String, val free: Boolean = false)

    data class Step(
        val who: String,
        val title: String,
        val body: String,
        val note: String? = null,
        /** "Our turn": the tinted card and a blue circle; "Your turn" (and "You, then we") the plain card. */
        val ours: Boolean = false,
        val choices: List<Choice> = emptyList(),
    )

    const val LABEL = "How Raviga works"
    const val TITLE = "From your idea to your app in the stores"
    const val LEDE =
        "Eight steps. You describe what you want, we build it, you try the demo on your own phone, and it goes live under your name."
    const val YOUR_TURN = "Your turn"
    const val OUR_TURN = "Our turn"
    const val KEY_SPOKEN = "Your turn: plain cards. Our turn: tinted cards."
    const val FIXED_FREE = "Fixed free"

    /** The PM's words for the entry points. */
    const val HOME_CARD = "See how it works"
    const val TIMELINE_LINK = "How it works"

    /** Step 8's days come from /config.report.freeFixDays (7 gives her text exactly). */
    fun steps(freeFixDays: Int = 7) = listOf(
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
            "For ${if (freeFixDays == 1) "1 day" else "$freeFixDays days"} after launch, bugs and anything that doesn't match the brief are still fixed free.",
            note = "Want something new later? Request a change any time. You always see the price before you pay.",
            ours = true,
        ),
    )

    val steps = steps()

    /** TalkBack, one element per step: "Step N of 8. {who}. {title}. {text} {note}", then step 6's choices. */
    fun spoken(number: Int, total: Int, step: Step): String = buildString {
        append("Step $number of $total. ${step.who}. ${step.title}. ${step.body}")
        step.note?.let { append(" ").append(it) }
        step.choices.forEach { c ->
            append(" ").append(c.label)
            if (c.free) append(", ").append(FIXED_FREE)
            append(": ").append(c.detail)
        }
    }
}

@Composable
fun HowItWorksScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val config by container.session.config.collectAsStateWithLifecycle()
    val steps = HowItWorks.steps(config.report.freeFixDays.takeIf { it > 0 } ?: 7)
    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text(HowItWorks.LABEL, style = RaType.secondaryMedium, color = Ink.link)
            Spacer(Modifier.height(8.dp))
            Text(HowItWorks.TITLE, style = RaType.title, color = Ink.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Text(HowItWorks.LEDE, style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = HowItWorks.KEY_SPOKEN },
            ) {
                Key(HowItWorks.YOUR_TURN, ours = false)
                Key(HowItWorks.OUR_TURN, ours = true)
            }
            Spacer(Modifier.height(22.dp))
            steps.forEachIndexed { i, step -> StepRow(i + 1, steps.size, step, last = i == steps.lastIndex) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Key(label: String, ours: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(4.dp)
        Box(Modifier.size(14.dp).clip(shape).then(if (ours) Modifier.background(Ink.blueWash) else Modifier.background(Ink.surface).border(1.dp, Ink.rule, shape)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = RaType.caption, color = Ink.graphite)
    }
}

/** A numbered path: a 40dp circle per step (ink for "You", link blue for "We"), joined by a dashed rule to the next. */
@Composable
private fun StepRow(number: Int, total: Int, step: HowItWorks.Step, last: Boolean) {
    val shape = RoundedCornerShape(Ra.tileRadius)
    Row(Modifier.height(IntrinsicSize.Min).clearAndSetSemantics { contentDescription = HowItWorks.spoken(number, total, step) }) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (step.ours) Ink.link else Ink.ink), contentAlignment = Alignment.Center) {
                Text("$number", style = RaType.heading, color = if (step.ours) Color.White else Ink.paper)
            }
            if (!last) {
                val rule = Ink.ruleStrong
                Box(
                    Modifier.weight(1f).width(2.dp).drawBehind {
                        drawLine(rule, Offset(size.width / 2, 4.dp.toPx()), Offset(size.width / 2, size.height - 4.dp.toPx()),
                            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
                    },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .then(if (step.ours) Modifier.background(Ink.blueWash) else Modifier.background(Ink.surface).border(1.dp, Ink.rule, shape))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                val accent = if (step.ours) Ink.blueInk else Ink.ash
                Text(step.who, style = RaType.secondaryMedium, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(step.title, style = RaType.heading, color = Ink.ink)
                Spacer(Modifier.height(6.dp))
                Text(step.body, style = RaType.secondary, color = Ink.ink)
                step.note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = RaType.secondary, color = if (step.ours) Ink.blueInk else Ink.graphite)
                }
                step.choices.forEach { choice ->
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink.paper).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(choice.label, style = RaType.bodyMedium, color = Ink.ink)
                            if (choice.free) {
                                Spacer(Modifier.width(8.dp))
                                Chip(HowItWorks.FIXED_FREE, tone = ChipTone.Marigold)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(choice.detail, style = RaType.secondary, color = Ink.ash)
                    }
                }
            }
        }
    }
}
