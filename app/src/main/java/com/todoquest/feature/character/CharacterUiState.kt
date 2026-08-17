package com.todoquest.feature.character

import androidx.compose.runtime.Immutable
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.StatType
import com.todoquest.feature.battle.ActiveStatusEffectUiModel

@Immutable
data class CharacterUiState(
    val isLoading: Boolean = true,
    val level: Int = 1,
    val isMaxLevel: Boolean = false,
    val totalXp: Long = 0L,
    val xpIntoCurrentLevel: Long = 0L,
    val xpRequiredForNextLevel: Long = 100L,
    val xpProgress: Float = 0f,
    val gold: Long = 0L,
    val currentHp: Int = 0,
    val maxHp: Int = 0,
    val streakDays: Int = 0,
    val momentumBonus: String = "0.0%",
    val activeStatusEffects: List<ActiveStatusEffectUiModel> = emptyList(),
    val selectedStatusEffect: ActiveStatusEffectUiModel? = null,
    val appearance: CharacterAppearance = CharacterLoadoutCatalog.defaultAppearance,
    val equippedItems: EquippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
    val remainingUnspentPoints: Int = 0,
    val pendingStatPoints: Int = 0,
    val hasPendingStatAllocation: Boolean = false,
    val isSavingStatAllocation: Boolean = false,
    val baseStats: List<BaseStatUiState> = emptyList(),
    val derivedStats: List<DerivedStatUiState> = emptyList(),
    val isResetFree: Boolean = true,
    val resetCostGold: Long = 0L,
    val canReset: Boolean = false,
    val resetUnavailableReason: CharacterUiMessage? = null,
    val resetConfirmation: ResetConfirmationUiState? = null,
    val statDescription: StatDescriptionTarget? = null,
    val isStatAllocationGuideVisible: Boolean = false,
    val error: CharacterUiMessage? = null,
)

@Immutable
sealed interface StatDescriptionTarget {
    data class Base(val type: StatType) : StatDescriptionTarget

    data class Derived(val type: DerivedStatType) : StatDescriptionTarget
}

@Immutable
data class BaseStatUiState(
    val type: StatType,
    val confirmedValue: Int,
    val pendingIncrease: Int,
    val expectedValue: Int,
)

@Immutable
data class DerivedStatUiState(
    val type: DerivedStatType,
    val displayValue: String,
)

@Immutable
sealed interface CharacterUiMessage {
    data object LoadFailed : CharacterUiMessage

    data object NoUnspentStatPoints : CharacterUiMessage

    data class StatAtInvestmentCap(
        val type: StatType,
        val investmentCap: Int,
    ) : CharacterUiMessage

    data object AllocationFailed : CharacterUiMessage

    data object NothingToReset : CharacterUiMessage

    data class InsufficientGold(
        val requiredGold: Long,
        val availableGold: Long,
    ) : CharacterUiMessage

    data object ResetUnavailable : CharacterUiMessage

    data object PendingStatAllocation : CharacterUiMessage

    data object ResetFailed : CharacterUiMessage

    data object LoadoutUpdateFailed : CharacterUiMessage
}

@Immutable
data class ResetConfirmationUiState(
    val isFree: Boolean,
    val costGold: Long,
)
