package com.raviga.app.ui.status

import com.raviga.app.ui.nav.openProjectScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BodyText
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaRow
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.TimelineView
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.openLink
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.util.Time
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.tile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val me by container.session.me.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    val context = LocalContext.current
    // R-103 "Something's wrong": the sheet and what's being written survive new terms opening over it (R-094 P2-7).
    var reportSheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // R-092 #38 (as iOS): approving can't be undone; it deletes the brief from the server.
    var confirmAccept by remember { mutableStateOf(false) }
    var category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var description by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // R-109 (E): "Request a change", and back to a priced change waiting to be paid.
    var changeSheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // R-113 (3, the PM: the same for changes): paying for a change asks first; back from buying credits, asks to pay.
    var confirmChangePay by remember { mutableStateOf(false) }
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val creditsAdded by (handle?.getStateFlow(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false) ?: kotlinx.coroutines.flow.MutableStateFlow(false))
        .collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(creditsAdded) { if (creditsAdded) vm.watchCredits() }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { nav.popBackStack() },
                actions = {
                    if (project != null) {
                        InlineAction("Brief", onClick = { nav.openProjectScreen(Routes.document(projectId)) })
                        InlineAction("Status", onClick = { nav.openProjectScreen(Routes.status(projectId)) })
                    }
                },
            )
        },
        bottomBar = {
            val status = project?.status ?: return@ScreenScaffold
            if (status == ProjectStatus.DELIVERED || state.freeFixesOpen || state.canRequestChange) BottomBar {
                InlineNotice(state.error.takeIf { !reportSheet }, Modifier.padding(bottom = 8.dp))
                // R-113 (the PM's call 1): the free-fix rule once, by the buttons, while fixes are free.
                if (state.freeFixesOpen) {
                    Text(
                        state.config.report.freeFixesLine.ifBlank { com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE },
                        style = RaType.caption, color = Ink.graphite,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    )
                }
                // R-109 (D, iOS's labels): approve the demo, say what's wrong (free), or ask for something new (paid).
                if (status == ProjectStatus.DELIVERED) {
                    PrimaryButton(DeliveryCopy.APPROVE, loading = state.busy, onClick = { confirmAccept = true })
                    Spacer(Modifier.height(8.dp))
                }
                // R-113 (5b): from the demo until 7 days after launch; after that, Request a change alone.
                if (state.freeFixesOpen) SecondaryButton(DeliveryCopy.SOMETHING_WRONG, enabled = !state.busy, onClick = { reportSheet = true })
                if (state.canRequestChange) TertiaryButton(DeliveryCopy.REQUEST_CHANGE, enabled = !state.busy, onClick = { changeSheet = true })
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error.orEmpty(), Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Ra.gutter)) }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                val delivery = project.delivery
                Column(Modifier.padding(horizontal = Ra.gutter)) {
                    Spacer(Modifier.height(4.dp))
                    Illustration(Picture.Delivered, height = 200.dp, radius = Ra.tileRadius)
                    Spacer(Modifier.height(20.dp))
                    Text(StatusCopy.detailTitle(project.status), style = RaType.title, color = Ink.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(StatusCopy.projectTitle(project, container.drafts.get(project.id)?.displayTitle), style = RaType.secondary, color = Ink.graphite)
                    if (project.statusLine.isNotBlank()) {
                        // The server's line for the status, as it comes ("Demo ready: check your invites").
                        Spacer(Modifier.height(8.dp))
                        Text(StatusCopy.keepDatesTogether(project.statusLine), style = RaType.body, color = Ink.ink)
                    }
                    Spacer(Modifier.height(16.dp))

                    // R-109 (D): the demo is the real app on the client's phone, from Raviga's store accounts.
                    val demo = delivery?.demo
                    if (demo != null && (project.status == ProjectStatus.DELIVERED || project.status == ProjectStatus.REVISION_REQUESTED)) {
                        SectionHeading(DeliveryCopy.TRY_IT)
                        Spacer(Modifier.height(12.dp))
                        demo.testflight?.let { tf ->
                            val email = demo.invitedEmails.apple ?: state.storeEmails.apple.orEmpty()
                            DemoStoreTile(
                                if (tf.ready) DeliveryCopy.testflightReady(email) else DeliveryCopy.TESTFLIGHT_WAITING,
                                button = DeliveryCopy.OPEN_TESTFLIGHT.takeIf { tf.ready },
                                onOpen = { tf.url?.let { openLink(context, it) } },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        demo.play?.takeIf { it.ready }?.let { play ->
                            val email = demo.invitedEmails.google ?: state.storeEmails.google.orEmpty()
                            DemoStoreTile(
                                DeliveryCopy.playReady(email),
                                button = DeliveryCopy.OPEN_PLAY,
                                onOpen = { play.url?.let { openLink(context, it) } },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    // R-109 (F): after approval, the steps into the client's own stores. R-113 (5a): "Your app is live" once
                    // it's in a store, also while a fix or a change goes round again.
                    if (project.status == ProjectStatus.ACCEPTED || project.status == ProjectStatus.LIVE || state.launched) {
                        LinkTile(
                            title = if (state.launched) DeliveryCopy.LIVE_TILE else DeliveryCopy.LIVE_TITLE,
                            detail = project.delivery?.handover?.statusLine?.takeIf { it.isNotBlank() } ?: DeliveryCopy.STEP_ACCOUNTS,
                            icon = Icons.Outlined.Flag,
                            onClick = { nav.navigate(Routes.goLive(projectId)) },
                        )
                    }
                    // R-103 (her rule, PM 11-04): no team text reaches the client; the delivered statusLine says it.
                    if (project.status == ProjectStatus.DELIVERED && delivery?.acceptBy != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Approved automatically on ${Time.shortDate(delivery.acceptBy)} if you do nothing.", style = RaType.caption, color = Ink.graphite)
                    }

                    // R-109 (E, as iOS): every change with the server's line; a priced one shows its price and Pay here.
                    val changes = delivery?.changes.orEmpty()
                    if (changes.isNotEmpty()) {
                        Spacer(Modifier.height(Ra.sectionGap))
                        SectionHeading("Your changes")
                        Spacer(Modifier.height(12.dp))
                        changes.forEach { change ->
                            ChangeRow(change, state, onPay = { confirmChangePay = true }, onBuyCredits = { nav.navigate(Routes.creditsFor(projectId, state.changeShortBy)) })
                            Spacer(Modifier.height(Ra.tileGap))
                        }
                        InlineNotice(state.changeError.takeIf { !changeSheet })
                    }

                    // R-103 (as iOS): the client's reports, one row each; nothing comes back but the status.
                    if (project.reports.isNotEmpty()) {
                        Spacer(Modifier.height(Ra.sectionGap))
                        SectionHeading("What you reported")
                        Spacer(Modifier.height(12.dp))
                        project.reports.forEach { r ->
                            Text(
                                "${GoLiveLogic.reportLabel(r.category, state.config.report.categories)}, ${Time.shortDate(r.createdAt)}",
                                style = RaType.bodyMedium, color = Ink.ink,
                            )
                            r.description?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                BodyText(it)
                            }
                            r.resolvedAt?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("Fixed ${Time.shortDate(it)}", style = RaType.caption, color = Ink.moss)
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    project.timeline?.takeIf { it.milestones.isNotEmpty() }?.let { tl ->
                        Spacer(Modifier.height(Ra.sectionGap))
                        TimelineView(tl.milestones, note = "Projects are usually delivered well ahead of this date.", estimatedDeliveryDate = tl.estimatedDeliveryDate)
                        // R-113 item 6: the whole flow, by the timeline.
                        InlineAction(com.raviga.app.ui.onboarding.HowItWorks.TIMELINE_LINK, onClick = { nav.navigate(Routes.HOW_IT_WORKS) }, modifier = Modifier.offset(x = (-8).dp))
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (confirmAccept) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmAccept = false },
            containerColor = Ink.surface,
            // R-113 (the PM's call 2): a fix or a change approved again is published again; the brief went at the first.
            title = { Text(if (state.secondApproval) DeliveryCopy.APPROVE_AGAIN_TITLE else DeliveryCopy.APPROVE_TITLE, style = RaType.heading, color = Ink.ink) },
            text = {
                val stores = GoLiveLogic.stores(project ?: return@AlertDialog)
                Text(
                    if (state.secondApproval) DeliveryCopy.approveAgainBody(stores.apple, stores.play) else DeliveryCopy.APPROVE_BODY,
                    style = RaType.body, color = Ink.graphite,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmAccept = false; vm.accept() }) { Text("Approve", style = RaType.button, color = Ink.ink) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmAccept = false }) { Text("Not yet", style = RaType.button, color = Ink.ink) }
            },
        )
    }
    // v0.13.0 (as iOS): free fixes ended while the report sheet was open: the sheet closes, the project reloads,
    // then an alert with the server's words, Request a change / OK.
    val ended = state.freeFixesEndedMessage
    androidx.compose.runtime.LaunchedEffect(ended) { if (ended != null) reportSheet = false }
    if (ended != null && !reportSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.freeFixesEndedSeen() },
            containerColor = Ink.surface,
            title = { Text(ended, style = RaType.heading, color = Ink.ink) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.freeFixesEndedSeen(); changeSheet = true }) {
                    Text(DeliveryCopy.REQUEST_CHANGE, style = RaType.button, color = Ink.ink)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.freeFixesEndedSeen() }) { Text("OK", style = RaType.button, color = Ink.ink) }
            },
        )
    }
    val quoted = state.quotedChange?.quote?.credits
    if (confirmChangePay && quoted != null) {
        PayDialog(
            com.raviga.app.ui.credits.PayCopy.confirmTitle(quoted),
            com.raviga.app.ui.credits.PayCopy.confirmMessage(quoted, state.balance),
            onPay = { confirmChangePay = false; vm.payChange(onPaid = { changeSheet = false }) },
            onCancel = { confirmChangePay = false },
        )
    }
    if (creditsAdded && quoted != null && state.changeShortBy <= 0.0 && !state.busy && !confirmChangePay) {
        PayDialog(
            com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED_TITLE,
            com.raviga.app.ui.credits.PayCopy.creditsAddedMessage(quoted),
            onPay = { handle?.set(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false); vm.payChange(onPaid = { changeSheet = false }) },
            onCancel = { handle?.set(com.raviga.app.ui.credits.PayCopy.CREDITS_ADDED, false) },
        )
    }
    if (changeSheet && project != null) {
        ChangeSheet(
            state = state,
            onPrice = { vm.requestChange(it) },
            onPay = { confirmChangePay = true },
            onBuyCredits = { changeSheet = false; nav.navigate(Routes.creditsFor(projectId, state.changeShortBy)) },
            onDismiss = { changeSheet = false },
        )
    }
    if (reportSheet && project != null) {
        val report = state.config.report
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) reportSheet = false },
            sheetState = sheet,
            containerColor = Ink.surface,
            dragHandle = null,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)
                    .padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding().imePadding(),
            ) {
                // R-103 (iOS's sheet; the hint is the PM's final copy).
                Text("What's wrong?", style = RaType.heading, color = Ink.ink)
                Spacer(Modifier.height(16.dp))
                report.categories.forEach { c ->
                    CategoryRow(c.label, selected = category == c.id, enabled = !state.busy, onClick = { category = c.id })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                RaTextField(
                    value = description,
                    onValueChange = { description = it.take(report.descriptionMaxChars) },
                    placeholder = "What happens, and where?",
                    minLines = 3,
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text("${description.length}/${report.descriptionMaxChars}", style = RaType.caption, color = Ink.graphite)
                }
                Spacer(Modifier.height(8.dp))
                Text(REPORT_HINT, style = RaType.secondary, color = Ink.graphite)
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    "Send report",
                    enabled = category != null && description.isNotBlank(),
                    loading = state.busy,
                    onClick = {
                        // Closed and cleared only once it's sent; a failure (a 422 says why) shows here with the text kept.
                        vm.report(category ?: return@PrimaryButton, description) {
                            reportSheet = false
                            category = null
                            description = ""
                        }
                    },
                )
            }
        }
    }
}

