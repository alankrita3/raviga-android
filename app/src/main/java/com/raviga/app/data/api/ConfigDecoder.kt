package com.raviga.app.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * R-149 (iOS ran on its built-in config for a week because one /config field didn't decode): `GET /config` is read
 * one part at a time. Unknown keys were always ignored ([Json.ignoreUnknownKeys]); now a part the app can't read (a
 * changed type, say) falls back to the app's default for that part alone, and [Result.dropped] names it, so the rest
 * of the server's config still applies and the fallback is never silent (logged, and shown in debug Settings).
 *
 * Two levels: a section (`legal`) and, inside it, a field (`legal.termsVersion`). A bad field drops only itself when
 * the section still decodes without it; otherwise the section goes.
 */
object ConfigDecoder {

    data class Result(val config: AppConfig, val dropped: List<String>)

    fun decode(json: Json, raw: JsonObject): Result {
        runCatching { return Result(json.decodeFromJsonElement(AppConfig.serializer(), raw), emptyList()) }
        val dropped = mutableListOf<String>()
        val kept = LinkedHashMap<String, JsonElement>()
        for ((key, value) in raw) {
            if (reads(json, mapOf(key to value))) { kept[key] = value; continue }
            val section = value as? JsonObject
            if (section == null) { dropped += key; continue }
            // Keep the fields of the section that read on their own, if the section reads with just those.
            val bad = section.keys.filter { field -> !reads(json, mapOf(key to JsonObject(mapOf(field to section.getValue(field))))) }
            val partial = JsonObject(section.filterKeys { it !in bad })
            if (bad.size < section.size && reads(json, mapOf(key to partial))) {
                kept[key] = partial
                dropped += bad.map { "$key.$it" }
            } else {
                dropped += key
            }
        }
        val config = runCatching { json.decodeFromJsonElement(AppConfig.serializer(), JsonObject(kept)) }.getOrElse { AppConfig() }
        return Result(config, dropped)
    }

    private fun reads(json: Json, parts: Map<String, JsonElement>): Boolean =
        runCatching { json.decodeFromJsonElement(AppConfig.serializer(), JsonObject(parts)) }.isSuccess
}
