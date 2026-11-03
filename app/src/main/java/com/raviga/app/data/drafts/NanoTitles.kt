package com.raviga.app.data.drafts

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * R-082: Gemini Nano's title for a first note, through ML Kit's Prompt API, on phones that have it ready (AICore,
 * e.g. recent Pixels and Galaxy phones). The note goes to the on-device model only; nothing leaves the phone. A phone
 * without the model, or with it still to download, gets null and keeps the fallback: this never starts a download.
 */
class NanoTitles(private val client: () -> GenerativeModel = { Generation.getClient() }) {

    private var model: GenerativeModel? = null

    /** True once the model was ready; false once the phone said it has none. Unknown (null) while downloadable. */
    @Volatile private var ready: Boolean? = null

    suspend fun title(note: String): String? {
        val model = runCatching { model ?: client().also { model = it } }.getOrNull() ?: return null
        if (!isReady(model)) return null
        val text = note.take(DraftTitle.INPUT_LIMIT)
        val reply = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                val systemPrompt = com.google.mlkit.genai.prompt.Feature.SYSTEM_PROMPT in model.getAvailableFeatures()
                val request = if (systemPrompt) {
                    generateContentRequest(SystemInstruction(DraftTitle.INSTRUCTIONS), TextPart(text)) { settings() }
                } else {
                    generateContentRequest(TextPart(DraftTitle.INSTRUCTIONS + "\n\n" + text)) { settings() }
                }
                model.generateContent(request).candidates.firstOrNull()?.text
            }.getOrNull()
        }
        return reply?.let(DraftTitle::tidy)
    }

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.settings() {
        temperature = 0.2f
        topK = 10
        candidateCount = 1
        maxOutputTokens = 24
    }

    private suspend fun isReady(model: GenerativeModel): Boolean {
        ready?.let { return it }
        val status = runCatching { model.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
        return when (status) {
            FeatureStatus.AVAILABLE -> true.also { ready = true }
            FeatureStatus.UNAVAILABLE -> false.also { ready = false }
            else -> false   // downloadable or downloading: not ours to fetch; ask again next time
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
