package com.raviga.app.ui.capture

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import android.os.SystemClock
import com.raviga.app.ui.document.BriefLimits
import android.os.Build
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.audio.DictationEngine
import com.raviga.app.data.files.FileImporter
import com.raviga.app.data.files.TextExtractor
import com.raviga.app.data.screening.SensitiveScan
import com.raviga.app.di.AppContainer
import com.raviga.app.ui.userLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.data.drafts.WorkingNotes
import kotlinx.coroutines.launch

/**
 * Capture → review → Drafting, local-first (contract v0.6). Speech is
 * recognised on the phone, documents are read on the phone, and the notes are
 * saved in the local draft as they change ([WorkingNotes]): typing, each
 * utterance, a document once it's read, edits on review. Leaving at any point
 * keeps them; the draft shows on Home. Only their text goes to the AI to write
 * the brief.
 *
 * R-108 (her decision, 2026-11-04): each part is its own note. "Add more" on
 * every review saves the part, then Speak, Type or Upload a document adds the
 * next one at the end; switching tabs ends the part on screen. A file and a
 * recording never merge into one note, and the order is the order made.
 */
class CaptureViewModel(
    private val container: AppContainer,
    initialDraftId: String,
    private val mode: String,
    initialTab: String = DescribeWith.SPEAK,
    /** Review 2026-11-03 (Android): where this visit was, kept across the app being ended in the background. */
    private val saved: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    enum class Phase { CAPTURE, TRANSCRIPT, CHOOSE, DRAFTING }

    /** What was read from a document on this phone. */
    data class FileRead(
        val fileName: String,
        val pageCount: Int?,
        val truncated: Boolean,
        val notice: String?,
    )

    sealed interface Speech {
        data object Checking : Speech
        data class Ready(val engine: DictationEngine.Availability.Ready) : Speech
        data class NotYet(val message: String) : Speech
        data class Unavailable(val message: String) : Speech
    }

    data class State(
        /** R-099: the line above "Looks right, write the brief" (K); null while it can't be worked out yet. */
        val briefLine: String? = null,
        /** R-099: the server said 402 for a new brief; the screen offers credits (L). */
        val needCredits: Boolean = false,
        val phase: Phase = Phase.CAPTURE,
        val tab: Int = 0,                       // 0 speak, 1 type, 2 upload
        val speech: Speech = Speech.Checking,
        /** English only; the accent steers the on-device model (from the phone's region at first). */
        val accent: DictationEngine.Accent = DictationEngine.Accent.preferred(),
        val listening: Boolean = false,
        val committed: List<String> = emptyList(),
        val partial: String = "",
        val typed: String = "",
        val level: Float = 0f,
        val elapsedSec: Int = 0,
        val transcript: String = "",
        /** Reading a document on the phone. */
        val reading: Boolean = false,
        /** R-094 P2-16: the picked file is still being copied to the phone (a cloud copy downloading). */
        val fetching: Boolean = false,
        /** R-094 P2-4: a save failed and none has succeeded since (the drafts store's saveFailing). */
        val saveFailing: Boolean = false,
        val progress: Float? = null,
        val progressMessage: String? = null,
        val error: String? = null,
        val needsConsent: Boolean = false,
        val done: String? = null,               // draft id to open
        val file: FileRead? = null,
        /** Title of the "this looks like it includes…" sheet, while it is open. */
        val sensitive: String? = null,
        /** Bumped to ask the review editor for focus (after "Edit" on the sensitive sheet). */
        val focusEditor: Int = 0,
        /** Utterances that belong to parts already finished (R-108); the Speak tab shows and saves only the ones after. */
        val consumed: Int = 0,
        /** R-108: the draft's parts in order (with a brief: the ones not in it yet), shown on the review and choices. */
        val parts: List<Part> = emptyList(),
        /** The part on screen now (this tab's note), left out of the review's list above it. */
        val currentPartId: String? = null,
        /** Notes saved so far in this visit (documents saved before adding more), shown on the choices. */
        val savedNotes: Int = 0,
        val lastSavedName: String? = null,
        val saving: Boolean = false,
    ) {
        val hasSpeech get() = committed.size > consumed || partial.isNotBlank()
    }

    /**
     * Review 2026-11-03 (Android): Android ended the app in the background on this screen and brought it back. The
     * draft and the notes this visit was making come back with it (their text from the draft), so the screen shows
     * them and the next change edits the same note; it used to come back empty and start a second draft.
     */
    private val restored: Pair<String, Map<Int, LocalInput>>? = saved.get<android.os.Bundle>(SAVED_STATE)?.let { b ->
        val draft = b.getString(SAVED_DRAFT)?.let { container.drafts.get(it) } ?: return@let null
        val slots = b.getIntArray(SAVED_SLOTS) ?: IntArray(0)
        val inputs = b.getStringArray(SAVED_INPUTS) ?: emptyArray()
        draft.id to slots.indices.mapNotNull { i ->
            draft.inputs.firstOrNull { it.id == inputs.getOrNull(i) }?.let { slots[i] to it }
        }.toMap()
    }

    private val _state = MutableStateFlow(
        State(
            tab = when (initialTab) {
                DescribeWith.TYPE -> TAB_TYPE
                DescribeWith.UPLOAD -> TAB_UPLOAD
                else -> TAB_SPEAK
            },
        ).let { fresh ->
            val b = saved.get<android.os.Bundle>(SAVED_STATE)
            if (restored == null || b == null) fresh
            else restoredState(fresh, b.getString(SAVED_PHASE), b.getInt(SAVED_TAB, fresh.tab), restored.second, b.getInt(SAVED_NOTES), b.getString(SAVED_LAST_NAME))
        },
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun dismissNeedCredits() = _state.update { it.copy(needCredits = false) }

    val isAppend: Boolean get() = mode == "append"

    private val notes = WorkingNotes(
        object : WorkingNotes.Sink {
            override suspend fun create() = container.drafts.create().id
            override suspend fun add(draftId: String, kind: String, text: String, meta: WorkingNotes.Meta) =
                container.drafts.addInput(draftId, kind, text, meta.languageDetected, meta.fileName, meta.pageCount, meta.durationSec).id
            override suspend fun edit(draftId: String, inputId: String, text: String) { container.drafts.editInput(draftId, inputId, text) }
            override suspend fun remove(draftId: String, inputId: String) { container.drafts.removeInput(draftId, inputId) }
        },
        restored?.first ?: initialDraftId.takeIf { it != "new" },
        restored?.second.orEmpty().mapValues { (_, input) -> input.id to input.text },
    )

    /** Read by Android when it saves the screen's state (on the main thread, after ON_STOP's flush). */
    private fun snapshot(): android.os.Bundle {
        val s = _state.value
        val slots = notes.slots().toList()
        return android.os.Bundle().apply {
            putString(SAVED_DRAFT, notes.draftId)
            putIntArray(SAVED_SLOTS, slots.map { it.first }.toIntArray())
            putStringArray(SAVED_INPUTS, slots.map { it.second }.toTypedArray())
            putString(SAVED_PHASE, s.phase.name)
            putInt(SAVED_TAB, s.tab)
            putInt(SAVED_NOTES, s.savedNotes)
            putString(SAVED_LAST_NAME, s.lastSavedName)
        }
    }
    private val draftId: String? get() = notes.draftId
    private var saveJob: Job? = null
    /** When the oldest change not yet saved was made (uptime ms), 0 when everything is saved. */
    @Volatile private var firstUnsavedAt = 0L
    private var listenJob: Job? = null
    private var timerJob: Job? = null
    private var pendingAfterConsent: (() -> Unit)? = null
    /** Text the client chose to keep despite the sensitive-data warning. */
    private var acknowledgedText: String? = null
    /** The sensitive-data sheet is open for "Add more": carry on saving, not writing. */
    private var addingMore = false

    init {
        viewModelScope.launch { container.draftStore.saveFailing.collect { f -> _state.update { it.copy(saveFailing = f) } } }
    }

    init {
        // After `notes` (draftId reads it; the first combine runs at once).
        // R-099 (K, as iOS): free only when the server's count isn't 0 and this phone has no other draft with a brief and no
        // submitted project (/me is often read before the first brief, so it can still say 1 just after it).
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                container.session.me, container.credits.credits, container.session.config, container.drafts.drafts, container.projects.summaries,
            ) { me, credits, config, drafts, projects ->
                val wroteBefore = drafts.any { (id, d) -> id != draftId && d.document != null } || projects.isNotEmpty() ||
                    credits?.ledger?.any { it.type == "brief" } == true
                val free = com.raviga.app.ui.credits.BriefPricing.freeLeft(credits?.freeBriefsLeft ?: me?.credits?.freeBriefsLeft, wroteBefore)
                // No line when this draft already has a brief (a rewrite is free) or on a server without brief pricing.
                if (draftId?.let { drafts[it]?.document } != null) null
                else com.raviga.app.ui.credits.BriefPricing.line(free, config.credits, credits?.balance ?: me?.credits?.balance ?: 0.0)
            }.collect { line -> _state.update { it.copy(briefLine = line) } }
        }
        // R-108: the parts of this description, in order, for the review and the choices.
        viewModelScope.launch {
            container.drafts.drafts.collect { drafts ->
                val draft = draftId?.let { drafts[it] }
                val parts = (if (isAppend) draft?.newInputs else draft?.inputs).orEmpty().map { it.toPart() }
                _state.update { it.copy(parts = parts, currentPartId = notes.slots()[it.tab]) }
            }
        }
    }

    /** The part on screen is this tab's note; after a save (which may have just made it) the screen leaves it out above. */
    private fun syncCurrentPart() = _state.update { it.copy(currentPartId = notes.slots()[it.tab]) }

    init {
        checkSpeech()
        saved.setSavedStateProvider(SAVED_STATE) { snapshot() }
    }

    fun checkSpeech() {
        val accent = _state.value.accent
        viewModelScope.launch {
            val speech = when (val a = container.dictation.availability(accent)) {
                is DictationEngine.Availability.Ready -> Speech.Ready(a)
                DictationEngine.Availability.Downloading ->
                    Speech.NotYet("Getting English speech ready on this phone so it works offline. Try again in a minute.")
                is DictationEngine.Availability.Unavailable -> Speech.Unavailable(a.message)
            }
            // A newer pick of accent wins over a slower check for the old one.
            _state.update { if (it.accent == accent) it.copy(speech = speech) else it }
        }
    }

    /** Speak in another English accent; it only steers the on-device model. */
    fun selectAccent(accent: DictationEngine.Accent) {
        if (_state.value.listening || accent == _state.value.accent) return
        _state.update { it.copy(accent = accent, speech = Speech.Checking) }
        checkSpeech()
    }

    fun selectTab(index: Int) {
        if (_state.value.listening) stopListening()
        // Second review 2026-11-03 (Android): the tab being left is saved now. The autosave runs up to half a second
        // later and saves whatever tab is on screen then; Upload has no text, so the last words typed or said before
        // the switch were never saved (Back from Upload, a document's review, or the app ended behind the picker
        // lost them). In the app's scope, as leaving is, so an immediate Back doesn't cancel it.
        // R-108: leaving a tab ends its part, so coming back starts a new part at the end (the order is kept).
        if (index != _state.value.tab) endPart()
        _state.update { it.copy(tab = index, error = null, currentPartId = notes.slots()[index]) }
    }

    /**
     * R-108: the part on screen is finished (a tab switch, or back to the choices): saved now, in the app's scope so an
     * immediate Back doesn't cancel it, and its tab starts empty. Emptied text saves too: its note goes.
     */
    private fun endPart() {
        val leaving = _state.value
        val text = liveText(leaving) ?: return
        saveJob?.cancel()
        container.appScope.launch(Dispatchers.IO) {
            runCatching { notes.close(leaving.tab, kindOf(leaving.tab), text, metaOf(leaving)) }
            syncCurrentPart()
        }
        _state.update { leftTab(it) }
    }

    fun setTyped(text: String) { _state.update { it.copy(typed = text) }; scheduleSave() }
    fun setTranscript(text: String) { _state.update { it.copy(transcript = text) }; scheduleSave() }

    /** Emulators have no microphone and demos want a quick start: offer a canned description. */
    /** Debug builds only: a sample description on the demo backend or an emulator. */
    val offersSample: Boolean get() = com.raviga.app.BuildConfig.DEBUG && (container.isDemo || isEmulator())

    fun useSample() { _state.update { it.copy(tab = TAB_TYPE, typed = SAMPLE_DESCRIPTION, error = null) }; scheduleSave() }

    private fun isEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return fp.contains("generic") || fp.contains("emulator") || product.contains("sdk") || product.contains("emulator") ||
            android.os.Build.HARDWARE.lowercase().let { it.contains("goldfish") || it.contains("ranchu") }
    }

    private fun needsConsent(): Boolean = container.session.needsAiConsent(container.session.me.value)

    /** The screen has navigated to the consent flow; do not ask again on re-entry. */
    fun consentRequested() = _state.update { it.copy(needsConsent = false) }

    fun consentGranted() {
        _state.update { it.copy(needsConsent = false) }
        pendingAfterConsent?.invoke()
        pendingAfterConsent = null
    }

    // ----- Speak (on the device only) -----

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening()
    }

    private fun startListening() {
        val speech = _state.value.speech
        if (speech !is Speech.Ready) {
            if (speech is Speech.NotYet) checkSpeech()
            return
        }
        // Ready only ever comes on Android 12+ (on-device recogniser); this says so to the compiler too.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        _state.update { it.copy(listening = true, error = null) }
        startTimer()
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            container.dictation.listen(speech.engine).collect { event ->
                when (event) {
                    is DictationEngine.Event.Partial -> _state.update { heard(it, event) }
                    is DictationEngine.Event.Utterance -> {
                        _state.update { heard(it, event) }
                        scheduleSave()
                    }
                    is DictationEngine.Event.Level -> _state.update { it.copy(level = DictationEngine.levelFromRms(event.rmsDb)) }
                    is DictationEngine.Event.Error -> if (event.fatal) {
                        stopListening()
                        _state.update { it.copy(error = DictationEngine.errorLine(event.code)) }
                    }
                    DictationEngine.Event.Ready -> Unit
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel(); listenJob = null
        stopTimer()
        _state.update { s ->
            val committed = if (s.partial.isNotBlank()) s.committed + s.partial else s.committed
            s.copy(listening = false, level = 0f, committed = committed, partial = "")
        }
        scheduleSave()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel(); timerJob = null }

    // ----- Upload (read on the device) -----

    private var readJob: Job? = null

    /** R-092 #36 (iOS review): a long scanned document can be stopped; nothing of it is kept. */
    fun cancelReading() {
        if (!_state.value.reading) return
        readJob?.cancel()
        readJob = null
        _state.update { it.copy(phase = Phase.CAPTURE, tab = TAB_UPLOAD, reading = false, fetching = false, progress = null, progressMessage = null) }
    }

    fun pickedFile(uri: Uri) {
        val limits = container.session.config.value.limits
        // R-094 P2-16 (iOS review 2): getting the file (a slow cloud download) shows "Getting <name>" with Stop.
        _state.update {
            it.copy(
                error = null, phase = Phase.TRANSCRIPT, tab = TAB_UPLOAD, reading = true, fetching = true,
                progress = null, progressMessage = "Getting the file",
            )
        }
        readJob = viewModelScope.launch {
            val picked = try {
                container.files.import(uri, limits.fileMaxBytes, limits.acceptedFileTypes) { name ->
                    _state.update { s -> if (s.fetching) s.copy(progressMessage = "Getting $name") else s }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e   // stopped from the screen; cancelReading() has set it back
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = Phase.CAPTURE, reading = false, fetching = false, progressMessage = null,
                        error = (e as? com.raviga.app.data.UserFacingException)?.message ?: "Couldn't open that file. Try again, or choose another copy.",
                    )
                }
                return@launch
            }
            // A new document is a new note, even if an earlier one was already saved.
            notes.forget(TAB_UPLOAD)
            acknowledgedText = null
            _state.update {
                it.copy(
                    phase = Phase.TRANSCRIPT, tab = TAB_UPLOAD, reading = true, fetching = false, transcript = "", file = null,
                    progress = null, progressMessage = "Reading ${picked.name}", error = null,
                )
            }
            try {
                val result = container.extractor.extract(
                    picked.file, picked.contentType,
                    maxChars = limits.textInputMaxChars, maxPages = limits.fileMaxPages,
                ) { step -> _state.update { it.copy(progressMessage = step) } }
                _state.update {
                    it.copy(
                        reading = false, progress = null, progressMessage = null,
                        transcript = result.text,
                        file = FileRead(picked.name, result.pageCount, result.truncated, result.notice),
                    )
                }
                // Read on the phone: saved in the draft straight away.
                persist()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e   // stopped from the screen; cancelReading() has set it back
            } catch (e: TextExtractor.Unreadable) {
                ensureActive()   // stopped meanwhile: no error to show
                _state.update { it.copy(phase = Phase.CAPTURE, reading = false, progressMessage = null, error = e.message) }
            } catch (e: Exception) {
                ensureActive()
                _state.update { it.copy(phase = Phase.CAPTURE, reading = false, progressMessage = null, error = "Couldn't read that file. Try another copy, or paste the text instead.") }
            } finally {
                // The file never leaves the phone, and doesn't stay on it either.
                picked.file.delete()
            }
        }
    }

    // ----- Done → review -----

    fun finishCapture() {
        if (_state.value.listening) stopListening()
        val s = _state.value
        when (s.tab) {
            TAB_UPLOAD -> return
            TAB_TYPE -> _state.update { it.copy(phase = Phase.TRANSCRIPT, transcript = s.typed.trim()) }
            else -> _state.update {
                // This take's words only: the utterances of finished parts stay with them.
                it.copy(phase = Phase.TRANSCRIPT, consumed = s.committed.size, transcript = spokenReview(it.transcript, it.committed, s.consumed))
            }
        }
        scheduleSave()
    }

    /**
     * R-108 (her decision, 2026-11-04): "Add more" on every review, whichever way this part was made. The part is
     * saved as its own note (the text as reviewed, edits included), then Speak, Type or Upload a document adds the
     * next part as a new note at the end, the same way twice included. Back from the review leaves, everything saved.
     */
    fun addMore() {
        val s = _state.value
        val text = s.transcript.trim()
        if (s.phase != Phase.TRANSCRIPT || s.reading || s.saving || text.isBlank()) return
        overLimit(text, container.session.config.value.limits.textInputMaxChars)?.let { line ->
            _state.update { it.copy(error = line) }
            return
        }
        addingMore = true
        if (flagged(text)) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                saveJob?.cancel()
                notes.close(s.tab, kindOf(s.tab), text, metaOf(s))
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.userLine()) }
                return@launch
            }
            acknowledgedText = null
            _state.update { afterAddMore(it, s.file?.fileName) }
            syncCurrentPart()
        }
    }

    /** The brief's title when adding to one, for the choices' heading; null for a new project. */
    fun appendTitle(): String? = if (isAppend) draftId?.let { container.drafts.get(it)?.displayTitle } ?: "" else null

    /** A way to add the next part, picked on the choices shown after a document was saved. */
    fun choose(tab: Int) = _state.update { it.copy(phase = Phase.CAPTURE, tab = tab, error = null) }

    /** Back to the choices from a new part that hasn't been reviewed yet. */
    fun backToChoices() {
        if (_state.value.listening) stopListening()
        endPart()
        _state.update { it.copy(phase = Phase.CHOOSE, error = null) }
    }

    /** "Write the brief now": from the notes already saved in this visit. */
    fun writeSavedNotes() {
        val id = draftId ?: return
        if (needsConsent()) {
            pendingAfterConsent = { writeSavedNotes() }
            _state.update { it.copy(needsConsent = true) }
            return
        }
        _state.update { it.copy(phase = Phase.DRAFTING, error = null, progress = null, progressMessage = "Reading your notes") }
        viewModelScope.launch {
            try {
                container.drafts.writeBrief(id) { st ->
                    _state.update { it.copy(progress = st.progress, progressMessage = st.message ?: it.progressMessage) }
                }
                _state.update { it.copy(done = id) }
            } catch (e: Exception) {
                onWriteFailed(id, e, back = Phase.CHOOSE)
            }
        }
    }

    // ----- Sensitive data (layer 0) -----

    fun removeSensitive() {
        _state.update { it.copy(transcript = SensitiveScan.redact(it.transcript.trim()), sensitive = null) }
        proceed()
    }

    fun keepSensitive() {
        acknowledgedText = _state.value.transcript.trim()
        _state.update { it.copy(sensitive = null) }
        proceed()
    }

    private fun proceed() = if (addingMore) addMore() else writeBrief()

    /** Opens the sensitive-data sheet if [text] has something the client hasn't decided on yet. */
    private fun flagged(text: String): Boolean {
        if (text == acknowledgedText) return false
        val findings = SensitiveScan.scan(text)
        if (findings.isEmpty()) return false
        _state.update { it.copy(sensitive = SensitiveScan.title(findings), error = null) }
        return true
    }

    fun editSensitive() = _state.update { it.copy(sensitive = null, focusEditor = it.focusEditor + 1) }

    // ----- Review → Drafting -----

    fun writeBrief() {
        val s = _state.value
        val text = s.transcript.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "Say or type something about the project first.") }
            return
        }
        // QA D4: limits.textInputMaxChars per note, the same words as iOS. The note stays saved as it is.
        overLimit(text, container.session.config.value.limits.textInputMaxChars)?.let { line ->
            _state.update { it.copy(error = line) }
            return
        }
        // Text goes to the AI from here on; ask once.
        if (needsConsent()) {
            pendingAfterConsent = { writeBrief() }
            _state.update { it.copy(needsConsent = true) }
            return
        }
        addingMore = false
        if (flagged(text)) return
        _state.update { it.copy(phase = Phase.DRAFTING, error = null, progress = null, progressMessage = "Reading your notes") }
        viewModelScope.launch {
            val drafts = container.drafts
            val id = try {
                notes.ensureDraft().also { saveNote(s, text) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.TRANSCRIPT, error = e.userLine(), progress = null, progressMessage = null) }
                return@launch
            }
            try {
                drafts.writeBrief(id) { st ->
                    _state.update { it.copy(progress = st.progress, progressMessage = st.message ?: it.progressMessage) }
                }
                _state.update { it.copy(done = id) }
                // R-099: the free brief may be used now, and a later one booked a twelfth of a credit.
                container.appScope.launch { runCatching { container.credits.refresh() }; runCatching { container.session.refreshMe() } }
            } catch (e: Exception) {
                onWriteFailed(id, e)
            }
        }
    }

    /** Saves the reviewed text as this way's note on the phone (added once, then edited in place). */
    private suspend fun saveNote(s: State, text: String) {
        saveJob?.cancel()
        notes.save(s.tab, kindOf(s.tab), text, metaOf(s))
    }

    // ----- Autosave (contract v0.6 section 8: saved on the phone as you go) -----

    /** Saves what's on screen about half a second after the last change. */
    private fun scheduleSave() {
        saveJob?.cancel()
        val now = SystemClock.uptimeMillis()
        if (firstUnsavedAt == 0L) firstUnsavedAt = now
        val wait = saveDelay(now, firstUnsavedAt)
        // Off the main thread, so flush() can wait for a save in progress without blocking on it.
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(wait)
            persist()
        }
    }

    /**
     * Saves now and waits for it (at most [FLUSH_TIMEOUT_MS]): the app is going to the background, and a kill right
     * after (swiped away, QA G2) must find the words saved. The save runs on IO, never on the main thread it blocks.
     */
    fun flush() {
        saveJob?.cancel()
        val s = _state.value
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { persist(s) } }
    }

    private suspend fun persist(s: State = _state.value) {
        firstUnsavedAt = 0L
        val text = liveText(s) ?: return
        // A failed save must not stop the person typing; the next change or leaving tries again. A refusal (the
        // draft's note limit, QA F5) is shown, so nothing is dropped without a word.
        runCatching { notes.save(s.tab, kindOf(s.tab), text, metaOf(s)) }
            .onSuccess { syncCurrentPart() }
            .onFailure { e ->
                when {
                    (e as? ApiException)?.code == ApiException.INVALID_ARGUMENT -> _state.update { it.copy(error = e.userLine()) }
                    // R-092 #4 / R-094 P2-4: a failed save (disk full) isn't swallowed: the screen says so from the
                    // store's saveFailing until a save succeeds; the text stays on screen and the next change tries again.
                }
            }
    }

    /** One message per run of failed saves; a save that works resets it. */

    private fun kindOf(tab: Int): String = when (tab) {
        TAB_SPEAK -> "voice"
        TAB_UPLOAD -> "file"
        else -> "text"
    }

    private fun metaOf(s: State): WorkingNotes.Meta {
        val file = s.file?.takeIf { s.tab == TAB_UPLOAD }
        val voice = s.tab == TAB_SPEAK
        return WorkingNotes.Meta(
            languageDetected = (s.speech as? Speech.Ready)?.engine?.languageTag?.takeIf { voice },
            fileName = file?.fileName,
            pageCount = file?.pageCount,
            durationSec = s.elapsedSec.takeIf { voice && it > 0 },
        )
    }

    private suspend fun onWriteFailed(id: String, e: Exception, back: Phase = Phase.TRANSCRIPT) {
        val api = e as? ApiException
        // v0.11.3: a draft that failed on the server's side gave back its twelfth (or the free brief); show the new count.
        if (api?.code == ApiException.AI_UNAVAILABLE || api?.code == ApiException.INTERNAL) {
            container.appScope.launch { runCatching { container.credits.refresh() }; runCatching { container.session.refreshMe() } }
        }
        val legal = api?.detailStrings("missing")?.any { it == "terms" || it == "privacy" } == true
        when {
            // Terms out of date: the app shows them (ConsentInterceptor). AI consent is asked here.
            api?.code == ApiException.CONSENT_REQUIRED && !legal -> {
                pendingAfterConsent = { writeBrief() }
                _state.update { it.copy(phase = back, needsConsent = true, progress = null, progressMessage = null) }
            }
            // R-099 (L): a new brief needs a twelfth of a credit and the account is short; nothing was counted.
            api?.code == ApiException.INSUFFICIENT_CREDITS ->
                _state.update { it.copy(phase = back, needCredits = true, error = null, progress = null, progressMessage = null) }
            // A policy refusal froze the draft (DraftRepository); the brief screen explains it.
            api?.code == ApiException.CONTENT_REJECTED && api.detailString("kind") == "policy" ->
                _state.update { it.copy(done = id) }
            else -> {
                // A quality refusal: the note stays saved, and the fixed text edits it in place.
                _state.update { it.copy(phase = back, error = e.userLine(), progress = null, progressMessage = null) }
            }
        }
    }

    override fun onCleared() {
        listenJob?.cancel()
        timerJob?.cancel()
        saveJob?.cancel()
        // Leaving keeps what was said, typed or read (half an utterance included). A draft left with no note at
        // all (everything cleared) is just noise on Home.
        val s = _state.value
        val madeHere = createdThisVisit
        container.appScope.launch {
            persist(s)
            val id = draftId ?: return@launch
            if (shouldDropEmpty(madeHere, mode, container.drafts.get(id))) container.drafts.delete(id)
        }
    }

    /**
     * Review #4 (Android): only a draft this visit made (the route said "new") is dropped when it's left empty. "+" on
     * an existing draft with no notes (all deleted, or only named) used to delete it, name and all, on Back.
     */
    private val createdThisVisit: Boolean = initialDraftId == "new"

    companion object {
        /** Whether a draft left with no notes and no brief should go: only one this Capture visit made. */
        internal fun shouldDropEmpty(createdThisVisit: Boolean, mode: String, draft: com.raviga.app.data.drafts.LocalDraft?): Boolean =
            createdThisVisit && mode != "append" && draft != null && draft.inputs.isEmpty() && draft.versions.isEmpty()

        private const val SAVED_STATE = "capture_visit"
        private const val SAVED_DRAFT = "draft"
        private const val SAVED_SLOTS = "slots"
        private const val SAVED_INPUTS = "inputs"
        private const val SAVED_PHASE = "phase"
        private const val SAVED_TAB = "tab"
        private const val SAVED_NOTES = "saved_notes"
        private const val SAVED_LAST_NAME = "last_saved_name"

        /**
         * The screen as it was when Android ended the app, from the notes the visit had saved ([notes], by slot):
         * the typed text back in the editor, anything spoken or read back under review (recording had stopped), and
         * the choices after a saved document. Writing (the brief's job) comes back as the review it started from.
         */
        fun restoredState(
            fresh: State,
            phase: String?,
            tab: Int,
            notes: Map<Int, LocalInput>,
            savedNotes: Int,
            lastSavedName: String?,
        ): State {
            val was = Phase.entries.firstOrNull { it.name == phase } ?: Phase.CAPTURE
            val note = notes[tab]
            val base = fresh.copy(tab = tab, savedNotes = savedNotes, lastSavedName = lastSavedName)
            return when {
                was == Phase.CHOOSE || (note == null && was == Phase.DRAFTING && savedNotes > 0) -> base.copy(phase = Phase.CHOOSE)
                note == null -> base
                was == Phase.CAPTURE && tab == TAB_TYPE -> base.copy(typed = note.text)
                else -> base.copy(
                    phase = Phase.TRANSCRIPT,
                    transcript = note.text,
                    file = if (tab == TAB_UPLOAD) FileRead(note.fileName ?: "Document", note.pageCount, truncated = false, notice = null) else null,
                )
            }
        }

        const val SAVE_DELAY_MS = 500L
        /** Typing without a pause still saves at least this often (QA G2: a kill within 1 s of a keystroke). */
        const val SAVE_MAX_WAIT_MS = 1_000L
        const val FLUSH_TIMEOUT_MS = 2_000L

        /** Half a second after the last change, but never more than [SAVE_MAX_WAIT_MS] after the first unsaved one. */
        fun saveDelay(now: Long, firstUnsavedAt: Long): Long =
            minOf(SAVE_DELAY_MS, maxOf(0L, SAVE_MAX_WAIT_MS - (now - firstUnsavedAt)))

        /** R-075: a finished utterance goes after the ones before it; only the live partial is ever replaced. */
        fun heard(s: State, event: DictationEngine.Event): State = when (event) {
            is DictationEngine.Event.Partial -> s.copy(partial = event.text)
            is DictationEngine.Event.Utterance -> s.copy(committed = s.committed + event.text, partial = "")
            else -> s
        }

        /**
         * "Done" while speaking: this part's utterances ([consumed] belong to finished parts) go under review.
         */
        fun spokenReview(transcript: String, committed: List<String>, consumed: Int): String =
            joinText(transcript, committed.drop(consumed).joinToString(" "))

        /** Over the per-note limit: what to say (iOS's words), else null. */
        fun overLimit(text: String, maxChars: Int): String? =
            if (maxChars > 0 && com.raviga.app.ui.document.BriefLimits.length(text) > maxChars) {
                "That's over the ${BriefLimits.grouped(maxChars)} character limit. Shorten it, or save and add the rest as another note."
            } else {
                null
            }

        private fun joinText(a: String, b: String): String = listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

        /** What switching from [s]'s tab to [toTab] must save first (second review): the left tab's live text, if any. */
        internal fun leavingTabText(s: State, toTab: Int): String? =
            if (toTab == s.tab) null else liveText(s)

        /** R-108: the tab just left starts empty next time; what it held is a finished part now. */
        internal fun leftTab(s: State): State = when (s.tab) {
            TAB_TYPE -> s.copy(typed = "")
            TAB_SPEAK -> s.copy(consumed = s.committed.size, partial = "", elapsedSec = 0)
            else -> s
        }

        /** R-108: after "Add more" saved the part under review: the choices, with nothing carried into the next part. */
        internal fun afterAddMore(s: State, fileName: String?): State = s.copy(
            phase = Phase.CHOOSE, saving = false, error = null, transcript = "", file = null, typed = "",
            consumed = s.committed.size, partial = "", elapsedSec = 0,
            savedNotes = s.savedNotes + 1, lastSavedName = fileName ?: s.lastSavedName,
        )

        /**
         * The text of the note being made, as it stands on screen: the typed text, the speech so far (joined to
         * what's under review when recording more), or the text under review. Null when there's nothing to save
         * (a document still being read, the choices, writing).
         */
        /**
         * What the Speak tab shows: only this part's words, never a finished part's again (her iPhone 16 bug, iOS
         * de0be16; R-108). Returns the lines above (graphite) and the latest line (ink).
         */
        fun speakLines(s: State): Pair<List<String>, String?> {
            val take = s.committed.drop(s.consumed)
            val earlier = take.dropLast(if (s.partial.isBlank()) 1 else 0)
            val latest = s.partial.takeIf { it.isNotBlank() } ?: take.lastOrNull()
            return earlier to latest
        }

        fun liveText(s: State): String? = when (s.phase) {
            Phase.TRANSCRIPT -> if (s.reading) null else s.transcript
            Phase.CAPTURE -> when (s.tab) {
                TAB_TYPE -> s.typed
                TAB_SPEAK -> (s.committed.drop(s.consumed) + s.partial).filter { it.isNotBlank() }.joinToString(" ")
                else -> null
            }
            else -> null
        }

        const val TAB_SPEAK = 0
        const val TAB_TYPE = 1
        const val TAB_UPLOAD = 2
        const val SAMPLE_DESCRIPTION = "I want an app for my salon in Austin called GlowBook. Customers should be able to see the services and prices, pick a stylist, book a slot, and pay online by card. They should get a text reminder the day before. Staff need a simple admin panel to manage the calendar, mark no-shows and see daily earnings. Later I might add loyalty points."
    }
}
