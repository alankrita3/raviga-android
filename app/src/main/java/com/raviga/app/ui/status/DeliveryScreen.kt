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
            if (project?.status != ProjectStatus.DELIVERED) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                // R-103 (iOS's labels): approve it, or say what's wrong and the team delivers again.
                PrimaryButton("It works, approved", loading = state.busy, onClick = { confirmAccept = true })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Something's wrong", enabled = !state.busy, onClick = { reportSheet = true })
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
                    Text(
                        when (project.status) {
                            ProjectStatus.DELIVERED -> "Your code is ready."
                            ProjectStatus.ACCEPTED -> "Your code."
                            ProjectStatus.REVISION_REQUESTED -> "Being fixed."
                            else -> StatusCopy.detailTitle(project.status)
                        },
                        style = RaType.title, color = Ink.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(StatusCopy.projectTitle(project, container.drafts.get(project.id)?.displayTitle), style = RaType.secondary, color = Ink.graphite)
                    if (project.status == ProjectStatus.ACCEPTED) {
                        Text("Approved on ${Time.shortDate(project.statusChangedAt ?: project.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at ?: project.updatedAt)}", style = RaType.secondary, color = Ink.moss)
                    } else if (project.statusLine.isNotBlank()) {
                        // Contract v0.10.0: the server's line for the status, as it comes.
                        Spacer(Modifier.height(8.dp))
                        Text(StatusCopy.keepDatesTogether(project.statusLine), style = RaType.body, color = Ink.ink)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (delivery?.repoUrl != null) {
                        LinkTile(
                            title = repoName(delivery.repoUrl),
                            detail = delivery.repoUrl.removePrefix("https://"),
                            icon = Icons.Outlined.Code,
                            dark = true,
                            trailing = Icons.Outlined.ArrowOutward,
                            description = "Open ${repoName(delivery.repoUrl)} on GitHub",
                            onClick = { openLink(context, delivery.repoUrl) },
                        )
                    }
                    delivery?.handover?.let { guide ->
                        Spacer(Modifier.height(10.dp))
                        LinkTile(
                            title = "Handover guide",
                            detail = "What we built and how to run it",
                            icon = Icons.AutoMirrored.Outlined.Article,
                            onClick = { openLink(context, guide) },
                        )
                    }
                    if (delivery != null) {
                        Spacer(Modifier.height(10.dp))
                        LinkTile(
                            title = "Make it live",
                            detail = "Steps to put it in front of customers",
                            icon = Icons.Outlined.Flag,
                            onClick = { nav.navigate(Routes.goLive(projectId)) },
                        )
                    }

                    val transfer = delivery?.transfer
                    // R-092 #28: the transfer goes to the current delivery target; the name given at submit only if none.
                    val github = me?.deliveryTargets?.githubUsername?.ifBlank { null } ?: project.submission?.githubUsername?.ifBlank { null }
                    // Pending is queued, not sent; its error (if any) is internal and never shown.
                    val transferLine = transfer?.let { GoLiveLogic.transferLine(if (it.status == "pending") it.copy(error = null) else it, github) }
                    if (transferLine != null) {
                        Labelled("Repository") {
                            Text(transferLine, style = RaType.body, color = if (transfer.status == "failed") Ink.brick else Ink.ink)
                            if (transfer.status == "awaiting_target") {
                                InlineAction("Set GitHub username", onClick = { nav.navigate(Routes.DELIVERY_TARGETS) }, modifier = Modifier.offset(x = (-8).dp))
                            }
                            if (transfer.status == "failed" && StatusCopy.supportEmail.isNotBlank()) {
                                InlineAction(
                                    StatusCopy.supportEmail,
                                    onClick = { com.raviga.app.ui.emailLink(context, StatusCopy.supportEmail, "Repository transfer for ${project.ref}") },
                                    modifier = Modifier.offset(x = (-8).dp),
                                )
                            }
                        }
                    }
                    delivery?.aws?.let { aws ->
                        Labelled("Backend") {
                            Text(GoLiveLogic.awsLine(aws), style = RaType.body, color = if (aws.status == "deployed") Ink.moss else Ink.ink)
                            if (aws.status == "not_requested") {
                                InlineAction("Connect AWS", onClick = { nav.navigate(Routes.CONNECT_AWS) }, modifier = Modifier.offset(x = (-8).dp))
                            }
                        }
                    }
                    // R-103 (her rule, PM 11-04): no team text reaches the client; the delivered statusLine says it.
                    if (project.status == ProjectStatus.DELIVERED && delivery?.acceptBy != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Approved automatically on ${Time.shortDate(delivery.acceptBy)} if you do nothing.", style = RaType.caption, color = Ink.graphite)
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
            title = { Text("Approve this delivery?", style = RaType.heading, color = Ink.ink) },
            text = {
                Text(
                    "This deletes the brief from our servers. Your code and handover guide stay yours.",
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

/** A caption label over its content, as on iOS ("Repository", "Backend"). */
@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(label, style = RaType.caption, color = Ink.graphite)
    Spacer(Modifier.height(4.dp))
    content()
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

/** "raviga-builds/tiffin-app" from the repository URL. */
private fun repoName(url: String): String = url.trimEnd('/').split('/').takeLast(2).joinToString("/")
