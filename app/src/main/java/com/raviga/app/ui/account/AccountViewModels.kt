package com.raviga.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.DeviceSummary
import com.raviga.app.data.api.LinkCodeResponse
import com.raviga.app.data.api.Me
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.userLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Move to another phone (R-079, as iOS): there is no account, so this is the account. The recovery key, a one-time
 * link code and the phones with access; and the way back in on a new phone, by key, recovery email or link code.
 */
class MovePhoneViewModel(
    private val container: AppContainer,
    /**
     * Review 2026-11-03 (Android): the restore-by-email step (the address, where the code went) outlives the app being
     * ended while the person reads the code in their mail app.
     */
    private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    enum class RestoreWay { KEY, EMAIL }

    sealed interface Restore {
        data class Key(val key: String) : Restore
        data class Code(val code: String) : Restore
        data class Email(val email: String, val otp: String) : Restore
    }

    /** Where to go once this phone is another client: Terms when that account hasn't agreed to the current ones. */
    enum class Done { HOME, TERMS }

    data class State(
        val hasAccount: Boolean = false,
        // Recovery key
        val recoveryKey: String? = null,
        val keyVisible: Boolean = false,
        val copied: Boolean = false,
        val keyBusy: Boolean = false,
        val confirmNewKey: Boolean = false,
        val keyError: String? = null,
        // Link a new phone
        val linkCode: LinkCodeResponse? = null,
        val linkBusy: Boolean = false,
        val linkError: String? = null,
        // This is the new phone
        val restoreWay: RestoreWay = RestoreWay.KEY,
        val enteredKey: String = "",
        val enteredCode: String = "",
        val restoreEmail: String = "",
        val restoreOtp: String = "",
        val codeSentTo: String? = null,
        val codeCheck: OtpAttempts = OtpAttempts(),
        val pendingRestore: Restore? = null,
        val restoreBusy: Boolean = false,
        val restoreError: String? = null,
        // Phones with access
        val devices: List<DeviceSummary> = emptyList(),
        val signingOut: DeviceSummary? = null,
        val deviceBusy: Boolean = false,
        val deviceError: String? = null,
        // Navigation, once
        val done: Done? = null,
    ) {
        val keyReady: Boolean get() = !restoreBusy && enteredKey.trim().length >= 8
        val codeReady: Boolean get() = !restoreBusy && enteredCode.trim().length >= 6
        val emailReady: Boolean get() = !restoreBusy && RecoveryEmailLogic.looksLikeEmail(restoreEmail)
        val otpReady: Boolean get() = !restoreBusy && RecoveryEmailLogic.digits(restoreOtp).length == 6
    }

    private val session = container.session
    private val _state = MutableStateFlow(
        State(
            hasAccount = session.hasAccount,
            recoveryKey = session.recoveryKey,
            restoreWay = saved.get<String>(KEY_WAY)?.let { w -> RestoreWay.entries.firstOrNull { it.name == w } } ?: RestoreWay.KEY,
            restoreEmail = saved.get<String>(KEY_EMAIL).orEmpty(),
            codeSentTo = saved.get<String>(KEY_SENT_TO),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        if (session.hasAccount) loadDevices()
        viewModelScope.launch {
            _state.collect { s ->
                saved[KEY_WAY] = s.restoreWay.name
                saved[KEY_EMAIL] = s.restoreEmail
                saved[KEY_SENT_TO] = s.codeSentTo
            }
        }
    }

    private companion object {
        const val KEY_WAY = "restore_way"
        const val KEY_EMAIL = "restore_email"
        const val KEY_SENT_TO = "restore_code_sent_to"
    }

    private fun loadDevices() {
        viewModelScope.launch { runCatching { session.devices() }.onSuccess { d -> _state.update { it.copy(devices = d) } } }
    }

    // ----- Recovery key -----

    fun toggleKey() {
        _state.update { it.copy(keyVisible = !it.keyVisible) }
        viewModelScope.launch { container.prefs.setRecoveryKeyShown() }
    }

    /** R-094 P2-14: masked again when the app leaves the front. */
    fun hideKey() {
        if (_state.value.keyVisible) _state.update { it.copy(keyVisible = false) }
    }

    fun copied() {
        _state.update { it.copy(copied = true) }
        viewModelScope.launch {
            container.prefs.setRecoveryKeyShown()
            delay(2_000)
            _state.update { it.copy(copied = false) }
        }
    }

    fun askNewKey(ask: Boolean) = _state.update { it.copy(confirmNewKey = ask) }

    fun makeNewKey() {
        _state.update { it.copy(keyBusy = true, confirmNewKey = false, keyError = null) }
        viewModelScope.launch {
            runCatching { session.issueRecoveryKey() }
                .onSuccess { key ->
                    container.prefs.setRecoveryKeyShown()
                    _state.update { it.copy(recoveryKey = key, keyVisible = true, hasAccount = session.hasAccount) }
                }
                .onFailure { e -> _state.update { it.copy(keyError = e.userLine()) } }
            _state.update { it.copy(keyBusy = false) }
        }
    }

    // ----- Link a new phone -----

    fun getLinkCode() {
        _state.update { it.copy(linkBusy = true, linkError = null) }
        viewModelScope.launch {
            runCatching { session.createLinkCode() }
                .onSuccess { code -> _state.update { it.copy(linkCode = code) } }
                .onFailure { e -> _state.update { it.copy(linkError = e.userLine()) } }
            _state.update { it.copy(linkBusy = false) }
        }
    }

    // ----- This is the new phone -----

    fun setRestoreWay(way: RestoreWay) = _state.update { it.copy(restoreWay = way, restoreError = null) }
    fun setEnteredKey(v: String) = _state.update { it.copy(enteredKey = v, restoreError = null) }
    fun setEnteredCode(v: String) = _state.update { it.copy(enteredCode = v, restoreError = null) }
    fun setRestoreEmail(v: String) = _state.update { it.copy(restoreEmail = v, restoreError = null) }
    fun setRestoreOtp(v: String) = _state.update { it.copy(restoreOtp = RecoveryEmailLogic.digits(v).take(6), restoreError = null) }

    fun askRestore(restore: Restore?) = _state.update { it.copy(pendingRestore = restore) }

    /** Contract 3.3: the code goes out whether or not the email is on an account, and the screen never says which. */
    fun sendRestoreCode() {
        val address = _state.value.restoreEmail.trim()
        _state.update { it.copy(restoreBusy = true, restoreError = null) }
        viewModelScope.launch {
            runCatching { session.sendRecoveryCode(address) }
                .onSuccess { _state.update { it.copy(codeSentTo = address, restoreOtp = "", codeCheck = OtpAttempts()) } }
                .onFailure { e -> _state.update { it.copy(restoreError = e.userLine()) } }
            _state.update { it.copy(restoreBusy = false) }
        }
    }

    /** After "Replace and restore". */
    fun restore() {
        val pending = _state.value.pendingRestore ?: return
        _state.update { it.copy(pendingRestore = null, restoreBusy = true, restoreError = null) }
        viewModelScope.launch {
            runCatching {
                when (pending) {
                    is Restore.Key -> session.recover(pending.key)
                    is Restore.Code -> session.link(pending.code)
                    is Restore.Email -> session.recoverByEmail(pending.email, pending.otp)
                }
            }
                .onSuccess { restored -> afterRestore(restored) }
                .onFailure { e ->
                    if (pending is Restore.Email) {
                        val (next, outcome) = _state.value.codeCheck.failed(e)
                        _state.update {
                            when (outcome) {
                                is OtpAttempts.Outcome.TryAgain -> it.copy(codeCheck = next, restoreError = outcome.message, restoreOtp = "")
                                is OtpAttempts.Outcome.NewCode -> it.copy(codeCheck = next, restoreError = outcome.message, restoreOtp = "", codeSentTo = null)
                                OtpAttempts.Outcome.Other -> it.copy(restoreError = e.userLine())
                            }
                        }
                    } else {
                        _state.update { it.copy(restoreError = e.userLine()) }
                    }
                }
            _state.update { it.copy(restoreBusy = false) }
        }
    }

    private suspend fun afterRestore(restored: Me) {
        container.dismissSignedOut()
        // Contract order: recover, consent (done in the repository), then the push target.
        container.push.identityChanged()
        container.syncAnalytics()
        runCatching { container.projects.refreshAll() }
        runCatching { container.credits.refresh() }
        // The restored account may not have agreed to the current terms (a Play reviewer's demo account never has).
        val needsTerms = session.needsLegal(restored, session.config.value)
        _state.update {
            it.copy(
                done = if (needsTerms) Done.TERMS else Done.HOME,
                enteredKey = "", enteredCode = "", restoreEmail = "", restoreOtp = "", codeSentTo = null,
            )
        }
    }

    // ----- Phones with access -----

    fun askSignOut(device: DeviceSummary?) = _state.update { it.copy(signingOut = device, deviceError = null) }

    fun signOut() {
        val device = _state.value.signingOut ?: return
        _state.update { it.copy(signingOut = null, deviceBusy = true, deviceError = null) }
        viewModelScope.launch {
            if (device.isCurrent) {
                // Home with no account; the drafts and the agreement made here stay.
                runCatching { session.signOutThisPhone(device.deviceId) }
                    .onSuccess { _state.update { it.copy(done = Done.HOME) } }
                    .onFailure { e -> _state.update { it.copy(deviceError = e.userLine()) } }
            } else {
                runCatching { session.revokeDevice(device.deviceId) }
                    .onFailure { e -> _state.update { it.copy(deviceError = e.userLine()) } }
                runCatching { session.devices() }.onSuccess { d -> _state.update { it.copy(devices = d) } }
            }
            _state.update { it.copy(deviceBusy = false) }
        }
    }
}

/** Settings > Recovery email (R-079, iOS's words): add one with a 6-digit code, or remove it. */
class RecoveryEmailViewModel(
    private val container: AppContainer,
    /** Review 2026-11-03 (Android): the address and where the code went outlive the app being ended (mail app open). */
    private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    data class State(
        /** The confirmed email on the account, if any. */
        val saved: String? = null,
        val email: String = "",
        val otp: String = "",
        /** Where the code went, while it's being entered. */
        val sentTo: String? = null,
        val codeCheck: OtpAttempts = OtpAttempts(),
        val busy: Boolean = false,
        val error: String? = null,
        val confirmed: Boolean = false,
        val confirmRemove: Boolean = false,
    ) {
        val canSend: Boolean get() = !busy && RecoveryEmailLogic.looksLikeEmail(email)
        val canConfirm: Boolean get() = !busy && otp.length == 6
    }

    private val session = container.session
    private val _state = MutableStateFlow(State(email = saved.get<String>(KEY_EMAIL).orEmpty(), sentTo = saved.get<String>(KEY_SENT_TO)))
    val state: StateFlow<State> = _state.asStateFlow()

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_SENT_TO = "code_sent_to"
    }

    init {
        viewModelScope.launch {
            _state.collect { s ->
                saved[KEY_EMAIL] = s.email
                saved[KEY_SENT_TO] = s.sentTo
            }
        }
        viewModelScope.launch { session.me.collect { me -> _state.update { it.copy(saved = savedEmail(me)) } } }
        if (session.hasAccount) viewModelScope.launch { runCatching { session.refreshMe() } }
    }

    private fun savedEmail(me: Me?): String? = me?.recoveryEmail?.takeIf { it.verified }?.email

    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun setOtp(v: String) = _state.update { it.copy(otp = RecoveryEmailLogic.digits(v).take(6), error = null) }
    fun askRemove(ask: Boolean) = _state.update { it.copy(confirmRemove = ask) }

    fun send() {
        val address = _state.value.email.trim()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { session.setRecoveryEmail(address) }
                .onSuccess { _state.update { it.copy(sentTo = address, otp = "", codeCheck = OtpAttempts()) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun confirm() {
        val code = _state.value.otp
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { session.confirmRecoveryEmail(code) }
                .onSuccess {
                    _state.update { it.copy(sentTo = null, otp = "", email = "", confirmed = true) }
                    launch { delay(3_000); _state.update { it.copy(confirmed = false) } }
                }
                .onFailure { e ->
                    val (next, outcome) = _state.value.codeCheck.failed(e)
                    _state.update {
                        when (outcome) {
                            is OtpAttempts.Outcome.TryAgain -> it.copy(codeCheck = next, error = outcome.message, otp = "")
                            is OtpAttempts.Outcome.NewCode -> it.copy(codeCheck = next, error = outcome.message, otp = "", sentTo = null)
                            OtpAttempts.Outcome.Other -> it.copy(error = e.userLine())
                        }
                    }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun remove() {
        _state.update { it.copy(confirmRemove = false, busy = true, error = null) }
        viewModelScope.launch {
            runCatching { session.removeRecoveryEmail() }
                .onSuccess { _state.update { it.copy(email = "") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }
}
