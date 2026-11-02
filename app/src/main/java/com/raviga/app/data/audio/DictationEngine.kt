package com.raviga.app.data.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Live dictation that never leaves the phone (contract v0.6): only the
 * on-device recogniser (Android 12+) is used, never the cloud one. It stops
 * at every pause, so this restarts it until the flow is cancelled and reports
 * each finished utterance separately. No audio is recorded or kept.
 *
 * Speech is English only (founder, contract v0.7); the [Accent] steers the
 * on-device model, and the brief is always written in English.
 */
class DictationEngine(private val context: Context) {

    /** English accents offered for speaking, the same four as iOS. */
    enum class Accent(val tag: String, val label: String) {
        US("en-US", "English (US)"),
        UK("en-GB", "English (UK)"),
        INDIA("en-IN", "English (India)"),
        AUSTRALIA("en-AU", "English (Australia)");

        companion object {
            /** The accent that matches the phone's region, else US English. */
            fun preferred(region: String? = Locale.getDefault().country): Accent = when (region?.uppercase()) {
                "GB", "IE" -> UK
                "IN" -> INDIA
                "AU", "NZ" -> AUSTRALIA
                else -> US
            }
        }
    }

    sealed interface Event {
        data object Ready : Event
        data class Partial(val text: String) : Event
        data class Utterance(val text: String) : Event
        data class Level(val rmsDb: Float) : Event
        data class Error(val code: Int, val fatal: Boolean) : Event
    }

    /**
     * R-075: what one recogniser session after another means for the note. The recogniser stops at every pause and
     * a new session starts; each session's finished text is its own utterance, added after the ones before (the
     * screen replaces only the live partial). A session that ends without a result (an error after words were
     * heard, or an empty result) keeps the words it last showed, so nothing heard is dropped across a pause.
     */
    class SessionText {
        private var heard = ""

        fun partial(text: String?): Event.Partial? = text?.takeIf { it.isNotBlank() }?.let { heard = it; Event.Partial(it) }

        fun result(text: String?): Event.Utterance? = finish(text)

        /** The session ended on an error: whatever it showed is kept as the utterance. */
        fun ended(): Event.Utterance? = finish(null)

        private fun finish(text: String?): Event.Utterance? {
            val words = text?.takeIf { it.isNotBlank() } ?: heard.takeIf { it.isNotBlank() }
            heard = ""
            return words?.let { Event.Utterance(it) }
        }
    }

    sealed interface Availability {
        /** [languageTag] is the on-device model that will be used. */
        data class Ready(val languageTag: String) : Availability
        /** The English model is downloading (we asked for it); try again shortly. */
        data object Downloading : Availability
        data class Unavailable(val message: String) : Availability
    }

    /** True only where speech can be recognised on the device itself. */
    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Which on-device English model to use for [accent], downloading one first if the phone offers that. Main thread. */
    suspend fun availability(accent: Accent = Accent.preferred()): Availability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Availability.Unavailable(NEEDS_ANDROID_12)
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return Availability.Unavailable(NO_OFFLINE_SPEECH)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Availability.Ready(accent.tag)
        return supportOnT(accent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun supportOnT(accent: Accent): Availability {
        val locale = Locale.forLanguageTag(accent.tag)
        val support = querySupport(locale) ?: return Availability.Ready(accent.tag)
        // The accent's own model, else any English one already on the phone.
        pick(accent.tag, support.installedOnDeviceLanguages)?.let { return Availability.Ready(it) }
        if (pick(accent.tag, support.pendingOnDeviceLanguages) != null) return Availability.Downloading
        pick(accent.tag, support.supportedOnDeviceLanguages)?.let { tag ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                runCatching { triggerModelDownload(intent(Locale.forLanguageTag(tag))) }
                destroy()
            }
            return Availability.Downloading
        }
        return Availability.Unavailable(NO_ENGLISH_MODEL)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun querySupport(locale: Locale): RecognitionSupport? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        cont.invokeOnCancellation { runCatching { recognizer.destroy() } }
        runCatching {
            recognizer.checkRecognitionSupport(
                intent(locale),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        recognizer.destroy()
                        if (cont.isActive) cont.resume(support)
                    }
                    override fun onError(error: Int) {
                        recognizer.destroy()
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }.onFailure {
            recognizer.destroy()
            if (cont.isActive) cont.resume(null)
        }
    }

    /** The same accent exactly, else English in another region. */
    private fun pick(wanted: String, offered: List<String>): String? {
        offered.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        val lang = wanted.substringBefore('-').lowercase()
        return offered.firstOrNull { it.substringBefore('-').lowercase() == lang }
    }

    private fun intent(language: Locale) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Keep listening through natural pauses between sentences.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
    }

    /** Collect on the main thread. Requires [Availability.Ready]. */
    @RequiresApi(Build.VERSION_CODES.S)
    fun listen(ready: Availability.Ready): Flow<Event> = callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        var active = true
        // Busy/client errors in a row: past a few, the recognizer is stuck rather than idle.
        var stuck = 0
        val intent = intent(Locale.forLanguageTag(ready.languageTag))
        val session = SessionText()

        fun restart() {
            if (!active) return
            runCatching { recognizer.startListening(intent) }
        }

        fun restartSoon(afterMs: Long) {
            launch {
                delay(afterMs)
                restart()
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(Event.Ready) }
            override fun onBeginningOfSpeech() { stuck = 0 }
            override fun onRmsChanged(rmsdB: Float) { trySend(Event.Level(rmsdB)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                session.partial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())?.let { trySend(it) }
            }
            override fun onResults(results: Bundle?) {
                session.result(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())?.let { trySend(it) }
                stuck = 0
                restart()
            }
            override fun onError(error: Int) {
                session.ended()?.let { trySend(it) }
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val hiccup = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT
                if (hiccup) stuck++ else if (silence) stuck = 0
                val recoverable = silence || (hiccup && stuck <= MAX_HICCUPS)
                trySend(Event.Error(error, fatal = !recoverable))
                if (recoverable) restartSoon(if (hiccup) 250L * stuck else 100L)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        restart()

        awaitClose {
            active = false
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    companion object {
        private const val MAX_HICCUPS = 5

        const val NEEDS_ANDROID_12 = "Speaking needs Android 12 or newer, so your voice never leaves this phone. Type or upload instead."
        const val NO_OFFLINE_SPEECH = "This phone can't recognise speech offline, and your voice never leaves it. Type or upload instead."
        const val NO_ENGLISH_MODEL = "This phone has no offline English speech yet. Type or upload instead."

        /** Copy for a fatal recogniser error. */
        fun errorLine(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> NO_ENGLISH_MODEL
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Raviga needs the microphone to listen. Allow it in Settings, or type instead."
            else -> "The microphone stopped. Tap the button to try again, or type instead."
        }

        /** Maps the recognizer's rms (about -2..10 dB) onto 0..1 for the ink line. */
        fun levelFromRms(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    }
}
