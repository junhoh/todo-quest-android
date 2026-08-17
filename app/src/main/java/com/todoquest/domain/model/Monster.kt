package com.todoquest.domain.model

import java.util.Collections

enum class MonsterType {
    BALANCED,
    ATTACK,
    DEFENSE,
    BOSS,
}

enum class MonsterGrade {
    NORMAL,
    ELITE,
    BOSS,
}

enum class MonsterSpecies {
    GOBLIN_SCOUT,
    SKELETON_SOLDIER,
    CORRUPTED_TREE_SPIRIT,
    HARPY,
    SLIME,
}

enum class CombatEventStatus {
    PENDING,
    APPLIED,
    SKIPPED,
}

enum class MonsterAttackSkipReason {
    SKIPPED_RECONCILIATION_CAP,
    NORMALIZED_LEGACY_ZERO_HP,
}

data class MonsterDefinition(
    val id: String,
    val nameKey: String,
    val type: MonsterType,
    val baseMaxHp: Int,
    val baseDamage: Int,
    val baseDefense: Int,
    val hpGrowthPerLevel: Int,
    val damageGrowthPerLevel: Int,
    val defenseGrowthPerLevel: Int,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(nameKey.isNotBlank()) { "nameKey must not be blank" }
        require(baseMaxHp >= 1) { "baseMaxHp must be positive" }
        require(baseDamage >= 1) { "baseDamage must be positive" }
        require(baseDefense >= 0) { "baseDefense must not be negative" }
        require(hpGrowthPerLevel >= 0) { "hpGrowthPerLevel must not be negative" }
        require(damageGrowthPerLevel >= 0) { "damageGrowthPerLevel must not be negative" }
        require(defenseGrowthPerLevel >= 0) { "defenseGrowthPerLevel must not be negative" }
    }
}

data class MonsterInstance(
    val id: Long,
    val definitionId: String,
    val grade: MonsterGrade,
    val stageNumber: Int,
    val encounterNumber: Int,
    val level: Int,
    val currentHp: Int,
    val balanceVersion: Int,
) {
    val isDefeated: Boolean get() = currentHp == 0

    init {
        require(definitionId.isNotBlank()) { "definitionId must not be blank" }
        require(stageNumber > 0) { "stageNumber must be positive" }
        require(encounterNumber > 0) { "encounterNumber must be positive" }
        require(level > 0) { "level must be positive" }
        require(currentHp >= 0) { "currentHp must not be negative" }
        require(balanceVersion > 0) { "balanceVersion must be positive" }
    }
}

data class MonsterStats(
    val maxHp: Int,
    val damage: Int,
    val defense: Int,
) {
    init {
        require(maxHp >= 1) { "maxHp must be positive" }
        require(damage >= 1) { "damage must be positive" }
        require(defense >= 0) { "defense must not be negative" }
    }
}

data class MonsterStatMultipliersBp(
    val maxHp: Int,
    val damage: Int,
    val defense: Int,
)

data class CombatRewardBalanceConfig(
    val version: Int,
    val hitXpBase: Long,
    val hitXpLevelBand: Int,
    val killBonusXpBase: Long,
    val killBonusXpLevelBand: Int,
    val killGoldBase: Long,
    val killGoldLevelBand: Int,
) {
    init {
        require(version > 0) { "version must be positive" }
        require(hitXpBase > 0L && killBonusXpBase > 0L && killGoldBase > 0L) {
            "combat reward bases must be positive"
        }
        require(
            hitXpLevelBand > 0 &&
                killBonusXpLevelBand > 0 &&
                killGoldLevelBand > 0,
        ) {
            "combat reward level bands must be positive"
        }
    }
}

object CombatRewardBalanceCatalog {
    const val CURRENT_VERSION: Int = 2

    private val configs = mapOf(
        1 to CombatRewardBalanceConfig(
            version = 1,
            hitXpBase = 1L,
            hitXpLevelBand = 10,
            killBonusXpBase = 10L,
            killBonusXpLevelBand = 5,
            killGoldBase = 5L,
            killGoldLevelBand = 10,
        ),
        2 to CombatRewardBalanceConfig(
            version = 2,
            hitXpBase = 3L,
            hitXpLevelBand = 10,
            killBonusXpBase = 20L,
            killBonusXpLevelBand = 5,
            killGoldBase = 15L,
            killGoldLevelBand = 10,
        ),
    )

    fun configFor(version: Int): CombatRewardBalanceConfig =
        requireNotNull(configs[version]) { "Unknown combat reward version: $version" }

    fun supports(version: Int): Boolean = configs.containsKey(version)
}

