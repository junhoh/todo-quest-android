package com.todoquest.domain

import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskDifficultyCombatBalanceCatalog
import com.todoquest.domain.model.TaskDifficultyCombatBalanceConfig
import com.todoquest.domain.usecase.TaskDifficultyCombatPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskDifficultyCombatPolicyTest {
    @Test
    fun catalogExposesLegacyNeutralAndCurrentDifficultyMappings() {
        assertEquals(0, TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION)
        assertEquals(1, TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION)
        assertEquals(
            mapOf(
                TaskDifficulty.EASY to 10_000,
                TaskDifficulty.MEDIUM to 10_000,
                TaskDifficulty.HARD to 10_000,
            ),
            TaskDifficultyCombatBalanceCatalog.configFor(
                TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
            ).multiplierBpByDifficulty,
        )
        assertEquals(
            mapOf(
                TaskDifficulty.EASY to 10_000,
                TaskDifficulty.MEDIUM to 15_000,
                TaskDifficulty.HARD to 20_000,
            ),
            TaskDifficultyCombatBalanceCatalog.configFor(
                TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ).multiplierBpByDifficulty,
        )
    }

    @Test
    fun legacyVersionIsNeutralForNullableAndNonNullDifficulty() {
        assertEquals(
            10_000,
            TaskDifficultyCombatPolicy.multiplierBp(
                difficulty = null,
                version = TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
            ),
        )
        TaskDifficulty.entries.forEach { difficulty ->
            assertEquals(
                10_000,
                TaskDifficultyCombatPolicy.multiplierBp(
                    difficulty = difficulty,
                    version = TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
                ),
            )
        }
    }

    @Test
    fun currentVersionRequiresDifficultyAndUsesExactIntegerFlooring() {
        assertEquals(
            3,
            TaskDifficultyCombatPolicy.scaleDamage(
                value = 3,
                difficulty = TaskDifficulty.EASY,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertEquals(
            4,
            TaskDifficultyCombatPolicy.scaleDamage(
                value = 3,
                difficulty = TaskDifficulty.MEDIUM,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertEquals(
            6,
            TaskDifficultyCombatPolicy.scaleDamage(
                value = 3,
                difficulty = TaskDifficulty.HARD,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertEquals(
            34L,
            TaskDifficultyCombatPolicy.scaleXp(
                value = 23L,
                difficulty = TaskDifficulty.MEDIUM,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertEquals(
            0,
            TaskDifficultyCombatPolicy.scaleDamage(
                value = 0,
                difficulty = TaskDifficulty.HARD,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertEquals(
            1L,
            TaskDifficultyCombatPolicy.scaleXp(
                value = 1L,
                difficulty = TaskDifficulty.EASY,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatPolicy.multiplierBp(
                difficulty = null,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatPolicy.scaleXp(
                value = 0L,
                difficulty = null,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatPolicy.scaleDamage(
                value = 0,
                difficulty = TaskDifficulty.EASY,
                version = 99,
            )
        }
    }

    @Test
    fun unsupportedVersionsAndInvalidConfigMappingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatBalanceCatalog.configFor(version = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatBalanceConfig(
                version = 1,
                multiplierBpByDifficulty = mapOf(TaskDifficulty.EASY to 10_000),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatBalanceConfig(
                version = 1,
                multiplierBpByDifficulty = TaskDifficulty.entries.associateWith { difficulty ->
                    if (difficulty == TaskDifficulty.MEDIUM) 0 else 10_000
                },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatBalanceConfig(
                version = 1,
                multiplierBpByDifficulty = TaskDifficulty.entries.associateWith { difficulty ->
                    if (difficulty == TaskDifficulty.HARD) 100_001 else 10_000
                },
            )
        }
    }

    @Test
    fun negativeInputsAndExactArithmeticOverflowAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatPolicy.scaleDamage(
                value = -1,
                difficulty = TaskDifficulty.EASY,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskDifficultyCombatPolicy.scaleXp(
                value = -1L,
                difficulty = TaskDifficulty.EASY,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            TaskDifficultyCombatPolicy.scaleDamage(
                value = Int.MAX_VALUE,
                difficulty = TaskDifficulty.HARD,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            TaskDifficultyCombatPolicy.scaleXp(
                value = Long.MAX_VALUE,
                difficulty = TaskDifficulty.HARD,
                version = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )
        }
    }
}
