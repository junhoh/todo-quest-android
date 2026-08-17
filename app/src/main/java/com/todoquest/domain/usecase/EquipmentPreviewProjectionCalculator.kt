package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentPreviewProjection
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.StatCalculationInput

object EquipmentPreviewProjectionCalculator {
    fun calculate(
        candidate: Equipment,
        equippedBySlot: Map<EquipmentSlot, EquippedEquipment>,
        renderedEquippedItems: EquippedItems,
        statCalculationInput: StatCalculationInput,
        config: CharacterStatBalanceConfig,
    ): EquipmentPreviewProjection {
        if (!EquipmentTypeSlotPolicy.isCompatible(candidate.type, candidate.slot)) {
            return EquipmentPreviewProjection(
                renderedEquippedItems = renderedEquippedItems,
                derivedStats = DerivedStatsCalculator.calculate(statCalculationInput, config),
            )
        }

        val currentEquipmentId = equippedBySlot[candidate.slot]?.ownedEquipment?.equipmentId
        val previewStatInput = statCalculationInput.copy(
            equipmentModifiers = statCalculationInput.equipmentModifiers
                .filterNot { modifier -> modifier.itemId == currentEquipmentId } + candidate.modifiers,
        )
        return EquipmentPreviewProjection(
            renderedEquippedItems = previewRenderedItems(candidate, renderedEquippedItems),
            derivedStats = DerivedStatsCalculator.calculate(previewStatInput, config),
        )
    }

    private fun previewRenderedItems(
        candidate: Equipment,
        current: EquippedItems,
    ): EquippedItems {
        val layerId = candidate.layerKey ?: return current
        val preview = when (candidate.slot) {
            EquipmentSlot.WEAPON -> current.copy(weaponId = layerId)
            EquipmentSlot.HELMET -> current.copy(headId = layerId)
            EquipmentSlot.CHEST -> current.copy(topId = layerId)
            EquipmentSlot.LEGS -> current.copy(bottomId = layerId)
            EquipmentSlot.GLOVES -> current.copy(glovesId = layerId)
            EquipmentSlot.SHOES -> current.copy(shoesId = layerId)
            EquipmentSlot.ACCESSORY -> current.copy(accessoryId = layerId)
        }
        return preview.takeIf(CharacterLoadoutCatalog::contains) ?: current
    }
}
