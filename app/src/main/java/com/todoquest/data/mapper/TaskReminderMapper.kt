package com.todoquest.data.mapper

import com.todoquest.data.local.TaskReminderEntity
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object TaskReminderMapper {
    fun toSetting(entity: TaskReminderEntity): ReminderSetting {
        val mode = enumValue<ReminderMode>(entity.mode, "reminder mode")
        val customTime = entity.customTimeMinuteOfDay?.let { minuteOfDay ->
            require(minuteOfDay in MINUTE_OF_DAY_RANGE) {
                "customTimeMinuteOfDay must be between 0 and 1439: $minuteOfDay"
            }
            LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        }
        require((mode == ReminderMode.CUSTOM_TIME) == (customTime != null)) {
            "CUSTOM_TIME must have a custom minute and other reminder modes must not"
        }
        return ReminderSetting(mode = mode, customTime = customTime)
    }

    fun toScheduleState(entity: TaskReminderEntity): ReminderScheduleState {
        val occurrenceEpochDay = entity.scheduledOccurrenceEpochDay
        val triggerAtEpochMillis = entity.scheduledTriggerAtEpochMillis
        require((occurrenceEpochDay == null) == (triggerAtEpochMillis == null)) {
            "scheduled occurrence date and trigger instant must both be set or both be null"
        }
        val plan = occurrenceEpochDay?.let { epochDay ->
            ReminderPlan(
                key = ReminderOccurrenceKey(
                    taskId = entity.taskId,
                    occurrenceDate = LocalDate.ofEpochDay(epochDay),
                ),
                triggerAt = Instant.ofEpochMilli(requireNotNull(triggerAtEpochMillis)),
            )
        }
        return ReminderScheduleState(
            taskId = entity.taskId,
            setting = toSetting(entity),
            status = enumValue(entity.scheduleStatus, "reminder schedule status"),
            scheduledPlan = plan,
        )
    }

    fun fromSetting(
        taskId: Long,
        setting: ReminderSetting,
        existing: TaskReminderEntity?,
        updatedAt: Instant,
    ): TaskReminderEntity = TaskReminderEntity(
        taskId = taskId,
        mode = setting.mode.name,
        customTimeMinuteOfDay = setting.customTime?.let { it.hour * MINUTES_PER_HOUR + it.minute },
        scheduleStatus = initialStatus(setting).name,
        scheduledOccurrenceEpochDay = existing?.scheduledOccurrenceEpochDay,
        scheduledTriggerAtEpochMillis = existing?.scheduledTriggerAtEpochMillis,
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )

    private fun initialStatus(setting: ReminderSetting): ReminderScheduleStatus =
        if (setting.mode == ReminderMode.NONE) {
            ReminderScheduleStatus.DISABLED
        } else {
            ReminderScheduleStatus.PENDING
        }

    private inline fun <reified T : Enum<T>> enumValue(rawValue: String, label: String): T =
        enumValues<T>().singleOrNull { it.name == rawValue }
            ?: throw IllegalArgumentException("Unknown $label: $rawValue")

    private const val MINUTES_PER_HOUR = 60
    private val MINUTE_OF_DAY_RANGE = 0..1_439
}
