package com.raviga.app.data.repo

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.AwsConnectInfo
import com.raviga.app.data.api.AwsTarget
import com.raviga.app.data.api.ConsentGuard
import com.raviga.app.data.api.ConsentRequest
import com.raviga.app.data.api.ConsentState
import com.raviga.app.data.api.DeleteMeResponse
import com.raviga.app.data.api.DeliveryTargets
import com.raviga.app.data.api.DeliveryTargetsRequest
import com.raviga.app.data.api.DeviceInfo
import com.raviga.app.data.api.DeviceSummary
import com.raviga.app.data.api.LinkCodeResponse
import com.raviga.app.data.api.OtpRequest
import com.raviga.app.data.api.RecoverByEmailConfirmRequest
import com.raviga.app.data.api.RecoverByEmailRequest
import com.raviga.app.data.api.RecoveryEmailRequest
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.ExportResult
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.JobState
import com.raviga.app.data.api.Me
import com.raviga.app.data.api.PushTokenRequest
import com.raviga.app.data.api.RecoverDeviceRequest
import com.raviga.app.data.api.apiCall
import com.raviga.app.data.local.CacheStore
import com.raviga.app.data.local.PrefsStore
import com.raviga.app.data.local.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Who this device is (clientId + token), what the backend says about them
 * ([me]) and the server-driven [config]. Registration is lazy and idempotent.
 */
