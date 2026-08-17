package com.todoquest.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todoquest.R
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.notification.AndroidReminderCapabilityAdapter
import com.todoquest.notification.NotificationPermissionLaunchAction

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    reminderCapabilityAdapter: AndroidReminderCapabilityAdapter,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onNotificationPermissionResult()
    }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onNotificationSettingsReturned()
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

    SettingsContent(
        state = state,
        onSetBattleSfxEnabled = viewModel::setBattleSfxEnabled,
        onClearSaveError = viewModel::clearSaveError,
        onNotificationPermissionAction = {
            val capabilityStatus = state.notificationPermission.capabilityStatusOrNull()
            if (capabilityStatus == null) {
                if (state.notificationPermission == NotificationPermissionUiState.CheckFailed) {
                    viewModel.refreshNotificationPermission()
                }
            } else {
                val action = runCatching {
                    reminderCapabilityAdapter.notificationPermissionSettingsAction(
                        capabilityStatus,
                    )
                }.getOrElse {
                    viewModel.refreshNotificationPermission()
                    return@SettingsContent
                }
                launchNotificationPermissionAction(
                    action = action,
                    launchRuntimePermission = notificationPermissionLauncher::launch,
                    launchSettings = notificationSettingsLauncher::launch,
                    refresh = viewModel::refreshNotificationPermission,
                )
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onSetBattleSfxEnabled: (Boolean) -> Unit,
    onClearSaveError: () -> Unit,
    onNotificationPermissionAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailureMessage = stringResource(R.string.settings_battle_sfx_save_failed)

    LaunchedEffect(state.saveFailed, saveFailureMessage) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailureMessage)
            onClearSaveError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("settings-top-bar"),
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("settings-content-scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BattleSfxSettingRow(
                enabled = state.battleSfxEnabled,
                isSaving = state.isSaving,
                onEnabledChange = onSetBattleSfxEnabled,
            )
            NotificationPermissionSettingRow(
                state = state.notificationPermission,
                onAction = onNotificationPermissionAction,
            )
        }
    }
}

@Composable
private fun NotificationPermissionSettingRow(
    state: NotificationPermissionUiState,
    onAction: () -> Unit,
) {
    val presentation = state.presentation()
    val label = stringResource(R.string.settings_notification_permission_label)
    val status = stringResource(presentation.statusResId)
    val action = stringResource(presentation.actionResId)
    val semanticsDescription = stringResource(
        R.string.settings_notification_permission_semantics,
        label,
        status,
        action,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = presentation.actionEnabled, onClick = onAction)
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticsDescription
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("settings-notification-permission-row"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status,
                modifier = Modifier.testTag("settings-notification-permission-status"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == NotificationPermissionUiState.Available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = onAction,
                enabled = presentation.actionEnabled,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = action }
                    .testTag("settings-notification-permission-action"),
            ) {
                Text(text = action)
            }
        }
    }
}

@Composable
private fun BattleSfxSettingRow(
    enabled: Boolean,
    isSaving: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val stateLabel = stringResource(
        if (enabled) R.string.settings_state_on else R.string.settings_state_off,
    )
    val label = stringResource(R.string.settings_battle_sfx_label)
    val description = stringResource(R.string.settings_battle_sfx_description)
    val semanticsDescription = stringResource(
        R.string.settings_battle_sfx_semantics,
        label,
        stateLabel,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = !isSaving) {
                    onEnabledChange(!enabled)
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticsDescription
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("settings-battle-sfx-row"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = !isSaving,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("settings-battle-sfx-switch"),
            )
        }
    }
}

internal fun launchNotificationPermissionAction(
    action: NotificationPermissionLaunchAction,
    launchRuntimePermission: (String) -> Unit,
    launchSettings: (Intent) -> Unit,
    refresh: () -> Unit,
) {
    val launched = runCatching {
        when (action) {
            is NotificationPermissionLaunchAction.RuntimePermission ->
                launchRuntimePermission(action.permission)
            is NotificationPermissionLaunchAction.AppSettings -> launchSettings(action.intent)
            NotificationPermissionLaunchAction.None -> return@runCatching false
        }
        true
    }.getOrDefault(false)
    if (!launched) {
        refresh()
    }
}

private fun NotificationPermissionUiState.capabilityStatusOrNull(): ReminderCapabilityStatus? =
    when (this) {
        NotificationPermissionUiState.Available -> ReminderCapabilityStatus.AVAILABLE
        NotificationPermissionUiState.Required -> ReminderCapabilityStatus.REQUIRED
        NotificationPermissionUiState.ChannelDisabled ->
            ReminderCapabilityStatus.CHANNEL_DISABLED
        NotificationPermissionUiState.Loading,
        NotificationPermissionUiState.CheckFailed,
        -> null
    }

private data class NotificationPermissionPresentation(
    @param:StringRes val statusResId: Int,
    @param:StringRes val actionResId: Int,
    val actionEnabled: Boolean,
)

private fun NotificationPermissionUiState.presentation(): NotificationPermissionPresentation =
    when (this) {
        NotificationPermissionUiState.Loading -> NotificationPermissionPresentation(
            statusResId = R.string.settings_notification_permission_status_loading,
            actionResId = R.string.settings_notification_permission_action_loading,
            actionEnabled = false,
        )
        NotificationPermissionUiState.Available -> NotificationPermissionPresentation(
            statusResId = R.string.settings_notification_permission_status_available,
            actionResId = R.string.settings_notification_permission_action_open_settings,
            actionEnabled = true,
        )
        NotificationPermissionUiState.Required -> NotificationPermissionPresentation(
            statusResId = R.string.settings_notification_permission_status_required,
            actionResId = R.string.settings_notification_permission_action_request,
            actionEnabled = true,
        )
        NotificationPermissionUiState.ChannelDisabled -> NotificationPermissionPresentation(
            statusResId = R.string.settings_notification_permission_status_channel_disabled,
            actionResId = R.string.settings_notification_permission_action_open_channel,
            actionEnabled = true,
        )
        NotificationPermissionUiState.CheckFailed -> NotificationPermissionPresentation(
            statusResId = R.string.settings_notification_permission_status_check_failed,
            actionResId = R.string.settings_notification_permission_action_retry,
            actionEnabled = true,
        )
    }
