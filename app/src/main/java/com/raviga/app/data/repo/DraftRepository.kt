package com.raviga.app.data.repo

import com.raviga.app.data.drafts.SentSubmit
import com.raviga.app.data.api.AiDocument
import com.raviga.app.data.api.AiInput
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.JobState
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.SectionBody
import com.raviga.app.data.api.SectionIds
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.DraftTitle
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.PendingJob
import com.raviga.app.data.drafts.Refusal
import com.raviga.app.data.screening.SensitiveScan
import com.raviga.app.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** limits.inputsPerProject from /config (QA F5): how many notes a draft can hold. */
    private val maxNotes: () -> Int = { Int.MAX_VALUE },
    /** R-082: the on-device model's title for a first note (Gemini Nano), or null where the phone has none. */
    private val titleModel: (suspend (String) -> String?)? = null,
    /** Where the model's title is asked for, a moment after the first note stops changing. */
    private val scope: CoroutineScope? = null,
    private val titleDelayMs: Long = 1_500,
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

    /**
     * Names a draft before it has a brief (afterwards the title is part of the brief). The client's name is never
     * replaced (R-082); clearing it hands the name back to the first note, as on iOS.
     */
    suspend fun rename(id: String, title: String): LocalDraft {
        notHeld(id)
        val clean = title.trim()
        touch(id) { it.copy(title = clean, titleSource = if (clean.isEmpty()) null else LocalDraft.TITLE_USER) }
        return retitle(id)
    }

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
        notHeld(id)
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
        noteLimitProblem(get(id)?.inputs?.size ?: 0, maxNotes())?.let { throw ApiException(ApiException.INVALID_ARGUMENT, message = it) }
        val first = get(id)?.inputs?.isEmpty() == true
        touch(id) { it.copy(inputs = it.inputs + input) }
        retitle(id)
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

    suspend fun editInput(id: String, inputId: String, text: String): LocalDraft {
        notHeld(id)
        touch(id) { d -> d.copy(inputs = d.inputs.map { if (it.id == inputId) it.copy(text = text.trim()) else it }) }
        return retitle(id)
    }

    suspend fun removeInput(id: String, inputId: String): LocalDraft {
        notHeld(id)
        touch(id) { d -> d.copy(inputs = d.inputs.filterNot { it.id == inputId }, inputsInBrief = d.inputsInBrief - inputId) }
        return retitle(id)
    }

    // ----- R-082: the draft's name from its first note -----

    private val titleJobs = mutableMapOf<String, Job>()

    /**
     * Names the draft from its first note while the name is still the app's: at once by the fallback rules, then,
     * where the phone has Gemini Nano, by the model. Never over the client's own name, never once a brief exists.
     * Only the first note names it. Nothing leaves the phone, and the title goes into no analytics.
     */
    private suspend fun retitle(id: String): LocalDraft {
        val draft = require(id)
        if (!draft.titleIsTheApps) return draft
        val note = draft.inputs.firstOrNull()?.text ?: return draft
        val title = DraftTitle.fallback(note) ?: return draft
        val named = if (title != draft.title || draft.titleSource != LocalDraft.TITLE_AUTO) {
            store.update(draft.id) { it.copy(title = title, titleSource = LocalDraft.TITLE_AUTO) }
        } else {
            draft
        }
        askTheModel(draft.id, note)
        return named
    }

    private fun askTheModel(id: String, note: String) {
        val model = titleModel ?: return
        val scope = scope ?: return
        synchronized(titleJobs) {
            titleJobs[id]?.cancel()
            titleJobs[id] = scope.launch {
                delay(titleDelayMs)
                val title = model(note) ?: return@launch
                fun stillTheApps(d: LocalDraft) =
                    d.titleSource == LocalDraft.TITLE_AUTO && d.versions.isEmpty() && d.inputs.firstOrNull()?.text == note
                val now = store.drafts.value[id] ?: return@launch
                if (!stillTheApps(now) || now.title == title) return@launch
                runCatching { store.update(id) { d -> if (stillTheApps(d)) d.copy(title = title) else d } }
            }
        }
    }

    // ----- The brief -----

    /**
     * Writes the brief from every input (first time), or folds the inputs it
     * doesn't reflect yet into the current brief.
     */
    suspend fun writeBrief(id: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        notHeld(id)
        // R-083: a draft or append job from before the app closed is collected, not paid for twice.
        if (require(id).pendingJob?.kind.let { it == PendingJob.DRAFT || it == PendingJob.APPEND }) {
            resumePending(id, onProgress)?.let { return@guarded it }
        }
        val draft = require(id)
        val current = draft.document
        val inputsUsed = draft.inputs.map { it.id }
        if (current == null) {
            val result = tracked(id, { PendingJob(it, PendingJob.DRAFT, Time.nowIso(), inputsUsed) }) { started ->
                ai.draft(draft.inputs.map { it.ai() }, draft.titleForTheAi, onProgress, started)
            }
            finishDraft(id, result, inputsUsed)
        } else {
            val fresh = draft.newInputs
            if (fresh.isEmpty()) return@guarded draft
            val result = tracked(id, { PendingJob(it, PendingJob.APPEND, Time.nowIso(), inputsUsed) }) { started ->
                ai.append(current.body(), fresh.map { it.ai() }, onProgress, started)
            }
            commit(id, result.document, "append", inputsUsed = inputsUsed, notices = result.notices)
        }
    }

    suspend fun regenerate(id: String, sectionId: String, instruction: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        notHeld(id)
        val draft = require(id)
        val current = draft.document ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val result = tracked(id, { PendingJob(it, PendingJob.REGENERATE, Time.nowIso(), sectionId = sectionId) }) { started ->
            ai.regenerate(current.body(), sectionId, instruction, draft.inputs.map { it.ai() }, onProgress, started)
        }
        finishRegenerate(id, result, sectionId)
    }

    // ----- R-083: a job the app was closed during -----

    /**
     * Runs an AI call with its job id kept on the draft from the moment the server accepts it. The result clears it
     * (and so does a failure); a cancelled call (the screen closed) or a killed app keeps it, and [resumePending]
     * collects the result the next time the draft or its quote opens.
     */
    private suspend fun <T> tracked(id: String, pending: (String) -> PendingJob, call: suspend (suspend (String) -> Unit) -> T): T {
        try {
            val result = call { jobId -> store.update(require(id).id) { it.copy(pendingJob = pending(jobId)) } }
            clearPending(id)
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Offline while it ran: the job goes on at the server; keep it to collect on the next try or open.
            if ((e as? ApiException)?.code != ApiException.NETWORK) clearPending(id)
            throw e
        }
    }

    private suspend fun clearPending(id: String) {
        val draft = get(id) ?: return
        if (draft.pendingJob != null) store.update(draft.id) { it.copy(pendingJob = null) }
    }

    /**
     * Collects a job started before the app closed, landing it exactly as the call would have. Null when none is
     * pending. Past the 20-minute window, or when the server no longer has it: [ApiException.JOB_LOST].
     */
    suspend fun resumePending(id: String, onProgress: (JobState) -> Unit = {}, now: Long = System.currentTimeMillis()): LocalDraft? {
        val draft = get(id) ?: return null
        val pending = draft.pendingJob ?: return null
        val started = Time.parse(pending.startedAt)?.toEpochMilli() ?: 0L
        if (now - started > PendingJob.WINDOW_MS) {
            clearPending(id)
            throw ApiException(ApiException.JOB_LOST, message = "That stopped when the app closed. Try again.")
        }
        return guarded(id) {
            try {
                val landed = when (pending.kind) {
                    PendingJob.QUOTE -> finishQuote(id, ai.resumeQuote(pending.jobId, onProgress), pending.baseVersion ?: (draft.current?.version ?: 0))
                    PendingJob.REGENERATE -> finishRegenerate(id, ai.resumeDocument(pending.jobId, onProgress), pending.sectionId.orEmpty())
                    PendingJob.APPEND -> {
                        val result = ai.resumeDocument(pending.jobId, onProgress)
                        commit(id, result.document, "append", inputsUsed = pending.inputsUsed, notices = result.notices)
                    }
                    else -> finishDraft(id, ai.resumeDocument(pending.jobId, onProgress), pending.inputsUsed)
                }
                clearPending(id)
                landed
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == ApiException.NETWORK) throw e   // still collectable later
                clearPending(id)
                if (e.code == ApiException.NOT_FOUND || e.code == ApiException.JOB_EXPIRED) {
                    throw ApiException(ApiException.JOB_LOST, message = "That stopped when the app closed. Try again.")
                }
                throw e
            }
        }
    }

    private suspend fun finishDraft(id: String, result: com.raviga.app.data.api.AiDocumentResult, inputsUsed: List<String>): LocalDraft {
        analytics?.briefGenerated(inputsUsed.size, result.document.sections.count { it.body.isNotBlank() })
        return commit(id, result.document, "draft", inputsUsed = inputsUsed, notices = result.notices)
    }

    private suspend fun finishRegenerate(id: String, result: com.raviga.app.data.api.AiDocumentResult, sectionId: String): LocalDraft {
        val current = require(id).document ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        // Only the asked-for section changes; the rest stays exactly as the client left it.
        val regenerated = result.document.sections.firstOrNull { it.id == sectionId }
        val sections = current.sections.map { if (it.id == sectionId && regenerated != null) it.copy(body = regenerated.body) else it }
        val heading = current.section(sectionId)?.heading ?: SectionIds.headings[sectionId] ?: sectionId
        return commit(
            id,
            AiDocument(current.title, sections, result.document.changeSummary ?: "Regenerated $heading"),
            "regenerate",
            notices = result.notices,
        )
    }

    private suspend fun finishQuote(id: String, result: com.raviga.app.data.api.QuoteResult, version: Int): LocalDraft =
        touch(id) { it.copy(quote = result.quote.copy(documentVersion = version), quoteToken = result.quoteToken, quotedVersion = version) }

    /** The client's own edits: a new version with these section bodies (and title, if given). */
    suspend fun saveEdit(id: String, title: String?, sections: List<SectionBody>): LocalDraft {
        notHeld(id)
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
        notHeld(id)
        val draft = require(id)
        val old = draft.versions.firstOrNull { it.version == version } ?: throw ApiException(ApiException.NOT_FOUND, message = "No version $version.")
        return commit(id, AiDocument(old.document.title, old.document.sections, "Restored version $version"), "restore")
    }

    // ----- Quote and submit -----

    suspend fun quote(id: String, onProgress: (JobState) -> Unit = {}): LocalDraft = guarded(id) {
        notHeld(id)
        if (require(id).pendingJob?.kind == PendingJob.QUOTE) resumePending(id, onProgress)?.let { return@guarded it }
        val draft = require(id)
        val current = draft.current ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
        val result = tracked(id, { PendingJob(it, PendingJob.QUOTE, Time.nowIso(), baseVersion = current.version) }) { started ->
            ai.quote(current.document.body(), onProgress, started)
        }
        finishQuote(id, result, current.version)
    }

    /** Creates the server project (free since v0.10.0). The brief stays here as the client's own copy. */
    suspend fun submit(id: String, awsAccountId: String?, idempotencyKey: String): Project = guarded(id) {
        val draft = require(id)
        // R-094 P2-6: a send already out with this token goes again exactly as it was.
        val held = draft.sentSubmit?.takeIf { draft.holdsSentSubmit }?.let { s -> s.submit?.let { it to s.version } }
        val (request, version) = held ?: run {
            val current = draft.current ?: throw ApiException(ApiException.INVALID_STATE, message = "Write the brief first.")
            val token = draft.quoteToken?.takeIf { draft.quoteIsCurrent } ?: throw ApiException(ApiException.QUOTE_STALE, message = "Get a new quote first.")
            val request = SubmitProjectRequest(current.document.body(), token, awsAccountId?.ifBlank { null })
            touch(id) { it.copy(sentSubmit = SentSubmit(token, current.version, submit = request)) }
            request to current.version
        }
        val project = sending(id) { projects.submit(request, idempotencyKey) }
        touch(id) {
            it.copy(serverProjectId = project.id, submittedVersion = version, submittedAt = Time.nowIso(), quoteToken = null, sentSubmit = null)
        }
        project
    }

    /** R-094 P2-6: a held send is let go only when the server answered that it didn't take it. */
    private suspend fun <T> sending(id: String, call: suspend () -> T): T = try {
        call()
    } catch (e: ApiException) {
        if (SentSubmit.refusedForGood(e)) runCatching { touch(id) { it.copy(sentSubmit = null) } }
        throw e
    }

    /**
     * R-092 #19 / backend R-091: the answer to a submit (or resubmit) was lost and the retry was told the brief is
     * already project [projectId]. The draft becomes that project, as after the submit: the brief kept as the copy
     * submitted, the used quote token dropped, so it isn't re-quoted and charged twice.
     */
    suspend fun adoptSubmitted(id: String, projectId: String): Project {
        val project = projects.refresh(projectId)
        touch(id) {
            it.copy(
                serverProjectId = projectId,
                // The version that was sent (R-094 P2-6), else the newest.
                submittedVersion = it.sentSubmit?.version ?: it.current?.version ?: it.submittedVersion,
                submittedAt = project.submission?.submittedAt ?: Time.nowIso(),
                quoteToken = null,
                sentSubmit = null,
            )
        }
        return project
    }

    // ----- internal -----

    private fun require(id: String): LocalDraft = get(id) ?: throw ApiException(ApiException.NOT_FOUND, message = "That draft is no longer on this phone.")

    /**
     * R-098 P3-4 (iOS review 3, the PM's call): while a submit is held the brief is read-only (notes, sections, the
     * title, versions, a new quote), so what the retry sends is what the phone shows; it opens again once the answer
     * is seen. An app's own invalid_state line, shown as written.
     */
    private fun notHeld(id: String) {
        if (get(id)?.holdsSentSubmit == true) throw ApiException(ApiException.INVALID_STATE, message = HELD_EDIT)
    }

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
        val createdAt = Time.nowIso()
        // R-082: the first brief's title replaces a name made from the first note, never the client's own.
        val clientsName = d.versions.isEmpty() && d.titleSource == LocalDraft.TITLE_USER && d.title.isNotBlank()
        val document = Document(
            version = number,
            title = if (clientsName) d.title else doc.title.ifBlank { d.document?.title ?: d.title },
            sections = sections,
            createdAt = createdAt,
            source = source,
            changeSummary = doc.changeSummary,
        )
        val version = LocalVersion(number, document, createdAt, source, doc.changeSummary.orEmpty())
        // The server redacted something it should never have seen; take it out of the local notes too.
        val inputs = if (notices.isEmpty()) d.inputs else d.inputs.map { it.copy(text = SensitiveScan.redact(it.text)) }
        d.copy(
            title = document.title,
            titleSource = null,
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

    companion object {
        /** R-098: iOS's words for an edit tried while a submit is held. */
        const val HELD_EDIT = "Editing opens again once your Submit is confirmed."

        /** QA F5, iOS's words: a draft already holding [max] notes takes no more. */
        fun noteLimitProblem(count: Int, max: Int): String? =
            if (max in 1..count) "A project can have up to $max notes. Add the rest to an existing one." else null
    }
}
