package com.todoquest.feature.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.WeaponType
import com.todoquest.ui.theme.TodoQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InventoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ownedHelmetsUseDistinctArtworkAndKoreanDescriptionsWhileUnknownHelmetFallsBack() {
        val leather = inventoryItem(
            ownedEquipmentId = 13L,
            equipmentId = 1_003L,
            nameKey = "equipment_name_leather_hat",
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            imageKey = "headgear_leather_hat",
        )
        val iron = inventoryItem(
            ownedEquipmentId = 14L,
            equipmentId = 1_004L,
            nameKey = "equipment_name_iron_helmet",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            imageKey = "headgear_iron_helmet",
        )
        val fallback = inventoryItem(
            ownedEquipmentId = 15L,
            equipmentId = 1_004L,
            nameKey = "equipment_name_iron_helmet",
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            imageKey = "equipment_image_unknown",
        )
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = InventoryUiState(
                        isLoading = false,
                        items = listOf(leather, iron, fallback),
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_leather_hat",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("가죽 모자 이미지").assertExists()
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_iron_helmet",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("철 투구 이미지").assertExists()
        composeRule.onNodeWithTag(
            "equipment-placeholder-helmet",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("투구 기본 이미지").assertExists()
    }

    @Test
    fun everyOwnedOutfitUsesArtworkTagAndKoreanImageDescription() {
        val fixtures = outfitArtworkFixtures()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = InventoryUiState(
                        isLoading = false,
                        items = fixtures.map(InventoryOutfitArtworkFixture::item),
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        fixtures.forEach { fixture ->
            composeRule.onNodeWithTag("inventory-list")
                .performScrollToNode(
                    hasTestTag("inventory-equipment-${fixture.item.ownedEquipmentId}"),
                )
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()
        }
    }

    @Test
    fun everyOwnedGloveAndShoeUsesArtworkTagAndKoreanImageDescription() {
        val fixtures = glovesShoesArtworkFixtures()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = InventoryUiState(
                        isLoading = false,
                        items = fixtures.map(InventoryOutfitArtworkFixture::item),
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        fixtures.forEach { fixture ->
            composeRule.onNodeWithTag("inventory-list")
                .performScrollToNode(
                    hasTestTag("inventory-equipment-${fixture.item.ownedEquipmentId}"),
                )
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()
        }
    }

    @Test
    fun ownedWeaponsUseArtworkKoreanSubtypeAndOneWeaponSlotReplacement() {
        val fixtures = weaponArtworkFixtures()
        val equipped = fixtures.first().item.copy(isEquipped = true)
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(equipped) + fixtures.drop(1).map(InventoryWeaponArtworkFixture::item),
                equippedBySlot = mapOf(EquipmentSlot.WEAPON to equipped),
            ),
        )
        val events = mutableListOf<InventoryEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = events::add,
                    onBack = {},
                )
            }
        }

        fixtures.forEachIndexed { index, fixture ->
            composeRule.onNodeWithTag("inventory-list")
                .performScrollToNode(
                    hasTestTag("inventory-equipment-${fixture.item.ownedEquipmentId}"),
                )
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()
            composeRule.onNodeWithTag(
                "inventory-equipment-type-${fixture.item.ownedEquipmentId}",
            ).assertTextContains("무기 · ${fixture.weaponTypeName}")
            composeRule.onNodeWithTag(
                "inventory-equipment-rarity-${fixture.item.ownedEquipmentId}",
            ).assertTextContains(fixture.rarityName)
            composeRule.onNodeWithText(fixture.modifierText).assertExists()
            if (index == 0) {
                composeRule.onNodeWithTag("inventory-equip-${fixture.item.ownedEquipmentId}")
                    .assertIsEnabled()
                    .assertTextContains("해제")
            } else {
                composeRule.onNodeWithTag("inventory-equip-${fixture.item.ownedEquipmentId}")
                    .assertIsEnabled()
            }
        }

        val mace = fixtures.last().item.copy(isEquipped = true)
        composeRule.onNodeWithTag("inventory-equip-${mace.ownedEquipmentId}").performClick()
        assertEquals(
            InventoryEvent.SelectOwnedEquipment(mace.ownedEquipmentId),
            events[events.lastIndex - 1],
        )
        assertEquals(InventoryEvent.EquipSelected, events.last())
        composeRule.runOnIdle {
            state.value = state.value.copy(
                items = state.value.items.map { item ->
                    item.copy(isEquipped = item.ownedEquipmentId == mace.ownedEquipmentId)
                },
                equippedBySlot = mapOf(EquipmentSlot.WEAPON to mace),
                equipResult = InventoryEquipResult.Success(
                    ownedEquipmentId = mace.ownedEquipmentId,
                    equipmentId = mace.equipmentId,
                    slot = EquipmentSlot.WEAPON,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("“강철 철퇴” 장비를 장착했습니다.").assertIsDisplayed()
    }

    @Test
    fun ownedGloveReplacementKeepsBothArtworkCardsAndReportsNewKoreanName() {
        val leather = inventoryItem(
            ownedEquipmentId = 21L,
            equipmentId = 1_011L,
            nameKey = "equipment_name_leather_gloves",
            rarity = EquipmentRarity.UNCOMMON,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            imageKey = "gloves_leather",
            isEquipped = true,
        )
        val steel = inventoryItem(
            ownedEquipmentId = 22L,
            equipmentId = 1_015L,
            nameKey = "equipment_name_steel_gauntlets",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            imageKey = "gloves_steel_gauntlets",
        )
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(leather, steel),
                equippedBySlot = mapOf(EquipmentSlot.GLOVES to leather),
            ),
        )
        val events = mutableListOf<InventoryEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = events::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("inventory-equip-21").assertIsEnabled()
        composeRule.onNodeWithTag("inventory-equip-22").assertIsEnabled().performClick()
        assertEquals(InventoryEvent.SelectOwnedEquipment(22L), events[events.lastIndex - 1])
        assertEquals(InventoryEvent.EquipSelected, events.last())

        composeRule.runOnIdle {
            val replacedLeather = leather.copy(isEquipped = false)
            val equippedSteel = steel.copy(isEquipped = true)
            state.value = InventoryUiState(
                isLoading = false,
                items = listOf(replacedLeather, equippedSteel),
                equippedBySlot = mapOf(EquipmentSlot.GLOVES to equippedSteel),
                equipResult = InventoryEquipResult.Success(
                    ownedEquipmentId = 22L,
                    equipmentId = 1_015L,
                    slot = EquipmentSlot.GLOVES,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inventory-equip-21").assertIsEnabled()
        composeRule.onNodeWithTag("inventory-equip-22").assertIsEnabled()
            .assertTextContains("해제")
        composeRule.onNodeWithText("“강철 건틀릿” 장비를 장착했습니다.").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_gloves_leather",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_gloves_steel_gauntlets",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun ownedHelmetReplacementStateKeepsBothArtworkCardsAndOneEquippedResult() {
        val leather = inventoryItem(
            ownedEquipmentId = 13L,
            equipmentId = 1_003L,
            nameKey = "equipment_name_leather_hat",
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            imageKey = "headgear_leather_hat",
            isEquipped = true,
        )
        val iron = inventoryItem(
            ownedEquipmentId = 14L,
            equipmentId = 1_004L,
            nameKey = "equipment_name_iron_helmet",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            imageKey = "headgear_iron_helmet",
        )
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(leather, iron),
                equippedBySlot = mapOf(EquipmentSlot.HELMET to leather),
            ),
        )
        val events = mutableListOf<InventoryEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = events::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("inventory-equip-13").assertIsEnabled()
        composeRule.onNodeWithTag("inventory-equip-14").assertIsEnabled().performClick()
        assertEquals(InventoryEvent.SelectOwnedEquipment(14L), events[events.lastIndex - 1])
        assertEquals(InventoryEvent.EquipSelected, events.last())

        composeRule.runOnIdle {
            val replacedLeather = leather.copy(isEquipped = false)
            val equippedIron = iron.copy(isEquipped = true)
            state.value = InventoryUiState(
                isLoading = false,
                items = listOf(replacedLeather, equippedIron),
                equippedBySlot = mapOf(EquipmentSlot.HELMET to equippedIron),
                equipResult = InventoryEquipResult.Success(
                    ownedEquipmentId = 14L,
                    equipmentId = 1_004L,
                    slot = EquipmentSlot.HELMET,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inventory-equip-13").assertIsEnabled()
        composeRule.onNodeWithTag("inventory-equip-14").assertIsEnabled()
            .assertTextContains("해제")
        composeRule.onNodeWithTag("inventory-equipped-14").assertIsDisplayed()
        composeRule.onNodeWithText("“철 투구” 장비를 장착했습니다.").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_leather_hat",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_iron_helmet",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun loadingEmptyAndLoadErrorStatesAreExplicitAndRetryable() {
        val state = mutableStateOf(InventoryUiState())
        val events = mutableListOf<InventoryEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = { events.add(it) },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("인벤토리 불러오는 중").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = InventoryUiState(isLoading = false)
        }
        composeRule.onNodeWithText("보유한 장비가 없습니다.").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = InventoryUiState(
                isLoading = false,
                error = InventoryError.LoadFailed,
                retryState = InventoryRetryState.Load,
            )
        }
        composeRule.onNodeWithText("인벤토리를 불러오지 못했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(InventoryEvent.Retry, events.last())
    }

    @Test
    fun ownedCardsShowEquippedBadgeAndEquipOrReplaceActions() {
        val events = mutableListOf<InventoryEvent>()
        val equipped = inventoryItem(
            ownedEquipmentId = 11L,
            equipmentId = 1_005L,
            nameKey = "equipment_name_cloth_top",
            imageKey = "top_cloth",
            isEquipped = true,
        )
        val replacement = inventoryItem(
            ownedEquipmentId = 12L,
            equipmentId = 1_007L,
            nameKey = "equipment_name_iron_breastplate",
            rarity = EquipmentRarity.EPIC,
            imageKey = "top_iron_breastplate",
            isEquipped = false,
        )
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = InventoryUiState(
                        isLoading = false,
                        items = listOf(equipped, replacement),
                        equippedBySlot = mapOf(EquipmentSlot.CHEST to equipped),
                    ),
                    onEvent = { events.add(it) },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("inventory-equipped-11").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-equip-11").assertIsEnabled()
            .assertTextContains("해제")
        composeRule.onNodeWithTag("inventory-equip-12").assertIsEnabled().performClick()
        assertEquals(InventoryEvent.SelectOwnedEquipment(12L), events[events.lastIndex - 1])
        assertEquals(InventoryEvent.EquipSelected, events.last())
        composeRule.onNodeWithText("교체").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("천 상의 이미지").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("철 흉갑 이미지").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_top_cloth",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_top_iron_breastplate",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun equippedItemUnequipEmitsSlotEventAndProcessingDisablesEveryEquipmentCommand() {
        val events = mutableListOf<InventoryEvent>()
        val equipped = inventoryItem(
            ownedEquipmentId = 11L,
            equipmentId = 1_005L,
            nameKey = "equipment_name_cloth_top",
            isEquipped = true,
        )
        val replacement = inventoryItem(
            ownedEquipmentId = 12L,
            equipmentId = 1_007L,
            nameKey = "equipment_name_iron_breastplate",
        )
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(equipped, replacement),
                equippedBySlot = mapOf(EquipmentSlot.CHEST to equipped),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = events::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("inventory-equip-11")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .assertTextContains("해제")
            .performClick()
        assertEquals(InventoryEvent.UnequipSlot(EquipmentSlot.CHEST), events.last())

        composeRule.runOnIdle {
            state.value = state.value.copy(
                processingState = InventoryProcessingState.Unequipping(EquipmentSlot.CHEST),
            )
        }
        composeRule.onNodeWithTag("inventory-equip-11").assertIsNotEnabled()
        composeRule.onNodeWithTag("inventory-equip-12").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("해제 처리 중").assertIsDisplayed()
    }

    @Test
    fun unequipSuccessAlreadyEmptyAndFailureFeedbackAreConsumedOrRetriedOnce() {
        val events = mutableListOf<InventoryEvent>()
        val item = inventoryItem(
            ownedEquipmentId = 11L,
            equipmentId = 1_005L,
            nameKey = "equipment_name_cloth_top",
            isEquipped = true,
        )
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(item),
                equippedBySlot = mapOf(EquipmentSlot.CHEST to item),
                unequipResult = InventoryUnequipResult.Success(
                    EquipmentSlot.CHEST,
                    changed = true,
                ),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = events::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("해제 완료").assertIsDisplayed()
        composeRule.onNodeWithText("장비를 해제했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        assertEquals(InventoryEvent.ConsumeUnequipResult, events.last())

        composeRule.runOnIdle {
            state.value = state.value.copy(
                unequipResult = InventoryUnequipResult.Success(
                    EquipmentSlot.CHEST,
                    changed = false,
                ),
            )
        }
        composeRule.onNodeWithText("이미 비어 있는 부위입니다. 안전하게 해제를 완료했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                unequipResult = InventoryUnequipResult.Failed(EquipmentSlot.CHEST),
                retryState = InventoryRetryState.Unequip(EquipmentSlot.CHEST),
            )
        }
        composeRule.onNodeWithText("해제 실패").assertIsDisplayed()
        composeRule.onNodeWithText("장비를 해제하지 못했습니다. 다시 시도할 수 있습니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(InventoryEvent.Retry, events.last())
    }

    @Test
    fun processingPreventsDuplicateEquipAndSuccessOrFailureIsAnnounced() {
        val events = mutableListOf<InventoryEvent>()
        val item = inventoryItem(
            ownedEquipmentId = 12L,
            equipmentId = 1_007L,
            nameKey = "equipment_name_iron_breastplate",
        )
        val state = mutableStateOf(
            InventoryUiState(
                isLoading = false,
                items = listOf(item),
                processingOwnedEquipmentId = 12L,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                InventoryContent(
                    state = state.value,
                    onEvent = { events.add(it) },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("inventory-equip-12").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("장착 처리 중").assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                processingOwnedEquipmentId = null,
                equipResult = InventoryEquipResult.Success(12L, 1_007L, EquipmentSlot.CHEST),
            )
        }
        composeRule.onNodeWithText("“철 흉갑” 장비를 장착했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        assertEquals(InventoryEvent.ConsumeEquipResult, events.last())

        composeRule.runOnIdle {
            state.value = state.value.copy(
                equipResult = null,
                error = InventoryError.EquipFailed,
                retryState = InventoryRetryState.Equip(12L, EquipmentSlot.CHEST),
            )
        }
        composeRule.onNodeWithText("장비를 장착하지 못했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(InventoryEvent.Retry, events.last())
    }

    @Test
    fun smallWidthLargeFontKeepsBackNameAndEquipActionReachable() {
        var backCount = 0
        val item = inventoryItem(
            ownedEquipmentId = 12L,
            equipmentId = 1_016L,
            nameKey = "equipment_name_windwalker_boots",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.SHOES,
            slot = EquipmentSlot.SHOES,
            imageKey = "shoes_windwalker_boots",
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        InventoryContent(
                            state = InventoryUiState(
                                isLoading = false,
                                items = listOf(item),
                            ),
                            onEvent = {},
                            onBack = { backCount += 1 },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("inventory-back").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, backCount)
        composeRule.onNodeWithTag("inventory-list")
            .performScrollToNode(hasTestTag("inventory-equip-12"))
        composeRule.onNodeWithText("바람걸음 장화").assertExists()
        composeRule.onNodeWithContentDescription("바람걸음 장화 이미지").assertExists()
        composeRule.onNodeWithTag("inventory-equip-12")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun inventoryItem(
        ownedEquipmentId: Long,
        equipmentId: Long,
        nameKey: String,
        rarity: EquipmentRarity = EquipmentRarity.COMMON,
        isEquipped: Boolean = false,
        type: EquipmentType = EquipmentType.CHEST,
        slot: EquipmentSlot = EquipmentSlot.CHEST,
        imageKey: String? = null,
        weaponType: WeaponType? = null,
    ) = InventoryEquipmentUiModel(
        ownedEquipmentId = ownedEquipmentId,
        equipmentId = equipmentId,
        nameKey = nameKey,
        descriptionKey = if (equipmentId == 1_005L) {
            "equipment_description_cloth_top"
        } else {
            "equipment_description_iron_breastplate"
        },
        type = type,
        slot = slot,
        weaponType = weaponType,
        rarity = rarity,
        modifiers = listOf(
            EquipmentStatModifier(
                itemId = equipmentId,
                target = StatTarget.Derived(DerivedStatType.MAX_HP),
                type = ModifierType.FLAT,
                amount = if (equipmentId == 1_005L) 12 else 50,
            ),
        ),
        comparisons = emptyList(),
        imageKey = imageKey,
        acquiredAtEpochMillis = 1_000L,
        isEquipped = isEquipped,
    )

    private fun outfitArtworkFixtures(): List<InventoryOutfitArtworkFixture> = listOf(
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 105L,
            equipmentId = 1_005L,
            nameKey = "equipment_name_cloth_top",
            type = EquipmentType.CHEST,
            slot = EquipmentSlot.CHEST,
            imageKey = "top_cloth",
            displayName = "천 상의",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 106L,
            equipmentId = 1_006L,
            nameKey = "equipment_name_leather_armor",
            type = EquipmentType.CHEST,
            slot = EquipmentSlot.CHEST,
            imageKey = "top_leather_armor",
            displayName = "가죽 갑옷",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 107L,
            equipmentId = 1_007L,
            nameKey = "equipment_name_iron_breastplate",
            type = EquipmentType.CHEST,
            slot = EquipmentSlot.CHEST,
            imageKey = "top_iron_breastplate",
            displayName = "철 흉갑",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 108L,
            equipmentId = 1_008L,
            nameKey = "equipment_name_cloth_pants",
            type = EquipmentType.LEGS,
            slot = EquipmentSlot.LEGS,
            imageKey = "bottom_cloth_pants",
            displayName = "천 바지",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 109L,
            equipmentId = 1_009L,
            nameKey = "equipment_name_leather_pants",
            type = EquipmentType.LEGS,
            slot = EquipmentSlot.LEGS,
            imageKey = "bottom_leather_pants",
            displayName = "가죽 바지",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 110L,
            equipmentId = 1_010L,
            nameKey = "equipment_name_steel_greaves",
            type = EquipmentType.LEGS,
            slot = EquipmentSlot.LEGS,
            imageKey = "bottom_steel_greaves",
            displayName = "강철 각반",
        ),
    )

    private fun glovesShoesArtworkFixtures(): List<InventoryOutfitArtworkFixture> = listOf(
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 111L,
            equipmentId = 1_011L,
            nameKey = "equipment_name_leather_gloves",
            rarity = EquipmentRarity.UNCOMMON,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            imageKey = "gloves_leather",
            displayName = "가죽 장갑",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 115L,
            equipmentId = 1_015L,
            nameKey = "equipment_name_steel_gauntlets",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            imageKey = "gloves_steel_gauntlets",
            displayName = "강철 건틀릿",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 112L,
            equipmentId = 1_012L,
            nameKey = "equipment_name_travelers_boots",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.SHOES,
            slot = EquipmentSlot.SHOES,
            imageKey = "shoes_travelers_boots",
            displayName = "여행자의 장화",
        ),
        inventoryOutfitArtworkFixture(
            ownedEquipmentId = 116L,
            equipmentId = 1_016L,
            nameKey = "equipment_name_windwalker_boots",
            rarity = EquipmentRarity.RARE,
            type = EquipmentType.SHOES,
            slot = EquipmentSlot.SHOES,
            imageKey = "shoes_windwalker_boots",
            displayName = "바람걸음 장화",
        ),
    )

    private fun weaponArtworkFixtures(): List<InventoryWeaponArtworkFixture> = listOf(
        inventoryWeaponArtworkFixture(
            ownedEquipmentId = 101L,
            equipmentId = 1_001L,
            nameKey = "equipment_name_worn_sword",
            weaponType = WeaponType.LONGSWORD,
            rarity = EquipmentRarity.COMMON,
            imageKey = "weapon_worn_sword",
            displayName = "낡은 검",
            weaponTypeName = "장검",
            rarityName = "일반",
            attack = 3,
        ),
        inventoryWeaponArtworkFixture(
            ownedEquipmentId = 102L,
            equipmentId = 1_002L,
            nameKey = "equipment_name_iron_longsword",
            weaponType = WeaponType.LONGSWORD,
            rarity = EquipmentRarity.RARE,
            imageKey = "weapon_iron_longsword",
            displayName = "철 장검",
            weaponTypeName = "장검",
            rarityName = "희귀",
            attack = 10,
        ),
        inventoryWeaponArtworkFixture(
            ownedEquipmentId = 117L,
            equipmentId = 1_017L,
            nameKey = "equipment_name_ash_spear",
            weaponType = WeaponType.SPEAR,
            rarity = EquipmentRarity.COMMON,
            imageKey = "weapon_ash_spear",
            displayName = "물푸레나무 창",
            weaponTypeName = "창",
            rarityName = "일반",
            attack = 4,
        ),
        inventoryWeaponArtworkFixture(
            ownedEquipmentId = 118L,
            equipmentId = 1_018L,
            nameKey = "equipment_name_steel_mace",
            weaponType = WeaponType.BLUNT,
            rarity = EquipmentRarity.RARE,
            imageKey = "weapon_steel_mace",
            displayName = "강철 철퇴",
            weaponTypeName = "둔기",
            rarityName = "희귀",
            attack = 12,
        ),
    )

    private fun inventoryWeaponArtworkFixture(
        ownedEquipmentId: Long,
        equipmentId: Long,
        nameKey: String,
        weaponType: WeaponType,
        rarity: EquipmentRarity,
        imageKey: String,
        displayName: String,
        weaponTypeName: String,
        rarityName: String,
        attack: Int,
    ) = InventoryWeaponArtworkFixture(
        item = inventoryItem(
            ownedEquipmentId = ownedEquipmentId,
            equipmentId = equipmentId,
            nameKey = nameKey,
            rarity = rarity,
            type = EquipmentType.WEAPON,
            slot = EquipmentSlot.WEAPON,
            imageKey = imageKey,
            weaponType = weaponType,
        ).copy(
            modifiers = listOf(
                EquipmentStatModifier(
                    itemId = equipmentId,
                    target = StatTarget.Derived(DerivedStatType.ATTACK),
                    type = ModifierType.FLAT,
                    amount = attack,
                ),
            ),
        ),
        imageKey = imageKey,
        displayName = displayName,
        weaponTypeName = weaponTypeName,
        rarityName = rarityName,
        modifierText = "공격력 +$attack",
    )

    private fun inventoryOutfitArtworkFixture(
        ownedEquipmentId: Long,
        equipmentId: Long,
        nameKey: String,
        rarity: EquipmentRarity = EquipmentRarity.COMMON,
        type: EquipmentType,
        slot: EquipmentSlot,
        imageKey: String,
        displayName: String,
    ) = InventoryOutfitArtworkFixture(
        item = inventoryItem(
            ownedEquipmentId = ownedEquipmentId,
            equipmentId = equipmentId,
            nameKey = nameKey,
            rarity = rarity,
            type = type,
            slot = slot,
            imageKey = imageKey,
        ),
        imageKey = imageKey,
        displayName = displayName,
    )

    private data class InventoryOutfitArtworkFixture(
        val item: InventoryEquipmentUiModel,
        val imageKey: String,
        val displayName: String,
    )

    private data class InventoryWeaponArtworkFixture(
        val item: InventoryEquipmentUiModel,
        val imageKey: String,
        val displayName: String,
        val weaponTypeName: String,
        val rarityName: String,
        val modifierText: String,
    )
}
