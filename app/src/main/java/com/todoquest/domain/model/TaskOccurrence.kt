package com.todoquest.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class TaskOccurrenceStatus {
    TODO,
    COMPLETED,
    FAILED,
}

data class FailureResult(
    val wasAlreadyFailed: Boolean,
) {
    val isNewFailure: Boolean get() = !wasAlreadyFailed
}

class OccurrenceStateConflictException(
    val taskId: Long,
    val occurrenceDate: LocalDate,
    val currentStatus: TaskOccurrenceStatus,
    val requestedStatus: TaskOccurrenceStatus,
) : IllegalStateException(
    "Occurrence $taskId on $occurrenceDate is $currentStatus and cannot become $requestedStatus",
)

data class TaskOccurrence(
    val taskId: Long,
    val title: String,
    val memo: String,
    val occurrenceDate: LocalDate,
    val time: LocalTime?,
    val difficulty: TaskDifficulty,
    val category: String,
    val recurrenceRule: RecurrenceRule,
    val status: TaskOccurrenceStatus,
    val reminderSetting: ReminderSetting = ReminderSetting(),
    val reminderScheduleStatus: ReminderScheduleStatus = ReminderScheduleStatus.DISABLED,
) {
    constructor(
        taskId: Long,
        title: String,
        memo: String,
        occurrenceDate: LocalDate,
        time: LocalTime?,
        difficulty: TaskDifficulty,
        category: String,
        recurrenceRule: RecurrenceRule,
        isCompleted: Boolean,
    ) : this(
        taskId = taskId,
        title = title,
        memo = memo,
        occurrenceDate = occurrenceDate,
        time = time,
        difficulty = difficulty,
        category = category,
        recurrenceRule = recurrenceRule,
        status = if (isCompleted) TaskOccurrenceStatus.COMPLETED else TaskOccurrenceStatus.TODO,
    )

    val isCompleted: Boolean get() = status == TaskOccurrenceStatus.COMPLETED
    val isFailed: Boolean get() = status == TaskOccurrenceStatus.FAILED
    val isPending: Boolean get() = status == TaskOccurrenceStatus.TODO
    val isRecurring: Boolean = recurrenceRule != RecurrenceRule.NONE

    fun copy(isCompleted: Boolean): TaskOccurrence = copy(
        status = if (isCompleted) TaskOccurrenceStatus.COMPLETED else TaskOccurrenceStatus.TODO,
    )
}
