package com.raviga.app.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.Me
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.userLine
import com.raviga.app.util.Time
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Prices the brief on this phone and submits it (contract v0.6). The quote and
 * its token are kept with the local draft; submitting creates the server
 * project, and a "changes requested" round resubmits the edited brief.
 */
class QuoteViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {

    data class State(
        val draft: LocalDraft? = null,
        /** The submitted project, when this is a resubmit. */
        val project: Project? = null,
        val me: Me? = null,
        val config: AppConfig = AppConfig(),
        val balance: Int = 0,
        val quoting: Boolean = false,
        val progressMessage: String? = null,
        val progress: Float? = null,
        val github: String = "",
        val githubTouched: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        /** Server id of the project once submit landed. */
        val submittedProjectId: String? = null,
    ) {
        val quote get() = draft?.quote
        val isResubmit get() = project?.status == ProjectStatus.CHANGES_REQUESTED
        val alreadyCharged get() = project?.submission?.creditsCharged ?: 0
        /** Credits that will actually leave the balance now. */
        val due: Int get() = ((quote?.credits ?: 0) - if (isResubmit) alreadyCharged else 0)
        val shortBy: Int get() = (due - balance).coerceAtLeast(0)
        val rejected get() = draft?.isRefused == true || project?.isRejected == true
        val refusalReason get() = draft?.refusal?.reason ?: project?.screening?.reason
        /** No quote for this exact brief, or it ran out. */
        val needsNewQuote: Boolean
            get() = draft != null && (!draft.quoteIsCurrent || quote?.expiresAt?.let { Time.parse(it)?.isBefore(Instant.now()) } == true)
        val submitted get() = submittedProjectId != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var quotedOnce = false
    /** The quote already counted as viewed (contract v0.9 quote_viewed), so a re-collect doesn't log it twice. */
    private var viewedQuoteId: String? = null
    private val serverId = MutableStateFlow(projectId.takeIf { it.startsWith("pr_") })

    /**
     * One Idempotency-Key per quote token (the backend's rule), reused on retry: if
     * the first attempt charged credits but the response was lost, the retry replays
     * it instead of failing. Cleared once a submit lands.
     */
    private var submitKey: Pair<String, String>? = null

    private fun keyFor(body: String): String =
        submitKey?.takeIf { it.first == body }?.second
            ?: java.util.UUID.randomUUID().toString().also { submitKey = body to it }

    init {
        viewModelScope.launch {
            container.drafts.load()
            container.drafts.draft(projectId).collect { d ->
                _state.update { it.copy(draft = d) }
                d?.serverProjectId?.let { serverId.value = it }
                d?.quote?.takeIf { d.quoteIsCurrent && it.id != viewedQuoteId }?.let { q ->
                    viewedQuoteId = q.id
                    container.analytics.quoteViewed(q.bracketId, q.credits)
                }
                maybeQuote()
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            serverId.flatMapLatest { sid -> if (sid == null) flowOf(null) else container.projects.project(sid) }
                .collect { p -> _state.update { it.copy(project = p) } }
        }
        viewModelScope.launch {
            container.session.me.collect { me ->
                _state.update { s ->
                    s.copy(
                        me = me,
                        balance = container.credits.credits.value?.balance ?: me?.credits?.balance ?: s.balance,
                        github = if (!s.githubTouched && s.github.isBlank()) me?.deliveryTargets?.githubUsername.orEmpty() else s.github,
                    )
                }
            }
        }
        viewModelScope.launch { container.credits.credits.collect { c -> if (c != null) _state.update { it.copy(balance = c.balance) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch {
            serverId.value?.let { sid -> runCatching { container.projects.refresh(sid) } }
            runCatching { container.credits.refresh() }
            runCatching { container.session.refreshMe() }
        }
    }

    private fun maybeQuote() {
        val s = _state.value
        val d = s.draft ?: return
        val editable = d.serverProjectId == null || s.project?.status == ProjectStatus.CHANGES_REQUESTED || s.project == null
        if (s.needsNewQuote && d.document != null && editable && !s.rejected && !quotedOnce) requestQuote()
    }

    fun setGithub(value: String) = _state.update { it.copy(github = value, githubTouched = true, error = null) }

    fun requestQuote() {
        val draftId = _state.value.draft?.id ?: return
        if (_state.value.quoting) return
        quotedOnce = true
        viewModelScope.launch {
            _state.update { it.copy(quoting = true, progressMessage = "Sizing the work", progress = null, error = null) }
            runCatching {
                container.drafts.quote(draftId) { st ->
                    _state.update { it.copy(progressMessage = st.message ?: it.progressMessage, progress = st.progress) }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(quoting = false, progressMessage = null, progress = null) }
        }
    }

    fun submit() {
        val s = _state.value
        val draft = s.draft ?: return
        val github = s.github.trim()
        if (github.isBlank()) {
            _state.update { it.copy(error = "Enter your GitHub username so we can hand over the code.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                if (s.isResubmit) {
                    if (github != s.me?.deliveryTargets?.githubUsername) container.session.setDeliveryTargets(github, null)
                    container.drafts.resubmit(draft.id, keyFor(draft.quoteToken.orEmpty()))
                } else {
                    container.drafts.submit(draft.id, github, null, keyFor(draft.quoteToken.orEmpty()))
                }
            }.recoverCatching { e ->
                // A resubmit that landed under an earlier key shows up as invalid_state; check before calling it an error.
                val sid = draft.serverProjectId
                if ((e as? ApiException)?.code != ApiException.INVALID_STATE || sid == null) throw e
                val now = container.projects.refresh(sid)
                if (now.status != ProjectStatus.SUBMITTED && now.status != ProjectStatus.APPROVED) throw e
                now
            }.onSuccess { project ->
                submitKey = null
                // POST /projects only (contract v0.9); a resubmit is a different call and not counted.
                if (!s.isResubmit) container.analytics.projectSubmitted(project.quote?.bracketId ?: draft.quote?.bracketId, project.quote?.credits ?: draft.quote?.credits ?: 0)
                runCatching { container.credits.refresh() }
                runCatching { container.session.refreshMe() }
                _state.update { it.copy(submitting = false, submittedProjectId = project.id) }
            }.onFailure { e ->
                val line = when ((e as? ApiException)?.code) {
                    ApiException.QUOTE_STALE -> { requestQuote(); "The brief changed. Here is a fresh quote." }
                    else -> e.userLine()
                }
                _state.update { it.copy(submitting = false, error = line) }
            }
        }
    }
}
