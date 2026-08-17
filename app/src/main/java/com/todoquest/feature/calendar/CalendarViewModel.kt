package com.todoquest.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.NoOpBattleSfxPlayer
import com.todoquest.core.AppClock
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CompletionRewardMode
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskMutationResult
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.ReminderPlanner
import com.todoquest.domain.usecase.UndoCompleteOccurrenceUseCase
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import com.todoquest.feature.battle.BattleAnimationController
import com.todoquest.feature.battle.BattleAnimationTimeline
import com.todoquest.feature.battle.BattleMapUiState
import com.todoquest.feature.battle.BattlePresentationState
import com.todoquest.feature.battle.BattlePresentationMapper
import com.todoquest.feature.battle.ActiveStatusEffectUiModel
import com.todoquest.feature.battle.toActiveStatusEffectUiModel
import com.todoquest.ui.character.CharacterRenderState
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: TaskRepository,
    characterRepository: CharacterRepository,
    private val combatRepository: CombatRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val completeOccurrence: CompleteOccurrenceUseCase,
    private val undoCompleteOccurrence: UndoCompleteOccurrenceUseCase,
    private val failOccurrence: FailOccurrenceUseCase,
    private val undoFailOccurrence: UndoFailOccurrenceUseCase,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val createTaskUseCase: suspend (CreateTaskInput) -> TaskMutationResult = { input ->
        TaskMutationResult(
            taskId = repository.createTask(input),
            reminderStatus = input.reminderSetting.initialScheduleStatus(),
        )
    },
    private val updateTaskUseCase: suspend (UpdateTaskInput) -> TaskMutationResult = { input ->
        TaskMutationResult(
            taskId = repository.updateTask(input),
            reminderStatus = input.reminderSetting.initialScheduleStatus(),
        )
    },
    private val deleteTaskUseCase: suspend (Long, LocalDate) -> Unit = { taskId, effectiveDate ->
        repository.deleteTask(taskId, effectiveDate)
    },
    private val loadReminderStatus: suspend (Long) -> ReminderScheduleStatus? = { null },
    private val reconcileTaskReminderUseCase: suspend (Long) -> ReminderScheduleStatus = {
        ReminderScheduleStatus.PENDING
    },
    private val prepareFirstLaunchNotificationPrompt: suspend () -> Boolean = { false },
    private val reminderPlanner: ReminderPlanner = ReminderPlanner(),
    battleSfxPlayer: BattleSfxPlayer = NoOpBattleSfxPlayer,
    battleAnimationTimeline: BattleAnimationTimeline = BattleAnimationTimeline(),
    private val characterId: Long = 1L,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(clock.today())
    private val taskEditor = MutableStateFlow<TaskEditorUiState?>(null)
    private val deleteConfirmation = MutableStateFlow<DeleteTaskConfirmationUiState?>(null)
    private val message = MutableStateFlow<CalendarUiMessage?>(null)
    private val notificationPermissionPrompt =
        MutableStateFlow<NotificationPermissionPromptUiState?>(null)
    private val isExactAlarmRationaleVisible = MutableStateFlow(false)
    private val processingOccurrenceKeys = MutableStateFlow<Set<CalendarOccurrenceKey>>(emptySet())
    private val statusReferenceTime = MutableStateFlow(clock.now())
    private val selectedStatusEffectKey = MutableStateFlow<SelectedStatusEffectKey?>(null)
    private val eventChannel = Channel<CalendarEvent>(capacity = Channel.BUFFERED)
    private val battleAnimationController = BattleAnimationController(
        scope = viewModelScope,
        timeline = battleAnimationTimeline,
        battleSfxPlayer = battleSfxPlayer,
    )
    private var legacyTimeInputIsInvalid = false
    private var lastSavedReminderTaskId: Long? = null
    private var pendingNotificationSettingsTaskId: Long? = null
    private var pendingExactAlarmSettingsTaskId: Long? = null

    val events: Flow<CalendarEvent> = eventChannel.receiveAsFlow()

    private val taskEditorState = combine(
        taskEditor,
        deleteConfirmation,
        message,
        notificationPermissionPrompt,
        isExactAlarmRationaleVisible,
    ) { editor, deleteConfirmation, message, permissionPrompt, exactAlarmRationaleVisible ->
        TaskEditorState(
            editor = editor,
            deleteConfirmation = deleteConfirmation,
            message = message,
            notificationPermissionPrompt = permissionPrompt,
            isExactAlarmRationaleVisible = exactAlarmRationaleVisible,
        )
    }

    private val monthOccurrences = selectedDate
        .map { YearMonth.from(it) }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            repository.observeOccurrences(
                rangeStart = month.atDay(1),
                rangeEnd = month.atEndOfMonth(),
            )
        }

    private val characterSnapshot = characterRepository.observeCharacter(clock.today())
        .map<CharacterSnapshot, CharacterSnapshot?> { it }
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val characterSummary = characterSnapshot.map { snapshot ->
        snapshot?.toCalendarCharacterSummary() ?: CalendarCharacterSummary()
    }

    private val combatState = combatRepository.observeCombat()
        .map<CombatSnapshot, CombatLoadState>(CombatLoadState::Loaded)
        .onStart { emit(CombatLoadState.Loading) }
        .catch { emit(CombatLoadState.Unavailable) }

    private val battleMap = combine(combatState, characterSnapshot) { combat, character ->
        when (combat) {
            CombatLoadState.Loading -> BattleMapUiState.Loading
            CombatLoadState.Unavailable -> BattleMapUiState.Unavailable
            is CombatLoadState.Loaded -> character?.let(combat.snapshot::toBattleMapUiState)
                ?: BattleMapUiState.Loading
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BattleMapUiState.Loading,
    )

    private val activeStatusEffects: Flow<List<ActiveStatusEffectUiModel>> = combine(
        statusEffectRepository.observeActiveStatusEffects(characterId)
            .catch { emit(emptyList()) },
        statusReferenceTime,
    ) { effects, now ->
        effects.filter { it.isEffectiveAt(now) }
            .map { it.toActiveStatusEffectUiModel(now) }
    }.catch { emit(emptyList()) }

    private val statusEffectPresentation = combine(
        activeStatusEffects,
        selectedStatusEffectKey,
    ) { effects, selectedKey ->
        StatusEffectPresentationState(
            active = effects,
            selected = effects.firstOrNull {
                selectedKey != null &&
                    it.type == selectedKey.type &&
                    it.revision == selectedKey.revision
            },
        )
    }

    private val presentationState = combine(
        taskEditorState,
        battleMap,
        battleAnimationController.presentation,
        processingOccurrenceKeys,
        statusEffectPresentation,
    ) { editorState, battleMap, battlePresentation, processingKeys, statusEffects ->
        CalendarPresentationState(
            editorState = editorState,
            battleMap = battleMap,
            battlePresentation = battlePresentation,
            processingKeys = processingKeys,
            activeStatusEffects = statusEffects.active,
            selectedStatusEffect = statusEffects.selected,
        )
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        selectedDate,
        monthOccurrences,
        characterSummary,
        presentationState,
    ) { selected, occurrences, characterSummary, presentation ->
        val selectedTasks = occurrences
            .filter { it.occurrenceDate == selected }
            .sortedWith(compareBy<TaskOccurrence> { it.time }.thenBy { it.title.lowercase() })
        val reminderUiStates = selectedTasks.mapNotNull { occurrence ->
            occurrence.toCalendarReminderUiState(reminderPlanner)?.let { reminder ->
                CalendarOccurrenceKey(
                    taskId = occurrence.taskId,
                    occurrenceDate = occurrence.occurrenceDate,
                ) to reminder
            }
        }.toMap()

        CalendarUiState(
            visibleMonth = YearMonth.from(selected),
            selectedDate = selected,
            tasks = selectedTasks,
            reminderUiStates = reminderUiStates,
            monthDaySummaries = occurrences.toDaySummaries(),
            characterSummary = characterSummary,
            battleMap = presentation.battleMap,
            battlePresentation = presentation.battlePresentation,
            processingOccurrenceKeys = presentation.processingKeys,
            activeStatusEffects = presentation.activeStatusEffects,
            selectedStatusEffect = presentation.selectedStatusEffect,
            taskEditor = presentation.editorState.editor,
            deleteConfirmation = presentation.editorState.deleteConfirmation,
            message = presentation.editorState.message,
            notificationPermissionPrompt =
                presentation.editorState.notificationPermissionPrompt,
            isExactAlarmRationaleVisible =
                presentation.editorState.isExactAlarmRationaleVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalendarUiState(
            visibleMonth = YearMonth.from(clock.today()),
            selectedDate = clock.today(),
            characterSummary = CalendarCharacterSummary(),
        ),
    )

    init {
        viewModelScope.launch(dispatcher) {
            try {
                if (prepareFirstLaunchNotificationPrompt()) {
                    notificationPermissionPrompt.value = NotificationPermissionPromptUiState(
                        origin = NotificationPermissionPromptOrigin.FIRST_LAUNCH,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Permission onboarding must not prevent Calendar from loading.
            }
        }
        viewModelScope.launch(dispatcher) {
            combatRepository.events
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                }
                .collect { transition ->
                    try {
                        val character = characterSnapshot.filterNotNull().first()
                        battleAnimationController.enqueue(
                            transition = transition,
                            characterRenderState = CharacterRenderState(
                                appearance = character.appearance,
                                equippedItems = character.equippedItems,
                            ),
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Transient presentation failure must not affect persisted task/combat state.
                    }
                }
        }
        viewModelScope.launch(dispatcher) {
            statusEffectRepository.observeRemovalEvents(characterId)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                }
                .collect { event ->
                    val scene = battleMap.value as? BattleMapUiState.Content ?: return@collect
                    try {
                        battleAnimationController.enqueueStatusEffectRemoval(event, scene)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Removal presentation is transient and must not affect persisted recovery.
                    }
                }
        }
    }

    fun onScreenEntered() {
        reconcileStatusEffects()
    }

    fun onLifecycleResumed() {
        reconcileStatusEffects()
    }

    fun showStatusEffectDetails(type: StatusEffectType) {
        val effect = uiState.value.activeStatusEffects.firstOrNull { it.type == type } ?: return
        selectedStatusEffectKey.value = SelectedStatusEffectKey(
            type = effect.type,
            revision = effect.revision,
        )
    }

    fun dismissStatusEffectDetails() {
        selectedStatusEffectKey.value = null
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun openReminderDestination(
        taskId: Long?,
        occurrenceDate: LocalDate?,
        onHandled: () -> Unit = {},
    ) {
        viewModelScope.launch(dispatcher) {
            val destinationDate = if (taskId != null && occurrenceDate != null) {
                try {
                    if (repository.getTask(taskId) != null) occurrenceDate else clock.today()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    clock.today()
                }
            } else {
                clock.today()
            }
            selectedDate.value = destinationDate
            onHandled()
        }
    }

    fun showPreviousMonth() {
        selectedDate.value = selectedDate.value.minusMonths(1)
    }

    fun showNextMonth() {
        selectedDate.value = selectedDate.value.plusMonths(1)
    }

    fun showAddTaskDialog() {
        message.value = null
        legacyTimeInputIsInvalid = false
        taskEditor.value = TaskEditorUiState(
            mode = TaskEditorMode.ADD,
            effectiveDate = selectedDate.value,
            reminderSetting = ReminderEditorSettingUiState(),
            reminderStatus = ReminderScheduleStatus.DISABLED,
        )
    }

    fun showEditTaskDialog(taskId: Long, occurrenceDate: LocalDate) {
        viewModelScope.launch(dispatcher) {
            runCatching {
                repository.getTask(taskId)
            }.onSuccess { task ->
                if (task == null) {
                    message.value = CalendarUiMessage.TaskNotFound
                    return@onSuccess
                }

                legacyTimeInputIsInvalid = false
                message.value = null
                val reminderStatus = loadReminderStatus(taskId)
                    ?: task.reminderSetting.initialScheduleStatus()
                taskEditor.value = TaskEditorUiState(
                    mode = TaskEditorMode.EDIT,
                    taskId = taskId,
                    effectiveDate = occurrenceDate,
                    title = task.title,
                    memo = task.memo,
                    time = task.time,
                    difficulty = task.difficulty,
                    category = TaskCategory.normalize(task.category),
                    recurrenceRule = task.recurrenceRule,
                    reminderSetting = ReminderEditorSettingUiState.from(task.reminderSetting),
                    reminderStatus = reminderStatus,
                )
            }.onFailure {
                message.value = CalendarUiMessage.LoadFailed
            }
        }
    }

    fun hideAddTaskDialog() {
        hideTaskEditor()
    }

    fun hideTaskEditor() {
        taskEditor.value = null
        legacyTimeInputIsInvalid = false
        message.value = null
    }

    fun updateNewTaskTitle(title: String) {
        updateTaskTitle(title)
    }

    fun updateTaskTitle(title: String) {
        updateTaskEditor { copy(title = title) }
    }

    fun updateNewTaskMemo(memo: String) {
        updateTaskMemo(memo)
    }

    fun updateTaskMemo(memo: String) {
        updateTaskEditor { copy(memo = memo) }
    }

    fun updateNewTaskTime(timeText: String) {
        val trimmed = timeText.trim()
        if (trimmed.isBlank()) {
            updateTaskTime(null)
            return
        }

        val parsed = parseNewTaskTime(trimmed)
        if (parsed != null) {
            updateTaskTime(parsed)
        }
    }

    fun updateTaskTime(time: LocalTime?) {
        legacyTimeInputIsInvalid = false
        val editor = taskEditor.value ?: return
        val clearsPreset = time == null &&
            editor.reminderSetting.mode in PresetReminderModes
        taskEditor.value = editor.copy(
            time = time,
            reminderSetting = if (clearsPreset) {
                ReminderEditorSettingUiState()
            } else {
                editor.reminderSetting
            },
        )
        if (clearsPreset) {
            message.value = CalendarUiMessage.ReminderClearedAfterTimeRemoved
        }
    }

    fun updateNewTaskDifficulty(difficulty: TaskDifficulty) {
        updateTaskDifficulty(difficulty)
    }

    fun updateTaskDifficulty(difficulty: TaskDifficulty) {
        updateTaskEditor { copy(difficulty = difficulty) }
    }

    fun updateNewTaskCategory(category: String) {
        updateTaskCategory(category)
    }

    fun updateTaskCategory(category: String) {
        updateTaskEditor { copy(category = category) }
    }

    fun updateNewTaskRecurrenceRule(recurrenceRule: RecurrenceRule) {
        updateTaskRecurrenceRule(recurrenceRule)
    }

    fun updateTaskRecurrenceRule(recurrenceRule: RecurrenceRule) {
        updateTaskEditor { copy(recurrenceRule = recurrenceRule) }
    }

    fun updateTaskReminderMode(mode: ReminderMode) {
        val editor = taskEditor.value ?: return
        if (mode in PresetReminderModes && !editor.arePresetRemindersEnabled) return

        taskEditor.value = editor.copy(
            reminderSetting = when (mode) {
                ReminderMode.NONE -> ReminderEditorSettingUiState()
                ReminderMode.TEN_MINUTES_BEFORE,
                ReminderMode.ONE_HOUR_BEFORE,
                -> ReminderEditorSettingUiState(mode = mode)
                ReminderMode.CUSTOM_TIME -> ReminderEditorSettingUiState(
                    mode = mode,
                    customTime = editor.reminderSetting.customTime,
                )
            },
            isReminderCustomTimePickerOpen = mode == ReminderMode.CUSTOM_TIME,
        )
        message.value = null
    }

    fun showTaskReminderCustomTimePicker() {
        val editor = taskEditor.value ?: return
        if (editor.reminderSetting.mode != ReminderMode.CUSTOM_TIME) return
        taskEditor.value = editor.copy(isReminderCustomTimePickerOpen = true)
    }

    fun dismissTaskReminderCustomTimePicker() {
        updateTaskEditor { copy(isReminderCustomTimePickerOpen = false) }
    }

    fun updateTaskReminderCustomTime(time: LocalTime?) {
        val editor = taskEditor.value ?: return
        taskEditor.value = editor.copy(
            reminderSetting = ReminderEditorSettingUiState(
                mode = ReminderMode.CUSTOM_TIME,
                customTime = time,
            ),
            isReminderCustomTimePickerOpen = false,
        )
        message.value = null
    }

    fun saveNewTask() {
        saveTaskEditor()
    }

    fun saveTaskEditor() {
        val editor = taskEditor.value ?: return
        if (legacyTimeInputIsInvalid) {
            message.value = CalendarUiMessage.InvalidTime
            return
        }

        val title = editor.title.trim()
        if (title.isBlank()) {
            message.value = CalendarUiMessage.TitleRequired
            return
        }
        val reminderSetting = editor.reminderSetting.toDomainOrNull()
        if (reminderSetting == null) {
            message.value = CalendarUiMessage.ReminderCustomTimeRequired
            return
        }

        val memo = editor.memo.trim()
        val category = TaskCategory.normalize(editor.category)
        viewModelScope.launch(dispatcher) {
            runCatching {
                when (editor.mode) {
                    TaskEditorMode.ADD -> {
                        createTaskUseCase(
                            CreateTaskInput(
                                title = title,
                                memo = memo,
                                startDate = editor.effectiveDate ?: selectedDate.value,
                                time = editor.time,
                                difficulty = editor.difficulty,
                                category = category,
                                recurrenceRule = editor.recurrenceRule,
                                reminderSetting = reminderSetting,
                            ),
                        )
                    }

                    TaskEditorMode.EDIT -> {
                        updateTaskUseCase(
                            UpdateTaskInput(
                                taskId = editor.taskId ?: error("Missing task id"),
                                effectiveDate = editor.effectiveDate ?: error("Missing effective date"),
                                title = title,
                                memo = memo,
                                time = editor.time,
                                difficulty = editor.difficulty,
                                category = category,
                                recurrenceRule = editor.recurrenceRule,
                                reminderSetting = reminderSetting,
                            ),
                        )
                    }
                }
            }.onSuccess { result ->
                taskEditor.value = null
                legacyTimeInputIsInvalid = false
                lastSavedReminderTaskId = if (reminderSetting.mode == ReminderMode.NONE) {
                    null
                } else {
                    result.taskId
                }
                handleReminderStatus(
                    status = result.reminderStatus,
                    taskId = result.taskId,
                    showPostNotificationsPrompt = true,
                )
            }.onFailure {
                message.value = CalendarUiMessage.SaveFailed
            }
        }
    }

    fun requestDeleteTask(taskId: Long, occurrenceDate: LocalDate, title: String) {
        deleteConfirmation.value = DeleteTaskConfirmationUiState(
            taskId = taskId,
            occurrenceDate = occurrenceDate,
            title = title,
        )
        message.value = null
    }

    fun dismissDeleteTask() {
        deleteConfirmation.value = null
    }

    fun confirmDeleteTask() {
        val delete = deleteConfirmation.value ?: return

        viewModelScope.launch(dispatcher) {
            runCatching {
                deleteTaskUseCase(delete.taskId, delete.occurrenceDate)
            }.onSuccess {
                deleteConfirmation.value = null
                message.value = null
            }.onFailure {
                message.value = CalendarUiMessage.DeleteFailed
            }
        }
    }

    private fun updateTaskEditor(transform: TaskEditorUiState.() -> TaskEditorUiState) {
        taskEditor.value = taskEditor.value?.transform()
    }

    private fun parseNewTaskTime(timeText: String): LocalTime? {
        if (!NewTaskTimeRegex.matches(timeText)) {
            legacyTimeInputIsInvalid = true
            message.value = CalendarUiMessage.InvalidTime
            return null
        }
        return runCatching {
            LocalTime.parse(timeText, NewTaskTimeFormatter)
        }.getOrElse {
            legacyTimeInputIsInvalid = true
            message.value = CalendarUiMessage.InvalidTime
            null
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        notificationPermissionPrompt.value = null
        if (!granted) {
            isExactAlarmRationaleVisible.value = false
            message.value = CalendarUiMessage.ReminderNotificationPermissionDenied
            return
        }
        if (!reconcileLastSavedReminder()) {
            isExactAlarmRationaleVisible.value = false
            message.value = null
        }
    }

    fun confirmNotificationPermissionPrompt() {
        val origin = notificationPermissionPrompt.value?.origin ?: return
        notificationPermissionPrompt.value = null
        viewModelScope.launch(dispatcher) {
            when (origin) {
                NotificationPermissionPromptOrigin.FIRST_LAUNCH ->
                    eventChannel.send(CalendarEvent.RequestPostNotificationsPermission)
                NotificationPermissionPromptOrigin.REMINDER -> {
                    val taskId = lastSavedReminderTaskId ?: return@launch
                    pendingNotificationSettingsTaskId = taskId
                    eventChannel.send(CalendarEvent.OpenNotificationSettings(taskId))
                }
            }
        }
    }

    fun dismissNotificationPermissionPrompt() {
        notificationPermissionPrompt.value = null
    }

    fun onNotificationSettingsReturned() {
        notificationPermissionPrompt.value = null
        val taskId = pendingNotificationSettingsTaskId
        pendingNotificationSettingsTaskId = null
        if (taskId == null || !reconcileReminder(taskId)) {
            isExactAlarmRationaleVisible.value = false
            message.value = null
        }
    }

    fun requestReminderRecovery(taskId: Long, occurrenceDate: LocalDate) {
        val reminder = uiState.value.reminderUiStates[
            CalendarOccurrenceKey(taskId, occurrenceDate)
        ] ?: return
        when (reminder.recoveryReason) {
            ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingNotificationSettingsTaskId = taskId
                viewModelScope.launch(dispatcher) {
                    eventChannel.send(CalendarEvent.OpenNotificationSettings(taskId))
                }
            }

            ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingNotificationSettingsTaskId = taskId
                viewModelScope.launch(dispatcher) {
                    eventChannel.send(CalendarEvent.OpenNotificationChannelSettings(taskId))
                }
            }

            ReminderCapabilityRecoveryReason.EXACT_ALARM -> {
                notificationPermissionPrompt.value = null
                pendingExactAlarmSettingsTaskId = taskId
                isExactAlarmRationaleVisible.value = true
                message.value = CalendarUiMessage.ReminderExactAlarmAccessRequired
            }

            null -> Unit
        }
    }

    fun requestExactAlarmSettings() {
        if (!isExactAlarmRationaleVisible.value) return
        val taskId = pendingExactAlarmSettingsTaskId ?: return
        isExactAlarmRationaleVisible.value = false
        viewModelScope.launch(dispatcher) {
            eventChannel.send(CalendarEvent.OpenExactAlarmSettings(taskId))
        }
    }

    fun dismissExactAlarmRationale() {
        isExactAlarmRationaleVisible.value = false
    }

    fun onExactAlarmSettingsReturned() {
        val taskId = pendingExactAlarmSettingsTaskId ?: return
        pendingExactAlarmSettingsTaskId = null
        reconcileReminder(taskId)
    }

    private fun reconcileLastSavedReminder(): Boolean {
        val taskId = lastSavedReminderTaskId ?: return false
        return reconcileReminder(taskId)
    }

    private fun reconcileReminder(taskId: Long): Boolean {
        viewModelScope.launch(dispatcher) {
            try {
                handleReminderStatus(
                    status = reconcileTaskReminderUseCase(taskId),
                    taskId = taskId,
                    showPostNotificationsPrompt = false,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                handleReminderStatus(
                    status = ReminderScheduleStatus.ERROR,
                    taskId = taskId,
                    showPostNotificationsPrompt = false,
                )
            }
        }
        return true
    }

    private suspend fun handleReminderStatus(
        status: ReminderScheduleStatus,
        taskId: Long,
        showPostNotificationsPrompt: Boolean,
    ) {
        when (status) {
            ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED -> {
                isExactAlarmRationaleVisible.value = false
                pendingExactAlarmSettingsTaskId = null
                message.value = CalendarUiMessage.ReminderPostNotificationsRequired
                notificationPermissionPrompt.value = if (showPostNotificationsPrompt) {
                    NotificationPermissionPromptUiState(
                        origin = NotificationPermissionPromptOrigin.REMINDER,
                    )
                } else {
                    null
                }
            }

            ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingExactAlarmSettingsTaskId = null
                message.value = CalendarUiMessage.ReminderNotificationChannelDisabled
            }

            ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED -> {
                notificationPermissionPrompt.value = null
                pendingExactAlarmSettingsTaskId = taskId
                isExactAlarmRationaleVisible.value = true
                message.value = CalendarUiMessage.ReminderExactAlarmAccessRequired
            }

            ReminderScheduleStatus.NO_FUTURE_OCCURRENCE -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingExactAlarmSettingsTaskId = null
                message.value = CalendarUiMessage.ReminderNoFutureOccurrence
            }

            ReminderScheduleStatus.ERROR,
            -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingExactAlarmSettingsTaskId = null
                message.value = CalendarUiMessage.ReminderScheduleError
            }

            ReminderScheduleStatus.DISABLED,
            ReminderScheduleStatus.PENDING,
            ReminderScheduleStatus.SCHEDULED,
            ReminderScheduleStatus.DELIVERED,
            -> {
                notificationPermissionPrompt.value = null
                isExactAlarmRationaleVisible.value = false
                pendingExactAlarmSettingsTaskId = null
                message.value = null
            }
        }
    }

    fun completeOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        launchOccurrenceCommand(
            taskId = taskId,
            occurrenceDate = occurrenceDate,
            failureMessage = CalendarUiMessage.CompleteFailed,
        ) {
            val result = completeOccurrence.invoke(taskId, occurrenceDate)
            if (
                !result.alreadyRewarded &&
                result.rewardMode == CompletionRewardMode.TODO_COMPLETION
            ) {
                eventChannel.send(
                    CalendarEvent.RewardGranted(
                        awardedXp = result.awardedXp,
                        awardedGold = result.awardedGold,
                        isOnTime = result.isOnTime,
                    ),
                )
            }
        }
    }

    fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        launchOccurrenceCommand(
            taskId = taskId,
            occurrenceDate = occurrenceDate,
            failureMessage = CalendarUiMessage.UndoCompleteFailed,
        ) {
            undoCompleteOccurrence.invoke(taskId, occurrenceDate)
        }
    }

    fun failOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        launchOccurrenceCommand(
            taskId = taskId,
            occurrenceDate = occurrenceDate,
            failureMessage = CalendarUiMessage.FailFailed,
        ) {
            failOccurrence.invoke(taskId, occurrenceDate)
        }
    }

    fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
        launchOccurrenceCommand(
            taskId = taskId,
            occurrenceDate = occurrenceDate,
            failureMessage = CalendarUiMessage.UndoFailFailed,
        ) {
            undoFailOccurrence.invoke(taskId, occurrenceDate)
        }
    }

    private fun launchOccurrenceCommand(
        taskId: Long,
        occurrenceDate: LocalDate,
        failureMessage: CalendarUiMessage,
        command: suspend () -> Unit,
    ) {
        val key = CalendarOccurrenceKey(taskId, occurrenceDate)
        if (battleAnimationController.presentation.value.isInputLocked) return
        if (key in processingOccurrenceKeys.value) return
        processingOccurrenceKeys.update { it + key }
        message.value = null

        viewModelScope.launch(dispatcher) {
            try {
                command()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                message.value = failureMessage
            } finally {
                processingOccurrenceKeys.update { it - key }
            }
        }
    }

    private fun reconcileStatusEffects() {
        statusReferenceTime.value = clock.now()
        viewModelScope.launch(dispatcher) {
            try {
                statusEffectRepository.reconcileExpired(characterId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Expiration recovery is retried on the next entry/resume/reconciliation boundary.
            }
        }
    }

    private fun List<TaskOccurrence>.toDaySummaries(): Map<LocalDate, CalendarDaySummary> =
        groupBy { it.occurrenceDate }
            .mapValues { (_, tasks) ->
                CalendarDaySummary(
                    totalCount = tasks.size,
                    completedCount = tasks.count { it.isCompleted },
                    failedCount = tasks.count { it.isFailed },
                )
            }
}

