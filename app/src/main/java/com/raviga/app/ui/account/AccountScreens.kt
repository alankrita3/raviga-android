package com.raviga.app.ui.account

import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.findActivity
import com.raviga.app.ui.account.MovePhoneViewModel.Restore
import com.raviga.app.ui.account.MovePhoneViewModel.RestoreWay
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.components.rememberCopier
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.util.Time

/**
 * Settings > Move to another phone (R-079, as iOS's MoveToAnotherPhoneView): the recovery key, a link code, "this is
 * the new phone" and the phones with access. With no account on this phone it is "Restore my account": only the way in.
 */
@Composable
fun MoveToAnotherPhoneScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: MovePhoneViewModel = viewModel { MovePhoneViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        val done = state.done ?: return@LaunchedEffect
        nav.navigate(if (done == MovePhoneViewModel.Done.TERMS) Routes.TERMS else Routes.HOME) { popUpTo(0) { inclusive = true } }
    }

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            if (state.hasAccount) {
                Text("Move to another phone", style = RaType.title, color = Ink.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Text(
                    "Your projects belong to this phone, not to an account. Keep the recovery key somewhere safe so a lost phone never " +
                        "means lost work. The key moves submitted projects and credits; drafts never leave this phone, so submit them first.",
                    style = RaType.secondary, color = Ink.graphite,
                )
                Spacer(Modifier.height(28.dp))
                RecoveryKeySection(state, vm)
                LinkSection(state, vm)
                NewPhoneSection(state, vm)
                DevicesSection(state, vm)
            } else {
                // No client on this phone yet: only the way in.
                Text("Restore my account", style = RaType.title, color = Ink.ink, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                NewPhoneSection(state, vm)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    state.signingOut?.let { device ->
        ConfirmDialog(
            title = if (device.isCurrent) "Sign out this phone?" else "Sign out ${PhoneNames.of(device)}?",
            text = if (device.isCurrent) {
                "This phone loses access until you restore it with your recovery key or email. Drafts stay on it."
            } else {
                "That phone loses access to your projects and credits. It can come back with your recovery key or email."
            },
            confirm = "Sign out",
            confirmColor = Ink.brick,
            dismiss = "Keep",
            onConfirm = { vm.signOut() },
            onDismiss = { vm.askSignOut(null) },
        )
    }
    if (state.confirmNewKey) {
        ConfirmDialog(
            title = "Make a new recovery key?",
            text = "Your old key stops working the moment the new one is made.",
            confirm = "Make a new key",
            dismiss = "Cancel",
            onConfirm = { vm.makeNewKey() },
            onDismiss = { vm.askNewKey(false) },
        )
    }
    if (state.pendingRestore != null) {
        ConfirmDialog(
            title = "Replace the projects on this phone?",
            // R-092 #16 (iOS review): with an account here, say what it takes to come back to it.
            text = RestoreWarning.text(state.hasAccount),
            confirm = "Replace and restore",
            confirmColor = Ink.brick,
            dismiss = "Cancel",
            onConfirm = { vm.restore() },
            onDismiss = { vm.askRestore(null) },
        )
    }
}

@Composable
private fun RecoveryKeySection(state: MovePhoneViewModel.State, vm: MovePhoneViewModel) {
    val copy = rememberCopier()
    // R-092 #14 (iOS review): once the key has been shown in full, the screen stays out of Recents' snapshot and screen
    // recordings until it closes (R-094 P2-14: kept after Hide too, so the last frame with the key is never captured).
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    var revealed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (state.keyVisible) revealed = true
    androidx.compose.runtime.DisposableEffect(revealed, activity) {
        val window = activity?.window
        if (revealed) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // R-094 P2-14 (iOS review 2): leaving the app (Home, Recents, another app) masks the key again.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { vm.hideKey() }
    SectionHeading("Your recovery key")
    Spacer(Modifier.height(10.dp))
    val key = state.recoveryKey
    if (key != null) {
        Text(
            if (state.keyVisible) key else RecoveryKeyFormat.mask(key),
            style = RaType.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
            color = Ink.ink,
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink.surface, RoundedCornerShape(Ra.fieldRadius))
                .border(Ra.hairline, Ink.rule, RoundedCornerShape(Ra.fieldRadius))
                .padding(Ra.fieldPadding),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InlineAction(if (state.keyVisible) "Hide" else "Show", onClick = { vm.toggleKey() }, modifier = Edge)
            Spacer(Modifier.width(12.dp))
            InlineAction(if (state.copied) "Copied" else "Copy", onClick = { copy(key, true); vm.copied() })
            Spacer(Modifier.weight(1f))
            InlineAction(if (state.keyBusy) "Making" else "Make a new key", enabled = !state.keyBusy, onClick = { vm.askNewKey(true) }, modifier = Modifier.offset(x = 8.dp))
        }
    } else {
        Text("No key on this phone yet.", style = RaType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(6.dp))
        // R-092 #12: the account may have a key on another phone, and making one here stops that one: the same confirm.
        InlineAction(if (state.keyBusy) "Making" else "Make a key", enabled = !state.keyBusy, onClick = { vm.askNewKey(true) }, modifier = Edge)
    }
    Spacer(Modifier.height(8.dp))
    Text("Anyone with this key can open your projects. Keep it private.", style = RaType.caption, color = Ink.ash)
    InlineNotice(state.keyError)
    SectionGap()
}

@Composable
private fun LinkSection(state: MovePhoneViewModel.State, vm: MovePhoneViewModel) {
    SectionHeading("Link a new phone")
    Spacer(Modifier.height(6.dp))
    Text("Install Raviga on the other phone, open Settings there, and enter this code. Single use.", style = RaType.secondary, color = Ink.graphite)
    state.linkCode?.let { link ->
        Spacer(Modifier.height(14.dp))
        Text(link.code, style = RaType.display, color = Ink.ink)
        Text(
            link.expiresAt?.let { Time.clock(it) }?.takeIf { it.isNotBlank() }?.let { "Expires at $it" } ?: "Expires in 10 minutes",
            style = RaType.caption, color = Ink.ash,
        )
    }
    Spacer(Modifier.height(6.dp))
    InlineAction(
        when {
            state.linkBusy -> "Getting a code"
            state.linkCode == null -> "Get a code"
            else -> "Get a new code"
        },
        enabled = !state.linkBusy,
        onClick = { vm.getLinkCode() },
        modifier = Edge,
    )
    InlineNotice(state.linkError)
    SectionGap()
}

@Composable
private fun NewPhoneSection(state: MovePhoneViewModel.State, vm: MovePhoneViewModel) {
    if (state.hasAccount) {
        SectionHeading("This is the new phone")
        Spacer(Modifier.height(6.dp))
    }
    Text("Restore with the recovery key from your old phone, or with a link code it just made.", style = RaType.secondary, color = Ink.graphite)
    if (!state.hasAccount) {
        // QA N4: on a fresh install, say before the restore that drafts don't come with it (local-first).
        Spacer(Modifier.height(8.dp))
        Text(
            "Submitted projects and credits come back. Drafts stay on the phone they were written on.",
            style = RaType.secondary, color = Ink.graphite,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Edge) {
        RestoreChoice("Use recovery key", selected = state.restoreWay == RestoreWay.KEY) { vm.setRestoreWay(RestoreWay.KEY) }
        Spacer(Modifier.width(12.dp))
        RestoreChoice("Use recovery email", selected = state.restoreWay == RestoreWay.EMAIL) { vm.setRestoreWay(RestoreWay.EMAIL) }
    }
    Spacer(Modifier.height(8.dp))
    if (state.restoreWay == RestoreWay.KEY) {
        RaTextField(
            value = state.enteredKey,
            onValueChange = vm::setEnteredKey,
            placeholder = "Recovery key",
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(4.dp))
        InlineAction("Restore with key", enabled = state.keyReady, onClick = { vm.askRestore(Restore.Key(state.enteredKey.trim())) }, modifier = Edge)
        Spacer(Modifier.height(14.dp))
    } else {
        EmailRestore(state, vm)
    }
    RaTextField(
        value = state.enteredCode,
        onValueChange = vm::setEnteredCode,
        placeholder = "Link code",
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
    )
    Spacer(Modifier.height(4.dp))
    InlineAction("Link", enabled = state.codeReady, onClick = { vm.askRestore(Restore.Code(state.enteredCode.trim())) }, modifier = Edge)
    if (state.restoreBusy) {
        Spacer(Modifier.height(10.dp))
        Text("Restoring", style = RaType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(8.dp))
        ProgressRule()
    }
    InlineNotice(state.restoreError)
    if (state.hasAccount) SectionGap()
}

/** Back in with the recovery email: the screen never says whether the email is on an account. */
@Composable
private fun EmailRestore(state: MovePhoneViewModel.State, vm: MovePhoneViewModel) {
    val sentTo = state.codeSentTo
    if (sentTo != null) {
        Text("If that email is on an account, we've sent a code.", style = RaType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(8.dp))
        Text("Enter the 6-digit code we sent to $sentTo", style = RaType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(8.dp))
        RaTextField(
            value = state.restoreOtp,
            onValueChange = vm::setRestoreOtp,
            placeholder = "6-digit code",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(4.dp))
        InlineAction("Confirm", enabled = state.otpReady, onClick = { vm.askRestore(Restore.Email(sentTo, state.restoreOtp)) }, modifier = Edge)
    } else {
        Text("Enter the email you added in Raviga", style = RaType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(8.dp))
        RaTextField(
            value = state.restoreEmail,
            onValueChange = vm::setRestoreEmail,
            placeholder = "Email",
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(4.dp))
        InlineAction(if (state.restoreBusy) "Sending" else "Send code", enabled = state.emailReady, onClick = { vm.sendRestoreCode() }, modifier = Edge)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun RestoreChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    InlineAction(text, onClick = onClick, color = if (selected) Ink.link else Ink.ash, modifier = Modifier.semantics { this.selected = selected })
}

@Composable
private fun DevicesSection(state: MovePhoneViewModel.State, vm: MovePhoneViewModel) {
    if (state.devices.isEmpty()) return
    SectionHeading("Phones with access")
    Spacer(Modifier.height(4.dp))
    state.devices.forEach { device ->
        RaRow(
            title = PhoneNames.of(device),
            subtitle = device.lastSeenAt?.let { Time.relative(it) }?.takeIf { it.isNotBlank() }?.let { "Last used $it" },
            horizontalPadding = 0.dp,
            trailing = { InlineAction("Sign out", enabled = !state.deviceBusy, color = Ink.brick, onClick = { vm.askSignOut(device) }, modifier = Modifier.offset(x = 8.dp)) },
        )
    }
    InlineNotice(state.deviceError)
}

/** Text actions have 8dp of padding inside; this lines their words up with the text above (as Credits does). */
private val Edge = Modifier.offset(x = (-8).dp)

@Composable
private fun SectionGap() = Hairline(Modifier.padding(vertical = 24.dp))

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Ink.ink,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.surface,
        title = { Text(title, style = RaType.heading, color = Ink.ink) },
        text = { Text(text, style = RaType.body, color = Ink.graphite) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, style = RaType.button, color = confirmColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, style = RaType.button, color = Ink.ink) } },
    )
}

