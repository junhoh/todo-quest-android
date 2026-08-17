package com.todoquest.domain.usecase

import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.PurchaseEligibility
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.model.toEquipmentSlot
import com.todoquest.domain.repository.EquipmentRepository

object EquipmentTypeSlotPolicy {
    fun evaluate(type: EquipmentType, slot: EquipmentSlot): EquipmentTypeSlotMatch =
        if (type.toEquipmentSlot() == slot) {
            EquipmentTypeSlotMatch.Compatible
        } else {
            EquipmentTypeSlotMatch.Mismatch(type, slot)
        }

    fun isCompatible(type: EquipmentType, slot: EquipmentSlot): Boolean =
        evaluate(type, slot) == EquipmentTypeSlotMatch.Compatible
}

sealed interface EquipmentTypeSlotMatch {
    data object Compatible : EquipmentTypeSlotMatch

    data class Mismatch(
        val type: EquipmentType,
        val slot: EquipmentSlot,
    ) : EquipmentTypeSlotMatch
}

object PurchaseEquipmentPolicy {
    fun evaluate(
        equipment: Equipment,
        characterLevel: Int,
        availableGold: Long,
        isOwned: Boolean,
    ): PurchaseEligibility {
        require(characterLevel > 0) { "characterLevel must be positive" }
        require(availableGold >= 0) { "availableGold must not be negative" }

        return when {
            !EquipmentTypeSlotPolicy.isCompatible(equipment.type, equipment.slot) ->
                PurchaseEligibility.UnsupportedSlot(equipment.id, equipment.type, equipment.slot)

            !equipment.isForSale -> PurchaseEligibility.NotForSale(equipment.id)
            isOwned -> PurchaseEligibility.AlreadyOwned(equipment.id)
            characterLevel < equipment.requiredLevel -> PurchaseEligibility.LevelTooLow(
                equipmentId = equipment.id,
                requiredLevel = equipment.requiredLevel,
                characterLevel = characterLevel,
            )

            availableGold < equipment.price -> PurchaseEligibility.InsufficientGold(
                equipmentId = equipment.id,
                price = equipment.price,
                availableGold = availableGold,
            )

            else -> PurchaseEligibility.Eligible
        }
    }
}

object EquipmentComparisonCalculator {
    fun compare(
        candidate: Equipment,
        current: Equipment?,
    ): List<EquipmentStatComparison> {
        require(current == null || candidate.slot == current.slot) {
            "equipment comparison requires the same slot"
        }

        val candidateAmounts = candidate.modifiers.amountsByKey()
        val currentAmounts = current?.modifiers?.amountsByKey().orEmpty()
        return (candidateAmounts.keys + currentAmounts.keys)
            .distinct()
            .sortedWith(modifierKeyComparator)
            .map { key ->
                val candidateAmount = candidateAmounts[key] ?: 0
                val currentAmount = currentAmounts[key] ?: 0
                EquipmentStatComparison(
                    target = key.target,
                    modifierType = key.type,
                    currentAmount = currentAmount,
                    candidateAmount = candidateAmount,
                    difference = Math.subtractExact(candidateAmount, currentAmount),
                )
            }
    }

    private data class ModifierKey(
        val target: StatTarget,
        val type: ModifierType,
    )

    private val modifierKeyComparator = compareBy<ModifierKey>(
        { targetGroup(it.target) },
        { targetOrder(it.target) },
        { it.type.ordinal },
    )

    private fun targetGroup(target: StatTarget): Int = when (target) {
        is StatTarget.Base -> 0
        is StatTarget.Derived -> 1
    }

    private fun targetOrder(target: StatTarget): Int = when (target) {
        is StatTarget.Base -> target.type.ordinal
        is StatTarget.Derived -> target.type.ordinal
    }

    private fun List<EquipmentStatModifier>.amountsByKey(): Map<ModifierKey, Int> =
        groupBy { ModifierKey(it.target, it.type) }
            .mapValues { (_, modifiers) ->
                modifiers.fold(0) { total, modifier -> Math.addExact(total, modifier.amount) }
            }
}

class PurchaseEquipmentUseCase(
    private val repository: EquipmentRepository,
) {
    suspend operator fun invoke(
        characterId: Long,
        equipmentId: Long,
    ): PurchaseEquipmentResult = repository.purchaseEquipment(characterId, equipmentId)
}

class EquipOwnedEquipmentUseCase(
    private val repository: EquipmentRepository,
) {
    suspend operator fun invoke(
        characterId: Long,
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
    ): EquipOwnedEquipmentResult = repository.equipOwnedEquipment(
        characterId = characterId,
        ownedEquipmentId = ownedEquipmentId,
        targetSlot = targetSlot,
    )
}

class UnequipEquipmentUseCase(
    private val repository: EquipmentRepository,
) {
    suspend operator fun invoke(
        characterId: Long,
        targetSlot: EquipmentSlot,
    ): UnequipEquipmentResult = repository.unequipEquipment(
        characterId = characterId,
        targetSlot = targetSlot,
    )
}
