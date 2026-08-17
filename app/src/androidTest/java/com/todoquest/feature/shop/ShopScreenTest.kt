package com.todoquest.feature.shop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.R
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.data.repository.RoomCharacterRepository
import com.todoquest.data.repository.RoomEquipmentRepository
import com.todoquest.data.repository.RoomTaskRepository
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatComparison
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
import com.todoquest.domain.usecase.PurchaseEquipmentUseCase
import com.todoquest.domain.usecase.UnequipEquipmentUseCase
import com.todoquest.feature.battle.BattleMap
import com.todoquest.feature.battle.BattleMapDefaults
import com.todoquest.feature.battle.BattleMapUiState
import com.todoquest.feature.battle.BattleSpriteUiModel
import com.todoquest.feature.battle.BattleUnitType
import com.todoquest.feature.battle.BattleUnitUiModel
import com.todoquest.feature.character.CharacterContent
import com.todoquest.feature.character.CharacterUiState
import com.todoquest.ui.character.CharacterBitmapComposer
import com.todoquest.ui.character.CharacterLayerCatalog
import com.todoquest.ui.character.CharacterLayerSlot
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.theme.TodoQuestTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShopScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun redesignedShopUsesFixedTopBarOneLazyColumnAndRequiredSectionOrder() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(400.dp).height(720.dp)) {
                        ShopContent(
                            state = populatedState(),
                            onEvent = {},
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-back").assertDoesNotExist()
        val topBar = composeRule.onNodeWithTag("shop-top-bar").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("shop-equipment-list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("shop-equipment-list").assertCountEquals(1)

        composeRule.onNodeWithTag("shop-equipment-list").performScrollToIndex(0)
        val banner = composeRule.onNodeWithTag("shop-merchant-banner")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(80.dp)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(topBar.bottom <= banner.top)
        val preview = composeRule.onNodeWithTag("shop-equipment-preview-card")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(banner.top < preview.top)
        composeRule.onNodeWithTag("shop-merchant-sprite-frame", useUnmergedTree = true)
            .assertWidthIsEqualTo(60.dp)
            .assertHeightIsEqualTo(60.dp)
        composeRule.onNodeWithText("필요한 장비를 골라 보게.", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-sale-header"))
        val saleHeader = composeRule.onNodeWithTag("shop-sale-header")
            .fetchSemanticsNode().boundsInRoot
        val categories = composeRule.onNodeWithTag("shop-category-row")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(saleHeader.top < categories.top)
        composeRule.onNodeWithTag("shop-sale-header")
            .assertContentDescriptionEquals("판매 장비 2개")
            .assertTextContains("판매 장비")
            .assertTextContains("2개")
    }

    @Test
    fun shopkeeperGreetingIsTheFirstShopItemWithPixelArtworkAndMergedNonInteractiveSemantics() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = density.fontScale),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(400.dp).height(720.dp)) {
                        ShopContent(
                            state = populatedState(),
                            onEvent = {},
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list").performScrollToIndex(0)
        val greeting = composeRule.onNodeWithTag("shop-merchant-banner")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(
                "장비 상점 대장장이. 필요한 장비를 골라 보게.",
            )
            .assertHasNoClickAction()
        composeRule.onNodeWithText("대장장이", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "필요한 장비를 골라 보게.",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        val greetingBounds = greeting.fetchSemanticsNode().boundsInRoot
        val previewBounds = composeRule.onNodeWithTag("shop-character-preview")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(greetingBounds.top < previewBounds.top)

        composeRule.onNodeWithTag("shop-merchant-sprite-frame", useUnmergedTree = true)
            .assertWidthIsEqualTo(60.dp)
            .assertHeightIsEqualTo(60.dp)
        val rendered = composeRule.onNodeWithTag(
            "shop-merchant-sprite",
            useUnmergedTree = true,
        ).assertIsDisplayed().captureToImage().asAndroidBitmap()
        val source = BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources,
            R.drawable.todo_quest_blacksmith_shopkeeper_front_idle,
        )
        val sourceColors = buildSet {
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val color = source.getPixel(x, y)
                    if (color ushr 24 == 255) add(color)
                }
            }
        }
        val background = rendered.getPixel(0, 0)
        val renderedColors = buildSet {
            for (y in 0 until rendered.height) {
                for (x in 0 until rendered.width) add(rendered.getPixel(x, y))
            }
        }
        assertTrue(renderedColors.any(sourceColors::contains))
        assertTrue(
            "최근접 확대는 원본 팔레트 외 보간색을 만들지 않아야 합니다.",
            renderedColors.all { it == background || it in sourceColors },
        )
    }

    @Test
    fun merchantBannerKeepsSideBySideLayoutAndGrowsIntrinsicallyForLargeFont() {
        val largeFont = mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = if (largeFont.value) 1.5f else 1f,
                ),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        MerchantBanner()
                    }
                }
            }
        }

        fun bannerHeight() = composeRule.onNodeWithTag("shop-merchant-banner")
            .fetchSemanticsNode().boundsInRoot.height

        fun assertSideBySideWithoutOverlap() {
            val sprite = composeRule.onNodeWithTag(
                "shop-merchant-sprite-frame",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val copy = composeRule.onNodeWithTag(
                "shop-merchant-copy",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(sprite.right <= copy.left)
            assertTrue(sprite.top >= 0f)
            assertTrue(copy.top >= 0f)
        }

        assertSideBySideWithoutOverlap()
        val normalHeight = bannerHeight()
        composeRule.runOnIdle {
            largeFont.value = true
        }
        composeRule.waitForIdle()
        assertSideBySideWithoutOverlap()
        assertTrue(bannerHeight() >= normalHeight)
    }

    @Test
    fun shopkeeperGreetingDecodeFailureKeepsKoreanCopyAndBuildFallback() {
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    ShopkeeperGreeting(spriteResId = 0)
                }
            }
        }

        composeRule.onNodeWithTag("shop-merchant-banner")
            .assertContentDescriptionEquals(
                "장비 상점 대장장이. 필요한 장비를 골라 보게.",
            )
            .assertHasNoClickAction()
        composeRule.onNodeWithText("대장장이", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "필요한 장비를 골라 보게.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("shop-merchant-sprite-frame", useUnmergedTree = true)
            .assertWidthIsEqualTo(60.dp)
            .assertHeightIsEqualTo(60.dp)
        composeRule.onNodeWithTag("shop-merchant-fallback", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun helmetFilterAndDetailsUseDistinctArtworkWhileUnknownKeyKeepsHelmetFallback() {
        val leather = leatherHat()
        val iron = ironHelmet()
        val state = mutableStateOf(
            populatedState(
                selectedCategory = EquipmentType.HELMET,
            ).copy(items = listOf(leather, iron)),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-category-2").assertIsSelected()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_leather_hat",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("가죽 모자 이미지").assertExists()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_iron_helmet",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("철 투구 이미지").assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = null)
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = leather)
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(
            "equipment_artwork_headgear_leather_hat",
            useUnmergedTree = true,
        ).assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("가죽 모자 이미지").assertCountEquals(2)

        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = null)
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = iron)
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(
            "equipment_artwork_headgear_iron_helmet",
            useUnmergedTree = true,
        ).assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("철 투구 이미지").assertCountEquals(2)

        composeRule.runOnIdle {
            state.value = state.value.copy(
                selectedDetail = iron.copy(imageKey = "equipment_image_unknown"),
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(
            "equipment-placeholder-helmet",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("투구 기본 이미지").assertExists()
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsEnabled()
    }

    @Test
    fun weaponFilterListDetailCompactPreviewAndPurchaseActionsUseArtworkNamesAndSubtypes() {
        val fixtures = weaponArtworkFixtures()
        val state = mutableStateOf(
            populatedState(selectedCategory = EquipmentType.WEAPON).copy(
                equipmentSlots = emptyShopEquipmentSlots(selectedCategory = EquipmentType.WEAPON),
                items = fixtures.map(WeaponArtworkFixture::item),
            ),
        )
        val events = mutableListOf<ShopEvent>()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        ShopContent(
                            state = state.value,
                            onEvent = events::add,
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-1"))
        composeRule.onNodeWithTag("shop-category-1").assertIsSelected()
        fixtures.forEachIndexed { index, fixture ->
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToIndex(4 + index)
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertExists()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertExists()
            composeRule.onNodeWithTag(
                "equipment-type-${fixture.item.equipmentId}",
                useUnmergedTree = true,
            )
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains(fixture.weaponTypeName, substring = true)
                .assertTextContains(fixture.rarityName, substring = true)
            composeRule.onNodeWithTag(
                "equipment-price-${fixture.item.equipmentId}",
                useUnmergedTree = true,
            )
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains(fixture.priceText)
            composeRule.onNodeWithTag(
                "equipment-rarity-${fixture.item.equipmentId}",
                useUnmergedTree = true,
            )
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains(fixture.rarityName)
            composeRule.onNodeWithText(fixture.modifierText, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = fixture.item)
            }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertCountEquals(2)
            composeRule.onNodeWithTag("shop-equipment-detail-type")
                .assertTextContains("무기 · ${fixture.weaponTypeName}")
            composeRule.onNodeWithText(fixture.description).assertIsDisplayed()
            composeRule.onNodeWithTag("shop-equipment-detail-scroll")
                .performScrollToNode(hasTestTag("shop-detail-purchase"))
            composeRule.onNodeWithTag("shop-detail-purchase")
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertHeightIsAtLeast(48.dp)

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = null)
            }
            composeRule.waitForIdle()
        }

        fixtures.forEach { fixture ->
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    equipmentSlots = emptyShopEquipmentSlots(
                        selectedCategory = EquipmentType.WEAPON,
                    ).map { slot ->
                        if (slot.slot == EquipmentSlot.WEAPON) {
                            slot.equippedWith(fixture)
                        } else {
                            slot
                        }
                    },
                    items = emptyList(),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))
            composeRule.onNodeWithTag("shop-preview-compact-layout").assertExists()
            composeRule.onNodeWithTag("shop-equipment-slot-weapon").performScrollTo()
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onAllNodesWithContentDescription("${fixture.displayName} 이미지")
                .assertCountEquals(0)
            composeRule.onNodeWithTag("shop-equipment-slot-weapon")
                .assertContentDescriptionEquals(
                    "무기 · ${fixture.weaponTypeName} 슬롯, ${fixture.displayName}, " +
                        "${fixture.rarityName}, 장착 중",
                )
        }

        val purchased = fixtures[2]
        composeRule.runOnIdle {
            state.value = state.value.copy(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 117L,
                    equipmentId = purchased.item.equipmentId,
                    equipmentNameKey = purchased.item.nameKey,
                    type = EquipmentType.WEAPON,
                    slot = EquipmentSlot.WEAPON,
                    currentGold = 4_950L,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("바로 장착").assertIsDisplayed()
        composeRule.onNodeWithText("인벤토리로 이동").assertIsDisplayed()
        composeRule.onNodeWithText("계속 쇼핑").assertIsDisplayed().performClick()
        assertEquals(ShopEvent.ConsumePurchaseSuccess, events.last())
    }

    @Test
    fun purchaseEquipReplaceAndRepositoryRecreationRestoreWeaponsAcrossSharedScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            val fallback = CharacterLoadoutCatalog.defaultEquippedItems
            runBlocking {
                database.characterProfileDao().upsertEquippedItems(
                    CharacterMapper.fromDomain(CharacterMapper.DEFAULT_CHARACTER_ID, fallback),
                )
            }
            val repository = RoomEquipmentRepository(database, FixedClock)
            val viewModel = ShopViewModel(
                repository = repository,
                purchaseEquipment = PurchaseEquipmentUseCase(repository),
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            val inventoryViewModel = InventoryViewModel(
                repository = repository,
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            val renderSurface = mutableStateOf(OutfitRenderSurface.SHOP)
            var confirmedItems = fallback
            composeRule.setContent {
                TodoQuestTheme {
                    when (renderSurface.value) {
                        OutfitRenderSurface.SHOP -> ShopScreen(
                            viewModel = viewModel,
                            onOpenInventory = {},
                        )

                        OutfitRenderSurface.INVENTORY -> InventoryScreen(
                            viewModel = inventoryViewModel,
                            onBack = {},
                        )

                        OutfitRenderSurface.CHARACTER -> CharacterContent(
                            state = CharacterUiState(
                                isLoading = false,
                                appearance = CharacterLoadoutCatalog.defaultAppearance,
                                equippedItems = confirmedItems,
                            ),
                            onIncreaseStat = {},
                            onDecreaseStat = {},
                            onSaveStatAllocation = {},
                            onRequestStatReset = {},
                            onDismissStatReset = {},
                            onConfirmStatReset = {},
                            onDismissError = {},
                        )

                        OutfitRenderSurface.BATTLE -> BattleMap(
                            state = battleState(confirmedItems),
                        )
                    }
                }
            }

            waitForTag("shop-equipment-list")
            val initialAttack = runBlocking {
                repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first().derivedStats.attack
            }
            val fixturesById = weaponArtworkFixtures().associateBy { it.item.equipmentId }
            val replacementOrder = listOf(
                EquipmentCatalogSeeder.WORN_SWORD_ID,
                EquipmentCatalogSeeder.ASH_SPEAR_ID,
                EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
                EquipmentCatalogSeeder.STEEL_MACE_ID,
            )
            val attackValues = mutableListOf<Int>()

            replacementOrder.forEach { equipmentId ->
                val fixture = requireNotNull(fixturesById[equipmentId])
                selectCategory(viewModel, EquipmentType.WEAPON)
                purchaseAndEquipWeapon(viewModel, fixture)
                val snapshot = runBlocking {
                    repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                        it.renderedEquippedItems.weaponId == fixture.imageKey
                    }
                }
                confirmedItems = snapshot.renderedEquippedItems
                attackValues += snapshot.derivedStats.attack

                assertEquals(fallback.copy(weaponId = fixture.imageKey), confirmedItems)
                assertEquals(setOf(EquipmentSlot.WEAPON), snapshot.equippedBySlot.keys)
                assertEquals(
                    equipmentId,
                    snapshot.equippedBySlot.getValue(EquipmentSlot.WEAPON)
                        .ownedEquipment.equipment.id,
                )
                assertEquals(
                    fixture.item.weaponType,
                    snapshot.equippedBySlot.getValue(EquipmentSlot.WEAPON)
                        .ownedEquipment.equipment.weaponType,
                )
                assertEquals(
                    fixture.item.weaponType?.name,
                    runBlocking { database.equipmentDao().getEquipment(equipmentId)?.weaponType },
                )
                assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")
                composeRule.onNodeWithTag("shop-equipment-slot-weapon")
                    .assertContentDescriptionEquals(
                        "무기 · ${fixture.weaponTypeName} 슬롯, ${fixture.displayName}, " +
                            "${fixture.rarityName}, 장착 중",
                    )
            }

            assertEquals(4, attackValues.toSet().size)
            assertTrue(attackValues.all { it != initialAttack })
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-stat-summary"))
            composeRule.onNodeWithTag("shop-stat-attack")
                .assertTextContains(attackValues.last().toString())

            val restartedRepository = RoomEquipmentRepository(database, FixedClock)
            val restarted = runBlocking {
                restartedRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                    it.renderedEquippedItems.weaponId == CharacterLoadoutCatalog.WEAPON_STEEL_MACE
                }
            }
            confirmedItems = restarted.renderedEquippedItems
            assertEquals(CharacterLoadoutCatalog.WEAPON_STEEL_MACE, confirmedItems.weaponId)
            assertNull(runBlocking { database.characterProfileDao().getEquippedItems()?.weaponId })
            assertEquals(4, restarted.ownedEquipmentIds.intersect(replacementOrder.toSet()).size)

            val restartedCharacterRepository = RoomCharacterRepository(database, FixedClock)
            assertEquals(
                confirmedItems,
                runBlocking {
                    restartedCharacterRepository.observeCharacter(FixedClock.today()).first {
                        it.equippedItems == confirmedItems
                    }.equippedItems
                },
            )

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.INVENTORY }
            waitForTag("inventory-list")
            weaponArtworkFixtures().forEach { fixture ->
                val ownedEquipmentId = inventoryViewModel.uiState.value.items.single {
                    it.equipmentId == fixture.item.equipmentId
                }.ownedEquipmentId
                composeRule.onNodeWithTag("inventory-list")
                    .performScrollToNode(hasTestTag("inventory-equipment-$ownedEquipmentId"))
                composeRule.onNodeWithTag(
                    "equipment_artwork_${fixture.imageKey}",
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                    .assertIsDisplayed()
            }

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.CHARACTER }
            composeRule.waitForIdle()
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "equipped-character-sprite")

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.BATTLE }
            composeRule.waitForIdle()
            assertBattleSpriteContainsProjectedWeapon(confirmedItems)
        } finally {
            database.close()
        }
    }

    @Test
    fun gameplayWeaponsAreTopmostAndKeepTheFaceProtectedRegionUnchanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val composer = CharacterBitmapComposer(context.assets)
        val weaponIds = listOf(
            CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
        )
        val withoutWeapon = CharacterLoadoutCatalog.defaultEquippedItems.copy(weaponId = null)
        val baseline = composeCharacter(composer, withoutWeapon)

        weaponIds.forEach { weaponId ->
            val equipped = withoutWeapon.copy(weaponId = weaponId)
            val layers = CharacterLayerCatalog.resolve(
                CharacterRenderState(CharacterLoadoutCatalog.defaultAppearance, equipped),
            )
            assertEquals(CharacterLayerSlot.WEAPON_FRONT, layers.last().slot)
            assertEquals("character/layers/$weaponId.png", layers.last().assetPath)

            val weapon = loadCharacterLayer("$weaponId.png")
            val composite = composeCharacter(composer, equipped)
            var opaqueWeaponPixels = 0
            for (y in 0 until CharacterCanvasSize) {
                for (x in 0 until CharacterCanvasSize) {
                    val weaponPixel = weapon.getPixel(x, y)
                    if (weaponPixel ushr 24 != 0) {
                        assertEquals(
                            "$weaponId must cover every earlier character layer at ($x, $y)",
                            weaponPixel,
                            composite.getPixel(x, y),
                        )
                        opaqueWeaponPixels += 1
                    }
                }
            }
            assertTrue("$weaponId must contain opaque pixels", opaqueWeaponPixels > 0)

            for (y in WeaponFaceProtectedTop..WeaponFaceProtectedBottom) {
                for (x in WeaponFaceProtectedLeft..WeaponFaceProtectedRight) {
                    assertEquals(
                        "$weaponId must not enter the face protected region at ($x, $y)",
                        0,
                        weapon.getPixel(x, y) ushr 24,
                    )
                    assertEquals(
                        "$weaponId must preserve the composed face at ($x, $y)",
                        baseline.getPixel(x, y),
                        composite.getPixel(x, y),
                    )
                }
            }
        }
    }

    @Test
    fun missingWeaponArtworkDecodeKeepsTaskRewardPurchaseAndEquipTransactionsUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = EquipmentArtworkLoader(context.assets)
        assertNull(
            loader.load(
                EquipmentArtworkDefinition(
                    imageKey = "missing-weapon",
                    assetPath = "character/layers/missing-weapon.png",
                ),
            ),
        )
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            runBlocking {
                database.characterProfileDao().upsertEquippedItems(
                    CharacterMapper.fromDomain(
                        CharacterMapper.DEFAULT_CHARACTER_ID,
                        CharacterLoadoutCatalog.defaultEquippedItems,
                    ),
                )
            }
            val taskRepository = RoomTaskRepository(database, FixedClock)
            val equipmentRepository = RoomEquipmentRepository(database, FixedClock)
            runBlocking {
                val taskId = taskRepository.createTask(
                    CreateTaskInput(
                        title = "무기 디코드 실패 격리 일정",
                        memo = "",
                        startDate = FixedClock.today(),
                        time = null,
                        difficulty = TaskDifficulty.MEDIUM,
                        category = "검증",
                        recurrenceRule = RecurrenceRule.NONE,
                    ),
                )
                taskRepository.completeOccurrence(taskId, FixedClock.today())
                val ledgerBefore = requireNotNull(
                    database.rewardLedgerDao().find(taskId, FixedClock.today().toEpochDay()),
                )
                val purchase = equipmentRepository.purchaseEquipment(
                    CharacterMapper.DEFAULT_CHARACTER_ID,
                    EquipmentCatalogSeeder.ASH_SPEAR_ID,
                ) as PurchaseEquipmentResult.Success
                val equip = equipmentRepository.equipOwnedEquipment(
                    CharacterMapper.DEFAULT_CHARACTER_ID,
                    purchase.ownedEquipmentId,
                    EquipmentSlot.WEAPON,
                )
                assertTrue(equip is EquipOwnedEquipmentResult.Success)
                assertEquals(
                    ledgerBefore,
                    database.rewardLedgerDao().find(taskId, FixedClock.today().toEpochDay()),
                )
                assertTrue(
                    database.completionLogDao()
                        .find(taskId, FixedClock.today().toEpochDay()) != null,
                )
                assertEquals(
                    CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
                    equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID)
                        .first {
                            it.renderedEquippedItems.weaponId ==
                                CharacterLoadoutCatalog.WEAPON_ASH_SPEAR
                        }
                        .renderedEquippedItems.weaponId,
                )
                assertNull(database.characterProfileDao().getEquippedItems()?.weaponId)
            }
        } finally {
            database.close()
        }

        val spear = weaponArtworkFixtures()[2].item.copy(imageKey = "equipment_image_unknown")
        val events = mutableListOf<ShopEvent>()
        val state = mutableStateOf(populatedState(
            selectedCategory = EquipmentType.WEAPON,
        ).copy(
            characterEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                weaponId = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            ),
            equipmentSlots = emptyShopEquipmentSlots(
                selectedCategory = EquipmentType.WEAPON,
            ).map { slot ->
                if (slot.slot == EquipmentSlot.WEAPON) {
                    slot.copy(
                        equipmentId = spear.equipmentId,
                        nameKey = spear.nameKey,
                        rarity = spear.rarity,
                        imageKey = spear.imageKey,
                        weaponType = spear.weaponType,
                        isEquipped = true,
                    )
                } else {
                    slot
                }
            },
            items = listOf(spear),
        ))
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = events::add,
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-weapon",
            ancestorTag = "shop-equipment-slot-weapon",
        )
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-slot-weapon")
            .assertContentDescriptionEquals("무기 · 창 슬롯, 물푸레나무 창, 일반, 장착 중")
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-${spear.equipmentId}"))
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-weapon",
            ancestorTag = "shop-equipment-card-${spear.equipmentId}",
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 117L,
                    equipmentId = spear.equipmentId,
                    equipmentNameKey = spear.nameKey,
                    type = spear.type,
                    slot = spear.slot,
                    currentGold = 4_950L,
                ),
            )
        }
        composeRule.onNodeWithText("물푸레나무 창 구매 완료").assertIsDisplayed()
        composeRule.onNodeWithText("바로 장착").assertIsEnabled().performClick()
        assertEquals(
            ShopEvent.EquipPurchased(117L, EquipmentSlot.WEAPON),
            events.last(),
        )
    }

    @Test
    fun everyOutfitArtworkRendersInShopListDetailAndDecorativeEquippedSlots() {
        val fixtures = outfitArtworkFixtures()
        val state = mutableStateOf(
            populatedState().copy(
                equipmentSlots = emptyShopEquipmentSlots(),
                items = fixtures.map(OutfitArtworkFixture::item),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        fixtures.forEach { fixture ->
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-equipment-card-${fixture.item.equipmentId}"))
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = fixture.item)
            }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertCountEquals(2)
            composeRule.onAllNodesWithContentDescription("${fixture.displayName} 이미지")
                .assertCountEquals(2)

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = null)
            }
            composeRule.waitForIdle()
        }

        fixtures.take(3).zip(fixtures.drop(3)).forEach { (top, bottom) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    equipmentSlots = emptyShopEquipmentSlots().map { slot ->
                        when (slot.slot) {
                            EquipmentSlot.CHEST -> slot.equippedWith(top)
                            EquipmentSlot.LEGS -> slot.equippedWith(bottom)
                            else -> slot
                        }
                    },
                    items = emptyList(),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))

            listOf(top, bottom).forEach { fixture ->
                composeRule.onNodeWithTag(
                    "equipment_artwork_${fixture.imageKey}",
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                composeRule.onAllNodesWithContentDescription("${fixture.displayName} 이미지")
                    .assertCountEquals(0)
            }
            composeRule.onNodeWithTag("shop-equipment-slot-chest")
                .assertContentDescriptionEquals(
                    "상의 슬롯, ${top.displayName}, ${top.rarityName}, 장착 중",
                )
            composeRule.onNodeWithTag("shop-equipment-slot-legs")
                .assertContentDescriptionEquals(
                    "하의 슬롯, ${bottom.displayName}, ${bottom.rarityName}, 장착 중",
                )
        }
    }

    @Test
    fun outfitFiltersUseThreeDistinctArtworksAndUnknownDetailKeepsTypeFallback() {
        val fixtures = outfitArtworkFixtures()
        val tops = fixtures.take(3)
        val bottoms = fixtures.drop(3)
        val state = mutableStateOf(
            populatedState(selectedCategory = EquipmentType.CHEST).copy(
                items = tops.map(OutfitArtworkFixture::item),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = { event ->
                        if (event is ShopEvent.SelectCategory) {
                            state.value = state.value.copy(
                                selectedCategory = event.category,
                                selectedDetail = null,
                                items = when (event.category) {
                                    EquipmentType.CHEST -> tops.map(OutfitArtworkFixture::item)
                                    EquipmentType.LEGS -> bottoms.map(OutfitArtworkFixture::item)
                                    else -> emptyList()
                                },
                            )
                        }
                    },
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-category-3").assertIsSelected()
        assertDistinctVisibleArtwork(tops)

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-row"))
        composeRule.onNodeWithTag("shop-category-row").performScrollToIndex(4)
        composeRule.onNodeWithTag("shop-category-4").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop-category-4").assertIsSelected()
        assertDistinctVisibleArtwork(bottoms)

        composeRule.runOnIdle {
            state.value = populatedState(
                selectedCategory = EquipmentType.CHEST,
                selectedDetail = tops.first().item.copy(imageKey = "equipment_image_unknown"),
            ).copy(items = tops.map(OutfitArtworkFixture::item))
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(
            "equipment-placeholder-chest",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("상의 기본 이미지").assertExists()
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsEnabled()
    }

    @Test
    fun gloveAndShoeArtworkRendersInListDetailAndDecorativeEquippedSlotsWithKoreanText() {
        val fixtures = glovesShoesArtworkFixtures()
        val expectedCatalogText = mapOf(
            EquipmentCatalogSeeder.LEATHER_GLOVES_ID to ("140 골드" to "고급"),
            EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID to ("410 골드" to "희귀"),
            EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID to ("380 골드" to "희귀"),
            EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID to ("430 골드" to "희귀"),
        )
        val state = mutableStateOf(
            populatedState().copy(
                equipmentSlots = emptyShopEquipmentSlots(),
                items = fixtures.map(OutfitArtworkFixture::item),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        fixtures.forEach { fixture ->
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-equipment-card-${fixture.item.equipmentId}"))
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()
            val (price, rarity) = expectedCatalogText.getValue(fixture.item.equipmentId)
            composeRule.onNodeWithText(price).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText(rarity).fetchSemanticsNodes().isNotEmpty())

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = fixture.item)
            }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertCountEquals(2)
            composeRule.onAllNodesWithContentDescription("${fixture.displayName} 이미지")
                .assertCountEquals(2)
            composeRule.onNodeWithText(fixture.description).assertIsDisplayed()

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = null)
            }
            composeRule.waitForIdle()
        }

        fixtures.take(2).zip(fixtures.drop(2)).forEach { (gloves, shoes) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    equipmentSlots = emptyShopEquipmentSlots().map { slot ->
                        when (slot.slot) {
                            EquipmentSlot.GLOVES -> slot.equippedWith(gloves)
                            EquipmentSlot.SHOES -> slot.equippedWith(shoes)
                            else -> slot
                        }
                    },
                    items = emptyList(),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))

            listOf(gloves, shoes).forEach { fixture ->
                composeRule.onNodeWithTag(
                    "equipment_artwork_${fixture.imageKey}",
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                composeRule.onAllNodesWithContentDescription("${fixture.displayName} 이미지")
                    .assertCountEquals(0)
            }
            composeRule.onNodeWithTag("shop-equipment-slot-gloves")
                .assertContentDescriptionEquals(
                    "장갑 슬롯, ${gloves.displayName}, ${gloves.rarityName}, 장착 중",
                )
            composeRule.onNodeWithTag("shop-equipment-slot-shoes")
                .assertContentDescriptionEquals(
                    "신발 슬롯, ${shoes.displayName}, ${shoes.rarityName}, 장착 중",
                )
        }
    }

    @Test
    fun purchaseEquipReplaceAndRepositoryRecreationRestoreGlovesShoesAcrossSharedScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(glovesId = null)
            runBlocking {
                database.characterProfileDao().upsertEquippedItems(
                    CharacterMapper.fromDomain(CharacterMapper.DEFAULT_CHARACTER_ID, fallback),
                )
            }
            val repository = RoomEquipmentRepository(database, FixedClock)
            val viewModel = ShopViewModel(
                repository = repository,
                purchaseEquipment = PurchaseEquipmentUseCase(repository),
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            val inventoryViewModel = InventoryViewModel(
                repository = repository,
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            val renderSurface = mutableStateOf(OutfitRenderSurface.SHOP)
            var confirmedItems = fallback
            composeRule.setContent {
                TodoQuestTheme {
                    when (renderSurface.value) {
                        OutfitRenderSurface.SHOP -> ShopScreen(
                            viewModel = viewModel,
                            onOpenInventory = {},
                        )

                        OutfitRenderSurface.INVENTORY -> InventoryScreen(
                            viewModel = inventoryViewModel,
                            onBack = {},
                        )

                        OutfitRenderSurface.CHARACTER -> CharacterContent(
                            state = CharacterUiState(
                                isLoading = false,
                                appearance = CharacterLoadoutCatalog.defaultAppearance,
                                equippedItems = confirmedItems,
                            ),
                            onIncreaseStat = {},
                            onDecreaseStat = {},
                            onSaveStatAllocation = {},
                            onRequestStatReset = {},
                            onDismissStatReset = {},
                            onConfirmStatReset = {},
                            onDismissError = {},
                        )

                        OutfitRenderSurface.BATTLE -> BattleMap(
                            state = battleState(confirmedItems),
                        )
                    }
                }
            }

            waitForTag("shop-equipment-list")
            val initial = runBlocking {
                repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first()
            }
            selectCategory(viewModel, EquipmentType.GLOVES)
            assertCatalogCard(
                equipmentId = EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
                artworkKey = CharacterLoadoutCatalog.GLOVES_LEATHER,
                displayName = "가죽 장갑",
                price = "140 골드",
                rarity = "고급",
            )
            assertCatalogCard(
                equipmentId = EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                artworkKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                displayName = "강철 건틀릿",
                price = "410 골드",
                rarity = "희귀",
            )

            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.LEATHER_GLOVES_ID)
            val leatherItems = awaitGlovesShoesProjection(
                repository = repository,
                glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
                shoesId = fallback.shoesId,
            )
            assertEquals(fallback.copy(glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER), leatherItems)
            waitForEquippedSlot(
                viewModel = viewModel,
                slot = EquipmentSlot.GLOVES,
                equipmentId = EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
            )
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))
            composeRule.onNodeWithTag("shop-equipment-slot-gloves")
                .assertContentDescriptionEquals("장갑 슬롯, 가죽 장갑, 고급, 장착 중")

            selectCategory(viewModel, EquipmentType.GLOVES)
            composeRule.runOnIdle {
                viewModel.onEvent(
                    ShopEvent.OpenEquipmentDetail(EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID),
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedDetail?.equipmentId ==
                    EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID
            }
            assertTrue(
                viewModel.uiState.value.selectedDetail?.comparisons.orEmpty().any { comparison ->
                    comparison.target == StatTarget.Base(StatType.STRENGTH) &&
                        comparison.currentAmount == 2 &&
                        comparison.candidateAmount == 4 &&
                        comparison.difference == 2
                },
            )
            composeRule.runOnIdle { viewModel.onEvent(ShopEvent.CloseEquipmentDetail) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedDetail == null
            }
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID)
            val steelItems = awaitGlovesShoesProjection(
                repository = repository,
                glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                shoesId = fallback.shoesId,
            )
            assertEquals(leatherItems.copy(glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS), steelItems)

            selectCategory(viewModel, EquipmentType.SHOES)
            assertCatalogCard(
                equipmentId = EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID,
                artworkKey = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
                displayName = "여행자의 장화",
                price = "380 골드",
                rarity = "희귀",
            )
            assertCatalogCard(
                equipmentId = EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                artworkKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                displayName = "바람걸음 장화",
                price = "430 골드",
                rarity = "희귀",
            )
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID)
            val travelersItems = awaitGlovesShoesProjection(
                repository = repository,
                glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            )
            assertEquals(steelItems.copy(shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS), travelersItems)

            selectCategory(viewModel, EquipmentType.SHOES)
            composeRule.runOnIdle {
                viewModel.onEvent(
                    ShopEvent.OpenEquipmentDetail(EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID),
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedDetail?.equipmentId ==
                    EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID
            }
            assertTrue(
                viewModel.uiState.value.selectedDetail?.comparisons.orEmpty().any { comparison ->
                    comparison.target == StatTarget.Base(StatType.FOCUS) &&
                        comparison.currentAmount == 3 &&
                        comparison.candidateAmount == 4 &&
                        comparison.difference == 1
                },
            )
            composeRule.runOnIdle { viewModel.onEvent(ShopEvent.CloseEquipmentDetail) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedDetail == null
            }
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID)

            val restartedRepository = RoomEquipmentRepository(database, FixedClock)
            val restarted = runBlocking {
                restartedRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                    it.renderedEquippedItems.glovesId ==
                        CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS &&
                        it.renderedEquippedItems.shoesId ==
                        CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS
                }
            }
            confirmedItems = restarted.renderedEquippedItems
            assertEquals(
                setOf(EquipmentSlot.GLOVES, EquipmentSlot.SHOES),
                restarted.equippedBySlot.keys,
            )
            assertEquals(null, runBlocking {
                database.characterProfileDao().getEquippedItems()?.glovesId
            })
            assertEquals(fallback.shoesId, runBlocking {
                database.characterProfileDao().getEquippedItems()?.shoesId
            })
            assertTrue(restarted.derivedStats.attack > initial.derivedStats.attack)
            assertTrue(restarted.derivedStats.defense > initial.derivedStats.defense)

            val restartedCharacterRepository = RoomCharacterRepository(database, FixedClock)
            assertEquals(
                confirmedItems,
                runBlocking {
                    restartedCharacterRepository.observeCharacter(FixedClock.today()).first {
                        it.equippedItems == confirmedItems
                    }.equippedItems
                },
            )

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.INVENTORY }
            waitForTag("inventory-list")
            glovesShoesArtworkFixtures().forEach { fixture ->
                val ownedEquipmentId = inventoryViewModel.uiState.value.items.single {
                    it.equipmentId == fixture.item.equipmentId
                }.ownedEquipmentId
                composeRule.onNodeWithTag("inventory-list")
                    .performScrollToNode(
                        hasTestTag("inventory-equipment-$ownedEquipmentId"),
                    )
                composeRule.onNodeWithTag(
                    "equipment_artwork_${fixture.imageKey}",
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                    .assertIsDisplayed()
            }

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.CHARACTER }
            composeRule.waitForIdle()
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "equipped-character-sprite")

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.BATTLE }
            composeRule.waitForIdle()
            assertBattleSpriteContainsProjectedGlovesAndShoes(confirmedItems)
        } finally {
            database.close()
        }
    }

    @Test
    fun gloveShoeCompositesPreserveSchemaV5WeaponOverlayFiveBottomSeamsAndUnrelatedPixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val composer = CharacterBitmapComposer(context.assets)
        val base = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = null,
            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            accessoryId = null,
        )
        val gloveIds = listOf(
            CharacterLoadoutCatalog.GLOVES_LEATHER,
            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
        )
        val shoeIds = listOf(
            CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
        )
        val bottomIds = listOf(
            CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
        )

        gloveIds.forEach { glovesId ->
            listOf(null, CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD).forEach { weaponId ->
                val items = base.copy(glovesId = glovesId, weaponId = weaponId)
                val layers = com.todoquest.ui.character.CharacterLayerCatalog.resolve(
                    CharacterRenderState(CharacterLoadoutCatalog.defaultAppearance, items),
                )
                if (weaponId != null) {
                    assertTrue(
                        layers.indexOfFirst { it.assetPath.endsWith("$glovesId.png") } <
                            layers.indexOfFirst { it.assetPath.endsWith("weapon_back_default_sword.png") },
                    )
                    assertTrue(
                        layers.indexOfFirst { it.assetPath.endsWith("weapon_back_default_sword.png") } <
                            layers.indexOfFirst { it.assetPath.endsWith("weapon_held_default_sword.png") },
                    )
                    assertTrue(
                        layers.indexOfFirst { it.assetPath.endsWith("weapon_held_default_sword.png") } <
                            layers.indexOfFirst { it.assetPath.endsWith("weapon_front_default_sword.png") },
                    )
                }
                val actual = composeCharacter(composer, items)
                val gloves = loadCharacterLayer("$glovesId.png")
                val back = weaponId?.let {
                    loadCharacterLayer("weapon_back_default_sword.png")
                }
                val held = weaponId?.let {
                    loadCharacterLayer("weapon_held_default_sword.png")
                }
                val front = weaponId?.let {
                    loadCharacterLayer("weapon_front_default_sword.png")
                }
                var glovePixels = 0
                for (y in 0 until CharacterCanvasSize) {
                    for (x in 0 until CharacterCanvasSize) {
                        val glovePixel = gloves.getPixel(x, y)
                        if (glovePixel ushr 24 == 0) continue
                        val frontPixel = front?.getPixel(x, y)
                        val heldPixel = held?.getPixel(x, y)
                        val backPixel = back?.getPixel(x, y)
                        val expected = frontPixel?.takeIf { it ushr 24 != 0 }
                            ?: heldPixel?.takeIf { it ushr 24 != 0 }
                            ?: backPixel?.takeIf { it ushr 24 != 0 }
                            ?: glovePixel
                        assertEquals("장갑과 검 front 순서가 깨졌습니다. ($x, $y)", expected, actual.getPixel(x, y))
                        glovePixels += 1
                    }
                }
                assertEquals(38, glovePixels)
            }
        }

        shoeIds.forEach { shoesId ->
            val shoes = loadCharacterLayer("$shoesId.png")
            assertEquals(listOf(23, 53, 41, 58), shoes.opaqueBoundsInclusive())
            bottomIds.forEach { bottomId ->
                val items = base.copy(
                    bottomId = bottomId,
                    shoesId = shoesId,
                    glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
                    weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                )
                val actual = composeCharacter(composer, items)
                val bottom = loadCharacterLayer("$bottomId.png")
                for (y in AnkleTop..AnkleBottom) {
                    for (x in LeftAnkleLeft..LeftAnkleRight) {
                        assertSeamUsesSingleSource(actual, bottom, shoes, x, y)
                    }
                    for (x in RightAnkleLeft..RightAnkleRight) {
                        assertSeamUsesSingleSource(actual, bottom, shoes, x, y)
                    }
                }
                for (x in 23..31) assertEquals(255, actual.getPixel(x, 58) ushr 24)
                for (x in 33..41) assertEquals(255, actual.getPixel(x, 58) ushr 24)
            }
        }

        val unchanged = composeCharacter(
            composer,
            base.copy(
                glovesId = null,
                shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
                weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            ),
        )
        gloveIds.forEach { glovesId ->
            shoeIds.forEach { shoesId ->
                val mixed = composeCharacter(
                    composer,
                    base.copy(
                        glovesId = glovesId,
                        shoesId = shoesId,
                        weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                    ),
                )
                for (y in 0 until CharacterCanvasSize) {
                    for (x in 0 until CharacterCanvasSize) {
                        val inGloves = x in 21..43 && y in 39..45
                        val inShoes = x in 23..41 && y in 53..58
                        if (!inGloves && !inShoes) {
                            assertEquals(
                                "혼합 착용이 다른 외형 픽셀을 변경했습니다. ($x, $y)",
                                unchanged.getPixel(x, y),
                                mixed.getPixel(x, y),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun smallWidthLargeFontKeepsEveryGloveShoeNamePriceArtworkAndPurchaseReachable() {
        val fixtures = glovesShoesArtworkFixtures()
        val state = mutableStateOf(
            populatedState(selectedCategory = EquipmentType.GLOVES).copy(
                items = listOf(fixtures.first().item),
            ),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        ShopContent(
                            state = state.value,
                            onEvent = {},
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        fixtures.forEach { fixture ->
            composeRule.runOnIdle {
                state.value = populatedState(selectedCategory = fixture.item.type).copy(
                    items = listOf(fixture.item),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-equipment-card-${fixture.item.equipmentId}"))
            composeRule.onNodeWithText(fixture.displayName).assertIsDisplayed()
            composeRule.onNodeWithText("${fixture.item.price} 골드").assertIsDisplayed()
            composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed()

            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = fixture.item)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-detail-scroll")
                .performScrollToNode(hasTestTag("shop-detail-purchase"))
            composeRule.onNodeWithTag("shop-detail-purchase")
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertHeightIsAtLeast(48.dp)
            composeRule.runOnIdle {
                state.value = state.value.copy(selectedDetail = null)
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun gloveAndShoeFiltersUseDistinctArtworkAndUnknownDetailKeepsTypeFallback() {
        val fixtures = glovesShoesArtworkFixtures()
        val gloves = fixtures.take(2)
        val shoes = fixtures.drop(2)
        val state = mutableStateOf(
            populatedState(selectedCategory = EquipmentType.GLOVES).copy(
                items = gloves.map(OutfitArtworkFixture::item),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = { event ->
                        if (event is ShopEvent.SelectCategory) {
                            state.value = state.value.copy(
                                selectedCategory = event.category,
                                selectedDetail = null,
                                items = when (event.category) {
                                    EquipmentType.GLOVES -> gloves.map(OutfitArtworkFixture::item)
                                    EquipmentType.SHOES -> shoes.map(OutfitArtworkFixture::item)
                                    else -> emptyList()
                                },
                            )
                        }
                    },
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-category-5").assertIsSelected()
        assertDistinctVisibleArtwork(gloves)

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-row"))
        composeRule.onNodeWithTag("shop-category-6").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            state.value.selectedCategory == EquipmentType.SHOES
        }
        composeRule.onNodeWithTag("shop-category-6").assertIsSelected()
        assertDistinctVisibleArtwork(shoes)

        composeRule.runOnIdle {
            state.value = populatedState(
                selectedCategory = EquipmentType.GLOVES,
                selectedDetail = gloves.first().item.copy(imageKey = "equipment_image_unknown"),
            ).copy(items = gloves.map(OutfitArtworkFixture::item))
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(
            "equipment-placeholder-gloves",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("장갑 기본 이미지").assertExists()
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsEnabled()
    }

    @Test
    fun purchaseEquipAndReplaceHelmetUpdatesRoomProjectionSharedSpriteAndOpenFacePixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            runBlocking {
                database.characterProfileDao().insertProfile(
                    CharacterProfileEntity(
                        id = CharacterMapper.DEFAULT_CHARACTER_ID,
                        totalXp = 100_000L,
                        currentGold = 10_000L,
                        strength = 5,
                        vitality = 5,
                        focus = 5,
                        willpower = 5,
                        unspentStatPoints = 98,
                        hasUsedFreeStatReset = false,
                    ),
                )
                database.characterProfileDao().insertCurrentState(
                    CharacterCurrentStateEntity(
                        characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
                        currentHp = 110,
                        balanceVersion = 1,
                        updatedAtEpochMillis = 0L,
                    ),
                )
            }
            val repository = RoomEquipmentRepository(database, FixedClock)
            val viewModel = ShopViewModel(
                repository = repository,
                purchaseEquipment = PurchaseEquipmentUseCase(repository),
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            composeRule.setContent {
                TodoQuestTheme {
                    ShopScreen(
                        viewModel = viewModel,
                        onOpenInventory = {},
                    )
                }
            }

            waitForTag("shop-equipment-list")
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-2"))
        composeRule.onNodeWithTag("shop-category-2").performClick()
        composeRule.onNodeWithTag("shop-equipment-list").performScrollToNode(
            hasTestTag("shop-equipment-card-${EquipmentCatalogSeeder.LEATHER_HAT_ID}"),
        )
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_leather_hat",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag("shop-equipment-list").performScrollToNode(
            hasTestTag("shop-equipment-card-${EquipmentCatalogSeeder.IRON_HELMET_ID}"),
        )
        composeRule.onNodeWithTag(
                "equipment_artwork_headgear_iron_helmet",
                useUnmergedTree = true,
            ).assertExists()

            purchaseAndEquip(
                viewModel = viewModel,
                equipmentId = EquipmentCatalogSeeder.LEATHER_HAT_ID,
            )
            val leatherSnapshot = runBlocking {
                repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                    it.renderedEquippedItems.headId == CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT
                }
            }
            assertEquals(
                CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                leatherSnapshot.renderedEquippedItems.headId,
            )
            assertEquals(
                CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                ),
                leatherSnapshot.renderedEquippedItems,
            )
            waitForEquippedSlot(
                viewModel = viewModel,
                slot = EquipmentSlot.HELMET,
                equipmentId = EquipmentCatalogSeeder.LEATHER_HAT_ID,
            )
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))
            composeRule.onNodeWithTag("shop-equipment-slot-helmet")
                .assertContentDescriptionEquals("투구 슬롯, 가죽 모자, 일반, 장착 중")
            assertSpriteMatchesOpaqueSourcePixels(
                expectedItems = leatherSnapshot.renderedEquippedItems,
            )

            purchaseAndEquip(
                viewModel = viewModel,
                equipmentId = EquipmentCatalogSeeder.IRON_HELMET_ID,
            )
            val ironSnapshot = runBlocking {
                repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                    it.renderedEquippedItems.headId == CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET
                }
            }
            assertEquals(
                leatherSnapshot.renderedEquippedItems.copy(
                    headId = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
                ),
                ironSnapshot.renderedEquippedItems,
            )
            waitForEquippedSlot(
                viewModel = viewModel,
                slot = EquipmentSlot.HELMET,
                equipmentId = EquipmentCatalogSeeder.IRON_HELMET_ID,
            )
            composeRule.onNodeWithTag("shop-equipment-slot-helmet")
                .assertContentDescriptionEquals("투구 슬롯, 철 투구, 희귀, 장착 중")
            assertIronSpriteUsesOpenFaceCompositeWithoutLeatherPixels(
                leatherItems = leatherSnapshot.renderedEquippedItems,
                ironItems = ironSnapshot.renderedEquippedItems,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun purchaseEquipAndReplaceOutfitsUpdatesOnlySlotsAndEverySharedRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            val repository = RoomEquipmentRepository(database, FixedClock)
            val characterRepository = RoomCharacterRepository(database, FixedClock)
            val viewModel = ShopViewModel(
                repository = repository,
                purchaseEquipment = PurchaseEquipmentUseCase(repository),
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            val renderSurface = mutableStateOf(OutfitRenderSurface.SHOP)
            var confirmedItems = CharacterLoadoutCatalog.defaultEquippedItems
            composeRule.setContent {
                TodoQuestTheme {
                    when (renderSurface.value) {
                        OutfitRenderSurface.SHOP -> ShopScreen(
                            viewModel = viewModel,
                            onOpenInventory = {},
                        )

                        OutfitRenderSurface.INVENTORY -> Unit

                        OutfitRenderSurface.CHARACTER -> CharacterContent(
                            state = CharacterUiState(
                                isLoading = false,
                                appearance = CharacterLoadoutCatalog.defaultAppearance,
                                equippedItems = confirmedItems,
                            ),
                            onIncreaseStat = {},
                            onDecreaseStat = {},
                            onSaveStatAllocation = {},
                            onRequestStatReset = {},
                            onDismissStatReset = {},
                            onConfirmStatReset = {},
                            onDismissError = {},
                        )

                        OutfitRenderSurface.BATTLE -> BattleMap(
                            state = battleState(confirmedItems),
                        )
                    }
                }
            }

            waitForTag("shop-equipment-list")
            selectCategory(viewModel, EquipmentType.CHEST)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.CLOTH_TOP_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_CLOTH,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            )
            assertOutfitProjectionPreservesOtherSlots(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            selectCategory(viewModel, EquipmentType.LEGS)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.CLOTH_PANTS_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_CLOTH,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            )
            assertOutfitCompositeIntegrity(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            selectCategory(viewModel, EquipmentType.CHEST)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.LEATHER_ARMOR_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            )
            assertOutfitCompositeIntegrity(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            selectCategory(viewModel, EquipmentType.LEGS)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.LEATHER_PANTS_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            )
            assertOutfitCompositeIntegrity(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            selectCategory(viewModel, EquipmentType.CHEST)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.IRON_BREASTPLATE_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            )
            assertOutfitCompositeIntegrity(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            selectCategory(viewModel, EquipmentType.LEGS)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.STEEL_GREAVES_ID)
            confirmedItems = awaitOutfitProjection(
                repository = repository,
                expectedTopId = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
                expectedBottomId = CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            )
            assertOutfitCompositeIntegrity(confirmedItems)
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "shop-character-sprite")

            val characterSnapshot = runBlocking {
                characterRepository.observeCharacter(FixedClock.today()).first {
                    it.equippedItems == confirmedItems
                }
            }
            assertEquals(confirmedItems, characterSnapshot.equippedItems)

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.CHARACTER }
            composeRule.waitForIdle()
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "equipped-character-sprite")

            composeRule.runOnIdle { renderSurface.value = OutfitRenderSurface.BATTLE }
            composeRule.waitForIdle()
            assertSpriteMatchesOpaqueSourcePixels(confirmedItems, "battle-player-layer")
        } finally {
            database.close()
        }
    }

    @Test
    fun missingGloveShoeArtworkDecodeKeepsTaskRewardPurchaseAndEquipTransactionsUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = EquipmentArtworkLoader(context.assets)
        assertNull(
            loader.load(
                EquipmentArtworkDefinition(
                    imageKey = "missing-shoes",
                    assetPath = "character/layers/missing-shoes.png",
                ),
            ),
        )
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            val taskRepository = RoomTaskRepository(database, FixedClock)
            val equipmentRepository = RoomEquipmentRepository(database, FixedClock)
            runBlocking {
                val taskId = taskRepository.createTask(
                    CreateTaskInput(
                        title = "디코드 실패 격리 일정",
                        memo = "",
                        startDate = FixedClock.today(),
                        time = null,
                        difficulty = TaskDifficulty.MEDIUM,
                        category = "검증",
                        recurrenceRule = RecurrenceRule.NONE,
                    ),
                )
                taskRepository.completeOccurrence(taskId, FixedClock.today())
                val ledgerBefore = requireNotNull(
                    database.rewardLedgerDao().find(taskId, FixedClock.today().toEpochDay()),
                )
                val purchase = equipmentRepository.purchaseEquipment(
                    CharacterMapper.DEFAULT_CHARACTER_ID,
                    EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                ) as PurchaseEquipmentResult.Success
                val equip = equipmentRepository.equipOwnedEquipment(
                    CharacterMapper.DEFAULT_CHARACTER_ID,
                    purchase.ownedEquipmentId,
                    EquipmentSlot.SHOES,
                )
                assertTrue(equip is EquipOwnedEquipmentResult.Success)
                assertEquals(
                    ledgerBefore,
                    database.rewardLedgerDao().find(taskId, FixedClock.today().toEpochDay()),
                )
                assertTrue(
                    database.completionLogDao()
                        .find(taskId, FixedClock.today().toEpochDay()) != null,
                )
                assertEquals(
                    CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                    equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID)
                        .first {
                            it.renderedEquippedItems.shoesId ==
                                CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS
                        }
                        .renderedEquippedItems.shoesId,
                )
            }
        } finally {
            database.close()
        }

        val windwalker = glovesShoesArtworkFixtures().last().item.copy(
            imageKey = "equipment_image_unknown",
        )
        val events = mutableListOf<ShopEvent>()
        val state = mutableStateOf(populatedState(
            selectedCategory = EquipmentType.SHOES,
        ).copy(
            characterEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                shoesId = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            ),
            equipmentSlots = populatedState().equipmentSlots.map { slot ->
                if (slot.slot == EquipmentSlot.SHOES) {
                    slot.copy(
                        equipmentId = windwalker.equipmentId,
                        nameKey = windwalker.nameKey,
                        rarity = windwalker.rarity,
                        imageKey = windwalker.imageKey,
                        isEquipped = true,
                    )
                } else {
                    slot
                }
            },
            items = listOf(windwalker),
        ))
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = events::add,
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-shoes",
            ancestorTag = "shop-equipment-slot-shoes",
        )
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-slot-shoes")
            .assertContentDescriptionEquals("신발 슬롯, 바람걸음 장화, 희귀, 장착 중")
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-${windwalker.equipmentId}"))
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-shoes",
            ancestorTag = "shop-equipment-card-${windwalker.equipmentId}",
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 88L,
                    equipmentId = windwalker.equipmentId,
                    equipmentNameKey = windwalker.nameKey,
                    type = windwalker.type,
                    slot = windwalker.slot,
                    currentGold = 9_320L,
                ),
            )
        }
        composeRule.onNodeWithText("바람걸음 장화 구매 완료").assertIsDisplayed()
        composeRule.onNodeWithText("바로 장착").assertIsEnabled().performClick()
        assertEquals(
            ShopEvent.EquipPurchased(88L, EquipmentSlot.SHOES),
            events.last(),
        )
    }

    @Test
    fun helmetArtworkRendersInListDetailAndDecorativeEquippedSlot() {
        val helmet = leatherHat()
        val state = mutableStateOf(populatedState().copy(
            equipmentSlots = populatedState().equipmentSlots.map { slot ->
                if (slot.slot == EquipmentSlot.HELMET) {
                    slot.copy(
                        equipmentId = helmet.equipmentId,
                        nameKey = helmet.nameKey,
                        rarity = helmet.rarity,
                        imageKey = helmet.imageKey,
                        isEquipped = true,
                    )
                } else {
                    slot
                }
            },
            items = listOf(helmet, clothTop()),
            selectedDetail = null,
        ))
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag("shop-equipment-slot-helmet")
            .assertContentDescriptionEquals("투구 슬롯, 가죽 모자, 일반, 장착 중")
        assertTaggedDescendantDisplayed(
            tag = "equipment_artwork_headgear_leather_hat",
            ancestorTag = "shop-equipment-slot-helmet",
        )
        composeRule.onNodeWithTag(
            "shop-equipment-slot-empty-icon-chest",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = helmet)
        }
        assertTaggedDescendantDisplayed(
            tag = "equipment_artwork_headgear_leather_hat",
            ancestorTag = "shop-equipment-card-1003",
        )
        assertTaggedDescendantDisplayed(
            tag = "equipment_artwork_headgear_leather_hat",
            ancestorTag = "shop-equipment-detail",
        )
        composeRule.onAllNodesWithContentDescription("가죽 모자 이미지")
            .assertCountEquals(2)
        assertTrue(
            !composeRule.onNodeWithTag("shop-equipment-card-1003")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.ContentDescription),
        )
    }

    @Test
    fun characterPreviewShowsSharedSpriteEquippedAndEmptySlotsAndRoutesSlotSelection() {
        var receivedEvent: ShopEvent? = null
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = populatedState(selectedCategory = EquipmentType.WEAPON),
                    onEvent = { receivedEvent = it },
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-character-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertContentDescriptionEquals("상점 캐릭터 장비 프리뷰")
        composeRule.onNodeWithTag("shop-character-level", useUnmergedTree = true)
            .assertTextContains("Lv.30")

        composeRule.onNodeWithTag("shop-equipment-slot-weapon")
            .assertIsSelected()
            .assertContentDescriptionEquals("무기 슬롯, 낡은 검, 희귀, 장착 중")
            .performClick()
        assertEquals(ShopEvent.OpenSlotManagement(EquipmentSlot.WEAPON), receivedEvent)

        composeRule.onNodeWithTag("shop-equipment-slot-helmet")
            .assertContentDescriptionEquals("투구 슬롯 비어 있음")
        composeRule.onNodeWithTag("shop-category-1").assertIsSelected()
    }

    @Test
    fun slotManagementDialogShowsMergedEquippedOrEmptyStateAndRoutesAccessibleActions() {
        val events = mutableListOf<ShopEvent>()
        val state = mutableStateOf(
            populatedState().copy(managedSlot = EquipmentSlot.WEAPON),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = events::add,
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-slot-management-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-slot-management-state")
            .assertContentDescriptionEquals("무기 부위, 낡은 검, 희귀, 장착 중")
            .assertTextContains("낡은 검")
            .assertTextContains("희귀")
        composeRule.onNodeWithTag("shop-slot-management-browse")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
        composeRule.onNodeWithTag("shop-slot-management-unequip")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        assertEquals(ShopEvent.UnequipManagedSlot, events.last())

        composeRule.runOnIdle {
            state.value = populatedState().copy(managedSlot = EquipmentSlot.HELMET)
        }
        composeRule.onNodeWithTag("shop-slot-management-state")
            .assertContentDescriptionEquals("투구 부위, 비어 있음")
            .assertTextContains("비어 있음")
        composeRule.onNodeWithTag("shop-slot-management-unequip").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-slot-management-browse").performClick()
        assertEquals(ShopEvent.BrowseManagedSlot, events.last())
        composeRule.onNodeWithTag("shop-slot-management-close")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(ShopEvent.CloseSlotManagement, events.last())
    }

    @Test
    fun slotManagementProcessingDisablesDialogAndOtherEquipmentCommands() {
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = populatedState().copy(
                        managedSlot = EquipmentSlot.WEAPON,
                        unequipState = ShopUnequipState.Processing(
                            equipmentId = 1_001L,
                            slot = EquipmentSlot.WEAPON,
                        ),
                    ),
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("해제 처리 중").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-slot-management-browse").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-slot-management-unequip").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-slot-management-close").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-category-0").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-equipment-slot-helmet").assertIsNotEnabled()
    }

    @Test
    fun unequipSuccessAlreadyEmptyAndFailureFeedbackAreConsumedOrRetriedOnce() {
        val events = mutableListOf<ShopEvent>()
        val state = mutableStateOf(
            populatedState().copy(
                managedSlot = EquipmentSlot.WEAPON,
                unequipState = ShopUnequipState.Success(
                    equipmentId = 1_001L,
                    slot = EquipmentSlot.WEAPON,
                    changed = true,
                ),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = events::add,
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithText("해제 완료").assertIsDisplayed()
        composeRule.onNodeWithText("장비를 해제했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        assertEquals(ShopEvent.ConsumeUnequipResult, events.last())

        composeRule.runOnIdle {
            state.value = state.value.copy(
                unequipState = ShopUnequipState.Success(
                    equipmentId = 1_001L,
                    slot = EquipmentSlot.WEAPON,
                    changed = false,
                ),
            )
        }
        composeRule.onNodeWithText("이미 비어 있는 부위입니다. 안전하게 해제를 완료했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                unequipState = ShopUnequipState.Failed(
                    equipmentId = 1_001L,
                    slot = EquipmentSlot.WEAPON,
                ),
                retryState = ShopRetryState.Unequip(
                    equipmentId = 1_001L,
                    slot = EquipmentSlot.WEAPON,
                ),
            )
        }
        composeRule.onNodeWithText("해제 실패").assertIsDisplayed()
        composeRule.onNodeWithText("장비를 해제하지 못했습니다. 다시 시도할 수 있습니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(ShopEvent.Retry, events.last())
    }

    @Test
    fun statSummaryUsesEqualWidthRowAndAccessiblePositiveAndNegativeDeltas() {
        var positiveDeltaColor = 0
        var negativeDeltaColor = 0
        composeRule.setContent {
            TodoQuestTheme {
                positiveDeltaColor = MaterialTheme.colorScheme.secondary.toArgb()
                negativeDeltaColor = MaterialTheme.colorScheme.error.toArgb()
                ShopContent(
                    state = populatedState().copy(
                        statSummary = CharacterStatSummaryUiModel(
                            attack = CharacterStatValueUiModel(37, 5),
                            maxHp = CharacterStatValueUiModel(245, 0),
                            defense = CharacterStatValueUiModel(18, -2),
                        ),
                    ),
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        val statTags = listOf(
            "shop-stat-attack" to "공격력 현재 37, 선택 시 +5",
            "shop-stat-max-hp" to "최대 체력 245",
            "shop-stat-defense" to "방어력 현재 18, 선택 시 -2",
        )
        val bounds = statTags.map { (tag, description) ->
            composeRule.onNodeWithTag(tag)
                .assertContentDescriptionEquals(description)
                .fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (current, next) ->
            assertTrue(abs(current.width - next.width) <= composeRule.density.density)
            assertTrue(abs(current.top - next.top) <= composeRule.density.density)
            assertTrue(current.left < next.left)
        }
        composeRule.onNodeWithTag("shop-stat-attack-delta", useUnmergedTree = true)
            .assertTextContains("+5")
            .captureToImage()
            .asAndroidBitmap()
            .also { assertTrue(it.containsColorNear(positiveDeltaColor)) }
        composeRule.onNodeWithTag("shop-stat-defense-delta", useUnmergedTree = true)
            .assertTextContains("-2")
            .captureToImage()
            .asAndroidBitmap()
            .also { assertTrue(it.containsColorNear(negativeDeltaColor)) }
        composeRule.onNodeWithTag("shop-stat-max-hp-delta", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun statSummaryKeepsSummaryCellsCurrentAndDeltaBoundsAcrossPreviewDifferences() {
        val summary = mutableStateOf(
            CharacterStatSummaryUiModel(
                attack = CharacterStatValueUiModel(37, 0),
                maxHp = CharacterStatValueUiModel(245, 0),
                defense = CharacterStatValueUiModel(18, 0),
            ),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        StatSummary(summary = summary.value)
                    }
                }
            }
        }

        val stableTags = listOf(
            "shop-character-stat-summary",
            "shop-stat-attack",
            "shop-stat-attack-current",
            "shop-stat-attack-delta-slot",
            "shop-stat-max-hp",
            "shop-stat-max-hp-current",
            "shop-stat-max-hp-delta-slot",
            "shop-stat-defense",
            "shop-stat-defense-current",
            "shop-stat-defense-delta-slot",
        )
        val baseline = stableTags.associateWith { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        val variants = listOf(
            CharacterStatSummaryUiModel(
                attack = CharacterStatValueUiModel(37, 12),
                maxHp = CharacterStatValueUiModel(245, 0),
                defense = CharacterStatValueUiModel(18, 0),
            ),
            CharacterStatSummaryUiModel(
                attack = CharacterStatValueUiModel(37, 0),
                maxHp = CharacterStatValueUiModel(245, -40),
                defense = CharacterStatValueUiModel(18, 0),
            ),
            CharacterStatSummaryUiModel(
                attack = CharacterStatValueUiModel(37, 0),
                maxHp = CharacterStatValueUiModel(245, 0),
                defense = CharacterStatValueUiModel(18, 0),
            ),
        )

        variants.forEach { variant ->
            composeRule.runOnIdle { summary.value = variant }
            composeRule.waitForIdle()
            stableTags.forEach { tag ->
                val expected = requireNotNull(baseline[tag])
                val actual = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("$tag left changed", abs(expected.left - actual.left) <= 1f)
                assertTrue("$tag top changed", abs(expected.top - actual.top) <= 1f)
                assertTrue("$tag width changed", abs(expected.width - actual.width) <= 1f)
                assertTrue("$tag height changed", abs(expected.height - actual.height) <= 1f)
            }
        }

        composeRule.onNodeWithTag("shop-stat-attack")
            .assertContentDescriptionEquals("공격력 37")
        composeRule.onNodeWithTag("shop-stat-max-hp")
            .assertContentDescriptionEquals("최대 체력 245")
        composeRule.onNodeWithTag("shop-stat-defense")
            .assertContentDescriptionEquals("방어력 18")
    }

    @Test
    fun regularPreviewPlacesThreeSlotsOnEachSideAndShoesBelowLargeAvatar() {
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CharacterEquipmentPreview(
                        level = 30,
                        appearance = populatedState().characterAppearance,
                        equippedItems = populatedState().characterEquippedItems,
                        slots = populatedState().equipmentSlots,
                        enabled = true,
                        onSelectSlot = {},
                    )
                }
            }
        }

        val left = composeRule.onNodeWithTag("shop-preview-left-slots")
            .fetchSemanticsNode().boundsInRoot
        val avatar = composeRule.onNodeWithTag("shop-character-avatar")
            .fetchSemanticsNode().boundsInRoot
        val right = composeRule.onNodeWithTag("shop-preview-right-slots")
            .fetchSemanticsNode().boundsInRoot
        val shoes = composeRule.onNodeWithTag("shop-equipment-slot-shoes")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(left.right <= avatar.left)
        assertTrue(avatar.right <= right.left)
        assertTrue(shoes.top >= avatar.bottom)
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertWidthIsEqualTo(144.dp)
            .assertHeightIsEqualTo(144.dp)
        composeRule.onNodeWithTag("shop-equipment-slot-helmet")
            .assertHeightIsEqualTo(68.dp)
            .assertWidthIsEqualTo(68.dp)
            .assertContentDescriptionEquals("투구 슬롯 비어 있음")
        composeRule.onNodeWithTag("shop-equipment-slot-weapon")
            .assertHeightIsEqualTo(68.dp)
            .assertWidthIsEqualTo(68.dp)
            .assertContentDescriptionEquals("무기 슬롯, 낡은 검, 희귀, 장착 중")
        composeRule.onNodeWithTag("shop-preview-regular-layout").assertExists()
        composeRule.onNodeWithTag("shop-preview-compact-layout").assertDoesNotExist()
    }

    @Test
    fun compactPreviewKeepsSevenSquareSlotsAroundMinimumSizeAvatar() {
        composeRule.setContent {
            TodoQuestTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(760.dp),
                ) {
                    CharacterEquipmentPreview(
                        level = 30,
                        appearance = populatedState().characterAppearance,
                        equippedItems = populatedState().characterEquippedItems,
                        slots = populatedState().equipmentSlots,
                        enabled = true,
                        onSelectSlot = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertWidthIsEqualTo(120.dp)
            .assertHeightIsEqualTo(120.dp)
        val orderedSlots = listOf(
            EquipmentSlot.HELMET,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.WEAPON,
            EquipmentSlot.GLOVES,
            EquipmentSlot.ACCESSORY,
            EquipmentSlot.SHOES,
        )
        val bounds = orderedSlots.map { slot ->
            composeRule.onNodeWithTag("shop-equipment-slot-${slot.name.lowercase()}")
                .assertHeightIsEqualTo(64.dp)
                .assertWidthIsEqualTo(64.dp)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
                .fetchSemanticsNode().boundsInRoot
        }
        assertEquals(7, bounds.size)
        val avatar = composeRule.onNodeWithTag("shop-character-avatar")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.take(3).all { it.right <= avatar.left })
        assertTrue(bounds.drop(3).take(3).all { it.left >= avatar.right })
        assertTrue(bounds.last().top >= avatar.bottom)
        composeRule.onNodeWithTag("shop-preview-compact-layout").assertExists()
        composeRule.onNodeWithTag("shop-preview-left-slots").assertExists()
        composeRule.onNodeWithTag("shop-preview-right-slots").assertExists()
        composeRule.onNodeWithTag("shop-equipment-slot-weapon")
            .assertContentDescriptionEquals("무기 슬롯, 낡은 검, 희귀, 장착 중")
        composeRule.onNodeWithTag("shop-equipment-slot-helmet")
            .assertContentDescriptionEquals("투구 슬롯 비어 있음")
    }

    @Test
    fun largeFontUsesCompactPreviewAtRegularWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        CharacterEquipmentPreview(
                            level = 30,
                            appearance = populatedState().characterAppearance,
                            equippedItems = populatedState().characterEquippedItems,
                            slots = populatedState().equipmentSlots,
                            enabled = true,
                            onSelectSlot = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-preview-compact-layout").assertExists()
        composeRule.onNodeWithTag("shop-preview-left-slots").assertExists()
        composeRule.onNodeWithTag("shop-preview-right-slots").assertExists()
        composeRule.onNodeWithTag("shop-equipment-slot-weapon")
            .assertHeightIsEqualTo(64.dp)
        composeRule.onNodeWithTag("shop-equipment-slot-helmet")
            .assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun cardsAlwaysShowRequiredLevelAndLockedCardsExposeKoreanLevelShortageSemantics() {
        val available = leatherHat()
        val locked = ironHelmet().copy(
            isRequiredLevelMet = false,
            purchaseAvailability = PurchaseAvailability.Unavailable(
                PurchaseUnavailableReason.LevelTooLow(
                    requiredLevel = 11,
                    characterLevel = 10,
                ),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = populatedState().copy(items = listOf(available, locked)),
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        composeRule.onNodeWithTag("shop-equipment-card-1003")
            .assertTextContains("요구 레벨 1")
        composeRule.onNodeWithTag("equipment-required-level-1003", useUnmergedTree = true)
            .assertTextContains("요구 레벨 1")
        composeRule.onNodeWithTag("equipment-level-lock-1003", useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        composeRule.onNodeWithText("요구 레벨 11").assertExists()
        composeRule.onNodeWithText("레벨 부족").assertExists()
        composeRule.onNodeWithTag("equipment-required-level-1004", useUnmergedTree = true)
            .assertContentDescriptionEquals("요구 레벨 11, 레벨 부족")
        composeRule.onNodeWithTag("equipment-level-lock-1004", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun shopItemCardSeparatesSelectionDetailAndDirectPurchaseEvents() {
        val events = mutableListOf<ShopEvent>()
        val item = leatherHat()
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = populatedState().copy(
                        selectedEquipmentId = item.equipmentId,
                        items = listOf(item),
                    ),
                    onEvent = events::add,
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-${item.equipmentId}"))
        composeRule.onNodeWithTag("shop-equipment-card-${item.equipmentId}").performClick()
        assertEquals(listOf(ShopEvent.SelectEquipment(item.equipmentId)), events)

        composeRule.onNodeWithTag("shop-equipment-detail-action-${item.equipmentId}")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(ShopEvent.OpenEquipmentDetail(item.equipmentId), events.last())
        assertEquals(2, events.size)

        composeRule.onNodeWithTag("shop-card-purchase-${item.equipmentId}")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        assertEquals(
            ShopEvent.ExecuteEquipmentAction(item.action),
            events.last(),
        )
        assertEquals(3, events.size)
    }

    @Test
    fun shopItemCardUsesFourExactActionLabelsAndStableBottomEndButtonBounds() {
        val state = mutableStateOf(populatedState())
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        var baselineCardHeight: Float? = null
        var baselineLocalLeft: Float? = null
        var baselineLocalTop: Float? = null
        fun assertAction(
            item: ShopEquipmentUiModel,
            label: String,
            enabled: Boolean,
            reason: String? = null,
        ) {
            composeRule.runOnIdle { state.value = populatedState().copy(items = listOf(item)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-equipment-card-${item.equipmentId}"))
            val cardBounds = composeRule.onNodeWithTag("shop-equipment-card-${item.equipmentId}")
                .fetchSemanticsNode().boundsInRoot
            val actionNode = composeRule.onNodeWithTag("shop-card-purchase-${item.equipmentId}")
                .assertTextEquals(label)
                .assertWidthIsEqualTo(104.dp)
                .assertHeightIsEqualTo(48.dp)
            val actionBounds = actionNode.fetchSemanticsNode().boundsInRoot
            if (enabled) actionNode.assertIsEnabled() else actionNode.assertIsNotEnabled()
            val inset = 12f * composeRule.density.density
            assertTrue(abs((cardBounds.right - actionBounds.right) - inset) <= 1f)
            assertTrue(abs((cardBounds.bottom - actionBounds.bottom) - inset) <= 1f)
            baselineCardHeight?.let { assertTrue(abs(it - cardBounds.height) <= 1f) }
            baselineLocalLeft?.let {
                assertTrue(abs(it - (actionBounds.left - cardBounds.left)) <= 1f)
            }
            baselineLocalTop?.let {
                assertTrue(abs(it - (actionBounds.top - cardBounds.top)) <= 1f)
            }
            if (baselineCardHeight == null) baselineCardHeight = cardBounds.height
            if (baselineLocalLeft == null) baselineLocalLeft = actionBounds.left - cardBounds.left
            if (baselineLocalTop == null) baselineLocalTop = actionBounds.top - cardBounds.top

            val reasonNode = composeRule.onNodeWithTag(
                "shop-action-reason-${item.equipmentId}",
                useUnmergedTree = true,
            )
            if (reason == null) {
                reasonNode.assertExists()
            } else {
                reasonNode.assertTextEquals(reason)
                val reasonBounds = reasonNode.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    reasonBounds.bottom <= actionBounds.top ||
                        reasonBounds.right <= actionBounds.left,
                )
            }
        }

        assertAction(leatherHat(), "구매", enabled = true)
        val insufficientGold = PurchaseUnavailableReason.InsufficientGold(340L, 240L)
        assertAction(
            leatherHat().copy(
                purchaseAvailability = PurchaseAvailability.Unavailable(insufficientGold),
                action = ShopEquipmentAction.PurchaseUnavailable(insufficientGold),
            ),
            "구매 불가",
            enabled = false,
            reason = "골드가 부족합니다. 필요 340, 보유 240.",
        )
        assertAction(
            leatherHat().copy(
                isOwned = true,
                purchaseAvailability = PurchaseAvailability.Unavailable(
                    PurchaseUnavailableReason.AlreadyOwned,
                ),
                action = ShopEquipmentAction.Equip(
                    ownedEquipmentId = 503L,
                    slot = EquipmentSlot.HELMET,
                ),
            ),
            "장착",
            enabled = true,
        )
        assertAction(
            leatherHat().copy(
                isOwned = true,
                isEquipped = true,
                purchaseAvailability = PurchaseAvailability.Unavailable(
                    PurchaseUnavailableReason.AlreadyOwned,
                ),
                action = ShopEquipmentAction.Unequip(
                    equipmentId = 1_003L,
                    slot = EquipmentSlot.HELMET,
                ),
            ),
            "해제",
            enabled = true,
        )
    }

    @Test
    fun cardAndDetailShareTypedActionLabelsBoundsAndEvents() {
        val events = mutableListOf<ShopEvent>()
        val purchase = leatherHat()
        val unavailableReason = PurchaseUnavailableReason.LevelTooLow(11, 10)
        val unavailable = purchase.copy(
            purchaseAvailability = PurchaseAvailability.Unavailable(unavailableReason),
            action = ShopEquipmentAction.PurchaseUnavailable(unavailableReason),
        )
        val equip = purchase.copy(
            isOwned = true,
            purchaseAvailability = PurchaseAvailability.Unavailable(
                PurchaseUnavailableReason.AlreadyOwned,
            ),
            action = ShopEquipmentAction.Equip(503L, EquipmentSlot.HELMET),
        )
        val unequip = equip.copy(
            isEquipped = true,
            action = ShopEquipmentAction.Unequip(1_003L, EquipmentSlot.HELMET),
        )
        val fixtures = listOf(
            purchase to "구매",
            unavailable to "구매 불가",
            equip to "장착",
            unequip to "해제",
        )
        val state = mutableStateOf(
            populatedState().copy(items = listOf(purchase), selectedDetail = purchase),
        )
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(400.dp).height(720.dp)) {
                    ShopContent(
                        state = state.value,
                        onEvent = events::add,
                        onOpenInventory = {},
                    )
                }
            }
        }

        fixtures.forEach { (item, label) ->
            composeRule.runOnIdle {
                state.value = populatedState().copy(
                    items = listOf(item),
                    selectedDetail = item,
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shop-equipment-detail-scroll")
                .performScrollToNode(hasTestTag("shop-detail-purchase"))
            val detailAction = composeRule.onNodeWithTag("shop-detail-purchase")
                .assertTextEquals(label)
                .assertWidthIsEqualTo(104.dp)
                .assertHeightIsEqualTo(48.dp)
            val detailArea = composeRule.onNodeWithTag("shop-detail-action-area")
                .fetchSemanticsNode().boundsInRoot
            val actionBounds = detailAction.fetchSemanticsNode().boundsInRoot
            assertTrue(abs(detailArea.right - actionBounds.right) <= 1f)
            assertTrue(abs(detailArea.bottom - actionBounds.bottom) <= 1f)

            if (item.action.isEnabled) {
                detailAction.assertIsEnabled().performClick()
                assertEquals(ShopEvent.ExecuteEquipmentAction(item.action), events.last())
            } else {
                detailAction.assertIsNotEnabled()
            }
        }
    }

    @Test
    fun unequippingSelectedEquippedItemClosesDetailClearsPreviewAndShowsEquipAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            insertRichMaxLevelCharacter(database)
            val repository = RoomEquipmentRepository(database, FixedClock)
            val viewModel = ShopViewModel(
                repository = repository,
                purchaseEquipment = PurchaseEquipmentUseCase(repository),
                equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
                unequipEquipment = UnequipEquipmentUseCase(repository),
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            )
            composeRule.setContent {
                TodoQuestTheme {
                    ShopScreen(
                        viewModel = viewModel,
                        onOpenInventory = {},
                    )
                }
            }

            waitForTag("shop-equipment-list")
            selectCategory(viewModel, EquipmentType.HELMET)
            purchaseAndEquip(viewModel, EquipmentCatalogSeeder.LEATHER_HAT_ID)
            selectCategory(viewModel, EquipmentType.HELMET)
            composeRule.runOnIdle {
                viewModel.onEvent(
                    ShopEvent.OpenEquipmentDetail(EquipmentCatalogSeeder.LEATHER_HAT_ID),
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedDetail?.action is ShopEquipmentAction.Unequip
            }
            composeRule.onNodeWithTag("shop-equipment-detail-scroll")
                .performScrollToNode(hasTestTag("shop-detail-purchase"))
            composeRule.onNodeWithTag("shop-detail-purchase")
                .assertTextEquals("해제")
                .assertIsEnabled()
                .performClick()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.selectedEquipmentId == null &&
                    viewModel.uiState.value.selectedDetail == null &&
                    viewModel.uiState.value.unequipState is ShopUnequipState.Success &&
                    viewModel.uiState.value.statSummary.run {
                        attack.difference == 0 && maxHp.difference == 0 && defense.difference == 0
                    }
            }
            composeRule.onNodeWithTag("shop-equipment-detail").assertDoesNotExist()
            composeRule.onNodeWithText("해제 완료").assertIsDisplayed()
            composeRule.onNodeWithText("확인").performClick()

            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))
            composeRule.onNodeWithTag("shop-equipment-slot-helmet")
                .assertContentDescriptionEquals("투구 슬롯 비어 있음")
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(
                    hasTestTag(
                        "shop-equipment-card-${EquipmentCatalogSeeder.LEATHER_HAT_ID}",
                    ),
                )
            composeRule.onNodeWithTag(
                "shop-card-purchase-${EquipmentCatalogSeeder.LEATHER_HAT_ID}",
            ).assertTextEquals("장착")
        } finally {
            database.close()
        }
    }

    @Test
    fun ownedCardUsesDistinctThemeContainerWithCheckAndSeparateOwnedEquippedText() {
        val unowned = leatherHat()
        val owned = ironHelmet().copy(
            isOwned = true,
            isEquipped = true,
            purchaseAvailability = PurchaseAvailability.Unavailable(
                PurchaseUnavailableReason.AlreadyOwned,
            ),
        )
        var surfaceColor = 0
        var secondaryContainerColor = 0
        composeRule.setContent {
            TodoQuestTheme {
                surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer.toArgb()
                ShopContent(
                    state = populatedState().copy(items = listOf(unowned, owned)),
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        val unownedCard = composeRule.onNodeWithTag("shop-equipment-card-1003")
            .captureToImage().asAndroidBitmap()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        val ownedNode = composeRule.onNodeWithTag("shop-equipment-card-1004")
            .assertTextContains("보유 중")
            .assertTextContains("장착 중")
        val ownedCard = ownedNode.captureToImage().asAndroidBitmap()

        composeRule.onNodeWithTag("equipment-owned-icon-1004", useUnmergedTree = true)
            .assertExists()
        val insetPx = (8f * composeRule.density.density).toInt().coerceAtLeast(1)
        assertEquals(
            "미보유 카드는 surface container를 사용해야 합니다.",
            surfaceColor,
            unownedCard.getPixel(unownedCard.width - insetPx, unownedCard.height / 2),
        )
        assertEquals(
            "보유 카드는 secondaryContainer로 실제 캡처 색상이 달라야 합니다.",
            secondaryContainerColor,
            ownedCard.getPixel(ownedCard.width - insetPx, ownedCard.height / 2),
        )
        assertNotEquals(surfaceColor, secondaryContainerColor)
    }

    @Test
    fun saleBadgeIsHiddenByDefaultAndOnlyNotForSaleExceptionAppearsOnCardAndDetail() {
        val state = mutableStateOf(
            populatedState().copy(items = listOf(leatherHat())),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1003"))
        composeRule.onAllNodesWithText("판매 중").assertCountEquals(0)

        val unavailable = ironHelmet().copy(
            isForSale = false,
            purchaseAvailability = PurchaseAvailability.Unavailable(
                PurchaseUnavailableReason.NotForSale,
            ),
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(items = listOf(unavailable))
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        assertTrue(
            composeRule.onAllNodesWithText("판매 중지")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = unavailable)
        }
        assertTrue(
            composeRule.onAllNodesWithText("판매 중지")
                .fetchSemanticsNodes()
                .size >= 2,
        )
        composeRule.onAllNodesWithText("판매 중").assertCountEquals(0)
    }

    @Test
    fun categoryOrderCardLabelsAndChestLegFallbacksAreExplicit() {
        var receivedEvent: ShopEvent? = null
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = populatedState(selectedCategory = EquipmentType.CHEST),
                    onEvent = { receivedEvent = it },
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag(
            "shop-equipment-slot-empty-icon-chest",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "shop-equipment-slot-empty-icon-legs",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-row"))
        val expectedCategories = listOf("전체", "무기", "투구", "상의", "하의", "장갑", "신발", "액세서리")
        expectedCategories.forEachIndexed { index, label ->
            composeRule.onNodeWithTag("shop-category-row").performScrollToIndex(index)
            composeRule.onNodeWithTag("shop-category-$index")
                .assertTextContains(label)
                .assertHeightIsEqualTo(48.dp)
            if (index == 3) {
                composeRule.onNodeWithTag("shop-category-$index").assertIsSelected()
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1005"))
        composeRule.onNodeWithText("천 상의").assertIsDisplayed()
        composeRule.onNodeWithTag("equipment-rarity-1005", useUnmergedTree = true)
            .assertTextContains("일반")
        composeRule.onNodeWithTag("equipment-type-1005", useUnmergedTree = true)
            .assertTextContains("상의", substring = true)
        composeRule.onNodeWithTag("equipment-price-1005", useUnmergedTree = true)
            .assertTextContains("22 골드")
        composeRule.onNodeWithText("최대 체력 +12").assertExists()
        composeRule.onNodeWithContentDescription("상의 기본 이미지").assertExists()
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-chest",
            ancestorTag = "shop-equipment-card-1005",
        )

        composeRule.runOnIdle {
            receivedEvent = null
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-row"))
        composeRule.onNodeWithTag("shop-category-4").performClick()
        assertEquals(ShopEvent.SelectCategory(EquipmentType.LEGS), receivedEvent)

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1008"))
        composeRule.onNodeWithContentDescription("하의 기본 이미지").assertExists()
        assertTaggedDescendantDisplayed(
            tag = "equipment-placeholder-legs",
            ancestorTag = "shop-equipment-card-1008",
        )
    }

    @Test
    fun detailShowsAllModifiersSameSlotSignedComparisonsAndImageFallback() {
        val state = mutableStateOf(populatedState())
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1005"))
        composeRule.runOnIdle {
            state.value = state.value.copy(
                selectedDetail = breastplate(
                            imageKey = "equipment_image_not_renderable",
                            comparisons = listOf(
                                comparison(DerivedStatType.MAX_HP, 30, 50, 20),
                                comparison(DerivedStatType.DEFENSE, 12, 9, -3),
                                comparison(DerivedStatType.HP_RECOVERY, 0, 0, 0),
                            ),
                        ),
            )
        }

        composeRule.onNodeWithTag("shop-equipment-detail").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("상의 기본 이미지").assertCountEquals(2)
        composeRule.onNodeWithText("철 흉갑").assertExists()
        composeRule.onNodeWithText("영웅").assertExists()
        composeRule.onNodeWithText("동일 부위 현재 장비").assertExists()
        composeRule.onNodeWithText("+20").assertExists()
        composeRule.onNodeWithText("-3").assertExists()
        composeRule.onNodeWithText("변화 없음").assertExists()
        composeRule.onNodeWithText("최대 체력 +50").assertExists()
        composeRule.onNodeWithText("방어력 +9").assertExists()
        composeRule.onNodeWithText("활력 +5").assertExists()
        composeRule.onNodeWithText("최대 체력 +12.0%").assertExists()
    }

    @Test
    fun unavailableAndProcessingPurchaseDisableActionsWithConcreteReasons() {
        val state = mutableStateOf(
            populatedState(
                selectedDetail = breastplate(
                    purchaseAvailability = PurchaseAvailability.Unavailable(
                        PurchaseUnavailableReason.InsufficientGold(
                            price = 1_200L,
                            availableGold = 900L,
                        ),
                    ),
                ),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = {},
                    onOpenInventory = {},
                )
            }
        }

        composeRule.onNodeWithText("골드가 부족합니다. 필요 1,200, 보유 900.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = populatedState()
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1005"))
        composeRule.runOnIdle {
            state.value = populatedState(
                selectedDetail = breastplate(),
                purchaseConfirmation = PurchaseConfirmationUiState(
                    equipmentId = 1_007L,
                    equipmentNameKey = "equipment_name_iron_breastplate",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    price = 1_200L,
                    currentGold = 5_000L,
                    expectedRemainingGold = 3_800L,
                ),
                purchaseState = PurchaseState.Processing(1_007L),
            )
        }

        composeRule.onNodeWithTag("shop-confirm-purchase").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsNotEnabled()
        composeRule.onNodeWithTag("shop-equipment-card-1005").assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("구매 처리 중").assertCountEquals(2)
    }

    @Test
    fun purchaseConfirmationAndSuccessExposeAllRequiredChoices() {
        val events = mutableListOf<ShopEvent>()
        var inventoryOpened = false
        val state = mutableStateOf(
            populatedState(
                selectedDetail = breastplate(),
                purchaseConfirmation = PurchaseConfirmationUiState(
                    equipmentId = 1_007L,
                    equipmentNameKey = "equipment_name_iron_breastplate",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    price = 1_200L,
                    currentGold = 5_000L,
                    expectedRemainingGold = 3_800L,
                ),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                ShopContent(
                    state = state.value,
                    onEvent = { events.add(it) },
                    onOpenInventory = { inventoryOpened = true },
                )
            }
        }

        composeRule.onNodeWithTag("shop-purchase-confirmation").assertIsDisplayed()
        composeRule.onNodeWithText("“철 흉갑” 장비를 구매할까요?").assertExists()
        composeRule.onNodeWithText("부위 상의").assertExists()
        composeRule.onAllNodesWithText("현재 골드 5,000").assertCountEquals(2)
        composeRule.onNodeWithText("구매 후 골드 3,800").assertExists()
        composeRule.onNodeWithTag("shop-confirm-purchase").performClick()
        assertEquals(ShopEvent.ConfirmPurchase, events.last())

        composeRule.runOnIdle {
            state.value = populatedState(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 77L,
                    equipmentId = 1_007L,
                    equipmentNameKey = "equipment_name_iron_breastplate",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    currentGold = 3_800L,
                ),
            )
        }

        composeRule.onNodeWithText("철 흉갑 구매 완료").assertIsDisplayed()
        composeRule.onNodeWithText("바로 장착").assertIsEnabled().performClick()
        assertEquals(
            ShopEvent.EquipPurchased(
                ownedEquipmentId = 77L,
                targetSlot = EquipmentSlot.CHEST,
            ),
            events.last(),
        )

        composeRule.runOnIdle {
            state.value = state.value.copy(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 77L,
                    equipmentId = 1_007L,
                    equipmentNameKey = "equipment_name_iron_breastplate",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    currentGold = 3_800L,
                ),
            )
        }
        composeRule.onNodeWithText("인벤토리로 이동").performClick()
        assertTrue(inventoryOpened)
        assertEquals(ShopEvent.ConsumePurchaseSuccess, events.last())

        composeRule.runOnIdle {
            inventoryOpened = false
            state.value = state.value.copy(
                purchaseState = PurchaseState.Success(
                    ownedEquipmentId = 77L,
                    equipmentId = 1_007L,
                    equipmentNameKey = "equipment_name_iron_breastplate",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    currentGold = 3_800L,
                ),
            )
        }
        composeRule.onNodeWithText("계속 쇼핑").performClick()
        assertEquals(ShopEvent.ConsumePurchaseSuccess, events.last())
    }

    @Test
    fun smallWidthLargeFontKeepsTopBarGoldInventoryCardAndDialogActionsReachable() {
        val iron = ironHelmet()
        val state = mutableStateOf(
            populatedState(
                currentGold = 12_345L,
                selectedCategory = EquipmentType.HELMET,
            ).copy(items = listOf(iron)),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        ShopContent(
                            state = state.value,
                            onEvent = {},
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-back").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-open-inventory").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("shop-gold-summary")
            .assertContentDescriptionEquals("보유 골드 12,345")
        composeRule.onNodeWithTag("shop-merchant-banner")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(
                "장비 상점 대장장이. 필요한 장비를 골라 보게.",
            )
        val iconBounds = composeRule.onNodeWithTag("shop-gold-icon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithTag("shop-gold-value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val density = composeRule.density.density
        assertTrue(abs((valueBounds.left - iconBounds.right) - (4f * density)) <= density)

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-row"))
        composeRule.onNodeWithTag("shop-category-row").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1004"))
        composeRule.onNodeWithTag("shop-equipment-card-1004").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_headgear_iron_helmet",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("철 투구").assertIsDisplayed()
        composeRule.onNodeWithText("340 골드").assertIsDisplayed()
        val cardBounds = composeRule.onNodeWithTag("shop-equipment-card-1004")
            .fetchSemanticsNode().boundsInRoot
        val actionBounds = listOf(
            composeRule.onNodeWithTag("equipment-price-1004", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("shop-equipment-detail-action-1004")
                .fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("shop-card-purchase-1004")
                .fetchSemanticsNode().boundsInRoot,
        )
        actionBounds.forEach { bounds ->
            assertTrue(bounds.left >= cardBounds.left)
            assertTrue(bounds.right <= cardBounds.right)
            assertTrue(bounds.top >= cardBounds.top)
            assertTrue(bounds.bottom <= cardBounds.bottom)
        }
        actionBounds.forEachIndexed { index, current ->
            actionBounds.drop(index + 1).forEach { next ->
                assertTrue(
                    current.right <= next.left || next.right <= current.left ||
                        current.bottom <= next.top || next.bottom <= current.top,
                )
            }
        }

        composeRule.runOnIdle {
            state.value = populatedState(
                currentGold = 12_345L,
                selectedCategory = EquipmentType.HELMET,
                selectedDetail = iron,
            ).copy(items = listOf(iron))
        }
        composeRule.onNodeWithTag("shop-equipment-detail-scroll")
            .performScrollToNode(hasTestTag("shop-detail-purchase"))
        composeRule.onNodeWithTag("shop-detail-purchase").assertIsDisplayed().assertIsEnabled()
        composeRule.onAllNodesWithText("철 투구").assertCountEquals(2)
        composeRule.onAllNodesWithText("340 골드").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("철 투구 이미지").assertCountEquals(2)

        composeRule.runOnIdle {
            state.value = populatedState(
                currentGold = 12_345L,
                purchaseConfirmation = PurchaseConfirmationUiState(
                    equipmentId = 1_004L,
                    equipmentNameKey = "equipment_name_iron_helmet",
                    type = EquipmentType.HELMET,
                    slot = EquipmentSlot.HELMET,
                    price = 340L,
                    currentGold = 12_345L,
                    expectedRemainingGold = 12_005L,
                ),
            )
        }
        composeRule.onNodeWithTag("shop-confirm-purchase")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("shop-cancel-purchase")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun smallWidthLargeFontKeepsOutfitArtworkNamePriceAndPurchaseReachable() {
        val outfit = breastplate(imageKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE)
        val state = mutableStateOf(
            populatedState(
                currentGold = 12_345L,
                selectedCategory = EquipmentType.CHEST,
            ).copy(items = listOf(outfit)),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        ShopContent(
                            state = state.value,
                            onEvent = {},
                            onOpenInventory = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-1007"))
        composeRule.onNodeWithTag(
            "equipment_artwork_top_iron_breastplate",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("철 흉갑").assertIsDisplayed()
        composeRule.onNodeWithText("1,200 골드").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = state.value.copy(selectedDetail = outfit)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop-equipment-detail-scroll")
            .performScrollToNode(hasTestTag("shop-detail-purchase"))
        composeRule.onNodeWithTag("shop-detail-purchase")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun assertDistinctVisibleArtwork(fixtures: List<OutfitArtworkFixture>) {
        val hashes = fixtures.map { fixture ->
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-equipment-card-${fixture.item.equipmentId}"))
            composeRule.onNodeWithContentDescription("${fixture.displayName} 이미지")
                .assertIsDisplayed()
            val bitmap = composeRule.onNodeWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).assertIsDisplayed().captureToImage().asAndroidBitmap()
            IntArray(bitmap.width * bitmap.height).also { pixels ->
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }.contentHashCode()
        }
        assertEquals("각 장비 artwork는 서로 달라야 합니다.", fixtures.size, hashes.toSet().size)
    }

    private fun insertRichMaxLevelCharacter(database: TodoQuestDatabase) {
        runBlocking {
            database.characterProfileDao().insertProfile(
                CharacterProfileEntity(
                    id = CharacterMapper.DEFAULT_CHARACTER_ID,
                    totalXp = 100_000L,
                    currentGold = 100_000L,
                    strength = 5,
                    vitality = 5,
                    focus = 5,
                    willpower = 5,
                    unspentStatPoints = 98,
                    hasUsedFreeStatReset = false,
                ),
            )
            database.characterProfileDao().insertCurrentState(
                CharacterCurrentStateEntity(
                    characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
                    currentHp = 110,
                    balanceVersion = 1,
                    updatedAtEpochMillis = 0L,
                ),
            )
        }
    }

    private fun selectCategory(
        viewModel: ShopViewModel,
        type: EquipmentType,
    ) {
        composeRule.runOnIdle { viewModel.onEvent(ShopEvent.SelectCategory(type)) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.selectedCategory == type
        }
    }

    private fun awaitOutfitProjection(
        repository: RoomEquipmentRepository,
        expectedTopId: String,
        expectedBottomId: String,
    ): EquippedItems = runBlocking {
        repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
            it.renderedEquippedItems.topId == expectedTopId &&
                it.renderedEquippedItems.bottomId == expectedBottomId
        }.renderedEquippedItems
    }

    private fun awaitGlovesShoesProjection(
        repository: RoomEquipmentRepository,
        glovesId: String?,
        shoesId: String,
    ): EquippedItems = runBlocking {
        repository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
            it.renderedEquippedItems.glovesId == glovesId &&
                it.renderedEquippedItems.shoesId == shoesId
        }.renderedEquippedItems
    }

    private fun assertCatalogCard(
        equipmentId: Long,
        artworkKey: String,
        displayName: String,
        price: String,
        rarity: String,
    ) {
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-equipment-card-$equipmentId"))
        composeRule.onNodeWithTag("shop-equipment-card-$equipmentId").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "equipment_artwork_$artworkKey",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$displayName 이미지").assertIsDisplayed()
        composeRule.onNodeWithText(displayName).assertIsDisplayed()
        composeRule.onNodeWithText(price).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(rarity).fetchSemanticsNodes().isNotEmpty())
    }

    private fun composeCharacter(
        composer: CharacterBitmapComposer,
        items: EquippedItems,
    ): Bitmap = requireNotNull(
        composer.compose(
            CharacterRenderState(
                appearance = CharacterLoadoutCatalog.defaultAppearance,
                equippedItems = items,
            ),
        ),
    )

    private fun loadCharacterLayer(fileName: String): Bitmap {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        return assets.open("character/layers/$fileName").use { input ->
            requireNotNull(
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inScaled = false },
                ),
            )
        }
    }

    private fun assertSeamUsesSingleSource(
        actual: Bitmap,
        bottom: Bitmap,
        shoes: Bitmap,
        x: Int,
        y: Int,
    ) {
        val bottomPixel = bottom.getPixel(x, y)
        val shoesPixel = shoes.getPixel(x, y)
        val expected = bottomPixel.takeIf { it ushr 24 != 0 } ?: shoesPixel
        assertEquals("발목 seam은 뒤의 한 source 색만 사용해야 합니다. ($x, $y)", expected, actual.getPixel(x, y))
        assertEquals("발목 seam에 투명 틈이 없어야 합니다. ($x, $y)", 255, actual.getPixel(x, y) ushr 24)
    }

    private fun Bitmap.opaqueBoundsInclusive(): List<Int> {
        val opaque = buildList {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (getPixel(x, y) ushr 24 == 255) add(x to y)
                }
            }
        }
        require(opaque.isNotEmpty())
        return listOf(
            opaque.minOf { it.first },
            opaque.minOf { it.second },
            opaque.maxOf { it.first },
            opaque.maxOf { it.second },
        )
    }

    private fun assertOutfitProjectionPreservesOtherSlots(items: EquippedItems) {
        val defaults = CharacterLoadoutCatalog.defaultEquippedItems
        assertEquals(defaults.headId, items.headId)
        assertEquals(defaults.shoesId, items.shoesId)
        assertEquals(defaults.accessoryId, items.accessoryId)
        assertEquals(defaults.weaponId, items.weaponId)
    }

    private fun assertOutfitCompositeIntegrity(items: EquippedItems) {
        assertOutfitProjectionPreservesOtherSlots(items)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val composer = CharacterBitmapComposer(context.assets)
        val actual = requireNotNull(
            composer.compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = items,
                ),
            ),
        )
        val baseline = requireNotNull(
            composer.compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = items.copy(
                        topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
                        bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                    ),
                ),
            ),
        )

        var changedTopPixels = 0
        var changedBottomPixels = 0
        for (y in 0 until CharacterCanvasSize) {
            for (x in 0 until CharacterCanvasSize) {
                val inTop = x in TopLeft..TopRight && y in TopTop..TopBottom
                val inBottom = x in BottomLeft..BottomRight && y in BottomTop..BottomBottom
                if (!inTop && !inBottom) {
                    assertEquals(
                        "상의·하의 외부의 얼굴·투구·무기·신발 픽셀은 보존되어야 합니다. ($x, $y)",
                        baseline.getPixel(x, y),
                        actual.getPixel(x, y),
                    )
                } else if (baseline.getPixel(x, y) != actual.getPixel(x, y)) {
                    if (inTop) changedTopPixels += 1
                    if (inBottom) changedBottomPixels += 1
                }
            }
        }
        assertTrue("상의 layer가 합성 결과에 반영되어야 합니다.", changedTopPixels > 0)
        assertTrue("하의 layer가 합성 결과에 반영되어야 합니다.", changedBottomPixels > 0)

        for (y in WaistTop..WaistBottom) {
            for (x in BottomLeft..BottomRight) {
                assertEquals("허리 seam에 투명 틈이 없어야 합니다.", 255, actual.getPixel(x, y) ushr 24)
            }
        }
        for (y in AnkleTop..AnkleBottom) {
            for (x in LeftAnkleLeft..LeftAnkleRight) {
                assertEquals("왼쪽 발목 seam에 투명 틈이 없어야 합니다.", 255, actual.getPixel(x, y) ushr 24)
            }
            for (x in RightAnkleLeft..RightAnkleRight) {
                assertEquals("오른쪽 발목 seam에 투명 틈이 없어야 합니다.", 255, actual.getPixel(x, y) ushr 24)
            }
        }

        listOf(
            "character/layers/hands_front.png",
            "character/layers/weapon_held_default_sword.png",
            "character/layers/weapon_front_default_sword.png",
        ).forEach { path ->
            val foreground = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            for (y in 0 until CharacterCanvasSize) {
                for (x in 0 until CharacterCanvasSize) {
                    if (foreground.getPixel(x, y) ushr 24 != 0) {
                        assertEquals(
                            "$path 픽셀은 outfit layer 위에서 보존되어야 합니다. ($x, $y)",
                            baseline.getPixel(x, y),
                            actual.getPixel(x, y),
                        )
                    }
                }
            }
        }
    }

    private fun battleState(items: EquippedItems) = BattleMapUiState.Content(
        player = BattleUnitUiModel(
            id = "player",
            type = BattleUnitType.PLAYER,
            sprite = BattleSpriteUiModel.LayeredCharacter(
                renderState = CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = items,
                ),
                frame = BattleMapDefaults.PLAYER_FRAME,
            ),
            position = BattleMapDefaults.PLAYER_POSITION,
            scale = 1f,
            groundOffset = 0f,
            currentHp = 100,
            maxHp = 100,
            nameResId = R.string.battle_player_name,
            deathAnnouncementResId = R.string.battle_player_death_announcement,
        ),
        monsters = emptyList(),
        stageNumber = 1,
    )

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForEquippedSlot(
        viewModel: ShopViewModel,
        slot: EquipmentSlot,
        equipmentId: Long,
    ) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.equipmentSlots.singleOrNull { it.slot == slot }
                ?.let { it.equipmentId == equipmentId && it.isEquipped } == true
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
    }

    private fun assertTaggedDescendantDisplayed(
        tag: String,
        ancestorTag: String,
    ) {
        composeRule.onNode(
            matcher = hasTestTag(tag).and(hasAnyAncestor(hasTestTag(ancestorTag))),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    private fun purchaseAndEquip(
        viewModel: ShopViewModel,
        equipmentId: Long,
    ) {
        val cardTag = "shop-equipment-card-$equipmentId"
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag(cardTag))
        composeRule.onNodeWithTag("shop-equipment-detail-action-$equipmentId")
            .performClick()
        waitForTag("shop-equipment-detail")
        composeRule.onNodeWithTag("shop-equipment-detail-scroll")
            .performScrollToNode(hasTestTag("shop-detail-purchase"))
        composeRule.onNodeWithTag("shop-detail-purchase").performClick()
        waitForTag("shop-purchase-confirmation")
        composeRule.onNodeWithTag("shop-confirm-purchase").performClick()
        waitForTag("shop-purchase-success")
        composeRule.onNodeWithText("바로 장착").performClick()
        waitForText("장착 완료")
        composeRule.onNodeWithText("확인").performClick()
        composeRule.runOnIdle {
            viewModel.onEvent(ShopEvent.CloseEquipmentDetail)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("shop-equipment-detail")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun purchaseAndEquipWeapon(
        viewModel: ShopViewModel,
        fixture: WeaponArtworkFixture,
    ) {
        val equipmentId = fixture.item.equipmentId
        val cardTag = "shop-equipment-card-$equipmentId"
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag(cardTag))
        composeRule.onNodeWithTag("shop-equipment-detail-action-$equipmentId")
            .performClick()
        waitForTag("shop-equipment-detail")
        composeRule.onNodeWithTag("shop-equipment-detail-type")
            .assertTextContains("무기 · ${fixture.weaponTypeName}")
        composeRule.onAllNodesWithTag(
            "equipment_artwork_${fixture.imageKey}",
            useUnmergedTree = true,
        ).assertCountEquals(2)
        composeRule.runOnIdle {
            val detail = requireNotNull(viewModel.uiState.value.selectedDetail)
            assertEquals(fixture.item.weaponType, detail.weaponType)
            assertTrue(
                detail.comparisons.any { comparison ->
                    comparison.target == StatTarget.Derived(DerivedStatType.ATTACK) &&
                        comparison.difference != 0
                },
            )
        }
        composeRule.onNodeWithTag("shop-equipment-detail-scroll")
            .performScrollToNode(hasTestTag("shop-detail-purchase"))
        composeRule.onNodeWithTag("shop-detail-purchase").performClick()
        waitForTag("shop-purchase-confirmation")
        composeRule.onNodeWithTag("shop-confirm-purchase").performClick()
        waitForTag("shop-purchase-success")
        assertTrue(
            composeRule.onAllNodesWithTag(
                "equipment_artwork_${fixture.imageKey}",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("바로 장착").performClick()
        waitForText("장착 완료")
        composeRule.onNodeWithText("확인").performClick()
        composeRule.runOnIdle {
            viewModel.onEvent(ShopEvent.CloseEquipmentDetail)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("shop-equipment-detail")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertSpriteMatchesOpaqueSourcePixels(
        expectedItems: EquippedItems,
        tag: String = "shop-character-sprite",
    ) {
        if (tag == "shop-character-sprite") {
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag("shop-character-preview"))
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = requireNotNull(
            CharacterBitmapComposer(context.assets).compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = expectedItems,
                ),
            ),
        )
        val captured = composeRule.onNodeWithTag(
            tag,
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        if (tag == "battle-player-layer") {
            assertBattleSpriteContainsProjectedOutfit(
                captured = captured,
                expected = expected,
                expectedItems = expectedItems,
            )
            return
        }
        var comparedPixels = 0
        for (y in 0 until CharacterCanvasSize) {
            for (x in 0 until CharacterCanvasSize) {
                val expectedColor = expected.getPixel(x, y)
                if (expectedColor ushr 24 == 0) continue
                assertEquals(
                    "shared sprite pixel must match source at ($x, $y)",
                    expectedColor,
                    captured.sourcePixel(x, y),
                )
                comparedPixels += 1
            }
        }
        assertTrue("shared sprite must contain opaque character pixels", comparedPixels > 0)
    }

    private fun assertBattleSpriteContainsProjectedOutfit(
        captured: Bitmap,
        expected: Bitmap,
        expectedItems: EquippedItems,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baseline = requireNotNull(
            CharacterBitmapComposer(context.assets).compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = expectedItems.copy(
                        topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
                        bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                    ),
                ),
            ),
        )
        val capturedColors = buildSet {
            for (y in 0 until captured.height) {
                for (x in 0 until captured.width) {
                    add(captured.getPixel(x, y))
                }
            }
        }

        fun projectedColors(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): Set<Int> = buildSet {
            for (y in top..bottom) {
                for (x in left..right) {
                    val color = expected.getPixel(x, y)
                    if (color ushr 24 != 0 && color != baseline.getPixel(x, y)) {
                        add(color)
                    }
                }
            }
        }

        val topColors = projectedColors(TopLeft, TopTop, TopRight, TopBottom)
        val bottomColors = projectedColors(BottomLeft, BottomTop, BottomRight, BottomBottom)
        assertTrue("battle player must expose projected top colors", topColors.isNotEmpty())
        assertTrue("battle player must expose projected bottom colors", bottomColors.isNotEmpty())
        assertTrue(
            "battle player must render every visible projected top color",
            capturedColors.containsAll(topColors),
        )
        assertTrue(
            "battle player must render every visible projected bottom color",
            capturedColors.containsAll(bottomColors),
        )
    }

    private fun assertBattleSpriteContainsProjectedGlovesAndShoes(items: EquippedItems) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val composer = CharacterBitmapComposer(context.assets)
        val expected = requireNotNull(
            composer.compose(
                CharacterRenderState(CharacterLoadoutCatalog.defaultAppearance, items),
            ),
        )
        val baseline = requireNotNull(
            composer.compose(
                CharacterRenderState(
                    CharacterLoadoutCatalog.defaultAppearance,
                    items.copy(
                        glovesId = null,
                        shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
                    ),
                ),
            ),
        )
        val captured = composeRule.onNodeWithTag(
            "battle-player-layer",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        val capturedColors = buildSet {
            for (y in 0 until captured.height) {
                for (x in 0 until captured.width) add(captured.getPixel(x, y))
            }
        }
        val projectedColors = buildSet {
            for (y in 0 until CharacterCanvasSize) {
                for (x in 0 until CharacterCanvasSize) {
                    val inGloves = x in 21..43 && y in 39..45
                    val inShoes = x in 23..41 && y in 53..58
                    val color = expected.getPixel(x, y)
                    if ((inGloves || inShoes) && color != baseline.getPixel(x, y)) add(color)
                }
            }
        }
        assertTrue("장갑·신발 projection 색상이 있어야 합니다.", projectedColors.isNotEmpty())
        assertTrue(
            "Battle Map shared renderer가 확정된 장갑·신발 projection을 표시해야 합니다.",
            capturedColors.containsAll(projectedColors),
        )
    }

    private fun assertBattleSpriteContainsProjectedWeapon(items: EquippedItems) {
        val weaponId = requireNotNull(items.weaponId)
        val weapon = loadCharacterLayer("$weaponId.png")
        val captured = composeRule.onNodeWithTag(
            "battle-player-layer",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        val capturedColors = buildSet {
            for (y in 0 until captured.height) {
                for (x in 0 until captured.width) add(captured.getPixel(x, y))
            }
        }
        val weaponColors = buildSet {
            for (y in 0 until CharacterCanvasSize) {
                for (x in 0 until CharacterCanvasSize) {
                    val color = weapon.getPixel(x, y)
                    if (color ushr 24 != 0) add(color)
                }
            }
        }
        assertTrue("gameplay weapon must expose opaque colors", weaponColors.isNotEmpty())
        assertTrue(
            "Battle Map shared renderer must render every gameplay weapon color",
            capturedColors.containsAll(weaponColors),
        )
    }

    private fun assertIronSpriteUsesOpenFaceCompositeWithoutLeatherPixels(
        leatherItems: com.todoquest.domain.model.EquippedItems,
        ironItems: com.todoquest.domain.model.EquippedItems,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val composer = CharacterBitmapComposer(context.assets)
        fun compose(items: com.todoquest.domain.model.EquippedItems): Bitmap = requireNotNull(
            composer.compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = items,
                ),
            ),
        )
        val leather = compose(leatherItems)
        val iron = compose(ironItems)
        val withoutHeadgear = compose(ironItems.copy(headId = null))
        val changedPixel = (0 until CharacterCanvasSize).firstNotNullOfOrNull { y ->
            (0 until CharacterCanvasSize).firstNotNullOfOrNull { x ->
                if (
                    iron.getPixel(x, y) ushr 24 != 0 &&
                    leather.getPixel(x, y) != iron.getPixel(x, y)
                ) {
                    x to y
                } else {
                    null
                }
            }
        }
        requireNotNull(changedPixel)
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .performScrollTo()
        val captured = composeRule.onNodeWithTag(
            "shop-character-sprite",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertEquals(
            iron.getPixel(changedPixel.first, changedPixel.second),
            captured.sourcePixel(changedPixel.first, changedPixel.second),
        )
        assertNotEquals(
            leather.getPixel(changedPixel.first, changedPixel.second),
            captured.sourcePixel(changedPixel.first, changedPixel.second),
        )
        for (y in FaceProtectedTop..FaceProtectedBottom) {
            for (x in FaceProtectedLeft..FaceProtectedRight) {
                assertEquals(
                    "iron helmet must keep the face open at ($x, $y)",
                    withoutHeadgear.getPixel(x, y),
                    iron.getPixel(x, y),
                )
            }
        }
        assertSpriteMatchesOpaqueSourcePixels(ironItems)
    }

    private fun Bitmap.sourcePixel(x: Int, y: Int): Int {
        val scale = minOf(width, height) / CharacterCanvasSize
        require(scale > 0) { "captured character sprite must fit the 64x64 source" }
        val destinationSize = CharacterCanvasSize * scale
        val left = (width - destinationSize) / 2
        val top = (height - destinationSize) / 2
        return getPixel(
            left + x * scale + scale / 2,
            top + y * scale + scale / 2,
        )
    }

    private fun Bitmap.containsColorNear(expected: Int, tolerance: Int = 8): Boolean {
        val expectedRed = expected shr 16 and 0xFF
        val expectedGreen = expected shr 8 and 0xFF
        val expectedBlue = expected and 0xFF
        return (0 until height).any { y ->
            (0 until width).any { x ->
                val actual = getPixel(x, y)
                kotlin.math.abs((actual shr 16 and 0xFF) - expectedRed) <= tolerance &&
                    kotlin.math.abs((actual shr 8 and 0xFF) - expectedGreen) <= tolerance &&
                    kotlin.math.abs((actual and 0xFF) - expectedBlue) <= tolerance
            }
        }
    }

    private fun populatedState(
        currentGold: Long = 5_000L,
        selectedCategory: EquipmentType? = null,
        selectedDetail: ShopEquipmentUiModel? = null,
        purchaseConfirmation: PurchaseConfirmationUiState? = null,
        purchaseState: PurchaseState = PurchaseState.Idle,
    ): ShopUiState {
        val chest = clothTop()
        val legs = clothPants()
        return ShopUiState(
            isLoading = false,
            currentGold = currentGold,
            characterLevel = 30,
            equipmentSlots = EquipmentSlot.entries.map { slot ->
                val equipped = slot == EquipmentSlot.WEAPON
                ShopEquipmentSlotUiModel(
                    slot = slot,
                    type = when (slot) {
                        EquipmentSlot.WEAPON -> EquipmentType.WEAPON
                        EquipmentSlot.HELMET -> EquipmentType.HELMET
                        EquipmentSlot.CHEST -> EquipmentType.CHEST
                        EquipmentSlot.LEGS -> EquipmentType.LEGS
                        EquipmentSlot.GLOVES -> EquipmentType.GLOVES
                        EquipmentSlot.SHOES -> EquipmentType.SHOES
                        EquipmentSlot.ACCESSORY -> EquipmentType.ACCESSORY
                    },
                    equipmentId = if (equipped) 1_001L else null,
                    nameKey = if (equipped) "equipment_name_worn_sword" else null,
                    rarity = if (equipped) EquipmentRarity.RARE else null,
                    imageKey = null,
                    isEquipped = equipped,
                    isSelected = selectedCategory == EquipmentType.WEAPON && equipped ||
                        selectedCategory?.name == slot.name,
                )
            },
            statSummary = CharacterStatSummaryUiModel(
                attack = CharacterStatValueUiModel(currentValue = 37, difference = 0),
                maxHp = CharacterStatValueUiModel(currentValue = 245, difference = 0),
                defense = CharacterStatValueUiModel(currentValue = 18, difference = 0),
            ),
            selectedCategory = selectedCategory,
            items = listOf(chest, legs),
            selectedDetail = selectedDetail,
            purchaseConfirmation = purchaseConfirmation,
            purchaseState = purchaseState,
        )
    }

    private fun outfitArtworkFixtures(): List<OutfitArtworkFixture> = listOf(
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.CLOTH_TOP_ID,
            nameKey = "equipment_name_cloth_top",
            descriptionKey = "equipment_description_cloth_top",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.COMMON,
            price = 22L,
            imageKey = CharacterLoadoutCatalog.TOP_CLOTH,
            displayName = "천 상의",
            rarityName = "일반",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
            nameKey = "equipment_name_leather_armor",
            descriptionKey = "equipment_description_leather_armor",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.UNCOMMON,
            price = 130L,
            imageKey = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            displayName = "가죽 갑옷",
            rarityName = "고급",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.IRON_BREASTPLATE_ID,
            nameKey = "equipment_name_iron_breastplate",
            descriptionKey = "equipment_description_iron_breastplate",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.EPIC,
            price = 1_200L,
            imageKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            displayName = "철 흉갑",
            rarityName = "영웅",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.CLOTH_PANTS_ID,
            nameKey = "equipment_name_cloth_pants",
            descriptionKey = "equipment_description_cloth_pants",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.COMMON,
            price = 22L,
            imageKey = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            displayName = "천 바지",
            rarityName = "일반",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.LEATHER_PANTS_ID,
            nameKey = "equipment_name_leather_pants",
            descriptionKey = "equipment_description_leather_pants",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.UNCOMMON,
            price = 120L,
            imageKey = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            displayName = "가죽 바지",
            rarityName = "고급",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.STEEL_GREAVES_ID,
            nameKey = "equipment_name_steel_greaves",
            descriptionKey = "equipment_description_steel_greaves",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.EPIC,
            price = 1_150L,
            imageKey = CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            displayName = "강철 각반",
            rarityName = "영웅",
        ),
    )

    private fun glovesShoesArtworkFixtures(): List<OutfitArtworkFixture> = listOf(
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
            nameKey = "equipment_name_leather_gloves",
            descriptionKey = "equipment_description_leather_gloves",
            type = EquipmentType.GLOVES,
            rarity = EquipmentRarity.UNCOMMON,
            price = 140L,
            imageKey = CharacterLoadoutCatalog.GLOVES_LEATHER,
            displayName = "가죽 장갑",
            rarityName = "고급",
            description = "그립을 안정시켜 힘과 치명타 확률을 높입니다.",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
            nameKey = "equipment_name_steel_gauntlets",
            descriptionKey = "equipment_description_steel_gauntlets",
            type = EquipmentType.GLOVES,
            rarity = EquipmentRarity.RARE,
            price = 410L,
            imageKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
            displayName = "강철 건틀릿",
            rarityName = "희귀",
            description = "정교한 강철 관절이 힘과 치명타 위력을 높입니다.",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID,
            nameKey = "equipment_name_travelers_boots",
            descriptionKey = "equipment_description_travelers_boots",
            type = EquipmentType.SHOES,
            rarity = EquipmentRarity.RARE,
            price = 380L,
            imageKey = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            displayName = "여행자의 장화",
            rarityName = "희귀",
            description = "긴 여정을 위한 장화로 집중과 회복을 돕습니다.",
        ),
        outfitArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
            nameKey = "equipment_name_windwalker_boots",
            descriptionKey = "equipment_description_windwalker_boots",
            type = EquipmentType.SHOES,
            rarity = EquipmentRarity.RARE,
            price = 430L,
            imageKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            displayName = "바람걸음 장화",
            rarityName = "희귀",
            description = "가벼운 밑창이 집중과 방어, 회복 효율을 높입니다.",
        ),
    )

    private fun weaponArtworkFixtures(): List<WeaponArtworkFixture> = listOf(
        weaponArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
            nameKey = "equipment_name_worn_sword",
            descriptionKey = "equipment_description_worn_sword",
            weaponType = WeaponType.LONGSWORD,
            rarity = EquipmentRarity.COMMON,
            price = 20L,
            imageKey = "weapon_worn_sword",
            displayName = "낡은 검",
            weaponTypeName = "장검",
            rarityName = "일반",
            description = "손때가 묻었지만 기본기에 충실한 검입니다.",
            attack = 3,
        ),
        weaponArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
            nameKey = "equipment_name_iron_longsword",
            descriptionKey = "equipment_description_iron_longsword",
            weaponType = WeaponType.LONGSWORD,
            rarity = EquipmentRarity.RARE,
            price = 360L,
            imageKey = "weapon_iron_longsword",
            displayName = "철 장검",
            weaponTypeName = "장검",
            rarityName = "희귀",
            description = "무게감 있는 철날로 공격을 강화합니다.",
            attack = 10,
        ),
        weaponArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.ASH_SPEAR_ID,
            nameKey = "equipment_name_ash_spear",
            descriptionKey = "equipment_description_ash_spear",
            weaponType = WeaponType.SPEAR,
            rarity = EquipmentRarity.COMMON,
            price = 25L,
            imageKey = "weapon_ash_spear",
            displayName = "물푸레나무 창",
            weaponTypeName = "창",
            rarityName = "일반",
            description = "탄력 있는 자루와 날카로운 창끝으로 빈틈을 찌릅니다.",
            attack = 4,
        ),
        weaponArtworkFixture(
            equipmentId = EquipmentCatalogSeeder.STEEL_MACE_ID,
            nameKey = "equipment_name_steel_mace",
            descriptionKey = "equipment_description_steel_mace",
            weaponType = WeaponType.BLUNT,
            rarity = EquipmentRarity.RARE,
            price = 390L,
            imageKey = "weapon_steel_mace",
            displayName = "강철 철퇴",
            weaponTypeName = "둔기",
            rarityName = "희귀",
            description = "묵직한 강철 머리로 공격과 치명타 위력을 높입니다.",
            attack = 12,
        ),
    )

    private fun weaponArtworkFixture(
        equipmentId: Long,
        nameKey: String,
        descriptionKey: String,
        weaponType: WeaponType,
        rarity: EquipmentRarity,
        price: Long,
        imageKey: String,
        displayName: String,
        weaponTypeName: String,
        rarityName: String,
        description: String,
        attack: Int,
    ) = WeaponArtworkFixture(
        item = equipment(
            equipmentId = equipmentId,
            nameKey = nameKey,
            descriptionKey = descriptionKey,
            type = EquipmentType.WEAPON,
            slot = EquipmentSlot.WEAPON,
            weaponType = weaponType,
            rarity = rarity,
            price = price,
            modifiers = listOf(modifier(equipmentId, DerivedStatType.ATTACK, attack)),
            imageKey = imageKey,
        ),
        imageKey = imageKey,
        displayName = displayName,
        weaponTypeName = weaponTypeName,
        rarityName = rarityName,
        priceText = "${formatNumber(price)} 골드",
        modifierText = "공격력 +$attack",
        description = description,
    )

    private fun outfitArtworkFixture(
        equipmentId: Long,
        nameKey: String,
        descriptionKey: String,
        type: EquipmentType,
        rarity: EquipmentRarity,
        price: Long,
        imageKey: String,
        displayName: String,
        rarityName: String,
        description: String = "",
    ) = OutfitArtworkFixture(
        item = equipment(
            equipmentId = equipmentId,
            nameKey = nameKey,
            descriptionKey = descriptionKey,
            type = type,
            slot = when (type) {
                EquipmentType.CHEST -> EquipmentSlot.CHEST
                EquipmentType.LEGS -> EquipmentSlot.LEGS
                EquipmentType.GLOVES -> EquipmentSlot.GLOVES
                EquipmentType.SHOES -> EquipmentSlot.SHOES
                else -> error("unsupported artwork fixture type: $type")
            },
            rarity = rarity,
            price = price,
            modifiers = listOf(modifier(equipmentId, DerivedStatType.DEFENSE, 1)),
            imageKey = imageKey,
        ),
        imageKey = imageKey,
        displayName = displayName,
        rarityName = rarityName,
        description = description,
    )

    private fun ShopEquipmentSlotUiModel.equippedWith(
        fixture: OutfitArtworkFixture,
    ): ShopEquipmentSlotUiModel = copy(
        equipmentId = fixture.item.equipmentId,
        nameKey = fixture.item.nameKey,
        rarity = fixture.item.rarity,
        imageKey = fixture.imageKey,
        isEquipped = true,
    )

    private fun ShopEquipmentSlotUiModel.equippedWith(
        fixture: WeaponArtworkFixture,
    ): ShopEquipmentSlotUiModel = copy(
        equipmentId = fixture.item.equipmentId,
        nameKey = fixture.item.nameKey,
        rarity = fixture.item.rarity,
        imageKey = fixture.imageKey,
        weaponType = fixture.item.weaponType,
        isEquipped = true,
    )

    private fun clothTop(): ShopEquipmentUiModel = equipment(
        equipmentId = 1_005L,
        nameKey = "equipment_name_cloth_top",
        descriptionKey = "equipment_description_cloth_top",
        type = EquipmentType.CHEST,
        slot = EquipmentSlot.CHEST,
        rarity = EquipmentRarity.COMMON,
        price = 22L,
        modifiers = listOf(modifier(1_005L, DerivedStatType.MAX_HP, 12)),
    )

    private fun leatherHat(): ShopEquipmentUiModel = equipment(
        equipmentId = 1_003L,
        nameKey = "equipment_name_leather_hat",
        descriptionKey = "equipment_description_leather_hat",
        type = EquipmentType.HELMET,
        slot = EquipmentSlot.HELMET,
        rarity = EquipmentRarity.COMMON,
        price = 27L,
        modifiers = listOf(modifier(1_003L, DerivedStatType.MAX_HP, 12)),
        imageKey = "headgear_leather_hat",
    )

    private fun ironHelmet(
        imageKey: String? = "headgear_iron_helmet",
    ): ShopEquipmentUiModel = equipment(
        equipmentId = 1_004L,
        nameKey = "equipment_name_iron_helmet",
        descriptionKey = "equipment_description_iron_helmet",
        type = EquipmentType.HELMET,
        slot = EquipmentSlot.HELMET,
        rarity = EquipmentRarity.RARE,
        price = 340L,
        requiredLevel = 11,
        modifiers = listOf(
            modifier(1_004L, DerivedStatType.MAX_HP, 30),
            modifier(1_004L, DerivedStatType.DEFENSE, 6),
            EquipmentStatModifier(
                itemId = 1_004L,
                target = StatTarget.Base(StatType.FOCUS),
                type = ModifierType.FLAT,
                amount = 3,
            ),
        ),
        imageKey = imageKey,
    )

    private fun clothPants(): ShopEquipmentUiModel = equipment(
        equipmentId = 1_008L,
        nameKey = "equipment_name_cloth_pants",
        descriptionKey = "equipment_description_cloth_pants",
        type = EquipmentType.LEGS,
        slot = EquipmentSlot.LEGS,
        rarity = EquipmentRarity.COMMON,
        price = 22L,
        modifiers = listOf(modifier(1_008L, DerivedStatType.HP_RECOVERY, 1)),
    )

    private fun breastplate(
        imageKey: String? = null,
        comparisons: List<EquipmentStatComparison> = listOf(
            comparison(DerivedStatType.MAX_HP, 30, 50, 20),
        ),
        purchaseAvailability: PurchaseAvailability = PurchaseAvailability.Available,
    ): ShopEquipmentUiModel = equipment(
        equipmentId = 1_007L,
        nameKey = "equipment_name_iron_breastplate",
        descriptionKey = "equipment_description_iron_breastplate",
        type = EquipmentType.CHEST,
        slot = EquipmentSlot.CHEST,
        rarity = EquipmentRarity.EPIC,
        price = 1_200L,
        requiredLevel = 24,
        modifiers = listOf(
            modifier(1_007L, DerivedStatType.MAX_HP, 50),
            modifier(1_007L, DerivedStatType.DEFENSE, 9),
            EquipmentStatModifier(1_007L, StatTarget.Base(StatType.VITALITY), ModifierType.FLAT, 5),
            EquipmentStatModifier(
                1_007L,
                StatTarget.Derived(DerivedStatType.MAX_HP),
                ModifierType.PERCENT_ADD,
                1_200,
            ),
        ),
        comparisons = comparisons,
        imageKey = imageKey,
        purchaseAvailability = purchaseAvailability,
    )

    private fun equipment(
        equipmentId: Long,
        nameKey: String,
        descriptionKey: String,
        type: EquipmentType,
        slot: EquipmentSlot,
        weaponType: WeaponType? = null,
        rarity: EquipmentRarity,
        price: Long,
        requiredLevel: Int = 1,
        modifiers: List<EquipmentStatModifier>,
        comparisons: List<EquipmentStatComparison> = emptyList(),
        imageKey: String? = null,
        purchaseAvailability: PurchaseAvailability = PurchaseAvailability.Available,
        action: ShopEquipmentAction = when (purchaseAvailability) {
            PurchaseAvailability.Available -> ShopEquipmentAction.Purchase(equipmentId)
            is PurchaseAvailability.Unavailable -> ShopEquipmentAction.PurchaseUnavailable(
                purchaseAvailability.reason,
            )
        },
    ) = ShopEquipmentUiModel(
        equipmentId = equipmentId,
        nameKey = nameKey,
        descriptionKey = descriptionKey,
        type = type,
        slot = slot,
        weaponType = weaponType,
        rarity = rarity,
        price = price,
        requiredLevel = requiredLevel,
        modifiers = modifiers,
        comparisons = comparisons,
        imageKey = imageKey,
        isForSale = true,
        isOwned = false,
        isEquipped = false,
        purchaseAvailability = purchaseAvailability,
        action = action,
    )

    private fun modifier(
        itemId: Long,
        stat: DerivedStatType,
        amount: Int,
    ) = EquipmentStatModifier(
        itemId = itemId,
        target = StatTarget.Derived(stat),
        type = ModifierType.FLAT,
        amount = amount,
    )

    private fun comparison(
        stat: DerivedStatType,
        current: Int,
        candidate: Int,
        difference: Int,
    ) = EquipmentStatComparison(
        target = StatTarget.Derived(stat),
        modifierType = ModifierType.FLAT,
        currentAmount = current,
        candidateAmount = candidate,
        difference = difference,
    )

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("Asia/Seoul")
        private val instant = Instant.parse("2026-08-04T12:00:00Z")

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }

    private companion object {
        const val CharacterCanvasSize = 64
        const val TopLeft = 20
        const val TopTop = 29
        const val TopRight = 44
        const val TopBottom = 45
        const val BottomLeft = 24
        const val BottomTop = 41
        const val BottomRight = 40
        const val BottomBottom = 54
        const val WaistTop = 41
        const val WaistBottom = 43
        const val AnkleTop = 53
        const val AnkleBottom = 54
        const val LeftAnkleLeft = 24
        const val LeftAnkleRight = 31
        const val RightAnkleLeft = 33
        const val RightAnkleRight = 40
        const val FaceProtectedLeft = 23
        const val FaceProtectedTop = 20
        const val FaceProtectedRight = 41
        const val FaceProtectedBottom = 28
        const val WeaponFaceProtectedLeft = 20
        const val WeaponFaceProtectedTop = 7
        const val WeaponFaceProtectedRight = 44
        const val WeaponFaceProtectedBottom = 28
    }

    private data class OutfitArtworkFixture(
        val item: ShopEquipmentUiModel,
        val imageKey: String,
        val displayName: String,
        val rarityName: String,
        val description: String,
    )

    private data class WeaponArtworkFixture(
        val item: ShopEquipmentUiModel,
        val imageKey: String,
        val displayName: String,
        val weaponTypeName: String,
        val rarityName: String,
        val priceText: String,
        val modifierText: String,
        val description: String,
    )

    private enum class OutfitRenderSurface {
        SHOP,
        INVENTORY,
        CHARACTER,
        BATTLE,
    }
}
