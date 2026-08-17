package com.todoquest.feature.shop

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.WeaponType
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.character.LayeredCharacterSprite
import com.todoquest.ui.theme.ShopMerchantSurface
import com.todoquest.ui.theme.TodoQuestTheme
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun ShopScreen(
    viewModel: ShopViewModel,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    ShopContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenInventory = onOpenInventory,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopContent(
    state: ShopUiState,
    onEvent: (ShopEvent) -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = state.purchaseState is PurchaseState.Processing ||
        state.equipState is ShopEquipState.Processing ||
        state.unequipState is ShopUnequipState.Processing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShopTopBar(
                gold = state.currentGold,
                onOpenInventory = onOpenInventory,
            )
        },
    ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("shop-equipment-list"),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "merchant-banner") {
                    MerchantBanner(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item(key = "equipment-preview") {
                    EquipmentPreviewCard(
                        level = state.characterLevel,
                        appearance = state.characterAppearance,
                        equippedItems = state.characterEquippedItems,
                        slots = state.equipmentSlots,
                        summary = state.statSummary,
                        enabled = !isBusy,
                        onSelectSlot = { onEvent(ShopEvent.OpenSlotManagement(it)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item(key = "sale-header") {
                    SaleEquipmentHeader(
                        itemCount = state.items.size,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item(key = "shop-categories") {
                    CategoryFilterRow(
                        selected = state.selectedCategory,
                        enabled = !isBusy,
                        onSelect = { onEvent(ShopEvent.SelectCategory(it)) },
                    )
                }
                when {
                    state.isLoading -> item(key = "shop-loading") {
                        LoadingState(
                            description = stringResource(R.string.shop_loading_description),
                            modifier = Modifier.heightIn(min = 160.dp),
                        )
                    }

                    state.items.isEmpty() -> item(key = "shop-empty") {
                        EmptyState(text = stringResource(R.string.shop_empty))
                    }

                    else -> {
                        items(
                            items = state.items,
                            key = ShopEquipmentUiModel::equipmentId,
                        ) { item ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ShopItemCard(
                                    item = item,
                                    selected = state.selectedEquipmentId == item.equipmentId,
                                    enabled = !isBusy,
                                    isProcessing = state.isProcessing(item.action),
                                    onSelect = {
                                        onEvent(ShopEvent.SelectEquipment(item.equipmentId))
                                    },
                                    onOpenDetail = {
                                        onEvent(ShopEvent.OpenEquipmentDetail(item.equipmentId))
                                    },
                                    onAction = {
                                        onEvent(ShopEvent.ExecuteEquipmentAction(item.action))
                                    },
                                )
                            }
                        }
                    }
                }
            }
    }

    state.selectedDetail?.let { detail ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                if (!isBusy) onEvent(ShopEvent.CloseEquipmentDetail)
            },
            sheetState = sheetState,
            modifier = Modifier.testTag("shop-equipment-detail"),
        ) {
            EquipmentDetailContent(
                item = detail,
                currentGold = state.currentGold,
                isProcessing = state.isProcessing(detail.action),
                onAction = {
                    onEvent(ShopEvent.ExecuteEquipmentAction(detail.action))
                },
            )
        }
    }

    state.purchaseConfirmation?.let { confirmation ->
        PurchaseConfirmationDialog(
            confirmation = confirmation,
            isProcessing = state.purchaseState is PurchaseState.Processing,
            onDismiss = { onEvent(ShopEvent.CancelPurchaseConfirmation) },
            onConfirm = { onEvent(ShopEvent.ConfirmPurchase) },
        )
    }

    val managedSlotItem = state.managedSlot?.let { managedSlot ->
        state.equipmentSlots.firstOrNull { it.slot == managedSlot }
    }
    if (
        managedSlotItem != null &&
        (state.unequipState is ShopUnequipState.Idle ||
            state.unequipState is ShopUnequipState.Processing)
    ) {
        EquipmentSlotManagementDialog(
            item = managedSlotItem,
            isProcessing = state.unequipState is ShopUnequipState.Processing,
            onBrowse = { onEvent(ShopEvent.BrowseManagedSlot) },
            onUnequip = { onEvent(ShopEvent.UnequipManagedSlot) },
            onClose = { onEvent(ShopEvent.CloseSlotManagement) },
        )
    }

    when (val purchaseState = state.purchaseState) {
        is PurchaseState.Success -> PurchaseSuccessDialog(
            purchase = purchaseState,
            isEquipping = state.equipState is ShopEquipState.Processing,
            onEquip = {
                onEvent(
                    ShopEvent.EquipPurchased(
                        ownedEquipmentId = purchaseState.ownedEquipmentId,
                        targetSlot = purchaseState.slot,
                    ),
                )
            },
            onOpenInventory = {
                onEvent(ShopEvent.ConsumePurchaseSuccess)
                onOpenInventory()
            },
            onContinue = { onEvent(ShopEvent.ConsumePurchaseSuccess) },
        )

        is PurchaseState.Failed -> FeedbackDialog(
            title = stringResource(R.string.shop_purchase_failure_title),
            message = stringResource(R.string.shop_purchase_failure_message),
            canRetry = state.retryState is ShopRetryState.Purchase,
            onRetry = { onEvent(ShopEvent.Retry) },
            onDismiss = null,
        )

        PurchaseState.Idle,
        is PurchaseState.Processing,
        is PurchaseState.Unavailable,
        -> Unit
    }

    when (val equipState = state.equipState) {
        is ShopEquipState.Success -> FeedbackDialog(
            title = stringResource(R.string.shop_equip_success_title),
            message = stringResource(R.string.shop_equip_success_message),
            canRetry = false,
            onRetry = {},
            onDismiss = { onEvent(ShopEvent.ConsumeEquipResult) },
        )

        is ShopEquipState.Failed -> FeedbackDialog(
            title = stringResource(R.string.shop_equip_failure_title),
            message = equipState.reason.displayText(),
            canRetry = state.retryState is ShopRetryState.Equip,
            onRetry = { onEvent(ShopEvent.Retry) },
            onDismiss = { onEvent(ShopEvent.ConsumeEquipResult) },
        )

        ShopEquipState.Idle,
        is ShopEquipState.Processing,
        -> Unit
    }

    when (val unequipState = state.unequipState) {
        is ShopUnequipState.Success -> FeedbackDialog(
            title = stringResource(R.string.equipment_unequip_success_title),
            message = stringResource(
                if (unequipState.changed) {
                    R.string.equipment_unequip_success_message
                } else {
                    R.string.equipment_unequip_already_empty_message
                },
            ),
            canRetry = false,
            onRetry = {},
            onDismiss = { onEvent(ShopEvent.ConsumeUnequipResult) },
        )

        is ShopUnequipState.Failed -> FeedbackDialog(
            title = stringResource(R.string.equipment_unequip_failure_title),
            message = stringResource(R.string.equipment_unequip_failure_message),
            canRetry = state.retryState is ShopRetryState.Unequip,
            onRetry = { onEvent(ShopEvent.Retry) },
            onDismiss = { onEvent(ShopEvent.ConsumeUnequipResult) },
        )

        ShopUnequipState.Idle,
        is ShopUnequipState.Processing,
        -> Unit
    }

    if (state.error is ShopError.LoadFailed) {
        FeedbackDialog(
            title = stringResource(R.string.shop_error_title),
            message = stringResource(R.string.shop_error_load_failed),
            canRetry = state.retryState is ShopRetryState.Load,
            onRetry = { onEvent(ShopEvent.Retry) },
            onDismiss = { onEvent(ShopEvent.ConsumeError) },
        )
    }
}

@Composable
internal fun MerchantBanner(
    @DrawableRes spriteResId: Int = R.drawable.todo_quest_blacksmith_shopkeeper_front_idle,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val sprite = remember(resources, spriteResId) {
        runCatching { ImageBitmap.imageResource(resources, spriteResId) }.getOrNull()
    }
    val description = stringResource(R.string.shop_blacksmith_description)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MerchantBannerHeight)
            .testTag("shop-merchant-banner")
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        color = ShopMerchantSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MerchantSprite(
                sprite = sprite,
                modifier = Modifier.size(MerchantSpriteSize),
            )
            MerchantBannerCopy(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ShopkeeperGreeting(
    @DrawableRes spriteResId: Int = R.drawable.todo_quest_blacksmith_shopkeeper_front_idle,
    modifier: Modifier = Modifier,
) {
    MerchantBanner(
        spriteResId = spriteResId,
        modifier = modifier,
    )
}

@Composable
private fun MerchantSprite(
    sprite: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag("shop-merchant-sprite-frame"),
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) {
            Image(
                bitmap = sprite,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("shop-merchant-sprite"),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("shop-merchant-fallback"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MerchantBannerCopy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("shop-merchant-copy"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.shop_blacksmith_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.shop_blacksmith_greeting),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EquipmentSlotManagementDialog(
    item: ShopEquipmentSlotUiModel,
    isProcessing: Boolean,
    onBrowse: () -> Unit,
    onUnequip: () -> Unit,
    onClose: () -> Unit,
) {
    val slotName = item.slot.displayName()
    val equipmentDisplayName = item.nameKey?.let { equipmentName(it) }
    val rarityName = item.rarity?.displayName()
    val hasEquippedItem = item.isEquipped &&
        equipmentDisplayName != null &&
        rarityName != null
    val stateDescription = if (hasEquippedItem) {
        stringResource(
            R.string.shop_slot_management_equipped_description,
            slotName,
            equipmentDisplayName,
            rarityName,
        )
    } else {
        stringResource(R.string.shop_slot_management_empty_description, slotName)
    }
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onClose() },
        modifier = Modifier.testTag("shop-slot-management-dialog"),
        title = {
            Text(text = stringResource(R.string.shop_slot_management_title, slotName))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = stateDescription
                    }
                    .testTag("shop-slot-management-state"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = slotName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (hasEquippedItem) {
                    Text(text = requireNotNull(equipmentDisplayName))
                    Text(
                        text = requireNotNull(rarityName),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = stringResource(R.string.shop_equipped_badge))
                } else {
                    Text(text = stringResource(R.string.shop_equipment_slot_empty))
                }
                if (isProcessing) {
                    ProcessingIndicator(
                        description = stringResource(
                            R.string.equipment_unequipping_description,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onBrowse,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("shop-slot-management-browse"),
                ) {
                    Text(text = stringResource(R.string.shop_slot_management_browse))
                }
                if (hasEquippedItem) {
                    OutlinedButton(
                        onClick = onUnequip,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("shop-slot-management-unequip"),
                    ) {
                        Text(text = stringResource(R.string.equipment_unequip))
                    }
                }
                TextButton(
                    onClick = onClose,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("shop-slot-management-close"),
                ) {
                    Text(text = stringResource(R.string.shop_slot_management_close))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopTopBar(
    gold: Long,
    onOpenInventory: () -> Unit,
) {
    val inventoryDescription = stringResource(R.string.shop_open_inventory)
    val formattedGold = formatNumber(gold)
    val goldDescription = stringResource(R.string.shop_gold_description, formattedGold)
    TopAppBar(
        modifier = Modifier.testTag("shop-top-bar"),
        title = {
            Text(
                text = stringResource(R.string.shop_title),
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            Surface(
                modifier = Modifier
                    .testTag("shop-gold-summary")
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = goldDescription
                    },
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("shop-gold-icon"),
                    )
                    Text(
                        text = formattedGold,
                        modifier = Modifier.testTag("shop-gold-value"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(
                onClick = onOpenInventory,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("shop-open-inventory"),
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = inventoryDescription,
                )
            }
        },
    )
}

@Composable
internal fun EquipmentPreviewCard(
    level: Int,
    appearance: CharacterAppearance,
    equippedItems: EquippedItems,
    slots: List<ShopEquipmentSlotUiModel>,
    summary: CharacterStatSummaryUiModel,
    enabled: Boolean,
    onSelectSlot: (EquipmentSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotsByType = slots.associateBy(ShopEquipmentSlotUiModel::slot)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shop-equipment-preview-card"),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            val compact = maxWidth < CompactPreviewWidth ||
                LocalDensity.current.fontScale >= CompactPreviewFontScale
            val slotSize = if (compact) CompactEquipmentSlotSize else RegularEquipmentSlotSize
            val avatarSize = if (compact) CompactCharacterSize else RegularCharacterSize
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shop-character-preview"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.shop_equipment_preview_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (compact) {
                                "shop-preview-compact-layout"
                            } else {
                                "shop-preview-regular-layout"
                            },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top,
                ) {
                    EquipmentSlotColumn(
                        slots = RegularLeftEquipmentSlotOrder,
                        slotsByType = slotsByType,
                        slotSize = slotSize,
                        enabled = enabled,
                        onSelectSlot = onSelectSlot,
                        modifier = Modifier
                            .testTag("shop-preview-left-slots"),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CharacterAvatar(
                            level = level,
                            characterAppearance = appearance,
                            characterEquippedItems = equippedItems,
                            spriteSize = avatarSize,
                        )
                        EquipmentSlot(
                            item = slotsByType.getValue(EquipmentSlot.SHOES),
                            enabled = enabled,
                            onClick = { onSelectSlot(EquipmentSlot.SHOES) },
                            modifier = Modifier.size(slotSize),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    EquipmentSlotColumn(
                        slots = RegularRightEquipmentSlotOrder,
                        slotsByType = slotsByType,
                        slotSize = slotSize,
                        enabled = enabled,
                        onSelectSlot = onSelectSlot,
                        modifier = Modifier
                            .testTag("shop-preview-right-slots"),
                    )
                }
                StatSummary(summary = summary)
            }
        }
    }
}

@Composable
internal fun CharacterEquipmentPreview(
    level: Int,
    appearance: CharacterAppearance,
    equippedItems: EquippedItems,
    slots: List<ShopEquipmentSlotUiModel>,
    enabled: Boolean,
    onSelectSlot: (EquipmentSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    EquipmentPreviewCard(
        level = level,
        appearance = appearance,
        equippedItems = equippedItems,
        slots = slots,
        summary = CharacterStatSummaryUiModel(
            attack = CharacterStatValueUiModel(0, 0),
            maxHp = CharacterStatValueUiModel(0, 0),
            defense = CharacterStatValueUiModel(0, 0),
        ),
        enabled = enabled,
        onSelectSlot = onSelectSlot,
        modifier = modifier,
    )
}

@Composable
private fun EquipmentSlotColumn(
    slots: List<EquipmentSlot>,
    slotsByType: Map<EquipmentSlot, ShopEquipmentSlotUiModel>,
    slotSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onSelectSlot: (EquipmentSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        slots.forEach { slot ->
            EquipmentSlot(
                item = slotsByType.getValue(slot),
                enabled = enabled,
                onClick = { onSelectSlot(slot) },
                modifier = Modifier.size(slotSize),
            )
        }
    }
}

@Composable
internal fun CharacterAvatar(
    level: Int,
    characterAppearance: CharacterAppearance,
    characterEquippedItems: EquippedItems,
    spriteSize: androidx.compose.ui.unit.Dp = RegularCharacterSize,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("shop-character-avatar"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LayeredCharacterSprite(
            renderState = CharacterRenderState(
                appearance = characterAppearance,
                equippedItems = characterEquippedItems,
            ),
            contentDescription = stringResource(R.string.shop_character_avatar_description),
            modifier = Modifier
                .size(spriteSize)
                .testTag("shop-character-sprite"),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = stringResource(R.string.shop_character_level, level),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("shop-character-level"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun EquipmentSlot(
    item: ShopEquipmentSlotUiModel,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotName = item.type.displayName(item.weaponType)
    val rarityName = item.rarity?.displayName()
    val equipmentDisplayName = item.nameKey?.let { equipmentName(it) }
    val description = if (
        item.isEquipped && equipmentDisplayName != null && rarityName != null
    ) {
        stringResource(
            R.string.shop_equipment_slot_equipped_description,
            slotName,
            equipmentDisplayName,
            rarityName,
        )
    } else {
        stringResource(R.string.shop_equipment_slot_empty_description, slotName)
    }
    Surface(
        modifier = modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .selectable(
                selected = item.isSelected,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .testTag("shop-equipment-slot-${item.slot.name.lowercase(Locale.US)}"),
        color = if (item.isSelected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (item.isSelected) 2.dp else 1.dp,
            color = if (item.isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier.padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (
                    item.isEquipped && equipmentDisplayName != null && rarityName != null
                ) {
                    EquipmentArtwork(
                        imageKey = item.imageKey,
                        type = item.type,
                        contentDescription = null,
                        modifier = Modifier.size(EquipmentSlotArtworkSize),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(EquipmentSlotEmptyIconSize)
                            .testTag(
                                "shop-equipment-slot-empty-icon-" +
                                    item.slot.name.lowercase(Locale.US),
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                Text(
                    text = item.slot.displayName(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.rarity != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .testTag("shop-slot-rarity-${item.slot.name.lowercase(Locale.US)}"),
                    color = item.rarity.indicatorColor(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {}
            }
        }
    }
}

@Composable
internal fun EquipmentSlotItem(
    item: ShopEquipmentSlotUiModel,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EquipmentSlot(
        item = item,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun StatSummary(
    summary: CharacterStatSummaryUiModel,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale >= CompactPreviewFontScale
    val cellHeight = if (largeFont) LargeFontStatCellHeight else StatCellHeight
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shop-character-stat-summary"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CharacterStatItem(
            label = stringResource(R.string.character_derived_attack),
            stat = summary.attack,
            icon = Icons.Default.Build,
            testTag = "shop-stat-attack",
            cellHeight = cellHeight,
            largeFont = largeFont,
            modifier = Modifier.weight(1f),
        )
        CharacterStatItem(
            label = stringResource(R.string.character_derived_max_hp),
            stat = summary.maxHp,
            icon = Icons.Default.Favorite,
            testTag = "shop-stat-max-hp",
            cellHeight = cellHeight,
            largeFont = largeFont,
            modifier = Modifier.weight(1f),
        )
        CharacterStatItem(
            label = stringResource(R.string.character_derived_defense),
            stat = summary.defense,
            icon = Icons.Default.Shield,
            testTag = "shop-stat-defense",
            cellHeight = cellHeight,
            largeFont = largeFont,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CharacterStatItem(
    label: String,
    stat: CharacterStatValueUiModel,
    icon: ImageVector,
    testTag: String,
    cellHeight: androidx.compose.ui.unit.Dp,
    largeFont: Boolean,
    modifier: Modifier = Modifier,
) {
    val formattedValue = formatNumber(stat.currentValue.toLong())
    val formattedDifference = formatSignedNumber(stat.difference)
    val description = if (stat.difference == 0) {
        stringResource(
            R.string.shop_stat_summary_description,
            label,
            formattedValue,
        )
    } else {
        stringResource(
            R.string.shop_stat_summary_delta_description,
            label,
            formattedValue,
            formattedDifference,
        )
    }
    Surface(
        modifier = modifier
            .height(cellHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .testTag(testTag),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (largeFont) LargeFontStatLabelHeight else StatLabelHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (largeFont) LargeFontStatCurrentHeight else StatCurrentHeight)
                    .testTag("$testTag-current"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formattedValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (largeFont) LargeFontStatDeltaHeight else StatDeltaHeight)
                    .testTag("$testTag-delta-slot"),
                contentAlignment = Alignment.Center,
            ) {
                if (stat.difference != 0) {
                    Text(
                        text = formattedDifference,
                        modifier = Modifier.testTag("$testTag-delta"),
                        color = if (stat.difference > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
internal fun CharacterStatSummary(
    summary: CharacterStatSummaryUiModel,
    modifier: Modifier = Modifier,
) {
    StatSummary(summary = summary, modifier = modifier)
}

@Composable
internal fun CategoryFilterRow(
    selected: EquipmentType?,
    enabled: Boolean,
    onSelect: (EquipmentType?) -> Unit,
) {
    val categories = listOf<EquipmentType?>(null) + EquipmentType.entries
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shop-category-row"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, category ->
            val selectedCategory = selected == category
            Box(
                modifier = Modifier
                    .height(CategoryTouchTargetHeight)
                    .selectable(
                        selected = selectedCategory,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = { onSelect(category) },
                    )
                    .testTag("shop-category-$index"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.height(CategoryPillHeight),
                    color = if (selectedCategory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (selectedCategory) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selectedCategory) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = category?.displayName()
                            ?: stringResource(R.string.shop_category_all),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleEquipmentHeader(
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.shop_sale_equipment)
    val count = stringResource(R.string.shop_filter_item_count, itemCount)
    val description = stringResource(R.string.shop_sale_equipment_count, itemCount)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("shop-sale-header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun ShopItemCard(
    item: ShopEquipmentUiModel,
    selected: Boolean,
    enabled: Boolean,
    isProcessing: Boolean,
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onAction: () -> Unit,
) {
    val displayName = equipmentName(item.nameKey)
    val largeFont = LocalDensity.current.fontScale >= CompactPreviewFontScale
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .testTag("shop-equipment-card-${item.equipmentId}"),
        color = if (item.isOwned) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                item.isOwned -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ShopCardContentInset),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ShopPrimaryActionHeight + ShopCardActionGap),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                EquipmentArtwork(
                    imageKey = item.imageKey,
                    type = item.type,
                    contentDescription = stringResource(
                        R.string.equipment_artwork_description,
                        displayName,
                    ),
                    modifier = Modifier.size(ShopItemArtworkSize),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.shop_item_type_and_rarity,
                            item.type.displayName(item.weaponType),
                            item.rarity.displayName(),
                        ),
                        modifier = Modifier.testTag("equipment-type-${item.equipmentId}"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.rarity.displayName(),
                        modifier = Modifier.testTag("equipment-rarity-${item.equipmentId}"),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EquipmentRequiredLevel(item = item)
                    item.modifiers.firstOrNull { it.amount > 0 }?.let { modifier ->
                        Text(
                            text = modifier.displayLine(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (largeFont) {
                                    LargeFontShopStatusHeight
                                } else {
                                    ShopStatusHeight
                                },
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        EquipmentStatusBadges(
                            equipmentId = item.equipmentId,
                            isForSale = item.isForSale,
                            isOwned = item.isOwned,
                            isEquipped = item.isEquipped,
                        )
                    }
                    ActionReasonCaption(
                        equipmentId = item.equipmentId,
                        reason = (item.action as? ShopEquipmentAction.PurchaseUnavailable)
                            ?.reason,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = ShopPrimaryActionWidth + ShopCardActionGap)
                    .testTag("shop-item-actions-${item.equipmentId}"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriceLabel(item = item)
                IconButton(
                    onClick = onOpenDetail,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("shop-equipment-detail-action-${item.equipmentId}"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(
                            R.string.shop_open_equipment_detail,
                            displayName,
                        ),
                    )
                }
            }
            EquipmentActionButton(
                action = item.action,
                enabled = enabled,
                isProcessing = isProcessing,
                onAction = onAction,
                modifier = Modifier.align(Alignment.BottomEnd),
                testTag = "shop-card-purchase-${item.equipmentId}",
            )
        }
    }
}

@Composable
private fun PriceLabel(item: ShopEquipmentUiModel) {
    val formattedPrice = formatNumber(item.price)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("shop-item-price-row-${item.equipmentId}"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.MonetizationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.shop_price, formattedPrice),
            modifier = Modifier.testTag("equipment-price-${item.equipmentId}"),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EquipmentActionButton(
    action: ShopEquipmentAction,
    enabled: Boolean,
    isProcessing: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    val actionLabel = action.displayLabel()
    val processingDescription = action.processingDescription()
    Button(
        onClick = onAction,
        enabled = enabled && action.isEnabled && !isProcessing,
        modifier = modifier
            .size(width = ShopPrimaryActionWidth, height = ShopPrimaryActionHeight)
            .testTag(testTag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        if (isProcessing && processingDescription != null) {
            ProcessingIndicator(description = processingDescription)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = actionLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionReasonCaption(
    equipmentId: Long,
    reason: PurchaseUnavailableReason?,
) {
    Text(
        text = reason?.displayText().orEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .height(ShopActionReasonHeight)
            .testTag("shop-action-reason-$equipmentId"),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EquipmentRequiredLevel(item: ShopEquipmentUiModel) {
    val requiredLevelText = stringResource(R.string.shop_required_level, item.requiredLevel)
    val levelInsufficientText = stringResource(R.string.shop_level_insufficient)
    val description = if (item.isRequiredLevelMet) {
        requiredLevelText
    } else {
        stringResource(R.string.shop_required_level_unmet_description, item.requiredLevel)
    }
    if (item.isRequiredLevelMet) {
        Text(
            text = requiredLevelText,
            modifier = Modifier.testTag("equipment-required-level-${item.equipmentId}"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Surface(
            modifier = Modifier
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag("equipment-required-level-${item.equipmentId}"),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("equipment-level-lock-${item.equipmentId}"),
                )
                Text(
                    text = requiredLevelText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = levelInsufficientText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun EquipmentPlaceholder(
    type: EquipmentType,
    modifier: Modifier = Modifier.size(64.dp),
    decorative: Boolean = false,
) {
    val typeName = type.displayName()
    val semanticModifier = if (decorative) {
        Modifier
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Surface(
        modifier = modifier.then(semanticModifier),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = type.placeholderIcon(),
                contentDescription = if (decorative) {
                    null
                } else {
                    stringResource(
                        R.string.equipment_placeholder_description,
                        typeName,
                    )
                },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("equipment-placeholder-${type.name.lowercase(Locale.US)}"),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EquipmentStatusBadges(
    equipmentId: Long? = null,
    isForSale: Boolean,
    isOwned: Boolean,
    isEquipped: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isForSale) {
            EquipmentBadge(text = stringResource(R.string.shop_not_for_sale))
        }
        if (isOwned) {
            OwnedEquipmentBadge(equipmentId = equipmentId)
        }
        if (isEquipped) EquipmentBadge(text = stringResource(R.string.shop_equipped_badge))
    }
}

@Composable
private fun OwnedEquipmentBadge(equipmentId: Long?) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (equipmentId == null) {
                            Modifier
                        } else {
                            Modifier.testTag("equipment-owned-icon-$equipmentId")
                        },
                    ),
            )
            Text(
                text = stringResource(R.string.shop_owned_badge),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun EquipmentBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EquipmentDetailContent(
    item: ShopEquipmentUiModel,
    currentGold: Long,
    isProcessing: Boolean,
    onAction: () -> Unit,
) {
    val availabilityReason = (item.action as? ShopEquipmentAction.PurchaseUnavailable)?.reason
    val displayName = equipmentName(item.nameKey)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
            .testTag("shop-equipment-detail-scroll"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.rarity.displayName(),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.type.displayName(item.weaponType),
                    modifier = Modifier.testTag("shop-equipment-detail-type"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        DetailSection(title = stringResource(R.string.shop_detail_description)) {
            Text(
                text = equipmentDescription(item.descriptionKey),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.shop_required_level, item.requiredLevel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DetailSection(title = stringResource(R.string.shop_detail_all_modifiers)) {
            item.modifiers.forEach { modifier ->
                Text(
                    text = modifier.displayLine(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        DetailSection(title = stringResource(R.string.shop_detail_same_slot)) {
            item.comparisons.forEach { comparison ->
                EquipmentComparisonRow(comparison)
            }
        }
        Text(
            text = stringResource(R.string.shop_price, formatNumber(item.price)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.shop_detail_current_gold, formatNumber(currentGold)),
            style = MaterialTheme.typography.bodyLarge,
        )
        EquipmentStatusBadges(
            isForSale = item.isForSale,
            isOwned = item.isOwned,
            isEquipped = item.isEquipped,
        )
        availabilityReason?.let { reason ->
            Text(
                text = reason.displayText(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ShopPrimaryActionHeight)
                .testTag("shop-detail-action-area"),
            contentAlignment = Alignment.BottomEnd,
        ) {
            EquipmentActionButton(
                action = item.action,
                enabled = true,
                isProcessing = isProcessing,
                onAction = onAction,
                testTag = "shop-detail-purchase",
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun EquipmentComparisonRow(comparison: EquipmentStatComparison) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = comparison.target.displayName(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                R.string.equipment_comparison_current,
                formatAmount(comparison.target, comparison.modifierType, comparison.currentAmount),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.equipment_comparison_candidate,
                formatAmount(comparison.target, comparison.modifierType, comparison.candidateAmount),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = comparison.displayDifference(),
            color = when {
                comparison.difference > 0 -> MaterialTheme.colorScheme.secondary
                comparison.difference < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PurchaseConfirmationDialog(
    confirmation: PurchaseConfirmationUiState,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = equipmentName(confirmation.equipmentNameKey)
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("shop-purchase-confirmation"),
        title = {
            Text(text = stringResource(R.string.shop_purchase_dialog_title, name))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.shop_purchase_slot, confirmation.slot.displayName()))
                Text(
                    text = stringResource(
                        R.string.shop_price,
                        formatNumber(confirmation.price),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.shop_purchase_current_gold,
                        formatNumber(confirmation.currentGold),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.shop_purchase_remaining_gold,
                        formatNumber(confirmation.expectedRemainingGold),
                    ),
                )
                if (isProcessing) {
                    ProcessingIndicator(description = stringResource(R.string.shop_purchase_processing))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isProcessing,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("shop-confirm-purchase"),
            ) {
                Text(text = stringResource(R.string.shop_purchase_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("shop-cancel-purchase"),
            ) {
                Text(text = stringResource(R.string.shop_purchase_cancel))
            }
        },
    )
}

@Composable
private fun PurchaseSuccessDialog(
    purchase: PurchaseState.Success,
    isEquipping: Boolean,
    onEquip: () -> Unit,
    onOpenInventory: () -> Unit,
    onContinue: () -> Unit,
) {
    val name = equipmentName(purchase.equipmentNameKey)
    AlertDialog(
        onDismissRequest = { if (!isEquipping) onContinue() },
        modifier = Modifier.testTag("shop-purchase-success"),
        title = {
            Text(text = stringResource(R.string.shop_purchase_success_title, name))
        },
        text = {
            Text(text = stringResource(R.string.shop_purchase_success_message))
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onEquip,
                    enabled = !isEquipping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    if (isEquipping) {
                        ProcessingIndicator(
                            description = stringResource(R.string.inventory_equipping_description),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.shop_equip_now))
                }
                OutlinedButton(
                    onClick = onOpenInventory,
                    enabled = !isEquipping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_move_to_inventory))
                }
                TextButton(
                    onClick = onContinue,
                    enabled = !isEquipping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_continue_shopping))
                }
            }
        },
    )
}

@Composable
internal fun FeedbackDialog(
    title: String,
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            if (canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_retry))
                }
            } else {
                Button(
                    onClick = { onDismiss?.invoke() },
                    enabled = onDismiss != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_confirm))
                }
            }
        },
        dismissButton = if (canRetry && onDismiss != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.shop_confirm))
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun LoadingState(
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
internal fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun ProcessingIndicator(description: String) {
    CircularProgressIndicator(
        modifier = Modifier
            .size(20.dp)
            .semantics { contentDescription = description },
        strokeWidth = 2.dp,
    )
}

@Composable
internal fun equipmentName(key: String): String = stringResource(equipmentNameResource(key))

@Composable
internal fun equipmentDescription(key: String): String =
    stringResource(equipmentDescriptionResource(key))

@StringRes
private fun equipmentNameResource(key: String): Int = when (key) {
    "equipment_name_worn_sword" -> R.string.equipment_name_worn_sword
    "equipment_name_iron_longsword" -> R.string.equipment_name_iron_longsword
    "equipment_name_ash_spear" -> R.string.equipment_name_ash_spear
    "equipment_name_steel_mace" -> R.string.equipment_name_steel_mace
    "equipment_name_leather_hat" -> R.string.equipment_name_leather_hat
    "equipment_name_iron_helmet" -> R.string.equipment_name_iron_helmet
    "equipment_name_cloth_top" -> R.string.equipment_name_cloth_top
    "equipment_name_leather_armor" -> R.string.equipment_name_leather_armor
    "equipment_name_iron_breastplate" -> R.string.equipment_name_iron_breastplate
    "equipment_name_cloth_pants" -> R.string.equipment_name_cloth_pants
    "equipment_name_leather_pants" -> R.string.equipment_name_leather_pants
    "equipment_name_steel_greaves" -> R.string.equipment_name_steel_greaves
    "equipment_name_leather_gloves" -> R.string.equipment_name_leather_gloves
    "equipment_name_steel_gauntlets" -> R.string.equipment_name_steel_gauntlets
    "equipment_name_travelers_boots" -> R.string.equipment_name_travelers_boots
    "equipment_name_windwalker_boots" -> R.string.equipment_name_windwalker_boots
    "equipment_name_mage_ring" -> R.string.equipment_name_mage_ring
    "equipment_name_guardian_necklace" -> R.string.equipment_name_guardian_necklace
    "equipment_name_adventure_sword" -> R.string.equipment_name_adventure_sword
    "equipment_name_adventure_hat" -> R.string.equipment_name_adventure_hat
    "equipment_name_adventure_jacket" -> R.string.equipment_name_adventure_jacket
    "equipment_name_adventure_pants" -> R.string.equipment_name_adventure_pants
    "equipment_name_adventure_gloves" -> R.string.equipment_name_adventure_gloves
    "equipment_name_adventure_shoes" -> R.string.equipment_name_adventure_shoes
    "equipment_name_adventure_accessory" -> R.string.equipment_name_adventure_accessory
    else -> R.string.equipment_unknown_name
}

@StringRes
private fun equipmentDescriptionResource(key: String): Int = when (key) {
    "equipment_description_worn_sword" -> R.string.equipment_description_worn_sword
    "equipment_description_iron_longsword" -> R.string.equipment_description_iron_longsword
    "equipment_description_ash_spear" -> R.string.equipment_description_ash_spear
    "equipment_description_steel_mace" -> R.string.equipment_description_steel_mace
    "equipment_description_leather_hat" -> R.string.equipment_description_leather_hat
    "equipment_description_iron_helmet" -> R.string.equipment_description_iron_helmet
    "equipment_description_cloth_top" -> R.string.equipment_description_cloth_top
    "equipment_description_leather_armor" -> R.string.equipment_description_leather_armor
    "equipment_description_iron_breastplate" -> R.string.equipment_description_iron_breastplate
    "equipment_description_cloth_pants" -> R.string.equipment_description_cloth_pants
    "equipment_description_leather_pants" -> R.string.equipment_description_leather_pants
    "equipment_description_steel_greaves" -> R.string.equipment_description_steel_greaves
    "equipment_description_leather_gloves" -> R.string.equipment_description_leather_gloves
    "equipment_description_steel_gauntlets" -> R.string.equipment_description_steel_gauntlets
    "equipment_description_travelers_boots" -> R.string.equipment_description_travelers_boots
    "equipment_description_windwalker_boots" -> R.string.equipment_description_windwalker_boots
    "equipment_description_mage_ring" -> R.string.equipment_description_mage_ring
    "equipment_description_guardian_necklace" -> R.string.equipment_description_guardian_necklace
    "equipment_description_adventure_sword" -> R.string.equipment_description_adventure_sword
    "equipment_description_adventure_hat" -> R.string.equipment_description_adventure_hat
    "equipment_description_adventure_jacket" -> R.string.equipment_description_adventure_jacket
    "equipment_description_adventure_pants" -> R.string.equipment_description_adventure_pants
    "equipment_description_adventure_gloves" -> R.string.equipment_description_adventure_gloves
    "equipment_description_adventure_shoes" -> R.string.equipment_description_adventure_shoes
    "equipment_description_adventure_accessory" ->
        R.string.equipment_description_adventure_accessory
    else -> R.string.equipment_unknown_description
}

@Composable
internal fun EquipmentType.displayName(): String = stringResource(
    when (this) {
        EquipmentType.WEAPON -> R.string.equipment_type_weapon
        EquipmentType.HELMET -> R.string.equipment_type_helmet
        EquipmentType.CHEST -> R.string.equipment_type_chest
        EquipmentType.LEGS -> R.string.equipment_type_legs
        EquipmentType.GLOVES -> R.string.equipment_type_gloves
        EquipmentType.SHOES -> R.string.equipment_type_shoes
        EquipmentType.ACCESSORY -> R.string.equipment_type_accessory
    },
)

@Composable
internal fun EquipmentType.displayName(weaponType: WeaponType?): String {
    val typeName = displayName()
    return if (this == EquipmentType.WEAPON && weaponType != null) {
        stringResource(
            R.string.equipment_type_with_weapon_type,
            typeName,
            weaponType.displayName(),
        )
    } else {
        typeName
    }
}

@Composable
internal fun WeaponType.displayName(): String = stringResource(
    when (this) {
        WeaponType.LONGSWORD -> R.string.equipment_weapon_type_longsword
        WeaponType.DAGGER -> R.string.equipment_weapon_type_dagger
        WeaponType.SPEAR -> R.string.equipment_weapon_type_spear
        WeaponType.BLUNT -> R.string.equipment_weapon_type_blunt
    },
)

@Composable
internal fun EquipmentSlot.displayName(): String = stringResource(
    when (this) {
        EquipmentSlot.WEAPON -> R.string.equipment_type_weapon
        EquipmentSlot.HELMET -> R.string.equipment_type_helmet
        EquipmentSlot.CHEST -> R.string.equipment_type_chest
        EquipmentSlot.LEGS -> R.string.equipment_type_legs
        EquipmentSlot.GLOVES -> R.string.equipment_type_gloves
        EquipmentSlot.SHOES -> R.string.equipment_type_shoes
        EquipmentSlot.ACCESSORY -> R.string.equipment_type_accessory
    },
)

@Composable
internal fun EquipmentRarity.displayName(): String = stringResource(
    when (this) {
        EquipmentRarity.COMMON -> R.string.equipment_rarity_common
        EquipmentRarity.UNCOMMON -> R.string.equipment_rarity_uncommon
        EquipmentRarity.RARE -> R.string.equipment_rarity_rare
        EquipmentRarity.EPIC -> R.string.equipment_rarity_epic
        EquipmentRarity.LEGENDARY -> R.string.equipment_rarity_legendary
    },
)

@Composable
private fun EquipmentRarity.indicatorColor() = when (this) {
    EquipmentRarity.COMMON -> MaterialTheme.colorScheme.outline
    EquipmentRarity.UNCOMMON -> MaterialTheme.colorScheme.secondary
    EquipmentRarity.RARE -> MaterialTheme.colorScheme.primary
    EquipmentRarity.EPIC -> MaterialTheme.colorScheme.tertiary
    EquipmentRarity.LEGENDARY -> MaterialTheme.colorScheme.error
}

@Composable
internal fun EquipmentStatModifier.displayLine(): String = stringResource(
    R.string.equipment_modifier_line,
    target.displayName(),
    formatSignedAmount(target, type, amount),
)

@Composable
private fun StatTarget.displayName(): String = stringResource(
    when (this) {
        is StatTarget.Base -> when (type) {
            StatType.STRENGTH -> R.string.character_stat_strength
            StatType.VITALITY -> R.string.character_stat_vitality
            StatType.FOCUS -> R.string.character_stat_focus
            StatType.WILLPOWER -> R.string.character_stat_willpower
        }

        is StatTarget.Derived -> when (type) {
            DerivedStatType.MAX_HP -> R.string.character_derived_max_hp
            DerivedStatType.ATTACK -> R.string.character_derived_attack
            DerivedStatType.DEFENSE -> R.string.character_derived_defense
            DerivedStatType.CRITICAL_CHANCE -> R.string.character_derived_critical_chance
            DerivedStatType.CRITICAL_DAMAGE -> R.string.character_derived_critical_damage
            DerivedStatType.STATUS_RESISTANCE -> R.string.character_derived_status_resistance
            DerivedStatType.HP_RECOVERY -> R.string.character_derived_hp_recovery
            DerivedStatType.GOLD_GAIN_BONUS -> R.string.character_derived_gold_gain_bonus
        }
    },
)

@Composable
private fun PurchaseUnavailableReason.displayText(): String = when (this) {
    is PurchaseUnavailableReason.UnsupportedSlot -> stringResource(
        R.string.shop_purchase_reason_unsupported_slot,
        type.displayName(),
        slot.displayName(),
    )

    PurchaseUnavailableReason.NotForSale ->
        stringResource(R.string.shop_purchase_reason_not_for_sale)
    PurchaseUnavailableReason.AlreadyOwned ->
        stringResource(R.string.shop_purchase_reason_already_owned)
    is PurchaseUnavailableReason.LevelTooLow -> stringResource(
        R.string.shop_purchase_reason_level_too_low,
        requiredLevel,
        characterLevel,
    )

    is PurchaseUnavailableReason.InsufficientGold -> stringResource(
        R.string.shop_purchase_reason_insufficient_gold,
        formatNumber(price),
        formatNumber(availableGold),
    )
}

@Composable
private fun ShopEquipmentAction.displayLabel(): String = stringResource(
    when (labelKey) {
        ShopEquipmentActionLabelKey.PURCHASE -> R.string.shop_detail_purchase
        ShopEquipmentActionLabelKey.PURCHASE_UNAVAILABLE ->
            R.string.shop_detail_purchase_unavailable
        ShopEquipmentActionLabelKey.EQUIP -> R.string.inventory_equip
        ShopEquipmentActionLabelKey.UNEQUIP -> R.string.equipment_unequip
    },
)

@Composable
private fun ShopEquipmentAction.processingDescription(): String? = when (this) {
    is ShopEquipmentAction.Purchase -> stringResource(R.string.shop_purchase_processing)
    is ShopEquipmentAction.PurchaseUnavailable -> null
    is ShopEquipmentAction.Equip -> stringResource(R.string.inventory_equipping_description)
    is ShopEquipmentAction.Unequip -> stringResource(R.string.equipment_unequipping_description)
}

private fun ShopUiState.isProcessing(action: ShopEquipmentAction): Boolean = when (action) {
    is ShopEquipmentAction.Purchase ->
        (purchaseState as? PurchaseState.Processing)?.equipmentId == action.equipmentId
    is ShopEquipmentAction.PurchaseUnavailable -> false
    is ShopEquipmentAction.Equip -> (equipState as? ShopEquipState.Processing)?.let { state ->
        state.ownedEquipmentId == action.ownedEquipmentId && state.targetSlot == action.slot
    } == true
    is ShopEquipmentAction.Unequip ->
        (unequipState as? ShopUnequipState.Processing)?.let { state ->
            state.equipmentId == action.equipmentId && state.slot == action.slot
        } == true
}

@Composable
private fun EquipFailure.displayText(): String = when (this) {
    EquipFailure.OwnedEquipmentNotFound -> stringResource(R.string.shop_equip_reason_not_found)
    EquipFailure.NotOwnedByCharacter -> stringResource(R.string.shop_equip_reason_not_owned)
    is EquipFailure.SlotMismatch -> stringResource(
        R.string.shop_equip_reason_slot_mismatch,
        type.displayName(),
        equipmentSlot.displayName(),
        targetSlot.displayName(),
    )

    EquipFailure.CommandFailed -> stringResource(R.string.shop_equip_reason_command_failed)
}

@Composable
private fun EquipmentStatComparison.displayDifference(): String = when {
    difference == 0 -> stringResource(R.string.equipment_comparison_no_change)
    difference > 0 -> "+${formatAmount(target, modifierType, difference)}"
    else -> "-${formatAmount(target, modifierType, difference.absoluteValue)}"
}

private fun EquipmentType.placeholderIcon(): ImageVector = when (this) {
    EquipmentType.WEAPON -> Icons.Default.Build
    EquipmentType.HELMET -> Icons.Default.Face
    EquipmentType.CHEST -> Icons.Default.Checkroom
    EquipmentType.LEGS -> Icons.Default.AccessibilityNew
    EquipmentType.GLOVES -> Icons.Default.BackHand
    EquipmentType.SHOES -> Icons.AutoMirrored.Filled.DirectionsWalk
    EquipmentType.ACCESSORY -> Icons.Default.Star
}

private fun formatSignedAmount(
    target: StatTarget,
    modifierType: ModifierType,
    amount: Int,
): String = if (amount >= 0) {
    "+${formatAmount(target, modifierType, amount)}"
} else {
    "-${formatAmount(target, modifierType, amount.absoluteValue)}"
}

private fun formatAmount(
    target: StatTarget,
    modifierType: ModifierType,
    amount: Int,
): String = if (modifierType == ModifierType.PERCENT_ADD || target.isBasisPointStat()) {
    String.format(Locale.US, "%.1f%%", amount / 100.0)
} else {
    formatNumber(amount.toLong())
}

private fun StatTarget.isBasisPointStat(): Boolean = this is StatTarget.Derived && type in setOf(
    DerivedStatType.CRITICAL_CHANCE,
    DerivedStatType.CRITICAL_DAMAGE,
    DerivedStatType.STATUS_RESISTANCE,
    DerivedStatType.GOLD_GAIN_BONUS,
)

internal fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

private fun formatSignedNumber(value: Int): String = when {
    value > 0 -> "+${formatNumber(value.toLong())}"
    value < 0 -> "-${formatNumber(value.toLong().absoluteValue)}"
    else -> "0"
}

private val RegularLeftEquipmentSlotOrder = listOf(
    EquipmentSlot.HELMET,
    EquipmentSlot.CHEST,
    EquipmentSlot.LEGS,
)

private val RegularRightEquipmentSlotOrder = listOf(
    EquipmentSlot.WEAPON,
    EquipmentSlot.GLOVES,
    EquipmentSlot.ACCESSORY,
)

private val CompactPreviewWidth = 340.dp
private const val CompactPreviewFontScale = 1.5f
private val MerchantBannerHeight = 88.dp
private val MerchantSpriteSize = 60.dp
private val RegularCharacterSize = 144.dp
private val CompactCharacterSize = 120.dp
private val RegularEquipmentSlotSize = 68.dp
private val CompactEquipmentSlotSize = 64.dp
private val EquipmentSlotArtworkSize = 38.dp
private val EquipmentSlotEmptyIconSize = 24.dp
private val CategoryTouchTargetHeight = 48.dp
private val CategoryPillHeight = 40.dp
private val ShopItemArtworkSize = 76.dp
private val StatCellHeight = 112.dp
private val LargeFontStatCellHeight = 170.dp
private val StatLabelHeight = 16.dp
private val LargeFontStatLabelHeight = 32.dp
private val StatCurrentHeight = 28.dp
private val LargeFontStatCurrentHeight = 56.dp
private val StatDeltaHeight = 20.dp
private val LargeFontStatDeltaHeight = 40.dp
private val ShopPrimaryActionWidth = 104.dp
private val ShopPrimaryActionHeight = 48.dp
private val ShopCardContentInset = 12.dp
private val ShopCardActionGap = 12.dp
private val ShopActionReasonHeight = 36.dp
private val ShopStatusHeight = 28.dp
private val LargeFontShopStatusHeight = 52.dp

@Preview(name = "상점", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
private fun ShopPreview() {
    TodoQuestTheme {
        ShopContent(
            state = previewShopState(),
            onEvent = {},
            onOpenInventory = {},
        )
    }
}

@Preview(
    name = "상점 320dp 큰 글꼴",
    widthDp = 320,
    heightDp = 640,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun ShopSmallLargeFontPreview() {
    TodoQuestTheme {
        ShopContent(
            state = previewShopState(),
            onEvent = {},
            onOpenInventory = {},
        )
    }
}

private fun previewShopState(): ShopUiState {
    val helmet = ShopEquipmentUiModel(
        equipmentId = 1_003L,
        nameKey = "equipment_name_leather_hat",
        descriptionKey = "equipment_description_leather_hat",
        type = EquipmentType.HELMET,
        slot = EquipmentSlot.HELMET,
        rarity = EquipmentRarity.COMMON,
        price = 27L,
        requiredLevel = 1,
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
        isForSale = true,
        isOwned = true,
        isEquipped = true,
        purchaseAvailability = PurchaseAvailability.Unavailable(
            PurchaseUnavailableReason.AlreadyOwned,
        ),
        action = ShopEquipmentAction.Unequip(
            equipmentId = 1_003L,
            slot = EquipmentSlot.HELMET,
        ),
    )
    val breastplate = ShopEquipmentUiModel(
        equipmentId = 1_007L,
        nameKey = "equipment_name_iron_breastplate",
        descriptionKey = "equipment_description_iron_breastplate",
        type = EquipmentType.CHEST,
        slot = EquipmentSlot.CHEST,
        rarity = EquipmentRarity.EPIC,
        price = 1_200L,
        requiredLevel = 24,
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
        isForSale = true,
        isOwned = false,
        isEquipped = false,
        purchaseAvailability = PurchaseAvailability.Available,
        action = ShopEquipmentAction.Purchase(equipmentId = 1_007L),
    )
    return ShopUiState(
        isLoading = false,
        currentGold = 12_345L,
        characterLevel = 30,
        equipmentSlots = emptyShopEquipmentSlots().map { slot ->
            if (slot.slot == EquipmentSlot.HELMET) {
                slot.copy(
                    equipmentId = helmet.equipmentId,
                    nameKey = helmet.nameKey,
                    rarity = helmet.rarity,
                    imageKey = helmet.imageKey,
                    isEquipped = true,
                )
            } else if (slot.slot == EquipmentSlot.WEAPON) {
                slot.copy(
                    equipmentId = 1_001L,
                    nameKey = "equipment_name_worn_sword",
                    rarity = EquipmentRarity.COMMON,
                    imageKey = "weapon_worn_sword",
                    weaponType = WeaponType.LONGSWORD,
                    isEquipped = true,
                )
            } else if (slot.slot == EquipmentSlot.CHEST) {
                slot.copy(isSelected = true)
            } else {
                slot
            }
        },
        statSummary = CharacterStatSummaryUiModel(
            attack = CharacterStatValueUiModel(currentValue = 37, difference = 0),
            maxHp = CharacterStatValueUiModel(currentValue = 245, difference = 50),
            defense = CharacterStatValueUiModel(currentValue = 18, difference = 9),
        ),
        selectedEquipmentId = breastplate.equipmentId,
        items = listOf(helmet, breastplate),
    )
}
