package com.todoquest.domain

import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.StatResetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterProgressionPolicyTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun oneRewardCanGrantSeveralLevelsAndTheirStatPoints() {
        val character = character(totalXp = 90, unspentStatPoints = 0)

        val updated = CharacterProgressionPolicy.awardXp(character, 250, config)

        assertEquals(340, updated.totalXp)
        assertEquals(4, CharacterProgressionPolicy.levelFor(updated.totalXp, config))
        assertEquals(6, updated.unspentStatPoints)
        CharacterProgressionPolicy.validatePointInvariant(updated, config)
    }

    @Test
    fun xpAfterLevel50IsPreservedWithoutGrantingMorePoints() {
        val character = character(totalXp = 4_900, unspentStatPoints = 98)

        val updated = CharacterProgressionPolicy.awardXp(character, 1_000, config)

        assertEquals(5_900, updated.totalXp)
        assertEquals(50, CharacterProgressionPolicy.levelFor(updated.totalXp, config))
        assertEquals(98, updated.unspentStatPoints)
        CharacterProgressionPolicy.validatePointInvariant(updated, config)
    }

    @Test
    fun allocationKeepsThePointInvariantAndEnforcesTheInvestmentCap() {
        val character = character(totalXp = 4_900, unspentStatPoints = 98)

        val updated = CharacterProgressionPolicy.allocate(
            character = character,
            allocation = StatAllocation(strength = 55),
            config = config,
        )

        assertEquals(60, updated.baseStats.strength)
        assertEquals(43, updated.unspentStatPoints)
        CharacterProgressionPolicy.validatePointInvariant(updated, config)
        assertThrows(IllegalArgumentException::class.java) {
            CharacterProgressionPolicy.allocate(
                character,
                StatAllocation(strength = 56),
                config,
            )
        }
    }

    @Test
    fun invalidPointStateIsRejectedAtThePolicyBoundary() {
        val invalid = character(totalXp = 200, unspentStatPoints = 3)

        assertThrows(IllegalArgumentException::class.java) {
            CharacterProgressionPolicy.validatePointInvariant(invalid, config)
        }
    }

    @Test
    fun firstSuccessfulResetIsFreeAndReturnsOnlyInvestedPoints() {
        val character = character(
            totalXp = 300,
            currentGold = 500,
            baseStats = CharacterBaseStats(8, 6, 5, 5),
            unspentStatPoints = 2,
        )

        val result = StatResetPolicy.reset(character, config)

        assertTrue(result.resetPerformed)
        assertEquals(0, result.goldSpent)
        assertEquals(CharacterBaseStats(5, 5, 5, 5), result.character.baseStats)
        assertEquals(6, result.character.unspentStatPoints)
        assertEquals(500, result.character.currentGold)
        assertTrue(result.character.hasUsedFreeStatReset)
        CharacterProgressionPolicy.validatePointInvariant(result.character, config)
    }

    @Test
    fun laterResetChargesLevelBasedGold() {
        val character = character(
            totalXp = 300,
            currentGold = 500,
            baseStats = CharacterBaseStats(8, 6, 5, 5),
            unspentStatPoints = 2,
            hasUsedFreeStatReset = true,
        )

        val result = StatResetPolicy.reset(character, config)

        assertTrue(result.resetPerformed)
        assertEquals(180, result.goldSpent)
        assertEquals(320, result.character.currentGold)
        assertEquals(6, result.character.unspentStatPoints)
    }

    @Test
    fun resetWithoutInvestmentConsumesNeitherFreeResetNorGold() {
        val character = character(
            totalXp = 100,
            currentGold = 500,
            unspentStatPoints = 2,
        )

        val result = StatResetPolicy.reset(character, config)

        assertFalse(result.resetPerformed)
        assertEquals(0, result.goldSpent)
        assertEquals(character, result.character)
        assertFalse(result.character.hasUsedFreeStatReset)
    }

    private fun character(
        totalXp: Long,
        currentGold: Long = 0,
        baseStats: CharacterBaseStats = CharacterBaseStats(5, 5, 5, 5),
        unspentStatPoints: Int,
        hasUsedFreeStatReset: Boolean = false,
    ): PlayerCharacter = PlayerCharacter(
        id = 1,
        totalXp = totalXp,
        currentGold = currentGold,
        baseStats = baseStats,
        unspentStatPoints = unspentStatPoints,
        hasUsedFreeStatReset = hasUsedFreeStatReset,
    )
}
