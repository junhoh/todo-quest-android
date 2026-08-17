package com.todoquest.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.WorkManager
import com.todoquest.audio.AndroidBattleSfxPlayer
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.ConfiguredBattleSfxPlayer
import com.todoquest.audio.NoOpBattleSfxPlayer
import com.todoquest.background.CombatReconciliationWork
import com.todoquest.core.AppClock
import com.todoquest.core.SystemAppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.repository.RoomCharacterRepository
import com.todoquest.data.repository.RoomCombatRepository
import com.todoquest.data.repository.RoomEquipmentRepository
import com.todoquest.data.repository.RoomReminderRepository
import com.todoquest.data.repository.RoomStatusEffectRepository
import com.todoquest.data.repository.RoomTaskRepository
import com.todoquest.data.repository.SharedPreferencesCharacterGuideRepository
import com.todoquest.data.repository.SharedPreferencesBattleSfxSettingsRepository
import com.todoquest.domain.model.CharacterStatGuideStatus
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.domain.repository.CharacterGuideRepository
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore
import com.todoquest.domain.repository.ReminderPublisher
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderRepository
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.AcknowledgeCharacterStatGuideUseCase
import com.todoquest.domain.usecase.CombatProcessingDiagnosticSink
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.CreateTaskUseCase
import com.todoquest.domain.usecase.DeleteTaskUseCase
import com.todoquest.domain.usecase.DeliverReminderUseCase
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
import com.todoquest.domain.usecase.PurchaseEquipmentUseCase
import com.todoquest.domain.usecase.PrepareCharacterStatGuideUseCase
import com.todoquest.domain.usecase.PrepareFirstLaunchNotificationPromptUseCase
import com.todoquest.domain.usecase.ReconcileAllRemindersUseCase
import com.todoquest.domain.usecase.ReconcileCombatUseCase
import com.todoquest.domain.usecase.ReconcileTaskReminderUseCase
import com.todoquest.domain.usecase.ReminderDiagnosticSink
import com.todoquest.domain.usecase.UndoCompleteOccurrenceUseCase
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import com.todoquest.domain.usecase.UnequipEquipmentUseCase
import com.todoquest.domain.usecase.UpdateTaskUseCase
import com.todoquest.notification.AndroidReminderCapabilityAdapter
import com.todoquest.notification.AndroidReminderPublisher
import com.todoquest.notification.AndroidReminderScheduler
import com.todoquest.notification.ReminderReconciliationWork
import com.todoquest.notification.ReminderRuntimeProvider
import com.todoquest.notification.SharedPreferencesFirstLaunchNotificationPromptStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface TodoQuestContainerOwner : ReminderRuntimeProvider {
    val todoQuestContainer: TodoQuestAppContainer

    override val deliverReminderUseCase: DeliverReminderUseCase
        get() = todoQuestContainer.deliverReminderUseCase

    override val reconcileAllRemindersUseCase: ReconcileAllRemindersUseCase
        get() = todoQuestContainer.reconcileAllRemindersUseCase
}

