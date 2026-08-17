package com.todoquest.domain.usecase

import com.todoquest.domain.model.IntValueRange
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterDefinition
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterStats

object MonsterStatsCalculator {
    fun calculate(
        definition: MonsterDefinition,
        grade: MonsterGrade,
        level: Int,
        config: MonsterBalanceConfig,
    ): MonsterStats {
        validateDefinition(definition, config)
        require(level in config.monsterLevelMin..config.monsterLevelMax) {
            "level is outside the configured monster level range"
        }

        val typeMultipliers = config.typeMultipliersBp.getValue(definition.type)
        val gradeMultipliers = config.gradeMultipliersBp.getValue(grade)
        val levelOffset = (level - config.monsterLevelMin).toLong()
        val maxHpAtLevel = levelValue(definition.baseMaxHp, definition.hpGrowthPerLevel, levelOffset)
        val damageAtLevel = levelValue(definition.baseDamage, definition.damageGrowthPerLevel, levelOffset)
        val defenseAtLevel = levelValue(definition.baseDefense, definition.defenseGrowthPerLevel, levelOffset)

        return MonsterStats(
            maxHp = scaleAndClamp(
                levelValue = maxHpAtLevel,
                typeBp = typeMultipliers.maxHp,
                gradeBp = gradeMultipliers.maxHp,
                combinedCapBp = config.combinedMultiplierCapsBp.maxHp,
                finalRange = config.finalMaxHpRange,
                config = config,
            ),
            damage = scaleAndClamp(
                levelValue = damageAtLevel,
                typeBp = typeMultipliers.damage,
                gradeBp = gradeMultipliers.damage,
                combinedCapBp = config.combinedMultiplierCapsBp.damage,
                finalRange = config.finalDamageRange,
                config = config,
            ),
            defense = scaleAndClamp(
                levelValue = defenseAtLevel,
                typeBp = typeMultipliers.defense,
                gradeBp = gradeMultipliers.defense,
                combinedCapBp = config.combinedMultiplierCapsBp.defense,
                finalRange = config.finalDefenseRange,
                config = config,
            ),
        )
    }

    fun calculate(
        instance: MonsterInstance,
        definition: MonsterDefinition,
        config: MonsterBalanceConfig,
    ): MonsterStats {
        require(instance.definitionId == definition.id) { "instance definition does not match" }
        require(instance.balanceVersion == config.version) { "instance balance version does not match" }
        val stats = calculate(definition, instance.grade, instance.level, config)
        require(instance.currentHp <= stats.maxHp) { "currentHp must be within 0..maxHp" }
        return stats
    }

    private fun validateDefinition(definition: MonsterDefinition, config: MonsterBalanceConfig) {
        require(definition.baseMaxHp in config.definitionBaseMaxHpRange) {
            "baseMaxHp is outside the configured definition range"
        }
        require(definition.baseDamage in config.definitionBaseDamageRange) {
            "baseDamage is outside the configured definition range"
        }
        require(definition.baseDefense in config.definitionBaseDefenseRange) {
            "baseDefense is outside the configured definition range"
        }
        require(definition.hpGrowthPerLevel in config.definitionHpGrowthRange) {
            "hpGrowthPerLevel is outside the configured definition range"
        }
        require(definition.damageGrowthPerLevel in config.definitionDamageGrowthRange) {
            "damageGrowthPerLevel is outside the configured definition range"
        }
        require(definition.defenseGrowthPerLevel in config.definitionDefenseGrowthRange) {
            "defenseGrowthPerLevel is outside the configured definition range"
        }
    }

    private fun levelValue(base: Int, growth: Int, levelOffset: Long): Long = Math.addExact(
        base.toLong(),
        Math.multiplyExact(growth.toLong(), levelOffset),
    )

    private fun scaleAndClamp(
        levelValue: Long,
        typeBp: Int,
        gradeBp: Int,
        combinedCapBp: Int,
        finalRange: IntValueRange,
        config: MonsterBalanceConfig,
    ): Int {
        val combinedNumerator = minOf(
            Math.multiplyExact(typeBp.toLong(), gradeBp.toLong()),
            Math.multiplyExact(combinedCapBp.toLong(), config.basisPointScale.toLong()),
        )
        val denominator = Math.multiplyExact(
            config.basisPointScale.toLong(),
            config.basisPointScale.toLong(),
        )
        val scaled = Math.multiplyExact(levelValue, combinedNumerator).div(denominator)
        return scaled.coerceIn(finalRange.min.toLong(), finalRange.max.toLong()).toInt()
    }
}
