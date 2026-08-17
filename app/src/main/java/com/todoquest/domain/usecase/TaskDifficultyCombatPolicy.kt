package com.todoquest.domain.usecase

import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskDifficultyCombatBalanceCatalog

object TaskDifficultyCombatPolicy {
    fun multiplierBp(difficulty: TaskDifficulty?, version: Int): Int {
        val config = TaskDifficultyCombatBalanceCatalog.configFor(version)
        if (version == TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION) {
            return TaskDifficultyCombatBalanceCatalog.BASIS_POINT_SCALE
        }
        return config.multiplierBpByDifficulty.getValue(
            requireNotNull(difficulty) {
                "difficulty is required for task difficulty combat balance version $version"
            },
        )
    }

    fun scaleDamage(value: Int, difficulty: TaskDifficulty?, version: Int): Int {
        require(value >= 0) { "damage value must not be negative" }
        val multiplierBp = multiplierBp(difficulty, version)
        if (value == 0) return 0

        val scaled = scalePositive(value.toLong(), multiplierBp)
        return Math.toIntExact(scaled)
    }

    fun scaleXp(value: Long, difficulty: TaskDifficulty?, version: Int): Long {
        require(value >= 0L) { "XP value must not be negative" }
        val multiplierBp = multiplierBp(difficulty, version)
        if (value == 0L) return 0L

        return scalePositive(value, multiplierBp)
    }

    private fun scalePositive(
        value: Long,
        multiplierBp: Int,
    ): Long {
        val numerator = Math.multiplyExact(
            value,
            multiplierBp.toLong(),
        )
        return maxOf(
            1L,
            numerator / TaskDifficultyCombatBalanceCatalog.BASIS_POINT_SCALE,
        )
    }
}
