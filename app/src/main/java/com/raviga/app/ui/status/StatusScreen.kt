package com.raviga.app.ui.status

import com.raviga.app.ui.nav.openProjectScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.DestructiveButton
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.TimelineView
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.util.Time
import com.raviga.app.ui.components.Chip
import com.raviga.app.ui.components.ChipTone
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.util.Money

@Composable
fun StatusScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    var confirmCancel by remember { mutableStateOf(false) }
    // R-113 (her yes, 3a): paying asks first.
    var confirmPay by remember { mutableStateOf(false) }
    // R-113 (3b): back from buying credits for this project: ask to pay once they cover the price.
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val creditsAdded by (handle?.getStateFlow(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false) ?: kotlinx.coroutines.flow.MutableStateFlow(false))
        .collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(creditsAdded) { if (creditsAdded) vm.watchCredits() }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { nav.popBackStack() },
                actions = { if (project != null) InlineAction("Brief", onClick = { nav.openProjectScreen(Routes.document(projectId)) }) },
            )
        },
        bottomBar = {
            if (project == null) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when (project.status) {
                    ProjectStatus.SUBMITTED -> DestructiveButton("Cancel project", enabled = !state.busy, onClick = { confirmCancel = true })
                    // R-103 (contract 11, iOS's labels): pay; a new quote first when the price expired; Buy credits when short.
                    ProjectStatus.APPROVED -> {
                        // R-109 (contract v0.12.0): no AWS before paying; Raviga builds on its own side.
                        when {
                            !state.awaitingPayment -> Unit
                            state.priceExpired -> PrimaryButton("Get a new quote", loading = state.quoting, enabled = !state.busy, onClick = { vm.requestNewQuote() })
                            // R-113 (3b, 3c): the packs, with the pack that covers it standing out; back here to pay.
                            state.shortBy > 0.0 -> PrimaryButton("Buy credits", onClick = { nav.navigate(Routes.creditsFor(projectId, state.shortBy)) })
                            else -> PrimaryButton("Pay ${Money.credits(state.price)}", loading = state.busy, enabled = !state.quoting, onClick = { confirmPay = true })
                        }
                        Spacer(Modifier.height(4.dp))
                        DestructiveButton("Cancel project", enabled = !state.busy && !state.quoting, onClick = { confirmCancel = true })
                    }
                    ProjectStatus.DELIVERED, ProjectStatus.REVISION_REQUESTED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE ->
                        PrimaryButton("View delivery", onClick = { nav.openProjectScreen(Routes.delivery(projectId)) })
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error.orEmpty(), Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Ra.gutter)) }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
                // A picture of the stage the project is at, then where it stands.
                headerPicture(project.status)?.let { picture ->
                    Spacer(Modifier.height(4.dp))
                    Illustration(picture, height = 200.dp, radius = Ra.tileRadius)
                    Spacer(Modifier.height(20.dp))
                } ?: Spacer(Modifier.height(8.dp))
                Text(StatusCopy.projectTitle(project, container.drafts.get(project.id)?.displayTitle), style = RaType.title, color = Ink.ink)
                Spacer(Modifier.height(12.dp))
                // QA R1 (as iOS fc9c380): at the large text sizes the chip and the ref stack, nothing cut or broken mid-word.
                val done = project.status in setOf(ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE)
                val chip: @Composable () -> Unit = {
                    Chip(StatusCopy.detailTitle(project.status), tone = if (done) ChipTone.Blue else ChipTone.Plain, dot = StatusCopy.markColor(project.status))
                }
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                    chip()
                    Spacer(Modifier.height(8.dp))
                    Text(headerCaption(project), style = RaType.caption, color = Ink.graphite)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        chip()
                        Spacer(Modifier.width(12.dp))
                        Text(headerCaption(project), style = RaType.caption, color = Ink.graphite, maxLines = 2)
                    }
                }

                // Contract v0.10.0: the server's line for the status, as it comes (credits and dates filled in).
                if (project.statusLine.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(StatusCopy.keepDatesTogether(project.statusLine), style = RaType.body, color = Ink.ink)
                }
                state.newQuote?.let { q ->
                    Spacer(Modifier.height(12.dp))
                    Text("New quote: ${Money.credits(q.quote.credits)}, about ${Money.usd(q.quote.usd ?: (q.quote.credits * state.config.credits.creditValueUsd))}.", style = RaType.bodyMedium, color = Ink.ink)
                }
                if (state.shortBy > 0.0) {
                    Spacer(Modifier.height(12.dp))
                    Text("You have ${Money.credits(state.balance)}. You need ${Money.creditsOwed(state.shortBy)} more.", style = RaType.body, color = Ink.ink)
                }
                // R-109 (C, E): after paying, what happens next; then where the test invites go, until they're out.
                if (project.status == ProjectStatus.BUILDING) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.openChange?.status == com.raviga.app.data.api.Change.PAID) DeliveryCopy.CHANGE_BUILDING else DeliveryCopy.BUILDING_LINE,
                        style = RaType.secondary, color = Ink.graphite,
                    )
                }
                if (state.invitesEditable) {
                    Spacer(Modifier.height(Ra.sectionGap))
                    InviteEmails(state, onSave = { apple, google -> vm.saveStoreEmails(apple, google) })
                }

                project.timeline?.takeIf { it.milestones.isNotEmpty() }?.let { tl ->
                    Spacer(Modifier.height(Ra.sectionGap))
                    val weeks = project.quote?.let { q -> com.raviga.app.ui.quote.timelineText(q.timeline?.minWeeks ?: 0, q.timeline?.maxWeeks ?: 0, q.estimatedWorkingDays) }
                    TimelineView(
                        tl.milestones, note = "Projects are usually delivered well ahead of this date.",
                        estimatedDeliveryDate = tl.estimatedDeliveryDate,
                        undatedDelivery = weeks?.let { "Ready in $it after payment" },
                    )
                    // R-113 item 6: the whole flow, by the timeline.
                    InlineAction(com.raviga.app.ui.onboarding.HowItWorks.TIMELINE_LINK, onClick = { nav.navigate(Routes.HOW_IT_WORKS) }, modifier = Modifier.offset(x = (-8).dp))
                }

                // R-121 (her rule 2026-11-12, as iOS): nothing about keeping or deleting the brief; one plain line only
                // when the server no longer has it and this phone has no copy.
                if (project.contentDeletedAt != null && container.drafts.get(project.id) == null) {
                    Spacer(Modifier.height(Ra.sectionGap))
                    Text(StatusCopy.BRIEF_GONE, style = RaType.caption, color = Ink.graphite)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmPay && project != null) {
        PayDialog(
            com.raviga.app.ui.credits.PayCopy.confirmTitle(state.price.toDouble()),
            com.raviga.app.ui.credits.PayCopy.confirmMessage(state.price.toDouble(), state.balance),
            onPay = { confirmPay = false; vm.pay() }, onCancel = { confirmPay = false },
        )
    }
    // Credits landed and cover it: one tap to start building. Cancel leaves the Pay button as it was.
    if (creditsAdded && state.awaitingPayment && !state.priceExpired && state.shortBy <= 0.0 && !state.busy && !confirmPay) {
        PayDialog(
            com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED_TITLE,
            com.raviga.app.ui.credits.PayCopy.creditsAddedMessage(state.price.toDouble()),
            onPay = { handle?.set(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false); vm.pay() },
            onCancel = { handle?.set(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false) },
        )
    }
    if (confirmCancel) {
        // R-121 (her rule 2026-11-12, as iOS): "Nothing was charged." only.
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = Ink.surface,
            title = { Text("Cancel this project?", style = RaType.heading, color = Ink.ink) },
            text = {
                Text(StatusCopy.CANCEL_CONFIRM, style = RaType.body, color = Ink.graphite)
            },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel() }) { Text("Cancel project", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it", style = RaType.button, color = Ink.ink) } },
        )
    }
}

