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
) {
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
