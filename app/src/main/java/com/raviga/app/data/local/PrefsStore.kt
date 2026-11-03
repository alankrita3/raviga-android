package com.raviga.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Review #9 (Android): a prefs file that can't be read (a torn write, a bad flash block) starts empty instead of
 * crashing every launch. It costs the welcome flag and the agreed versions; a registered phone keeps working (its
 * identity is in the session store, and /me is re-read).
 */
internal val prefsCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs", corruptionHandler = prefsCorruptionHandler)

/** Small, non-secret flags and caches that shape the first-run and settings flows. */
data class Prefs(
    val welcomeDone: Boolean = false,
    val termsVersionAccepted: String? = null,
    val aiVersionAccepted: String? = null,
    val aiProcessingAllowed: Boolean = false,
    val notificationsAsked: Boolean = false,
    val githubUsername: String = "",
    val awsAccountId: String = "",
    /** The Firebase Installation ID FCM registered with (contract v0.8.2). */
    val pushFid: String? = null,
    val pushFidSynced: Boolean = false,
    val recoveryKeyShown: Boolean = false,
    /** R-056: the versions the person on this phone agreed to. Device-level: a restored account keeps it. */
    val agreedTermsVersion: String? = null,
    val agreedPrivacyVersion: String? = null,
    /** R-079: "Protect your credits" has been offered on this install (once, whichever button was tapped). */
    val recoveryEmailPromptShown: Boolean = false,
    /** R-092 #20: a purchase the store confirmed wasn't booked yet; the next Credits open asks the server to sync. */
    val creditsSyncDue: Boolean = false,
)

class PrefsStore(private val context: Context) {

    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            welcomeDone = p[WELCOME_DONE] ?: false,
            termsVersionAccepted = p[TERMS_VERSION],
            aiVersionAccepted = p[AI_VERSION],
            aiProcessingAllowed = p[AI_ALLOWED] ?: false,
            notificationsAsked = p[NOTIFICATIONS_ASKED] ?: false,
            githubUsername = p[GITHUB] ?: "",
            awsAccountId = p[AWS] ?: "",
            pushFid = p[PUSH_FID],
            pushFidSynced = p[PUSH_SYNCED] ?: false,
            recoveryKeyShown = p[RECOVERY_SHOWN] ?: false,
            agreedTermsVersion = p[AGREED_TERMS],
            agreedPrivacyVersion = p[AGREED_PRIVACY],
            recoveryEmailPromptShown = p[RECOVERY_EMAIL_PROMPT] ?: false,
            creditsSyncDue = p[CREDITS_SYNC_DUE] ?: false,
        )
    }

    suspend fun current(): Prefs = prefs.first()

    suspend fun setWelcomeDone() = context.dataStore.edit { it[WELCOME_DONE] = true }
    suspend fun setTermsAccepted(version: String) = context.dataStore.edit { it[TERMS_VERSION] = version }
    suspend fun setAgreed(termsVersion: String, privacyVersion: String) = context.dataStore.edit {
        it[AGREED_TERMS] = termsVersion
        it[AGREED_PRIVACY] = privacyVersion
    }
    suspend fun setAiAccepted(version: String, allowed: Boolean) = context.dataStore.edit {
        it[AI_VERSION] = version
        it[AI_ALLOWED] = allowed
    }
    suspend fun setAiProcessingAllowed(allowed: Boolean) = context.dataStore.edit { it[AI_ALLOWED] = allowed }
    suspend fun setNotificationsAsked() = context.dataStore.edit { it[NOTIFICATIONS_ASKED] = true }
    suspend fun setDeliveryTargets(github: String?, aws: String?) = context.dataStore.edit {
        if (github != null) it[GITHUB] = github
        if (aws != null) it[AWS] = aws
    }
    suspend fun setPushFid(fid: String, synced: Boolean) = context.dataStore.edit {
        it.remove(LEGACY_PUSH_TOKEN)
        it[PUSH_FID] = fid
        it[PUSH_SYNCED] = synced
    }
    suspend fun clearPushFid() = context.dataStore.edit {
        it.remove(LEGACY_PUSH_TOKEN)
        it.remove(PUSH_FID)
        it[PUSH_SYNCED] = false
    }
    suspend fun setRecoveryKeyShown() = context.dataStore.edit { it[RECOVERY_SHOWN] = true }
    suspend fun setRecoveryEmailPromptShown() = context.dataStore.edit { it[RECOVERY_EMAIL_PROMPT] = true }
    suspend fun setCreditsSyncDue(due: Boolean) = context.dataStore.edit { it[CREDITS_SYNC_DUE] = due }
    suspend fun clear() = context.dataStore.edit { it.clear() }

    /** Forgets what belonged to the previous client; device-level choices stay. */
    suspend fun clearClientData() = context.dataStore.edit {
        it.remove(GITHUB)
        it.remove(AWS)
        it.remove(TERMS_VERSION)
        it.remove(AI_VERSION)
        it.remove(AI_ALLOWED)
        it.remove(RECOVERY_SHOWN)
        it[PUSH_SYNCED] = false
    }

    private companion object {
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        val TERMS_VERSION = stringPreferencesKey("terms_version_accepted")
        val AGREED_TERMS = stringPreferencesKey("agreed_terms_version")
        val AGREED_PRIVACY = stringPreferencesKey("agreed_privacy_version")
        val AI_VERSION = stringPreferencesKey("ai_version_accepted")
        val AI_ALLOWED = booleanPreferencesKey("ai_processing_allowed")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        val GITHUB = stringPreferencesKey("github_username")
        val AWS = stringPreferencesKey("aws_account_id")
        val PUSH_FID = stringPreferencesKey("push_fid")
        val PUSH_SYNCED = booleanPreferencesKey("push_fid_synced")
        /** Pre-v0.8.2 FCM token; never sent again, removed on the next write. */
        val LEGACY_PUSH_TOKEN = stringPreferencesKey("push_token")
        val RECOVERY_SHOWN = booleanPreferencesKey("recovery_key_shown")
        val RECOVERY_EMAIL_PROMPT = booleanPreferencesKey("recovery_email_prompt_shown")
        val CREDITS_SYNC_DUE = booleanPreferencesKey("credits_sync_due")
    }
}
