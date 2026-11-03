package com.raviga.app.ui.settings

import com.raviga.app.di.AppEvent
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

class SettingsViewModel(
    private val container: AppContainer,
    /** Review 2026-11-03 (Android): the export link outlives the app being ended while the save picker is open. */
    private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    data class State(
        val me: Me? = null,
        val config: AppConfig = AppConfig(),
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val exportUrl: String? = null,
        val exportMessage: String? = null,
        val deleted: Boolean = false,
        val github: String = "",
    )

    private val _state = MutableStateFlow(State(exportUrl = saved.get<String>(KEY_EXPORT_URL)))
    val state: StateFlow<State> = _state.asStateFlow()

    val isDemo: Boolean get() = container.isDemo
    val versionName: String get() = BuildConfig.VERSION_NAME
    val pushConfigured: Boolean get() = container.push.isFirebaseConfigured

    init {
        viewModelScope.launch { container.session.me.collect { me -> _state.update { it.copy(me = me, github = if (it.github.isBlank()) me?.deliveryTargets?.githubUsername.orEmpty() else it.github) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        // No account yet (before Agree, or agreed on the way to Restore): nothing to refresh.
        if (container.session.isRegistered) viewModelScope.launch { runCatching { container.session.refreshMe() } }
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
                .onSuccess { r ->
                    saved[KEY_EXPORT_URL] = r.downloadUrl
                    _state.update { it.copy(exportUrl = r.downloadUrl, exportMessage = null, notice = "Your data is ready, with the drafts from this phone added. Choose where to save it.") }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userLine(), exportMessage = null) } }
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * Saves the server's zip, plus this phone's drafts, into the file the client picked. Review 2026-11-03 (Android):
     * the picker has already made that file; with no link to fill it from (it was lost with the app) it used to stay
     * as an empty zip with no word on screen. Now it is removed and the screen says so; a failed save removes it too.
     */
    fun saveExport(target: android.net.Uri) {
        val url = _state.value.exportUrl
        viewModelScope.launch {
            if (url == null) {
                container.exports.discard(target)
                _state.update { it.copy(error = "The download link has expired. Ask for a new one.") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = null, exportMessage = "Saving") }
            runCatching { container.exports.write(url, target) }
                .onSuccess {
                    saved.remove<String>(KEY_EXPORT_URL)
                    _state.update { it.copy(exportUrl = null, exportMessage = null, notice = "Saved.") }
                }
                .onFailure { e -> _state.update { it.copy(exportMessage = null, error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val KEY_EXPORT_URL = "export_url"
    }

    /**
     * Review #15 (Android): in the app's scope, not the screen's. Leaving Privacy and data while DELETE /me is in
     * flight used to cancel it after the server may have taken it: tokens revoked, this phone not wiped. Now the
     * request and the wipe run to the end. R-097 #7 (review 2): once it lands the app always starts over at Welcome
     * through the root (AppEvent.SignedOut), whether this screen is in front, leaving, or gone; the screen no longer
     * navigates itself, so a deletion landing in its exit animation can't leave the app on Settings.
     */
    fun deleteEverything() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        container.appScope.launch {
            val result = runCatching {
                container.session.deleteMe()
                // Drafts were only ever here; they go too, and so does any copy of a document left mid-read.
                container.draftStore.wipe()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.files.clearStale() }
                // Contract v0.9 section 22: collection off and the SDK's data dropped.
                container.analytics.clearAfterDeletion()
            }
            result
                .onSuccess {
                    _state.update { it.copy(deleted = true) }
                    container.emit(AppEvent.SignedOut)
                }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    // ----- demo controls -----
    private val demo get() = container.demo

    fun demoAdvance() = demoAction { demo?.advanceLatest() ?: "No submitted project to advance." }
    fun demoAddCredits() = demoAction { demo?.addCredits(50); container.credits.refresh(); "50 demo credits added." }
    fun demoReset() = demoAction {
        demo?.reset()
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
