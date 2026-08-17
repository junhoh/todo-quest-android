package com.todoquest.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ReminderMode {
    NONE,
    TEN_MINUTES_BEFORE,
    ONE_HOUR_BEFORE,
    CUSTOM_TIME,
}

data class ReminderSetting(
    val mode: ReminderMode = ReminderMode.NONE,
    val customTime: LocalTime? = null,
) {
    init {
        require((mode == ReminderMode.CUSTOM_TIME) == (customTime != null)) {
            "customTime must be set only for CUSTOM_TIME mode"
        }
    }
}

data class ReminderOccurrenceKey(
    val taskId: Long,
    val occurrenceDate: LocalDate,
)

data class ReminderPlan(
    val key: ReminderOccurrenceKey,
    val triggerAt: Instant,
)

enum class ReminderScheduleStatus {
    DISABLED,
    PENDING,
    SCHEDULED,
    POST_NOTIFICATIONS_REQUIRED,
    NOTIFICATION_CHANNEL_DISABLED,
    EXACT_ALARM_ACCESS_REQUIRED,
    DELIVERED,
    NO_FUTURE_OCCURRENCE,
    ERROR,
}

data class ReminderScheduleState(
    val taskId: Long,
    val setting: ReminderSetting,
    val status: ReminderScheduleStatus,
    val scheduledPlan: ReminderPlan? = null,
)

data class ReminderNotificationPayload(
    val key: ReminderOccurrenceKey,
    val title: String,
    val memo: String,
    val occurrenceDate: LocalDate,
    val taskTime: LocalTime?,
)

data class ReminderDeliveryResult(
    val published: Boolean,
    val reminderStatus: ReminderScheduleStatus,
)

data class TaskMutationResult(
    val taskId: Long,
    val reminderStatus: ReminderScheduleStatus,
)
