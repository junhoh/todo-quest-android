package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.usecase.CombatCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatCalculatorTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun defenseUsesDiminishingReductionAndMinimumDamage() {
        assertEquals(50, CombatCalculator.normalDamage(100, 100, config))
        assertEquals(16, CombatCalculator.normalDamage(100, 500, config))
        assertEquals(1, CombatCalculator.normalDamage(5, 500, config))
        assertEquals(4, CombatCalculator.normalDamage(12, 200, config))
        assertEquals(833_333, CombatCalculator.damageAfterDefense(config.maxCombatRawDamage, 500, config))
    }

    @Test
    fun criticalDamageFloorsRawDamageBeforeDefense() {
        assertEquals(18, CombatCalculator.normalDamage(20, 7, config))
        assertEquals(28, CombatCalculator.criticalDamage(20, 15_250, 7, config))
        assertEquals(40, CombatCalculator.normalDamage(51, 25, config))
        assertEquals(80, CombatCalculator.criticalRawDamage(51, 15_750, config))
        assertEquals(64, CombatCalculator.criticalDamage(51, 15_750, 25, config))
        assertEquals(100, CombatCalculator.normalDamage(166, 65, config))
        assertEquals(177, CombatCalculator.criticalDamage(166, 17_700, 65, config))
    }

    @Test
    fun statusChanceClampsAndIntegerRollUsesStrictLessThan() {
        assertEquals(
            2_625,
            CombatCalculator.statusApplicationChanceBp(
                effectBaseBp = 3_000,
                sourceEquipmentBonusBp = 0,
                sourcePassiveBonusBp = 0,
                sourceTemporaryBonusBp = 0,
                targetResistanceBp = 375,
                isImmune = false,
                config = config,
            ),
        )
        assertEquals(
            500,
            CombatCalculator.statusApplicationChanceBp(0, 0, 0, -10_000, 7_500, false, config),
        )
        assertEquals(
            9_500,
            CombatCalculator.statusApplicationChanceBp(10_000, 10_000, 10_000, 10_000, 0, false, config),
        )
        assertEquals(
            0,
            CombatCalculator.statusApplicationChanceBp(3_000, 0, 0, 0, 0, true, config),
        )

        assertTrue(CombatCalculator.rollSucceeds(500, 0, config))
        assertTrue(CombatCalculator.rollSucceeds(500, 499, config))
        assertFalse(CombatCalculator.rollSucceeds(500, 500, config))
        assertFalse(CombatCalculator.rollSucceeds(0, 0, config))
        assertTrue(CombatCalculator.rollSucceeds(10_000, 9_999, config))
    }

    @Test
    fun maxHpChangesPreserveRatioWithoutRevivingZeroHp() {
        assertEquals(121, CombatCalculator.preserveHpRatio(55, 110, 243, config))
        assertEquals(2, CombatCalculator.preserveHpRatio(1, 110, 243, config))
        assertEquals(0, CombatCalculator.preserveHpRatio(0, 110, 243, config))
        assertEquals(1, CombatCalculator.preserveHpRatio(1, 243, 110, config))
    }

    @Test
    fun invalidCombatInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CombatCalculator.normalDamage(0, 0, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CombatCalculator.rollSucceeds(500, 10_000, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CombatCalculator.preserveHpRatio(111, 110, 243, config)
        }
    }
}
