package com.raviga.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

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
        )
    }

    suspend fun current(): Prefs = prefs.first()

    suspend fun setWelcomeDone() = context.dataStore.edit { it[WELCOME_DONE] = true }
    suspend fun setTermsAccepted(version: String) = context.dataStore.edit { it[TERMS_VERSION] = version }
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
    }
}
