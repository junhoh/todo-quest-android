package com.todoquest.feature.calendar

import com.todoquest.R
import com.todoquest.audio.BattleSfx
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.core.AppClock
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterCurrentState
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterProfile
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CompletionRewardMode
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.StageProgress
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskMutationResult
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.CombatProcessingDiagnosticSink
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.DerivedStatsCalculator
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.OccurrenceCalculator
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import com.todoquest.domain.usecase.UndoCompleteOccurrenceUseCase
import com.todoquest.feature.battle.BattleAnimationPhase
import com.todoquest.feature.battle.BattleAnimationTimeline
import com.todoquest.feature.battle.BattleMapDefaults
import com.todoquest.feature.battle.BattleMapUiState
import com.todoquest.feature.battle.BattleMonsterSlots
import com.todoquest.feature.battle.BattleSpriteUiModel
import com.todoquest.feature.battle.BattleUnitType
import com.todoquest.feature.battle.StatusEffectRemainingTimeUiState
import com.todoquest.ui.character.CharacterRenderState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeTaskRepository
    private lateinit var characterRepository: FakeCharacterRepository
    private lateinit var combatRepository: FakeCombatRepository
    private lateinit var statusEffectRepository: FakeStatusEffectRepository
    private lateinit var combatFailures: MutableList<Throwable>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTaskRepository()
        characterRepository = FakeCharacterRepository()
        combatRepository = FakeCombatRepository()
        statusEffectRepository = FakeStatusEffectRepository(FixedClock)
        combatFailures = mutableListOf()
        repository.onRewardGranted = characterRepository::grantReward
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateShowsSelectedDateOccurrences() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 1L,
                title = "Daily quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "General",
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(YearMonth.of(2026, 7), state.visibleMonth)
        assertEquals(LocalDate.of(2026, 7, 14), state.selectedDate)
        assertEquals(listOf("Daily quest"), state.tasks.map { it.title })
    }

    @Test
    fun characterSummaryStartsLoadingBeforeCharacterSnapshotArrives() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(
            CalendarCharacterSummary(
                isLoading = true,
                level = 1,
                xpIntoCurrentLevel = 0,
                xpRequiredForNextLevel = 0,
                gold = 0,
            ),
            viewModel.uiState.value.characterSummary,
        )

        advanceUntilIdle()

        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 1,
                xpIntoCurrentLevel = 0,
                xpRequiredForNextLevel = 100,
                gold = 0,
            ),
            viewModel.uiState.value.characterSummary,
        )
    }

    @Test
    fun battleMapStartsLoadingBeforeCombatSnapshotArrives() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(BattleMapUiState.Loading, viewModel.uiState.value.battleMap)

        advanceUntilIdle()
        assertEquals(BattleMapUiState.Loading, viewModel.uiState.value.battleMap)
    }

    @Test
    fun combatSnapshotMapsToSingleMonsterBattleMapContent() = runTest(dispatcher) {
        combatRepository.emitSnapshot(combatSnapshot())
        val viewModel = viewModel()
        advanceUntilIdle()

        val battleMap = viewModel.uiState.value.battleMap as BattleMapUiState.Content
        assertEquals(7, battleMap.stageNumber)
        assertEquals(48, battleMap.player.currentHp)
        assertEquals(80, battleMap.player.maxHp)
        assertEquals(BattleUnitType.PLAYER, battleMap.player.type)
        assertEquals(
            BattleSpriteUiModel.LayeredCharacter(
                renderState = CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                ),
                frame = BattleMapDefaults.PLAYER_FRAME,
            ),
            battleMap.player.sprite,
        )
        assertEquals(BattleMapDefaults.PLAYER_FRAME, battleMap.player.sprite.frame)
        assertEquals(BattleMapDefaults.PLAYER_POSITION, battleMap.player.position)
        assertEquals(R.string.battle_player_name, battleMap.player.nameResId)
        assertEquals(
            R.string.battle_player_death_announcement,
            battleMap.player.deathAnnouncementResId,
        )

        val monster = battleMap.monsters.single()
        assertEquals("monster-42", monster.id)
        assertEquals(BattleUnitType.MONSTER, monster.type)
        assertEquals(37, monster.currentHp)
        assertEquals(55, monster.maxHp)
        assertEquals(
            R.drawable.todo_quest_skeleton_soldier_front_idle,
            (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
        )
        assertEquals(BattleMapDefaults.MONSTER_FRAME, monster.sprite.frame)
        assertEquals(BattleMonsterSlots.forCount(1).single(), monster.position)
        assertEquals(R.string.battle_monster_skeleton_soldier_name, monster.nameResId)
        assertEquals(
            R.string.battle_monster_skeleton_soldier_death_announcement,
            monster.deathAnnouncementResId,
        )
    }

    @Test
    fun eliteAttackMonsterSnapshotUsesGoblinPresentation() = runTest(dispatcher) {
        val snapshot = combatSnapshot().copy(
            activeMonster = combatSnapshot().activeMonster.copy(grade = MonsterGrade.ELITE),
            activeMonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
        )
        combatRepository.emitSnapshot(snapshot)
        val viewModel = viewModel()
        advanceUntilIdle()

        val monster = (viewModel.uiState.value.battleMap as BattleMapUiState.Content)
            .monsters.single()

        assertEquals(
            R.drawable.todo_quest_goblin_scout_front_idle,
            (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
        )
        assertEquals(R.string.battle_monster_goblin_scout_name, monster.nameResId)
        assertEquals(
            R.string.battle_monster_death_announcement,
            monster.deathAnnouncementResId,
        )
    }

    @Test
    fun balancedNormalStageOneEncounterOneSnapshotUsesTreeSpiritPresentation() =
        runTest(dispatcher) {
            val baseSnapshot = combatSnapshot()
            val snapshot = baseSnapshot.copy(
                progress = baseSnapshot.progress.copy(
                    stageNumber = 1,
                    activeMonsterInstanceId = 1L,
                ),
                activeMonster = baseSnapshot.activeMonster.copy(
                    id = 1L,
                    definitionId = "monster_balanced_v1",
                    grade = MonsterGrade.NORMAL,
                    stageNumber = 1,
                    encounterNumber = 1,
                ),
                activeMonsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            )
            combatRepository.emitSnapshot(snapshot)
            val viewModel = viewModel()
            advanceUntilIdle()

            val battleMap = viewModel.uiState.value.battleMap as BattleMapUiState.Content
            val monster = battleMap.monsters.single()

            assertEquals(1, battleMap.stageNumber)
            assertEquals("monster-1", monster.id)
            assertEquals(
                R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
                (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
            )
            assertEquals(BattleMapDefaults.MONSTER_FRAME, monster.sprite.frame)
            assertEquals(R.string.battle_monster_corrupted_tree_spirit_name, monster.nameResId)
            assertEquals(
                R.string.battle_monster_corrupted_tree_spirit_death_announcement,
                monster.deathAnnouncementResId,
            )
        }

    @Test
    fun defenseNormalStageOneEncounterThreeSnapshotUsesHarpyPresentation() =
        runTest(dispatcher) {
            val baseSnapshot = combatSnapshot()
            val snapshot = baseSnapshot.copy(
                progress = baseSnapshot.progress.copy(
                    stageNumber = 1,
                    activeMonsterInstanceId = 3L,
                ),
                activeMonster = baseSnapshot.activeMonster.copy(
                    id = 3L,
                    definitionId = "monster_defense_v1",
                    grade = MonsterGrade.NORMAL,
                    stageNumber = 1,
                    encounterNumber = 3,
                ),
                activeMonsterSpecies = MonsterSpecies.HARPY,
            )
            combatRepository.emitSnapshot(snapshot)
            val viewModel = viewModel()
            advanceUntilIdle()

            val battleMap = viewModel.uiState.value.battleMap as BattleMapUiState.Content
            val monster = battleMap.monsters.single()

            assertEquals(1, battleMap.stageNumber)
            assertEquals("monster-3", monster.id)
            assertEquals(
                R.drawable.todo_quest_harpy_front_idle,
                (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
            )
            assertEquals(BattleMapDefaults.MONSTER_FRAME, monster.sprite.frame)
            assertEquals(R.string.battle_monster_harpy_name, monster.nameResId)
            assertEquals(
                R.string.battle_monster_harpy_death_announcement,
                monster.deathAnnouncementResId,
            )
        }

    @Test
    fun snapshotSpeciesMapsSlimeWithoutViewModelStageEncounterOrGradeInterpretation() =
        runTest(dispatcher) {
            val baseSnapshot = combatSnapshot()
            val snapshot = baseSnapshot.copy(
                progress = baseSnapshot.progress.copy(
                    stageNumber = 9,
                    activeMonsterInstanceId = 91L,
                ),
                activeMonster = baseSnapshot.activeMonster.copy(
                    id = 91L,
                    definitionId = "monster_boss_v1",
                    grade = MonsterGrade.BOSS,
                    stageNumber = 9,
                    encounterNumber = 7,
                ),
                activeMonsterSpecies = MonsterSpecies.SLIME,
            )
            combatRepository.emitSnapshot(snapshot)
            val viewModel = viewModel()
            advanceUntilIdle()

            val battleMap = viewModel.uiState.value.battleMap as BattleMapUiState.Content
            val monster = battleMap.monsters.single()

            assertEquals(9, battleMap.stageNumber)
            assertEquals("monster-91", monster.id)
            assertEquals(
                R.drawable.todo_quest_slime_front_idle,
                (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
            )
            assertEquals(BattleMapDefaults.MONSTER_FRAME, monster.sprite.frame)
            assertEquals(1f, monster.scale)
            assertEquals(R.string.battle_monster_slime_name, monster.nameResId)
            assertEquals(
                R.string.battle_monster_slime_death_announcement,
                monster.deathAnnouncementResId,
            )
        }

    @Test
    fun combatFlowFailureShowsUnavailableWithoutStoppingTaskOccurrences() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 11L,
                title = "전투와 무관한 일정",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.EASY,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        combatRepository.observeFailure = IllegalStateException("combat observation failed")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(BattleMapUiState.Unavailable, viewModel.uiState.value.battleMap)
        assertEquals(listOf("전투와 무관한 일정"), viewModel.uiState.value.tasks.map { it.title })
    }

    @Test
    fun mixedLoadoutAndWeaponChangesUpdateTheBattlePlayerWithoutChangingMonsterRenderer() =
        runTest(dispatcher) {
            combatRepository.emitSnapshot(combatSnapshot())
            val viewModel = viewModel()
            advanceUntilIdle()
            val mixedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_DEFAULT,
                bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
                weaponId = null,
            )

            characterRepository.setLoadout(
                appearance = CharacterLoadoutCatalog.defaultAppearance,
                equippedItems = mixedItems,
            )
            advanceUntilIdle()

            val withoutWeapon = viewModel.uiState.value.battleMap as BattleMapUiState.Content
            assertEquals(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = mixedItems,
                ),
                (withoutWeapon.player.sprite as BattleSpriteUiModel.LayeredCharacter).renderState,
            )
            assertEquals(
                R.drawable.todo_quest_skeleton_soldier_front_idle,
                (withoutWeapon.monsters.single().sprite as BattleSpriteUiModel.Resource).spriteResId,
            )

            characterRepository.setLoadout(
                appearance = CharacterLoadoutCatalog.defaultAppearance,
                equippedItems = mixedItems.copy(
                    weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                ),
            )
            advanceUntilIdle()

            val withWeapon = viewModel.uiState.value.battleMap as BattleMapUiState.Content
            assertEquals(
                CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                (withWeapon.player.sprite as BattleSpriteUiModel.LayeredCharacter)
                    .renderState.equippedItems.weaponId,
            )
        }

    @Test
    fun previousAndNextMonthMoveSelectedDateByExactlyOneMonth() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.showPreviousMonth()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 14), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 6), viewModel.uiState.value.visibleMonth)

        viewModel.showNextMonth()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 7, 14), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 7), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun monthNavigationCrossesDecemberAndJanuaryYearBoundary() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectDate(LocalDate.of(2026, 12, 14))
        advanceUntilIdle()

        viewModel.showNextMonth()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2027, 1, 14), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2027, 1), viewModel.uiState.value.visibleMonth)

        viewModel.showPreviousMonth()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 12, 14), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun nextMonthClampsJanuaryThirtyFirstToFebruaryEnd() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectDate(LocalDate.of(2027, 1, 31))
        advanceUntilIdle()

        viewModel.showNextMonth()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2027, 2, 28), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2027, 2), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun nextMonthClampsJanuaryThirtyFirstToLeapDay() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectDate(LocalDate.of(2028, 1, 31))
        advanceUntilIdle()

        viewModel.showNextMonth()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2028, 2, 29), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2028, 2), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun notificationDestinationSelectsActiveTaskDateAndMissingTaskFallsBackToToday() =
        runTest(dispatcher) {
            val destinationDate = LocalDate.of(2026, 8, 9)
            repository.addExistingTask(
                TodoTask(
                    id = 41L,
                    title = "알림으로 열 일정",
                    memo = "",
                    startDate = destinationDate,
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openReminderDestination(taskId = 41L, occurrenceDate = destinationDate)
            advanceUntilIdle()

            assertEquals(destinationDate, viewModel.uiState.value.selectedDate)
            assertEquals(listOf("알림으로 열 일정"), viewModel.uiState.value.tasks.map { it.title })

            viewModel.openReminderDestination(taskId = 404L, occurrenceDate = destinationDate)
            advanceUntilIdle()

            assertEquals(FixedClock.today(), viewModel.uiState.value.selectedDate)
        }

    @Test
    fun monthNavigationResubscribesOccurrencesForMovedMonth() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 42L,
                title = "August quest",
                memo = "",
                startDate = LocalDate.of(2026, 8, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31),
            repository.observedRanges.last(),
        )

        viewModel.showNextMonth()
        advanceUntilIdle()

        assertEquals(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31),
            repository.observedRanges.last(),
        )
        assertEquals(listOf("August quest"), viewModel.uiState.value.tasks.map { it.title })
    }

    @Test
    fun savingNewTaskUsesSelectedDateAndHidesDialog() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("Write tests")
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddTaskDialogOpen)
        assertEquals("", state.newTaskTitle)
        assertEquals(listOf("Write tests"), state.tasks.map { it.title })
    }

    @Test
    fun savingNewTaskUsesAllEnteredFields() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("  Write tests  ")
        viewModel.updateTaskMemo("  Cover form state  ")
        viewModel.updateTaskTime(LocalTime.of(9, 30))
        viewModel.updateTaskDifficulty(TaskDifficulty.HARD)
        viewModel.updateTaskCategory("업무")
        viewModel.updateTaskRecurrenceRule(RecurrenceRule.DAILY)
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val task = state.tasks.single()
        assertEquals("Write tests", task.title)
        assertEquals("Cover form state", task.memo)
        assertEquals(LocalTime.of(9, 30), task.time)
        assertEquals(TaskDifficulty.HARD, task.difficulty)
        assertEquals("업무", task.category)
        assertEquals(RecurrenceRule.DAILY, task.recurrenceRule)
        assertEquals(TaskEditorUiState(), state.newTaskForm)
        assertFalse(state.isAddTaskDialogOpen)
    }

    @Test
    fun savingNewTaskWithEmptyTimeStoresNullTime() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("Any time task")
        viewModel.updateTaskTime(null)
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.tasks.single().time)
    }

    @Test
    fun savingNewTaskWithBlankTitleDoesNotCreateTask() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("   ")
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAddTaskDialogOpen)
        assertEquals(CalendarUiMessage.TitleRequired, state.message)
        assertEquals(emptyList<String>(), state.tasks.map { it.title })
        assertEquals(0, repository.createCallCount)
    }

    @Test
    fun addTaskEditorUsesDefaultFormValuesAndPresets() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        advanceUntilIdle()

        val editor = requireNotNull(viewModel.uiState.value.taskEditor)
        assertEquals(TaskEditorMode.ADD, editor.mode)
        assertEquals(null, editor.taskId)
        assertEquals(LocalDate.of(2026, 7, 14), editor.effectiveDate)
        assertEquals("", editor.title)
        assertEquals("", editor.memo)
        assertEquals(null, editor.time)
        assertEquals(TaskDifficulty.MEDIUM, editor.difficulty)
        assertEquals(TaskCategory.DEFAULT, editor.category)
        assertEquals(RecurrenceRule.NONE, editor.recurrenceRule)
        assertEquals(TaskCategory.PRESETS, editor.categoryPresets)
        assertEquals(ReminderMode.NONE, editor.reminderSetting.mode)
        assertEquals(null, editor.reminderSetting.customTime)
        assertEquals(ReminderScheduleStatus.DISABLED, editor.reminderStatus)
        assertFalse(editor.arePresetRemindersEnabled)
        assertFalse(editor.isReminderCustomTimePickerOpen)
    }

    @Test
    fun presetReminderRequiresTaskTimeAndRemovingTimeClearsPresetWithMessage() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.showAddTaskDialog()
            viewModel.updateTaskReminderMode(ReminderMode.TEN_MINUTES_BEFORE)
            advanceUntilIdle()

            assertEquals(
                ReminderMode.NONE,
                requireNotNull(viewModel.uiState.value.taskEditor).reminderSetting.mode,
            )

            viewModel.updateTaskTime(LocalTime.of(9, 0))
            viewModel.updateTaskReminderMode(ReminderMode.ONE_HOUR_BEFORE)
            advanceUntilIdle()

            val configured = requireNotNull(viewModel.uiState.value.taskEditor)
            assertTrue(configured.arePresetRemindersEnabled)
            assertEquals(ReminderMode.ONE_HOUR_BEFORE, configured.reminderSetting.mode)

            viewModel.updateTaskTime(null)
            advanceUntilIdle()

            val cleared = requireNotNull(viewModel.uiState.value.taskEditor)
            assertEquals(ReminderMode.NONE, cleared.reminderSetting.mode)
            assertEquals(CalendarUiMessage.ReminderClearedAfterTimeRemoved, viewModel.uiState.value.message)
        }

    @Test
    fun customReminderOwnsPickerStateAndNullTimeBlocksSave() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("직접 알림")
        viewModel.updateTaskReminderMode(ReminderMode.CUSTOM_TIME)
        advanceUntilIdle()

        val opened = requireNotNull(viewModel.uiState.value.taskEditor)
        assertEquals(ReminderMode.CUSTOM_TIME, opened.reminderSetting.mode)
        assertEquals(null, opened.reminderSetting.customTime)
        assertTrue(opened.isReminderCustomTimePickerOpen)

        viewModel.dismissTaskReminderCustomTimePicker()
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTaskEditorOpen)
        assertEquals(CalendarUiMessage.ReminderCustomTimeRequired, viewModel.uiState.value.message)
        assertEquals(0, repository.createCallCount)

        viewModel.showTaskReminderCustomTimePicker()
        viewModel.updateTaskReminderCustomTime(LocalTime.of(7, 40))
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTaskEditorOpen)
        assertEquals(
            ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(7, 40)),
            repository.lastCreateInput?.reminderSetting,
        )
    }

    @Test
    fun occurrenceRemindersMapModeActualTriggerDayAndTypedRecoveryWithoutUiCopy() =
        runTest(dispatcher) {
            val occurrenceDate = FixedClock.today()
            repository.addExistingTask(
                TodoTask(
                    id = 31L,
                    title = "당일 미리 알림",
                    memo = "",
                    startDate = occurrenceDate,
                    time = LocalTime.of(9, 0),
                    difficulty = TaskDifficulty.EASY,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
                ),
            )
            repository.addExistingTask(
                TodoTask(
                    id = 32L,
                    title = "전날 미리 알림",
                    memo = "",
                    startDate = occurrenceDate,
                    time = LocalTime.of(0, 30),
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(ReminderMode.ONE_HOUR_BEFORE),
                ),
            )
            repository.addExistingTask(
                TodoTask(
                    id = 33L,
                    title = "직접 알림",
                    memo = "",
                    startDate = occurrenceDate,
                    time = null,
                    difficulty = TaskDifficulty.HARD,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(
                        ReminderMode.CUSTOM_TIME,
                        LocalTime.of(7, 5),
                    ),
                ),
            )
            repository.addExistingTask(task(id = 34L, title = "알림 없음"))
            repository.setReminderStatus(
                taskId = 31L,
                status = ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
            )
            repository.setReminderStatus(
                taskId = 32L,
                status = ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED,
            )
            repository.setReminderStatus(
                taskId = 33L,
                status = ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
            )

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                CalendarReminderUiState(
                    mode = ReminderMode.TEN_MINUTES_BEFORE,
                    triggerTime = LocalTime.of(8, 50),
                    dayRelation = ReminderDayRelation.SAME_DAY,
                    recoveryReason = ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS,
                ),
                viewModel.uiState.value.reminderUiStates[
                    CalendarOccurrenceKey(31L, occurrenceDate)
                ],
            )
            assertEquals(
                CalendarReminderUiState(
                    mode = ReminderMode.ONE_HOUR_BEFORE,
                    triggerTime = LocalTime.of(23, 30),
                    dayRelation = ReminderDayRelation.PREVIOUS_DAY,
                    recoveryReason = ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL,
                ),
                viewModel.uiState.value.reminderUiStates[
                    CalendarOccurrenceKey(32L, occurrenceDate)
                ],
            )
            assertEquals(
                CalendarReminderUiState(
                    mode = ReminderMode.CUSTOM_TIME,
                    triggerTime = LocalTime.of(7, 5),
                    dayRelation = ReminderDayRelation.SAME_DAY,
                    recoveryReason = ReminderCapabilityRecoveryReason.EXACT_ALARM,
                ),
                viewModel.uiState.value.reminderUiStates[
                    CalendarOccurrenceKey(33L, occurrenceDate)
                ],
            )
            assertFalse(
                CalendarOccurrenceKey(34L, occurrenceDate) in
                    viewModel.uiState.value.reminderUiStates,
            )
        }

    @Test
    fun reminderRecoveryEventsKeepTaskIdAndReconcileOnlyTheReturningTask() =
        runTest(dispatcher) {
            val occurrenceDate = FixedClock.today()
            val statuses = listOf(
                41L to ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
                42L to ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED,
                43L to ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
                44L to ReminderScheduleStatus.ERROR,
                45L to ReminderScheduleStatus.SCHEDULED,
            )
            statuses.forEach { (taskId, status) ->
                repository.addExistingTask(
                    TodoTask(
                        id = taskId,
                        title = "알림 $taskId",
                        memo = "",
                        startDate = occurrenceDate,
                        time = null,
                        difficulty = TaskDifficulty.MEDIUM,
                        category = TaskCategory.DEFAULT,
                        recurrenceRule = RecurrenceRule.NONE,
                        reminderSetting = ReminderSetting(
                            ReminderMode.CUSTOM_TIME,
                            LocalTime.of(7, 5),
                        ),
                    ),
                )
                repository.setReminderStatus(taskId, status)
            }
            val reconciledTaskIds = mutableListOf<Long>()
            val events = mutableListOf<CalendarEvent>()
            val viewModel = viewModel(
                reconcileTaskReminderUseCase = { taskId ->
                    reconciledTaskIds += taskId
                    ReminderScheduleStatus.SCHEDULED
                },
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }
            advanceUntilIdle()

            viewModel.requestReminderRecovery(41L, occurrenceDate)
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenNotificationSettings(41L)), events)
            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()
            assertEquals(listOf(41L), reconciledTaskIds)

            events.clear()
            viewModel.requestReminderRecovery(42L, occurrenceDate)
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenNotificationChannelSettings(42L)), events)
            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()
            assertEquals(listOf(41L, 42L), reconciledTaskIds)

            events.clear()
            viewModel.requestReminderRecovery(43L, occurrenceDate)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isExactAlarmRationaleVisible)
            assertTrue(events.isEmpty())
            viewModel.requestExactAlarmSettings()
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenExactAlarmSettings(43L)), events)
            viewModel.onExactAlarmSettingsReturned()
            advanceUntilIdle()
            assertEquals(listOf(41L, 42L, 43L), reconciledTaskIds)

            events.clear()
            viewModel.requestReminderRecovery(44L, occurrenceDate)
            viewModel.requestReminderRecovery(45L, occurrenceDate)
            advanceUntilIdle()
            assertTrue(events.isEmpty())
            assertEquals(listOf(41L, 42L, 43L), reconciledTaskIds)
            assertTrue(
                requireNotNull(
                    viewModel.uiState.value.reminderUiStates[
                        CalendarOccurrenceKey(44L, occurrenceDate)
                    ],
                ).hasScheduleError,
            )
        }

    @Test
    fun firstLaunchPromptIsPreparedOnceAndDismissDoesNotAutomaticallyShowItAgain() =
        runTest(dispatcher) {
            var prepareCallCount = 0
            val viewModel = viewModel(
                prepareFirstLaunchNotificationPrompt = {
                    prepareCallCount += 1
                    true
                },
            )

            advanceUntilIdle()

            assertEquals(1, prepareCallCount)
            assertEquals(
                NotificationPermissionPromptUiState(
                    origin = NotificationPermissionPromptOrigin.FIRST_LAUNCH,
                ),
                viewModel.uiState.value.notificationPermissionPrompt,
            )

            viewModel.dismissNotificationPermissionPrompt()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.notificationPermissionPrompt)
            assertEquals(1, prepareCallCount)
        }

    @Test
    fun firstLaunchConfirmEmitsOneRuntimeRequestAndDenialKeepsCalendarCommandsAvailable() =
        runTest(dispatcher) {
            val events = mutableListOf<CalendarEvent>()
            val viewModel = viewModel(prepareFirstLaunchNotificationPrompt = { true })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }
            advanceUntilIdle()

            viewModel.confirmNotificationPermissionPrompt()
            advanceUntilIdle()

            assertEquals(listOf(CalendarEvent.RequestPostNotificationsPermission), events)
            assertEquals(null, viewModel.uiState.value.notificationPermissionPrompt)

            viewModel.confirmNotificationPermissionPrompt()
            viewModel.onNotificationPermissionResult(granted = false)
            advanceUntilIdle()

            assertEquals(listOf(CalendarEvent.RequestPostNotificationsPermission), events)
            assertEquals(
                CalendarUiMessage.ReminderNotificationPermissionDenied,
                viewModel.uiState.value.message,
            )

            viewModel.showAddTaskDialog()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isTaskEditorOpen)
        }

    @Test
    fun reminderPermissionWarningShowsSettingsRationaleWithoutImmediateSystemEvent() =
        runTest(dispatcher) {
            val events = mutableListOf<CalendarEvent>()
            val viewModel = viewModel(
                createTaskUseCase = { input ->
                    repository.createTask(input)
                    TaskMutationResult(
                        taskId = 41L,
                        reminderStatus = ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
                    )
                },
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.showAddTaskDialog()
            viewModel.updateTaskTitle("권한 분리")
            viewModel.updateTaskTime(LocalTime.of(12, 30))
            viewModel.updateTaskReminderMode(ReminderMode.TEN_MINUTES_BEFORE)
            viewModel.saveTaskEditor()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isTaskEditorOpen)
            assertEquals(
                ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
                repository.lastCreateInput?.reminderSetting,
            )
            assertEquals(
                CalendarUiMessage.ReminderPostNotificationsRequired,
                viewModel.uiState.value.message,
            )
            assertEquals(
                NotificationPermissionPromptUiState(
                    origin = NotificationPermissionPromptOrigin.REMINDER,
                ),
                viewModel.uiState.value.notificationPermissionPrompt,
            )
            assertEquals(emptyList<CalendarEvent>(), events)

            viewModel.confirmNotificationPermissionPrompt()
            advanceUntilIdle()

            assertEquals(listOf(CalendarEvent.OpenNotificationSettings(41L)), events)
            assertEquals(null, viewModel.uiState.value.notificationPermissionPrompt)
        }

    @Test
    fun hidingTaskEditorResetsFormState() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("Draft")
        viewModel.updateTaskMemo("Draft memo")
        viewModel.updateTaskTime(LocalTime.of(11, 45))
        viewModel.updateTaskDifficulty(TaskDifficulty.EASY)
        viewModel.updateTaskCategory("개인")
        viewModel.updateTaskRecurrenceRule(RecurrenceRule.WEEKLY)
        viewModel.hideTaskEditor()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddTaskDialogOpen)
        assertEquals(null, state.taskEditor)
        assertEquals(TaskEditorUiState(), state.newTaskForm)
        assertEquals(null, state.message)
    }

    @Test
    fun savingKoreanTitleAndMemoPreservesTextInOccurrence() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("  장보기 퀘스트  ")
        viewModel.updateTaskMemo("  우유와 계란 구매  ")
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        val occurrence = viewModel.uiState.value.tasks.single()
        assertEquals("장보기 퀘스트", occurrence.title)
        assertEquals("우유와 계란 구매", occurrence.memo)
    }

    @Test
    fun editTaskEditorLoadsExistingTaskValues() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 7L,
                title = "기존 일정",
                memo = "기존 메모",
                startDate = LocalDate.of(2026, 7, 1),
                time = LocalTime.of(8, 15),
                difficulty = TaskDifficulty.HARD,
                category = "공부",
                recurrenceRule = RecurrenceRule.WEEKLY,
                reminderSetting = ReminderSetting(
                    ReminderMode.CUSTOM_TIME,
                    LocalTime.of(7, 5),
                ),
            ),
        )
        val viewModel = viewModel(
            loadReminderStatus = { taskId ->
                assertEquals(7L, taskId)
                ReminderScheduleStatus.SCHEDULED
            },
        )

        viewModel.showEditTaskDialog(taskId = 7L, occurrenceDate = LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        val editor = requireNotNull(viewModel.uiState.value.taskEditor)
        assertEquals(TaskEditorMode.EDIT, editor.mode)
        assertEquals(7L, editor.taskId)
        assertEquals(LocalDate.of(2026, 7, 15), editor.effectiveDate)
        assertEquals("기존 일정", editor.title)
        assertEquals("기존 메모", editor.memo)
        assertEquals(LocalTime.of(8, 15), editor.time)
        assertEquals(TaskDifficulty.HARD, editor.difficulty)
        assertEquals("공부", editor.category)
        assertEquals(RecurrenceRule.WEEKLY, editor.recurrenceRule)
        assertEquals(ReminderMode.CUSTOM_TIME, editor.reminderSetting.mode)
        assertEquals(LocalTime.of(7, 5), editor.reminderSetting.customTime)
        assertEquals(ReminderScheduleStatus.SCHEDULED, editor.reminderStatus)
    }

    @Test
    fun savingEditTaskPassesUpdateInputWithEffectiveDateAndChangedFields() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 8L,
                title = "Old",
                memo = "Old memo",
                startDate = LocalDate.of(2026, 7, 1),
                time = null,
                difficulty = TaskDifficulty.EASY,
                category = "일반",
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val viewModel = viewModel(
            updateTaskUseCase = { input ->
                val taskId = repository.updateTask(input)
                TaskMutationResult(taskId, ReminderScheduleStatus.SCHEDULED)
            },
        )

        viewModel.showEditTaskDialog(taskId = 8L, occurrenceDate = LocalDate.of(2026, 7, 16))
        advanceUntilIdle()
        viewModel.updateTaskTitle("  새 제목  ")
        viewModel.updateTaskMemo("  새 메모  ")
        viewModel.updateTaskTime(LocalTime.of(13, 5))
        viewModel.updateTaskDifficulty(TaskDifficulty.HARD)
        viewModel.updateTaskCategory("건강")
        viewModel.updateTaskRecurrenceRule(RecurrenceRule.WEEKLY)
        viewModel.updateTaskReminderMode(ReminderMode.CUSTOM_TIME)
        viewModel.updateTaskReminderCustomTime(LocalTime.of(6, 20))
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        assertEquals(
            UpdateTaskInput(
                taskId = 8L,
                effectiveDate = LocalDate.of(2026, 7, 16),
                title = "새 제목",
                memo = "새 메모",
                time = LocalTime.of(13, 5),
                difficulty = TaskDifficulty.HARD,
                category = "건강",
                recurrenceRule = RecurrenceRule.WEEKLY,
                reminderSetting = ReminderSetting(
                    ReminderMode.CUSTOM_TIME,
                    LocalTime.of(6, 20),
                ),
            ),
            repository.updateInputs.single(),
        )
    }

    @Test
    fun notificationSettingsReturnReconcilesAndExplainsExactAlarmBeforeSettings() =
        runTest(dispatcher) {
            val reconciledTaskIds = mutableListOf<Long>()
            val statuses = ArrayDeque(
                listOf(
                    ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
                    ReminderScheduleStatus.SCHEDULED,
                ),
            )
            val events = mutableListOf<CalendarEvent>()
            val viewModel = viewModel(
                createTaskUseCase = { input ->
                    val taskId = repository.createTask(input)
                    TaskMutationResult(taskId, ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED)
                },
                reconcileTaskReminderUseCase = { taskId ->
                    reconciledTaskIds += taskId
                    statuses.removeFirst()
                },
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.showAddTaskDialog()
            viewModel.updateTaskTitle("순차 권한")
            viewModel.updateTaskReminderMode(ReminderMode.CUSTOM_TIME)
            viewModel.updateTaskReminderCustomTime(LocalTime.of(8, 0))
            viewModel.saveTaskEditor()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionPromptOrigin.REMINDER,
                viewModel.uiState.value.notificationPermissionPrompt?.origin,
            )

            viewModel.confirmNotificationPermissionPrompt()
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenNotificationSettings(1L)), events)
            events.clear()

            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()

            assertEquals(listOf(1L), reconciledTaskIds)
            assertTrue(viewModel.uiState.value.isExactAlarmRationaleVisible)
            assertEquals(
                CalendarUiMessage.ReminderExactAlarmAccessRequired,
                viewModel.uiState.value.message,
            )

            viewModel.requestExactAlarmSettings()
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenExactAlarmSettings(1L)), events)

            viewModel.onExactAlarmSettingsReturned()
            advanceUntilIdle()
            assertEquals(listOf(1L, 1L), reconciledTaskIds)
            assertFalse(viewModel.uiState.value.isExactAlarmRationaleVisible)
            assertEquals(null, viewModel.uiState.value.message)
        }

    @Test
    fun notificationSettingsErrorKeepsSavedTaskAndDoesNotReplaySystemPrompt() =
        runTest(dispatcher) {
            val events = mutableListOf<CalendarEvent>()
            val viewModel = viewModel(
                createTaskUseCase = { input ->
                    val taskId = repository.createTask(input)
                    TaskMutationResult(taskId, ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED)
                },
                reconcileTaskReminderUseCase = {
                    ReminderScheduleStatus.ERROR
                },
            )
            val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.showAddTaskDialog()
            viewModel.updateTaskTitle("거부해도 저장")
            viewModel.updateTaskReminderMode(ReminderMode.CUSTOM_TIME)
            viewModel.updateTaskReminderCustomTime(LocalTime.of(18, 10))
            viewModel.saveTaskEditor()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isTaskEditorOpen)
            assertEquals(listOf("거부해도 저장"), viewModel.uiState.value.tasks.map { it.title })
            assertEquals(emptyList<CalendarEvent>(), events)
            assertEquals(
                NotificationPermissionPromptOrigin.REMINDER,
                viewModel.uiState.value.notificationPermissionPrompt?.origin,
            )

            viewModel.confirmNotificationPermissionPrompt()
            advanceUntilIdle()
            assertEquals(listOf(CalendarEvent.OpenNotificationSettings(1L)), events)

            firstCollector.cancel()
            val replayed = withTimeoutOrNull(100) { viewModel.events.first() }
            assertEquals(null, replayed)

            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()
            assertEquals(CalendarUiMessage.ReminderScheduleError, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isTaskEditorOpen)
        }

    @Test
    fun notificationSettingsReturnWithoutSavedReminderSafelyClearsPromptAndMessage() =
        runTest(dispatcher) {
            val viewModel = viewModel(prepareFirstLaunchNotificationPrompt = { true })
            advanceUntilIdle()

            viewModel.confirmNotificationPermissionPrompt()
            viewModel.onNotificationPermissionResult(granted = false)
            advanceUntilIdle()
            assertEquals(
                CalendarUiMessage.ReminderNotificationPermissionDenied,
                viewModel.uiState.value.message,
            )

            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.notificationPermissionPrompt)
            assertEquals(null, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isExactAlarmRationaleVisible)
        }

    @Test
    fun confirmingDeleteTaskCallsRepositoryAndRemovesOccurrence() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 9L,
                title = "삭제할 일정",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "일반",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestDeleteTask(
            taskId = 9L,
            occurrenceDate = LocalDate.of(2026, 7, 14),
            title = "삭제할 일정",
        )
        advanceUntilIdle()
        assertEquals(
            DeleteTaskConfirmationUiState(
                taskId = 9L,
                occurrenceDate = LocalDate.of(2026, 7, 14),
                title = "삭제할 일정",
            ),
            viewModel.uiState.value.deleteConfirmation,
        )

        viewModel.confirmDeleteTask()
        advanceUntilIdle()

        assertEquals(listOf(9L to LocalDate.of(2026, 7, 14)), repository.deleteInputs)
        assertEquals(emptyList<TaskOccurrence>(), viewModel.uiState.value.tasks)
        assertEquals(null, viewModel.uiState.value.deleteConfirmation)
    }

    @Test
    fun savingUnknownKoreanCategoryNormalizesToDefaultCategory() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.showAddTaskDialog()
        viewModel.updateTaskTitle("운동하기")
        viewModel.updateTaskCategory("운동")
        viewModel.saveTaskEditor()
        advanceUntilIdle()

        assertEquals(TaskCategory.DEFAULT, viewModel.uiState.value.tasks.single().category)
    }

    @Test
    fun completingOccurrenceEmitsActualRewardAndUpdatesCharacterSummary() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 1L,
                title = "Rewarded quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.HARD,
                category = "General",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        repository.nextCompletionResult = CompletionResult(
            awardedXp = 19,
            awardedGold = 7,
            alreadyRewarded = false,
            isOnTime = true,
            rewardEfficiencyBp = 5_000,
        )
        val viewModel = viewModel()
        val events = mutableListOf<CalendarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        advanceUntilIdle()

        viewModel.completeOccurrence(taskId = 1L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.tasks.single().isCompleted)
        assertEquals(
            listOf(
                CalendarEvent.RewardGranted(
                    awardedXp = 19,
                    awardedGold = 7,
                    isOnTime = true,
                ),
            ),
            events,
        )
        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 1,
                xpIntoCurrentLevel = 19,
                xpRequiredForNextLevel = 100,
                gold = 7,
            ),
            state.characterSummary,
        )
    }

    @Test
    fun combatAttackCompletionDoesNotEmitLegacyCalendarRewardEvent() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 2L,
                title = "Combat quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = TaskCategory.DEFAULT,
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        repository.nextCompletionResult = CompletionResult(
            awardedXp = 0,
            awardedGold = 0,
            alreadyRewarded = false,
            isOnTime = true,
            rewardMode = CompletionRewardMode.COMBAT_ATTACK,
        )
        val viewModel = viewModel()
        val events = mutableListOf<CalendarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        advanceUntilIdle()

        viewModel.completeOccurrence(taskId = 2L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.tasks.single().isCompleted)
        assertEquals(emptyList<CalendarEvent>(), events)
        assertEquals(
            listOf(2L to LocalDate.of(2026, 7, 14)),
            combatRepository.playerAttackInputs,
        )
    }

    @Test
    fun combatFailureKeepsRewardUiSuccessfulAndDoesNotRepeatPlayerAttack() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 3L,
                title = "Best effort combat quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.HARD,
                category = "General",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        repository.nextCompletionResult = CompletionResult(
            awardedXp = 19,
            awardedGold = 7,
            alreadyRewarded = false,
            isOnTime = true,
        )
        val failure = IllegalStateException("combat unavailable")
        combatRepository.playerAttackFailure = failure
        val viewModel = viewModel()
        val events = mutableListOf<CalendarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        advanceUntilIdle()

        viewModel.completeOccurrence(taskId = 3L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()
        viewModel.completeOccurrence(taskId = 3L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.tasks.single().isCompleted)
        assertEquals(
            listOf(CalendarEvent.RewardGranted(awardedXp = 19, awardedGold = 7, isOnTime = true)),
            events,
        )
        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 1,
                xpIntoCurrentLevel = 19,
                xpRequiredForNextLevel = 100,
                gold = 7,
            ),
            state.characterSummary,
        )
        assertEquals(listOf(3L to LocalDate.of(2026, 7, 14)), combatRepository.playerAttackInputs)
        assertEquals(listOf(failure), combatFailures)
        assertEquals(null, state.message)
    }

    @Test
    fun undoThenRecompleteDoesNotEmitDuplicateRewardEvent() = runTest(dispatcher) {
        repository.addExistingTask(
            TodoTask(
                id = 2L,
                title = "Idempotent quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "General",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )
        val viewModel = viewModel()
        val events = mutableListOf<CalendarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        advanceUntilIdle()

        viewModel.completeOccurrence(taskId = 2L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()
        viewModel.undoCompleteOccurrence(taskId = 2L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()
        viewModel.completeOccurrence(taskId = 2L, occurrenceDate = LocalDate.of(2026, 7, 14))
        advanceUntilIdle()

        assertEquals(
            listOf(
                CalendarEvent.RewardGranted(
                    awardedXp = 20,
                    awardedGold = 10,
                    isOnTime = false,
                ),
            ),
            events,
        )
        assertEquals(20, viewModel.uiState.value.characterSummary.xpIntoCurrentLevel)
        assertEquals(100, viewModel.uiState.value.characterSummary.xpRequiredForNextLevel)
        assertEquals(10, viewModel.uiState.value.characterSummary.gold)
        assertTrue(viewModel.uiState.value.tasks.single().isCompleted)
    }

    @Test
    fun duplicateCompleteClicksAreRejectedWhileOccurrenceCommandIsProcessing() =
        runTest(dispatcher) {
            repository.addExistingTask(task(id = 21L, title = "중복 완료 방지"))
            repository.completionGate = CompletableDeferred()
            val viewModel = viewModel()
            advanceUntilIdle()
            val key = CalendarOccurrenceKey(21L, FixedClock.today())

            viewModel.completeOccurrence(21L, FixedClock.today())
            viewModel.completeOccurrence(21L, FixedClock.today())

            runCurrent()
            assertEquals(setOf(key), viewModel.uiState.value.processingOccurrenceKeys)
            assertEquals(1, repository.completeCallCount)

            repository.completionGate?.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.processingOccurrenceKeys.isEmpty())
            assertEquals(1, repository.completeCallCount)
        }

    @Test
    fun failCombatFailureKeepsFailedStateAndUndoFailRestoresTodoWithoutReward() =
        runTest(dispatcher) {
            repository.addExistingTask(task(id = 22L, title = "실패 전투 복구"))
            val combatFailure = IllegalStateException("combat retry required")
            combatRepository.monsterAttackFailure = combatFailure
            val viewModel = viewModel()
            val rewardEvents = mutableListOf<CalendarEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(rewardEvents)
            }
            advanceUntilIdle()

            viewModel.failOccurrence(22L, FixedClock.today())
            advanceUntilIdle()

            assertEquals(TaskOccurrenceStatus.FAILED, viewModel.uiState.value.tasks.single().status)
            assertEquals(listOf(22L to FixedClock.today()), combatRepository.monsterAttackInputs)
            assertEquals(listOf(combatFailure), combatFailures)
            assertEquals(null, viewModel.uiState.value.message)
            assertTrue(rewardEvents.isEmpty())

            viewModel.undoFailOccurrence(22L, FixedClock.today())
            advanceUntilIdle()

            assertEquals(TaskOccurrenceStatus.TODO, viewModel.uiState.value.tasks.single().status)
            assertEquals(1, repository.undoFailCallCount)
            assertTrue(rewardEvents.isEmpty())
        }

    @Test
    fun battleTransitionLocksEveryOutcomeCommandUntilAnimationQueueDrains() =
        runTest(dispatcher) {
            repository.addExistingTask(task(id = 23L, title = "전투 잠금 A"))
            repository.addExistingTask(task(id = 24L, title = "전투 잠금 B"))
            combatRepository.emitSnapshot(combatSnapshot())
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(1, combatRepository.eventCollectionCount)

            combatRepository.emitTransition(nonLethalPlayerTransition())
            runCurrent()

            assertEquals(
                BattleAnimationPhase.PLAYER_ATTACKING,
                viewModel.uiState.value.battlePresentation.phase,
            )
            assertTrue(viewModel.uiState.value.isBattleInputLocked)
            viewModel.failOccurrence(24L, FixedClock.today())
            runCurrent()
            assertEquals(0, repository.failCallCount)

            advanceUntilIdle()

            assertEquals(BattleAnimationPhase.IDLE, viewModel.uiState.value.battlePresentation.phase)
            assertFalse(viewModel.uiState.value.isBattleInputLocked)
            assertEquals(1, combatRepository.eventCollectionCount)
        }

    @Test
    fun battleSfxPlayerReceivesRepositoryTransitionOnceAndNeverInfersSoundFromHpState() =
        runTest(dispatcher) {
            val player = FakeBattleSfxPlayer()
            combatRepository.emitSnapshot(combatSnapshot())
            val viewModel = viewModel(battleSfxPlayer = player)
            advanceUntilIdle()

            combatRepository.emitSnapshot(
                combatSnapshot().copy(
                    activeMonster = combatSnapshot().activeMonster.copy(currentHp = 27),
                ),
            )
            advanceUntilIdle()
            assertTrue(player.requests.isEmpty())

            val transition = nonLethalPlayerTransition()
            combatRepository.emitTransition(transition)
            runCurrent()
            assertEquals(
                listOf(BattleSfx.PLAYER_ATTACK to transition.eventKey.battleEffectEventId()),
                player.requests,
            )

            testScheduler.advanceTimeBy(100L)
            runCurrent()
            assertEquals(
                listOf(
                    BattleSfx.PLAYER_ATTACK to transition.eventKey.battleEffectEventId(),
                    BattleSfx.MONSTER_HIT to transition.eventKey.battleEffectEventId(),
                ),
                player.requests,
            )
            advanceUntilIdle()
            assertEquals(2, player.requests.size)
            assertEquals(1, combatRepository.eventCollectionCount)
            assertEquals(BattleAnimationPhase.IDLE, viewModel.uiState.value.battlePresentation.phase)
        }

    @Test
    fun calendarExposesActiveSevereInjuryAndReconcilesItOnEntryAndResume() =
        runTest(dispatcher) {
            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = FixedClock.now().plusSeconds(2 * 60 * 60 + 1),
                    remainingCompletions = 2,
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            val effect = viewModel.uiState.value.activeStatusEffects.single()
            assertEquals(StatusEffectType.SEVERE_INJURY, effect.type)
            assertEquals(2, effect.remainingRecoveryCompletions)
            assertEquals(StatusEffectRemainingTimeUiState.Hours(3), effect.remainingTime)

            viewModel.onScreenEntered()
            viewModel.onLifecycleResumed()
            advanceUntilIdle()

            assertEquals(2, statusEffectRepository.reconcileCalls)
        }

    @Test
    fun calendarOwnsSevereInjuryDetailsSelectionAndDropsItWhenEffectEnds() =
        runTest(dispatcher) {
            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = FixedClock.now().plusSeconds(4 * 60 * 60),
                    remainingCompletions = 2,
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.showStatusEffectDetails(StatusEffectType.SEVERE_INJURY)
            advanceUntilIdle()
            assertEquals(
                viewModel.uiState.value.activeStatusEffects.single(),
                viewModel.uiState.value.selectedStatusEffect,
            )

            statusEffectRepository.effects.value = emptyList()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)

            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = FixedClock.now().plusSeconds(60 * 60),
                    revision = 2L,
                ),
            )
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)

            viewModel.showStatusEffectDetails(StatusEffectType.SEVERE_INJURY)
            viewModel.dismissStatusEffectDetails()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)
        }

    @Test
    fun exactExpirationOnResumeRemovesStatusAndDoesNotUseUiTimerAsAuthority() =
        runTest(dispatcher) {
            val mutableClock = MutableCalendarClock(FixedClock.now())
            val effects = FakeStatusEffectRepository(mutableClock)
            val expiresAt = mutableClock.now().plusSeconds(60 * 60)
            effects.effects.value = listOf(severeInjury(expiresAt))
            val viewModel = viewModel(
                statusEffectRepository = effects,
                clock = mutableClock,
            )
            advanceUntilIdle()

            mutableClock.instant = expiresAt
            viewModel.onLifecycleResumed()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.activeStatusEffects.isEmpty())
            assertEquals(1, effects.reconcileCalls)
        }

    @Test
    fun statusRemovalEventPlaysOnceAndARecreatedControllerDoesNotReplayIt() =
        runTest(dispatcher) {
            combatRepository.emitSnapshot(combatSnapshot())
            val firstViewModel = viewModel()
            advanceUntilIdle()
            val event = CombatLifecycleEvent.StatusEffectRemoved(
                eventId = "status-effect:removed:SEVERE_INJURY:1",
                effectType = StatusEffectType.SEVERE_INJURY,
                effectRevision = 1L,
                removedAtEpochMillis = FixedClock.now().toEpochMilli(),
            )

            statusEffectRepository.emitRemoval(event)
            statusEffectRepository.emitRemoval(event)
            runCurrent()

            assertEquals(
                BattleAnimationPhase.STATUS_EFFECT_REMOVING,
                firstViewModel.uiState.value.battlePresentation.phase,
            )
            advanceUntilIdle()

            val recreated = viewModel()
            advanceUntilIdle()

            assertEquals(BattleAnimationPhase.IDLE, recreated.uiState.value.battlePresentation.phase)
            assertEquals(2, combatRepository.eventCollectionCount)
        }

    @Test
    fun daySummaryKeepsCompletedAndFailedCountsSeparate() = runTest(dispatcher) {
        repository.addExistingTask(task(id = 25L, title = "완료 항목"))
        repository.addExistingTask(task(id = 26L, title = "실패 항목"))
        repository.addExistingTask(task(id = 27L, title = "대기 항목"))
        repository.markCompleted(25L, FixedClock.today())
        repository.markFailed(26L, FixedClock.today())
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            CalendarDaySummary(totalCount = 3, completedCount = 1, failedCount = 1),
            viewModel.uiState.value.monthDaySummaries.getValue(FixedClock.today()),
        )
    }

    @Test
    fun characterSummaryMapsGeneralLevelProgress() = runTest(dispatcher) {
        characterRepository.setProfile(
            CharacterProfile(level = 4, totalXp = 325, currentGold = 91),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 4,
                xpIntoCurrentLevel = 25,
                xpRequiredForNextLevel = 100,
                gold = 91,
            ),
            viewModel.uiState.value.characterSummary,
        )
    }

    @Test
    fun characterSummaryMapsExactLevelBoundary() = runTest(dispatcher) {
        characterRepository.setProfile(
            CharacterProfile(level = 2, totalXp = 100, currentGold = 12),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 2,
                xpIntoCurrentLevel = 0,
                xpRequiredForNextLevel = 100,
                gold = 12,
            ),
            viewModel.uiState.value.characterSummary,
        )
    }

    @Test
    fun characterSummaryTracksSeparateCharacterRepositoryUpdates() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        characterRepository.setProfile(
            CharacterProfile(level = 4, totalXp = 325, currentGold = 91),
        )
        advanceUntilIdle()

        assertEquals(
            CalendarCharacterSummary(
                isLoading = false,
                level = 4,
                xpIntoCurrentLevel = 25,
                xpRequiredForNextLevel = 100,
                gold = 91,
            ),
            viewModel.uiState.value.characterSummary,
        )
    }

    private fun viewModel(
        testDispatcher: TestDispatcher = dispatcher,
        statusEffectRepository: StatusEffectRepository = this.statusEffectRepository,
        battleSfxPlayer: BattleSfxPlayer = FakeBattleSfxPlayer(),
        clock: AppClock = FixedClock,
        createTaskUseCase: suspend (CreateTaskInput) -> TaskMutationResult = { input ->
            TaskMutationResult(
                taskId = repository.createTask(input),
                reminderStatus = if (input.reminderSetting.mode == ReminderMode.NONE) {
                    ReminderScheduleStatus.DISABLED
                } else {
                    ReminderScheduleStatus.PENDING
                },
            )
        },
        updateTaskUseCase: suspend (UpdateTaskInput) -> TaskMutationResult = { input ->
            TaskMutationResult(
                taskId = repository.updateTask(input),
                reminderStatus = if (input.reminderSetting.mode == ReminderMode.NONE) {
                    ReminderScheduleStatus.DISABLED
                } else {
                    ReminderScheduleStatus.PENDING
                },
            )
        },
        deleteTaskUseCase: suspend (Long, LocalDate) -> Unit = repository::deleteTask,
        loadReminderStatus: suspend (Long) -> ReminderScheduleStatus? = { null },
        reconcileTaskReminderUseCase: suspend (Long) -> ReminderScheduleStatus = {
            ReminderScheduleStatus.SCHEDULED
        },
        prepareFirstLaunchNotificationPrompt: suspend () -> Boolean = { false },
    ) = CalendarViewModel(
        repository = repository,
        characterRepository = characterRepository,
        combatRepository = combatRepository,
        statusEffectRepository = statusEffectRepository,
        completeOccurrence = CompleteOccurrenceUseCase(
            repository = repository,
            combatRepository = combatRepository,
            diagnosticSink = CombatProcessingDiagnosticSink { _, _, failure ->
                combatFailures += failure
            },
        ),
        undoCompleteOccurrence = UndoCompleteOccurrenceUseCase(repository),
        failOccurrence = FailOccurrenceUseCase(
            repository = repository,
            combatRepository = combatRepository,
            diagnosticSink = CombatProcessingDiagnosticSink { _, _, failure ->
                combatFailures += failure
            },
        ),
        undoFailOccurrence = UndoFailOccurrenceUseCase(repository),
        clock = clock,
        dispatcher = testDispatcher,
        createTaskUseCase = createTaskUseCase,
        updateTaskUseCase = updateTaskUseCase,
        deleteTaskUseCase = deleteTaskUseCase,
        loadReminderStatus = loadReminderStatus,
        reconcileTaskReminderUseCase = reconcileTaskReminderUseCase,
        prepareFirstLaunchNotificationPrompt = prepareFirstLaunchNotificationPrompt,
        battleSfxPlayer = battleSfxPlayer,
        battleAnimationTimeline = BattleAnimationTimeline(
            advanceMillis = 100L,
            hitMillis = 100L,
            deathMillis = 100L,
            monsterSpawnAlertMillis = 100L,
            spawnOrRecoveryMillis = 100L,
        ),
    )

    private fun task(id: Long, title: String) = TodoTask(
        id = id,
        title = title,
        memo = "",
        startDate = FixedClock.today(),
        time = null,
        difficulty = TaskDifficulty.MEDIUM,
        category = TaskCategory.DEFAULT,
        recurrenceRule = RecurrenceRule.NONE,
    )

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = Instant.parse("2026-07-14T09:00:00Z")
        override fun today(): LocalDate = LocalDate.of(2026, 7, 14)
    }

    private class FakeTaskRepository : TaskRepository {
        private val calculator = OccurrenceCalculator()
        private val tasks = MutableStateFlow<List<TodoTask>>(emptyList())
        private val completed = MutableStateFlow<Set<Pair<Long, LocalDate>>>(emptySet())
        private val failed = MutableStateFlow<Set<Pair<Long, LocalDate>>>(emptySet())
        private val reminderStatuses =
            MutableStateFlow<Map<Long, ReminderScheduleStatus>>(emptyMap())
        private val rewarded = mutableSetOf<Pair<Long, LocalDate>>()
        private var nextId = 1L
        var nextCompletionResult: CompletionResult? = null
        var onRewardGranted: (CompletionResult) -> Unit = {}
        var createCallCount = 0
            private set
        var completeCallCount = 0
            private set
        var failCallCount = 0
            private set
        var undoFailCallCount = 0
            private set
        var completionGate: CompletableDeferred<Unit>? = null
        var lastCreateInput: CreateTaskInput? = null
            private set
        val updateInputs = mutableListOf<UpdateTaskInput>()
        val deleteInputs = mutableListOf<Pair<Long, LocalDate>>()
        val observedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

        fun addExistingTask(task: TodoTask) {
            tasks.value = tasks.value + task
            nextId = maxOf(nextId, task.id + 1L)
        }

        fun markCompleted(taskId: Long, occurrenceDate: LocalDate) {
            completed.value += taskId to occurrenceDate
        }

        fun markFailed(taskId: Long, occurrenceDate: LocalDate) {
            failed.value += taskId to occurrenceDate
        }

        fun setReminderStatus(taskId: Long, status: ReminderScheduleStatus) {
            reminderStatuses.value += taskId to status
        }

        override fun observeOccurrences(
            rangeStart: LocalDate,
            rangeEnd: LocalDate,
        ): Flow<List<TaskOccurrence>> {
            observedRanges += rangeStart to rangeEnd
            return combine(
                tasks,
                completed,
                failed,
                reminderStatuses,
            ) { allTasks, completedSet, failedSet, reminderStatusByTaskId ->
                allTasks.flatMap { task ->
                    val completedDates = completedSet
                        .filter { it.first == task.id }
                        .map { it.second }
                        .toSet()
                    calculator.occurrencesFor(task, rangeStart, rangeEnd, completedDates)
                        .map { occurrence ->
                            occurrence.copy(
                                status = when {
                                    task.id to occurrence.occurrenceDate in completedSet -> {
                                        TaskOccurrenceStatus.COMPLETED
                                    }
                                    task.id to occurrence.occurrenceDate in failedSet -> {
                                        TaskOccurrenceStatus.FAILED
                                    }
                                    else -> TaskOccurrenceStatus.TODO
                                },
                                reminderScheduleStatus =
                                    reminderStatusByTaskId[task.id]
                                        ?: ReminderScheduleStatus.DISABLED,
                            )
                        }
                }
            }
        }

        override suspend fun createTask(input: CreateTaskInput): Long {
            createCallCount += 1
            lastCreateInput = input
            val id = nextId++
            tasks.value = tasks.value + TodoTask(
                id = id,
                title = input.title,
                memo = input.memo,
                startDate = input.startDate,
                time = input.time,
                difficulty = input.difficulty,
                category = TaskCategory.normalize(input.category),
                recurrenceRule = input.recurrenceRule,
                reminderSetting = input.reminderSetting,
            )
            return id
        }

        override suspend fun getTask(taskId: Long): TodoTask? =
            tasks.value.firstOrNull { it.id == taskId }

        override suspend fun updateTask(task: TodoTask) {
            tasks.value = tasks.value.map { if (it.id == task.id) task else it }
        }

        override suspend fun updateTask(input: UpdateTaskInput): Long {
            updateInputs += input
            tasks.value = tasks.value.map { task ->
                if (task.id == input.taskId) {
                    task.copy(
                        title = input.title,
                        memo = input.memo,
                        time = input.time,
                        difficulty = input.difficulty,
                        category = TaskCategory.normalize(input.category),
                        recurrenceRule = input.recurrenceRule,
                        reminderSetting = input.reminderSetting,
                    )
                } else {
                    task
                }
            }
            return input.taskId
        }

        override suspend fun deleteTask(taskId: Long) {
            tasks.value = tasks.value.filterNot { it.id == taskId }
        }

        override suspend fun deleteTask(taskId: Long, effectiveDate: LocalDate) {
            deleteInputs += taskId to effectiveDate
            deleteTask(taskId)
        }

        override suspend fun completeOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): CompletionResult {
            completeCallCount += 1
            completionGate?.await()
            val key = taskId to occurrenceDate
            completed.value = completed.value + key
            failed.value = failed.value - key
            if (key in rewarded) {
                return CompletionResult(awardedXp = 0, awardedGold = 0, alreadyRewarded = true)
            }
            val task = tasks.value.single { it.id == taskId }
            val reward = com.todoquest.domain.usecase.RewardPolicy.rewardFor(task.difficulty)
            val result = nextCompletionResult ?: CompletionResult(
                awardedXp = reward.xp,
                awardedGold = reward.gold,
                alreadyRewarded = false,
            )
            nextCompletionResult = null
            if (!result.alreadyRewarded) {
                rewarded += key
                onRewardGranted(result)
            }
            return result
        }

        override suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate) {
            completed.value = completed.value - (taskId to occurrenceDate)
        }

        override suspend fun failOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): FailureResult {
            failCallCount += 1
            val key = taskId to occurrenceDate
            val alreadyFailed = key in failed.value
            if (!alreadyFailed) failed.value += key
            return FailureResult(wasAlreadyFailed = alreadyFailed)
        }

        override suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
            undoFailCallCount += 1
            failed.value -= taskId to occurrenceDate
        }

    }

    private class FakeCharacterRepository : CharacterRepository {
        private val profile = MutableStateFlow(CharacterProfile.default())
        private val appearance = MutableStateFlow(CharacterLoadoutCatalog.defaultAppearance)
        private val equippedItems = MutableStateFlow(CharacterLoadoutCatalog.defaultEquippedItems)

        fun grantReward(result: CompletionResult) {
            if (!result.alreadyRewarded) {
                profile.value = profile.value.withReward(result.awardedXp, result.awardedGold)
            }
        }

        fun setProfile(value: CharacterProfile) {
            profile.value = value
        }

        fun setLoadout(
            appearance: CharacterAppearance,
            equippedItems: EquippedItems,
        ) {
            this.appearance.value = appearance
            this.equippedItems.value = equippedItems
        }

        override fun observeCharacter(referenceDate: LocalDate): Flow<CharacterSnapshot> =
            combine(profile, appearance, equippedItems, ::snapshotFor)

        override suspend fun updateAppearance(
            appearance: CharacterAppearance,
        ): CharacterLoadoutUpdateResult = error("not used by calendar tests")

        override suspend fun updateEquippedItems(
            items: EquippedItems,
        ): CharacterLoadoutUpdateResult = error("not used by calendar tests")

        override suspend fun allocateStatPoints(
            allocation: StatAllocation,
        ): AllocateStatPointsResult =
            error("not used by calendar tests")

        override suspend fun resetStats(): StatResetResult = error("not used by calendar tests")

        private fun snapshotFor(
            profile: CharacterProfile,
            appearance: CharacterAppearance,
            equippedItems: EquippedItems,
        ): CharacterSnapshot {
            val config = CharacterStatBalanceConfig()
            val character = PlayerCharacter(
                id = 1L,
                totalXp = profile.totalXp,
                currentGold = profile.currentGold,
                baseStats = CharacterBaseStats(5, 5, 5, 5),
                unspentStatPoints = 2 * (profile.level - 1),
                hasUsedFreeStatReset = false,
            )
            val derivedStats = DerivedStatsCalculator.calculate(
                StatCalculationInput(profile.level, character.baseStats),
                config,
            )
            return CharacterSnapshot(
                character = character,
                appearance = appearance,
                equippedItems = equippedItems,
                level = profile.level,
                xpIntoCurrentLevel = profile.totalXp % config.xpPerLevel,
                xpRequiredForNextLevel = config.xpPerLevel,
                isMaxLevel = profile.level == config.levelMax,
                currentState = CharacterCurrentState(
                    characterId = 1L,
                    currentHp = derivedStats.maxHp,
                    balanceVersion = config.version,
                    updatedAtEpochMillis = 0,
                ),
                derivedStats = derivedStats,
                currentStreak = 0,
                momentumBonusBp = 0,
            )
        }
    }

    private class FakeCombatRepository : CombatRepository {
        private val snapshots = MutableSharedFlow<CombatSnapshot>(replay = 1)
        private val transitionEvents = MutableSharedFlow<CombatTransition>(extraBufferCapacity = 8)
        val playerAttackInputs = mutableListOf<Pair<Long, LocalDate>>()
        val monsterAttackInputs = mutableListOf<Pair<Long, LocalDate>>()
        var playerAttackFailure: Throwable? = null
        var monsterAttackFailure: Throwable? = null
        var observeFailure: Throwable? = null
        var eventCollectionCount = 0
            private set

        override val events: Flow<CombatTransition> = flow {
            eventCollectionCount += 1
            transitionEvents.collect(::emit)
        }

        fun emitSnapshot(snapshot: CombatSnapshot) {
            check(snapshots.tryEmit(snapshot))
        }

        fun emitTransition(transition: CombatTransition) {
            check(transitionEvents.tryEmit(transition))
        }

        override fun observeCombat(): Flow<CombatSnapshot> = observeFailure?.let { failure ->
            flow { throw failure }
        } ?: snapshots

        override suspend fun processPlayerAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): PlayerAttackResult {
            playerAttackInputs += taskId to occurrenceDate
            playerAttackFailure?.let { throw it }
            return PlayerAttackResult.NotFound
        }

        override suspend fun processPendingPlayerAttacks(): Int = 0

        override suspend fun processFailedOccurrenceAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): MonsterAttackResult {
            monsterAttackInputs += taskId to occurrenceDate
            monsterAttackFailure?.let { throw it }
            return MonsterAttackResult.NotFound
        }

        override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult =
            CombatReconciliationResult(playerAttacksProcessed = 0)
    }

    private class FakeStatusEffectRepository(
        private val clock: AppClock,
    ) : StatusEffectRepository {
        val effects = MutableStateFlow<List<CharacterStatusEffect>>(emptyList())
        private val removals = MutableSharedFlow<CombatLifecycleEvent.StatusEffectRemoved>(
            extraBufferCapacity = 8,
        )
        var reconcileCalls = 0

        override fun observeActiveStatusEffects(
            characterId: Long,
        ): Flow<List<CharacterStatusEffect>> = effects

        override fun observeRemovalEvents(
            characterId: Long,
        ): Flow<CombatLifecycleEvent.StatusEffectRemoved> = removals

        fun emitRemoval(event: CombatLifecycleEvent.StatusEffectRemoved) {
            assertTrue(removals.tryEmit(event))
        }

        override suspend fun reconcileExpired(characterId: Long): Int {
            reconcileCalls += 1
            val now = clock.now().toEpochMilli()
            val expired = effects.value.count { it.expiresAtEpochMillis <= now }
            effects.value = effects.value.filter { it.expiresAtEpochMillis > now }
            return expired
        }

        override suspend fun removeStatusEffect(
            characterId: Long,
            type: StatusEffectType,
            revision: Long,
            mutationId: String,
        ): Boolean = false
    }

    private class FakeBattleSfxPlayer : BattleSfxPlayer {
        val requests = mutableListOf<Pair<BattleSfx, String>>()

        override fun play(effect: BattleSfx, eventId: String) {
            requests += effect to eventId
        }

        override fun release() = Unit
    }

    private class MutableCalendarClock(
        var instant: Instant,
    ) : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = instant
        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }

    private fun combatSnapshot() = CombatSnapshot(
        progress = StageProgress(
            stageNumber = 7,
            stageLevel = 3,
            activeMonsterInstanceId = 42L,
            lastReconciledAt = Instant.parse("2026-07-14T08:55:00Z"),
            balanceVersion = 1,
        ),
        activeMonster = MonsterInstance(
            id = 42L,
            definitionId = "monster_attack_v1",
            grade = MonsterGrade.NORMAL,
            stageNumber = 7,
            encounterNumber = 2,
            level = 3,
            currentHp = 37,
            balanceVersion = 1,
        ),
        activeMonsterStats = MonsterStats(
            maxHp = 55,
            damage = 15,
            defense = 9,
        ),
        activeMonsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        playerCurrentHp = 48,
        playerMaxHp = 80,
    )

    private fun nonLethalPlayerTransition(): CombatTransition.PlayerAttack {
        val before = combatSnapshot()
        val after = before.copy(
            activeMonster = before.activeMonster.copy(currentHp = 27),
        )
        return CombatTransition.PlayerAttack(
            attack = com.todoquest.domain.model.PlayerAttackSnapshot(
                taskId = 23L,
                occurrenceDateEpochDay = FixedClock.today().toEpochDay(),
                targetMonsterInstanceId = before.activeMonster.id,
                seed = 1L,
                roll = 5000,
                wasCritical = false,
                rawDamage = 19,
                targetDefense = 9,
                finalDamage = 10,
                targetHpBefore = 37,
                targetHpAfter = 27,
                processedAt = FixedClock.now(),
            ),
            before = before,
            after = after,
        )
    }

    private fun severeInjury(
        expiresAt: Instant,
        remainingCompletions: Int = 3,
        revision: Long = 1L,
    ) = CharacterStatusEffect(
        characterId = 1L,
        type = StatusEffectType.SEVERE_INJURY,
        definitionVersion = 1,
        appliedAtEpochMillis = expiresAt.minusSeconds(24 * 60 * 60).toEpochMilli(),
        expiresAtEpochMillis = expiresAt.toEpochMilli(),
        remainingRecoveryCompletions = remainingCompletions,
        active = true,
        revision = revision,
        lastMutationId = "status-effect:test",
    )
}
