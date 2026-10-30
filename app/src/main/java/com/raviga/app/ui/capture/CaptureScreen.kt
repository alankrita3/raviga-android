package com.raviga.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.raviga.app.data.audio.DictationEngine
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raviga.app.data.screening.SensitiveScan
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import androidx.compose.foundation.layout.offset
import com.raviga.app.ui.components.Picture
import com.raviga.app.ui.components.SpotIllustration
import com.raviga.app.ui.components.PrivacyNote
import com.raviga.app.ui.components.BoxedEditor
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.InkLine
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.MicButton
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ProgressRule
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.Segmented
import com.raviga.app.ui.components.TertiaryButton
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

private const val HINT = "Tell us what it does, who it is for, and anything it must connect to. Ramble is fine."

@Composable
fun CaptureScreen(nav: NavController, projectId: String, mode: String, tab: String = DescribeWith.SPEAK) {
    val container = LocalAppContainer.current
    val vm: CaptureViewModel = viewModel(key = "capture_$projectId$mode") { CaptureViewModel(container, projectId, mode, tab) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config by container.session.config.collectAsStateWithLifecycle()

    // Upload: the system picker, filtered to what the backend reads. "text/*" catches
    // Markdown files that providers label loosely; anything unreadable is refused politely.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickedFile(uri)
    }
    val pickFile = { picker.launch((config.limits.acceptedFileTypes + "text/*").distinct().toTypedArray()) }
    // Chosen "Upload a document" in the chooser: open the picker straight away, once.
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (tab == DescribeWith.UPLOAD && !autoPicked) {
            autoPicked = true
            pickFile()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleListening()
    }
    fun onMic() {
        if (state.listening) { vm.toggleListening(); return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.toggleListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // AI consent round trip.
    val consentResult = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("ai_consent_granted", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(consentResult?.value) {
        if (consentResult?.value == true) {
            nav.currentBackStackEntry?.savedStateHandle?.set("ai_consent_granted", false)
            vm.consentGranted()
        }
    }
    LaunchedEffect(state.needsConsent) {
        if (state.needsConsent) {
            vm.consentRequested()
            nav.navigate(Routes.AI_CONSENT)
        }
    }

    LaunchedEffect(state.done) {
        val id = state.done ?: return@LaunchedEffect
        nav.navigate(Routes.document(id, reveal = true)) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    val captureBack = captureBack(vm, state)
    BackHandler(enabled = state.phase == CaptureViewModel.Phase.TRANSCRIPT || state.phase == CaptureViewModel.Phase.DRAFTING || captureBack != null) {
        when (state.phase) {
            CaptureViewModel.Phase.TRANSCRIPT -> if (!state.reading && !state.saving) vm.recordMore()
            CaptureViewModel.Phase.CAPTURE -> captureBack?.invoke()
            else -> Unit
        }
    }

    AnimatedContent(targetState = state.phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { phase ->
        when (phase) {
            CaptureViewModel.Phase.CAPTURE -> CapturePhase(nav, vm, state, ::onMic, pickFile, config.limits.fileMaxBytes)
            CaptureViewModel.Phase.TRANSCRIPT -> TranscriptPhase(vm, state)
            CaptureViewModel.Phase.CHOOSE -> ChoosePhase(nav, vm, state, onPickFile = pickFile)
            CaptureViewModel.Phase.DRAFTING -> DraftingPhase(state)
        }
    }

    state.sensitive?.let { title ->
        SensitiveSheet(
            title = title,
            onRemove = { vm.removeSensitive() },
            onKeep = { vm.keepSensitive() },
            onEdit = { vm.editSensitive() },
        )
    }
}

/**
 * Where back goes from capture when it shouldn't leave the screen: continuing a note returns to
 * its review; a new part after a saved document returns to the choices. Null leaves.
 */
private fun captureBack(vm: CaptureViewModel, state: CaptureViewModel.State): (() -> Unit)? = when {
    state.phase != CaptureViewModel.Phase.CAPTURE -> null
    state.continuing -> { { if (state.listening) vm.stopListening(); vm.finishCapture() } }
    state.savedNotes > 0 -> { { vm.backToChoices() } }
    else -> null
}

/** "English (US) ▾": which English accent the phone listens for. */
@Composable
private fun AccentPicker(accent: DictationEngine.Accent, enabled: Boolean, onPick: (DictationEngine.Accent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }
                .semantics { contentDescription = "Speaking in ${accent.label}" }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(accent.label, style = RaType.caption, color = if (enabled) Ink.graphite else Ink.ash)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = Ink.ash, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Ink.surface) {
            DictationEngine.Accent.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = RaType.body, color = if (option == accent) Ink.link else Ink.ink) },
                    onClick = { open = false; onPick(option) },
                )
            }
        }
    }
}

