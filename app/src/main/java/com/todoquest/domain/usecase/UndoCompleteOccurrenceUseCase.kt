package com.todoquest.domain.usecase

import com.todoquest.domain.repository.TaskRepository
import java.time.LocalDate

class UndoCompleteOccurrenceUseCase(
    private val repository: TaskRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase? = null,
) {
    suspend operator fun invoke(taskId: Long, occurrenceDate: LocalDate) {
        repository.undoCompleteOccurrence(taskId, occurrenceDate)
        reconcileTaskReminder?.invoke(taskId)
    }
}
