package com.raviga.app.ui.document

import com.raviga.app.ui.components.SaveFailingNotice
import com.raviga.app.ui.nav.Routes
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.SectionBody
import com.raviga.app.data.api.SectionIds
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.LocalAppContainer
import com.raviga.app.ui.components.BottomBar
import com.raviga.app.ui.components.RaTopBar
import com.raviga.app.ui.components.InlineAction
import com.raviga.app.ui.components.InlineNotice
import com.raviga.app.ui.components.PlainEditor
import com.raviga.app.ui.components.PrimaryButton
import com.raviga.app.ui.components.ScreenScaffold
import com.raviga.app.ui.components.SecondaryButton
import com.raviga.app.ui.components.SectionHeading
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink
import com.raviga.app.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits one section of a brief. R-092 (iOS review): #1 "Regenerate with AI" saves what was typed first and goes
 * through the same AI-consent and length checks as the brief; #2 the unsaved text survives the app being ended in the
 * background ([saved], restored on reopen) and leaving with changes asks first; #32 the section limit is checked on Save.
 */
class SectionEditorViewModel(
    private val container: AppContainer,
    private val projectId: String,
    private val sectionId: String,
    private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    data class State(
        val document: Document? = null,
        val heading: String = "",
        val hint: String? = null,
        val body: String = "",
        val saving: Boolean = false,
        val busyMessage: String? = null,
        val busyProgress: Float? = null,
        val sheet: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        /** The section's text as last saved, to tell an unsaved change. */
        val savedBody: String = "",
        val confirmDiscard: Boolean = false,
        /** AI processing isn't allowed yet: the screen opens the consent screen, then the rewrite runs. */
        val needsAiConsent: Boolean = false,
        /** R-124 (v0.16.0): this brief's AI edits left, and a pack to confirm (or credits to buy) when they're used up. */
        val aiEdits: com.raviga.app.data.api.AiEdits? = null,
    ) {
        val changed: Boolean get() = document != null && body.trim() != savedBody.trim()
    }

    private val _state = MutableStateFlow(State(heading = SectionIds.headings[sectionId] ?: ""))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.drafts.load()
            val doc = container.drafts.get(projectId)?.document
            val section = doc?.section(sectionId)
            val savedBody = section?.body.orEmpty()
            // An edit from before the app was ended in the background comes back as it was.
            val unsaved = saved.get<String>(KEY_BODY)
            _state.update {
                it.copy(document = doc, heading = section?.heading ?: it.heading, hint = section?.hint, body = unsaved ?: savedBody, savedBody = savedBody)
            }
        }
        viewModelScope.launch { container.drafts.draft(projectId).collect { d -> _state.update { it.copy(aiEdits = d?.aiEdits) } } }
    }

    fun setBody(text: String) {
        saved[KEY_BODY] = text
        _state.update { it.copy(body = text, error = null) }
    }

    /** Back or the top bar's arrow: leave, or ask first when there's an unsaved change. */
    fun leave(): Boolean {
        if (!_state.value.changed) return true
        _state.update { it.copy(confirmDiscard = true) }
        return false
    }

    fun keepEditing() = _state.update { it.copy(confirmDiscard = false) }

    fun discard() {
        saved.remove<String>(KEY_BODY)
        _state.update { it.copy(confirmDiscard = false, body = it.savedBody, saved = true) }
    }

    private fun tooLong(body: String): String? {
        val max = container.session.config.value.limits.textInputMaxChars
        return if (max > 0 && BriefLimits.length(body) > max) {
            "The ${_state.value.heading} section is over the ${BriefLimits.grouped(max)} character limit. Shorten it, then save."
        } else {
            null
        }
    }

    /** Turns every line into a bullet, or strips bullets if they all have one. */
    fun toggleBullets() {
        val lines = _state.value.body.lines()
        val content = lines.filter { it.isNotBlank() }
        val allBullets = content.isNotEmpty() && content.all { it.trimStart().startsWith("- ") }
        val next = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                allBullets -> line.trimStart().removePrefix("- ")
                line.trimStart().startsWith("- ") -> line
                else -> "- " + line.trim()
            }
        }
        // Review 2026-11-03 (Android): through setBody, so the bullets are kept with the unsaved text (R-092 #2).
        setBody(next)
    }

    fun save() {
        _state.value.document ?: return
        tooLong(_state.value.body)?.let { line -> _state.update { it.copy(error = line) }; return }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching { container.drafts.saveEdit(projectId, null, listOf(SectionBody(sectionId, _state.value.body.trim()))) }
                .onSuccess {
                    saved.remove<String>(KEY_BODY)
                    _state.update { it.copy(saving = false, saved = true, savedBody = it.body) }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.userLine()) } }
        }
    }

    fun openSheet() = _state.update { it.copy(sheet = true) }
    fun closeSheet() = _state.update { it.copy(sheet = false) }

    private var pendingInstruction: String? = null

    /** The consent screen has opened; don't open it again on return. */
    fun aiConsentRequested() = _state.update { it.copy(needsAiConsent = false) }

    /** Allowed: the rewrite that was asked for runs. */
    fun aiConsentGranted() {
        val instruction = pendingInstruction ?: return
        pendingInstruction = null
        regenerate(instruction)
    }

    /** R-124 (v0.16.0): AI edits used up (buy 5 more?), or short of the pack; only "Buy" charges. */
    val packs = EditPackFlow(container, viewModelScope)

    fun regenerate(instruction: String, buyEditPack: Boolean = false) {
        // The same checks as the brief's Regenerate: AI consent, then the length the AI call takes.
        if (container.session.needsAiConsent(container.session.me.value)) {
            pendingInstruction = instruction
            _state.update { it.copy(needsAiConsent = true) }
            return
        }
        tooLong(_state.value.body)?.let { line -> _state.update { it.copy(error = line, sheet = false) }; return }
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Rereading the section", busyProgress = null, error = null) }
            runCatching {
                // What was typed is saved first (a version), so the rewrite starts from it and nothing is lost.
                if (_state.value.changed) {
                    container.drafts.saveEdit(projectId, null, listOf(SectionBody(sectionId, _state.value.body.trim())))
                    saved.remove<String>(KEY_BODY)
                    _state.update { it.copy(savedBody = it.body) }
                }
                container.drafts.regenerate(projectId, sectionId, instruction, buyEditPack) { st ->
                    _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
                }
            }.onSuccess { draft ->
                val doc = draft.document
                val body = doc?.section(sectionId)?.body.orEmpty()
                _state.update { it.copy(document = doc, body = body, savedBody = body, busyMessage = null, busyProgress = null, sheet = false) }
                packs.handle(null, buyEditPack) {}   // a bought pack: the balance is read again
            }.onFailure { e ->
                // R-124: none left (or short of the pack): ask, with the sheet and what was typed in it kept.
                val asked = packs.handle(e, buyEditPack) { regenerate(instruction, buyEditPack = true) }
                _state.update {
                    it.copy(error = if (asked) null else e.userLine(), busyMessage = null, busyProgress = null, sheet = asked && it.sheet)
                }
            }
        }
    }

    private companion object {
        const val KEY_BODY = "unsaved_body"
    }
}

