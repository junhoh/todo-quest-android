package com.todoquest.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.feature.battle.BattlePresentationState
import com.todoquest.feature.battle.BattleMapDefaults
import com.todoquest.feature.battle.BattleMapUiState
import com.todoquest.feature.battle.BattleMonsterSlots
import com.todoquest.feature.battle.BattleSpriteUiModel
import com.todoquest.feature.battle.BattleUnitType
import com.todoquest.feature.battle.BattleUnitUiModel
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.theme.TodoQuestTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarDayIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun battleMapHudPrecedesMonthAndTasksWithoutLegacySummary() {
        val date = LocalDate.of(2026, 7, 14)

        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    CalendarContent(
                        state = CalendarUiState(
                            visibleMonth = YearMonth.of(2026, 7),
                            selectedDate = date,
                            characterSummary = CalendarCharacterSummary(
                                isLoading = false,
                                level = 12,
                                xpIntoCurrentLevel = 345,
                                xpRequiredForNextLevel = 900,
                                gold = 1_234,
                            ),
                            battleMap = battleContent(),
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

        val orderedBounds = listOf(
            "battle-map",
            "calendar-month-grid",
            "calendar-task-header",
        ).map { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }

        orderedBounds.zipWithNext().forEach { (upper, lower) ->
            assertTrue("Expected $upper above $lower", upper.bottom <= lower.top)
        }
        composeRule.onNodeWithContentDescription(
            "레벨 12, 경험치 345/900, 골드 1,234",
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag("calendar-summary").assertCountEquals(0)
        composeRule.onAllNodesWithTag("calendar-selected-date").assertCountEquals(0)
        composeRule.onAllNodesWithText("Todo Quest").assertCountEquals(0)
    }

    @Test
    fun hudStaysAbovePlayerAndMonsterOnStandardAndCompactScreens() {
        val date = LocalDate.of(2026, 7, 14)
        val screenWidth = mutableStateOf(360.dp)

        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.size(width = screenWidth.value, height = 800.dp)) {
                    CalendarContent(
                        state = CalendarUiState(
                            visibleMonth = YearMonth.of(2026, 7),
                            selectedDate = date,
                            characterSummary = CalendarCharacterSummary(
                                isLoading = false,
                                level = 50,
                                xpIntoCurrentLevel = 9_876_543_210,
                                xpRequiredForNextLevel = 12_345_678_901,
                                gold = 9_876_543_210,
                            ),
                            battleMap = battleContent(),
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

        listOf(360.dp, 320.dp).forEach { width ->
            composeRule.runOnUiThread { screenWidth.value = width }
            composeRule.waitForIdle()

            val hudBounds = boundsOf("player-progress-hud")
            val playerBounds = boundsOf("battle-player-layer")
            val monsterBounds = boundsOf("battle-monster-layer")

            assertHudDoesNotExcessivelyOverlapActor(
                hudBounds = hudBounds,
                actorBounds = playerBounds,
                actorName = "player",
                screenWidth = width.toString(),
            )
            assertHudDoesNotExcessivelyOverlapActor(
                hudBounds = hudBounds,
                actorBounds = monsterBounds,
                actorName = "monster",
                screenWidth = width.toString(),
            )
        }
    }

    @Test
    fun weekdayHeadersAndFirstDayUseSundayFirstColumns() {
        val month = YearMonth.of(2026, 7)

        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    CalendarContent(
                        state = CalendarUiState(
                            visibleMonth = month,
                            selectedDate = month.atDay(1),
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

        val weekdayTags = listOf(
            "calendar-weekday-sunday" to "일",
            "calendar-weekday-monday" to "월",
            "calendar-weekday-tuesday" to "화",
            "calendar-weekday-wednesday" to "수",
            "calendar-weekday-thursday" to "목",
            "calendar-weekday-friday" to "금",
            "calendar-weekday-saturday" to "토",
        )
        val weekdayBounds = weekdayTags.map { (tag, text) ->
            composeRule.onNodeWithTag(tag)
                .assertTextContains(text)
                .fetchSemanticsNode().boundsInRoot
        }

        weekdayBounds.zipWithNext().forEach { (left, right) ->
            assertTrue("Expected weekday columns left-to-right", left.center.x < right.center.x)
        }
        val firstDayBounds = boundsOf("calendar-day-${month.atDay(1)}")
        val wednesdayBounds = weekdayBounds[3]
        assertTrue(
            "Expected July 1, 2026 in the Wednesday column",
            kotlin.math.abs(firstDayBounds.center.x - wednesdayBounds.center.x) <= 1f,
        )
    }

    @Test
    fun compactLayoutKeepsFixedBattleMapAndCalendarTaskActionsReachable() {
        val date = LocalDate.of(2026, 7, 14)
        val completed = mutableStateOf(false)
        val clickedActions = mutableSetOf<String>()
        val tasks = (1L..8L).map { taskId ->
            TaskOccurrence(
                taskId = taskId,
                title = "quest $taskId",
                memo = "",
                occurrenceDate = date,
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
                isCompleted = taskId == 8L && completed.value,
            )
        }

        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
                    CalendarContent(
                        state = CalendarUiState(
                            visibleMonth = YearMonth.of(2026, 7),
                            selectedDate = date,
                            tasks = tasks.map { task ->
                                if (task.taskId == 8L) {
                                    task.copy(isCompleted = completed.value)
                                } else {
                                    task
                                }
                            },
                        ),
                        onSelectDate = { clickedActions += "date" },
                        onShowPreviousMonth = { clickedActions += "previous" },
                        onShowNextMonth = {},
                        onShowAddTask = { clickedActions += "add" },
                        onCompleteOccurrence = { _, _ -> clickedActions += "complete" },
                        onUndoCompleteOccurrence = { _, _ -> clickedActions += "undo" },
                        onFailOccurrence = { _, _ -> clickedActions += "fail" },
                        onUndoFailOccurrence = { _, _ -> clickedActions += "undo-fail" },
                        onEditTask = { _, _ -> clickedActions += "edit" },
                        onRequestDeleteTask = { _, _, _ -> clickedActions += "delete" },
                    )
                }
            }
        }

        val scrollOwner = composeRule.onNodeWithTag("task-lazy-list")
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        val mapBounds = boundsOf("battle-map")
        val scrollBounds = boundsOf("task-lazy-list")
        assertTrue("Battle map must be a fixed sibling above the scroll", mapBounds.bottom <= scrollBounds.top)
        listOf(
            "calendar-previous-month",
            "calendar-day-$date",
            "add-task-button",
            "edit-task-8-$date",
            "delete-task-8-$date",
            "complete-task-8-$date",
        ).forEach { tag ->
            scrollOwner.performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            composeRule.onNodeWithTag(tag).performClick()
        }

        composeRule.runOnUiThread { completed.value = true }
        composeRule.waitForIdle()
        scrollOwner.performScrollToNode(hasTestTag("undo-task-8-$date"))
        composeRule.onNodeWithTag("undo-task-8-$date").performClick()

        composeRule.runOnIdle {
            assertTrue(
                clickedActions.containsAll(
                    setOf("previous", "date", "add", "edit", "delete", "complete", "undo"),
                ),
            )
        }
    }

    @Test
    fun dayCellShowsTotalAndCompletedCounts() {
        val date = LocalDate.of(2026, 7, 14)
        val summary = mutableStateOf(
            CalendarDaySummary(totalCount = 3, completedCount = 0, failedCount = 1),
        )

        composeRule.setContent {
            TodoQuestTheme {
                CalendarContent(
                    state = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 7),
                        selectedDate = date,
                        monthDaySummaries = mapOf(date to summary.value),
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

        composeRule.onNodeWithTag("calendar-day-$date").assertIsDisplayed()
        composeRule.onNodeWithText("일정 3개 · 실패 1").assertIsDisplayed()

        composeRule.runOnUiThread {
            summary.value = CalendarDaySummary(totalCount = 3, completedCount = 1, failedCount = 1)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1/3 완료 · 실패 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2026년 7월 14일, 일정 3개, 완료 1개, 실패 1개")
            .assertIsDisplayed()
    }

    @Test
    fun battleMapTopIsFixedWhileOnlyMonthAndTaskBoundsMove() {
        val date = LocalDate.of(2026, 7, 14)
        val tasks = (1L..10L).map { taskId ->
            TaskOccurrence(
                taskId = taskId,
                title = "스크롤 퀘스트 $taskId",
                memo = "",
                occurrenceDate = date,
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
                status = TaskOccurrenceStatus.TODO,
            )
        }
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    CalendarContent(
                        state = CalendarUiState(
                            visibleMonth = YearMonth.of(2026, 7),
                            selectedDate = date,
                            tasks = tasks,
                            battleMap = battleContent(),
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

        val mapBefore = boundsOf("battle-map")
        val taskHeaderBefore = boundsOf("calendar-task-header")
        composeRule.onNodeWithTag("task-lazy-list").performScrollToIndex(1)
        composeRule.waitForIdle()
        val mapAfter = boundsOf("battle-map")
        val taskHeaderAfter = boundsOf("calendar-task-header")

        assertTrue(kotlin.math.abs(mapBefore.top - mapAfter.top) <= 1f)
        assertTrue(
            "Task header should move inside the calendar scroll",
            taskHeaderAfter.top < taskHeaderBefore.top,
        )
    }

    @Test
    fun occurrenceButtonsExposeThreeStatesAndRespectProcessingAndBattleLocks() {
        val date = LocalDate.of(2026, 7, 14)
        val tasks = listOf(
            TaskOccurrence(1L, "대기", "메모", date, null, TaskDifficulty.MEDIUM, "일반", RecurrenceRule.NONE, TaskOccurrenceStatus.TODO),
            TaskOccurrence(2L, "완료됨", "", date, null, TaskDifficulty.MEDIUM, "일반", RecurrenceRule.NONE, TaskOccurrenceStatus.COMPLETED),
            TaskOccurrence(3L, "실패함", "실패 메모", date, null, TaskDifficulty.MEDIUM, "일반", RecurrenceRule.NONE, TaskOccurrenceStatus.FAILED),
        )
        val state = mutableStateOf(
            CalendarUiState(
                visibleMonth = YearMonth.of(2026, 7),
                selectedDate = date,
                tasks = tasks,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CalendarContent(
                    state = state.value,
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

        val list = composeRule.onNodeWithTag("task-lazy-list")
        list.performScrollToNode(hasTestTag("complete-task-1-$date"))
        val completeBounds = boundsOf("complete-task-1-$date")
        val failBounds = boundsOf("fail-task-1-$date")
        assertTrue(kotlin.math.abs(completeBounds.top - failBounds.top) <= 1f)
        assertTrue(completeBounds.height >= 48f && failBounds.height >= 48f)
        composeRule.onNodeWithTag("complete-task-1-$date").assertIsEnabled()
        composeRule.onNodeWithTag("fail-task-1-$date").assertIsEnabled()

        list.performScrollToNode(hasTestTag("undo-task-2-$date"))
        composeRule.onNodeWithText("완료됨").assertIsDisplayed()
        composeRule.onAllNodesWithTag("fail-task-2-$date").assertCountEquals(0)
        list.performScrollToNode(hasTestTag("undo-fail-task-3-$date"))
        composeRule.onNodeWithText("실패함").assertIsDisplayed()
        composeRule.onAllNodesWithTag("complete-task-3-$date").assertCountEquals(0)

        composeRule.runOnUiThread {
            state.value = state.value.copy(
                processingOccurrenceKeys = setOf(CalendarOccurrenceKey(1L, date)),
                battlePresentation = BattlePresentationState(queuedTransitionCount = 1),
            )
        }
        composeRule.waitForIdle()
        list.performScrollToNode(hasTestTag("complete-task-1-$date"))
        composeRule.onNodeWithTag("complete-task-1-$date").assertIsNotEnabled()
        composeRule.onNodeWithTag("fail-task-1-$date").assertIsNotEnabled()
        list.performScrollToNode(hasTestTag("undo-fail-task-3-$date"))
        composeRule.onNodeWithTag("undo-fail-task-3-$date").assertIsNotEnabled()
    }

    @Test
    fun taskRowsDescribeCombatRewardFlow() {
        val date = LocalDate.of(2026, 7, 14)
        val tasks = TaskDifficulty.entries.mapIndexed { index, difficulty ->
            TaskOccurrence(
                taskId = index.toLong() + 1,
                title = "${difficulty.name} quest",
                memo = "",
                occurrenceDate = date,
                time = null,
                difficulty = difficulty,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
                isCompleted = false,
            )
        }

        composeRule.setContent {
            TodoQuestTheme {
                CalendarContent(
                    state = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 7),
                        selectedDate = date,
                        tasks = tasks,
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

        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasText("완료하면 몬스터를 공격합니다"))
        assertTrue(
            composeRule.onAllNodesWithText("완료하면 몬스터를 공격합니다")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithTag("task-lazy-list").performScrollToNode(
            hasTestTag("reward-conditions-3-$date"),
        )
        composeRule.onNodeWithTag("reward-conditions-3-$date")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("reward-conditions-3-$date")
            .assertTextContains("공격 적중 시 경험치 · 처치 시 추가 경험치와 골드")
    }

    private fun battleContent() = BattleMapUiState.Content(
        player = BattleUnitUiModel(
            id = "player",
            type = BattleUnitType.PLAYER,
            sprite = BattleSpriteUiModel.LayeredCharacter(
                renderState = CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                ),
                frame = BattleMapDefaults.PLAYER_FRAME,
            ),
            position = BattleMapDefaults.PLAYER_POSITION,
            scale = 1f,
            groundOffset = 0f,
            currentHp = 75,
            maxHp = 100,
            nameResId = R.string.battle_player_name,
            deathAnnouncementResId = R.string.battle_player_death_announcement,
        ),
        monsters = listOf(
            BattleUnitUiModel(
                id = "monster",
                type = BattleUnitType.MONSTER,
                sprite = BattleSpriteUiModel.Resource(
                    spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
                    frame = BattleMapDefaults.MONSTER_FRAME,
                ),
                position = BattleMonsterSlots.forCount(1).single(),
                scale = 1f,
                groundOffset = 0f,
                currentHp = 40,
                maxHp = 50,
                nameResId = R.string.battle_monster_goblin_scout_name,
                deathAnnouncementResId = R.string.battle_monster_death_announcement,
            ),
        ),
        stageNumber = 7,
    )

    private fun boundsOf(testTag: String): Rect = composeRule.onNodeWithTag(
        testTag = testTag,
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun assertHudDoesNotExcessivelyOverlapActor(
        hudBounds: Rect,
        actorBounds: Rect,
        actorName: String,
        screenWidth: String,
    ) {
        val overlapHeight = (hudBounds.bottom - actorBounds.top).coerceAtLeast(0f)
        assertTrue(
            "HUD overlap with $actorName is too large at $screenWidth: " +
                "$overlapHeight/${actorBounds.height}",
            overlapHeight <= actorBounds.height * 0.25f,
        )
    }
}
