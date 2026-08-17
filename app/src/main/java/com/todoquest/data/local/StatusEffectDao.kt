package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusEffectDao {
    @Query(
        """
        SELECT *
        FROM character_status_effects
        WHERE characterId = :characterId
        ORDER BY effectType
        """,
    )
    fun observeStatusEffects(characterId: Long): Flow<List<CharacterStatusEffectEntity>>

    @Query(
        """
        SELECT *
        FROM character_status_effects
        WHERE characterId = :characterId
          AND active = 1
        ORDER BY effectType
        """,
    )
    fun observeActiveStatusEffects(characterId: Long): Flow<List<CharacterStatusEffectEntity>>

    @Query(
        """
        SELECT *
        FROM character_status_effects
        WHERE characterId = :characterId
          AND active = 1
        ORDER BY effectType
        """,
    )
    suspend fun getActiveStatusEffects(characterId: Long): List<CharacterStatusEffectEntity>

    @Query(
        """
        SELECT *
        FROM character_status_effects
        WHERE characterId = :characterId
          AND effectType = :effectType
        LIMIT 1
        """,
    )
    suspend fun getStatusEffect(
        characterId: Long,
        effectType: String,
    ): CharacterStatusEffectEntity?

    @Upsert
    suspend fun upsertStatusEffect(entity: CharacterStatusEffectEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecoveryOccurrence(entity: StatusEffectRecoveryOccurrenceEntity): Long

    @Query(
        """
        SELECT *
        FROM status_effect_recovery_occurrences
        WHERE characterId = :characterId
          AND effectType = :effectType
        ORDER BY revision, occurrenceDateEpochDay, taskId
        """,
    )
    suspend fun getRecoveryOccurrences(
        characterId: Long,
        effectType: String,
    ): List<StatusEffectRecoveryOccurrenceEntity>

    @Query(
        """
        UPDATE character_status_effects
        SET remainingRecoveryCompletions = remainingRecoveryCompletions - 1,
            lastMutationId = :lastMutationId
        WHERE characterId = :characterId
          AND effectType = :effectType
          AND revision = :revision
          AND active = 1
          AND remainingRecoveryCompletions > 0
        """,
    )
    suspend fun decrementRemainingRecoveryCompletions(
        characterId: Long,
        effectType: String,
        revision: Long,
        lastMutationId: String,
    ): Int

    @Query(
        """
        UPDATE character_status_effects
        SET active = 0,
            lastMutationId = :lastMutationId
        WHERE characterId = :characterId
          AND effectType = :effectType
          AND revision = :revision
          AND active = 1
          AND remainingRecoveryCompletions = 0
        """,
    )
    suspend fun deactivateIfRecovered(
        characterId: Long,
        effectType: String,
        revision: Long,
        lastMutationId: String,
    ): Int

    @Query(
        """
        UPDATE character_status_effects
        SET active = 0,
            lastMutationId = :lastMutationId
        WHERE characterId = :characterId
          AND effectType = :effectType
          AND revision = :revision
          AND active = 1
          AND expiresAtEpochMillis <= :nowEpochMillis
        """,
    )
    suspend fun deactivateIfExpired(
        characterId: Long,
        effectType: String,
        revision: Long,
        nowEpochMillis: Long,
        lastMutationId: String,
    ): Int

    @Query(
        """
        UPDATE character_status_effects
        SET active = 0,
            lastMutationId = :lastMutationId
        WHERE characterId = :characterId
          AND effectType = :effectType
          AND revision = :revision
          AND active = 1
        """,
    )
    suspend fun deactivateStatusEffect(
        characterId: Long,
        effectType: String,
        revision: Long,
        lastMutationId: String,
    ): Int

    @Query(
        """
        UPDATE status_effect_recovery_occurrences
        SET taskId = :newTaskId
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        """,
    )
    suspend fun reassignRecoveryOccurrencesFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
        newTaskId: Long,
    )
}