@Composable
fun SectionEditorScreen(nav: NavController, projectId: String, sectionId: String) {
    val container = LocalAppContainer.current
    val vm: SectionEditorViewModel = viewModel(key = "section_${projectId}_$sectionId") {
        SectionEditorViewModel(container, projectId, sectionId, createSavedStateHandle())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val credits = container.session.config.collectAsStateWithLifecycle().value.credits

    LaunchedEffect(state.saved) { if (state.saved) nav.popBackStack() }
    androidx.activity.compose.BackHandler(enabled = state.changed && !state.saved) { vm.leave() }
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

    ScreenScaffold(
        topBar = {
            RaTopBar(
                onBack = { if (vm.leave()) nav.popBackStack() },
                actions = { InlineAction("Bullets", onClick = { vm.toggleBullets() }) },
            )
        },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                val saveFailing by com.raviga.app.ui.LocalAppContainer.current.draftStore.saveFailing.collectAsStateWithLifecycle()
                SaveFailingNotice(saveFailing, state.error, Modifier.padding(bottom = 8.dp))   // R-094 P2-4
                PrimaryButton("Save", loading = state.saving, enabled = state.document != null && state.busyMessage == null, onClick = { vm.save() })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Regenerate with AI", enabled = state.document != null && state.busyMessage == null, onClick = { vm.openSheet() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Ra.gutter)) {
            Spacer(Modifier.height(8.dp))
            SectionHeading(state.heading)
            Spacer(Modifier.height(16.dp))
            PlainEditor(
                value = state.body,
                onValueChange = { vm.setBody(it) },
                placeholder = state.hint ?: "Write here. Start a line with \"- \" for a bullet.",
                minLines = 10,
                label = state.heading,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(8.dp))
            Text("Paragraphs and \"- \" bullets. Nothing else is needed.", style = RaType.caption, color = Ink.ash)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.keepEditing() },
            containerColor = Ink.surface,
            title = { Text("Discard your changes?", style = RaType.heading, color = Ink.ink) },
            text = { Text("What you typed in ${state.heading} isn't saved.", style = RaType.body, color = Ink.graphite) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.discard() }) { Text("Discard", style = RaType.button, color = Ink.brick) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.keepEditing() }) { Text("Keep editing", style = RaType.button, color = Ink.ink) } },
        )
    }

    if (state.sheet) {
        RegenerateSheet(
            heading = state.heading,
            busyMessage = state.busyMessage,
            busyProgress = state.busyProgress,
            onDismiss = { vm.closeSheet() },
            onRegenerate = { vm.regenerate(it) },
            editsLine = AiEditsCopy.regenerateLine(state.aiEdits, credits.aiEdits),
        )
    }

    EditPackDialogs(vm.packs, credits, nav)
}
