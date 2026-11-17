package com.raviga.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-149 (iOS read none of dev's /config for a week because one field didn't decode, and said nothing): the app reads
 * the server's /config, dev's and prod's as they are now, and a part it can't read falls back on its own, by name.
 */
class ConfigDecoderTest {
    // As AppContainer's.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true; isLenient = true }

    private fun fixture(name: String): JsonObject {
        val url = javaClass.classLoader!!.getResource("fixtures/$name")
        return json.parseToJsonElement(File(url.toURI()).readText()).jsonObject
    }

    private fun JsonObject.with(section: String, field: String, value: kotlinx.serialization.json.JsonElement) =
        JsonObject(this + (section to JsonObject(getValue(section).jsonObject + (field to value))))

    @Test fun prodsLiveConfigIsReadWhole() {
        // Captured from prod's GET /config on 2026-11-17 after the v0.17.1 deploy (nested statusLines and all).
        val (config, dropped) = ConfigDecoder.decode(json, fixture("config-prod-v0171.json"))
        assertEquals(emptyList<String>(), dropped)
        assertEquals("2026-11-17", config.legal.termsVersion)
        assertEquals("2026-11-17", config.legal.privacyVersion)
        assertEquals(listOf("credits_quarter", "credits_10", "credits_25", "credits_50", "credits_100"), config.credits.packs.map { it.productId })
        assertEquals(10.0, config.credits.packs.first { it.productId == "credits_10" }.credits, 0.0)
        assertEquals(ReportConfig.FREE_FIXES_LINE, config.report.freeFixesLine)
        assertTrue(config.ai.providers.single().startsWith("Anthropic"))
    }

    @Test fun devsConfigIsReadWhole() {
        val (config, dropped) = ConfigDecoder.decode(json, fixture("config-v0170.json"))
        assertEquals(emptyList<String>(), dropped)
        assertEquals("2026-11-17", config.legal.termsVersion)
        assertTrue(config.credits.packs.isNotEmpty())
    }

    @Test fun aFieldOfAnUnexpectedTypeDropsOnlyItself() {
        val raw = fixture("config-prod-v0171.json")
            .with("legal", "termsVersion", buildJsonObject { put("v", "2026-11-17") })
        val (config, dropped) = ConfigDecoder.decode(json, raw)
        assertEquals(listOf("legal.termsVersion"), dropped)
        assertEquals("the rest of legal still from the server", "2026-11-17", config.legal.privacyVersion)
        assertEquals("", config.legal.termsVersion)
        assertEquals("other sections untouched", 5, config.credits.packs.size)
    }

    @Test fun aSectionOfAnUnexpectedTypeDropsOnlyThatSection() {
        val raw = JsonObject(fixture("config-prod-v0171.json") + ("credits" to JsonPrimitive("soon")))
        val (config, dropped) = ConfigDecoder.decode(json, raw)
        assertEquals(listOf("credits"), dropped)
        assertTrue(config.credits.packs.isEmpty())
        assertEquals("2026-11-17", config.legal.termsVersion)
    }

    @Test fun unknownKeysAndNewShapesAreIgnoredNotDropped() {
        val raw = JsonObject(
            fixture("config-prod-v0171.json") +
                ("somethingNew" to buildJsonObject { put("nested", buildJsonObject { put("x", 1) }) }),
        ).with("legal", "aNewField", JsonPrimitive(3))
        val (config, dropped) = ConfigDecoder.decode(json, raw)
        assertEquals(emptyList<String>(), dropped)
        assertEquals("2026-11-17", config.legal.termsVersion)
    }

    @Test fun throughRetrofitABadFieldStillLeavesTheServersConfig() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val body = fixture("config-prod-v0171.json").with("report", "freeFixDays", JsonPrimitive("seven")).toString()
            server.enqueue(MockResponse().setBody(body))
            val api = retrofit2.Retrofit.Builder()
                .baseUrl(server.url("/v1/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RavigaApi::class.java)
            val (config, dropped) = ConfigDecoder.decode(json, api.config())
            assertEquals(listOf("report.freeFixDays"), dropped)
            assertEquals(7, config.report.freeFixDays)
            assertEquals("2026-11-17", config.legal.termsVersion)
        } finally {
            server.shutdown()
        }
    }
}
