package com.todoquest.domain.usecase

import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

fun interface CombatProcessingDiagnosticSink {
    fun reportPlayerAttackFailure(
        taskId: Long,
        occurrenceDate: LocalDate,
        failure: Throwable,
    )

    fun reportMonsterAttackFailure(
        taskId: Long,
        occurrenceDate: LocalDate,
        failure: Throwable,
    ) {
        reportPlayerAttackFailure(taskId, occurrenceDate, failure)
    }
}

class CompleteOccurrenceUseCase(
    private val repository: TaskRepository,
    private val combatRepository: CombatRepository,
    private val diagnosticSink: CombatProcessingDiagnosticSink,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase? = null,
) {
    suspend operator fun invoke(taskId: Long, occurrenceDate: LocalDate): CompletionResult {
        val completionResult = repository.completeOccurrence(taskId, occurrenceDate)
        if (!completionResult.alreadyRewarded) {
            try {
                combatRepository.processPlayerAttack(taskId, occurrenceDate)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                diagnosticSink.reportPlayerAttackFailure(taskId, occurrenceDate, failure)
            }
        }

        reconcileTaskReminder?.invoke(taskId)
        return completionResult
    }
}
