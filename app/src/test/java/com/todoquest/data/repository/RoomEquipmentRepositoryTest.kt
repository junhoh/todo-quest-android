package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterAppearanceEntity
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterEquipmentEntity
import com.todoquest.data.local.CharacterEquippedItemsEntity
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.EquipmentEntity
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.PurchaseEligibility
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.usecase.CombatCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomEquipmentRepositoryTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: RoomEquipmentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        clock = MutableClock()
        repository = RoomEquipmentRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun purchaseEmitsGoldAndOwnershipTogetherWithCharacterPreviewSources() = runTest {
        seedCharacter(currentGold = 500L, totalXp = 500L)

        val initial = repository.observeStore(1L).first()

        assertEquals(25, initial.equipment.size)
        assertEquals(500L, initial.currentGold)
        assertEquals(6, initial.characterLevel)
        assertEquals(emptySet<Long>(), initial.ownedEquipmentIds)
        assertTrue(initial.equippedBySlot.isEmpty())
        assertEquals(CharacterLoadoutCatalog.defaultAppearance, initial.appearance)
        assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, initial.renderedEquippedItems)
        assertEquals(140, initial.derivedStats.maxHp)

        val collectorReady = CompletableDeferred<Unit>()
        var initialSeen = false
        val changedSnapshot = async {
            repository.observeStore(1L).first {
                if (!initialSeen) {
                    initialSeen = true
                    collectorReady.complete(Unit)
                    false
                } else {
                    true
                }
            }
        }
        collectorReady.await()
        val purchase = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.CLOTH_TOP_ID)
            as PurchaseEquipmentResult.Success
        assertEquals(478L, purchase.remainingGold)

        val updated = changedSnapshot.await()
        assertEquals(478L, updated.currentGold)
        assertEquals(setOf(EquipmentCatalogSeeder.CLOTH_TOP_ID), updated.ownedEquipmentIds)
        assertTrue(updated.equippedBySlot.isEmpty())
        assertEquals(initial.derivedStats, updated.derivedStats)
        assertEquals(initial.renderedEquippedItems, updated.renderedEquippedItems)
        assertEquals(25, database.equipmentDao().getAllEquipment().size)
    }

    @Test
    fun storeSnapshotProjectsOwnedActionLookupAcrossOwnershipAndEquipStates() = runTest {
        seedCharacter(currentGold = 500L)

        val unowned = repository.observeStore(1L).first()

        assertTrue(unowned.ownedEquipmentByEquipmentId.isEmpty())
        assertEquals(unowned.ownedEquipmentByEquipmentId.keys, unowned.ownedEquipmentIds)
        assertTrue(unowned.equippedBySlot.isEmpty())
        assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, unowned.renderedEquippedItems)

        val purchase = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        assertTrue(purchase.ownedEquipmentId != purchase.equipmentId)

        val ownedOnly = repository.observeStore(1L).first()
        val ownedChest = ownedOnly.ownedEquipmentByEquipmentId.getValue(purchase.equipmentId)

        assertEquals(setOf(purchase.equipmentId), ownedOnly.ownedEquipmentByEquipmentId.keys)
        assertEquals(ownedOnly.ownedEquipmentByEquipmentId.keys, ownedOnly.ownedEquipmentIds)
        assertEquals(purchase.ownedEquipmentId, ownedChest.id)
        assertEquals(purchase.equipmentId, ownedChest.equipmentId)
        assertTrue(ownedOnly.equippedBySlot.isEmpty())
        assertEquals(CharacterLoadoutCatalog.defaultEquippedItems, ownedOnly.renderedEquippedItems)

        repository.equipOwnedEquipment(
            characterId = 1L,
            ownedEquipmentId = purchase.ownedEquipmentId,
            targetSlot = EquipmentSlot.CHEST,
        )

        val equipped = repository.observeStore(1L).first()
        val equippedChest = equipped.equippedBySlot.getValue(EquipmentSlot.CHEST)

        assertEquals(equipped.ownedEquipmentByEquipmentId.keys, equipped.ownedEquipmentIds)
        assertSame(
            equipped.ownedEquipmentByEquipmentId.getValue(purchase.equipmentId),
            equippedChest.ownedEquipment,
        )
        assertEquals(
            CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_CLOTH,
            ),
            equipped.renderedEquippedItems,
        )
    }

    @Test
    fun seededAdventureSetKeepsCatalogPreviewOwnedLookupAndEquippedProjectionConsistent() = runTest {
        seedCharacter(currentGold = 10_000L, totalXp = 4_900L)
        val adventureEquipment = listOf(
            EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to EquipmentSlot.WEAPON,
            EquipmentCatalogSeeder.ADVENTURE_HAT_ID to EquipmentSlot.HELMET,
            EquipmentCatalogSeeder.ADVENTURE_JACKET_ID to EquipmentSlot.CHEST,
            EquipmentCatalogSeeder.ADVENTURE_PANTS_ID to EquipmentSlot.LEGS,
            EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID to EquipmentSlot.GLOVES,
            EquipmentCatalogSeeder.ADVENTURE_SHOES_ID to EquipmentSlot.SHOES,
            EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID to EquipmentSlot.ACCESSORY,
        )
        val purchasesByEquipmentId = adventureEquipment.associate { (equipmentId, _) ->
            equipmentId to purchase(equipmentId)
        }
        adventureEquipment.forEach { (equipmentId, slot) ->
            repository.equipOwnedEquipment(
                characterId = 1L,
                ownedEquipmentId = purchasesByEquipmentId.getValue(equipmentId).ownedEquipmentId,
                targetSlot = slot,
            )
        }

        val snapshot = repository.observeStore(1L).first()
        val adventureEquipmentIds = adventureEquipment.mapTo(mutableSetOf()) { it.first }

        assertEquals(snapshot.equipment.map { it.id }.toSet(), snapshot.previewByEquipmentId.keys)
        assertTrue(snapshot.previewByEquipmentId.keys.containsAll(adventureEquipmentIds))
        assertEquals(adventureEquipmentIds, snapshot.ownedEquipmentByEquipmentId.keys)
        assertEquals(snapshot.ownedEquipmentByEquipmentId.keys, snapshot.ownedEquipmentIds)
        adventureEquipment.forEach { (equipmentId, slot) ->
            val purchase = purchasesByEquipmentId.getValue(equipmentId)
            val owned = snapshot.ownedEquipmentByEquipmentId.getValue(equipmentId)
            assertEquals(purchase.ownedEquipmentId, owned.id)
            assertTrue(owned.id != equipmentId)
            assertSame(owned, snapshot.equippedBySlot.getValue(slot).ownedEquipment)
        }
        assertEquals(
            CharacterLoadoutCatalog.defaultEquippedItems.copy(
                headId = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
                topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
                bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
                accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
                weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                glovesId = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
            ),
            snapshot.renderedEquippedItems,
        )
    }

    @Test
    fun duplicateCatalogDefinitionsAreRejectedBeforeOwnedRowsCanBeSelected() {
        val definition = equipment(
            id = 9_001L,
            type = EquipmentType.CHEST.name,
            slot = EquipmentSlot.CHEST.name,
        )
        val failure = runCatching {
            mapEquipmentSources(
                definitionEntities = listOf(
                    definition,
                    definition.copy(nameKey = "equipment_name_duplicate"),
                ),
                modifierEntities = emptyList(),
                ownedEntities = listOf(
                    OwnedEquipmentEntity(
                        id = 8_001L,
                        characterId = 1L,
                        equipmentId = definition.id,
                        acquiredAtEpochMillis = 1_000L,
                    ),
                ),
                equippedEntities = emptyList(),
            )
        }.exceptionOrNull()

        assertTrue(failure is EquipmentRepositoryDataException)
    }

    @Test
    fun storeSnapshotProjectsEveryUnownedDefinitionWithoutMutatingRoomSources() = runTest {
        seedCharacter(currentGold = 500L, totalXp = 500L, currentHp = 73)
        val prepared = repository.observeStore(1L).first()
        val profileBefore = database.characterProfileDao().getProfile(1L)
        val currentStateBefore = database.characterProfileDao().getCurrentState(1L)
        val equipmentBefore = database.equipmentDao().getAllEquipment()
        val ownedBefore = database.equipmentDao().getOwnedEquipment(1L)
        val equippedBefore = database.equipmentDao().getCharacterEquipment(1L)

        val projected = repository.observeStore(1L).first()

        assertEquals(
            projected.equipment.map { it.id }.toSet(),
            projected.previewByEquipmentId.keys,
        )
        assertEquals(25, projected.previewByEquipmentId.size)
        assertTrue(EquipmentCatalogSeeder.CLOTH_TOP_ID !in projected.ownedEquipmentIds)
        assertEquals(
            CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_CLOTH,
            ),
            projected.previewByEquipmentId
                .getValue(EquipmentCatalogSeeder.CLOTH_TOP_ID)
                .renderedEquippedItems,
        )
        assertEquals(
            prepared.derivedStats.maxHp + 12,
            projected.previewByEquipmentId
                .getValue(EquipmentCatalogSeeder.CLOTH_TOP_ID)
                .derivedStats
                .maxHp,
        )
        assertEquals(prepared.derivedStats, projected.derivedStats)
        assertEquals(prepared.renderedEquippedItems, projected.renderedEquippedItems)
        assertEquals(profileBefore, database.characterProfileDao().getProfile(1L))
        assertEquals(currentStateBefore, database.characterProfileDao().getCurrentState(1L))
        assertEquals(equipmentBefore, database.equipmentDao().getAllEquipment())
        assertEquals(ownedBefore, database.equipmentDao().getOwnedEquipment(1L))
        assertEquals(equippedBefore, database.equipmentDao().getCharacterEquipment(1L))
    }

    @Test
    fun storePreviewReplacesOnlyCandidateSlotAndKeepsActualSnapshotUnchanged() = runTest {
        seedCharacter(currentGold = 1_000L, currentHp = 73)
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val legs = purchase(EquipmentCatalogSeeder.CLOTH_PANTS_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        repository.equipOwnedEquipment(1L, legs.ownedEquipmentId, EquipmentSlot.LEGS)

        val snapshot = repository.observeStore(1L).first()
        val preview = snapshot.previewByEquipmentId.getValue(
            EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
        )

        assertTrue(EquipmentCatalogSeeder.LEATHER_ARMOR_ID !in snapshot.ownedEquipmentIds)
        assertEquals(CharacterLoadoutCatalog.TOP_CLOTH, snapshot.renderedEquippedItems.topId)
        assertEquals(
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            snapshot.renderedEquippedItems.bottomId,
        )
        assertEquals(CharacterLoadoutCatalog.TOP_LEATHER_ARMOR, preview.renderedEquippedItems.topId)
        assertEquals(snapshot.renderedEquippedItems.bottomId, preview.renderedEquippedItems.bottomId)
        assertEquals(snapshot.renderedEquippedItems.headId, preview.renderedEquippedItems.headId)
        assertEquals(122, snapshot.derivedStats.maxHp)
        assertEquals(130, preview.derivedStats.maxHp)
    }

    @Test
    fun storePreviewUsesActiveSevereInjuryInTheOfficialStatFormula() = runTest {
        seedCharacter(currentGold = 500L, currentHp = 44)
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        val hpBefore = database.characterProfileDao().getCurrentState(1L)?.currentHp

        val snapshot = repository.observeStore(1L).first()
        val preview = snapshot.previewByEquipmentId.getValue(
            EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
        )

        assertEquals(97, snapshot.derivedStats.maxHp)
        assertEquals(16, snapshot.derivedStats.attack)
        assertEquals(104, preview.derivedStats.maxHp)
        assertEquals(16, preview.derivedStats.attack)
        assertEquals(hpBefore, database.characterProfileDao().getCurrentState(1L)?.currentHp)
        assertEquals(CharacterLoadoutCatalog.TOP_CLOTH, snapshot.renderedEquippedItems.topId)
        assertEquals(CharacterLoadoutCatalog.TOP_LEATHER_ARMOR, preview.renderedEquippedItems.topId)
    }

    @Test
    fun storePreviewKeepsCurrentAppearanceForNullAndUnknownLayers() = runTest {
        seedCharacter(currentGold = 500L)
        repository.observeStore(1L).first()
        val unknownLayerDefinition = equipment(
            id = 9_003L,
            type = EquipmentType.GLOVES.name,
            slot = EquipmentSlot.GLOVES.name,
            layerKey = "gloves_unknown",
        )
        database.equipmentDao().insertEquipmentDefinitions(listOf(unknownLayerDefinition))

        val snapshot = repository.observeStore(1L).first()

        assertEquals(
            snapshot.renderedEquippedItems,
            snapshot.previewByEquipmentId
                .getValue(EquipmentCatalogSeeder.MAGE_RING_ID)
                .renderedEquippedItems,
        )
        assertEquals(
            snapshot.renderedEquippedItems,
            snapshot.previewByEquipmentId
                .getValue(unknownLayerDefinition.id)
                .renderedEquippedItems,
        )
        assertEquals(
            snapshot.derivedStats,
            snapshot.previewByEquipmentId.getValue(unknownLayerDefinition.id).derivedStats,
        )
    }

    @Test
    fun equipEmitsSlotFinalStatsAndValidatedRenderedLayerTogether() = runTest {
        seedCharacter(currentGold = 500L)
        val purchase = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.CLOTH_TOP_ID)
            as PurchaseEquipmentResult.Success

        repository.equipOwnedEquipment(1L, purchase.ownedEquipmentId, EquipmentSlot.CHEST)

        val updated = repository.observeStore(1L).first()
        assertEquals(
            purchase.ownedEquipmentId,
            updated.equippedBySlot.getValue(EquipmentSlot.CHEST).ownedEquipment.id,
        )
        assertEquals(122, updated.derivedStats.maxHp)
        assertEquals(CharacterLoadoutCatalog.TOP_CLOTH, updated.renderedEquippedItems.topId)
        assertEquals(
            CharacterLoadoutCatalog.defaultEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_CLOTH,
            ),
            updated.renderedEquippedItems,
        )
    }

    @Test
    fun equipUsesActiveStatusModifiersAndRemovingInjuryDoesNotHealCurrentHp() = runTest {
        seedCharacter(currentGold = 500L, currentHp = 44)
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val injured = repository.observeStore(1L).first()
        assertEquals(88, injured.derivedStats.maxHp)
        assertEquals(16, injured.derivedStats.attack)

        val purchase = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        repository.equipOwnedEquipment(1L, purchase.ownedEquipmentId, EquipmentSlot.CHEST)

        val equipped = repository.observeStore(1L).first()
        assertEquals(97, equipped.derivedStats.maxHp)
        assertEquals(48, database.characterProfileDao().getCurrentState()?.currentHp)

        assertTrue(
            RoomStatusEffectRepository(database, clock).removeStatusEffect(
                characterId = 1L,
                type = StatusEffectType.SEVERE_INJURY,
                revision = 1L,
                mutationId = "status-effect:removed:test",
            ),
        )
        val recovered = repository.observeStore(1L).first()
        assertEquals(122, recovered.derivedStats.maxHp)
        assertEquals(20, recovered.derivedStats.attack)
        assertEquals(48, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun seededHelmetsProjectTheirLayersAndPreserveStatsHpRatioAndOtherAppearance() = runTest {
        seedCharacter(currentGold = 10_000L, totalXp = 1_000L, currentHp = 85)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
            accessoryId = null,
            weaponId = null,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val initial = repository.observeStore(1L).first()
        val leatherHat = purchase(EquipmentCatalogSeeder.LEATHER_HAT_ID)
        val ironHelmet = purchase(EquipmentCatalogSeeder.IRON_HELMET_ID)

        assertEquals(initial.derivedStats, repository.observeStore(1L).first().derivedStats)
        assertEquals(85, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, leatherHat.ownedEquipmentId, EquipmentSlot.HELMET)

        val leatherSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT),
            leatherSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp + 12, leatherSnapshot.derivedStats.maxHp)
        assertEquals(initial.derivedStats.defense, leatherSnapshot.derivedStats.defense)
        assertEquals(91, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, ironHelmet.ownedEquipmentId, EquipmentSlot.HELMET)

        val ironSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(headId = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET),
            ironSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp + 30, ironSnapshot.derivedStats.maxHp)
        assertEquals(initial.derivedStats.defense + 6, ironSnapshot.derivedStats.defense)
        assertEquals(100, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            setOf(
                EquipmentCatalogSeeder.LEATHER_HAT_ID,
                EquipmentCatalogSeeder.IRON_HELMET_ID,
            ),
            ironSnapshot.ownedEquipmentIds,
        )
    }

    @Test
    fun everySeededOutfitProjectsOnlyItsSlotAndPreservesStatsHpRatio() = runTest {
        seedCharacter(currentGold = 100_000L, totalXp = 4_900L, currentHp = 200)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
            accessoryId = null,
            weaponId = null,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val cases = listOf(
            Triple(
                EquipmentCatalogSeeder.CLOTH_TOP_ID,
                EquipmentSlot.CHEST,
                CharacterLoadoutCatalog.TOP_CLOTH,
            ),
            Triple(
                EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
                EquipmentSlot.CHEST,
                CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            ),
            Triple(
                EquipmentCatalogSeeder.IRON_BREASTPLATE_ID,
                EquipmentSlot.CHEST,
                CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            ),
            Triple(
                EquipmentCatalogSeeder.CLOTH_PANTS_ID,
                EquipmentSlot.LEGS,
                CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            ),
            Triple(
                EquipmentCatalogSeeder.LEATHER_PANTS_ID,
                EquipmentSlot.LEGS,
                CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            ),
            Triple(
                EquipmentCatalogSeeder.STEEL_GREAVES_ID,
                EquipmentSlot.LEGS,
                CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            ),
        )
        val balanceConfig = CharacterStatBalanceConfig()
        var expectedItems = fallback
        var expectedHp = 200
        var previousMaxHp = repository.observeStore(1L).first().derivedStats.maxHp

        cases.forEach { (equipmentId, slot, expectedLayerId) ->
            val purchase = purchase(equipmentId)

            repository.equipOwnedEquipment(1L, purchase.ownedEquipmentId, slot)

            val snapshot = repository.observeStore(1L).first()
            expectedItems = when (slot) {
                EquipmentSlot.CHEST -> expectedItems.copy(topId = expectedLayerId)
                EquipmentSlot.LEGS -> expectedItems.copy(bottomId = expectedLayerId)
                else -> error("Unexpected outfit slot: $slot")
            }
            expectedHp = CombatCalculator.preserveHpRatio(
                oldHp = expectedHp,
                oldMax = previousMaxHp,
                newMax = snapshot.derivedStats.maxHp,
                config = balanceConfig,
            )
            assertEquals(expectedItems, snapshot.renderedEquippedItems)
            assertEquals(expectedHp, database.characterProfileDao().getCurrentState()?.currentHp)
            previousMaxHp = snapshot.derivedStats.maxHp
        }

        val finalSnapshot = repository.observeStore(1L).first()
        assertEquals(
            CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            finalSnapshot.renderedEquippedItems.topId,
        )
        assertEquals(
            CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            finalSnapshot.renderedEquippedItems.bottomId,
        )
        assertEquals(
            setOf(EquipmentSlot.CHEST, EquipmentSlot.LEGS),
            finalSnapshot.equippedBySlot.keys,
        )
    }

    @Test
    fun seededGlovesAndShoesPurchaseEquipAndReplaceOnlyTheirAppearanceFields() = runTest {
        seedCharacter(currentGold = 100_000L, totalXp = 4_900L, currentHp = 173)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            bottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            glovesId = null,
            shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
            accessoryId = null,
            weaponId = null,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val initial = repository.observeStore(1L).first()
        val initialHp = database.characterProfileDao().getCurrentState()!!.currentHp
        val leatherGloves = purchase(EquipmentCatalogSeeder.LEATHER_GLOVES_ID)
        val steelGauntlets = purchase(EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID)
        val travelersBoots = purchase(EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID)
        val windwalkerBoots = purchase(EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID)

        val ownedOnly = repository.observeStore(1L).first()
        assertEquals(initial.derivedStats, ownedOnly.derivedStats)
        assertEquals(fallback, ownedOnly.renderedEquippedItems)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, leatherGloves.ownedEquipmentId, EquipmentSlot.GLOVES)
        val leatherSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER),
            leatherSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp, leatherSnapshot.derivedStats.maxHp)
        assertTrue(leatherSnapshot.derivedStats.attack > initial.derivedStats.attack)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, travelersBoots.ownedEquipmentId, EquipmentSlot.SHOES)
        val travelersSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(
                glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
                shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            ),
            travelersSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp, travelersSnapshot.derivedStats.maxHp)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, steelGauntlets.ownedEquipmentId, EquipmentSlot.GLOVES)
        val steelSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(
                glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            ),
            steelSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp, steelSnapshot.derivedStats.maxHp)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, windwalkerBoots.ownedEquipmentId, EquipmentSlot.SHOES)
        val finalSnapshot = repository.observeStore(1L).first()
        assertEquals(
            fallback.copy(
                glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                shoesId = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            ),
            finalSnapshot.renderedEquippedItems,
        )
        assertEquals(initial.derivedStats.maxHp, finalSnapshot.derivedStats.maxHp)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            setOf(EquipmentSlot.GLOVES, EquipmentSlot.SHOES),
            finalSnapshot.equippedBySlot.keys,
        )
        assertEquals(
            setOf(
                EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
                EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID,
                EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
            ),
            finalSnapshot.ownedEquipmentIds,
        )
    }

    @Test
    fun seededWeaponsPurchaseEquipAndReplaceOnlyWeaponAppearanceWithoutOwnedStatEffects() = runTest {
        seedCharacter(currentGold = 100_000L, totalXp = 4_900L, currentHp = 173)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            bottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
            shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            accessoryId = null,
            weaponId = null,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val initial = repository.observeStore(1L).first()
        val initialHp = database.characterProfileDao().getCurrentState()!!.currentHp
        val cases = listOf(
            Triple(
                EquipmentCatalogSeeder.WORN_SWORD_ID,
                WeaponType.LONGSWORD,
                CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            ),
            Triple(
                EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
                WeaponType.LONGSWORD,
                CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            ),
            Triple(
                EquipmentCatalogSeeder.ASH_SPEAR_ID,
                WeaponType.SPEAR,
                CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            ),
            Triple(
                EquipmentCatalogSeeder.STEEL_MACE_ID,
                WeaponType.BLUNT,
                CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            ),
        )
        val purchases = cases.map { (equipmentId, _, _) -> purchase(equipmentId) }

        val ownedOnly = repository.observeStore(1L).first()
        assertEquals(initial.derivedStats, ownedOnly.derivedStats)
        assertEquals(fallback, ownedOnly.renderedEquippedItems)
        assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)

        cases.zip(purchases).forEach { (case, purchase) ->
            val (_, expectedWeaponType, expectedLayerId) = case
            repository.equipOwnedEquipment(1L, purchase.ownedEquipmentId, EquipmentSlot.WEAPON)

            val snapshot = repository.observeStore(1L).first()
            assertEquals(expectedWeaponType, purchase.let { result ->
                snapshot.equipment.single { it.id == result.equipmentId }.weaponType
            })
            assertEquals(fallback.copy(weaponId = expectedLayerId), snapshot.renderedEquippedItems)
            assertEquals(setOf(EquipmentSlot.WEAPON), snapshot.equippedBySlot.keys)
            assertEquals(initial.derivedStats.maxHp, snapshot.derivedStats.maxHp)
            assertEquals(initialHp, database.characterProfileDao().getCurrentState()?.currentHp)
        }

        val finalSnapshot = repository.observeStore(1L).first()
        assertEquals(cases.map { it.first }.toSet(), finalSnapshot.ownedEquipmentIds)
        assertTrue(finalSnapshot.derivedStats.attack > initial.derivedStats.attack)
    }

    @Test
    fun unknownGameplayLayerKeepsExistingAppearanceFallback() = runTest {
        seedCharacter(currentGold = 500L)
        database.characterProfileDao().upsertAppearance(
            CharacterAppearanceEntity(
                characterId = 1L,
                hairId = CharacterLoadoutCatalog.HAIR_DEFAULT,
            ),
        )
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
            bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val customDefinition = equipment(
            id = 9_003L,
            type = EquipmentType.GLOVES.name,
            slot = EquipmentSlot.GLOVES.name,
            layerKey = "gloves_unknown",
        )
        database.equipmentDao().insertEquipmentDefinitions(listOf(customDefinition))
        val customPurchase = purchase(customDefinition.id)
        repository.equipOwnedEquipment(1L, customPurchase.ownedEquipmentId, EquipmentSlot.GLOVES)

        val unknownLayer = repository.observeStore(1L).first()
        assertEquals(CharacterLoadoutCatalog.defaultAppearance, unknownLayer.appearance)
        assertEquals(fallback, unknownLayer.renderedEquippedItems)
    }

    @Test
    fun inventoryObservesOwnedDefinitionsAndCurrentEquippedSlot() = runTest {
        seedCharacter(currentGold = 1_000L)
        val chest = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.CLOTH_TOP_ID)
            as PurchaseEquipmentResult.Success
        val legs = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.CLOTH_PANTS_ID)
            as PurchaseEquipmentResult.Success
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        repository.equipOwnedEquipment(1L, legs.ownedEquipmentId, EquipmentSlot.LEGS)

        val inventory = repository.observeInventory(1L).first()

        assertEquals(
            listOf(EquipmentCatalogSeeder.CLOTH_TOP_ID, EquipmentCatalogSeeder.CLOTH_PANTS_ID),
            inventory.ownedEquipment.map { it.equipmentId },
        )
        assertEquals(
            mapOf(
                EquipmentSlot.CHEST to chest.ownedEquipmentId,
                EquipmentSlot.LEGS to legs.ownedEquipmentId,
            ),
            inventory.equippedBySlot.mapValues { it.value.ownedEquipment.id },
        )
    }

    @Test
    fun unknownStorageEnumBecomesRepositoryDataErrorWithoutMutatingCharacter() = runTest {
        seedCharacter(currentGold = 100L)
        database.equipmentDao().insertEquipmentDefinitions(
            listOf(equipment(id = 9_001L, type = "CAPE", slot = EquipmentSlot.CHEST.name)),
        )

        val failure = runCatching { repository.observeStore(1L).first() }.exceptionOrNull()

        assertTrue(failure is EquipmentRepositoryDataException)
        assertEquals(100L, database.characterProfileDao().getProfile()?.currentGold)
        assertTrue(database.equipmentDao().getOwnedEquipment(1L).isEmpty())
    }

    @Test
    fun knownWeaponSubtypeStorageValuesMapToAllDomainWeaponTypes() = runTest {
        seedCharacter(currentGold = 100L)
        val storedWeapons = WeaponType.entries.mapIndexed { index, weaponType ->
            equipment(
                id = 9_100L + index,
                type = EquipmentType.WEAPON.name,
                slot = EquipmentSlot.WEAPON.name,
                weaponType = weaponType.name,
            )
        }
        database.equipmentDao().insertEquipmentDefinitions(storedWeapons)

        val definitions = repository.observeStore(1L).first().equipment.associateBy { it.id }

        storedWeapons.forEachIndexed { index, entity ->
            assertEquals(WeaponType.entries[index], definitions.getValue(entity.id).weaponType)
        }
    }

    @Test
    fun unknownWeaponSubtypeBecomesRepositoryDataErrorWithoutMutatingCharacter() = runTest {
        seedCharacter(currentGold = 100L)
        database.equipmentDao().insertEquipmentDefinitions(
            listOf(
                equipment(
                    id = 9_200L,
                    type = EquipmentType.WEAPON.name,
                    slot = EquipmentSlot.WEAPON.name,
                    weaponType = "BOW",
                ),
            ),
        )

        val failure = runCatching { repository.observeStore(1L).first() }.exceptionOrNull()

        assertTrue(failure is EquipmentRepositoryDataException)
        assertEquals(100L, database.characterProfileDao().getProfile()?.currentGold)
        assertTrue(database.equipmentDao().getOwnedEquipment(1L).isEmpty())
    }

    @Test
    fun purchaseUsesLatestRoomStateAndReturnsCompleteSuccessResult() = runTest {
        seedCharacter(currentGold = 100L)
        repository.observeStore(1L).first()
        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(currentGold = 60L),
        )

        val result = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID)

        assertEquals(
            PurchaseEquipmentResult.Success(
                ownedEquipmentId = 1L,
                equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
                equipmentNameKey = "equipment_name_worn_sword",
                type = EquipmentType.WEAPON,
                slot = EquipmentSlot.WEAPON,
                remainingGold = 40L,
            ),
            result,
        )
        assertEquals(40L, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(
            listOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
            database.equipmentDao().getOwnedEquipment(1L).map { it.equipmentId },
        )
    }

    @Test
    fun concurrentDuplicatePurchaseChargesAndOwnsExactlyOnce() = runTest {
        seedCharacter(currentGold = 100L)

        val results = listOf(
            async { repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID) },
            async { repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID) },
        ).awaitAll()

        assertEquals(1, results.count { it is PurchaseEquipmentResult.Success })
        assertEquals(
            1,
            results.count {
                it == PurchaseEquipmentResult.Unavailable(
                    PurchaseEligibility.AlreadyOwned(EquipmentCatalogSeeder.WORN_SWORD_ID),
                )
            },
        )
        assertEquals(80L, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(1, database.equipmentDao().getOwnedEquipment(1L).size)
    }

    @Test
    fun purchaseFailuresDoNotChangeGoldOrInventory() = runTest {
        seedCharacter(currentGold = 10_000L, totalXp = 4_900L)
        repository.observeStore(1L).first()

        setForSale(EquipmentCatalogSeeder.WORN_SWORD_ID, false)
        assertPurchaseUnchanged(
            equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
            expected = PurchaseEquipmentResult.Unavailable(
                PurchaseEligibility.NotForSale(EquipmentCatalogSeeder.WORN_SWORD_ID),
            ),
        )
        setForSale(EquipmentCatalogSeeder.WORN_SWORD_ID, true)

        val bought = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID)
            as PurchaseEquipmentResult.Success
        assertPurchaseUnchanged(
            equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
            expected = PurchaseEquipmentResult.Unavailable(
                PurchaseEligibility.AlreadyOwned(EquipmentCatalogSeeder.WORN_SWORD_ID),
            ),
        )
        assertEquals(9_980L, bought.remainingGold)

        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(totalXp = 0L),
        )
        assertPurchaseUnchanged(
            equipmentId = EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
            expected = PurchaseEquipmentResult.Unavailable(
                PurchaseEligibility.LevelTooLow(
                    equipmentId = EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
                    requiredLevel = 12,
                    characterLevel = 1,
                ),
            ),
        )

        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(currentGold = 26L),
        )
        assertPurchaseUnchanged(
            equipmentId = EquipmentCatalogSeeder.LEATHER_HAT_ID,
            expected = PurchaseEquipmentResult.Unavailable(
                PurchaseEligibility.InsufficientGold(
                    equipmentId = EquipmentCatalogSeeder.LEATHER_HAT_ID,
                    price = 27L,
                    availableGold = 26L,
                ),
            ),
        )

        val mismatched = equipment(
            id = 9_002L,
            type = EquipmentType.CHEST.name,
            slot = EquipmentSlot.LEGS.name,
            price = 1L,
        )
        database.equipmentDao().insertEquipmentDefinitions(listOf(mismatched))
        assertPurchaseUnchanged(
            equipmentId = mismatched.id,
            expected = PurchaseEquipmentResult.Unavailable(
                PurchaseEligibility.UnsupportedSlot(
                    equipmentId = mismatched.id,
                    type = EquipmentType.CHEST,
                    slot = EquipmentSlot.LEGS,
                ),
            ),
        )
    }

    @Test
    fun storeAndInventoryPreparationUpdateLegacyPricesWithoutRefundingOwnedEquipment() = runTest {
        seedCharacter(currentGold = 100L)
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        val ownedId = database.equipmentDao().insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.LEATHER_HAT_ID,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = 40 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = 55 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.LEATHER_HAT_ID),
        )

        val inventory = repository.observeInventory(1L).first()

        assertEquals(27L, inventory.ownedEquipment.single().equipment.price)
        assertEquals(ownedId, inventory.ownedEquipment.single().id)
        assertEquals(100L, database.characterProfileDao().getProfile()?.currentGold)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = 40 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )
        val store = repository.observeStore(1L).first()

        assertEquals(
            20L,
            store.equipment.single { it.id == EquipmentCatalogSeeder.WORN_SWORD_ID }.price,
        )
        assertEquals(setOf(EquipmentCatalogSeeder.LEATHER_HAT_ID), store.ownedEquipmentIds)
        assertEquals(100L, store.currentGold)
    }

    @Test
    fun purchasePreparationUpdatesLegacyPriceAndChargesTheLatestAmount() = runTest {
        seedCharacter(currentGold = 25L)
        repository.observeStore(1L).first()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = 40 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )

        val result = repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID)

        assertEquals(
            PurchaseEquipmentResult.Success(
                ownedEquipmentId = 1L,
                equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
                equipmentNameKey = "equipment_name_worn_sword",
                type = EquipmentType.WEAPON,
                slot = EquipmentSlot.WEAPON,
                remainingGold = 5L,
            ),
            result,
        )
        assertEquals(5L, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(
            listOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
            database.equipmentDao().getOwnedEquipment(1L).map { it.equipmentId },
        )
    }

    @Test
    fun unexpectedOwnedInsertFailureRollsBackGoldDeduction() = runTest {
        seedCharacter(currentGold = 100L)
        repository.observeStore(1L).first()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_owned_equipment_insert
            BEFORE INSERT ON owned_equipment
            BEGIN
                SELECT RAISE(ABORT, 'forced owned insert failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching {
            repository.purchaseEquipment(1L, EquipmentCatalogSeeder.WORN_SWORD_ID)
        }

        assertTrue(failure.isFailure)
        assertEquals(100L, database.characterProfileDao().getProfile()?.currentGold)
        assertTrue(database.equipmentDao().getOwnedEquipment(1L).isEmpty())
    }

    @Test
    fun chestAndLegsEquipIndependentlyAndChestReplacementPreservesInventoryAndHpRatio() = runTest {
        seedCharacter(currentGold = 10_000L, totalXp = 400L, currentHp = 55)
        val clothChest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val leatherChest = purchase(EquipmentCatalogSeeder.LEATHER_ARMOR_ID)
        val clothLegs = purchase(EquipmentCatalogSeeder.CLOTH_PANTS_ID)

        assertEquals(
            EquipOwnedEquipmentResult.Success(
                clothChest.ownedEquipmentId,
                clothChest.equipmentId,
                EquipmentSlot.CHEST,
            ),
            repository.equipOwnedEquipment(1L, clothChest.ownedEquipmentId, EquipmentSlot.CHEST),
        )
        assertEquals(59, database.characterProfileDao().getCurrentState()?.currentHp)
        repository.equipOwnedEquipment(1L, clothLegs.ownedEquipmentId, EquipmentSlot.LEGS)
        assertEquals(59, database.characterProfileDao().getCurrentState()?.currentHp)

        repository.equipOwnedEquipment(1L, leatherChest.ownedEquipmentId, EquipmentSlot.CHEST)

        assertEquals(62, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            mapOf(
                EquipmentSlot.CHEST.name to leatherChest.ownedEquipmentId,
                EquipmentSlot.LEGS.name to clothLegs.ownedEquipmentId,
            ),
            database.equipmentDao().getCharacterEquipment(1L).associate { it.slot to it.ownedEquipmentId },
        )
        assertEquals(
            setOf(clothChest.ownedEquipmentId, leatherChest.ownedEquipmentId, clothLegs.ownedEquipmentId),
            database.equipmentDao().getOwnedEquipment(1L).map { it.id }.toSet(),
        )
    }

    @Test
    fun legsReplacementPreservesChestOtherSixSlotsAndEveryOwnedRow() = runTest {
        seedCharacter(currentGold = 100_000L, totalXp = 4_900L, currentHp = 200)
        val initialBySlot = linkedMapOf(
            EquipmentSlot.WEAPON to purchase(EquipmentCatalogSeeder.WORN_SWORD_ID),
            EquipmentSlot.HELMET to purchase(EquipmentCatalogSeeder.LEATHER_HAT_ID),
            EquipmentSlot.CHEST to purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID),
            EquipmentSlot.LEGS to purchase(EquipmentCatalogSeeder.CLOTH_PANTS_ID),
            EquipmentSlot.GLOVES to purchase(EquipmentCatalogSeeder.LEATHER_GLOVES_ID),
            EquipmentSlot.SHOES to purchase(EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID),
            EquipmentSlot.ACCESSORY to purchase(EquipmentCatalogSeeder.MAGE_RING_ID),
        )
        val replacementLegs = purchase(EquipmentCatalogSeeder.LEATHER_PANTS_ID)
        initialBySlot.forEach { (slot, owned) ->
            repository.equipOwnedEquipment(1L, owned.ownedEquipmentId, slot)
        }
        val equippedBefore = database.equipmentDao().getCharacterEquipment(1L)
            .associate { EquipmentSlot.valueOf(it.slot) to it.ownedEquipmentId }
        val ownedBefore = database.equipmentDao().getOwnedEquipment(1L).map { it.id }.toSet()

        repository.equipOwnedEquipment(1L, replacementLegs.ownedEquipmentId, EquipmentSlot.LEGS)

        val equippedAfter = database.equipmentDao().getCharacterEquipment(1L)
            .associate { EquipmentSlot.valueOf(it.slot) to it.ownedEquipmentId }
        assertEquals(EquipmentSlot.entries.toSet(), equippedAfter.keys)
        assertEquals(
            equippedBefore - EquipmentSlot.LEGS,
            equippedAfter - EquipmentSlot.LEGS,
        )
        assertEquals(
            initialBySlot.getValue(EquipmentSlot.CHEST).ownedEquipmentId,
            equippedAfter.getValue(EquipmentSlot.CHEST),
        )
        assertEquals(replacementLegs.ownedEquipmentId, equippedAfter.getValue(EquipmentSlot.LEGS))
        assertEquals(ownedBefore, database.equipmentDao().getOwnedEquipment(1L).map { it.id }.toSet())
    }

    @Test
    fun equipKeepsZeroHpAtZero() = runTest {
        seedCharacter(currentGold = 100L, currentHp = 0)
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)

        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)

        assertEquals(0, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun equipRejectsMissingForeignOwnedAndSlotMismatchWithoutMutation() = runTest {
        seedCharacter(currentGold = 1_000L, totalXp = 400L, currentHp = 55)
        seedCharacter(characterId = 2L, currentGold = 1_000L, currentHp = 55)
        repository.observeStore(1L).first()
        val foreignOwnedId = database.equipmentDao().insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 2L,
                equipmentId = EquipmentCatalogSeeder.CLOTH_TOP_ID,
                acquiredAtEpochMillis = 1L,
            ),
        )
        val ownLegs = purchase(EquipmentCatalogSeeder.CLOTH_PANTS_ID)

        assertEquals(
            EquipOwnedEquipmentResult.OwnedEquipmentNotFound(1L, Long.MAX_VALUE),
            repository.equipOwnedEquipment(1L, Long.MAX_VALUE, EquipmentSlot.CHEST),
        )
        assertEquals(
            EquipOwnedEquipmentResult.NotOwnedByCharacter(1L, foreignOwnedId, 2L),
            repository.equipOwnedEquipment(1L, foreignOwnedId, EquipmentSlot.CHEST),
        )
        assertEquals(
            EquipOwnedEquipmentResult.SlotMismatch(
                ownedEquipmentId = ownLegs.ownedEquipmentId,
                type = EquipmentType.LEGS,
                equipmentSlot = EquipmentSlot.LEGS,
                targetSlot = EquipmentSlot.CHEST,
            ),
            repository.equipOwnedEquipment(1L, ownLegs.ownedEquipmentId, EquipmentSlot.CHEST),
        )
        assertTrue(database.equipmentDao().getCharacterEquipment(1L).isEmpty())
        assertEquals(55, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun currentStateFailureRollsBackTargetSlotReplacement() = runTest {
        seedCharacter(currentGold = 1_000L, totalXp = 400L, currentHp = 55)
        val clothChest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val leatherChest = purchase(EquipmentCatalogSeeder.LEATHER_ARMOR_ID)
        repository.equipOwnedEquipment(1L, clothChest.ownedEquipmentId, EquipmentSlot.CHEST)
        val hpBefore = database.characterProfileDao().getCurrentState()!!.currentHp
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_character_state_update
            BEFORE INSERT ON character_current_state
            BEGIN
                SELECT RAISE(ABORT, 'forced current state failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching {
            repository.equipOwnedEquipment(1L, leatherChest.ownedEquipmentId, EquipmentSlot.CHEST)
        }

        assertTrue(failure.isFailure)
        assertEquals(hpBefore, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            clothChest.ownedEquipmentId,
            database.equipmentDao().getCharacterEquipmentAtSlot(1L, EquipmentSlot.CHEST.name)
                ?.ownedEquipmentId,
        )
    }

    @Test
    fun unequipRemovesOnlyTargetPreservesOwnershipAndClearsItsAppearanceFallback() = runTest {
        seedCharacter(currentGold = 1_000L, currentHp = 55)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            bottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
            shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            accessoryId = null,
            weaponId = CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val legs = purchase(EquipmentCatalogSeeder.CLOTH_PANTS_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        repository.equipOwnedEquipment(1L, legs.ownedEquipmentId, EquipmentSlot.LEGS)
        val before = repository.observeStore(1L).first()
        val hpBefore = database.characterProfileDao().getCurrentState()!!.currentHp
        val goldBefore = database.characterProfileDao().getProfile()!!.currentGold

        val result = repository.unequipEquipment(1L, EquipmentSlot.CHEST)

        assertEquals(
            UnequipEquipmentResult.Success(
                ownedEquipmentId = chest.ownedEquipmentId,
                equipmentId = chest.equipmentId,
                slot = EquipmentSlot.CHEST,
            ),
            result,
        )
        val after = repository.observeStore(1L).first()
        assertEquals(setOf(EquipmentSlot.LEGS), after.equippedBySlot.keys)
        assertEquals(
            legs.ownedEquipmentId,
            after.equippedBySlot.getValue(EquipmentSlot.LEGS).ownedEquipment.id,
        )
        assertEquals(
            setOf(chest.equipmentId, legs.equipmentId),
            after.ownedEquipmentIds,
        )
        assertEquals(
            fallback.copy(topId = CharacterLoadoutCatalog.TOP_DEFAULT),
            database.characterProfileDao().getEquippedItems(1L)?.let {
                CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    headId = it.headId,
                    topId = it.topId,
                    bottomId = it.bottomId,
                    shoesId = it.shoesId,
                    accessoryId = it.accessoryId,
                    weaponId = it.weaponId,
                    glovesId = it.glovesId,
                )
            },
        )
        assertEquals(
            fallback.copy(
                topId = CharacterLoadoutCatalog.TOP_DEFAULT,
                bottomId = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            ),
            after.renderedEquippedItems,
        )
        assertEquals(before.derivedStats.maxHp - 12, after.derivedStats.maxHp)
        assertEquals(
            CombatCalculator.preserveHpRatio(
                oldHp = hpBefore,
                oldMax = before.derivedStats.maxHp,
                newMax = after.derivedStats.maxHp,
                config = CharacterStatBalanceConfig(),
            ),
            database.characterProfileDao().getCurrentState()?.currentHp,
        )
        assertEquals(goldBefore, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(25, database.equipmentDao().getAllEquipment().size)
    }

    @Test
    fun unequipAlreadyEmptyIsIdempotentAndDoesNotWriteCharacterSources() = runTest {
        seedCharacter(currentGold = 100L, currentHp = 77)
        repository.observeStore(1L).first()
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = null,
            topId = CharacterLoadoutCatalog.TOP_DEFAULT,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val profileBefore = database.characterProfileDao().getProfile(1L)
        val currentStateBefore = database.characterProfileDao().getCurrentState(1L)
        val fallbackBefore = database.characterProfileDao().getEquippedItems(1L)
        val ownedBefore = database.equipmentDao().getOwnedEquipment(1L)

        assertEquals(
            UnequipEquipmentResult.AlreadyEmpty(EquipmentSlot.HELMET),
            repository.unequipEquipment(1L, EquipmentSlot.HELMET),
        )
        assertEquals(
            UnequipEquipmentResult.AlreadyEmpty(EquipmentSlot.HELMET),
            repository.unequipEquipment(1L, EquipmentSlot.HELMET),
        )

        assertEquals(profileBefore, database.characterProfileDao().getProfile(1L))
        assertEquals(currentStateBefore, database.characterProfileDao().getCurrentState(1L))
        assertEquals(fallbackBefore, database.characterProfileDao().getEquippedItems(1L))
        assertEquals(ownedBefore, database.equipmentDao().getOwnedEquipment(1L))
        assertTrue(database.equipmentDao().getCharacterEquipment(1L).isEmpty())
    }

    @Test
    fun unequipComposesActiveStatusModifierWhenReducingStatsAndHp() = runTest {
        seedCharacter(currentGold = 500L, currentHp = 44)
        database.statusEffectDao().upsertStatusEffect(severeInjury())
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        val before = repository.observeStore(1L).first()
        val hpBefore = database.characterProfileDao().getCurrentState()!!.currentHp

        repository.unequipEquipment(1L, EquipmentSlot.CHEST)

        val after = repository.observeStore(1L).first()
        assertEquals(97, before.derivedStats.maxHp)
        assertEquals(88, after.derivedStats.maxHp)
        assertEquals(16, after.derivedStats.attack)
        assertEquals(
            CombatCalculator.preserveHpRatio(
                oldHp = hpBefore,
                oldMax = before.derivedStats.maxHp,
                newMax = after.derivedStats.maxHp,
                config = CharacterStatBalanceConfig(),
            ),
            database.characterProfileDao().getCurrentState()?.currentHp,
        )
    }

    @Test
    fun unequipKeepsZeroHpAtZero() = runTest {
        seedCharacter(currentGold = 100L, currentHp = 0)
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)

        repository.unequipEquipment(1L, EquipmentSlot.CHEST)

        assertEquals(0, database.characterProfileDao().getCurrentState()?.currentHp)
        assertTrue(database.equipmentDao().getCharacterEquipment(1L).isEmpty())
        assertEquals(listOf(chest.ownedEquipmentId), database.equipmentDao().getOwnedEquipment(1L).map { it.id })
    }

    @Test
    fun equipmentDeleteFailureLeavesSlotFallbackHpAndOwnershipUnchanged() = runTest {
        val sources = equippedChestSources()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_character_equipment_delete
            BEFORE DELETE ON character_equipment
            BEGIN
                SELECT RAISE(ABORT, 'forced equipment delete failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching { repository.unequipEquipment(1L, EquipmentSlot.CHEST) }

        assertTrue(failure.isFailure)
        assertUnequipSourcesUnchanged(sources)
    }

    @Test
    fun appearanceFallbackFailureRollsBackEquipmentDeleteHpAndOwnership() = runTest {
        val sources = equippedChestSources()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_character_equipped_items_update
            BEFORE INSERT ON character_equipped_items
            BEGIN
                SELECT RAISE(ABORT, 'forced appearance fallback failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching { repository.unequipEquipment(1L, EquipmentSlot.CHEST) }

        assertTrue(failure.isFailure)
        assertUnequipSourcesUnchanged(sources)
    }

    @Test
    fun currentStateFailureRollsBackUnequipAndAppearanceFallback() = runTest {
        val sources = equippedChestSources()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_unequip_character_state_update
            BEFORE INSERT ON character_current_state
            BEGIN
                SELECT RAISE(ABORT, 'forced unequip current state failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching { repository.unequipEquipment(1L, EquipmentSlot.CHEST) }

        assertTrue(failure.isFailure)
        assertUnequipSourcesUnchanged(sources)
    }

    private suspend fun assertPurchaseUnchanged(
        equipmentId: Long,
        expected: PurchaseEquipmentResult,
    ) {
        val goldBefore = database.characterProfileDao().getProfile()!!.currentGold
        val ownedBefore = database.equipmentDao().getOwnedEquipment(1L)

        assertEquals(expected, repository.purchaseEquipment(1L, equipmentId))

        assertEquals(goldBefore, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(ownedBefore, database.equipmentDao().getOwnedEquipment(1L))
    }

    private suspend fun purchase(equipmentId: Long): PurchaseEquipmentResult.Success =
        repository.purchaseEquipment(1L, equipmentId) as PurchaseEquipmentResult.Success

    private suspend fun equippedChestSources(): UnequipSources {
        seedCharacter(currentGold = 500L, currentHp = 55)
        val fallback = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
        )
        database.characterProfileDao().upsertEquippedItems(
            CharacterEquippedItemsEntity(
                characterId = 1L,
                headId = fallback.headId,
                topId = fallback.topId,
                bottomId = fallback.bottomId,
                shoesId = fallback.shoesId,
                accessoryId = fallback.accessoryId,
                weaponId = fallback.weaponId,
                glovesId = fallback.glovesId,
            ),
        )
        val chest = purchase(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        repository.equipOwnedEquipment(1L, chest.ownedEquipmentId, EquipmentSlot.CHEST)
        return UnequipSources(
            equipped = database.equipmentDao().getCharacterEquipment(1L),
            fallback = database.characterProfileDao().getEquippedItems(1L),
            currentState = database.characterProfileDao().getCurrentState(1L),
            owned = database.equipmentDao().getOwnedEquipment(1L),
        )
    }

    private suspend fun assertUnequipSourcesUnchanged(expected: UnequipSources) {
        assertEquals(expected.equipped, database.equipmentDao().getCharacterEquipment(1L))
        assertEquals(expected.fallback, database.characterProfileDao().getEquippedItems(1L))
        assertEquals(expected.currentState, database.characterProfileDao().getCurrentState(1L))
        assertEquals(expected.owned, database.equipmentDao().getOwnedEquipment(1L))
    }

    private suspend fun seedCharacter(
        characterId: Long = 1L,
        currentGold: Long,
        totalXp: Long = 0L,
        currentHp: Int = 110,
    ) {
        val level = minOf(50L, 1L + totalXp / 100L).toInt()
        database.characterProfileDao().insertProfile(
            CharacterProfileEntity(
                id = characterId,
                totalXp = totalXp,
                currentGold = currentGold,
                strength = 5,
                vitality = 5,
                focus = 5,
                willpower = 5,
                unspentStatPoints = 2 * (level - 1),
                hasUsedFreeStatReset = false,
            ),
        )
        database.characterProfileDao().insertCurrentState(
            CharacterCurrentStateEntity(
                characterId = characterId,
                currentHp = currentHp,
                balanceVersion = 1,
                updatedAtEpochMillis = 0L,
            ),
        )
    }

    private fun equipment(
        id: Long,
        type: String,
        slot: String,
        price: Long = 10L,
        layerKey: String? = null,
        weaponType: String? = null,
    ): EquipmentEntity = EquipmentEntity(
        id = id,
        nameKey = "equipment_name_test_$id",
        descriptionKey = "equipment_description_test_$id",
        type = type,
        slot = slot,
        rarity = EquipmentRarity.COMMON.name,
        price = price,
        requiredLevel = 1,
        imageKey = null,
        layerKey = layerKey,
        isForSale = true,
        weaponType = weaponType,
    )

    private fun severeInjury() = CharacterStatusEffectEntity(
        characterId = 1L,
        effectType = StatusEffectType.SEVERE_INJURY.name,
        definitionVersion = 1,
        appliedAtEpochMillis = clock.now().toEpochMilli(),
        expiresAtEpochMillis = clock.now().plusSeconds(24L * 60L * 60L).toEpochMilli(),
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1L,
        lastMutationId = "monster-attack:apply",
    )

    private fun setForSale(equipmentId: Long, isForSale: Boolean) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET isForSale = ? WHERE id = ?",
            arrayOf(if (isForSale) 1 else 0, equipmentId),
        )
    }

    private data class UnequipSources(
        val equipped: List<CharacterEquipmentEntity>,
        val fallback: CharacterEquippedItemsEntity?,
        val currentState: CharacterCurrentStateEntity?,
        val owned: List<OwnedEquipmentEntity>,
    )

    private class MutableClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        private val instant = Instant.parse("2026-07-22T02:00:00Z")

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }
}
