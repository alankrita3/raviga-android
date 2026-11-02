package com.raviga.app.ui.capture

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
import kotlinx.coroutines.isActive
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
 * Each note is one way of describing: "Record more" and "Edit what I typed"
 * continue the same note in the same mode, and "Add more" on a document saves
 * it as its own note before the next part (as on iOS), so a file and a
 * recording never merge into one voice note.
 */
class CaptureViewModel(
    private val container: AppContainer,
    initialDraftId: String,
    private val mode: String,
    initialTab: String = DescribeWith.SPEAK,
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
        /** Continuing the note under review in the same mode; the tabs are hidden meanwhile. */
        val continuing: Boolean = false,
        /** Notes saved so far in this visit (documents saved before adding more), shown on the choices. */
        val savedNotes: Int = 0,
        val lastSavedName: String? = null,
        val saving: Boolean = false,
    ) {
        val hasSpeech get() = committed.isNotEmpty() || partial.isNotBlank()
    }

    private val _state = MutableStateFlow(
        State(
            tab = when (initialTab) {
                DescribeWith.TYPE -> TAB_TYPE
                DescribeWith.UPLOAD -> TAB_UPLOAD
                else -> TAB_SPEAK
            },
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    val isAppend: Boolean get() = mode == "append"

    private val notes = WorkingNotes(
        object : WorkingNotes.Sink {
            override suspend fun create() = container.drafts.create().id
            override suspend fun add(draftId: String, kind: String, text: String, meta: WorkingNotes.Meta) =
                container.drafts.addInput(draftId, kind, text, meta.languageDetected, meta.fileName, meta.pageCount, meta.durationSec).id
            override suspend fun edit(draftId: String, inputId: String, text: String) { container.drafts.editInput(draftId, inputId, text) }
            override suspend fun remove(draftId: String, inputId: String) { container.drafts.removeInput(draftId, inputId) }
        },
        initialDraftId.takeIf { it != "new" },
    )
    private val draftId: String? get() = notes.draftId
    private var saveJob: Job? = null
    private var listenJob: Job? = null
    private var timerJob: Job? = null
    private var pendingAfterConsent: (() -> Unit)? = null
    /** Utterances already moved into the transcript, so "Record more" adds only the new ones. */
    private var consumedUtterances = 0
    /** Text the client chose to keep despite the sensitive-data warning. */
    private var acknowledgedText: String? = null
    /** The sensitive-data sheet is open for "Add more" on a document: carry on saving, not writing. */
    private var addingMore = false

    init { checkSpeech() }

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
        if (_state.value.continuing) return
        if (_state.value.listening) stopListening()
        _state.update { it.copy(tab = index, error = null) }
    }

    fun setTyped(text: String) { _state.update { it.copy(typed = text) }; scheduleSave() }
    fun setTranscript(text: String) { _state.update { it.copy(transcript = text) }; scheduleSave() }

    /** Emulators have no microphone and demos want a quick start: offer a canned description. */
    val offersSample: Boolean get() = container.isDemo || isEmulator()

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

    fun pickedFile(uri: Uri) {
        val limits = container.session.config.value.limits
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val picked = try {
                container.files.import(uri, limits.fileMaxBytes, limits.acceptedFileTypes)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't open that file.") }
                return@launch
            }
            // A new document is a new note, even if an earlier one was already saved.
            notes.forget(TAB_UPLOAD)
            acknowledgedText = null
            _state.update {
                it.copy(
                    phase = Phase.TRANSCRIPT, tab = TAB_UPLOAD, reading = true, transcript = "", file = null,
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
            } catch (e: TextExtractor.Unreadable) {
                _state.update { it.copy(phase = Phase.CAPTURE, reading = false, progressMessage = null, error = e.message) }
            } catch (e: Exception) {
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
            TAB_TYPE -> _state.update { it.copy(phase = Phase.TRANSCRIPT, continuing = false, transcript = s.typed.trim()) }
            else -> {
                val consumed = consumedUtterances
                consumedUtterances = s.committed.size
                _state.update { it.copy(phase = Phase.TRANSCRIPT, continuing = false, transcript = spokenReview(it.transcript, it.committed, consumed)) }
            }
        }
        scheduleSave()
    }

    /**
     * "Record more" / "Edit what I typed" on review: the note continues in the same mode (new
     * speech joins the end; the typed editor gets the reviewed text, edits included). A
     * document's review offers "Add more" instead ([addMore]); back leaves, everything saved.
     */
    fun recordMore() {
        _state.update {
            when (it.tab) {
                TAB_UPLOAD -> it.copy(phase = Phase.CAPTURE, error = null, file = null, transcript = "")
                TAB_TYPE -> it.copy(phase = Phase.CAPTURE, error = null, typed = it.transcript, continuing = true)
                else -> it.copy(phase = Phase.CAPTURE, error = null, continuing = true)
            }
        }
    }

    /** "Add more" on a document: save it as its own note, then offer all three ways for the next part. */
    fun addMore() {
        val s = _state.value
        val text = s.transcript.trim()
        if (s.tab != TAB_UPLOAD || s.reading || s.saving || text.isBlank()) return
        addingMore = true
        if (flagged(text)) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                saveNote(s, text)
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.userLine()) }
                return@launch
            }
            // The next part is a new note.
            notes.forget(TAB_UPLOAD)
            acknowledgedText = null
            _state.update {
                it.copy(
                    phase = Phase.CHOOSE, saving = false, transcript = "", file = null,
                    savedNotes = it.savedNotes + 1, lastSavedName = s.file?.fileName ?: it.lastSavedName,
                )
            }
        }
    }

    /** The brief's title when adding to one, for the choices' heading; null for a new project. */
    fun appendTitle(): String? = if (isAppend) draftId?.let { container.drafts.get(it)?.displayTitle } ?: "" else null

    /** A way to add the next part, picked on the choices shown after a document was saved. */
    fun choose(tab: Int) = _state.update { it.copy(phase = Phase.CAPTURE, tab = tab, error = null) }

    /** Back to the choices from a new part that hasn't been reviewed yet. */
    fun backToChoices() {
        if (_state.value.listening) stopListening()
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
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    /** Saves now, even if this screen is going away (the app going to the background, or leaving Capture). */
    fun flush() {
        saveJob?.cancel()
        val s = _state.value
        container.appScope.launch { persist(s) }
    }

    private suspend fun persist(s: State = _state.value) {
        val text = liveText(s, consumedUtterances) ?: return
        // A failed save must not stop the person typing; the next change or leaving tries again.
        runCatching { notes.save(s.tab, kindOf(s.tab), text, metaOf(s)) }
    }

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
        val legal = api?.detailStrings("missing")?.any { it == "terms" || it == "privacy" } == true
        when {
            // Terms out of date: the app shows them (ConsentInterceptor). AI consent is asked here.
            api?.code == ApiException.CONSENT_REQUIRED && !legal -> {
                pendingAfterConsent = { writeBrief() }
                _state.update { it.copy(phase = back, needsConsent = true, progress = null, progressMessage = null) }
            }
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
        container.appScope.launch {
            persist(s)
            val id = draftId ?: return@launch
            if (mode != "append") {
                container.drafts.get(id)?.takeIf { it.inputs.isEmpty() && it.versions.isEmpty() }?.let { container.drafts.delete(it.id) }
            }
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L

        /** R-075: a finished utterance goes after the ones before it; only the live partial is ever replaced. */
        fun heard(s: State, event: DictationEngine.Event): State = when (event) {
            is DictationEngine.Event.Partial -> s.copy(partial = event.text)
            is DictationEngine.Event.Utterance -> s.copy(committed = s.committed + event.text, partial = "")
            else -> s
        }

        /**
         * "Done" while speaking: the utterances not yet under review ([consumed] were taken before) join the end of
         * what's under review, so "Record more" adds to the note instead of replacing it.
         */
        fun spokenReview(transcript: String, committed: List<String>, consumed: Int): String =
            joinText(transcript, committed.drop(consumed).joinToString(" "))

        private fun joinText(a: String, b: String): String = listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

        /**
         * The text of the note being made, as it stands on screen: the typed text, the speech so far (joined to
         * what's under review when recording more), or the text under review. Null when there's nothing to save
         * (a document still being read, the choices, writing).
         */
        fun liveText(s: State, consumedUtterances: Int): String? = when (s.phase) {
            Phase.TRANSCRIPT -> if (s.reading) null else s.transcript
            Phase.CAPTURE -> when (s.tab) {
                TAB_TYPE -> s.typed
                TAB_SPEAK -> {
                    val fresh = (s.committed.drop(consumedUtterances) + s.partial).filter { it.isNotBlank() }.joinToString(" ")
                    listOf(if (s.continuing) s.transcript else "", fresh).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                }
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
