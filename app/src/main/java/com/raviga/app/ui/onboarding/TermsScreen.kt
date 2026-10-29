package com.raviga.app.ui.onboarding

import com.raviga.app.ui.status.StatusCopy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.raviga.app.ui.components.InlineAction
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
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import kotlinx.coroutines.launch

/** [next]: set for the first consent (R-056), the action the client tapped; Agree carries on there. */
@Composable
fun TermsScreen(nav: NavController, next: String? = null) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.session.config.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val legal = config.legal
    val me by container.session.me.collectAsStateWithLifecycle()
    // Agreed to an older version before: this is an update, and afterwards the client goes back to what they were doing.
    val updating = me?.consent?.terms != null
    // Contract v0.9: usage analytics is its own choice, off until the client turns it on.
    val analyticsGranted = me?.consent?.analyticsGranted == true
    var analyticsOn by remember(analyticsGranted) { mutableStateOf(analyticsGranted) }

    ScreenScaffold(
        bottomBar = {
            BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton(if (busy) "One moment" else "Agree and continue", enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            if (updating) {
                                container.session.acceptLegal()
                                if (analyticsOn != analyticsGranted) container.session.setAnalytics(analyticsOn)
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
                    "You agree to the terms of service and the privacy policy.",
                    style = RaType.caption, color = Ink.ash,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            if (updating) {
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
                "The brief you submit for us to build, and our comments on it. A random id for this phone. Your credit purchases and balance. A GitHub username and, if you add one, an AWS account id, so we can hand your project over.",
            )
            Block("Why", "To write your brief, quote it, build it and deliver it. We do not sell data or show ads.")
            Block(
                "What we don't keep",
                "Recordings and files never leave your phone. Drafts are stored only on your phone. To write or price a brief, its text is sent to our AI, used once, and not stored by us. When you submit, we keep the brief to review and build it, and delete it when you accept the delivery.",
            )
            Block(
                "What we won't build",
                "Anything made to harm, deceive or track people, or that breaks the law. Every description is checked automatically, passwords and ID numbers are removed, and a refused project is never charged.",
            )
            Block(
                "Your rights",
                "You can download or delete everything from Settings at any time, withdraw consent, and raise a grievance. Under India's DPDP Act you may also complain to the Data Protection Board after using our grievance process.",
            )
            // Contract v0.9, wording shared with iOS: its own block, the switch off until the client turns it on.
            Text("Usage analytics", style = RaType.bodyMedium, color = Ink.ink)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow usage analytics", style = RaType.body, color = Ink.ink)
                    Text(config.analytics.providers.joinToString(", "), style = RaType.secondary, color = Ink.graphite)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = analyticsOn, enabled = !busy, onCheckedChange = { analyticsOn = it }, colors = switchColors())
            }
            StatusCopy.grievanceLine(legal.grievance)?.let {
                Text(it, style = RaType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(20.dp))
            }
            Row(Modifier.padding(top = 4.dp)) {
                InlineAction("Privacy policy", onClick = { openLink(context, legal.privacyUrl) })
                Spacer(Modifier.width(12.dp))
                InlineAction("Terms of service", onClick = { openLink(context, legal.termsUrl) })
            }
            Spacer(Modifier.height(32.dp))
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
