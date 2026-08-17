package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: Long): TaskReminderEntity?

    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId")
    fun observeByTaskId(taskId: Long): Flow<TaskReminderEntity?>

    @Query("SELECT * FROM task_reminders ORDER BY taskId")
    fun observeAll(): Flow<List<TaskReminderEntity>>

    @Upsert
    suspend fun upsert(entity: TaskReminderEntity)

    @Query(
        """
        SELECT *
        FROM task_reminders
        WHERE mode != 'NONE'
        ORDER BY taskId
        """,
    )
    suspend fun getConfiguredReminders(): List<TaskReminderEntity>

    @Query("SELECT taskId FROM task_reminders ORDER BY taskId")
    suspend fun getAllTaskIds(): List<Long>

    @Query(
        """
        UPDATE task_reminders
        SET scheduleStatus = :scheduleStatus,
            scheduledOccurrenceEpochDay = :scheduledOccurrenceEpochDay,
            scheduledTriggerAtEpochMillis = :scheduledTriggerAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE taskId = :taskId
          AND (
              (scheduledOccurrenceEpochDay IS NULL AND :expectedOccurrenceEpochDay IS NULL)
              OR scheduledOccurrenceEpochDay = :expectedOccurrenceEpochDay
          )
        """,
    )
    suspend fun compareAndUpdateScheduleState(
        taskId: Long,
        expectedOccurrenceEpochDay: Long?,
        scheduleStatus: String,
        scheduledOccurrenceEpochDay: Long?,
        scheduledTriggerAtEpochMillis: Long?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE task_reminders
        SET scheduleStatus = :scheduleStatus,
            scheduledOccurrenceEpochDay = NULL,
            scheduledTriggerAtEpochMillis = NULL,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE taskId = :taskId
        """,
    )
    suspend fun resetScheduleState(
        taskId: Long,
        scheduleStatus: String,
        updatedAtEpochMillis: Long,
    ): Int
}
