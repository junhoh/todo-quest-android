package com.todoquest.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.EquipmentInventorySnapshot
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.usecase.EquipmentComparisonCalculator
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
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
class InventoryViewModel(
    private val repository: EquipmentRepository,
    private val equipOwnedEquipment: EquipOwnedEquipmentUseCase,
    private val characterId: Long,
    private val unequipEquipment: UnequipEquipmentUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val loadGeneration = MutableStateFlow(0)
    private val commandState = MutableStateFlow(InventoryCommandState())
    @Volatile
    private var latestInventorySnapshot: EquipmentInventorySnapshot? = null

    private val inventoryState: Flow<InventoryLoadState> = loadGeneration.flatMapLatest {
        repository.observeInventory(characterId)
            .map<EquipmentInventorySnapshot, InventoryLoadState> { snapshot ->
                latestInventorySnapshot = snapshot
                InventoryLoadState.Loaded(snapshot)
            }
            .onStart { emit(InventoryLoadState.Loading) }
            .catch { emit(InventoryLoadState.Failed) }
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryState,
        commandState,
    ) { loadState, command ->
        when (loadState) {
            InventoryLoadState.Loading -> InventoryUiState(
                selectedOwnedEquipmentId = command.selectedOwnedEquipmentId,
                processingState = command.processingState,
                equipResult = command.equipResult,
                unequipResult = command.unequipResult,
                error = command.error,
                retryState = command.retryState,
            )

            InventoryLoadState.Failed -> InventoryUiState(
                isLoading = false,
                selectedOwnedEquipmentId = command.selectedOwnedEquipmentId,
                processingState = command.processingState,
                equipResult = command.equipResult,
                unequipResult = command.unequipResult,
                error = InventoryError.LoadFailed,
                retryState = InventoryRetryState.Load,
            )

            is InventoryLoadState.Loaded -> loadState.snapshot.toUiState(command)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InventoryUiState(),
    )

    fun onEvent(event: InventoryEvent) {
        when (event) {
            is InventoryEvent.SelectOwnedEquipment -> selectOwnedEquipment(event.ownedEquipmentId)
            InventoryEvent.EquipSelected -> equipSelected()
            is InventoryEvent.UnequipSlot -> unequipSlot(event.slot)
            InventoryEvent.Retry -> retry()
            InventoryEvent.ConsumeError -> updateCommand {
                copy(error = null, retryState = null)
            }

            InventoryEvent.ConsumeEquipResult -> updateCommand { copy(equipResult = null) }
            InventoryEvent.ConsumeUnequipResult -> updateCommand { copy(unequipResult = null) }
        }
    }

    private fun selectOwnedEquipment(ownedEquipmentId: Long?) {
        if (
            ownedEquipmentId != null &&
            latestInventorySnapshot?.ownedEquipment?.none { it.id == ownedEquipmentId } != false
        ) {
            return
        }
        updateCommand { copy(selectedOwnedEquipmentId = ownedEquipmentId) }
    }

    private fun equipSelected() {
        if (commandState.value.processingState != InventoryProcessingState.Idle) return
        val selectedId = commandState.value.selectedOwnedEquipmentId ?: return
        val owned = latestInventorySnapshot?.ownedEquipment?.firstOrNull { it.id == selectedId } ?: return
        launchEquip(selectedId, owned.equipment.slot)
    }

    private fun launchEquip(
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
    ) {
        if (commandState.value.processingState != InventoryProcessingState.Idle) return
        updateCommand {
            copy(
                processingState = InventoryProcessingState.Equipping(ownedEquipmentId, targetSlot),
                equipResult = null,
                unequipResult = null,
                error = null,
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
                            processingState = InventoryProcessingState.Idle,
                            equipResult = InventoryEquipResult.Success(
                                ownedEquipmentId = result.ownedEquipmentId,
                                equipmentId = result.equipmentId,
                                slot = result.slot,
                            ),
                            error = null,
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.OwnedEquipmentNotFound -> copy(
                            processingState = InventoryProcessingState.Idle,
                            error = InventoryError.OwnedEquipmentNotFound(result.ownedEquipmentId),
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.NotOwnedByCharacter -> copy(
                            processingState = InventoryProcessingState.Idle,
                            error = InventoryError.NotOwnedByCharacter(result.ownedEquipmentId),
                            retryState = null,
                        )

                        is EquipOwnedEquipmentResult.SlotMismatch -> copy(
                            processingState = InventoryProcessingState.Idle,
                            error = InventoryError.SlotMismatch(
                                ownedEquipmentId = result.ownedEquipmentId,
                                type = result.type,
                                equipmentSlot = result.equipmentSlot,
                                targetSlot = result.targetSlot,
                            ),
                            retryState = null,
                        )
                    }
                }
            }.onFailure {
                updateCommand {
                    copy(
                        processingState = InventoryProcessingState.Idle,
                        error = InventoryError.EquipFailed,
                        retryState = InventoryRetryState.Equip(ownedEquipmentId, targetSlot),
                    )
                }
            }
        }
    }

    private fun unequipSlot(slot: EquipmentSlot) {
        if (commandState.value.processingState != InventoryProcessingState.Idle) return
        if (latestInventorySnapshot?.equippedBySlot?.containsKey(slot) != true) return
        launchUnequip(slot)
    }

    private fun launchUnequip(slot: EquipmentSlot) {
        if (commandState.value.processingState != InventoryProcessingState.Idle) return
        updateCommand {
            copy(
                processingState = InventoryProcessingState.Unequipping(slot),
                equipResult = null,
                unequipResult = null,
                error = null,
                retryState = null,
            )
        }
        viewModelScope.launch(dispatcher) {
            runCatching {
                unequipEquipment(characterId, slot)
            }.onSuccess { result ->
                updateCommand {
                    copy(
                        processingState = InventoryProcessingState.Idle,
                        unequipResult = when (result) {
                            is UnequipEquipmentResult.Success -> InventoryUnequipResult.Success(
                                slot = result.slot,
                                changed = true,
                            )

                            is UnequipEquipmentResult.AlreadyEmpty -> InventoryUnequipResult.Success(
                                slot = result.slot,
                                changed = false,
                            )
                        },
                        error = null,
                        retryState = null,
                    )
                }
            }.onFailure {
                updateCommand {
                    copy(
                        processingState = InventoryProcessingState.Idle,
                        unequipResult = InventoryUnequipResult.Failed(slot),
                        retryState = InventoryRetryState.Unequip(slot),
                    )
                }
            }
        }
    }

    private fun retry() {
        when (val retry = commandState.value.retryState ?: uiState.value.retryState) {
            InventoryRetryState.Load -> {
                updateCommand { copy(error = null, retryState = null) }
                loadGeneration.value += 1
            }

            is InventoryRetryState.Equip -> launchEquip(
                ownedEquipmentId = retry.ownedEquipmentId,
                targetSlot = retry.targetSlot,
            )

            is InventoryRetryState.Unequip -> launchUnequip(retry.slot)

            null -> Unit
        }
    }

    private inline fun updateCommand(update: InventoryCommandState.() -> InventoryCommandState) {
        commandState.value = commandState.value.update()
    }
}

