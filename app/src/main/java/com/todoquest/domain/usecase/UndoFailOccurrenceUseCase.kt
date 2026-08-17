package com.todoquest.domain.usecase

import com.todoquest.domain.repository.TaskRepository
import java.time.LocalDate

class UndoFailOccurrenceUseCase(
    private val repository: TaskRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase? = null,
) {
    suspend operator fun invoke(taskId: Long, occurrenceDate: LocalDate) {
        repository.undoFailOccurrence(taskId, occurrenceDate)
        reconcileTaskReminder?.invoke(taskId)
    }
}
