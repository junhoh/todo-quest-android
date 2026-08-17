package com.todoquest.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.core.AppClock
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.usecase.AllocateStatPointsUseCase
import com.todoquest.domain.usecase.ResetStatsUseCase
import com.todoquest.domain.usecase.StatResetPolicy
import com.todoquest.feature.battle.ActiveStatusEffectUiModel
import com.todoquest.feature.battle.toActiveStatusEffectUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterViewModel(
    private val repository: CharacterRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val allocateStatPoints: AllocateStatPointsUseCase,
    private val resetStats: ResetStatsUseCase,
    private val clock: AppClock,
    private val balanceConfig: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val characterId: Long = 1L,
    private val prepareCharacterStatGuide: () -> Boolean = { false },
    private val acknowledgeCharacterStatGuide: () -> Boolean = { true },
) : ViewModel() {
    private val referenceDate = MutableStateFlow(clock.today())
    private val statusReferenceTime = MutableStateFlow(clock.now())
    private val commandState = MutableStateFlow(CharacterCommandUiState())

    private val characterState: Flow<CharacterLoadState> = referenceDate
        .flatMapLatest { date ->
            repository.observeCharacter(date)
                .map<CharacterSnapshot, CharacterLoadState>(CharacterLoadState::Loaded)
                .onStart { emit(CharacterLoadState.Loading) }
                .catch { emit(CharacterLoadState.Failed) }
        }

    private val activeStatusEffects: Flow<List<ActiveStatusEffectUiModel>> = combine(
        statusEffectRepository.observeActiveStatusEffects(characterId)
            .catch { emit(emptyList()) },
        statusReferenceTime,
    ) { effects, now ->
        effects.filter { it.isEffectiveAt(now) }
            .map { it.toActiveStatusEffectUiModel(now) }
    }.catch { emit(emptyList()) }

    val uiState: StateFlow<CharacterUiState> = combine(
        characterState,
        commandState,
        activeStatusEffects,
    ) { loadState, command, statusEffects ->
        when (loadState) {
            CharacterLoadState.Loading -> CharacterUiState(
                pendingStatPoints = command.statAllocationDraft.totalPoints,
                hasPendingStatAllocation = command.statAllocationDraft.totalPoints > 0,
                isSavingStatAllocation = command.isSavingStatAllocation,
                resetConfirmation = command.resetConfirmation,
                statDescription = command.statDescription,
                isStatAllocationGuideVisible =
                    command.statAllocationGuideOrigin == CharacterStatGuideOrigin.MANUAL,
                error = command.error,
                activeStatusEffects = statusEffects,
                selectedStatusEffect = statusEffects.selectedBy(command.selectedStatusEffectKey),
            )

            CharacterLoadState.Failed -> CharacterUiState(
                isLoading = false,
                pendingStatPoints = command.statAllocationDraft.totalPoints,
                hasPendingStatAllocation = command.statAllocationDraft.totalPoints > 0,
                isSavingStatAllocation = command.isSavingStatAllocation,
                resetConfirmation = command.resetConfirmation,
                statDescription = command.statDescription,
                isStatAllocationGuideVisible =
                    command.statAllocationGuideOrigin == CharacterStatGuideOrigin.MANUAL,
                error = command.error ?: CharacterUiMessage.LoadFailed,
                activeStatusEffects = statusEffects,
                selectedStatusEffect = statusEffects.selectedBy(command.selectedStatusEffectKey),
            )

            is CharacterLoadState.Loaded -> loadState.snapshot.toUiState(
                balanceConfig = balanceConfig,
                command = command,
                activeStatusEffects = statusEffects,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CharacterUiState(),
    )

    fun onScreenEntered() {
        refreshReferenceDate()
        reconcileStatusEffects()
        prepareStatAllocationGuideOnce()
    }

    fun onLifecycleResumed() {
        refreshReferenceDate()
        reconcileStatusEffects()
    }

    fun increaseStat(statType: StatType) {
        val command = commandState.value
        val state = uiState.value
        val stat = state.baseStats.firstOrNull { it.type == statType } ?: return
        val confirmedUnspentPoints = state.remainingUnspentPoints + state.pendingStatPoints
        if (
            command.isSavingStatAllocation ||
            command.statAllocationDraft.totalPoints >= confirmedUnspentPoints ||
            stat.confirmedValue + command.statAllocationDraft.valueOf(statType) >=
            balanceConfig.investedBaseStatMax
        ) {
            return
        }

        commandState.value = command.copy(
            statAllocationDraft = command.statAllocationDraft.adjust(statType, delta = 1),
            resetConfirmation = null,
            error = null,
        )
    }

    fun decreaseStat(statType: StatType) {
        val command = commandState.value
        if (
            command.isSavingStatAllocation ||
            command.statAllocationDraft.valueOf(statType) <= 0
        ) {
            return
        }

        commandState.value = command.copy(
            statAllocationDraft = command.statAllocationDraft.adjust(statType, delta = -1),
            resetConfirmation = null,
            error = null,
        )
    }

    fun saveStatAllocation() {
        val command = commandState.value
        if (command.isSavingStatAllocation || command.statAllocationDraft.totalPoints == 0) return

        val allocation = command.statAllocationDraft
        commandState.value = command.copy(
            isSavingStatAllocation = true,
            resetConfirmation = null,
            error = null,
        )
        viewModelScope.launch(dispatcher) {
            runCatching {
                allocateStatPoints.invoke(allocation)
            }.onSuccess { result ->
                commandState.value = when (result) {
                    is AllocateStatPointsResult.Success,
                    AllocateStatPointsResult.NoChanges,
                    -> commandState.value.copy(
                        statAllocationDraft = StatAllocation(),
                        isSavingStatAllocation = false,
                        error = null,
                    )

                    is AllocateStatPointsResult.InsufficientPoints ->
                        commandState.value.copy(
                            isSavingStatAllocation = false,
                            error = CharacterUiMessage.NoUnspentStatPoints,
                        )

                    is AllocateStatPointsResult.StatCap ->
                        commandState.value.copy(
                            isSavingStatAllocation = false,
                            error = CharacterUiMessage.StatAtInvestmentCap(
                                type = result.type,
                                investmentCap = balanceConfig.investedBaseStatMax,
                            ),
                        )
                }
            }.onFailure {
                commandState.value = commandState.value.copy(
                    isSavingStatAllocation = false,
                    error = CharacterUiMessage.AllocationFailed,
                )
            }
        }
    }

    fun updateAppearance(appearance: CharacterAppearance) {
        viewModelScope.launch(dispatcher) {
            runCatching {
                repository.updateAppearance(appearance)
            }.onSuccess { result ->
                commandState.value = commandState.value.copy(
                    error = result.toLoadoutError(),
                )
            }.onFailure {
                commandState.value = commandState.value.copy(
                    error = CharacterUiMessage.LoadoutUpdateFailed,
                )
            }
        }
    }

    fun updateEquippedItems(items: EquippedItems) {
        viewModelScope.launch(dispatcher) {
            runCatching {
                repository.updateEquippedItems(items)
            }.onSuccess { result ->
                commandState.value = commandState.value.copy(
                    error = result.toLoadoutError(),
                )
            }.onFailure {
                commandState.value = commandState.value.copy(
                    error = CharacterUiMessage.LoadoutUpdateFailed,
                )
            }
        }
    }

    fun requestStatReset() {
        val command = commandState.value
        if (command.isSavingStatAllocation || command.statAllocationDraft.totalPoints > 0) {
            commandState.value = command.copy(
                resetConfirmation = null,
                error = CharacterUiMessage.PendingStatAllocation,
            )
            return
        }

        val state = uiState.value
        if (state.isLoading) return

        if (!state.canReset) {
            commandState.value = commandState.value.copy(
                resetConfirmation = null,
                error = state.resetUnavailableReason ?: CharacterUiMessage.ResetUnavailable,
            )
            return
        }

        commandState.value = commandState.value.copy(
            resetConfirmation = ResetConfirmationUiState(
                isFree = state.isResetFree,
                costGold = state.resetCostGold,
            ),
            error = null,
        )
    }

    fun dismissStatResetConfirmation() {
        commandState.value = commandState.value.copy(resetConfirmation = null)
    }

    fun showBaseStatDescription(statType: StatType) {
        commandState.value = commandState.value.copy(
            statDescription = StatDescriptionTarget.Base(statType),
        )
    }

    fun showDerivedStatDescription(statType: DerivedStatType) {
        commandState.value = commandState.value.copy(
            statDescription = StatDescriptionTarget.Derived(statType),
        )
    }

    fun dismissStatDescription() {
        commandState.value = commandState.value.copy(statDescription = null)
    }

    fun showStatAllocationGuide() {
        commandState.value = commandState.value.copy(
            statAllocationGuideOrigin = CharacterStatGuideOrigin.MANUAL,
        )
    }

    fun dismissStatAllocationGuide() {
        val origin = commandState.value.statAllocationGuideOrigin
        commandState.value = commandState.value.copy(statAllocationGuideOrigin = null)
        if (origin != CharacterStatGuideOrigin.AUTOMATIC) return

        try {
            acknowledgeCharacterStatGuide()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The guide preference is best-effort and does not block character commands.
        }
    }

    fun showStatusEffectDetails(type: StatusEffectType) {
        val effect = uiState.value.activeStatusEffects.firstOrNull { it.type == type } ?: return
        commandState.value = commandState.value.copy(
            selectedStatusEffectKey = SelectedStatusEffectKey(
                type = effect.type,
                revision = effect.revision,
            ),
        )
    }

    fun dismissStatusEffectDetails() {
        commandState.value = commandState.value.copy(selectedStatusEffectKey = null)
    }

    fun confirmStatReset() {
        if (
            commandState.value.resetConfirmation == null ||
            commandState.value.isSavingStatAllocation ||
            commandState.value.statAllocationDraft.totalPoints > 0
        ) {
            return
        }

        viewModelScope.launch(dispatcher) {
            runCatching {
                resetStats.invoke()
            }.onSuccess { result ->
                commandState.value = CharacterCommandUiState(
                    hasPreparedStatAllocationGuide =
                        commandState.value.hasPreparedStatAllocationGuide,
                    statAllocationGuideOrigin = commandState.value.statAllocationGuideOrigin,
                    error = when (result) {
                        is StatResetResult.Success -> null
                        StatResetResult.NothingToReset -> CharacterUiMessage.NothingToReset
                        is StatResetResult.InsufficientGold ->
                            CharacterUiMessage.InsufficientGold(
                                requiredGold = result.requiredGold,
                                availableGold = result.availableGold,
                            )
                    },
                )
            }.onFailure {
                commandState.value = CharacterCommandUiState(
                    hasPreparedStatAllocationGuide =
                        commandState.value.hasPreparedStatAllocationGuide,
                    statAllocationGuideOrigin = commandState.value.statAllocationGuideOrigin,
                    error = CharacterUiMessage.ResetFailed,
                )
            }
        }
    }

    fun dismissError() {
        commandState.value = commandState.value.copy(error = null)
    }

    private fun refreshReferenceDate() {
        referenceDate.value = clock.today()
    }

    private fun prepareStatAllocationGuideOnce() {
        val command = commandState.value
        if (command.hasPreparedStatAllocationGuide) return

        commandState.value = command.copy(hasPreparedStatAllocationGuide = true)
        val shouldShow = try {
            prepareCharacterStatGuide()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (shouldShow) {
            commandState.value = commandState.value.copy(
                statAllocationGuideOrigin = CharacterStatGuideOrigin.AUTOMATIC,
            )
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
                // Expiration recovery is retried on the next entry/resume boundary.
            }
        }
    }
}

private data class CharacterCommandUiState(
    val statAllocationDraft: StatAllocation = StatAllocation(),
    val isSavingStatAllocation: Boolean = false,
    val resetConfirmation: ResetConfirmationUiState? = null,
    val statDescription: StatDescriptionTarget? = null,
    val selectedStatusEffectKey: SelectedStatusEffectKey? = null,
    val hasPreparedStatAllocationGuide: Boolean = false,
    val statAllocationGuideOrigin: CharacterStatGuideOrigin? = null,
    val error: CharacterUiMessage? = null,
)

private enum class CharacterStatGuideOrigin {
    AUTOMATIC,
    MANUAL,
}

private data class SelectedStatusEffectKey(
    val type: StatusEffectType,
    val revision: Long,
)

private sealed interface CharacterLoadState {
    data object Loading : CharacterLoadState

    data class Loaded(val snapshot: CharacterSnapshot) : CharacterLoadState

    data object Failed : CharacterLoadState
}

private fun CharacterSnapshot.toUiState(
    balanceConfig: CharacterStatBalanceConfig,
    command: CharacterCommandUiState,
    activeStatusEffects: List<ActiveStatusEffectUiModel>,
): CharacterUiState {
    val resetCost = if (character.hasUsedFreeStatReset) {
        StatResetPolicy.resetCost(level, balanceConfig)
    } else {
        0L
    }
    val hasAllocatedPoints = with(character.baseStats) {
        strength > balanceConfig.initialBaseStat ||
            vitality > balanceConfig.initialBaseStat ||
            focus > balanceConfig.initialBaseStat ||
            willpower > balanceConfig.initialBaseStat
    }
    val pendingStatPoints = command.statAllocationDraft.totalPoints
    val resetUnavailableReason = when {
        pendingStatPoints > 0 || command.isSavingStatAllocation ->
            CharacterUiMessage.PendingStatAllocation
        !hasAllocatedPoints -> CharacterUiMessage.NothingToReset
        character.currentGold < resetCost ->
            CharacterUiMessage.InsufficientGold(
                requiredGold = resetCost,
                availableGold = character.currentGold,
            )
        else -> null
    }

    return CharacterUiState(
        isLoading = false,
        level = level,
        isMaxLevel = isMaxLevel,
        totalXp = character.totalXp,
        xpIntoCurrentLevel = xpIntoCurrentLevel,
        xpRequiredForNextLevel = xpRequiredForNextLevel,
        xpProgress = if (isMaxLevel) {
            1f
        } else {
            (xpIntoCurrentLevel.toFloat() / xpRequiredForNextLevel.coerceAtLeast(1L))
                .coerceIn(0f, 1f)
        },
        gold = character.currentGold,
        currentHp = currentState.currentHp,
        maxHp = derivedStats.maxHp,
        streakDays = currentStreak,
        momentumBonus = formatBasisPoints(momentumBonusBp),
        appearance = appearance,
        equippedItems = equippedItems,
        remainingUnspentPoints = (character.unspentStatPoints - pendingStatPoints).coerceAtLeast(0),
        pendingStatPoints = pendingStatPoints,
        hasPendingStatAllocation = pendingStatPoints > 0,
        isSavingStatAllocation = command.isSavingStatAllocation,
        baseStats = with(character.baseStats) {
            listOf(
                StatType.STRENGTH.toUiState(strength, command.statAllocationDraft),
                StatType.VITALITY.toUiState(vitality, command.statAllocationDraft),
                StatType.FOCUS.toUiState(focus, command.statAllocationDraft),
                StatType.WILLPOWER.toUiState(willpower, command.statAllocationDraft),
            )
        },
        derivedStats = with(derivedStats) {
            listOf(
                DerivedStatUiState(DerivedStatType.MAX_HP, maxHp.toString()),
                DerivedStatUiState(DerivedStatType.ATTACK, attack.toString()),
                DerivedStatUiState(DerivedStatType.DEFENSE, defense.toString()),
                DerivedStatUiState(
                    DerivedStatType.CRITICAL_CHANCE,
                    formatBasisPoints(criticalChanceBp),
                ),
                DerivedStatUiState(
                    DerivedStatType.CRITICAL_DAMAGE,
                    formatBasisPoints(criticalDamageBp),
                ),
                DerivedStatUiState(
                    DerivedStatType.STATUS_RESISTANCE,
                    formatBasisPoints(statusResistanceBp),
                ),
                DerivedStatUiState(DerivedStatType.HP_RECOVERY, hpRecovery.toString()),
                DerivedStatUiState(
                    DerivedStatType.GOLD_GAIN_BONUS,
                    formatBasisPoints(goldGainBonusBp),
                ),
            )
        },
        isResetFree = !character.hasUsedFreeStatReset,
        resetCostGold = resetCost,
        canReset = resetUnavailableReason == null,
        resetUnavailableReason = resetUnavailableReason,
        resetConfirmation = command.resetConfirmation,
        statDescription = command.statDescription,
        isStatAllocationGuideVisible = command.statAllocationGuideOrigin != null,
        selectedStatusEffect = activeStatusEffects.selectedBy(command.selectedStatusEffectKey),
        error = command.error,
        activeStatusEffects = activeStatusEffects,
    )
}

private fun List<ActiveStatusEffectUiModel>.selectedBy(
    key: SelectedStatusEffectKey?,
): ActiveStatusEffectUiModel? = firstOrNull {
    key != null && it.type == key.type && it.revision == key.revision
}

private fun CharacterLoadoutUpdateResult.toLoadoutError(): CharacterUiMessage? = when (this) {
    CharacterLoadoutUpdateResult.Success -> null
    CharacterLoadoutUpdateResult.InvalidItem -> CharacterUiMessage.LoadoutUpdateFailed
}

private fun StatType.toUiState(
    confirmedValue: Int,
    allocation: StatAllocation,
): BaseStatUiState {
    val pendingIncrease = allocation.valueOf(this)
    return BaseStatUiState(
        type = this,
        confirmedValue = confirmedValue,
        pendingIncrease = pendingIncrease,
        expectedValue = confirmedValue + pendingIncrease,
    )
}

private fun StatAllocation.adjust(type: StatType, delta: Int): StatAllocation = when (type) {
    StatType.STRENGTH -> copy(strength = strength + delta)
    StatType.VITALITY -> copy(vitality = vitality + delta)
    StatType.FOCUS -> copy(focus = focus + delta)
    StatType.WILLPOWER -> copy(willpower = willpower + delta)
}

private fun formatBasisPoints(basisPoints: Int): String {
    val sign = if (basisPoints < 0) "-" else ""
    val roundedTenths = (kotlin.math.abs(basisPoints.toLong()) + 5L) / 10L
    return "$sign${roundedTenths / 10L}.${roundedTenths % 10L}%"
}
