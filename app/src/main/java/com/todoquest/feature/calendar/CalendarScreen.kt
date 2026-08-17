package com.todoquest.feature.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todoquest.R
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.feature.battle.BattleMap
import com.todoquest.feature.battle.BattleMapHeightPolicy
import com.todoquest.feature.battle.PlayerProgressHud
import com.todoquest.feature.battle.SevereInjuryDetailsDialog
import com.todoquest.notification.AndroidReminderCapabilityAdapter
import com.todoquest.notification.NotificationPermissionLaunchAction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
    reminderCapabilityAdapter: AndroidReminderCapabilityAdapter? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val contextReminderCapabilityAdapter = remember(context) {
        AndroidReminderCapabilityAdapter(context)
    }
    val activeReminderCapabilityAdapter =
        reminderCapabilityAdapter ?: contextReminderCapabilityAdapter
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult,
    )
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onNotificationSettingsReturned()
    }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onExactAlarmSettingsReturned()
    }

    LaunchedEffect(viewModel) {
        viewModel.onScreenEntered()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onLifecycleResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        viewModel,
        snackbarHostState,
        resources,
        activeReminderCapabilityAdapter,
        notificationPermissionLauncher,
        notificationSettingsLauncher,
        exactAlarmSettingsLauncher,
    ) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.RewardGranted -> {
                    val messageResource = if (event.isOnTime) {
                        R.string.calendar_reward_snackbar_on_time
                    } else {
                        R.string.calendar_reward_snackbar_not_on_time
                    }
                    withTimeoutOrNull(RewardSnackbarDurationMillis) {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(
                                messageResource,
                                event.awardedXp,
                                event.awardedGold,
                            ),
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                }

                CalendarEvent.RequestPostNotificationsPermission -> {
                    when (
                        val action = activeReminderCapabilityAdapter
                            .firstLaunchNotificationPermissionAction()
                    ) {
                        is NotificationPermissionLaunchAction.RuntimePermission ->
                            notificationPermissionLauncher.launch(action.permission)
                        is NotificationPermissionLaunchAction.AppSettings ->
                            notificationSettingsLauncher.launch(action.intent)
                        NotificationPermissionLaunchAction.None ->
                            viewModel.onNotificationPermissionResult(granted = true)
                    }
                }

                is CalendarEvent.OpenNotificationSettings -> {
                    notificationSettingsLauncher.launch(
                        activeReminderCapabilityAdapter.notificationSettingsIntent(),
                    )
                }

                is CalendarEvent.OpenNotificationChannelSettings -> {
                    notificationSettingsLauncher.launch(
                        activeReminderCapabilityAdapter.notificationChannelSettingsIntent(),
                    )
                }

                is CalendarEvent.OpenExactAlarmSettings -> {
                    val intent = activeReminderCapabilityAdapter.exactAlarmAccessIntent()
                    if (intent == null) {
                        viewModel.onExactAlarmSettingsReturned()
                    } else {
                        exactAlarmSettingsLauncher.launch(intent)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CalendarContent(
            state = state,
            onSelectDate = viewModel::selectDate,
            onShowPreviousMonth = viewModel::showPreviousMonth,
            onShowNextMonth = viewModel::showNextMonth,
            onShowAddTask = viewModel::showAddTaskDialog,
            onCompleteOccurrence = viewModel::completeOccurrence,
            onUndoCompleteOccurrence = viewModel::undoCompleteOccurrence,
            onFailOccurrence = viewModel::failOccurrence,
            onUndoFailOccurrence = viewModel::undoFailOccurrence,
            onEditTask = viewModel::showEditTaskDialog,
            onRequestDeleteTask = viewModel::requestDeleteTask,
            onRecoverReminder = viewModel::requestReminderRecovery,
            onShowStatusEffectDetails = viewModel::showStatusEffectDetails,
            modifier = Modifier
                .fillMaxSize()
                .dismissRewardSnackbarOnPointerDown(snackbarHostState),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .testTag("calendar-snackbar-host"),
        )
    }

    state.taskEditor?.let { editor ->
        TaskEditorDialog(
            form = editor,
            message = state.message,
            onTitleChange = viewModel::updateTaskTitle,
            onMemoChange = viewModel::updateTaskMemo,
            onTimeChange = viewModel::updateTaskTime,
            onDifficultyChange = viewModel::updateTaskDifficulty,
            onCategoryChange = viewModel::updateTaskCategory,
            onRecurrenceRuleChange = viewModel::updateTaskRecurrenceRule,
            onReminderModeChange = viewModel::updateTaskReminderMode,
            onShowReminderCustomTimePicker = viewModel::showTaskReminderCustomTimePicker,
            onReminderCustomTimeChange = viewModel::updateTaskReminderCustomTime,
            onDismissReminderCustomTimePicker =
                viewModel::dismissTaskReminderCustomTimePicker,
            onSave = viewModel::saveTaskEditor,
            onDismiss = viewModel::hideTaskEditor,
        )
    }

    state.deleteConfirmation?.let { confirmation ->
        DeleteTaskDialog(
            confirmation = confirmation,
            onConfirm = viewModel::confirmDeleteTask,
            onDismiss = viewModel::dismissDeleteTask,
        )
    }

    state.notificationPermissionPrompt?.let { prompt ->
        NotificationPermissionPromptDialog(
            prompt = prompt,
            onConfirm = viewModel::confirmNotificationPermissionPrompt,
            onDismiss = viewModel::dismissNotificationPermissionPrompt,
        )
    }

    if (state.isExactAlarmRationaleVisible) {
        ExactAlarmRationaleDialog(
            onOpenSettings = viewModel::requestExactAlarmSettings,
            onDismiss = viewModel::dismissExactAlarmRationale,
        )
    }


    state.selectedStatusEffect?.let { effect ->
        SevereInjuryDetailsDialog(
            effect = effect,
            onDismiss = viewModel::dismissStatusEffectDetails,
        )
    }
}

@Composable
internal fun NotificationPermissionPromptDialog(
    prompt: NotificationPermissionPromptUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (prompt.origin) {
        NotificationPermissionPromptOrigin.FIRST_LAUNCH ->
            stringResource(R.string.calendar_notification_first_launch_title)
        NotificationPermissionPromptOrigin.REMINDER ->
            stringResource(R.string.calendar_notification_reminder_title)
    }
    val message = when (prompt.origin) {
        NotificationPermissionPromptOrigin.FIRST_LAUNCH ->
            stringResource(R.string.calendar_notification_first_launch_message)
        NotificationPermissionPromptOrigin.REMINDER ->
            stringResource(R.string.calendar_notification_reminder_message)
    }
    val confirmText = when (prompt.origin) {
        NotificationPermissionPromptOrigin.FIRST_LAUNCH ->
            stringResource(R.string.calendar_notification_allow_action)
        NotificationPermissionPromptOrigin.REMINDER ->
            stringResource(R.string.calendar_notification_settings_action)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notification-permission-prompt-dialog"),
        title = { Text(text = title) },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("notification-permission-confirm"),
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("notification-permission-dismiss"),
            ) {
                Text(text = stringResource(R.string.calendar_notification_later_action))
            }
        },
    )
}

