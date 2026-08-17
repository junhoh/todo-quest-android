package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
import com.todoquest.domain.model.toEquipmentSlot
import com.todoquest.domain.usecase.EquipmentModifierValidator
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY id")
    fun observeAllEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment ORDER BY id")
    suspend fun getAllEquipment(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment WHERE isForSale = 1 ORDER BY requiredLevel, price, id")
    fun observeForSaleEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :equipmentId")
    suspend fun getEquipment(equipmentId: Long): EquipmentEntity?

    @Query("SELECT * FROM equipment_modifiers ORDER BY equipmentId, sortOrder")
    fun observeAllEquipmentModifiers(): Flow<List<EquipmentModifierEntity>>

    @Query("SELECT * FROM equipment_modifiers ORDER BY equipmentId, sortOrder")
    suspend fun getAllEquipmentModifiers(): List<EquipmentModifierEntity>

    @Query(
        "SELECT * FROM equipment_modifiers " +
            "WHERE equipmentId = :equipmentId ORDER BY sortOrder",
    )
    suspend fun getEquipmentModifiers(equipmentId: Long): List<EquipmentModifierEntity>

    @Query("SELECT * FROM owned_equipment WHERE characterId = :characterId ORDER BY acquiredAtEpochMillis, id")
    fun observeOwnedEquipment(characterId: Long): Flow<List<OwnedEquipmentEntity>>

    @Query("SELECT * FROM owned_equipment WHERE characterId = :characterId ORDER BY acquiredAtEpochMillis, id")
    suspend fun getOwnedEquipment(characterId: Long): List<OwnedEquipmentEntity>

    @Query("SELECT * FROM owned_equipment WHERE id = :ownedEquipmentId")
    suspend fun getOwnedEquipmentById(ownedEquipmentId: Long): OwnedEquipmentEntity?

    @Query(
        "SELECT * FROM owned_equipment " +
            "WHERE characterId = :characterId AND equipmentId = :equipmentId",
    )
    suspend fun findOwnedEquipment(
        characterId: Long,
        equipmentId: Long,
    ): OwnedEquipmentEntity?

    @Query("SELECT * FROM character_equipment WHERE characterId = :characterId ORDER BY slot")
    fun observeCharacterEquipment(characterId: Long): Flow<List<CharacterEquipmentEntity>>

    @Query("SELECT * FROM character_equipment WHERE characterId = :characterId ORDER BY slot")
    suspend fun getCharacterEquipment(characterId: Long): List<CharacterEquipmentEntity>

    @Query(
        "SELECT * FROM character_equipment " +
            "WHERE characterId = :characterId AND slot = :slot",
    )
    suspend fun getCharacterEquipmentAtSlot(
        characterId: Long,
        slot: String,
    ): CharacterEquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipmentDefinitions(entities: List<EquipmentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipmentModifiers(entities: List<EquipmentModifierEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwnedEquipment(entity: OwnedEquipmentEntity): Long

    @Insert
    suspend fun insertCharacterEquipment(entity: CharacterEquipmentEntity)

    @Query(
        "UPDATE character_equipment SET ownedEquipmentId = :ownedEquipmentId " +
            "WHERE characterId = :characterId AND slot = :slot",
    )
    suspend fun updateCharacterEquipment(
        characterId: Long,
        slot: String,
        ownedEquipmentId: Long,
    ): Int

    @Query(
        "DELETE FROM character_equipment " +
            "WHERE characterId = :characterId AND slot = :slot",
    )
    suspend fun deleteCharacterEquipmentAtSlot(
        characterId: Long,
        slot: String,
    ): Int

    @Query("DELETE FROM equipment WHERE id = :equipmentId")
    suspend fun deleteEquipmentById(equipmentId: Long): Int

    @Query(
        "UPDATE equipment SET imageKey = :imageKey, layerKey = :layerKey " +
            "WHERE id = :equipmentId AND nameKey = :expectedNameKey " +
            "AND type = :expectedType AND slot = :expectedSlot",
    )
    suspend fun updateVisualMetadataIfCatalogIdentityMatches(
        equipmentId: Long,
        expectedNameKey: String,
        expectedType: String,
        expectedSlot: String,
        imageKey: String,
        layerKey: String,
    ): Int

    @Query(
        "UPDATE equipment SET price = :newPrice " +
            "WHERE id = :equipmentId AND nameKey = :expectedNameKey " +
            "AND type = :expectedType AND slot = :expectedSlot " +
            "AND price = :expectedOldPrice",
    )
    suspend fun updatePriceIfCatalogIdentityAndOldPriceMatch(
        equipmentId: Long,
        expectedNameKey: String,
        expectedType: String,
        expectedSlot: String,
        expectedOldPrice: Long,
        newPrice: Long,
    ): Int

    @Transaction
    suspend fun seedCatalogIgnoreAndApplyCanonicalUpdates(
        equipment: List<EquipmentEntity>,
        modifiers: List<EquipmentModifierEntity>,
        visualMetadata: List<EquipmentVisualMetadataUpdate>,
        priceUpdates: List<EquipmentPriceUpdate>,
    ) {
        insertEquipmentDefinitions(equipment)
        insertEquipmentModifiers(modifiers)
        visualMetadata.forEach { metadata ->
            updateVisualMetadataIfCatalogIdentityMatches(
                equipmentId = metadata.equipmentId,
                expectedNameKey = metadata.expectedNameKey,
                expectedType = metadata.expectedType,
                expectedSlot = metadata.expectedSlot,
                imageKey = metadata.imageKey,
                layerKey = metadata.layerKey,
            )
        }
        priceUpdates.forEach { metadata ->
            updatePriceIfCatalogIdentityAndOldPriceMatch(
                equipmentId = metadata.equipmentId,
                expectedNameKey = metadata.expectedNameKey,
                expectedType = metadata.expectedType,
                expectedSlot = metadata.expectedSlot,
                expectedOldPrice = metadata.expectedOldPrice,
                newPrice = metadata.newPrice,
            )
        }
    }

    @Transaction
    suspend fun upsertCharacterEquipment(entity: CharacterEquipmentEntity) {
        val updated = updateCharacterEquipment(
            characterId = entity.characterId,
            slot = entity.slot,
            ownedEquipmentId = entity.ownedEquipmentId,
        )
        if (updated == 0) insertCharacterEquipment(entity)
    }
}

data class EquipmentVisualMetadataUpdate(
    val equipmentId: Long,
    val expectedNameKey: String,
    val expectedType: String,
    val expectedSlot: String,
    val imageKey: String,
    val layerKey: String,
)

data class EquipmentPriceUpdate(
    val equipmentId: Long,
    val expectedNameKey: String,
    val expectedType: String,
    val expectedSlot: String,
    val expectedOldPrice: Long,
    val newPrice: Long,
)

object EquipmentCatalogSeeder {
    const val WORN_SWORD_ID = 1_001L
    const val IRON_LONGSWORD_ID = 1_002L
    const val LEATHER_HAT_ID = 1_003L
    const val IRON_HELMET_ID = 1_004L
    const val CLOTH_TOP_ID = 1_005L
    const val LEATHER_ARMOR_ID = 1_006L
    const val IRON_BREASTPLATE_ID = 1_007L
    const val CLOTH_PANTS_ID = 1_008L
    const val LEATHER_PANTS_ID = 1_009L
    const val STEEL_GREAVES_ID = 1_010L
    const val LEATHER_GLOVES_ID = 1_011L
    const val TRAVELERS_BOOTS_ID = 1_012L
    const val MAGE_RING_ID = 1_013L
    const val GUARDIAN_NECKLACE_ID = 1_014L
    const val STEEL_GAUNTLETS_ID = 1_015L
    const val WINDWALKER_BOOTS_ID = 1_016L
    const val ASH_SPEAR_ID = 1_017L
    const val STEEL_MACE_ID = 1_018L
    const val ADVENTURE_SWORD_ID = 1_019L
    const val ADVENTURE_HAT_ID = 1_020L
    const val ADVENTURE_JACKET_ID = 1_021L
    const val ADVENTURE_PANTS_ID = 1_022L
    const val ADVENTURE_GLOVES_ID = 1_023L
    const val ADVENTURE_SHOES_ID = 1_024L
    const val ADVENTURE_ACCESSORY_ID = 1_025L

    private data class CatalogEntry(
        val equipment: EquipmentEntity,
        val modifiers: List<EquipmentModifierEntity>,
        val domainModifiers: List<EquipmentStatModifier>,
    )

    private val legacyPrices = mapOf(
        WORN_SWORD_ID to 40L,
        IRON_LONGSWORD_ID to 720L,
        LEATHER_HAT_ID to 55L,
        IRON_HELMET_ID to 680L,
        CLOTH_TOP_ID to 45L,
        LEATHER_ARMOR_ID to 260L,
        IRON_BREASTPLATE_ID to 2_400L,
        CLOTH_PANTS_ID to 45L,
        LEATHER_PANTS_ID to 240L,
        STEEL_GREAVES_ID to 2_300L,
        LEATHER_GLOVES_ID to 280L,
        TRAVELERS_BOOTS_ID to 760L,
        MAGE_RING_ID to 2_700L,
        GUARDIAN_NECKLACE_ID to 6_800L,
        STEEL_GAUNTLETS_ID to 820L,
        WINDWALKER_BOOTS_ID to 860L,
        ASH_SPEAR_ID to 50L,
        STEEL_MACE_ID to 780L,
        ADVENTURE_SWORD_ID to 150L,
        ADVENTURE_HAT_ID to 100L,
        ADVENTURE_JACKET_ID to 120L,
        ADVENTURE_PANTS_ID to 110L,
        ADVENTURE_GLOVES_ID to 125L,
        ADVENTURE_SHOES_ID to 130L,
        ADVENTURE_ACCESSORY_ID to 160L,
    )

    private val catalog: List<CatalogEntry> = listOf(
        entry(
            id = WORN_SWORD_ID,
            key = "worn_sword",
            type = EquipmentType.WEAPON,
            rarity = EquipmentRarity.COMMON,
            price = 20L,
            requiredLevel = 1,
            imageKey = CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            layerKey = CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            weaponType = WeaponType.LONGSWORD,
            modifiers = listOf(
                derived(WORN_SWORD_ID, DerivedStatType.ATTACK, ModifierType.FLAT, 3),
            ),
        ),
        entry(
            id = IRON_LONGSWORD_ID,
            key = "iron_longsword",
            type = EquipmentType.WEAPON,
            rarity = EquipmentRarity.RARE,
            price = 360L,
            requiredLevel = 12,
            imageKey = CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            layerKey = CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            weaponType = WeaponType.LONGSWORD,
            modifiers = listOf(
                derived(IRON_LONGSWORD_ID, DerivedStatType.ATTACK, ModifierType.FLAT, 10),
                base(IRON_LONGSWORD_ID, StatType.STRENGTH, 3),
                derived(
                    IRON_LONGSWORD_ID,
                    DerivedStatType.ATTACK,
                    ModifierType.PERCENT_ADD,
                    800,
                ),
            ),
        ),
        entry(
            id = LEATHER_HAT_ID,
            key = "leather_hat",
            type = EquipmentType.HELMET,
            rarity = EquipmentRarity.COMMON,
            price = 27L,
            requiredLevel = 1,
            imageKey = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            layerKey = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            modifiers = listOf(
                derived(LEATHER_HAT_ID, DerivedStatType.MAX_HP, ModifierType.FLAT, 12),
            ),
        ),
        entry(
            id = IRON_HELMET_ID,
            key = "iron_helmet",
            type = EquipmentType.HELMET,
            rarity = EquipmentRarity.RARE,
            price = 340L,
            requiredLevel = 11,
            imageKey = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            layerKey = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            modifiers = listOf(
                derived(IRON_HELMET_ID, DerivedStatType.MAX_HP, ModifierType.FLAT, 30),
                derived(IRON_HELMET_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 6),
                base(IRON_HELMET_ID, StatType.FOCUS, 3),
            ),
        ),
        entry(
            id = CLOTH_TOP_ID,
            key = "cloth_top",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.COMMON,
            price = 22L,
            requiredLevel = 1,
            imageKey = CharacterLoadoutCatalog.TOP_CLOTH,
            layerKey = CharacterLoadoutCatalog.TOP_CLOTH,
            modifiers = listOf(
                derived(CLOTH_TOP_ID, DerivedStatType.MAX_HP, ModifierType.FLAT, 12),
            ),
        ),
        entry(
            id = LEATHER_ARMOR_ID,
            key = "leather_armor",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.UNCOMMON,
            price = 130L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            layerKey = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            modifiers = listOf(
                base(LEATHER_ARMOR_ID, StatType.VITALITY, 2),
                derived(LEATHER_ARMOR_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 3),
            ),
        ),
        entry(
            id = IRON_BREASTPLATE_ID,
            key = "iron_breastplate",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.EPIC,
            price = 1_200L,
            requiredLevel = 24,
            imageKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            layerKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            modifiers = listOf(
                derived(IRON_BREASTPLATE_ID, DerivedStatType.MAX_HP, ModifierType.FLAT, 50),
                derived(IRON_BREASTPLATE_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 9),
                base(IRON_BREASTPLATE_ID, StatType.VITALITY, 5),
                derived(
                    IRON_BREASTPLATE_ID,
                    DerivedStatType.MAX_HP,
                    ModifierType.PERCENT_ADD,
                    1_200,
                ),
            ),
        ),
        entry(
            id = CLOTH_PANTS_ID,
            key = "cloth_pants",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.COMMON,
            price = 22L,
            requiredLevel = 1,
            imageKey = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            layerKey = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            modifiers = listOf(
                derived(CLOTH_PANTS_ID, DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 1),
            ),
        ),
        entry(
            id = LEATHER_PANTS_ID,
            key = "leather_pants",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.UNCOMMON,
            price = 120L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            layerKey = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            modifiers = listOf(
                base(LEATHER_PANTS_ID, StatType.WILLPOWER, 2),
                derived(LEATHER_PANTS_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 3),
            ),
        ),
        entry(
            id = STEEL_GREAVES_ID,
            key = "steel_greaves",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.EPIC,
            price = 1_150L,
            requiredLevel = 23,
            imageKey = CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            layerKey = CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            modifiers = listOf(
                base(STEEL_GREAVES_ID, StatType.VITALITY, 5),
                base(STEEL_GREAVES_ID, StatType.WILLPOWER, 5),
                derived(STEEL_GREAVES_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 9),
                derived(
                    STEEL_GREAVES_ID,
                    DerivedStatType.HP_RECOVERY,
                    ModifierType.PERCENT_ADD,
                    1_200,
                ),
            ),
        ),
        entry(
            id = LEATHER_GLOVES_ID,
            key = "leather_gloves",
            type = EquipmentType.GLOVES,
            rarity = EquipmentRarity.UNCOMMON,
            price = 140L,
            requiredLevel = 6,
            imageKey = CharacterLoadoutCatalog.GLOVES_LEATHER,
            layerKey = CharacterLoadoutCatalog.GLOVES_LEATHER,
            modifiers = listOf(
                base(LEATHER_GLOVES_ID, StatType.STRENGTH, 2),
                derived(
                    LEATHER_GLOVES_ID,
                    DerivedStatType.CRITICAL_CHANCE,
                    ModifierType.FLAT,
                    200,
                ),
            ),
        ),
        entry(
            id = TRAVELERS_BOOTS_ID,
            key = "travelers_boots",
            type = EquipmentType.SHOES,
            rarity = EquipmentRarity.RARE,
            price = 380L,
            requiredLevel = 13,
            imageKey = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            layerKey = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            modifiers = listOf(
                base(TRAVELERS_BOOTS_ID, StatType.FOCUS, 3),
                derived(TRAVELERS_BOOTS_ID, DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 3),
                derived(
                    TRAVELERS_BOOTS_ID,
                    DerivedStatType.STATUS_RESISTANCE,
                    ModifierType.FLAT,
                    300,
                ),
            ),
        ),
        entry(
            id = MAGE_RING_ID,
            key = "mage_ring",
            type = EquipmentType.ACCESSORY,
            rarity = EquipmentRarity.EPIC,
            price = 1_350L,
            requiredLevel = 26,
            modifiers = listOf(
                base(MAGE_RING_ID, StatType.FOCUS, 5),
                derived(MAGE_RING_ID, DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 500),
                derived(MAGE_RING_ID, DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 600),
                derived(MAGE_RING_ID, DerivedStatType.GOLD_GAIN_BONUS, ModifierType.FLAT, 600),
            ),
        ),
        entry(
            id = GUARDIAN_NECKLACE_ID,
            key = "guardian_necklace",
            type = EquipmentType.ACCESSORY,
            rarity = EquipmentRarity.LEGENDARY,
            price = 3_400L,
            requiredLevel = 40,
            modifiers = listOf(
                base(GUARDIAN_NECKLACE_ID, StatType.WILLPOWER, 7),
                derived(
                    GUARDIAN_NECKLACE_ID,
                    DerivedStatType.CRITICAL_CHANCE,
                    ModifierType.FLAT,
                    800,
                ),
                derived(
                    GUARDIAN_NECKLACE_ID,
                    DerivedStatType.CRITICAL_DAMAGE,
                    ModifierType.FLAT,
                    900,
                ),
                derived(
                    GUARDIAN_NECKLACE_ID,
                    DerivedStatType.GOLD_GAIN_BONUS,
                    ModifierType.FLAT,
                    900,
                ),
            ),
        ),
        entry(
            id = STEEL_GAUNTLETS_ID,
            key = "steel_gauntlets",
            type = EquipmentType.GLOVES,
            rarity = EquipmentRarity.RARE,
            price = 410L,
            requiredLevel = 14,
            imageKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
            layerKey = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
            modifiers = listOf(
                base(STEEL_GAUNTLETS_ID, StatType.STRENGTH, 4),
                derived(
                    STEEL_GAUNTLETS_ID,
                    DerivedStatType.CRITICAL_CHANCE,
                    ModifierType.FLAT,
                    400,
                ),
                derived(
                    STEEL_GAUNTLETS_ID,
                    DerivedStatType.CRITICAL_DAMAGE,
                    ModifierType.FLAT,
                    400,
                ),
            ),
        ),
        entry(
            id = WINDWALKER_BOOTS_ID,
            key = "windwalker_boots",
            type = EquipmentType.SHOES,
            rarity = EquipmentRarity.RARE,
            price = 430L,
            requiredLevel = 15,
            imageKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            layerKey = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            modifiers = listOf(
                base(WINDWALKER_BOOTS_ID, StatType.FOCUS, 4),
                derived(
                    WINDWALKER_BOOTS_ID,
                    DerivedStatType.DEFENSE,
                    ModifierType.FLAT,
                    5,
                ),
                derived(
                    WINDWALKER_BOOTS_ID,
                    DerivedStatType.HP_RECOVERY,
                    ModifierType.PERCENT_ADD,
                    800,
                ),
            ),
        ),
        entry(
            id = ASH_SPEAR_ID,
            key = "ash_spear",
            type = EquipmentType.WEAPON,
            rarity = EquipmentRarity.COMMON,
            price = 25L,
            requiredLevel = 1,
            imageKey = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            layerKey = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            weaponType = WeaponType.SPEAR,
            modifiers = listOf(
                derived(ASH_SPEAR_ID, DerivedStatType.ATTACK, ModifierType.FLAT, 4),
            ),
        ),
        entry(
            id = STEEL_MACE_ID,
            key = "steel_mace",
            type = EquipmentType.WEAPON,
            rarity = EquipmentRarity.RARE,
            price = 390L,
            requiredLevel = 12,
            imageKey = CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            layerKey = CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            weaponType = WeaponType.BLUNT,
            modifiers = listOf(
                derived(STEEL_MACE_ID, DerivedStatType.ATTACK, ModifierType.FLAT, 12),
                base(STEEL_MACE_ID, StatType.STRENGTH, 4),
                derived(
                    STEEL_MACE_ID,
                    DerivedStatType.CRITICAL_DAMAGE,
                    ModifierType.FLAT,
                    400,
                ),
            ),
        ),
        entry(
            id = ADVENTURE_SWORD_ID,
            key = "adventure_sword",
            type = EquipmentType.WEAPON,
            rarity = EquipmentRarity.UNCOMMON,
            price = 150L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            layerKey = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            weaponType = WeaponType.LONGSWORD,
            modifiers = listOf(
                derived(ADVENTURE_SWORD_ID, DerivedStatType.ATTACK, ModifierType.FLAT, 5),
                base(ADVENTURE_SWORD_ID, StatType.STRENGTH, 1),
            ),
        ),
        entry(
            id = ADVENTURE_HAT_ID,
            key = "adventure_hat",
            type = EquipmentType.HELMET,
            rarity = EquipmentRarity.UNCOMMON,
            price = 100L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_HAT_ID, StatType.FOCUS, 1),
                derived(
                    ADVENTURE_HAT_ID,
                    DerivedStatType.STATUS_RESISTANCE,
                    ModifierType.FLAT,
                    150,
                ),
            ),
        ),
        entry(
            id = ADVENTURE_JACKET_ID,
            key = "adventure_jacket",
            type = EquipmentType.CHEST,
            rarity = EquipmentRarity.UNCOMMON,
            price = 120L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.TOP_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.TOP_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_JACKET_ID, StatType.VITALITY, 1),
                derived(ADVENTURE_JACKET_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 2),
            ),
        ),
        entry(
            id = ADVENTURE_PANTS_ID,
            key = "adventure_pants",
            type = EquipmentType.LEGS,
            rarity = EquipmentRarity.UNCOMMON,
            price = 110L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_PANTS_ID, StatType.WILLPOWER, 1),
                derived(ADVENTURE_PANTS_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 2),
            ),
        ),
        entry(
            id = ADVENTURE_GLOVES_ID,
            key = "adventure_gloves",
            type = EquipmentType.GLOVES,
            rarity = EquipmentRarity.UNCOMMON,
            price = 125L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_GLOVES_ID, StatType.STRENGTH, 1),
                derived(
                    ADVENTURE_GLOVES_ID,
                    DerivedStatType.CRITICAL_CHANCE,
                    ModifierType.FLAT,
                    150,
                ),
            ),
        ),
        entry(
            id = ADVENTURE_SHOES_ID,
            key = "adventure_shoes",
            type = EquipmentType.SHOES,
            rarity = EquipmentRarity.UNCOMMON,
            price = 130L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.SHOES_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.SHOES_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_SHOES_ID, StatType.FOCUS, 1),
                derived(ADVENTURE_SHOES_ID, DerivedStatType.DEFENSE, ModifierType.FLAT, 2),
            ),
        ),
        entry(
            id = ADVENTURE_ACCESSORY_ID,
            key = "adventure_accessory",
            type = EquipmentType.ACCESSORY,
            rarity = EquipmentRarity.UNCOMMON,
            price = 160L,
            requiredLevel = 5,
            imageKey = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            layerKey = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            modifiers = listOf(
                base(ADVENTURE_ACCESSORY_ID, StatType.WILLPOWER, 1),
                derived(
                    ADVENTURE_ACCESSORY_ID,
                    DerivedStatType.GOLD_GAIN_BONUS,
                    ModifierType.FLAT,
                    150,
                ),
            ),
        ),
    ).also { entries ->
        require(entries.map { it.equipment.id }.distinct().size == entries.size) {
            "equipment catalog ids must be unique"
        }
        val config = CharacterStatBalanceConfig()
        entries.forEach { entry ->
            val slot = EquipmentSlot.valueOf(entry.equipment.slot)
            val rarity = EquipmentRarity.valueOf(entry.equipment.rarity)
            EquipmentModifierValidator.validate(slot, rarity, entry.domainModifiers, config)
        }
        require(entries.map { it.equipment.id }.toSet() == legacyPrices.keys) {
            "legacy equipment prices must cover the canonical catalog"
        }
    }

    suspend fun seed(dao: EquipmentDao) {
        dao.seedCatalogIgnoreAndApplyCanonicalUpdates(
            equipment = catalog.map(CatalogEntry::equipment),
            modifiers = catalog.flatMap(CatalogEntry::modifiers),
            visualMetadata = catalog.mapNotNull { entry ->
                val imageKey = entry.equipment.imageKey ?: return@mapNotNull null
                val layerKey = entry.equipment.layerKey ?: return@mapNotNull null
                EquipmentVisualMetadataUpdate(
                    equipmentId = entry.equipment.id,
                    expectedNameKey = entry.equipment.nameKey,
                    expectedType = entry.equipment.type,
                    expectedSlot = entry.equipment.slot,
                    imageKey = imageKey,
                    layerKey = layerKey,
                )
            },
            priceUpdates = catalog.map { entry ->
                EquipmentPriceUpdate(
                    equipmentId = entry.equipment.id,
                    expectedNameKey = entry.equipment.nameKey,
                    expectedType = entry.equipment.type,
                    expectedSlot = entry.equipment.slot,
                    expectedOldPrice = legacyPrices.getValue(entry.equipment.id),
                    newPrice = entry.equipment.price,
                )
            },
        )
    }

    private fun entry(
        id: Long,
        key: String,
        type: EquipmentType,
        rarity: EquipmentRarity,
        price: Long,
        requiredLevel: Int,
        imageKey: String? = null,
        layerKey: String? = null,
        weaponType: WeaponType? = null,
        modifiers: List<EquipmentStatModifier>,
    ): CatalogEntry {
        val slot = type.toEquipmentSlot()
        require(modifiers.all { it.itemId == id })
        require((type == EquipmentType.WEAPON) == (weaponType != null))
        return CatalogEntry(
            equipment = EquipmentEntity(
                id = id,
                nameKey = "equipment_name_$key",
                descriptionKey = "equipment_description_$key",
                type = type.name,
                slot = slot.name,
                rarity = rarity.name,
                price = price,
                requiredLevel = requiredLevel,
                imageKey = imageKey,
                layerKey = layerKey,
                isForSale = true,
                weaponType = weaponType?.name,
            ),
            modifiers = modifiers.mapIndexed { index, modifier ->
                EquipmentModifierEntity(
                    equipmentId = id,
                    sortOrder = index,
                    targetKind = when (modifier.target) {
                        is StatTarget.Base -> EquipmentModifierTargetKind.BASE.name
                        is StatTarget.Derived -> EquipmentModifierTargetKind.DERIVED.name
                    },
                    targetStat = when (val target = modifier.target) {
                        is StatTarget.Base -> target.type.name
                        is StatTarget.Derived -> target.type.name
                    },
                    modifierType = modifier.type.name,
                    amount = modifier.amount,
                )
            },
            domainModifiers = modifiers,
        )
    }

    private fun base(
        itemId: Long,
        type: StatType,
        amount: Int,
    ): EquipmentStatModifier = EquipmentStatModifier(
        itemId = itemId,
        target = StatTarget.Base(type),
        type = ModifierType.FLAT,
        amount = amount,
    )

    private fun derived(
        itemId: Long,
        type: DerivedStatType,
        modifierType: ModifierType,
        amount: Int,
    ): EquipmentStatModifier = EquipmentStatModifier(
        itemId = itemId,
        target = StatTarget.Derived(type),
        type = modifierType,
        amount = amount,
    )
}
