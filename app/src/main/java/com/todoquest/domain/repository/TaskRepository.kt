package com.todoquest.domain.repository

import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.model.UpdateTaskInput
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeOccurrences(rangeStart: LocalDate, rangeEnd: LocalDate): Flow<List<TaskOccurrence>>

    suspend fun createTask(input: CreateTaskInput): Long

    suspend fun getTask(taskId: Long): TodoTask? =
        error("getTask(taskId) is not implemented")

    suspend fun updateTask(task: TodoTask)

    suspend fun updateTask(input: UpdateTaskInput): Long =
        error("updateTask(input) is not implemented")

    suspend fun deleteTask(taskId: Long)

    suspend fun deleteTask(taskId: Long, effectiveDate: LocalDate) {
        error("deleteTask(taskId, effectiveDate) is not implemented")
    }

    suspend fun completeOccurrence(taskId: Long, occurrenceDate: LocalDate): CompletionResult

    suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate)

    suspend fun failOccurrence(taskId: Long, occurrenceDate: LocalDate): FailureResult =
        error("failOccurrence(taskId, occurrenceDate) is not implemented")

    suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        error("undoFailOccurrence(taskId, occurrenceDate) is not implemented")
    }
}
