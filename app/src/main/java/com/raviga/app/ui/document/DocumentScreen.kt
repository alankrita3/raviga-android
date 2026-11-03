package com.raviga.app.ui.document

import com.raviga.app.ui.components.SaveFailingNotice
import com.raviga.app.ui.nav.openProjectScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.material.icons.outlined.ChevronRight
import com.raviga.app.ui.components.tile
import com.raviga.app.ui.components.BoxedEditor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
    // The note open for editing (a draft with no brief yet, or one not in the brief yet), by input id.
    // R-092 #2: an open note survives the app being ended in the background (with its text, below).
    var openNote by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    // Notes saved on the phone that the written brief doesn't reflect yet, and their list.
    val pendingNotes = if (document != null && editable) draft?.newInputs.orEmpty() else emptyList()
    var pendingSheet by remember { mutableStateOf(false) }
    var backToPending by remember { mutableStateOf(false) }

    // The document reveal: sections fade in one after another, 90 ms apart, once.
    var revealed by remember { mutableIntStateOf(if (reveal) -1 else Int.MAX_VALUE) }
    LaunchedEffect(reveal, document?.version) {
        if (reveal && document != null && revealed < 0) {
            for (i in 0..document.sections.size) {
                revealed = i
                delay(90)
            }
            revealed = Int.MAX_VALUE
        }
    }

    // AI consent round trip (R-056 order): opened right before the AI call, which carries on when it's allowed.
    val aiConsent = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("ai_consent_granted", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(aiConsent?.value) {
        if (aiConsent?.value == true) {
            nav.currentBackStackEntry?.savedStateHandle?.set("ai_consent_granted", false)
            vm.aiConsentGranted()
        }
    }
    LaunchedEffect(state.needsAiConsent) {
        if (state.needsAiConsent) {
            vm.aiConsentRequested()
            nav.navigate(Routes.AI_CONSENT)
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
                val saveFailing by com.raviga.app.ui.LocalAppContainer.current.draftStore.saveFailing.collectAsStateWithLifecycle()
                SaveFailingNotice(saveFailing, state.error, Modifier.padding(bottom = 8.dp))   // R-094 P2-4
                when {
                    rejected -> PrimaryButton("Delete draft", onClick = { confirmDelete = true })
                    // R-098 P3-4: while a submit is held, read-only, with the way back to its quote (as iOS's See quote).
                    state.held -> {
                        Text(com.raviga.app.ui.quote.QuoteViewModel.HELD_LINE, style = RaType.secondary, color = Ink.graphite)
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton("See quote", onClick = { nav.navigate(Routes.quote(draftId)) })
                    }
                    editable && document != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            "Get a quote",
                            enabled = state.busyMessage == null,
                            onClick = { nav.navigate(Routes.quote(draftId)) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        AddMoreButton(enabled = state.busyMessage == null, onClick = { if (vm.canAddNote()) chooser = true })
                    }
                    // No brief yet: the same "+" adds voice, text or a document to the notes (her spec, 2026-11-02).
                    editable -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            "Write the brief",
                            enabled = state.busyMessage == null && draft?.inputs?.isNotEmpty() == true,
                            onClick = { vm.generate() },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        AddMoreButton(enabled = state.busyMessage == null, onClick = { if (vm.canAddNote()) chooser = true })
                    }
                    project != null -> PrimaryButton("View status", onClick = { nav.openProjectScreen(Routes.forProject(project.id, project.status)) })
                }
            }
        },
    ) { padding ->
        when {
            draft == null && project == null && state.error != null -> ErrorState(state.error.orEmpty(), Modifier.padding(padding), onRetry = { vm.refresh() })
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
                            Text(state.busyMessage.orEmpty(), style = RaType.secondary, color = Ink.link)
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
                            Text(state.busyMessage.orEmpty(), style = RaType.caption, color = Ink.link)
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
                        if (pendingNotes.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            PendingNotesLine(pendingNotes.size, onClick = if (state.busyMessage == null) ({ pendingSheet = true }) else null)
                        }
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
                                if (editable) SecondaryButton("Describe it", onClick = { if (vm.canAddNote()) chooser = true })
                            } else {
                                // Each note opens to be edited or deleted (on the phone only).
                                inputs.forEach { input ->
                                    Column(Modifier.tile(onClick = if (editable && state.busyMessage == null) ({ openNote = input.id }) else null)) {
                                        Text(input.text, style = RaType.body, color = Ink.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(6.dp))
                                        Text(inputSource(input) + " " + Time.relative(input.createdAt), style = RaType.caption, color = Ink.graphite)
                                    }
                                    Spacer(Modifier.height(Ra.tileGap))
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
                                val heading = section.heading.ifBlank { SectionIds.headings[section.id] ?: section.id }
                                val actions: @Composable () -> Unit = {
                                    if (editable) {
                                        // QA R4: TalkBack says which section ("Edit Summary"), not "Edit" eight times.
                                        InlineAction(
                                            "Edit", enabled = state.busyMessage == null, onClick = { nav.navigate(Routes.section(draftId, section.id)) },
                                            modifier = Modifier.semantics { contentDescription = "Edit $heading" },
                                        )
                                        InlineAction(
                                            "Regenerate", enabled = state.busyMessage == null, onClick = { vm.openRegenerate(section.id) },
                                            modifier = Modifier.semantics { contentDescription = "Regenerate $heading" },
                                        )
                                    }
                                }
                                // QA R1: at the large text sizes the actions go under the heading, so it never breaks mid-word.
                                if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                                    SectionHeading(heading)
                                    Row(Modifier.offset(x = (-8).dp)) { actions() }
                                } else {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        SectionHeading(heading, Modifier.weight(1f))
                                        actions()
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

    if (pendingSheet && pendingNotes.isNotEmpty()) {
        PendingNotesSheet(
            notes = pendingNotes,
            onOpen = { pendingSheet = false; backToPending = true; openNote = it },
            onAdd = { pendingSheet = false; vm.generate() },
            onDismiss = { pendingSheet = false },
        )
    }

    openNote?.let { noteId ->
        val input = draft?.inputs?.firstOrNull { it.id == noteId }
        // Closing a note opened from the list goes back to the list (if any notes are left in it).
        val close = { openNote = null; if (backToPending) { backToPending = false; pendingSheet = true } }
        if (input == null) {
            close()
        } else {
            NoteSheet(
                text = input.text,
                maxChars = container.session.config.value.limits.textInputMaxChars,
                source = inputSource(input) + " " + Time.relative(input.createdAt),
                onSave = { vm.editNote(input.id, it); close() },
                onDelete = { vm.deleteNote(input.id); close() },
                onDismiss = close,
            )
        }
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

/** "Add more": a square plus beside the primary action. */
@Composable
private fun AddMoreButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Ra.buttonHeight),
        shape = RoundedCornerShape(Ra.buttonRadius),
        border = BorderStroke(1.5.dp, Ink.ruleStrong),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "Add more", tint = Ink.ink)
    }
}

