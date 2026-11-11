package com.raviga.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.Handover
import com.raviga.app.data.api.Project
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.rememberCopier
import com.raviga.app.ui.components.tile
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType

/**
 * "Make it live" (R-109, F; PM-approved copy as iOS): what the client does (store accounts, inviting Raviga, AWS when
 * there's a backend) and what Raviga does (publish, per store), until the app is live in their stores.
 */
@Composable
fun GoLiveScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    val steps = project?.let { GoLiveLogic.steps(it, state.awsVerified) }

    ScreenScaffold(topBar = { RaTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error.orEmpty(), Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Ra.gutter)) }
            steps == null -> ErrorState(DeliveryCopy.NOTHING_YET, Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
                Spacer(Modifier.height(4.dp))
                Illustration(Picture.Delivered, height = 180.dp, radius = Ra.tileRadius)
                Spacer(Modifier.height(20.dp))
                Text(DeliveryCopy.LIVE_TITLE, style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Text(DeliveryCopy.LIVE_INTRO, style = RaType.secondary, color = Ink.graphite)
                // The server's line for the current step, as sent.
                project.delivery?.handover?.statusLine?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = RaType.body, color = Ink.ink)
                }
                Spacer(Modifier.height(22.dp))
                steps.forEachIndexed { index, step ->
                    StepTile(index + 1, step) { StepBody(step.id, project, state, vm, nav) }
                    Spacer(Modifier.height(10.dp))
                }
                InlineNotice(state.error)
                Spacer(Modifier.height(10.dp))
                // No messaging in the app (R-103); support's email is the way to reach a person.
                Text(DeliveryCopy.stuck(state.config.legal.supportEmail), style = RaType.caption, color = Ink.ash)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StepBody(id: GoLiveLogic.StepId, project: Project, state: StatusViewModel.State, vm: StatusViewModel, nav: NavController) {
    val context = LocalContext.current
    val stores = GoLiveLogic.stores(project)
    val handover = project.delivery?.handover
    val guides = state.config.handover.guides
    val link: @Composable (String, String) -> Unit = { label, url ->
        InlineAction(label, onClick = { openLink(context, url) }, modifier = Modifier.offset(x = (-8).dp))
    }
    when (id) {
        GoLiveLogic.StepId.ACCOUNTS -> {
            // R-113 (her yes, 4a/4d): the stores' own fees, once, at the top; no prices.
            Detail(DeliveryCopy.feeLine(stores.apple, stores.play))
            if (stores.apple) {
                Detail(DeliveryCopy.APPLE_JOIN)
                link(DeliveryCopy.APPLE_ENROL_GUIDE, guides.appleEnroll.ifBlank { DeliveryCopy.APPLE_ENROL_URL })
                Detail(DeliveryCopy.APPLE_ORG)
                link(DeliveryCopy.DUNS, DeliveryCopy.DUNS_URL)
            }
            if (stores.play) {
                if (stores.apple) Spacer(Modifier.height(6.dp))
                Detail(DeliveryCopy.GOOGLE_JOIN)
                link(DeliveryCopy.GOOGLE_SIGNUP_GUIDE, guides.playSignup.ifBlank { DeliveryCopy.GOOGLE_SIGNUP_URL })
                Detail(DeliveryCopy.GOOGLE_ORG)
                link(DeliveryCopy.CLOSED_TEST, DeliveryCopy.CLOSED_TEST_URL)
            }
        }
        GoLiveLogic.StepId.INVITE -> {
            // The publishing user from /config (one address for both stores); nothing hardcoded.
            val email = state.config.delivery.publishingEmail
            val named = email.ifBlank { DeliveryCopy.PUBLISHING_EMAIL_FALLBACK }
            val copy = rememberCopier()
            if (email.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(email, style = RaType.bodyMedium, color = Ink.ink, modifier = Modifier.weight(1f))
                    InlineAction(DeliveryCopy.COPY, onClick = { copy(email, false) })
                }
            }
            Detail(DeliveryCopy.INVITE_LINE)
            // R-113 (her yes, 4c): the exact roles and permissions fold behind "Show exact steps", opened in place.
            var steps by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            if (!steps) {
                InlineAction(DeliveryCopy.SHOW_STEPS, onClick = { steps = true }, modifier = Modifier.offset(x = (-8).dp))
            }
            if (steps) {
                if (stores.apple) {
                    Detail(DeliveryCopy.appleInvite(named))
                    link(DeliveryCopy.APPLE_GUIDE, DeliveryCopy.APPLE_GUIDE_URL)
                }
                if (stores.play) {
                    Detail(DeliveryCopy.googleInvite(named))
                    link(DeliveryCopy.GOOGLE_GUIDE, DeliveryCopy.GOOGLE_GUIDE_URL)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (GoLiveLogic.waitingForInvite(project).isNotEmpty() && !state.invitesSent) {
                SecondaryButton(DeliveryCopy.INVITES_SENT, enabled = !state.busy, onClick = { vm.invitesSent() })
            } else {
                Text(DeliveryCopy.INVITES_THANKS, style = RaType.secondary, color = Ink.moss)
            }
        }
        GoLiveLogic.StepId.AWS -> {
            Detail(DeliveryCopy.AWS_LINE)
            Spacer(Modifier.height(8.dp))
            if (state.awsVerified || handover?.backend == "deployed") {
                Text(DeliveryCopy.CONNECTED, style = RaType.secondary, color = Ink.moss)
            } else {
                SecondaryButton(DeliveryCopy.CONNECT_AWS, onClick = { nav.navigate(Routes.CONNECT_AWS) })
            }
        }
        GoLiveLogic.StepId.PUBLISH -> {
            stores.list.forEach { store ->
                Spacer(Modifier.height(6.dp))
                Text(if (store == Handover.APPLE) "App Store" else "Google Play", style = RaType.caption, color = Ink.graphite)
                Text(GoLiveLogic.storeLine(handover, store), style = RaType.body, color = Ink.ink)
            }
            Detail(DeliveryCopy.PUBLISH_LINE)
        }
        GoLiveLogic.StepId.LIVE -> {
            Detail(DeliveryCopy.LIVE_LINE)
            handover?.storeUrls?.get(Handover.APPLE)?.takeIf { stores.apple && it.isNotBlank() }?.let { link(DeliveryCopy.OPEN_APP_STORE, it) }
            handover?.storeUrls?.get(Handover.PLAY)?.takeIf { stores.play && it.isNotBlank() }?.let { link(DeliveryCopy.OPEN_GOOGLE_PLAY, it) }
            // 4b: only when the backend is deployed; nothing about AWS without one.
            if (handover?.backend == "deployed") Detail(DeliveryCopy.BACKEND_LIVE)
        }
    }
}

@Composable
private fun Detail(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = RaType.secondary, color = Ink.graphite)
}

@Composable
private fun StepTile(number: Int, step: GoLiveLogic.Step, body: @Composable () -> Unit) {
    Row(Modifier.tile(padding = 16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (step.done) Ink.link else Ink.ink), contentAlignment = Alignment.Center) {
            if (step.done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Ink.surface, modifier = Modifier.size(15.dp))
            else Text("$number", style = RaType.chip, color = Ink.surface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // TalkBack: "Step 2: Invite Raviga" or "Done: Invite Raviga", as a heading to jump between steps.
            Text(
                step.title, style = RaType.bodyMedium, color = if (step.done) Ink.graphite else Ink.ink,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = (if (step.done) "Done: " else "Step $number: ") + step.title
                },
            )
            body()
        }
    }
}
