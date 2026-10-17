package com.raviga.app.data.repo

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.AwsConnectInfo
import com.raviga.app.data.api.AwsTarget
import com.raviga.app.data.api.ConsentRequest
import com.raviga.app.data.api.ConsentState
import com.raviga.app.data.api.DeleteMeResponse
import com.raviga.app.data.api.DeliveryTargets
import com.raviga.app.data.api.DeliveryTargetsRequest
import com.raviga.app.data.api.DeviceInfo
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
) {
    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val registerLock = Mutex()

    val clientId: String? get() = sessionStore.clientId
    val recoveryKey: String? get() = sessionStore.recoveryKey
    val isRegistered: Boolean get() = sessionStore.isRegistered

    /** Drops the previous client's [me]; [config] is the same for everyone. */
    fun resetMe() {
        _me.value = null
    }

    suspend fun warmFromCache() {
        cache.read(CacheStore.CONFIG, AppConfig.serializer())?.let { _config.value = it }
        cache.read(CacheStore.ME, Me.serializer())?.let { _me.value = it }
    }

    /** Registers the device on first run; afterwards just refreshes [config] and [me]. */
    suspend fun ensureRegistered(): Me = registerLock.withLock {
        if (!sessionStore.isRegistered) {
            val response = apiCall(json) { api.registerDevice(DeviceInfo.registerRequest(sessionStore.installId)) }
            sessionStore.set(response.clientId, response.accessToken, response.recoveryKey)
        }
        refreshConfig()
        refreshMe()
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

    suspend fun acceptLegal() {
        // After a deletion the device starts over without an identity.
        if (!sessionStore.isRegistered) ensureRegistered()
        val legal = _config.value.legal
        val consent = apiCall(json) {
            api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion))
        }
        prefs.setTermsAccepted(legal.termsVersion)
        updateMe { it.copy(consent = consent) }
    }

    suspend fun setAiProcessing(granted: Boolean): ConsentState {
        val consent = apiCall(json) { api.putConsent(ConsentRequest(aiProcessing = granted)) }
        prefs.setAiAccepted(_config.value.legal.privacyVersion, allowed = granted)
        updateMe { it.copy(consent = consent) }
        return consent
    }

    // ----- Delivery targets (section 12) -----

    suspend fun setDeliveryTargets(githubUsername: String?, awsAccountId: String?): DeliveryTargets {
        val targets = apiCall(json) {
            api.putDeliveryTargets(DeliveryTargetsRequest(githubUsername = githubUsername, awsAccountId = awsAccountId))
        }
        prefs.setDeliveryTargets(targets.githubUsername, targets.aws?.accountId ?: "")
        updateMe { it.copy(deliveryTargets = targets) }
        return targets
    }

    /** Clears a target: the contract wants an explicit null for that. */
    suspend fun clearDeliveryTarget(github: Boolean = false, aws: Boolean = false): DeliveryTargets {
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

    suspend fun awsConnect(): AwsConnectInfo = apiCall(json) { api.awsConnect() }

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
        val response = apiCall(json) {
            api.recoverDevice(
                RecoverDeviceRequest(
                    recoveryKey = recoveryKey.trim(),
                    installId = sessionStore.installId,
                    appVersion = DeviceInfo.appVersion,
                    deviceModel = DeviceInfo.deviceModel,
                    osVersion = DeviceInfo.osVersion,
                ),
            )
        }
        // The container drops the previous client's projects and credits when the id changes.
        sessionStore.set(response.clientId, response.accessToken, recoveryKey.trim())
        refreshConfig()
        return refreshMe()
    }

    /** Issues a fresh recovery key (the old one stops working) and remembers it locally. */
    suspend fun issueRecoveryKey(): String {
        val key = apiCall(json) { api.issueRecoveryKey() }.recoveryKey
        sessionStore.recoveryKey = key
        return key
    }

    // ----- Privacy (section 14) -----

    suspend fun exportData(onProgress: (JobState) -> Unit = {}): ExportResult {
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
        return response
    }

    fun isAuthError(t: Throwable) = (t as? ApiException)?.isUnauthenticated == true
}
