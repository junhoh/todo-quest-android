package com.todoquest.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class CreateTaskInput(
    val title: String,
    val memo: String,
    val startDate: LocalDate,
    val time: LocalTime?,
    val difficulty: TaskDifficulty,
    val category: String,
    val recurrenceRule: RecurrenceRule,
    val reminderSetting: ReminderSetting = ReminderSetting(),
)
