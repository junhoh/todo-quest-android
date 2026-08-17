package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "failure_logs",
    indices = [
        Index(
            value = ["taskId", "occurrenceDateEpochDay"],
            unique = true,
        ),
    ],
)
data class FailureLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val recurrenceSeriesId: Long,
    val failedAtEpochMillis: Long,
)
