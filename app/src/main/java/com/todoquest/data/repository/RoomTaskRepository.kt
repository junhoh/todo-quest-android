package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.local.CompletionLogEntity
import com.todoquest.data.local.FailureLogEntity
import com.todoquest.data.local.PlayerAttackEventEntity
import com.todoquest.data.local.RewardLedgerEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.data.mapper.TaskReminderMapper
import com.todoquest.data.mapper.TodoTaskMapper
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.CompletionRewardMode
import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.OccurrenceStateConflictException
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskDifficultyCombatBalanceCatalog
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.OccurrenceCalculator
import com.todoquest.domain.usecase.OnTimePolicy
import com.todoquest.domain.usecase.RewardPolicy
import com.todoquest.domain.usecase.StreakPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomTaskRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
    private val occurrenceCalculator: OccurrenceCalculator = OccurrenceCalculator(),
    private val balanceConfig: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
    private val monsterBalanceConfig: MonsterBalanceConfig = MonsterBalanceConfig(),
) : TaskRepository {
    private val taskDao = database.todoTaskDao()
    private val completionLogDao = database.completionLogDao()
    private val failureLogDao = database.failureLogDao()
    private val rewardLedgerDao = database.rewardLedgerDao()
    private val characterProfileDao = database.characterProfileDao()
    private val combatDao = database.combatDao()
    private val taskReminderDao = database.taskReminderDao()

    override fun observeOccurrences(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): Flow<List<TaskOccurrence>> {
        val tasks = taskDao.observeActiveTasksStartingBefore(rangeEnd.toEpochDay())
        val completions = completionLogDao.observeBetween(rangeStart.toEpochDay(), rangeEnd.toEpochDay())
        val failures = failureLogDao.observeBetween(rangeStart.toEpochDay(), rangeEnd.toEpochDay())
        val reminders = taskReminderDao.observeAll()
        return combine(
            tasks,
            completions,
            failures,
            reminders,
        ) { taskEntities, completionEntities, failureEntities, reminderEntities ->
            val completedByTask = completionEntities
                .groupBy { it.taskId }
                .mapValues { entry -> entry.value.map { LocalDate.ofEpochDay(it.occurrenceDateEpochDay) }.toSet() }
            val failedByTask = failureEntities
                .groupBy { it.taskId }
                .mapValues { entry -> entry.value.map { LocalDate.ofEpochDay(it.occurrenceDateEpochDay) }.toSet() }
            val reminderEntityByTask = reminderEntities.associateBy { it.taskId }

            taskEntities
                .flatMap { entity ->
                    val task = TodoTaskMapper.toDomain(entity)
                    val reminder = reminderEntityByTask[task.id]
                        ?.let(TaskReminderMapper::toScheduleState)
                    occurrenceCalculator.occurrencesFor(
                        task = task,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        completedDates = completedByTask[task.id].orEmpty(),
                        failedDates = failedByTask[task.id].orEmpty(),
                    ).map { occurrence ->
                        occurrence.copy(
                            reminderSetting = reminder?.setting ?: ReminderSetting(),
                            reminderScheduleStatus = reminder?.status ?: ReminderScheduleStatus.DISABLED,
                        )
                    }
                }
                .sortedWith(
                    compareBy<TaskOccurrence> { it.occurrenceDate }
                        .thenBy { it.time }
                        .thenBy { it.title.lowercase() },
                )
        }
    }

    override suspend fun createTask(input: CreateTaskInput): Long {
        require(input.title.isNotBlank()) { "Task title must not be blank" }
        return database.withTransaction {
            val now = clock.now()
            val taskId = taskDao.insert(TodoTaskMapper.fromInput(input, now))
            taskDao.setRecurrenceSeriesId(taskId, taskId)
            taskReminderDao.upsert(
                TaskReminderMapper.fromSetting(
                    taskId = taskId,
                    setting = input.reminderSetting,
                    existing = null,
                    updatedAt = now,
                ),
            )
            taskId
        }
    }

    override suspend fun getTask(taskId: Long): TodoTask? = database.withTransaction {
        val entity = taskDao.getActiveById(taskId) ?: return@withTransaction null
        TodoTaskMapper.toDomain(entity, taskReminderDao.getByTaskId(taskId))
    }

    override suspend fun updateTask(task: TodoTask) {
        require(task.title.isNotBlank()) { "Task title must not be blank" }
        database.withTransaction {
            val existing = taskDao.getActiveById(task.id) ?: error("Task ${task.id} not found")
            val existingReminder = taskReminderDao.getByTaskId(task.id)
            val now = clock.now()
            taskDao.update(TodoTaskMapper.fromDomain(task, existing, now))
            taskReminderDao.upsert(
                TaskReminderMapper.fromSetting(
                    taskId = task.id,
                    setting = task.reminderSetting,
                    existing = existingReminder,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun updateTask(input: UpdateTaskInput): Long = database.withTransaction {
        require(input.title.trim().isNotEmpty()) { "Task title must not be blank" }
        val existingEntity = taskDao.getActiveById(input.taskId)
            ?: error("Task ${input.taskId} not found")
        val existingReminder = taskReminderDao.getByTaskId(input.taskId)
        val existing = TodoTaskMapper.toDomain(existingEntity, existingReminder)
        require(occurrenceCalculator.occursOn(existing, input.effectiveDate)) {
            "Task ${input.taskId} does not occur on ${input.effectiveDate}"
        }

        val now = clock.now()
        if (existing.recurrenceRule == RecurrenceRule.NONE || !input.effectiveDate.isAfter(existing.startDate)) {
            val updated = existing.copy(
                title = input.title,
                memo = input.memo,
                time = input.time,
                difficulty = input.difficulty,
                category = input.category,
                recurrenceRule = input.recurrenceRule,
            )
            taskDao.update(TodoTaskMapper.fromDomain(updated, existingEntity, now))
            taskReminderDao.upsert(
                TaskReminderMapper.fromSetting(
                    taskId = existing.id,
                    setting = input.reminderSetting,
                    existing = existingReminder,
                    updatedAt = now,
                ),
            )
            existing.id
        } else {
            taskDao.update(
                TodoTaskMapper.fromDomain(
                    existing.copy(endDate = input.effectiveDate.minusDays(1)),
                    existingEntity,
                    now,
                ),
            )

            val newTaskId = taskDao.insert(
                newTaskEntityFrom(
                    input = input,
                    existing = existing,
                    recurrenceSeriesId = existingEntity.recurrenceSeriesId,
                    now = now,
                ),
            )
            taskReminderDao.upsert(
                TaskReminderMapper.fromSetting(
                    taskId = newTaskId,
                    setting = input.reminderSetting,
                    existing = null,
                    updatedAt = now,
                ),
            )
            val fromEpochDay = input.effectiveDate.toEpochDay()
            completionLogDao.reassignFrom(existing.id, fromEpochDay, newTaskId)
            failureLogDao.reassignFrom(existing.id, fromEpochDay, newTaskId)
            rewardLedgerDao.reassignFrom(existing.id, fromEpochDay, newTaskId)
            combatDao.reassignPlayerAttackEventsFrom(existing.id, fromEpochDay, newTaskId)
            combatDao.reassignMonsterAttackEventsFrom(existing.id, fromEpochDay, newTaskId)
            database.statusEffectDao().reassignRecoveryOccurrencesFrom(
                existing.id,
                fromEpochDay,
                newTaskId,
            )
            newTaskId
        }
    }

    override suspend fun deleteTask(taskId: Long) {
        taskDao.softDelete(taskId, clock.now().toEpochMilli())
    }

    override suspend fun deleteTask(taskId: Long, effectiveDate: LocalDate) {
        database.withTransaction {
            val existingEntity = taskDao.getActiveById(taskId)
                ?: error("Task $taskId not found")
            val existing = TodoTaskMapper.toDomain(existingEntity)
            require(occurrenceCalculator.occursOn(existing, effectiveDate)) {
                "Task $taskId does not occur on $effectiveDate"
            }

            val now = clock.now()
            if (existing.recurrenceRule == RecurrenceRule.NONE || !effectiveDate.isAfter(existing.startDate)) {
                taskDao.softDelete(taskId, now.toEpochMilli())
            } else {
                taskDao.update(
                    TodoTaskMapper.fromDomain(
                        existing.copy(endDate = effectiveDate.minusDays(1)),
                        existingEntity,
                        now,
                    ),
                )
            }
        }
    }

    override suspend fun completeOccurrence(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): CompletionResult = database.withTransaction {
        val taskEntity = taskDao.getActiveById(taskId)
            ?: error("Task $taskId not found")
        val task = TodoTaskMapper.toDomain(taskEntity)

        require(occurrenceCalculator.occursOn(task, occurrenceDate)) {
            "Task $taskId does not occur on $occurrenceDate"
        }

        val occurrenceEpochDay = occurrenceDate.toEpochDay()
        if (failureLogDao.find(taskId, occurrenceEpochDay) != null) {
            throw OccurrenceStateConflictException(
                taskId = taskId,
                occurrenceDate = occurrenceDate,
                currentStatus = TaskOccurrenceStatus.FAILED,
                requestedStatus = TaskOccurrenceStatus.COMPLETED,
            )
        }
        val completedAt = clock.now()
        val nowMillis = completedAt.toEpochMilli()
        reconcileExpiredStatusEffects(
            database = database,
            characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            now = completedAt,
        )
        val completionInserted = completionLogDao.insert(
            CompletionLogEntity(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceEpochDay,
                completedAtEpochMillis = nowMillis,
            ),
        ) != -1L
        if (completionInserted) {
            creditCompletionRecovery(
                database = database,
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceEpochDay,
                completedAt = completedAt,
            )
        }

        val existingReward = rewardLedgerDao.find(taskId, occurrenceEpochDay)
        if (existingReward != null) {
            CompletionResult(
                awardedXp = 0,
                awardedGold = 0,
                alreadyRewarded = true,
                rewardMode = existingReward.rewardMode.toCompletionRewardMode(),
            )
        } else {
            val onTime = OnTimePolicy.evaluate(
                occurrenceDate = occurrenceDate,
                scheduledTime = task.time,
                completedAt = completedAt,
                zoneId = clock.zoneId,
            )
            val rewardDateEpochDay = onTime.rewardLocalDate.toEpochDay()
            val dailyOrdinal = rewardLedgerDao.countForRewardLocalDate(rewardDateEpochDay) + 1
            val repeatOrdinal = rewardLedgerDao.countForRecurrenceSeriesOnRewardLocalDate(
                recurrenceSeriesId = taskEntity.recurrenceSeriesId,
                rewardLocalDateEpochDay = rewardDateEpochDay,
            ) + 1

            val profileEntity = characterProfileDao.getProfile()
            val currentStateEntity = characterProfileDao.getCurrentState()
            check(profileEntity != null || currentStateEntity == null) {
                "character current state cannot exist without its profile"
            }
            val character = profileEntity?.let(CharacterMapper::toDomain)
                ?: CharacterMapper.defaultCharacter(balanceConfig)
            val equipmentModifiers = loadEquippedEquipmentModifiers(database, character.id)
            val statusModifiers = loadActiveStatusModifiers(database, character.id, completedAt)
            val oldDerivedStats = derivedStatsFor(
                character,
                balanceConfig,
                equipmentModifiers,
                statusModifiers,
            )
            val currentState = currentStateEntity?.let(CharacterMapper::toDomain)
                ?: defaultCurrentState(
                    character = character,
                    derivedStats = oldDerivedStats,
                    config = balanceConfig,
                    updatedAtEpochMillis = nowMillis,
                )
            val goldGainBonusBp = oldDerivedStats.goldGainBonusBp
            val reward = RewardPolicy.rewardFor(
                input = RewardPolicy.Input(
                    difficulty = task.difficulty,
                    isOnTime = onTime.isOnTime,
                    recurringRootSequence = repeatOrdinal,
                    dailySequence = dailyOrdinal,
                    goldGainBonusBp = goldGainBonusBp,
                ),
                config = balanceConfig,
            )
            val rewardLedger = RewardLedgerEntity(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceEpochDay,
                recurrenceSeriesId = taskEntity.recurrenceSeriesId,
                xpAward = 0L,
                goldAward = 0L,
                rewardLocalDateEpochDay = rewardDateEpochDay,
                onTime = onTime.isOnTime,
                onTimeMultiplierBp = reward.onTimeMultiplierBp,
                rewardEfficiencyBp = reward.rewardEfficiencyBp,
                repeatOrdinal = repeatOrdinal,
                dailyOrdinal = dailyOrdinal,
                goldGainBonusBp = goldGainBonusBp,
                combatEligible = true,
                balanceVersion = balanceConfig.version,
                awardedAtEpochMillis = nowMillis,
                rewardMode = CompletionRewardMode.COMBAT_ATTACK.name,
            )
            val insertedId = rewardLedgerDao.insert(rewardLedger)
            if (insertedId == -1L) {
                CompletionResult(
                    awardedXp = 0,
                    awardedGold = 0,
                    alreadyRewarded = true,
                    rewardMode = CompletionRewardMode.COMBAT_ATTACK,
                )
            } else {
                if (profileEntity == null) {
                    check(
                        characterProfileDao.insertCharacterIfAbsent(
                            profile = CharacterMapper.fromDomain(character),
                            currentState = CharacterMapper.fromDomain(currentState),
                            appearance = CharacterMapper.fromDomain(
                                character.id,
                                CharacterLoadoutCatalog.defaultAppearance,
                            ),
                            equippedItems = CharacterMapper.fromDomain(
                                character.id,
                                CharacterLoadoutCatalog.defaultEquippedItems,
                            ),
                        ),
                    ) {
                        "character source state could not be initialized"
                    }
                }
                val onTimeOccurrenceDates = rewardLedgerDao
                    .findOnTimeOccurrenceDatesThrough(rewardDateEpochDay)
                    .map(LocalDate::ofEpochDay)
                check(
                    combatDao.insertPlayerAttackEvent(
                        pendingPlayerAttackEvent(
                            rewardLedger = rewardLedger,
                            character = character,
                            derivedStats = oldDerivedStats,
                            momentumReferenceDate = onTime.rewardLocalDate,
                            onTimeOccurrenceDates = onTimeOccurrenceDates,
                            createdAtEpochMillis = nowMillis,
                            sourceTaskDifficulty = task.difficulty,
                            taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
                        ),
                    ) != -1L,
                ) { "player attack outbox could not be created" }
                CompletionResult(
                    awardedXp = 0L,
                    awardedGold = 0L,
                    alreadyRewarded = false,
                    isOnTime = onTime.isOnTime,
                    rewardEfficiencyBp = reward.rewardEfficiencyBp,
                    rewardMode = CompletionRewardMode.COMBAT_ATTACK,
                )
            }
        }
    }

    internal suspend fun repairMissingPlayerAttackEvents(): Int = database.withTransaction {
        val missingRewards = rewardLedgerDao.findCombatEligibleWithoutPlayerAttackEvent()
        if (missingRewards.isEmpty()) {
            return@withTransaction 0
        }

        val character = characterProfileDao.getProfile()?.let(CharacterMapper::toDomain)
            ?: CharacterMapper.defaultCharacter(balanceConfig)
        val now = clock.now()
        reconcileExpiredStatusEffects(database, character.id, now)
        val equipmentModifiers = loadEquippedEquipmentModifiers(database, character.id)
        val statusModifiers = loadActiveStatusModifiers(database, character.id, now)
        val derivedStats = derivedStatsFor(
            character,
            balanceConfig,
            equipmentModifiers,
            statusModifiers,
        )
        val latestReferenceEpochDay = missingRewards.maxOf(RewardLedgerEntity::rewardLocalDateEpochDay)
        val onTimeOccurrenceDates = rewardLedgerDao
            .findOnTimeOccurrenceDatesThrough(latestReferenceEpochDay)
            .map(LocalDate::ofEpochDay)
        val createdAtEpochMillis = now.toEpochMilli()

        missingRewards.count { rewardLedger ->
            combatDao.insertPlayerAttackEvent(
                pendingPlayerAttackEvent(
                    rewardLedger = rewardLedger,
                    character = character,
                    derivedStats = derivedStats,
                    momentumReferenceDate = LocalDate.ofEpochDay(rewardLedger.rewardLocalDateEpochDay),
                    onTimeOccurrenceDates = onTimeOccurrenceDates,
                    createdAtEpochMillis = createdAtEpochMillis,
                    sourceTaskDifficulty = null,
                    taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
                ),
            ) != -1L
        }
    }

    override suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        completionLogDao.delete(taskId, occurrenceDate.toEpochDay())
    }

    override suspend fun failOccurrence(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): FailureResult = database.withTransaction {
        val taskEntity = taskDao.getActiveById(taskId)
            ?: error("Task $taskId not found")
        val task = TodoTaskMapper.toDomain(taskEntity)
        require(occurrenceCalculator.occursOn(task, occurrenceDate)) {
            "Task $taskId does not occur on $occurrenceDate"
        }

        val occurrenceEpochDay = occurrenceDate.toEpochDay()
        if (completionLogDao.find(taskId, occurrenceEpochDay) != null) {
            throw OccurrenceStateConflictException(
                taskId = taskId,
                occurrenceDate = occurrenceDate,
                currentStatus = TaskOccurrenceStatus.COMPLETED,
                requestedStatus = TaskOccurrenceStatus.FAILED,
            )
        }
        val insertedId = failureLogDao.insert(
            FailureLogEntity(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceEpochDay,
                recurrenceSeriesId = taskEntity.recurrenceSeriesId,
                failedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
        FailureResult(wasAlreadyFailed = insertedId == -1L)
    }

    override suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        failureLogDao.delete(taskId, occurrenceDate.toEpochDay())
    }

    private fun newTaskEntityFrom(
        input: UpdateTaskInput,
        existing: TodoTask,
        recurrenceSeriesId: Long,
        now: Instant,
    ) = com.todoquest.data.local.TodoTaskEntity(
        recurrenceSeriesId = recurrenceSeriesId,
        title = input.title.trim(),
        memo = input.memo.trim(),
        startDateEpochDay = input.effectiveDate.toEpochDay(),
        endDateEpochDay = existing.endDate?.toEpochDay(),
        timeMinuteOfDay = input.time?.toMinuteOfDay(),
        difficulty = input.difficulty.name,
        category = TaskCategory.normalize(input.category),
        recurrenceRule = input.recurrenceRule.name,
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
        deletedAtEpochMillis = null,
    )

    private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

    private fun pendingPlayerAttackEvent(
        rewardLedger: RewardLedgerEntity,
        character: PlayerCharacter,
        derivedStats: DerivedStats,
        momentumReferenceDate: LocalDate,
        onTimeOccurrenceDates: List<LocalDate>,
        createdAtEpochMillis: Long,
        sourceTaskDifficulty: TaskDifficulty?,
        taskDifficultyBalanceVersion: Int,
    ): PlayerAttackEventEntity {
        val momentum = StreakPolicy.calculate(
            onTimeOccurrenceDates = onTimeOccurrenceDates,
            referenceDate = momentumReferenceDate,
            config = balanceConfig,
        )
        return PlayerAttackEventEntity(
            taskId = rewardLedger.taskId,
            occurrenceDateEpochDay = rewardLedger.occurrenceDateEpochDay,
            recurrenceSeriesId = rewardLedger.recurrenceSeriesId,
            status = CombatEventStatus.PENDING.name,
            sourcePlayerLevel = CharacterProgressionPolicy.levelFor(character.totalXp, balanceConfig),
            sourceAttack = derivedStats.attack,
            sourceCriticalChanceBp = derivedStats.criticalChanceBp,
            sourceCriticalDamageBp = derivedStats.criticalDamageBp,
            sourceMomentumBp = momentum.momentumBonusBp,
            characterBalanceVersion = balanceConfig.version,
            monsterBalanceVersion = monsterBalanceConfig.version,
            createdAtEpochMillis = createdAtEpochMillis,
            targetMonsterInstanceId = null,
            seed = null,
            roll = null,
            wasCritical = null,
            rawDamage = null,
            targetDefense = null,
            finalDamage = null,
            targetHpBefore = null,
            targetHpAfter = null,
            processedAtEpochMillis = null,
            combatRewardVersion = if (
                rewardLedger.rewardMode.toCompletionRewardMode() == CompletionRewardMode.COMBAT_ATTACK
            ) {
                CombatRewardBalanceCatalog.CURRENT_VERSION
            } else {
                0
            },
            sourceTaskDifficulty = sourceTaskDifficulty?.name,
            taskDifficultyBalanceVersion = taskDifficultyBalanceVersion,
        )
    }
}

private fun String.toCompletionRewardMode(): CompletionRewardMode =
    CompletionRewardMode.entries.singleOrNull { it.name == this }
        ?: throw IllegalArgumentException("Unknown completion reward mode: $this")
