package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.usecase.RewardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RewardPolicyTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun baseRewardsRemainPredictableByDifficulty() {
        assertEquals(RewardPolicy.Reward(xp = 10, gold = 5), RewardPolicy.rewardFor(TaskDifficulty.EASY))
        assertEquals(RewardPolicy.Reward(xp = 20, gold = 10), RewardPolicy.rewardFor(TaskDifficulty.MEDIUM))
        assertEquals(RewardPolicy.Reward(xp = 35, gold = 20), RewardPolicy.rewardFor(TaskDifficulty.HARD))
    }

    @Test
    fun onTimeMultiplierAndGoldBonusRoundDownOnlyAtTheEnd() {
        val reward = RewardPolicy.rewardFor(
            RewardPolicy.Input(
                difficulty = TaskDifficulty.HARD,
                isOnTime = true,
                recurringRootSequence = 4,
                dailySequence = 1,
                goldGainBonusBp = 800,
            ),
            config,
        )

        assertEquals(19, reward.xp)
        assertEquals(11, reward.gold)
        assertEquals(11_000, reward.onTimeMultiplierBp)
        assertEquals(5_000, reward.rewardEfficiencyBp)
    }

    @Test
    fun lowerEfficiencyIsUsedOnceAndNeverMultipliedWithTheOtherLimit() {
        val reward = RewardPolicy.rewardFor(
            RewardPolicy.Input(
                difficulty = TaskDifficulty.MEDIUM,
                isOnTime = true,
                recurringRootSequence = 7,
                dailySequence = 21,
                goldGainBonusBp = 5_000,
            ),
            config,
        )

        assertEquals(4, reward.xp)
        assertEquals(3, reward.gold)
        assertEquals(2_000, reward.rewardEfficiencyBp)
    }

    @Test
    fun efficiencyBoundariesMatchRecurringAndDailySequences() {
        assertEquals(10_000, rewardEfficiency(recurring = 3, daily = 20))
        assertEquals(5_000, rewardEfficiency(recurring = 4, daily = 21))
        assertEquals(5_000, rewardEfficiency(recurring = 6, daily = 30))
        assertEquals(2_000, rewardEfficiency(recurring = 7, daily = 31))
    }

    @Test
    fun sequencesMustBePositiveAndGoldBonusMustBeConfigured() {
        assertThrows(IllegalArgumentException::class.java) {
            rewardEfficiency(recurring = 0, daily = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RewardPolicy.rewardFor(
                RewardPolicy.Input(TaskDifficulty.EASY, false, 1, 1, 5_001),
                config,
            )
        }
    }

    private fun rewardEfficiency(recurring: Int, daily: Int): Int = RewardPolicy.rewardFor(
        RewardPolicy.Input(
            difficulty = TaskDifficulty.EASY,
            isOnTime = false,
            recurringRootSequence = recurring,
            dailySequence = daily,
            goldGainBonusBp = 0,
        ),
        config,
    ).rewardEfficiencyBp
}
