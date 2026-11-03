package com.raviga.app.data

import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.Sealer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * R-094 P2-13 (iOS review 2): a draft this build can't read is in the data export as it is, and Delete everything
 * removes it with the rest (temporary files included).
 */
class DraftExportTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test fun anUnreadableDraftIsExportedAsItIsAndWipedWithTheRest() = runBlocking {
        val dir = Files.createTempDirectory("drafts").toFile()
        val newerShape = """{"id":"ld_new","sections":"a shape this build doesn't know"}""".toByteArray()
        File(dir, "ld_new.draft").writeBytes(newerShape)
        File(dir, "ld_a.0f1e.tmp").writeText("half a write")
        val store = DraftStore(dir, json, Sealer.None)
        store.load()
        store.create(LocalDraft(id = "ld_a", title = "Bakery app", createdAt = "a", updatedAt = "a"))
        assertEquals(1, store.unreadable)

        assertEquals(setOf("ld_a"), store.exportJson().keys)
        val unreadable = store.exportUnreadable()
        assertEquals(setOf("ld_new.json"), unreadable.keys)
        assertArrayEquals("verbatim", newerShape, unreadable.getValue("ld_new.json"))

        store.wipe()
        assertTrue("nothing left in the drafts folder", dir.listFiles().orEmpty().isEmpty())
    }
}
