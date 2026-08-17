package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.TemporaryStatEffect
import kotlin.math.abs

fun interface DerivedStatsCalculator {
    fun calculate(
        input: StatCalculationInput,
        config: CharacterStatBalanceConfig,
    ): DerivedStats

    companion object {
        fun calculate(
            input: StatCalculationInput,
            config: CharacterStatBalanceConfig,
        ): DerivedStats = DerivedStatsCalculation.calculate(input, config)
    }
}

private object DerivedStatsCalculation {
    private val fixedDerivedStats = setOf(
        DerivedStatType.MAX_HP,
        DerivedStatType.ATTACK,
        DerivedStatType.DEFENSE,
        DerivedStatType.HP_RECOVERY,
    )

    fun calculate(
        input: StatCalculationInput,
        config: CharacterStatBalanceConfig,
    ): DerivedStats {
        validateInput(input, config)
        val activeTemporaryEffects = strongestTemporaryEffects(input.temporaryEffects)
        val equipmentBase = effectiveEquipmentBaseBonuses(input.baseStats, input.equipmentModifiers, config)

        val maxHpBeforePercent =
            config.maxHpBase.toLong() +
                config.maxHpPerLevel.toLong() * (input.level - config.levelMin) +
                config.maxHpPerVitality.toLong() * input.baseStats.vitality +
                config.maxHpPerVitality.toLong() * equipmentBase.getValue(StatType.VITALITY) +
                equipmentFlat(input.equipmentModifiers, DerivedStatType.MAX_HP)
        val maxHp = fixedStat(
            maxHpBeforePercent,
            DerivedStatType.MAX_HP,
            input,
            activeTemporaryEffects,
            config,
            config.maxHpMin,
            config.maxHpMax,
        )

        val attackBeforePercent =
            config.attackBase.toLong() +
                config.attackPerLevel.toLong() * (input.level - config.levelMin) +
                config.attackPerStrength.toLong() * input.baseStats.strength +
                config.attackPerFocus.toLong() * input.baseStats.focus +
                config.attackPerStrength.toLong() * equipmentBase.getValue(StatType.STRENGTH) +
                config.attackPerFocus.toLong() * equipmentBase.getValue(StatType.FOCUS) +
                equipmentFlat(input.equipmentModifiers, DerivedStatType.ATTACK)
        val attack = fixedStat(
            attackBeforePercent,
            DerivedStatType.ATTACK,
            input,
            activeTemporaryEffects,
            config,
            config.attackMin,
            config.attackMax,
        )

        val defenseBeforePercent =
            config.defenseBase.toLong() +
                (input.level - config.levelMin) / config.defenseLevelDivisor +
                config.defensePerVitality.toLong() * input.baseStats.vitality +
                config.defensePerVitality.toLong() * equipmentBase.getValue(StatType.VITALITY) +
                equipmentFlat(input.equipmentModifiers, DerivedStatType.DEFENSE)
        val defense = fixedStat(
            defenseBeforePercent,
            DerivedStatType.DEFENSE,
            input,
            activeTemporaryEffects,
            config,
            config.defenseMin,
            config.defenseMax,
        )

        val recoveryBeforePercent =
            config.hpRecoveryBase.toLong() +
                (input.level - config.levelMin) / config.hpRecoveryLevelDivisor +
                config.hpRecoveryPerWillpower.toLong() * input.baseStats.willpower +
                config.hpRecoveryPerWillpower.toLong() * equipmentBase.getValue(StatType.WILLPOWER) +
                equipmentFlat(input.equipmentModifiers, DerivedStatType.HP_RECOVERY)
        val recoveryDynamicMax = minOf(
            config.hpRecoveryAbsoluteMax,
            Math.multiplyExact(maxHp.toLong(), config.hpRecoveryMaxHpRatioBp.toLong())
                .div(config.basisPointScale)
                .toInt(),
        )
        val hpRecovery = fixedStat(
            recoveryBeforePercent,
            DerivedStatType.HP_RECOVERY,
            input,
            activeTemporaryEffects,
            config,
            config.hpRecoveryMin,
            recoveryDynamicMax,
        )

        return DerivedStats(
            maxHp = maxHp,
            attack = attack,
            defense = defense,
            criticalChanceBp = bpStat(
                config.criticalChanceBaseBp.toLong() +
                    config.criticalChancePerFocusBp.toLong() * input.baseStats.focus +
                    config.criticalChancePerFocusBp.toLong() * equipmentBase.getValue(StatType.FOCUS),
                DerivedStatType.CRITICAL_CHANCE,
                input,
                activeTemporaryEffects,
                config.criticalChanceMinBp,
                config.criticalChanceMaxBp,
            ),
            criticalDamageBp = bpStat(
                config.criticalDamageBaseBp.toLong() +
                    config.criticalDamagePerStrengthBp.toLong() * input.baseStats.strength +
                    config.criticalDamagePerStrengthBp.toLong() * equipmentBase.getValue(StatType.STRENGTH),
                DerivedStatType.CRITICAL_DAMAGE,
                input,
                activeTemporaryEffects,
                config.criticalDamageMinBp,
                config.criticalDamageMaxBp,
            ),
            statusResistanceBp = bpStat(
                config.statusResistancePerWillpowerBp.toLong() * input.baseStats.willpower +
                    config.statusResistancePerWillpowerBp.toLong() * equipmentBase.getValue(StatType.WILLPOWER),
                DerivedStatType.STATUS_RESISTANCE,
                input,
                activeTemporaryEffects,
                config.statusResistanceMinBp,
                config.statusResistanceMaxBp,
            ),
            hpRecovery = hpRecovery,
            goldGainBonusBp = bpStat(
                0,
                DerivedStatType.GOLD_GAIN_BONUS,
                input,
                activeTemporaryEffects,
                config.goldGainBonusMinBp,
                config.goldGainBonusMaxBp,
            ),
        )
    }

