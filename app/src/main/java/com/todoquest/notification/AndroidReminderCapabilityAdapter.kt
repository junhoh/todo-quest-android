package com.todoquest.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus

sealed interface NotificationPermissionLaunchAction {
    data class RuntimePermission(
        val permission: String,
    ) : NotificationPermissionLaunchAction

    data class AppSettings(
        val intent: Intent,
    ) : NotificationPermissionLaunchAction

    data object None : NotificationPermissionLaunchAction
}

class AndroidReminderCapabilityAdapter(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    fun status(capability: ReminderCapability): ReminderCapabilityStatus = when (capability) {
        ReminderCapability.POST_NOTIFICATIONS -> postNotificationsStatus()
        ReminderCapability.EXACT_ALARM -> exactAlarmStatus()
    }

    fun notificationRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun firstLaunchNotificationPermissionAction(): NotificationPermissionLaunchAction {
        val status = postNotificationsStatus()
        return if (status == ReminderCapabilityStatus.AVAILABLE) {
            NotificationPermissionLaunchAction.None
        } else {
            notificationPermissionSettingsAction(status)
        }
    }

    fun notificationPermissionSettingsAction(
        status: ReminderCapabilityStatus,
    ): NotificationPermissionLaunchAction = when {
        status == ReminderCapabilityStatus.CHANNEL_DISABLED ->
            NotificationPermissionLaunchAction.AppSettings(
                notificationChannelSettingsIntent(),
            )
        status == ReminderCapabilityStatus.REQUIRED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPostNotificationsRuntimePermissionGranted() ->
            NotificationPermissionLaunchAction.RuntimePermission(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        else -> NotificationPermissionLaunchAction.AppSettings(notificationSettingsIntent())
    }

    fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${applicationContext.packageName}"),
            )
        }

    fun notificationChannelSettingsIntent(): Intent =
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            reminderNotificationChannel() != null
        ) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidReminderPublisher.CHANNEL_ID)
        } else {
            notificationSettingsIntent()
        }

    fun exactAlarmAccessIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${applicationContext.packageName}"),
            )
        } else {
            null
        }

    private fun postNotificationsStatus(): ReminderCapabilityStatus {
        if (!isPostNotificationsRuntimePermissionGranted() || !areNotificationsEnabled()) {
            return ReminderCapabilityStatus.REQUIRED
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            reminderNotificationChannel()?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            return ReminderCapabilityStatus.CHANNEL_DISABLED
        }
        return ReminderCapabilityStatus.AVAILABLE
    }

    private fun reminderNotificationChannel() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(AndroidReminderPublisher.CHANNEL_ID)
        } else {
            null
        }

    private fun isPostNotificationsRuntimePermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun areNotificationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        }

    private fun exactAlarmStatus(): ReminderCapabilityStatus {
        val available =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        return if (available) {
            ReminderCapabilityStatus.AVAILABLE
        } else {
            ReminderCapabilityStatus.REQUIRED
        }
    }
}
