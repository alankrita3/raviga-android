package com.raviga.app.data.repo

import com.raviga.app.data.api.AiDocument
import com.raviga.app.data.api.AiInput
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.JobState
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ResubmitRequest
import com.raviga.app.data.api.SectionBody
import com.raviga.app.data.api.SectionIds
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.Refusal
import com.raviga.app.data.screening.SensitiveScan
import com.raviga.app.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Local-first briefs (contract v0.6). Inputs, every version of the brief and
 * the quote live in [DraftStore] on this phone. Only text goes to the
 * stateless AI; nothing is stored server-side until [submit]. After submit the
 * draft keeps a copy of the brief, so the client can still read it once the
 * server deletes its copy, and so a "changes requested" round is edited here.
 */
class DraftRepository(
    private val store: DraftStore,
    private val ai: AiRepository,
    private val projects: ProjectRepository,
    private val analytics: com.raviga.app.data.analytics.Analytics? = null,
) {
    val drafts: StateFlow<Map<String, LocalDraft>> = store.drafts

    /** Redactions the AI reported on the last write ("We removed an API key from input 2."), shown once. */
    private val _notices = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val notices: StateFlow<Map<String, List<String>>> = _notices
    fun consumeNotices(draftId: String) = _notices.update { it - draftId }

    fun draft(id: String): Flow<LocalDraft?> = drafts.map { resolve(id, it) }.distinctUntilChanged()

    /** The local draft for a draft id, or for a submitted project's server id. */
    fun get(id: String): LocalDraft? = resolve(id, drafts.value)

    private fun resolve(id: String, all: Map<String, LocalDraft>): LocalDraft? =
        all[id] ?: all.values.firstOrNull { it.serverProjectId == id }

    suspend fun load() = store.load()

    suspend fun create(): LocalDraft {
        val now = Time.nowIso()
        return store.create(LocalDraft(id = DraftStore.newId(), createdAt = now, updatedAt = now))
    }

    /** Names a draft before it has a brief (afterwards the title is part of the brief). */
    suspend fun rename(id: String, title: String) = touch(id) { it.copy(title = title.trim()) }

    suspend fun delete(id: String) {
        val draft = get(id) ?: return
        store.delete(draft.id)
    }

    // ----- Inputs -----

    suspend fun addInput(
        id: String,
        kind: String,
        text: String,
        languageDetected: String? = null,
        fileName: String? = null,
        pageCount: Int? = null,
        durationSec: Int? = null,
    ): LocalInput {
        val input = LocalInput(
            id = "in_" + UUID.randomUUID().toString().replace("-", "").take(16),
            kind = kind,
            text = text.trim(),
            languageDetected = languageDetected,
            fileName = fileName,
            pageCount = pageCount,
            durationSec = durationSec,
            createdAt = Time.nowIso(),
        )
        val first = get(id)?.inputs?.isEmpty() == true
        touch(id) { it.copy(inputs = it.inputs + input) }
        if (first) {
            val inputKind = when (kind) {
                "voice" -> com.raviga.app.data.analytics.Analytics.InputKind.VOICE
                "file" -> com.raviga.app.data.analytics.Analytics.InputKind.FILE
                else -> com.raviga.app.data.analytics.Analytics.InputKind.TEXT
            }
            analytics?.briefStarted(inputKind)
        }
        return input
    }

    suspend fun editInput(id: String, inputId: String, text: String) =
        touch(id) { d -> d.copy(inputs = d.inputs.map { if (it.id == inputId) it.copy(text = text.trim()) else it }) }

    suspend fun removeInput(id: String, inputId: String) =
        touch(id) { d -> d.copy(inputs = d.inputs.filterNot { it.id == inputId }, inputsInBrief = d.inputsInBrief - inputId) }

    // ----- The brief -----

    /**
     * Writes the brief from every input (first time), or folds the inputs it
     * doesn't reflect yet into the current brief.
     */
    suspend fun writeBrief(id: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        val draft = require(id)
        val current = draft.document
        if (current == null) {
            val result = ai.draft(draft.inputs.map { it.ai() }, draft.title, onProgress)
            analytics?.briefGenerated(draft.inputs.size, result.document.sections.count { it.body.isNotBlank() })
            commit(id, result.document, "draft", inputsUsed = draft.inputs.map { it.id }, notices = result.notices)
        } else {
            val fresh = draft.newInputs
            if (fresh.isEmpty()) return@guarded draft
            val result = ai.append(current.body(), fresh.map { it.ai() }, onProgress)
            commit(id, result.document, "append", inputsUsed = draft.inputs.map { it.id }, notices = result.notices)
        }
    }

    suspend fun regenerate(id: String, sectionId: String, instruction: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        val draft = require(id)
        val current = draft.document ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val result = ai.regenerate(current.body(), sectionId, instruction, draft.inputs.map { it.ai() }, onProgress)
        // Only the asked-for section changes; the rest stays exactly as the client left it.
        val regenerated = result.document.sections.firstOrNull { it.id == sectionId }
        val sections = current.sections.map { if (it.id == sectionId && regenerated != null) it.copy(body = regenerated.body) else it }
        val heading = current.section(sectionId)?.heading ?: SectionIds.headings[sectionId] ?: sectionId
        commit(
            id,
            AiDocument(current.title, sections, result.document.changeSummary ?: "Regenerated $heading"),
            "regenerate",
            notices = result.notices,
        )
    }

    /** The client's own edits: a new version with these section bodies (and title, if given). */
    suspend fun saveEdit(id: String, title: String?, sections: List<SectionBody>): LocalDraft {
        val draft = require(id)
        val current = draft.document ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val bodies = sections.associate { it.id to it.body }
        val next = current.sections.map { s -> bodies[s.id]?.let { s.copy(body = it) } ?: s }
        val changed = next.filter { s -> current.section(s.id)?.body != s.body }.map { it.heading }
        val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: current.title
        val summary = when {
            changed.isEmpty() && newTitle != current.title -> "You renamed the brief"
            changed.size == 1 -> "You edited ${changed.single()}"
            changed.isEmpty() -> "You edited the brief"
            else -> "You edited ${changed.size} sections"
        }
        return commit(id, AiDocument(newTitle, next, summary), "edit")
    }

    /** Brings back an older version as a new one; nothing is lost. */
    suspend fun restore(id: String, version: Int): LocalDraft {
        val draft = require(id)
        val old = draft.versions.firstOrNull { it.version == version } ?: throw ApiException(ApiException.NOT_FOUND, message = "No version $version.")
        return commit(id, AiDocument(old.document.title, old.document.sections, "Restored version $version"), "restore")
    }

    // ----- Quote and submit -----

    suspend fun quote(id: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        val draft = require(id)
        val current = draft.current ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val result = ai.quote(current.document.body(), onProgress)
        touch(id) {
            it.copy(quote = result.quote.copy(documentVersion = current.version), quoteToken = result.quoteToken, quotedVersion = current.version)
        }
    }

    /** Creates the server project, charged. The brief stays here as the client's own copy. */
    suspend fun submit(id: String, githubUsername: String?, awsAccountId: String?, idempotencyKey: String): Project = guarded(id) {
        val draft = require(id)
        val current = draft.current ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val token = draft.quoteToken?.takeIf { draft.quoteIsCurrent } ?: throw ApiException(ApiException.QUOTE_STALE, message = "Get a new quote first.")
        val project = projects.submit(
            SubmitProjectRequest(current.document.body(), token, githubUsername?.ifBlank { null }, awsAccountId?.ifBlank { null }),
            idempotencyKey,
        )
        touch(id) {
            it.copy(serverProjectId = project.id, submittedVersion = current.version, submittedAt = Time.nowIso(), quoteToken = null)
        }
        project
    }

    suspend fun resubmit(id: String, idempotencyKey: String): Project = guarded(id) {
        val draft = require(id)
        val projectId = draft.serverProjectId ?: throw ApiException(ApiException.INVALID_STATE, message = "This brief hasn't been submitted.")
        val current = draft.current ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val token = draft.quoteToken?.takeIf { draft.quoteIsCurrent } ?: throw ApiException(ApiException.QUOTE_STALE, message = "Get a new quote first.")
        val project = projects.resubmit(projectId, ResubmitRequest(current.document.body(), token), idempotencyKey)
        touch(id) { it.copy(submittedVersion = current.version, submittedAt = Time.nowIso(), quoteToken = null) }
        project
    }

    /**
     * A submitted project this phone has no copy of (moved here with a recovery
     * key) that the team sent back with changes: copy the brief the server holds
     * into a local draft so it can be edited and resubmitted.
     */
    suspend fun importSubmitted(project: Project): LocalDraft {
        get(project.id)?.let { return it }
        val held = project.document ?: throw ApiException(ApiException.NOT_FOUND, message = "The brief is no longer held.")
        val now = Time.nowIso()
        val version = LocalVersion(1, held.asDocument().copy(version = 1, source = "submitted"), now, "submitted", "The brief you submitted")
        return store.create(
            LocalDraft(
                id = DraftStore.newId(),
                title = held.title,
                createdAt = project.createdAt ?: now,
                updatedAt = now,
                versions = listOf(version),
                serverProjectId = project.id,
                submittedVersion = 1,
                submittedAt = project.submission?.submittedAt,
            ),
        )
    }

    // ----- internal -----

    private fun require(id: String): LocalDraft = get(id) ?: throw ApiException(ApiException.NOT_FOUND, message = "That draft is no longer on this phone.")

    private suspend fun touch(id: String, change: (LocalDraft) -> LocalDraft): LocalDraft =
        store.update(require(id).id) { change(it).copy(updatedAt = Time.nowIso()) }

    private suspend fun commit(
        id: String,
        doc: AiDocument,
        source: String,
        inputsUsed: List<String>? = null,
        notices: List<String> = emptyList(),
    ): LocalDraft = touch(id) { d ->
        if (notices.isNotEmpty()) _notices.update { it + (d.id to notices) }
        val number = (d.current?.version ?: 0) + 1
        val sections = doc.sections.map { s ->
            s.copy(heading = s.heading.ifBlank { d.document?.section(s.id)?.heading ?: SectionIds.headings[s.id] ?: s.id })
        }
        val document = Document(
            version = number,
            title = doc.title.ifBlank { d.document?.title ?: d.title },
            sections = sections,
            createdAt = Time.nowIso(),
            source = source,
            changeSummary = doc.changeSummary,
        )
        val version = LocalVersion(number, document, document.createdAt!!, source, doc.changeSummary.orEmpty())
        // The server redacted something it should never have seen; take it out of the local notes too.
        val inputs = if (notices.isEmpty()) d.inputs else d.inputs.map { it.copy(text = SensitiveScan.redact(it.text)) }
        d.copy(
            title = document.title,
            versions = d.versions + version,
            inputs = inputs,
            inputsInBrief = inputsUsed ?: d.inputsInBrief,
        )
    }

    /** A policy refusal freezes the draft: same block as a refused project, delete is the only way out. */
    private suspend fun <T> guarded(id: String, block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.code == ApiException.CONTENT_REJECTED && e.detailString("kind") == "policy") {
            runCatching { touch(id) { it.copy(refusal = Refusal(e.message ?: "We can't take this project on.", Time.nowIso())) } }
        }
        throw e
    }

    private fun LocalInput.ai() = AiInput(kind = kind, text = text, languageDetected = languageDetected, fileName = fileName)
}
