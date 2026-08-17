package com.todoquest.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.todoquest.R
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderPublisher

class ReminderNotificationUnavailableException : IllegalStateException(
    "Notification permission or app notification capability is unavailable",
)

class AndroidReminderPublisher(
    context: Context,
    private val capabilityAdapter: AndroidReminderCapabilityAdapter =
        AndroidReminderCapabilityAdapter(context),
) : ReminderPublisher {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    override suspend fun publish(payload: ReminderNotificationPayload) {
        ensureNotificationChannel()
        if (
            capabilityAdapter.status(ReminderCapability.POST_NOTIFICATIONS) !=
            ReminderCapabilityStatus.AVAILABLE
        ) {
            throw ReminderNotificationUnavailableException()
        }

        val body = payload.taskTime?.let { time ->
            applicationContext.getString(
                R.string.reminder_notification_body_timed,
                payload.occurrenceDate.year,
                payload.occurrenceDate.monthValue,
                payload.occurrenceDate.dayOfMonth,
                time.hour,
                time.minute,
            )
        } ?: applicationContext.getString(
            R.string.reminder_notification_body_untimed,
            payload.occurrenceDate.year,
            payload.occurrenceDate.monthValue,
            payload.occurrenceDate.dayOfMonth,
        )
        val publicNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(applicationContext.getString(R.string.reminder_notification_public_title))
            .setContentText(applicationContext.getString(R.string.reminder_notification_public_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(payload.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(
                ReminderAlarmIntents.contentPendingIntent(applicationContext, payload.key),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setDefaults(
                Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE,
            )
        }
        val notification = notificationBuilder.build()

        notificationManager.notify(notificationTag(payload), NOTIFICATION_ID, notification)
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(
                R.string.reminder_notification_channel_description,
            )
            enableVibration(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationTag(payload: ReminderNotificationPayload): String =
        "reminder:${payload.key.taskId}:${payload.key.occurrenceDate.toEpochDay()}"

    companion object {
        const val CHANNEL_ID = "todo_task_reminders"
        private const val NOTIFICATION_ID = 0
    }
}
