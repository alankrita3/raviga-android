package com.raviga.app.di

import android.content.Context
import com.raviga.app.data.audio.DictationEngine

/** Release builds have no scripted dictation (build 8 gate: no demo or mock code in Release). */
object DictationScripts {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): DictationEngine.DictationScript? = null
}
