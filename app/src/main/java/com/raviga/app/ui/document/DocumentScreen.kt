package com.raviga.app.ui.document

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.SectionIds
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BodyText
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.DestructiveButton
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.ErrorState
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.components.SectionHint
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.Hairline
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.BorderStroke
import com.raviga.app.ui.capture.DescribeChooserSheet
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.util.Time
import kotlinx.coroutines.delay

@Composable
fun DocumentScreen(nav: NavController, projectId: String, reveal: Boolean) {
    val container = LocalAppContainer.current
    val vm: DocumentViewModel = viewModel(key = "doc_$projectId") { DocumentViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val project = state.project
    val draft = state.draft
    val document = state.document
    val rejected = state.refused
    // A refused brief is frozen: nothing to edit, quote or add.
    val editable = state.editable
    // Edits and quotes always go through the local draft, whichever id opened this screen.
    val draftId = draft?.id ?: projectId
    var confirmDelete by remember { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }

    // The document reveal: sections fade in one after another, 90 ms apart, once.
    var revealed by remember { mutableStateOf(if (reveal) -1 else Int.MAX_VALUE) }
    LaunchedEffect(reveal, document?.version) {
        if (reveal && document != null && revealed < 0) {
            for (i in 0..document.sections.size) {
                revealed = i
                delay(90)
            }
            revealed = Int.MAX_VALUE
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    }

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { nav.popBackStack() },
                actions = {
                    if (draft != null && draft.versions.isNotEmpty() && project?.contentDeletedAt == null) {
                        InlineAction("Versions", onClick = { nav.navigate(Routes.versions(draftId)) })
                    }
                    if (state.isLocalDraft && !rejected) InlineAction("Delete", color = Ink.brick, onClick = { confirmDelete = true })
                },
            )
        },
        bottomBar = {
            if (draft == null && project == null) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when {
                    rejected -> PrimaryButton("Delete draft", onClick = { confirmDelete = true })
                    editable && document != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            if (state.status == ProjectStatus.CHANGES_REQUESTED) "Get a new quote" else "Get a quote",
                            enabled = state.busyMessage == null,
                            onClick = { nav.navigate(Routes.quote(draftId)) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        // "Add more": a square plus beside the primary action.
                        OutlinedIconButton(
                            onClick = { chooser = true },
                            enabled = state.busyMessage == null,
                            modifier = Modifier.size(Ra.buttonHeight),
                            shape = RoundedCornerShape(Ra.buttonRadius),
                            border = BorderStroke(1.5.dp, Ink.ruleStrong),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add more", tint = Ink.ink)
                        }
                    }
                    editable -> PrimaryButton("Write the brief", enabled = state.busyMessage == null && draft?.inputs?.isNotEmpty() == true, onClick = { vm.generate() })
                    project != null -> PrimaryButton("View status", onClick = { nav.navigate(Routes.forProject(project.id, project.status)) })
                }
            }
        },
    ) { padding ->
        when {
            draft == null && project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
            draft == null && project == null && state.loading -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Ra.gutter)) }
            draft == null && project == null -> ErrorState("That draft is no longer on this phone.", Modifier.padding(padding), onRetry = { nav.popBackStack() })
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (document == null && state.busyMessage != null) {
                    // Writing the first brief: the writing picture while it happens.
                    item {
                        Column(Modifier.padding(horizontal = Ra.gutter)) {
                            Spacer(Modifier.height(16.dp))
                            SpotIllustration(Picture.Writing, size = 180.dp, modifier = Modifier.offset(x = (-14).dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Writing your brief", style = RaType.title, color = Ink.ink)
                            Spacer(Modifier.height(8.dp))
                            Text(state.busyMessage!!, style = RaType.secondary, color = Ink.link)
                            Spacer(Modifier.height(16.dp))
                            ProgressRule(progress = state.busyProgress?.takeIf { it > 0f })
                        }
                    }
                    return@LazyColumn
                }
                item {
                    Column(Modifier.padding(horizontal = Ra.gutter)) {
                        if (state.busyMessage != null) {
                            ProgressRule(progress = state.busyProgress?.takeIf { it > 0f })
                            Spacer(Modifier.height(8.dp))
                            Text(state.busyMessage!!, style = RaType.caption, color = Ink.link)
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                        EditableTitle(
                            title = state.title,
                            editable = editable,
                            onSave = { vm.saveTitle(it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusLine(state, nav)
                        state.notices.forEach { InlineNotice(it, color = Ink.graphite) }
                        Spacer(Modifier.height(24.dp))
                        if (rejected) {
                            RefusedBlock(state.refusalReason, state.supportEmail)
                            Spacer(Modifier.height(Ra.sectionGap))
                        }
                    }
                }
                if (document == null && state.contentGone) {
                    item {
                        Column(Modifier.padding(horizontal = Ra.gutter)) {
                            SectionHint("The brief was deleted from our servers when this project closed, and this phone has no copy of it.")
                            Spacer(Modifier.height(Ra.sectionGap))
                        }
                    }
                } else if (document == null) {
                    item {
                        Column(Modifier.padding(horizontal = Ra.gutter)) {
                            SectionHeading("Your description")
                            Spacer(Modifier.height(12.dp))
                            val inputs = draft?.inputs.orEmpty()
                            if (inputs.isEmpty()) {
                                SectionHint("Nothing yet. Speak, type or upload a description.")
                                Spacer(Modifier.height(16.dp))
                                if (editable) SecondaryButton("Describe it", onClick = { chooser = true })
                            } else {
                                inputs.forEach { input ->
                                    BodyText(input.text)
                                    Spacer(Modifier.height(4.dp))
                                    Text(inputSource(input) + " " + Time.relative(input.createdAt), style = RaType.caption, color = Ink.graphite)
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                            Spacer(Modifier.height(Ra.sectionGap))
                        }
                    }
                } else {
                    itemsIndexed(document.sections, key = { _, s -> s.id }) { index, section ->
                        AnimatedVisibility(
                            visible = index < revealed,
                            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 12 },
                        ) {
                            Column(Modifier.padding(horizontal = Ra.gutter)) {
                                if (index > 0) {
                                    Hairline()
                                    Spacer(Modifier.height(20.dp))
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    SectionHeading(section.heading.ifBlank { SectionIds.headings[section.id] ?: section.id }, Modifier.weight(1f))
                                    if (editable) {
                                        InlineAction("Edit", enabled = state.busyMessage == null, onClick = { nav.navigate(Routes.section(draftId, section.id)) })
                                        InlineAction("Regenerate", enabled = state.busyMessage == null, onClick = { vm.openRegenerate(section.id) })
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                if (section.body.isBlank()) SectionHint(section.hint ?: "Nothing here yet.")
                                else BodyText(section.body)
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                    }
                }
                item {
                    if (state.isLocalDraft && document != null) {
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    state.sheetSection?.let { sectionId ->
        val heading = document?.section(sectionId)?.heading ?: SectionIds.headings[sectionId] ?: "section"
        RegenerateSheet(
            heading = heading,
            busyMessage = state.busyMessage,
            busyProgress = state.busyProgress,
            onDismiss = { vm.closeRegenerate() },
            onRegenerate = { vm.regenerate(sectionId, it) },
        )
    }

    if (chooser && draft != null) {
        DescribeChooserSheet(
            projectTitle = state.title.ifBlank { "this project" },
            onPick = { tab ->
                chooser = false
                nav.navigate(Routes.capture(draft.id, if (document != null) "append" else "new", tab))
            },
            onDismiss = { chooser = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Ink.surface,
            title = { Text("Delete this draft?", style = RaType.heading, color = Ink.ink) },
            text = { Text("The description, the brief and all its versions are deleted. This cannot be undone.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDraft() }) { Text("Delete", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = RaType.button, color = Ink.ink) } },
        )
    }
}

/** Screening refused the project: say so calmly, say nothing was charged, say where to appeal. */
@Composable
private fun RefusedBlock(reason: String?, supportEmail: String) {
    Column {
        SectionHeading("We can't take this project on")
        Spacer(Modifier.height(12.dp))
        if (!reason.isNullOrBlank()) {
            BodyText(reason)
            Spacer(Modifier.height(8.dp))
        }
        val appeal = supportEmail.isNotBlank() && !StatusCopy.hasAppeal(reason)
        Text(
            "Nothing has been charged." + if (appeal) " If you think this is a mistake, write to $supportEmail." else "",
            style = RaType.secondary,
            color = Ink.graphite,
        )
    }
}

private fun inputSource(input: com.raviga.app.data.drafts.LocalInput): String = when (input.kind) {
    "voice" -> "Spoken"
    "file" -> "From ${input.fileName ?: "a document"}"
    else -> "Typed"
}.let { if (input.kind == "file") "$it," else it }

@Composable
private fun EditableTitle(title: String, editable: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(title) { mutableStateOf(title) }
    val focus = remember { FocusRequester() }
    if (editing) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = RaType.title.copy(color = Ink.ink),
            cursorBrush = SolidColor(Ink.link),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { editing = false; if (text.trim() != title) onSave(text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    } else {
        Text(
            title,
            style = RaType.title,
            color = Ink.ink,
            modifier = Modifier.fillMaxWidth().then(if (editable) Modifier.clickable { editing = true } else Modifier),
        )
    }
}

@Composable
private fun StatusLine(state: DocumentViewModel.State, nav: NavController) {
    val project = state.project
    val draft = state.draft
    when {
        project?.status == ProjectStatus.CHANGES_REQUESTED -> Text(
            "The team left comments. Read them, edit, then get a new quote.",
            style = RaType.secondary, color = Ink.link,
            modifier = Modifier.clickable { nav.navigate(Routes.status(project.id)) },
        )
        project?.contentDeletedAt != null && draft != null -> Text(
            "${StatusCopy.rowLine(project)}. Raviga deleted its copy; this one is kept only on this phone.",
            style = RaType.secondary, color = Ink.graphite,
        )
        project != null && project.submission?.submittedAt != null -> Text(
            "Submitted on ${Time.shortDate(project.submission.submittedAt)}, locked",
            style = RaType.secondary, color = Ink.graphite,
        )
        project != null -> Text(StatusCopy.rowLine(project), style = RaType.secondary, color = Ink.graphite)
        draft != null -> Text(
            "Draft, on this phone only. Edited ${Time.relative(draft.updatedAt)}",
            style = RaType.secondary, color = Ink.graphite,
        )
    }
}

