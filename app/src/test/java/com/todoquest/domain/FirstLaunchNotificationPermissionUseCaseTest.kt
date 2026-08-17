package com.todoquest.domain

import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.usecase.PrepareFirstLaunchNotificationPromptUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchNotificationPermissionUseCaseTest {
    @Test
    fun firstLaunchWithMissingNotificationCapabilityRequestsPrompt() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(ReminderCapabilityStatus.REQUIRED)
        val useCase = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)

        val shouldPrompt = useCase()

        assertTrue(shouldPrompt)
        assertEquals(1, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    @Test
    fun firstLaunchWithAvailableNotificationCapabilityDoesNotRequestPrompt() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(ReminderCapabilityStatus.AVAILABLE)
        val useCase = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)

        val shouldPrompt = useCase()

        assertFalse(shouldPrompt)
        assertEquals(1, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    @Test
    fun secondLaunchDoesNotCheckCapabilitiesAgain() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(ReminderCapabilityStatus.REQUIRED)
        val useCase = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)

        assertTrue(useCase())
        assertFalse(useCase())

        assertEquals(2, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    @Test
    fun notificationCapabilityFailureIsIsolatedFromLaunch() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(
            notificationStatus = ReminderCapabilityStatus.REQUIRED,
            capabilityFailure = IllegalStateException("notification capability unavailable"),
        )
        val useCase = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)

        assertFalse(useCase())
        assertFalse(useCase())

        assertEquals(2, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    @Test
    fun firstLaunchNeverChecksExactAlarmCapability() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(ReminderCapabilityStatus.REQUIRED)

        PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)()

        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    private class FakeFirstLaunchNotificationPromptStore : FirstLaunchNotificationPromptStore {
        var consumeCalls = 0
        private var isFirstLaunchCheck = true

        override fun consumeFirstLaunchCheck(): Boolean {
            consumeCalls += 1
            return isFirstLaunchCheck.also { isFirstLaunchCheck = false }
        }
    }

    private class FakeReminderScheduler(
        private val notificationStatus: ReminderCapabilityStatus,
        private val capabilityFailure: Throwable? = null,
    ) : ReminderScheduler {
        val capabilityCalls = mutableListOf<ReminderCapability>()

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus {
            capabilityCalls += capability
            capabilityFailure?.let { throw it }
            return notificationStatus
        }

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus =
            error("First-launch policy must not schedule alarms")

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus =
            error("First-launch policy must not cancel alarms")
    }
}
