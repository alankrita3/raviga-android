package com.raviga.app.data.drafts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Drafts on this phone: one sealed JSON file each under filesDir/drafts,
 * excluded from backup. Everything is held in memory once loaded (a client
 * has a handful of drafts), and every change is written through atomically.
 */
class DraftStore(
    private val dir: File,
    private val json: Json,
    private val sealer: Sealer,
) {
    private val _drafts = MutableStateFlow<Map<String, LocalDraft>>(emptyMap())
    val drafts: StateFlow<Map<String, LocalDraft>> = _drafts.asStateFlow()

    private val lock = Mutex()
    @Volatile private var loaded = false

    /**
     * R-094 P2-4 (iOS review 2): true from a failed save until the next one succeeds. Home, the brief, its sections
     * and Capture say so the whole time, so the line can't vanish while saves still fail.
     */
    private val _saveFailing = MutableStateFlow(false)
    val saveFailing: StateFlow<Boolean> = _saveFailing.asStateFlow()

    /** Files that could not be opened (Keystore key lost, e.g. after a factory reset of credentials). */
    @Volatile var unreadable: Int = 0
        private set

    /**
     * Reads every draft file not already in memory. Safe to call often: a file that
     * can't be opened (a Keystore hiccup) is left exactly as it is and tried again on
     * the next call; it is never written over, because only drafts that were read are
     * ever written, each to its own file.
     */
    suspend fun load() = lock.withLock {
        if (loaded && unreadable == 0) return@withLock
        val known = _drafts.value
        val (found, bad) = withContext(Dispatchers.IO) {
            dir.mkdirs()
            var bad = 0
            val map = dir.listFiles { f -> f.name.endsWith(EXT) }.orEmpty()
                .filter { f -> f.name.removeSuffix(EXT) !in known }
                .mapNotNull { f ->
                    runCatching { json.decodeFromString(LocalDraft.serializer(), String(sealer.open(f.readBytes()), Charsets.UTF_8)) }
                        .onFailure { bad++ }
                        .getOrNull()
                }.associateBy { it.id }
            map to bad
        }
        unreadable = bad
        if (found.isNotEmpty()) _drafts.update { it + found }
        loaded = true
    }

    fun get(id: String): LocalDraft? = _drafts.value[id]

    fun bySubmitted(serverProjectId: String): LocalDraft? =
        _drafts.value.values.firstOrNull { it.serverProjectId == serverProjectId }

    /** Applies [change] to the draft and writes it; returns the result. */
    suspend fun update(id: String, change: (LocalDraft) -> LocalDraft): LocalDraft = lock.withLock {
        val before = _drafts.value[id] ?: throw NoSuchElementException("No draft $id")
        val after = change(before)
        write(after)
        _drafts.update { it + (after.id to after) }
        after
    }

    suspend fun create(draft: LocalDraft): LocalDraft = lock.withLock {
        write(draft)
        _drafts.update { it + (draft.id to draft) }
        draft
    }

    suspend fun delete(id: String) = lock.withLock {
        withContext(Dispatchers.IO) { File(dir, "$id$EXT").delete() }
        _drafts.update { it - id }
    }

    /** Delete my data: every draft on this phone goes. */
    suspend fun wipe() = lock.withLock {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
        _drafts.value = emptyMap()
        unreadable = 0
    }

    /** Plain JSON of every draft, for the data export. */
    fun exportJson(): Map<String, String> = _drafts.value.mapValues { (_, d) ->
        json.encodeToString(LocalDraft.serializer(), d)
    }

    /**
     * R-094 P2-13 (iOS review 2): drafts on this phone that this build couldn't read, for the data export, as they
     * are: the plain text when it opens ("<id>.json"), else the stored bytes ("<id>.sealed"). Nothing is left out.
     */
    suspend fun exportUnreadable(): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val known = _drafts.value
        dir.listFiles { f -> f.name.endsWith(EXT) }.orEmpty()
            .filter { f -> f.name.removeSuffix(EXT) !in known }
            .associate { f ->
                val id = f.name.removeSuffix(EXT)
                val raw = f.readBytes()
                runCatching { sealer.open(raw) }.fold(onSuccess = { "$id.json" to it }, onFailure = { "$id.sealed" to raw })
            }
    }

    private suspend fun write(draft: LocalDraft) {
        try {
            writeFile(draft)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _saveFailing.value = true
            throw e
        }
        _saveFailing.value = false
    }

    private suspend fun writeFile(draft: LocalDraft) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val bytes = sealer.seal(json.encodeToString(LocalDraft.serializer(), draft).toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "${draft.id}.${UUID.randomUUID()}.tmp")
        // R-092 #4: a full disk fails here; say so in words, not as "Something went wrong". The saved copy is untouched.
        try { tmp.writeBytes(bytes) } catch (e: java.io.IOException) { tmp.delete(); throw com.raviga.app.data.UserFacingException(SAVE_FAILED) }
        val target = File(dir, "${draft.id}$EXT")
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); throw com.raviga.app.data.UserFacingException(SAVE_FAILED) }
        }
    }

    companion object {
        private const val EXT = ".draft"
        /** A save that failed (a full disk), in iOS's words (bb1f7d3), said by Capture and the brief alike. */
        const val SAVE_FAILED = "Couldn't save your drafts on this phone. Free up some storage, then try again."
        fun newId(): String = "ld_" + UUID.randomUUID().toString().replace("-", "").take(20)
    }
}