private data class InventoryCommandState(
    val selectedOwnedEquipmentId: Long? = null,
    val processingState: InventoryProcessingState = InventoryProcessingState.Idle,
    val equipResult: InventoryEquipResult? = null,
    val unequipResult: InventoryUnequipResult? = null,
    val error: InventoryError? = null,
    val retryState: InventoryRetryState? = null,
)

private sealed interface InventoryLoadState {
    data object Loading : InventoryLoadState

    data class Loaded(val snapshot: EquipmentInventorySnapshot) : InventoryLoadState

    data object Failed : InventoryLoadState
}

private fun EquipmentInventorySnapshot.toUiState(command: InventoryCommandState): InventoryUiState {
    val items = ownedEquipment.map { owned -> owned.toInventoryItem(this) }
    val itemsByOwnedId = items.associateBy(InventoryEquipmentUiModel::ownedEquipmentId)
    val validSelectedId = command.selectedOwnedEquipmentId?.takeIf(itemsByOwnedId::containsKey)
    return InventoryUiState(
        isLoading = false,
        items = items,
        equippedBySlot = equippedBySlot.mapNotNull { (slot, equipped) ->
            itemsByOwnedId[equipped.ownedEquipment.id]?.let { slot to it }
        }.toMap(),
        selectedOwnedEquipmentId = validSelectedId,
        processingState = command.processingState,
        equipResult = command.equipResult,
        unequipResult = command.unequipResult,
        error = command.error,
        retryState = command.retryState,
    )
}

private fun OwnedEquipment.toInventoryItem(
    snapshot: EquipmentInventorySnapshot,
): InventoryEquipmentUiModel {
    val definition = equipment
    val current = snapshot.equippedBySlot[definition.slot]?.ownedEquipment?.equipment
    return InventoryEquipmentUiModel(
        ownedEquipmentId = id,
        equipmentId = equipmentId,
        nameKey = definition.nameKey,
        descriptionKey = definition.descriptionKey,
        type = definition.type,
        slot = definition.slot,
        rarity = definition.rarity,
        modifiers = definition.modifiers,
        comparisons = EquipmentComparisonCalculator.compare(
            candidate = definition,
            current = current,
        ),
        imageKey = definition.imageKey,
        acquiredAtEpochMillis = acquiredAtEpochMillis,
        isEquipped = snapshot.equippedBySlot[definition.slot]?.ownedEquipment?.id == id,
        weaponType = definition.weaponType,
    )
}
