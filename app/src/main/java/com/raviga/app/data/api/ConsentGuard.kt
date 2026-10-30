package com.raviga.app.data.api

import retrofit2.http.GET
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.resumeWithException

/**
 * R-056 (founder, 2026-10-29; contract v0.9.2 section 3): until the person on this phone agrees to the terms, nothing
 * but GET /config leaves it: no registration, no consent, no push target, no AI or text calls. Every [RavigaApi] call
 * goes through [wrap]; before [agreed] anything else fails here with [ApiException.BEFORE_CONSENT] and never reaches
 * the network (so no 401 can trigger a re-register either).
 */
class ConsentGuard {
    @Volatile var agreed: Boolean = false

    fun wrap(api: RavigaApi): RavigaApi = Proxy.newProxyInstance(RavigaApi::class.java.classLoader, arrayOf(RavigaApi::class.java)) { _, method, args ->
        if (method.declaringClass != Any::class.java && !agreed && !allowedBeforeAgree(method)) {
            val error = ApiException(ApiException.BEFORE_CONSENT, message = "Agree to the terms first.")
            // Every call is a suspend function: fail it through its continuation, as Retrofit does. Thrown from a
            // proxy, a checked exception would arrive wrapped in UndeclaredThrowableException.
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?> ?: throw error
            continuation.intercepted().resumeWithException(error)
            return@newProxyInstance COROUTINE_SUSPENDED
        }
        try {
            method.invoke(api, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    } as RavigaApi

    companion object {
        /** Public and anonymous: no identity, nothing about the person. */
        private val BEFORE_AGREE = setOf("config")

        /**
         * Matched on the route (`@GET("config")`), never the method name: R8 renames RavigaApi's methods in release
         * builds, and a name check there refused GET /config too, so Agree could not load the terms versions.
         */
        fun allowedBeforeAgree(method: Method): Boolean = method.getAnnotation(GET::class.java)?.value in BEFORE_AGREE
    }
}
