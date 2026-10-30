package com.raviga.app.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raviga.app.ui.components.Illustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.tile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    val context = LocalContext.current
    var revisionSheet by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { nav.popBackStack() },
                actions = {
                    if (project != null) {
                        InlineAction("Brief", onClick = { nav.navigate(Routes.document(projectId)) })
                        InlineAction("Status", onClick = { nav.navigate(Routes.status(projectId)) })
                    }
                },
            )
        },
        bottomBar = {
            if (project?.status != ProjectStatus.DELIVERED) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Accept delivery", loading = state.busy, onClick = { vm.accept() })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Request a revision", enabled = !state.busy, onClick = { revisionSheet = true })
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
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
                            ProjectStatus.REVISION_REQUESTED -> "Revision in progress."
                            else -> StatusCopy.detailTitle(project.status)
                        },
                        style = RaType.title, color = Ink.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(StatusCopy.projectTitle(project, container.drafts.get(project.id)?.displayTitle), style = RaType.secondary, color = Ink.graphite)
                    if (project.status == ProjectStatus.ACCEPTED) {
                        Text("Accepted on ${Time.shortDate(project.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at ?: project.updatedAt)}", style = RaType.secondary, color = Ink.moss)
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
                    val github = project.submission?.githubUsername?.ifBlank { null } ?: container.session.me.value?.deliveryTargets?.githubUsername?.ifBlank { null }
                    // Pending is queued, not sent; its error (if any) is internal and never shown.
                    val transferLine = transfer?.let { GoLiveLogic.transferLine(if (it.status == "pending") it.copy(error = null) else it, github) }
                    if (transferLine != null) {
                        Labelled("Repository") {
                            Text(transferLine, style = RaType.body, color = if (transfer?.status == "failed") Ink.brick else Ink.ink)
                            if (transfer?.status == "awaiting_target" || transfer?.status == "failed") {
                                InlineAction("Set GitHub username", onClick = { nav.navigate(Routes.DELIVERY_TARGETS) }, modifier = Modifier.offset(x = (-8).dp))
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
                    delivery?.note?.takeIf { it.isNotBlank() }?.let {
                        Labelled("From the team") { BodyText(it) }
                    }
                    if (project.status == ProjectStatus.DELIVERED && delivery?.acceptBy != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Accepted automatically on ${Time.shortDate(delivery.acceptBy)} if you do nothing.", style = RaType.caption, color = Ink.graphite)
                    }

                    if (project.revisions.requests.isNotEmpty()) {
                        Spacer(Modifier.height(Ra.sectionGap))
                        SectionHeading("Revisions")
                        Spacer(Modifier.height(12.dp))
                        project.revisions.requests.forEach { r ->
                            BodyText(r.message)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Requested ${Time.shortDate(r.createdAt)}" + (r.resolvedAt?.let { ", delivered ${Time.shortDate(it)}" } ?: ", in progress"),
                                style = RaType.caption, color = Ink.graphite,
                            )
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

    if (revisionSheet && project != null) {
        val remaining = (project.revisions.included - project.revisions.used).coerceAtLeast(0)
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) revisionSheet = false },
            sheetState = sheet,
            containerColor = Ink.surface,
            dragHandle = null,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
                Text("Request a revision", style = RaType.heading, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (remaining > 0) "$remaining of ${project.revisions.included} revision ${if (project.revisions.included == 1) "round" else "rounds"} left." else "Your included revision rounds are used up. Email us and we can add more.",
                    style = RaType.secondary, color = if (remaining > 0) Ink.graphite else Ink.amber,
                )
                Spacer(Modifier.height(16.dp))
                RaTextField(value = message, onValueChange = { message = it }, placeholder = "What should change?", minLines = 3)
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Send request", enabled = remaining > 0 && message.isNotBlank(), loading = state.busy, onClick = {
                    vm.requestRevision(message)
                    revisionSheet = false
                    message = ""
                })
            }
        }
    }
}

/** A caption label over its content, as on iOS ("Repository", "Backend", "From the team"). */
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
        Column(Modifier.weight(1f)) {
            Text(title, style = RaType.bodyMedium, color = Ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = RaType.caption, color = Ink.graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(4.dp))
        Icon(trailing, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(18.dp))
    }
}

/** "raviga-builds/tiffin-app" from the repository URL. */
private fun repoName(url: String): String = url.trimEnd('/').split('/').takeLast(2).joinToString("/")
