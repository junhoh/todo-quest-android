package com.todoquest.domain.usecase

import com.todoquest.core.AppClock
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.repository.CombatRepository

class ReconcileCombatUseCase(
    private val repository: CombatRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): CombatReconciliationResult =
        repository.reconcileOverdue(clock.now())
}
