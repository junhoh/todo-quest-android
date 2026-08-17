package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.TaskDifficulty

object RewardPolicy {
    data class Input(
        val difficulty: TaskDifficulty,
        val isOnTime: Boolean,
        val recurringRootSequence: Int,
        val dailySequence: Int,
        val goldGainBonusBp: Int,
    )

    data class Reward(
        val xp: Long,
        val gold: Long,
        val onTimeMultiplierBp: Int = 10_000,
        val rewardEfficiencyBp: Int = 10_000,
    )

    fun rewardFor(difficulty: TaskDifficulty): Reward = when (difficulty) {
        TaskDifficulty.EASY -> Reward(xp = 10, gold = 5)
        TaskDifficulty.MEDIUM -> Reward(xp = 20, gold = 10)
        TaskDifficulty.HARD -> Reward(xp = 35, gold = 20)
    }

    fun rewardFor(
        input: Input,
        config: CharacterStatBalanceConfig,
    ): Reward {
        require(input.recurringRootSequence > 0) { "recurringRootSequence must be positive" }
        require(input.dailySequence > 0) { "dailySequence must be positive" }
        require(input.goldGainBonusBp in config.goldGainBonusMinBp..config.goldGainBonusMaxBp) {
            "goldGainBonusBp is outside the configured range"
        }

        val baseReward = rewardFor(input.difficulty)
        val onTimeMultiplierBp = if (input.isOnTime) {
            config.onTimeRewardMultiplierBp
        } else {
            config.lateRewardMultiplierBp
        }
        val rewardEfficiencyBp = minOf(
            recurringEfficiency(input.recurringRootSequence, config),
            dailyEfficiency(input.dailySequence, config),
        )
        val scale = config.basisPointScale.toLong()
        val xpNumerator = multiplyExact(
            baseReward.xp,
            onTimeMultiplierBp.toLong(),
            rewardEfficiencyBp.toLong(),
        )
        val goldNumerator = multiplyExact(
            baseReward.gold,
            onTimeMultiplierBp.toLong(),
            rewardEfficiencyBp.toLong(),
            Math.addExact(scale, input.goldGainBonusBp.toLong()),
        )

        return Reward(
            xp = positiveReward(xpNumerator / Math.multiplyExact(scale, scale), baseReward.xp),
            gold = positiveReward(
                goldNumerator / Math.multiplyExact(Math.multiplyExact(scale, scale), scale),
                baseReward.gold,
            ),
            onTimeMultiplierBp = onTimeMultiplierBp,
            rewardEfficiencyBp = rewardEfficiencyBp,
        )
    }

    private fun recurringEfficiency(
        sequence: Int,
        config: CharacterStatBalanceConfig,
    ): Int = when {
        sequence <= config.recurringFullRewardThrough -> config.fullRewardEfficiencyBp
        sequence <= config.recurringReducedRewardThrough -> config.reducedRewardEfficiencyBp
        else -> config.minimumRewardEfficiencyBp
    }

    private fun dailyEfficiency(
        sequence: Int,
        config: CharacterStatBalanceConfig,
    ): Int = when {
        sequence <= config.dailyFullRewardThrough -> config.fullRewardEfficiencyBp
        sequence <= config.dailyReducedRewardThrough -> config.reducedRewardEfficiencyBp
        else -> config.minimumRewardEfficiencyBp
    }

    private fun positiveReward(calculated: Long, baseReward: Long): Long =
        if (baseReward > 0) maxOf(1, calculated) else calculated

    private fun multiplyExact(first: Long, vararg remaining: Long): Long =
        remaining.fold(first, Math::multiplyExact)
}