/** R-103: above "Send report" (the PM's final copy, as iOS). */
internal const val REPORT_HINT = "We'll fix it and deliver again. You'll see it here when it's ready."

/** One of config.report.categories: a single choice, with a check when picked (as iOS). */
@Composable
private fun CategoryRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.tile(padding = 14.dp, onClick = if (enabled) onClick else null)
            .semantics(mergeDescendants = true) { this.selected = selected; role = androidx.compose.ui.semantics.Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = RaType.body, color = Ink.ink, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Ink.blue, modifier = Modifier.size(20.dp))
    }
}

/** R-109 (E, as iOS): one change: what it is, its line, and for a priced one the price with Pay (or Buy credits). */
@Composable
private fun ChangeRow(change: com.raviga.app.data.api.Change, state: StatusViewModel.State, onPay: () -> Unit, onBuyCredits: () -> Unit) {
    Column(Modifier.tile(padding = 14.dp)) {
        val what = change.brief?.title?.takeIf { it.isNotBlank() } ?: change.description?.takeIf { it.isNotBlank() }
        what?.let {
            Text(it, style = RaType.bodyMedium, color = Ink.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        if (change.statusLine.isNotBlank()) {
            Text(change.statusLine, style = RaType.secondary, color = if (change.status == com.raviga.app.data.api.Change.DELIVERED) Ink.moss else Ink.graphite)
        }
        val quote = change.quote
        if (change.status == com.raviga.app.data.api.Change.QUOTED && quote != null) {
            Spacer(Modifier.height(8.dp))
            val usd = quote.usd.takeIf { it > 0.0 } ?: (quote.credits * state.config.credits.creditValueUsd)
            Text(DeliveryCopy.changePrice(com.raviga.app.util.Money.credits(quote.credits), com.raviga.app.util.Money.usd(usd)), style = RaType.body, color = Ink.ink)
            Spacer(Modifier.height(10.dp))
            if (state.changeShortBy > 0.0) {
                Text(
                    "You have ${com.raviga.app.util.Money.credits(state.balance)}. You need ${com.raviga.app.util.Money.creditsOwed(state.changeShortBy)} more.",
                    style = RaType.body, color = Ink.ink,
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Buy credits", onClick = onBuyCredits)
            } else {
                SecondaryButton("Pay ${com.raviga.app.util.Money.credits(quote.credits)}", enabled = !state.busy, onClick = onPay)
            }
        }
    }
}

/** R-109 (D): one store's test invite: what to do (or that it's waiting), and the button that opens it. */
@Composable
private fun DemoStoreTile(line: String, button: String?, onOpen: () -> Unit) {
    Column(Modifier.tile(padding = 14.dp)) {
        Text(line, style = RaType.body, color = Ink.ink)
        if (button != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(button, onClick = onOpen, icon = Icons.Outlined.ArrowOutward)
        }
    }
}

/** The repository or a document, as a tile: an icon square, a name and one line. */
@Composable
private fun LinkTile(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
    dark: Boolean = false,
    trailing: ImageVector = Icons.Outlined.ChevronRight,
    description: String = title,
) {
    Row(
        Modifier.tile(padding = 14.dp, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (dark) Ink.ink else Ink.blueWash),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (dark) Ink.surface else Ink.blueInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        // QA R1: at the large text sizes the row wraps in full instead of "...".
        val lines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) Int.MAX_VALUE else 1
        Column(Modifier.weight(1f)) {
            Text(title, style = RaType.bodyMedium, color = Ink.ink, maxLines = lines, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = RaType.caption, color = Ink.graphite, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(4.dp))
        Icon(trailing, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(18.dp))
    }
}
