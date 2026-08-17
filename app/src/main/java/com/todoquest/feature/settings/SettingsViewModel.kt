package com.todoquest.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface NotificationPermissionUiState {
    data object Loading : NotificationPermissionUiState

    data object Available : NotificationPermissionUiState

    data object Required : NotificationPermissionUiState

    data object ChannelDisabled : NotificationPermissionUiState

    data object CheckFailed : NotificationPermissionUiState
}

data class SettingsUiState(
    val battleSfxEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val notificationPermission: NotificationPermissionUiState =
        NotificationPermissionUiState.Loading,
)

class SettingsViewModel(
    private val repository: BattleSfxSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SettingsUiState(battleSfxEnabled = repository.isEnabled.value),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    private val commands = Channel<Boolean>(capacity = Channel.UNLIMITED)
    private var notificationPermissionRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.isEnabled.collect { enabled ->
                mutableUiState.update { state ->
                    state.copy(battleSfxEnabled = enabled)
                }
            }
        }
        viewModelScope.launch {
            for (enabled in commands) {
                persist(enabled)
            }
        }
        refreshNotificationPermission()
    }

    fun setBattleSfxEnabled(enabled: Boolean) {
        commands.trySend(enabled)
    }

    fun clearSaveError() {
        mutableUiState.update { state -> state.copy(saveFailed = false) }
    }

    fun onNotificationPermissionResult() {
        refreshNotificationPermission()
    }

    fun onNotificationSettingsReturned() {
        refreshNotificationPermission()
    }

    fun onLifecycleResumed() {
        refreshNotificationPermission()
    }

    fun refreshNotificationPermission() {
        notificationPermissionRefreshJob?.cancel()
        notificationPermissionRefreshJob = viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(notificationPermission = NotificationPermissionUiState.Loading)
            }
            val permissionState = try {
                val capabilityStatus = withContext(dispatcher) {
                    reminderScheduler.capabilityStatus(
                        ReminderCapability.POST_NOTIFICATIONS,
                    )
                }
                capabilityStatus.toNotificationPermissionUiState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                NotificationPermissionUiState.CheckFailed
            }
            mutableUiState.update { state ->
                state.copy(notificationPermission = permissionState)
            }
        }
    }

    private suspend fun persist(enabled: Boolean) {
        mutableUiState.update { state ->
            state.copy(isSaving = true, saveFailed = false)
        }
        val saved = try {
            withContext(dispatcher) {
                repository.setEnabled(enabled)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        mutableUiState.update { state ->
            state.copy(
                battleSfxEnabled = repository.isEnabled.value,
                isSaving = false,
                saveFailed = !saved,
            )
        }
    }
}

private fun ReminderCapabilityStatus.toNotificationPermissionUiState():
    NotificationPermissionUiState = when (this) {
    ReminderCapabilityStatus.AVAILABLE -> NotificationPermissionUiState.Available
    ReminderCapabilityStatus.REQUIRED -> NotificationPermissionUiState.Required
    ReminderCapabilityStatus.CHANNEL_DISABLED ->
        NotificationPermissionUiState.ChannelDisabled
}
