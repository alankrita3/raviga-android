package com.raviga.app.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/**
 * One exception type for everything that goes wrong talking to the backend.
 * Callers branch on [code], never on [message]; the UI maps codes to copy.
 * Decode failures are not network failures (contract section 2).
 */
open class ApiException(
    val code: String,
    val httpStatus: Int = 0,
    message: String? = null,
    val details: JsonElement? = null,
    cause: Throwable? = null,
) : Exception(message ?: code, cause) {

    val isNetwork get() = code == NETWORK
    val isUnauthenticated get() = httpStatus == 401 || code == UNAUTHENTICATED

    fun detailInt(key: String): Int? = runCatching { details?.jsonObject?.get(key)?.jsonPrimitive?.int }.getOrNull()
    fun detailString(key: String): String? = runCatching { details?.jsonObject?.get(key)?.jsonPrimitive?.content }.getOrNull()
    fun detailStrings(key: String): List<String> = runCatching {
        details?.jsonObject?.get(key)?.jsonArray?.map { it.jsonPrimitive.content }
    }.getOrNull().orEmpty()

    companion object {
        const val NETWORK = "network"
        const val DECODE = "decode"

        const val UNAUTHENTICATED = "unauthenticated"
        const val PERMISSION_DENIED = "permission_denied"
        const val CONSENT_REQUIRED = "consent_required"
        const val NOT_FOUND = "not_found"
        const val INVALID_ARGUMENT = "invalid_argument"
        const val GITHUB_USER_NOT_FOUND = "github_user_not_found"
        const val INVALID_STATE = "invalid_state"
        const val DOCUMENT_LOCKED = "document_locked"
        const val VERSION_CONFLICT = "version_conflict"
        const val QUOTE_STALE = "quote_stale"
        const val INSUFFICIENT_CREDITS = "insufficient_credits"
        const val REVISION_LIMIT_REACHED = "revision_limit_reached"
        const val ACTIVE_PROJECTS = "active_projects"
        const val AWS_ROLE_NOT_READY = "aws_role_not_ready"
        const val OTP_INVALID = "otp_invalid"
        const val UPGRADE_REQUIRED = "upgrade_required"
        const val RATE_LIMITED = "rate_limited"
        const val AI_UNAVAILABLE = "ai_unavailable"
        const val INTERNAL = "internal"
        const val CONTENT_REJECTED = "content_rejected"
        const val FILE_UNSUPPORTED = "file_unsupported"
        const val FILE_UNREADABLE = "file_unreadable"
        const val ACCOUNT_RESTRICTED = "account_restricted"
        /** Local only, never from the server: [ConsentGuard] stopped a call before the person agreed (R-056). */
        const val BEFORE_CONSENT = "before_consent"
        /** Client-side: a job was still running when we stopped waiting for it. */
        const val JOB_TIMEOUT = "job_timeout"
        /** Client-side: an AI result was gone (read or aged out) before we fetched it; rerun the call. */
        const val JOB_EXPIRED = "job_expired"

        fun from(t: Throwable, json: Json): ApiException = when (t) {
            is ApiException -> t
            is java.lang.reflect.UndeclaredThrowableException -> from(t.undeclaredThrowable ?: t, json)
            is HttpException -> {
                val raw = runCatching { t.response()?.errorBody()?.string() }.getOrNull()
                val body = raw?.let { runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), it).error }.getOrNull() }
                ApiException(
                    code = body?.code ?: defaultCode(t.code()),
                    httpStatus = t.code(),
                    message = body?.message ?: "Request failed (${t.code()})",
                    details = body?.details,
                    cause = t,
                )
            }
            is IOException -> ApiException(NETWORK, 0, "You appear to be offline.", cause = t)
            is kotlinx.serialization.SerializationException ->
                ApiException(DECODE, 0, "Unexpected reply from Raviga.", cause = t)
            else -> ApiException(INTERNAL, 0, t.message ?: "Something went wrong.", cause = t)
        }

        private fun defaultCode(status: Int) = when (status) {
            401 -> UNAUTHENTICATED
            402 -> INSUFFICIENT_CREDITS
            403 -> PERMISSION_DENIED
            404 -> NOT_FOUND
            409 -> INVALID_STATE
            423 -> DOCUMENT_LOCKED
            426 -> UPGRADE_REQUIRED
            429 -> RATE_LIMITED
            503 -> AI_UNAVAILABLE
            else -> INTERNAL
        }
    }
}
