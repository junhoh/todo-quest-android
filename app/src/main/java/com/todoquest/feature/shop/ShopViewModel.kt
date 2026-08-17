package com.todoquest.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.PurchaseEligibility
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.model.toEquipmentSlot
import com.todoquest.domain.model.toEquipmentType
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.usecase.EquipmentComparisonCalculator
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
import com.todoquest.domain.usecase.PurchaseEquipmentPolicy
import com.todoquest.domain.usecase.PurchaseEquipmentUseCase
import com.todoquest.domain.usecase.UnequipEquipmentUseCase
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
class ShopViewModel(
    private val repository: EquipmentRepository,
    private val purchaseEquipment: PurchaseEquipmentUseCase,
    private val equipOwnedEquipment: EquipOwnedEquipmentUseCase,
    private val characterId: Long,
    private val unequipEquipment: UnequipEquipmentUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val loadGeneration = MutableStateFlow(0)
    private val commandState = MutableStateFlow(ShopCommandState())
    @Volatile
    private var latestStoreSnapshot: EquipmentStoreSnapshot? = null

    private val storeState: Flow<ShopLoadState> = loadGeneration.flatMapLatest {
        repository.observeStore(characterId)
            .map<EquipmentStoreSnapshot, ShopLoadState> { snapshot ->
                latestStoreSnapshot = snapshot
                ShopLoadState.Loaded(snapshot)
            }
            .onStart { emit(ShopLoadState.Loading) }
            .catch { emit(ShopLoadState.Failed) }
    }

    val uiState: StateFlow<ShopUiState> = combine(
        storeState,
        commandState,
    ) { loadState, command ->
        when (loadState) {
            ShopLoadState.Loading -> ShopUiState(
                selectedCategory = command.selectedCategory,
                managedSlot = command.managedSlot,
                equipmentSlots = emptyShopEquipmentSlots(command.selectedCategory),
                purchaseConfirmation = command.purchaseConfirmation,
                purchaseState = command.purchaseState,
                equipState = command.equipState,
                unequipState = command.unequipState,
                error = command.error,
                retryState = command.retryState,
            )

            ShopLoadState.Failed -> ShopUiState(
                isLoading = false,
                selectedCategory = command.selectedCategory,
                managedSlot = command.managedSlot,
                purchaseState = command.purchaseState,
                equipState = command.equipState,
                unequipState = command.unequipState,
                error = ShopError.LoadFailed,
                retryState = ShopRetryState.Load,
            )

            is ShopLoadState.Loaded -> loadState.snapshot.toUiState(command)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ShopUiState(),
    )

    fun onEvent(event: ShopEvent) {
        when (event) {
            is ShopEvent.SelectCategory -> selectCategory(event.category)
            is ShopEvent.SelectSlot -> selectCategory(event.slot.toEquipmentType())
            is ShopEvent.OpenSlotManagement -> openSlotManagement(event.slot)
            ShopEvent.CloseSlotManagement -> closeSlotManagement()
            ShopEvent.BrowseManagedSlot -> browseManagedSlot()
            ShopEvent.UnequipManagedSlot -> unequipManagedSlot()
            is ShopEvent.SelectEquipment -> selectEquipment(event.equipmentId)
            is ShopEvent.OpenEquipmentDetail -> openEquipmentDetail(event.equipmentId)
            ShopEvent.CloseEquipmentDetail -> updateCommand {
                copy(selectedDetailEquipmentId = null)
            }

            is ShopEvent.ExecuteEquipmentAction -> executeEquipmentAction(event.action)
            is ShopEvent.RequestPurchaseConfirmation -> requestPurchaseConfirmation(event.equipmentId)
            ShopEvent.CancelPurchaseConfirmation -> updateCommand {
                copy(purchaseConfirmation = null)
            }

            ShopEvent.ConfirmPurchase -> confirmPurchase()
            is ShopEvent.EquipPurchased -> equipPurchased(
                ownedEquipmentId = event.ownedEquipmentId,
                targetSlot = event.targetSlot,
            )
            ShopEvent.ConsumePurchaseSuccess -> consumePurchaseSuccess()
            ShopEvent.ConsumeEquipResult -> consumeEquipResult()
            ShopEvent.ConsumeUnequipResult -> consumeUnequipResult()
            ShopEvent.Retry -> retry()
            ShopEvent.ConsumeError -> updateCommand { copy(error = null, retryState = null) }
        }
    }

    private fun selectCategory(category: EquipmentType?) {
        updateCommand {
            copy(
                selectedCategory = category,
                selectedEquipmentId = null,
                selectedDetailEquipmentId = null,
                purchaseConfirmation = null,
            )
        }
    }

    private fun openSlotManagement(slot: EquipmentSlot) {
        updateCommand {
            copy(
                managedSlot = slot,
                unequipState = ShopUnequipState.Idle,
                retryState = retryState.takeUnless { it is ShopRetryState.Unequip },
            )
        }
    }

    private fun closeSlotManagement() {
        updateCommand { copy(managedSlot = null) }
    }

    private fun browseManagedSlot() {
        if (commandState.value.unequipState is ShopUnequipState.Processing) return
        val slot = commandState.value.managedSlot ?: return
        updateCommand {
            copy(
                managedSlot = null,
                selectedCategory = slot.toEquipmentType(),
                selectedEquipmentId = null,
                selectedDetailEquipmentId = null,
                purchaseConfirmation = null,
            )
        }
    }

    private fun unequipManagedSlot() {
        val slot = commandState.value.managedSlot ?: return
        val equipmentId = latestStoreSnapshot
            ?.equippedBySlot
            ?.get(slot)
            ?.ownedEquipment
            ?.equipmentId
            ?: return
        launchUnequip(equipmentId = equipmentId, slot = slot)
    }

    private fun selectEquipment(equipmentId: Long) {
        if (latestStoreSnapshot?.equipment?.none { it.id == equipmentId } != false) return
        updateCommand { copy(selectedEquipmentId = equipmentId) }
    }

    private fun openEquipmentDetail(equipmentId: Long) {
        if (latestStoreSnapshot?.equipment?.none { it.id == equipmentId } != false) return
        updateCommand {
            copy(
                selectedEquipmentId = equipmentId,
                selectedDetailEquipmentId = equipmentId,
            )
        }
    }

    private fun executeEquipmentAction(action: ShopEquipmentAction) {
        if (isCommandProcessing()) return
        val snapshot = latestStoreSnapshot ?: return
        if (!snapshot.hasCurrentAction(action)) return
        when (action) {
            is ShopEquipmentAction.Purchase -> requestPurchaseConfirmation(action.equipmentId)
            is ShopEquipmentAction.PurchaseUnavailable -> Unit
            is ShopEquipmentAction.Equip -> launchEquip(
                ownedEquipmentId = action.ownedEquipmentId,
                targetSlot = action.slot,
                consumePurchase = false,
            )

            is ShopEquipmentAction.Unequip -> launchUnequip(
                equipmentId = action.equipmentId,
                slot = action.slot,
            )
        }
    }

    private fun requestPurchaseConfirmation(equipmentId: Long) {
        if (isCommandProcessing()) return
        val command = commandState.value
        val snapshot = latestStoreSnapshot ?: return
        val definition = snapshot.equipment.firstOrNull { it.id == equipmentId } ?: return
        val detail = definition.toShopItem(snapshot)
        if (command.purchaseState is PurchaseState.Processing || command.purchaseState is PurchaseState.Success) {
            return
        }
        when (val availability = detail.purchaseAvailability) {
            PurchaseAvailability.Available -> updateCommand {
                copy(
                    selectedEquipmentId = equipmentId,
                    purchaseConfirmation = PurchaseConfirmationUiState(
                        equipmentId = detail.equipmentId,
                        equipmentNameKey = detail.nameKey,
                        type = detail.type,
                        slot = detail.slot,
                        price = detail.price,
                        currentGold = snapshot.currentGold,
                        expectedRemainingGold = Math.subtractExact(snapshot.currentGold, detail.price),
                    ),
                    purchaseState = PurchaseState.Idle,
                    retryState = null,
                )
            }

            is PurchaseAvailability.Unavailable -> updateCommand {
                copy(
                    selectedEquipmentId = equipmentId,
                    purchaseConfirmation = null,
                    purchaseState = PurchaseState.Unavailable(
                        equipmentId = detail.equipmentId,
                        reason = availability.reason,
                    ),
                    retryState = null,
                )
            }
        }
    }

    private fun confirmPurchase() {
        val command = commandState.value
        val confirmation = command.purchaseConfirmation ?: return
        if (command.purchaseState is PurchaseState.Processing) return
        launchPurchase(confirmation.equipmentId)
    }

    private fun launchPurchase(equipmentId: Long) {
        if (isCommandProcessing()) return
        updateCommand {
            copy(
                purchaseConfirmation = null,
                purchaseState = PurchaseState.Processing(equipmentId),
                retryState = null,
            )
        }
        viewModelScope.launch(dispatcher) {
            runCatching {
                purchaseEquipment(characterId, equipmentId)
            }.onSuccess { result ->
                updateCommand {
                    when (result) {
                        is PurchaseEquipmentResult.Success -> copy(
                            purchaseState = PurchaseState.Success(
                                ownedEquipmentId = result.ownedEquipmentId,
                                equipmentId = result.equipmentId,
                                equipmentNameKey = result.equipmentNameKey,
                                type = result.type,
                                slot = result.slot,
                                currentGold = result.remainingGold,
                            ),
                            retryState = null,
                        )

                        is PurchaseEquipmentResult.Unavailable -> copy(
                            purchaseState = PurchaseState.Unavailable(
                                equipmentId = equipmentId,
                                reason = result.reason.toPresentation(),
                            ),
                            retryState = null,
                        )
                    }
                }
            }.onFailure {
                updateCommand {
                    copy(
                        purchaseState = PurchaseState.Failed(equipmentId),
                        retryState = ShopRetryState.Purchase(equipmentId),
                    )
                }
            }
        }
    }

    private fun equipPurchased(
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
    ) {
        val purchase = commandState.value.purchaseState as? PurchaseState.Success ?: return
        if (purchase.ownedEquipmentId != ownedEquipmentId || purchase.slot != targetSlot) return
        launchEquip(
            ownedEquipmentId = ownedEquipmentId,
            targetSlot = targetSlot,
            consumePurchase = true,
        )
    }

    private fun launchEquip(
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
        consumePurchase: Boolean,
    ) {
        if (isCommandProcessing()) return
        updateCommand {
            copy(
                purchaseState = if (consumePurchase) PurchaseState.Idle else purchaseState,
                equipState = ShopEquipState.Processing(ownedEquipmentId, targetSlot),
                retryState = null,
            )
        }
        viewModelScope.launch(dispatcher) {
            runCatching {
                equipOwnedEquipment(characterId, ownedEquipmentId, targetSlot)
            }.onSuccess { result ->
                updateCommand {
                    when (result) {
                        is EquipOwnedEquipmentResult.Success -> copy(
                            equipState = ShopEquipState.Success(
                                ownedEquipmentId = result.ownedEquipmentId,
                                equipmentId = result.equipmentId,
                                slot = result.slot,
                            ),
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.OwnedEquipmentNotFound -> copy(
                            equipState = ShopEquipState.Failed(
                                ownedEquipmentId = ownedEquipmentId,
                                targetSlot = targetSlot,
                                reason = EquipFailure.OwnedEquipmentNotFound,
                            ),
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.NotOwnedByCharacter -> copy(
                            equipState = ShopEquipState.Failed(
                                ownedEquipmentId = ownedEquipmentId,
                                targetSlot = targetSlot,
                                reason = EquipFailure.NotOwnedByCharacter,
                            ),
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.SlotMismatch -> copy(
                            equipState = ShopEquipState.Failed(
                                ownedEquipmentId = ownedEquipmentId,
                                targetSlot = targetSlot,
                                reason = EquipFailure.SlotMismatch(
                                    type = result.type,
                                    equipmentSlot = result.equipmentSlot,
                                    targetSlot = result.targetSlot,
                                ),
                            ),
                            retryState = null,
                        )
                    }
                }
            }.onFailure {
                updateCommand {
                    copy(
                        equipState = ShopEquipState.Failed(
                            ownedEquipmentId = ownedEquipmentId,
                            targetSlot = targetSlot,
                            reason = EquipFailure.CommandFailed,
                        ),
                        retryState = ShopRetryState.Equip(ownedEquipmentId, targetSlot),
                    )
                }
            }
        }
    }

    private fun launchUnequip(
        equipmentId: Long,
        slot: EquipmentSlot,
    ) {
        if (isCommandProcessing()) return
        updateCommand {
            copy(
                unequipState = ShopUnequipState.Processing(
                    equipmentId = equipmentId,
                    slot = slot,
                ),
                retryState = null,
            )
        }
        viewModelScope.launch(dispatcher) {
            runCatching {
                unequipEquipment(characterId, slot)
            }.onSuccess { result ->
                updateCommand {
                    val succeededEquipmentId = when (result) {
                        is UnequipEquipmentResult.Success -> result.equipmentId
                        is UnequipEquipmentResult.AlreadyEmpty -> equipmentId
                    }
                    val succeededSlot = when (result) {
                        is UnequipEquipmentResult.Success -> result.slot
                        is UnequipEquipmentResult.AlreadyEmpty -> result.slot
                    }
                    clearSelectionForUnequippedItem(succeededEquipmentId).copy(
                        unequipState = ShopUnequipState.Success(
                            equipmentId = succeededEquipmentId,
                            slot = succeededSlot,
                            changed = result is UnequipEquipmentResult.Success,
                        ),
                        retryState = null,
                    )
                }
            }.onFailure {
                updateCommand {
                    copy(
                        unequipState = ShopUnequipState.Failed(
                            equipmentId = equipmentId,
                            slot = slot,
                        ),
                        retryState = ShopRetryState.Unequip(
                            equipmentId = equipmentId,
                            slot = slot,
                        ),
                    )
                }
            }
        }
    }

    private fun consumePurchaseSuccess() {
        if (commandState.value.purchaseState !is PurchaseState.Success) return
        updateCommand {
            copy(
                selectedEquipmentId = null,
                selectedDetailEquipmentId = null,
                purchaseConfirmation = null,
                purchaseState = PurchaseState.Idle,
            )
        }
    }

    private fun consumeEquipResult() {
        if (commandState.value.equipState is ShopEquipState.Processing) return
        updateCommand { copy(equipState = ShopEquipState.Idle, retryState = null) }
    }

    private fun consumeUnequipResult() {
        if (commandState.value.unequipState is ShopUnequipState.Processing) return
        updateCommand {
            copy(
                unequipState = ShopUnequipState.Idle,
                retryState = retryState.takeUnless { it is ShopRetryState.Unequip },
            )
        }
    }

    private fun retry() {
        when (val retry = commandState.value.retryState ?: uiState.value.retryState) {
            ShopRetryState.Load -> {
                updateCommand { copy(error = null, retryState = null) }
                loadGeneration.value += 1
            }

            is ShopRetryState.Purchase -> launchPurchase(retry.equipmentId)
            is ShopRetryState.Equip -> launchEquip(
                ownedEquipmentId = retry.ownedEquipmentId,
                targetSlot = retry.targetSlot,
                consumePurchase = false,
            )

            is ShopRetryState.Unequip -> launchUnequip(
                equipmentId = retry.equipmentId,
                slot = retry.slot,
            )

            null -> Unit
        }
    }

    private inline fun updateCommand(update: ShopCommandState.() -> ShopCommandState) {
        commandState.value = commandState.value.update()
    }

    private fun isCommandProcessing(): Boolean = commandState.value.run {
        purchaseState is PurchaseState.Processing ||
            equipState is ShopEquipState.Processing ||
            unequipState is ShopUnequipState.Processing
    }
}

private data class ShopCommandState(
    val selectedCategory: EquipmentType? = null,
    val managedSlot: EquipmentSlot? = null,
    val selectedEquipmentId: Long? = null,
    val selectedDetailEquipmentId: Long? = null,
    val purchaseConfirmation: PurchaseConfirmationUiState? = null,
    val purchaseState: PurchaseState = PurchaseState.Idle,
    val equipState: ShopEquipState = ShopEquipState.Idle,
    val unequipState: ShopUnequipState = ShopUnequipState.Idle,
    val error: ShopError? = null,
    val retryState: ShopRetryState? = null,
) {
    fun clearSelectionForUnequippedItem(equipmentId: Long): ShopCommandState = copy(
        selectedEquipmentId = selectedEquipmentId.takeUnless { it == equipmentId },
        selectedDetailEquipmentId = selectedDetailEquipmentId.takeUnless { it == equipmentId },
        purchaseConfirmation = purchaseConfirmation?.takeUnless { it.equipmentId == equipmentId },
    )
}

private sealed interface ShopLoadState {
    data object Loading : ShopLoadState

    data class Loaded(val snapshot: EquipmentStoreSnapshot) : ShopLoadState

    data object Failed : ShopLoadState
}

private fun EquipmentStoreSnapshot.toUiState(command: ShopCommandState): ShopUiState {
    val allItems = equipment.map { definition -> definition.toShopItem(this) }
    val filteredItems = allItems.filter { command.selectedCategory == null || it.type == command.selectedCategory }
    val selectedEquipment = command.selectedEquipmentId?.let { selectedId ->
        equipment.firstOrNull { it.id == selectedId }
    }
    val selectedDetail = command.selectedDetailEquipmentId?.let { selectedId ->
        allItems.firstOrNull { it.equipmentId == selectedId }
    }
    val preview = selectedEquipment?.let { previewByEquipmentId[it.id] }
    val previewStats = preview?.derivedStats
    val selectedSlot = selectedEquipment?.slot
        ?: command.managedSlot
        ?: command.selectedCategory?.toEquipmentSlot()
    val slotItems = EquipmentSlot.entries.map { slot ->
        val type = slot.toEquipmentType()
        val equipped = equippedBySlot[slot]?.ownedEquipment?.equipment
        ShopEquipmentSlotUiModel(
            slot = slot,
            type = type,
            equipmentId = equipped?.id,
            nameKey = equipped?.nameKey,
            rarity = equipped?.rarity,
            imageKey = equipped?.imageKey,
            isEquipped = equipped != null,
            isSelected = selectedSlot == slot,
            weaponType = equipped?.weaponType,
        )
    }
    return ShopUiState(
        isLoading = false,
        currentGold = currentGold,
        characterLevel = characterLevel,
        characterAppearance = appearance,
        characterEquippedItems = preview?.renderedEquippedItems ?: renderedEquippedItems,
        equipmentSlots = slotItems,
        statSummary = CharacterStatSummaryUiModel(
            attack = CharacterStatValueUiModel(
                currentValue = derivedStats.attack,
                difference = (previewStats?.attack ?: derivedStats.attack) - derivedStats.attack,
            ),
            maxHp = CharacterStatValueUiModel(
                currentValue = derivedStats.maxHp,
                difference = (previewStats?.maxHp ?: derivedStats.maxHp) - derivedStats.maxHp,
            ),
            defense = CharacterStatValueUiModel(
                currentValue = derivedStats.defense,
                difference = (previewStats?.defense ?: derivedStats.defense) - derivedStats.defense,
            ),
        ),
        selectedCategory = command.selectedCategory,
        managedSlot = command.managedSlot,
        items = filteredItems,
        selectedEquipmentId = selectedEquipment?.id,
        selectedDetail = selectedDetail,
        purchaseConfirmation = command.purchaseConfirmation,
        purchaseState = command.purchaseState,
        equipState = command.equipState,
        unequipState = command.unequipState,
        error = command.error,
        retryState = command.retryState,
    )
}

private fun Equipment.toShopItem(snapshot: EquipmentStoreSnapshot): ShopEquipmentUiModel {
    val current = snapshot.equippedBySlot[slot]?.ownedEquipment?.equipment
    val owned = snapshot.ownedEquipmentByEquipmentId[id]
    val isOwned = owned != null
    val equippedOwned = snapshot.equippedBySlot[slot]?.ownedEquipment
    val isEquipped = owned != null && equippedOwned?.id == owned.id
    val eligibility = PurchaseEquipmentPolicy.evaluate(
        equipment = this,
        characterLevel = snapshot.characterLevel,
        availableGold = snapshot.currentGold,
        isOwned = isOwned,
    )
    val availability = eligibility.toPresentationAvailability()
    val action = when {
        owned == null && availability is PurchaseAvailability.Available ->
            ShopEquipmentAction.Purchase(equipmentId = id)

        owned == null && availability is PurchaseAvailability.Unavailable ->
            ShopEquipmentAction.PurchaseUnavailable(reason = availability.reason)

        isEquipped -> ShopEquipmentAction.Unequip(
            equipmentId = id,
            slot = slot,
        )

        else -> ShopEquipmentAction.Equip(
            ownedEquipmentId = requireNotNull(owned).id,
            slot = slot,
        )
    }
    return ShopEquipmentUiModel(
        equipmentId = id,
        nameKey = nameKey,
        descriptionKey = descriptionKey,
        type = type,
        slot = slot,
        rarity = rarity,
        price = price,
        requiredLevel = requiredLevel,
        modifiers = modifiers,
        comparisons = EquipmentComparisonCalculator.compare(candidate = this, current = current),
        imageKey = imageKey,
        isForSale = isForSale,
        isOwned = isOwned,
        isEquipped = isEquipped,
        purchaseAvailability = availability,
        action = action,
        isRequiredLevelMet = snapshot.characterLevel >= requiredLevel,
        weaponType = weaponType,
    )
}

private fun EquipmentStoreSnapshot.hasCurrentAction(action: ShopEquipmentAction): Boolean = when (action) {
    is ShopEquipmentAction.Purchase -> equipment
        .firstOrNull { it.id == action.equipmentId }
        ?.toShopItem(this)
        ?.action == action

    is ShopEquipmentAction.PurchaseUnavailable -> false
    is ShopEquipmentAction.Equip -> ownedEquipmentByEquipmentId.values
        .firstOrNull { it.id == action.ownedEquipmentId }
        ?.equipment
        ?.toShopItem(this)
        ?.action == action

    is ShopEquipmentAction.Unequip -> equipment
        .firstOrNull { it.id == action.equipmentId }
        ?.toShopItem(this)
        ?.action == action
}

private fun PurchaseEligibility.toPresentationAvailability(): PurchaseAvailability = when (this) {
    PurchaseEligibility.Eligible -> PurchaseAvailability.Available
    is PurchaseEligibility.Unavailable -> PurchaseAvailability.Unavailable(toPresentation())
}

private fun PurchaseEligibility.Unavailable.toPresentation(): PurchaseUnavailableReason = when (this) {
    is PurchaseEligibility.UnsupportedSlot -> PurchaseUnavailableReason.UnsupportedSlot(type, slot)
    is PurchaseEligibility.NotForSale -> PurchaseUnavailableReason.NotForSale
    is PurchaseEligibility.AlreadyOwned -> PurchaseUnavailableReason.AlreadyOwned
    is PurchaseEligibility.LevelTooLow -> PurchaseUnavailableReason.LevelTooLow(
        requiredLevel = requiredLevel,
        characterLevel = characterLevel,
    )

    is PurchaseEligibility.InsufficientGold -> PurchaseUnavailableReason.InsufficientGold(
        price = price,
        availableGold = availableGold,
    )
}
