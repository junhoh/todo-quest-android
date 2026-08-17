package com.todoquest.domain

import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.usecase.ReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun reminderSettingRequiresCustomTimeOnlyForCustomMode() {
        assertEquals(ReminderSetting(), ReminderSetting(ReminderMode.NONE))
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSetting(ReminderMode.CUSTOM_TIME)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE, LocalTime.NOON)
        }
    }

    @Test
    fun scheduleStatusesExposeTheCanonicalTypedValues() {
        assertEquals(
            listOf(
                ReminderScheduleStatus.DISABLED,
                ReminderScheduleStatus.PENDING,
                ReminderScheduleStatus.SCHEDULED,
                ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
                ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED,
                ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
                ReminderScheduleStatus.DELIVERED,
                ReminderScheduleStatus.NO_FUTURE_OCCURRENCE,
                ReminderScheduleStatus.ERROR,
            ),
            ReminderScheduleStatus.entries,
        )
    }

    @Test
    fun localTriggerApiIsTheSingleSourceForModeOffsetsAndDateRollover() {
        val occurrenceDate = LocalDate.of(2026, 7, 29)

        assertNull(
            planner.triggerLocalDateTime(
                occurrenceDate = occurrenceDate,
                taskTime = LocalTime.of(9, 0),
                setting = ReminderSetting(),
            ),
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 28, 23, 55),
            planner.triggerLocalDateTime(
                occurrenceDate = occurrenceDate,
                taskTime = LocalTime.of(0, 5),
                setting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
            ),
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 28, 23, 30),
            planner.triggerLocalDateTime(
                occurrenceDate = occurrenceDate,
                taskTime = LocalTime.of(0, 30),
                setting = ReminderSetting(ReminderMode.ONE_HOUR_BEFORE),
            ),
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 29, 20, 30),
            planner.triggerLocalDateTime(
                occurrenceDate = occurrenceDate,
                taskTime = null,
                setting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(20, 30)),
            ),
        )
        assertNull(
            planner.triggerLocalDateTime(
                occurrenceDate = occurrenceDate,
                taskTime = null,
                setting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
            ),
        )
    }

    @Test
    fun fourModesUseOffsetsOrOccurrenceLocalCustomTime() {
        val occurrenceDate = LocalDate.of(2026, 7, 29)
        val task = task(
            startDate = occurrenceDate,
            time = LocalTime.of(10, 0),
        )

        assertNull(
            planner.triggerFor(task, occurrenceDate, ReminderSetting(), seoul),
        )
        assertEquals(
            Instant.parse("2026-07-29T00:50:00Z"),
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
                seoul,
            ),
        )
        assertEquals(
            Instant.parse("2026-07-29T00:00:00Z"),
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.ONE_HOUR_BEFORE),
                seoul,
            ),
        )
        assertEquals(
            Instant.parse("2026-07-29T11:30:00Z"),
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(20, 30)),
                seoul,
            ),
        )
    }

    @Test
    fun presetModesWithoutTaskTimeDoNotCreatePlansWhileCustomTimeStillDoes() {
        val occurrenceDate = LocalDate.of(2026, 7, 29)
        val task = task(startDate = occurrenceDate, time = null)

        assertNull(
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
                seoul,
            ),
        )
        assertNull(
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.ONE_HOUR_BEFORE),
                seoul,
            ),
        )
        assertEquals(
            Instant.parse("2026-07-29T03:00:00Z"),
            planner.triggerFor(
                task,
                occurrenceDate,
                ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.NOON),
                seoul,
            ),
        )
    }

    @Test
    fun presetOffsetsCanTriggerOnThePreviousLocalDate() {
        val occurrenceDate = LocalDate.of(2026, 7, 29)

        assertEquals(
            Instant.parse("2026-07-28T14:55:00Z"),
            planner.triggerFor(
                task(startDate = occurrenceDate, time = LocalTime.of(0, 5)),
                occurrenceDate,
                ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
                seoul,
            ),
        )
        assertEquals(
            Instant.parse("2026-07-28T14:30:00Z"),
            planner.triggerFor(
                task(startDate = occurrenceDate, time = LocalTime.of(0, 30)),
                occurrenceDate,
                ReminderSetting(ReminderMode.ONE_HOUR_BEFORE),
                seoul,
            ),
        )
    }

    @Test
    fun oneTimeTaskReturnsItsOnlyStrictlyFuturePlan() {
        val occurrenceDate = LocalDate.of(2026, 7, 29)
        val task = task(
            startDate = occurrenceDate,
            recurrenceRule = RecurrenceRule.NONE,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0)),
        )

        assertEquals(
            ReminderPlan(
                key = ReminderOccurrenceKey(task.id, occurrenceDate),
                triggerAt = Instant.parse("2026-07-29T00:00:00Z"),
            ),
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-28T23:59:59Z"),
                zoneId = seoul,
            ),
        )
    }

    @Test
    fun dailyTaskSkipsPastAndExactlyCurrentTriggers() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 27),
            recurrenceRule = RecurrenceRule.DAILY,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0)),
        )

        assertEquals(
            LocalDate.of(2026, 7, 30),
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-29T00:00:00Z"),
                zoneId = seoul,
            )?.key?.occurrenceDate,
        )
    }

    @Test
    fun nextPlanHasNoArbitraryOneYearOccurrenceHorizon() {
        val startDate = LocalDate.of(2026, 7, 29)
        val task = task(
            startDate = startDate,
            recurrenceRule = RecurrenceRule.DAILY,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0)),
        )
        val ineligibleDates = (0L..366L).mapTo(mutableSetOf()) { startDate.plusDays(it) }

        assertEquals(
            startDate.plusDays(367),
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-28T23:00:00Z"),
                zoneId = seoul,
                ineligibleOccurrenceDates = ineligibleDates,
            )?.key?.occurrenceDate,
        )
    }

    @Test
    fun weeklyTaskUsesOccurrenceCalculatorDates() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 28),
            recurrenceRule = RecurrenceRule.WEEKLY,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(8, 0)),
        )

        assertEquals(
            LocalDate.of(2026, 8, 4),
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-28T00:00:00Z"),
                zoneId = seoul,
            )?.key?.occurrenceDate,
        )
    }

    @Test
    fun monthlyTaskSkipsMissingDayAndIneligibleOccurrence() {
        val task = task(
            startDate = LocalDate.of(2026, 1, 31),
            recurrenceRule = RecurrenceRule.MONTHLY,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(8, 0)),
        )

        assertEquals(
            LocalDate.of(2026, 5, 31),
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-02-01T00:00:00Z"),
                zoneId = seoul,
                ineligibleOccurrenceDates = setOf(LocalDate.of(2026, 3, 31)),
            )?.key?.occurrenceDate,
        )
    }

    @Test
    fun endDatePreventsSchedulingBeyondTheActiveTaskSegment() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 28),
            endDate = LocalDate.of(2026, 7, 29),
            recurrenceRule = RecurrenceRule.DAILY,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0)),
        )

        assertNull(
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-29T00:00:00Z"),
                zoneId = seoul,
            ),
        )
    }

    @Test
    fun pastOneTimeTriggerIsNotConvertedIntoAnImmediatePlan() {
        val task = task(
            startDate = LocalDate.of(2026, 7, 29),
            recurrenceRule = RecurrenceRule.NONE,
            reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0)),
        )

        assertNull(
            planner.nextFuturePlan(
                task = task,
                now = Instant.parse("2026-07-29T00:00:01Z"),
                zoneId = seoul,
            ),
        )
    }

    @Test
    fun dstGapUsesStandardAtZoneForwardAdjustment() {
        val newYork = ZoneId.of("America/New_York")
        val occurrenceDate = LocalDate.of(2026, 3, 8)

        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            planner.triggerFor(
                task(startDate = occurrenceDate),
                occurrenceDate,
                ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(2, 30)),
                newYork,
            ),
        )
    }

    @Test
    fun dstOverlapUsesStandardAtZoneEarlierOffset() {
        val newYork = ZoneId.of("America/New_York")
        val occurrenceDate = LocalDate.of(2026, 11, 1)

        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z"),
            planner.triggerFor(
                task(startDate = occurrenceDate),
                occurrenceDate,
                ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(1, 30)),
                newYork,
            ),
        )
    }

    private fun task(
        startDate: LocalDate,
        endDate: LocalDate? = null,
        time: LocalTime? = null,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        reminderSetting: ReminderSetting = ReminderSetting(),
    ) = TodoTask(
        id = 41L,
        title = "Quest",
        memo = "",
        startDate = startDate,
        time = time,
        difficulty = TaskDifficulty.MEDIUM,
        category = "General",
        recurrenceRule = recurrenceRule,
        endDate = endDate,
        reminderSetting = reminderSetting,
    )
}