class TodoQuestAppContainer(
    val database: TodoQuestDatabase,
    val clock: AppClock,
    taskRepository: TaskRepository? = null,
    characterRepository: CharacterRepository? = null,
    combatRepository: CombatRepository? = null,
    equipmentRepository: EquipmentRepository? = null,
    statusEffectRepository: StatusEffectRepository? = null,
    characterGuideRepository: CharacterGuideRepository? = null,
    val battleSfxSettingsRepository: BattleSfxSettingsRepository =
        DefaultBattleSfxSettingsRepository(),
    val battleSfxPlayer: BattleSfxPlayer = NoOpBattleSfxPlayer,
    reminderRepository: ReminderRepository? = null,
    reminderScheduler: ReminderScheduler? = null,
    reminderPublisher: ReminderPublisher? = null,
    val reminderCapabilityAdapter: AndroidReminderCapabilityAdapter? = null,
    val firstLaunchNotificationPromptStore: FirstLaunchNotificationPromptStore =
        InactiveFirstLaunchNotificationPromptStore,
    val prepareFirstLaunchNotificationPromptUseCase:
        PrepareFirstLaunchNotificationPromptUseCase = PrepareFirstLaunchNotificationPromptUseCase(
            firstLaunchNotificationPromptStore,
            reminderScheduler ?: InactiveReminderScheduler,
        ),
    combatDiagnosticSink: CombatProcessingDiagnosticSink = AndroidCombatProcessingDiagnosticSink,
    reminderDiagnosticSink: ReminderDiagnosticSink = AndroidReminderDiagnosticSink,
) {
    private val audioReleaseLock = Any()
    private var audioReleased = false

    val taskRepository: TaskRepository = taskRepository ?: RoomTaskRepository(database, clock)
    val characterRepository: CharacterRepository =
        characterRepository ?: RoomCharacterRepository(database, clock)
    val combatRepository: CombatRepository =
        combatRepository ?: RoomCombatRepository(database, clock)
    val equipmentRepository: EquipmentRepository =
        equipmentRepository ?: RoomEquipmentRepository(database, clock)
    val statusEffectRepository: StatusEffectRepository =
        statusEffectRepository ?: RoomStatusEffectRepository(database, clock)
    val characterGuideRepository: CharacterGuideRepository =
        characterGuideRepository ?: InactiveCharacterGuideRepository
    val reminderRepository: ReminderRepository =
        reminderRepository ?: RoomReminderRepository(database, clock)
    val reminderScheduler: ReminderScheduler = reminderScheduler ?: InactiveReminderScheduler
    val reminderPublisher: ReminderPublisher = reminderPublisher ?: InactiveReminderPublisher
    val reconcileTaskReminderUseCase = ReconcileTaskReminderUseCase(
        repository = this.reminderRepository,
        scheduler = this.reminderScheduler,
        clock = clock,
        diagnosticSink = reminderDiagnosticSink,
    )
    val reconcileAllRemindersUseCase = ReconcileAllRemindersUseCase(
        repository = this.reminderRepository,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
    )
    val deliverReminderUseCase = DeliverReminderUseCase(
        repository = this.reminderRepository,
        publisher = this.reminderPublisher,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
        diagnosticSink = reminderDiagnosticSink,
    )
    val createTaskUseCase = CreateTaskUseCase(this.taskRepository, reconcileTaskReminderUseCase)
    val updateTaskUseCase = UpdateTaskUseCase(this.taskRepository, reconcileTaskReminderUseCase)
    val deleteTaskUseCase = DeleteTaskUseCase(this.taskRepository, reconcileTaskReminderUseCase)
    val completeOccurrenceUseCase = CompleteOccurrenceUseCase(
        repository = this.taskRepository,
        combatRepository = this.combatRepository,
        diagnosticSink = combatDiagnosticSink,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
    )
    val failOccurrenceUseCase = FailOccurrenceUseCase(
        repository = this.taskRepository,
        combatRepository = this.combatRepository,
        diagnosticSink = combatDiagnosticSink,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
    )
    val undoCompleteOccurrenceUseCase = UndoCompleteOccurrenceUseCase(
        repository = this.taskRepository,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
    )
    val undoFailOccurrenceUseCase = UndoFailOccurrenceUseCase(
        repository = this.taskRepository,
        reconcileTaskReminder = reconcileTaskReminderUseCase,
    )
    val reconcileCombatUseCase = ReconcileCombatUseCase(this.combatRepository, clock)
    val purchaseEquipmentUseCase = PurchaseEquipmentUseCase(this.equipmentRepository)
    val equipOwnedEquipmentUseCase = EquipOwnedEquipmentUseCase(this.equipmentRepository)
    val unequipEquipmentUseCase = UnequipEquipmentUseCase(this.equipmentRepository)
    val prepareCharacterStatGuideUseCase =
        PrepareCharacterStatGuideUseCase(this.characterGuideRepository)
    val acknowledgeCharacterStatGuideUseCase =
        AcknowledgeCharacterStatGuideUseCase(this.characterGuideRepository)

    fun releaseAudio() {
        val shouldRelease = synchronized(audioReleaseLock) {
            if (audioReleased) {
                false
            } else {
                audioReleased = true
                true
            }
        }
        if (!shouldRelease) return
        try {
            battleSfxPlayer.release()
        } catch (failure: Throwable) {
            Log.w("TodoQuestBattleSfx", "Battle SFX container release failed", failure)
        }
    }

    companion object {
        fun create(context: Context): TodoQuestAppContainer {
            val applicationContext = context.applicationContext
            val battleSfxDependencies = createProductionBattleSfxDependencies(applicationContext)
            return create(
                context = applicationContext,
                battleSfxDependencies = battleSfxDependencies,
            )
        }

        internal fun createForTest(
            context: Context,
            battleSfxSettingsRepository: BattleSfxSettingsRepository,
            battleSfxPlayer: BattleSfxPlayer,
        ): TodoQuestAppContainer = create(
            context = context.applicationContext,
            battleSfxDependencies = ProductionBattleSfxDependencies(
                settingsRepository = battleSfxSettingsRepository,
                player = battleSfxPlayer,
            ),
        )

        private fun create(
            context: Context,
            battleSfxDependencies: ProductionBattleSfxDependencies,
        ): TodoQuestAppContainer {
            val applicationContext = context.applicationContext
            val databaseExistedBeforeInitialization =
                todoQuestDatabaseExists(applicationContext)
            val characterGuideRepository = SharedPreferencesCharacterGuideRepository(
                context = applicationContext,
                eligibleOnFirstInitialization = !databaseExistedBeforeInitialization,
            )
            val database = Room.databaseBuilder(
                applicationContext,
                TodoQuestDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*PRODUCTION_MIGRATIONS)
                .build()
            val clock = SystemAppClock()
            val capabilityAdapter = AndroidReminderCapabilityAdapter(applicationContext)
            val reminderScheduler = AndroidReminderScheduler(
                applicationContext,
                capabilityAdapter,
            )
            return TodoQuestAppContainer(
                database = database,
                clock = clock,
                reminderScheduler = reminderScheduler,
                reminderPublisher = AndroidReminderPublisher(
                    applicationContext,
                    capabilityAdapter,
                ),
                reminderCapabilityAdapter = capabilityAdapter,
                characterGuideRepository = characterGuideRepository,
                battleSfxSettingsRepository = battleSfxDependencies.settingsRepository,
                battleSfxPlayer = battleSfxDependencies.player,
                firstLaunchNotificationPromptStore =
                    SharedPreferencesFirstLaunchNotificationPromptStore(applicationContext),
            )
        }

        internal val PRODUCTION_MIGRATIONS = arrayOf(
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

        const val DATABASE_NAME = "todo-quest.db"
    }
}

internal fun todoQuestDatabaseExists(
    context: Context,
    databaseName: String = TodoQuestAppContainer.DATABASE_NAME,
): Boolean = context.getDatabasePath(databaseName).exists()

class TodoQuestApplication : Application(), TodoQuestContainerOwner {
    private val containerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TodoQuestAppContainer.create(this)
    }
    override val todoQuestContainer: TodoQuestAppContainer
        get() = containerDelegate.value

    override fun onCreate() {
        super.onCreate()
        CombatReconciliationWork.enqueue(WorkManager.getInstance(this))
        isolateReminderInitialization("notification channel") {
            (todoQuestContainer.reminderPublisher as? AndroidReminderPublisher)
                ?.ensureNotificationChannel()
        }
        isolateReminderInitialization("startup reconciliation") {
            ReminderReconciliationWork.enqueueStartup(WorkManager.getInstance(this))
        }
    }

    override fun onTerminate() {
        if (containerDelegate.isInitialized()) {
            containerDelegate.value.releaseAudio()
        }
        super.onTerminate()
    }

    private inline fun isolateReminderInitialization(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            Log.e("TodoQuestReminder", "Reminder $operation initialization failed", failure)
        }
    }
}

