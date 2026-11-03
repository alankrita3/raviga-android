package com.raviga.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/** Review #9 (Android): an unreadable prefs file starts empty and is written again; it never crashes the launch. */
class PrefsCorruptionTest {
    @Test fun anUnreadablePrefsFileStartsEmptyAndCanBeWrittenAgain() = runBlocking {
        val file = Files.createTempDirectory("prefs").resolve("prefs.preferences_pb").toFile()
        file.writeBytes(byteArrayOf(0x0a, 0x7f, 0x01, 0x02, 0x03))   // a length that runs past the end: not a protobuf
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            // The fixture really is unreadable: without the handler, reading it throws.
            val bare = Files.createTempDirectory("prefs").resolve("prefs.preferences_pb").toFile().apply { writeBytes(file.readBytes()) }
            try {
                PreferenceDataStoreFactory.create(scope = scope) { bare }.data.first()
                org.junit.Assert.fail("expected CorruptionException without the handler")
            } catch (_: androidx.datastore.core.CorruptionException) {
            }
            val store = PreferenceDataStoreFactory.create(corruptionHandler = prefsCorruptionHandler, scope = scope) { file }
            val welcome = booleanPreferencesKey("welcome_done")
            assertNull("read as empty, no CorruptionException", store.data.first()[welcome])
            store.edit { it[welcome] = true }
            assertEquals(true, store.data.first()[welcome])
        } finally {
            scope.cancel()
        }
    }
}
