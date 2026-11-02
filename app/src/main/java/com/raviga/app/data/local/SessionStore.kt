package com.raviga.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.raviga.app.data.drafts.KeystoreSealer
import com.raviga.app.data.drafts.Sealer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Base64

/** What the network layer needs of the identity; [SessionStore] in the app, a fake in tests. */
interface SessionTokens {
    val clientId: String?
    val accessToken: String?
    val installId: String
    fun set(clientId: String, accessToken: String, recoveryKey: String?)
}

/** Where the identity's values live, by key. A sealed SharedPreferences file on the phone; a map in tests. */
interface SessionFile {
    fun get(key: String): String?
    /** Writes every entry at once; a null value removes its key. */
    fun put(values: Map<String, String?>)
    fun clear()
}

/**
 * The client identity: clientId, bearer token, recovery key and the install id. Each value is sealed with
 * AES-256-GCM under a non-exportable Android Keystore key (the drafts' [KeystoreSealer], its own alias) in a private
 * SharedPreferences file that is excluded from backup, so a restored phone registers afresh or links with the key.
 *
 * Build 8 gate (2026-11-02): this replaced EncryptedSharedPreferences, which security-crypto 1.1.0 deprecates. The
 * old file is read once and moved over ([open]); if it can't be read, it stays in use, so no identity is ever lost.
 */
class SessionStore(private val file: SessionFile) : SessionTokens {

    constructor(context: Context) : this(open(context))

    private val _clientIdFlow = MutableStateFlow(file.get(KEY_CLIENT_ID))

    /** The client this device belongs to; changes on register, recover, sign-out and deletion. */
    val clientIdFlow: StateFlow<String?> = _clientIdFlow.asStateFlow()

    override val clientId: String? get() = file.get(KEY_CLIENT_ID)

    override var accessToken: String?
        get() = file.get(KEY_TOKEN)
        set(value) = file.put(mapOf(KEY_TOKEN to value))

    var recoveryKey: String?
        get() = file.get(KEY_RECOVERY)
        set(value) = file.put(mapOf(KEY_RECOVERY to value))

    val isRegistered: Boolean get() = !clientId.isNullOrBlank() && !accessToken.isNullOrBlank()

    /** Stable per-install UUID; re-registering with it yields the same clientId. */
    override val installId: String
        get() = file.get(KEY_INSTALL_ID) ?: java.util.UUID.randomUUID().toString().also {
            file.put(mapOf(KEY_INSTALL_ID to it))
        }

    override fun set(clientId: String, accessToken: String, recoveryKey: String?) {
        file.put(
            buildMap {
                put(KEY_CLIENT_ID, clientId)
                put(KEY_TOKEN, accessToken)
                if (recoveryKey != null) put(KEY_RECOVERY, recoveryKey)
            },
        )
        _clientIdFlow.value = clientId
    }

    /** Forgets the identity but keeps the installId so a re-register maps to the same client. */
    fun clear() {
        val install = installId
        file.clear()
        file.put(mapOf(KEY_INSTALL_ID to install))
        _clientIdFlow.value = null
    }

    /** Full wipe, including the installId (after account deletion). */
    fun wipe() {
        file.clear()
        _clientIdFlow.value = null
    }

    companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_RECOVERY = "recovery_key"
        const val KEY_INSTALL_ID = "install_id"
        val KEYS = listOf(KEY_CLIENT_ID, KEY_TOKEN, KEY_RECOVERY, KEY_INSTALL_ID)

        private const val SEALED_FILE = "session_v2"
        private const val LEGACY_FILE = "session"           // EncryptedSharedPreferences, before build 8
        private const val PLAIN_FILE = "session_plain"      // a phone whose Keystore failed
        private const val KEY_ALIAS = "raviga_session_v1"

        /**
         * The file to use on this phone. Keystore is broken on a handful of devices; a private plain file beats a
         * crash. Once the plain file holds an identity it stays in use, so a Keystore that recovers later cannot
         * swap this phone back to an older identity.
         */
        fun open(context: Context): SessionFile {
            val plain = context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            if (plain.contains(KEY_INSTALL_ID)) return PrefsFile(plain)
            val sealer = runCatching { KeystoreSealer(KEY_ALIAS).also { it.open(it.seal(byteArrayOf(1))) } }.getOrNull()
                ?: return PrefsFile(plain)
            val sealed = SealedFile(context.getSharedPreferences(SEALED_FILE, Context.MODE_PRIVATE), sealer)
            if (sealed.get(KEY_INSTALL_ID) != null) return sealed
            val legacyXml = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_FILE.xml")
            if (!legacyXml.exists()) return sealed
            val legacy = runCatching { legacyPrefs(context) }.getOrNull() ?: return sealed
            return migrate(PrefsFile(legacy), sealed) { context.deleteSharedPreferences(LEGACY_FILE) }
        }

        /**
         * Moves an identity from [from] into [to], then [dropOld]. If the copy doesn't read back the same, [from]
         * stays in use: losing the token, install id or recovery key would cost the client their account.
         */
        fun migrate(from: SessionFile, to: SessionFile, dropOld: () -> Unit): SessionFile {
            val values = KEYS.associateWith { from.get(it) }
            if (values.values.all { it == null }) {
                dropOld()
                return to
            }
            return runCatching {
                to.put(values)
                check(KEYS.all { to.get(it) == values[it] })
                dropOld()
                to
            }.getOrElse { from }
        }

        /** The pre-build-8 store, read only to move it over (see [open]). */
        @Suppress("DEPRECATION")   // security-crypto 1.1.0 deprecates it; read once here, never written again
        private fun legacyPrefs(context: Context): SharedPreferences {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            return androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                LEGACY_FILE,
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

/** A plain private SharedPreferences file (the legacy store, or the no-Keystore fallback). */
class PrefsFile(private val prefs: SharedPreferences) : SessionFile {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(values: Map<String, String?>) = prefs.edit(commit = true) {
        values.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) }
    }
    override fun clear() = prefs.edit(commit = true) { clear() }
}

/** Each value sealed with [sealer] and stored as Base64; a value that no longer opens reads as missing. */
class SealedFile(private val prefs: SharedPreferences, private val sealer: Sealer) : SessionFile {
    override fun get(key: String): String? = prefs.getString(key, null)?.let { stored ->
        runCatching { String(sealer.open(Base64.getDecoder().decode(stored)), Charsets.UTF_8) }.getOrNull()
    }
    override fun put(values: Map<String, String?>) = prefs.edit(commit = true) {
        values.forEach { (k, v) ->
            if (v == null) remove(k) else putString(k, Base64.getEncoder().encodeToString(sealer.seal(v.toByteArray(Charsets.UTF_8))))
        }
    }
    override fun clear() = prefs.edit(commit = true) { clear() }
}