internal data class ProductionBattleSfxDependencies(
    val settingsRepository: BattleSfxSettingsRepository,
    val player: BattleSfxPlayer,
)

internal fun createProductionBattleSfxDependencies(
    context: Context,
    settingsRepositoryFactory: (Context) -> BattleSfxSettingsRepository =
        ::SharedPreferencesBattleSfxSettingsRepository,
    playerFactory: (Context) -> BattleSfxPlayer = ::AndroidBattleSfxPlayer,
    onFailure: (String, Throwable) -> Unit = { operation, failure ->
        Log.w("TodoQuestBattleSfx", "Battle SFX $operation initialization failed", failure)
    },
): ProductionBattleSfxDependencies {
    val applicationContext = context.applicationContext
    val settingsRepository = try {
        settingsRepositoryFactory(applicationContext)
    } catch (failure: Throwable) {
        onFailure("settings repository", failure)
        return ProductionBattleSfxDependencies(
            settingsRepository = DefaultBattleSfxSettingsRepository(),
            player = NoOpBattleSfxPlayer,
        )
    }

    var delegate: BattleSfxPlayer? = null
    return try {
        delegate = playerFactory(applicationContext)
        ProductionBattleSfxDependencies(
            settingsRepository = settingsRepository,
            player = ConfiguredBattleSfxPlayer(
                settingsRepository = settingsRepository,
                delegate = delegate,
            ),
        )
    } catch (failure: Throwable) {
        try {
            delegate?.release()
        } catch (_: Throwable) {
            // The initialization failure is already isolated and logged below.
        }
        onFailure("player", failure)
        ProductionBattleSfxDependencies(
            settingsRepository = settingsRepository,
            player = NoOpBattleSfxPlayer,
        )
    }
}

