package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FailureLogDao {
    @Query(
        """
        SELECT *
        FROM failure_logs
        WHERE occurrenceDateEpochDay BETWEEN :rangeStartEpochDay AND :rangeEndEpochDay
        ORDER BY occurrenceDateEpochDay, taskId
        """,
    )
    fun observeBetween(
        rangeStartEpochDay: Long,
        rangeEndEpochDay: Long,
    ): Flow<List<FailureLogEntity>>

    @Query(
        """
        SELECT *
        FROM failure_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun find(taskId: Long, occurrenceDateEpochDay: Long): FailureLogEntity?

    @Query(
        """
        SELECT *
        FROM failure_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        ORDER BY occurrenceDateEpochDay
        """,
    )
    suspend fun findFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
    ): List<FailureLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FailureLogEntity): Long

    @Query(
        """
        DELETE FROM failure_logs
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        """,
    )
    suspend fun delete(taskId: Long, occurrenceDateEpochDay: Long)

    @Query(
        """
        UPDATE failure_logs
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
        SELECT failure_logs.*
        FROM failure_logs
        LEFT JOIN monster_attack_events
          ON monster_attack_events.taskId = failure_logs.taskId
         AND monster_attack_events.occurrenceDateEpochDay = failure_logs.occurrenceDateEpochDay
        WHERE monster_attack_events.taskId IS NULL
        ORDER BY failure_logs.failedAtEpochMillis,
                 failure_logs.occurrenceDateEpochDay,
                 failure_logs.taskId
        """,
    )
    suspend fun findPendingMonsterAttacks(): List<FailureLogEntity>
}
