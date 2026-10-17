package com.raviga.app.push

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.raviga.app.RavigaApp
import com.raviga.app.di.AppEvent
import com.raviga.app.ui.status.StatusCopy

/**
 * FCM data messages (contract section 13): anything with a projectId means
 * "refresh that project". In the foreground that happens in place; otherwise
 * a notification deep links to it.
 */
class RavigaMessagingService : FirebaseMessagingService() {

    override fun onRegistered(fid: String) {
        (application as RavigaApp).container.push.onRegistered(fid)
    }

    override fun onUnregistered(fid: String) {
        (application as RavigaApp).container.push.onUnregistered()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val projectId = data["projectId"]
        val container = (application as RavigaApp).container

        when {
            projectId != null -> container.emit(AppEvent.ProjectUpdated(projectId))
            type == "credits_updated" -> container.emit(AppEvent.CreditsUpdated)
            type == "export_ready" -> container.emit(AppEvent.ExportReady)
        }

        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground || projectId == null) return
        val title = data["title"]?.ifBlank { null } ?: message.notification?.title ?: "Raviga"
        val body = data["body"]?.ifBlank { null } ?: message.notification?.body ?: StatusCopy.pushLine(data["status"] ?: type)
        Notifications.showProjectUpdate(this, projectId, title, body)
    }
}
