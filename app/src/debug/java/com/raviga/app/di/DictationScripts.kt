package com.raviga.app.di

import android.content.Context
import com.raviga.app.data.audio.DictationEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Debug builds only (Play screenshots, 2026-11-03): a phone without an on-device recogniser can still show the real
 * Speak screen. Put the words in the app's files folder and Speak "hears" them:
 *   adb push words.txt /data/local/tmp/ds.txt
 *   adb shell "run-as com.raviga.app.debug cp /data/local/tmp/ds.txt files/dictation-script.txt"
 * Delete the file to go back to the phone's own recogniser.
 */
object DictationScripts {
    fun create(context: Context): DictationEngine.DictationScript = object : DictationEngine.DictationScript {
        override fun text(): String? =
            File(context.filesDir, "dictation-script.txt").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

        /** Each sentence grows word by word, then is finished; voice levels throughout, until stopped. */
        override fun listen(text: String): Flow<DictationEngine.Event> = flow {
            emit(DictationEngine.Event.Ready)
            val sentences = Regex("""[^.!?]+[.!?]?""").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
            val random = java.util.Random(7)
            for (sentence in sentences) {
                val words = sentence.split(" ")
                for (n in 1..words.size) {
                    emit(DictationEngine.Event.Level(2f + random.nextFloat() * 8f))
                    emit(DictationEngine.Event.Partial(words.take(n).joinToString(" ")))
                    delay(170)
                }
                if (sentence.last() in ".!?") emit(DictationEngine.Event.Utterance(sentence))
            }
            while (true) { emit(DictationEngine.Event.Level(1f + random.nextFloat() * 7f)); delay(90) }
        }
    }
}