/** Layer-0 warning: secrets or ID numbers found in what is about to be saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensitiveSheet(title: String, onRemove: () -> Unit, onKeep: () -> Unit, onEdit: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onEdit,
        sheetState = sheet,
        containerColor = Ink.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Ra.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text(title, style = RaType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text(SensitiveScan.MESSAGE, style = RaType.body, color = Ink.graphite)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Remove them", onClick = onRemove)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Keep as is", onClick = onKeep)
            TertiaryButton("Edit", onClick = onEdit)
        }
    }
}

@Composable
private fun CapturePhase(
    nav: NavController,
    vm: CaptureViewModel,
    state: CaptureViewModel.State,
    onMic: () -> Unit,
    onPickFile: () -> Unit,
    fileMaxBytes: Long,
) {
    val canFinish = when (state.tab) {
        CaptureViewModel.TAB_SPEAK -> state.hasSpeech
        CaptureViewModel.TAB_TYPE -> state.typed.isNotBlank()
        else -> false
    }
    ScreenScaffold(
        topBar = {
            RaTopBar(
                title = if (vm.isAppend) "Add more" else null,
                onBack = captureBack(vm, state) ?: { if (state.listening) vm.stopListening(); nav.popBackStack(); Unit },
                actions = { if (canFinish && !state.listening && state.tab == CaptureViewModel.TAB_SPEAK) com.raviga.app.ui.components.InlineAction("Done", onClick = { vm.finishCapture() }) },
            )
        },
        bottomBar = {
            when (state.tab) {
                CaptureViewModel.TAB_TYPE -> BottomBar { PrimaryButton("Done", enabled = canFinish, onClick = { vm.finishCapture() }) }
                CaptureViewModel.TAB_UPLOAD -> BottomBar { PrimaryButton("Choose a file", onClick = onPickFile) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Ra.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Continuing a note stays in its mode, so the tabs make way for what is happening.
                Box(Modifier.weight(1f)) {
                    if (state.continuing) {
                        Text(if (state.tab == CaptureViewModel.TAB_SPEAK) "Recording more" else "Adding more", style = RaType.bodyMedium, color = Ink.ink, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        Segmented(listOf("Speak", "Type", "Upload"), state.tab, onSelect = { vm.selectTab(it) })
                    }
                }
                if (state.tab == CaptureViewModel.TAB_SPEAK) AccentPicker(state.accent, enabled = !state.listening, onPick = { vm.selectAccent(it) })
            }
            Spacer(Modifier.height(24.dp))
            if (state.tab == CaptureViewModel.TAB_UPLOAD) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("Upload a requirements document", style = RaType.heading, color = Ink.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A PDF, Word, RTF or text file up to ${fileMaxBytes / 1_048_576} MB. We read the text, you check it, and the brief is written from it.",
                        style = RaType.secondary,
                        color = Ink.graphite,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrivacyNote(FILE_NOTE)
                    InlineNotice(state.error)
                }
            } else if (state.tab == CaptureViewModel.TAB_SPEAK) {
                val speech = state.speech
                // Dictation lands above the ink line; the newest sentence in ink, earlier ones in graphite.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true)) {
                    if (speech is CaptureViewModel.Speech.Unavailable || speech is CaptureViewModel.Speech.NotYet) {
                        val message = (speech as? CaptureViewModel.Speech.Unavailable)?.message ?: (speech as CaptureViewModel.Speech.NotYet).message
                        Text(message, style = RaType.dictationItalic, color = Ink.graphite)
                        Spacer(Modifier.height(16.dp))
                        if (!state.continuing) Row {
                            com.raviga.app.ui.components.InlineAction("Type instead", onClick = { vm.selectTab(CaptureViewModel.TAB_TYPE) })
                            com.raviga.app.ui.components.InlineAction("Upload a document", onClick = { vm.selectTab(CaptureViewModel.TAB_UPLOAD) })
                        }
                    } else if (!state.hasSpeech && !state.listening) {
                        Text(HINT, style = RaType.dictationItalic, color = Ink.ash)
                        Spacer(Modifier.height(12.dp))
                        Text("Your voice is turned into text on this phone. No recording is kept or sent.", style = RaType.caption, color = Ink.graphite)
                    } else {
                        state.committed.dropLast(if (state.partial.isBlank()) 1 else 0).forEach {
                            Text(it, style = RaType.dictation, color = Ink.graphite)
                            Spacer(Modifier.height(8.dp))
                        }
                        val latest = if (state.partial.isNotBlank()) state.partial else state.committed.lastOrNull()
                        if (latest != null) Text(latest, style = RaType.dictation, color = Ink.ink)
                    }
                }
                InkLine(level = state.level, active = state.listening)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(state.elapsedSec), style = RaType.caption, color = if (state.listening) Ink.link else Ink.graphite)
                }
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MicButton(
                        recording = state.listening,
                        onClick = { if (speech is CaptureViewModel.Speech.NotYet) vm.checkSpeech() else onMic() },
                        icon = Icons.Outlined.Mic,
                        enabled = speech is CaptureViewModel.Speech.Ready || speech is CaptureViewModel.Speech.NotYet,
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    canFinish && !state.listening -> TertiaryButton("Done, show me the text", onClick = { vm.finishCapture() })
                    vm.offersSample && !state.listening && !state.continuing -> TertiaryButton("Use a sample description", onClick = { vm.useSample() }, color = Ink.graphite)
                    else -> Spacer(Modifier.height(Ra.buttonHeight))
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    BoxedEditor(
                        value = state.typed,
                        onValueChange = { vm.setTyped(it) },
                        placeholder = HINT,
                        minHeight = 240.dp,
                    )
                }
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TranscriptPhase(vm: CaptureViewModel, state: CaptureViewModel.State) {
    val fromFile = state.tab == CaptureViewModel.TAB_UPLOAD
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusEditor) { if (state.focusEditor > 0) runCatching { focus.requestFocus() } }
    if (state.reading) {
        // Reading a document on the phone.
        ScreenScaffold(topBar = { RaTopBar() }) { padding ->
            Processing(
                title = state.progressMessage ?: "Reading your document",
                line = "Read on your phone. Long documents take a little longer.",
                progress = state.progress,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }
    ScreenScaffold(
        topBar = { RaTopBar(onBack = { if (!state.saving) vm.recordMore() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                PrimaryButton(
                    if (vm.isAppend) "Looks right, add it to the brief" else "Looks right, write the brief",
                    enabled = !state.saving && state.transcript.isNotBlank(),
                    onClick = { vm.writeBrief() },
                )
                Spacer(Modifier.height(10.dp))
                // A document is its own note: "Add more" saves it, then any way adds the next part.
                // Back from a document picks another file instead.
                SecondaryButton(
                    when (state.tab) {
                        CaptureViewModel.TAB_SPEAK -> "Record more"
                        CaptureViewModel.TAB_UPLOAD -> if (state.saving) "Saving" else "Add more"
                        else -> "Edit what I typed"
                    },
                    enabled = !state.saving && (!fromFile || state.transcript.isNotBlank()),
                    onClick = { if (fromFile) vm.addMore() else vm.recordMore() },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(16.dp))
            Text(
                when (state.tab) {
                    CaptureViewModel.TAB_UPLOAD -> "Here's what we read"
                    CaptureViewModel.TAB_TYPE -> "Here's your description"
                    else -> "Here's what we heard"
                },
                style = RaType.title,
                color = Ink.ink,
            )
            Spacer(Modifier.height(6.dp))
            val file = state.file
            if (fromFile && file != null) {
                Text(fileSource(file), style = RaType.caption, color = Ink.graphite)
                val notice = file.notice ?: if (file.truncated) "Only the first part fit. Add the rest as another note." else null
                if (notice != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(notice, style = RaType.secondary, color = Ink.amber)
                }
            }
            Spacer(Modifier.height(14.dp))
            BoxedEditor(
                value = state.transcript,
                onValueChange = { vm.setTranscript(it) },
                placeholder = HINT,
                modifier = Modifier.focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    fromFile -> "Tap to fix anything before we write the brief."
                    state.tab == CaptureViewModel.TAB_TYPE -> "Tap to edit before we write the brief."
                    else -> "Tap to fix anything that was misheard."
                },
                style = RaType.caption,
                color = Ink.ash,
            )
            if (fromFile) {
                Spacer(Modifier.height(16.dp))
                Text(FILE_KEPT, style = RaType.caption, color = Ink.graphite)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Something is happening on the phone or with the AI: the writing picture, a title, progress. */
