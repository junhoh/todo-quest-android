package com.todoquest.feature.shop

import androidx.compose.runtime.Immutable
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.WeaponType

@Immutable
data class InventoryUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryEquipmentUiModel> = emptyList(),
    val equippedBySlot: Map<EquipmentSlot, InventoryEquipmentUiModel> = emptyMap(),
    val selectedOwnedEquipmentId: Long? = null,
    val processingState: InventoryProcessingState = InventoryProcessingState.Idle,
    val processingOwnedEquipmentId: Long? =
        (processingState as? InventoryProcessingState.Equipping)?.ownedEquipmentId,
    val equipResult: InventoryEquipResult? = null,
    val unequipResult: InventoryUnequipResult? = null,
    val error: InventoryError? = null,
    val retryState: InventoryRetryState? = null,
)

@Immutable
sealed interface InventoryProcessingState {
    data object Idle : InventoryProcessingState

    @Immutable
    data class Equipping(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
    ) : InventoryProcessingState

    @Immutable
    data class Unequipping(val slot: EquipmentSlot) : InventoryProcessingState
}

@Immutable
data class InventoryEquipmentUiModel(
    val ownedEquipmentId: Long,
    val equipmentId: Long,
    val nameKey: String,
    val descriptionKey: String,
    val type: EquipmentType,
    val slot: EquipmentSlot,
    val rarity: EquipmentRarity,
    val modifiers: List<EquipmentStatModifier>,
    val comparisons: List<EquipmentStatComparison>,
    val imageKey: String?,
    val acquiredAtEpochMillis: Long,
    val isEquipped: Boolean,
    val weaponType: WeaponType? = null,
)

@Immutable
sealed interface InventoryEquipResult {
    @Immutable
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : InventoryEquipResult
}

@Immutable
sealed interface InventoryUnequipResult {
    @Immutable
    data class Success(
        val slot: EquipmentSlot,
        val changed: Boolean,
    ) : InventoryUnequipResult

    @Immutable
    data class Failed(val slot: EquipmentSlot) : InventoryUnequipResult
}

@Immutable
sealed interface InventoryError {
    data object LoadFailed : InventoryError

    @Immutable
    data class OwnedEquipmentNotFound(val ownedEquipmentId: Long) : InventoryError

    @Immutable
    data class NotOwnedByCharacter(val ownedEquipmentId: Long) : InventoryError

    @Immutable
    data class SlotMismatch(
        val ownedEquipmentId: Long,
        val type: EquipmentType,
        val equipmentSlot: EquipmentSlot,
        val targetSlot: EquipmentSlot,
    ) : InventoryError

    data object EquipFailed : InventoryError
}

@Immutable
sealed interface InventoryRetryState {
    data object Load : InventoryRetryState

    @Immutable
    data class Equip(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
    ) : InventoryRetryState

    @Immutable
    data class Unequip(val slot: EquipmentSlot) : InventoryRetryState
}

@Immutable
sealed interface InventoryEvent {
    @Immutable
    data class SelectOwnedEquipment(val ownedEquipmentId: Long?) : InventoryEvent

    data object EquipSelected : InventoryEvent

    @Immutable
    data class UnequipSlot(val slot: EquipmentSlot) : InventoryEvent

    data object Retry : InventoryEvent

    data object ConsumeError : InventoryEvent

    data object ConsumeEquipResult : InventoryEvent

    data object ConsumeUnequipResult : InventoryEvent
}
