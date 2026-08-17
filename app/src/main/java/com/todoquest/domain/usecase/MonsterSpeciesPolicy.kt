package com.todoquest.domain.usecase

import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterSpecies
import java.util.Collections
import java.util.Random

object MonsterSpeciesPolicy {
    private const val BASE_SEED = 0x544F444F51554553L

    private val scheduledSpecies = listOf(
        MonsterSpecies.GOBLIN_SCOUT,
        MonsterSpecies.SLIME,
        MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        MonsterSpecies.SKELETON_SOLDIER,
        MonsterSpecies.HARPY,
    )

    fun speciesFor(
        stageNumber: Int,
        encounterNumber: Int,
        grade: MonsterGrade,
        encounterCount: Int,
        balanceVersion: Int,
    ): MonsterSpecies {
        require(stageNumber > 0) { "stageNumber must be positive" }
        require(balanceVersion > 0) { "balanceVersion must be positive" }
        require(encounterCount > 0) { "encounterCount must be positive" }
        require(encounterNumber in 1..encounterCount) {
            "encounterNumber is outside the encounter range"
        }

        val random = Random(seedFor(balanceVersion, stageNumber, grade))
        return when (grade) {
            MonsterGrade.NORMAL -> normalSpecies(
                encounterNumber = encounterNumber,
                encounterCount = encounterCount,
                random = random,
            )
            MonsterGrade.ELITE,
            MonsterGrade.BOSS,
            -> specialSpecies(encounterNumber, encounterCount, random)
        }
    }

    private fun normalSpecies(
        encounterNumber: Int,
        encounterCount: Int,
        random: Random,
    ): MonsterSpecies {
        require(encounterCount >= scheduledSpecies.size) {
            "NORMAL encounterCount must include every scheduled species"
        }
        val pool = scheduledSpecies.toMutableList()
        while (pool.size < encounterCount) {
            val shuffledSpecies = scheduledSpecies.toMutableList()
            Collections.shuffle(shuffledSpecies, random)
            pool.addAll(shuffledSpecies.take(encounterCount - pool.size))
        }
        Collections.shuffle(pool, random)
        return pool[encounterNumber - 1]
    }

    private fun specialSpecies(
        encounterNumber: Int,
        encounterCount: Int,
        random: Random,
    ): MonsterSpecies {
        require(encounterCount == 1 && encounterNumber == 1) {
            "ELITE and BOSS stages must contain exactly one encounter"
        }
        val candidates = scheduledSpecies.toMutableList()
        Collections.shuffle(candidates, random)
        return candidates.first()
    }

    private fun seedFor(
        balanceVersion: Int,
        stageNumber: Int,
        grade: MonsterGrade,
    ): Long {
        val gradeCode = when (grade) {
            MonsterGrade.NORMAL -> 1
            MonsterGrade.ELITE -> 2
            MonsterGrade.BOSS -> 3
        }
        var seed = BASE_SEED
        seed = seed * 31L + balanceVersion
        seed = seed * 31L + stageNumber
        seed = seed * 31L + gradeCode
        return seed
    }
}
