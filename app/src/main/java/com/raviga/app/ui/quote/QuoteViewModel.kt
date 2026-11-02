package com.raviga.app.ui.quote

import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.raviga.app.ui.document.BriefLimits
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
        /** AI processing isn't allowed yet: the screen shows "Before we send this to AI", then the action runs. */
        val needsAiConsent: Boolean = false,
        /** Server id of the project once submit landed. */
        val submittedProjectId: String? = null,
        /** The server refused the quote at submit (409 quote_stale): its own words, which say which case (contract 11). */
        val staleMessage: String? = null,
        /** Now, as of the last tick: moves on when the quote's expiry passes, so the screen changes without a tap. */
        val clock: Long = System.currentTimeMillis(),
    ) {
        val quote get() = draft?.quote
        val isResubmit get() = project?.status == ProjectStatus.CHANGES_REQUESTED
        val alreadyCharged get() = project?.submission?.creditsCharged ?: 0
        /** Credits that will actually leave the balance now. */
        val due: Int get() = ((quote?.credits ?: 0) - if (isResubmit) alreadyCharged else 0)
        val shortBy: Int get() = (due - balance).coerceAtLeast(0)
        val rejected get() = draft?.isRefused == true || project?.isRejected == true
        val refusalReason get() = draft?.refusal?.reason ?: project?.screening?.reason
        /** The brief changed since this quote. */
        val briefChanged: Boolean get() = draft != null && !draft.quoteIsCurrent
        /** The quote ran out (QA I3: from expiresAt, before any submit). */
        val quoteExpired: Boolean get() = quote?.expiresAt?.let { Time.parse(it)?.toEpochMilli() }?.let { it <= clock } == true
        /** No quote for this exact brief, it ran out, or the server refused it. */
        val needsNewQuote: Boolean get() = draft != null && (briefChanged || quoteExpired || staleMessage != null)
        /** Why a new quote is needed, in iOS's words; the server's own message when it refused the quote. */
        val staleLine: String?
            get() = when {
                !needsNewQuote -> null
                briefChanged -> "The brief changed since this quote."
                quoteExpired -> "This quote has expired. Get a new one before submitting."
                else -> staleMessage
            }
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

    /** QA H9: a brief over the section or total limit is refused before the call, in plain words. */
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

    private fun keyFor(body: String): String =
        submitKey?.takeIf { it.first == body }?.second
            ?: java.util.UUID.randomUUID().toString().also { submitKey = body to it }

    init {
        viewModelScope.launch {
            container.drafts.load()
            container.drafts.draft(projectId).collect { d ->
                // A new quote clears the server's refusal of the old one.
                _state.update { it.copy(draft = d, staleMessage = it.staleMessage.takeIf { _ -> it.draft?.quote?.id == d?.quote?.id }) }
                watchExpiry(d?.quote?.expiresAt)
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
                .collect { p ->
                    _state.update { it.copy(project = p) }
                    maybeQuote()   // a resubmit's project status arrives after the draft
                }
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
        // Signed out on this phone (R-079): nothing to refresh until the account comes back with the next AI step.
        if (container.session.hasAccount) viewModelScope.launch {
            serverId.value?.let { sid -> runCatching { container.projects.refresh(sid) } }
            runCatching { container.credits.refresh() }
            runCatching { container.session.refreshMe() }
        }
    }

    /**
     * Opening the quote for a brief with no current quote asks for one. Never while or after submitting: a submit
     * clears the quote token, and with the project not loaded yet this used to ask for a new quote, an AI call nobody
     * asked for (QA L1, 2026-11-03). A submitted brief is quoted again only once the team asked for changes.
     */
    private fun maybeQuote() {
        val s = _state.value
        val d = s.draft ?: return
        if (s.submitting || s.submittedProjectId != null) return
        // R-083: a quote job from before the app closed is collected (resumeQuoteOnce), never asked for twice.
        if (d.pendingJob != null) {
            if (d.pendingJob.kind == com.raviga.app.data.drafts.PendingJob.QUOTE) resumeQuoteOnce()
            return
        }
        val editable = d.serverProjectId == null || s.project?.status == ProjectStatus.CHANGES_REQUESTED
        if (s.needsNewQuote && d.document != null && editable && !s.rejected && !quotedOnce) requestQuote()
    }

    private var resumedQuote = false

    private fun resumeQuoteOnce() {
        if (resumedQuote || _state.value.quoting) return
        resumedQuote = true
        val draftId = _state.value.draft?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(quoting = true, progressMessage = "Sizing the work", progress = null, error = null) }
            runCatching {
                container.drafts.resumePending(draftId, { st ->
                    _state.update { it.copy(progressMessage = st.message ?: it.progressMessage, progress = st.progress) }
                })
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(quoting = false, progressMessage = null, progress = null) }
        }
    }

    fun setGithub(value: String) = _state.update { it.copy(github = value, githubTouched = true, error = null) }

    fun requestQuote() {
        if (briefTooLong()) return
        requestQuoteNow()
    }

    private var expiryJob: Job? = null
    private var watchedExpiry: String? = null

    /** Ticks the clock when the quote's expiry passes, so "Submit" turns into "Get a new quote" on its own. */
    private fun watchExpiry(expiresAt: String?) {
        if (expiresAt == watchedExpiry) return
        watchedExpiry = expiresAt
        expiryJob?.cancel()
        val at = expiresAt?.let { Time.parse(it)?.toEpochMilli() } ?: return
        expiryJob = viewModelScope.launch {
            val wait = at - System.currentTimeMillis()
            if (wait > 0) delay(wait + 500)
            tick()
        }
    }

    /** Also on return to the screen. */
    fun tick() = _state.update { it.copy(clock = System.currentTimeMillis()) }

    private fun requestQuoteNow() = withAiConsent {
        val draftId = _state.value.draft?.id ?: return@withAiConsent
        if (_state.value.quoting) return@withAiConsent
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
        // R-092 #21 (iOS review): two taps in one frame start one submit.
        if (_state.value.submitting || _state.value.submittedProjectId != null) return
        if (briefTooLong()) return
        submitNow()
    }

    private fun submitNow() = withAiConsent {
        val s = _state.value
        val draft = s.draft ?: return@withAiConsent
        val github = s.github.trim()
        if (github.isBlank()) {
            _state.update { it.copy(error = "Enter your GitHub username so we can hand over the code.") }
            return@withAiConsent
        }
        // Set before the coroutine starts, so a second tap already sees it.
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
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
                // quote_stale (QA I3, as iOS): the server's message says which case (expired, used, brief changed);
                // the screen offers "Get a new quote". No quote is asked for on the person's behalf: it's an AI call.
                if ((e as? ApiException)?.code == ApiException.QUOTE_STALE) {
                    _state.update { it.copy(submitting = false, error = null, staleMessage = e.userLine(), clock = System.currentTimeMillis()) }
                } else {
                    _state.update { it.copy(submitting = false, error = e.userLine()) }
                }
            }
        }
    }
}
