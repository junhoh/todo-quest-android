package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatBalanceConfig

object CombatCalculator {
    fun normalDamage(
        attack: Int,
        defense: Int,
        config: CharacterStatBalanceConfig,
    ): Int {
        require(attack in config.attackMin..config.attackMax) { "attack is outside the configured range" }
        return damageAfterDefense(attack, defense, config)
    }

    fun criticalRawDamage(
        attack: Int,
        criticalDamageBp: Int,
        config: CharacterStatBalanceConfig,
    ): Int {
        require(attack in config.attackMin..config.attackMax) { "attack is outside the configured range" }
        require(criticalDamageBp in config.criticalDamageMinBp..config.criticalDamageMaxBp) {
            "criticalDamageBp is outside the configured range"
        }
        return Math.multiplyExact(attack.toLong(), criticalDamageBp.toLong())
            .div(config.basisPointScale)
            .toInt()
    }

    fun criticalDamage(
        attack: Int,
        criticalDamageBp: Int,
        defense: Int,
        config: CharacterStatBalanceConfig,
    ): Int = damageAfterDefense(
        criticalRawDamage(attack, criticalDamageBp, config),
        defense,
        config,
    )

    fun damageAfterDefense(
        rawDamage: Int,
        defense: Int,
        config: CharacterStatBalanceConfig,
    ): Int {
        require(rawDamage in 1..config.maxCombatRawDamage) { "rawDamage is outside the configured range" }
        require(defense in config.defenseMin..config.defenseMax) { "defense is outside the configured range" }
        val reducedDamage = Math.multiplyExact(rawDamage.toLong(), config.defenseConstant.toLong())
            .div(defense.toLong() + config.defenseConstant)
            .toInt()
        val minimumDamage = maxOf(
            config.minimumDamageFloor,
            Math.multiplyExact(rawDamage.toLong(), config.minimumDamageRateBp.toLong())
                .div(config.basisPointScale)
                .toInt(),
        )
        return maxOf(minimumDamage, reducedDamage)
    }

    @Suppress("LongParameterList")
    fun statusApplicationChanceBp(
        effectBaseBp: Int,
        sourceEquipmentBonusBp: Int,
        sourcePassiveBonusBp: Int,
        sourceTemporaryBonusBp: Int,
        targetResistanceBp: Int,
        isImmune: Boolean,
        config: CharacterStatBalanceConfig,
    ): Int {
        if (isImmune) return 0
        require(effectBaseBp in 0..config.basisPointScale) { "effectBaseBp is outside the probability range" }
        listOf(sourceEquipmentBonusBp, sourcePassiveBonusBp, sourceTemporaryBonusBp).forEach { bonus ->
            require(bonus in config.minModifierAmount..config.maxModifierAmount) {
                "status source bonus is outside the configured source range"
            }
        }
        require(targetResistanceBp in config.statusResistanceMinBp..config.statusResistanceMaxBp) {
            "targetResistanceBp is outside the configured range"
        }
        val chance = effectBaseBp.toLong() +
            sourceEquipmentBonusBp +
            sourcePassiveBonusBp +
            sourceTemporaryBonusBp -
            targetResistanceBp
        return chance.coerceIn(config.statusChanceMinBp.toLong(), config.statusChanceMaxBp.toLong()).toInt()
    }

    fun rollSucceeds(
        chanceBp: Int,
        roll: Int,
        config: CharacterStatBalanceConfig,
    ): Boolean {
        require(chanceBp in 0..config.basisPointScale) { "chanceBp is outside the probability range" }
        require(roll in config.probabilityRollMin..config.probabilityRollMax) {
            "roll is outside the configured integer roll range"
        }
        return roll < chanceBp
    }

    fun preserveHpRatio(
        oldHp: Int,
        oldMax: Int,
        newMax: Int,
        config: CharacterStatBalanceConfig,
    ): Int {
        require(oldMax in config.maxHpMin..config.maxHpMax) { "oldMax is outside the configured range" }
        require(newMax in config.maxHpMin..config.maxHpMax) { "newMax is outside the configured range" }
        require(oldHp in 0..oldMax) { "oldHp must be within 0..oldMax" }
        if (oldHp == 0) return 0
        return Math.multiplyExact(oldHp.toLong(), newMax.toLong())
            .div(oldMax)
            .coerceIn(config.maxHpMin.toLong(), newMax.toLong())
            .toInt()
    }
}
