package com.raviga.app.ui.document

import com.raviga.app.ui.capture.CaptureViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.di.AppContainer
import com.raviga.app.di.AppEvent
import com.raviga.app.ui.userLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The brief, wherever it lives (contract v0.6). [id] is a local draft id, or
 * a submitted project's server id: then the brief is this phone's copy if it
 * has one, else the copy the server holds until the project closes.
 */
class DocumentViewModel(private val container: AppContainer, val id: String) : ViewModel() {

    data class State(
        val draft: LocalDraft? = null,
        val project: Project? = null,
        val loading: Boolean = true,
        val error: String? = null,
        /** AI processing isn't allowed yet: the screen shows "Before we send this to AI", then the action runs. */
        val needsAiConsent: Boolean = false,
        val busyMessage: String? = null,
        val busyProgress: Float? = null,
        val sheetSection: String? = null,
        val deleted: Boolean = false,
        /** What the AI took out of the notes on the last write, shown once. */
        val notices: List<String> = emptyList(),
        val supportEmail: String = "",
    ) {
        val document: Document? get() = draft?.document ?: project?.document?.asDocument()
        val status: String get() = project?.status ?: ProjectStatus.DRAFT
        /** Not submitted yet: it exists only on this phone. */
        val isLocalDraft: Boolean get() = draft != null && draft.serverProjectId == null
        val refused: Boolean get() = draft?.isRefused == true || project?.isRejected == true
        val refusalReason: String? get() = draft?.refusal?.reason ?: project?.screening?.reason
        /** Drafts, and briefs the team sent back with changes, are edited here. */
        val editable: Boolean
            get() = draft != null && !refused && (draft.serverProjectId == null || project?.status == ProjectStatus.CHANGES_REQUESTED)
        /** The server deleted its copy when the project closed, and this phone has none. */
        val contentGone: Boolean get() = draft == null && project?.contentDeletedAt != null
        val title: String
            get() = (document?.title ?: draft?.title ?: project?.title).orEmpty().ifBlank {
                project?.ref?.takeIf { it.isNotBlank() }?.let { "Project $it" } ?: "Untitled project"
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Server id: the route's own for a submitted project, else the draft's once submitted. */
    private val serverId = MutableStateFlow(id.takeIf { it.startsWith("pr_") })

    init {
        val drafts = container.drafts
        viewModelScope.launch {
            drafts.draft(id).collect { d ->
                _state.update { it.copy(draft = d, loading = false) }
                d?.serverProjectId?.let { sid -> serverId.value = sid }
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            serverId.flatMapLatest { sid -> if (sid == null) flowOf(null) else container.projects.project(sid) }
                .collect { p -> _state.update { it.copy(project = p) } }
        }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(supportEmail = c.legal.supportEmail) } } }
        viewModelScope.launch {
            drafts.notices.map { it }.distinctUntilChanged().collect { all ->
                val draftId = _state.value.draft?.id ?: return@collect
                val list = all[draftId] ?: return@collect
                _state.update { it.copy(notices = list) }
                drafts.consumeNotices(draftId)
            }
        }
        viewModelScope.launch {
            container.events.collect { if (it is AppEvent.ProjectUpdated && it.projectId == serverId.value) refresh() }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            container.drafts.load()
            val sid = serverId.value ?: return@launch
            container.projects.warmProject(sid)
            runCatching { container.projects.refresh(sid) }
                .onSuccess { p ->
                    _state.update { it.copy(loading = false, error = null) }
                    // Sent back with changes to a phone without its own copy (recovered here): take the held copy.
                    if (p.status == ProjectStatus.CHANGES_REQUESTED && container.drafts.get(p.id) == null && p.document != null) {
                        runCatching { container.drafts.importSubmitted(p) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = if (it.project == null && it.draft == null) e.userLine() else null) } }
        }
    }

    fun openRegenerate(sectionId: String) = _state.update { it.copy(sheetSection = sectionId) }
    fun closeRegenerate() = _state.update { it.copy(sheetSection = null) }

    fun regenerate(sectionId: String, instruction: String) {
        if (briefTooLong()) return
        regenerateNow(sectionId, instruction)
    }

