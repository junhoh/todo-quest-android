package com.todoquest.domain.usecase

import com.todoquest.domain.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object MissedOccurrencePolicy {
    private const val TIMED_GRACE_MINUTES = 15L

    data class Source(
        val task: TodoTask,
        val recurrenceSeriesId: Long,
        val deletedAt: Instant?,
    )

    data class Candidate(
        val taskId: Long,
        val recurrenceSeriesId: Long,
        val occurrenceDate: LocalDate,
        val deadline: Instant,
    )

    fun dueCandidates(
        sources: List<Source>,
        cursor: Instant,
        now: Instant,
        zoneId: ZoneId,
        occurrenceCalculator: OccurrenceCalculator = OccurrenceCalculator(),
    ): List<Candidate> {
        require(!now.isBefore(cursor)) { "now must not be before cursor" }
        if (now == cursor) return emptyList()

        val rangeStart = cursor.atZone(zoneId).toLocalDate().minusDays(1)
        val rangeEnd = now.atZone(zoneId).toLocalDate()
        return sources.flatMap { source ->
            occurrenceCalculator.occurrencesFor(
                task = source.task,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                completedDates = emptySet(),
            ).mapNotNull { occurrence ->
                val deadline = deadlineFor(
                    occurrenceDate = occurrence.occurrenceDate,
                    scheduledTime = occurrence.time,
                    zoneId = zoneId,
                )
                val isWithinCursor = !deadline.isBefore(cursor) && deadline.isBefore(now)
                val existedBeforeDeletion = source.deletedAt?.let(deadline::isBefore) ?: true
                if (isWithinCursor && existedBeforeDeletion) {
                    Candidate(
                        taskId = source.task.id,
                        recurrenceSeriesId = source.recurrenceSeriesId,
                        occurrenceDate = occurrence.occurrenceDate,
                        deadline = deadline,
                    )
                } else {
                    null
                }
            }
        }.sortedWith(
            compareBy<Candidate> { it.occurrenceDate }
                .thenBy { it.taskId },
        )
    }

    fun deadlineFor(
        occurrenceDate: LocalDate,
        scheduledTime: LocalTime?,
        zoneId: ZoneId,
    ): Instant = if (scheduledTime == null) {
        occurrenceDate.plusDays(1).atStartOfDay(zoneId).toInstant()
    } else {
        ZonedDateTime.of(occurrenceDate, scheduledTime, zoneId)
            .plusMinutes(TIMED_GRACE_MINUTES)
            .toInstant()
    }
}
