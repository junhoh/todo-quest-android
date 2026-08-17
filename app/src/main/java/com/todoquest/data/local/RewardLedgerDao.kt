package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardLedgerDao {
    @Query(
        """
        SELECT *
        FROM reward_ledger
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun find(taskId: Long, occurrenceDateEpochDay: Long): RewardLedgerEntity?

    @Query(
        """
        SELECT *
        FROM reward_ledger
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        ORDER BY occurrenceDateEpochDay
        """,
    )
    suspend fun findFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
    ): List<RewardLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: RewardLedgerEntity): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM reward_ledger
        WHERE rewardLocalDateEpochDay = :rewardLocalDateEpochDay
        """,
    )
    suspend fun countForRewardLocalDate(rewardLocalDateEpochDay: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM reward_ledger
        WHERE recurrenceSeriesId = :recurrenceSeriesId
        """,
    )
    suspend fun countForRecurrenceSeries(recurrenceSeriesId: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM reward_ledger
        WHERE recurrenceSeriesId = :recurrenceSeriesId
          AND rewardLocalDateEpochDay = :rewardLocalDateEpochDay
        """,
    )
    suspend fun countForRecurrenceSeriesOnRewardLocalDate(
        recurrenceSeriesId: Long,
        rewardLocalDateEpochDay: Long,
    ): Int

    @Query(
        """
        SELECT occurrenceDateEpochDay
        FROM reward_ledger
        WHERE onTime = 1
          AND occurrenceDateEpochDay <= :throughOccurrenceDateEpochDay
        ORDER BY occurrenceDateEpochDay
        """,
    )
    suspend fun findOnTimeOccurrenceDatesThrough(
        throughOccurrenceDateEpochDay: Long,
    ): List<Long>

    @Query(
        """
        SELECT reward_ledger.*
        FROM reward_ledger
        LEFT JOIN player_attack_events
          ON player_attack_events.taskId = reward_ledger.taskId
         AND player_attack_events.occurrenceDateEpochDay = reward_ledger.occurrenceDateEpochDay
        WHERE reward_ledger.combatEligible = 1
          AND player_attack_events.taskId IS NULL
        ORDER BY reward_ledger.awardedAtEpochMillis,
                 reward_ledger.occurrenceDateEpochDay,
                 reward_ledger.taskId
        """,
    )
    suspend fun findCombatEligibleWithoutPlayerAttackEvent(): List<RewardLedgerEntity>

    @Query(
        """
        SELECT DISTINCT occurrenceDateEpochDay
        FROM reward_ledger
        WHERE onTime = 1
          AND occurrenceDateEpochDay <= :throughOccurrenceDateEpochDay
        ORDER BY occurrenceDateEpochDay
        """,
    )
    fun observeOnTimeOccurrenceDatesThrough(
        throughOccurrenceDateEpochDay: Long,
    ): Flow<List<Long>>

    @Query(
        """
        UPDATE reward_ledger
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
}
