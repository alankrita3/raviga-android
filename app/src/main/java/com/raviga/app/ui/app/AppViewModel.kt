package com.raviga.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.ApiException
import com.raviga.app.di.AppContainer
import com.raviga.app.di.AppEvent
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bootstraps the session and decides the first screen: welcome, terms or
 * home. Keeps a cached session usable offline; registration is the only step
 * that truly needs the network.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val ready: Boolean = false,
        val startRoute: String = Routes.HOME,
        val error: String? = null,
        val upgradeRequired: Boolean = false,
        /** Set when the app must start over at this route with a cleared back stack. */
        val restartAt: String? = null,
        /** The legal documents changed; show Terms on top of wherever the client is. */
        val legalDue: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        bootstrap()
        viewModelScope.launch {
            container.events.collect {
                when (it) {
                    AppEvent.SignedOut -> bootstrap(restart = true)
                    AppEvent.LegalRequired -> _state.update { s -> s.copy(legalDue = s.ready) }
                    else -> Unit
                }
            }
        }
    }

    fun restartConsumed() = _state.update { it.copy(restartAt = null) }
    fun legalShown() = _state.update { it.copy(legalDue = false) }

    private var started = false

    /** Back in the foreground: a new terms version may have shipped while the app was away. */
    fun onForeground() {
        if (!started) { started = true; return }
        if (!_state.value.ready) return
        viewModelScope.launch {
            val session = container.session
            runCatching { session.refreshConfig(); session.refreshMe() }.onSuccess { me ->
                if (session.needsLegal(me, session.config.value)) _state.update { it.copy(legalDue = true) }
            }
        }
    }

    fun bootstrap(restart: Boolean = false) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val session = container.session
            session.warmFromCache()
            container.drafts.load()
            container.projects.warmFromCache()
            container.credits.warmFromCache()
            val prefs = container.prefs.current()
            try {
                val me = session.ensureRegistered()
                launch { runCatching { container.push.syncIfNeeded() } }
                launch { runCatching { container.projects.refreshAll(force = false) } }
                launch { runCatching { container.credits.refresh(force = false) } }
                val config = session.config.value
                val start = when {
                    !prefs.welcomeDone -> Routes.WELCOME
                    session.needsLegal(me, config) -> Routes.TERMS
                    else -> Routes.HOME
                }
                _state.update { it.copy(ready = true, startRoute = start, restartAt = if (restart) start else null) }
            } catch (e: ApiException) {
                if (e.code == ApiException.UPGRADE_REQUIRED) {
                    _state.update { it.copy(ready = true, upgradeRequired = true) }
                } else if (session.isRegistered && session.me.value != null) {
                    // Offline with a cached identity: carry on, reads come from cache.
                    val start = if (!prefs.welcomeDone) Routes.WELCOME else Routes.HOME
                    _state.update { it.copy(ready = true, startRoute = start) }
                } else {
                    _state.update { it.copy(error = e.userLine()) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userLine()) }
            }
        }
    }
}
