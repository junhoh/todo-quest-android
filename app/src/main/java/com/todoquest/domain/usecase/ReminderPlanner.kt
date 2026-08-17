package com.todoquest.domain.usecase

import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlanner(
    private val occurrenceCalculator: OccurrenceCalculator = OccurrenceCalculator(),
) {
    fun triggerFor(
        task: TodoTask,
        occurrenceDate: LocalDate,
        setting: ReminderSetting,
        zoneId: ZoneId,
    ): Instant? {
        if (!occurrenceCalculator.occursOn(task, occurrenceDate)) return null
        return triggerLocalDateTime(
            occurrenceDate = occurrenceDate,
            taskTime = task.time,
            setting = setting,
        )?.atZone(zoneId)?.toInstant()
    }

    fun triggerLocalDateTime(
        occurrenceDate: LocalDate,
        taskTime: LocalTime?,
        setting: ReminderSetting,
    ): LocalDateTime? = when (setting.mode) {
        ReminderMode.NONE -> null
        ReminderMode.TEN_MINUTES_BEFORE -> taskTime
            ?.let { occurrenceDate.atTime(it).minusMinutes(TEN_MINUTES) }
        ReminderMode.ONE_HOUR_BEFORE -> taskTime
            ?.let { occurrenceDate.atTime(it).minusHours(ONE_HOUR) }
        ReminderMode.CUSTOM_TIME -> LocalDateTime.of(
            occurrenceDate,
            requireNotNull(setting.customTime),
        )
    }

    fun nextFuturePlan(
        task: TodoTask,
        now: Instant,
        zoneId: ZoneId,
        setting: ReminderSetting = task.reminderSetting,
        ineligibleOccurrenceDates: Set<LocalDate> = emptySet(),
    ): ReminderPlan? {
        if (setting.mode == ReminderMode.NONE) return null
        if (setting.mode != ReminderMode.CUSTOM_TIME && task.time == null) return null

        val localToday = now.atZone(zoneId).toLocalDate()
        var rangeStart = maxOf(task.startDate, localToday)
        if (task.endDate?.isBefore(rangeStart) == true) return null

        while (true) {
            val windowEnd = rangeStart.plusDays(SEARCH_WINDOW_DAYS)
            val rangeEnd = task.endDate?.let { minOf(it, windowEnd) } ?: windowEnd
            val plan = occurrenceCalculator.occurrencesFor(
                task = task,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                completedDates = emptySet(),
            ).asSequence()
                .filterNot { it.occurrenceDate in ineligibleOccurrenceDates }
                .mapNotNull { occurrence ->
                    triggerFor(
                        task = task,
                        occurrenceDate = occurrence.occurrenceDate,
                        setting = setting,
                        zoneId = zoneId,
                    )?.let { triggerAt ->
                        ReminderPlan(
                            key = ReminderOccurrenceKey(task.id, occurrence.occurrenceDate),
                            triggerAt = triggerAt,
                        )
                    }
                }
                .firstOrNull { candidate -> candidate.triggerAt.isAfter(now) }
            if (plan != null) return plan

            val reachedEndDate = task.endDate?.let { !rangeEnd.isBefore(it) } == true
            if (task.recurrenceRule == RecurrenceRule.NONE || reachedEndDate) return null
            rangeStart = rangeEnd.plusDays(1)
        }
    }

    private companion object {
        const val TEN_MINUTES = 10L
        const val ONE_HOUR = 1L
        const val SEARCH_WINDOW_DAYS = 366L
    }
}
