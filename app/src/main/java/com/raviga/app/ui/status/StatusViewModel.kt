package com.raviga.app.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.di.AppContainer
import com.raviga.app.di.AppEvent
import com.raviga.app.ui.userLine
import com.raviga.app.util.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared by the status, delivery and Make it live screens: one project and the client's actions on it. Contract v0.10.0
 * (R-103): no messaging; an accepted project is paid here (a new quote first when its price expired). v0.12.0 (R-109):
 * no AWS before paying; the test invites' emails; a demo is approved, reported or changed (a paid change request);
 * after approval, Make it live.
 */
class StatusViewModel(
    private val container: AppContainer,
    val projectId: String,
    @Suppress("unused") private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    data class State(
        val project: Project? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null,
        val config: com.raviga.app.data.api.AppConfig = com.raviga.app.data.api.AppConfig(),
        val balance: Double = 0.0,
        /** A new quote for the same brief, asked for after the price expired; paid with its token. */
        val newQuote: QuoteResult? = null,
        val quoting: Boolean = false,
        /** The server said the price expired (409 quote_stale, expired) before the phone's clock did. */
        val expiredByServer: Boolean = false,
        val clock: Long = System.currentTimeMillis(),
        /** v0.12.0: where the test invites go (`me.storeEmails`), and whether the AWS role is verified (Make it live). */
        val storeEmails: com.raviga.app.data.api.StoreEmails = com.raviga.app.data.api.StoreEmails(),
        val awsVerified: Boolean = false,
        /** "Saved. You can change these until the invites go out." after the invite emails were saved. */
        val emailsSaved: Boolean = false,
        /** Pricing a change request (the AI's job), with its progress line. */
        val pricing: Boolean = false,
        /** A change request's own error (its sheet shows it), apart from the screen's. */
        val changeError: String? = null,
        /** "Thanks. We'll check and start publishing." once "I've sent the invites" went through. */
        val invitesSent: Boolean = false,
        /** v0.13.0: the server said 409 free_fixes_ended (the window closed while the screen was open). */
        val freeFixesEnded: Boolean = false,
        /** Its message, for the alert (as iOS): the server's words as the title, Request a change / OK. */
        val freeFixesEndedMessage: String? = null,
    ) {
        /**
         * R-113 (her decision 5b; contract v0.13.0): "Something's wrong" is offered from the demo until 7 days after
         * launch: in delivered, accepted and live while the server's `freeFixesUntil` is missing or still ahead. The app
         * never works the date out itself.
         */
        val freeFixesOpen: Boolean
            get() = project?.status in REPORT_STATUSES && !freeFixesEnded &&
                (project?.freeFixesUntil?.let { Time.parse(it)?.toEpochMilli()?.let { end -> end > clock } ?: false } ?: true)
        /** Live in a store (the free-fix clock has started): the tile reads "Your app is live". */
        val launched: Boolean get() = project?.status == ProjectStatus.LIVE || project?.freeFixesUntil != null
        /** A fix or a change approved after the first approval: "Approve this version?" (the PM's call 2). */
        val secondApproval: Boolean
            get() = project?.history.orEmpty().any { it.status == ProjectStatus.ACCEPTED || it.status == ProjectStatus.LIVE }
        /** Accepted and not paid yet: the client pays here. */
        val awaitingPayment: Boolean get() = project?.status == ProjectStatus.APPROVED && project.payment == null
        /** What paying costs now: the new quote's price once there is one. */
        val price: Int get() = newQuote?.quote?.credits ?: project?.quote?.credits ?: 0
        /** The project's own price ran out and no new quote is in hand yet. */
        val priceExpired: Boolean
            get() = newQuote == null && (expiredByServer ||
                project?.quote?.expiresAt?.let { Time.parse(it)?.toEpochMilli() }?.let { it <= clock } == true)
        /** Credits short of the price (iOS's line: "You have 10 credits. You need 20 more."). */
        val shortBy: Double get() = if (!awaitingPayment || priceExpired) 0.0 else (price - balance).coerceAtLeast(0.0)

        /** v0.12.0: the change request still open (pricing, quoted or paid); at most one at a time. */
        val openChange: com.raviga.app.data.api.Change? get() = project?.delivery?.openChange
        /** A priced change waiting to be paid. */
        val quotedChange: com.raviga.app.data.api.Change?
            get() = openChange?.takeIf { it.status == com.raviga.app.data.api.Change.QUOTED }
        /** Credits short of the priced change. */
        val changeShortBy: Double
            get() = quotedChange?.quote?.credits?.let { (it - balance).coerceAtLeast(0.0) } ?: 0.0
        /** A change can be asked for: after the demo (delivered, approved or live) with none open. */
        val canRequestChange: Boolean
            get() = project?.status in CHANGE_STATUSES && openChange == null
        /** The test invites can still change: built (or rebuilt) and the invites not out yet. */
        val invitesEditable: Boolean
            get() = project?.status == ProjectStatus.BUILDING || project?.status == ProjectStatus.REVISION_REQUESTED
        /** Which invites the project needs: its apps (both when an older server doesn't say). */
        val wantsApple: Boolean get() = project?.apps.isNullOrEmpty() || project?.apps?.contains("ios") == true
        val wantsGoogle: Boolean get() = project?.apps.isNullOrEmpty() || project?.apps?.contains("android") == true
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.projects.project(projectId).collect { p -> _state.update { it.copy(project = p) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch {
            container.credits.credits.collect { c -> if (c != null) _state.update { it.copy(balance = c.balance) } }
        }
        viewModelScope.launch {
            container.session.me.collect { me ->
                _state.update {
                    it.copy(
                        awsVerified = me?.deliveryTargets?.aws?.verified == true,
                        storeEmails = me?.storeEmails ?: it.storeEmails,
                        balance = if (container.credits.credits.value == null && me != null) me.credits.balance else it.balance,
                    )
                }
            }
        }
        viewModelScope.launch {
            container.events.collect { if (visible && it is AppEvent.ProjectUpdated && it.projectId == projectId) refresh() }
        }
        viewModelScope.launch { container.projects.warmProject(projectId) }
    }

    private var visible = false
    private var pollJob: kotlinx.coroutines.Job? = null

    /**
     * The screen is in front: refresh, and poll gently while the team or the system may move the project, in case push
     * is not configured. Nothing runs while the screen is covered or the app is in the background.
     */
    fun onVisible() {
        visible = true
        refresh()
        // Back from Credits after buying, or from Connect AWS: the balance and the AWS role decide what shows.
        viewModelScope.launch { runCatching { container.credits.refresh(force = false) } }
        viewModelScope.launch { runCatching { container.session.refreshMe() } }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(if (container.isDemo) 5_000 else 30_000)
                _state.update { it.copy(clock = System.currentTimeMillis()) }
                val status = _state.value.project?.status ?: continue
                if (!ProjectStatus.isTerminal(status)) runCatching { container.projects.refresh(projectId) }
            }
        }
    }

    fun onHidden() {
        visible = false
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.projects.refresh(projectId) }
                .onSuccess { _state.update { it.copy(loading = false, error = null, clock = System.currentTimeMillis()) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = if (it.project == null) e.userLine() else null) } }
        }
    }

    fun cancel() = action { container.projects.cancel(projectId) }

    fun accept() = action {
        // The server deletes the quote's detail on accept, so read the bracket from the copy held before.
        val before = container.projects.cachedProject(projectId)
        container.projects.accept(projectId)
        container.analytics.deliveryAccepted(before?.quote?.bracketId, before?.reports?.size ?: 0)
    }

    /**
     * "Something's wrong" (R-103; v0.13.0 also after approval and once live, until free fixes end). [onSent] runs only
     * once the server has it; a failure keeps the sheet and its text. After the window: the server's words, then the
     * screen offers Request a change.
     */
    fun report(category: String, description: String, onSent: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.projects.report(projectId, category, description) }
                .onSuccess { onSent() }
                .onFailure { e ->
                    val ended = (e as? ApiException)?.code == ApiException.FREE_FIXES_ENDED
                    _state.update {
                        if (ended) it.copy(error = null, freeFixesEnded = true, freeFixesEndedMessage = e.userLine())
                        else it.copy(error = e.userLine())
                    }
                    runCatching { container.projects.refresh(projectId) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    /** The free-fixes-ended alert was answered (Request a change or OK). */
    fun freeFixesEndedSeen() = _state.update { it.copy(freeFixesEndedMessage = null) }

    /**
     * One key per price paid: a retry after a lost answer reuses it (the server answers the same), a new quote gets a
     * new one. Dropped once the server took the payment or refused it for good.
     */
    private var payKey: Pair<String, String>? = null

    private fun keyFor(token: String): String =
        payKey?.takeIf { it.first == token }?.second ?: java.util.UUID.randomUUID().toString().also { payKey = token to it }

    /** Pay the accepted project: its own price, or the new quote's after the old one expired (contract 11). */
    fun pay() {
        val s = _state.value
        if (s.busy || !s.awaitingPayment || s.priceExpired) return
        val token = s.newQuote?.quoteToken
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { container.projects.pay(projectId, keyFor(token.orEmpty()), token) }
                .onSuccess {
                    payKey = null
                    _state.update { it.copy(newQuote = null, expiredByServer = false) }
                    runCatching { container.credits.refresh() }
                    runCatching { container.session.refreshMe() }
                }
                .onFailure { e -> _state.update { afterFailedPay(it, e) }; if (e is ApiException && e.httpStatus in 400..499) payKey = null }
            if (_state.value.error != null) runCatching { container.projects.refresh(projectId) }
            if ((_state.value.error == null) && (_state.value.shortBy > 0)) runCatching { container.credits.refresh() }
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * R-113 (3b): back from buying credits for this project. They land by webhook, so the balance is read again every 2 s
     * for up to 10 s; the screen asks to pay once it covers the price.
     */
    fun watchCredits() {
        viewModelScope.launch {
            repeat(6) {
                runCatching { container.credits.refresh() }
                val s = _state.value
                val covered = if (s.quotedChange != null) s.changeShortBy <= 0.0 else s.shortBy <= 0.0
                if (covered) return@launch
                delay(2_000)
            }
        }
    }

    /** The price expired: quote the same brief again (an AI call, only on the client's tap), then pay with that token. */
    fun requestNewQuote() {
        val s = _state.value
        val document = s.project?.document ?: return
        if (s.quoting || s.busy) return
        _state.update { it.copy(quoting = true, error = null) }
        viewModelScope.launch {
            runCatching { container.ai.quote(document.asDocument().body()) }
                .onSuccess { q -> _state.update { it.copy(newQuote = q, expiredByServer = false) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(quoting = false) }
        }
    }

    // ----- v0.12.0 (R-109) -----

    /** "Save" the test invites' emails; only the ones the project needs are asked. [onSaved] runs once the server has them. */
    fun saveStoreEmails(apple: String?, google: String?, onSaved: () -> Unit = {}) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, emailsSaved = false) }
        viewModelScope.launch {
            runCatching { container.session.setStoreEmails(apple, google) }
                .onSuccess { _state.update { it.copy(emailsSaved = true) }; onSaved() }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    /** "Get a price": the AI writes a short change brief and prices it; the sheet shows it once quoted. */
    fun requestChange(description: String) {
        val s = _state.value
        if (s.pricing || description.isBlank()) return
        _state.update { it.copy(pricing = true, changeError = null) }
        viewModelScope.launch {
            runCatching { container.projects.requestChange(projectId, description, container.jobs) }
                .onFailure { e -> _state.update { it.copy(changeError = e.userLine()) } }
            _state.update { it.copy(pricing = false) }
        }
    }

    /** One key per change: a retry after a lost answer reuses it. */
    private var changeKey: Pair<String, String>? = null

    /** "Pay N credits" for the priced change: the project goes back to building. [onPaid] once the server took it. */
    fun payChange(onPaid: () -> Unit = {}) {
        val s = _state.value
        val change = s.quotedChange ?: return
        if (s.busy || s.changeShortBy > 0.0) return
        val key = changeKey?.takeIf { it.first == change.id }?.second
            ?: java.util.UUID.randomUUID().toString().also { changeKey = change.id to it }
        _state.update { it.copy(busy = true, changeError = null) }
        viewModelScope.launch {
            runCatching { container.projects.payChange(projectId, change.id, key) }
                .onSuccess {
                    changeKey = null
                    runCatching { container.credits.refresh() }
                    onPaid()
                }
                .onFailure { e ->
                    val api = e as? ApiException
                    // Short: the shortfall line and "Buy credits" say it, as for the project.
                    if (api?.code == ApiException.INSUFFICIENT_CREDITS) {
                        _state.update { it.copy(balance = api.detailDouble("balance") ?: it.balance) }
                    } else {
                        _state.update { it.copy(changeError = e.userLine()) }
                    }
                    if (api != null && api.httpStatus in 400..499) changeKey = null
                    runCatching { container.projects.refresh(projectId) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    /** "I've sent the invites": every store still waiting for the client's invite (Make it live, step 2). */
    fun invitesSent() = action(onSuccess = { _state.update { it.copy(invitesSent = true) } }) {
        val project = _state.value.project ?: return@action
        GoLiveLogic.waitingForInvite(project).forEach { store -> container.projects.handoverInvited(projectId, store) }
    }

    private fun action(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { onSuccess(); runCatching { container.credits.refresh() } }
                .onFailure { e ->
                    _state.update { it.copy(error = e.userLine()) }
                    // R-092 #39 (iOS review): the server said no (invalid_state): show where it really is.
                    runCatching { container.projects.refresh(projectId) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    companion object {
        /** v0.12.0: a change can be asked for after the demo, after approval, and once live. */
        private val CHANGE_STATUSES = setOf(ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE)
        /** v0.13.0: a report is taken from the demo, after approval and once live (until free fixes end). */
        private val REPORT_STATUSES = setOf(ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE)

        /**
         * What the screen shows after a payment didn't go through.
         * - 402 insufficient_credits: no error line; the balance line and "Buy credits" say it (iOS).
         * - 409 quote_stale, expired: the price ran out; "Get a new quote" takes over.
         */
        internal fun afterFailedPay(s: State, e: Throwable): State {
            val api = e as? ApiException
            return when {
                api?.code == ApiException.INSUFFICIENT_CREDITS ->
                    s.copy(error = null, balance = api.detailDouble("balance") ?: s.balance)
                api?.code == ApiException.QUOTE_STALE && (api.detailBoolean("expired") == true || s.newQuote == null) ->
                    s.copy(error = null, newQuote = null, expiredByServer = true)
                else -> s.copy(error = e.userLine())
            }
        }
    }
}
