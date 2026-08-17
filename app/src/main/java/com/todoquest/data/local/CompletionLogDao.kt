package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {
    @Query(
        """
        SELECT *
        FROM completion_logs
        WHERE occurrenceDateEpochDay BETWEEN :rangeStartEpochDay AND :rangeEndEpochDay
        """,
    )
    fun observeBetween(
        rangeStartEpochDay: Long,
        rangeEndEpochDay: Long,
    ): Flow<List<CompletionLogEntity>>

    @Query(
        """
        SELECT *
        FROM completion_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun find(taskId: Long, occurrenceDateEpochDay: Long): CompletionLogEntity?

    @Query(
        """
        SELECT *
        FROM completion_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        ORDER BY occurrenceDateEpochDay
        """,
    )
    suspend fun findFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
    ): List<CompletionLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CompletionLogEntity): Long

    @Query(
        """
        UPDATE completion_logs
        SET taskId = :newTaskId
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        """,
    )
    suspend fun reassignFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
        newTaskId: Long,
    )

    @Query(
        """
        DELETE FROM completion_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        """,
    )
    suspend fun delete(taskId: Long, occurrenceDateEpochDay: Long)
}
