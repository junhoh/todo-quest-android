package com.todoquest.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.todoquest.domain.model.MonsterAttackTrigger

@Entity(
    tableName = "monster_instances",
    indices = [
        Index(
            value = ["stageNumber", "encounterNumber"],
            unique = true,
        ),
    ],
)
data class MonsterInstanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val definitionId: String,
    val grade: String,
    val stageNumber: Int,
    val encounterNumber: Int,
    val level: Int,
    val currentHp: Int,
    val balanceVersion: Int,
)

@Entity(tableName = "combat_progress")
data class CombatProgressEntity(
    @PrimaryKey
    val id: Long = SINGLETON_ID,
    val stageNumber: Int,
    val stageLevel: Int,
    val activeMonsterInstanceId: Long,
    val lastReconciledAtEpochMillis: Long,
    val balanceVersion: Int,
) {
    init {
        require(id == SINGLETON_ID) { "combat progress id must be 1" }
    }

    companion object {
        const val SINGLETON_ID = 1L
    }
}

@Entity(
    tableName = "player_attack_events",
    primaryKeys = ["taskId", "occurrenceDateEpochDay"],
    indices = [
        Index(value = ["status", "createdAtEpochMillis"]),
    ],
)
data class PlayerAttackEventEntity(
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val recurrenceSeriesId: Long,
    val status: String,
    val sourcePlayerLevel: Int,
    val sourceAttack: Int,
    val sourceCriticalChanceBp: Int,
    val sourceCriticalDamageBp: Int,
    val sourceMomentumBp: Int,
    val characterBalanceVersion: Int,
    val monsterBalanceVersion: Int,
    val createdAtEpochMillis: Long,
    val targetMonsterInstanceId: Long?,
    val seed: Long?,
    val roll: Int?,
    val wasCritical: Boolean?,
    val rawDamage: Int?,
    val targetDefense: Int?,
    val finalDamage: Int?,
    val targetHpBefore: Int?,
    val targetHpAfter: Int?,
    val processedAtEpochMillis: Long?,
    @ColumnInfo(defaultValue = "0")
    val combatRewardVersion: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val hitXpAward: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val killBonusXpAward: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val killGoldAward: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val rewardGradeMultiplierBp: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val rewardGoldGainBonusBp: Int = 0,
    val sourceTaskDifficulty: String? = null,
    @ColumnInfo(defaultValue = "0")
    val taskDifficultyBalanceVersion: Int = 0,
)

@Entity(
    tableName = "monster_attack_events",
    primaryKeys = ["taskId", "occurrenceDateEpochDay"],
)
data class MonsterAttackEventEntity(
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val recurrenceSeriesId: Long,
    @ColumnInfo(defaultValue = "MISSED_DEADLINE")
    val trigger: String = MonsterAttackTrigger.MISSED_DEADLINE.name,
    val status: String,
    val skipReason: String?,
    val sourceMonsterInstanceId: Long,
    val sourceMonsterLevel: Int,
    val sourceRawDamage: Int,
    val playerDefense: Int,
    val playerMaxHp: Int,
    val finalDamage: Int,
    val playerHpBefore: Int,
    val playerHpAfter: Int,
    val wasLethal: Boolean,
    val revivedHp: Int?,
    val characterBalanceVersion: Int,
    val monsterBalanceVersion: Int,
    val processedAtEpochMillis: Long,
)
