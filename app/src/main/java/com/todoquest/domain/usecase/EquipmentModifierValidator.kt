package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentRarityRule
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.IntValueRange
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget

object EquipmentModifierValidator {
    private val fixedDerivedStats = setOf(
        DerivedStatType.MAX_HP,
        DerivedStatType.ATTACK,
        DerivedStatType.DEFENSE,
        DerivedStatType.HP_RECOVERY,
    )

    fun validate(
        slot: EquipmentSlot,
        rarity: EquipmentRarity,
        modifiers: List<EquipmentStatModifier>,
        config: CharacterStatBalanceConfig,
    ) {
        val rarityRule = config.equipmentRarityRules.getValue(rarity)
        require(modifiers.size == rarityRule.affixCount) {
            "$rarity requires exactly ${rarityRule.affixCount} numeric affixes"
        }
        require(modifiers.map { it.itemId }.distinct().size <= 1) {
            "all modifiers must belong to the same item"
        }

        val slotRule = config.equipmentSlotRules.getValue(slot)
        modifiers.forEach { validateModifier(slot, rarity, it, config) }
    }

    fun validateModifier(
        slot: EquipmentSlot,
        rarity: EquipmentRarity,
        modifier: EquipmentStatModifier,
        config: CharacterStatBalanceConfig,
    ) {
        validateTargetAndType(modifier)
        val slotRule = config.equipmentSlotRules.getValue(slot)
        val rarityRule = config.equipmentRarityRules.getValue(rarity)

        val allowedRange = when (val target = modifier.target) {
            is StatTarget.Base -> {
                require(target.type in slotRule.allowedBaseStats) {
                    "${target.type} is not allowed on $slot"
                }
                rarityRule.baseStatRange
            }

            is StatTarget.Derived -> {
                when (modifier.type) {
                    ModifierType.FLAT -> {
                        require(target.type in slotRule.allowedFlatDerivedStats) {
                            "${target.type} flat modifier is not allowed on $slot"
                        }
                        flatRange(target.type, rarityRule)
                    }

                    ModifierType.PERCENT_ADD -> {
                        require(target.type in slotRule.allowedPercentDerivedStats) {
                            "${target.type} percent modifier is not allowed on $slot"
                        }
                        rarityRule.fixedPercentRangeBp
                    }
                }
            }
        }

        require(modifier.amount in allowedRange) {
            "modifier amount ${modifier.amount} is outside ${allowedRange.min}..${allowedRange.max}"
        }
    }

    fun validateTargetAndType(modifier: EquipmentStatModifier) {
        when (val target = modifier.target) {
            is StatTarget.Base -> require(modifier.type == ModifierType.FLAT) {
                "base stats only accept FLAT modifiers"
            }

            is StatTarget.Derived -> when {
                target.type in fixedDerivedStats -> Unit
                else -> require(modifier.type == ModifierType.FLAT) {
                    "bp stats only accept FLAT modifiers"
                }
            }
        }
    }

    private fun flatRange(
        type: DerivedStatType,
        rarityRule: EquipmentRarityRule,
    ): IntValueRange = when (type) {
        DerivedStatType.ATTACK -> rarityRule.attackRange
        DerivedStatType.MAX_HP -> rarityRule.maxHpRange
        DerivedStatType.DEFENSE -> rarityRule.defenseRange
        DerivedStatType.HP_RECOVERY -> rarityRule.hpRecoveryRange
        DerivedStatType.CRITICAL_CHANCE,
        DerivedStatType.CRITICAL_DAMAGE,
        DerivedStatType.STATUS_RESISTANCE,
        DerivedStatType.GOLD_GAIN_BONUS,
        -> rarityRule.probabilityPointRangeBp
    }
}
