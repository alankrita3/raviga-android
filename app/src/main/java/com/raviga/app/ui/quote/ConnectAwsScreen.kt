package com.raviga.app.ui.quote

import com.raviga.app.ui.components.rememberCopier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.AwsConnectInfo
import com.raviga.app.data.api.AwsTarget
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.DestructiveButton
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.tile
import androidx.compose.foundation.layout.offset
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.KeyValueRow
import com.raviga.app.ui.components.NumberedSteps
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import com.raviga.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectAwsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val accountId: String = "",
        val target: AwsTarget? = null,
        val info: AwsConnectInfo? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val disconnected: Boolean = false,
    ) {
        val saved get() = target != null && target.accountId == accountId.filter { it.isDigit() }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val aws = container.session.me.value?.deliveryTargets?.aws
        _state.update { it.copy(accountId = aws?.accountId.orEmpty(), target = aws) }
        if (aws != null) loadInfo()
    }

    fun setAccountId(v: String) = _state.update { it.copy(accountId = v.filter { c -> c.isDigit() }.take(12), error = null, notice = null) }

    fun save() {
        val id = _state.value.accountId
        if (id.length != 12) { _state.update { it.copy(error = "An AWS account ID is 12 digits.") }; return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { container.session.setDeliveryTargets(id) }
                .onSuccess { t -> _state.update { it.copy(target = t.aws) }; loadInfo() }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun loadInfo() {
        viewModelScope.launch {
            runCatching { container.session.awsConnect() }
                .onSuccess { info -> _state.update { it.copy(info = info) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    fun verify() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { container.session.awsVerify() }
                .onSuccess { t -> _state.update { it.copy(target = t, notice = if (t.verified) "Connected. We can deploy to this account." else t.lastError) } }
                .onFailure { e ->
                    val reason = (e as? ApiException)?.takeIf { it.code == ApiException.AWS_ROLE_NOT_READY }?.detailString("reason")
                    _state.update { it.copy(error = if (reason != null) "AWS hasn't finished creating the role yet ($reason). Give it a minute and check again." else e.userLine()) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.clearAwsTarget() }
                .onSuccess { _state.update { it.copy(disconnected = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }
}

@Composable
fun ConnectAwsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: ConnectAwsViewModel = viewModel { ConnectAwsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copy = rememberCopier()
    val config by container.session.config.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(state.disconnected) { if (state.disconnected) nav.popBackStack() }

    ScreenScaffold(
        topBar = { RaTopBar(title = "Connect AWS", onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                InlineNotice(state.notice, Modifier.padding(bottom = 8.dp), color = if (state.target?.verified == true) Ink.moss else Ink.graphite)
                if (!state.saved) {
                    PrimaryButton("Save account ID", loading = state.busy, enabled = state.accountId.length == 12, onClick = { vm.save() })
                } else {
                    PrimaryButton("Open AWS to create the role", enabled = state.info != null && !state.busy, onClick = { state.info?.let { openLink(context, it.quickCreateUrl) } })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Check connection", enabled = !state.busy, onClick = { vm.verify() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            // R-109 (G, PM-approved): only an app's backend runs in the client's AWS.
            Text(
                com.raviga.app.ui.status.DeliveryCopy.CONNECT_AWS_INTRO,
                style = RaType.body, color = Ink.ink,
            )
            if (!state.saved) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().tile(padding = 16.dp)) {
                    Text("No AWS account yet?", style = RaType.bodyMedium, color = Ink.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("Create one at aws.amazon.com, then come back here to connect it.", style = RaType.secondary, color = Ink.graphite)
                    Spacer(Modifier.height(4.dp))
                    InlineAction("Create a free AWS account", onClick = { openLink(context, AWS_SIGN_UP) }, modifier = Modifier.offset(x = (-8).dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("AWS account ID", style = RaType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(6.dp))
            RaTextField(
                value = state.accountId,
                onValueChange = { vm.setAccountId(it) },
                placeholder = "123456789012",
                label = "AWS account ID",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.busy,
                trailing = if (state.saved) ({ InlineAction("Change", onClick = { vm.setAccountId("") }) }) else null,
            )
            Spacer(Modifier.height(6.dp))
            Text("The 12-digit account id where we build and run your project. Find it in the AWS console under your account name, top right.", style = RaType.caption, color = Ink.graphite)

            val target = state.target
            if (state.saved && target != null) {
                Spacer(Modifier.height(Ra.sectionGap))
                SectionHeading("The role")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Role name", target.roleName ?: config.delivery.awsRoleName)
                KeyValueRow("Trusted account", state.info?.trustedAccountId?.ifBlank { null } ?: config.delivery.awsTrustedAccountId)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("External ID", style = RaType.body, color = Ink.graphite)
                    Spacer(Modifier.height(4.dp))
                    Text(target.externalId ?: "", style = RaType.body, color = Ink.ink)
                    InlineAction("Copy", onClick = { copy(target.externalId.orEmpty(), false) }, modifier = Modifier.padding(top = 2.dp))
                }
                com.raviga.app.ui.components.Hairline()
                KeyValueRow(
                    "Status",
                    if (target.verified) "Connected ${Time.shortDate(target.verifiedAt)}" else "Not verified yet",
                    valueColor = if (target.verified) Ink.moss else Ink.amber,
                )

                Spacer(Modifier.height(Ra.sectionGap))
                SectionHeading("Steps")
                Spacer(Modifier.height(8.dp))
                NumberedSteps(
                    state.info?.instructions?.takeIf { it.isNotEmpty() } ?: listOf(
                        "Sign in to the AWS account you want the project deployed to.",
                        "Review the stack: it creates one IAM role.",
                        "Tick the IAM acknowledgement and choose Create stack.",
                        "Come back here and check the connection.",
                    ),
                )
                Spacer(Modifier.height(24.dp))
                DestructiveButton("Disconnect AWS", enabled = !state.busy, onClick = { vm.disconnect() })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** R-105: "Create a free AWS account" (iOS opens the same page). */
internal const val AWS_SIGN_UP = "https://aws.amazon.com/"
