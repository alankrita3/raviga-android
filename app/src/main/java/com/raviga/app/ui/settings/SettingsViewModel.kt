package com.raviga.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.BuildConfig
import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.Me
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val me: Me? = null,
        val config: AppConfig = AppConfig(),
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val exportUrl: String? = null,
        val exportMessage: String? = null,
        val deleted: Boolean = false,
        val recoveryKey: String? = null,
        val recovered: Boolean = false,
        val github: String = "",
    )

    private val _state = MutableStateFlow(State(recoveryKey = container.session.recoveryKey))
    val state: StateFlow<State> = _state.asStateFlow()

    val isDemo: Boolean get() = container.isDemo
    val isDebug: Boolean get() = BuildConfig.DEBUG
    val versionName: String get() = BuildConfig.VERSION_NAME
    val pushConfigured: Boolean get() = container.push.isFirebaseConfigured

    init {
        viewModelScope.launch { container.session.me.collect { me -> _state.update { it.copy(me = me, github = if (it.github.isBlank()) me?.deliveryTargets?.githubUsername.orEmpty() else it.github) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch { runCatching { container.session.refreshMe() } }
    }

    fun clearMessages() = _state.update { it.copy(error = null, notice = null) }

    // ----- delivery targets -----
    fun setGithub(v: String) = _state.update { it.copy(github = v, error = null, notice = null) }
    fun saveGithub() = run {
        val name = _state.value.github.trim()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { if (name.isBlank()) container.session.clearDeliveryTarget(github = true) else container.session.setDeliveryTargets(name, null) }
                .onSuccess { _state.update { it.copy(notice = if (name.isBlank()) "GitHub username cleared." else "Saved. Repositories will be transferred to @$name.") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    // ----- AI consent -----
    fun setAiProcessing(granted: Boolean) = run {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.setAiProcessing(granted) }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    // ----- Usage analytics (contract v0.9) -----
    fun setAnalytics(granted: Boolean) = run {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.setAnalytics(granted) }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    // ----- export / delete -----
    fun export() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, exportUrl = null, exportMessage = "Collecting your data") }
            runCatching { container.session.exportData { st -> _state.update { it.copy(exportMessage = st.message ?: it.exportMessage) } } }
                .onSuccess { r -> _state.update { it.copy(exportUrl = r.downloadUrl, exportMessage = null, notice = "Your data is ready, with the drafts from this phone added. Choose where to save it.") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine(), exportMessage = null) } }
            _state.update { it.copy(busy = false) }
        }
    }

    /** Saves the server's zip, plus this phone's drafts, into the file the client picked. */
    fun saveExport(target: android.net.Uri) {
        val url = _state.value.exportUrl ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, exportMessage = "Saving") }
            runCatching { container.exports.write(url, target) }
                .onSuccess { _state.update { it.copy(exportUrl = null, exportMessage = null, notice = "Saved.") } }
                .onFailure { e -> _state.update { it.copy(exportMessage = null, error = e.message ?: e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                container.session.deleteMe()
                // Drafts were only ever here; they go too.
                container.draftStore.wipe()
                // Contract v0.9 section 22: collection off and the SDK's data dropped.
                container.analytics.clearAfterDeletion()
            }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    // ----- recovery -----
    fun issueRecoveryKey() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.issueRecoveryKey() }
                .onSuccess { key -> _state.update { it.copy(recoveryKey = key, notice = "New key issued. The old one no longer works.") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun recoverWith(key: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.recover(key) }
                .onSuccess {
                    container.dismissSignedOut()
                    runCatching { container.projects.refreshAll() }
                    runCatching { container.credits.refresh() }
                    _state.update { s -> s.copy(recovered = true, recoveryKey = container.session.recoveryKey) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    suspend fun markRecoveryKeyShown() = container.prefs.setRecoveryKeyShown()

    // ----- demo controls -----
    private val demo get() = container.api as? com.raviga.app.data.demo.DemoApi

    fun demoAdvance() = demoAction { demo?.demoAdvanceLatest() ?: "No submitted project to advance." }
    fun demoAddCredits() = demoAction { demo?.demoAddCredits(50); container.credits.refresh(); "50 demo credits added." }
    fun demoReset() = demoAction {
        demo?.demoReset()
        container.cache.clearAll()
        container.prefs.clear()
        "Demo data reset. Restart the app."
    }

    private fun demoAction(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { block() }
                .onSuccess { msg -> runCatching { container.projects.refreshAll() }; _state.update { it.copy(notice = msg) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }
}
