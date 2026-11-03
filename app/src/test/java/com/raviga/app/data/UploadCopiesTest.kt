package com.raviga.app.data

import com.raviga.app.data.files.FileImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Review 2026-11-03 (Android): a document copied for reading stayed in cache/uploads when the app was ended mid-read
 * (OnePlus: a 7.4 MB scan kept after `am kill` and a relaunch), and "Delete my data" left it too. The copies are
 * cleared at the next start (only those from before it) and by deletion (all).
 */
class UploadCopiesTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun aCopyFromBeforeThisStartIsDeleted() {
        val dir = tmp.newFolder("uploads")
        val old = dir.resolve("a").apply { writeText("scan"); setLastModified(1_000) }
        assertEquals(1, FileImporter.clearCopies(dir, before = 2_000))
        assertFalse(old.exists())
    }

    @Test fun aCopyBeingMadeNowIsKept() {
        val dir = tmp.newFolder("uploads")
        val fresh = dir.resolve("b").apply { writeText("picked just now"); setLastModified(5_000) }
        assertEquals(0, FileImporter.clearCopies(dir, before = 2_000))
        assertTrue(fresh.exists())
    }

    @Test fun deletionClearsEveryCopy() {
        val dir = tmp.newFolder("uploads")
        dir.resolve("a").writeText("one")
        dir.resolve("b").writeText("two")
        assertEquals(2, FileImporter.clearCopies(dir, before = Long.MAX_VALUE))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun noFolderYetIsFine() {
        assertEquals(0, FileImporter.clearCopies(tmp.root.resolve("missing"), before = Long.MAX_VALUE))
    }
}
