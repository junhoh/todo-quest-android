package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterType
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.domain.usecase.CombatRewardPolicy
import com.todoquest.domain.usecase.MonsterSpeciesPolicy
import com.todoquest.domain.usecase.MonsterStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonsterSpeciesPolicyTest {
    @Test
    fun balanceVersionOneMatchesTheGoldenSpeciesSchedule() {
        assertEquals(
            listOf(
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.HARPY,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.SLIME,
                MonsterSpecies.HARPY,
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.SLIME,
            ),
            normalSchedule(stageNumber = 1),
        )
        assertEquals(
            listOf(
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SLIME,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.HARPY,
                MonsterSpecies.HARPY,
            ),
            normalSchedule(stageNumber = 2),
        )
        assertEquals(
            MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            MonsterSpeciesPolicy.speciesFor(
                stageNumber = 5,
                encounterNumber = 1,
                grade = MonsterGrade.ELITE,
                encounterCount = 1,
                balanceVersion = 1,
            ),
        )
        assertEquals(
            MonsterSpecies.HARPY,
            MonsterSpeciesPolicy.speciesFor(
                stageNumber = 10,
                encounterNumber = 1,
                grade = MonsterGrade.BOSS,
                encounterCount = 1,
                balanceVersion = 1,
            ),
        )
    }

    @Test
    fun repeatedCallsReturnTheSameSpecies() {
        val expected = MonsterSpeciesPolicy.speciesFor(
            stageNumber = 7,
            encounterNumber = 6,
            grade = MonsterGrade.NORMAL,
            encounterCount = 8,
            balanceVersion = 1,
        )

        repeat(20) {
            assertEquals(
                expected,
                MonsterSpeciesPolicy.speciesFor(
                    stageNumber = 7,
                    encounterNumber = 6,
                    grade = MonsterGrade.NORMAL,
                    encounterCount = 8,
                    balanceVersion = 1,
                ),
            )
        }
    }

    @Test
    fun everyNormalStageContainsAllSpeciesWithBalancedCountsAndItsOwnOrder() {
        val normalStages = listOf(1, 2, 3, 4, 6, 7, 8, 9)
        val expectedSpecies = setOf(
            MonsterSpecies.GOBLIN_SCOUT,
            MonsterSpecies.SLIME,
            MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            MonsterSpecies.SKELETON_SOLDIER,
            MonsterSpecies.HARPY,
        )
        val schedules = normalStages.map(::normalSchedule)

        schedules.forEach { schedule ->
            val counts = schedule.groupingBy { it }.eachCount()
            assertEquals(expectedSpecies, counts.keys)
            assertEquals(listOf(1, 1, 2, 2, 2), counts.values.sorted())
        }
        assertEquals(normalStages.size, schedules.toSet().size)
    }

    @Test
    fun invalidScheduleInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(0, 1, MonsterGrade.NORMAL, 8, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(1, 1, MonsterGrade.NORMAL, 8, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(1, 1, MonsterGrade.NORMAL, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(1, 0, MonsterGrade.NORMAL, 8, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(1, 9, MonsterGrade.NORMAL, 8, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(1, 1, MonsterGrade.NORMAL, 4, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(5, 1, MonsterGrade.ELITE, 2, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterSpeciesPolicy.speciesFor(10, 1, MonsterGrade.BOSS, 2, 1)
        }
    }

    @Test
    fun speciesMetadataDoesNotChangeStatsDamageOrCombatRewards() {
        val monsterConfig = MonsterBalanceConfig()
        val characterConfig = CharacterStatBalanceConfig()
        val definition = MonsterCatalog.definitionFor(MonsterType.DEFENSE, monsterConfig)
        val grade = MonsterGrade.NORMAL
        val level = 17
        val statsBeforeSpeciesLookup = MonsterStatsCalculator.calculate(
            definition = definition,
            grade = grade,
            level = level,
            config = monsterConfig,
        )
        val damageBeforeSpeciesLookup = CombatCalculator.damageAfterDefense(
            rawDamage = 60,
            defense = statsBeforeSpeciesLookup.defense,
            config = characterConfig,
        )
        val rewardsBeforeSpeciesLookup = (1..2).associateWith { rewardVersion ->
            listOf(false, true).associateWith { isKill ->
                CombatRewardPolicy.rewardFor(
                    monsterLevel = level,
                    monsterGrade = grade,
                    isKill = isKill,
                    goldGainBonusBp = 0,
                    combatRewardVersion = rewardVersion,
                    monsterConfig = monsterConfig,
                    characterConfig = characterConfig,
                )
            }
        }

        assertEquals(
            MonsterSpecies.HARPY,
            MonsterSpeciesPolicy.speciesFor(
                stageNumber = 2,
                encounterNumber = 7,
                grade = grade,
                encounterCount = 8,
                balanceVersion = monsterConfig.version,
            ),
        )

        val statsAfterSpeciesLookup = MonsterStatsCalculator.calculate(
            definition = definition,
            grade = grade,
            level = level,
            config = monsterConfig,
        )
        val damageAfterSpeciesLookup = CombatCalculator.damageAfterDefense(
            rawDamage = 60,
            defense = statsAfterSpeciesLookup.defense,
            config = characterConfig,
        )
        val rewardsAfterSpeciesLookup = (1..2).associateWith { rewardVersion ->
            listOf(false, true).associateWith { isKill ->
                CombatRewardPolicy.rewardFor(
                    monsterLevel = level,
                    monsterGrade = grade,
                    isKill = isKill,
                    goldGainBonusBp = 0,
                    combatRewardVersion = rewardVersion,
                    monsterConfig = monsterConfig,
                    characterConfig = characterConfig,
                )
            }
        }

        assertEquals(statsBeforeSpeciesLookup, statsAfterSpeciesLookup)
        assertEquals(statsBeforeSpeciesLookup.maxHp, statsAfterSpeciesLookup.maxHp)
        assertEquals(170, statsAfterSpeciesLookup.maxHp)
        assertEquals(37, statsAfterSpeciesLookup.damage)
        assertEquals(44, statsAfterSpeciesLookup.defense)
        assertEquals(damageBeforeSpeciesLookup, damageAfterSpeciesLookup)
        assertEquals(41, damageAfterSpeciesLookup)
        assertEquals(rewardsBeforeSpeciesLookup, rewardsAfterSpeciesLookup)
        assertEquals(2L, rewardsAfterSpeciesLookup.getValue(1).getValue(false).hitXpAward)
        assertEquals(13L, rewardsAfterSpeciesLookup.getValue(1).getValue(true).killBonusXpAward)
        assertEquals(6L, rewardsAfterSpeciesLookup.getValue(1).getValue(true).killGoldAward)
        assertEquals(4L, rewardsAfterSpeciesLookup.getValue(2).getValue(false).hitXpAward)
        assertEquals(23L, rewardsAfterSpeciesLookup.getValue(2).getValue(true).killBonusXpAward)
        assertEquals(16L, rewardsAfterSpeciesLookup.getValue(2).getValue(true).killGoldAward)
    }

    private fun normalSchedule(stageNumber: Int): List<MonsterSpecies> =
        (1..8).map { encounterNumber ->
            MonsterSpeciesPolicy.speciesFor(
                stageNumber = stageNumber,
                encounterNumber = encounterNumber,
                grade = MonsterGrade.NORMAL,
                encounterCount = 8,
                balanceVersion = 1,
            )
        }
}