class SessionRepository(
    private val api: RavigaApi,
    private val json: Json,
    private val jobs: JobRunner,
    private val sessionStore: SessionStore,
    private val prefs: PrefsStore,
    private val cache: CacheStore,
    private val guard: ConsentGuard,
) {
    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val registerLock = Mutex()

    val clientId: String? get() = sessionStore.clientId
    val recoveryKey: String? get() = sessionStore.recoveryKey
    val isRegistered: Boolean get() = sessionStore.isRegistered

    /** There is an account on this phone: agreed here and registered (iOS's hasAccount). */
    val hasAccount: Boolean get() = guard.agreed && sessionStore.isRegistered

    /**
     * R-079: this phone was signed out from Move to another phone in this run. Home then shows only the drafts, with
     * no account, until something needs one ([ensureAccount]) or the app starts again (as iOS).
     */
    @Volatile var signedOutHere: Boolean = false
        private set

    /** R-056: the person on this phone has agreed (any version). Until then [guard] lets only GET /config out. */
    val agreed: Boolean get() = guard.agreed

    /** The account has the terms on record; push and analytics wait for this (contract v0.9.2 order). */
    val consentRecorded: Boolean get() = _me.value?.consent?.terms != null

    /** The analytics choice made on the consent screen, applied once the account has the consent. */
    @Volatile private var pendingAnalytics: Boolean? = null

    /** Drops the previous client's [me]; [config] is the same for everyone. */
    fun resetMe() {
        _me.value = null
    }

    suspend fun warmFromCache() {
        cache.read(CacheStore.CONFIG, AppConfig.serializer())?.let { _config.value = it }
        cache.read(CacheStore.ME, Me.serializer())?.let { _me.value = it }
        // Installs from before R-056 agreed on the server and kept the version; they never see the first consent again.
        val p = prefs.current()
        guard.agreed = p.agreedTermsVersion != null || p.termsVersionAccepted != null || _me.value?.consent?.terms != null
    }

    /**
     * Registers the device on first run; afterwards just refreshes [config] and [me]. [perCallMs]: each call capped
     * (review #13: a cold start with nothing cached), a slow one failing as offline.
     */
    suspend fun ensureRegistered(perCallMs: Long? = null): Me = registerLock.withLock {
        suspend fun <T> call(block: suspend () -> T): T = if (perCallMs == null) block() else StartupCalls.capped(perCallMs, block)
        if (!sessionStore.isRegistered) {
            val response = call { apiCall(json) { api.registerDevice(DeviceInfo.registerRequest(sessionStore.installId)) } }
            sessionStore.set(response.clientId, response.accessToken, response.recoveryKey)
        }
        call { refreshConfig() }
        call { refreshMe() }
    }

    /**
     * The account an action needs: after "Sign out this phone" (or agreeing on the way to Restore and backing out)
     * there is none until here. Registers, puts the agreement on it, and the container follows the new clientId.
     */
    suspend fun ensureAccount() {
        if (!guard.agreed || sessionStore.isRegistered) return
        completeConsent(ensureRegistered())
        signedOutHere = false
    }

    suspend fun refreshConfig(): AppConfig {
        val config = apiCall(json) { api.config() }
        _config.value = config
        cache.write(CacheStore.CONFIG, AppConfig.serializer(), config)
        return config
    }

    suspend fun refreshMe(): Me {
        val me = apiCall(json) { api.me() }
        setMe(me)
        return me
    }

    private suspend fun setMe(me: Me) {
        _me.value = me
        cache.write(CacheStore.ME, Me.serializer(), me)
    }

    private suspend fun updateMe(transform: (Me) -> Me) {
        _me.value?.let { setMe(transform(it)) }
    }

    // ----- Consent (section 4) -----

    /** Terms and privacy must be accepted at the versions in config. */
    fun needsLegal(me: Me?, config: AppConfig): Boolean {
        if (me == null) return true
        val c = me.consent
        return c.terms == null || c.privacy == null ||
            c.terms.version != config.legal.termsVersion || c.privacy.version != config.legal.privacyVersion
    }

    fun needsAiConsent(me: Me?): Boolean = me?.consent?.aiGranted != true

    /**
     * R-056: the first agreement on this phone. Recorded here first; then the phone registers and the consent goes
     * up (contract order: register, consent, push). For Restore, recover() does both instead, so no spare account is
     * made.
     */
    suspend fun agree(analytics: Boolean, restoring: Boolean) {
        if (_config.value.legal.termsVersion.isBlank()) refreshConfig()
        val legal = _config.value.legal
        prefs.setAgreed(legal.termsVersion, legal.privacyVersion)
        pendingAnalytics = analytics
        guard.agreed = true
        if (!restoring) completeConsent(ensureRegistered())
    }

    /**
     * Puts this phone's agreement on the account when the account lacks it (just registered, restored, or given a
     * fresh account), but only for the versions the person saw: a newer version is the "updated terms" screen.
     */
    suspend fun completeConsent(me: Me): Me {
        val legal = _config.value.legal
        val p = prefs.current()
        if (needsLegal(me, _config.value) && p.agreedTermsVersion == legal.termsVersion && p.agreedPrivacyVersion == legal.privacyVersion) {
            val consent = apiCall(json) {
                api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion))
            }
            prefs.setTermsAccepted(legal.termsVersion)
            setMe(me.copy(consent = consent))
        }
        val wanted = pendingAnalytics
        if (wanted != null && consentRecorded) {
            pendingAnalytics = null
            if (wanted != _me.value?.consent?.analyticsGranted) setAnalytics(wanted)
        }
        return _me.value ?: me
    }

    suspend fun acceptLegal() {
        // After a deletion the device starts over without an identity.
        if (!sessionStore.isRegistered) ensureRegistered()
        val legal = _config.value.legal
        val consent = apiCall(json) {
            api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion))
        }
        prefs.setTermsAccepted(legal.termsVersion)
        prefs.setAgreed(legal.termsVersion, legal.privacyVersion)
        guard.agreed = true
        updateMe { it.copy(consent = consent) }
    }

    /** v0.9: usage analytics. Never gates anything; AnalyticsGate follows the result. */
    suspend fun setAnalytics(granted: Boolean): ConsentState {
        ensureAccount()
        val consent = apiCall(json) { api.putConsent(ConsentRequest(analytics = granted)) }
        updateMe { it.copy(consent = consent) }
        return consent
    }

    suspend fun setAiProcessing(granted: Boolean): ConsentState {
        ensureAccount()
        val consent = apiCall(json) { api.putConsent(ConsentRequest(aiProcessing = granted)) }
        prefs.setAiAccepted(_config.value.legal.privacyVersion, allowed = granted)
        updateMe { it.copy(consent = consent) }
        return consent
    }

    // ----- Delivery targets (section 12) -----

    suspend fun setDeliveryTargets(githubUsername: String?, awsAccountId: String?): DeliveryTargets {
        ensureAccount()
        val targets = apiCall(json) {
            api.putDeliveryTargets(DeliveryTargetsRequest(githubUsername = githubUsername, awsAccountId = awsAccountId))
        }
        prefs.setDeliveryTargets(targets.githubUsername, targets.aws?.accountId ?: "")
        updateMe { it.copy(deliveryTargets = targets) }
        return targets
    }

    /** Clears a target: the contract wants an explicit null for that. */
    suspend fun clearDeliveryTarget(github: Boolean = false, aws: Boolean = false): DeliveryTargets {
        ensureAccount()
        val body = kotlinx.serialization.json.buildJsonObject {
            if (github) put("githubUsername", kotlinx.serialization.json.JsonNull)
            if (aws) put("awsAccountId", kotlinx.serialization.json.JsonNull)
        }
        val targets = apiCall(json) { api.putDeliveryTargetsRaw(body) }
        prefs.setDeliveryTargets(targets.githubUsername, targets.aws?.accountId ?: "")
        updateMe { it.copy(deliveryTargets = targets) }
        return targets
    }

    suspend fun refreshDeliveryTargets(): DeliveryTargets {
        val targets = apiCall(json) { api.deliveryTargets() }
        updateMe { it.copy(deliveryTargets = targets) }
        return targets
    }

    suspend fun awsConnect(): AwsConnectInfo {
        ensureAccount()
        return apiCall(json) { api.awsConnect() }
    }

    suspend fun awsVerify(): AwsTarget {
        val aws = apiCall(json) { api.awsVerify() }
        updateMe { it.copy(deliveryTargets = it.deliveryTargets.copy(aws = aws)) }
        return aws
    }

    // ----- Push -----

    suspend fun registerPushFid(fid: String) = apiCall(json) { api.putPushToken(PushTokenRequest(fid = fid)) }

    suspend fun unregisterPush() = apiCall(json) { api.deletePushToken() }

    // ----- Recovery (section 3.3) -----

    suspend fun recover(recoveryKey: String): Me {
        val key = recoveryKey.trim()
        val response = apiCall(json) {
            api.recoverDevice(
                RecoverDeviceRequest(
                    recoveryKey = key,
                    installId = sessionStore.installId,
                    appVersion = DeviceInfo.appVersion,
                    deviceModel = DeviceInfo.deviceModel,
                    osVersion = DeviceInfo.osVersion,
                ),
            )
        }
        return adopt(response.clientId, response.accessToken, key)
    }

    /** R-079: back in with the recovery email and its 6-digit code (contract 3.3). */
    suspend fun recoverByEmail(email: String, otp: String): Me {
        val response = apiCall(json) {
            api.recoverByEmailConfirm(
                RecoverByEmailConfirmRequest(
                    email = email.trim(),
                    otp = otp,
                    installId = sessionStore.installId,
                    appVersion = DeviceInfo.appVersion,
                    deviceModel = DeviceInfo.deviceModel,
                    osVersion = DeviceInfo.osVersion,
                ),
            )
        }
        return adopt(response.clientId, response.accessToken, null)
    }

    /** R-079: this phone joins the client whose other phone made [code] (contract 3.2). */
    suspend fun link(code: String): Me {
        val response = apiCall(json) { api.registerDevice(DeviceInfo.registerRequest(sessionStore.installId, linkCode = code.trim())) }
        return adopt(response.clientId, response.accessToken, null)
    }

    /**
     * This phone is now [clientId]. The container drops the previous client's projects and credits when the id
     * changes. R-056: the person agreed on this phone; the restored account gets that consent, no second screen.
     */
    private suspend fun adopt(clientId: String, accessToken: String, recoveryKey: String?): Me {
        sessionStore.replace(clientId, accessToken, recoveryKey)
        signedOutHere = false
        refreshConfig()
        return completeConsent(refreshMe())
    }

    /** Always 202, whether or not the email is on an account (no enumeration). */
    suspend fun sendRecoveryCode(email: String) {
        apiCall(json) { api.recoverByEmail(RecoverByEmailRequest(email.trim())) }
    }

    // ----- Recovery email, link codes and phones (sections 3.1-3.3, R-079) -----

    suspend fun setRecoveryEmail(email: String) {
        ensureAccount()
        apiCall(json) { api.setRecoveryEmail(RecoveryEmailRequest(email.trim())) }
    }

    suspend fun confirmRecoveryEmail(otp: String): Me {
        apiCall(json) { api.confirmRecoveryEmail(OtpRequest(otp)) }
        return refreshMe()
    }

    suspend fun removeRecoveryEmail(): Me {
        apiCall(json) { api.removeRecoveryEmail() }
        return refreshMe()
    }

    suspend fun createLinkCode(): LinkCodeResponse {
        ensureAccount()
        return apiCall(json) { api.createLinkCode() }
    }

    suspend fun devices(): List<DeviceSummary> = apiCall(json) { api.devices() }.devices

    suspend fun revokeDevice(deviceId: String) = apiCall(json) { api.revokeDevice(deviceId) }

    /**
     * "Sign out this phone": the server forgets this phone's token first, so a later start can't slip back into the
     * account; then the identity goes. The install id, the agreement made here and the drafts all stay. If the server
     * can't be told (offline), nothing changes and the error shows.
     */
    suspend fun signOutThisPhone(deviceId: String) {
        try {
            apiCall(json) { api.revokeDevice(deviceId) }
        } catch (e: ApiException) {
            if (!e.isUnauthenticated && e.code != ApiException.NOT_FOUND) throw e   // already gone is fine
        }
        signedOutHere = true
        sessionStore.clear()
    }

    /** Issues a fresh recovery key (the old one stops working) and remembers it locally. */
    suspend fun issueRecoveryKey(): String {
        // Agreed on the way to Restore but asked for this phone's own key instead: make the account first.
        if (!sessionStore.isRegistered) completeConsent(ensureRegistered())
        val key = apiCall(json) { api.issueRecoveryKey() }.recoveryKey
        sessionStore.recoveryKey = key
        return key
    }

    // ----- Privacy (section 14) -----

    suspend fun exportData(onProgress: (JobState) -> Unit = {}): ExportResult {
        ensureAccount()
        val job = apiCall(json) { api.exportData() }.job
        return jobs.await(job, ExportResult.serializer(), onProgress = onProgress)
    }

    /** Asks for deletion server-side, then forgets this device locally. */
    suspend fun deleteMe(): DeleteMeResponse {
        val response = apiCall(json) { api.deleteMe() }
        sessionStore.wipe()
        prefs.clear()
        cache.clearAll()
        _me.value = null
        guard.agreed = false
        return response
    }

    fun isAuthError(t: Throwable) = (t as? ApiException)?.isUnauthenticated == true
}
