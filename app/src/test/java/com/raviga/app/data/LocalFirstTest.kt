package com.raviga.app.data

import com.raviga.app.data.api.AiDocumentResult
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.FileTypes
import com.raviga.app.data.api.QuoteRequest
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.data.api.Section
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.data.files.PlainFormats
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Contract v0.6: drafts on the phone, stateless AI shapes, documents read on the phone. */
class LocalFirstTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

    @Test fun aiResultAndQuoteResultDecode() {
        val ai = json.decodeFromString(
            AiDocumentResult.serializer(),
            """{"document":{"title":"Bakery app","sections":[{"id":"summary","heading":"Summary","body":"Orders.","hint":"h"}],"changeSummary":"Written"},"notices":["We removed an API key from input 2."]}""",
        )
        assertEquals("Bakery app", ai.document.title)
        assertEquals(listOf("We removed an API key from input 2."), ai.notices)

        val q = json.decodeFromString(
            QuoteResult.serializer(),
            """{"quote":{"id":"qt_1","credits":48,"inr":48000,"bracketId":"standard","estimatedWorkingDays":30,"timeline":{"minWeeks":4,"maxWeeks":6},"complexity":{"score":5,"drivers":["payments"]},"breakdown":[{"area":"Apps","credits":30}],"assumptions":[],"createdAt":"x","expiresAt":"y","documentHash":"ab"},"quoteToken":"qtk_1"}""",
        )
        assertEquals("qtk_1", q.quoteToken)
        assertEquals(48, q.quote.credits)
    }

    @Test fun requestBriefCarriesOnlyTitleAndBodies() {
        val doc = Document(title = "T", sections = listOf(Section("summary", "Summary", "Body", hint = "Hint")))
        val encoded = json.encodeToString(QuoteRequest.serializer(), QuoteRequest(doc.body()))
        assertEquals("""{"document":{"title":"T","sections":[{"id":"summary","body":"Body"}]}}""", encoded)
    }

    @Test fun draftStoreRoundTripsAndForgets() = runTest {
        val dir = Files.createTempDirectory("drafts").toFile()
        val store = DraftStore(dir, json, Sealer.None)
        store.load()
        val draft = LocalDraft(id = DraftStore.newId(), createdAt = "a", updatedAt = "a", inputs = listOf(LocalInput("in_1", "text", "hello", createdAt = "a")))
        store.create(draft)
        store.update(draft.id) { it.copy(title = "Named") }

        val reopened = DraftStore(dir, json, Sealer.None).apply { load() }
        assertEquals("Named", reopened.get(draft.id)?.title)
        assertEquals("hello", reopened.get(draft.id)?.inputs?.single()?.text)
        assertTrue(reopened.exportJson().getValue(draft.id).contains("\"hello\""))

        reopened.wipe()
        assertNull(DraftStore(dir, json, Sealer.None).apply { load() }.get(draft.id))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun unreadableDraftsAreCountedNotFatal() = runTest {
        val dir = Files.createTempDirectory("drafts").toFile()
        java.io.File(dir, "ld_broken.draft").writeText("not json")
        val store = DraftStore(dir, json, Sealer.None).apply { load() }
        assertEquals(1, store.unreadable)
        assertTrue(store.drafts.value.isEmpty())
    }

    @Test fun anUnreadableDraftIsNeverOverwrittenAndIsPickedUpLater() = runTest {
        val dir = Files.createTempDirectory("drafts").toFile()
        // A sealer that fails until "unlocked", like a Keystore that isn't ready yet.
        var ready = false
        val flaky = object : Sealer {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray): ByteArray = if (ready) sealed else throw IllegalStateException("locked")
        }
        val existing = LocalDraft(id = "ld_existing", title = "Keep me", createdAt = "a", updatedAt = "a")
        val file = java.io.File(dir, "ld_existing.draft")
        file.writeText(json.encodeToString(LocalDraft.serializer(), existing))
        val before = file.readBytes()

        val store = DraftStore(dir, json, flaky)
        store.load()
        assertEquals(1, store.unreadable)
        // Working on other drafts meanwhile must not touch the one we couldn't read.
        store.create(LocalDraft(id = "ld_new", createdAt = "b", updatedAt = "b"))
        assertTrue(file.readBytes().contentEquals(before))

        ready = true
        store.load()
        assertEquals(0, store.unreadable)
        assertEquals("Keep me", store.get("ld_existing")?.title)
        assertEquals(2, store.drafts.value.size)
    }

    @Test fun quoteIsCurrentOnlyForTheQuotedVersion() {
        val v1 = com.raviga.app.data.drafts.LocalVersion(1, Document(version = 1), "a", "draft")
        val v2 = v1.copy(version = 2)
        val quote = com.raviga.app.data.api.Quote(id = "qt", credits = 10)
        val quoted = LocalDraft(id = "ld_1", createdAt = "a", updatedAt = "a", versions = listOf(v1), quote = quote, quoteToken = "t", quotedVersion = 1)
        assertTrue(quoted.quoteIsCurrent)
        assertFalse(quoted.copy(versions = listOf(v1, v2)).quoteIsCurrent)
    }

    @Test fun plainFormatsReadWordRtfAndText() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>Bakery app</w:t></w:r></w:p><w:p><w:r><w:t>Pay with UPI &amp; card</w:t><w:br/><w:t>Pickup slots</w:t></w:r></w:p>""" +
            """<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Branch</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Pune</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        val docx = PlainFormats.tidy(PlainFormats.docxXml(xml))
        assertTrue(docx.startsWith("Bakery app\nPay with UPI & card\nPickup slots"))
        assertTrue(docx.contains("Branch"))
        assertTrue(docx.contains("Pune"))

        // \uN is decimal in RTF: 2344 and 2350 are na and ma in Devanagari.
        val u = "\\u"  // RTF unicode control word
        val rtf = """{\rtf1\ansi{\fonttbl{\f0 Helvetica;}}{\colortbl;\red0\green0\blue0;}\f0 Caf\'e9 ordering app\par Hindi: ${u}2344?${u}2350? menu\par{\*\generator Word;}Done}"""
        assertEquals("Café ordering app\nHindi: नम menu\nDone", PlainFormats.tidy(PlainFormats.rtf(rtf)))

        assertEquals("héllo", PlainFormats.decode("héllo".toByteArray(Charsets.UTF_8)))
        assertEquals("héllo", PlainFormats.decode("héllo".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("a\n\nb", PlainFormats.tidy("a\r\n\r\n\r\n\r\nb  "))
    }

    @Test fun fileTypesComeFromTheProviderOrTheExtension() {
        val accepted = FileTypes.DEFAULT
        assertEquals(FileTypes.PDF, FileTypes.resolve("brief.pdf", "application/pdf", accepted))
        assertEquals(FileTypes.MD, FileTypes.resolve("notes.md", "application/octet-stream", accepted))
        assertEquals(FileTypes.RTF, FileTypes.resolve("spec.rtf", "text/rtf", accepted))
        assertEquals(FileTypes.DOCX, FileTypes.resolve("Spec.DOCX", null, accepted))
        assertNull(FileTypes.resolve("old.doc", "application/msword", accepted))
    }

    /** "N notes not in the brief yet" counts the notes the brief doesn't reflect, and only those. */
    @Test fun notesNotInTheBriefAreTheNewOnes() {
        val inputs = listOf("in_1", "in_2", "in_3").map { LocalInput(it, "text", it, createdAt = "a") }
        val draft = LocalDraft(id = "ld_1", createdAt = "a", updatedAt = "a", inputs = inputs, inputsInBrief = listOf("in_1", "in_2"))
        assertEquals(listOf("in_3"), draft.newInputs.map { it.id })
        assertTrue(draft.copy(inputsInBrief = inputs.map { it.id }).newInputs.isEmpty())
    }
}
