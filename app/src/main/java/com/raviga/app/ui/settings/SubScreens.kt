package com.raviga.app.ui.settings

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import com.raviga.app.ui.components.switchRow
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.Picture
import androidx.compose.foundation.layout.offset
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.raviga.app.ui.status.StatusCopy
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.push.Notifications
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.LegalLinks
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.emailLink
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

@Composable
internal fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink.surface, checkedTrackColor = Ink.link, checkedBorderColor = Ink.link,
    uncheckedThumbColor = Ink.surface, uncheckedTrackColor = Ink.ruleStrong, uncheckedBorderColor = Ink.ruleStrong,
)

/** The bundled fonts and their licence (SIL Open Font License 1.1, shipped in assets/licenses). */
@Composable
fun FontsScreen(nav: NavController) {
    val context = LocalContext.current
    val licence = remember { runCatching { context.assets.open("licenses/OFL.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("Fonts", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "Raviga uses Bricolage Grotesque and Figtree, both under the SIL Open Font License 1.1.",
                style = RaType.body, color = Ink.graphite,
            )
            Spacer(Modifier.height(20.dp))
            Text(licence, style = RaType.caption, color = Ink.graphite)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeliveryTargetsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val aws = state.me?.deliveryTargets?.aws

    // R-106 (contract v0.11.0; PM-approved copy as iOS): no GitHub. The client's AWS account is the only target.
    ScreenScaffold(topBar = { RaTopBar(title = "AWS account", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                "Your project is built, delivered and run here.", style = RaType.body, color = Ink.graphite,
                modifier = Modifier.padding(horizontal = Ra.gutter).padding(top = 8.dp, bottom = 16.dp),
            )
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            RaRow(
                title = "AWS account",
                subtitle = when {
                    aws == null -> "Not connected yet"
                    aws.verified -> "Connected, ${aws.accountId}"
                    else -> "${aws.accountId}, not verified yet"
                },
                subtitleColor = if (aws?.verified == true) Ink.moss else Ink.graphite,
                onClick = { nav.navigate(Routes.CONNECT_AWS) },
            )
            Text(
                com.raviga.app.ui.quote.AWS_CAPTION, style = RaType.caption, color = Ink.graphite,
                modifier = Modifier.padding(horizontal = Ra.gutter).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container, createSavedStateHandle()) }
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Notifications.canPost(context)) }
    // Back from system settings: show what the phone says now.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = Notifications.canPost(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    ScreenScaffold(topBar = { RaTopBar(title = "Notifications", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("We send one when the team replies, approves, or delivers, and when a brief or quote is ready. Nothing else.", style = RaType.body, color = Ink.ink)
            Spacer(Modifier.height(24.dp))
            Text(
                when {
                    !vm.pushConfigured -> "Push is not set up on this build yet."
                    granted -> "Notifications are on."
                    else -> "Notifications are off."
                },
                style = RaType.body, color = if (granted && vm.pushConfigured) Ink.moss else Ink.amber,
            )
            Spacer(Modifier.height(24.dp))
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PrimaryButton("Allow notifications", onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) })
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton("Open system settings", onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(intent) }
            })
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val legal = state.config.legal
    val aiOn = state.me?.consent?.aiGranted == true
    val analyticsOn = state.me?.consent?.analyticsGranted == true
    var confirmDelete by remember { mutableStateOf(false) }
    val saveExport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.saveExport(uri) }

    // R-097 #7: after a deletion the root restarts the app at Welcome (AppEvent.SignedOut), not this screen.

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                SpotIllustration(Picture.Privacy, size = 180.dp, modifier = Modifier.offset(x = (-24).dp))
                Spacer(Modifier.height(20.dp))
                Text("Privacy and data", style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().switchRow(aiOn, enabled = !state.busy) { vm.setAiProcessing(it) }
                    .padding(horizontal = Ra.gutter, vertical = Ra.rowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI processing", style = RaType.bodyMedium, color = Ink.ink)
                    Text(
                        if (aiOn) "On. Only the text of your notes and brief is sent, to write and price it: ${state.config.ai.providers.joinToString("; ").ifBlank { "our AI provider" }}." else "Off. Raviga cannot write briefs until this is on.",
                        style = RaType.secondary, color = Ink.graphite,
                    )
                }
                Switch(checked = aiOn, enabled = !state.busy, onCheckedChange = null, colors = switchColors())
            }
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            Row(
                Modifier.fillMaxWidth().switchRow(analyticsOn, enabled = !state.busy) { vm.setAnalytics(it) }
                    .padding(horizontal = Ra.gutter, vertical = Ra.rowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // Contract v0.9, wording shared with iOS.
                    Text("Usage analytics", style = RaType.bodyMedium, color = Ink.ink)
                    Text(if (analyticsOn) "Allowed" else "Not allowed", style = RaType.body, color = Ink.ink)
                    Text(state.config.analytics.providers.joinToString(", "), style = RaType.secondary, color = Ink.graphite)
                }
                Switch(checked = analyticsOn, enabled = !state.busy, onCheckedChange = null, colors = switchColors())
            }
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                Spacer(Modifier.height(16.dp))
                // R-066: the consent screen's words, so both say the same.
                Text("What we don't keep", style = RaType.bodyMedium, color = Ink.ink)
                Spacer(Modifier.height(6.dp))
                Text(StatusCopy.LOCAL_FIRST_PROMISE, style = RaType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
            }
            RaRow(
                title = "Download my data",
                // The drafts are named in the caption below (her copy), so the row names what comes from our servers.
                subtitle = state.exportMessage ?: "A zip of your submitted projects and your credit history",
                onClick = if (!state.busy) ({ vm.export() }) else null,
                chevron = false,
                hairline = false,
            )
            if (state.exportMessage != null) ProgressRule(Modifier.padding(horizontal = Ra.gutter))
            state.exportUrl?.let {
                Column(Modifier.padding(horizontal = Ra.gutter)) {
                    InlineNotice(state.notice, color = Ink.moss)
                    InlineAction("Save the file", enabled = !state.busy, onClick = {
                        saveExport.launch("Raviga data ${java.time.LocalDate.now()}.zip")
                    })
                }
            }
            if (state.exportUrl == null && state.notice == "Saved.") {
                InlineNotice(state.notice, Modifier.padding(horizontal = Ra.gutter), color = Ink.moss)
            }
            Text(
                StatusCopy.EXPORT_DRAFTS_CAPTION,
                style = RaType.secondary, color = Ink.graphite,
                modifier = Modifier.padding(start = Ra.gutter, end = Ra.gutter, top = 4.dp, bottom = Ra.rowPadding + 2.dp),
            )
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            RaRow(
                title = "Delete my data",
                subtitle = "Drafts on this phone, submitted projects and this phone's link. Credits history is kept anonymised, as the law requires.",
                titleColor = Ink.brick,
                onClick = if (!state.busy) ({ confirmDelete = true }) else null,
                chevron = false,
            )
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                InlineNotice(state.error)
                Spacer(Modifier.height(Ra.sectionGap))
                StatusCopy.grievanceLine(legal.grievance)?.let { line ->
                    val email = legal.grievance.email
                    Text(
                        buildAnnotatedString {
                            append(line)
                            val at = line.indexOf(email)
                            addStyle(SpanStyle(color = Ink.link), at, at + email.length)
                        },
                        style = RaType.body, color = Ink.ink,
                        modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { emailLink(context, email, "Raviga grievance") }.padding(vertical = 4.dp),
                    )
                }
                // Contract v0.9.2: the line sits next to the privacy policy link, as on the consent screen; v0.9.3 adds
                // the "Your rights" page beside it.
                LegalLinks(
                    listOfNotNull(
                        "Privacy policy" to { openLink(context, legal.privacyUrl) },
                        legal.rightsUrl.takeIf { it.isNotBlank() }?.let { url -> "Your rights" to { openLink(context, url) } },
                    ),
                    Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Ink.surface,
            title = { Text("Delete everything?", style = RaType.heading, color = Ink.ink) },
            text = {
                Text(
                    "Your drafts on this phone are deleted now. Submitted projects are deleted after ${state.config.retention.deletionGraceDays} days; recovering with your key before then cancels it. Active projects must be finished or cancelled first.",
                    style = RaType.body, color = Ink.graphite,
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEverything() }) { Text("Delete", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = RaType.button, color = Ink.ink) } },
        )
    }
}
