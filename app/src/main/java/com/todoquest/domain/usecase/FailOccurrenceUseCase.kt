package com.todoquest.domain.usecase

import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class FailOccurrenceUseCase(
    private val repository: TaskRepository,
    private val combatRepository: CombatRepository,
    private val diagnosticSink: CombatProcessingDiagnosticSink,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase? = null,
) {
    suspend operator fun invoke(taskId: Long, occurrenceDate: LocalDate): FailureResult {
        val failureResult = repository.failOccurrence(taskId, occurrenceDate)
        if (!failureResult.wasAlreadyFailed) {
            try {
                combatRepository.processFailedOccurrenceAttack(taskId, occurrenceDate)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                diagnosticSink.reportMonsterAttackFailure(taskId, occurrenceDate, failure)
            }
        }

        reconcileTaskReminder?.invoke(taskId)
        return failureResult
    }
}
