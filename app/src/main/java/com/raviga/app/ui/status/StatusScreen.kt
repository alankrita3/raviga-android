package com.raviga.app.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.raviga.app.data.api.Comment
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BodyText
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.DestructiveButton
import com.raviga.app.ui.components.RaTextField
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.Hairline
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.StatusMark
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
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    var confirmCancel by remember { mutableStateOf(false) }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { nav.popBackStack() },
                actions = { if (project != null) InlineAction("Brief", onClick = { nav.navigate(Routes.document(projectId)) }) },
            )
        },
        bottomBar = {
            if (project == null) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when (project.status) {
                    ProjectStatus.CHANGES_REQUESTED -> {
                        PrimaryButton("Edit and resubmit", onClick = { nav.navigate(Routes.document(projectId)) })
                        Spacer(Modifier.height(4.dp))
                        DestructiveButton("Cancel project", enabled = !state.busy, onClick = { confirmCancel = true })
                    }
                    ProjectStatus.SUBMITTED -> DestructiveButton("Cancel project", enabled = !state.busy, onClick = { confirmCancel = true })
                    ProjectStatus.DELIVERED, ProjectStatus.REVISION_REQUESTED, ProjectStatus.ACCEPTED ->
                        PrimaryButton("View delivery", onClick = { nav.navigate(Routes.delivery(projectId)) })
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val done = project.status == ProjectStatus.DELIVERED || project.status == ProjectStatus.ACCEPTED
                    Chip(StatusCopy.detailTitle(project.status), tone = if (done) ChipTone.Blue else ChipTone.Plain, dot = StatusCopy.markColor(project.status))
                    Spacer(Modifier.width(12.dp))
                    Text(headerCaption(project), style = RaType.caption, color = Ink.graphite, maxLines = 2)
                }

                if (project.status == ProjectStatus.REJECTED) {
                    project.history.lastOrNull { it.status == ProjectStatus.REJECTED }?.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = RaType.body, color = Ink.ink)
                    }
                }

                project.timeline?.takeIf { it.milestones.isNotEmpty() }?.let { tl ->
                    Spacer(Modifier.height(Ra.sectionGap))
                    TimelineView(tl.milestones, note = "Projects are usually delivered well ahead of this date.", estimatedDeliveryDate = tl.estimatedDeliveryDate)
                }

                val deletedAt = project.contentDeletedAt
                // Once closed, the server deleted the brief and its comments: no empty section, just say so.
                if (deletedAt == null) {
                    Spacer(Modifier.height(Ra.sectionGap))
                    Hairline()
                    Spacer(Modifier.height(20.dp))
                    SectionHeading("From the team")
                    Spacer(Modifier.height(12.dp))
                    if (state.comments.isEmpty()) {
                        Text(
                            if (project.status == ProjectStatus.SUBMITTED) "Nothing yet. We usually reply within a working day." else "No comments.",
                            style = RaType.body, color = Ink.graphite,
                        )
                    } else {
                        state.comments.forEach { CommentBlock(it) }
                    }
                }
                if (deletedAt == null && ProjectStatus.canComment(project.status)) {
                    Spacer(Modifier.height(16.dp))
                    RaTextField(value = state.reply, onValueChange = { vm.setReply(it) }, placeholder = "Reply to the team", minLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        InlineAction("Send", enabled = state.reply.isNotBlank() && !state.busy, onClick = { vm.sendReply() })
                    }
                }
                Spacer(Modifier.height(Ra.sectionGap))
                Text(
                    if (deletedAt != null) {
                        "We deleted this brief and its comments from our servers on ${Time.shortDate(deletedAt)}." +
                            if (container.drafts.get(project.id) != null) " Your copy stays on this phone." else ""
                    } else {
                        "We keep this brief only to review and build it, and delete it from our servers when you accept the delivery."
                    },
                    style = RaType.caption, color = Ink.graphite,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = Ink.surface,
            title = { Text("Cancel this project?", style = RaType.heading, color = Ink.ink) },
            text = { Text("Your ${project?.submission?.creditsCharged ?: 0} credits come back to your balance and the brief unlocks as a draft.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel() }) { Text("Cancel project", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it", style = RaType.button, color = Ink.ink) } },
        )
    }
}

@Composable
fun CommentBlock(comment: Comment) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        BodyText(comment.body)
        Spacer(Modifier.height(4.dp))
        Text(
            (if (comment.author == "client") "You" else "Raviga team") + ", " + Time.dateTime(comment.createdAt),
            style = RaType.caption, color = Ink.graphite,
        )
        Spacer(Modifier.height(12.dp))
        Hairline()
    }
}

/** Building while the team works on it, the parcel once it's delivered; no picture for the rest. */
private fun headerPicture(status: String): Picture? = when (status) {
    ProjectStatus.SUBMITTED, ProjectStatus.CHANGES_REQUESTED, ProjectStatus.APPROVED, ProjectStatus.REVISION_REQUESTED -> Picture.Building
    ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED -> Picture.Delivered
    else -> null
}

/** "RA-4XEAZQ, submitted 9 Oct, 32 credits" */
private fun headerCaption(project: com.raviga.app.data.api.Project): String = listOfNotNull(
    project.ref.takeIf { it.isNotBlank() },
    project.submission?.submittedAt?.let { "submitted ${Time.shortDate(it)}" },
    project.submission?.creditsCharged?.takeIf { it > 0 }?.let { Money.credits(it) },
).joinToString(", ")
