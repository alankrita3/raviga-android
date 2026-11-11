package com.raviga.app.data.drafts

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Quote
import kotlinx.serialization.Serializable

/**
 * A project that lives only on this phone until it is submitted (founder's
 * local-first rule, contract v0.6). Recordings and files never get here:
 * only the text read from them. After submit it keeps [serverProjectId] and a
 * copy of the brief, so the client can still read it once the server deletes
 * its copy on acceptance.
 */
@Serializable
data class LocalDraft(
    val id: String,
    val title: String = "",
    val createdAt: String,
    val updatedAt: String,
    val inputs: List<LocalInput> = emptyList(),
    /** Inputs the brief already reflects; the rest go to /ai/append next time. */
    val inputsInBrief: List<String> = emptyList(),
    /** Oldest first; each is a full copy of the brief. */
    val versions: List<LocalVersion> = emptyList(),
    val quote: Quote? = null,
    val quoteToken: String? = null,
    /** The brief version the quote was made for; any later edit needs a new quote. */
    val quotedVersion: Int? = null,
    val refusal: Refusal? = null,
    val serverProjectId: String? = null,
    val submittedVersion: Int? = null,
    val submittedAt: String? = null,
    /**
     * R-082: where [title] came from before a brief existed: [TITLE_AUTO], named from the first note on the phone,
     * or [TITLE_USER], the client's own. Null otherwise (no title yet, or the brief's).
     */
    val titleSource: String? = null,
    /** R-083: an AI job started for this draft whose result hasn't been collected yet (the app was closed). */
    val pendingJob: PendingJob? = null,
    /**
     * R-094 P2-6 (iOS review 2): a submit or resubmit sent with [quoteToken] whose answer wasn't seen. A retry sends
     * this exact body (a GitHub name changed since doesn't apply to it), even after the quote's local expiry or an
     * edit: the server decides (contract v0.9.7: a used token answers with its project for 90 days).
     */
    val sentSubmit: SentSubmit? = null,
) {
    /** A send with the current token is out and unanswered: it is retried as it was, never re-quoted. */
    val holdsSentSubmit: Boolean get() = sentSubmit != null && sentSubmit.quoteToken == quoteToken
    val current: LocalVersion? get() = versions.lastOrNull()
    val document: Document? get() = current?.document

    /**
     * R-094 P2-5 (iOS review 2, as iOS 9ad65bc): the brief was changed after it was submitted (a changes round). A
     * cancel keeps those edits as the newer versions; nothing is deleted.
     */
    val hasEditsSinceSubmit: Boolean get() {
        val submitted = submittedVersion?.let { v -> versions.firstOrNull { it.version == v } } ?: return false
        val latest = current ?: return false
        return latest.version != submitted.version &&
            (latest.document.title != submitted.document.title || latest.document.sections != submitted.document.sections)
    }
    val isSubmitted: Boolean get() = serverProjectId != null

    /**
     * v0.12.1 (R-111): a draft from before keeps every version, without the retired open-questions section. Its quote
     * was made with it: the server answers that submit with 409 quote_stale, and the app re-quotes as usual.
     */
    fun withoutRetired(): LocalDraft =
        if (versions.none { v -> v.document.sections.any { it.id == com.raviga.app.data.api.SectionIds.RETIRED_OPEN_QUESTIONS } }) this
        else copy(versions = versions.map { it.copy(document = it.document.withoutRetired()) })
    val isRefused: Boolean get() = refusal != null
    val quoteIsCurrent: Boolean get() = quote != null && quoteToken != null && quotedVersion == current?.version
    val displayTitle: String get() = (document?.title ?: title).ifBlank { title }
    val newInputs: List<LocalInput> get() = inputs.filter { it.id !in inputsInBrief }

    /** The first note may (re)name this draft: no brief yet, and no name of the client's own. */
    val titleIsTheApps: Boolean
        get() = versions.isEmpty() && !isSubmitted && titleSource != TITLE_USER && (titleSource == TITLE_AUTO || title.isBlank())

    /** The AI gets the client's own name for the project, never one the app made from the first note. */
    val titleForTheAi: String get() = if (titleSource == TITLE_AUTO) "" else title

    companion object {
        const val TITLE_AUTO = "auto"
        const val TITLE_USER = "user"
    }
}

@Serializable
data class LocalInput(
    val id: String,
    val kind: String,                 // voice | text | file
    val text: String,
    val languageDetected: String? = null,
    val fileName: String? = null,
    val pageCount: Int? = null,
    val durationSec: Int? = null,
    val createdAt: String,
)

@Serializable
data class LocalVersion(
    val version: Int,
    val document: Document,
    val createdAt: String,
    /** draft | append | regenerate | edit | restore */
    val source: String,
    val changeSummary: String = "",
)

/**
 * R-083 (as iOS): the server's job id, kept from the moment the server accepts the job until its result lands. A call
 * the app was closed during keeps it, and opening the draft (or its quote) collects the result.
 */
@Serializable
data class PendingJob(
    val jobId: String,
    /** draft | append | regenerate | quote */
    val kind: String,
    val startedAt: String,
    /** draft and append: the notes the brief will reflect. */
    val inputsUsed: List<String> = emptyList(),
    /** regenerate: the section rewritten. */
    val sectionId: String? = null,
    /** quote: the brief version priced. */
    val baseVersion: Int? = null,
) {
    companion object {
        const val DRAFT = "draft"
        const val APPEND = "append"
        const val REGENERATE = "regenerate"
        const val QUOTE = "quote"
        /** The job's own 5 minutes plus the server's 15 to collect its result. */
        const val WINDOW_MS = 20 * 60 * 1000L
    }
}

/** Screening refused this brief; it is frozen and can only be deleted. */
@Serializable
data class Refusal(val reason: String, val at: String)

/** R-094 P2-6: what a submit sent, kept until its answer is seen. (Resubmit is gone since contract v0.10.0.) */
@Serializable
data class SentSubmit(
    val quoteToken: String,
    /** The brief version the body carries. */
    val version: Int,
    val submit: com.raviga.app.data.api.SubmitProjectRequest? = null,
) {
    companion object {
        /**
         * The server answered and didn't take it. Then the body may change (another name, a new quote). Anything else
         * (offline, a 5xx, a lost answer) keeps it for the retry.
         *
         * Second review 2026-11-03 (Android): only an answer the server gives after its used-token check (contract 11,
         * check 1a) proves this token made no project. Consent, a paused or restricted account, 401, 426 and 429 are
         * answered before that check, so they used to let the hold go even when the first send had charged; an
         * expired quote or an edit then offered "Get a new quote", a new token, and a second project and charge.
         */
        fun refusedForGood(e: com.raviga.app.data.api.ApiException): Boolean =
            e.httpStatus in 400..499 && e.code in AFTER_USED_TOKEN_CHECK &&
                !(e.code == com.raviga.app.data.api.ApiException.QUOTE_STALE && e.detailString("projectId") != null)

        /** Submit and resubmit refusals that come after check 1a (quote_stale naming a project is check 1a itself). */
        private val AFTER_USED_TOKEN_CHECK = setOf(
            com.raviga.app.data.api.ApiException.INVALID_ARGUMENT,
            com.raviga.app.data.api.ApiException.QUOTE_STALE,
            com.raviga.app.data.api.ApiException.CONTENT_REJECTED,
            com.raviga.app.data.api.ApiException.INSUFFICIENT_CREDITS,
            com.raviga.app.data.api.ApiException.INVALID_STATE,
        )
    }
}
