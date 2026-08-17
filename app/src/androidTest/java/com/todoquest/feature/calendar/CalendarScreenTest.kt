package com.todoquest.feature.calendar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.MainActivity
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.ui.theme.TodoQuestTheme
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun battleMapIsFixedAboveScrollableCalendarWithHudAndLegacySummaryIsAbsent() {
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.onNodeWithTag("player-progress-hud", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Todo Quest").assertCountEquals(0)
        composeRule.onAllNodesWithTag("calendar-summary").assertCountEquals(0)
        composeRule.onAllNodesWithTag("calendar-selected-date").assertCountEquals(0)

        listOf(
            "calendar-month-grid",
            "calendar-task-header",
        ).forEach { tag ->
            scrollToTag(tag)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun previousNextMonthAndDateSelectionUpdateTitleTaskQueryDateAndDayCells() {
        val initialDate = LocalDate.now()
        val previousDate = initialDate.minusMonths(1)
        val selectedPreviousDate = previousDate.withDayOfMonth(
            if (previousDate.dayOfMonth == 1) 2 else 1,
        )

        scrollToText(monthTitle(initialDate))
        composeRule.onNodeWithText(monthTitle(initialDate)).assertIsDisplayed()
        scrollToText(selectedDateText(initialDate))
        composeRule.onNodeWithText(selectedDateText(initialDate)).assertIsDisplayed()
        scrollToTag("calendar-previous-month")
        composeRule.onNodeWithContentDescription("이전 달").performClick()

        scrollToText(monthTitle(previousDate))
        composeRule.onNodeWithText(monthTitle(previousDate)).assertIsDisplayed()
        scrollToText(selectedDateText(previousDate))
        composeRule.onNodeWithText(selectedDateText(previousDate)).assertIsDisplayed()
        scrollToTag("calendar-day-$selectedPreviousDate")
        composeRule.onNodeWithTag("calendar-day-$selectedPreviousDate").performClick()
        scrollToText(selectedDateText(selectedPreviousDate))
        composeRule.onNodeWithText(selectedDateText(selectedPreviousDate)).assertIsDisplayed()

        scrollToTag("calendar-next-month")
        composeRule.onNodeWithContentDescription("다음 달").performClick()
        val selectedNextDate = selectedPreviousDate.plusMonths(1)
        scrollToText(monthTitle(initialDate))
        composeRule.onNodeWithText(monthTitle(initialDate)).assertIsDisplayed()
        scrollToText(selectedDateText(selectedNextDate))
        composeRule.onNodeWithText(selectedDateText(selectedNextDate)).assertIsDisplayed()
        scrollToTag("calendar-day-$selectedNextDate")
        composeRule.onNodeWithTag("calendar-day-$selectedNextDate").assertIsDisplayed()
    }

    @Test
    fun addButtonFromMainActivityOpensKoreanTaskEditor() {
        val notificationPromptLater = composeRule.onAllNodesWithText("나중에")
        if (notificationPromptLater.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("나중에").performClick()
            composeRule.waitForIdle()
        }
        scrollToTag("add-task-button")
        composeRule.onNodeWithText("추가").assertIsDisplayed()
        val addButton = composeRule.onNodeWithTag("add-task-button")
        addButton.assertIsDisplayed()
        composeRule.waitForIdle()

        val outputDir = additionalOutputDirectory()
        val deviceInfo = collectDeviceInfo()
        val tapTarget = calculateScreenTapTarget(addButton.fetchSemanticsNode().boundsInRoot)
        val beforeFirst = captureDisplayScreenshot()
        SystemClock.sleep(STABILITY_SCREENSHOT_INTERVAL_MILLIS)
        val beforeStable = captureDisplayScreenshot()
        val preClickNoiseRatio = beforeFirst.centerChannelDifferenceRatio(beforeStable)
        val requiredChangeRatio = maxOf(
            MINIMUM_DISPLAY_CHANGE_RATIO,
            preClickNoiseRatio + DISPLAY_CHANGE_NOISE_MARGIN,
        )
        val beforeFirstFile = File(outputDir, "task-editor-before-first-${deviceInfo.safeLabel}.png")
        val beforeStableFile = File(outputDir, "task-editor-before-stable-${deviceInfo.safeLabel}.png")
        savePng(beforeFirst, beforeFirstFile)
        savePng(beforeStable, beforeStableFile)

        val tapCommand = "input touchscreen -d 0 tap ${tapTarget.x} ${tapTarget.y}"
        val tapCommandOutput = readShellCommand(tapCommand)
        composeRule.waitForIdle()
        val displayResult = waitForDisplayChange(beforeStable, requiredChangeRatio)
        val afterFile = File(outputDir, "task-editor-after-${deviceInfo.safeLabel}.png")
        savePng(displayResult.screenshot, afterFile)
        val metadataFile = File(outputDir, "task-editor-display-${deviceInfo.safeLabel}.txt")
        writeTaskEditorDisplayMetadata(
            outputFile = metadataFile,
            deviceInfo = deviceInfo,
            tapTarget = tapTarget,
            tapCommand = tapCommand,
            tapCommandOutput = tapCommandOutput,
            preClickNoiseRatio = preClickNoiseRatio,
            requiredChangeRatio = requiredChangeRatio,
            displayResult = displayResult,
            beforeFirstFile = beforeFirstFile,
            beforeStableFile = beforeStableFile,
            afterFile = afterFile,
        )

        composeRule.onNodeWithTag("task-editor-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("할 일 추가").assertIsDisplayed()
        composeRule.onNodeWithText("제목").assertIsDisplayed()
        composeRule.onNodeWithTag("new-task-title").assertIsDisplayed()
        assertTrue(
            String.format(
                Locale.US,
                "Task editor did not reach final display composition: changeRatio=%.4f, required=%.4f, preClickNoise=%.4f, tap=(%d,%d), device=%s, sdk=%s, release=%s, emulator=%s, renderer=%s, fingerprint=%s, before=%s, after=%s, metadata=%s",
                displayResult.changeRatio,
                requiredChangeRatio,
                preClickNoiseRatio,
                tapTarget.x,
                tapTarget.y,
                deviceInfo.model,
                deviceInfo.sdk,
                deviceInfo.release,
                deviceInfo.emulatorLabel,
                deviceInfo.renderer,
                deviceInfo.fingerprint,
                beforeStableFile.absolutePath,
                afterFile.absolutePath,
                metadataFile.absolutePath,
            ),
            displayResult.changeRatio >= requiredChangeRatio,
        )
    }

    @Test
    fun reminderSelectorUsesKoreanLabelsDisabledSemanticsAndEnablesPresetsAfterTime() {
        openAddTaskDialog()
        composeRule.onNodeWithTag("task-editor-scroll")
            .performScrollToNode(hasTestTag("task-reminder-selector"))

        composeRule.onNodeWithText("알림").assertIsDisplayed()
        composeRule.onNodeWithText("설정 없음").assertIsDisplayed()
        composeRule.onNodeWithText("10분 전").assertIsDisplayed()
        composeRule.onNodeWithText("1시간 전").assertIsDisplayed()
        composeRule.onNodeWithText("직접 설정").assertIsDisplayed()
        composeRule.onNodeWithTag("task-reminder-mode-ten_minutes_before")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("task-reminder-mode-one_hour_before")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            "알림 10분 전, 일정 시간이 없어 선택할 수 없습니다.",
        ).assertExists()
        composeRule.onNodeWithText("10분 전과 1시간 전 알림은 일정 시간이 필요합니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-reminder-mode-custom_time").assertIsEnabled()

        selectClockTime(hour = 10, minute = 30)
        composeRule.onNodeWithTag("task-editor-scroll")
            .performScrollToNode(hasTestTag("task-reminder-selector"))
        composeRule.onNodeWithTag("task-reminder-mode-ten_minutes_before")
            .assertIsEnabled()
        composeRule.onNodeWithTag("task-reminder-mode-one_hour_before")
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("알림 10분 전").assertExists()
    }

    @Test
    fun firstLaunchNotificationPromptUsesKoreanRationaleConfirmAndLaterActions() {
        var confirmed = false
        var dismissed = false
        composeRule.activity.setContent {
            TodoQuestTheme {
                NotificationPermissionPromptDialog(
                    prompt = NotificationPermissionPromptUiState(
                        origin = NotificationPermissionPromptOrigin.FIRST_LAUNCH,
                    ),
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("일정 알림을 받아보세요").assertIsDisplayed()
        composeRule.onNodeWithText(
            "할 일을 놓치지 않도록 Todo Quest가 일정 알림을 보내도 될까요? " +
                "허용하지 않아도 일정 기능은 계속 사용할 수 있습니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("알림 허용").performClick()

        composeRule.runOnIdle {
            assertTrue(confirmed)
            assertTrue(!dismissed)
        }
    }

    @Test
    fun reminderNotificationPromptUsesSettingsCopyAndCanBeDismissedForLater() {
        var confirmed = false
        var dismissed = false
        composeRule.activity.setContent {
            TodoQuestTheme {
                NotificationPermissionPromptDialog(
                    prompt = NotificationPermissionPromptUiState(
                        origin = NotificationPermissionPromptOrigin.REMINDER,
                    ),
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("알림 권한을 켜 주세요").assertIsDisplayed()
        composeRule.onNodeWithText(
            "일정은 저장되었습니다. 알림을 받으려면 시스템 설정에서 Todo Quest 알림을 " +
                "허용해 주세요. 허용하지 않아도 일정 기능은 계속 사용할 수 있습니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("알림 설정").assertIsDisplayed()
        composeRule.onNodeWithText("나중에").performClick()

        composeRule.runOnIdle {
            assertTrue(!confirmed)
            assertTrue(dismissed)
        }
    }

    @Test
    fun customReminderNullValidationAndCompactEditorScrollKeepActionsReachable() {
        val title = uniqueTitle("직접 알림")
        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        composeRule.onNodeWithTag("task-editor-scroll")
            .performScrollToNode(hasTestTag("task-reminder-mode-custom_time"))
        composeRule.onNodeWithTag("task-reminder-mode-custom_time").performClick()
        composeRule.onNodeWithTag("reminder-time-picker-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-time-clear").performClick()

        composeRule.onNodeWithTag("save-task-button").performClick()
        composeRule.onNodeWithText("직접 설정 알림 시각을 선택해 주세요.").assertIsDisplayed()
        composeRule.onNodeWithTag("task-editor-dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("task-editor-scroll")
            .performScrollToNode(hasTestTag("task-reminder-custom-time-button"))
        composeRule.onNodeWithTag("task-reminder-custom-time-button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("reminder-time-picker-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-time-confirm").performClick()
        composeRule.onNodeWithTag("task-editor-scroll")
            .performScrollToNode(hasTestTag("new-task-category"))
        composeRule.onNodeWithTag("new-task-category").assertIsDisplayed()
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun taskReminderRowsShowActualLocalTriggerRecoveryAndNoScheduledClaimForTerminalTasks() {
        val date = LocalDate.of(2026, 7, 14)
        val todo = reminderOccurrence(71L, "당일 알림", date, TaskOccurrenceStatus.TODO)
        val completed = reminderOccurrence(72L, "전날 알림", date, TaskOccurrenceStatus.COMPLETED)
        val failed = reminderOccurrence(73L, "직접 알림", date, TaskOccurrenceStatus.FAILED)
        val error = reminderOccurrence(74L, "오류 알림", date, TaskOccurrenceStatus.COMPLETED)
        var recovered: CalendarOccurrenceKey? = null
        composeRule.activity.setContent {
            TodoQuestTheme {
                CalendarContent(
                    state = CalendarUiState(
                        visibleMonth = java.time.YearMonth.from(date),
                        selectedDate = date,
                        tasks = listOf(todo, completed, failed, error),
                        reminderUiStates = mapOf(
                            CalendarOccurrenceKey(71L, date) to CalendarReminderUiState(
                                mode = ReminderMode.TEN_MINUTES_BEFORE,
                                triggerTime = java.time.LocalTime.of(8, 50),
                                dayRelation = ReminderDayRelation.SAME_DAY,
                                recoveryReason =
                                    ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS,
                            ),
                            CalendarOccurrenceKey(72L, date) to CalendarReminderUiState(
                                mode = ReminderMode.ONE_HOUR_BEFORE,
                                triggerTime = java.time.LocalTime.of(23, 30),
                                dayRelation = ReminderDayRelation.PREVIOUS_DAY,
                                recoveryReason =
                                    ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL,
                            ),
                            CalendarOccurrenceKey(73L, date) to CalendarReminderUiState(
                                mode = ReminderMode.CUSTOM_TIME,
                                triggerTime = java.time.LocalTime.of(7, 5),
                                dayRelation = ReminderDayRelation.SAME_DAY,
                                recoveryReason = ReminderCapabilityRecoveryReason.EXACT_ALARM,
                            ),
                            CalendarOccurrenceKey(74L, date) to CalendarReminderUiState(
                                mode = ReminderMode.CUSTOM_TIME,
                                triggerTime = java.time.LocalTime.of(18, 40),
                                dayRelation = ReminderDayRelation.SAME_DAY,
                                hasScheduleError = true,
                            ),
                        ),
                    ),
                    onSelectDate = {},
                    onShowPreviousMonth = {},
                    onShowNextMonth = {},
                    onShowAddTask = {},
                    onCompleteOccurrence = { _, _ -> },
                    onUndoCompleteOccurrence = { _, _ -> },
                    onFailOccurrence = { _, _ -> },
                    onUndoFailOccurrence = { _, _ -> },
                    onEditTask = { _, _ -> },
                    onRequestDeleteTask = { _, _, _ -> },
                    onRecoverReminder = { taskId, occurrenceDate ->
                        recovered = CalendarOccurrenceKey(taskId, occurrenceDate)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("10분 전 · 당일 08:50"))
        composeRule.onNodeWithText("10분 전 · 당일 08:50").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("알림 10분 전, 당일 08:50").assertExists()
        composeRule.onNodeWithText("알림 권한이 필요합니다.").assertIsDisplayed()
        composeRule.onNodeWithText("알림 설정").performClick()
        composeRule.runOnIdle {
            assertEquals(CalendarOccurrenceKey(71L, date), recovered)
        }

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("1시간 전 · 전날 23:30"))
        composeRule.onNodeWithText("1시간 전 · 전날 23:30").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("알림 1시간 전, 전날 23:30").assertExists()
        composeRule.onNodeWithText("알림 채널이 꺼져 있습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("알림 채널 설정").performClick()
        composeRule.runOnIdle {
            assertEquals(CalendarOccurrenceKey(72L, date), recovered)
        }

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("직접 설정 · 당일 07:05"))
        composeRule.onNodeWithText("직접 설정 · 당일 07:05").assertIsDisplayed()
        composeRule.onNodeWithText("정확한 알림 권한이 필요합니다.").assertIsDisplayed()
        composeRule.onNodeWithText("정확한 알림 설정").performClick()
        composeRule.runOnIdle {
            assertEquals(CalendarOccurrenceKey(73L, date), recovered)
        }

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("직접 설정 · 당일 18:40"))
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("알림을 예약하지 못했습니다."))
        composeRule.onNodeWithText("알림을 예약하지 못했습니다.").assertIsDisplayed()
        composeRule.onAllNodesWithText("알림이 예약되었습니다.").assertCountEquals(0)
    }

    @Test
    fun todoOutcomeButtonsStayContentSizedAndKeepFortyEightDpTargetsAtLargeFontOnSmallWidth() {
        val date = LocalDate.of(2026, 7, 14)
        val task = reminderOccurrence(81L, "작은 화면 버튼", date, TaskOccurrenceStatus.TODO)
        val originalDensity = composeRule.activity.resources.displayMetrics.density
        composeRule.activity.setContent {
            TodoQuestTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = originalDensity,
                        fontScale = 2f,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxSize(),
                    ) {
                        CalendarContent(
                            state = CalendarUiState(
                                visibleMonth = java.time.YearMonth.from(date),
                                selectedDate = date,
                                tasks = listOf(task),
                            ),
                            onSelectDate = {},
                            onShowPreviousMonth = {},
                            onShowNextMonth = {},
                            onShowAddTask = {},
                            onCompleteOccurrence = { _, _ -> },
                            onUndoCompleteOccurrence = { _, _ -> },
                            onFailOccurrence = { _, _ -> },
                            onUndoFailOccurrence = { _, _ -> },
                            onEditTask = { _, _ -> },
                            onRequestDeleteTask = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasContentDescription("작은 화면 버튼 완료"))
        val complete = composeRule.onNodeWithContentDescription("작은 화면 버튼 완료")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val fail = composeRule.onNodeWithContentDescription("작은 화면 버튼 실패")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("task-81-$date")
            .fetchSemanticsNode().boundsInRoot
        val minimumTargetPx = 48f * originalDensity

        assertTrue("완료 버튼은 최소 48dp 높이여야 합니다.", complete.height >= minimumTargetPx)
        assertTrue("실패 버튼은 최소 48dp 높이여야 합니다.", fail.height >= minimumTargetPx)
        assertTrue("완료 버튼은 카드 절반 폭을 채우지 않아야 합니다.", complete.width < card.width / 2f)
        assertTrue("실패 버튼은 카드 절반 폭을 채우지 않아야 합니다.", fail.width < card.width / 2f)
        assertTrue("완료와 실패 버튼은 겹치지 않아야 합니다.", complete.right <= fail.left)
        assertTrue(
            "두 compact action은 Row 오른쪽에 배치되어야 합니다.",
            card.right - fail.right <= 16f * originalDensity,
        )
        composeRule.onNodeWithText("완료").assertIsDisplayed()
        composeRule.onNodeWithText("실패").assertIsDisplayed()
    }

    @Test
    fun addKoreanTitleAndMemoShowsKoreanTextInList() {
        val title = uniqueTitle("한글 제목")
        val memo = "한글 메모 ${System.currentTimeMillis()}"

        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        composeRule.onNodeWithTag("new-task-memo").performTextInput(memo)
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        assertListTextDisplayed(memo)
    }

    @Test
    fun addTaskWithKoreanCategoryShowsCategoryInMetadata() {
        val title = uniqueTitle("카테고리")

        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        composeRule.onNodeWithTag("task-category-공부").performClick()
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        assertListTextDisplayed("시간 없음  없음  보통  공부")
    }

    @Test
    fun timePickerModeSelectionShowsFormattedTimeInMetadata() {
        val title = uniqueTitle("시간 피커")

        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        selectClockTime(hour = 10, minute = 30)
        composeRule.onNodeWithTag("task-time-button").assertTextContains("10:30")
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        assertListTextDisplayed("10:30  없음  보통  일반")
    }

    @Test
    fun timeInputModeTypedValueShowsFormattedTimeInMetadata() {
        val title = uniqueTitle("시간 입력")

        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        enterTimeInput(hour = "14", minute = "45")
        composeRule.onNodeWithTag("task-time-button").assertTextContains("14:45")
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        assertListTextDisplayed("14:45  없음  보통  일반")
    }

    @Test
    fun editTaskChangesTitleMemoTimeAndCategoryInList() {
        val title = uniqueTitle("수정 전")
        val memo = "처음 메모"
        val editedTitle = uniqueTitle("수정 후")
        val editedMemo = "수정 메모 ${System.currentTimeMillis()}"

        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        composeRule.onNodeWithTag("new-task-memo").performTextInput(memo)
        enterTimeInput(hour = "08", minute = "15")
        composeRule.onNodeWithTag("task-category-업무").performClick()
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(title)
        composeRule.onNodeWithContentDescription("$title 수정").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("할 일 수정")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("할 일 수정").assertIsDisplayed()
        composeRule.onNodeWithTag("new-task-title").performTextClearance()
        composeRule.onNodeWithTag("new-task-title").performTextInput(editedTitle)
        composeRule.onNodeWithTag("new-task-memo").performTextClearance()
        composeRule.onNodeWithTag("new-task-memo").performTextInput(editedMemo)
        enterTimeInput(hour = "16", minute = "20")
        composeRule.onNodeWithTag("task-category-공부").performClick()
        composeRule.onNodeWithTag("save-task-button").performClick()

        scrollToTask(editedTitle)
        composeRule.onNodeWithText(editedTitle).assertIsDisplayed()
        assertListTextDisplayed(editedMemo)
        assertListTextDisplayed("16:20  없음  보통  공부")
    }

    @Test
    fun deleteTaskAfterConfirmationRemovesCreatedTaskFromList() {
        val title = uniqueTitle("삭제")

        createTask(title)

        scrollToTask(title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$title 삭제").performClick()
        composeRule.onNodeWithTag("delete-task-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-delete-task").performClick()
        composeRule.onNodeWithText(title).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("$title 삭제").performClick()
        composeRule.onNodeWithTag("confirm-delete-task").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText(title).assertCountEquals(0)
    }

    @Test
    fun completeAndUndoStillWorkWithEditAndDeleteButtonsPresent() {
        val title = uniqueTitle("완료")

        createTask(title)

        scrollToTask(title)
        composeRule.onNodeWithContentDescription("$title 수정").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$title 삭제").assertIsDisplayed()
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasContentDescription("$title 완료"))
        composeRule.onNodeWithContentDescription("$title 완료").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$title 완료").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("$title 완료 취소")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("+22 XP · +11 골드 · 정시 완료").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("$title 완료 취소").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$title 완료 취소").performClick()
        composeRule.onNodeWithContentDescription("$title 완료").assertIsDisplayed()
    }

    @Test
    fun completionDoesNotShowLegacyCalendarRewardSnackbar() {
        val title = uniqueTitle("전투 보상 표시")
        val legacyRewardText = "+22 XP · +11 골드 · 정시 완료"
        createTask(title)

        scrollToTask(title)
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasContentDescription("$title 완료"))
        composeRule.onNodeWithContentDescription("$title 완료").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("$title 완료 취소")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(legacyRewardText).assertCountEquals(0)
    }

    @Test
    fun monthNavigationStillWorksImmediatelyAfterCombatCompletion() {
        val title = uniqueTitle("전투 후 달 이동")
        val nextMonth = LocalDate.now().plusMonths(1)
        createTask(title)

        scrollToTask(title)
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasContentDescription("$title 완료"))
        composeRule.onNodeWithContentDescription("$title 완료").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("$title 완료 취소")
                .fetchSemanticsNodes().isNotEmpty()
        }

        scrollToTag("calendar-next-month")
        composeRule.onNodeWithTag("calendar-next-month").performClick()
        scrollToText(monthTitle(nextMonth))
        composeRule.onNodeWithText(monthTitle(nextMonth)).assertIsDisplayed()
    }

    @Test
    fun failAndUndoFailReturnOccurrenceToTodoWithoutRewardSnackbar() {
        val title = uniqueTitle("실패")
        createTask(title)

        scrollToTask(title)
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasContentDescription("$title 실패"))
        composeRule.onNodeWithContentDescription("$title 실패").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("실패").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("$title 실패 취소").assertIsDisplayed()
        composeRule.onAllNodesWithText("+22 XP · +11 골드 · 정시 완료").assertCountEquals(0)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("$title 실패 취소")
                    .fetchSemanticsNode().config
                    .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                    .not()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithContentDescription("$title 실패 취소").performClick()
        composeRule.onNodeWithContentDescription("$title 완료").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$title 실패").assertIsDisplayed()
    }

    private fun openAddTaskDialog() {
        scrollToTag("add-task-button")
        composeRule.onNodeWithTag("add-task-button").performClick()
        composeRule.onNodeWithTag("task-editor-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("할 일 추가").assertIsDisplayed()
    }

    private fun createTask(title: String) {
        openAddTaskDialog()
        composeRule.onNodeWithTag("new-task-title").performTextInput(title)
        composeRule.onNodeWithTag("save-task-button").performClick()
    }

    private fun reminderOccurrence(
        taskId: Long,
        title: String,
        occurrenceDate: LocalDate,
        status: TaskOccurrenceStatus,
    ) = TaskOccurrence(
        taskId = taskId,
        title = title,
        memo = "",
        occurrenceDate = occurrenceDate,
        time = java.time.LocalTime.of(9, 0),
        difficulty = TaskDifficulty.MEDIUM,
        category = TaskCategory.DEFAULT,
        recurrenceRule = RecurrenceRule.NONE,
        status = status,
    )

    private fun scrollToTask(title: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("task-lazy-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("task-lazy-list").performScrollToNode(hasText(title))
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun scrollToText(text: String) {
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText(text))
    }

    private fun assertListTextDisplayed(text: String) {
        composeRule.onNodeWithTag("task-lazy-list").performScrollToNode(hasText(text))
        val matchingNodes = composeRule.onAllNodesWithText(text)
        val matchingNodeCount = matchingNodes.fetchSemanticsNodes().size

        assertTrue(
            "Expected at least one displayed node with text: $text",
            (0 until matchingNodeCount).any { index -> matchingNodes[index].isDisplayed() },
        )
    }

    private fun calculateScreenTapTarget(semanticsBounds: androidx.compose.ui.geometry.Rect): TapTarget {
        var decorX = 0
        var decorY = 0
        var decorWidth = 0
        var decorHeight = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            val decorView = activity.findViewById<android.view.View>(android.R.id.content)
            val screenLocation = IntArray(2)
            decorView.getLocationOnScreen(screenLocation)
            decorX = screenLocation[0]
            decorY = screenLocation[1]
            decorWidth = decorView.width
            decorHeight = decorView.height
        }

        assertTrue("Add task semantics bounds must have positive width", semanticsBounds.width > 0f)
        assertTrue("Add task semantics bounds must have positive height", semanticsBounds.height > 0f)
        assertTrue("Activity decor must have positive width", decorWidth > 0)
        assertTrue("Activity decor must have positive height", decorHeight > 0)

        val x = (decorX + semanticsBounds.center.x).roundToInt()
            .coerceIn(decorX, decorX + decorWidth - 1)
        val y = (decorY + semanticsBounds.center.y).roundToInt()
            .coerceIn(decorY, decorY + decorHeight - 1)
        return TapTarget(
            x = x,
            y = y,
            semanticsLeft = semanticsBounds.left,
            semanticsTop = semanticsBounds.top,
            semanticsRight = semanticsBounds.right,
            semanticsBottom = semanticsBounds.bottom,
            decorX = decorX,
            decorY = decorY,
            decorWidth = decorWidth,
            decorHeight = decorHeight,
        )
    }

    private fun waitForDisplayChange(
        before: Bitmap,
        requiredChangeRatio: Double,
    ): DisplayChangeResult {
        val startedAt = SystemClock.uptimeMillis()
        val deadline = startedAt + DISPLAY_CHANGE_TIMEOUT_MILLIS
        var bestScreenshot: Bitmap? = null
        var bestChangeRatio = 0.0
        var pollCount = 0

        do {
            SystemClock.sleep(DISPLAY_POLL_INTERVAL_MILLIS)
            val candidate = captureDisplayScreenshot()
            val changeRatio = before.centerChannelDifferenceRatio(candidate)
            pollCount += 1
            if (bestScreenshot == null || changeRatio > bestChangeRatio) {
                bestScreenshot?.recycle()
                bestScreenshot = candidate
                bestChangeRatio = changeRatio
            } else {
                candidate.recycle()
            }
            if (bestChangeRatio >= requiredChangeRatio) {
                break
            }
        } while (SystemClock.uptimeMillis() < deadline)

        return DisplayChangeResult(
            screenshot = requireNotNull(bestScreenshot) {
                "Display polling did not capture a post-click screenshot"
            },
            changeRatio = bestChangeRatio,
            pollCount = pollCount,
            elapsedMillis = SystemClock.uptimeMillis() - startedAt,
        )
    }

    private fun captureDisplayScreenshot(): Bitmap {
        val pngBytes = readShellCommandBytes("screencap -p")
        return requireNotNull(BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)) {
            "UiAutomation screencap did not return a decodable full display PNG (${pngBytes.size} bytes)"
        }
    }

    private fun Bitmap.centerChannelDifferenceRatio(other: Bitmap): Double {
        require(width == other.width && height == other.height) {
            "Display screenshot size changed from ${width}x$height to ${other.width}x${other.height}"
        }
        val left = width / 4
        val top = height / 4
        val regionWidth = width / 2
        val regionHeight = height / 2
        val beforePixels = IntArray(regionWidth * regionHeight)
        val afterPixels = IntArray(regionWidth * regionHeight)
        getPixels(beforePixels, 0, regionWidth, left, top, regionWidth, regionHeight)
        other.getPixels(afterPixels, 0, regionWidth, left, top, regionWidth, regionHeight)

        var changedPixels = 0
        for (index in beforePixels.indices) {
            val beforePixel = beforePixels[index]
            val afterPixel = afterPixels[index]
            if (
                kotlin.math.abs(Color.red(beforePixel) - Color.red(afterPixel)) >= CHANNEL_DIFFERENCE_THRESHOLD ||
                kotlin.math.abs(Color.green(beforePixel) - Color.green(afterPixel)) >= CHANNEL_DIFFERENCE_THRESHOLD ||
                kotlin.math.abs(Color.blue(beforePixel) - Color.blue(afterPixel)) >= CHANNEL_DIFFERENCE_THRESHOLD
            ) {
                changedPixels += 1
            }
        }
        return changedPixels.toDouble() / beforePixels.size.toDouble()
    }

    private fun savePng(bitmap: Bitmap, outputFile: File) {
        outputFile.outputStream().use { output ->
            assertTrue(
                "Unable to encode display screenshot PNG at ${outputFile.absolutePath}",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output),
            )
        }
    }

    private fun readShellCommand(command: String): String {
        return readShellCommandBytes(command).toString(Charsets.UTF_8).trim()
    }

    private fun readShellCommandBytes(command: String): ByteArray {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input -> input.readBytes() }
        }
    }

    private fun collectDeviceInfo(): DeviceInfo {
        val kernelQemu = readShellCommand("getprop ro.kernel.qemu").orUnknown()
        return DeviceInfo(
            model = Build.MODEL.orUnknown(),
            sdk = Build.VERSION.SDK_INT.toString(),
            release = Build.VERSION.RELEASE.orUnknown(),
            fingerprint = Build.FINGERPRINT.orUnknown(),
            emulatorLabel = when (kernelQemu) {
                "1" -> "true"
                "0" -> "false"
                else -> "unknown($kernelQemu)"
            },
            renderer = readShellCommand("getprop debug.hwui.renderer").orUnknown(),
            safeLabel = safeFileToken("${Build.MODEL}-sdk${Build.VERSION.SDK_INT}-$kernelQemu"),
        )
    }

    private fun additionalOutputDirectory(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val additionalOutputDir = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val context = instrumentation.targetContext
        return (additionalOutputDir ?: context.getExternalFilesDir(null) ?: context.filesDir).also(File::mkdirs)
    }

    private fun writeTaskEditorDisplayMetadata(
        outputFile: File,
        deviceInfo: DeviceInfo,
        tapTarget: TapTarget,
        tapCommand: String,
        tapCommandOutput: String,
        preClickNoiseRatio: Double,
        requiredChangeRatio: Double,
        displayResult: DisplayChangeResult,
        beforeFirstFile: File,
        beforeStableFile: File,
        afterFile: File,
    ) {
        outputFile.writeText(
            listOf(
                "model=${deviceInfo.model}",
                "sdk=${deviceInfo.sdk}",
                "release=${deviceInfo.release}",
                "emulator=${deviceInfo.emulatorLabel}",
                "renderer=${deviceInfo.renderer}",
                "fingerprint=${deviceInfo.fingerprint}",
                "tapCommand=$tapCommand",
                "tapCommandOutput=${tapCommandOutput.replace('\n', ' ')}",
                "tapX=${tapTarget.x}",
                "tapY=${tapTarget.y}",
                String.format(
                    Locale.US,
                    "semanticsBounds=%.2f,%.2f,%.2f,%.2f",
                    tapTarget.semanticsLeft,
                    tapTarget.semanticsTop,
                    tapTarget.semanticsRight,
                    tapTarget.semanticsBottom,
                ),
                "decorOrigin=${tapTarget.decorX},${tapTarget.decorY}",
                "decorSize=${tapTarget.decorWidth}x${tapTarget.decorHeight}",
                String.format(Locale.US, "preClickNoiseRatio=%.6f", preClickNoiseRatio),
                String.format(Locale.US, "requiredChangeRatio=%.6f", requiredChangeRatio),
                String.format(Locale.US, "observedChangeRatio=%.6f", displayResult.changeRatio),
                "pollCount=${displayResult.pollCount}",
                "elapsedMillis=${displayResult.elapsedMillis}",
                "beforeFirst=${beforeFirstFile.absolutePath}",
                "beforeStable=${beforeStableFile.absolutePath}",
                "after=${afterFile.absolutePath}",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun selectClockTime(
        hour: Int,
        minute: Int,
    ) {
        composeRule.onNodeWithTag("task-time-button").performClick()
        composeRule.onNodeWithTag("task-time-picker-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("task-time-clock").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            materialTimePickerString("m3c_time_picker_hour_24h_suffix", hour),
        ).performClick()
        composeRule.onNodeWithContentDescription(
            materialTimePickerString("m3c_time_picker_minute_selection"),
        ).performClick()
        composeRule.onNodeWithContentDescription(
            materialTimePickerString("m3c_time_picker_minute_suffix", minute),
        ).performClick()
        composeRule.onNodeWithTag("task-time-confirm").performClick()
    }

    private fun enterTimeInput(hour: String, minute: String) {
        composeRule.onNodeWithTag("task-time-button").performClick()
        composeRule.onNodeWithTag("task-time-picker-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("task-time-input-toggle").performClick()
        composeRule.onNodeWithTag("task-time-input").assertIsDisplayed()
        val hourDescription = materialTimePickerString("m3c_time_picker_hour_text_field")
        val minuteDescription = materialTimePickerString("m3c_time_picker_minute_text_field")
        composeRule.onNodeWithContentDescription(hourDescription).performTextClearance()
        composeRule.onNodeWithContentDescription(hourDescription).performTextInput(hour)
        composeRule.onNodeWithContentDescription(minuteDescription).performTextClearance()
        composeRule.onNodeWithContentDescription(minuteDescription).performTextInput(minute)
        composeRule.onNodeWithTag("task-time-confirm").performClick()
    }

    private fun materialTimePickerString(name: String, vararg formatArgs: Any): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resourceId = context.resources.getIdentifier(name, "string", context.packageName)
        check(resourceId != 0) { "Missing Material time picker resource: $name" }
        return context.getString(resourceId, *formatArgs)
    }

    private fun uniqueTitle(prefix: String): String =
        "00 $prefix ${System.currentTimeMillis()}"

    private fun monthTitle(date: LocalDate): String = "${date.year}년 ${date.monthValue}월"

    private fun selectedDateText(date: LocalDate): String {
        val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
        return "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 ${weekday}요일"
    }

    private data class TapTarget(
        val x: Int,
        val y: Int,
        val semanticsLeft: Float,
        val semanticsTop: Float,
        val semanticsRight: Float,
        val semanticsBottom: Float,
        val decorX: Int,
        val decorY: Int,
        val decorWidth: Int,
        val decorHeight: Int,
    )

    private data class DisplayChangeResult(
        val screenshot: Bitmap,
        val changeRatio: Double,
        val pollCount: Int,
        val elapsedMillis: Long,
    )

    private data class DeviceInfo(
        val model: String,
        val sdk: String,
        val release: String,
        val fingerprint: String,
        val emulatorLabel: String,
        val renderer: String,
        val safeLabel: String,
    )

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: UNKNOWN_DEVICE_VALUE

    private fun safeFileToken(rawValue: String): String {
        val safeValue = rawValue
            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
            .trim('-', '_')
        return safeValue.ifBlank { UNKNOWN_DEVICE_VALUE }
    }

    private companion object {
        const val UNKNOWN_DEVICE_VALUE = "unknown"
        const val CHANNEL_DIFFERENCE_THRESHOLD = 12
        const val MINIMUM_DISPLAY_CHANGE_RATIO = 0.10
        const val DISPLAY_CHANGE_NOISE_MARGIN = 0.08
        const val STABILITY_SCREENSHOT_INTERVAL_MILLIS = 250L
        const val DISPLAY_POLL_INTERVAL_MILLIS = 100L
        const val DISPLAY_CHANGE_TIMEOUT_MILLIS = 5_000L
    }
}
