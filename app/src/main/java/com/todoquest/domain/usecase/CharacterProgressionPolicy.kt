package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatType

object CharacterProgressionPolicy {
    fun levelFor(
        totalXp: Long,
        config: CharacterStatBalanceConfig,
    ): Int {
        require(totalXp >= 0) { "totalXp must not be negative" }
        val uncappedLevel = config.levelMin.toLong() + totalXp / config.xpPerLevel
        return minOf(config.levelMax.toLong(), uncappedLevel).toInt()
    }

    fun awardXp(
        character: PlayerCharacter,
        xpAward: Long,
        config: CharacterStatBalanceConfig,
    ): PlayerCharacter {
        require(xpAward >= 0) { "xpAward must not be negative" }
        validatePointInvariant(character, config)

        val oldLevel = levelFor(character.totalXp, config)
        val newTotalXp = Math.addExact(character.totalXp, xpAward)
        val newLevel = levelFor(newTotalXp, config)
        val grantedPoints = Math.multiplyExact(newLevel - oldLevel, config.statPointsPerLevel)
        val updated = character.copy(
            totalXp = newTotalXp,
            unspentStatPoints = Math.addExact(character.unspentStatPoints, grantedPoints),
        )
        validatePointInvariant(updated, config)
        return updated
    }

    fun allocate(
        character: PlayerCharacter,
        allocation: StatAllocation,
        config: CharacterStatBalanceConfig,
    ): PlayerCharacter {
        val evaluation = StatAllocationPolicy.evaluate(character, allocation, config)
        require(evaluation is AllocateStatPointsResult.Success) {
            "allocation must pass validation before it can be applied: $evaluation"
        }

        val updated = character.copy(
            baseStats = CharacterBaseStats(
                strength = Math.addExact(character.baseStats.strength, allocation.strength),
                vitality = Math.addExact(character.baseStats.vitality, allocation.vitality),
                focus = Math.addExact(character.baseStats.focus, allocation.focus),
                willpower = Math.addExact(character.baseStats.willpower, allocation.willpower),
            ),
            unspentStatPoints = character.unspentStatPoints - allocation.totalPoints,
        )
        validatePointInvariant(updated, config)
        return updated
    }

    fun validatePointInvariant(
        character: PlayerCharacter,
        config: CharacterStatBalanceConfig,
    ) {
        val values = character.baseStats.values()
        require(values.all { it in config.initialBaseStat..config.investedBaseStatMax }) {
            "invested base stats must be within ${config.initialBaseStat}..${config.investedBaseStatMax}"
        }
        val investedPoints = values.sumOf { it - config.initialBaseStat }
        val expectedPoints = Math.multiplyExact(
            levelFor(character.totalXp, config) - config.levelMin,
            config.statPointsPerLevel,
        )
        require(Math.addExact(investedPoints, character.unspentStatPoints) == expectedPoints) {
            "allocated and unspent points must equal level-earned points"
        }
    }
}

object StatAllocationPolicy {
    private val validationOrder = listOf(
        StatType.STRENGTH,
        StatType.VITALITY,
        StatType.FOCUS,
        StatType.WILLPOWER,
    )

    fun evaluate(
        character: PlayerCharacter,
        allocation: StatAllocation,
        config: CharacterStatBalanceConfig,
    ): AllocateStatPointsResult {
        CharacterProgressionPolicy.validatePointInvariant(character, config)
        if (allocation.totalPoints == 0) {
            return AllocateStatPointsResult.NoChanges
        }
        if (allocation.totalPoints > character.unspentStatPoints) {
            return AllocateStatPointsResult.InsufficientPoints(
                requested = allocation.totalPoints,
                available = character.unspentStatPoints,
            )
        }

        validationOrder.forEach { type ->
            val expected = Math.addExact(
                character.baseStats.valueOf(type),
                allocation.valueOf(type),
            )
            if (expected > config.investedBaseStatMax) {
                return AllocateStatPointsResult.StatCap(type)
            }
        }
        return AllocateStatPointsResult.Success(allocation)
    }
}

object StatResetPolicy {
    data class ResetResult(
        val character: PlayerCharacter,
        val goldSpent: Long,
        val resetPerformed: Boolean,
    )

    fun reset(
        character: PlayerCharacter,
        config: CharacterStatBalanceConfig,
    ): ResetResult {
        CharacterProgressionPolicy.validatePointInvariant(character, config)
        val investedPoints = character.baseStats.values().sumOf { it - config.initialBaseStat }
        if (investedPoints == 0) {
            return ResetResult(character, goldSpent = 0, resetPerformed = false)
        }

        val level = CharacterProgressionPolicy.levelFor(character.totalXp, config)
        val cost = if (character.hasUsedFreeStatReset) resetCost(level, config) else 0
        require(character.currentGold >= cost) { "not enough gold to reset stats" }

        val updated = character.copy(
            currentGold = character.currentGold - cost,
            baseStats = CharacterBaseStats(
                strength = config.initialBaseStat,
                vitality = config.initialBaseStat,
                focus = config.initialBaseStat,
                willpower = config.initialBaseStat,
            ),
            unspentStatPoints = Math.addExact(character.unspentStatPoints, investedPoints),
            hasUsedFreeStatReset = true,
        )
        CharacterProgressionPolicy.validatePointInvariant(updated, config)
        return ResetResult(updated, goldSpent = cost, resetPerformed = true)
    }

    fun resetCost(
        level: Int,
        config: CharacterStatBalanceConfig,
    ): Long {
        require(level in config.levelMin..config.levelMax) { "level is outside the configured range" }
        val scaledLevelCost = Math.multiplyExact(config.statResetCostPerLevel, level.toLong())
        return minOf(config.statResetMaxCost, Math.addExact(config.statResetBaseCost, scaledLevelCost))
    }
}

private fun CharacterBaseStats.values(): List<Int> = listOf(strength, vitality, focus, willpower)