/** "1 note not in the brief yet": opens the list of notes the written brief doesn't reflect yet. */
@Composable
private fun PendingNotesLine(count: Int, onClick: (() -> Unit)?) {
    Row(Modifier.tile(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(StatusCopy.notesNotInBrief(count), style = RaType.secondaryMedium, color = Ink.link, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(22.dp))
    }
}

/** The notes not in the brief yet: tap one to edit or delete it, or add them all to the brief. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingNotesSheet(
    notes: List<com.raviga.app.data.drafts.LocalInput>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ra.gutter)
                .padding(top = 28.dp, bottom = 12.dp)
                .navigationBarsPadding(),
        ) {
            Text("Not in the brief yet", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(6.dp))
            Text("Saved on this phone. Tap a note to edit or delete it.", style = RaType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            notes.forEach { input ->
                Column(Modifier.tile(onClick = { onOpen(input.id) })) {
                    Text(input.text, style = RaType.body, color = Ink.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(inputSource(input) + " " + Time.relative(input.createdAt), style = RaType.caption, color = Ink.graphite)
                }
                Spacer(Modifier.height(Ra.tileGap))
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(StatusCopy.addNotesToBrief(notes.size), onClick = onAdd)
        }
    }
}

/** One note of a draft with no brief yet: edit its text, or delete it after a confirm. Nothing leaves the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(text: String, source: String, maxChars: Int, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    // R-092 #2 (iOS review): the edit survives the app being ended in the background, and closing with an unsaved
    // change asks first (swipe-down is held while there is one).
    var value by androidx.compose.runtime.saveable.rememberSaveable(text) { mutableStateOf(text) }
    val changed = value.trim() != text.trim()
    val changedNow by androidx.compose.runtime.rememberUpdatedState(changed)
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (changed) confirmDiscard = true else onDismiss() }
    // R-092 #32 (iOS review): the note limit is checked here, before saving, not by the next AI call's 400.
    val tooLong = com.raviga.app.ui.capture.CaptureViewModel.overLimit(value, maxChars)
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = leave,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || !changedNow },
        ),
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ra.gutter)
                .padding(top = 28.dp, bottom = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text("Your note", style = RaType.title, color = Ink.ink)
            Spacer(Modifier.height(6.dp))
            Text(source, style = RaType.caption, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            BoxedEditor(value = value, onValueChange = { value = it }, minHeight = 160.dp, label = "Your note")
            InlineNotice(tooLong)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save", enabled = tooLong == null && value.isNotBlank() && value.trim() != text.trim(), onClick = { onSave(value) })
            Spacer(Modifier.height(4.dp))
            DestructiveButton("Delete note", onClick = { confirm = true })
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Ink.surface,
            title = { Text("Discard your changes?", style = RaType.heading, color = Ink.ink) },
            text = { Text("What you changed in this note isn't saved.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Discard", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing", style = RaType.button, color = Ink.ink) } },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Ink.surface,
            title = { Text("Delete this note?", style = RaType.heading, color = Ink.ink) },
            text = { Text("It's removed from this draft on your phone. This cannot be undone.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", style = RaType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep", style = RaType.button, color = Ink.ink) } },
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
    // Review #3 (Android): a title being typed survives a font-size change, split screen or the app being ended.
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(title) { mutableStateOf(title) }
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
            modifier = Modifier.fillMaxWidth().then(if (editable) Modifier.clickable(onClickLabel = "Rename", role = Role.Button) { editing = true } else Modifier),   // review #20
        )
    }
}

@Composable
private fun StatusLine(state: DocumentViewModel.State, nav: NavController) {
    val project = state.project
    val draft = state.draft
    when {
        project?.contentDeletedAt != null && draft != null -> Text(
            // v0.10.0: the statusLine ends with its own full stop ("Approved by you."); never two.
            "${StatusCopy.rowLine(project).trimEnd().trimEnd('.')}. Raviga deleted its copy; this one is kept only on this phone.",
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

