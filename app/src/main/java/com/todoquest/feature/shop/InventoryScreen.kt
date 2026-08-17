package com.todoquest.feature.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.ui.theme.TodoQuestTheme

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    InventoryContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun InventoryContent(
    state: InventoryUiState,
    onEvent: (InventoryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            InventoryTopBar(onBack = onBack)
            when {
                state.isLoading -> LoadingState(
                    description = stringResource(R.string.inventory_loading_description),
                    modifier = Modifier.weight(1f),
                )

                state.items.isEmpty() -> EmptyState(
                    text = stringResource(R.string.inventory_empty),
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("inventory-list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.items,
                        key = InventoryEquipmentUiModel::ownedEquipmentId,
                    ) { item ->
                        val isAnyEquipmentProcessing =
                            state.processingState != InventoryProcessingState.Idle ||
                                state.processingOwnedEquipmentId != null
                        val isThisEquipmentProcessing =
                            state.processingOwnedEquipmentId == item.ownedEquipmentId ||
                                (state.processingState as? InventoryProcessingState.Equipping)
                                    ?.ownedEquipmentId == item.ownedEquipmentId
                        val isThisEquipmentUnequipping =
                            item.isEquipped &&
                                (state.processingState as? InventoryProcessingState.Unequipping)
                                    ?.slot == item.slot
                        InventoryEquipmentCard(
                            item = item,
                            hasEquippedItemInSlot = state.equippedBySlot.containsKey(item.slot),
                            isAnyEquipmentProcessing = isAnyEquipmentProcessing,
                            isThisEquipmentProcessing = isThisEquipmentProcessing,
                            isThisEquipmentUnequipping = isThisEquipmentUnequipping,
                            onEquip = {
                                onEvent(
                                    InventoryEvent.SelectOwnedEquipment(item.ownedEquipmentId),
                                )
                                onEvent(InventoryEvent.EquipSelected)
                            },
                            onUnequip = {
                                onEvent(InventoryEvent.UnequipSlot(item.slot))
                            },
                        )
                    }
                }
            }
        }
    }

    state.equipResult?.let { result ->
        val success = result as InventoryEquipResult.Success
        val itemName = state.items
            .firstOrNull { it.equipmentId == success.equipmentId }
            ?.let { equipmentName(it.nameKey) }
            ?: stringResource(R.string.equipment_unknown_name)
        AlertDialog(
            onDismissRequest = { onEvent(InventoryEvent.ConsumeEquipResult) },
            title = { Text(text = stringResource(R.string.inventory_equip_success_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.inventory_equip_success_message,
                        itemName,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(InventoryEvent.ConsumeEquipResult) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_confirm))
                }
            },
        )
    }

    when (val unequipResult = state.unequipResult) {
        is InventoryUnequipResult.Success -> FeedbackDialog(
            title = stringResource(R.string.equipment_unequip_success_title),
            message = stringResource(
                if (unequipResult.changed) {
                    R.string.equipment_unequip_success_message
                } else {
                    R.string.equipment_unequip_already_empty_message
                },
            ),
            canRetry = false,
            onRetry = {},
            onDismiss = { onEvent(InventoryEvent.ConsumeUnequipResult) },
        )

        is InventoryUnequipResult.Failed -> FeedbackDialog(
            title = stringResource(R.string.equipment_unequip_failure_title),
            message = stringResource(R.string.equipment_unequip_failure_message),
            canRetry = state.retryState is InventoryRetryState.Unequip,
            onRetry = { onEvent(InventoryEvent.Retry) },
            onDismiss = { onEvent(InventoryEvent.ConsumeUnequipResult) },
        )

        null -> Unit
    }

    state.error?.let { error ->
        FeedbackDialog(
            title = stringResource(R.string.inventory_error_title),
            message = error.displayText(),
            canRetry = state.retryState != null,
            onRetry = { onEvent(InventoryEvent.Retry) },
            onDismiss = { onEvent(InventoryEvent.ConsumeError) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryTopBar(onBack: () -> Unit) {
    val backDescription = stringResource(R.string.inventory_back)
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.inventory_title),
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("inventory-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                )
            }
        },
    )
}

