package com.todoquest.notification

import android.app.AlarmManager
import android.content.Context
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderScheduler

class AndroidReminderScheduler(
    context: Context,
    private val capabilityAdapter: AndroidReminderCapabilityAdapter =
        AndroidReminderCapabilityAdapter(context),
) : ReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override suspend fun capabilityStatus(
        capability: ReminderCapability,
    ): ReminderCapabilityStatus = capabilityAdapter.status(capability)

    override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus {
        if (
            capabilityAdapter.status(ReminderCapability.EXACT_ALARM) !=
            ReminderCapabilityStatus.AVAILABLE
        ) {
            return ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                plan.triggerAt.toEpochMilli(),
                ReminderAlarmIntents.alarmPendingIntent(applicationContext, plan.key),
            )
            ReminderScheduleStatus.SCHEDULED
        } catch (_: SecurityException) {
            ReminderScheduleStatus.ERROR
        }
    }

    override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus {
        alarmManager.cancel(ReminderAlarmIntents.alarmPendingIntent(applicationContext, key))
        return ReminderScheduleStatus.DISABLED
    }
}
