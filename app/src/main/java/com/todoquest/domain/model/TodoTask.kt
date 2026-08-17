package com.todoquest.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class TodoTask(
    val id: Long,
    val title: String,
    val memo: String,
    val startDate: LocalDate,
    val time: LocalTime?,
    val difficulty: TaskDifficulty,
    val category: String,
    val recurrenceRule: RecurrenceRule,
    val endDate: LocalDate? = null,
    val reminderSetting: ReminderSetting = ReminderSetting(),
)
