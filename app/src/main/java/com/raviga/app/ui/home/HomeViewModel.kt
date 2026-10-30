package com.raviga.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.di.AppContainer
import com.raviga.app.di.AppEvent
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.userLine
import com.raviga.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** One row on Home: a draft on this phone, or a submitted project. */
    data class Row(
        val id: String,
        val title: String,
        val line: String,
        val status: String,
        val unread: Int,
        val updatedAt: String,
        val route: String,
        val refused: Boolean = false,
        /** What it cost, for projects that were paid for and not refunded. */
        val credits: Int? = null,
    )

    /**
     * Drafts (only on this phone) and submitted projects (from the server), newest first.
     * A closed project's title was deleted server-side; this phone's copy names it if it has one.
     */
    val rows: StateFlow<List<Row>> = combine(container.drafts.drafts, container.projects.summaries) { drafts, summaries ->
        val localDrafts = drafts.values.filter { it.serverProjectId == null }.map { d ->
            Row(
                id = d.id,
                title = d.displayTitle.ifBlank { "Untitled draft" },
                line = when {
                    d.isRefused -> "We can't take this on"
                    d.quoteIsCurrent -> "Draft, quoted ${d.quote?.credits} credits"
                    d.document == null -> "Notes, no brief yet. Edited ${Time.relative(d.updatedAt)}"
                    else -> "Draft, edited ${Time.relative(d.updatedAt)}"
                },
                status = ProjectStatus.DRAFT,
                unread = 0,
                updatedAt = d.updatedAt,
                route = Routes.document(d.id),
                refused = d.isRefused,
            )
        }
        val byServerId = drafts.values.filter { it.serverProjectId != null }.associateBy { it.serverProjectId }
        val submitted = summaries.map { s ->
            Row(
                id = s.id,
                title = s.title.ifBlank { byServerId[s.id]?.displayTitle.orEmpty() }.ifBlank { "Project ${s.ref}" },
                line = StatusCopy.rowLine(s),
                status = s.status,
                unread = s.unreadComments,
                updatedAt = s.updatedAt.orEmpty(),
                route = Routes.forProject(s.id, s.status),
                credits = s.credits.takeIf { s.status != ProjectStatus.REJECTED && s.status != ProjectStatus.CANCELLED },
            )
        }
        (localDrafts + submitted).sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loadedOnce = container.projects.loadedOnce

    val balance: StateFlow<Int> = combine(container.credits.credits, container.session.me) { credits, me ->
        credits?.balance ?: me?.credits?.balance ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val isDemo: Boolean get() = container.isDemo

    /** This phone was signed out of its old account; the empty state says how to get it back. */
    val signedOut: StateFlow<Boolean> = container.signedOut
    fun dismissSignedOut() = container.dismissSignedOut()

    init {
        // No refresh here: HomeScreen refreshes on resume, which also covers the first showing.
        viewModelScope.launch {
            container.events.collect { event ->
                if (event is AppEvent.ProjectUpdated) runCatching { container.projects.refreshAll() }
                if (event is AppEvent.CreditsUpdated) runCatching { container.credits.refresh() }
            }
        }
    }

    /** [force] false on resume: a refresh from the last few seconds (start-up) stands. Retry forces. */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _refreshing.value = true
            container.drafts.load()
            // R-056: before Agree there is no account to load; drafts are all there is.
            if (!container.session.agreed) {
                container.projects.noAccountYet()
                _refreshing.value = false
                return@launch
            }
            // Agreed on the way to Restore, then backed out: the account comes now (register, consent, push).
            if (!container.session.isRegistered) {
                runCatching { container.session.completeConsent(container.session.ensureRegistered()) }
                    .onSuccess { container.push.identityChanged() }
            }
            runCatching { container.projects.refreshAll(force) }
                .onSuccess { _error.value = null }
                .onFailure { if (rows.value.isEmpty()) _error.value = it.userLine() }
            runCatching { container.credits.refresh(force) }
            _refreshing.value = false
        }
    }

    suspend fun markNotificationsAsked() = container.prefs.setNotificationsAsked()
    suspend fun notificationsAsked(): Boolean = container.prefs.current().notificationsAsked
}
