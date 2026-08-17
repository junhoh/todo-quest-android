package com.todoquest.feature.character

import com.todoquest.core.AppClock
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterCurrentState
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.usecase.AllocateStatPointsUseCase
import com.todoquest.domain.usecase.ResetStatsUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeCharacterRepository
    private lateinit var clock: MutableClock
    private lateinit var statusEffectRepository: FakeStatusEffectRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCharacterRepository(snapshot())
        clock = MutableClock(LocalDate.of(2026, 7, 14))
        statusEffectRepository = FakeStatusEffectRepository(clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun snapshotMapsEveryCharacterSectionAndFormatsBasisPoints() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(12, state.level)
        assertFalse(state.isMaxLevel)
        assertEquals(1_145L, state.totalXp)
        assertEquals(45L, state.xpIntoCurrentLevel)
        assertEquals(100L, state.xpRequiredForNextLevel)
        assertEquals(0.45f, state.xpProgress)
        assertEquals(620L, state.gold)
        assertEquals(155, state.currentHp)
        assertEquals(210, state.maxHp)
        assertEquals(7, state.streakDays)
        assertEquals("5.1%", state.momentumBonus)
        assertEquals(CharacterLoadoutCatalog.defaultAppearance, state.appearance)
        assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, state.equippedItems)
        assertEquals(3, state.remainingUnspentPoints)
        assertEquals(0, state.pendingStatPoints)
        assertFalse(state.hasPendingStatAllocation)
        assertFalse(state.isSavingStatAllocation)
        assertEquals(4, state.baseStats.size)
        with(state.baseStats.single { it.type == StatType.STRENGTH }) {
            assertEquals(60, confirmedValue)
            assertEquals(0, pendingIncrease)
            assertEquals(60, expectedValue)
        }
        assertEquals(8, state.derivedStats.size)
        assertEquals(
            "7.6%",
            state.derivedStats.single { it.type == DerivedStatType.CRITICAL_CHANCE }.displayValue,
        )
        assertEquals(
            "156.5%",
            state.derivedStats.single { it.type == DerivedStatType.CRITICAL_DAMAGE }.displayValue,
        )
        assertEquals(
            "12.5%",
            state.derivedStats.single { it.type == DerivedStatType.STATUS_RESISTANCE }.displayValue,
        )
        assertEquals(
            "0.6%",
            state.derivedStats.single { it.type == DerivedStatType.GOLD_GAIN_BONUS }.displayValue,
        )
        assertFalse(state.isResetFree)
        assertEquals(340L, state.resetCostGold)
        assertTrue(state.canReset)
        assertNull(state.resetUnavailableReason)
    }

    @Test
    fun maxLevelKeepsTotalXpAndUsesFullProgress() = runTest(dispatcher) {
        repository.snapshot.value = snapshot(
            level = 50,
            totalXp = 7_777L,
            xpIntoCurrentLevel = 100L,
            isMaxLevel = true,
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(50, state.level)
        assertTrue(state.isMaxLevel)
        assertEquals(7_777L, state.totalXp)
        assertEquals(1f, state.xpProgress)
    }

    @Test
    fun screenEntryAndResumeRefreshTheRepositoryReferenceDate() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        clock.currentDate = LocalDate.of(2026, 7, 15)
        viewModel.onScreenEntered()
        advanceUntilIdle()

        clock.currentDate = LocalDate.of(2026, 7, 16)
        viewModel.onLifecycleResumed()
        advanceUntilIdle()

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 16),
            ),
            repository.observedDates,
        )
        assertEquals(2, statusEffectRepository.reconcileCalls)
    }

    @Test
    fun eligibleStatGuideIsPreparedOnlyOnFirstScreenEntryAndWaitsForLoadedCharacter() =
        runTest(dispatcher) {
            val loadGate = CompletableDeferred<Unit>()
            repository.observeGate = loadGate
            var prepareCalls = 0
            val viewModel = viewModel(
                prepareCharacterStatGuide = {
                    prepareCalls += 1
                    true
                },
            )

            viewModel.onScreenEntered()
            runCurrent()

            assertEquals(1, prepareCalls)
            assertTrue(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)

            loadGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.isStatAllocationGuideVisible)
        }

    @Test
    fun ineligibleStatGuideStaysHidden() = runTest(dispatcher) {
        var prepareCalls = 0
        val viewModel = viewModel(
            prepareCharacterStatGuide = {
                prepareCalls += 1
                false
            },
        )
        advanceUntilIdle()

        viewModel.onScreenEntered()
        advanceUntilIdle()

        assertEquals(1, prepareCalls)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
    }

    @Test
    fun eligibleAutomaticStatGuideIsNotShownWhenCharacterLoadFails() = runTest(dispatcher) {
        repository.observeFailure = IllegalStateException("character unavailable")
        val viewModel = viewModel(prepareCharacterStatGuide = { true })

        viewModel.onScreenEntered()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(CharacterUiMessage.LoadFailed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
    }

    @Test
    fun dismissingAutomaticStatGuideAcknowledgesAndClosesIt() = runTest(dispatcher) {
        var acknowledgementCalls = 0
        val viewModel = viewModel(
            prepareCharacterStatGuide = { true },
            acknowledgeCharacterStatGuide = {
                acknowledgementCalls += 1
                true
            },
        )
        advanceUntilIdle()
        viewModel.onScreenEntered()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStatAllocationGuideVisible)

        viewModel.dismissStatAllocationGuide()
        advanceUntilIdle()

        assertEquals(1, acknowledgementCalls)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
    }

    @Test
    fun automaticStatGuideIsNotPreparedAgainOnReentryOrResume() = runTest(dispatcher) {
        var prepareCalls = 0
        val viewModel = viewModel(
            prepareCharacterStatGuide = {
                prepareCalls += 1
                true
            },
        )
        advanceUntilIdle()

        viewModel.onScreenEntered()
        advanceUntilIdle()
        viewModel.dismissStatAllocationGuide()
        viewModel.onScreenEntered()
        viewModel.onLifecycleResumed()
        advanceUntilIdle()

        assertEquals(1, prepareCalls)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
    }

    @Test
    fun manualStatGuideCanReopenAfterAutomaticDismissWithoutAnotherAcknowledgement() =
        runTest(dispatcher) {
            var acknowledgementCalls = 0
            val viewModel = viewModel(
                prepareCharacterStatGuide = { true },
                acknowledgeCharacterStatGuide = {
                    acknowledgementCalls += 1
                    true
                },
            )
            advanceUntilIdle()
            viewModel.onScreenEntered()
            advanceUntilIdle()
            viewModel.dismissStatAllocationGuide()

            viewModel.showStatAllocationGuide()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isStatAllocationGuideVisible)
            assertEquals(1, acknowledgementCalls)

            viewModel.dismissStatAllocationGuide()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
            assertEquals(1, acknowledgementCalls)
        }

    @Test
    fun manuallyShownStatGuideDismissesWithoutAcknowledgingPreference() = runTest(dispatcher) {
        var acknowledgementCalls = 0
        val viewModel = viewModel(
            acknowledgeCharacterStatGuide = {
                acknowledgementCalls += 1
                true
            },
        )
        advanceUntilIdle()

        viewModel.showStatAllocationGuide()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStatAllocationGuideVisible)

        viewModel.dismissStatAllocationGuide()
        advanceUntilIdle()

        assertEquals(0, acknowledgementCalls)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
    }

    @Test
    fun prepareStatGuideFailureIsIsolatedFromCharacterAndDraftState() = runTest(dispatcher) {
        var prepareCalls = 0
        val viewModel = viewModel(
            prepareCharacterStatGuide = {
                prepareCalls += 1
                throw IllegalStateException("guide preference unavailable")
            },
        )
        advanceUntilIdle()

        viewModel.onScreenEntered()
        viewModel.onScreenEntered()
        advanceUntilIdle()
        viewModel.increaseStat(StatType.VITALITY)
        advanceUntilIdle()

        assertEquals(1, prepareCalls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
        assertEquals(1, viewModel.uiState.value.pendingStatPoints)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun acknowledgeStatGuideFailureStillClosesDialogAndPreservesDraftState() =
        runTest(dispatcher) {
            val viewModel = viewModel(
                prepareCharacterStatGuide = { true },
                acknowledgeCharacterStatGuide = {
                    throw IllegalStateException("guide preference unavailable")
                },
            )
            advanceUntilIdle()
            viewModel.onScreenEntered()
            advanceUntilIdle()
            viewModel.increaseStat(StatType.VITALITY)

            viewModel.dismissStatAllocationGuide()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStatAllocationGuideVisible)
            assertEquals(1, viewModel.uiState.value.pendingStatPoints)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun activeSevereInjuryIsExposedWithRecoveryCountAndCeilingRemainingHours() =
        runTest(dispatcher) {
            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = clock.now().plusSeconds(2 * 60 * 60 + 1),
                    remainingCompletions = 2,
                ),
            )

            val viewModel = viewModel()
            advanceUntilIdle()

            val effect = viewModel.uiState.value.activeStatusEffects.single()
            assertEquals(StatusEffectType.SEVERE_INJURY, effect.type)
            assertEquals(2, effect.remainingRecoveryCompletions)
            assertEquals(
                com.todoquest.feature.battle.StatusEffectRemainingTimeUiState.Hours(3),
                effect.remainingTime,
            )

            clock.currentInstant = clock.now().plusSeconds(2 * 60 * 60)
            viewModel.onLifecycleResumed()
            advanceUntilIdle()

            assertEquals(
                com.todoquest.feature.battle.StatusEffectRemainingTimeUiState.LessThanOneHour,
                viewModel.uiState.value.activeStatusEffects.single().remainingTime,
            )
        }

    @Test
    fun severeInjuryDetailsSelectionUsesLatestActiveViewModelStateAndDismisses() =
        runTest(dispatcher) {
            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = clock.now().plusSeconds(5 * 60 * 60),
                    remainingCompletions = 2,
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.showStatusEffectDetails(StatusEffectType.SEVERE_INJURY)
            advanceUntilIdle()
            assertEquals(
                viewModel.uiState.value.activeStatusEffects.single(),
                viewModel.uiState.value.selectedStatusEffect,
            )

            statusEffectRepository.effects.value = emptyList()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)

            statusEffectRepository.effects.value = listOf(
                severeInjury(
                    expiresAt = clock.now().plusSeconds(60 * 60),
                    revision = 2L,
                ),
            )
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)

            viewModel.showStatusEffectDetails(StatusEffectType.SEVERE_INJURY)
            viewModel.dismissStatusEffectDetails()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusEffect)
        }

    @Test
    fun resumeAtExactExpirationReconcilesDatabaseStateAndRemovesTheEffect() =
        runTest(dispatcher) {
            val expiresAt = clock.now().plusSeconds(60 * 60)
            statusEffectRepository.effects.value = listOf(severeInjury(expiresAt))
            val viewModel = viewModel()
            advanceUntilIdle()

            clock.currentInstant = expiresAt
            viewModel.onLifecycleResumed()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.activeStatusEffects.isEmpty())
            assertEquals(1, statusEffectRepository.reconcileCalls)
        }

    @Test
    fun statDescriptionSelectionIsExposedAndDismissedByViewModel() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.showBaseStatDescription(StatType.STRENGTH)
        advanceUntilIdle()
        assertEquals(
            StatDescriptionTarget.Base(StatType.STRENGTH),
            viewModel.uiState.value.statDescription,
        )

        viewModel.showDerivedStatDescription(DerivedStatType.ATTACK)
        advanceUntilIdle()
        assertEquals(
            StatDescriptionTarget.Derived(DerivedStatType.ATTACK),
            viewModel.uiState.value.statDescription,
        )

        viewModel.dismissStatDescription()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.statDescription)
    }

    @Test
    fun statIncreaseAndDecreaseOnlyChangeTheViewModelDraft() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.increaseStat(StatType.VITALITY)
        viewModel.increaseStat(StatType.VITALITY)
        viewModel.increaseStat(StatType.FOCUS)
        viewModel.increaseStat(StatType.STRENGTH)
        viewModel.increaseStat(StatType.WILLPOWER)
        advanceUntilIdle()
        assertTrue(repository.allocations.isEmpty())

        var state = viewModel.uiState.value
        assertEquals(0, state.remainingUnspentPoints)
        assertEquals(3, state.pendingStatPoints)
        assertTrue(state.hasPendingStatAllocation)
        with(state.baseStats.single { it.type == StatType.VITALITY }) {
            assertEquals(9, confirmedValue)
            assertEquals(2, pendingIncrease)
            assertEquals(11, expectedValue)
        }
        with(state.baseStats.single { it.type == StatType.FOCUS }) {
            assertEquals(8, confirmedValue)
            assertEquals(1, pendingIncrease)
            assertEquals(9, expectedValue)
        }
        with(state.baseStats.single { it.type == StatType.STRENGTH }) {
            assertEquals(60, confirmedValue)
            assertEquals(0, pendingIncrease)
            assertEquals(60, expectedValue)
        }
        assertEquals(
            "88",
            state.derivedStats.single { it.type == DerivedStatType.ATTACK }.displayValue,
        )
        assertEquals(155, state.currentHp)
        assertEquals(210, state.maxHp)

        viewModel.decreaseStat(StatType.VITALITY)
        viewModel.decreaseStat(StatType.WILLPOWER)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.remainingUnspentPoints)
        assertEquals(2, state.pendingStatPoints)
        with(state.baseStats.single { it.type == StatType.VITALITY }) {
            assertEquals(9, confirmedValue)
            assertEquals(1, pendingIncrease)
            assertEquals(10, expectedValue)
        }
        assertEquals(7, state.baseStats.single { it.type == StatType.WILLPOWER }.expectedValue)
        assertTrue(repository.allocations.isEmpty())
    }

    @Test
    fun saveCallsBatchUseCaseOnceAndClearsDraftOnSuccessOrNoChanges() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val allocation = StatAllocation(vitality = 1, focus = 1)

        viewModel.increaseStat(StatType.VITALITY)
        viewModel.increaseStat(StatType.FOCUS)
        repository.allocateResult = AllocateStatPointsResult.Success(allocation)
        viewModel.saveStatAllocation()
        viewModel.saveStatAllocation()
        advanceUntilIdle()

        assertEquals(listOf(allocation), repository.allocations)
        assertFalse(viewModel.uiState.value.hasPendingStatAllocation)
        assertFalse(viewModel.uiState.value.isSavingStatAllocation)
        assertEquals(0, viewModel.uiState.value.pendingStatPoints)
        assertEquals(3, viewModel.uiState.value.remainingUnspentPoints)
        assertNull(viewModel.uiState.value.error)

        viewModel.increaseStat(StatType.WILLPOWER)
        repository.allocateResult = AllocateStatPointsResult.NoChanges
        viewModel.saveStatAllocation()
        advanceUntilIdle()

        assertEquals(
            listOf(allocation, StatAllocation(willpower = 1)),
            repository.allocations,
        )
        assertFalse(viewModel.uiState.value.hasPendingStatAllocation)
        assertEquals(0, viewModel.uiState.value.pendingStatPoints)
    }

    @Test
    fun savingStateRejectsDuplicateSaveAndDraftOrResetChanges() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.allocateGate = gate
        repository.allocateResult =
            AllocateStatPointsResult.Success(StatAllocation(vitality = 1))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.increaseStat(StatType.VITALITY)
        viewModel.saveStatAllocation()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSavingStatAllocation)
        assertTrue(viewModel.uiState.value.hasPendingStatAllocation)
        assertEquals(listOf(StatAllocation(vitality = 1)), repository.allocations)

        viewModel.saveStatAllocation()
        viewModel.increaseStat(StatType.FOCUS)
        viewModel.decreaseStat(StatType.VITALITY)
        viewModel.requestStatReset()
        runCurrent()

        assertEquals(listOf(StatAllocation(vitality = 1)), repository.allocations)
        assertEquals(1, viewModel.uiState.value.pendingStatPoints)
        assertEquals(CharacterUiMessage.PendingStatAllocation, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.resetConfirmation)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingStatAllocation)
        assertFalse(viewModel.uiState.value.hasPendingStatAllocation)
    }

    @Test
    fun allocationValidationFailuresKeepDraftForAdjustmentAndRetry() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.increaseStat(StatType.VITALITY)

        repository.allocateResult = AllocateStatPointsResult.InsufficientPoints(
            requested = 1,
            available = 0,
        )
        viewModel.saveStatAllocation()
        advanceUntilIdle()

        assertEquals(StatAllocation(vitality = 1), repository.allocations.single())
        assertTrue(viewModel.uiState.value.hasPendingStatAllocation)
        assertEquals(1, viewModel.uiState.value.pendingStatPoints)
        assertEquals(
            CharacterUiMessage.NoUnspentStatPoints,
            viewModel.uiState.value.error,
        )

        repository.allocateResult = AllocateStatPointsResult.StatCap(StatType.STRENGTH)
        viewModel.saveStatAllocation()
        advanceUntilIdle()
        assertEquals(
            CharacterUiMessage.StatAtInvestmentCap(
                type = StatType.STRENGTH,
                investmentCap = 60,
            ),
            viewModel.uiState.value.error,
        )
        assertTrue(viewModel.uiState.value.hasPendingStatAllocation)

        repository.allocateFailure = IllegalStateException("internal allocation detail")
        viewModel.saveStatAllocation()
        advanceUntilIdle()
        assertEquals(CharacterUiMessage.AllocationFailed, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.hasPendingStatAllocation)
        assertFalse(viewModel.uiState.value.isSavingStatAllocation)
    }

    @Test
    fun pendingAllocationDisablesResetUntilDraftIsRemoved() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.increaseStat(StatType.VITALITY)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canReset)
        assertEquals(
            CharacterUiMessage.PendingStatAllocation,
            viewModel.uiState.value.resetUnavailableReason,
        )
        viewModel.requestStatReset()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.resetConfirmation)
        assertEquals(CharacterUiMessage.PendingStatAllocation, viewModel.uiState.value.error)

        viewModel.decreaseStat(StatType.VITALITY)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canReset)
        assertNull(viewModel.uiState.value.resetUnavailableReason)
    }

    @Test
    fun resetConfirmationDistinguishesFreeAndPaidReset() = runTest(dispatcher) {
        repository.snapshot.value = snapshot(hasUsedFreeStatReset = false)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestStatReset()
        advanceUntilIdle()

        val freeConfirmation = requireNotNull(viewModel.uiState.value.resetConfirmation)
        assertTrue(freeConfirmation.isFree)
        assertEquals(0L, freeConfirmation.costGold)

        viewModel.dismissStatResetConfirmation()
        repository.snapshot.value = snapshot(hasUsedFreeStatReset = true)
        advanceUntilIdle()
        viewModel.requestStatReset()
        advanceUntilIdle()

        val paidConfirmation = requireNotNull(viewModel.uiState.value.resetConfirmation)
        assertFalse(paidConfirmation.isFree)
        assertEquals(340L, paidConfirmation.costGold)
    }

    @Test
    fun resetResultsCloseDialogAndExposeNothingOrInsufficientGold() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestStatReset()
        repository.resetResult = StatResetResult.NothingToReset
        viewModel.confirmStatReset()
        advanceUntilIdle()
        assertEquals(1, repository.resetCalls)
        assertNull(viewModel.uiState.value.resetConfirmation)
        assertEquals(CharacterUiMessage.NothingToReset, viewModel.uiState.value.error)

        viewModel.requestStatReset()
        repository.resetResult = StatResetResult.InsufficientGold(
            requiredGold = 340L,
            availableGold = 100L,
        )
        viewModel.confirmStatReset()
        advanceUntilIdle()
        assertEquals(
            CharacterUiMessage.InsufficientGold(
                requiredGold = 340L,
                availableGold = 100L,
            ),
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun unavailableResetExplainsReasonWithoutCallingUseCase() = runTest(dispatcher) {
        repository.snapshot.value = snapshot(
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            unspentPoints = 22,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestStatReset()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canReset)
        assertEquals(
            CharacterUiMessage.NothingToReset,
            viewModel.uiState.value.resetUnavailableReason,
        )
        assertEquals(CharacterUiMessage.NothingToReset, viewModel.uiState.value.error)
        assertEquals(0, repository.resetCalls)
    }

    @Test
    fun caughtCommandExceptionsExposeGenericSemanticMessages() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        repository.allocateFailure = IllegalStateException("internal allocation detail")
        viewModel.increaseStat(StatType.FOCUS)
        viewModel.saveStatAllocation()
        advanceUntilIdle()
        assertEquals(CharacterUiMessage.AllocationFailed, viewModel.uiState.value.error)

        repository.allocateFailure = null
        viewModel.decreaseStat(StatType.FOCUS)
        advanceUntilIdle()
        viewModel.requestStatReset()
        repository.resetFailure = IllegalStateException("internal reset detail")
        viewModel.confirmStatReset()
        advanceUntilIdle()
        assertEquals(CharacterUiMessage.ResetFailed, viewModel.uiState.value.error)
    }

    @Test
    fun loadoutCommandsUseRepositoryAndExposeOneGenericFailureMessage() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val appearance = CharacterAppearance(CharacterLoadoutCatalog.HAIR_DEFAULT)
        val mixedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
            weaponId = null,
        )

        viewModel.updateAppearance(appearance)
        viewModel.updateEquippedItems(mixedItems)
        advanceUntilIdle()

        assertEquals(listOf(appearance), repository.updatedAppearances)
        assertEquals(listOf(mixedItems), repository.updatedEquippedItems)
        assertNull(viewModel.uiState.value.error)

        repository.loadoutResult = CharacterLoadoutUpdateResult.InvalidItem
        viewModel.updateEquippedItems(mixedItems)
        advanceUntilIdle()
        assertEquals(CharacterUiMessage.LoadoutUpdateFailed, viewModel.uiState.value.error)

        repository.loadoutFailure = IllegalStateException("internal loadout detail")
        viewModel.updateAppearance(appearance)
        advanceUntilIdle()
        assertEquals(CharacterUiMessage.LoadoutUpdateFailed, viewModel.uiState.value.error)
    }

    @Test
    fun loadExceptionDoesNotExposeItsRawMessage() = runTest(dispatcher) {
        repository.observeFailure = IllegalStateException("internal database detail")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(CharacterUiMessage.LoadFailed, viewModel.uiState.value.error)
    }

    private fun viewModel(
        prepareCharacterStatGuide: () -> Boolean = { false },
        acknowledgeCharacterStatGuide: () -> Boolean = { true },
    ) = CharacterViewModel(
        repository = repository,
        statusEffectRepository = statusEffectRepository,
        allocateStatPoints = AllocateStatPointsUseCase(repository),
        resetStats = ResetStatsUseCase(repository),
        clock = clock,
        dispatcher = dispatcher,
        prepareCharacterStatGuide = prepareCharacterStatGuide,
        acknowledgeCharacterStatGuide = acknowledgeCharacterStatGuide,
    )

    private class FakeCharacterRepository(initialSnapshot: CharacterSnapshot) : CharacterRepository {
        val snapshot = MutableStateFlow(initialSnapshot)
        val observedDates = mutableListOf<LocalDate>()
        val allocations = mutableListOf<StatAllocation>()
        var allocateResult: AllocateStatPointsResult =
            AllocateStatPointsResult.Success(StatAllocation(strength = 1))
        var resetResult: StatResetResult = StatResetResult.Success(goldSpent = 0L)
        var allocateFailure: Throwable? = null
        var allocateGate: CompletableDeferred<Unit>? = null
        var resetFailure: Throwable? = null
        var observeFailure: Throwable? = null
        var observeGate: CompletableDeferred<Unit>? = null
        var resetCalls: Int = 0
        val updatedAppearances = mutableListOf<CharacterAppearance>()
        val updatedEquippedItems = mutableListOf<EquippedItems>()
        var loadoutResult: CharacterLoadoutUpdateResult = CharacterLoadoutUpdateResult.Success
        var loadoutFailure: Throwable? = null

        override fun observeCharacter(referenceDate: LocalDate): Flow<CharacterSnapshot> {
            observedDates += referenceDate
            val failure = observeFailure
            return when {
                failure != null -> flow { throw failure }
                observeGate != null -> flow {
                    observeGate?.await()
                    snapshot.collect { emit(it) }
                }
                else -> snapshot
            }
        }

        override suspend fun updateAppearance(
            appearance: CharacterAppearance,
        ): CharacterLoadoutUpdateResult {
            updatedAppearances += appearance
            loadoutFailure?.let { throw it }
            return loadoutResult
        }

        override suspend fun updateEquippedItems(
            items: EquippedItems,
        ): CharacterLoadoutUpdateResult {
            updatedEquippedItems += items
            loadoutFailure?.let { throw it }
            return loadoutResult
        }

        override suspend fun allocateStatPoints(
            allocation: StatAllocation,
        ): AllocateStatPointsResult {
            allocations += allocation
            allocateGate?.await()
            allocateFailure?.let { throw it }
            return allocateResult
        }

        override suspend fun resetStats(): StatResetResult {
            resetCalls += 1
            resetFailure?.let { throw it }
            return resetResult
        }
    }

    private class FakeStatusEffectRepository(
        private val clock: AppClock,
    ) : StatusEffectRepository {
        val effects = MutableStateFlow<List<CharacterStatusEffect>>(emptyList())
        var reconcileCalls = 0

        override fun observeActiveStatusEffects(
            characterId: Long,
        ): Flow<List<CharacterStatusEffect>> = effects

        override fun observeRemovalEvents(
            characterId: Long,
        ) = emptyFlow<com.todoquest.domain.model.CombatLifecycleEvent.StatusEffectRemoved>()

        override suspend fun reconcileExpired(characterId: Long): Int {
            reconcileCalls += 1
            val expired = effects.value.count {
                it.expiresAtEpochMillis <= clock.now().toEpochMilli()
            }
            effects.value = effects.value.filter {
                it.expiresAtEpochMillis > clock.now().toEpochMilli()
            }
            return expired
        }

        override suspend fun removeStatusEffect(
            characterId: Long,
            type: StatusEffectType,
            revision: Long,
            mutationId: String,
        ): Boolean = false
    }

    private class MutableClock(var currentDate: LocalDate) : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        var currentInstant: Instant = currentDate.atStartOfDay(zoneId).toInstant()
        override fun now(): Instant = currentInstant
        override fun today(): LocalDate = currentDate
    }

    companion object {
        private fun severeInjury(
            expiresAt: Instant,
            remainingCompletions: Int = 3,
            revision: Long = 1L,
        ) = CharacterStatusEffect(
            characterId = 1L,
            type = StatusEffectType.SEVERE_INJURY,
            definitionVersion = 1,
            appliedAtEpochMillis = expiresAt.minusSeconds(24 * 60 * 60).toEpochMilli(),
            expiresAtEpochMillis = expiresAt.toEpochMilli(),
            remainingRecoveryCompletions = remainingCompletions,
            active = true,
            revision = revision,
            lastMutationId = "status-effect:test",
        )

        private fun snapshot(
            level: Int = 12,
            totalXp: Long = 1_145L,
            xpIntoCurrentLevel: Long = 45L,
            isMaxLevel: Boolean = false,
            hasUsedFreeStatReset: Boolean = true,
            baseStats: CharacterBaseStats = CharacterBaseStats(60, 9, 8, 7),
            unspentPoints: Int = 3,
        ): CharacterSnapshot = CharacterSnapshot(
            character = PlayerCharacter(
                id = 1L,
                totalXp = totalXp,
                currentGold = 620L,
                baseStats = baseStats,
                unspentStatPoints = unspentPoints,
                hasUsedFreeStatReset = hasUsedFreeStatReset,
            ),
            appearance = CharacterLoadoutCatalog.defaultAppearance,
            equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
            level = level,
            xpIntoCurrentLevel = xpIntoCurrentLevel,
            xpRequiredForNextLevel = 100L,
            isMaxLevel = isMaxLevel,
            currentState = CharacterCurrentState(
                characterId = 1L,
                currentHp = 155,
                balanceVersion = 1,
                updatedAtEpochMillis = 0L,
            ),
            derivedStats = DerivedStats(
                maxHp = 210,
                attack = 88,
                defense = 31,
                criticalChanceBp = 755,
                criticalDamageBp = 15_654,
                statusResistanceBp = 1_245,
                hpRecovery = 14,
                goldGainBonusBp = 55,
            ),
            currentStreak = 7,
            momentumBonusBp = 505,
        )
    }
}