/** R-113 (3a, 3b): an alert with a title, its message, and Pay / Cancel (as iOS). */
@Composable
internal fun PayDialog(title: String, message: String, onPay: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ink.surface,
        title = { Text(title, style = RaType.heading, color = Ink.ink) },
        text = { Text(message, style = RaType.body, color = Ink.graphite) },
        confirmButton = { TextButton(onClick = onPay) { Text(com.raviga.app.ui.credits.PayCopy.PAY, style = RaType.button, color = Ink.ink) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(com.raviga.app.ui.credits.PayCopy.CANCEL, style = RaType.button, color = Ink.ink) } },
    )
}

/** Building while the team works on it, the parcel once it's delivered; no picture for the rest. */
private fun headerPicture(status: String): Picture? = when (status) {
    ProjectStatus.SUBMITTED, ProjectStatus.APPROVED, ProjectStatus.BUILDING, ProjectStatus.REVISION_REQUESTED -> Picture.Building
    ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE -> Picture.Delivered
    else -> null
}

/** "RA-4XEAZQ, submitted 9 Oct, 32 credits" */
private fun headerCaption(project: com.raviga.app.data.api.Project): String = listOfNotNull(
    project.ref.takeIf { it.isNotBlank() },
    project.submission?.submittedAt?.let { "submitted ${Time.shortDate(it)}" },
    project.submission?.creditsCharged?.takeIf { it > 0 }?.let { Money.credits(it) },
).joinToString(", ")

