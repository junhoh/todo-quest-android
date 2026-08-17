package com.todoquest.feature.shop

import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentInventorySnapshot
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
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
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
class InventoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeEquipmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeEquipmentRepository(inventorySnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun inventoryMapsOwnedItemsEquippedSlotsAndSameSlotComparisons() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(101L, 102L, 103L), state.items.map { it.ownedEquipmentId })
        assertEquals(101L, state.equippedBySlot[EquipmentSlot.CHEST]?.ownedEquipmentId)
        assertEquals(103L, state.equippedBySlot[EquipmentSlot.LEGS]?.ownedEquipmentId)
        val chestCandidate = state.items.single { it.ownedEquipmentId == 102L }
        assertFalse(chestCandidate.isEquipped)
        assertEquals(4, chestCandidate.comparisons.single().currentAmount)
        assertEquals(10, chestCandidate.comparisons.single().candidateAmount)
        val legs = state.items.single { it.slot == EquipmentSlot.LEGS }
        assertEquals(99, legs.comparisons.single().currentAmount)
    }

    @Test
    fun weaponSubtypeAndArtworkMapToOwnedItemsWithinOneWeaponSlot() = runTest(dispatcher) {
        val spear = equipment(
            id = 17L,
            type = EquipmentType.WEAPON,
            slot = EquipmentSlot.WEAPON,
            amount = 4,
            weaponType = WeaponType.SPEAR,
        )
        val mace = equipment(
            id = 18L,
            type = EquipmentType.WEAPON,
            slot = EquipmentSlot.WEAPON,
            amount = 12,
            weaponType = WeaponType.BLUNT,
        )
        val ownedSpear = owned(117L, spear)
        val ownedMace = owned(118L, mace)
        repository.inventory.value = EquipmentInventorySnapshot(
            characterId = 1L,
            ownedEquipment = listOf(ownedSpear, ownedMace),
            equippedBySlot = mapOf(
                EquipmentSlot.WEAPON to EquippedEquipment(
                    characterId = 1L,
                    slot = EquipmentSlot.WEAPON,
                    ownedEquipment = ownedSpear,
                ),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items.associateBy { it.equipmentId }
        assertEquals(WeaponType.SPEAR, items.getValue(17L).weaponType)
        assertEquals("equipment_image_17", items.getValue(17L).imageKey)
        assertEquals(WeaponType.BLUNT, items.getValue(18L).weaponType)
        assertEquals(117L, viewModel.uiState.value.equippedBySlot[EquipmentSlot.WEAPON]?.ownedEquipmentId)
    }

    @Test
    fun selectedEquipImmediatelyMarksProcessingAndIgnoresDuplicateEvents() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.equipGate = gate
        repository.equipResult = EquipOwnedEquipmentResult.Success(
            ownedEquipmentId = 102L,
            equipmentId = 2L,
            slot = EquipmentSlot.CHEST,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(InventoryEvent.SelectOwnedEquipment(102L))
        runCurrent()
        assertEquals(102L, viewModel.uiState.value.selectedOwnedEquipmentId)
        viewModel.onEvent(InventoryEvent.EquipSelected)
        viewModel.onEvent(InventoryEvent.EquipSelected)
        runCurrent()
        assertEquals(
            InventoryProcessingState.Equipping(102L, EquipmentSlot.CHEST),
            viewModel.uiState.value.processingState,
        )
        assertEquals(102L, viewModel.uiState.value.processingOwnedEquipmentId)
        assertEquals(listOf(Triple(1L, 102L, EquipmentSlot.CHEST)), repository.equipCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.processingOwnedEquipmentId)
        assertEquals(InventoryProcessingState.Idle, viewModel.uiState.value.processingState)
        assertEquals(
            InventoryEquipResult.Success(
                ownedEquipmentId = 102L,
                equipmentId = 2L,
                slot = EquipmentSlot.CHEST,
            ),
            viewModel.uiState.value.equipResult,
        )

        viewModel.onEvent(InventoryEvent.ConsumeEquipResult)
        runCurrent()
        assertNull(viewModel.uiState.value.equipResult)
    }

    @Test
    fun unequipUsesSlotCommandKeyBlocksConcurrentCommandsAndWaitsForFlowPresentation() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.unequipGate = gate
            repository.unequipResult = UnequipEquipmentResult.Success(
                ownedEquipmentId = 101L,
                equipmentId = 1L,
                slot = EquipmentSlot.CHEST,
            )
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onEvent(InventoryEvent.SelectOwnedEquipment(102L))

            viewModel.onEvent(InventoryEvent.UnequipSlot(EquipmentSlot.CHEST))
            viewModel.onEvent(InventoryEvent.UnequipSlot(EquipmentSlot.CHEST))
            viewModel.onEvent(InventoryEvent.EquipSelected)
            runCurrent()

            assertEquals(
                InventoryProcessingState.Unequipping(EquipmentSlot.CHEST),
                viewModel.uiState.value.processingState,
            )
            assertEquals(listOf(1L to EquipmentSlot.CHEST), repository.unequipCalls)
            assertTrue(repository.equipCalls.isEmpty())

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(InventoryProcessingState.Idle, viewModel.uiState.value.processingState)
            assertEquals(
                InventoryUnequipResult.Success(EquipmentSlot.CHEST, changed = true),
                viewModel.uiState.value.unequipResult,
            )
            assertTrue(viewModel.uiState.value.items.single { it.ownedEquipmentId == 101L }.isEquipped)

            repository.inventory.value = repository.inventory.value.copy(
                equippedBySlot = repository.inventory.value.equippedBySlot - EquipmentSlot.CHEST,
            )
            advanceUntilIdle()

            val ownedAfterUnequip = viewModel.uiState.value.items.single { it.ownedEquipmentId == 101L }
            assertFalse(ownedAfterUnequip.isEquipped)
            assertTrue(viewModel.uiState.value.items.any { it.ownedEquipmentId == 101L })

            viewModel.onEvent(InventoryEvent.ConsumeUnequipResult)
            repository.inventory.value = repository.inventory.value.copy()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.unequipResult)

            val recreated = viewModel()
            advanceUntilIdle()
            assertNull(recreated.uiState.value.unequipResult)
            assertEquals(1, repository.unequipCalls.size)
        }

    @Test
    fun emptySlotIsIgnoredAndUnequipFailureRetriesSameSlotWithAlreadyEmptySuccess() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEvent(InventoryEvent.UnequipSlot(EquipmentSlot.WEAPON))
            runCurrent()
            assertTrue(repository.unequipCalls.isEmpty())
            assertEquals(InventoryProcessingState.Idle, viewModel.uiState.value.processingState)

            repository.unequipFailure = IllegalStateException("raw unequip database detail")
            viewModel.onEvent(InventoryEvent.UnequipSlot(EquipmentSlot.LEGS))
            advanceUntilIdle()

            assertEquals(
                InventoryUnequipResult.Failed(EquipmentSlot.LEGS),
                viewModel.uiState.value.unequipResult,
            )
            assertEquals(
                InventoryRetryState.Unequip(EquipmentSlot.LEGS),
                viewModel.uiState.value.retryState,
            )

            repository.unequipFailure = null
            repository.unequipResult = UnequipEquipmentResult.AlreadyEmpty(EquipmentSlot.LEGS)
            viewModel.onEvent(InventoryEvent.Retry)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    1L to EquipmentSlot.LEGS,
                    1L to EquipmentSlot.LEGS,
                ),
                repository.unequipCalls,
            )
            assertEquals(
                InventoryUnequipResult.Success(EquipmentSlot.LEGS, changed = false),
                viewModel.uiState.value.unequipResult,
            )
            assertNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.retryState)
        }

    @Test
    fun equipProcessingBlocksUnequipUntilTheTypedCommandFinishes() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.equipGate = gate
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(InventoryEvent.SelectOwnedEquipment(102L))
        viewModel.onEvent(InventoryEvent.EquipSelected)
        viewModel.onEvent(InventoryEvent.UnequipSlot(EquipmentSlot.LEGS))
        runCurrent()

        assertEquals(
            InventoryProcessingState.Equipping(102L, EquipmentSlot.CHEST),
            viewModel.uiState.value.processingState,
        )
        assertTrue(repository.unequipCalls.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun equipFailureIsSemanticConsumableAndGenericFailureRetriesOnlyOnEvent() = runTest(dispatcher) {
        repository.equipResult = EquipOwnedEquipmentResult.OwnedEquipmentNotFound(
            characterId = 1L,
            ownedEquipmentId = 102L,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(InventoryEvent.SelectOwnedEquipment(102L))
        viewModel.onEvent(InventoryEvent.EquipSelected)
        advanceUntilIdle()
        assertEquals(
            InventoryError.OwnedEquipmentNotFound(ownedEquipmentId = 102L),
            viewModel.uiState.value.error,
        )
        assertNull(viewModel.uiState.value.retryState)

        viewModel.onEvent(InventoryEvent.ConsumeError)
        runCurrent()
        assertNull(viewModel.uiState.value.error)

        repository.equipFailure = IllegalStateException("raw equip database detail")
        viewModel.onEvent(InventoryEvent.EquipSelected)
        advanceUntilIdle()
        assertEquals(InventoryError.EquipFailed, viewModel.uiState.value.error)
        assertEquals(
            InventoryRetryState.Equip(102L, EquipmentSlot.CHEST),
            viewModel.uiState.value.retryState,
        )
        assertEquals(2, repository.equipCalls.size)

        repository.equipFailure = null
        repository.equipResult = EquipOwnedEquipmentResult.Success(102L, 2L, EquipmentSlot.CHEST)
        viewModel.onEvent(InventoryEvent.Retry)
        advanceUntilIdle()
        assertEquals(3, repository.equipCalls.size)
        assertTrue(viewModel.uiState.value.equipResult is InventoryEquipResult.Success)

        val recreated = viewModel()
        advanceUntilIdle()
        assertEquals(3, repository.equipCalls.size)
        assertNull(recreated.uiState.value.processingOwnedEquipmentId)
        assertNull(recreated.uiState.value.equipResult)
    }

    @Test
    fun inventoryLoadFailureHasExplicitRetryThatResubscribes() = runTest(dispatcher) {
        repository.inventoryFailure = IllegalStateException("raw inventory load detail")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(InventoryError.LoadFailed, viewModel.uiState.value.error)
        assertEquals(InventoryRetryState.Load, viewModel.uiState.value.retryState)
        assertEquals(1, repository.inventoryObserveCalls)

        repository.inventoryFailure = null
        viewModel.onEvent(InventoryEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.retryState)
        assertEquals(2, repository.inventoryObserveCalls)
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    private fun viewModel() = InventoryViewModel(
        repository = repository,
        equipOwnedEquipment = EquipOwnedEquipmentUseCase(repository),
        unequipEquipment = UnequipEquipmentUseCase(repository),
        characterId = 1L,
        dispatcher = dispatcher,
    )

    private class FakeEquipmentRepository(initialInventory: EquipmentInventorySnapshot) :
        EquipmentRepository {
        val inventory = MutableStateFlow(initialInventory)
        var inventoryFailure: Throwable? = null
        var inventoryObserveCalls: Int = 0
        var equipFailure: Throwable? = null
        var equipGate: CompletableDeferred<Unit>? = null
        var equipResult: EquipOwnedEquipmentResult = EquipOwnedEquipmentResult.Success(
            ownedEquipmentId = 102L,
            equipmentId = 2L,
            slot = EquipmentSlot.CHEST,
        )
        var unequipFailure: Throwable? = null
        var unequipGate: CompletableDeferred<Unit>? = null
        var unequipResult: UnequipEquipmentResult = UnequipEquipmentResult.AlreadyEmpty(
            EquipmentSlot.CHEST,
        )
        val equipCalls = mutableListOf<Triple<Long, Long, EquipmentSlot>>()
        val unequipCalls = mutableListOf<Pair<Long, EquipmentSlot>>()

        override fun observeStore(characterId: Long): Flow<EquipmentStoreSnapshot> =
            flow { error("not used") }

        override fun observeInventory(characterId: Long): Flow<EquipmentInventorySnapshot> {
            inventoryObserveCalls += 1
            val failure = inventoryFailure
            return if (failure == null) inventory else flow { throw failure }
        }

        override suspend fun purchaseEquipment(
            characterId: Long,
            equipmentId: Long,
        ): PurchaseEquipmentResult = error("not used")

        override suspend fun equipOwnedEquipment(
            characterId: Long,
            ownedEquipmentId: Long,
            targetSlot: EquipmentSlot,
        ): EquipOwnedEquipmentResult {
            equipCalls += Triple(characterId, ownedEquipmentId, targetSlot)
            equipGate?.await()
            equipFailure?.let { throw it }
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

    companion object {
        private fun inventorySnapshot(): EquipmentInventorySnapshot {
            val currentChest = equipment(1L, EquipmentType.CHEST, EquipmentSlot.CHEST, 4)
            val candidateChest = equipment(2L, EquipmentType.CHEST, EquipmentSlot.CHEST, 10)
            val currentLegs = equipment(3L, EquipmentType.LEGS, EquipmentSlot.LEGS, 99)
            val ownedChest = owned(101L, currentChest)
            val ownedCandidate = owned(102L, candidateChest)
            val ownedLegs = owned(103L, currentLegs)
            return EquipmentInventorySnapshot(
                characterId = 1L,
                ownedEquipment = listOf(ownedChest, ownedCandidate, ownedLegs),
                equippedBySlot = mapOf(
                    EquipmentSlot.CHEST to EquippedEquipment(1L, EquipmentSlot.CHEST, ownedChest),
                    EquipmentSlot.LEGS to EquippedEquipment(1L, EquipmentSlot.LEGS, ownedLegs),
                ),
            )
        }

        private fun equipment(
            id: Long,
            type: EquipmentType,
            slot: EquipmentSlot,
            amount: Int,
            weaponType: WeaponType? = null,
        ) = Equipment(
            id = id,
            nameKey = "equipment_name_$id",
            descriptionKey = "equipment_description_$id",
            type = type,
            slot = slot,
            rarity = EquipmentRarity.COMMON,
            price = 100L,
            requiredLevel = 1,
            modifiers = listOf(
                EquipmentStatModifier(
                    itemId = id,
                    target = StatTarget.Derived(DerivedStatType.DEFENSE),
                    type = ModifierType.FLAT,
                    amount = amount,
                ),
            ),
            isForSale = true,
            imageKey = if (type == EquipmentType.WEAPON) "equipment_image_$id" else null,
            weaponType = weaponType,
        )

        private fun owned(id: Long, equipment: Equipment) = OwnedEquipment(
            id = id,
            characterId = 1L,
            equipment = equipment,
            acquiredAtEpochMillis = id,
        )
    }
}
