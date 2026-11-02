package com.raviga.app.data.api

import android.os.Build
import com.raviga.app.BuildConfig
import com.raviga.app.data.local.SessionTokens
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.Locale

/** Device facts sent at register and recover. */
object DeviceInfo {
    val appVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')
    val deviceModel: String get() = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ")
    val osVersion: String get() = "Android ${Build.VERSION.RELEASE}"
    val locale: String get() = Locale.getDefault().toLanguageTag()

    fun registerRequest(installId: String, linkCode: String? = null) = RegisterDeviceRequest(
        installId = installId,
        appVersion = appVersion,
        deviceModel = deviceModel,
        osVersion = osVersion,
        locale = locale,
        linkCode = linkCode,
    )
}

/**
 * On 401 the token was replaced or revoked. Re-registering with the same
 * installId returns the same clientId and a fresh token, so the app heals
 * itself without bothering the client. Runs on OkHttp's thread, hence the
 * blocking call through a plain client.
 *
 * If the device itself was revoked (removed from another phone) its installId
 * no longer maps to the client and the backend answers with a different, new
 * client. That is a sign-out, not a heal: the new identity is kept (its
 * recovery key is shown only once), the failed request is not replayed against
 * it, and [onSignedOut] lets the app start over. With no session at all
 * (never registered, or wiped after deletion) there is nothing to heal.
 */
class ReRegisterAuthenticator(
    private val baseUrl: String,
    private val plainClient: OkHttpClient,
    private val json: Json,
    private val sessionStore: SessionTokens,
    private val onSignedOut: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (priorResponses(response) >= 2) return null
        if (response.request.url.encodedPath.endsWith("/devices/register")) return null
        synchronized(this) {
            val failed = response.request.header("Authorization")
            val current = sessionStore.accessToken
            if (current.isNullOrBlank()) return null
            if (failed != "Bearer $current") {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val previousClient = sessionStore.clientId
            val fresh = registerBlocking() ?: return null
            if (fresh.clientId != previousClient) {
                onSignedOut()
                return null
            }
            return response.request.newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build()
        }
    }

    private fun registerBlocking(): RegisterDeviceResponse? {
        val body = json.encodeToString(RegisterDeviceRequest.serializer(), DeviceInfo.registerRequest(sessionStore.installId))
        val request = Request.Builder()
            .url(baseUrl + "devices/register")
            .header("X-App-Platform", "android")
            .header("X-App-Version", DeviceInfo.appVersion)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            plainClient.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val text = r.body?.string() ?: return null
                val parsed = json.decodeFromString(RegisterDeviceResponse.serializer(), text)
                sessionStore.set(parsed.clientId, parsed.accessToken, parsed.recoveryKey)
                parsed
            }
        }.getOrNull()
    }

    private fun priorResponses(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
