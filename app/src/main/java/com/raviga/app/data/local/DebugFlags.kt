package com.raviga.app.data.local

import androidx.core.content.edit
import android.content.Context

/** Debug-only switches read synchronously at process start. */
object DebugFlags {
    private const val FILE = "debug_flags"
    private const val USE_DEMO = "use_demo_backend"

    fun useDemoBackend(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(USE_DEMO, false)

    fun setUseDemoBackend(context: Context, value: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) { putBoolean(USE_DEMO, value) }
}