private fun Modifier.dismissRewardSnackbarOnPointerDown(
    snackbarHostState: SnackbarHostState,
): Modifier = pointerInput(snackbarHostState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { change -> change.pressed && !change.previousPressed }) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }
}

private const val RewardSnackbarDurationMillis = 600L

@Composable
internal fun CalendarContent(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onShowPreviousMonth: () -> Unit,
    onShowNextMonth: () -> Unit,
    onShowAddTask: () -> Unit,
    onCompleteOccurrence: (Long, LocalDate) -> Unit,
    onUndoCompleteOccurrence: (Long, LocalDate) -> Unit,
    onFailOccurrence: (Long, LocalDate) -> Unit,
    onUndoFailOccurrence: (Long, LocalDate) -> Unit,
    onEditTask: (Long, LocalDate) -> Unit,
    onRequestDeleteTask: (Long, LocalDate, String) -> Unit,
    onRecoverReminder: (Long, LocalDate) -> Unit = { _, _ -> },
    onShowStatusEffectDetails: (com.todoquest.domain.model.StatusEffectType) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val mapHeightPolicy = if (maxHeight < CompactViewportHeightThreshold) {
                BattleMapHeightPolicy.COMPACT
            } else {
                BattleMapHeightPolicy.STANDARD
            }
            Column(modifier = Modifier.fillMaxSize()) {
                BattleMap(
                    state = state.battleMap,
                    presentation = state.battlePresentation,
                    activeStatusEffects = state.activeStatusEffects,
                    onStatusEffectClick = onShowStatusEffectDetails,
                    heightPolicy = mapHeightPolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                    overlayContent = {
                        PlayerProgressHud(
                            isLoading = state.characterSummary.isLoading,
                            level = state.characterSummary.level,
                            currentExp = state.characterSummary.xpIntoCurrentLevel,
                            requiredExp = state.characterSummary.xpRequiredForNextLevel,
                            gold = state.characterSummary.gold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .fillMaxWidth(),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("task-lazy-list"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "calendar-month-grid") {
                        MonthGrid(
                            visibleMonth = state.visibleMonth,
                            selectedDate = state.selectedDate,
                            summaries = state.monthDaySummaries,
                            onSelectDate = onSelectDate,
                            onShowPreviousMonth = onShowPreviousMonth,
                            onShowNextMonth = onShowNextMonth,
                        )
                    }
                    item(key = "calendar-task-header") {
                        TaskListHeader(
                            state = state,
                            onShowAddTask = onShowAddTask,
                        )
                    }
                    state.message?.takeIf { state.taskEditor == null }?.let { message ->
                        item(key = "calendar-message") {
                            Text(
                                text = message.displayText(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (state.tasks.isEmpty()) {
                        item(key = "calendar-empty-tasks") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.calendar_empty_tasks),
                                    modifier = Modifier.padding(18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.tasks,
                            key = { "${it.taskId}-${it.occurrenceDate}" },
                        ) { task ->
                            TaskRow(
                                task = task,
                                reminder = state.reminderUiStates[
                                    CalendarOccurrenceKey(
                                        taskId = task.taskId,
                                        occurrenceDate = task.occurrenceDate,
                                    )
                                ],
                                isProcessing = CalendarOccurrenceKey(
                                    taskId = task.taskId,
                                    occurrenceDate = task.occurrenceDate,
                                ) in state.processingOccurrenceKeys,
                                isBattleInputLocked = state.isBattleInputLocked,
                                onCompleteOccurrence = onCompleteOccurrence,
                                onUndoCompleteOccurrence = onUndoCompleteOccurrence,
                                onFailOccurrence = onFailOccurrence,
                                onUndoFailOccurrence = onUndoFailOccurrence,
                                onEditTask = onEditTask,
                                onRequestDeleteTask = onRequestDeleteTask,
                                onRecoverReminder = onRecoverReminder,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    summaries: Map<LocalDate, CalendarDaySummary>,
    onSelectDate: (LocalDate) -> Unit,
    onShowPreviousMonth: () -> Unit,
    onShowNextMonth: () -> Unit,
) {
    val cells = remember(visibleMonth) { visibleMonth.calendarCells() }
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar-month-grid"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val previousMonthDescription = stringResource(R.string.calendar_previous_month)
        val nextMonthDescription = stringResource(R.string.calendar_next_month)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onShowPreviousMonth,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("calendar-previous-month"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = previousMonthDescription,
                )
            }
            Text(
                text = stringResource(
                    R.string.calendar_month_title,
                    visibleMonth.year,
                    visibleMonth.monthValue,
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = onShowNextMonth,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("calendar-next-month"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = nextMonthDescription,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "sunday" to stringResource(R.string.calendar_weekday_sunday),
                "monday" to stringResource(R.string.calendar_weekday_monday),
                "tuesday" to stringResource(R.string.calendar_weekday_tuesday),
                "wednesday" to stringResource(R.string.calendar_weekday_wednesday),
                "thursday" to stringResource(R.string.calendar_weekday_thursday),
                "friday" to stringResource(R.string.calendar_weekday_friday),
                "saturday" to stringResource(R.string.calendar_weekday_saturday),
            ).forEach { (weekday, label) ->
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calendar-weekday-$weekday"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        summary = date?.let(summaries::get),
                        onSelectDate = onSelectDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    summary: CalendarDaySummary?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val dayDescription = date?.contentDescription(summary)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(BorderStroke(1.dp, borderColor), MaterialTheme.shapes.extraSmall)
            .background(backgroundColor)
            .then(
                if (dayDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = dayDescription }
                },
            )
            .then(
                if (date == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable { onSelectDate(date) }
                        .testTag("calendar-day-$date")
                },
            )
            .padding(6.dp),
    ) {
        if (date != null) {
            Text(
                text = date.dayOfMonth.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            summary?.indicatorText()?.let { indicatorText ->
                Text(
                    text = indicatorText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskListHeader(
    state: CalendarUiState,
    onShowAddTask: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-list"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calendar-task-header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.selectedDate.displayText(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.calendar_completion_summary,
                        state.tasks.count { it.isCompleted },
                        state.tasks.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val failedCount = state.tasks.count { it.isFailed }
                if (failedCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.calendar_failure_summary,
                            failedCount,
                        ),
                        modifier = Modifier.testTag("calendar-failure-summary"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = onShowAddTask,
                modifier = Modifier.testTag("add-task-button"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.calendar_add))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskOccurrence,
    reminder: CalendarReminderUiState?,
    isProcessing: Boolean,
    isBattleInputLocked: Boolean,
    onCompleteOccurrence: (Long, LocalDate) -> Unit,
    onUndoCompleteOccurrence: (Long, LocalDate) -> Unit,
    onFailOccurrence: (Long, LocalDate) -> Unit,
    onUndoFailOccurrence: (Long, LocalDate) -> Unit,
    onEditTask: (Long, LocalDate) -> Unit,
    onRequestDeleteTask: (Long, LocalDate, String) -> Unit,
    onRecoverReminder: (Long, LocalDate) -> Unit,
) {
    val editDescription = stringResource(R.string.calendar_edit_task_description, task.title)
    val deleteDescription = stringResource(R.string.calendar_delete_task_description, task.title)
    val completeDescription = stringResource(R.string.calendar_complete_task_description, task.title)
    val undoDescription = stringResource(R.string.calendar_undo_task_description, task.title)
    val failDescription = stringResource(R.string.calendar_fail_task_description, task.title)
    val undoFailDescription = stringResource(R.string.calendar_undo_fail_task_description, task.title)
    val outcomeButtonsEnabled = !isProcessing && !isBattleInputLocked
    val primaryTextColor = if (task.isFailed) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = FailedContentAlpha)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (task.isFailed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FailedContentAlpha)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-${task.taskId}-${task.occurrenceDate}"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = task.title,
                        color = primaryTextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.metadataText(),
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    reminder?.let {
                        TaskReminderSummary(
                            task = task,
                            reminder = it,
                            textColor = secondaryTextColor,
                            onRecoverReminder = onRecoverReminder,
                        )
                    }
                    if (task.memo.isNotBlank()) {
                        Text(
                            text = task.memo,
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { onEditTask(task.taskId, task.occurrenceDate) },
                        modifier = Modifier
                            .testTag("edit-task-${task.taskId}-${task.occurrenceDate}")
                            .semantics { contentDescription = editDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            onRequestDeleteTask(
                                task.taskId,
                                task.occurrenceDate,
                                task.title,
                            )
                        },
                        modifier = Modifier
                            .testTag("delete-task-${task.taskId}-${task.occurrenceDate}")
                            .semantics { contentDescription = deleteDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.calendar_combat_reward),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.calendar_combat_reward_conditions),
                    modifier = Modifier.testTag(
                        "reward-conditions-${task.taskId}-${task.occurrenceDate}",
                    ),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.calendar_combat_reward_difficulty),
                    modifier = Modifier.testTag(
                        "reward-difficulty-${task.taskId}-${task.occurrenceDate}",
                    ),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when (task.status) {
                    TaskOccurrenceStatus.TODO -> TodoOutcomeButtons(
                        task = task,
                        enabled = outcomeButtonsEnabled,
                        isProcessing = isProcessing,
                        completeDescription = completeDescription,
                        failDescription = failDescription,
                        onCompleteOccurrence = onCompleteOccurrence,
                        onFailOccurrence = onFailOccurrence,
                    )

                    TaskOccurrenceStatus.COMPLETED -> TerminalOutcomeAction(
                        statusText = stringResource(R.string.calendar_status_completed),
                        statusIcon = Icons.Default.Check,
                        statusColor = MaterialTheme.colorScheme.primary,
                        actionText = stringResource(R.string.calendar_undo_complete),
                        actionDescription = undoDescription,
                        actionTag = "undo-task-${task.taskId}-${task.occurrenceDate}",
                        enabled = outcomeButtonsEnabled,
                        isProcessing = isProcessing,
                        onClick = {
                            onUndoCompleteOccurrence(task.taskId, task.occurrenceDate)
                        },
                    )

                    TaskOccurrenceStatus.FAILED -> {
                        TerminalOutcomeAction(
                            statusText = stringResource(R.string.calendar_status_failed),
                            statusIcon = Icons.Default.Error,
                            statusColor = MaterialTheme.colorScheme.error,
                            actionText = stringResource(R.string.calendar_undo_fail),
                            actionDescription = undoFailDescription,
                            actionTag = "undo-fail-task-${task.taskId}-${task.occurrenceDate}",
                            enabled = outcomeButtonsEnabled,
                            isProcessing = isProcessing,
                            actionUsesErrorColor = true,
                            onClick = { onUndoFailOccurrence(task.taskId, task.occurrenceDate) },
                        )
                        Text(
                            text = stringResource(R.string.calendar_undo_fail_notice),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskReminderSummary(
    task: TaskOccurrence,
    reminder: CalendarReminderUiState,
    textColor: androidx.compose.ui.graphics.Color,
    onRecoverReminder: (Long, LocalDate) -> Unit,
) {
    val mode = reminder.mode.displayName()
    val day = reminder.dayRelation.displayName()
    val timePattern = stringResource(R.string.calendar_reminder_time_format)
    val time = reminder.triggerTime.format(
        DateTimeFormatter.ofPattern(timePattern, LocalLocale.current.platformLocale),
    )
    val summary = stringResource(
        R.string.calendar_task_reminder_summary,
        mode,
        day,
        time,
    )
    val description = stringResource(
        R.string.calendar_task_reminder_description,
        mode,
        day,
        time,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = summary,
            modifier = Modifier
                .testTag("task-reminder-${task.taskId}-${task.occurrenceDate}")
                .semantics { contentDescription = description },
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        reminder.recoveryReason?.let { reason ->
            val statusText = when (reason) {
                ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS ->
                    stringResource(R.string.calendar_reminder_status_post_notifications_required)
                ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL ->
                    stringResource(R.string.calendar_reminder_status_channel_disabled)
                ReminderCapabilityRecoveryReason.EXACT_ALARM ->
                    stringResource(R.string.calendar_reminder_status_exact_alarm_required)
            }
            val actionText = when (reason) {
                ReminderCapabilityRecoveryReason.POST_NOTIFICATIONS ->
                    stringResource(R.string.calendar_reminder_recovery_app_settings)
                ReminderCapabilityRecoveryReason.NOTIFICATION_CHANNEL ->
                    stringResource(R.string.calendar_reminder_recovery_channel_settings)
                ReminderCapabilityRecoveryReason.EXACT_ALARM ->
                    stringResource(R.string.calendar_reminder_recovery_exact_settings)
            }
            val actionDescription = stringResource(
                R.string.calendar_reminder_recovery_action_description,
                task.title,
                actionText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(
                    onClick = {
                        onRecoverReminder(task.taskId, task.occurrenceDate)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(
                            "reminder-recovery-${task.taskId}-${task.occurrenceDate}",
                        )
                        .semantics { contentDescription = actionDescription },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = actionText)
                }
            }
        }
        if (reminder.hasScheduleError) {
            Text(
                text = stringResource(R.string.calendar_task_reminder_schedule_error),
                modifier = Modifier.testTag(
                    "reminder-error-${task.taskId}-${task.occurrenceDate}",
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TodoOutcomeButtons(
    task: TaskOccurrence,
    enabled: Boolean,
    isProcessing: Boolean,
    completeDescription: String,
    failDescription: String,
    onCompleteOccurrence: (Long, LocalDate) -> Unit,
    onFailOccurrence: (Long, LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        CompactOutcomeButton(
            onClick = { onCompleteOccurrence(task.taskId, task.occurrenceDate) },
            enabled = enabled,
            actionTag = "complete-task-${task.taskId}-${task.occurrenceDate}",
            actionDescription = completeDescription,
            label = stringResource(R.string.calendar_complete),
            icon = Icons.Default.Check,
            showProgress = isProcessing,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        CompactOutcomeButton(
            onClick = { onFailOccurrence(task.taskId, task.occurrenceDate) },
            enabled = enabled,
            actionTag = "fail-task-${task.taskId}-${task.occurrenceDate}",
            actionDescription = failDescription,
            label = stringResource(R.string.calendar_fail),
            icon = Icons.Default.Close,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    }
}

@Composable
private fun CompactOutcomeButton(
    onClick: () -> Unit,
    enabled: Boolean,
    actionTag: String,
    actionDescription: String,
    label: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    showProgress: Boolean = false,
) {
    val visualContainerColor = if (enabled) {
        containerColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val visualContentColor = if (enabled) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(actionTag)
            .semantics { contentDescription = actionDescription }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = visualContainerColor,
            contentColor = visualContentColor,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showProgress) {
                    OutcomeProgressIndicator()
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TerminalOutcomeAction(
    statusText: String,
    statusIcon: ImageVector,
    statusColor: androidx.compose.ui.graphics.Color,
    actionText: String,
    actionDescription: String,
    actionTag: String,
    enabled: Boolean,
    isProcessing: Boolean,
    actionUsesErrorColor: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(actionTag)
                .semantics { contentDescription = actionDescription },
            colors = if (actionUsesErrorColor) {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (isProcessing) {
                OutcomeProgressIndicator()
            } else {
                Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = actionText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OutcomeProgressIndicator() {
    val description = stringResource(R.string.calendar_processing_description)
    CircularProgressIndicator(
        modifier = Modifier
            .sizeIn(minWidth = 18.dp, minHeight = 18.dp, maxWidth = 18.dp, maxHeight = 18.dp)
            .semantics { contentDescription = description },
        strokeWidth = 2.dp,
    )
}

@Composable
private fun TaskEditorDialog(
    form: TaskEditorUiState,
    message: CalendarUiMessage?,
    onTitleChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onDifficultyChange: (TaskDifficulty) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRecurrenceRuleChange: (RecurrenceRule) -> Unit,
    onReminderModeChange: (ReminderMode) -> Unit,
    onShowReminderCustomTimePicker: () -> Unit,
    onReminderCustomTimeChange: (LocalTime?) -> Unit,
    onDismissReminderCustomTimePicker: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("task-editor-dialog"),
        title = {
            Text(
                text = when (form.mode) {
                    TaskEditorMode.ADD -> stringResource(R.string.calendar_task_add_title)
                    TaskEditorMode.EDIT -> stringResource(R.string.calendar_task_edit_title)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("task-editor-scroll"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KoreanImeTextField(
                    value = form.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.testTag("new-task-title"),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.calendar_task_title)) },
                )
                KoreanImeTextField(
                    value = form.memo,
                    onValueChange = onMemoChange,
                    modifier = Modifier.testTag("new-task-memo"),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    label = { Text(text = stringResource(R.string.calendar_task_memo)) },
                )
                TaskTimeField(
                    time = form.time,
                    onValueChange = onTimeChange,
                )
                ReminderSelector(
                    form = form,
                    message = message,
                    onModeChange = onReminderModeChange,
                    onShowCustomTimePicker = onShowReminderCustomTimePicker,
                )
                DifficultySelector(
                    selected = form.difficulty,
                    onSelect = onDifficultyChange,
                )
                RecurrenceSelector(
                    selected = form.recurrenceRule,
                    onSelect = onRecurrenceRuleChange,
                )
                CategorySelector(
                    selected = form.category,
                    presets = form.categoryPresets,
                    onSelect = onCategoryChange,
                )
                message?.takeUnless(CalendarUiMessage::isReminderEditorMessage)
                    ?.let { calendarMessage ->
                    Text(
                        text = calendarMessage.displayText(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                modifier = Modifier.testTag("save-task-button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.calendar_task_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.calendar_task_cancel))
            }
        },
    )

    if (form.isReminderCustomTimePickerOpen) {
        TaskTimePickerDialog(
            initialTime = form.reminderSetting.customTime ?: DefaultTaskEditorTime,
            title = stringResource(R.string.calendar_reminder_custom_picker_title),
            tagPrefix = "reminder-time",
            onDismiss = onDismissReminderCustomTimePicker,
            onClear = { onReminderCustomTimeChange(null) },
            onConfirm = onReminderCustomTimeChange,
        )
    }
}

@Composable
private fun KoreanImeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    label: @Composable (() -> Unit)? = null,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { nextValue ->
            textFieldValue = nextValue
            onValueChange(nextValue.text)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else maxLines,
        label = label,
    )
}

@Composable
private fun TaskTimeField(
    time: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val timeText = time?.format(TaskDisplayTimeFormatter)
        ?: stringResource(R.string.calendar_time_none)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.calendar_task_time),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = { isPickerOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task-time-button"),
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = timeText)
        }
    }

    if (isPickerOpen) {
        TaskTimePickerDialog(
            initialTime = time ?: DefaultTaskEditorTime,
            title = stringResource(R.string.calendar_time_picker_title),
            onDismiss = { isPickerOpen = false },
            onClear = {
                onValueChange(null)
                isPickerOpen = false
            },
            onConfirm = { selectedTime ->
                onValueChange(selectedTime)
                isPickerOpen = false
            },
        )
    }
}

@Composable
private fun ReminderSelector(
    form: TaskEditorUiState,
    message: CalendarUiMessage?,
    onModeChange: (ReminderMode) -> Unit,
    onShowCustomTimePicker: () -> Unit,
) {
    val presetHelper = stringResource(R.string.calendar_reminder_preset_requires_time)
    Column(
        modifier = Modifier.testTag("task-reminder-selector"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.calendar_task_reminder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        ReminderMode.entries.chunked(2).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModes.forEach { mode ->
                    val enabled = mode !in PresetReminderModes ||
                        form.arePresetRemindersEnabled
                    val label = mode.displayName()
                    val description = if (enabled) {
                        stringResource(R.string.calendar_reminder_mode_description, label)
                    } else {
                        stringResource(
                            R.string.calendar_reminder_mode_disabled_description,
                            label,
                        )
                    }
                    FilterChip(
                        selected = mode == form.reminderSetting.mode,
                        onClick = { onModeChange(mode) },
                        enabled = enabled,
                        label = { Text(text = label) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("task-reminder-mode-${mode.name.lowercase()}")
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
        if (!form.arePresetRemindersEnabled) {
            Text(
                text = presetHelper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (form.reminderSetting.mode == ReminderMode.CUSTOM_TIME) {
            val customTimeText = form.reminderSetting.customTime
                ?.format(TaskDisplayTimeFormatter)
                ?: stringResource(R.string.calendar_reminder_custom_time_unset)
            val customTimeDescription = stringResource(
                R.string.calendar_reminder_custom_time_description,
                customTimeText,
            )
            OutlinedButton(
                onClick = onShowCustomTimePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("task-reminder-custom-time-button")
                    .semantics { contentDescription = customTimeDescription },
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = customTimeText)
            }
        }
        form.reminderStatus.displayTextOrNull()?.let { statusText ->
            Text(
                text = statusText,
                modifier = Modifier.testTag("task-reminder-status"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        message?.takeIf(CalendarUiMessage::isReminderEditorMessage)?.let { reminderMessage ->
            Text(
                text = reminderMessage.displayText(),
                modifier = Modifier.testTag("task-reminder-message"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTimePickerDialog(
    initialTime: LocalTime,
    title: String,
    tagPrefix: String = "task-time",
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    var useTextInput by rememberSaveable { mutableStateOf(false) }

    TimePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("$tagPrefix-picker-dialog"),
        title = { Text(text = title) },
        modeToggleButton = {
            IconButton(
                onClick = { useTextInput = !useTextInput },
                modifier = Modifier.testTag("$tagPrefix-input-toggle"),
            ) {
                Icon(
                    imageVector = if (useTextInput) Icons.Default.Schedule else Icons.Default.Keyboard,
                    contentDescription = if (useTextInput) {
                        stringResource(R.string.calendar_time_use_clock)
                    } else {
                        stringResource(R.string.calendar_time_use_input)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClear,
                modifier = Modifier.testTag("$tagPrefix-clear"),
            ) {
                Text(text = stringResource(R.string.calendar_time_none))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                },
                modifier = Modifier.testTag("$tagPrefix-confirm"),
            ) {
                Text(text = stringResource(R.string.calendar_confirm))
            }
        },
    ) {
        if (useTextInput) {
            TimeInput(
                state = timePickerState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("$tagPrefix-input"),
            )
        } else {
            TimePicker(
                state = timePickerState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("$tagPrefix-clock"),
            )
        }
    }
}

@Composable
private fun CategorySelector(
    selected: String,
    presets: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("new-task-category"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.calendar_task_category),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        presets.chunked(3).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPresets.forEach { category ->
                    FilterChip(
                        selected = category == selected,
                        onClick = { onSelect(category) },
                        label = { Text(text = category) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("task-category-$category"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteTaskDialog(
    confirmation: DeleteTaskConfirmationUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete-task-dialog"),
        title = { Text(text = stringResource(R.string.calendar_delete_dialog_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.calendar_delete_dialog_message,
                    confirmation.title,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-delete-task"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.calendar_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel-delete-task"),
            ) {
                Text(text = stringResource(R.string.calendar_task_cancel))
            }
        },
    )
}

@Composable
private fun ExactAlarmRationaleDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("exact-alarm-rationale-dialog"),
        title = {
            Text(text = stringResource(R.string.calendar_exact_alarm_rationale_title))
        },
        text = {
            Text(
                text = stringResource(R.string.calendar_exact_alarm_rationale_message),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open-exact-alarm-settings"),
            ) {
                Text(text = stringResource(R.string.calendar_exact_alarm_settings_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.calendar_task_cancel))
            }
        },
    )
}

@Composable
private fun DifficultySelector(
    selected: TaskDifficulty,
    onSelect: (TaskDifficulty) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.calendar_task_difficulty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskDifficulty.entries.forEach { difficulty ->
                FilterChip(
                    selected = difficulty == selected,
                    onClick = { onSelect(difficulty) },
                    label = { Text(text = difficulty.displayName()) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("new-task-difficulty-${difficulty.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun RecurrenceSelector(
    selected: RecurrenceRule,
    onSelect: (RecurrenceRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.calendar_task_recurrence),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        RecurrenceRule.entries.chunked(2).forEach { rowRules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowRules.forEach { recurrenceRule ->
                    FilterChip(
                        selected = recurrenceRule == selected,
                        onClick = { onSelect(recurrenceRule) },
                        label = { Text(text = recurrenceRule.displayName()) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("new-task-recurrence-${recurrenceRule.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

private fun YearMonth.calendarCells(): List<LocalDate?> {
    val firstDay = atDay(1)
    val leadingEmptyCells = firstDay.dayOfWeek.value % 7
    val dates = (1..lengthOfMonth()).map { atDay(it) }
    val cells = List(leadingEmptyCells) { null } + dates
    val remainder = cells.size % 7
    return if (remainder == 0) cells else cells + List(7 - remainder) { null }
}

@Composable
private fun TaskOccurrence.metadataText(): String {
    val timeText = time?.format(TaskDisplayTimeFormatter)
        ?: stringResource(R.string.calendar_time_none)
    return stringResource(
        R.string.calendar_task_metadata,
        timeText,
        recurrenceRule.displayName(),
        difficulty.displayName(),
        category,
    )
}

@Composable
private fun CalendarDaySummary.indicatorText(): String? =
    when {
        totalCount <= 0 -> null
        failedCount > 0 && completedCount > 0 -> stringResource(
            R.string.calendar_day_completed_failed_count,
            completedCount,
            totalCount,
            failedCount,
        )
        failedCount > 0 -> stringResource(
            R.string.calendar_day_task_failed_count,
            totalCount,
            failedCount,
        )
        completedCount > 0 -> stringResource(
            R.string.calendar_day_completed_count,
            completedCount,
            totalCount,
        )
        else -> stringResource(R.string.calendar_day_task_count, totalCount)
    }

@Composable
private fun LocalDate.contentDescription(summary: CalendarDaySummary?): String {
    val totalCount = summary?.totalCount ?: 0
    val completedCount = summary?.completedCount ?: 0
    val failedCount = summary?.failedCount ?: 0
    return stringResource(
        R.string.calendar_day_description,
        year,
        monthValue,
        dayOfMonth,
        totalCount,
        completedCount,
        failedCount,
    )
}

@Composable
private fun LocalDate.displayText(): String = stringResource(
    R.string.calendar_selected_date,
    year,
    monthValue,
    dayOfMonth,
    dayOfWeek.displayName(),
)

@Composable
private fun DayOfWeek.displayName(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.calendar_weekday_monday
        DayOfWeek.TUESDAY -> R.string.calendar_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.calendar_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.calendar_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.calendar_weekday_friday
        DayOfWeek.SATURDAY -> R.string.calendar_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.calendar_weekday_sunday
    },
)

@Composable
private fun TaskDifficulty.displayName(): String = stringResource(
    when (this) {
        TaskDifficulty.EASY -> R.string.calendar_difficulty_easy
        TaskDifficulty.MEDIUM -> R.string.calendar_difficulty_medium
        TaskDifficulty.HARD -> R.string.calendar_difficulty_hard
    },
)

@Composable
private fun RecurrenceRule.displayName(): String = stringResource(
    when (this) {
        RecurrenceRule.NONE -> R.string.calendar_recurrence_none
        RecurrenceRule.DAILY -> R.string.calendar_recurrence_daily
        RecurrenceRule.WEEKLY -> R.string.calendar_recurrence_weekly
        RecurrenceRule.MONTHLY -> R.string.calendar_recurrence_monthly
    },
)

@Composable
private fun ReminderMode.displayName(): String = stringResource(
    when (this) {
        ReminderMode.NONE -> R.string.calendar_reminder_none
        ReminderMode.TEN_MINUTES_BEFORE -> R.string.calendar_reminder_ten_minutes_before
        ReminderMode.ONE_HOUR_BEFORE -> R.string.calendar_reminder_one_hour_before
        ReminderMode.CUSTOM_TIME -> R.string.calendar_reminder_custom_time
    },
)

@Composable
private fun ReminderDayRelation.displayName(): String = stringResource(
    when (this) {
        ReminderDayRelation.SAME_DAY -> R.string.calendar_reminder_day_same
        ReminderDayRelation.PREVIOUS_DAY -> R.string.calendar_reminder_day_previous
    },
)

@Composable
private fun ReminderScheduleStatus.displayTextOrNull(): String? = when (this) {
    ReminderScheduleStatus.DISABLED,
    ReminderScheduleStatus.PENDING,
    -> null
    ReminderScheduleStatus.SCHEDULED ->
        stringResource(R.string.calendar_reminder_status_scheduled)
    ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED ->
        stringResource(R.string.calendar_reminder_status_post_notifications_required)
    ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED ->
        stringResource(R.string.calendar_reminder_status_channel_disabled)
    ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED ->
        stringResource(R.string.calendar_reminder_status_exact_alarm_required)
    ReminderScheduleStatus.DELIVERED ->
        stringResource(R.string.calendar_reminder_status_delivered)
    ReminderScheduleStatus.NO_FUTURE_OCCURRENCE ->
        stringResource(R.string.calendar_reminder_status_no_future)
    ReminderScheduleStatus.ERROR ->
        stringResource(R.string.calendar_reminder_status_error)
}

@Composable
private fun CalendarUiMessage.displayText(): String = stringResource(
    when (this) {
        CalendarUiMessage.TaskNotFound -> R.string.calendar_error_task_not_found
        CalendarUiMessage.LoadFailed -> R.string.calendar_error_load_failed
        CalendarUiMessage.InvalidTime -> R.string.calendar_error_invalid_time
        CalendarUiMessage.TitleRequired -> R.string.calendar_error_title_required
        CalendarUiMessage.SaveFailed -> R.string.calendar_error_save_failed
        CalendarUiMessage.DeleteFailed -> R.string.calendar_error_delete_failed
        CalendarUiMessage.CompleteFailed -> R.string.calendar_error_complete_failed
        CalendarUiMessage.UndoCompleteFailed -> R.string.calendar_error_undo_complete_failed
        CalendarUiMessage.FailFailed -> R.string.calendar_error_fail_failed
        CalendarUiMessage.UndoFailFailed -> R.string.calendar_error_undo_fail_failed
        CalendarUiMessage.ReminderClearedAfterTimeRemoved ->
            R.string.calendar_reminder_cleared_after_time_removed
        CalendarUiMessage.ReminderCustomTimeRequired ->
            R.string.calendar_reminder_custom_time_required
        CalendarUiMessage.ReminderPostNotificationsRequired ->
            R.string.calendar_reminder_post_notifications_required
        CalendarUiMessage.ReminderNotificationChannelDisabled ->
            R.string.calendar_reminder_notification_channel_disabled
        CalendarUiMessage.ReminderNotificationPermissionDenied ->
            R.string.calendar_reminder_notification_permission_denied
        CalendarUiMessage.ReminderExactAlarmAccessRequired ->
            R.string.calendar_reminder_exact_alarm_access_required
        CalendarUiMessage.ReminderNoFutureOccurrence ->
            R.string.calendar_reminder_no_future_occurrence
        CalendarUiMessage.ReminderScheduleError ->
            R.string.calendar_reminder_schedule_error
    },
)

private fun CalendarUiMessage.isReminderEditorMessage(): Boolean =
    this == CalendarUiMessage.ReminderClearedAfterTimeRemoved ||
        this == CalendarUiMessage.ReminderCustomTimeRequired

private val TaskDisplayTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DefaultTaskEditorTime: LocalTime = LocalTime.of(9, 0)
private val CompactViewportHeightThreshold = 520.dp
private const val FailedContentAlpha = 0.58f
private val PresetReminderModes = setOf(
    ReminderMode.TEN_MINUTES_BEFORE,
    ReminderMode.ONE_HOUR_BEFORE,
)
