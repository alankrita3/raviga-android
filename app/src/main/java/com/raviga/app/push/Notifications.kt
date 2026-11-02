package com.raviga.app.push

import androidx.core.net.toUri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raviga.app.MainActivity
import com.raviga.app.R

object Notifications {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = context.getString(R.string.notification_channel_projects)
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(
            id,
            context.getString(R.string.notification_channel_projects_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_projects_description) }
        manager.createNotificationChannel(channel)
    }

    /**
     * Whether a notification would show: the permission (Android 13+) and the phone's own switch for this app, which
     * on Android 12 and earlier is the only one (QA M4: turned off in system settings, the app still said "On").
     */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    fun projectDeepLink(projectId: String): Uri = "raviga://project/$projectId".toUri()

    fun showProjectUpdate(context: Context, projectId: String, title: String, body: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW, projectDeepLink(projectId), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            projectId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_projects))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // canPost checked above; the permission can still be revoked in between.
        try {
            NotificationManagerCompat.from(context).notify(projectId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
