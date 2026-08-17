package com.todoquest.data.mapper

import com.todoquest.data.local.CombatProgressEntity
import com.todoquest.data.local.MonsterAttackEventEntity
import com.todoquest.data.local.MonsterInstanceEntity
import com.todoquest.data.local.PlayerAttackEventEntity
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterAttackSkipReason
import com.todoquest.domain.model.MonsterAttackSnapshot
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.PlayerAttackSnapshot
import com.todoquest.domain.model.StageProgress
import com.todoquest.domain.model.TaskDifficulty
import java.time.Instant

object CombatEntityMapper {
    fun toDomain(entity: MonsterInstanceEntity): MonsterInstance = MonsterInstance(
        id = entity.id,
        definitionId = entity.definitionId,
        grade = parseEnum(entity.grade, "monster grade"),
        stageNumber = entity.stageNumber,
        encounterNumber = entity.encounterNumber,
        level = entity.level,
        currentHp = entity.currentHp,
        balanceVersion = entity.balanceVersion,
    )

    fun fromDomain(instance: MonsterInstance): MonsterInstanceEntity = MonsterInstanceEntity(
        id = instance.id,
        definitionId = instance.definitionId,
        grade = instance.grade.name,
        stageNumber = instance.stageNumber,
        encounterNumber = instance.encounterNumber,
        level = instance.level,
        currentHp = instance.currentHp,
        balanceVersion = instance.balanceVersion,
    )

    fun toDomain(entity: CombatProgressEntity): StageProgress = StageProgress(
        stageNumber = entity.stageNumber,
        stageLevel = entity.stageLevel,
        activeMonsterInstanceId = entity.activeMonsterInstanceId,
        lastReconciledAt = Instant.ofEpochMilli(entity.lastReconciledAtEpochMillis),
        balanceVersion = entity.balanceVersion,
    )

    fun toAppliedPlayerAttack(
        entity: PlayerAttackEventEntity,
        wasAlreadyApplied: Boolean,
    ): PlayerAttackResult.Applied {
        check(toEventStatus(entity.status) == CombatEventStatus.APPLIED) {
            "player attack event is not applied"
        }
        return PlayerAttackResult.Applied(
            attack = PlayerAttackSnapshot(
                taskId = entity.taskId,
                occurrenceDateEpochDay = entity.occurrenceDateEpochDay,
                targetMonsterInstanceId = requireResult(
                    entity.targetMonsterInstanceId,
                    "target monster instance",
                ),
                seed = requireResult(entity.seed, "seed"),
                roll = requireResult(entity.roll, "roll"),
                wasCritical = requireResult(entity.wasCritical, "critical result"),
                rawDamage = requireResult(entity.rawDamage, "raw damage"),
                targetDefense = requireResult(entity.targetDefense, "target defense"),
                finalDamage = requireResult(entity.finalDamage, "final damage"),
                targetHpBefore = requireResult(entity.targetHpBefore, "target HP before"),
                targetHpAfter = requireResult(entity.targetHpAfter, "target HP after"),
                processedAt = Instant.ofEpochMilli(
                    requireResult(entity.processedAtEpochMillis, "processed time"),
                ),
                combatRewardVersion = entity.combatRewardVersion,
                hitXpAward = entity.hitXpAward,
                killBonusXpAward = entity.killBonusXpAward,
                killGoldAward = entity.killGoldAward,
                rewardGradeMultiplierBp = entity.rewardGradeMultiplierBp,
                rewardGoldGainBonusBp = entity.rewardGoldGainBonusBp,
                sourceTaskDifficulty = toTaskDifficulty(entity.sourceTaskDifficulty),
                taskDifficultyBalanceVersion = entity.taskDifficultyBalanceVersion,
            ),
            wasAlreadyApplied = wasAlreadyApplied,
        )
    }

    fun toAppliedMonsterAttack(
        entity: MonsterAttackEventEntity,
        wasAlreadyApplied: Boolean,
    ): MonsterAttackResult.Applied {
        val status = toEventStatus(entity.status)
        check(status == CombatEventStatus.APPLIED || status == CombatEventStatus.SKIPPED) {
            "monster attack event is neither applied nor permanently skipped"
        }
        return MonsterAttackResult.Applied(
            attack = MonsterAttackSnapshot(
                taskId = entity.taskId,
                occurrenceDateEpochDay = entity.occurrenceDateEpochDay,
                trigger = toMonsterAttackTrigger(entity.trigger),
                sourceMonsterInstanceId = entity.sourceMonsterInstanceId,
                sourceMonsterLevel = entity.sourceMonsterLevel,
                sourceRawDamage = entity.sourceRawDamage,
                playerDefense = entity.playerDefense,
                playerMaxHp = entity.playerMaxHp,
                finalDamage = entity.finalDamage,
                playerHpBefore = entity.playerHpBefore,
                playerHpAfter = entity.playerHpAfter,
                wasLethal = entity.wasLethal,
                revivedHp = entity.revivedHp,
                processedAt = Instant.ofEpochMilli(entity.processedAtEpochMillis),
            ),
            wasAlreadyApplied = wasAlreadyApplied,
        )
    }

    fun toEventStatus(value: String): CombatEventStatus = parseEnum(value, "combat event status")

    fun toMonsterAttackSkipReason(value: String?): MonsterAttackSkipReason? =
        value?.let { parseEnum(it, "monster attack skip reason") }

    fun toMonsterAttackTrigger(value: String): MonsterAttackTrigger =
        parseEnum(value, "monster attack trigger")

    fun toTaskDifficulty(value: String?): TaskDifficulty? = value?.let { difficulty ->
        enumValues<TaskDifficulty>().singleOrNull { it.name == difficulty }
    }

    private fun <T : Any> requireResult(value: T?, fieldName: String): T =
        checkNotNull(value) { "Applied player attack is missing $fieldName" }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fieldName: String): T =
        enumValues<T>().singleOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown $fieldName: $value")
}
