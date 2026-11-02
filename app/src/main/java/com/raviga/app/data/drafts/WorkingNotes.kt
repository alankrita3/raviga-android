package com.raviga.app.data.drafts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps what is being typed, spoken or read saved in the local draft as it changes (contract v0.6 section 8:
 * everything is saved on the phone as you go; nothing reaches the server before submit).
 *
 * Each way of describing in one visit to Capture has one note, its [slot] (speak, type, upload): a change
 * edits that note in place. The draft is made with the first words, so an empty visit leaves nothing on Home.
 * [forget] starts a new note in a slot (the next document); a slot's note goes when its text is cleared.
 */
class WorkingNotes(private val sink: Sink, draftId: String?) {

    /** What the notes are saved through: the draft repository in the app, a fake in tests. */
    interface Sink {
        suspend fun create(): String
        suspend fun add(draftId: String, kind: String, text: String, meta: Meta): String
        suspend fun edit(draftId: String, inputId: String, text: String)
        suspend fun remove(draftId: String, inputId: String)
    }

    data class Meta(
        val languageDetected: String? = null,
        val fileName: String? = null,
        val pageCount: Int? = null,
        val durationSec: Int? = null,
    )

    private val lock = Mutex()
    private val saved = mutableMapOf<Int, Pair<String, String>>()   // slot -> (input id, text as saved)

    @Volatile var draftId: String? = draftId
        private set

    /** The draft, made now if there isn't one yet. */
    suspend fun ensureDraft(): String = lock.withLock { draftLocked() }

    private suspend fun draftLocked(): String = draftId ?: sink.create().also { draftId = it }

    /** Saves [text] as the note in [slot]: added the first time, edited after, removed when it's cleared. */
    suspend fun save(slot: Int, kind: String, text: String, meta: Meta = Meta()) = lock.withLock {
        val clean = text.trim()
        val current = saved[slot]
        when {
            clean.isEmpty() -> current?.let { (inputId, _) ->
                draftId?.let { sink.remove(it, inputId) }
                saved.remove(slot)
            }
            current == null -> {
                val id = draftLocked()
                saved[slot] = sink.add(id, kind, clean, meta) to clean
            }
            current.second != clean -> {
                sink.edit(draftLocked(), current.first, clean)
                saved[slot] = current.first to clean
            }
        }
        Unit
    }

    /** The next save in [slot] starts a new note; the one saved so far stays in the draft. */
    suspend fun forget(slot: Int) = lock.withLock { saved.remove(slot); Unit }
}
