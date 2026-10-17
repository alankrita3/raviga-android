package com.raviga.app.di

import android.app.Application
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.BuildConfig
import com.raviga.app.data.api.AuthInterceptor
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.ReRegisterAuthenticator
import com.raviga.app.data.audio.DictationEngine
import com.raviga.app.data.billing.BillingManager
import com.raviga.app.data.demo.DemoApi
import com.raviga.app.data.local.CacheStore
import com.raviga.app.data.local.PrefsStore
import com.raviga.app.data.local.SessionStore
import com.raviga.app.data.repo.CreditsRepository
import com.raviga.app.data.repo.ProjectRepository
import com.raviga.app.data.repo.SessionRepository
import com.raviga.app.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

sealed interface AppEvent {
    /** A push said this project changed; screens showing it refresh. */
    data class ProjectUpdated(val projectId: String) : AppEvent
    data object CreditsUpdated : AppEvent
    data object ExportReady : AppEvent
    /** This phone was revoked from the client's account and now holds a fresh one; start over. */
    data object SignedOut : AppEvent
    /** The terms or privacy policy changed; the client must agree again before content routes work. */
    data object LegalRequired : AppEvent
}

/**
 * Hand-wired dependency graph: one instance per process, created by
 * [com.raviga.app.RavigaApp]. Small enough that a DI framework would
 * cost more than it saves.
 */
class AppContainer(private val app: Application) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore = SessionStore(app)
    val prefs = PrefsStore(app)
    val cache = CacheStore(app, json)

    /**
     * True until secrets.properties names a backend, or when a debug build has
     * the "demo backend" switch on in Settings; the demo backend then serves everything.
     */
    val isDemo: Boolean = BuildConfig.API_BASE_URL.isBlank() ||
        (BuildConfig.DEBUG && com.raviga.app.data.local.DebugFlags.useDemoBackend(app))

    private val baseUrl: String = BuildConfig.API_BASE_URL.let { if (it.isBlank() || it.endsWith("/")) it else "$it/" }

    /** No interceptors: used for presigned uploads and the token re-register call. */
    val plainHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor { sessionStore.accessToken })
        .addInterceptor(com.raviga.app.data.api.ConsentInterceptor { emit(AppEvent.LegalRequired) })
        .authenticator(ReRegisterAuthenticator(baseUrl, plainHttp, json, sessionStore, onSignedOut = ::onSignedOut))
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: RavigaApi = if (isDemo) {
        DemoApi(cache, json)
    } else {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    val jobs = JobRunner(api, json)

    val session = SessionRepository(api, json, jobs, sessionStore, prefs, cache)
    val projects = ProjectRepository(api, json, cache)
    val ai = com.raviga.app.data.repo.AiRepository(api, json, jobs)

    /** Drafts live only on this phone, sealed with a Keystore key (contract v0.6). */
    val draftStore = com.raviga.app.data.drafts.DraftStore(
        java.io.File(app.filesDir, "drafts"),
        json,
        com.raviga.app.data.drafts.KeystoreSealer(),
    )
    val drafts = com.raviga.app.data.repo.DraftRepository(draftStore, ai, projects)
    val billing = BillingManager(app, BuildConfig.REVENUECAT_API_KEY)
    val credits = CreditsRepository(api, json, cache, billing)
    val push = PushRegistrar(app, prefs, session, appScope)

    val files = com.raviga.app.data.files.FileImporter(app)
    val extractor = com.raviga.app.data.files.TextExtractor(app)
    val exports = com.raviga.app.data.files.ExportBuilder(app, plainHttp, draftStore)

    val dictation = DictationEngine(app)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    fun emit(event: AppEvent) { _events.tryEmit(event) }

    private val _signedOut = MutableStateFlow(false)

    /** True after a revoked phone was given a fresh account; Home explains it once. */
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()
    fun dismissSignedOut() { _signedOut.value = false }

    private fun onSignedOut() {
        _signedOut.value = true
        emit(AppEvent.SignedOut)
    }

    init {
        appScope.launch { session.config.collect { com.raviga.app.ui.status.StatusCopy.supportEmail = it.legal.supportEmail } }
    }

    init {
        // Everything tied to a client follows the stored clientId: RevenueCat's app user,
        // the push token's device record, and the cached projects and credits.
        appScope.launch {
            var known: String? = null
            var first = true
            sessionStore.clientIdFlow.collect { id ->
                val previous = known
                known = id
                val initial = first
                first = false
                if (previous != null && id != previous) forgetClient()
                if (id == null) return@collect
                billing.identify(id)
                if (!initial) {
                    push.identityChanged()
                    if (previous != null) {
                        launch { runCatching { projects.refreshAll() } }
                        launch { runCatching { credits.refresh() } }
                    }
                }
            }
        }
    }

    private suspend fun forgetClient() {
        session.resetMe()
        credits.reset()
        projects.reset()
        prefs.clearClientData()
    }
}
