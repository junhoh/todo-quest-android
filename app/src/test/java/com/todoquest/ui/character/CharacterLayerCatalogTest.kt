package com.todoquest.ui.character

import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.EquippedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterLayerCatalogTest {
    @Test
    fun fullyEquippedStateResolvesDefinitionsInSchemaV5TopmostWeaponZOrder() {
        val definitions = CharacterLayerCatalog.resolve(defaultState())

        assertEquals(
            listOf(
                CharacterLayerSlot.HAIR_BACK to 1,
                CharacterLayerSlot.BODY_BASE to 3,
                CharacterLayerSlot.SHOES to 4,
                CharacterLayerSlot.BOTTOM to 5,
                CharacterLayerSlot.TOP to 6,
                CharacterLayerSlot.HANDS_FRONT to 7,
                CharacterLayerSlot.HAIR_FRONT to 9,
                CharacterLayerSlot.HEADGEAR_FRONT to 10,
                CharacterLayerSlot.ACCESSORY_FRONT to 11,
                CharacterLayerSlot.WEAPON_BACK to 12,
                CharacterLayerSlot.WEAPON_HELD to 13,
                CharacterLayerSlot.WEAPON_FRONT to 14,
            ),
            definitions.map { it.slot to it.zIndex },
        )
        assertTrue(definitions.zipWithNext().all { (left, right) -> left.zIndex < right.zIndex })
    }

    @Test
    fun nullOptionalItemsOmitHeadgearAccessoryAndAllWeaponLayers() {
        val definitions = CharacterLayerCatalog.resolve(
            defaultState().copy(
                equippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    headId = null,
                    accessoryId = null,
                    weaponId = null,
                ),
            ),
        )

        assertEquals(
            listOf(
                CharacterLayerSlot.HAIR_BACK,
                CharacterLayerSlot.BODY_BASE,
                CharacterLayerSlot.SHOES,
                CharacterLayerSlot.BOTTOM,
                CharacterLayerSlot.TOP,
                CharacterLayerSlot.HANDS_FRONT,
                CharacterLayerSlot.HAIR_FRONT,
            ),
            definitions.map(CharacterLayerDefinition::slot),
        )
    }

    @Test
    fun everyLoadoutUsesOnlyCanonicalPathsCanvasAndAnchorProfile() {
        val allDefinitions = allLoadouts().flatMap(CharacterLayerCatalog::resolve)

        assertEquals(
            setOf(
                null,
                CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
                CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            ),
            allLoadouts().map { it.equippedItems.headId }.toSet(),
        )
        assertEquals(9_600, allLoadouts().size)
        assertEquals(CANONICAL_ASSET_PATHS, allDefinitions.map { it.assetPath }.toSet())
        assertTrue(allDefinitions.all { it.canvasWidth == 64 && it.canvasHeight == 64 })
        assertEquals(
            setOf(CharacterLayerCatalog.ANCHOR_PROFILE_ID),
            allDefinitions.map { it.anchorProfileId }.toSet(),
        )
        assertTrue(allDefinitions.all { it.assetPath.startsWith("character/layers/") })
        assertTrue(allDefinitions.all { it.zIndex == it.slot.ordinal })
    }

    @Test
    fun outfitItemIdsResolveOneSchemaV5LayerEach() {
        listOf(
            Triple(
                CharacterLoadoutCatalog.TOP_CLOTH,
                CharacterLayerSlot.TOP,
                "character/layers/top_cloth.png",
            ),
            Triple(
                CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
                CharacterLayerSlot.TOP,
                "character/layers/top_leather_armor.png",
            ),
            Triple(
                CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
                CharacterLayerSlot.TOP,
                "character/layers/top_iron_breastplate.png",
            ),
            Triple(
                CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
                CharacterLayerSlot.BOTTOM,
                "character/layers/bottom_cloth_pants.png",
            ),
            Triple(
                CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
                CharacterLayerSlot.BOTTOM,
                "character/layers/bottom_leather_pants.png",
            ),
            Triple(
                CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
                CharacterLayerSlot.BOTTOM,
                "character/layers/bottom_steel_greaves.png",
            ),
            Triple(
                CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
                CharacterLayerSlot.SHOES,
                "character/layers/shoes_travelers_boots.png",
            ),
            Triple(
                CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                CharacterLayerSlot.SHOES,
                "character/layers/shoes_windwalker_boots.png",
            ),
        ).forEach { (itemId, expectedSlot, expectedPath) ->
            val equippedItems = when (expectedSlot) {
                CharacterLayerSlot.TOP -> CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    topId = itemId,
                )
                CharacterLayerSlot.BOTTOM -> CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    bottomId = itemId,
                )
                CharacterLayerSlot.SHOES -> CharacterLoadoutCatalog.defaultEquippedItems.copy(
                    shoesId = itemId,
                )
                else -> error("outfit test only supports top, bottom, and shoes")
            }
            val definition = CharacterLayerCatalog.resolve(
                defaultState().copy(equippedItems = equippedItems),
            ).single { it.slot == expectedSlot }

            assertEquals(expectedPath, definition.assetPath)
            assertEquals(expectedSlot.ordinal, definition.zIndex)
            assertEquals(64, definition.canvasWidth)
            assertEquals(64, definition.canvasHeight)
            assertEquals(CharacterLayerCatalog.ANCHOR_PROFILE_ID, definition.anchorProfileId)
        }
    }

    @Test
    fun gloveIdsReplaceTheExistingHandsFrontDefinitionWithoutAddingAZOrderSlot() {
        assertEquals(15, CharacterLayerSlot.entries.size)

        val bareHands = CharacterLayerCatalog.resolve(
            defaultState().copy(
                equippedItems = defaultState().equippedItems.copy(glovesId = null),
            ),
        )
            .single { it.slot == CharacterLayerSlot.HANDS_FRONT }
        assertEquals("character/layers/hands_front.png", bareHands.assetPath)

        mapOf(
            CharacterLoadoutCatalog.GLOVES_ADVENTURE to
                "character/layers/gloves_adventure.png",
            CharacterLoadoutCatalog.GLOVES_LEATHER to
                "character/layers/gloves_leather.png",
            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS to
                "character/layers/gloves_steel_gauntlets.png",
        ).forEach { (glovesId, expectedPath) ->
            val definitions = CharacterLayerCatalog.resolve(
                defaultState().copy(
                    equippedItems = defaultState().equippedItems.copy(
                        glovesId = glovesId,
                    ),
                ),
            )
            val handsDefinition = definitions.single {
                it.slot == CharacterLayerSlot.HANDS_FRONT
            }

            assertEquals(expectedPath, handsDefinition.assetPath)
            assertEquals(CharacterLayerSlot.HANDS_FRONT.ordinal, handsDefinition.zIndex)
            assertEquals(
                listOf(
                    CharacterLayerSlot.HANDS_FRONT,
                    CharacterLayerSlot.WEAPON_HELD,
                    CharacterLayerSlot.WEAPON_FRONT,
                ),
                definitions.map(CharacterLayerDefinition::slot).filter {
                    it in setOf(
                        CharacterLayerSlot.WEAPON_HELD,
                        CharacterLayerSlot.HANDS_FRONT,
                        CharacterLayerSlot.WEAPON_FRONT,
                    )
                },
            )
        }
    }

    @Test
    fun helmetHeadIdsResolveOneSchemaV5HeadgearFrontLayerEach() {
        mapOf(
            CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT to
                "character/layers/headgear_leather_hat.png",
            CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET to
                "character/layers/headgear_iron_helmet.png",
        ).forEach { (headId, expectedPath) ->
            val headgearDefinitions = CharacterLayerCatalog.resolve(
                defaultState().copy(
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                        headId = headId,
                    ),
                ),
            ).filter { it.slot == CharacterLayerSlot.HEADGEAR_FRONT }

            assertEquals(1, headgearDefinitions.size)
            assertEquals(expectedPath, headgearDefinitions.single().assetPath)
            assertEquals(10, headgearDefinitions.single().zIndex)
            assertEquals(64, headgearDefinitions.single().canvasWidth)
            assertEquals(64, headgearDefinitions.single().canvasHeight)
            assertEquals(
                CharacterLayerCatalog.ANCHOR_PROFILE_ID,
                headgearDefinitions.single().anchorProfileId,
            )
        }
    }

    @Test
    fun gameplayWeaponIdsResolveExactlyOneTopmostWeaponFrontLayer() {
        mapOf(
            CharacterLoadoutCatalog.WEAPON_WORN_SWORD to
                "character/layers/weapon_worn_sword.png",
            CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD to
                "character/layers/weapon_iron_longsword.png",
            CharacterLoadoutCatalog.WEAPON_ASH_SPEAR to
                "character/layers/weapon_ash_spear.png",
            CharacterLoadoutCatalog.WEAPON_STEEL_MACE to
                "character/layers/weapon_steel_mace.png",
        ).forEach { (weaponId, expectedPath) ->
            val definitions = CharacterLayerCatalog.resolve(
                defaultState().copy(
                    equippedItems = defaultState().equippedItems.copy(weaponId = weaponId),
                ),
            )
            val weaponDefinitions = definitions.filter {
                it.slot in setOf(
                    CharacterLayerSlot.WEAPON_BACK,
                    CharacterLayerSlot.WEAPON_HELD,
                    CharacterLayerSlot.WEAPON_FRONT,
                )
            }

            assertEquals(1, weaponDefinitions.size)
            assertEquals(CharacterLayerSlot.WEAPON_FRONT, weaponDefinitions.single().slot)
            assertEquals(CharacterLayerSlot.entries.lastIndex, weaponDefinitions.single().zIndex)
            assertEquals(expectedPath, weaponDefinitions.single().assetPath)
        }
    }

    @Test
    fun unknownAppearanceOrItemCannotResolveRuntimeLayers() {
        assertThrows(IllegalArgumentException::class.java) {
            CharacterLayerCatalog.resolve(
                defaultState().copy(appearance = CharacterAppearance(hairId = "hair_unknown")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CharacterLayerCatalog.resolve(
                defaultState().copy(
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
                        topId = "top_unknown",
                    ),
                ),
            )
        }
    }

    private fun defaultState() = CharacterRenderState(
        appearance = CharacterLoadoutCatalog.defaultAppearance,
        equippedItems = EquippedItems(
            headId = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
            bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
            accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            glovesId = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
        ),
    )

    private fun allLoadouts(): List<CharacterRenderState> = buildList {
        listOf(
            CharacterLoadoutCatalog.TOP_DEFAULT,
            CharacterLoadoutCatalog.TOP_ADVENTURE,
            CharacterLoadoutCatalog.TOP_CLOTH,
            CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
        )
            .forEach { topId ->
                listOf(
                    CharacterLoadoutCatalog.BOTTOM_DEFAULT,
                    CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                    CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
                    CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
                    CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
                ).forEach { bottomId ->
                    listOf(
                        CharacterLoadoutCatalog.SHOES_DEFAULT,
                        CharacterLoadoutCatalog.SHOES_ADVENTURE,
                        CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
                        CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                    ).forEach { shoesId ->
                        listOf(
                            null,
                            CharacterLoadoutCatalog.GLOVES_ADVENTURE,
                            CharacterLoadoutCatalog.GLOVES_LEATHER,
                            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                        ).forEach { glovesId ->
                            listOf(
                                null,
                                CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
                                CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                                CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
                            ).forEach { headId ->
                                val accessoryId = if (headId == null) {
                                    null
                                } else {
                                    CharacterLoadoutCatalog.ACCESSORY_ADVENTURE
                                }
                                listOf(
                                    null,
                                    CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                                    CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
                                    CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
                                    CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
                                    CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
                                ).forEach { weaponId ->
                                    add(
                                        CharacterRenderState(
                                            appearance = CharacterAppearance(
                                                hairId = CharacterLoadoutCatalog.HAIR_DEFAULT,
                                            ),
                                            equippedItems = EquippedItems(
                                                headId = headId,
                                                topId = topId,
                                                bottomId = bottomId,
                                                shoesId = shoesId,
                                                accessoryId = accessoryId,
                                                weaponId = weaponId,
                                                glovesId = glovesId,
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    private companion object {
        val CANONICAL_ASSET_PATHS = setOf(
            "character/layers/accessory_adventure.png",
            "character/layers/body_base.png",
            "character/layers/bottom_adventure.png",
            "character/layers/bottom_cloth_pants.png",
            "character/layers/bottom_default.png",
            "character/layers/bottom_leather_pants.png",
            "character/layers/bottom_steel_greaves.png",
            "character/layers/hair_back_default.png",
            "character/layers/hair_front_default.png",
            "character/layers/hands_front.png",
            "character/layers/gloves_adventure.png",
            "character/layers/gloves_leather.png",
            "character/layers/gloves_steel_gauntlets.png",
            "character/layers/headgear_adventure.png",
            "character/layers/headgear_iron_helmet.png",
            "character/layers/headgear_leather_hat.png",
            "character/layers/shoes_adventure.png",
            "character/layers/shoes_default.png",
            "character/layers/shoes_travelers_boots.png",
            "character/layers/shoes_windwalker_boots.png",
            "character/layers/top_adventure.png",
            "character/layers/top_cloth.png",
            "character/layers/top_default.png",
            "character/layers/top_iron_breastplate.png",
            "character/layers/top_leather_armor.png",
            "character/layers/weapon_back_default_sword.png",
            "character/layers/weapon_front_default_sword.png",
            "character/layers/weapon_held_default_sword.png",
            "character/layers/weapon_worn_sword.png",
            "character/layers/weapon_iron_longsword.png",
            "character/layers/weapon_ash_spear.png",
            "character/layers/weapon_steel_mace.png",
        )
    }
}
