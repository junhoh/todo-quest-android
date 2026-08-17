package com.todoquest.ui.character

import androidx.compose.runtime.Immutable
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.EquippedItems

enum class CharacterLayerSlot {
    ACCESSORY_BACK,
    HAIR_BACK,
    HEADGEAR_BACK,
    BODY_BASE,
    SHOES,
    BOTTOM,
    TOP,
    HANDS_FRONT,
    FACE_OVERLAY,
    HAIR_FRONT,
    HEADGEAR_FRONT,
    ACCESSORY_FRONT,
    WEAPON_BACK,
    WEAPON_HELD,
    WEAPON_FRONT,
}

data class CharacterLayerDefinition(
    val assetPath: String,
    val slot: CharacterLayerSlot,
    val zIndex: Int,
    val canvasWidth: Int = 64,
    val canvasHeight: Int = 64,
    val anchorProfileId: String,
) {
    init {
        require(assetPath.startsWith(ASSET_PATH_PREFIX) && assetPath.endsWith(".png")) {
            "character layer must use the runtime asset path contract"
        }
        require(zIndex == slot.ordinal) { "zIndex must match the schema v5 slot order" }
        require(canvasWidth == CANVAS_SIZE && canvasHeight == CANVAS_SIZE) {
            "character layers must share the 64x64 canvas"
        }
        require(anchorProfileId.isNotBlank()) { "anchorProfileId must not be blank" }
    }

    private companion object {
        const val ASSET_PATH_PREFIX = "character/layers/"
        const val CANVAS_SIZE = 64
    }
}

@Immutable
data class CharacterRenderState(
    val appearance: CharacterAppearance,
    val equippedItems: EquippedItems,
)

object CharacterLayerCatalog {
    const val ANCHOR_PROFILE_ID = "canvas-64-center-x-32-sole-y-58-schema-v5"

    fun resolve(state: CharacterRenderState): List<CharacterLayerDefinition> {
        require(CharacterLoadoutCatalog.contains(state.appearance)) {
            "unknown character appearance"
        }
        require(CharacterLoadoutCatalog.contains(state.equippedItems)) {
            "unknown equipped character item"
        }

        return buildList {
            addHairLayers(state.appearance)
            state.equippedItems.weaponId?.let { addWeaponLayers(it) }
            add(definition("body_base.png", CharacterLayerSlot.BODY_BASE))
            add(outfitDefinition(state.equippedItems.shoesId, CharacterLayerSlot.SHOES))
            add(outfitDefinition(state.equippedItems.bottomId, CharacterLayerSlot.BOTTOM))
            add(outfitDefinition(state.equippedItems.topId, CharacterLayerSlot.TOP))
            addHandsLayer(state.equippedItems.glovesId)
            state.equippedItems.headId?.let { addHeadgearLayer(it) }
            state.equippedItems.accessoryId?.let { addAccessoryLayer(it) }
        }.sortedBy(CharacterLayerDefinition::zIndex)
    }

    private fun MutableList<CharacterLayerDefinition>.addHairLayers(
        appearance: CharacterAppearance,
    ) {
        when (appearance.hairId) {
            CharacterLoadoutCatalog.HAIR_DEFAULT -> {
                add(definition("hair_back_default.png", CharacterLayerSlot.HAIR_BACK))
                add(definition("hair_front_default.png", CharacterLayerSlot.HAIR_FRONT))
            }

            else -> error("validated appearance has no runtime layers")
        }
    }

    private fun MutableList<CharacterLayerDefinition>.addWeaponLayers(weaponId: String) {
        when (weaponId) {
            CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD -> {
                add(
                    definition(
                        "weapon_back_default_sword.png",
                        CharacterLayerSlot.WEAPON_BACK,
                    ),
                )
                add(
                    definition(
                        "weapon_held_default_sword.png",
                        CharacterLayerSlot.WEAPON_HELD,
                    ),
                )
                add(
                    definition(
                        "weapon_front_default_sword.png",
                        CharacterLayerSlot.WEAPON_FRONT,
                    ),
                )
            }

            CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            -> add(definition("$weaponId.png", CharacterLayerSlot.WEAPON_FRONT))

            else -> error("validated weapon has no runtime layers")
        }
    }

    private fun MutableList<CharacterLayerDefinition>.addHeadgearLayer(headId: String) {
        when (headId) {
            CharacterLoadoutCatalog.HEADGEAR_ADVENTURE -> add(
                definition("headgear_adventure.png", CharacterLayerSlot.HEADGEAR_FRONT),
            )
            CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT -> add(
                definition("headgear_leather_hat.png", CharacterLayerSlot.HEADGEAR_FRONT),
            )
            CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET -> add(
                definition("headgear_iron_helmet.png", CharacterLayerSlot.HEADGEAR_FRONT),
            )

            else -> error("validated headgear has no runtime layer")
        }
    }

    private fun MutableList<CharacterLayerDefinition>.addAccessoryLayer(accessoryId: String) {
        when (accessoryId) {
            CharacterLoadoutCatalog.ACCESSORY_ADVENTURE -> add(
                definition("accessory_adventure.png", CharacterLayerSlot.ACCESSORY_FRONT),
            )

            else -> error("validated accessory has no runtime layer")
        }
    }

    private fun MutableList<CharacterLayerDefinition>.addHandsLayer(glovesId: String?) {
        val fileName = when (glovesId) {
            null -> "hands_front.png"
            CharacterLoadoutCatalog.GLOVES_ADVENTURE -> "gloves_adventure.png"
            CharacterLoadoutCatalog.GLOVES_LEATHER -> "gloves_leather.png"
            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS -> "gloves_steel_gauntlets.png"
            else -> error("validated gloves have no runtime layer")
        }
        add(definition(fileName, CharacterLayerSlot.HANDS_FRONT))
    }

    private fun outfitDefinition(
        itemId: String,
        slot: CharacterLayerSlot,
    ): CharacterLayerDefinition {
        val expectedPrefix = when (slot) {
            CharacterLayerSlot.TOP -> "top"
            CharacterLayerSlot.BOTTOM -> "bottom"
            CharacterLayerSlot.SHOES -> "shoes"
            else -> error("slot is not an outfit layer")
        }
        require(itemId.startsWith("${expectedPrefix}_")) { "item does not match its outfit slot" }
        return definition("$itemId.png", slot)
    }

    private fun definition(
        fileName: String,
        slot: CharacterLayerSlot,
    ) = CharacterLayerDefinition(
        assetPath = "character/layers/$fileName",
        slot = slot,
        zIndex = slot.ordinal,
        anchorProfileId = ANCHOR_PROFILE_ID,
    )
}
