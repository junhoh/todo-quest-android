package com.todoquest.domain

import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.usecase.OccurrenceCalculator
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceCalculatorTest {
    private val calculator = OccurrenceCalculator()

    @Test
    fun oneTimeTaskOnlyAppearsOnStartDateWithinRange() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            recurrenceRule = RecurrenceRule.NONE,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 1),
            rangeEnd = LocalDate.of(2026, 7, 31),
            completedDates = emptySet(),
        )

        assertEquals(listOf(LocalDate.of(2026, 7, 14)), occurrences.map { it.occurrenceDate })
    }

    @Test
    fun dailyTaskAppearsOnEveryDateFromStartDate() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            recurrenceRule = RecurrenceRule.DAILY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 13),
            rangeEnd = LocalDate.of(2026, 7, 17),
            completedDates = setOf(LocalDate.of(2026, 7, 15)),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 16),
                LocalDate.of(2026, 7, 17),
            ),
            occurrences.map { it.occurrenceDate },
        )
        assertTrue(occurrences.single { it.occurrenceDate == LocalDate.of(2026, 7, 15) }.isCompleted)
    }

    @Test
    fun occurrenceCopiesReminderSettingAndKeepsSourceCompatibleDefaults() {
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val reminderSetting = ReminderSetting(
            mode = ReminderMode.CUSTOM_TIME,
            customTime = LocalTime.of(8, 15),
        )
        val task = task(
            startDate = occurrenceDate,
            recurrenceRule = RecurrenceRule.NONE,
            reminderSetting = reminderSetting,
        )

        val calculated = calculator.occurrencesFor(
            task = task,
            rangeStart = occurrenceDate,
            rangeEnd = occurrenceDate,
            completedDates = emptySet(),
        ).single()
        val sourceCompatible = TaskOccurrence(
            taskId = task.id,
            title = task.title,
            memo = task.memo,
            occurrenceDate = occurrenceDate,
            time = task.time,
            difficulty = task.difficulty,
            category = task.category,
            recurrenceRule = task.recurrenceRule,
            status = TaskOccurrenceStatus.TODO,
        )

        assertEquals(reminderSetting, calculated.reminderSetting)
        assertEquals(ReminderScheduleStatus.DISABLED, calculated.reminderScheduleStatus)
        assertEquals(ReminderSetting(), sourceCompatible.reminderSetting)
        assertEquals(ReminderScheduleStatus.DISABLED, sourceCompatible.reminderScheduleStatus)
    }

    @Test
    fun occurrenceStatusMapCreatesOneExplicitStatusPerDate() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            recurrenceRule = RecurrenceRule.DAILY,
        )
        val completedDate = LocalDate.of(2026, 7, 15)
        val failedDate = LocalDate.of(2026, 7, 16)

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 14),
            rangeEnd = LocalDate.of(2026, 7, 17),
            statusesByDate = mapOf(
                completedDate to TaskOccurrenceStatus.COMPLETED,
                failedDate to TaskOccurrenceStatus.FAILED,
            ),
        )

        assertEquals(
            listOf(
                TaskOccurrenceStatus.TODO,
                TaskOccurrenceStatus.COMPLETED,
                TaskOccurrenceStatus.FAILED,
                TaskOccurrenceStatus.TODO,
            ),
            occurrences.map { it.status },
        )
        assertTrue(occurrences.single { it.occurrenceDate == completedDate }.isCompleted)
        assertTrue(occurrences.single { it.occurrenceDate == failedDate }.isFailed)
        assertTrue(occurrences.single { it.occurrenceDate == LocalDate.of(2026, 7, 17) }.isPending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun completedAndFailedSetsRejectConflictingSourceState() {
        val date = LocalDate.of(2026, 7, 14)

        calculator.occurrencesFor(
            task = task(startDate = date, recurrenceRule = RecurrenceRule.NONE),
            rangeStart = date,
            rangeEnd = date,
            completedDates = setOf(date),
            failedDates = setOf(date),
        )
    }

    @Test
    fun weeklyTaskAppearsOnMatchingDayOfWeekOnly() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            recurrenceRule = RecurrenceRule.WEEKLY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 1),
            rangeEnd = LocalDate.of(2026, 8, 5),
            completedDates = emptySet(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 8, 4),
            ),
            occurrences.map { it.occurrenceDate },
        )
    }

    @Test
    fun monthlyTaskSkipsMonthsWithoutTheStartDay() {
        val task = task(
            startDate = LocalDate.of(2026, 1, 31),
            recurrenceRule = RecurrenceRule.MONTHLY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 5, 31),
            completedDates = emptySet(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 5, 31),
            ),
            occurrences.map { it.occurrenceDate },
        )
    }

    @Test
    fun occurrenceCheckDoesNotTreatEveryRepeatedDateAsOriginalCompletion() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            recurrenceRule = RecurrenceRule.DAILY,
        )

        assertTrue(calculator.occursOn(task, LocalDate.of(2026, 7, 16)))
        assertEquals(RecurrenceRule.DAILY, task.recurrenceRule)
    }

    @Test
    fun repeatedTaskDoesNotCreateOccurrencesAfterEndDate() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            endDate = LocalDate.of(2026, 7, 16),
            recurrenceRule = RecurrenceRule.DAILY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 14),
            rangeEnd = LocalDate.of(2026, 7, 20),
            completedDates = emptySet(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 16),
            ),
            occurrences.map { it.occurrenceDate },
        )
        assertEquals(false, calculator.occursOn(task, LocalDate.of(2026, 7, 17)))
    }

    @Test
    fun taskWithEndDateBeforeStartDateDoesNotCreateOccurrences() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            endDate = LocalDate.of(2026, 7, 13),
            recurrenceRule = RecurrenceRule.DAILY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 1),
            rangeEnd = LocalDate.of(2026, 7, 31),
            completedDates = emptySet(),
        )

        assertTrue(occurrences.isEmpty())
        assertEquals(false, calculator.occursOn(task, LocalDate.of(2026, 7, 14)))
    }

    @Test
    fun weeklyTaskDoesNotCreateOccurrencesAfterEndDate() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 14),
            endDate = LocalDate.of(2026, 7, 28),
            recurrenceRule = RecurrenceRule.WEEKLY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 7, 1),
            rangeEnd = LocalDate.of(2026, 8, 31),
            completedDates = emptySet(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 7, 28),
            ),
            occurrences.map { it.occurrenceDate },
        )
    }

    @Test
    fun monthlyTaskDoesNotCreateOccurrencesAfterEndDate() {
        val task = task(
            startDate = LocalDate.of(2026, 1, 31),
            endDate = LocalDate.of(2026, 4, 30),
            recurrenceRule = RecurrenceRule.MONTHLY,
        )

        val occurrences = calculator.occurrencesFor(
            task = task,
            rangeStart = LocalDate.of(2026, 1, 1),
            rangeEnd = LocalDate.of(2026, 5, 31),
            completedDates = emptySet(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 31),
            ),
            occurrences.map { it.occurrenceDate },
        )
    }

    private fun task(
        startDate: LocalDate,
        endDate: LocalDate? = null,
        recurrenceRule: RecurrenceRule,
        reminderSetting: ReminderSetting = ReminderSetting(),
    ) = TodoTask(
        id = 1L,
        title = "Quest",
        memo = "",
        startDate = startDate,
        endDate = endDate,
        time = null,
        difficulty = TaskDifficulty.MEDIUM,
        category = "General",
        recurrenceRule = recurrenceRule,
        reminderSetting = reminderSetting,
    )
}
