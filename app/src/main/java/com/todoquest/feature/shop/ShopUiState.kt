package com.todoquest.feature.shop

import androidx.compose.runtime.Immutable
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.model.toEquipmentType

@Immutable
data class ShopUiState(
    val isLoading: Boolean = true,
    val currentGold: Long = 0L,
    val characterLevel: Int = 1,
    val characterAppearance: CharacterAppearance = CharacterLoadoutCatalog.defaultAppearance,
    val characterEquippedItems: EquippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
    val equipmentSlots: List<ShopEquipmentSlotUiModel> = emptyShopEquipmentSlots(),
    val statSummary: CharacterStatSummaryUiModel = CharacterStatSummaryUiModel(
        attack = CharacterStatValueUiModel(currentValue = 0, difference = 0),
        maxHp = CharacterStatValueUiModel(currentValue = 0, difference = 0),
        defense = CharacterStatValueUiModel(currentValue = 0, difference = 0),
    ),
    val selectedCategory: EquipmentType? = null,
    val managedSlot: EquipmentSlot? = null,
    val items: List<ShopEquipmentUiModel> = emptyList(),
    val selectedEquipmentId: Long? = null,
    val selectedDetail: ShopEquipmentUiModel? = null,
    val purchaseConfirmation: PurchaseConfirmationUiState? = null,
    val purchaseState: PurchaseState = PurchaseState.Idle,
    val equipState: ShopEquipState = ShopEquipState.Idle,
    val unequipState: ShopUnequipState = ShopUnequipState.Idle,
    val error: ShopError? = null,
    val retryState: ShopRetryState? = null,
)

@Immutable
data class ShopEquipmentSlotUiModel(
    val slot: EquipmentSlot,
    val type: EquipmentType,
    val equipmentId: Long?,
    val nameKey: String?,
    val rarity: EquipmentRarity?,
    val imageKey: String?,
    val isEquipped: Boolean,
    val isSelected: Boolean,
    val weaponType: WeaponType? = null,
)

@Immutable
data class CharacterStatSummaryUiModel(
    val attack: CharacterStatValueUiModel,
    val maxHp: CharacterStatValueUiModel,
    val defense: CharacterStatValueUiModel,
)

@Immutable
data class CharacterStatValueUiModel(
    val currentValue: Int,
    val difference: Int,
)

internal fun emptyShopEquipmentSlots(
    selectedCategory: EquipmentType? = null,
): List<ShopEquipmentSlotUiModel> = EquipmentSlot.entries.map { slot ->
    val type = slot.toEquipmentType()
    ShopEquipmentSlotUiModel(
        slot = slot,
        type = type,
        equipmentId = null,
        nameKey = null,
        rarity = null,
        imageKey = null,
        isEquipped = false,
        isSelected = selectedCategory == type,
    )
}

@Immutable
data class ShopEquipmentUiModel(
    val equipmentId: Long,
    val nameKey: String,
    val descriptionKey: String,
    val type: EquipmentType,
    val slot: EquipmentSlot,
    val rarity: EquipmentRarity,
    val price: Long,
    val requiredLevel: Int,
    val modifiers: List<EquipmentStatModifier>,
    val comparisons: List<EquipmentStatComparison>,
    val imageKey: String?,
    val isForSale: Boolean,
    val isOwned: Boolean,
    val isEquipped: Boolean,
    val purchaseAvailability: PurchaseAvailability,
    val action: ShopEquipmentAction,
    val isRequiredLevelMet: Boolean = true,
    val weaponType: WeaponType? = null,
)

enum class ShopEquipmentActionLabelKey {
    PURCHASE,
    PURCHASE_UNAVAILABLE,
    EQUIP,
    UNEQUIP,
}

@Immutable
sealed interface ShopEquipmentAction {
    val labelKey: ShopEquipmentActionLabelKey
    val isEnabled: Boolean
        get() = this !is PurchaseUnavailable

    @Immutable
    data class Purchase(val equipmentId: Long) : ShopEquipmentAction {
        override val labelKey: ShopEquipmentActionLabelKey = ShopEquipmentActionLabelKey.PURCHASE
    }

    @Immutable
    data class PurchaseUnavailable(
        val reason: PurchaseUnavailableReason,
    ) : ShopEquipmentAction {
        override val labelKey: ShopEquipmentActionLabelKey =
            ShopEquipmentActionLabelKey.PURCHASE_UNAVAILABLE
    }