    private fun validateInput(input: StatCalculationInput, config: CharacterStatBalanceConfig) {
        require(input.level in config.levelMin..config.levelMax) { "level is outside the configured range" }
        StatType.entries.forEach { type ->
            require(input.baseStats.valueOf(type) in config.investedBaseStatMin..config.investedBaseStatMax) {
                "$type is outside the invested base stat range"
            }
        }
        require(input.equipmentModifiers.size <= config.maxModifiersPerSource) {
            "equipment modifier source exceeds its configured limit"
        }
        require(input.passiveAndSetModifiers.size <= config.maxModifiersPerSource) {
            "passive modifier source exceeds its configured limit"
        }
        require(input.temporaryEffects.size <= config.maxModifiersPerSource) {
            "temporary effect source exceeds its configured limit"
        }

        input.equipmentModifiers.forEach { modifier ->
            EquipmentModifierValidator.validateTargetAndType(modifier)
            require(modifier.amount in config.equipmentModifierMinAmount..config.maxModifierAmount) {
                "equipment modifier amount is outside the configured source range"
            }
        }
        input.passiveAndSetModifiers.forEach { modifier ->
            validateNonEquipmentModifier(modifier.target, modifier.type)
            require(modifier.amount in config.minModifierAmount..config.maxModifierAmount) {
                "passive modifier amount is outside the configured source range"
            }
        }
        input.temporaryEffects.forEach { effect ->
            validateNonEquipmentModifier(effect.target, effect.type)
            require(effect.amount in config.minModifierAmount..config.maxModifierAmount) {
                "temporary modifier amount is outside the configured source range"
            }
        }
    }

    private fun validateNonEquipmentModifier(target: StatTarget, type: ModifierType) {
        require(target is StatTarget.Derived) { "v1 passive and temporary modifiers cannot target base stats" }
        if (target.type in fixedDerivedStats) {
            require(type == ModifierType.PERCENT_ADD) {
                "v1 passive and temporary fixed stat modifiers must use PERCENT_ADD"
            }
        } else {
            require(type == ModifierType.FLAT) {
                "v1 passive and temporary bp modifiers must use FLAT"
            }
        }
    }

