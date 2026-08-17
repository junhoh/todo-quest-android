package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoTaskDao {
    @Query(
        """
        SELECT *
        FROM todo_tasks
        WHERE deletedAtEpochMillis IS NULL
          AND startDateEpochDay <= :endEpochDay
        ORDER BY startDateEpochDay, timeMinuteOfDay, title
        """,
    )
    fun observeActiveTasksStartingBefore(endEpochDay: Long): Flow<List<TodoTaskEntity>>

    @Query(
        """
        SELECT *
        FROM todo_tasks
        WHERE id = :id
          AND deletedAtEpochMillis IS NULL
        """,
    )
    suspend fun getActiveById(id: Long): TodoTaskEntity?

    @Query(
        """
        SELECT *
        FROM todo_tasks
        WHERE startDateEpochDay <= :endEpochDay
          AND (
              deletedAtEpochMillis IS NULL
              OR deletedAtEpochMillis > :cursorEpochMillis
          )
        ORDER BY id
        """,
    )
    suspend fun findForCombatReconciliation(
        endEpochDay: Long,
        cursorEpochMillis: Long,
    ): List<TodoTaskEntity>

    @Insert
    suspend fun insert(entity: TodoTaskEntity): Long

    @Query(
        """
        UPDATE todo_tasks
        SET recurrenceSeriesId = :recurrenceSeriesId
        WHERE id = :id
        """,
    )
    suspend fun setRecurrenceSeriesId(id: Long, recurrenceSeriesId: Long)

    @Update
    suspend fun update(entity: TodoTaskEntity)

    @Query(
        """
        UPDATE todo_tasks
        SET deletedAtEpochMillis = :deletedAtEpochMillis,
            updatedAtEpochMillis = :deletedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun softDelete(id: Long, deletedAtEpochMillis: Long)
}
