package com.raviga.app.ui.onboarding

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
import com.raviga.app.ui.status.StatusCopy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import kotlinx.coroutines.launch

/** Shown once, before anything is sent to an AI provider. Pops back when done. */
@Composable
fun AiConsentScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val config by container.session.config.collectAsStateWithLifecycle()
    // Off until the client turns it on, as the screen says (consent is an affirmative act; same as iOS).
    var allow by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val providers = config.ai.providers.ifEmpty { listOf("our AI providers") }

    ScreenScaffold(
        topBar = { RaTopBar(onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Continue", enabled = allow, loading = busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { container.session.setAiProcessing(true) }
                            .onSuccess {
                                nav.previousBackStackEntry?.savedStateHandle?.set("ai_consent_granted", true)
                                nav.popBackStack()
                            }
                            .onFailure { error = it.userLine() }
                        busy = false
                    }
                })
                TertiaryButton("Not now", onClick = { nav.popBackStack() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(16.dp))
            Text("Before we send this to AI", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            // R-028 (as iOS): the switch comes first, so it is never hidden behind the Continue bar on a short phone.
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow AI processing", style = RaType.bodyMedium, color = Ink.ink)
                    Text("Needed to write your brief", style = RaType.caption, color = Ink.graphite)
                }
                Switch(
                    checked = allow,
                    onCheckedChange = { allow = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.surface, checkedTrackColor = Ink.link, checkedBorderColor = Ink.link,
                        uncheckedThumbColor = Ink.surface, uncheckedTrackColor = Ink.ruleStrong, uncheckedBorderColor = Ink.ruleStrong,
                    ),
                )
            }
            Hairline()
            Spacer(Modifier.height(20.dp))
            Block("What is sent", "Only the text of your notes and brief, to write and price it. Never your recordings or files. We don't store it.")
            // The backend's provider line carries the full disclosure (retention, no training); show it as sent.
            Block("Who processes it", providers.joinToString("\n"))
            Block("How long it is kept", StatusCopy.LOCAL_FIRST_PROMISE)
            Block("When it is sent", "Only after you turn this on and continue. You can turn it off later in Settings.")
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(title, style = RaType.bodyMedium, color = Ink.ink)
        Spacer(Modifier.height(4.dp))
        Text(body, style = RaType.secondary, color = Ink.graphite)
    }
}
