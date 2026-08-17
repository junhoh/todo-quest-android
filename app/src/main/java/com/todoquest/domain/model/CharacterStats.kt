package com.todoquest.domain.model

data class PlayerCharacter(
    val id: Long,
    val totalXp: Long,
    val currentGold: Long,
    val baseStats: CharacterBaseStats,
    val unspentStatPoints: Int,
    val hasUsedFreeStatReset: Boolean,
) {
    init {
        require(totalXp >= 0) { "totalXp must not be negative" }
        require(currentGold >= 0) { "currentGold must not be negative" }
        require(unspentStatPoints >= 0) { "unspentStatPoints must not be negative" }
    }
}

data class CharacterBaseStats(
    val strength: Int,
    val vitality: Int,
    val focus: Int,
    val willpower: Int,
) {
    fun valueOf(type: StatType): Int = when (type) {
        StatType.STRENGTH -> strength
        StatType.VITALITY -> vitality
        StatType.FOCUS -> focus
        StatType.WILLPOWER -> willpower
    }
}

data class CharacterCurrentState(
    val characterId: Long,
    val currentHp: Int,
    val balanceVersion: Int,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(currentHp >= 0) { "currentHp must not be negative" }
        require(balanceVersion > 0) { "balanceVersion must be positive" }
    }
}

data class DerivedStats(
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val criticalChanceBp: Int,
    val criticalDamageBp: Int,
    val statusResistanceBp: Int,
    val hpRecovery: Int,
    val goldGainBonusBp: Int,
)

enum class StatType {
    STRENGTH,
    VITALITY,
    FOCUS,
    WILLPOWER,
}

data class StatAllocation(
    val strength: Int = 0,
    val vitality: Int = 0,
    val focus: Int = 0,
    val willpower: Int = 0,
) {
    val totalPoints: Int

    init {
        require(strength >= 0) { "strength allocation must not be negative" }
        require(vitality >= 0) { "vitality allocation must not be negative" }
        require(focus >= 0) { "focus allocation must not be negative" }
        require(willpower >= 0) { "willpower allocation must not be negative" }
        totalPoints = Math.addExact(
            Math.addExact(strength, vitality),
            Math.addExact(focus, willpower),
        )
    }

    fun valueOf(type: StatType): Int = when (type) {
        StatType.STRENGTH -> strength
        StatType.VITALITY -> vitality
        StatType.FOCUS -> focus
        StatType.WILLPOWER -> willpower
    }
}

enum class DerivedStatType {
    MAX_HP,
    ATTACK,
    DEFENSE,
    CRITICAL_CHANCE,
    CRITICAL_DAMAGE,
    STATUS_RESISTANCE,
    HP_RECOVERY,
    GOLD_GAIN_BONUS,
}

sealed interface StatTarget {
    data class Base(val type: StatType) : StatTarget

    data class Derived(val type: DerivedStatType) : StatTarget
}

enum class ModifierType {
    FLAT,
    PERCENT_ADD,
}

data class TemporaryStatEffect(
    val effectId: Long,
    val target: StatTarget,
    val type: ModifierType,
    val amount: Int,
    val stackingKey: String,
    val startedAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val remainingTriggers: Int?,
) {
    init {
        require(stackingKey.isNotBlank()) { "stackingKey must not be blank" }
        require(endsAtEpochMillis != null || remainingTriggers != null) {
            "at least one of endsAtEpochMillis and remainingTriggers must be set"
        }
        endsAtEpochMillis?.let {
            require(it > startedAtEpochMillis) { "endsAtEpochMillis must be after the start" }
        }
        remainingTriggers?.let {
            require(it > 0) { "remainingTriggers must be positive" }
        }
    }
}

data class StatCalculationInput(
    val level: Int,
    val baseStats: CharacterBaseStats,
    val equipmentModifiers: List<EquipmentStatModifier> = emptyList(),
    val passiveAndSetModifiers: List<EquipmentStatModifier> = emptyList(),
    val temporaryEffects: List<TemporaryStatEffect> = emptyList(),
)

data class IntValueRange(
    val min: Int,
    val max: Int,
) {
    init {
        require(min <= max) { "range min must not exceed max" }
    }

    operator fun contains(value: Int): Boolean = value in min..max
}

