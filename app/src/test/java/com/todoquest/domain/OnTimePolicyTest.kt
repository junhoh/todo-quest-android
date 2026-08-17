package com.todoquest.domain

import com.todoquest.domain.usecase.OnTimePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnTimePolicyTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun timedOccurrenceIncludesTheFifteenMinuteDeadline() {
        val atDeadline = OnTimePolicy.evaluate(
            occurrenceDate = LocalDate.of(2026, 7, 21),
            scheduledTime = LocalTime.of(9, 0),
            completedAt = instant("2026-07-21T09:15:00"),
            zoneId = zoneId,
        )
        val afterDeadline = OnTimePolicy.evaluate(
            occurrenceDate = LocalDate.of(2026, 7, 21),
            scheduledTime = LocalTime.of(9, 0),
            completedAt = instant("2026-07-21T09:15:00.001"),
            zoneId = zoneId,
        )

        assertTrue(atDeadline.isOnTime)
        assertFalse(afterDeadline.isOnTime)
    }

    @Test
    fun untimedOccurrenceMustFinishBeforeTheNextLocalDayStarts() {
        val beforeNextDay = OnTimePolicy.evaluate(
            occurrenceDate = LocalDate.of(2026, 7, 21),
            scheduledTime = null,
            completedAt = instant("2026-07-21T23:59:59.999"),
            zoneId = zoneId,
        )
        val atNextDay = OnTimePolicy.evaluate(
            occurrenceDate = LocalDate.of(2026, 7, 21),
            scheduledTime = null,
            completedAt = instant("2026-07-22T00:00:00"),
            zoneId = zoneId,
        )

        assertTrue(beforeNextDay.isOnTime)
        assertFalse(atNextDay.isOnTime)
    }

    @Test
    fun earlyFutureCompletionIsOnTimeButUsesTheActualCompletionDateForRewards() {
        val result = OnTimePolicy.evaluate(
            occurrenceDate = LocalDate.of(2026, 7, 22),
            scheduledTime = LocalTime.of(9, 0),
            completedAt = instant("2026-07-21T12:00:00"),
            zoneId = zoneId,
        )

        assertTrue(result.isOnTime)
        assertEquals(LocalDate.of(2026, 7, 21), result.rewardLocalDate)
        assertEquals(LocalDate.of(2026, 7, 22), result.occurrenceDate)
    }

    private fun instant(localDateTime: String): Instant =
        java.time.LocalDateTime.parse(localDateTime).atZone(zoneId).toInstant()
}