    private fun regenerateNow(sectionId: String, instruction: String) = withAiConsent {
        val draftId = _state.value.draft?.id ?: return@withAiConsent
        busy("Rereading the section", close = true) {
            container.drafts.regenerate(draftId, sectionId, instruction) { st ->
                _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
            }
        }
    }

    fun saveTitle(title: String) {
        val draft = _state.value.draft ?: return
        val clean = title.trim()
        // A brief always has a title; a draft's name can be cleared, and the first note names it again (R-082).
        if (clean.isBlank() && draft.document != null) return
        viewModelScope.launch {
            runCatching {
                if (draft.document != null) container.drafts.saveEdit(draft.id, clean, emptyList())
                else container.drafts.rename(draft.id, clean)
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    /** "Write the brief" for a draft with notes but no brief, and "Add it to the brief" for notes not in it yet. */
    fun generate() {
        // QA D4: any note over limits.textInputMaxChars is refused before the call, as in Capture.
        val max = container.session.config.value.limits.textInputMaxChars
        _state.value.draft?.inputs?.firstNotNullOfOrNull { CaptureViewModel.overLimit(it.text, max) }?.let { line ->
            _state.update { it.copy(error = line) }
            return
        }
        if (briefTooLong()) return
        writeBrief()
    }

    private fun writeBrief() = withAiConsent {
        val draftId = _state.value.draft?.id ?: return@withAiConsent
        busy("Reading your notes") {
            container.drafts.writeBrief(draftId) { st ->
                _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
            }
        }
    }

    fun deleteDraft() {
        val draftId = _state.value.draft?.id ?: return
        viewModelScope.launch {
            runCatching { container.drafts.delete(draftId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    /** Edits one note of a draft with no brief yet; the note lives only on the phone. */
    fun editNote(inputId: String, text: String) {
        val draftId = _state.value.draft?.id ?: return
        viewModelScope.launch {
            runCatching { container.drafts.editInput(draftId, inputId, text) }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    /** Deletes one note of a draft with no brief yet (after the screen's confirm). */
    fun deleteNote(inputId: String) {
        val draftId = _state.value.draft?.id ?: return
        viewModelScope.launch {
            runCatching { container.drafts.removeInput(draftId, inputId) }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** QA F5: "+" on a draft that already holds the most notes says so instead of opening the choices. */
    fun canAddNote(): Boolean {
        val count = _state.value.draft?.inputs?.size ?: return true
        val line = com.raviga.app.data.repo.DraftRepository.noteLimitProblem(count, container.session.config.value.limits.inputsPerProject)
            ?: return true
        _state.update { it.copy(error = line) }
        return false
    }

    /** QA H9: a written brief over the section or total limit is refused before the call, in plain words. */
    private fun briefTooLong(): Boolean {
        val doc = _state.value.draft?.document ?: return false
        val limits = container.session.config.value.limits
        val line = BriefLimits.problem(doc, limits.textInputMaxChars, limits.documentMaxChars) ?: return false
        _state.update { it.copy(error = line) }
        return true
    }

    // ----- AI consent right before the AI call (R-056 order; same on iOS) -----

    private var pendingAi: (() -> Unit)? = null

    /** Runs [action] now, or after "Before we send this to AI" when AI processing isn't allowed yet. */
    private fun withAiConsent(action: () -> Unit) {
        if (container.session.needsAiConsent(container.session.me.value)) {
            pendingAi = action
            _state.update { it.copy(needsAiConsent = true) }
        } else {
            action()
        }
    }

    /** The screen has opened the consent screen; don't open it again on return. */
    fun aiConsentRequested() = _state.update { it.copy(needsAiConsent = false) }

    /** Allowed: carry on with what was asked. */
    fun aiConsentGranted() {
        val action = pendingAi
        pendingAi = null
        action?.invoke()
    }

    private fun busy(message: String, close: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = message, busyProgress = null, error = null) }
            runCatching { block() }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busyMessage = null, busyProgress = null, sheetSection = if (close) null else it.sheetSection) }
        }
    }
}
