package com.todoquest.domain

import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.usecase.MonsterDiscoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterDiscoveryPolicyTest {
    private val config = MonsterBalanceConfig()

    @Test
    fun emptyEncounterHistoryDiscoversNoSpecies() {
        assertEquals(
            emptySet<MonsterSpecies>(),
            MonsterDiscoveryPolicy.discoveredSpecies(emptyList(), config),
        )
    }

    @Test
    fun repeatedEncountersWithTheSameSpeciesAreDeduplicated() {
        val instances = listOf(
            monsterInstance(id = 1L, stageNumber = 1, encounterNumber = 1),
            monsterInstance(id = 2L, stageNumber = 1, encounterNumber = 4),
        )

        assertEquals(
            setOf(MonsterSpecies.SKELETON_SOLDIER),
            MonsterDiscoveryPolicy.discoveredSpecies(instances, config),
        )
    }

    @Test
    fun stageEncounterAndGradeHistoryMapsToAllFiveScheduledSpecies() {
        val expectedMappings = listOf(
            monsterInstance(
                id = 1L,
                stageNumber = 1,
                encounterNumber = 3,
                grade = MonsterGrade.NORMAL,
            ) to MonsterSpecies.GOBLIN_SCOUT,
            monsterInstance(
                id = 2L,
                stageNumber = 1,
                encounterNumber = 1,
                grade = MonsterGrade.NORMAL,
            ) to MonsterSpecies.SKELETON_SOLDIER,
            monsterInstance(
                id = 3L,
                stageNumber = 5,
                encounterNumber = 1,
                grade = MonsterGrade.ELITE,
            ) to MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            monsterInstance(
                id = 4L,
                stageNumber = 10,
                encounterNumber = 1,
                grade = MonsterGrade.BOSS,
            ) to MonsterSpecies.HARPY,
            monsterInstance(
                id = 5L,
                stageNumber = 1,
                encounterNumber = 5,
                grade = MonsterGrade.NORMAL,
            ) to MonsterSpecies.SLIME,
        )

        expectedMappings.forEach { (instance, expectedSpecies) ->
            assertEquals(
                setOf(expectedSpecies),
                MonsterDiscoveryPolicy.discoveredSpecies(listOf(instance), config),
            )
        }
        assertEquals(
            MonsterSpecies.entries.toSet(),
            MonsterDiscoveryPolicy.discoveredSpecies(expectedMappings.map { it.first }, config),
        )
    }

    @Test
    fun defeatedHistoricalInstanceIsDiscovered() {
        val defeatedInstance = monsterInstance(
            id = 1L,
            stageNumber = 1,
            encounterNumber = 7,
            currentHp = 0,
        )

        assertEquals(
            setOf(MonsterSpecies.CORRUPTED_TREE_SPIRIT),
            MonsterDiscoveryPolicy.discoveredSpecies(listOf(defeatedInstance), config),
        )
    }

    @Test
    fun balanceVersionMismatchIsRejected() {
        val legacyInstance = monsterInstance(
            id = 1L,
            stageNumber = 1,
            encounterNumber = 1,
            balanceVersion = config.version + 1,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            MonsterDiscoveryPolicy.discoveredSpecies(listOf(legacyInstance), config)
        }
        assertTrue(error.message.orEmpty().contains("balance version"))
        assertTrue(error.message.orEmpty().contains("${legacyInstance.balanceVersion}"))
        assertTrue(error.message.orEmpty().contains("${config.version}"))
    }

    private fun monsterInstance(
        id: Long,
        stageNumber: Int,
        encounterNumber: Int,
        grade: MonsterGrade = MonsterGrade.NORMAL,
        currentHp: Int = 1,
        balanceVersion: Int = config.version,
    ): MonsterInstance = MonsterInstance(
        id = id,
        definitionId = "monster_balanced_v$balanceVersion",
        grade = grade,
        stageNumber = stageNumber,
        encounterNumber = encounterNumber,
        level = 1,
        currentHp = currentHp,
        balanceVersion = balanceVersion,
    )
}
