package com.todoquest.domain.repository

import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus

enum class ReminderCapability {
    POST_NOTIFICATIONS,
    EXACT_ALARM,
}

enum class ReminderCapabilityStatus {
    AVAILABLE,
    REQUIRED,
    CHANNEL_DISABLED,
}

interface FirstLaunchNotificationPromptStore {
    fun consumeFirstLaunchCheck(): Boolean
}

interface ReminderScheduler {
    suspend fun capabilityStatus(capability: ReminderCapability): ReminderCapabilityStatus

    suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus

    suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus
}
