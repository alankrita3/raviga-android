package com.raviga.app.ui.document

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.SectionIds
import java.util.Locale

/**
 * Contract section 9 (QA H9): each section at most limits.textInputMaxChars, the whole brief at most
 * limits.documentMaxChars, checked before an AI call or a submit. The same words as iOS, numbers grouped.
 */
object BriefLimits {

    /** What to say when [doc] is over a limit, else null. A limit of 0 (not in config) isn't checked. */
    fun problem(doc: Document, sectionMax: Int, documentMax: Int): String? {
        doc.sections.firstOrNull { sectionMax > 0 && it.body.length > sectionMax }?.let { section ->
            val heading = section.heading.ifBlank { SectionIds.headings[section.id] ?: section.id }
            return "The $heading section is over the ${grouped(sectionMax)} character limit. Shorten it, then try again."
        }
        val total = doc.title.length + doc.sections.sumOf { it.body.length }
        if (documentMax > 0 && total > documentMax) {
            return "The brief is over the ${grouped(documentMax)} character limit. Shorten a section, then try again."
        }
        return null
    }

    /** 20000 -> "20,000", whatever the phone's language (the app's words are English). */
    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
