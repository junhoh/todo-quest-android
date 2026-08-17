package com.todoquest.feature.shop

import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentInventorySnapshot
import com.todoquest.domain.model.EquipmentPreviewProjection
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
import com.todoquest.domain.usecase.PurchaseEquipmentUseCase
import com.todoquest.domain.usecase.UnequipEquipmentUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeEquipmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeEquipmentRepository(storeSnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingAndLoadedStatesExposeCharacterPreviewAndAllSevenEquipmentSlots() = runTest(dispatcher) {
        val snapshot = repository.store.value
        val viewModel = viewModel()

        with(viewModel.uiState.value) {
            assertTrue(isLoading)
            assertEquals(CharacterLoadoutCatalog.defaultAppearance, characterAppearance)
            assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, characterEquippedItems)
            assertEquals(
                CharacterStatSummaryUiModel(
                    attack = CharacterStatValueUiModel(currentValue = 0, difference = 0),
                    maxHp = CharacterStatValueUiModel(currentValue = 0, difference = 0),
                    defense = CharacterStatValueUiModel(currentValue = 0, difference = 0),
                ),
                statSummary,
            )
            assertEquals(EquipmentSlot.entries, equipmentSlots.map { it.slot })
        }

        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(snapshot.appearance, characterAppearance)
            assertEquals(snapshot.renderedEquippedItems, characterEquippedItems)
            assertEquals(
                CharacterStatSummaryUiModel(
                    attack = CharacterStatValueUiModel(currentValue = 20, difference = 0),
                    maxHp = CharacterStatValueUiModel(currentValue = 110, difference = 0),
                    defense = CharacterStatValueUiModel(currentValue = 111, difference = 0),
                ),
                statSummary,
            )
            assertEquals(EquipmentSlot.entries, equipmentSlots.map { it.slot })
            assertEquals(
                ShopEquipmentSlotUiModel(
                    slot = EquipmentSlot.CHEST,
                    type = EquipmentType.CHEST,
                    equipmentId = 1L,
                    nameKey = "equipment_name_1",
                    rarity = EquipmentRarity.COMMON,
                    imageKey = "equipment_image_1",
                    isEquipped = true,
                    isSelected = false,
                ),
                equipmentSlots.single { it.slot == EquipmentSlot.CHEST },
            )
            assertEquals(
                ShopEquipmentSlotUiModel(
                    slot = EquipmentSlot.WEAPON,
                    type = EquipmentType.WEAPON,
                    equipmentId = null,
                    nameKey = null,
                    rarity = null,
                    imageKey = null,
                    isEquipped = false,
                    isSelected = false,
                ),
                equipmentSlots.single { it.slot == EquipmentSlot.WEAPON },
            )
        }
    }

    @Test
    fun weaponSubtypeMapsToCardDetailAndEquippedWeaponSlot() = runTest(dispatcher) {
        val spear = equipment(
            id = 17L,
            type = EquipmentType.WEAPON,
            slot = EquipmentSlot.WEAPON,
            amount = 4,
            price = 50L,
            weaponType = WeaponType.SPEAR,
        )
        repository.store.value = repository.store.value.copy(
            equipment = repository.store.value.equipment + spear,
            ownedEquipmentIds = repository.store.value.ownedEquipmentIds + spear.id,
            ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                (spear.id to owned(117L, spear)),
            equippedBySlot = repository.store.value.equippedBySlot +
                (EquipmentSlot.WEAPON to EquippedEquipment(
                    characterId = 1L,
                    slot = EquipmentSlot.WEAPON,
                    ownedEquipment = owned(117L, spear),
                )),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.WEAPON))
        runCurrent()
        val weapon = viewModel.uiState.value.items.single()
        assertEquals(WeaponType.SPEAR, weapon.weaponType)
        assertEquals("equipment_image_17", weapon.imageKey)
        assertEquals(
            WeaponType.SPEAR,
            viewModel.uiState.value.equipmentSlots
                .single { it.slot == EquipmentSlot.WEAPON }
                .weaponType,
        )

        viewModel.onEvent(ShopEvent.SelectEquipment(spear.id))
        runCurrent()
        assertEquals(spear.id, viewModel.uiState.value.selectedEquipmentId)
        assertNull(viewModel.uiState.value.selectedDetail)

        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(spear.id))
        runCurrent()
        assertEquals(WeaponType.SPEAR, viewModel.uiState.value.selectedDetail?.weaponType)
    }

    @Test
    fun selectionPreviewsAppearanceAndExactStatDeltasWithoutOpeningDetailOrChangingFilter() =
        runTest(dispatcher) {
            val snapshot = repository.store.value
            val preview = requireNotNull(snapshot.previewByEquipmentId[2L])
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.CHEST))
            viewModel.onEvent(ShopEvent.SelectEquipment(2L))
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(EquipmentType.CHEST, selectedCategory)
                assertEquals(listOf(1L, 2L), items.map { it.equipmentId })
                assertEquals(2L, selectedEquipmentId)
                assertNull(selectedDetail)
                assertEquals(preview.renderedEquippedItems, characterEquippedItems)
                assertEquals(
                    CharacterStatSummaryUiModel(
                        attack = CharacterStatValueUiModel(currentValue = 20, difference = 3),
                        maxHp = CharacterStatValueUiModel(currentValue = 110, difference = 20),
                        defense = CharacterStatValueUiModel(currentValue = 111, difference = 6),
                    ),
                    statSummary,
                )
                assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.CHEST }.isSelected)
                assertFalse(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
            }

            viewModel.onEvent(ShopEvent.SelectCategory(null))
            viewModel.onEvent(ShopEvent.SelectEquipment(3L))
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(3L, selectedEquipmentId)
                assertEquals(snapshot.renderedEquippedItems, characterEquippedItems)
                assertEquals(CharacterStatValueUiModel(20, 0), statSummary.attack)
                assertEquals(CharacterStatValueUiModel(110, 0), statSummary.maxHp)
                assertEquals(CharacterStatValueUiModel(111, 0), statSummary.defense)
                assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
            }
        }

    @Test
    fun detailOpenAndCloseAreIndependentFromPreviewSelectionAndCategoryClearsBoth() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.OpenEquipmentDetail(2L))
            runCurrent()
            assertEquals(2L, viewModel.uiState.value.selectedEquipmentId)
            assertEquals(2L, viewModel.uiState.value.selectedDetail?.equipmentId)

            viewModel.onEvent(ShopEvent.CloseEquipmentDetail)
            runCurrent()
            assertEquals(2L, viewModel.uiState.value.selectedEquipmentId)
            assertNull(viewModel.uiState.value.selectedDetail)

            viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.LEGS))
            runCurrent()
            with(viewModel.uiState.value) {
                assertNull(selectedEquipmentId)
                assertNull(selectedDetail)
                assertNull(purchaseConfirmation)
                assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, characterEquippedItems)
                assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
            }
        }

    @Test
    fun slotSelectionPrefersSelectedEquipmentThenManagedSlotThenCategorySlot() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.LEGS))
            viewModel.onEvent(ShopEvent.OpenSlotManagement(EquipmentSlot.CHEST))
            runCurrent()
            assertTrue(
                viewModel.uiState.value.equipmentSlots
                    .single { it.slot == EquipmentSlot.CHEST }
                    .isSelected,
            )

            viewModel.onEvent(ShopEvent.SelectEquipment(3L))
            runCurrent()
            with(viewModel.uiState.value.equipmentSlots) {
                assertTrue(single { it.slot == EquipmentSlot.LEGS }.isSelected)
                assertFalse(single { it.slot == EquipmentSlot.CHEST }.isSelected)
            }
        }

    @Test
    fun slotAndCategorySelectionShareOneSourceAndKeepChestAndLegsIndependent() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(2L))
        viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
        runCurrent()
        assertTrue(viewModel.uiState.value.purchaseConfirmation != null)

        viewModel.onEvent(ShopEvent.SelectSlot(EquipmentSlot.LEGS))
        runCurrent()
        with(viewModel.uiState.value) {
            assertEquals(EquipmentType.LEGS, selectedCategory)
            assertNull(selectedEquipmentId)
            assertNull(selectedDetail)
            assertNull(purchaseConfirmation)
            assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
            assertFalse(equipmentSlots.single { it.slot == EquipmentSlot.CHEST }.isSelected)
        }

        viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.CHEST))
        runCurrent()
        with(viewModel.uiState.value) {
            assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.CHEST }.isSelected)
            assertFalse(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
        }

        viewModel.onEvent(ShopEvent.SelectCategory(null))
        runCurrent()
        assertTrue(viewModel.uiState.value.equipmentSlots.none { it.isSelected })
    }

    @Test
    fun purchasePresentationChangesOnlyAfterRepositoryEmitsCommittedSnapshot() = runTest(dispatcher) {
        repository.purchaseResult = PurchaseEquipmentResult.Success(
            ownedEquipmentId = 102L,
            equipmentId = 2L,
            equipmentNameKey = "equipment_name_2",
            type = EquipmentType.CHEST,
            slot = EquipmentSlot.CHEST,
            remainingGold = 380L,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
        viewModel.onEvent(ShopEvent.ConfirmPurchase)
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(2L, selectedEquipmentId)
            assertNull(selectedDetail)
            assertEquals(500L, currentGold)
            assertFalse(items.single { it.equipmentId == 2L }.isOwned)
            assertTrue(items.single { it.equipmentId == 2L }.purchaseAvailability is PurchaseAvailability.Available)
            assertEquals(20, statSummary.attack.currentValue)
            assertEquals(3, statSummary.attack.difference)
            assertEquals(CharacterLoadoutCatalog.TOP_DEFAULT, characterEquippedItems.topId)
        }

        repository.store.value = repository.store.value.copy(
            currentGold = 380L,
            ownedEquipmentIds = setOf(1L, 2L, 3L),
            ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                (2L to owned(
                    102L,
                    repository.store.value.equipment.single { it.id == 2L },
                )),
        )
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(380L, currentGold)
            assertTrue(items.single { it.equipmentId == 2L }.isOwned)
            assertEquals(
                PurchaseAvailability.Unavailable(PurchaseUnavailableReason.AlreadyOwned),
                items.single { it.equipmentId == 2L }.purchaseAvailability,
            )
        }
        assertEquals(listOf(1L to 2L), repository.purchaseCalls)
    }

    @Test
    fun equipPresentationRemapsSlotsStatsItemsDetailAndLoadoutWhilePreservingOtherSlots() =
        runTest(dispatcher) {
            val candidateLegs = equipment(4L, EquipmentType.LEGS, EquipmentSlot.LEGS, 120, 140L)
            repository.store.value = repository.store.value.copy(
                equipment = repository.store.value.equipment + candidateLegs,
            )
            repository.purchaseResult = PurchaseEquipmentResult.Success(
                ownedEquipmentId = 102L,
                equipmentId = 2L,
                equipmentNameKey = "equipment_name_2",
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.CHEST,
                remainingGold = 380L,
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.OpenEquipmentDetail(2L))
            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
            viewModel.onEvent(ShopEvent.ConfirmPurchase)
            advanceUntilIdle()
            repository.store.value = repository.store.value.copy(
                currentGold = 380L,
                ownedEquipmentIds = setOf(1L, 2L, 3L),
                ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                    (2L to owned(
                        102L,
                        repository.store.value.equipment.single { it.id == 2L },
                    )),
            )
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.EquipPurchased(102L, EquipmentSlot.CHEST))
            advanceUntilIdle()
            assertEquals(1L, viewModel.uiState.value.equipmentSlots.equipmentIdAt(EquipmentSlot.CHEST))
            assertEquals(
                CharacterStatSummaryUiModel(
                    attack = CharacterStatValueUiModel(20, 3),
                    maxHp = CharacterStatValueUiModel(110, 20),
                    defense = CharacterStatValueUiModel(111, 6),
                ),
                viewModel.uiState.value.statSummary,
            )

            val candidateChest = repository.store.value.equipment.single { it.id == 2L }
            val equippedCandidateChest = EquippedEquipment(
                characterId = 1L,
                slot = EquipmentSlot.CHEST,
                ownedEquipment = owned(102L, candidateChest),
            )
            val committedStats = repository.store.value.derivedStats.copy(
                attack = 30,
                maxHp = 150,
                defense = 117,
            )
            val committedLoadout = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            )
            repository.store.value = repository.store.value.copy(
                equippedBySlot = repository.store.value.equippedBySlot +
                    (EquipmentSlot.CHEST to equippedCandidateChest),
                renderedEquippedItems = committedLoadout,
                derivedStats = committedStats,
                previewByEquipmentId = repository.store.value.previewByEquipmentId +
                    (2L to EquipmentPreviewProjection(committedLoadout, committedStats)),
            )
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(2L, equipmentSlots.equipmentIdAt(EquipmentSlot.CHEST))
                assertEquals(3L, equipmentSlots.equipmentIdAt(EquipmentSlot.LEGS))
                assertTrue(items.single { it.equipmentId == 2L }.isEquipped)
                assertFalse(items.single { it.equipmentId == 1L }.isEquipped)
                val detail = requireNotNull(selectedDetail)
                assertEquals(10, detail.comparisons.single().currentAmount)
                assertEquals(0, detail.comparisons.single().difference)
                assertEquals(
                    CharacterStatSummaryUiModel(
                        attack = CharacterStatValueUiModel(30, 0),
                        maxHp = CharacterStatValueUiModel(150, 0),
                        defense = CharacterStatValueUiModel(117, 0),
                    ),
                    statSummary,
                )
                assertEquals(CharacterLoadoutCatalog.TOP_DEFAULT, characterEquippedItems.topId)
            }

            repository.purchaseResult = PurchaseEquipmentResult.Success(
                ownedEquipmentId = 104L,
                equipmentId = 4L,
                equipmentNameKey = "equipment_name_4",
                type = EquipmentType.LEGS,
                slot = EquipmentSlot.LEGS,
                remainingGold = 240L,
            )
            repository.equipResult = EquipOwnedEquipmentResult.Success(
                ownedEquipmentId = 104L,
                equipmentId = 4L,
                slot = EquipmentSlot.LEGS,
            )
            viewModel.onEvent(ShopEvent.SelectSlot(EquipmentSlot.LEGS))
            viewModel.onEvent(ShopEvent.SelectEquipment(4L))
            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(4L))
            viewModel.onEvent(ShopEvent.ConfirmPurchase)
            advanceUntilIdle()
            repository.store.value = repository.store.value.copy(
                currentGold = 240L,
                ownedEquipmentIds = setOf(1L, 2L, 3L, 4L),
                ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                    (4L to owned(104L, candidateLegs)),
            )
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.EquipPurchased(104L, EquipmentSlot.LEGS))
            advanceUntilIdle()
            val equippedCandidateLegs = EquippedEquipment(
                characterId = 1L,
                slot = EquipmentSlot.LEGS,
                ownedEquipment = owned(104L, candidateLegs),
            )
            repository.store.value = repository.store.value.copy(
                equippedBySlot = repository.store.value.equippedBySlot +
                    (EquipmentSlot.LEGS to equippedCandidateLegs),
            )
            advanceUntilIdle()

            with(viewModel.uiState.value.equipmentSlots) {
                assertEquals(2L, equipmentIdAt(EquipmentSlot.CHEST))
                assertEquals(4L, equipmentIdAt(EquipmentSlot.LEGS))
            }
            assertEquals(
                listOf(
                    Triple(1L, 102L, EquipmentSlot.CHEST),
                    Triple(1L, 104L, EquipmentSlot.LEGS),
                ),
                repository.equipCalls,
            )
        }

    @Test
    fun categoryFilterAndDetailComparisonUseOnlyCurrentlyEquippedSameSlot() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(3, viewModel.uiState.value.items.size)

        viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.CHEST))
        runCurrent()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.items.map { it.equipmentId })

        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(2L))
        runCurrent()
        val detail = requireNotNull(viewModel.uiState.value.selectedDetail)
        assertEquals("equipment_name_2", detail.nameKey)
        assertEquals(EquipmentType.CHEST, detail.type)
        assertEquals(EquipmentSlot.CHEST, detail.slot)
        assertEquals(4, detail.comparisons.single().currentAmount)
        assertEquals(10, detail.comparisons.single().candidateAmount)
        assertEquals(6, detail.comparisons.single().difference)
        assertTrue(detail.purchaseAvailability is PurchaseAvailability.Available)

        viewModel.onEvent(ShopEvent.SelectCategory(EquipmentType.LEGS))
        runCurrent()
        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(3L))
        runCurrent()
        assertEquals(99, viewModel.uiState.value.selectedDetail!!.comparisons.single().currentAmount)
    }

    @Test
    fun itemActionUsesOneTypedPriorityForPurchaseUnavailableEquipAndUnequip() = runTest(dispatcher) {
        val ownedUnequipped = equipment(
            id = 5L,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            amount = 5,
            price = 10L,
        )
        val unavailable = equipment(
            id = 6L,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            amount = 6,
            price = 501L,
        )
        repository.store.value = repository.store.value.copy(
            equipment = repository.store.value.equipment + ownedUnequipped + unavailable,
            ownedEquipmentIds = repository.store.value.ownedEquipmentIds + ownedUnequipped.id,
            ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                (ownedUnequipped.id to owned(905L, ownedUnequipped)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items.associateBy(ShopEquipmentUiModel::equipmentId)
        assertEquals(
            ShopEquipmentAction.Purchase(equipmentId = 2L),
            items.getValue(2L).action,
        )
        assertEquals(ShopEquipmentActionLabelKey.PURCHASE, items.getValue(2L).action.labelKey)
        assertTrue(items.getValue(2L).action.isEnabled)

        val unavailableAction = items.getValue(6L).action
        assertEquals(
            ShopEquipmentAction.PurchaseUnavailable(
                PurchaseUnavailableReason.InsufficientGold(price = 501L, availableGold = 500L),
            ),
            unavailableAction,
        )
        assertEquals(ShopEquipmentActionLabelKey.PURCHASE_UNAVAILABLE, unavailableAction.labelKey)
        assertFalse(unavailableAction.isEnabled)

        assertEquals(
            ShopEquipmentAction.Equip(
                ownedEquipmentId = 905L,
                slot = EquipmentSlot.HELMET,
            ),
            items.getValue(5L).action,
        )
        assertEquals(ShopEquipmentActionLabelKey.EQUIP, items.getValue(5L).action.labelKey)
        assertTrue(items.getValue(5L).action.isEnabled)

        assertEquals(
            ShopEquipmentAction.Unequip(
                equipmentId = 1L,
                slot = EquipmentSlot.CHEST,
            ),
            items.getValue(1L).action,
        )
        assertEquals(ShopEquipmentActionLabelKey.UNEQUIP, items.getValue(1L).action.labelKey)
        assertTrue(items.getValue(1L).action.isEnabled)

        viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(items.getValue(2L).action))
        runCurrent()
        assertEquals(2L, viewModel.uiState.value.purchaseConfirmation?.equipmentId)
        viewModel.onEvent(ShopEvent.CancelPurchaseConfirmation)
        viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(unavailableAction))
        runCurrent()
        assertNull(viewModel.uiState.value.purchaseConfirmation)
        assertTrue(repository.purchaseCalls.isEmpty())

        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(5L))
        runCurrent()
        val cardAction = viewModel.uiState.value.items.single { it.equipmentId == 5L }.action
        assertEquals(cardAction, requireNotNull(viewModel.uiState.value.selectedDetail).action)
    }

    @Test
    fun equipItemActionPassesOwnedRowIdInsteadOfEquipmentIdAndBlocksDuplicates() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val ownedUnequipped = equipment(
                id = 5L,
                type = EquipmentType.HELMET,
                slot = EquipmentSlot.HELMET,
                amount = 5,
                price = 10L,
            )
            repository.store.value = repository.store.value.copy(
                equipment = repository.store.value.equipment + ownedUnequipped,
                ownedEquipmentIds = repository.store.value.ownedEquipmentIds + ownedUnequipped.id,
                ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                    (ownedUnequipped.id to owned(905L, ownedUnequipped)),
            )
            repository.equipGate = gate
            repository.equipResult = EquipOwnedEquipmentResult.Success(
                ownedEquipmentId = 905L,
                equipmentId = 5L,
                slot = EquipmentSlot.HELMET,
            )
            val viewModel = viewModel()
            advanceUntilIdle()
            val action = viewModel.uiState.value.items.single { it.equipmentId == 5L }.action

            viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(action))
            viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(action))
            runCurrent()

            assertEquals(
                ShopEquipState.Processing(
                    ownedEquipmentId = 905L,
                    targetSlot = EquipmentSlot.HELMET,
                ),
                viewModel.uiState.value.equipState,
            )
            assertEquals(
                listOf(Triple(1L, 905L, EquipmentSlot.HELMET)),
                repository.equipCalls,
            )

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                ShopEquipState.Success(
                    ownedEquipmentId = 905L,
                    equipmentId = 5L,
                    slot = EquipmentSlot.HELMET,
                ),
                viewModel.uiState.value.equipState,
            )
        }

    @Test
    fun successfulSelectedItemUnequipClearsSelectionDetailConfirmationAndTemporaryPreview() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
            runCurrent()
            assertEquals(2L, viewModel.uiState.value.purchaseConfirmation?.equipmentId)

            val candidate = repository.store.value.equipment.single { it.id == 2L }
            val candidateOwned = owned(102L, candidate)
            val equippedLoadout = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
            )
            val equippedStats = repository.store.value.derivedStats.copy(
                attack = 30,
                maxHp = 150,
                defense = 117,
            )
            repository.store.value = repository.store.value.copy(
                ownedEquipmentIds = repository.store.value.ownedEquipmentIds + candidate.id,
                ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                    (candidate.id to candidateOwned),
                equippedBySlot = repository.store.value.equippedBySlot +
                    (EquipmentSlot.CHEST to EquippedEquipment(
                        characterId = 1L,
                        slot = EquipmentSlot.CHEST,
                        ownedEquipment = candidateOwned,
                    )),
                renderedEquippedItems = equippedLoadout,
                derivedStats = equippedStats,
                previewByEquipmentId = repository.store.value.previewByEquipmentId +
                    (candidate.id to EquipmentPreviewProjection(
                        renderedEquippedItems = equippedLoadout,
                        derivedStats = equippedStats,
                    )),
            )
            advanceUntilIdle()
            viewModel.onEvent(ShopEvent.OpenEquipmentDetail(candidate.id))
            runCurrent()

            val action = viewModel.uiState.value.items
                .single { it.equipmentId == candidate.id }
                .action
            assertEquals(
                ShopEquipmentAction.Unequip(candidate.id, EquipmentSlot.CHEST),
                action,
            )
            repository.unequipGate = gate
            repository.unequipResult = UnequipEquipmentResult.Success(
                ownedEquipmentId = candidateOwned.id,
                equipmentId = candidate.id,
                slot = EquipmentSlot.CHEST,
            )
            viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(action))
            runCurrent()

            val emptyStats = equippedStats.copy(attack = 20, maxHp = 110, defense = 111)
            repository.store.value = repository.store.value.copy(
                equippedBySlot = repository.store.value.equippedBySlot - EquipmentSlot.CHEST,
                renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                derivedStats = emptyStats,
            )
            runCurrent()
            assertEquals(
                CharacterLoadoutCatalog.TOP_ADVENTURE,
                viewModel.uiState.value.characterEquippedItems.topId,
            )
            assertEquals(10, viewModel.uiState.value.statSummary.attack.difference)

            gate.complete(Unit)
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertNull(selectedEquipmentId)
                assertNull(selectedDetail)
                assertNull(purchaseConfirmation)
                assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, characterEquippedItems)
                assertEquals(CharacterStatValueUiModel(20, 0), statSummary.attack)
                assertEquals(CharacterStatValueUiModel(110, 0), statSummary.maxHp)
                assertEquals(CharacterStatValueUiModel(111, 0), statSummary.defense)
                assertEquals(
                    ShopUnequipState.Success(
                        equipmentId = candidate.id,
                        slot = EquipmentSlot.CHEST,
                        changed = true,
                    ),
                    unequipState,
                )
            }
        }

    @Test
    fun unequippingAnotherItemKeepsCurrentSelectionDetailAndPreview() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(ShopEvent.OpenEquipmentDetail(2L))
        runCurrent()
        val selectedPreview = viewModel.uiState.value.characterEquippedItems
        val selectedDifference = viewModel.uiState.value.statSummary

        val otherAction = viewModel.uiState.value.items.single { it.equipmentId == 3L }.action
        repository.unequipResult = UnequipEquipmentResult.Success(
            ownedEquipmentId = 103L,
            equipmentId = 3L,
            slot = EquipmentSlot.LEGS,
        )
        viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(otherAction))
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(2L, selectedEquipmentId)
            assertEquals(2L, selectedDetail?.equipmentId)
            assertEquals(selectedPreview, characterEquippedItems)
            assertEquals(selectedDifference, statSummary)
            assertEquals(
                ShopUnequipState.Success(
                    equipmentId = 3L,
                    slot = EquipmentSlot.LEGS,
                    changed = true,
                ),
                unequipState,
            )
        }
    }

    @Test
    fun failedSelectedItemUnequipKeepsSelectionAndRetryTargetWhileBlockingDuplicates() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.unequipGate = gate
            repository.unequipFailure = IllegalStateException("raw unequip database detail")
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onEvent(ShopEvent.OpenEquipmentDetail(1L))
            runCurrent()
            val action = viewModel.uiState.value.items.single { it.equipmentId == 1L }.action

            viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(action))
            viewModel.onEvent(ShopEvent.ExecuteEquipmentAction(action))
            runCurrent()

            assertEquals(listOf(1L to EquipmentSlot.CHEST), repository.unequipCalls)
            assertEquals(
                ShopUnequipState.Processing(
                    equipmentId = 1L,
                    slot = EquipmentSlot.CHEST,
                ),
                viewModel.uiState.value.unequipState,
            )

            gate.complete(Unit)
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(1L, selectedEquipmentId)
                assertEquals(1L, selectedDetail?.equipmentId)
                assertEquals(
                    ShopUnequipState.Failed(
                        equipmentId = 1L,
                        slot = EquipmentSlot.CHEST,
                    ),
                    unequipState,
                )
                assertEquals(
                    ShopRetryState.Unequip(
                        equipmentId = 1L,
                        slot = EquipmentSlot.CHEST,
                    ),
                    retryState,
                )
            }
        }

    @Test
    fun confirmationSnapshotsExpectedGoldAndProcessingIgnoresDuplicateConfirmEvents() =
        runTest(dispatcher) {
            val purchaseGate = CompletableDeferred<Unit>()
            repository.purchaseGate = purchaseGate
            repository.purchaseResult = PurchaseEquipmentResult.Success(
                ownedEquipmentId = 102L,
                equipmentId = 2L,
                equipmentNameKey = "equipment_name_2",
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.CHEST,
                remainingGold = 380L,
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
            runCurrent()
            assertEquals(
                PurchaseConfirmationUiState(
                    equipmentId = 2L,
                    equipmentNameKey = "equipment_name_2",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    price = 120L,
                    currentGold = 500L,
                    expectedRemainingGold = 380L,
                ),
                viewModel.uiState.value.purchaseConfirmation,
            )

            viewModel.onEvent(ShopEvent.ConfirmPurchase)
            viewModel.onEvent(ShopEvent.ConfirmPurchase)
            runCurrent()
            assertEquals(PurchaseState.Processing(equipmentId = 2L), viewModel.uiState.value.purchaseState)
            assertEquals(listOf(1L to 2L), repository.purchaseCalls)

            purchaseGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                PurchaseState.Success(
                    ownedEquipmentId = 102L,
                    equipmentId = 2L,
                    equipmentNameKey = "equipment_name_2",
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.CHEST,
                    currentGold = 380L,
                ),
                viewModel.uiState.value.purchaseState,
            )
            assertNull(viewModel.uiState.value.purchaseConfirmation)
        }

    @Test
    fun directPurchaseRequestUsesLatestSnapshotAndSelectsUnavailableItemWithoutOpeningDetail() =
        runTest(dispatcher) {
            val expensive = equipment(
                id = 6L,
                type = EquipmentType.GLOVES,
                slot = EquipmentSlot.GLOVES,
                amount = 6,
                price = 501L,
            )
            repository.store.value = repository.store.value.copy(
                equipment = repository.store.value.equipment + expensive,
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(expensive.id))
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(expensive.id, selectedEquipmentId)
                assertNull(selectedDetail)
                assertNull(purchaseConfirmation)
                assertEquals(
                    PurchaseState.Unavailable(
                        equipmentId = expensive.id,
                        reason = PurchaseUnavailableReason.InsufficientGold(
                            price = expensive.price,
                            availableGold = 500L,
                        ),
                    ),
                    purchaseState,
                )
                assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.GLOVES }.isSelected)
            }
            assertTrue(repository.purchaseCalls.isEmpty())
        }

    @Test
    fun purchaseSuccessIsExplicitlyConsumedAndNewViewModelDoesNotReplayCommand() = runTest(dispatcher) {
        repository.purchaseResult = PurchaseEquipmentResult.Success(
            ownedEquipmentId = 102L,
            equipmentId = 2L,
            equipmentNameKey = "equipment_name_2",
            type = EquipmentType.CHEST,
            slot = EquipmentSlot.CHEST,
            remainingGold = 380L,
        )
        val first = viewModel()
        advanceUntilIdle()

        first.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
        runCurrent()
        first.onEvent(ShopEvent.ConfirmPurchase)
        advanceUntilIdle()
        assertTrue(first.uiState.value.purchaseState is PurchaseState.Success)
        assertEquals(1, repository.purchaseCalls.size)

        first.onEvent(ShopEvent.SelectCategory(EquipmentType.CHEST))
        runCurrent()
        first.onEvent(ShopEvent.ConsumePurchaseSuccess)
        first.onEvent(ShopEvent.ConsumePurchaseSuccess)
        runCurrent()
        assertEquals(PurchaseState.Idle, first.uiState.value.purchaseState)
        assertEquals(EquipmentType.CHEST, first.uiState.value.selectedCategory)
        assertNull(first.uiState.value.selectedDetail)

        val recreated = viewModel()
        advanceUntilIdle()
        assertEquals(PurchaseState.Idle, recreated.uiState.value.purchaseState)
        assertEquals(1, repository.purchaseCalls.size)
    }

    @Test
    fun commandFailureExposesSemanticRetryAndPurchasedEquipUsesExplicitEventOnly() =
        runTest(dispatcher) {
            repository.purchaseResult = PurchaseEquipmentResult.Success(
                ownedEquipmentId = 102L,
                equipmentId = 2L,
                equipmentNameKey = "equipment_name_2",
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.CHEST,
                remainingGold = 380L,
            )
            repository.purchaseFailure = IllegalStateException("raw purchase database detail")
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.RequestPurchaseConfirmation(2L))
            runCurrent()
            viewModel.onEvent(ShopEvent.ConfirmPurchase)
            advanceUntilIdle()

            assertEquals(PurchaseState.Failed(equipmentId = 2L), viewModel.uiState.value.purchaseState)
            assertEquals(ShopRetryState.Purchase(equipmentId = 2L), viewModel.uiState.value.retryState)

            repository.purchaseFailure = null
            viewModel.onEvent(ShopEvent.Retry)
            advanceUntilIdle()
            assertEquals(2, repository.purchaseCalls.size)
            assertTrue(viewModel.uiState.value.purchaseState is PurchaseState.Success)
            assertEquals(0, repository.equipCalls.size)

            repository.equipResult = EquipOwnedEquipmentResult.SlotMismatch(
                ownedEquipmentId = 102L,
                type = EquipmentType.CHEST,
                equipmentSlot = EquipmentSlot.CHEST,
                targetSlot = EquipmentSlot.LEGS,
            )
            viewModel.onEvent(
                ShopEvent.EquipPurchased(
                    ownedEquipmentId = 102L,
                    targetSlot = EquipmentSlot.CHEST,
                ),
            )
            advanceUntilIdle()

            assertEquals(PurchaseState.Idle, viewModel.uiState.value.purchaseState)
            assertEquals(listOf(Triple(1L, 102L, EquipmentSlot.CHEST)), repository.equipCalls)
            assertEquals(
                ShopEquipState.Failed(
                    ownedEquipmentId = 102L,
                    targetSlot = EquipmentSlot.CHEST,
                    reason = EquipFailure.SlotMismatch(
                        type = EquipmentType.CHEST,
                        equipmentSlot = EquipmentSlot.CHEST,
                        targetSlot = EquipmentSlot.LEGS,
                    ),
                ),
                viewModel.uiState.value.equipState,
            )
        }

    @Test
    fun slotManagementBrowseClosesPopupAndUsesTheExistingSlotCategorySelection() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.OpenSlotManagement(EquipmentSlot.LEGS))
            runCurrent()
            assertEquals(EquipmentSlot.LEGS, viewModel.uiState.value.managedSlot)
            assertNull(viewModel.uiState.value.selectedCategory)

            viewModel.onEvent(ShopEvent.BrowseManagedSlot)
            runCurrent()

            with(viewModel.uiState.value) {
                assertNull(managedSlot)
                assertEquals(EquipmentType.LEGS, selectedCategory)
                assertEquals(listOf(3L), items.map { it.equipmentId })
                assertTrue(equipmentSlots.single { it.slot == EquipmentSlot.LEGS }.isSelected)
            }
            assertTrue(repository.unequipCalls.isEmpty())
        }

    @Test
    fun managedUnequipRequiresAnEquippedSnapshotSlotAndMapsAlreadyEmptyAsUnchangedSuccess() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.unequipGate = gate
            repository.unequipResult = UnequipEquipmentResult.AlreadyEmpty(EquipmentSlot.CHEST)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.OpenSlotManagement(EquipmentSlot.WEAPON))
            viewModel.onEvent(ShopEvent.UnequipManagedSlot)
            runCurrent()
            assertTrue(repository.unequipCalls.isEmpty())
            assertEquals(ShopUnequipState.Idle, viewModel.uiState.value.unequipState)

            viewModel.onEvent(ShopEvent.OpenSlotManagement(EquipmentSlot.CHEST))
            viewModel.onEvent(ShopEvent.UnequipManagedSlot)
            viewModel.onEvent(ShopEvent.UnequipManagedSlot)
            runCurrent()

            assertEquals(
                ShopUnequipState.Processing(
                    equipmentId = 1L,
                    slot = EquipmentSlot.CHEST,
                ),
                viewModel.uiState.value.unequipState,
            )
            assertEquals(listOf(1L to EquipmentSlot.CHEST), repository.unequipCalls)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                ShopUnequipState.Success(
                    equipmentId = 1L,
                    slot = EquipmentSlot.CHEST,
                    changed = false,
                ),
                viewModel.uiState.value.unequipState,
            )
            viewModel.onEvent(ShopEvent.ConsumeUnequipResult)
            runCurrent()
            assertEquals(ShopUnequipState.Idle, viewModel.uiState.value.unequipState)
        }

    @Test
    fun managedUnequipFailureRetriesTheSameSlotAndSuccessDoesNotMutateSnapshotPresentation() =
        runTest(dispatcher) {
            repository.unequipFailure = IllegalStateException("raw unequip database detail")
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(ShopEvent.OpenSlotManagement(EquipmentSlot.LEGS))
            viewModel.onEvent(ShopEvent.UnequipManagedSlot)
            advanceUntilIdle()

            assertEquals(
                ShopUnequipState.Failed(
                    equipmentId = 3L,
                    slot = EquipmentSlot.LEGS,
                ),
                viewModel.uiState.value.unequipState,
            )
            assertEquals(
                ShopRetryState.Unequip(
                    equipmentId = 3L,
                    slot = EquipmentSlot.LEGS,
                ),
                viewModel.uiState.value.retryState,
            )

            repository.unequipFailure = null
            repository.unequipResult = UnequipEquipmentResult.Success(
                ownedEquipmentId = 103L,
                equipmentId = 3L,
                slot = EquipmentSlot.LEGS,
            )
            viewModel.onEvent(ShopEvent.Retry)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    1L to EquipmentSlot.LEGS,
                    1L to EquipmentSlot.LEGS,
                ),
                repository.unequipCalls,
            )
            assertEquals(
                ShopUnequipState.Success(
                    equipmentId = 3L,
                    slot = EquipmentSlot.LEGS,
                    changed = true,
                ),
                viewModel.uiState.value.unequipState,
            )
            assertEquals(3L, viewModel.uiState.value.equipmentSlots.equipmentIdAt(EquipmentSlot.LEGS))

            viewModel.onEvent(ShopEvent.ConsumeUnequipResult)
            repository.store.value = repository.store.value.copy(
                equippedBySlot = repository.store.value.equippedBySlot - EquipmentSlot.LEGS,
            )
            advanceUntilIdle()
            assertEquals(ShopUnequipState.Idle, viewModel.uiState.value.unequipState)
            assertNull(viewModel.uiState.value.equipmentSlots.equipmentIdAt(EquipmentSlot.LEGS))

            val recreated = viewModel()
            advanceUntilIdle()
            assertEquals(ShopUnequipState.Idle, recreated.uiState.value.unequipState)
            assertEquals(2, repository.unequipCalls.size)
        }

    @Test
    fun requiredLevelFlagIsIndependentFromPurchaseAvailabilityPriority() = runTest(dispatcher) {
        val lockedOwned = equipment(
            id = 5L,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            amount = 5,
            price = 10L,
            requiredLevel = 6,
        )
        val expensiveAvailableLevel = equipment(
            id = 6L,
            type = EquipmentType.GLOVES,
            slot = EquipmentSlot.GLOVES,
            amount = 6,
            price = 501L,
            requiredLevel = 5,
        )
        repository.store.value = repository.store.value.copy(
            equipment = repository.store.value.equipment + lockedOwned + expensiveAvailableLevel,
            ownedEquipmentIds = repository.store.value.ownedEquipmentIds + lockedOwned.id,
            ownedEquipmentByEquipmentId = repository.store.value.ownedEquipmentByEquipmentId +
                (lockedOwned.id to owned(105L, lockedOwned)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items.associateBy(ShopEquipmentUiModel::equipmentId)
        with(items.getValue(5L)) {
            assertFalse(isRequiredLevelMet)
            assertEquals(
                PurchaseAvailability.Unavailable(PurchaseUnavailableReason.AlreadyOwned),
                purchaseAvailability,
            )
        }
        with(items.getValue(6L)) {
            assertTrue(isRequiredLevelMet)
            assertEquals(
                PurchaseAvailability.Unavailable(
                    PurchaseUnavailableReason.InsufficientGold(price = 501L, availableGold = 500L),
                ),
                purchaseAvailability,
            )
        }
    }

    @Test
    fun loadFailureIsIsolatedAndRetryResubscribesWithoutExposingRawException() = runTest(dispatcher) {
        repository.storeFailure = IllegalStateException("raw load database detail")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ShopError.LoadFailed, viewModel.uiState.value.error)
        assertEquals(ShopRetryState.Load, viewModel.uiState.value.retryState)
        assertEquals(1, repository.storeObserveCalls)

        repository.storeFailure = null
        viewModel.onEvent(ShopEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.retryState)
        assertEquals(2, repository.storeObserveCalls)
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    private fun viewModel() = ShopViewModel(
        repository = repository,
        purchaseEquipment = PurchaseEquipmentUseCase(repository),
        equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
        unequipEquipment = UnequipEquipmentUseCase(repository),
        characterId = 1L,
        dispatcher = dispatcher,
    )

    private class FakeEquipmentRepository(initialStore: EquipmentStoreSnapshot) : EquipmentRepository {
        val store = MutableStateFlow(initialStore)
        var storeFailure: Throwable? = null
        var storeObserveCalls: Int = 0
        var purchaseFailure: Throwable? = null
        var purchaseGate: CompletableDeferred<Unit>? = null
        var purchaseResult: PurchaseEquipmentResult = PurchaseEquipmentResult.Unavailable(
            PurchaseAvailabilityFixtures.notForSale,
        )
        var equipResult: EquipOwnedEquipmentResult = EquipOwnedEquipmentResult.Success(
            ownedEquipmentId = 102L,
            equipmentId = 2L,
            slot = EquipmentSlot.CHEST,
        )
        var equipGate: CompletableDeferred<Unit>? = null
        var unequipFailure: Throwable? = null
        var unequipGate: CompletableDeferred<Unit>? = null
        var unequipResult: UnequipEquipmentResult = UnequipEquipmentResult.AlreadyEmpty(
            EquipmentSlot.CHEST,
        )
        val purchaseCalls = mutableListOf<Pair<Long, Long>>()
        val equipCalls = mutableListOf<Triple<Long, Long, EquipmentSlot>>()
        val unequipCalls = mutableListOf<Pair<Long, EquipmentSlot>>()

        override fun observeStore(characterId: Long): Flow<EquipmentStoreSnapshot> {
            storeObserveCalls += 1
            val failure = storeFailure
            return if (failure == null) store else flow { throw failure }
        }

        override fun observeInventory(characterId: Long): Flow<EquipmentInventorySnapshot> =
            flow { error("not used") }

        override suspend fun purchaseEquipment(
            characterId: Long,
            equipmentId: Long,
        ): PurchaseEquipmentResult {
            purchaseCalls += characterId to equipmentId
            purchaseGate?.await()
            purchaseFailure?.let { throw it }
            return purchaseResult
        }

        override suspend fun equipOwnedEquipment(
            characterId: Long,
            ownedEquipmentId: Long,
            targetSlot: EquipmentSlot,
        ): EquipOwnedEquipmentResult {
            equipCalls += Triple(characterId, ownedEquipmentId, targetSlot)
            equipGate?.await()
            return equipResult
        }

        override suspend fun unequipEquipment(
            characterId: Long,
            targetSlot: EquipmentSlot,
        ): UnequipEquipmentResult {
            unequipCalls += characterId to targetSlot
            unequipGate?.await()
            unequipFailure?.let { throw it }
            return unequipResult
        }
    }

    private object PurchaseAvailabilityFixtures {
        val notForSale = com.todoquest.domain.model.PurchaseEligibility.NotForSale(2L)
    }

    companion object {
        private fun storeSnapshot(): EquipmentStoreSnapshot {
            val currentChest = equipment(1L, EquipmentType.CHEST, EquipmentSlot.CHEST, 4, 80L)
            val candidateChest = equipment(2L, EquipmentType.CHEST, EquipmentSlot.CHEST, 10, 120L)
            val currentLegs = equipment(3L, EquipmentType.LEGS, EquipmentSlot.LEGS, 99, 90L)
            val ownedChest = owned(101L, currentChest)
            val ownedLegs = owned(103L, currentLegs)
            return EquipmentStoreSnapshot(
                characterId = 1L,
                currentGold = 500L,
                characterLevel = 5,
                equipment = listOf(currentChest, candidateChest, currentLegs),
                ownedEquipmentIds = setOf(1L, 3L),
                ownedEquipmentByEquipmentId = mapOf(
                    currentChest.id to ownedChest,
                    currentLegs.id to ownedLegs,
                ),
                equippedBySlot = mapOf(
                    EquipmentSlot.CHEST to EquippedEquipment(1L, EquipmentSlot.CHEST, ownedChest),
                    EquipmentSlot.LEGS to EquippedEquipment(1L, EquipmentSlot.LEGS, ownedLegs),
                ),
                appearance = CharacterLoadoutCatalog.defaultAppearance,
                renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                derivedStats = DerivedStats(
                    maxHp = 110,
                    attack = 20,
                    defense = 111,
                    criticalChanceBp = 750,
                    criticalDamageBp = 15_250,
                    statusResistanceBp = 375,
                    hpRecovery = 7,
                    goldGainBonusBp = 0,
                ),
                previewByEquipmentId = mapOf(
                    2L to EquipmentPreviewProjection(
                        renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
                        ),
                        derivedStats = DerivedStats(
                            maxHp = 130,
                            attack = 23,
                            defense = 117,
                            criticalChanceBp = 750,
                            criticalDamageBp = 15_250,
                            statusResistanceBp = 375,
                            hpRecovery = 7,
                            goldGainBonusBp = 0,
                        ),
                    ),
                ),
            )
        }

        private fun equipment(
            id: Long,
            type: EquipmentType,
            slot: EquipmentSlot,
            amount: Int,
            price: Long,
            requiredLevel: Int = 1,
            weaponType: WeaponType? = null,
        ) = Equipment(
            id = id,
            nameKey = "equipment_name_$id",
            descriptionKey = "equipment_description_$id",
            type = type,
            slot = slot,
            rarity = EquipmentRarity.COMMON,
            price = price,
            requiredLevel = requiredLevel,
            modifiers = listOf(
                EquipmentStatModifier(
                    itemId = id,
                    target = StatTarget.Derived(DerivedStatType.DEFENSE),
                    type = ModifierType.FLAT,
                    amount = amount,
                ),
            ),
            imageKey = "equipment_image_$id",
            isForSale = true,
            weaponType = weaponType,
        )

        private fun owned(id: Long, equipment: Equipment) = OwnedEquipment(
            id = id,
            characterId = 1L,
            equipment = equipment,
            acquiredAtEpochMillis = id,
        )

        private fun List<ShopEquipmentSlotUiModel>.equipmentIdAt(slot: EquipmentSlot): Long? =
            single { it.slot == slot }.equipmentId
    }
}
