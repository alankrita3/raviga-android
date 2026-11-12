package com.raviga.app.ui

import com.raviga.app.ui.components.PRIVACY_PROMISE
import com.raviga.app.ui.onboarding.ConsentCopy
import com.raviga.app.ui.quote.LOCK_LINE
import com.raviga.app.ui.status.DeliveryCopy
import com.raviga.app.ui.status.StatusCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-121 (her rule 2026-11-12, both apps; iOS docs/walkthrough-round2-2026-11-11.md, "No keep or delete words"): the app
 * says nothing about whether Raviga keeps or deletes the brief. Retention lives in the Privacy Policy and Terms; Delete
 * my data keeps its approved words (the client's own action), and the phone-only lines stay.
 */
class NoKeepOrDeleteWordsTest {

    @Test fun theLinesThatReplacedThem() {
        assertEquals("We use your brief only to build your project. We never sell it or use it to train AI.", PRIVACY_PROMISE)
        assertEquals("Private. Only our team sees your brief.", LOCK_LINE)
        assertEquals("Nothing was charged.", StatusCopy.CANCEL_CONFIRM)
        assertEquals("This brief is no longer available.", StatusCopy.BRIEF_GONE)
        assertEquals("Next, we publish it to your App Store and Google Play accounts.", DeliveryCopy.APPROVE_BODY)
        assertEquals("Anthropic's Claude, under its business terms. Never used to train AI.", ConsentCopy.AI_LINES.toMap()["Who processes it."])
    }

    @Test fun noLineSaysWeKeepOrDeleteTheBrief() {
        val lines = listOf(PRIVACY_PROMISE, LOCK_LINE, StatusCopy.CANCEL_CONFIRM, StatusCopy.BRIEF_GONE, StatusCopy.EXPORT_DRAFTS_CAPTION, DeliveryCopy.APPROVE_BODY) +
            ConsentCopy.AI_LINES.map { it.first + " " + it.second } +
            listOf(true to true, true to false, false to true).map { (a, p) -> DeliveryCopy.approveAgainBody(a, p) }
        lines.forEach { line -> FORBIDDEN.forEach { word -> assertTrue("\"$word\" in \"$line\"", !line.contains(word, ignoreCase = true)) } }
    }

    /** A sweep of the app's own strings, so a new screen can't bring the words back (comments are left out). */
    @Test fun noStringInTheAppSaysIt() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        val found = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                .flatMap { line -> literal.findAll(line).map { it.value } }
                .filter { s -> FORBIDDEN.any { s.contains(it, ignoreCase = true) } }
                .map { "${file.name}: $it" }
        }.toList()
        assertEquals(emptyList<String>(), found)
    }

    private companion object {
        val FORBIDDEN = listOf(
            "we keep", "we delete", "we deleted", "keep your brief", "keeps your brief", "brief is deleted", "deleted this brief",
            "delete it from our servers", "don't keep", "not stored by us", "while we hold", "deleted within", "never on our servers",
            "servers don't keep", "deleted its copy", "retain",
        )
    }
}