private data class TaskEditorState(
    val editor: TaskEditorUiState?,
    val deleteConfirmation: DeleteTaskConfirmationUiState?,
    val message: CalendarUiMessage?,
    val notificationPermissionPrompt: NotificationPermissionPromptUiState?,
    val isExactAlarmRationaleVisible: Boolean,
)

private data class CalendarPresentationState(
    val editorState: TaskEditorState,
    val battleMap: BattleMapUiState,
    val battlePresentation: BattlePresentationState,
    val processingKeys: Set<CalendarOccurrenceKey>,
    val activeStatusEffects: List<ActiveStatusEffectUiModel>,
    val selectedStatusEffect: ActiveStatusEffectUiModel?,
)

private data class StatusEffectPresentationState(
    val active: List<ActiveStatusEffectUiModel>,
    val selected: ActiveStatusEffectUiModel?,
)

private data class SelectedStatusEffectKey(
    val type: StatusEffectType,
    val revision: Long,
)

private sealed interface CombatLoadState {
    data object Loading : CombatLoadState

    data class Loaded(val snapshot: CombatSnapshot) : CombatLoadState

    data object Unavailable : CombatLoadState
}

private fun CharacterSnapshot.toCalendarCharacterSummary() = CalendarCharacterSummary(
    isLoading = false,
    level = level,
    xpIntoCurrentLevel = xpIntoCurrentLevel,
    xpRequiredForNextLevel = xpRequiredForNextLevel,
    gold = character.currentGold,
)

