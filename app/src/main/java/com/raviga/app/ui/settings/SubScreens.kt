package com.raviga.app.ui.settings

import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.Picture
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.raviga.app.ui.status.StatusCopy
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.push.Notifications
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.DestructiveButton
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.emailLink
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

@Composable
internal fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink.surface, checkedTrackColor = Ink.teal, checkedBorderColor = Ink.teal,
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
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val aws = state.me?.deliveryTargets?.aws

    ScreenScaffold(
        topBar = { RaTopBar(title = "Delivery targets", onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                InlineNotice(state.notice, Modifier.padding(bottom = 8.dp), color = Ink.moss)
                PrimaryButton("Save", loading = state.busy, onClick = { vm.saveGithub() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                Spacer(Modifier.height(8.dp))
                Text("Where finished projects go.", style = RaType.body, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
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
                Text("We transfer each finished repository to this account. GitHub emails you to accept it.", style = RaType.caption, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
            }
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            RaRow(
                title = "AWS account",
                subtitle = when {
                    aws == null -> "Optional. Connect it and we deploy there too."
                    aws.verified -> "Connected, ${aws.accountId}"
                    else -> "${aws.accountId}, not verified yet"
                },
                subtitleColor = if (aws?.verified == true) Ink.moss else Ink.graphite,
                onClick = { nav.navigate(Routes.CONNECT_AWS) },
            )
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Notifications.canPost(context)) }
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
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val legal = state.config.legal
    val aiOn = state.me?.consent?.aiGranted == true
    val analyticsOn = state.me?.consent?.analyticsGranted == true
    var confirmDelete by remember { mutableStateOf(false) }
    val saveExport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.saveExport(uri) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) nav.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
    }

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                SpotIllustration(Picture.Privacy, size = 180.dp, modifier = Modifier.offset(x = (-24).dp))
                Spacer(Modifier.height(20.dp))
                Text("Privacy and data", style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter, vertical = Ra.rowPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI processing", style = RaType.bodyMedium, color = Ink.ink)
                    Text(
                        if (aiOn) "On. Only the text of your notes and brief is sent, to write and price it: ${state.config.ai.providers.joinToString("; ").ifBlank { "our AI provider" }}." else "Off. Raviga cannot write briefs until this is on.",
                        style = RaType.secondary, color = Ink.graphite,
                    )
                }
                Switch(checked = aiOn, enabled = !state.busy, onCheckedChange = { vm.setAiProcessing(it) }, colors = switchColors())
            }
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            Row(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter, vertical = Ra.rowPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Contract v0.9, wording shared with iOS.
                    Text("Usage analytics", style = RaType.bodyMedium, color = Ink.ink)
                    Text(if (analyticsOn) "Allowed" else "Not allowed", style = RaType.body, color = Ink.ink)
                    Text(state.config.analytics.providers.joinToString(", "), style = RaType.secondary, color = Ink.graphite)
                }
                Switch(checked = analyticsOn, enabled = !state.busy, onCheckedChange = { vm.setAnalytics(it) }, colors = switchColors())
            }
            Hairline(Modifier.padding(horizontal = Ra.gutter))
            Column(Modifier.padding(horizontal = Ra.gutter)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recordings and files never leave this phone, and drafts are kept only here. A submitted brief is held by Raviga " +
                        "until you accept the delivery (or the project is cancelled), then deleted.",
                    style = RaType.caption, color = Ink.graphite,
                )
                Spacer(Modifier.height(24.dp))
            }
            RaRow(
                title = "Download my data",
                subtitle = state.exportMessage ?: "A zip of your submitted projects, the drafts on this phone, and your credit history",
                onClick = if (!state.busy) ({ vm.export() }) else null,
                chevron = false,
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
                            addStyle(SpanStyle(color = Ink.teal), at, at + email.length)
                        },
                        style = RaType.body, color = Ink.ink,
                        modifier = Modifier.clickable { emailLink(context, email, "Raviga grievance") }.padding(vertical = 4.dp),
                    )
                }
                // Contract v0.9.2: the line sits next to the privacy policy link, as on the consent screen.
                InlineAction("Privacy policy", onClick = { openLink(context, legal.privacyUrl) }, modifier = Modifier.padding(top = 4.dp))
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

@Composable
fun RecoveryScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var otherKey by remember { mutableStateOf("") }
    var confirmMove by remember { mutableStateOf(false) }

    LaunchedEffect(state.recoveryKey) { if (state.recoveryKey != null) vm.markRecoveryKeyShown() }
    LaunchedEffect(state.recovered) {
        if (state.recovered) nav.navigate(if (state.recoveredNeedsTerms) Routes.TERMS else Routes.HOME) { popUpTo(0) { inclusive = true } }
    }

    ScreenScaffold(topBar = { RaTopBar(title = "Move to another phone", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("There is no account. This key is the only way to get your submitted projects and credits back after a reinstall or on a new phone. Keep it somewhere safe.", style = RaType.body, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("Drafts stay on this phone: they are never sent anywhere, so a key can't move them.", style = RaType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(24.dp))
            SectionHeading("This phone's key")
            Spacer(Modifier.height(12.dp))
            val key = state.recoveryKey
            if (key != null) {
                Text(
                    key,
                    style = RaType.heading,
                    color = Ink.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ink.surface, RoundedCornerShape(Ra.fieldRadius))
                        .border(Ra.hairline, Ink.rule, RoundedCornerShape(Ra.fieldRadius))
                        .padding(Ra.fieldPadding),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    InlineAction("Copy", onClick = { clipboard.setText(AnnotatedString(key)) })
                    InlineAction("Issue a new key", enabled = !state.busy, onClick = { vm.issueRecoveryKey() })
                }
            } else {
                Text("No key on this phone yet.", style = RaType.body, color = Ink.graphite)
                Spacer(Modifier.height(8.dp))
                InlineAction("Issue a key", enabled = !state.busy, onClick = { vm.issueRecoveryKey() })
            }
            InlineNotice(state.notice, color = Ink.moss)
            Spacer(Modifier.height(Ra.sectionGap))
            SectionHeading("Bring projects here")
            Spacer(Modifier.height(12.dp))
            Text("Enter the key from your other phone. This phone then shows that phone's submitted projects and credits instead of its own. Drafts on this phone stay here.", style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(12.dp))
            RaTextField(value = otherKey, onValueChange = { otherKey = it }, placeholder = "rark_…", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
            InlineNotice(state.error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Move projects here", enabled = otherKey.isNotBlank() && !state.busy, loading = state.busy, onClick = { confirmMove = true })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            containerColor = Ink.surface,
            title = { Text("Switch to the other phone's projects?", style = RaType.heading, color = Ink.ink) },
            text = { Text("Anything created on this phone stays reachable only with this phone's key.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmMove = false; vm.recoverWith(otherKey) }) { Text("Switch", style = RaType.button, color = Ink.ink) } },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text("Keep this phone's", style = RaType.button, color = Ink.ink) } },
        )
    }
}
