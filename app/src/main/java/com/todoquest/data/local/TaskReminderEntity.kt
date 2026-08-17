package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_reminders",
    foreignKeys = [
        ForeignKey(
            entity = TodoTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["mode"]),
        Index(value = ["scheduleStatus"]),
    ],
)
data class TaskReminderEntity(
    @PrimaryKey
    val taskId: Long,
    val mode: String,
    val customTimeMinuteOfDay: Int?,
    val scheduleStatus: String,
    val scheduledOccurrenceEpochDay: Long?,
    val scheduledTriggerAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
)
