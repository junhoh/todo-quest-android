package com.todoquest.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.todoquest.MainActivity
import com.todoquest.domain.model.ReminderOccurrenceKey
import java.time.DateTimeException
import java.time.LocalDate

object ReminderAlarmIntents {
    const val ACTION_DELIVER_REMINDER = "com.todoquest.action.DELIVER_REMINDER"
    const val ACTION_OPEN_REMINDER = "com.todoquest.action.OPEN_REMINDER"
    const val EXTRA_TASK_ID = "com.todoquest.extra.REMINDER_TASK_ID"
    const val EXTRA_OCCURRENCE_EPOCH_DAY = "com.todoquest.extra.REMINDER_OCCURRENCE_EPOCH_DAY"

    private const val REMINDER_SCHEME = "todoquest"
    private const val ALARM_AUTHORITY = "reminder"
    private const val CALENDAR_AUTHORITY = "calendar"
    private const val REQUEST_CODE = 0
    private const val PENDING_INTENT_FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun alarmIntent(context: Context, key: ReminderOccurrenceKey): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            data = occurrenceUri(ALARM_AUTHORITY, key)
            putExtra(EXTRA_TASK_ID, key.taskId)
            putExtra(EXTRA_OCCURRENCE_EPOCH_DAY, key.occurrenceDate.toEpochDay())
        }

    fun alarmPendingIntent(context: Context, key: ReminderOccurrenceKey): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE,
            alarmIntent(context.applicationContext, key),
            PENDING_INTENT_FLAGS,
        )

    fun contentPendingIntent(context: Context, key: ReminderOccurrenceKey): PendingIntent =
        PendingIntent.getActivity(
            context.applicationContext,
            REQUEST_CODE,
            contentIntent(context.applicationContext, key),
            PENDING_INTENT_FLAGS,
        )

    fun contentIntent(context: Context, key: ReminderOccurrenceKey): Intent =
        Intent(context.applicationContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_REMINDER
            data = occurrenceUri(CALENDAR_AUTHORITY, key)
            putExtra(EXTRA_TASK_ID, key.taskId)
            putExtra(EXTRA_OCCURRENCE_EPOCH_DAY, key.occurrenceDate.toEpochDay())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun reminderKeyFromAlarm(intent: Intent): ReminderOccurrenceKey? {
        return reminderKeyFromIntent(intent, ACTION_DELIVER_REMINDER, ALARM_AUTHORITY)
    }

    fun reminderKeyFromContentIntent(intent: Intent): ReminderOccurrenceKey? =
        reminderKeyFromIntent(intent, ACTION_OPEN_REMINDER, CALENDAR_AUTHORITY)

    private fun reminderKeyFromIntent(
        intent: Intent,
        expectedAction: String,
        expectedAuthority: String,
    ): ReminderOccurrenceKey? = try {
        if (intent.action != expectedAction) return null
        if (!intent.hasExtra(EXTRA_TASK_ID) || !intent.hasExtra(EXTRA_OCCURRENCE_EPOCH_DAY)) {
            return null
        }
        val taskId = intent.taskIdExtra()
        val occurrenceEpochDay = intent.occurrenceEpochDayExtra()
        if (taskId <= 0L) return null
        val data = intent.data ?: return null
        if (data.scheme != REMINDER_SCHEME || data.authority != expectedAuthority) return null
        if (data.pathSegments != listOf(taskId.toString(), occurrenceEpochDay.toString())) {
            return null
        }
        ReminderOccurrenceKey(taskId, LocalDate.ofEpochDay(occurrenceEpochDay))
    } catch (_: DateTimeException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun occurrenceUri(authority: String, key: ReminderOccurrenceKey): Uri =
        Uri.Builder()
            .scheme(REMINDER_SCHEME)
            .authority(authority)
            .appendPath(key.taskId.toString())
            .appendPath(key.occurrenceDate.toEpochDay().toString())
            .build()
}

internal fun Intent.taskIdExtra(): Long =
    getLongExtra(ReminderAlarmIntents.EXTRA_TASK_ID, Long.MIN_VALUE)

internal fun Intent.occurrenceEpochDayExtra(): Long =
    getLongExtra(ReminderAlarmIntents.EXTRA_OCCURRENCE_EPOCH_DAY, Long.MIN_VALUE)