@Suppress("LongParameterList")
class MonsterBalanceConfig(
    val version: Int = 1,
    val basisPointScale: Int = 10_000,
    val monsterLevelMin: Int = 1,
    val monsterLevelMax: Int = 55,
    val stageLevelMin: Int = 1,
    val stageLevelMax: Int = 50,
    val definitionBaseMaxHpRange: IntValueRange = IntValueRange(1, 9_999),
    val definitionBaseDamageRange: IntValueRange = IntValueRange(1, 2_000),
    val definitionBaseDefenseRange: IntValueRange = IntValueRange(0, 200),
    val definitionHpGrowthRange: IntValueRange = IntValueRange(0, 9_999),
    val definitionDamageGrowthRange: IntValueRange = IntValueRange(0, 2_000),
    val definitionDefenseGrowthRange: IntValueRange = IntValueRange(0, 200),
    val finalMaxHpRange: IntValueRange = IntValueRange(1, 9_999),
    val finalDamageRange: IntValueRange = IntValueRange(1, 2_000),
    val finalDefenseRange: IntValueRange = IntValueRange(0, 200),
    val baseMaxHp: Int = 75,
    val baseDamage: Int = 12,
    val baseDefense: Int = 7,
    val hpGrowthPerLevel: Int = 5,
    val damageGrowthPerLevel: Int = 2,
    val defenseGrowthPerLevel: Int = 2,
    val multiplierBpMax: Int = 100_000,
    typeMultipliersBp: Map<MonsterType, MonsterStatMultipliersBp> = defaultTypeMultipliersBp(),
    gradeMultipliersBp: Map<MonsterGrade, MonsterStatMultipliersBp> = defaultGradeMultipliersBp(),
    val combinedMultiplierCapsBp: MonsterStatMultipliersBp = MonsterStatMultipliersBp(
        maxHp = 40_000,
        damage = 17_500,
        defense = 15_000,
    ),
    gradeLevelOffsets: Map<MonsterGrade, Int> = defaultGradeLevelOffsets(),
    val stageCount: Int = 10,
    val eliteStageNumber: Int = 5,
    val bossStageNumber: Int = 10,
    val normalEncountersPerStage: Int = 8,
    val specialEncountersPerStage: Int = 1,
    gradeRewardMultipliersBp: Map<MonsterGrade, Int> = defaultGradeRewardMultipliersBp(),
    val combatRewardVersion: Int = CombatRewardBalanceCatalog.CURRENT_VERSION,
) {
    val typeMultipliersBp: Map<MonsterType, MonsterStatMultipliersBp> =
        immutableCopy(typeMultipliersBp)

    val gradeMultipliersBp: Map<MonsterGrade, MonsterStatMultipliersBp> =
        immutableCopy(gradeMultipliersBp)

    val gradeLevelOffsets: Map<MonsterGrade, Int> = immutableCopy(gradeLevelOffsets)

    val gradeRewardMultipliersBp: Map<MonsterGrade, Int> =
        immutableCopy(gradeRewardMultipliersBp)

    init {
        require(version > 0) { "version must be positive" }
        require(basisPointScale > 0) { "basisPointScale must be positive" }
        require(monsterLevelMin > 0 && monsterLevelMin <= monsterLevelMax) {
            "invalid monster level range"
        }
        require(stageLevelMin >= monsterLevelMin && stageLevelMin <= stageLevelMax) {
            "invalid stage level range"
        }
        require(stageLevelMax <= monsterLevelMax) { "stage levels must not exceed monster levels" }
        require(baseMaxHp in definitionBaseMaxHpRange) { "baseMaxHp is outside the definition range" }
        require(baseDamage in definitionBaseDamageRange) { "baseDamage is outside the definition range" }
        require(baseDefense in definitionBaseDefenseRange) { "baseDefense is outside the definition range" }
        require(hpGrowthPerLevel in definitionHpGrowthRange) {
            "hpGrowthPerLevel is outside the definition range"
        }
        require(damageGrowthPerLevel in definitionDamageGrowthRange) {
            "damageGrowthPerLevel is outside the definition range"
        }
        require(defenseGrowthPerLevel in definitionDefenseGrowthRange) {
            "defenseGrowthPerLevel is outside the definition range"
        }
        require(finalMaxHpRange.min >= 1) { "final max HP minimum must be positive" }
        require(finalDamageRange.min >= 1) { "final damage minimum must be positive" }
        require(finalDefenseRange.min >= 0) { "final defense minimum must not be negative" }
        require(multiplierBpMax >= basisPointScale) { "multiplierBpMax must include the base scale" }
        require(typeMultipliersBp.keys == MonsterType.entries.toSet()) {
            "every monster type must have multipliers"
        }
        require(gradeMultipliersBp.keys == MonsterGrade.entries.toSet()) {
            "every monster grade must have multipliers"
        }
        require(gradeLevelOffsets.keys == MonsterGrade.entries.toSet()) {
            "every monster grade must have a level offset"
        }
        require(gradeRewardMultipliersBp.keys == MonsterGrade.entries.toSet()) {
            "every monster grade must have a reward multiplier"
        }
        (typeMultipliersBp.values + gradeMultipliersBp.values + combinedMultiplierCapsBp).forEach { multipliers ->
            listOf(multipliers.maxHp, multipliers.damage, multipliers.defense).forEach { value ->
                require(value in 1..multiplierBpMax) { "monster stat multiplier is outside the configured range" }
            }
        }
        gradeLevelOffsets.values.forEach { offset ->
            require(offset >= 0) { "grade level offsets must not be negative" }
        }
        gradeRewardMultipliersBp.values.forEach { multiplier ->
            require(multiplier in 1..multiplierBpMax) {
                "grade reward multiplier is outside the configured range"
            }
        }
        require(stageCount > 0) { "stageCount must be positive" }
        require(eliteStageNumber in 1..stageCount) { "eliteStageNumber is outside the stage range" }
        require(bossStageNumber in 1..stageCount && bossStageNumber != eliteStageNumber) {
            "bossStageNumber is outside the stage range or duplicates the elite stage"
        }
        require(normalEncountersPerStage > 0 && specialEncountersPerStage > 0) {
            "encounter counts must be positive"
        }
        require(CombatRewardBalanceCatalog.supports(combatRewardVersion)) {
            "Unknown combat reward version: $combatRewardVersion"
        }
    }

    companion object {
        private fun defaultTypeMultipliersBp(): Map<MonsterType, MonsterStatMultipliersBp> = mapOf(
            MonsterType.BALANCED to MonsterStatMultipliersBp(10_000, 10_000, 10_000),
            MonsterType.ATTACK to MonsterStatMultipliersBp(9_000, 12_500, 8_500),
            MonsterType.DEFENSE to MonsterStatMultipliersBp(11_000, 8_500, 11_500),
            MonsterType.BOSS to MonsterStatMultipliersBp(13_000, 11_500, 11_500),
        )

        private fun defaultGradeMultipliersBp(): Map<MonsterGrade, MonsterStatMultipliersBp> = mapOf(
            MonsterGrade.NORMAL to MonsterStatMultipliersBp(10_000, 10_000, 10_000),
            MonsterGrade.ELITE to MonsterStatMultipliersBp(17_500, 12_500, 10_500),
            MonsterGrade.BOSS to MonsterStatMultipliersBp(27_500, 14_000, 11_000),
        )

        private fun defaultGradeLevelOffsets(): Map<MonsterGrade, Int> = mapOf(
            MonsterGrade.NORMAL to 0,
            MonsterGrade.ELITE to 1,
            MonsterGrade.BOSS to 2,
        )

        private fun defaultGradeRewardMultipliersBp(): Map<MonsterGrade, Int> = mapOf(
            MonsterGrade.NORMAL to 10_000,
            MonsterGrade.ELITE to 20_000,
            MonsterGrade.BOSS to 40_000,
        )

        private fun <K, V> immutableCopy(source: Map<K, V>): Map<K, V> =
            Collections.unmodifiableMap(LinkedHashMap(source))
    }
}

object MonsterCatalog {
    fun definitions(config: MonsterBalanceConfig = MonsterBalanceConfig()): List<MonsterDefinition> =
        MonsterType.entries.map { type -> definitionFor(type, config) }

    fun definitionFor(
        type: MonsterType,
        config: MonsterBalanceConfig = MonsterBalanceConfig(),
    ): MonsterDefinition {
        val stableKey = when (type) {
            MonsterType.BALANCED -> "balanced"
            MonsterType.ATTACK -> "attack"
            MonsterType.DEFENSE -> "defense"
            MonsterType.BOSS -> "boss"
        }
        return MonsterDefinition(
            id = "monster_${stableKey}_v${config.version}",
            nameKey = "monster_name_$stableKey",
            type = type,
            baseMaxHp = config.baseMaxHp,
            baseDamage = config.baseDamage,
            baseDefense = config.baseDefense,
            hpGrowthPerLevel = config.hpGrowthPerLevel,
            damageGrowthPerLevel = config.damageGrowthPerLevel,
            defenseGrowthPerLevel = config.defenseGrowthPerLevel,
        )
    }
}
