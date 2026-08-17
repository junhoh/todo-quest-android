package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterEquipmentEntity
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.CompletionLogEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.FailureLogEntity
import com.todoquest.data.local.MonsterAttackEventEntity
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.local.RewardLedgerEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CompletionRewardMode
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterAttackSkipReason
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.OccurrenceStateConflictException
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskDifficultyCombatBalanceCatalog
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.model.UpdateTaskInput
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomTaskRepositoryTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var repository: RoomTaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTaskRepository(database, FixedClock)
    }

    @Test
    fun createTaskPersistsExplicitNonePresetAndCustomReminderRows() = runTest {
        val noneTaskId = repository.createTask(taskInput(title = "None"))
        val presetTaskId = repository.createTask(
            taskInput(
                title = "Preset",
                time = LocalTime.of(10, 0),
                reminderSetting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
            ),
        )
        val customTaskId = repository.createTask(
            taskInput(
                title = "Custom",
                reminderSetting = ReminderSetting(
                    mode = ReminderMode.CUSTOM_TIME,
                    customTime = LocalTime.of(7, 45),
                ),
            ),
        )

        assertEquals(ReminderMode.NONE.name, database.taskReminderDao().getByTaskId(noneTaskId)?.mode)
        assertEquals(
            ReminderScheduleStatus.DISABLED.name,
            database.taskReminderDao().getByTaskId(noneTaskId)?.scheduleStatus,
        )
        assertEquals(
            ReminderMode.TEN_MINUTES_BEFORE.name,
            database.taskReminderDao().getByTaskId(presetTaskId)?.mode,
        )
        assertEquals(
            ReminderScheduleStatus.PENDING.name,
            database.taskReminderDao().getByTaskId(presetTaskId)?.scheduleStatus,
        )
        assertEquals(ReminderMode.CUSTOM_TIME.name, database.taskReminderDao().getByTaskId(customTaskId)?.mode)
        assertEquals(7 * 60 + 45, database.taskReminderDao().getByTaskId(customTaskId)?.customTimeMinuteOfDay)
        assertEquals(
            ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(7, 45)),
            repository.getTask(customTaskId)?.reminderSetting,
        )
    }

    @Test
    fun reminderInsertFailureRollsBackTaskAndRecurrenceSeriesCreation() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_task_reminder_insert
            BEFORE INSERT ON task_reminders
            BEGIN
                SELECT RAISE(ABORT, 'forced reminder insert failure');
            END
            """.trimIndent(),
        )

        val result = runCatching {
            repository.createTask(
                taskInput(reminderSetting = ReminderSetting(ReminderMode.ONE_HOUR_BEFORE)),
            )
        }

        assertTrue(result.isFailure)
        assertNull(database.todoTaskDao().getActiveById(1L))
        assertNull(database.taskReminderDao().getByTaskId(1L))
    }

    @Test
    fun getTaskTreatsLegacyTaskWithoutReminderRowAsNone() = runTest {
        database.todoTaskDao().insert(todoTaskEntity(id = 100L, title = "Legacy", category = "General"))

        val task = repository.getTask(100L)

        assertEquals(ReminderSetting(), task?.reminderSetting)
        assertNull(database.taskReminderDao().getByTaskId(100L))
    }

    @Test
    fun updateTaskChangesReminderConfigButPreservesMaterializedKeyForCancellation() = runTest {
        val taskId = repository.createTask(
            taskInput(
                time = LocalTime.of(10, 0),
                reminderSetting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE),
            ),
        )
        val occurrenceEpochDay = LocalDate.of(2026, 7, 14).toEpochDay()
        val triggerAtEpochMillis = Instant.parse("2026-07-14T09:50:00Z").toEpochMilli()
        database.taskReminderDao().compareAndUpdateScheduleState(
            taskId = taskId,
            expectedOccurrenceEpochDay = null,
            scheduleStatus = ReminderScheduleStatus.SCHEDULED.name,
            scheduledOccurrenceEpochDay = occurrenceEpochDay,
            scheduledTriggerAtEpochMillis = triggerAtEpochMillis,
            updatedAtEpochMillis = FixedClock.now().toEpochMilli(),
        )

        repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = LocalDate.of(2026, 7, 14),
                time = LocalTime.of(10, 0),
                reminderSetting = ReminderSetting(),
            ),
        )
        val disabled = database.taskReminderDao().getByTaskId(taskId)!!
        assertEquals(ReminderMode.NONE.name, disabled.mode)
        assertEquals(ReminderScheduleStatus.DISABLED.name, disabled.scheduleStatus)
        assertEquals(occurrenceEpochDay, disabled.scheduledOccurrenceEpochDay)
        assertEquals(triggerAtEpochMillis, disabled.scheduledTriggerAtEpochMillis)

        val customSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(8, 15))
        repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = LocalDate.of(2026, 7, 14),
                time = LocalTime.of(10, 0),
                reminderSetting = customSetting,
            ),
        )
        val pending = database.taskReminderDao().getByTaskId(taskId)!!
        assertEquals(ReminderMode.CUSTOM_TIME.name, pending.mode)
        assertEquals(8 * 60 + 15, pending.customTimeMinuteOfDay)
        assertEquals(ReminderScheduleStatus.PENDING.name, pending.scheduleStatus)
        assertEquals(occurrenceEpochDay, pending.scheduledOccurrenceEpochDay)
        assertEquals(triggerAtEpochMillis, pending.scheduledTriggerAtEpochMillis)
        assertEquals(customSetting, repository.getTask(taskId)?.reminderSetting)
    }

    @Test
    fun recurringSplitKeepsOldReminderKeyAndCreatesIndependentNewReminderRow() = runTest {
        val originalSetting = ReminderSetting(ReminderMode.TEN_MINUTES_BEFORE)
        val taskId = repository.createTask(
            taskInput(
                time = LocalTime.of(10, 0),
                recurrenceRule = RecurrenceRule.DAILY,
                reminderSetting = originalSetting,
            ),
        )
        val oldOccurrenceEpochDay = LocalDate.of(2026, 7, 15).toEpochDay()
        val oldTriggerAtEpochMillis = Instant.parse("2026-07-15T09:50:00Z").toEpochMilli()
        database.taskReminderDao().compareAndUpdateScheduleState(
            taskId = taskId,
            expectedOccurrenceEpochDay = null,
            scheduleStatus = ReminderScheduleStatus.SCHEDULED.name,
            scheduledOccurrenceEpochDay = oldOccurrenceEpochDay,
            scheduledTriggerAtEpochMillis = oldTriggerAtEpochMillis,
            updatedAtEpochMillis = FixedClock.now().toEpochMilli(),
        )
        val newSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(7, 30))

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = LocalDate.of(2026, 7, 16),
                time = LocalTime.of(11, 0),
                recurrenceRule = RecurrenceRule.DAILY,
                reminderSetting = newSetting,
            ),
        )

        val oldReminder = database.taskReminderDao().getByTaskId(taskId)!!
        val newReminder = database.taskReminderDao().getByTaskId(newTaskId)!!
        assertEquals(LocalDate.of(2026, 7, 15), repository.getTask(taskId)?.endDate)
        assertEquals(originalSetting, repository.getTask(taskId)?.reminderSetting)
        assertEquals(ReminderScheduleStatus.SCHEDULED.name, oldReminder.scheduleStatus)
        assertEquals(oldOccurrenceEpochDay, oldReminder.scheduledOccurrenceEpochDay)
        assertEquals(oldTriggerAtEpochMillis, oldReminder.scheduledTriggerAtEpochMillis)
        assertEquals(newSetting, repository.getTask(newTaskId)?.reminderSetting)
        assertEquals(ReminderScheduleStatus.PENDING.name, newReminder.scheduleStatus)
        assertNull(newReminder.scheduledOccurrenceEpochDay)
        assertNull(newReminder.scheduledTriggerAtEpochMillis)
    }

    @Test
    fun softDeleteKeepsReminderStateForPendingIntentCleanup() = runTest {
        val taskId = repository.createTask(
            taskInput(reminderSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(9, 0))),
        )
        val reminderBeforeDelete = database.taskReminderDao().getByTaskId(taskId)

        repository.deleteTask(taskId)

        assertNull(repository.getTask(taskId))
        assertEquals(reminderBeforeDelete, database.taskReminderDao().getByTaskId(taskId))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeOccurrenceAwardsRewardOnlyOnce() = runTest {
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.HARD))

        val first = repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))
        val firstEvent = database.combatDao().getPlayerAttackEvent(
            taskId,
            LocalDate.of(2026, 7, 14).toEpochDay(),
        )
        val second = repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))

        assertEquals(
            CompletionResult(
                0,
                0,
                alreadyRewarded = false,
                isOnTime = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            first,
        )
        assertEquals(
            CompletionResult(
                0,
                0,
                alreadyRewarded = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            second,
        )
        assertNotNull(firstEvent)
        assertEquals(
            firstEvent,
            database.combatDao().getPlayerAttackEvent(
                taskId,
                LocalDate.of(2026, 7, 14).toEpochDay(),
            ),
        )

        val character = characterRepository().observeCharacter(LocalDate.of(2026, 7, 14)).first().character
        assertEquals(0, character.totalXp)
        assertEquals(0, character.currentGold)
    }

    @Test
    fun firstCompletionSnapshotsEachTaskDifficultyAndCurrentBalanceVersion() = runTest {
        TaskDifficulty.entries.forEachIndexed { index, difficulty ->
            val taskId = repository.createTask(
                taskInput(
                    title = difficulty.name,
                    startDate = LocalDate.of(2026, 7, 14).plusDays(index.toLong()),
                    difficulty = difficulty,
                ),
            )
            val occurrenceDate = LocalDate.of(2026, 7, 14).plusDays(index.toLong())

            repository.completeOccurrence(taskId, occurrenceDate)

            val event = database.combatDao()
                .getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())!!
            assertEquals(difficulty.name, event.sourceTaskDifficulty)
            assertEquals(
                TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
                event.taskDifficultyBalanceVersion,
            )
        }
    }

    @Test
    fun taskEditRetryAndUndoRedoPreserveTheOriginalDifficultySnapshotAndSingleSources() = runTest {
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.EASY))
        repository.completeOccurrence(taskId, occurrenceDate)
        val originalEvent = database.combatDao()
            .getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())!!

        repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = occurrenceDate,
                difficulty = TaskDifficulty.HARD,
            ),
        )
        repository = RoomTaskRepository(database, FixedClock)
        repository.completeOccurrence(taskId, occurrenceDate)
        repository.undoCompleteOccurrence(taskId, occurrenceDate)
        repository.completeOccurrence(taskId, occurrenceDate)

        assertEquals(
            originalEvent,
            database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay()),
        )
        assertEquals(TaskDifficulty.EASY.name, originalEvent.sourceTaskDifficulty)
        assertEquals(
            TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            originalEvent.taskDifficultyBalanceVersion,
        )
        assertEquals(1, database.completionLogDao().findFrom(taskId, occurrenceDate.toEpochDay()).size)
        assertEquals(1, database.rewardLedgerDao().findFrom(taskId, occurrenceDate.toEpochDay()).size)
        assertEquals(
            1,
            database.combatDao().findPendingPlayerAttackEvents().count {
                it.taskId == taskId && it.occurrenceDateEpochDay == occurrenceDate.toEpochDay()
            },
        )
    }

    @Test
    fun threeDistinctRecurringCompletionsRecoverInjuryAndThirdAttackUsesRestoredStats() = runTest {
        characterRepository().resetStats()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(1L, 44, 1, FixedClock.now().toEpochMilli()),
        )
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val taskId = repository.createTask(taskInput(recurrenceRule = RecurrenceRule.DAILY))
        val dates = listOf(
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 16),
        )

        dates.forEach { date -> repository.completeOccurrence(taskId, date) }

        val effect = database.statusEffectDao()
            .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
        assertFalse(effect.active)
        assertEquals(0, effect.remainingRecoveryCompletions)
        assertTrue(effect.lastMutationId.startsWith("status-effect:removed:recovery:"))
        assertEquals(
            dates.map(LocalDate::toEpochDay),
            database.statusEffectDao()
                .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
                .map { it.occurrenceDateEpochDay },
        )
        assertEquals(
            listOf(16, 16, 20),
            dates.map { date ->
                database.combatDao().getPlayerAttackEvent(taskId, date.toEpochDay())!!.sourceAttack
            },
        )
        val profile = database.characterProfileDao().getProfile()!!
        assertEquals(5, profile.strength)
        assertEquals(5, profile.vitality)
        assertEquals(0L, profile.totalXp)
        assertEquals(0L, profile.currentGold)
        dates.forEach { date ->
            val ledger = database.rewardLedgerDao().find(taskId, date.toEpochDay())!!
            assertEquals(0L, ledger.xpAward)
            assertEquals(0L, ledger.goldAward)
        }
    }

    @Test
    fun duplicateAndUndoRedoOfSameOccurrenceNeverGrantAnotherRecoveryCredit() = runTest {
        characterRepository().resetStats()
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val taskId = repository.createTask(taskInput())
        val date = LocalDate.of(2026, 7, 14)

        repository.completeOccurrence(taskId, date)
        repository.completeOccurrence(taskId, date)
        repository.undoCompleteOccurrence(taskId, date)
        repository.completeOccurrence(taskId, date)

        assertEquals(
            2,
            database.statusEffectDao()
                .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)
                ?.remainingRecoveryCompletions,
        )
        assertEquals(
            1,
            database.statusEffectDao()
                .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
                .size,
        )
    }

    @Test
    fun recurringSplitReassignsRecoveryKeySoUndoRedoCannotCreditTheSameOccurrenceAgain() = runTest {
        characterRepository().resetStats()
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val originalTaskId = repository.createTask(
            taskInput(recurrenceRule = RecurrenceRule.DAILY),
        )
        val occurrenceDate = LocalDate.of(2026, 7, 16)
        repository.completeOccurrence(originalTaskId, occurrenceDate)

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = originalTaskId,
                effectiveDate = occurrenceDate,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        repository.undoCompleteOccurrence(newTaskId, occurrenceDate)
        repository.completeOccurrence(newTaskId, occurrenceDate)

        val credits = database.statusEffectDao()
            .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
        assertEquals(1, credits.size)
        assertEquals(newTaskId, credits.single().taskId)
        assertEquals(occurrenceDate.toEpochDay(), credits.single().occurrenceDateEpochDay)
        assertEquals(
            2,
            database.statusEffectDao()
                .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)
                ?.remainingRecoveryCompletions,
        )
    }

    @Test
    fun playerAttackFailureRollsBackRecoveryCreditAndDecrementWithCompletion() = runTest {
        characterRepository().resetStats()
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val taskId = repository.createTask(taskInput())
        val date = LocalDate.of(2026, 7, 14)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_status_recovery_player_attack
            BEFORE INSERT ON player_attack_events
            BEGIN
                SELECT RAISE(ABORT, 'forced status recovery rollback');
            END
            """.trimIndent(),
        )

        assertTrue(runCatching { repository.completeOccurrence(taskId, date) }.isFailure)

        assertNull(database.completionLogDao().find(taskId, date.toEpochDay()))
        assertNull(database.rewardLedgerDao().find(taskId, date.toEpochDay()))
        assertEquals(
            3,
            database.statusEffectDao()
                .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)
                ?.remainingRecoveryCompletions,
        )
        assertTrue(
            database.statusEffectDao()
                .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
                .isEmpty(),
        )
    }

    @Test
    fun exactExpiryIsReconciledBeforeCompletionWithoutRecoveryCredit() = runTest {
        val clock = MutableClock().apply {
            instant = Instant.parse("2026-07-15T09:00:00Z")
        }
        repository = RoomTaskRepository(database, clock)
        RoomCharacterRepository(database, clock).resetStats()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(1L, 44, 1, clock.now().toEpochMilli()),
        )
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val taskId = repository.createTask(
            taskInput(startDate = LocalDate.of(2026, 7, 15)),
        )

        repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 15))

        val effect = database.statusEffectDao()
            .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
        assertFalse(effect.active)
        assertTrue(effect.lastMutationId.startsWith("status-effect:expired:"))
        assertTrue(
            database.statusEffectDao()
                .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
                .isEmpty(),
        )
        assertEquals(
            20,
            database.combatDao()
                .getPlayerAttackEvent(taskId, LocalDate.of(2026, 7, 15).toEpochDay())
                ?.sourceAttack,
        )
        assertEquals(44, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun combatSnapshotUsesTheSameActiveStatusModifiers() = runTest {
        characterRepository().resetStats()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(1L, 44, 1, FixedClock.now().toEpochMilli()),
        )
        database.statusEffectDao().upsertStatusEffect(severeInjury())

        val snapshot = RoomCombatRepository(database, FixedClock).observeCombat().first()

        assertEquals(88, snapshot.playerMaxHp)
        assertEquals(44, snapshot.playerCurrentHp)
    }

    @Test
    fun firstRewardSnapshotsCurrentCombatRewardVersionWithoutProcessingCombat() = runTest {
        repository = RoomTaskRepository(
            database = database,
            clock = FixedClock,
            monsterBalanceConfig = MonsterBalanceConfig(combatRewardVersion = 1),
        )
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.HARD))
        val occurrenceDate = LocalDate.of(2026, 7, 14)

        repository.completeOccurrence(taskId, occurrenceDate)

        val event = database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())!!
        assertEquals(taskId, event.recurrenceSeriesId)
        assertEquals(CombatEventStatus.PENDING.name, event.status)
        assertEquals(1, event.sourcePlayerLevel)
        assertEquals(20, event.sourceAttack)
        assertEquals(750, event.sourceCriticalChanceBp)
        assertEquals(15_250, event.sourceCriticalDamageBp)
        assertEquals(0, event.sourceMomentumBp)
        assertEquals(1, event.characterBalanceVersion)
        assertEquals(1, event.monsterBalanceVersion)
        assertEquals(CombatRewardBalanceCatalog.CURRENT_VERSION, event.combatRewardVersion)
        assertEquals(FixedClock.now().toEpochMilli(), event.createdAtEpochMillis)
        assertNull(event.targetMonsterInstanceId)
        assertNull(event.processedAtEpochMillis)
        assertNull(database.combatDao().getCombatProgress())
    }

    @Test
    fun equippedAccessoryAffectsOnlyNewRewardAndPlayerAttackSnapshots() = runTest {
        characterRepository().resetStats()
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        equip(
            ownEquipment(EquipmentCatalogSeeder.MAGE_RING_ID),
            EquipmentSlot.ACCESSORY,
        )
        val firstTask = repository.createTask(taskInput(difficulty = TaskDifficulty.HARD))

        val firstResult = repository.completeOccurrence(firstTask, LocalDate.of(2026, 7, 14))
        val firstLedger = database.rewardLedgerDao()
            .find(firstTask, LocalDate.of(2026, 7, 14).toEpochDay())!!
        val firstAttack = database.combatDao()
            .getPlayerAttackEvent(firstTask, LocalDate.of(2026, 7, 14).toEpochDay())!!

        assertEquals(
            CompletionResult(
                0,
                0,
                false,
                isOnTime = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            firstResult,
        )
        assertEquals(600, firstLedger.goldGainBonusBp)
        assertEquals(25, firstAttack.sourceAttack)
        assertEquals(1_500, firstAttack.sourceCriticalChanceBp)
        assertEquals(15_850, firstAttack.sourceCriticalDamageBp)

        equip(
            ownEquipment(EquipmentCatalogSeeder.GUARDIAN_NECKLACE_ID),
            EquipmentSlot.ACCESSORY,
        )
        val secondTask = repository.createTask(
            taskInput(title = "Second", difficulty = TaskDifficulty.HARD),
        )
        repository.completeOccurrence(secondTask, LocalDate.of(2026, 7, 14))

        assertEquals(
            firstLedger,
            database.rewardLedgerDao().find(firstTask, LocalDate.of(2026, 7, 14).toEpochDay()),
        )
        assertEquals(
            900,
            database.rewardLedgerDao()
                .find(secondTask, LocalDate.of(2026, 7, 14).toEpochDay())
                ?.goldGainBonusBp,
        )
    }

    @Test
    fun undoCompletionDoesNotReclaimOrDuplicateReward() = runTest {
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.MEDIUM))

        repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))
        repository.undoCompleteOccurrence(taskId, LocalDate.of(2026, 7, 14))

        val occurrenceAfterUndo = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14))
            .first()
            .single()
        assertFalse(occurrenceAfterUndo.isCompleted)

        val afterRedo = repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))
        assertEquals(
            CompletionResult(
                0,
                0,
                alreadyRewarded = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            afterRedo,
        )
        assertNotNull(
            database.combatDao().getPlayerAttackEvent(
                taskId,
                LocalDate.of(2026, 7, 14).toEpochDay(),
            ),
        )

        val character = characterRepository().observeCharacter(LocalDate.of(2026, 7, 14)).first().character
        assertEquals(0, character.totalXp)
        assertEquals(0, character.currentGold)
    }

    @Test
    fun recurringCompletionOnlyAffectsSelectedOccurrenceDate() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 15))

        val occurrences = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 16))
            .first()

        assertEquals(3, occurrences.size)
        assertFalse(occurrences.single { it.occurrenceDate == LocalDate.of(2026, 7, 14) }.isCompleted)
        assertTrue(occurrences.single { it.occurrenceDate == LocalDate.of(2026, 7, 15) }.isCompleted)
        assertFalse(occurrences.single { it.occurrenceDate == LocalDate.of(2026, 7, 16) }.isCompleted)
    }

    @Test
    fun occurrenceStatusesCombineCompletionFailureAndTodoWithoutCountingFailureAsCompleted() = runTest {
        val taskId = repository.createTask(
            taskInput(recurrenceRule = RecurrenceRule.DAILY),
        )
        val completedDate = LocalDate.of(2026, 7, 14)
        val failedDate = LocalDate.of(2026, 7, 15)

        repository.completeOccurrence(taskId, completedDate)
        val firstFailure = repository.failOccurrence(taskId, failedDate)
        val duplicateFailure = repository.failOccurrence(taskId, failedDate)

        val occurrences = repository
            .observeOccurrences(completedDate, LocalDate.of(2026, 7, 16))
            .first()

        assertEquals(FailureResult(wasAlreadyFailed = false), firstFailure)
        assertEquals(FailureResult(wasAlreadyFailed = true), duplicateFailure)
        assertEquals(
            listOf(
                TaskOccurrenceStatus.COMPLETED,
                TaskOccurrenceStatus.FAILED,
                TaskOccurrenceStatus.TODO,
            ),
            occurrences.map { it.status },
        )
        assertEquals(1, occurrences.count { it.isCompleted })
    }

    @Test
    fun occurrenceProjectionUsesLegacyDefaultsAndReemitsReminderSettingAndStatusChanges() = runTest {
        database.todoTaskDao().insert(todoTaskEntity(id = 100L, title = "Legacy", category = "General"))
        val recurringTaskId = repository.createTask(
            taskInput(
                title = "Recurring",
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val emissions = repository.observeOccurrences(
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 15),
        ).produceIn(backgroundScope)

        val initial = emissions.receive()
        val legacy = initial.single { it.taskId == 100L }
        assertEquals(ReminderSetting(), legacy.reminderSetting)
        assertEquals(ReminderScheduleStatus.DISABLED, legacy.reminderScheduleStatus)
        assertTrue(initial.filter { it.taskId == recurringTaskId }.all {
            it.reminderSetting == ReminderSetting() &&
                it.reminderScheduleStatus == ReminderScheduleStatus.DISABLED
        })

        val customSetting = ReminderSetting(ReminderMode.CUSTOM_TIME, LocalTime.of(8, 30))
        database.taskReminderDao().upsert(
            database.taskReminderDao().getByTaskId(recurringTaskId)!!.copy(
                mode = customSetting.mode.name,
                customTimeMinuteOfDay = 8 * 60 + 30,
                scheduleStatus = ReminderScheduleStatus.PENDING.name,
            ),
        )
        val pending = emissions.receive().filter { it.taskId == recurringTaskId }
        assertEquals(2, pending.size)
        assertTrue(pending.all {
            it.reminderSetting == customSetting &&
                it.reminderScheduleStatus == ReminderScheduleStatus.PENDING
        })

        database.taskReminderDao().compareAndUpdateScheduleState(
            taskId = recurringTaskId,
            expectedOccurrenceEpochDay = null,
            scheduleStatus = ReminderScheduleStatus.SCHEDULED.name,
            scheduledOccurrenceEpochDay = LocalDate.of(2026, 7, 14).toEpochDay(),
            scheduledTriggerAtEpochMillis = Instant.parse("2026-07-14T08:30:00Z").toEpochMilli(),
            updatedAtEpochMillis = FixedClock.now().toEpochMilli(),
        )
        val scheduled = emissions.receive().filter { it.taskId == recurringTaskId }
        assertTrue(scheduled.all {
            it.reminderSetting == customSetting &&
                it.reminderScheduleStatus == ReminderScheduleStatus.SCHEDULED
        })
    }

    @Test
    fun occurrenceProjectionRejectsCorruptReminderRowsThroughTheSharedMapper() = runTest {
        val taskId = repository.createTask(taskInput())
        database.taskReminderDao().upsert(
            database.taskReminderDao().getByTaskId(taskId)!!.copy(
                scheduleStatus = "UNKNOWN_STATUS",
            ),
        )

        val result = runCatching {
            repository.observeOccurrences(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 14),
            ).first()
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun overdueReconciliationPersistsFailureUsedByOccurrenceProjection() = runTest {
        val clock = MutableClock().apply {
            instant = Instant.parse("2026-07-14T08:00:00Z")
        }
        val taskRepository = RoomTaskRepository(database, clock)
        val combatRepository = RoomCombatRepository(database, clock)
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val taskId = taskRepository.createTask(
            taskInput(
                startDate = occurrenceDate,
                time = LocalTime.of(9, 0),
            ),
        )
        combatRepository.observeCombat().first()

        clock.instant = Instant.parse("2026-07-14T09:15:00.001Z")
        combatRepository.reconcileOverdue(clock.now())

        val failure = database.failureLogDao().find(taskId, occurrenceDate.toEpochDay())
        val occurrence = taskRepository
            .observeOccurrences(occurrenceDate, occurrenceDate)
            .first()
            .single()
        assertNotNull(failure)
        assertEquals(taskId, failure?.recurrenceSeriesId)
        assertEquals(clock.now().toEpochMilli(), failure?.failedAtEpochMillis)
        assertEquals(TaskOccurrenceStatus.FAILED, occurrence.status)
    }

    @Test
    fun completingFailedOccurrenceRejectsConflictBeforeRewardProfileOrOutboxChanges() = runTest {
        val taskId = repository.createTask(taskInput())
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        repository.failOccurrence(taskId, occurrenceDate)

        val failure = runCatching { repository.completeOccurrence(taskId, occurrenceDate) }

        val conflict = failure.exceptionOrNull() as OccurrenceStateConflictException
        assertEquals(TaskOccurrenceStatus.FAILED, conflict.currentStatus)
        assertEquals(TaskOccurrenceStatus.COMPLETED, conflict.requestedStatus)
        assertNull(database.completionLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNull(database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNull(database.characterProfileDao().getProfile())
        assertNull(database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay()))
        assertNotNull(database.failureLogDao().find(taskId, occurrenceDate.toEpochDay()))
    }

    @Test
    fun failingCompletedOccurrenceRejectsConflictWithoutChangingCompletionSources() = runTest {
        val taskId = repository.createTask(taskInput())
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        repository.completeOccurrence(taskId, occurrenceDate)
        val originalLedger = database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay())
        val originalProfile = database.characterProfileDao().getProfile()
        val originalOutbox = database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())

        val failure = runCatching { repository.failOccurrence(taskId, occurrenceDate) }

        val conflict = failure.exceptionOrNull() as OccurrenceStateConflictException
        assertEquals(TaskOccurrenceStatus.COMPLETED, conflict.currentStatus)
        assertEquals(TaskOccurrenceStatus.FAILED, conflict.requestedStatus)
        assertNotNull(database.completionLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNull(database.failureLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertEquals(originalLedger, database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()))
        assertEquals(originalProfile, database.characterProfileDao().getProfile())
        assertEquals(originalOutbox, database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay()))
    }

    @Test
    fun undoFailureDeletesOnlyFailureSourceAndKeepsExistingMonsterEvent() = runTest {
        val taskId = repository.createTask(taskInput())
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        repository.failOccurrence(taskId, occurrenceDate)
        database.combatDao().insertMonsterAttackEvent(
            monsterAttackEvent(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
                recurrenceSeriesId = taskId,
            ),
        )

        repository.undoFailOccurrence(taskId, occurrenceDate)

        assertNull(database.failureLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNotNull(database.combatDao().getMonsterAttackEvent(taskId, occurrenceDate.toEpochDay()))
        assertEquals(
            TaskOccurrenceStatus.TODO,
            repository.observeOccurrences(occurrenceDate, occurrenceDate).first().single().status,
        )
    }

    @Test
    fun failedOccurrenceRestoresAfterDatabaseReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "todo-quest-failure-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        var fileDatabase: TodoQuestDatabase? = null
        try {
            fileDatabase = Room.databaseBuilder(context, TodoQuestDatabase::class.java, databaseName)
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_6_7,
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()
            val firstRepository = RoomTaskRepository(fileDatabase, FixedClock)
            val taskId = firstRepository.createTask(taskInput())
            firstRepository.failOccurrence(taskId, LocalDate.of(2026, 7, 14))
            fileDatabase.close()

            fileDatabase = Room.databaseBuilder(context, TodoQuestDatabase::class.java, databaseName)
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_6_7,
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()
            val restored = RoomTaskRepository(fileDatabase, FixedClock)
                .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14))
                .first()
                .single()

            assertEquals(TaskOccurrenceStatus.FAILED, restored.status)
        } finally {
            fileDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun endDatePersistsAndRestoresToDomainModel() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        assertNull(database.todoTaskDao().getActiveById(taskId)?.endDateEpochDay)

        repository.updateTask(
            TodoTask(
                id = taskId,
                title = "Quest",
                memo = "",
                startDate = LocalDate.of(2026, 7, 14),
                time = null,
                difficulty = TaskDifficulty.MEDIUM,
                category = "일반",
                recurrenceRule = RecurrenceRule.DAILY,
                endDate = LocalDate.of(2026, 7, 16),
            ),
        )

        val stored = database.todoTaskDao().getActiveById(taskId)
        assertEquals(LocalDate.of(2026, 7, 16).toEpochDay(), stored?.endDateEpochDay)

        val occurrences = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 20))
            .first()

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 16),
            ),
            occurrences.map { it.occurrenceDate },
        )
    }

    @Test
    fun categoryIsNormalizedWhenSavedAndWhenRestoredFromLegacyRows() = runTest {
        val categories = listOf(
            "General" to "일반",
            "Work" to "업무",
            "Personal" to "개인",
            "" to "일반",
            "Unknown" to "일반",
        )

        for ((inputCategory, expectedCategory) in categories) {
            val taskId = repository.createTask(taskInput(category = inputCategory))
            assertEquals(expectedCategory, database.todoTaskDao().getActiveById(taskId)?.category)
        }

        database.todoTaskDao().insert(
            todoTaskEntity(
                id = 100L,
                title = "Legacy work",
                category = "Work",
            ),
        )

        val legacyOccurrence = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14))
            .first()
            .single { it.taskId == 100L }

        assertEquals("업무", legacyOccurrence.category)
    }

    @Test
    fun migrationFromVersion1To2AddsNullableEndDateColumn() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "todo-quest-migration-1-2-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        createVersion1Database(context, databaseName)

        var migratedDatabase: TodoQuestDatabase? = null
        try {
            migratedDatabase = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_1_2,
                    TodoQuestDatabase.MIGRATION_2_3,
                    TodoQuestDatabase.MIGRATION_3_4,
                    TodoQuestDatabase.MIGRATION_4_5,
                    TodoQuestDatabase.MIGRATION_5_6,
                    TodoQuestDatabase.MIGRATION_6_7,
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            val migratedTask = migratedDatabase.todoTaskDao().getActiveById(1L)

            assertNotNull(migratedTask)
            assertNull(migratedTask?.endDateEpochDay)
        } finally {
            migratedDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun occurrenceSourceRecordsCanBeFoundFilteredAndReassignedFromDate() = runTest {
        val fromDate = LocalDate.of(2026, 7, 15).toEpochDay()
        val beforeDate = LocalDate.of(2026, 7, 14).toEpochDay()
        val afterDate = LocalDate.of(2026, 7, 16).toEpochDay()

        database.completionLogDao().insert(completionLog(taskId = 1L, occurrenceDateEpochDay = beforeDate))
        database.completionLogDao().insert(completionLog(taskId = 1L, occurrenceDateEpochDay = fromDate))
        database.completionLogDao().insert(completionLog(taskId = 1L, occurrenceDateEpochDay = afterDate))
        database.rewardLedgerDao().insert(rewardLedger(taskId = 1L, occurrenceDateEpochDay = beforeDate))
        database.rewardLedgerDao().insert(rewardLedger(taskId = 1L, occurrenceDateEpochDay = fromDate))
        database.rewardLedgerDao().insert(rewardLedger(taskId = 1L, occurrenceDateEpochDay = afterDate))
        database.failureLogDao().insert(failureLog(taskId = 1L, occurrenceDateEpochDay = beforeDate))
        database.failureLogDao().insert(failureLog(taskId = 1L, occurrenceDateEpochDay = fromDate))
        database.failureLogDao().insert(failureLog(taskId = 1L, occurrenceDateEpochDay = afterDate))
        assertEquals(
            -1L,
            database.failureLogDao().insert(
                failureLog(taskId = 1L, occurrenceDateEpochDay = fromDate),
            ),
        )
        database.combatDao().insertMonsterAttackEvent(
            monsterAttackEvent(
                taskId = 1L,
                occurrenceDateEpochDay = fromDate,
                recurrenceSeriesId = 1L,
            ),
        )

        assertEquals(
            listOf(fromDate, afterDate),
            database.completionLogDao().findFrom(1L, fromDate).map { it.occurrenceDateEpochDay },
        )
        assertEquals(
            listOf(fromDate, afterDate),
            database.rewardLedgerDao().findFrom(1L, fromDate).map { it.occurrenceDateEpochDay },
        )
        assertEquals(
            listOf(fromDate, afterDate),
            database.failureLogDao().observeBetween(fromDate, afterDate).first()
                .map { it.occurrenceDateEpochDay },
        )
        assertEquals(
            listOf(beforeDate, afterDate),
            database.failureLogDao().findPendingMonsterAttacks()
                .map { it.occurrenceDateEpochDay },
        )

        database.completionLogDao().reassignFrom(1L, fromDate, newTaskId = 2L)
        database.rewardLedgerDao().reassignFrom(1L, fromDate, newTaskId = 2L)
        database.failureLogDao().reassignFrom(1L, fromDate, newTaskId = 2L)

        assertNotNull(database.completionLogDao().find(1L, beforeDate))
        assertNull(database.completionLogDao().find(1L, fromDate))
        assertNotNull(database.completionLogDao().find(2L, fromDate))
        assertNotNull(database.completionLogDao().find(2L, afterDate))
        assertNotNull(database.rewardLedgerDao().find(1L, beforeDate))
        assertNull(database.rewardLedgerDao().find(1L, fromDate))
        assertNotNull(database.rewardLedgerDao().find(2L, fromDate))
        assertNotNull(database.rewardLedgerDao().find(2L, afterDate))
        assertNotNull(database.failureLogDao().find(1L, beforeDate))
        assertNull(database.failureLogDao().find(1L, fromDate))
        assertEquals(1L, database.failureLogDao().find(2L, fromDate)?.recurrenceSeriesId)
        assertNotNull(database.failureLogDao().find(2L, afterDate))
        database.failureLogDao().delete(2L, afterDate)
        assertNull(database.failureLogDao().find(2L, afterDate))
    }

    @Test
    fun updateNonRecurringTaskKeepsSameTaskIdAndChangesOccurrenceContent() = runTest {
        val taskId = repository.createTask(taskInput(category = "General"))

        val returnedId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = LocalDate.of(2026, 7, 14),
                title = "  Updated quest  ",
                memo = "  Updated memo  ",
                time = LocalTime.of(8, 30),
                difficulty = TaskDifficulty.HARD,
                category = "Work",
                recurrenceRule = RecurrenceRule.NONE,
            ),
        )

        assertEquals(taskId, returnedId)
        val task = repository.getTask(taskId)
        assertNotNull(task)
        assertEquals("Updated quest", task?.title)
        assertEquals("Updated memo", task?.memo)
        assertEquals("업무", task?.category)

        val occurrence = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14))
            .first()
            .single()

        assertEquals(taskId, occurrence.taskId)
        assertEquals("Updated quest", occurrence.title)
        assertEquals("Updated memo", occurrence.memo)
        assertEquals(LocalTime.of(8, 30), occurrence.time)
        assertEquals(TaskDifficulty.HARD, occurrence.difficulty)
        assertEquals("업무", occurrence.category)
    }

    @Test
    fun updateRecurringTaskFutureOccurrencesSplitsExistingAndNewTask() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = LocalDate.of(2026, 7, 16),
                title = "Future quest",
                difficulty = TaskDifficulty.HARD,
                category = "Work",
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        assertTrue(newTaskId != taskId)
        assertEquals(LocalDate.of(2026, 7, 15), repository.getTask(taskId)?.endDate)
        assertEquals(LocalDate.of(2026, 7, 16), repository.getTask(newTaskId)?.startDate)

        val occurrences = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 18))
            .first()

        assertEquals(
            listOf(
                taskId to LocalDate.of(2026, 7, 14),
                taskId to LocalDate.of(2026, 7, 15),
                newTaskId to LocalDate.of(2026, 7, 16),
                newTaskId to LocalDate.of(2026, 7, 17),
                newTaskId to LocalDate.of(2026, 7, 18),
            ),
            occurrences.map { it.taskId to it.occurrenceDate },
        )
        assertEquals(
            listOf("Quest", "Quest", "Future quest", "Future quest", "Future quest"),
            occurrences.map { it.title },
        )
    }

    @Test
    fun updateRecurringTaskFutureOccurrencesReassignsRewardedOccurrencesWithoutDuplicateReward() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                difficulty = TaskDifficulty.MEDIUM,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val occurrenceDate = LocalDate.of(2026, 7, 16)
        repository.completeOccurrence(taskId, occurrenceDate)

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = occurrenceDate,
                title = "Hard future quest",
                difficulty = TaskDifficulty.HARD,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val afterRedo = repository.completeOccurrence(newTaskId, occurrenceDate)

        assertEquals(
            CompletionResult(
                0,
                0,
                alreadyRewarded = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            afterRedo,
        )
        assertNull(database.completionLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNull(database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNotNull(database.completionLogDao().find(newTaskId, occurrenceDate.toEpochDay()))
        assertNotNull(database.rewardLedgerDao().find(newTaskId, occurrenceDate.toEpochDay()))

        val character = characterRepository().observeCharacter(LocalDate.of(2026, 7, 14)).first().character
        assertEquals(0, character.totalXp)
        assertEquals(0, character.currentGold)
    }

    @Test
    fun deleteRecurringTaskFutureOccurrencesLeavesOnlyOccurrencesBeforeEffectiveDate() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        repository.deleteTask(taskId, LocalDate.of(2026, 7, 16))

        val occurrences = repository
            .observeOccurrences(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 18))
            .first()

        assertEquals(
            listOf(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 15)),
            occurrences.map { it.occurrenceDate },
        )
        assertEquals(LocalDate.of(2026, 7, 15), repository.getTask(taskId)?.endDate)
    }

    @Test
    fun deleteTaskDoesNotReclaimCompletionRewardLedgerOrCharacterProfile() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                difficulty = TaskDifficulty.HARD,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val occurrenceDate = LocalDate.of(2026, 7, 16)
        repository.completeOccurrence(taskId, occurrenceDate)

        repository.deleteTask(taskId, occurrenceDate)

        assertNotNull(database.completionLogDao().find(taskId, occurrenceDate.toEpochDay()))
        assertNotNull(database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()))

        val character = characterRepository().observeCharacter(LocalDate.of(2026, 7, 14)).first().character
        assertEquals(0, character.totalXp)
        assertEquals(0, character.currentGold)
    }

    @Test
    fun concurrentCompletionRetriesInsertOneLedgerAndAdvanceCharacterOnce() = runTest {
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.HARD))

        val results = coroutineScope {
            List(8) {
                async { repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14)) }
            }.awaitAll()
        }

        assertEquals(1, results.count { !it.alreadyRewarded })
        assertEquals(7, results.count { it.alreadyRewarded })
        assertEquals(1, database.rewardLedgerDao().countForRewardLocalDate(LocalDate.of(2026, 7, 14).toEpochDay()))
        val profile = database.characterProfileDao().getProfile()
        assertEquals(0L, profile?.totalXp)
        assertEquals(0L, profile?.currentGold)
        assertEquals(0, profile?.unspentStatPoints)
    }

    @Test
    fun completionSnapshotsOnTimeLateAndEarlyFutureDecisionsFromClockAndZone() = runTest {
        val clock = MutableClock()
        val repository = RoomTaskRepository(database, clock)
        val exactDeadlineTask = repository.createTask(taskInput(time = LocalTime.of(9, 0)))
        val lateTask = repository.createTask(taskInput(time = LocalTime.of(9, 0)))
        val futureTask = repository.createTask(taskInput(startDate = LocalDate.of(2026, 7, 15)))

        clock.instant = Instant.parse("2026-07-14T09:15:00Z")
        val exactDeadline = repository.completeOccurrence(exactDeadlineTask, LocalDate.of(2026, 7, 14))
        clock.instant = Instant.parse("2026-07-14T09:16:00Z")
        val late = repository.completeOccurrence(lateTask, LocalDate.of(2026, 7, 14))
        clock.instant = Instant.parse("2026-07-14T10:00:00Z")
        val earlyFuture = repository.completeOccurrence(futureTask, LocalDate.of(2026, 7, 15))

        assertEquals(
            CompletionResult(
                0,
                0,
                false,
                isOnTime = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            exactDeadline,
        )
        assertEquals(
            CompletionResult(
                0,
                0,
                false,
                isOnTime = false,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            late,
        )
        assertEquals(
            CompletionResult(
                0,
                0,
                false,
                isOnTime = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            earlyFuture,
        )

        val exactLedger = database.rewardLedgerDao().find(exactDeadlineTask, LocalDate.of(2026, 7, 14).toEpochDay())!!
        val lateLedger = database.rewardLedgerDao().find(lateTask, LocalDate.of(2026, 7, 14).toEpochDay())!!
        val futureLedger = database.rewardLedgerDao().find(futureTask, LocalDate.of(2026, 7, 15).toEpochDay())!!
        assertEquals(true, exactLedger.onTime)
        assertEquals(11_000, exactLedger.onTimeMultiplierBp)
        assertEquals(false, lateLedger.onTime)
        assertEquals(10_000, lateLedger.onTimeMultiplierBp)
        assertEquals(LocalDate.of(2026, 7, 14).toEpochDay(), futureLedger.rewardLocalDateEpochDay)
    }

    @Test
    fun recurringAndDailyEfficiencyLimitsUseTheLowerTierWithoutMultiplying() = runTest {
        val rewardDate = LocalDate.of(2026, 7, 14)
        val taskId = repository.createTask(
            taskInput(
                startDate = rewardDate,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        repeat(30) { index ->
            database.rewardLedgerDao().insert(
                rewardLedger(
                    taskId = 100L + index,
                    occurrenceDateEpochDay = LocalDate.of(2026, 6, 1).plusDays(index.toLong()).toEpochDay(),
                ).copy(
                    recurrenceSeriesId = if (index < 6) taskId else 1_000L + index,
                    rewardLocalDateEpochDay = rewardDate.toEpochDay(),
                    repeatOrdinal = if (index < 6) index + 1 else 1,
                    dailyOrdinal = index + 1,
                    combatEligible = index < 20,
                ),
            )
        }

        val result = repository.completeOccurrence(taskId, rewardDate)

        assertEquals(
            CompletionResult(
                awardedXp = 0,
                awardedGold = 0,
                alreadyRewarded = false,
                isOnTime = true,
                rewardEfficiencyBp = 2_000,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            result,
        )
        val ledger = database.rewardLedgerDao().find(taskId, rewardDate.toEpochDay())!!
        assertEquals(7, ledger.repeatOrdinal)
        assertEquals(31, ledger.dailyOrdinal)
        assertEquals(2_000, ledger.rewardEfficiencyBp)
        assertEquals(true, ledger.combatEligible)
    }

    @Test
    fun everyNewCompletionCreatesPlayerAttackAfterDailyOrdinalTwenty() = runTest {
        val rewardDate = LocalDate.of(2026, 7, 14)
        repeat(19) { index ->
            database.rewardLedgerDao().insert(
                rewardLedger(
                    taskId = 1_000L + index,
                    occurrenceDateEpochDay = LocalDate.of(2026, 6, 1).plusDays(index.toLong()).toEpochDay(),
                ).copy(
                    rewardLocalDateEpochDay = rewardDate.toEpochDay(),
                    dailyOrdinal = index + 1,
                    combatEligible = true,
                ),
            )
        }
        val twentiethTask = repository.createTask(taskInput(title = "Twentieth"))
        val twentyFirstTask = repository.createTask(taskInput(title = "Twenty first"))

        repository.completeOccurrence(twentiethTask, rewardDate)
        repository.completeOccurrence(twentyFirstTask, rewardDate)

        assertEquals(
            20,
            database.rewardLedgerDao().find(twentiethTask, rewardDate.toEpochDay())?.dailyOrdinal,
        )
        assertNotNull(
            database.combatDao().getPlayerAttackEvent(twentiethTask, rewardDate.toEpochDay()),
        )
        assertEquals(
            21,
            database.rewardLedgerDao().find(twentyFirstTask, rewardDate.toEpochDay())?.dailyOrdinal,
        )
        assertNotNull(database.combatDao().getPlayerAttackEvent(twentyFirstTask, rewardDate.toEpochDay()))
    }

    @Test
    fun recurringSplitReassignsTaskButKeepsSeriesLedgerSnapshot() = runTest {
        val taskId = repository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 7, 14),
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val rewardedDate = LocalDate.of(2026, 7, 16)
        repository.completeOccurrence(taskId, rewardedDate)

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = rewardedDate,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        assertEquals(taskId, database.todoTaskDao().getActiveById(newTaskId)?.recurrenceSeriesId)
        val reassignedLedger = database.rewardLedgerDao().find(newTaskId, rewardedDate.toEpochDay())!!
        assertEquals(taskId, reassignedLedger.recurrenceSeriesId)
        assertEquals(null, database.rewardLedgerDao().find(taskId, rewardedDate.toEpochDay()))
    }

    @Test
    fun completionDefersProgressionAndSnapshotsCurrentCombatStats() = runTest {
        val config = CharacterStatBalanceConfig(xpPerLevel = 10)
        val repository = RoomTaskRepository(database, FixedClock, balanceConfig = config)
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.HARD))

        repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))

        val profile = database.characterProfileDao().getProfile()!!
        val state = database.characterProfileDao().getCurrentState()!!
        assertEquals(0, profile.totalXp)
        assertEquals(0, profile.unspentStatPoints)
        assertEquals(110, state.currentHp)
        val postRewardCharacter = CharacterMapper.toDomain(profile)
        val postRewardStats = derivedStatsFor(postRewardCharacter, config, emptyList())
        val event = database.combatDao().getPlayerAttackEvent(
            taskId,
            LocalDate.of(2026, 7, 14).toEpochDay(),
        )!!
        assertEquals(1, event.sourcePlayerLevel)
        assertEquals(postRewardStats.attack, event.sourceAttack)
        assertEquals(postRewardStats.criticalChanceBp, event.sourceCriticalChanceBp)
        assertEquals(postRewardStats.criticalDamageBp, event.sourceCriticalDamageBp)
    }

    @Test
    fun playerAttackSnapshotsMomentumAtCompletionLocalDate() = runTest {
        insertOnTimeLedger(taskId = 100L, occurrenceDate = LocalDate.of(2026, 7, 12))
        insertOnTimeLedger(taskId = 101L, occurrenceDate = LocalDate.of(2026, 7, 13))
        val taskId = repository.createTask(taskInput())

        repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))

        val event = database.combatDao().getPlayerAttackEvent(
            taskId,
            LocalDate.of(2026, 7, 14).toEpochDay(),
        )!!
        assertEquals(300, event.sourceMomentumBp)
    }

    @Test
    fun earlyFutureCompletionDoesNotUseFutureOccurrenceAsMomentumReference() = runTest {
        insertOnTimeLedger(taskId = 100L, occurrenceDate = LocalDate.of(2026, 7, 13))
        insertOnTimeLedger(taskId = 101L, occurrenceDate = LocalDate.of(2026, 7, 14))
        val futureDate = LocalDate.of(2026, 7, 15)
        val taskId = repository.createTask(taskInput(startDate = futureDate))

        repository.completeOccurrence(taskId, futureDate)

        val event = database.combatDao().getPlayerAttackEvent(taskId, futureDate.toEpochDay())!!
        assertEquals(0, event.sourceMomentumBp)
    }

    @Test
    fun playerAttackInsertFailureRollsBackCompletionRewardAndCharacter() = runTest {
        val taskId = repository.createTask(taskInput())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_player_attack_insert
            BEFORE INSERT ON player_attack_events
            BEGIN
                SELECT RAISE(ABORT, 'forced player attack failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching {
            repository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))
        }

        assertTrue(failure.isFailure)
        assertNull(database.completionLogDao().find(taskId, LocalDate.of(2026, 7, 14).toEpochDay()))
        assertNull(database.rewardLedgerDao().find(taskId, LocalDate.of(2026, 7, 14).toEpochDay()))
        assertNull(database.characterProfileDao().getProfile())
        assertNull(database.combatDao().getPlayerAttackEvent(taskId, LocalDate.of(2026, 7, 14).toEpochDay()))
    }

    @Test
    fun recurringSplitReassignsPlayerAndMonsterAttackEventsWithSeriesLineage() = runTest {
        val taskId = repository.createTask(
            taskInput(
                difficulty = TaskDifficulty.EASY,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )
        val occurrenceDate = LocalDate.of(2026, 7, 16)
        val failedDate = occurrenceDate.plusDays(1)
        repository.completeOccurrence(taskId, occurrenceDate)
        val originalPlayerAttack = database.combatDao()
            .getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())!!
        repository.failOccurrence(taskId, failedDate)
        database.combatDao().insertMonsterAttackEvent(
            monsterAttackEvent(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
                recurrenceSeriesId = taskId,
            ),
        )

        val newTaskId = repository.updateTask(
            updateInput(
                taskId = taskId,
                effectiveDate = occurrenceDate,
                difficulty = TaskDifficulty.HARD,
                recurrenceRule = RecurrenceRule.DAILY,
            ),
        )

        assertNull(database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay()))
        assertNull(database.combatDao().getMonsterAttackEvent(taskId, occurrenceDate.toEpochDay()))
        assertEquals(
            taskId,
            database.combatDao().getPlayerAttackEvent(newTaskId, occurrenceDate.toEpochDay())?.recurrenceSeriesId,
        )
        assertEquals(
            originalPlayerAttack.copy(taskId = newTaskId),
            database.combatDao().getPlayerAttackEvent(newTaskId, occurrenceDate.toEpochDay()),
        )
        assertEquals(TaskDifficulty.EASY.name, originalPlayerAttack.sourceTaskDifficulty)
        assertEquals(
            TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            originalPlayerAttack.taskDifficultyBalanceVersion,
        )
        assertEquals(
            taskId,
            database.combatDao().getMonsterAttackEvent(newTaskId, occurrenceDate.toEpochDay())?.recurrenceSeriesId,
        )
        assertNull(database.failureLogDao().find(taskId, failedDate.toEpochDay()))
        assertEquals(
            taskId,
            database.failureLogDao().find(newTaskId, failedDate.toEpochDay())?.recurrenceSeriesId,
        )
    }

    @Test
    fun repairCreatesOnlyMissingEligibleOutboxAndDoesNotBackfillV3Ledger() = runTest {
        val eligibleDate = LocalDate.of(2026, 7, 14)
        val v3Date = LocalDate.of(2026, 7, 13)
        val eligibleTaskId = repository.createTask(taskInput())
        database.rewardLedgerDao().insert(
            rewardLedger(
                taskId = eligibleTaskId,
                occurrenceDateEpochDay = eligibleDate.toEpochDay(),
            ).copy(
                recurrenceSeriesId = eligibleTaskId,
                dailyOrdinal = 1,
                combatEligible = true,
            ),
        )
        database.rewardLedgerDao().insert(
            rewardLedger(taskId = 200L, occurrenceDateEpochDay = v3Date.toEpochDay()).copy(
                dailyOrdinal = 0,
                combatEligible = false,
                balanceVersion = 0,
            ),
        )

        val duplicateCompletion = repository.completeOccurrence(eligibleTaskId, eligibleDate)
        assertTrue(duplicateCompletion.alreadyRewarded)
        assertNull(
            database.combatDao().getPlayerAttackEvent(
                eligibleTaskId,
                eligibleDate.toEpochDay(),
            ),
        )

        val repaired = repository.repairMissingPlayerAttackEvents()
        val retried = repository.repairMissingPlayerAttackEvents()

        assertEquals(1, repaired)
        assertEquals(0, retried)
        val repairedEvent = database.combatDao().getPlayerAttackEvent(
            eligibleTaskId,
            eligibleDate.toEpochDay(),
        )
        assertNotNull(repairedEvent)
        assertNull(repairedEvent?.sourceTaskDifficulty)
        assertEquals(
            TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
            repairedEvent?.taskDifficultyBalanceVersion,
        )
        assertEquals(0, repairedEvent?.combatRewardVersion)
        assertNull(database.combatDao().getPlayerAttackEvent(200L, v3Date.toEpochDay()))
    }

    @Test
    fun repairMissingPlayerAttackUsesCurrentEquippedModifierSnapshot() = runTest {
        characterRepository().resetStats()
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        equip(
            ownEquipment(EquipmentCatalogSeeder.IRON_LONGSWORD_ID),
            EquipmentSlot.WEAPON,
        )
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val taskId = repository.createTask(taskInput())
        val ledger = rewardLedger(taskId, occurrenceDate.toEpochDay()).copy(
            recurrenceSeriesId = taskId,
            dailyOrdinal = 1,
            combatEligible = true,
        )
        database.rewardLedgerDao().insert(ledger)
        val persistedLedger = database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay())!!

        assertEquals(1, repository.repairMissingPlayerAttackEvents())

        val event = database.combatDao().getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())!!
        assertEquals(38, event.sourceAttack)
        assertEquals(750, event.sourceCriticalChanceBp)
        assertEquals(15_400, event.sourceCriticalDamageBp)
        assertEquals(0, event.combatRewardVersion)
        assertEquals(
            persistedLedger,
            database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()),
        )
    }

    @Test
    fun existingLedgerIsNeverRecomputedByANewerBalanceConfig() = runTest {
        val taskId = repository.createTask(taskInput(difficulty = TaskDifficulty.MEDIUM))
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        repository.completeOccurrence(taskId, occurrenceDate)
        val originalLedger = database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay())!!

        val newerRepository = RoomTaskRepository(
            database = database,
            clock = FixedClock,
            balanceConfig = CharacterStatBalanceConfig(
                version = 2,
                onTimeRewardMultiplierBp = 12_000,
            ),
        )
        val retry = newerRepository.completeOccurrence(taskId, occurrenceDate)

        assertEquals(
            CompletionResult(
                0,
                0,
                alreadyRewarded = true,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK,
            ),
            retry,
        )
        assertEquals(originalLedger, database.rewardLedgerDao().find(taskId, occurrenceDate.toEpochDay()))
        assertEquals(0L, database.characterProfileDao().getProfile()?.totalXp)
        assertEquals(0L, database.characterProfileDao().getProfile()?.currentGold)
    }

    private fun characterRepository() = RoomCharacterRepository(database, FixedClock)

    private suspend fun ownEquipment(equipmentId: Long): Long =
        database.equipmentDao().insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = equipmentId,
                acquiredAtEpochMillis = FixedClock.now().toEpochMilli(),
            ),
        ).also { check(it != -1L) }

    private suspend fun equip(ownedEquipmentId: Long, slot: EquipmentSlot) {
        database.equipmentDao().upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, slot.name, ownedEquipmentId),
        )
    }

    private fun taskInput(
        title: String = "Quest",
        memo: String = "",
        startDate: LocalDate = LocalDate.of(2026, 7, 14),
        time: LocalTime? = null,
        difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        category: String = "General",
        reminderSetting: ReminderSetting = ReminderSetting(),
    ) = CreateTaskInput(
        title = title,
        memo = memo,
        startDate = startDate,
        time = time,
        difficulty = difficulty,
        category = category,
        recurrenceRule = recurrenceRule,
        reminderSetting = reminderSetting,
    )

    private fun updateInput(
        taskId: Long,
        effectiveDate: LocalDate,
        title: String = "Updated quest",
        memo: String = "",
        time: LocalTime? = null,
        difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
        category: String = "General",
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        reminderSetting: ReminderSetting = ReminderSetting(),
    ) = UpdateTaskInput(
        taskId = taskId,
        effectiveDate = effectiveDate,
        title = title,
        memo = memo,
        time = time,
        difficulty = difficulty,
        category = category,
        recurrenceRule = recurrenceRule,
        reminderSetting = reminderSetting,
    )

    private fun todoTaskEntity(
        id: Long,
        title: String,
        category: String,
    ) = com.todoquest.data.local.TodoTaskEntity(
        id = id,
        title = title,
        memo = "",
        startDateEpochDay = LocalDate.of(2026, 7, 14).toEpochDay(),
        endDateEpochDay = null,
        timeMinuteOfDay = null,
        difficulty = TaskDifficulty.MEDIUM.name,
        category = category,
        recurrenceRule = RecurrenceRule.NONE.name,
        createdAtEpochMillis = FixedClock.now().toEpochMilli(),
        updatedAtEpochMillis = FixedClock.now().toEpochMilli(),
        deletedAtEpochMillis = null,
    )

    private fun completionLog(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ) = CompletionLogEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        completedAtEpochMillis = FixedClock.now().toEpochMilli(),
    )

    private fun failureLog(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ) = FailureLogEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        recurrenceSeriesId = taskId,
        failedAtEpochMillis = FixedClock.now().toEpochMilli(),
    )

    private fun rewardLedger(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ) = RewardLedgerEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        recurrenceSeriesId = taskId,
        xpAward = 20,
        goldAward = 10,
        rewardLocalDateEpochDay = occurrenceDateEpochDay,
        onTime = false,
        onTimeMultiplierBp = 10_000,
        rewardEfficiencyBp = 10_000,
        repeatOrdinal = 0,
        dailyOrdinal = 0,
        goldGainBonusBp = 0,
        combatEligible = false,
        balanceVersion = 1,
        awardedAtEpochMillis = FixedClock.now().toEpochMilli(),
    )

    private suspend fun insertOnTimeLedger(taskId: Long, occurrenceDate: LocalDate) {
        database.rewardLedgerDao().insert(
            rewardLedger(taskId, occurrenceDate.toEpochDay()).copy(
                rewardLocalDateEpochDay = occurrenceDate.toEpochDay(),
                onTime = true,
                onTimeMultiplierBp = 11_000,
            ),
        )
    }

    private fun monsterAttackEvent(
        taskId: Long,
        occurrenceDateEpochDay: Long,
        recurrenceSeriesId: Long,
    ) = MonsterAttackEventEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        recurrenceSeriesId = recurrenceSeriesId,
        trigger = MonsterAttackTrigger.MISSED_DEADLINE.name,
        status = CombatEventStatus.SKIPPED.name,
        skipReason = MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP.name,
        sourceMonsterInstanceId = 1L,
        sourceMonsterLevel = 1,
        sourceRawDamage = 12,
        playerDefense = 8,
        playerMaxHp = 110,
        finalDamage = 0,
        playerHpBefore = 110,
        playerHpAfter = 110,
        wasLethal = false,
        revivedHp = null,
        characterBalanceVersion = 1,
        monsterBalanceVersion = MonsterBalanceConfig().version,
        processedAtEpochMillis = FixedClock.now().toEpochMilli(),
    )

    private fun severeInjury() = CharacterStatusEffectEntity(
        characterId = 1L,
        effectType = StatusEffectType.SEVERE_INJURY.name,
        definitionVersion = 1,
        appliedAtEpochMillis = Instant.parse("2026-07-14T09:00:00Z").toEpochMilli(),
        expiresAtEpochMillis = Instant.parse("2026-07-15T09:00:00Z").toEpochMilli(),
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1L,
        lastMutationId = "monster-attack:apply",
    )

    private fun createVersion1Database(context: Context, databaseName: String) {
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val sqliteDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            sqliteDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `memo` TEXT NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL,
                    `timeMinuteOfDay` INTEGER,
                    `difficulty` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `recurrenceRule` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    `deletedAtEpochMillis` INTEGER
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_tasks_startDateEpochDay` " +
                    "ON `todo_tasks` (`startDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_tasks_deletedAtEpochMillis` " +
                    "ON `todo_tasks` (`deletedAtEpochMillis`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `completion_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `completedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_completion_logs_taskId_occurrenceDateEpochDay` " +
                    "ON `completion_logs` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reward_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `xp` INTEGER NOT NULL,
                    `gold` INTEGER NOT NULL,
                    `awardedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reward_ledger_taskId_occurrenceDateEpochDay` " +
                    "ON `reward_ledger` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `character_profile` (
                    `id` INTEGER NOT NULL,
                    `level` INTEGER NOT NULL,
                    `totalXp` INTEGER NOT NULL,
                    `currentGold` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO `todo_tasks` (
                    `id`,
                    `title`,
                    `memo`,
                    `startDateEpochDay`,
                    `timeMinuteOfDay`,
                    `difficulty`,
                    `category`,
                    `recurrenceRule`,
                    `createdAtEpochMillis`,
                    `updatedAtEpochMillis`,
                    `deletedAtEpochMillis`
                ) VALUES (
                    1,
                    'Legacy task',
                    '',
                    ${LocalDate.of(2026, 7, 14).toEpochDay()},
                    NULL,
                    'MEDIUM',
                    'General',
                    'DAILY',
                    1,
                    1,
                    NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL("PRAGMA user_version = 1")
        } finally {
            sqliteDatabase.close()
        }
    }

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = Instant.parse("2026-07-14T09:00:00Z")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 14)
    }

    private class MutableClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        var instant: Instant = Instant.parse("2026-07-14T09:00:00Z")

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }
}