    private fun strongestTemporaryEffects(effects: List<TemporaryStatEffect>): List<TemporaryStatEffect> =
        effects.groupBy { it.stackingKey }.values.map { sameKey ->
            sameKey.maxWithOrNull(compareBy<TemporaryStatEffect> { abs(it.amount) }.thenBy { it.effectId })!!
        }

    private fun effectiveEquipmentBaseBonuses(
        baseStats: CharacterBaseStats,
        equipment: List<EquipmentStatModifier>,
        config: CharacterStatBalanceConfig,
    ): Map<StatType, Long> = StatType.entries.associateWith { type ->
        val requested = equipment.sumOf { modifier ->
            if (modifier.target == StatTarget.Base(type)) modifier.amount.toLong() else 0L
        }
        minOf(requested, (config.effectiveBaseStatMax - baseStats.valueOf(type)).toLong())
    }

    private fun fixedStat(
        baseAndFlat: Long,
        target: DerivedStatType,
        input: StatCalculationInput,
        temporaryEffects: List<TemporaryStatEffect>,
        config: CharacterStatBalanceConfig,
        minValue: Int,
        maxValue: Int,
    ): Int {
        val equipmentPercent = bucketAmount(
            input.equipmentModifiers,
            target,
            ModifierType.PERCENT_ADD,
        ).coerceIn(config.equipmentPercentMinBp.toLong(), config.equipmentPercentMaxBp.toLong())
        val passivePercent = bucketAmount(
            input.passiveAndSetModifiers,
            target,
            ModifierType.PERCENT_ADD,
        ).coerceIn(config.passivePercentMinBp.toLong(), config.passivePercentMaxBp.toLong())
        val temporaryPercent = temporaryEffects.sumOf { effect ->
            if (effect.target == StatTarget.Derived(target) && effect.type == ModifierType.PERCENT_ADD) {
                effect.amount.toLong()
            } else {
                0L
            }
        }.coerceIn(config.temporaryPercentMinBp.toLong(), config.temporaryPercentMaxBp.toLong())

        val scale = config.basisPointScale.toLong()
        val numerator = Math.multiplyExact(
            Math.multiplyExact(
                Math.multiplyExact(baseAndFlat, scale + equipmentPercent),
                scale + passivePercent,
            ),
            scale + temporaryPercent,
        )
        val denominator = Math.multiplyExact(Math.multiplyExact(scale, scale), scale)
        return (numerator / denominator).coerceIn(minValue.toLong(), maxValue.toLong()).toInt()
    }

    private fun bpStat(
        baseValue: Long,
        target: DerivedStatType,
        input: StatCalculationInput,
        temporaryEffects: List<TemporaryStatEffect>,
        minValue: Int,
        maxValue: Int,
    ): Int {
        val total = baseValue +
            bucketAmount(input.equipmentModifiers, target, ModifierType.FLAT) +
            bucketAmount(input.passiveAndSetModifiers, target, ModifierType.FLAT) +
            temporaryEffects.sumOf { effect ->
                if (effect.target == StatTarget.Derived(target) && effect.type == ModifierType.FLAT) {
                    effect.amount.toLong()
                } else {
                    0L
                }
            }
        return total.coerceIn(minValue.toLong(), maxValue.toLong()).toInt()
    }

    private fun equipmentFlat(
        equipment: List<EquipmentStatModifier>,
        target: DerivedStatType,
    ): Long = bucketAmount(equipment, target, ModifierType.FLAT)

    private fun bucketAmount(
        modifiers: List<EquipmentStatModifier>,
        target: DerivedStatType,
        type: ModifierType,
    ): Long = modifiers.sumOf { modifier ->
        if (modifier.target == StatTarget.Derived(target) && modifier.type == type) {
            modifier.amount.toLong()
        } else {
            0L
        }
    }
}
