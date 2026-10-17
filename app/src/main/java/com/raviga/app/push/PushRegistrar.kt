package com.raviga.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.raviga.app.data.local.PrefsStore
import com.raviga.app.data.repo.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the backend's copy of this phone's Firebase Installation ID current (contract v0.8.2,
 * section 13): FCM registers once per app start, the ID goes up whenever onRegistered reports it
 * and once per start. The deprecated token APIs are never called; they fail once FID registration
 * is on. Silently does nothing when Firebase is not configured (no google-services.json yet).
 */
class PushRegistrar(
    private val context: Context,
    private val prefs: PrefsStore,
    private val session: SessionRepository,
    private val scope: CoroutineScope,
) {
    private var sentThisProcess = false
    private var registeredThisProcess = false

    val isFirebaseConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun onRegistered(fid: String) {
        scope.launch {
            val current = prefs.current()
            if (current.pushFid != fid || !current.pushFidSynced) {
                prefs.setPushFid(fid, synced = false)
                sentThisProcess = false
            }
            runCatching { syncIfNeeded() }
        }
    }

    fun onUnregistered() {
        scope.launch {
            prefs.clearPushFid()
            sentThisProcess = false
            if (session.isRegistered) runCatching { session.unregisterPush() }
        }
    }

    /** A new device record (register, recover) has no push target yet: send it again. */
    fun identityChanged() {
        sentThisProcess = false
        scope.launch { runCatching { syncIfNeeded() } }
    }

    suspend fun syncIfNeeded() {
        if (!isFirebaseConfigured || !session.isRegistered) return
        if (!registeredThisProcess) {
            // Delivers the ID through onRegistered, including the first time on a fresh install.
            registeredThisProcess = runCatching { FirebaseMessaging.getInstance().register().await() }.isSuccess
        }
        val current = prefs.current()
        val fid = current.pushFid ?: return
        if (current.pushFidSynced && sentThisProcess) return
        session.registerPushFid(fid)
        prefs.setPushFid(fid, synced = true)
        sentThisProcess = true
    }
}
