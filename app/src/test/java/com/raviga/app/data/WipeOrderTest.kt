package com.raviga.app.data

import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.Sealer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * R-098 P3-5 (iOS review 3): Delete everything's wipe goes through the same lock as every save, and a save asked for
 * before it is refused when its turn comes: nothing comes back after the wipe.
 */
class WipeOrderTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    /** Holds the first write until [go] opens, so the wipe and a new save queue up behind it. */
    private class GatedSealer(val go: CountDownLatch) : Sealer {
        @Volatile var first = true
        override fun seal(plain: ByteArray): ByteArray { if (first) { first = false; go.await(5, TimeUnit.SECONDS) }; return plain }
        override fun open(sealed: ByteArray): ByteArray = sealed
    }

    @Test fun aSaveQueuedBeforeTheWipeDoesNotBringADraftBack() = runBlocking {
        val dir = Files.createTempDirectory("drafts").toFile()
        val go = CountDownLatch(1)
        val store = DraftStore(dir, json, GatedSealer(go))
        store.load()
        val inFlight = async { runCatching { store.create(LocalDraft(id = "ld_a", createdAt = "a", updatedAt = "a")) } }
        delay(100)                                    // the first save holds the lock, mid-write
        val wipe = async { store.wipe() }
        delay(50)                                     // Delete everything is queued
        val late = async { runCatching { store.create(LocalDraft(id = "ld_b", createdAt = "a", updatedAt = "a")) } }
        delay(50)
        go.countDown()
        inFlight.await(); wipe.await()
        val lateResult = late.await()
        assertTrue("the save asked for before the wipe was refused", lateResult.exceptionOrNull() is DraftStore.Wiped)
        assertTrue("nothing on disk", dir.listFiles().orEmpty().isEmpty())
        assertTrue("nothing in memory", store.drafts.value.isEmpty())
    }
}
