package com.todoquest.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class UpdateTaskInput(
    val taskId: Long,
    val effectiveDate: LocalDate,
    val title: String,
    val memo: String,
    val time: LocalTime?,
    val difficulty: TaskDifficulty,
    val category: String,
    val recurrenceRule: RecurrenceRule,
    val reminderSetting: ReminderSetting = ReminderSetting(),
)
