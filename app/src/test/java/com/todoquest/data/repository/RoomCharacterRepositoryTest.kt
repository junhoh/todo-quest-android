package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterAppearanceEntity
import com.todoquest.data.local.CharacterEquipmentEntity
import com.todoquest.data.local.CharacterEquippedItemsEntity
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.local.RewardLedgerEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
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
class RoomCharacterRepositoryTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: RoomCharacterRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        clock = MutableClock()
        repository = RoomCharacterRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeCharacterCombinesSourceStateDerivedStatsXpProgressAndStreak() = runTest {
        seedCharacter(
            profile = profile(totalXp = 250, currentGold = 75, unspentStatPoints = 4),
            currentState = currentState(currentHp = 61),
        )
        listOf(
            LocalDate.of(2026, 7, 12),
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 14),
        ).forEachIndexed { index, date ->
            database.rewardLedgerDao().insert(
                ledger(
                    taskId = index + 1L,
                    occurrenceDate = date,
                    onTime = true,
                ),
            )
        }

        val snapshot = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()

        assertEquals(250, snapshot.character.totalXp)
        assertEquals(75, snapshot.character.currentGold)
        assertEquals(3, snapshot.level)
        assertEquals(50, snapshot.xpIntoCurrentLevel)
        assertEquals(100, snapshot.xpRequiredForNextLevel)
        assertEquals(61, snapshot.currentState.currentHp)
        assertEquals(122, snapshot.derivedStats.maxHp)
        assertEquals(3, snapshot.currentStreak)
        assertEquals(300, snapshot.momentumBonusBp)
    }

    @Test
    fun activeSevereInjuryAffectsSnapshotAndExpiryRestoresStatsWithoutHealingCurrentHp() = runTest {
        seedCharacter(
            profile = profile(),
            currentState = currentState(currentHp = 44),
        )
        database.statusEffectDao().upsertStatusEffect(severeInjury())

        val injured = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()
        assertEquals(88, injured.derivedStats.maxHp)
        assertEquals(16, injured.derivedStats.attack)
        assertEquals(44, injured.currentState.currentHp)

        clock.instant = Instant.parse("2026-07-15T09:00:00Z")
        val recovered = repository.observeCharacter(LocalDate.of(2026, 7, 15)).first()

        assertEquals(110, recovered.derivedStats.maxHp)
        assertEquals(20, recovered.derivedStats.attack)
        assertEquals(44, recovered.currentState.currentHp)
        assertFalse(
            database.statusEffectDao()
                .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
                .active,
        )
    }

    @Test
    fun ownedEquipmentWithoutEquippingKeepsEmptyModifierAndAppearanceFallback() = runTest {
        seedCharacter(
            profile = profile(),
            currentState = currentState(currentHp = 110),
        )
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        val baseline = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()

        ownEquipment(EquipmentCatalogSeeder.CLOTH_TOP_ID)

        val ownedOnly = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()
        assertEquals(baseline.derivedStats, ownedOnly.derivedStats)
        assertEquals(baseline.equippedItems, ownedOnly.equippedItems)
    }

    @Test
    fun equippedChestAndLegsCombineAndChestReplacementDropsPreviousModifier() = runTest {
        seedCharacter(
            profile = profile(),
            currentState = currentState(currentHp = 110),
        )
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        val clothChest = ownEquipment(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val leatherChest = ownEquipment(EquipmentCatalogSeeder.LEATHER_ARMOR_ID)
        val clothLegs = ownEquipment(EquipmentCatalogSeeder.CLOTH_PANTS_ID)

        equip(clothChest, EquipmentSlot.CHEST)
        equip(clothLegs, EquipmentSlot.LEGS)
        val combined = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()

        assertEquals(122, combined.derivedStats.maxHp)
        assertEquals(8, combined.derivedStats.hpRecovery)

        equip(leatherChest, EquipmentSlot.CHEST)
        val replaced = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()

        assertEquals(130, replaced.derivedStats.maxHp)
        assertEquals(13, replaced.derivedStats.defense)
        assertEquals(8, replaced.derivedStats.hpRecovery)
    }

    @Test
    fun statAllocationAndResetUseTheSameEquippedModifiersForOldAndNewStats() = runTest {
        seedCharacter(
            profile = profile(totalXp = 100, unspentStatPoints = 2),
            currentState = currentState(currentHp = 90),
        )
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        equip(
            ownEquipment(EquipmentCatalogSeeder.IRON_BREASTPLATE_ID),
            EquipmentSlot.CHEST,
        )

        assertEquals(
            241,
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).first().derivedStats.maxHp,
        )

        assertEquals(
            AllocateStatPointsResult.Success(
                StatAllocation(strength = 1, vitality = 1),
            ),
            repository.allocateStatPoints(
                StatAllocation(strength = 1, vitality = 1),
            ),
        )
        assertEquals(6, database.characterProfileDao().getProfile()?.strength)
        assertEquals(6, database.characterProfileDao().getProfile()?.vitality)
        assertEquals(0, database.characterProfileDao().getProfile()?.unspentStatPoints)
        assertEquals(94, database.characterProfileDao().getCurrentState()?.currentHp)

        assertEquals(StatResetResult.Success(goldSpent = 0), repository.resetStats())
        assertEquals(89, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            241,
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).first().derivedStats.maxHp,
        )
    }

    @Test
    fun onlyValidatedLayerIdProjectsOntoItsSupportedVisualSlot() = runTest {
        repository.resetStats()
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        val chest = ownEquipment(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        val gloves = ownEquipment(EquipmentCatalogSeeder.LEATHER_GLOVES_ID)
        equip(chest, EquipmentSlot.CHEST)

        setLayerKey(EquipmentCatalogSeeder.CLOTH_TOP_ID, "top_unknown")
        assertEquals(
            "top_default",
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).first().equippedItems.topId,
        )

        setLayerKey(EquipmentCatalogSeeder.CLOTH_TOP_ID, "top_default")
        assertEquals(
            "top_default",
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).first().equippedItems.topId,
        )

        setLayerKey(EquipmentCatalogSeeder.CLOTH_TOP_ID, null)
        setLayerKey(EquipmentCatalogSeeder.LEATHER_GLOVES_ID, "top_default")
        equip(gloves, EquipmentSlot.GLOVES)
        assertEquals(
            "top_default",
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).first().equippedItems.topId,
        )
    }

    @Test
    fun freshInitializationCreatesDefaultAppearanceAndEquippedItemsWithCharacterState() = runTest {
        repository.resetStats()

        assertEquals(profile(), database.characterProfileDao().getProfile())
        assertEquals(
            currentState(currentHp = 110).copy(updatedAtEpochMillis = 0L),
            database.characterProfileDao().getCurrentState(),
        )
        assertEquals(defaultAppearanceEntity(), database.characterProfileDao().getAppearance())
        assertEquals(defaultEquippedItemsEntity(), database.characterProfileDao().getEquippedItems())

        val snapshot = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()
        assertEquals(CharacterAppearance(hairId = "hair_default"), snapshot.appearance)
        assertEquals(defaultEquippedItems(), snapshot.equippedItems)
    }

    @Test
    fun validMixedLoadoutUpdatePersistsAndIsEmittedByCharacterFlow() = runTest {
        repository.resetStats()
        val mixedItems = EquippedItems(
            headId = null,
            topId = "top_default",
            bottomId = "bottom_adventure",
            shoesId = "shoes_default",
            accessoryId = "accessory_adventure",
            weaponId = null,
            glovesId = "gloves_leather",
        )

        assertSame(
            CharacterLoadoutUpdateResult.Success,
            repository.updateAppearance(CharacterAppearance(hairId = "hair_default")),
        )
        val emissions = mutableListOf<CharacterSnapshot>()
        val firstEmission = CompletableDeferred<Unit>()
        val collection = launch {
            repository.observeCharacter(LocalDate.of(2026, 7, 14)).take(2).collect { snapshot ->
                emissions += snapshot
                firstEmission.complete(Unit)
            }
        }
        firstEmission.await()

        assertSame(CharacterLoadoutUpdateResult.Success, repository.updateEquippedItems(mixedItems))
        collection.join()

        assertEquals(
            CharacterAppearanceEntity(characterId = 1L, hairId = "hair_default"),
            database.characterProfileDao().getAppearance(),
        )
        assertEquals(defaultEquippedItems(), emissions.first().equippedItems)
        assertEquals(mixedItems, emissions.last().equippedItems)
        assertEquals(
            "gloves_leather",
            database.characterProfileDao().getEquippedItems()?.glovesId,
        )
    }

    @Test
    fun weaponCanBeUnequippedAndEquippedAgain() = runTest {
        repository.resetStats()
        val unequipped = defaultEquippedItems().copy(weaponId = null)

        assertSame(CharacterLoadoutUpdateResult.Success, repository.updateEquippedItems(unequipped))
        assertEquals(null, database.characterProfileDao().getEquippedItems()?.weaponId)

        assertSame(
            CharacterLoadoutUpdateResult.Success,
            repository.updateEquippedItems(unequipped.copy(weaponId = "weapon_default_sword")),
        )
        assertEquals(
            "weapon_default_sword",
            database.characterProfileDao().getEquippedItems()?.weaponId,
        )
    }

    @Test
    fun invalidSlotIdDoesNotPartiallyWriteAnyLoadoutRow() = runTest {
        repository.resetStats()
        val appearanceBefore = database.characterProfileDao().getAppearance()
        val equippedBefore = database.characterProfileDao().getEquippedItems()

        assertSame(
            CharacterLoadoutUpdateResult.InvalidItem,
            repository.updateAppearance(CharacterAppearance(hairId = "hair_unknown")),
        )
        assertSame(
            CharacterLoadoutUpdateResult.InvalidItem,
            repository.updateEquippedItems(
                defaultEquippedItems().copy(
                    topId = "top_default",
                    bottomId = "bottom_unknown",
                ),
            ),
        )
        assertSame(
            CharacterLoadoutUpdateResult.InvalidItem,
            repository.updateEquippedItems(
                defaultEquippedItems().copy(glovesId = "gloves_unknown"),
            ),
        )

        assertEquals(appearanceBefore, database.characterProfileDao().getAppearance())
        assertEquals(equippedBefore, database.characterProfileDao().getEquippedItems())
    }

    @Test
    fun allocateStatPointsReportsNoChangesInsufficientPointsAndStatCapExplicitly() = runTest {
        seedCharacter(
            profile = profile(),
            currentState = currentState(currentHp = 110),
        )
        val initialProfile = database.characterProfileDao().getProfile()
        val initialState = database.characterProfileDao().getCurrentState()

        assertSame(
            AllocateStatPointsResult.NoChanges,
            repository.allocateStatPoints(StatAllocation()),
        )
        assertEquals(initialProfile, database.characterProfileDao().getProfile())
        assertEquals(initialState, database.characterProfileDao().getCurrentState())
        assertEquals(
            AllocateStatPointsResult.InsufficientPoints(requested = 1, available = 0),
            repository.allocateStatPoints(StatAllocation(strength = 1)),
        )
        assertEquals(initialProfile, database.characterProfileDao().getProfile())
        assertEquals(initialState, database.characterProfileDao().getCurrentState())

        seedCharacter(
            profile = profile(
                totalXp = 4_900,
                vitality = 60,
                unspentStatPoints = 43,
            ),
            currentState = currentState(currentHp = 454),
        )
        val cappedProfile = database.characterProfileDao().getProfile()
        val cappedState = database.characterProfileDao().getCurrentState()

        assertEquals(
            AllocateStatPointsResult.StatCap(StatType.VITALITY),
            repository.allocateStatPoints(
                StatAllocation(strength = 1, vitality = 1),
            ),
        )
        assertEquals(cappedProfile, database.characterProfileDao().getProfile())
        assertEquals(cappedState, database.characterProfileDao().getCurrentState())
    }

    @Test
    fun allocationRevalidatesLatestAvailablePointsBeforeWritingAnyStat() = runTest {
        seedCharacter(
            profile = profile(totalXp = 200, unspentStatPoints = 4),
            currentState = currentState(currentHp = 61),
        )
        val staleSnapshot = repository.observeCharacter(LocalDate.of(2026, 7, 14)).first()
        assertEquals(4, staleSnapshot.character.unspentStatPoints)

        val latestProfile = profile(
            totalXp = 200,
            strength = 8,
            unspentStatPoints = 1,
        )
        database.characterProfileDao().upsert(latestProfile)
        val stateBefore = database.characterProfileDao().getCurrentState()

        assertEquals(
            AllocateStatPointsResult.InsufficientPoints(requested = 2, available = 1),
            repository.allocateStatPoints(StatAllocation(strength = 1, vitality = 1)),
        )
        assertEquals(latestProfile, database.characterProfileDao().getProfile())
        assertEquals(stateBefore, database.characterProfileDao().getCurrentState())
    }

    @Test
    fun multiStatAllocationPersistsAllStatsAndPreservesHpRatioOnce() = runTest {
        seedCharacter(
            profile = profile(totalXp = 400, unspentStatPoints = 8),
            currentState = currentState(currentHp = 67),
        )
        val allocation = StatAllocation(
            strength = 2,
            vitality = 2,
            focus = 2,
            willpower = 2,
        )

        assertEquals(
            AllocateStatPointsResult.Success(allocation),
            repository.allocateStatPoints(allocation),
        )

        val storedProfile = database.characterProfileDao().getProfile()
        assertEquals(7, storedProfile?.strength)
        assertEquals(7, storedProfile?.vitality)
        assertEquals(7, storedProfile?.focus)
        assertEquals(7, storedProfile?.willpower)
        assertEquals(0, storedProfile?.unspentStatPoints)
        assertEquals(77, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun currentStateWriteFailureRollsBackEveryAllocatedStat() = runTest {
        seedCharacter(
            profile = profile(totalXp = 200, unspentStatPoints = 4),
            currentState = currentState(currentHp = 61),
        )
        val profileBefore = database.characterProfileDao().getProfile()
        val stateBefore = database.characterProfileDao().getCurrentState()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_stat_allocation_current_state
            BEFORE INSERT ON character_current_state
            BEGIN
                SELECT RAISE(ABORT, 'forced current state failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching {
            repository.allocateStatPoints(
                StatAllocation(strength = 1, vitality = 1, focus = 1, willpower = 1),
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(profileBefore, database.characterProfileDao().getProfile())
        assertEquals(stateBefore, database.characterProfileDao().getCurrentState())
    }

    @Test
    fun vitalityAllocationUpdatesProfileAndPreservesHpRatioInOneCommand() = runTest {
        seedCharacter(
            profile = profile(totalXp = 100, unspentStatPoints = 2),
            currentState = currentState(currentHp = 55),
        )

        val result = repository.allocateStatPoints(StatAllocation(vitality = 1))

        assertEquals(
            AllocateStatPointsResult.Success(StatAllocation(vitality = 1)),
            result,
        )
        assertEquals(6, database.characterProfileDao().getProfile()?.vitality)
        assertEquals(1, database.characterProfileDao().getProfile()?.unspentStatPoints)
        assertEquals(59, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun batchAllocationKeepsZeroHpAtZero() = runTest {
        seedCharacter(
            profile = profile(totalXp = 200, unspentStatPoints = 4),
            currentState = currentState(currentHp = 0),
        )

        repository.allocateStatPoints(
            StatAllocation(strength = 1, vitality = 2, focus = 1),
        )

        assertEquals(6, database.characterProfileDao().getProfile()?.strength)
        assertEquals(7, database.characterProfileDao().getProfile()?.vitality)
        assertEquals(6, database.characterProfileDao().getProfile()?.focus)
        assertEquals(0, database.characterProfileDao().getProfile()?.unspentStatPoints)
        assertEquals(0, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun resetStatsReportsNothingToResetWithoutConsumingFreeReset() = runTest {
        val result = repository.resetStats()

        assertSame(StatResetResult.NothingToReset, result)
        assertEquals(false, database.characterProfileDao().getProfile()?.hasUsedFreeStatReset)
    }

    @Test
    fun firstResetIsFreeReturnsPointsAndPreservesHpRatio() = runTest {
        seedCharacter(
            profile = profile(
                totalXp = 100,
                vitality = 7,
                unspentStatPoints = 0,
            ),
            currentState = currentState(currentHp = 58),
        )

        val result = repository.resetStats()

        assertEquals(StatResetResult.Success(goldSpent = 0), result)
        val storedProfile = database.characterProfileDao().getProfile()
        assertEquals(5, storedProfile?.vitality)
        assertEquals(2, storedProfile?.unspentStatPoints)
        assertEquals(true, storedProfile?.hasUsedFreeStatReset)
        assertEquals(49, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun laterResetChargesGoldAndInsufficientGoldDoesNotMutateState() = runTest {
        seedCharacter(
            profile = profile(
                totalXp = 100,
                currentGold = 139,
                vitality = 7,
                unspentStatPoints = 0,
                hasUsedFreeStatReset = true,
            ),
            currentState = currentState(currentHp = 58),
        )

        val insufficient = repository.resetStats()

        assertEquals(
            StatResetResult.InsufficientGold(requiredGold = 140, availableGold = 139),
            insufficient,
        )
        assertEquals(7, database.characterProfileDao().getProfile()?.vitality)
        assertEquals(139L, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(58, database.characterProfileDao().getCurrentState()?.currentHp)

        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(currentGold = 200),
        )

        val paid = repository.resetStats()

        assertEquals(StatResetResult.Success(goldSpent = 140), paid)
        assertEquals(60L, database.characterProfileDao().getProfile()?.currentGold)
        assertEquals(5, database.characterProfileDao().getProfile()?.vitality)
        assertEquals(49, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    private suspend fun seedCharacter(
        profile: CharacterProfileEntity,
        currentState: CharacterCurrentStateEntity,
    ) {
        database.characterProfileDao().upsert(profile)
        database.characterProfileDao().upsertCurrentState(currentState)
    }

    private suspend fun ownEquipment(equipmentId: Long): Long =
        database.equipmentDao().insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = equipmentId,
                acquiredAtEpochMillis = clock.now().toEpochMilli(),
            ),
        ).also { check(it != -1L) }

    private suspend fun equip(ownedEquipmentId: Long, slot: EquipmentSlot) {
        database.equipmentDao().upsertCharacterEquipment(
            CharacterEquipmentEntity(
                characterId = 1L,
                slot = slot.name,
                ownedEquipmentId = ownedEquipmentId,
            ),
        )
    }

    private fun setLayerKey(equipmentId: Long, layerKey: String?) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET layerKey = ? WHERE id = ?",
            arrayOf<Any?>(layerKey, equipmentId),
        )
    }

    private fun profile(
        totalXp: Long = 0,
        currentGold: Long = 0,
        strength: Int = 5,
        vitality: Int = 5,
        focus: Int = 5,
        willpower: Int = 5,
        unspentStatPoints: Int = 0,
        hasUsedFreeStatReset: Boolean = false,
    ) = CharacterProfileEntity(
        totalXp = totalXp,
        currentGold = currentGold,
        strength = strength,
        vitality = vitality,
        focus = focus,
        willpower = willpower,
        unspentStatPoints = unspentStatPoints,
        hasUsedFreeStatReset = hasUsedFreeStatReset,
    )

    private fun currentState(currentHp: Int) = CharacterCurrentStateEntity(
        characterId = 1L,
        currentHp = currentHp,
        balanceVersion = 1,
        updatedAtEpochMillis = clock.now().toEpochMilli(),
    )

    private fun defaultAppearanceEntity() = CharacterAppearanceEntity(
        characterId = 1L,
        hairId = "hair_default",
    )

    private fun defaultEquippedItemsEntity() = CharacterEquippedItemsEntity(
        characterId = 1L,
        headId = null,
        topId = "top_default",
        bottomId = "bottom_default",
        shoesId = "shoes_default",
        accessoryId = null,
        weaponId = null,
        glovesId = null,
    )

    private fun defaultEquippedItems() = EquippedItems(
        headId = null,
        topId = "top_default",
        bottomId = "bottom_default",
        shoesId = "shoes_default",
        accessoryId = null,
        weaponId = null,
    )

    private fun ledger(
        taskId: Long,
        occurrenceDate: LocalDate,
        onTime: Boolean,
    ) = RewardLedgerEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
        recurrenceSeriesId = taskId,
        xpAward = 20,
        goldAward = 10,
        rewardLocalDateEpochDay = occurrenceDate.toEpochDay(),
        onTime = onTime,
        onTimeMultiplierBp = if (onTime) 11_000 else 10_000,
        rewardEfficiencyBp = 10_000,
        repeatOrdinal = 1,
        dailyOrdinal = 1,
        goldGainBonusBp = 0,
        combatEligible = true,
        balanceVersion = CharacterStatBalanceConfig().version,
        awardedAtEpochMillis = clock.now().toEpochMilli(),
    )

    private fun severeInjury() = CharacterStatusEffectEntity(
        characterId = 1L,
        effectType = StatusEffectType.SEVERE_INJURY.name,
        definitionVersion = 1,
        appliedAtEpochMillis = Instant.parse("2026-07-14T09:00:00Z").toEpochMilli(),
        expiresAtEpochMillis = Instant.parse("2026-07-15T09:00:00Z").toEpochMilli(),
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1L,
        lastMutationId = "monster-attack:apply",
    )

    private class MutableClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        var instant: Instant = Instant.parse("2026-07-14T09:00:00Z")

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }
}
