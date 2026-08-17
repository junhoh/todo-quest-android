package com.todoquest.data.mapper

import com.todoquest.data.local.TaskReminderEntity
import com.todoquest.data.local.TodoTaskEntity
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object TodoTaskMapper {
    fun toDomain(entity: TodoTaskEntity): TodoTask = TodoTask(
        id = entity.id,
        title = entity.title,
        memo = entity.memo,
        startDate = LocalDate.ofEpochDay(entity.startDateEpochDay),
        endDate = entity.endDateEpochDay?.let(LocalDate::ofEpochDay),
        time = entity.timeMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
        difficulty = TaskDifficulty.valueOf(entity.difficulty),
        category = TaskCategory.normalize(entity.category),
        recurrenceRule = RecurrenceRule.valueOf(entity.recurrenceRule),
    )

    fun toDomain(
        entity: TodoTaskEntity,
        reminderEntity: TaskReminderEntity?,
    ): TodoTask = toDomain(entity).copy(
        reminderSetting = reminderEntity?.let(TaskReminderMapper::toSetting) ?: ReminderSetting(),
    )

    fun fromInput(input: CreateTaskInput, now: Instant): TodoTaskEntity = TodoTaskEntity(
        recurrenceSeriesId = 0L,
        title = input.title.trim(),
        memo = input.memo.trim(),
        startDateEpochDay = input.startDate.toEpochDay(),
        endDateEpochDay = null,
        timeMinuteOfDay = input.time?.let { it.hour * 60 + it.minute },
        difficulty = input.difficulty.name,
        category = TaskCategory.normalize(input.category),
        recurrenceRule = input.recurrenceRule.name,
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
        deletedAtEpochMillis = null,
    )

    fun fromDomain(task: TodoTask, existing: TodoTaskEntity, now: Instant): TodoTaskEntity =
        existing.copy(
            title = task.title.trim(),
            memo = task.memo.trim(),
            startDateEpochDay = task.startDate.toEpochDay(),
            endDateEpochDay = task.endDate?.toEpochDay(),
            timeMinuteOfDay = task.time?.let { it.hour * 60 + it.minute },
            difficulty = task.difficulty.name,
            category = TaskCategory.normalize(task.category),
            recurrenceRule = task.recurrenceRule.name,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
}
