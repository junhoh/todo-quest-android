package com.todoquest.domain.model

data class CharacterAppearance(
    val hairId: String,
)

data class EquippedItems(
    val headId: String?,
    val topId: String,
    val bottomId: String,
    val shoesId: String,
    val accessoryId: String?,
    val weaponId: String?,
    val glovesId: String? = null,
)

sealed interface CharacterLoadoutUpdateResult {
    data object Success : CharacterLoadoutUpdateResult

    data object InvalidItem : CharacterLoadoutUpdateResult
}

/** Clears only the shared-renderer fallback for an explicitly unequipped gameplay slot. */
object EquipmentUnequipAppearancePolicy {
    fun clearSlot(current: EquippedItems, slot: EquipmentSlot): EquippedItems = when (slot) {
        EquipmentSlot.WEAPON -> current.copy(weaponId = null)
        EquipmentSlot.HELMET -> current.copy(headId = null)
        EquipmentSlot.CHEST -> current.copy(topId = CharacterLoadoutCatalog.TOP_DEFAULT)
        EquipmentSlot.LEGS -> current.copy(bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT)
        EquipmentSlot.GLOVES -> current.copy(glovesId = null)
        EquipmentSlot.SHOES -> current.copy(shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT)
        EquipmentSlot.ACCESSORY -> current.copy(accessoryId = null)
    }
}

object CharacterLoadoutCatalog {
    const val HAIR_DEFAULT = "hair_default"
    const val HEADGEAR_ADVENTURE = "headgear_adventure"
    const val HEADGEAR_LEATHER_HAT = "headgear_leather_hat"
    const val HEADGEAR_IRON_HELMET = "headgear_iron_helmet"
    const val TOP_DEFAULT = "top_default"
    const val TOP_ADVENTURE = "top_adventure"
    const val TOP_CLOTH = "top_cloth"
    const val TOP_LEATHER_ARMOR = "top_leather_armor"
    const val TOP_IRON_BREASTPLATE = "top_iron_breastplate"
    const val BOTTOM_DEFAULT = "bottom_default"
    const val BOTTOM_ADVENTURE = "bottom_adventure"
    const val BOTTOM_CLOTH_PANTS = "bottom_cloth_pants"
    const val BOTTOM_LEATHER_PANTS = "bottom_leather_pants"
    const val BOTTOM_STEEL_GREAVES = "bottom_steel_greaves"
    const val GLOVES_ADVENTURE = "gloves_adventure"
    const val GLOVES_LEATHER = "gloves_leather"
    const val GLOVES_STEEL_GAUNTLETS = "gloves_steel_gauntlets"
    const val SHOES_DEFAULT = "shoes_default"
    const val SHOES_ADVENTURE = "shoes_adventure"
    const val SHOES_TRAVELERS_BOOTS = "shoes_travelers_boots"
    const val SHOES_WINDWALKER_BOOTS = "shoes_windwalker_boots"
    const val ACCESSORY_ADVENTURE = "accessory_adventure"
    const val WEAPON_DEFAULT_SWORD = "weapon_default_sword"
    const val WEAPON_WORN_SWORD = "weapon_worn_sword"
    const val WEAPON_IRON_LONGSWORD = "weapon_iron_longsword"
    const val WEAPON_ASH_SPEAR = "weapon_ash_spear"
    const val WEAPON_STEEL_MACE = "weapon_steel_mace"

    private val headIds = setOf(
        null,
        HEADGEAR_ADVENTURE,
        HEADGEAR_LEATHER_HAT,
        HEADGEAR_IRON_HELMET,
    )
    private val topIds = setOf(
        TOP_DEFAULT,
        TOP_ADVENTURE,
        TOP_CLOTH,
        TOP_LEATHER_ARMOR,
        TOP_IRON_BREASTPLATE,
    )
    private val bottomIds = setOf(
        BOTTOM_DEFAULT,
        BOTTOM_ADVENTURE,
        BOTTOM_CLOTH_PANTS,
        BOTTOM_LEATHER_PANTS,
        BOTTOM_STEEL_GREAVES,
    )
    private val glovesIds = setOf(
        null,
        GLOVES_ADVENTURE,
        GLOVES_LEATHER,
        GLOVES_STEEL_GAUNTLETS,
    )
    private val shoesIds = setOf(
        SHOES_DEFAULT,
        SHOES_ADVENTURE,
        SHOES_TRAVELERS_BOOTS,
        SHOES_WINDWALKER_BOOTS,
    )
    private val accessoryIds = setOf(null, ACCESSORY_ADVENTURE)
    private val weaponIds = setOf(
        null,
        WEAPON_DEFAULT_SWORD,
        WEAPON_WORN_SWORD,
        WEAPON_IRON_LONGSWORD,
        WEAPON_ASH_SPEAR,
        WEAPON_STEEL_MACE,
    )

    val defaultAppearance = CharacterAppearance(hairId = HAIR_DEFAULT)

    val defaultEquippedItems = EquippedItems(
        headId = null,
        topId = TOP_DEFAULT,
        bottomId = BOTTOM_DEFAULT,
        shoesId = SHOES_DEFAULT,
        accessoryId = null,
        weaponId = null,
        glovesId = null,
    )

    fun contains(appearance: CharacterAppearance): Boolean = appearance.hairId == HAIR_DEFAULT

    fun contains(items: EquippedItems): Boolean =
        items.headId in headIds &&
            items.topId in topIds &&
            items.bottomId in bottomIds &&
            items.glovesId in glovesIds &&
            items.shoesId in shoesIds &&
            items.accessoryId in accessoryIds &&
            items.weaponId in weaponIds
}
