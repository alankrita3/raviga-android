package com.raviga.app.data

import android.content.SharedPreferences
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.data.local.PrefsFile
import com.raviga.app.data.local.SealedFile
import com.raviga.app.data.local.SessionFile
import com.raviga.app.data.local.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 8 gate: the identity moved off EncryptedSharedPreferences (deprecated in security-crypto 1.1.0) to values
 * sealed under a Keystore key. Moving an existing phone over must never lose its token, install id or recovery key.
 */
class SessionStoreTest {

    /** SharedPreferences in memory, strings only (all the session uses). */
    private class MemoryPrefs : SharedPreferences {
        val map = mutableMapOf<String, String>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun contains(key: String) = key in map
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val puts = mutableMapOf<String, String?>()
            var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = apply { puts[key] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
    }

    /** Reverses the bytes: enough to prove values are stored sealed, not as typed. */
    private object Reverse : Sealer {
        override fun seal(plain: ByteArray) = plain.reversedArray()
        override fun open(sealed: ByteArray) = sealed.reversedArray()
    }

    private class MemoryFile(val values: MutableMap<String, String> = mutableMapOf()) : SessionFile {
        override fun get(key: String) = values[key]
        override fun put(values: Map<String, String?>) = values.forEach { (k, v) -> if (v == null) this.values.remove(k) else this.values[k] = v }
        override fun clear() = values.clear()
    }

    private val identity = mapOf(
        SessionStore.KEY_CLIENT_ID to "cl_1",
        SessionStore.KEY_TOKEN to "rat_token",
        SessionStore.KEY_RECOVERY to "rark_key",
        SessionStore.KEY_INSTALL_ID to "install-1",
    )

    @Test fun valuesAreSealedAtRestAndReadBack() {
        val prefs = MemoryPrefs()
        val store = SessionStore(SealedFile(prefs, Reverse))
        store.set("cl_1", "rat_token", "rark_key")
        assertEquals("cl_1", store.clientId)
        assertEquals("rat_token", store.accessToken)
        assertEquals("rark_key", store.recoveryKey)
        assertTrue(store.isRegistered)
        assertFalse("the token is not stored as typed", prefs.map.values.any { "rat_token" in it })
    }

    @Test fun installIdIsStableAndSurvivesSignOutButNotWipe() {
        val store = SessionStore(MemoryFile())
        val install = store.installId
        assertEquals(install, store.installId)
        store.set("cl_1", "rat", null)
        store.clear()
        assertNull(store.clientId)
        assertEquals("sign-out keeps the install id", install, store.installId)
        store.wipe()
        assertNotEquals("deletion starts afresh", install, store.installId)
    }

    @Test fun aValueThatNoLongerOpensReadsAsMissing() {
        val prefs = MemoryPrefs()
        SealedFile(prefs, Reverse).put(mapOf(SessionStore.KEY_TOKEN to "rat"))
        val broken = object : Sealer {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray): ByteArray = throw IllegalStateException("key gone")
        }
        assertNull(SealedFile(prefs, broken).get(SessionStore.KEY_TOKEN))
    }

    @Test fun anExistingPhoneMovesOverWithItsWholeIdentity() {
        val legacy = PrefsFile(MemoryPrefs().apply { map.putAll(identity) })
        val sealed = SealedFile(MemoryPrefs(), Reverse)
        var dropped = false
        val used = SessionStore.migrate(legacy, sealed) { dropped = true }
        assertSame(sealed, used)
        assertTrue("the old file is deleted only after the copy reads back", dropped)
        val store = SessionStore(used)
        assertEquals("cl_1", store.clientId)
        assertEquals("rat_token", store.accessToken)
        assertEquals("rark_key", store.recoveryKey)
        assertEquals("install-1", store.installId)
    }

    @Test fun aCopyThatDoesNotReadBackKeepsTheOldStore() {
        val legacy = MemoryFile(identity.toMutableMap())
        val lossy = object : SessionFile {
            override fun get(key: String): String? = null
            override fun put(values: Map<String, String?>) {}
            override fun clear() {}
        }
        var dropped = false
        val used = SessionStore.migrate(legacy, lossy) { dropped = true }
        assertSame(legacy, used)
        assertFalse(dropped)
        assertEquals("rat_token", SessionStore(used).accessToken)
    }

    @Test fun anEmptyOldStoreIsDroppedAndTheNewOneUsed() {
        val sealed = MemoryFile()
        var dropped = false
        assertSame(sealed, SessionStore.migrate(MemoryFile(), sealed) { dropped = true })
        assertTrue(dropped)
    }
}
