package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatBalanceConfig
import java.time.LocalDate

object StreakPolicy {
    data class Result(
        val streakDays: Int,
        val momentumBonusBp: Int,
        val momentumActiveThrough: LocalDate?,
    )

    fun calculate(
        onTimeOccurrenceDates: Iterable<LocalDate>,
        referenceDate: LocalDate,
        config: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
    ): Result {
        val eligibleDates = onTimeOccurrenceDates
            .filterNot { it.isAfter(referenceDate) }
            .toSet()
        val latestDate = eligibleDates.maxOrNull()
            ?: return noActiveStreak()
        if (latestDate != referenceDate && latestDate != referenceDate.minusDays(1)) {
            return noActiveStreak()
        }

        var streakDays = 0
        var cursor = latestDate
        while (cursor in eligibleDates) {
            streakDays += 1
            cursor = cursor.minusDays(1)
        }
        val momentumBonusBp = when {
            streakDays >= 14 -> config.momentumFourteenDayBonusBp
            streakDays >= 7 -> config.momentumSevenDayBonusBp
            streakDays >= 3 -> config.momentumThreeDayBonusBp
            else -> 0
        }
        return Result(
            streakDays = streakDays,
            momentumBonusBp = momentumBonusBp,
            momentumActiveThrough = latestDate.plusDays(1).takeIf { momentumBonusBp > 0 },
        )
    }

    private fun noActiveStreak(): Result = Result(
        streakDays = 0,
        momentumBonusBp = 0,
        momentumActiveThrough = null,
    )
}
