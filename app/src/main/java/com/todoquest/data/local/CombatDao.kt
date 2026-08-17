package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CombatDao {
    @Query("SELECT * FROM combat_progress WHERE id = 1")
    fun observeCombatProgress(): Flow<CombatProgressEntity?>

    @Query(
        """
        SELECT monster_instances.*
        FROM monster_instances
        INNER JOIN combat_progress
            ON combat_progress.activeMonsterInstanceId = monster_instances.id
        WHERE combat_progress.id = 1
        """,
    )
    fun observeActiveMonsterInstance(): Flow<MonsterInstanceEntity?>

    @Query(
        """
        SELECT *
        FROM monster_instances
        ORDER BY stageNumber, encounterNumber
        """,
    )
    fun observeMonsterInstances(): Flow<List<MonsterInstanceEntity>>

    @Query("SELECT * FROM combat_progress WHERE id = 1")
    suspend fun getCombatProgress(): CombatProgressEntity?

    @Query("SELECT * FROM monster_instances WHERE id = :id")
    suspend fun getMonsterInstance(id: Long): MonsterInstanceEntity?

    @Query(
        """
        SELECT *
        FROM monster_instances
        WHERE stageNumber = :stageNumber
          AND encounterNumber = :encounterNumber
        LIMIT 1
        """,
    )
    suspend fun getMonsterInstanceAt(
        stageNumber: Int,
        encounterNumber: Int,
    ): MonsterInstanceEntity?

    @Query(
        """
        SELECT *
        FROM player_attack_events
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun getPlayerAttackEvent(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ): PlayerAttackEventEntity?

    @Query(
        """
        SELECT *
        FROM monster_attack_events
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun getMonsterAttackEvent(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ): MonsterAttackEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMonsterInstance(entity: MonsterInstanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCombatProgress(entity: CombatProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayerAttackEvent(entity: PlayerAttackEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMonsterAttackEvent(entity: MonsterAttackEventEntity): Long

    @Query(
        """
        SELECT *
        FROM player_attack_events
        WHERE status = 'PENDING'
        ORDER BY createdAtEpochMillis, occurrenceDateEpochDay, taskId
        """,
    )
    suspend fun findPendingPlayerAttackEvents(): List<PlayerAttackEventEntity>

    @Query(
        """
        UPDATE monster_instances
        SET currentHp = :currentHp
        WHERE id = :id
        """,
    )
    suspend fun updateMonsterCurrentHp(id: Long, currentHp: Int): Int

    @Query(
        """
        UPDATE combat_progress
        SET stageNumber = :stageNumber,
            stageLevel = :stageLevel,
            activeMonsterInstanceId = :activeMonsterInstanceId,
            lastReconciledAtEpochMillis = :lastReconciledAtEpochMillis,
            balanceVersion = :balanceVersion
        WHERE id = :id
        """,
    )
    suspend fun updateCombatProgress(
        id: Long,
        stageNumber: Int,
        stageLevel: Int,
        activeMonsterInstanceId: Long,
        lastReconciledAtEpochMillis: Long,
        balanceVersion: Int,
    ): Int

    @Query(
        """
        UPDATE combat_progress
        SET lastReconciledAtEpochMillis = :lastReconciledAtEpochMillis
        WHERE id = 1
        """,
    )
    suspend fun updateLastReconciledAt(lastReconciledAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE player_attack_events
        SET status = :appliedStatus,
            targetMonsterInstanceId = :targetMonsterInstanceId,
            seed = :seed,
            roll = :roll,
            wasCritical = :wasCritical,
            rawDamage = :rawDamage,
            targetDefense = :targetDefense,
            finalDamage = :finalDamage,
            targetHpBefore = :targetHpBefore,
            targetHpAfter = :targetHpAfter,
            processedAtEpochMillis = :processedAtEpochMillis,
            hitXpAward = :hitXpAward,
            killBonusXpAward = :killBonusXpAward,
            killGoldAward = :killGoldAward,
            rewardGradeMultiplierBp = :rewardGradeMultiplierBp,
            rewardGoldGainBonusBp = :rewardGoldGainBonusBp
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay = :occurrenceDateEpochDay
          AND status = :pendingStatus
        """,
    )
    @Suppress("LongParameterList")
    suspend fun markPlayerAttackApplied(
        taskId: Long,
        occurrenceDateEpochDay: Long,
        pendingStatus: String,
        appliedStatus: String,
        targetMonsterInstanceId: Long,
        seed: Long,
        roll: Int,
        wasCritical: Boolean,
        rawDamage: Int,
        targetDefense: Int,
        finalDamage: Int,
        targetHpBefore: Int,
        targetHpAfter: Int,
        processedAtEpochMillis: Long,
        hitXpAward: Long,
        killBonusXpAward: Long,
        killGoldAward: Long,
        rewardGradeMultiplierBp: Int,
        rewardGoldGainBonusBp: Int,
    ): Int

    @Query(
        """
        UPDATE player_attack_events
        SET taskId = :newTaskId
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        """,
    )
    suspend fun reassignPlayerAttackEventsFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
        newTaskId: Long,
    )

    @Query(
        """
        UPDATE monster_attack_events
        SET taskId = :newTaskId
        WHERE taskId = :taskId
          AND occurrenceDateEpochDay >= :fromOccurrenceDateEpochDay
        """,
    )
    suspend fun reassignMonsterAttackEventsFrom(
        taskId: Long,
        fromOccurrenceDateEpochDay: Long,
        newTaskId: Long,
    )
}
