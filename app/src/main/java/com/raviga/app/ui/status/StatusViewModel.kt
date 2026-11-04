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
 * Shared by the status and delivery screens: one project and the client's actions on it. Contract v0.10.0 (R-103):
 * no messaging; an accepted project is paid here (a new quote first when its price expired); a delivery is approved or
 * reported.
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
        /** The account's AWS role is verified (GET /me). */
        val awsVerified: Boolean = false,
        /** The server said 409 aws_required before the phone knew. */
        val awsRequiredByServer: Boolean = false,
    ) {
        /** Accepted and not paid yet: the client pays here. */
        val awaitingPayment: Boolean get() = project?.status == ProjectStatus.APPROVED && project.payment == null
        /** What paying costs now: the new quote's price once there is one. */
        val price: Int get() = newQuote?.quote?.credits ?: project?.quote?.credits ?: 0
        /** The project's own price ran out and no new quote is in hand yet. */
        val priceExpired: Boolean
            get() = newQuote == null && (expiredByServer ||
                project?.quote?.expiresAt?.let { Time.parse(it)?.toEpochMilli() }?.let { it <= clock } == true)
        /**
         * v0.10.2 (R-105, her decision): every project is built in the client's own AWS, so paying waits for a verified
         * role: "Connect AWS to start" until then (iOS's copy).
         */
        val needsAws: Boolean
            get() = awaitingPayment && (awsRequiredByServer || (config.delivery.awsRequiredToPay && !awsVerified))
        /** Credits short of the price (iOS's line: "You have 10 credits. You need 20 more."). */
        val shortBy: Double get() = if (!awaitingPayment || priceExpired || needsAws) 0.0 else (price - balance).coerceAtLeast(0.0)
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
                val verified = me?.deliveryTargets?.aws?.verified == true
                _state.update {
                    it.copy(
                        awsVerified = verified,
                        // Verified since the server said no: try again.
                        awsRequiredByServer = it.awsRequiredByServer && !verified,
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
        // Back from Credits after buying, or from Connect AWS: the balance and the AWS role decide the button.
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

    /** "Something's wrong" (R-103). [onSent] runs only once the server has it; a failure keeps the sheet and its text. */
    fun report(category: String, description: String, onSent: () -> Unit = {}) =
        action(onSent) { container.projects.report(projectId, category, description) }

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
        if (s.busy || !s.awaitingPayment || s.priceExpired || s.needsAws) return
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
        /**
         * What the screen shows after a payment didn't go through.
         * - 402 insufficient_credits: no error line; the balance line and "Buy credits" say it (iOS).
         * - 409 quote_stale, expired: the price ran out; "Get a new quote" takes over.
         * - 409 aws_required (R-105): the server's message, and "Connect AWS to start" takes over (iOS).
         */
        internal fun afterFailedPay(s: State, e: Throwable): State {
            val api = e as? ApiException
            return when {
                api?.code == ApiException.INSUFFICIENT_CREDITS ->
                    s.copy(error = null, balance = api.detailDouble("balance") ?: s.balance)
                api?.code == ApiException.QUOTE_STALE && (api.detailBoolean("expired") == true || s.newQuote == null) ->
                    s.copy(error = null, newQuote = null, expiredByServer = true)
                api?.code == ApiException.AWS_REQUIRED -> s.copy(error = e.userLine(), awsRequiredByServer = true)
                else -> s.copy(error = e.userLine())
            }
        }
    }
}
