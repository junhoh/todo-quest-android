package com.todoquest.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object OnTimePolicy {
    data class Result(
        val occurrenceDate: LocalDate,
        val rewardLocalDate: LocalDate,
        val isOnTime: Boolean,
    )

    fun evaluate(
        occurrenceDate: LocalDate,
        scheduledTime: LocalTime?,
        completedAt: Instant,
        zoneId: ZoneId,
    ): Result {
        val deadline = MissedOccurrencePolicy.deadlineFor(occurrenceDate, scheduledTime, zoneId)
        val isOnTime = if (scheduledTime == null) {
            completedAt.isBefore(deadline)
        } else {
            !completedAt.isAfter(deadline)
        }
        return Result(
            occurrenceDate = occurrenceDate,
            rewardLocalDate = completedAt.atZone(zoneId).toLocalDate(),
            isOnTime = isOnTime,
        )
    }

}
