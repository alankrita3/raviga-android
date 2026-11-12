package com.raviga.app.data.repo

import com.raviga.app.data.api.AiAppendRequest
import com.raviga.app.data.api.AiDocumentResult
import com.raviga.app.data.api.AiDraftRequest
import com.raviga.app.data.api.AiInput
import com.raviga.app.data.api.AiRegenerateRequest
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.DocumentBody
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.JobEnvelope
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.JobState
import com.raviga.app.data.api.QuoteRequest
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.data.api.apiCall
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * The stateless AI calls (contract v0.6): the text goes up, a brief or a
 * quote comes back, and the server keeps nothing. Results are short-lived;
 * if one expired before we read it, the call is simply run again once.
 */
class AiRepository(
    private val api: RavigaApi,
    private val json: Json,
    private val jobs: JobRunner,
) {
    suspend fun draft(
        inputs: List<AiInput>,
        title: String?,
        onProgress: (JobState) -> Unit = {},
        onStarted: suspend (JobEnvelope) -> Unit = {},
    ): AiDocumentResult =
        run(AiDocumentResult.serializer(), onProgress, onStarted) { api.aiDraft(AiDraftRequest(inputs, title?.ifBlank { null })) }

    /** v0.16.0: one of the brief's AI edits; [buyEditPack] only after the person agreed to buy 5 more. */
    suspend fun append(
        briefId: String,
        document: DocumentBody,
        inputs: List<AiInput>,
        buyEditPack: Boolean = false,
        onProgress: (JobState) -> Unit = {},
        onStarted: suspend (JobEnvelope) -> Unit = {},
    ): AiDocumentResult =
        run(AiDocumentResult.serializer(), onProgress, onStarted) {
            api.aiAppend(AiAppendRequest(briefId, document, inputs, buyEditPack.takeIf { it }))
        }

    suspend fun regenerate(
        briefId: String,
        document: DocumentBody,
        sectionId: String,
        instruction: String,
        context: List<AiInput>?,
        buyEditPack: Boolean = false,
        onProgress: (JobState) -> Unit = {},
        onStarted: suspend (JobEnvelope) -> Unit = {},
    ): AiDocumentResult = run(AiDocumentResult.serializer(), onProgress, onStarted) {
        api.aiRegenerate(
            AiRegenerateRequest(
                briefId, document, sectionId, instruction.trim().ifBlank { null }, context?.takeIf { it.isNotEmpty() },
                buyEditPack.takeIf { it },
            ),
        )
    }

    /** v0.16.0: a new id for a brief the server doesn't know (made before v0.16.0, or forgotten); no free edits. */
    suspend fun registerBrief(): com.raviga.app.data.api.BriefRegistration = apiCall(json) { api.registerBrief() }

    /** v0.16.0: what's left on a brief. */
    suspend fun aiEdits(briefId: String): com.raviga.app.data.api.AiEditsCount = apiCall(json) { api.aiEdits(briefId) }

    suspend fun quote(
        document: DocumentBody,
        onProgress: (JobState) -> Unit = {},
        onStarted: suspend (JobEnvelope) -> Unit = {},
    ): QuoteResult =
        run(QuoteResult.serializer(), onProgress, onStarted) { api.createQuote(QuoteRequest(document)) }

    /** R-083: waits for a job started before the app was closed, by its id (GET /jobs/{id}, then as usual). */
    suspend fun resumeDocument(jobId: String, onProgress: (JobState) -> Unit = {}): AiDocumentResult =
        jobs.await(apiCall(json) { api.job(jobId) }, AiDocumentResult.serializer(), onProgress)

    suspend fun resumeQuote(jobId: String, onProgress: (JobState) -> Unit = {}): QuoteResult =
        jobs.await(apiCall(json) { api.job(jobId) }, QuoteResult.serializer(), onProgress)

    private suspend fun <T> run(
        deserializer: DeserializationStrategy<T>,
        onProgress: (JobState) -> Unit,
        onStarted: suspend (JobEnvelope) -> Unit,
        start: suspend () -> JobEnvelope,
    ): T {
        var attempt = 0
        while (true) {
            try {
                val envelope = apiCall(json) { start() }
                // R-083: the job id is kept on the draft until the result lands; v0.16.0: so are the brief's id and edits.
                onStarted(envelope)
                return jobs.await(envelope.job, deserializer, onProgress)
            } catch (e: ApiException) {
                if (e.code != ApiException.JOB_EXPIRED || ++attempt > 1) throw e
            }
        }
    }
}
