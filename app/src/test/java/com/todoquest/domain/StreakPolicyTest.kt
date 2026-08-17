package com.todoquest.domain

import com.todoquest.domain.usecase.StreakPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakPolicyTest {
    @Test
    fun distinctOccurrenceDatesCountOnceAndActivateThreeDayMomentum() {
        val today = LocalDate.of(2026, 7, 21)

        val result = StreakPolicy.calculate(
            onTimeOccurrenceDates = listOf(
                today.minusDays(2),
                today.minusDays(1),
                today,
                today,
            ),
            referenceDate = today,
        )

        assertEquals(3, result.streakDays)
        assertEquals(300, result.momentumBonusBp)
        assertEquals(today.plusDays(1), result.momentumActiveThrough)
    }

    @Test
    fun latestOnTimeDateMayBeYesterdayButNotEarlier() {
        val today = LocalDate.of(2026, 7, 21)
        val yesterdayResult = StreakPolicy.calculate(
            listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1)),
            today,
        )
        val staleResult = StreakPolicy.calculate(
            listOf(today.minusDays(4), today.minusDays(3), today.minusDays(2)),
            today,
        )

        assertEquals(3, yesterdayResult.streakDays)
        assertEquals(300, yesterdayResult.momentumBonusBp)
        assertEquals(today, yesterdayResult.momentumActiveThrough)
        assertEquals(0, staleResult.streakDays)
        assertEquals(0, staleResult.momentumBonusBp)
        assertNull(staleResult.momentumActiveThrough)
    }

    @Test
    fun highestMomentumTierWinsWithoutStacking() {
        val today = LocalDate.of(2026, 7, 21)
        val dates = (0L..13L).map(today::minusDays)

        val result = StreakPolicy.calculate(dates, today)

        assertEquals(14, result.streakDays)
        assertEquals(800, result.momentumBonusBp)
    }

    @Test
    fun earlyCompletedFutureOccurrenceDoesNotJoinTheStreakUntilItsDate() {
        val today = LocalDate.of(2026, 7, 21)
        val futureOccurrenceDate = today.plusDays(1)
        val ledgerOccurrenceDates = listOf(today, futureOccurrenceDate)

        val todayResult = StreakPolicy.calculate(ledgerOccurrenceDates, today)
        val tomorrowResult = StreakPolicy.calculate(ledgerOccurrenceDates, futureOccurrenceDate)

        assertEquals(1, todayResult.streakDays)
        assertEquals(2, tomorrowResult.streakDays)
    }
}
