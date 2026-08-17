package com.todoquest.domain.repository

import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.PlayerAttackResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CombatRepository {
    val events: Flow<CombatTransition>
        get() = emptyFlow()

    fun observeCombat(): Flow<CombatSnapshot>

    fun observeDiscoveredMonsterSpecies(): Flow<Set<MonsterSpecies>> = emptyFlow()

    suspend fun processPlayerAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): PlayerAttackResult

    suspend fun processPendingPlayerAttacks(): Int

    suspend fun processFailedOccurrenceAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): MonsterAttackResult = error(
        "processFailedOccurrenceAttack(taskId, occurrenceDate) is not implemented",
    )

    suspend fun processPendingFailureAttacks(): Int =
        error("processPendingFailureAttacks() is not implemented")

    suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult
}
