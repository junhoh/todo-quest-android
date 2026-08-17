package com.todoquest.feature.settings

import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeBattleSfxSettingsRepository
    private lateinit var reminderScheduler: FakeReminderScheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeBattleSfxSettingsRepository(initialEnabled = true)
        reminderScheduler = FakeReminderScheduler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingPreferenceDefaultIsRenderedAsEnabled() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            SettingsUiState(
                battleSfxEnabled = true,
                isSaving = false,
                saveFailed = false,
                notificationPermission = NotificationPermissionUiState.Available,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun offAndOnCommandsRenderRepositoryConfirmedValues() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setBattleSfxEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.battleSfxEnabled)

        viewModel.setBattleSfxEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf(false, true), repository.requests)
        assertTrue(viewModel.uiState.value.battleSfxEnabled)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun persistenceFailureRollsBackToLastConfirmedValueAndExposesTypedError() =
        runTest(dispatcher) {
            repository.results.add(false)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setBattleSfxEnabled(false)
            advanceUntilIdle()

            assertEquals(listOf(false), repository.requests)
            assertTrue(repository.isEnabled.value)
            assertTrue(viewModel.uiState.value.battleSfxEnabled)
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveFailed)

            viewModel.clearSaveError()

            assertFalse(viewModel.uiState.value.saveFailed)
        }

    @Test
    fun rapidToggleCommandsArePersistedInRequestOrderWithoutReversal() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setBattleSfxEnabled(false)
        viewModel.setBattleSfxEnabled(true)
        viewModel.setBattleSfxEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(false, true, false), repository.requests)
        assertFalse(repository.isEnabled.value)
        assertFalse(viewModel.uiState.value.battleSfxEnabled)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun postNotificationCapabilityStatusesMapToTypedUiStateWithoutCheckingExactAlarm() =
        runTest(dispatcher) {
            val viewModel = createViewModel(ReminderCapabilityStatus.AVAILABLE)
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.Available,
                viewModel.uiState.value.notificationPermission,
            )

            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.REQUIRED)
            viewModel.refreshNotificationPermission()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.Required,
                viewModel.uiState.value.notificationPermission,
            )

            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.CHANNEL_DISABLED)
            viewModel.refreshNotificationPermission()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.ChannelDisabled,
                viewModel.uiState.value.notificationPermission,
            )
            assertEquals(
                listOf(
                    ReminderCapability.POST_NOTIFICATIONS,
                    ReminderCapability.POST_NOTIFICATIONS,
                    ReminderCapability.POST_NOTIFICATIONS,
                ),
                reminderScheduler.capabilityCalls,
            )
        }

    @Test
    fun capabilityFailureMapsToCheckFailedAndExplicitRetryCanRecover() = runTest(dispatcher) {
        reminderScheduler.enqueueFailure(IllegalStateException("capability unavailable"))
        val viewModel = SettingsViewModel(repository, reminderScheduler, dispatcher)
        advanceUntilIdle()

        assertEquals(
            NotificationPermissionUiState.CheckFailed,
            viewModel.uiState.value.notificationPermission,
        )

        reminderScheduler.enqueueStatus(ReminderCapabilityStatus.AVAILABLE)
        viewModel.refreshNotificationPermission()
        advanceUntilIdle()

        assertEquals(
            NotificationPermissionUiState.Available,
            viewModel.uiState.value.notificationPermission,
        )
    }

    @Test
    fun consecutiveRefreshesCancelTheOlderCheckAndOnlyApplyTheLatestResult() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refreshNotificationPermission()
            runCurrent()
            assertEquals(
                NotificationPermissionUiState.Loading,
                viewModel.uiState.value.notificationPermission,
            )

            viewModel.refreshNotificationPermission()
            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.CHANNEL_DISABLED)
            advanceUntilIdle()

            assertEquals(
                NotificationPermissionUiState.ChannelDisabled,
                viewModel.uiState.value.notificationPermission,
            )
            assertEquals(3, reminderScheduler.capabilityCalls.size)
            assertTrue(
                reminderScheduler.capabilityCalls.all {
                    it == ReminderCapability.POST_NOTIFICATIONS
                },
            )
        }

    @Test
    fun notificationCheckFailureDoesNotDisableBattleSfxPersistence() = runTest(dispatcher) {
        reminderScheduler.enqueueFailure(IllegalStateException("capability unavailable"))
        val viewModel = SettingsViewModel(repository, reminderScheduler, dispatcher)
        advanceUntilIdle()
        assertEquals(
            NotificationPermissionUiState.CheckFailed,
            viewModel.uiState.value.notificationPermission,
        )

        viewModel.setBattleSfxEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(false), repository.requests)
        assertFalse(viewModel.uiState.value.battleSfxEnabled)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertEquals(
            NotificationPermissionUiState.CheckFailed,
            viewModel.uiState.value.notificationPermission,
        )
    }

    @Test
    fun permissionResultSettingsReturnAndLifecycleResumeRefreshTheLatestCapability() =
        runTest(dispatcher) {
            val viewModel = createViewModel(ReminderCapabilityStatus.REQUIRED)
            advanceUntilIdle()

            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.AVAILABLE)
            viewModel.onNotificationPermissionResult()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.Available,
                viewModel.uiState.value.notificationPermission,
            )

            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.CHANNEL_DISABLED)
            viewModel.onNotificationSettingsReturned()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.ChannelDisabled,
                viewModel.uiState.value.notificationPermission,
            )

            reminderScheduler.enqueueStatus(ReminderCapabilityStatus.REQUIRED)
            viewModel.onLifecycleResumed()
            advanceUntilIdle()
            assertEquals(
                NotificationPermissionUiState.Required,
                viewModel.uiState.value.notificationPermission,
            )
            assertEquals(4, reminderScheduler.capabilityCalls.size)
        }

    private fun createViewModel(
        initialStatus: ReminderCapabilityStatus = ReminderCapabilityStatus.AVAILABLE,
    ): SettingsViewModel {
        reminderScheduler.enqueueStatus(initialStatus)
        return SettingsViewModel(repository, reminderScheduler, dispatcher)
    }

    private class FakeBattleSfxSettingsRepository(
        initialEnabled: Boolean,
    ) : BattleSfxSettingsRepository {
        private val enabled = MutableStateFlow(initialEnabled)
        override val isEnabled: StateFlow<Boolean> = enabled

        val requests = mutableListOf<Boolean>()
        val results = ArrayDeque<Boolean>()

        override fun setEnabled(enabled: Boolean): Boolean {
            requests += enabled
            val succeeds = if (results.isEmpty()) true else results.removeFirst()
            if (succeeds) {
                this.enabled.value = enabled
            }
            return succeeds
        }
    }

    private class FakeReminderScheduler : ReminderScheduler {
        private val capabilityResults =
            Channel<Result<ReminderCapabilityStatus>>(capacity = Channel.UNLIMITED)
        val capabilityCalls = mutableListOf<ReminderCapability>()

        fun enqueueStatus(status: ReminderCapabilityStatus) {
            capabilityResults.trySend(Result.success(status)).getOrThrow()
        }

        fun enqueueFailure(failure: Throwable) {
            capabilityResults.trySend(Result.failure(failure)).getOrThrow()
        }

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus {
            capabilityCalls += capability
            return capabilityResults.receive().getOrThrow()
        }

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus =
            error("scheduleExact is outside this test scope")

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus =
            error("cancel is outside this test scope")
    }
}