data class EquipmentSlotRule(
    val allowedBaseStats: Set<StatType>,
    val allowedFlatDerivedStats: Set<DerivedStatType>,
    val allowedPercentDerivedStats: Set<DerivedStatType>,
)

data class EquipmentRarityRule(
    val affixCount: Int,
    val baseStatRange: IntValueRange,
    val attackRange: IntValueRange,
    val maxHpRange: IntValueRange,
    val defenseRange: IntValueRange,
    val hpRecoveryRange: IntValueRange,
    val probabilityPointRangeBp: IntValueRange,
    val fixedPercentRangeBp: IntValueRange,
)

@Suppress("LongParameterList")
class CharacterStatBalanceConfig(
    val version: Int = 1,
    val basisPointScale: Int = 10_000,
    val levelMin: Int = 1,
    val levelMax: Int = 50,
    val xpPerLevel: Long = 100,
    val statPointsPerLevel: Int = 2,
    val initialBaseStat: Int = 5,
    val investedBaseStatMin: Int = 1,
    val investedBaseStatMax: Int = 60,
    val effectiveBaseStatMax: Int = 99,
    val statResetBaseCost: Long = 100,
    val statResetCostPerLevel: Long = 20,
    val statResetMaxCost: Long = 2_000,
    val onTimeRewardMultiplierBp: Int = 11_000,
    val lateRewardMultiplierBp: Int = 10_000,
    val fullRewardEfficiencyBp: Int = 10_000,
    val reducedRewardEfficiencyBp: Int = 5_000,
    val minimumRewardEfficiencyBp: Int = 2_000,
    val recurringFullRewardThrough: Int = 3,
    val recurringReducedRewardThrough: Int = 6,
    val dailyFullRewardThrough: Int = 20,
    val dailyReducedRewardThrough: Int = 30,
    val momentumThreeDayBonusBp: Int = 300,
    val momentumSevenDayBonusBp: Int = 500,
    val momentumFourteenDayBonusBp: Int = 800,
    val maxModifiersPerSource: Int = 64,
    val minModifierAmount: Int = -10_000,
    val maxModifierAmount: Int = 10_000,
    val equipmentModifierMinAmount: Int = 0,
    val maxHpBase: Int = 60,
    val maxHpPerLevel: Int = 6,
    val maxHpPerVitality: Int = 10,
    val attackBase: Int = 5,
    val attackPerLevel: Int = 1,
    val attackPerStrength: Int = 2,
    val attackPerFocus: Int = 1,
    val defenseBase: Int = 3,
    val defenseLevelDivisor: Int = 2,
    val defensePerVitality: Int = 1,
    val criticalChanceBaseBp: Int = 500,
    val criticalChancePerFocusBp: Int = 50,
    val criticalDamageBaseBp: Int = 15_000,
    val criticalDamagePerStrengthBp: Int = 50,
    val statusResistancePerWillpowerBp: Int = 75,
    val hpRecoveryBase: Int = 2,
    val hpRecoveryLevelDivisor: Int = 5,
    val hpRecoveryPerWillpower: Int = 1,
    val maxHpMin: Int = 1,
    val maxHpMax: Int = 9_999,
    val attackMin: Int = 1,
    val attackMax: Int = 2_000,
    val defenseMin: Int = 0,
    val defenseMax: Int = 500,
    val criticalChanceMinBp: Int = 0,
    val criticalChanceMaxBp: Int = 5_000,
    val criticalDamageMinBp: Int = 10_000,
    val criticalDamageMaxBp: Int = 25_000,
    val statusResistanceMinBp: Int = 0,
    val statusResistanceMaxBp: Int = 7_500,
    val hpRecoveryMin: Int = 0,
    val hpRecoveryAbsoluteMax: Int = 999,
    val hpRecoveryMaxHpRatioBp: Int = 3_000,
    val goldGainBonusMinBp: Int = 0,
    val goldGainBonusMaxBp: Int = 5_000,
    val equipmentPercentMinBp: Int = 0,
    val equipmentPercentMaxBp: Int = 5_000,
    val passivePercentMinBp: Int = -5_000,
    val passivePercentMaxBp: Int = 3_000,
    val temporaryPercentMinBp: Int = -5_000,
    val temporaryPercentMaxBp: Int = 3_000,
    val defenseConstant: Int = 100,
    val minimumDamageRateBp: Int = 1_000,
    val minimumDamageFloor: Int = 1,
    val maxCombatRawDamage: Int = 5_000_000,
    val statusChanceMinBp: Int = 500,
    val statusChanceMaxBp: Int = 9_500,
    val probabilityRollMin: Int = 0,
    val probabilityRollMax: Int = 9_999,
    equipmentSlotRules: Map<EquipmentSlot, EquipmentSlotRule> = defaultEquipmentSlotRules(),
    equipmentRarityRules: Map<EquipmentRarity, EquipmentRarityRule> = defaultEquipmentRarityRules(),
) {
    val equipmentSlotRules: Map<EquipmentSlot, EquipmentSlotRule> = equipmentSlotRules.mapValues { (_, rule) ->
        rule.copy(
            allowedBaseStats = rule.allowedBaseStats.toSet(),
            allowedFlatDerivedStats = rule.allowedFlatDerivedStats.toSet(),
            allowedPercentDerivedStats = rule.allowedPercentDerivedStats.toSet(),
        )
    }.toMap()

    val equipmentRarityRules: Map<EquipmentRarity, EquipmentRarityRule> =
        equipmentRarityRules.toMap()

    init {
        require(version > 0) { "version must be positive" }
        require(basisPointScale > 0) { "basisPointScale must be positive" }
        require(levelMin > 0 && levelMin <= levelMax) { "invalid level range" }
        require(xpPerLevel > 0) { "xpPerLevel must be positive" }
        require(statPointsPerLevel > 0) { "statPointsPerLevel must be positive" }
        require(initialBaseStat in investedBaseStatMin..investedBaseStatMax) {
            "initialBaseStat must be within the invested base stat range"
        }
        require(investedBaseStatMin > 0 && investedBaseStatMin <= investedBaseStatMax) {
            "invalid invested base stat range"
        }
        require(effectiveBaseStatMax >= investedBaseStatMax) { "invalid effective base stat max" }
        require(statResetBaseCost >= 0 && statResetCostPerLevel >= 0 && statResetMaxCost >= 0) {
            "stat reset costs must not be negative"
        }
        require(
            minimumRewardEfficiencyBp in 1..reducedRewardEfficiencyBp &&
                reducedRewardEfficiencyBp <= fullRewardEfficiencyBp,
        ) { "invalid reward efficiency tiers" }
        require(lateRewardMultiplierBp > 0 && onTimeRewardMultiplierBp >= lateRewardMultiplierBp) {
            "invalid on-time reward multipliers"
        }
        require(
            recurringFullRewardThrough > 0 &&
                recurringReducedRewardThrough >= recurringFullRewardThrough &&
                dailyFullRewardThrough > 0 &&
                dailyReducedRewardThrough >= dailyFullRewardThrough,
        ) { "invalid reward sequence boundaries" }
        require(
            momentumThreeDayBonusBp >= 0 &&
                momentumSevenDayBonusBp >= momentumThreeDayBonusBp &&
                momentumFourteenDayBonusBp >= momentumSevenDayBonusBp,
        ) { "invalid momentum bonus tiers" }
        require(maxModifiersPerSource > 0) { "maxModifiersPerSource must be positive" }
        require(minModifierAmount <= equipmentModifierMinAmount && equipmentModifierMinAmount <= maxModifierAmount) {
            "invalid modifier range"
        }
        require(defenseLevelDivisor > 0 && hpRecoveryLevelDivisor > 0) {
            "formula divisors must be positive"
        }
        require(defenseConstant > 0) { "defenseConstant must be positive" }
        require(equipmentSlotRules.keys == EquipmentSlot.entries.toSet()) {
            "every equipment slot must have a rule"
        }
        require(equipmentRarityRules.keys == EquipmentRarity.entries.toSet()) {
            "every equipment rarity must have a rule"
        }
    }

    companion object {
        private fun defaultEquipmentSlotRules(): Map<EquipmentSlot, EquipmentSlotRule> = mapOf(
            EquipmentSlot.HELMET to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.FOCUS, StatType.WILLPOWER),
                allowedFlatDerivedStats = setOf(
                    DerivedStatType.MAX_HP,
                    DerivedStatType.DEFENSE,
                    DerivedStatType.CRITICAL_CHANCE,
                    DerivedStatType.STATUS_RESISTANCE,
                ),
                allowedPercentDerivedStats = emptySet(),
            ),
            EquipmentSlot.CHEST to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.VITALITY),
                allowedFlatDerivedStats = setOf(DerivedStatType.MAX_HP, DerivedStatType.DEFENSE),
                allowedPercentDerivedStats = setOf(DerivedStatType.MAX_HP, DerivedStatType.DEFENSE),
            ),
            EquipmentSlot.LEGS to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.VITALITY, StatType.WILLPOWER),
                allowedFlatDerivedStats = setOf(DerivedStatType.DEFENSE, DerivedStatType.HP_RECOVERY),
                allowedPercentDerivedStats = setOf(DerivedStatType.DEFENSE, DerivedStatType.HP_RECOVERY),
            ),
            EquipmentSlot.GLOVES to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.STRENGTH, StatType.FOCUS),
                allowedFlatDerivedStats = setOf(
                    DerivedStatType.CRITICAL_CHANCE,
                    DerivedStatType.CRITICAL_DAMAGE,
                ),
                allowedPercentDerivedStats = emptySet(),
            ),
            EquipmentSlot.SHOES to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.FOCUS, StatType.WILLPOWER),
                allowedFlatDerivedStats = setOf(
                    DerivedStatType.DEFENSE,
                    DerivedStatType.HP_RECOVERY,
                    DerivedStatType.STATUS_RESISTANCE,
                ),
                allowedPercentDerivedStats = setOf(DerivedStatType.DEFENSE, DerivedStatType.HP_RECOVERY),
            ),
            EquipmentSlot.ACCESSORY to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.FOCUS, StatType.WILLPOWER),
                allowedFlatDerivedStats = setOf(
                    DerivedStatType.CRITICAL_CHANCE,
                    DerivedStatType.CRITICAL_DAMAGE,
                    DerivedStatType.GOLD_GAIN_BONUS,
                ),
                allowedPercentDerivedStats = emptySet(),
            ),
            EquipmentSlot.WEAPON to EquipmentSlotRule(
                allowedBaseStats = setOf(StatType.STRENGTH, StatType.FOCUS),
                allowedFlatDerivedStats = setOf(DerivedStatType.ATTACK, DerivedStatType.CRITICAL_DAMAGE),
                allowedPercentDerivedStats = setOf(DerivedStatType.ATTACK),
            ),
        )

        private fun defaultEquipmentRarityRules(): Map<EquipmentRarity, EquipmentRarityRule> = mapOf(
            EquipmentRarity.COMMON to rarityRule(1, 0, 1, 2, 4, 8, 15, 1, 2, 1, 1, 50, 150, 200, 400),
            EquipmentRarity.UNCOMMON to rarityRule(2, 1, 2, 4, 7, 15, 25, 2, 4, 1, 2, 100, 250, 400, 600),
            EquipmentRarity.RARE to rarityRule(3, 2, 4, 7, 12, 25, 40, 4, 7, 2, 4, 200, 400, 600, 1_000),
            EquipmentRarity.EPIC to rarityRule(4, 3, 6, 12, 20, 40, 65, 7, 11, 4, 7, 400, 700, 1_000, 1_500),
            EquipmentRarity.LEGENDARY to rarityRule(4, 4, 8, 20, 30, 65, 100, 11, 16, 7, 10, 700, 1_000, 1_500, 2_000),
        )

        @Suppress("LongParameterList")
        private fun rarityRule(
            affixCount: Int,
            baseMin: Int,
            baseMax: Int,
            attackMin: Int,
            attackMax: Int,
            hpMin: Int,
            hpMax: Int,
            defenseMin: Int,
            defenseMax: Int,
            recoveryMin: Int,
            recoveryMax: Int,
            pointMin: Int,
            pointMax: Int,
            percentMin: Int,
            percentMax: Int,
        ): EquipmentRarityRule = EquipmentRarityRule(
            affixCount = affixCount,
            baseStatRange = IntValueRange(baseMin, baseMax),
            attackRange = IntValueRange(attackMin, attackMax),
            maxHpRange = IntValueRange(hpMin, hpMax),
            defenseRange = IntValueRange(defenseMin, defenseMax),
            hpRecoveryRange = IntValueRange(recoveryMin, recoveryMax),
            probabilityPointRangeBp = IntValueRange(pointMin, pointMax),
            fixedPercentRangeBp = IntValueRange(percentMin, percentMax),
        )
    }
}
