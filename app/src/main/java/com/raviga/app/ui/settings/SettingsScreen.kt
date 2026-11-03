package com.raviga.app.ui.settings

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.local.DebugFlags
import com.raviga.app.push.Notifications
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.switchRow
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.onboarding.navigateAfterConsent
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val legal = state.config.legal
    val github = state.me?.deliveryTargets?.githubUsername.orEmpty()
    val aws = state.me?.deliveryTargets?.aws
    var notificationsOn by remember { mutableStateOf(Notifications.canPost(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { notificationsOn = Notifications.canPost(context) }

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text("Settings", style = RaType.title, color = Ink.ink, modifier = Modifier.padding(horizontal = Ra.gutter).padding(top = 8.dp, bottom = 20.dp))
            RaRow(
                title = "Delivery targets",
                subtitle = buildString {
                    append(if (github.isBlank()) "No GitHub username yet" else "GitHub @$github")
                    if (aws != null) append(", AWS ${if (aws.verified) "connected" else "not verified"}")
                },
                onClick = { nav.navigate(Routes.DELIVERY_TARGETS) },
            )
            RaRow(
                title = "Notifications",
                subtitle = if (notificationsOn && vm.pushConfigured) "On" else if (!vm.pushConfigured) "Not set up on this build" else "Off",
                onClick = { nav.navigate(Routes.NOTIFICATIONS) },
            )
            // R-079, as iOS: with an account, the key and link code, and the recovery email; without one, the way in.
            if (container.session.hasAccount) {
                RaRow(title = "Move to another phone", subtitle = "Recovery key, link code", onClick = { nav.navigate(Routes.RECOVERY) })
                RaRow(
                    title = "Recovery email",
                    subtitle = state.me?.recoveryEmail?.takeIf { it.verified }?.email ?: "Not set",
                    onClick = { nav.navigate(Routes.RECOVERY_EMAIL) },
                )
            } else {
                RaRow(
                    title = "Restore my account",
                    subtitle = "With a recovery key, recovery email or link code",
                    onClick = { nav.navigateAfterConsent(container.session.agreed, Routes.RECOVERY) },
                )
            }
            RaRow(
                title = "Privacy and data",
                subtitle = "AI processing, download or delete your data, questions or complaints",
                onClick = { nav.navigate(Routes.PRIVACY) },
            )
            RaRow(title = "Privacy policy", onClick = { openLink(context, legal.privacyUrl) }, chevron = false)
            RaRow(title = "Terms of service", onClick = { openLink(context, legal.termsUrl) }, chevron = false)
            // R-066: right after the legal rows, as on iOS.
            RaRow(title = "How Raviga works", onClick = { nav.navigate(Routes.HOW_IT_WORKS) })
            RaRow(title = "Fonts", subtitle = "Bricolage Grotesque and Figtree, open font licence", onClick = { nav.navigate(Routes.FONTS) })
            if (com.raviga.app.BuildConfig.DEBUG) DebugBackendRow(context, vm.isDemo)
            if (com.raviga.app.BuildConfig.DEBUG && vm.isDemo) {
                Spacer(Modifier.height(24.dp))
                Text("Demo controls", style = RaType.secondary, color = Ink.graphite, modifier = Modifier.padding(horizontal = Ra.gutter, vertical = 4.dp))
                RaRow(title = "Advance the latest project", subtitle = "Plays the team's next move now instead of on the timer", onClick = { vm.demoAdvance() }, chevron = false)
                RaRow(title = "Add 50 credits", subtitle = "No store purchase in demo mode", onClick = { vm.demoAddCredits() }, chevron = false)
                RaRow(title = "Reset demo data", subtitle = "Clears projects, credits and consent on this phone", titleColor = Ink.brick, onClick = { vm.demoReset() }, chevron = false)
                Column(Modifier.padding(horizontal = Ra.gutter)) {
                    com.raviga.app.ui.components.InlineNotice(state.notice, color = Ink.moss)
                    com.raviga.app.ui.components.InlineNotice(state.error)
                }
            }
            Text("About", style = RaType.heading, color = Ink.ink, modifier = Modifier.padding(horizontal = Ra.gutter).padding(top = 32.dp, bottom = 4.dp))
            RaRow(title = "Raviga", subtitle = vm.versionName + if (com.raviga.app.BuildConfig.DEBUG && vm.isDemo) ", demo backend on this phone" else "")
            RaRow(title = legal.companyName)
            val clientId = state.me?.clientId
            if (legal.supportEmail.isNotBlank()) RaRow(
                title = "Support",
                subtitle = legal.supportEmail,
                subtitleColor = Ink.link,
                chevron = false,
                onClick = { com.raviga.app.ui.emailLink(context, legal.supportEmail, "Raviga support" + (clientId?.let { " ($it)" } ?: "")) },
            )
            // Support asks for this; long-press copies it. Review #20: TalkBack offers the same as "double-tap and hold to
            // copy" (a raw pointer gesture alone was out of its reach).
            clientId?.let { id ->
                val copy = com.raviga.app.ui.components.rememberCopier()
                RaRow(
                    title = "Support ID",
                    subtitle = id,
                    modifier = Modifier
                        .pointerInput(id) { detectTapGestures(onLongPress = { copy(id, false) }) }
                        .semantics { onLongClick(label = "Copy") { copy(id, false); true } },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Debug builds only: flip between the dev API and the in-app demo backend. Takes effect on next launch. */
@Composable
private fun DebugBackendRow(context: Context, isDemo: Boolean) {
    var useDemo by remember { mutableStateOf(DebugFlags.useDemoBackend(context)) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().switchRow(useDemo) { useDemo = it; DebugFlags.setUseDemoBackend(context, it) }
                .padding(horizontal = Ra.gutter, vertical = Ra.rowPadding + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Demo backend (debug)", style = RaType.bodyMedium, color = Ink.ink)
                Text(
                    if (useDemo != isDemo) "Restart the app to apply" else if (isDemo) "Using the in-app demo backend" else "Using the Raviga dev API",
                    style = RaType.secondary, color = if (useDemo != isDemo) Ink.amber else Ink.graphite,
                )
            }
            Switch(
                checked = useDemo,
                onCheckedChange = null,
                colors = switchColors(),
            )
        }
        Hairline(Modifier.padding(horizontal = Ra.gutter))
    }
}
