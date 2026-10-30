package com.raviga.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.File
import java.lang.reflect.Method
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** R-056: until Agree, the real Retrofit client behind the guard sends nothing but GET /config. */
class ConsentGuardTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun guarded(guard: ConsentGuard): RavigaApi = guard.wrap(
        Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java),
    )

    /** Calls any RavigaApi method with null arguments, as the app could from anywhere. */
    private suspend fun call(api: RavigaApi, method: Method): Result<Any?> = runCatching {
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            val args = method.parameterTypes.map<Class<*>, Any?> { type ->
                when (type) {
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Boolean::class.javaPrimitiveType -> false
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }.toTypedArray()
            args[args.size - 1] = continuation
            method.invoke(api, *args)
        }
    }

    // Instance calls only: the static `$default` helpers Kotlin adds for default arguments call these.
    private val methods = RavigaApi::class.java.declaredMethods
        .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .sortedBy { it.name }

    @Test
    fun `before Agree only GET config leaves the phone`() = runBlocking {
        val config = javaClass.classLoader!!.getResource("fixtures/config-v092.json")!!.let { File(it.toURI()).readText() }
        server.enqueue(MockResponse().setBody(config))
        val api = guarded(ConsentGuard())
        assertTrue("every call is checked, not a sample", methods.size >= 30)
        val refused = mutableListOf<String>()
        for (method in methods) {
            val result = call(api, method)
            if (method.name == "config") {
                assertTrue("config is allowed: ${result.exceptionOrNull()}", result.isSuccess)
            } else {
                val error = result.exceptionOrNull()
                assertTrue("${method.name} must be refused, got $error", error is ApiException && error.code == ApiException.BEFORE_CONSENT)
                refused += method.name
            }
        }
        assertEquals(1, server.requestCount)
        assertEquals("GET /v1/config", server.takeRequest().let { "${it.method} ${it.path}" })
        assertTrue("registerDevice" in refused && "recoverDevice" in refused && "putConsent" in refused && "putPushToken" in refused)
    }

    /** As R8 leaves RavigaApi in a release build: the routes stay, the method names don't. */
    private interface Renamed {
        @GET("config") suspend fun a(): AppConfig
        @GET("me") suspend fun config(): Me
    }

    @Test
    fun `the allow-list follows the route, not the method name R8 renames`() {
        val byName = Renamed::class.java.declaredMethods.associateBy { it.name }
        assertTrue(ConsentGuard.allowedBeforeAgree(byName.getValue("a")))
        assertFalse(ConsentGuard.allowedBeforeAgree(byName.getValue("config")))
    }

    @Test
    fun `after Agree calls go through`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val guard = ConsentGuard()
        val api = guarded(guard)
        guard.agreed = true
        call(api, methods.first { it.name == "me" })
        assertEquals("GET /v1/me", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `a refused call carries the before-consent code through apiCall`() = runBlocking {
        val error = runCatching { apiCall(json) { guarded(ConsentGuard()).me() } }.exceptionOrNull()
        assertEquals(ApiException.BEFORE_CONSENT, (error as ApiException).code)
        assertEquals(0, server.requestCount)
    }
}