@Composable
private fun Processing(title: String, line: String, progress: Float?, modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Ra.gutter)) {
        Spacer(Modifier.height(24.dp))
        SpotIllustration(Picture.Writing, size = 220.dp, modifier = Modifier.offset(x = (-16).dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = RaType.title, color = Ink.ink)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = RaType.secondary, color = Ink.link)
        }
        Spacer(Modifier.height(20.dp))
        ProgressRule(progress = progress?.takeIf { it > 0f })
        Spacer(Modifier.height(14.dp))
        Text(line, style = RaType.secondary, color = Ink.graphite)
    }
}

/** After a document was saved as a note: add the next part any way, or write the brief now. */
@Composable
private fun ChoosePhase(nav: NavController, vm: CaptureViewModel, state: CaptureViewModel.State, onPickFile: () -> Unit) {
    val title = remember { vm.appendTitle() }
    ScreenScaffold(
        topBar = { RaTopBar(title = if (vm.isAppend) "Add more" else null, onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                Text(savedLine(state), style = RaType.caption, color = Ink.graphite)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(if (vm.isAppend) "Add it to the brief now" else "Write the brief now", onClick = { vm.writeSavedNotes() })
            }
        },
    ) { padding ->
        DescribeChoices(
            projectTitle = title,
            onPick = { picked ->
                when (picked) {
                    DescribeWith.TYPE -> vm.choose(CaptureViewModel.TAB_TYPE)
                    DescribeWith.UPLOAD -> { vm.choose(CaptureViewModel.TAB_UPLOAD); onPickFile() }
                    else -> vm.choose(CaptureViewModel.TAB_SPEAK)
                }
            },
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
        )
    }
}

/** "Saved brief.pdf to your draft." or, after several, "Saved 2 notes to your draft." */
private fun savedLine(state: CaptureViewModel.State): String = when {
    state.savedNotes > 1 -> "Saved ${state.savedNotes} notes to your draft."
    state.lastSavedName != null -> "Saved ${state.lastSavedName} to your draft."
    else -> "Saved to your draft."
}

@Composable
private fun DraftingPhase(state: CaptureViewModel.State) {
    ScreenScaffold(topBar = { RaTopBar() }) { padding ->
        Processing(
            title = "Writing your brief",
            message = state.progressMessage,
            line = "This takes a minute or two.",
            progress = state.progress,
            modifier = Modifier.padding(padding),
        )
    }
}

private const val FILE_NOTE = "It's read on your phone. The file never leaves it, and only the text you check is kept, in your draft."
private const val FILE_KEPT = "Only the text is kept. The file is deleted as soon as it's read, and is never shared with the team."

/** "From brief.pdf, 4 pages"; pages are left out when the backend can't count them. */
private fun fileSource(file: CaptureViewModel.FileRead): String = buildString {
    append("From ${file.fileName}")
    file.pageCount?.let { append(", $it ${if (it == 1) "page" else "pages"}") }
}

private fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
