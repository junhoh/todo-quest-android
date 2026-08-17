package com.todoquest.domain

import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.usecase.MissedOccurrencePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedOccurrencePolicyTest {
    @Test
    fun timedAndUntimedOccurrencesBecomeDueOnlyAfterTheirDeadline() {
        val zoneId = ZoneId.of("Asia/Seoul")
        val date = LocalDate.of(2026, 7, 21)
        val timed = source(taskId = 1L, date = date, time = LocalTime.of(9, 0))
        val untimed = source(taskId = 2L, date = date, time = null)
        val cursor = Instant.parse("2026-07-20T00:00:00Z")

        assertTrue(
            MissedOccurrencePolicy.dueCandidates(
                sources = listOf(timed),
                cursor = cursor,
                now = Instant.parse("2026-07-21T00:15:00Z"),
                zoneId = zoneId,
            ).isEmpty(),
        )
        assertEquals(
            listOf(1L),
            MissedOccurrencePolicy.dueCandidates(
                sources = listOf(timed),
                cursor = cursor,
                now = Instant.parse("2026-07-21T00:15:00.001Z"),
                zoneId = zoneId,
            ).map { it.taskId },
        )
        assertTrue(
            MissedOccurrencePolicy.dueCandidates(
                sources = listOf(untimed),
                cursor = cursor,
                now = Instant.parse("2026-07-21T15:00:00Z"),
                zoneId = zoneId,
            ).isEmpty(),
        )
        assertEquals(
            listOf(2L),
            MissedOccurrencePolicy.dueCandidates(
                sources = listOf(untimed),
                cursor = cursor,
                now = Instant.parse("2026-07-21T15:00:00.001Z"),
                zoneId = zoneId,
            ).map { it.taskId },
        )
    }

    @Test
    fun deadlineUsesTheInjectedZoneAcrossDstTransitions() {
        val zoneId = ZoneId.of("America/New_York")
        val springForwardDate = LocalDate.of(2026, 3, 8)

        assertEquals(
            Instant.parse("2026-03-09T04:00:00Z"),
            MissedOccurrencePolicy.deadlineFor(springForwardDate, null, zoneId),
        )
        assertEquals(
            Instant.parse("2026-03-08T07:45:00Z"),
            MissedOccurrencePolicy.deadlineFor(
                springForwardDate,
                LocalTime.of(2, 30),
                zoneId,
            ),
        )
    }

    @Test
    fun candidatesRespectCursorDeletionAndSplitEndDateThenSortByDateAndTaskId() {
        val zoneId = ZoneId.of("UTC")
        val cursor = Instant.parse("2026-07-19T00:00:00Z")
        val now = Instant.parse("2026-07-23T01:00:00Z")
        val oldSplit = source(
            taskId = 30L,
            date = LocalDate.of(2026, 7, 18),
            recurrence = RecurrenceRule.DAILY,
            endDate = LocalDate.of(2026, 7, 20),
        )
        val newSplit = source(
            taskId = 20L,
            date = LocalDate.of(2026, 7, 21),
            recurrence = RecurrenceRule.DAILY,
        )
        val deleted = source(
            taskId = 10L,
            date = LocalDate.of(2026, 7, 19),
            recurrence = RecurrenceRule.DAILY,
            deletedAt = Instant.parse("2026-07-21T12:00:00Z"),
        )

        val candidates = MissedOccurrencePolicy.dueCandidates(
            sources = listOf(oldSplit, newSplit, deleted),
            cursor = cursor,
            now = now,
            zoneId = zoneId,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 18) to 30L,
                LocalDate.of(2026, 7, 19) to 10L,
                LocalDate.of(2026, 7, 19) to 30L,
                LocalDate.of(2026, 7, 20) to 10L,
                LocalDate.of(2026, 7, 20) to 30L,
                LocalDate.of(2026, 7, 21) to 20L,
                LocalDate.of(2026, 7, 22) to 20L,
            ),
            candidates.map { it.occurrenceDate to it.taskId },
        )
        assertTrue(candidates.none { it.taskId == 30L && it.occurrenceDate.isAfter(LocalDate.of(2026, 7, 20)) })
        assertTrue(candidates.none { it.taskId == 10L && it.occurrenceDate.isAfter(LocalDate.of(2026, 7, 20)) })
    }

    private fun source(
        taskId: Long,
        date: LocalDate,
        time: LocalTime? = null,
        recurrence: RecurrenceRule = RecurrenceRule.NONE,
        endDate: LocalDate? = null,
        deletedAt: Instant? = null,
    ) = MissedOccurrencePolicy.Source(
        task = TodoTask(
            id = taskId,
            title = "Quest $taskId",
            memo = "",
            startDate = date,
            endDate = endDate,
            time = time,
            difficulty = TaskDifficulty.MEDIUM,
            category = "General",
            recurrenceRule = recurrence,
        ),
        recurrenceSeriesId = 100L,
        deletedAt = deletedAt,
    )
}
