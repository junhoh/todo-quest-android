package com.todoquest.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_tasks",
    indices = [
        Index(value = ["startDateEpochDay"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class TodoTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val recurrenceSeriesId: Long = 0L,
    val title: String,
    val memo: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?,
    val timeMinuteOfDay: Int?,
    val difficulty: String,
    val category: String,
    val recurrenceRule: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)
