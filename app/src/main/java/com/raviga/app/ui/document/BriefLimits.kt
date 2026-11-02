package com.raviga.app.ui.document

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.SectionIds
import java.util.Locale

/**
 * Contract section 9 (QA H9): each section at most limits.textInputMaxChars, the whole brief at most
 * limits.documentMaxChars, checked before an AI call or a submit. The same words as iOS, numbers grouped.
 */
object BriefLimits {

    /** The contract's cap on a brief's title (section 9). */
    const val TITLE_MAX = 120

    /** Length as the server counts it: code points (an emoji is 1, not the 2 of String.length). R-092 #32. */
    fun length(text: String): Int = text.codePointCount(0, text.length)

    /** What to say when a title is over [TITLE_MAX], else null. */
    fun titleProblem(title: String): String? =
        if (length(title.trim()) > TITLE_MAX) "A title can be up to $TITLE_MAX characters. Shorten it, then save." else null

    /** What to say when [doc] is over a limit, else null. A limit of 0 (not in config) isn't checked. */
    fun problem(doc: Document, sectionMax: Int, documentMax: Int): String? {
        doc.sections.firstOrNull { sectionMax > 0 && length(it.body) > sectionMax }?.let { section ->
            val heading = section.heading.ifBlank { SectionIds.headings[section.id] ?: section.id }
            return "The $heading section is over the ${grouped(sectionMax)} character limit. Shorten it, then try again."
        }
        val total = length(doc.title) + doc.sections.sumOf { length(it.body) }
        if (documentMax > 0 && total > documentMax) {
            return "The brief is over the ${grouped(documentMax)} character limit. Shorten a section, then try again."
        }
        return null
    }

    /** 20000 -> "20,000", whatever the phone's language (the app's words are English). */
    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
