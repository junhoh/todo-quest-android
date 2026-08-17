package com.todoquest.feature.calendar

import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.feature.battle.BattleMapUiState
import com.todoquest.feature.battle.BattlePresentationState
import com.todoquest.feature.battle.ActiveStatusEffectUiModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val tasks: List<TaskOccurrence> = emptyList(),
    val reminderUiStates: Map<CalendarOccurrenceKey, CalendarReminderUiState> = emptyMap(),
    val monthDaySummaries: Map<LocalDate, CalendarDaySummary> = emptyMap(),
    val characterSummary: CalendarCharacterSummary = CalendarCharacterSummary(),
    val battleMap: BattleMapUiState = BattleMapUiState.Loading,
    val battlePresentation: BattlePresentationState = BattlePresentationState(),
    val activeStatusEffects: List<ActiveStatusEffectUiModel> = emptyList(),
    val selectedStatusEffect: ActiveStatusEffectUiModel? = null,
    val processingOccurrenceKeys: Set<CalendarOccurrenceKey> = emptySet(),
    val taskEditor: TaskEditorUiState? = null,
    val deleteConfirmation: DeleteTaskConfirmationUiState? = null,
    val message: CalendarUiMessage? = null,
    val notificationPermissionPrompt: NotificationPermissionPromptUiState? = null,
    val isExactAlarmRationaleVisible: Boolean = false,
) {
    val isTaskEditorOpen: Boolean
        get() = taskEditor != null

    val isAddTaskDialogOpen: Boolean
        get() = taskEditor?.mode == TaskEditorMode.ADD

    val newTaskForm: NewTaskFormUiState
        get() = taskEditor ?: NewTaskFormUiState()

    val newTaskTitle: String
        get() = newTaskForm.title

    val isBattleInputLocked: Boolean
        get() = battlePresentation.isInputLocked
}

data class CalendarOccurrenceKey(
    val taskId: Long,
    val occurrenceDate: LocalDate,
)

sealed interface CalendarUiMessage {
    data object TaskNotFound : CalendarUiMessage
    data object LoadFailed : CalendarUiMessage
    data object InvalidTime : CalendarUiMessage
    data object TitleRequired : CalendarUiMessage
    data object SaveFailed : CalendarUiMessage
    data object DeleteFailed : CalendarUiMessage
    data object CompleteFailed : CalendarUiMessage
    data object UndoCompleteFailed : CalendarUiMessage
    data object FailFailed : CalendarUiMessage
    data object UndoFailFailed : CalendarUiMessage
    data object ReminderClearedAfterTimeRemoved : CalendarUiMessage
    data object ReminderCustomTimeRequired : CalendarUiMessage
    data object ReminderPostNotificationsRequired : CalendarUiMessage
    data object ReminderNotificationChannelDisabled : CalendarUiMessage
    data object ReminderNotificationPermissionDenied : CalendarUiMessage
    data object ReminderExactAlarmAccessRequired : CalendarUiMessage
    data object ReminderNoFutureOccurrence : CalendarUiMessage
    data object ReminderScheduleError : CalendarUiMessage
}

data class CalendarCharacterSummary(
    val isLoading: Boolean = true,
    val level: Int = 1,
    val xpIntoCurrentLevel: Long = 0,
    val xpRequiredForNextLevel: Long = 0,
    val gold: Long = 0,
)

data class NotificationPermissionPromptUiState(
    val origin: NotificationPermissionPromptOrigin,
)

enum class NotificationPermissionPromptOrigin {
    FIRST_LAUNCH,
    REMINDER,
}

sealed interface CalendarEvent {
    data class RewardGranted(
        val awardedXp: Long,
        val awardedGold: Long,
        val isOnTime: Boolean,
    ) : CalendarEvent

    data object RequestPostNotificationsPermission : CalendarEvent

    data class OpenNotificationSettings(
        val taskId: Long,
    ) : CalendarEvent

    data class OpenNotificationChannelSettings(
        val taskId: Long,
    ) : CalendarEvent

    data class OpenExactAlarmSettings(
        val taskId: Long,
    ) : CalendarEvent
}

data class CalendarReminderUiState(
    val mode: ReminderMode,
    val triggerTime: LocalTime,
    val dayRelation: ReminderDayRelation,
    val recoveryReason: ReminderCapabilityRecoveryReason? = null,
    val hasScheduleError: Boolean = false,
)

enum class ReminderDayRelation {
    SAME_DAY,
    PREVIOUS_DAY,
}

enum class ReminderCapabilityRecoveryReason {
    POST_NOTIFICATIONS,
    NOTIFICATION_CHANNEL,
    EXACT_ALARM,
}

enum class TaskEditorMode {
    ADD,
    EDIT,
}

data class TaskEditorUiState(
    val mode: TaskEditorMode = TaskEditorMode.ADD,
    val taskId: Long? = null,
    val effectiveDate: LocalDate? = null,
    val title: String = "",
    val memo: String = "",
    val time: LocalTime? = null,
    val difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
    val category: String = TaskCategory.DEFAULT,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val categoryPresets: List<String> = TaskCategory.PRESETS,
    val reminderSetting: ReminderEditorSettingUiState = ReminderEditorSettingUiState(),
    val reminderStatus: ReminderScheduleStatus = ReminderScheduleStatus.DISABLED,
    val isReminderCustomTimePickerOpen: Boolean = false,
) {
    val timeText: String
        get() = time?.format(TaskEditorTimeFormatter) ?: ""

    val arePresetRemindersEnabled: Boolean
        get() = time != null
}

data class ReminderEditorSettingUiState(
    val mode: ReminderMode = ReminderMode.NONE,
    val customTime: LocalTime? = null,
) {
    companion object {
        fun from(setting: ReminderSetting): ReminderEditorSettingUiState =
            ReminderEditorSettingUiState(
                mode = setting.mode,
                customTime = setting.customTime,
            )
    }
}

typealias NewTaskFormUiState = TaskEditorUiState

data class DeleteTaskConfirmationUiState(
    val taskId: Long,
    val occurrenceDate: LocalDate,
    val title: String,
)

data class CalendarDaySummary(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int = 0,
)

private val TaskEditorTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
