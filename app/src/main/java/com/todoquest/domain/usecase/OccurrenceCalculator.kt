package com.todoquest.domain.usecase

import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.TodoTask
import java.time.LocalDate
import java.time.YearMonth

class OccurrenceCalculator {
    fun occurrencesFor(
        task: TodoTask,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        completedDates: Set<LocalDate>,
    ): List<TaskOccurrence> = occurrencesFor(
        task = task,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        completedDates = completedDates,
        failedDates = emptySet(),
    )

    fun occurrencesFor(
        task: TodoTask,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        completedDates: Set<LocalDate>,
        failedDates: Set<LocalDate>,
    ): List<TaskOccurrence> {
        val conflictingDates = completedDates intersect failedDates
        require(conflictingDates.isEmpty()) {
            "Occurrence dates cannot be both completed and failed: $conflictingDates"
        }
        return occurrencesFor(
            task = task,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            statusesByDate = buildMap {
                completedDates.forEach { put(it, TaskOccurrenceStatus.COMPLETED) }
                failedDates.forEach { put(it, TaskOccurrenceStatus.FAILED) }
            },
        )
    }

    fun occurrencesFor(
        task: TodoTask,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        statusesByDate: Map<LocalDate, TaskOccurrenceStatus>,
    ): List<TaskOccurrence> {
        require(!rangeEnd.isBefore(rangeStart)) { "rangeEnd must not be before rangeStart" }

        return occurrenceDates(task, rangeStart, rangeEnd).map { date ->
            TaskOccurrence(
                taskId = task.id,
                title = task.title,
                memo = task.memo,
                occurrenceDate = date,
                time = task.time,
                difficulty = task.difficulty,
                category = task.category,
                recurrenceRule = task.recurrenceRule,
                status = statusesByDate[date] ?: TaskOccurrenceStatus.TODO,
                reminderSetting = task.reminderSetting,
            )
        }
    }

    fun occursOn(task: TodoTask, date: LocalDate): Boolean {
        if (date.isBefore(task.startDate)) return false
        if (task.endDate != null && date.isAfter(task.endDate)) return false
        return when (task.recurrenceRule) {
            RecurrenceRule.NONE -> date == task.startDate
            RecurrenceRule.DAILY -> true
            RecurrenceRule.WEEKLY -> date.dayOfWeek == task.startDate.dayOfWeek
            RecurrenceRule.MONTHLY -> date.dayOfMonth == task.startDate.dayOfMonth
        }
    }

    private fun occurrenceDates(
        task: TodoTask,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        val effectiveRangeEnd = task.endDate?.let { minOf(rangeEnd, it) } ?: rangeEnd
        if (effectiveRangeEnd.isBefore(rangeStart) || effectiveRangeEnd.isBefore(task.startDate)) {
            return emptyList()
        }

        return when (task.recurrenceRule) {
            RecurrenceRule.NONE -> {
                if (!task.startDate.isBefore(rangeStart) && !task.startDate.isAfter(effectiveRangeEnd)) {
                    listOf(task.startDate)
                } else {
                    emptyList()
                }
            }
            RecurrenceRule.DAILY -> dailyDates(task.startDate, rangeStart, effectiveRangeEnd)
            RecurrenceRule.WEEKLY -> weeklyDates(task.startDate, rangeStart, effectiveRangeEnd)
            RecurrenceRule.MONTHLY -> monthlyDates(task.startDate, rangeStart, effectiveRangeEnd)
        }
    }

    private fun dailyDates(
        startDate: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        val first = maxOf(startDate, rangeStart)
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .toList()
    }

    private fun weeklyDates(
        startDate: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        var first = maxOf(startDate, rangeStart)
        while (first.dayOfWeek != startDate.dayOfWeek) {
            first = first.plusDays(1)
        }
        return generateSequence(first) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .toList()
    }

    private fun monthlyDates(
        startDate: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var month = YearMonth.from(maxOf(startDate, rangeStart))
        val endMonth = YearMonth.from(rangeEnd)
        while (!month.isAfter(endMonth)) {
            if (startDate.dayOfMonth <= month.lengthOfMonth()) {
                val candidate = month.atDay(startDate.dayOfMonth)
                if (!candidate.isBefore(startDate) &&
                    !candidate.isBefore(rangeStart) &&
                    !candidate.isAfter(rangeEnd)
                ) {
                    dates += candidate
                }
            }
            month = month.plusMonths(1)
        }
        return dates
    }
}
