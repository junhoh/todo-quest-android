package com.todoquest.domain

import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatType
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.StatAllocationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class StatAllocationPolicyTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun zeroAllocationReturnsNoChanges() {
        val character = character(totalXp = 200, unspentStatPoints = 4)

        val result = StatAllocationPolicy.evaluate(character, StatAllocation(), config)

        assertSame(AllocateStatPointsResult.NoChanges, result)
    }

    @Test
    fun allocationRejectsEveryNegativeStatValue() {
        listOf(
            { StatAllocation(strength = -1) },
            { StatAllocation(vitality = -1) },
            { StatAllocation(focus = -1) },
            { StatAllocation(willpower = -1) },
        ).forEach { allocation ->
            assertThrows(IllegalArgumentException::class.java) {
                allocation()
            }
        }
    }

    @Test
    fun multipleStatsAreAllocatedTogetherAndPreserveEarnedPoints() {
        val character = character(totalXp = 300, unspentStatPoints = 6)
        val allocation = StatAllocation(
            strength = 2,
            vitality = 1,
            focus = 2,
            willpower = 1,
        )

        assertEquals(
            AllocateStatPointsResult.Success(allocation),
            StatAllocationPolicy.evaluate(character, allocation, config),
        )

        val updated = CharacterProgressionPolicy.allocate(character, allocation, config)

        assertEquals(CharacterBaseStats(7, 6, 7, 6), updated.baseStats)
        assertEquals(0, updated.unspentStatPoints)
        CharacterProgressionPolicy.validatePointInvariant(updated, config)
    }

    @Test
    fun allocationReportsRequestedAndAvailablePointsBeforeStatCaps() {
        val character = character(
            totalXp = 4_900,
            baseStats = CharacterBaseStats(60, 47, 5, 5),
            unspentStatPoints = 1,
        )
        val allocation = StatAllocation(strength = 1, vitality = 1)

        val result = StatAllocationPolicy.evaluate(character, allocation, config)

        assertEquals(
            AllocateStatPointsResult.InsufficientPoints(requested = 2, available = 1),
            result,
        )
    }

    @Test
    fun allocationReportsTheFirstStatCapInStableStatOrder() {
        val highPointConfig = CharacterStatBalanceConfig(
            statPointsPerLevel = 10,
            investedBaseStatMax = 6,
            effectiveBaseStatMax = 99,
        )
        val character = character(
            totalXp = 100,
            baseStats = CharacterBaseStats(6, 6, 5, 5),
            unspentStatPoints = 8,
        )
        val allocation = StatAllocation(strength = 1, vitality = 1)

        val result = StatAllocationPolicy.evaluate(character, allocation, highPointConfig)

        assertEquals(AllocateStatPointsResult.StatCap(StatType.STRENGTH), result)
    }

    @Test
    fun allocationRejectsTotalsThatOverflowInt() {
        assertThrows(ArithmeticException::class.java) {
            StatAllocation(strength = Int.MAX_VALUE, vitality = 1)
        }
    }

    @Test
    fun allocationExposesOverflowSafeTotalAndTypedValues() {
        val allocation = StatAllocation(
            strength = 1,
            vitality = 2,
            focus = 3,
            willpower = 4,
        )

        assertEquals(10, allocation.totalPoints)
        assertEquals(1, allocation.valueOf(StatType.STRENGTH))
        assertEquals(2, allocation.valueOf(StatType.VITALITY))
        assertEquals(3, allocation.valueOf(StatType.FOCUS))
        assertEquals(4, allocation.valueOf(StatType.WILLPOWER))
    }

    private fun character(
        totalXp: Long,
        baseStats: CharacterBaseStats = CharacterBaseStats(5, 5, 5, 5),
        unspentStatPoints: Int,
    ): PlayerCharacter = PlayerCharacter(
        id = 1,
        totalXp = totalXp,
        currentGold = 0,
        baseStats = baseStats,
        unspentStatPoints = unspentStatPoints,
        hasUsedFreeStatReset = false,
    )
}
