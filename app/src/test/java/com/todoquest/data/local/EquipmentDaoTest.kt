package com.todoquest.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.usecase.EquipmentModifierValidator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EquipmentDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: EquipmentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.equipmentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun catalogSeederUsesStableIdsValidModifiersAndIsIdempotent() = runTest {
        EquipmentCatalogSeeder.seed(dao)
        val equipmentAfterFirstSeed = dao.getAllEquipment()
        val modifiersAfterFirstSeed = dao.getAllEquipmentModifiers()

        EquipmentCatalogSeeder.seed(dao)

        val equipment = dao.getAllEquipment()
        val modifiers = dao.getAllEquipmentModifiers()

        assertEquals(equipmentAfterFirstSeed, equipment)
        assertEquals(modifiersAfterFirstSeed, modifiers)
        assertEquals(25, equipment.size)
        assertEquals((1_001L..1_025L).toList(), equipment.map { it.id })
        assertEquals(expectedCatalogPrices, equipment.associate { it.id to it.price })
        assertEquals(
            setOf(
                "equipment_name_worn_sword",
                "equipment_name_iron_longsword",
                "equipment_name_leather_hat",
                "equipment_name_iron_helmet",
                "equipment_name_cloth_top",
                "equipment_name_leather_armor",
                "equipment_name_iron_breastplate",
                "equipment_name_cloth_pants",
                "equipment_name_leather_pants",
                "equipment_name_steel_greaves",
                "equipment_name_leather_gloves",
                "equipment_name_travelers_boots",
                "equipment_name_mage_ring",
                "equipment_name_guardian_necklace",
                "equipment_name_steel_gauntlets",
                "equipment_name_windwalker_boots",
                "equipment_name_ash_spear",
                "equipment_name_steel_mace",
                "equipment_name_adventure_sword",
                "equipment_name_adventure_hat",
                "equipment_name_adventure_jacket",
                "equipment_name_adventure_pants",
                "equipment_name_adventure_gloves",
                "equipment_name_adventure_shoes",
                "equipment_name_adventure_accessory",
            ),
            equipment.map { it.nameKey }.toSet(),
        )
        assertTrue(equipment.all { it.id in 1_001L..1_025L })
        assertTrue(equipment.all { it.isForSale })
        assertTrue(equipment.any { it.rarity == EquipmentRarity.EPIC.name })
        assertTrue(equipment.any { it.rarity == EquipmentRarity.LEGENDARY.name })
        assertEquals(
            setOf(
                EquipmentCatalogSeeder.LEATHER_HAT_ID,
                EquipmentCatalogSeeder.IRON_HELMET_ID,
                EquipmentCatalogSeeder.CLOTH_TOP_ID,
                EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
                EquipmentCatalogSeeder.IRON_BREASTPLATE_ID,
                EquipmentCatalogSeeder.CLOTH_PANTS_ID,
                EquipmentCatalogSeeder.LEATHER_PANTS_ID,
                EquipmentCatalogSeeder.STEEL_GREAVES_ID,
                EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
                EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID,
                EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                EquipmentCatalogSeeder.WORN_SWORD_ID,
                EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
                EquipmentCatalogSeeder.ASH_SPEAR_ID,
                EquipmentCatalogSeeder.STEEL_MACE_ID,
                EquipmentCatalogSeeder.ADVENTURE_SWORD_ID,
                EquipmentCatalogSeeder.ADVENTURE_HAT_ID,
                EquipmentCatalogSeeder.ADVENTURE_JACKET_ID,
                EquipmentCatalogSeeder.ADVENTURE_PANTS_ID,
                EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID,
                EquipmentCatalogSeeder.ADVENTURE_SHOES_ID,
                EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID,
            ),
            equipment
                .filter { it.imageKey != null || it.layerKey != null }
                .mapTo(mutableSetOf()) { it.id },
        )
        assertEquals(
            CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            dao.getEquipment(EquipmentCatalogSeeder.LEATHER_HAT_ID)?.imageKey,
        )
        assertEquals(
            CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            dao.getEquipment(EquipmentCatalogSeeder.LEATHER_HAT_ID)?.layerKey,
        )
        assertEquals(
            CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            dao.getEquipment(EquipmentCatalogSeeder.IRON_HELMET_ID)?.imageKey,
        )
        assertEquals(
            CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            dao.getEquipment(EquipmentCatalogSeeder.IRON_HELMET_ID)?.layerKey,
        )
        mapOf(
            EquipmentCatalogSeeder.CLOTH_TOP_ID to CharacterLoadoutCatalog.TOP_CLOTH,
            EquipmentCatalogSeeder.LEATHER_ARMOR_ID to CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            EquipmentCatalogSeeder.IRON_BREASTPLATE_ID to
                CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            EquipmentCatalogSeeder.CLOTH_PANTS_ID to CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            EquipmentCatalogSeeder.LEATHER_PANTS_ID to
                CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            EquipmentCatalogSeeder.STEEL_GREAVES_ID to
                CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            EquipmentCatalogSeeder.LEATHER_GLOVES_ID to
                CharacterLoadoutCatalog.GLOVES_LEATHER,
            EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID to
                CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID to
                CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
            EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID to
                CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            EquipmentCatalogSeeder.WORN_SWORD_ID to
                CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            EquipmentCatalogSeeder.IRON_LONGSWORD_ID to
                CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            EquipmentCatalogSeeder.ASH_SPEAR_ID to CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            EquipmentCatalogSeeder.STEEL_MACE_ID to CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to
                CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            EquipmentCatalogSeeder.ADVENTURE_HAT_ID to
                CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            EquipmentCatalogSeeder.ADVENTURE_JACKET_ID to
                CharacterLoadoutCatalog.TOP_ADVENTURE,
            EquipmentCatalogSeeder.ADVENTURE_PANTS_ID to
                CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID to
                CharacterLoadoutCatalog.GLOVES_ADVENTURE,
            EquipmentCatalogSeeder.ADVENTURE_SHOES_ID to
                CharacterLoadoutCatalog.SHOES_ADVENTURE,
            EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID to
                CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
        ).forEach { (equipmentId, expectedVisualKey) ->
            val definition = dao.getEquipment(equipmentId)!!
            assertEquals(expectedVisualKey, definition.imageKey)
            assertEquals(expectedVisualKey, definition.layerKey)
        }
        assertEquals(
            setOf(
                EquipmentCatalogSeeder.MAGE_RING_ID,
                EquipmentCatalogSeeder.GUARDIAN_NECKLACE_ID,
            ),
            equipment
                .filter { it.imageKey == null && it.layerKey == null }
                .mapTo(mutableSetOf()) { it.id },
        )
        assertEquals(
            EquipmentEntity(
                id = EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                nameKey = "equipment_name_steel_gauntlets",
                descriptionKey = "equipment_description_steel_gauntlets",
                type = EquipmentType.GLOVES.name,
                slot = EquipmentSlot.GLOVES.name,
                rarity = EquipmentRarity.RARE.name,
                price = 410L,
                requiredLevel = 14,
                imageKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                layerKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                isForSale = true,
            ),
            dao.getEquipment(EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID),
        )
        assertEquals(
            listOf(
                modifier(
                    EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                    0,
                    EquipmentModifierTargetKind.BASE,
                    StatType.STRENGTH,
                    ModifierType.FLAT,
                    4,
                ),
                modifier(
                    EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                    1,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.CRITICAL_CHANCE,
                    ModifierType.FLAT,
                    400,
                ),
                modifier(
                    EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                    2,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.CRITICAL_DAMAGE,
                    ModifierType.FLAT,
                    400,
                ),
            ),
            dao.getEquipmentModifiers(EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID),
        )
        assertEquals(
            EquipmentEntity(
                id = EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                nameKey = "equipment_name_windwalker_boots",
                descriptionKey = "equipment_description_windwalker_boots",
                type = EquipmentType.SHOES.name,
                slot = EquipmentSlot.SHOES.name,
                rarity = EquipmentRarity.RARE.name,
                price = 430L,
                requiredLevel = 15,
                imageKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                layerKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                isForSale = true,
            ),
            dao.getEquipment(EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID),
        )
        assertEquals(
            listOf(
                modifier(
                    EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                    0,
                    EquipmentModifierTargetKind.BASE,
                    StatType.FOCUS,
                    ModifierType.FLAT,
                    4,
                ),
                modifier(
                    EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                    1,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.DEFENSE,
                    ModifierType.FLAT,
                    5,
                ),
                modifier(
                    EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID,
                    2,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.HP_RECOVERY,
                    ModifierType.PERCENT_ADD,
                    800,
                ),
            ),
            dao.getEquipmentModifiers(EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID),
        )
        assertEquals(
            listOf(
                EquipmentCatalogSeeder.WORN_SWORD_ID to WeaponType.LONGSWORD.name,
                EquipmentCatalogSeeder.IRON_LONGSWORD_ID to WeaponType.LONGSWORD.name,
                EquipmentCatalogSeeder.ASH_SPEAR_ID to WeaponType.SPEAR.name,
                EquipmentCatalogSeeder.STEEL_MACE_ID to WeaponType.BLUNT.name,
                EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to WeaponType.LONGSWORD.name,
            ),
            equipment.filter { it.type == EquipmentType.WEAPON.name }
                .map { it.id to it.weaponType },
        )
        assertEquals(
            listOf(
                modifier(
                    EquipmentCatalogSeeder.ASH_SPEAR_ID,
                    0,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.ATTACK,
                    ModifierType.FLAT,
                    4,
                ),
            ),
            dao.getEquipmentModifiers(EquipmentCatalogSeeder.ASH_SPEAR_ID),
        )
        assertEquals(
            listOf(
                modifier(
                    EquipmentCatalogSeeder.STEEL_MACE_ID,
                    0,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.ATTACK,
                    ModifierType.FLAT,
                    12,
                ),
                modifier(
                    EquipmentCatalogSeeder.STEEL_MACE_ID,
                    1,
                    EquipmentModifierTargetKind.BASE,
                    StatType.STRENGTH,
                    ModifierType.FLAT,
                    4,
                ),
                modifier(
                    EquipmentCatalogSeeder.STEEL_MACE_ID,
                    2,
                    EquipmentModifierTargetKind.DERIVED,
                    DerivedStatType.CRITICAL_DAMAGE,
                    ModifierType.FLAT,
                    400,
                ),
            ),
            dao.getEquipmentModifiers(EquipmentCatalogSeeder.STEEL_MACE_ID),
        )
        assertEquals(
            EquipmentEntity(
                id = EquipmentCatalogSeeder.ASH_SPEAR_ID,
                nameKey = "equipment_name_ash_spear",
                descriptionKey = "equipment_description_ash_spear",
                type = EquipmentType.WEAPON.name,
                slot = EquipmentSlot.WEAPON.name,
                rarity = EquipmentRarity.COMMON.name,
                price = 25L,
                requiredLevel = 1,
                imageKey = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
                layerKey = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
                isForSale = true,
                weaponType = WeaponType.SPEAR.name,
            ),
            dao.getEquipment(EquipmentCatalogSeeder.ASH_SPEAR_ID),
        )
        assertEquals(
            EquipmentEntity(
                id = EquipmentCatalogSeeder.STEEL_MACE_ID,
                nameKey = "equipment_name_steel_mace",
                descriptionKey = "equipment_description_steel_mace",
                type = EquipmentType.WEAPON.name,
                slot = EquipmentSlot.WEAPON.name,
                rarity = EquipmentRarity.RARE.name,
                price = 390L,
                requiredLevel = 12,
                imageKey = CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
                layerKey = CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
                isForSale = true,
                weaponType = WeaponType.BLUNT.name,
            ),
            dao.getEquipment(EquipmentCatalogSeeder.STEEL_MACE_ID),
        )

        val config = CharacterStatBalanceConfig()
        equipment.forEach { definition ->
            val storedModifiers = modifiers.filter { it.equipmentId == definition.id }
            assertEquals(
                storedModifiers.indices.toList(),
                storedModifiers.map { it.sortOrder },
            )
            EquipmentModifierValidator.validate(
                EquipmentSlot.valueOf(definition.slot),
                EquipmentRarity.valueOf(definition.rarity),
                storedModifiers.map { it.toDomain() },
                config,
            )
        }
    }

    @Test
    fun catalogSeederAddsCompleteAdventureSetWithStableVisualsAndTwoValidModifiers() = runTest {
        EquipmentCatalogSeeder.seed(dao)

        val expected: LinkedHashMap<Long, AdventureEquipmentExpectation> = linkedMapOf(
            EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_sword",
                slot = EquipmentSlot.WEAPON,
                price = 150L,
                visualKey = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                weaponType = WeaponType.LONGSWORD,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_SWORD_ID,
                        0,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.ATTACK,
                        ModifierType.FLAT,
                        5,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_SWORD_ID,
                        1,
                        EquipmentModifierTargetKind.BASE,
                        StatType.STRENGTH,
                        ModifierType.FLAT,
                        1,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_HAT_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_hat",
                slot = EquipmentSlot.HELMET,
                price = 100L,
                visualKey = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_HAT_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.FOCUS,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_HAT_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.STATUS_RESISTANCE,
                        ModifierType.FLAT,
                        150,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_JACKET_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_jacket",
                slot = EquipmentSlot.CHEST,
                price = 120L,
                visualKey = CharacterLoadoutCatalog.TOP_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_JACKET_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.VITALITY,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_JACKET_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.DEFENSE,
                        ModifierType.FLAT,
                        2,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_PANTS_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_pants",
                slot = EquipmentSlot.LEGS,
                price = 110L,
                visualKey = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_PANTS_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.WILLPOWER,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_PANTS_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.DEFENSE,
                        ModifierType.FLAT,
                        2,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_gloves",
                slot = EquipmentSlot.GLOVES,
                price = 125L,
                visualKey = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.STRENGTH,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.CRITICAL_CHANCE,
                        ModifierType.FLAT,
                        150,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_SHOES_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_shoes",
                slot = EquipmentSlot.SHOES,
                price = 130L,
                visualKey = CharacterLoadoutCatalog.SHOES_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_SHOES_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.FOCUS,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_SHOES_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.DEFENSE,
                        ModifierType.FLAT,
                        2,
                    ),
                ),
            ),
            EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID to AdventureEquipmentExpectation(
                nameKey = "equipment_name_adventure_accessory",
                slot = EquipmentSlot.ACCESSORY,
                price = 160L,
                visualKey = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
                modifiers = listOf(
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID,
                        0,
                        EquipmentModifierTargetKind.BASE,
                        StatType.WILLPOWER,
                        ModifierType.FLAT,
                        1,
                    ),
                    modifier(
                        EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID,
                        1,
                        EquipmentModifierTargetKind.DERIVED,
                        DerivedStatType.GOLD_GAIN_BONUS,
                        ModifierType.FLAT,
                        150,
                    ),
                ),
            ),
        )

        assertEquals(7, expected.size)
        assertEquals(expected.keys, expected.keys.toSet())
        expected.forEach { (equipmentId, expectation) ->
            val definition = dao.getEquipment(equipmentId)!!
            assertEquals(expectation.nameKey, definition.nameKey)
            assertEquals(
                expectation.nameKey.replace("equipment_name_", "equipment_description_"),
                definition.descriptionKey,
            )
            assertEquals(expectation.slot.name, definition.type)
            assertEquals(expectation.slot.name, definition.slot)
            assertEquals(EquipmentRarity.UNCOMMON.name, definition.rarity)
            assertEquals(expectation.price, definition.price)
            assertEquals(5, definition.requiredLevel)
            assertEquals(expectation.visualKey, definition.imageKey)
            assertEquals(expectation.visualKey, definition.layerKey)
            assertEquals(expectation.weaponType?.name, definition.weaponType)
            assertTrue(definition.isForSale)
            assertEquals(expectation.modifiers, dao.getEquipmentModifiers(equipmentId))
            assertEquals(2, expectation.modifiers.size)
            EquipmentModifierValidator.validate(
                expectation.slot,
                EquipmentRarity.UNCOMMON,
                expectation.modifiers.map { it.toDomain() },
                CharacterStatBalanceConfig(),
            )
        }
    }

    @Test
    fun catalogSeederUpdatesOnlyCanonicalLegacyPricesAndPreservesRuntimeOwnership() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)
        val ownedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.IRON_LONGSWORD_ID,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.WEAPON.name, ownedId),
        )
        legacyCatalogPrices.forEach { (equipmentId, legacyPrice) ->
            database.openHelper.writableDatabase.execSQL(
                "UPDATE equipment SET price = ? WHERE id = ?",
                arrayOf(legacyPrice, equipmentId),
            )
        }
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = 321 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET price = ? WHERE id = ?",
            arrayOf(
                expectedCatalogPrices.getValue(EquipmentCatalogSeeder.LEATHER_HAT_ID),
                EquipmentCatalogSeeder.LEATHER_HAT_ID,
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET nameKey = 'equipment_name_damaged_cloth_top' WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.CLOTH_TOP_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET type = 'CHEST' WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.CLOTH_PANTS_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET slot = 'CHEST' WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.LEATHER_GLOVES_ID),
        )
        dao.insertEquipmentDefinitions(
            listOf(
                equipment(
                    id = 9_003L,
                    nameKey = "equipment_name_steel_greaves",
                    type = EquipmentType.LEGS.name,
                    slot = EquipmentSlot.LEGS.name,
                    price = legacyCatalogPrices.getValue(EquipmentCatalogSeeder.STEEL_GREAVES_ID),
                ),
            ),
        )
        val ownedBefore = dao.getOwnedEquipment(1L)
        val equippedBefore = dao.getCharacterEquipment(1L)
        val goldBefore = database.characterProfileDao().getProfile(1L)?.currentGold

        EquipmentCatalogSeeder.seed(dao)

        val expectedPrices = expectedCatalogPrices.toMutableMap().apply {
            this[EquipmentCatalogSeeder.WORN_SWORD_ID] = 321L
            this[EquipmentCatalogSeeder.CLOTH_TOP_ID] =
                legacyCatalogPrices.getValue(EquipmentCatalogSeeder.CLOTH_TOP_ID)
            this[EquipmentCatalogSeeder.CLOTH_PANTS_ID] =
                legacyCatalogPrices.getValue(EquipmentCatalogSeeder.CLOTH_PANTS_ID)
            this[EquipmentCatalogSeeder.LEATHER_GLOVES_ID] =
                legacyCatalogPrices.getValue(EquipmentCatalogSeeder.LEATHER_GLOVES_ID)
        }
        assertEquals(
            expectedPrices,
            dao.getAllEquipment()
                .filter { it.id in expectedCatalogPrices.keys }
                .associate { it.id to it.price },
        )
        assertEquals(
            legacyCatalogPrices.getValue(EquipmentCatalogSeeder.STEEL_GREAVES_ID),
            dao.getEquipment(9_003L)?.price,
        )
        assertEquals(ownedBefore, dao.getOwnedEquipment(1L))
        assertEquals(equippedBefore, dao.getCharacterEquipment(1L))
        assertEquals(goldBefore, database.characterProfileDao().getProfile(1L)?.currentGold)
    }

    @Test
    fun catalogSeederExtendsExistingSixteenItemsWithoutOverwritingRuntimeState() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)
        dao.deleteEquipmentById(EquipmentCatalogSeeder.ASH_SPEAR_ID)
        dao.deleteEquipmentById(EquipmentCatalogSeeder.STEEL_MACE_ID)
        (EquipmentCatalogSeeder.ADVENTURE_SWORD_ID..
            EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID).forEach { equipmentId ->
            dao.deleteEquipmentById(equipmentId)
        }
        val ownedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.WORN_SWORD_ID,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.WEAPON.name, ownedId),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET imageKey = NULL, layerKey = NULL, price = 321, " +
                "requiredLevel = 9, isForSale = 0 WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment_modifiers SET amount = 1 WHERE equipmentId = ? AND sortOrder = 0",
            arrayOf(EquipmentCatalogSeeder.WORN_SWORD_ID),
        )
        val modifiersBefore = dao.getAllEquipmentModifiers()
        val ownedBefore = dao.getOwnedEquipment(1L)
        val equippedBefore = dao.getCharacterEquipment(1L)

        EquipmentCatalogSeeder.seed(dao)

        val definitions = dao.getAllEquipment()
        val preserved = dao.getEquipment(EquipmentCatalogSeeder.WORN_SWORD_ID)!!
        assertEquals((1_001L..1_025L).toList(), definitions.map { it.id })
        assertEquals(321L, preserved.price)
        assertEquals(9, preserved.requiredLevel)
        assertFalse(preserved.isForSale)
        assertEquals(CharacterLoadoutCatalog.WEAPON_WORN_SWORD, preserved.imageKey)
        assertEquals(CharacterLoadoutCatalog.WEAPON_WORN_SWORD, preserved.layerKey)
        assertEquals(1, dao.getEquipmentModifiers(preserved.id).first().amount)
        assertEquals(
            modifiersBefore,
            dao.getAllEquipmentModifiers().filter {
                it.equipmentId < EquipmentCatalogSeeder.ASH_SPEAR_ID
            },
        )
        assertEquals(ownedBefore, dao.getOwnedEquipment(1L))
        assertEquals(equippedBefore, dao.getCharacterEquipment(1L))
        assertEquals(
            "equipment_name_ash_spear",
            dao.getEquipment(EquipmentCatalogSeeder.ASH_SPEAR_ID)?.nameKey,
        )
        assertEquals(
            "equipment_name_steel_mace",
            dao.getEquipment(EquipmentCatalogSeeder.STEEL_MACE_ID)?.nameKey,
        )
    }

    @Test
    fun catalogSeederBackfillsExistingOutfitVisualsWithoutChangingRuntimeState() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)
        val ownedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.CHEST.name, ownedId),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET imageKey = NULL, layerKey = NULL, price = 77, isForSale = 0 " +
                "WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.LEATHER_ARMOR_ID),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE equipment SET imageKey = NULL, layerKey = NULL WHERE id = ?",
            arrayOf(EquipmentCatalogSeeder.STEEL_GREAVES_ID),
        )
        val modifiersBefore = dao.getAllEquipmentModifiers()
        val ownedBefore = dao.getOwnedEquipment(1L)
        val equippedBefore = dao.getCharacterEquipment(1L)

        EquipmentCatalogSeeder.seed(dao)

        val leatherArmor = dao.getEquipment(EquipmentCatalogSeeder.LEATHER_ARMOR_ID)!!
        val steelGreaves = dao.getEquipment(EquipmentCatalogSeeder.STEEL_GREAVES_ID)!!
        assertEquals(CharacterLoadoutCatalog.TOP_LEATHER_ARMOR, leatherArmor.imageKey)
        assertEquals(CharacterLoadoutCatalog.TOP_LEATHER_ARMOR, leatherArmor.layerKey)
        assertEquals(CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES, steelGreaves.imageKey)
        assertEquals(CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES, steelGreaves.layerKey)
        assertEquals(77L, leatherArmor.price)
        assertFalse(leatherArmor.isForSale)
        assertEquals(25, dao.getAllEquipment().size)
        assertEquals(modifiersBefore, dao.getAllEquipmentModifiers())
        assertEquals(ownedBefore, dao.getOwnedEquipment(1L))
        assertEquals(equippedBefore, dao.getCharacterEquipment(1L))
    }

    @Test
    fun catalogSeederDoesNotPatchCustomOrMismatchedEquipmentRows() = runTest {
        dao.insertEquipmentDefinitions(
            listOf(
                equipment(
                    id = EquipmentCatalogSeeder.CLOTH_TOP_ID,
                    nameKey = "equipment_name_damaged_cloth_top",
                    type = EquipmentType.CHEST.name,
                    slot = EquipmentSlot.CHEST.name,
                ),
                equipment(
                    id = EquipmentCatalogSeeder.CLOTH_PANTS_ID,
                    nameKey = "equipment_name_cloth_pants",
                    type = EquipmentType.CHEST.name,
                    slot = EquipmentSlot.CHEST.name,
                ),
                equipment(
                    id = 9_003L,
                    nameKey = "equipment_name_steel_greaves",
                    type = EquipmentType.LEGS.name,
                    slot = EquipmentSlot.LEGS.name,
                ),
                equipment(
                    id = EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
                    nameKey = "equipment_name_damaged_leather_gloves",
                    type = EquipmentType.GLOVES.name,
                    slot = EquipmentSlot.GLOVES.name,
                ),
                equipment(
                    id = EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
                    nameKey = "equipment_name_damaged_steel_gauntlets",
                    type = EquipmentType.GLOVES.name,
                    slot = EquipmentSlot.GLOVES.name,
                ),
                equipment(
                    id = EquipmentCatalogSeeder.WORN_SWORD_ID,
                    nameKey = "equipment_name_damaged_worn_sword",
                    type = EquipmentType.WEAPON.name,
                    slot = EquipmentSlot.WEAPON.name,
                    weaponType = WeaponType.LONGSWORD.name,
                ),
            ),
        )

        EquipmentCatalogSeeder.seed(dao)

        listOf(
            EquipmentCatalogSeeder.CLOTH_TOP_ID,
            EquipmentCatalogSeeder.CLOTH_PANTS_ID,
            9_003L,
            EquipmentCatalogSeeder.LEATHER_GLOVES_ID,
            EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID,
            EquipmentCatalogSeeder.WORN_SWORD_ID,
        ).forEach { equipmentId ->
            val definition = dao.getEquipment(equipmentId)!!
            assertEquals(null, definition.imageKey)
            assertEquals(null, definition.layerKey)
        }
    }

    @Test
    fun equipmentModifiersKeepStableOrderAndCascadeWithDefinitionDeletion() = runTest {
        EquipmentCatalogSeeder.seed(dao)
        val equipmentId = 1_002L
        val beforeDelete = dao.getEquipmentModifiers(equipmentId)

        assertEquals(listOf(0, 1, 2), beforeDelete.map { it.sortOrder })
        assertEquals(1, dao.deleteEquipmentById(equipmentId))
        assertEquals(emptyList<EquipmentModifierEntity>(), dao.getEquipmentModifiers(equipmentId))
    }

    @Test
    fun weaponTypeRoundTripsAllKnownValuesAndKeepsNonWeaponsNull() = runTest {
        val weapons = WeaponType.entries.mapIndexed { index, weaponType ->
            equipment(
                id = 9_100L + index,
                nameKey = "equipment_name_${weaponType.name.lowercase()}",
                type = EquipmentType.WEAPON.name,
                slot = EquipmentSlot.WEAPON.name,
                weaponType = weaponType.name,
            )
        }
        val nonWeapon = equipment(
            id = 9_200L,
            nameKey = "equipment_name_non_weapon",
            type = EquipmentType.CHEST.name,
            slot = EquipmentSlot.CHEST.name,
            weaponType = null,
        )

        dao.insertEquipmentDefinitions(weapons + nonWeapon)

        assertEquals(
            WeaponType.entries.map { it.name },
            weapons.map { dao.getEquipment(it.id)?.weaponType },
        )
        assertEquals(null, dao.getEquipment(nonWeapon.id)?.weaponType)
    }

    @Test
    fun ownedEquipmentIsUniquePerCharacterAndRequiresExistingSources() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)

        val ownedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = 1_001L,
                acquiredAtEpochMillis = 1_000L,
            ),
        )

        assertTrue(ownedId > 0L)
        assertEquals(
            -1L,
            dao.insertOwnedEquipment(
                OwnedEquipmentEntity(
                    characterId = 1L,
                    equipmentId = 1_001L,
                    acquiredAtEpochMillis = 2_000L,
                ),
            ),
        )
        assertEquals(1, dao.getOwnedEquipment(1L).size)

        assertConstraintFailure {
            dao.insertOwnedEquipment(
                OwnedEquipmentEntity(
                    characterId = 1L,
                    equipmentId = Long.MAX_VALUE,
                    acquiredAtEpochMillis = 3_000L,
                ),
            )
        }
        assertConstraintFailure {
            dao.insertOwnedEquipment(
                OwnedEquipmentEntity(
                    characterId = 99L,
                    equipmentId = 1_002L,
                    acquiredAtEpochMillis = 4_000L,
                ),
            )
        }
    }

    @Test
    fun chestAndLegsStoreIndependentlyAndEquippedRowsRequireUniqueOwnedSources() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)
        val chestOwnedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = 1_006L,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        val legsOwnedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = 1_009L,
                acquiredAtEpochMillis = 2_000L,
            ),
        )

        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.CHEST.name, chestOwnedId),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.LEGS.name, legsOwnedId),
        )

        assertEquals(
            mapOf(
                EquipmentSlot.CHEST.name to chestOwnedId,
                EquipmentSlot.LEGS.name to legsOwnedId,
            ),
            dao.getCharacterEquipment(1L).associate { it.slot to it.ownedEquipmentId },
        )
        assertConstraintFailure {
            dao.upsertCharacterEquipment(
                CharacterEquipmentEntity(1L, EquipmentSlot.HELMET.name, chestOwnedId),
            )
        }
        assertConstraintFailure {
            dao.upsertCharacterEquipment(
                CharacterEquipmentEntity(1L, EquipmentSlot.ACCESSORY.name, Long.MAX_VALUE),
            )
        }
    }

    @Test
    fun deleteCharacterEquipmentAtSlotRemovesOnlyTargetAndReportsAffectedRows() = runTest {
        insertCharacter(characterId = 1L)
        EquipmentCatalogSeeder.seed(dao)
        val chestOwnedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.CLOTH_TOP_ID,
                acquiredAtEpochMillis = 1_000L,
            ),
        )
        val legsOwnedId = dao.insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = EquipmentCatalogSeeder.CLOTH_PANTS_ID,
                acquiredAtEpochMillis = 2_000L,
            ),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.CHEST.name, chestOwnedId),
        )
        dao.upsertCharacterEquipment(
            CharacterEquipmentEntity(1L, EquipmentSlot.LEGS.name, legsOwnedId),
        )

        assertEquals(
            1,
            dao.deleteCharacterEquipmentAtSlot(1L, EquipmentSlot.CHEST.name),
        )
        assertEquals(
            0,
            dao.deleteCharacterEquipmentAtSlot(1L, EquipmentSlot.CHEST.name),
        )
        assertEquals(
            listOf(CharacterEquipmentEntity(1L, EquipmentSlot.LEGS.name, legsOwnedId)),
            dao.getCharacterEquipment(1L),
        )
        assertEquals(
            setOf(chestOwnedId, legsOwnedId),
            dao.getOwnedEquipment(1L).mapTo(mutableSetOf()) { it.id },
        )
    }

    private suspend fun insertCharacter(characterId: Long) {
        database.characterProfileDao().insertProfile(
            CharacterProfileEntity(
                id = characterId,
                totalXp = 0L,
                currentGold = 10_000L,
                strength = 5,
                vitality = 5,
                focus = 5,
                willpower = 5,
                unspentStatPoints = 0,
                hasUsedFreeStatReset = false,
            ),
        )
    }

    private data class AdventureEquipmentExpectation(
        val nameKey: String,
        val slot: EquipmentSlot,
        val price: Long,
        val visualKey: String,
        val weaponType: WeaponType? = null,
        val modifiers: List<EquipmentModifierEntity>,
    )

    private suspend fun assertConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            Unit
        }
    }

    private fun equipment(
        id: Long,
        nameKey: String,
        type: String,
        slot: String,
        price: Long = 10L,
        weaponType: String? = null,
    ): EquipmentEntity = EquipmentEntity(
        id = id,
        nameKey = nameKey,
        descriptionKey = "equipment_description_test_$id",
        type = type,
        slot = slot,
        rarity = EquipmentRarity.COMMON.name,
        price = price,
        requiredLevel = 1,
        imageKey = null,
        layerKey = null,
        isForSale = true,
        weaponType = weaponType,
    )

    private val expectedCatalogPrices = linkedMapOf(
        EquipmentCatalogSeeder.WORN_SWORD_ID to 20L,
        EquipmentCatalogSeeder.IRON_LONGSWORD_ID to 360L,
        EquipmentCatalogSeeder.LEATHER_HAT_ID to 27L,
        EquipmentCatalogSeeder.IRON_HELMET_ID to 340L,
        EquipmentCatalogSeeder.CLOTH_TOP_ID to 22L,
        EquipmentCatalogSeeder.LEATHER_ARMOR_ID to 130L,
        EquipmentCatalogSeeder.IRON_BREASTPLATE_ID to 1_200L,
        EquipmentCatalogSeeder.CLOTH_PANTS_ID to 22L,
        EquipmentCatalogSeeder.LEATHER_PANTS_ID to 120L,
        EquipmentCatalogSeeder.STEEL_GREAVES_ID to 1_150L,
        EquipmentCatalogSeeder.LEATHER_GLOVES_ID to 140L,
        EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID to 380L,
        EquipmentCatalogSeeder.MAGE_RING_ID to 1_350L,
        EquipmentCatalogSeeder.GUARDIAN_NECKLACE_ID to 3_400L,
        EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID to 410L,
        EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID to 430L,
        EquipmentCatalogSeeder.ASH_SPEAR_ID to 25L,
        EquipmentCatalogSeeder.STEEL_MACE_ID to 390L,
        EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to 150L,
        EquipmentCatalogSeeder.ADVENTURE_HAT_ID to 100L,
        EquipmentCatalogSeeder.ADVENTURE_JACKET_ID to 120L,
        EquipmentCatalogSeeder.ADVENTURE_PANTS_ID to 110L,
        EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID to 125L,
        EquipmentCatalogSeeder.ADVENTURE_SHOES_ID to 130L,
        EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID to 160L,
    )

    private val legacyCatalogPrices = linkedMapOf(
        EquipmentCatalogSeeder.WORN_SWORD_ID to 40L,
        EquipmentCatalogSeeder.IRON_LONGSWORD_ID to 720L,
        EquipmentCatalogSeeder.LEATHER_HAT_ID to 55L,
        EquipmentCatalogSeeder.IRON_HELMET_ID to 680L,
        EquipmentCatalogSeeder.CLOTH_TOP_ID to 45L,
        EquipmentCatalogSeeder.LEATHER_ARMOR_ID to 260L,
        EquipmentCatalogSeeder.IRON_BREASTPLATE_ID to 2_400L,
        EquipmentCatalogSeeder.CLOTH_PANTS_ID to 45L,
        EquipmentCatalogSeeder.LEATHER_PANTS_ID to 240L,
        EquipmentCatalogSeeder.STEEL_GREAVES_ID to 2_300L,
        EquipmentCatalogSeeder.LEATHER_GLOVES_ID to 280L,
        EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID to 760L,
        EquipmentCatalogSeeder.MAGE_RING_ID to 2_700L,
        EquipmentCatalogSeeder.GUARDIAN_NECKLACE_ID to 6_800L,
        EquipmentCatalogSeeder.STEEL_GAUNTLETS_ID to 820L,
        EquipmentCatalogSeeder.WINDWALKER_BOOTS_ID to 860L,
        EquipmentCatalogSeeder.ASH_SPEAR_ID to 50L,
        EquipmentCatalogSeeder.STEEL_MACE_ID to 780L,
        EquipmentCatalogSeeder.ADVENTURE_SWORD_ID to 150L,
        EquipmentCatalogSeeder.ADVENTURE_HAT_ID to 100L,
        EquipmentCatalogSeeder.ADVENTURE_JACKET_ID to 120L,
        EquipmentCatalogSeeder.ADVENTURE_PANTS_ID to 110L,
        EquipmentCatalogSeeder.ADVENTURE_GLOVES_ID to 125L,
        EquipmentCatalogSeeder.ADVENTURE_SHOES_ID to 130L,
        EquipmentCatalogSeeder.ADVENTURE_ACCESSORY_ID to 160L,
    )

    private fun EquipmentModifierEntity.toDomain(): EquipmentStatModifier =
        EquipmentStatModifier(
            itemId = equipmentId,
            target = when (targetKind) {
                EquipmentModifierTargetKind.BASE.name ->
                    StatTarget.Base(StatType.valueOf(targetStat))

                EquipmentModifierTargetKind.DERIVED.name ->
                    StatTarget.Derived(DerivedStatType.valueOf(targetStat))

                else -> error("Unsupported target kind: $targetKind")
            },
            type = ModifierType.valueOf(modifierType),
            amount = amount,
        )

    private fun modifier(
        equipmentId: Long,
        sortOrder: Int,
        targetKind: EquipmentModifierTargetKind,
        targetStat: Enum<*>,
        modifierType: ModifierType,
        amount: Int,
    ): EquipmentModifierEntity = EquipmentModifierEntity(
        equipmentId = equipmentId,
        sortOrder = sortOrder,
        targetKind = targetKind.name,
        targetStat = targetStat.name,
        modifierType = modifierType.name,
        amount = amount,
    )
}
