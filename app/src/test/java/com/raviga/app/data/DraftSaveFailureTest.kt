package com.raviga.app.data

import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.ui.userLine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/** R-092 #4 (iOS review): a save that fails (a full disk) says so in iOS's words, and the saved copy stays as it was. */
class DraftSaveFailureTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test fun aFailedSaveSaysSoInWordsAndKeepsTheSavedCopy() = runBlocking {
        val dir = Files.createTempDirectory("drafts").toFile()
        val store = DraftStore(dir, json, Sealer.None)
        store.load()
        store.create(LocalDraft(id = "ld_a", title = "Saved", createdAt = "a", updatedAt = "a"))
        assumeTrue("needs a file system that honours read-only folders", dir.setWritable(false) && !dir.canWrite())
        try {
            store.update("ld_a") { it.copy(title = "Not saved") }
            fail("expected the save to fail")
        } catch (e: UserFacingException) {
            assertEquals("Couldn't save your drafts on this phone. Free up some storage, then try again.", e.message)
            assertEquals("shown as written", e.message, e.userLine())
        } finally {
            dir.setWritable(true)
        }
        assertEquals("Saved", store.get("ld_a")?.title)
    }
}
