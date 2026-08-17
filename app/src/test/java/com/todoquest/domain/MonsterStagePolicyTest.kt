package com.todoquest.domain

import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterType
import com.todoquest.domain.usecase.MonsterSpeciesPolicy
import com.todoquest.domain.usecase.MonsterStagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonsterStagePolicyTest {
    private val config = MonsterBalanceConfig()

    @Test
    fun tenStageGradesAndEncounterCountsMatchTheContract() {
        val expectedGrades = listOf(
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.ELITE,
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.NORMAL,
            MonsterGrade.BOSS,
        )

        expectedGrades.forEachIndexed { index, grade ->
            val stage = index + 1
            assertEquals(grade, MonsterStagePolicy.gradeFor(stage, config))
            assertEquals(
                if (grade == MonsterGrade.NORMAL) 8 else 1,
                MonsterStagePolicy.encounterCount(stage, config),
            )
        }
    }

    @Test
    fun normalAndEliteTypesUseTheirDocumentedCyclesAndBossIsFixed() {
        assertEquals(MonsterType.BALANCED, MonsterStagePolicy.typeFor(1, 1, config))
        assertEquals(MonsterType.ATTACK, MonsterStagePolicy.typeFor(1, 2, config))
        assertEquals(MonsterType.DEFENSE, MonsterStagePolicy.typeFor(1, 3, config))
        assertEquals(MonsterType.BALANCED, MonsterStagePolicy.typeFor(1, 4, config))
        assertEquals(MonsterType.ATTACK, MonsterStagePolicy.typeFor(1, 8, config))
        assertEquals(MonsterType.ATTACK, MonsterStagePolicy.typeFor(2, 1, config))
        assertEquals(MonsterType.ATTACK, MonsterStagePolicy.typeFor(5, 1, config))
        assertEquals(MonsterType.DEFENSE, MonsterStagePolicy.typeFor(6, 1, config))
        assertEquals(MonsterType.BOSS, MonsterStagePolicy.typeFor(10, 1, config))
    }

    @Test
    fun stageOneNormalEncountersKeepTypeCycleWhileSpeciesUseTheNewSchedule() {
        val expectedSpecies = listOf(
            MonsterSpecies.SKELETON_SOLDIER,
            MonsterSpecies.HARPY,
            MonsterSpecies.GOBLIN_SCOUT,
            MonsterSpecies.SKELETON_SOLDIER,
            MonsterSpecies.SLIME,
            MonsterSpecies.HARPY,
            MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            MonsterSpecies.SLIME,
        )
        val grade = MonsterStagePolicy.gradeFor(1, config)
        val encounterCount = MonsterStagePolicy.encounterCount(1, config)

        val actualSpecies = (1..encounterCount).map { encounter ->
            MonsterSpeciesPolicy.speciesFor(
                stageNumber = 1,
                encounterNumber = encounter,
                grade = grade,
                encounterCount = encounterCount,
                balanceVersion = config.version,
            )
        }

        assertEquals(expectedSpecies, actualSpecies)
    }

    @Test
    fun specialStagePolicyOutputsSelectOneDeterministicSpecies() {
        val expectedSpeciesByStage = mapOf(
            5 to MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            10 to MonsterSpecies.HARPY,
        )

        expectedSpeciesByStage.forEach { (stageNumber, expectedSpecies) ->
            val grade = MonsterStagePolicy.gradeFor(stageNumber, config)
            val encounterCount = MonsterStagePolicy.encounterCount(stageNumber, config)

            assertEquals(1, encounterCount)
            assertEquals(
                expectedSpecies,
                MonsterSpeciesPolicy.speciesFor(
                    stageNumber = stageNumber,
                    encounterNumber = 1,
                    grade = grade,
                    encounterCount = encounterCount,
                    balanceVersion = config.version,
                ),
            )
        }
    }

    @Test
    fun gradeOffsetsProduceLevels50_51_52AndClampAt55() {
        assertEquals(50, MonsterStagePolicy.monsterLevel(50, MonsterGrade.NORMAL, config))
        assertEquals(51, MonsterStagePolicy.monsterLevel(50, MonsterGrade.ELITE, config))
        assertEquals(52, MonsterStagePolicy.monsterLevel(50, MonsterGrade.BOSS, config))

        val futureConfig = MonsterBalanceConfig(stageLevelMax = 55)
        assertEquals(55, MonsterStagePolicy.monsterLevel(54, MonsterGrade.ELITE, futureConfig))
        assertEquals(55, MonsterStagePolicy.monsterLevel(55, MonsterGrade.BOSS, futureConfig))
    }

    @Test
    fun invalidStagesEncountersAndStageLevelsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.gradeFor(0, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.gradeFor(11, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.typeFor(1, 0, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.typeFor(1, 9, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.typeFor(5, 2, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.monsterLevel(0, MonsterGrade.NORMAL, config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterStagePolicy.monsterLevel(51, MonsterGrade.NORMAL, config)
        }
    }
}