/** Settings > Recovery email (R-079, iOS's RecoveryEmailView words). */
@Composable
fun RecoveryEmailScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: RecoveryEmailViewModel = viewModel { RecoveryEmailViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val saved = state.saved
    val sentTo = state.sentTo

    ScreenScaffold(
        topBar = { RaTopBar(onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (sentTo != null) {
                BottomBar { PrimaryButton(if (state.busy) "Checking" else "Confirm", enabled = state.canConfirm, onClick = { vm.confirm() }) }
            } else if (saved == null) {
                BottomBar { PrimaryButton(if (state.busy) "Sending" else "Send code", enabled = state.canSend, onClick = { vm.send() }) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Text("Recovery email", style = RaType.title, color = Ink.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text(
                "If you lose this phone and your recovery key, this email gets you back in. We use it for nothing else.",
                style = RaType.secondary, color = Ink.graphite,
            )
            Spacer(Modifier.height(24.dp))
            if (state.confirmed) {
                Text("Recovery email confirmed", style = RaType.bodyMedium, color = Ink.moss)
                Spacer(Modifier.height(16.dp))
            }
            when {
                saved != null && sentTo == null -> {
                    RaRow(title = saved, horizontalPadding = 0.dp)
                    Spacer(Modifier.height(8.dp))
                    InlineAction("Remove recovery email", enabled = !state.busy, color = Ink.brick, onClick = { vm.askRemove(true) }, modifier = Edge)
                }
                sentTo != null -> {
                    Text("Enter the 6-digit code we sent to $sentTo", style = RaType.secondary, color = Ink.graphite)
                    Spacer(Modifier.height(8.dp))
                    RaTextField(
                        value = state.otp,
                        onValueChange = vm::setOtp,
                        placeholder = "6-digit code",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
                else -> RaTextField(
                    value = state.email,
                    onValueChange = vm::setEmail,
                    placeholder = "Email",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                )
            }
            InlineNotice(state.error)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.confirmRemove) {
        ConfirmDialog(
            title = "Remove this email?",
            text = "You'd have only your recovery key to get back in.",
            confirm = "Remove",
            confirmColor = Ink.brick,
            dismiss = "Keep",
            onConfirm = { vm.remove() },
            onDismiss = { vm.askRemove(false) },
        )
    }
}

/** After the first purchase that added credits, once per install (R-079, iOS's ProtectCreditsSheet words). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectCreditsSheet(onAdd: () -> Unit, onNotNow: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = sheet,
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter).padding(top = 32.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text("Protect your credits", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(12.dp))
            Text("Add a recovery email so you can get your credits back if you lose this phone.", style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Add email", onClick = onAdd)
            Spacer(Modifier.height(10.dp))
            TertiaryButton("Not now", onClick = onNotNow, color = Ink.graphite)
        }
    }
}