    @Immutable
    data class Equip(
        val ownedEquipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopEquipmentAction {
        override val labelKey: ShopEquipmentActionLabelKey = ShopEquipmentActionLabelKey.EQUIP
    }

    @Immutable
    data class Unequip(
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopEquipmentAction {
        override val labelKey: ShopEquipmentActionLabelKey = ShopEquipmentActionLabelKey.UNEQUIP
    }
}

@Immutable
sealed interface PurchaseAvailability {
    data object Available : PurchaseAvailability

    @Immutable
    data class Unavailable(val reason: PurchaseUnavailableReason) : PurchaseAvailability
}

@Immutable
sealed interface PurchaseUnavailableReason {
    @Immutable
    data class UnsupportedSlot(
        val type: EquipmentType,
        val slot: EquipmentSlot,
    ) : PurchaseUnavailableReason

    data object NotForSale : PurchaseUnavailableReason

    data object AlreadyOwned : PurchaseUnavailableReason

    @Immutable
    data class LevelTooLow(
        val requiredLevel: Int,
        val characterLevel: Int,
    ) : PurchaseUnavailableReason

    @Immutable
    data class InsufficientGold(
        val price: Long,
        val availableGold: Long,
    ) : PurchaseUnavailableReason
}

@Immutable
data class PurchaseConfirmationUiState(
    val equipmentId: Long,
    val equipmentNameKey: String,
    val type: EquipmentType,
    val slot: EquipmentSlot,
    val price: Long,
    val currentGold: Long,
    val expectedRemainingGold: Long,
)

@Immutable
sealed interface PurchaseState {
    data object Idle : PurchaseState

    @Immutable
    data class Processing(val equipmentId: Long) : PurchaseState

    @Immutable
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val equipmentNameKey: String,
        val type: EquipmentType,
        val slot: EquipmentSlot,
        val currentGold: Long,
    ) : PurchaseState

    @Immutable
    data class Unavailable(
        val equipmentId: Long,
        val reason: PurchaseUnavailableReason,
    ) : PurchaseState

    @Immutable
    data class Failed(val equipmentId: Long) : PurchaseState
}

@Immutable
sealed interface ShopEquipState {
    data object Idle : ShopEquipState

    @Immutable
    data class Processing(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
    ) : ShopEquipState

    @Immutable
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopEquipState

    @Immutable
    data class Failed(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
        val reason: EquipFailure,
    ) : ShopEquipState
}

@Immutable
sealed interface ShopUnequipState {
    data object Idle : ShopUnequipState

    @Immutable
    data class Processing(
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopUnequipState

    @Immutable
    data class Success(
        val equipmentId: Long,
        val slot: EquipmentSlot,
        val changed: Boolean,
    ) : ShopUnequipState

    @Immutable
    data class Failed(
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopUnequipState
}

@Immutable
sealed interface EquipFailure {
    data object OwnedEquipmentNotFound : EquipFailure

    data object NotOwnedByCharacter : EquipFailure

    @Immutable
    data class SlotMismatch(
        val type: EquipmentType,
        val equipmentSlot: EquipmentSlot,
        val targetSlot: EquipmentSlot,
    ) : EquipFailure

    data object CommandFailed : EquipFailure
}

@Immutable
sealed interface ShopError {
    data object LoadFailed : ShopError
}

@Immutable
sealed interface ShopRetryState {
    data object Load : ShopRetryState

    @Immutable
    data class Purchase(val equipmentId: Long) : ShopRetryState

    @Immutable
    data class Equip(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
    ) : ShopRetryState

    @Immutable
    data class Unequip(
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : ShopRetryState
}

@Immutable
sealed interface ShopEvent {
    @Immutable
    data class SelectCategory(val category: EquipmentType?) : ShopEvent

    @Immutable
    data class SelectSlot(val slot: EquipmentSlot) : ShopEvent

    @Immutable
    data class OpenSlotManagement(val slot: EquipmentSlot) : ShopEvent

    data object CloseSlotManagement : ShopEvent

    data object BrowseManagedSlot : ShopEvent

    data object UnequipManagedSlot : ShopEvent

    @Immutable
    data class SelectEquipment(val equipmentId: Long) : ShopEvent

    @Immutable
    data class OpenEquipmentDetail(val equipmentId: Long) : ShopEvent

    data object CloseEquipmentDetail : ShopEvent

    @Immutable
    data class ExecuteEquipmentAction(val action: ShopEquipmentAction) : ShopEvent

    @Immutable
    data class RequestPurchaseConfirmation(val equipmentId: Long) : ShopEvent

    data object CancelPurchaseConfirmation : ShopEvent

    data object ConfirmPurchase : ShopEvent

    @Immutable
    data class EquipPurchased(
        val ownedEquipmentId: Long,
        val targetSlot: EquipmentSlot,
    ) : ShopEvent

    data object ConsumePurchaseSuccess : ShopEvent

    data object ConsumeEquipResult : ShopEvent

    data object ConsumeUnequipResult : ShopEvent

    data object Retry : ShopEvent

    data object ConsumeError : ShopEvent
}
