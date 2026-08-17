package com.todoquest.domain

import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.usecase.EquipmentComparisonCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EquipmentComparisonCalculatorTest {
    @Test
    fun comparisonUsesStableStatOrderAndPreservesPositiveNegativeAndZeroDeltas() {
        val current = equipment(
            id = 1,
            modifiers = listOf(
                modifier(1, StatTarget.Derived(DerivedStatType.DEFENSE), ModifierType.FLAT, 4),
                modifier(1, StatTarget.Base(StatType.VITALITY), ModifierType.FLAT, 2),
                modifier(1, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.PERCENT_ADD, 600),
                modifier(1, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 20),
            ),
        )
        val candidate = equipment(
            id = 2,
            modifiers = listOf(
                modifier(2, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 10),
                modifier(2, StatTarget.Base(StatType.VITALITY), ModifierType.FLAT, 3),
                modifier(2, StatTarget.Derived(DerivedStatType.DEFENSE), ModifierType.FLAT, 4),
                modifier(2, StatTarget.Derived(DerivedStatType.HP_RECOVERY), ModifierType.FLAT, 2),
                modifier(2, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 15),
            ),
        )

        assertEquals(
            listOf(
                EquipmentStatComparison(
                    target = StatTarget.Base(StatType.VITALITY),
                    modifierType = ModifierType.FLAT,
                    currentAmount = 2,
                    candidateAmount = 3,
                    difference = 1,
                ),
                EquipmentStatComparison(
                    target = StatTarget.Derived(DerivedStatType.MAX_HP),
                    modifierType = ModifierType.FLAT,
                    currentAmount = 20,
                    candidateAmount = 25,
                    difference = 5,
                ),
                EquipmentStatComparison(
                    target = StatTarget.Derived(DerivedStatType.MAX_HP),
                    modifierType = ModifierType.PERCENT_ADD,
                    currentAmount = 600,
                    candidateAmount = 0,
                    difference = -600,
                ),
                EquipmentStatComparison(
                    target = StatTarget.Derived(DerivedStatType.DEFENSE),
                    modifierType = ModifierType.FLAT,
                    currentAmount = 4,
                    candidateAmount = 4,
                    difference = 0,
                ),
                EquipmentStatComparison(
                    target = StatTarget.Derived(DerivedStatType.HP_RECOVERY),
                    modifierType = ModifierType.FLAT,
                    currentAmount = 0,
                    candidateAmount = 2,
                    difference = 2,
                ),
            ),
            EquipmentComparisonCalculator.compare(candidate = candidate, current = current),
        )
    }

    @Test
    fun comparisonRejectsDifferentSlotsSoChestAndLegsNeverShareABaseline() {
        val chest = equipment(id = 1)
        val legs = equipment(id = 2, type = EquipmentType.LEGS, slot = EquipmentSlot.LEGS)

        assertThrows(IllegalArgumentException::class.java) {
            EquipmentComparisonCalculator.compare(candidate = chest, current = legs)
        }
    }

    private fun equipment(
        id: Long,
        type: EquipmentType = EquipmentType.CHEST,
        slot: EquipmentSlot = EquipmentSlot.CHEST,
        modifiers: List<EquipmentStatModifier> = emptyList(),
    ): Equipment = Equipment(
        id = id,
        nameKey = "equipment_$id",
        descriptionKey = "equipment_${id}_description",
        type = type,
        slot = slot,
        rarity = EquipmentRarity.RARE,
        price = 100,
        requiredLevel = 1,
        modifiers = modifiers,
        isForSale = true,
    )

    private fun modifier(
        itemId: Long,
        target: StatTarget,
        type: ModifierType,
        amount: Int,
    ): EquipmentStatModifier = EquipmentStatModifier(itemId, target, type, amount)
}
