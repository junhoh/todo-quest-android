package com.todoquest.domain.model

enum class TaskDifficulty {
    EASY,
    MEDIUM,
    HARD,
}

data class TaskDifficultyCombatBalanceConfig(
    val version: Int,
    val multiplierBpByDifficulty: Map<TaskDifficulty, Int>,
) {
    init {
        require(version >= 0) { "version must not be negative" }
        val mappingsByDifficulty = multiplierBpByDifficulty.entries.groupBy { it.key }
        require(
            multiplierBpByDifficulty.entries.size == TaskDifficulty.entries.size &&
                TaskDifficulty.entries.all { difficulty ->
                    mappingsByDifficulty[difficulty]?.size == 1
                },
        ) {
            "every task difficulty must have exactly one combat multiplier"
        }
        multiplierBpByDifficulty.values.forEach { multiplierBp ->
            require(multiplierBp in 1..MAX_MULTIPLIER_BP) {
                "task difficulty combat multiplier is outside the supported range"
            }
        }
    }

    private companion object {
        const val MAX_MULTIPLIER_BP: Int = 100_000
    }
}

object TaskDifficultyCombatBalanceCatalog {
    const val LEGACY_VERSION: Int = 0
    const val CURRENT_VERSION: Int = 1

    private val configs = mapOf(
        LEGACY_VERSION to TaskDifficultyCombatBalanceConfig(
            version = LEGACY_VERSION,
            multiplierBpByDifficulty = TaskDifficulty.entries.associateWith { BASIS_POINT_SCALE },
        ),
        CURRENT_VERSION to TaskDifficultyCombatBalanceConfig(
            version = CURRENT_VERSION,
            multiplierBpByDifficulty = mapOf(
                TaskDifficulty.EASY to BASIS_POINT_SCALE,
                TaskDifficulty.MEDIUM to 15_000,
                TaskDifficulty.HARD to 20_000,
            ),
        ),
    )

    fun configFor(version: Int): TaskDifficultyCombatBalanceConfig =
        requireNotNull(configs[version]) {
            "Unknown task difficulty combat balance version: $version"
        }

    const val BASIS_POINT_SCALE: Int = 10_000
}