@Composable
private fun InventoryEquipmentCard(
    item: InventoryEquipmentUiModel,
    hasEquippedItemInSlot: Boolean,
    isAnyEquipmentProcessing: Boolean,
    isThisEquipmentProcessing: Boolean,
    isThisEquipmentUnequipping: Boolean,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
) {
    val displayName = equipmentName(item.nameKey)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory-equipment-${item.ownedEquipmentId}"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EquipmentArtwork(
                imageKey = item.imageKey,
                type = item.type,
                contentDescription = stringResource(
                    R.string.equipment_artwork_description,
                    displayName,
                ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.type.displayName(item.weaponType),
                    modifier = Modifier.testTag(
                        "inventory-equipment-type-${item.ownedEquipmentId}",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = item.rarity.displayName(),
                    modifier = Modifier.testTag(
                        "inventory-equipment-rarity-${item.ownedEquipmentId}",
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                item.modifiers.filter { it.amount > 0 }.forEach { modifier ->
                    Text(
                        text = modifier.displayLine(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (item.isEquipped) {
                    EquipmentBadge(
                        text = stringResource(R.string.shop_equipped_badge),
                        modifier = Modifier.testTag(
                            "inventory-equipped-${item.ownedEquipmentId}",
                        ),
                    )
                }
                Button(
                    onClick = if (item.isEquipped) onUnequip else onEquip,
                    enabled = !isAnyEquipmentProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("inventory-equip-${item.ownedEquipmentId}"),
                ) {
                    if (isThisEquipmentProcessing || isThisEquipmentUnequipping) {
                        ProcessingIndicator(
                            description = stringResource(
                                if (isThisEquipmentUnequipping) {
                                    R.string.equipment_unequipping_description
                                } else {
                                    R.string.inventory_equipping_description
                                },
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            isThisEquipmentUnequipping ->
                                stringResource(R.string.equipment_unequipping_description)
                            item.isEquipped -> stringResource(R.string.equipment_unequip)
                            hasEquippedItemInSlot -> stringResource(R.string.inventory_replace)
                            else -> stringResource(R.string.inventory_equip)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryError.displayText(): String = when (this) {
    InventoryError.LoadFailed -> stringResource(R.string.inventory_error_load_failed)
    is InventoryError.OwnedEquipmentNotFound ->
        stringResource(R.string.inventory_error_owned_not_found)
    is InventoryError.NotOwnedByCharacter ->
        stringResource(R.string.inventory_error_not_owned)
    is InventoryError.SlotMismatch -> stringResource(
        R.string.inventory_error_slot_mismatch,
        type.displayName(),
        equipmentSlot.displayName(),
        targetSlot.displayName(),
    )

    InventoryError.EquipFailed -> stringResource(R.string.inventory_error_equip_failed)
}

@Preview(name = "인벤토리", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
private fun InventoryPreview() {
    TodoQuestTheme {
        InventoryContent(
            state = previewInventoryState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "인벤토리 320dp 큰 글꼴",
    widthDp = 320,
    heightDp = 640,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun InventorySmallLargeFontPreview() {
    TodoQuestTheme {
        InventoryContent(
            state = previewInventoryState(),
            onEvent = {},
            onBack = {},
        )
    }
}

private fun previewInventoryState(): InventoryUiState {
    val helmet = InventoryEquipmentUiModel(
        ownedEquipmentId = 76L,
        equipmentId = 1_003L,
        nameKey = "equipment_name_leather_hat",
        descriptionKey = "equipment_description_leather_hat",
        type = EquipmentType.HELMET,
        slot = EquipmentSlot.HELMET,
        rarity = EquipmentRarity.COMMON,
        modifiers = listOf(
            EquipmentStatModifier(
                itemId = 1_003L,
                target = StatTarget.Derived(DerivedStatType.DEFENSE),
                type = ModifierType.FLAT,
                amount = 2,
            ),
        ),
        comparisons = emptyList(),
        imageKey = "headgear_leather_hat",
        acquiredAtEpochMillis = 900L,
        isEquipped = true,
    )
    val breastplate = InventoryEquipmentUiModel(
        ownedEquipmentId = 77L,
        equipmentId = 1_007L,
        nameKey = "equipment_name_iron_breastplate",
        descriptionKey = "equipment_description_iron_breastplate",
        type = EquipmentType.CHEST,
        slot = EquipmentSlot.CHEST,
        rarity = EquipmentRarity.EPIC,
        modifiers = listOf(
            EquipmentStatModifier(
                itemId = 1_007L,
                target = StatTarget.Derived(DerivedStatType.MAX_HP),
                type = ModifierType.FLAT,
                amount = 50,
            ),
        ),
        comparisons = emptyList(),
        imageKey = "top_iron_breastplate",
        acquiredAtEpochMillis = 1_000L,
        isEquipped = false,
    )
    return InventoryUiState(
        isLoading = false,
        items = listOf(helmet, breastplate),
        equippedBySlot = mapOf(EquipmentSlot.HELMET to helmet),
    )
}
