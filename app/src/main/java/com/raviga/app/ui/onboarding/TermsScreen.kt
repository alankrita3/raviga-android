package com.raviga.app.ui.onboarding

import com.raviga.app.ui.status.StatusCopy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.raviga.app.ui.components.switchRow
import com.raviga.app.ui.components.LegalLinks
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.SpotIllustration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.raviga.app.ui.settings.switchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.nav.rememberIsOnTop
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import kotlinx.coroutines.launch

/**
 * "Before you start" (R-107, her decision): the terms and AI processing in one sheet and one tap.
 * [next]: set for the first consent (R-056), the action the client tapped; the tap carries on there.
 * [aiReask]: an account without AI consent (agreed before R-107) on its way to an AI action; the caller runs it after.
 */
@Composable
fun TermsScreen(nav: NavController, next: String? = null, aiReask: Boolean = false) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.session.config.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val legal = config.legal
    val me by container.session.me.collectAsStateWithLifecycle()
    // Agreed before: this is the re-ask (a newer version, or no AI consent yet); afterwards the client goes back.
    val updating = me?.consent?.terms != null
    val termsChanged = updating && container.session.needsLegal(me, config)
    val onTop = nav.rememberIsOnTop()
    // Contract v0.9: usage analytics is its own choice, off until the client turns it on.
    val analyticsGranted = me?.consent?.analyticsGranted == true
    var analyticsOn by remember(analyticsGranted) { mutableStateOf(analyticsGranted) }

    ScreenScaffold(
        bottomBar = {
            BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton(if (busy) "One moment" else ConsentCopy.BUTTON, enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            if (updating) {
                                // v0.11.4: the same one PUT as the first tap, analytics only when the switch changed it.
                                container.session.acceptLegal(analytics = analyticsOn.takeIf { it != analyticsGranted })
                            } else {
                                // R-056: register (or, for Restore, let recover() do it), then the consent.
                                container.session.agree(analytics = analyticsOn, restoring = next == Routes.RECOVERY)
                            }
                            container.syncAnalytics()
                            if (!updating) {
                                container.analytics.onboardingComplete()
                                container.push.identityChanged()
                            }
                        }
                            .onSuccess {
                                when {
                                    next != null -> nav.navigate(next) { popUpTo(Routes.CONSENT_FIRST) { inclusive = true } }
                                    // The caller runs the AI action it was on; if Back already left, don't pop the one under.
                                    aiReask -> if (onTop()) {
                                        nav.previousBackStackEntry?.savedStateHandle?.set("ai_consent_granted", true)
                                        nav.popBackStack()
                                    }
                                    nav.previousBackStackEntry != null -> nav.popBackStack()
                                    else -> nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                                }
                            }
                            .onFailure { error = it.userLine() }
                        busy = false
                    }
                })
                Spacer(Modifier.height(10.dp))
                Text(
                    ConsentCopy.CAPTION,
                    style = RaType.caption, color = Ink.ash,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                // On the first sheet and the AI-call re-ask; the updated-terms screen at launch has none (as iOS).
                if ((next != null || aiReask) && nav.previousBackStackEntry != null) {
                    TertiaryButton("Not now", enabled = !busy, onClick = { nav.popBackStack() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            if (termsChanged) {
                Spacer(Modifier.height(24.dp))
                Text("We've updated our terms", style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Our Terms and Privacy policy changed, including what Raviga can and can't build. Read them, then agree to carry on.",
                    style = RaType.body, color = Ink.graphite,
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(8.dp))
                SpotIllustration(Picture.Privacy, size = 170.dp, modifier = Modifier.offset(x = (-22).dp))
                Spacer(Modifier.height(20.dp))
                Text("Before you start", style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(20.dp))
            }
            Block(
                "What we collect",
                "The brief you submit for us to build, and any delivery reports you send. A random id for this phone. Your credit purchases and balance. Your AWS account id, so we can build your project there and deliver it.",
            )
            Block("Why", "To write your brief, quote it, build it and deliver it. We do not sell data or show ads.")
            Block("What we don't keep", StatusCopy.LOCAL_FIRST_PROMISE)
            Block(
                "What we won't build",
                "Anything made to harm, deceive or track people, or that breaks the law. Every description is checked automatically, passwords and ID numbers are removed, and a refused project is never charged.",
            )
            Block(
                "Your rights",
                "You can download or delete everything from Settings at any time, withdraw consent, and raise a grievance.",
            )
            // R-107: AI processing at the bottom, before the optional analytics switch; her words, no switch.
            AiSection()
            // Contract v0.9, wording shared with iOS: its own block, the switch off until the client turns it on.
            Text("Usage analytics", style = RaType.bodyMedium, color = Ink.ink)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp).switchRow(analyticsOn, enabled = !busy) { analyticsOn = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Allow usage analytics", style = RaType.body, color = Ink.ink)
                    Text(config.analytics.providers.joinToString(", "), style = RaType.secondary, color = Ink.graphite)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = analyticsOn, enabled = !busy, onCheckedChange = null, colors = switchColors())
            }
            StatusCopy.grievanceLine(legal.grievance)?.let {
                Text(it, style = RaType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(20.dp))
            }
            LegalLinks(
                listOfNotNull(
                    "Privacy policy" to { openLink(context, legal.privacyUrl) },
                    "Terms of Use" to { openLink(context, legal.termsUrl) },
                    // R-066 / contract v0.9.3: withdrawing consent, download and delete, the grievance route, the Board.
                    // A server without the key (older than v0.9.3) gets no link, as on iOS.
                    legal.rightsUrl.takeIf { it.isNotBlank() }?.let { url -> "Your rights" to { openLink(context, url) } },
                ),
                Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** R-107: "AI processing" and its four lines, each with an ink lead-in (as iOS's aiLine). */
@Composable
private fun AiSection() {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(ConsentCopy.AI_TITLE, style = RaType.bodyMedium, color = Ink.ink)
        ConsentCopy.AI_LINES.forEach { (lead, body) ->
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = RaType.secondaryMedium.fontWeight, color = Ink.ink)) { append(lead) }
                    append(" ")
                    append(body)
                },
                style = RaType.secondary, color = Ink.graphite,
            )
        }
    }
}

/** A heading and its paragraph in the notice. */
@Composable
private fun Block(title: String, body: String) {
    Text(title, style = RaType.bodyMedium, color = Ink.ink)
    Spacer(Modifier.height(6.dp))
    Text(body, style = RaType.secondary, color = Ink.graphite)
    Spacer(Modifier.height(20.dp))
}