private fun CombatSnapshot.toBattleMapUiState(
    character: CharacterSnapshot,
): BattleMapUiState.Content = BattlePresentationMapper.mapSnapshot(
    snapshot = this,
    characterRenderState = CharacterRenderState(
        appearance = character.appearance,
        equippedItems = character.equippedItems,
    ),
)

private val NewTaskTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val NewTaskTimeRegex = Regex("""\d{2}:\d{2}""")
private val PresetReminderModes = setOf(
    ReminderMode.TEN_MINUTES_BEFORE,
    ReminderMode.ONE_HOUR_BEFORE,
)

private fun ReminderEditorSettingUiState.toDomainOrNull(): ReminderSetting? = when (mode) {
    ReminderMode.NONE -> ReminderSetting()
    ReminderMode.TEN_MINUTES_BEFORE,
    ReminderMode.ONE_HOUR_BEFORE,
    -> ReminderSetting(mode)
    ReminderMode.CUSTOM_TIME -> customTime?.let { ReminderSetting(mode, it) }
}

private fun ReminderSetting.initialScheduleStatus(): ReminderScheduleStatus =
    if (mode == ReminderMode.NONE) {
        ReminderScheduleStatus.DISABLED
    } else {
        ReminderScheduleStatus.PENDING
    }

private fun TaskOccurrence.toCalendarReminderUiState(
    reminderPlanner: ReminderPlanner,
): CalendarReminderUiState? {
    val trigger = reminderPlanner.triggerLocalDateTime(
        occurrenceDate = occurrenceDate,
        taskTime = time,
        setting = reminderSetting,
    ) ?: return null
    val dayRelation = when (trigger.toLocalDate()) {
        occurrenceDate -> ReminderDayRelation.SAME_DAY
        occurrenceDate.minusDays(1) -> ReminderDayRelation.PREVIOUS_DAY
        else -> return null
    }
    return CalendarReminderUiState(
        mode = reminderSetting.mode,
        triggerTime = trigger.toLocalTime(),
        dayRelation = dayRelation,
        recoveryReason = when (reminderScheduleStatus) {
            ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED ->
                ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS
            ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED ->
                ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL
            ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED ->
                ReminderCapabilityRecoveryReason.EXACT_ALARM
            ReminderScheduleStatus.DISABLED,
            ReminderScheduleStatus.PENDING,
            ReminderScheduleStatus.SCHEDULED,
            ReminderScheduleStatus.DELIVERED,
            ReminderScheduleStatus.NO_FUTURE_OCCURRENCE,
            ReminderScheduleStatus.ERROR,
            -> null
        },
        hasScheduleError = reminderScheduleStatus == ReminderScheduleStatus.ERROR,
    )
}
