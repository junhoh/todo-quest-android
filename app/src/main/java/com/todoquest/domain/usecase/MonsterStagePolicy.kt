package com.todoquest.domain.usecase

import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterType

object MonsterStagePolicy {
    private val normalTypeCycle = listOf(
        MonsterType.BALANCED,
        MonsterType.ATTACK,
        MonsterType.DEFENSE,
    )

    fun gradeFor(stageNumber: Int, config: MonsterBalanceConfig): MonsterGrade {
        require(stageNumber in 1..config.stageCount) { "stageNumber is outside the configured range" }
        return when (stageNumber) {
            config.eliteStageNumber -> MonsterGrade.ELITE
            config.bossStageNumber -> MonsterGrade.BOSS
            else -> MonsterGrade.NORMAL
        }
    }

    fun encounterCount(stageNumber: Int, config: MonsterBalanceConfig): Int =
        when (gradeFor(stageNumber, config)) {
            MonsterGrade.NORMAL -> config.normalEncountersPerStage
            MonsterGrade.ELITE,
            MonsterGrade.BOSS,
            -> config.specialEncountersPerStage
        }

    fun typeFor(
        stageNumber: Int,
        encounterNumber: Int,
        config: MonsterBalanceConfig,
    ): MonsterType {
        val grade = gradeFor(stageNumber, config)
        require(encounterNumber in 1..encounterCount(stageNumber, config)) {
            "encounterNumber is outside the configured range for the stage"
        }
        return when (grade) {
            MonsterGrade.NORMAL -> normalTypeCycle[
                (stageNumber - 1 + encounterNumber - 1) % normalTypeCycle.size
            ]
            MonsterGrade.ELITE -> normalTypeCycle[
                (stageNumber - 1) % normalTypeCycle.size
            ]
            MonsterGrade.BOSS -> MonsterType.BOSS
        }
    }

    fun monsterLevel(
        stageLevel: Int,
        grade: MonsterGrade,
        config: MonsterBalanceConfig,
    ): Int {
        require(stageLevel in config.stageLevelMin..config.stageLevelMax) {
            "stageLevel is outside the configured range"
        }
        val level = Math.addExact(stageLevel, config.gradeLevelOffsets.getValue(grade))
            .coerceAtMost(config.monsterLevelMax)
        require(level in config.monsterLevelMin..config.monsterLevelMax) {
            "calculated monster level is outside the configured range"
        }
        return level
    }
}