private class DefaultBattleSfxSettingsRepository : BattleSfxSettingsRepository {
    private val mutableEnabled = MutableStateFlow(true)
    override val isEnabled: StateFlow<Boolean> = mutableEnabled

    override fun setEnabled(enabled: Boolean): Boolean {
        mutableEnabled.value = enabled
        return true
    }
}

private val AndroidCombatProcessingDiagnosticSink =
    CombatProcessingDiagnosticSink { taskId, occurrenceDate, failure ->
        Log.e(
            "TodoQuestCombat",
            "Pending player attack failed for task=$taskId occurrence=$occurrenceDate",
            failure,
        )
    }

private val AndroidReminderDiagnosticSink =
    ReminderDiagnosticSink { taskId, operation, failure ->
        Log.e(
            "TodoQuestReminder",
            "Reminder operation failed for task=$taskId operation=$operation",
            failure,
        )
    }

private object InactiveReminderScheduler : ReminderScheduler {
    override suspend fun capabilityStatus(
        capability: ReminderCapability,
    ): ReminderCapabilityStatus = ReminderCapabilityStatus.REQUIRED

    override suspend fun scheduleExact(
        plan: com.todoquest.domain.model.ReminderPlan,
    ) = com.todoquest.domain.model.ReminderScheduleStatus.ERROR

    override suspend fun cancel(
        key: com.todoquest.domain.model.ReminderOccurrenceKey,
    ) = com.todoquest.domain.model.ReminderScheduleStatus.DISABLED
}

private object InactiveFirstLaunchNotificationPromptStore :
    FirstLaunchNotificationPromptStore {
    override fun consumeFirstLaunchCheck(): Boolean = false
}

private object InactiveCharacterGuideRepository : CharacterGuideRepository {
    override fun statAllocationGuideStatus(): CharacterStatGuideStatus =
        CharacterStatGuideStatus(
            automaticDisplayEligible = false,
            acknowledged = false,
        )

    override fun acknowledgeStatAllocationGuide(): Boolean = true
}

private val InactiveReminderPublisher = ReminderPublisher { }
