package com.todoquest.domain

import com.todoquest.domain.model.IntValueRange
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterDefinition
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterStatMultipliersBp
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.MonsterType
import com.todoquest.domain.usecase.MonsterStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterStatsCalculatorTest {
    private val config = MonsterBalanceConfig()
    private val definitions = MonsterCatalog.definitions(config).associateBy { it.type }

    @Test
    fun balancedNormalStatsMatchLevelGoldenValues() {
        val expected = mapOf(
            1 to MonsterStats(75, 12, 7),
            10 to MonsterStats(120, 30, 25),
            30 to MonsterStats(220, 70, 65),
            50 to MonsterStats(320, 110, 105),
            55 to MonsterStats(345, 120, 115),
        )

        expected.forEach { (level, stats) ->
            assertEquals(
                stats,
                MonsterStatsCalculator.calculate(
                    definitions.getValue(MonsterType.BALANCED),
                    MonsterGrade.NORMAL,
                    level,
                    config,
                ),
            )
        }
    }

    @Test
    fun everyTypeAndGradeUsesOneFloorAtLevel55() {
        val expected = mapOf(
            MonsterType.BALANCED to mapOf(
                MonsterGrade.NORMAL to MonsterStats(345, 120, 115),
                MonsterGrade.ELITE to MonsterStats(603, 150, 120),
                MonsterGrade.BOSS to MonsterStats(948, 168, 126),
            ),
            MonsterType.ATTACK to mapOf(
                MonsterGrade.NORMAL to MonsterStats(310, 150, 97),
                MonsterGrade.ELITE to MonsterStats(543, 187, 102),
                MonsterGrade.BOSS to MonsterStats(853, 210, 107),
            ),
            MonsterType.DEFENSE to mapOf(
                MonsterGrade.NORMAL to MonsterStats(379, 102, 132),
                MonsterGrade.ELITE to MonsterStats(664, 127, 138),
                MonsterGrade.BOSS to MonsterStats(1_043, 142, 145),
            ),
            MonsterType.BOSS to mapOf(
                MonsterGrade.NORMAL to MonsterStats(448, 138, 132),
                MonsterGrade.ELITE to MonsterStats(784, 172, 138),
                MonsterGrade.BOSS to MonsterStats(1_233, 193, 145),
            ),
        )

        expected.forEach { (type, grades) ->
            grades.forEach { (grade, stats) ->
                assertEquals(
                    "$type $grade",
                    stats,
                    MonsterStatsCalculator.calculate(definitions.getValue(type), grade, 55, config),
                )
            }
        }

        assertEquals(
            118,
            MonsterStatsCalculator.calculate(
                definitions.getValue(MonsterType.ATTACK),
                MonsterGrade.ELITE,
                1,
                config,
            ).maxHp,
        )
    }

    @Test
    fun combinedMultipliersAndFinalValuesClampAtConfiguredCaps() {
        val uncappedMultiplier = MonsterStatMultipliersBp(
            maxHp = 100_000,
            damage = 100_000,
            defense = 100_000,
        )
        val types = config.typeMultipliersBp.toMutableMap().apply {
            this[MonsterType.BALANCED] = uncappedMultiplier
        }
        val grades = config.gradeMultipliersBp.toMutableMap().apply {
            this[MonsterGrade.NORMAL] = uncappedMultiplier
        }
        val cappedConfig = MonsterBalanceConfig(
            typeMultipliersBp = types,
            gradeMultipliersBp = grades,
        )

        assertEquals(
            MonsterStats(300, 21, 10),
            MonsterStatsCalculator.calculate(
                MonsterCatalog.definitionFor(MonsterType.BALANCED, cappedConfig),
                MonsterGrade.NORMAL,
                1,
                cappedConfig,
            ),
        )

        val maximumSource = MonsterDefinition(
            id = "maximum_source",
            nameKey = "monster_name_maximum_source",
            type = MonsterType.BALANCED,
            baseMaxHp = config.definitionBaseMaxHpRange.max,
            baseDamage = config.definitionBaseDamageRange.max,
            baseDefense = config.definitionBaseDefenseRange.max,
            hpGrowthPerLevel = config.definitionHpGrowthRange.max,
            damageGrowthPerLevel = config.definitionDamageGrowthRange.max,
            defenseGrowthPerLevel = config.definitionDefenseGrowthRange.max,
        )
        assertEquals(
            MonsterStats(
                maxHp = config.finalMaxHpRange.max,
                damage = config.finalDamageRange.max,
                defense = config.finalDefenseRange.max,
            ),
            MonsterStatsCalculator.calculate(maximumSource, MonsterGrade.NORMAL, 55, cappedConfig),
        )
    }

    @Test
    fun configOwnsVersionOneValuesAndDefensivelyCopiesCompleteMaps() {
        assertEquals(1, config.version)
        assertEquals(1..55, config.monsterLevelMin..config.monsterLevelMax)
        assertEquals(50, config.stageLevelMax)
        assertEquals(10, config.stageCount)
        assertEquals(10_000, config.basisPointScale)
        assertEquals(
            mapOf(
                MonsterGrade.NORMAL to 10_000,
                MonsterGrade.ELITE to 20_000,
                MonsterGrade.BOSS to 40_000,
            ),
            config.gradeRewardMultipliersBp,
        )

        val mutableTypes = config.typeMultipliersBp.toMutableMap()
        val copied = MonsterBalanceConfig(typeMultipliersBp = mutableTypes)
        mutableTypes.clear()
        assertEquals(MonsterType.entries.toSet(), copied.typeMultipliersBp.keys)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (copied.typeMultipliersBp as MutableMap<MonsterType, MonsterStatMultipliersBp>).clear()
        }

        assertThrows(IllegalArgumentException::class.java) {
            MonsterBalanceConfig(
                gradeLevelOffsets = mapOf(
                    MonsterGrade.NORMAL to 0,
                    MonsterGrade.ELITE to 1,
                ),
            )
        }
    }

    @Test
    fun invalidDefinitionsLevelsAndCurrentHpAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            definition(id = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            definition(nameKey = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            definition(baseMaxHp = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            definition(hpGrowthPerLevel = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStatsCalculator.calculate(definition(), MonsterGrade.NORMAL, 0, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStatsCalculator.calculate(definition(), MonsterGrade.NORMAL, 56, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStatsCalculator.calculate(
                definition(baseMaxHp = config.definitionBaseMaxHpRange.max + 1),
                MonsterGrade.NORMAL,
                1,
                config,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            instance(currentHp = -1)
        }

        val healthy = instance(currentHp = 75)
        assertFalse(healthy.isDefeated)
        val defeated = healthy.copy(currentHp = 0)
        assertTrue(defeated.isDefeated)
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStatsCalculator.calculate(
                healthy.copy(currentHp = 76),
                definitions.getValue(MonsterType.BALANCED),
                config,
            )
        }
    }

    private fun definition(
        id: String = "test_monster",
        nameKey: String = "monster_name_test",
        baseMaxHp: Int = 75,
        hpGrowthPerLevel: Int = 5,
    ): MonsterDefinition = MonsterDefinition(
        id = id,
        nameKey = nameKey,
        type = MonsterType.BALANCED,
        baseMaxHp = baseMaxHp,
        baseDamage = 12,
        baseDefense = 7,
        hpGrowthPerLevel = hpGrowthPerLevel,
        damageGrowthPerLevel = 2,
        defenseGrowthPerLevel = 2,
    )

    private fun instance(currentHp: Int): MonsterInstance = MonsterInstance(
        id = 1,
        definitionId = "monster_balanced_v1",
        grade = MonsterGrade.NORMAL,
        stageNumber = 1,
        encounterNumber = 1,
        level = 1,
        currentHp = currentHp,
        balanceVersion = 1,
    )
}