/**
 * R-109 (C): "Where to send your test invites": the Apple ID for TestFlight and the Google account for the Play test,
 * only for the apps the project builds. Editable until the invites go out.
 */
@Composable
private fun InviteEmails(state: StatusViewModel.State, onSave: (String?, String?) -> Unit) {
    var apple by androidx.compose.runtime.saveable.rememberSaveable(state.storeEmails.apple) { mutableStateOf(state.storeEmails.apple.orEmpty()) }
    var google by androidx.compose.runtime.saveable.rememberSaveable(state.storeEmails.google) { mutableStateOf(state.storeEmails.google.orEmpty()) }
    var appleError by remember { mutableStateOf<String?>(null) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val email = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
    com.raviga.app.ui.components.SectionHeading(DeliveryCopy.INVITES_HEADING)
    Spacer(Modifier.height(12.dp))
    if (state.wantsApple) {
        com.raviga.app.ui.components.RaTextField(
            value = apple, onValueChange = { apple = it; appleError = null }, label = DeliveryCopy.APPLE_EMAIL,
            singleLine = true, keyboardOptions = email, isError = appleError != null, enabled = !state.busy,
        )
        Spacer(Modifier.height(4.dp))
        Text(appleError ?: DeliveryCopy.APPLE_EMAIL_HINT, style = RaType.caption, color = if (appleError != null) Ink.brick else Ink.graphite)
        Spacer(Modifier.height(12.dp))
    }
    if (state.wantsGoogle) {
        com.raviga.app.ui.components.RaTextField(
            value = google, onValueChange = { google = it; googleError = null }, label = DeliveryCopy.GOOGLE_EMAIL,
            singleLine = true, keyboardOptions = email, isError = googleError != null, enabled = !state.busy,
        )
        Spacer(Modifier.height(4.dp))
        Text(googleError ?: DeliveryCopy.GOOGLE_EMAIL_HINT, style = RaType.caption, color = if (googleError != null) Ink.brick else Ink.graphite)
        Spacer(Modifier.height(12.dp))
    }
    com.raviga.app.ui.components.SecondaryButton(DeliveryCopy.SAVE, enabled = !state.busy, onClick = {
        appleError = if (state.wantsApple) DeliveryCopy.emailProblem(apple) else null
        googleError = if (state.wantsGoogle) DeliveryCopy.emailProblem(google) else null
        if (appleError == null && googleError == null) {
            onSave(apple.trim().takeIf { state.wantsApple }, google.trim().takeIf { state.wantsGoogle })
        }
    })
    if (state.emailsSaved) {
        Spacer(Modifier.height(8.dp))
        Text(DeliveryCopy.SAVED, style = RaType.caption, color = Ink.moss)
    }
}
