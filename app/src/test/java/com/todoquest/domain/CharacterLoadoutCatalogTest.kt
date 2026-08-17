package com.todoquest.domain

import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentUnequipAppearancePolicy
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.UnequipEquipmentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterLoadoutCatalogTest {
    @Test
    fun newWeaponIdsAreValid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
            CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
            CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
        ).forEach { weaponId ->
            assertTrue(CharacterLoadoutCatalog.contains(defaultItems.copy(weaponId = weaponId)))
        }
    }

    @Test
    fun unknownBlankAndWrongSlotWeaponIdsRemainInvalid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            "weapon_unknown",
            " ",
            CharacterLoadoutCatalog.TOP_ADVENTURE,
        ).forEach { invalidWeaponId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(weaponId = invalidWeaponId)))
        }
    }

    @Test
    fun newGlovesAndShoesIdsAreValid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            CharacterLoadoutCatalog.GLOVES_ADVENTURE,
            CharacterLoadoutCatalog.GLOVES_LEATHER,
            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
        ).forEach { glovesId ->
            assertTrue(CharacterLoadoutCatalog.contains(defaultItems.copy(glovesId = glovesId)))
        }
        listOf(
            CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
        ).forEach { shoesId ->
            assertTrue(CharacterLoadoutCatalog.contains(defaultItems.copy(shoesId = shoesId)))
        }
    }

    @Test
    fun unknownBlankAndWrongSlotGlovesShoesIdsRemainInvalid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            "gloves_unknown",
            " ",
            CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
        ).forEach { invalidGlovesId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(glovesId = invalidGlovesId)))
        }
        listOf(
            "shoes_unknown",
            " ",
            CharacterLoadoutCatalog.GLOVES_LEATHER,
        ).forEach { invalidShoesId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(shoesId = invalidShoesId)))
        }
    }

    @Test
    fun newTopAndBottomIdsAreValid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            CharacterLoadoutCatalog.TOP_CLOTH,
            CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
        ).forEach { topId ->
            assertTrue(CharacterLoadoutCatalog.contains(defaultItems.copy(topId = topId)))
        }
        listOf(
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
        ).forEach { bottomId ->
            assertTrue(CharacterLoadoutCatalog.contains(defaultItems.copy(bottomId = bottomId)))
        }
    }

    @Test
    fun unknownBlankAndWrongSlotTopBottomIdsRemainInvalid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            "top_unknown",
            " ",
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
        ).forEach { invalidTopId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(topId = invalidTopId)))
        }
        listOf(
            "bottom_unknown",
            " ",
            CharacterLoadoutCatalog.TOP_CLOTH,
        ).forEach { invalidBottomId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(bottomId = invalidBottomId)))
        }
    }

    @Test
    fun newHeadgearIdsAreValid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        assertTrue(
            CharacterLoadoutCatalog.contains(
                defaultItems.copy(headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT),
            ),
        )
        assertTrue(
            CharacterLoadoutCatalog.contains(
                defaultItems.copy(headId = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET),
            ),
        )
    }

    @Test
    fun unknownBlankAndWrongSlotHeadIdsRemainInvalid() {
        val defaultItems = CharacterLoadoutCatalog.defaultEquippedItems

        listOf(
            "headgear_unknown",
            " ",
            CharacterLoadoutCatalog.TOP_ADVENTURE,
        ).forEach { invalidHeadId ->
            assertFalse(CharacterLoadoutCatalog.contains(defaultItems.copy(headId = invalidHeadId)))
        }
    }

    @Test
    fun catalogIdsAndEmptyDefaultLoadoutFollowCurrentContract() {
        assertEquals("hair_default", CharacterLoadoutCatalog.HAIR_DEFAULT)
        assertEquals("headgear_adventure", CharacterLoadoutCatalog.HEADGEAR_ADVENTURE)
        assertEquals("top_default", CharacterLoadoutCatalog.TOP_DEFAULT)
        assertEquals("top_adventure", CharacterLoadoutCatalog.TOP_ADVENTURE)
        assertEquals("top_cloth", CharacterLoadoutCatalog.TOP_CLOTH)
        assertEquals("top_leather_armor", CharacterLoadoutCatalog.TOP_LEATHER_ARMOR)
        assertEquals("top_iron_breastplate", CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE)
        assertEquals("bottom_default", CharacterLoadoutCatalog.BOTTOM_DEFAULT)
        assertEquals("bottom_adventure", CharacterLoadoutCatalog.BOTTOM_ADVENTURE)
        assertEquals("bottom_cloth_pants", CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS)
        assertEquals("bottom_leather_pants", CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS)
        assertEquals("bottom_steel_greaves", CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES)
        assertEquals("gloves_adventure", CharacterLoadoutCatalog.GLOVES_ADVENTURE)
        assertEquals("shoes_default", CharacterLoadoutCatalog.SHOES_DEFAULT)
        assertEquals("shoes_adventure", CharacterLoadoutCatalog.SHOES_ADVENTURE)
        assertEquals("gloves_leather", CharacterLoadoutCatalog.GLOVES_LEATHER)
        assertEquals("gloves_steel_gauntlets", CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS)
        assertEquals("shoes_travelers_boots", CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS)
        assertEquals("shoes_windwalker_boots", CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS)
        assertEquals("accessory_adventure", CharacterLoadoutCatalog.ACCESSORY_ADVENTURE)
        assertEquals("weapon_default_sword", CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD)
        assertEquals("weapon_worn_sword", CharacterLoadoutCatalog.WEAPON_WORN_SWORD)
        assertEquals("weapon_iron_longsword", CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD)
        assertEquals("weapon_ash_spear", CharacterLoadoutCatalog.WEAPON_ASH_SPEAR)
        assertEquals("weapon_steel_mace", CharacterLoadoutCatalog.WEAPON_STEEL_MACE)
        assertEquals(
            CharacterAppearance(hairId = CharacterLoadoutCatalog.HAIR_DEFAULT),
            CharacterLoadoutCatalog.defaultAppearance,
        )
        assertEquals(
            EquippedItems(
                headId = null,
                topId = CharacterLoadoutCatalog.TOP_DEFAULT,
                bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT,
                shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT,
                accessoryId = null,
                weaponId = null,
                glovesId = null,
            ),
            CharacterLoadoutCatalog.defaultEquippedItems,
        )
    }

    @Test
    fun clearingEveryAdventureSlotConvergesToTheEmptyDefaultLoadout() {
        val adventureItems = EquippedItems(
            headId = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
            bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
            accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            glovesId = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
        )

        assertTrue(CharacterLoadoutCatalog.contains(adventureItems))
        assertEquals(
            CharacterLoadoutCatalog.defaultEquippedItems,
            EquipmentSlot.entries.fold(adventureItems) { items, slot ->
                EquipmentUnequipAppearancePolicy.clearSlot(items, slot)
            },
        )
    }

    @Test
    fun replacingHeadPreservesEveryOtherAppearanceField() {
        val appearance = CharacterLoadoutCatalog.defaultAppearance
        val original = CharacterLoadoutCatalog.defaultEquippedItems
        val replaced = original.copy(headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT)

        assertEquals(
            listOf(
                appearance.hairId,
                original.topId,
                original.bottomId,
                original.shoesId,
                original.accessoryId,
                original.weaponId,
            ),
            listOf(
                CharacterLoadoutCatalog.defaultAppearance.hairId,
                replaced.topId,
                replaced.bottomId,
                replaced.shoesId,
                replaced.accessoryId,
                replaced.weaponId,
            ),
        )
    }

    @Test
    fun replacingTopOrBottomPreservesEveryOtherLoadoutField() {
        val original = CharacterLoadoutCatalog.defaultEquippedItems
        val replacedTop = original.copy(topId = CharacterLoadoutCatalog.TOP_CLOTH)
        val replacedBottom = original.copy(bottomId = CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS)

        assertEquals(original, replacedTop.copy(topId = original.topId))
        assertEquals(original, replacedBottom.copy(bottomId = original.bottomId))
    }

    @Test
    fun replacingGlovesOrShoesPreservesEveryOtherLoadoutField() {
        val original = CharacterLoadoutCatalog.defaultEquippedItems
        val replacedGloves = original.copy(glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER)
        val replacedShoes = original.copy(shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS)

        assertEquals(original, replacedGloves.copy(glovesId = original.glovesId))
        assertEquals(original, replacedShoes.copy(shoesId = original.shoesId))
    }

    @Test
    fun replacingWeaponPreservesEveryOtherAppearanceField() {
        val original = CharacterLoadoutCatalog.defaultEquippedItems
        val replaced = original.copy(weaponId = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR)

        assertEquals(original, replaced.copy(weaponId = original.weaponId))
    }

    @Test
    fun unequipAppearancePolicyClearsEachCanonicalSlotAndPreservesEveryOtherField() {
        val original = EquippedItems(
            headId = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
            topId = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            bottomId = CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
            shoesId = CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
            accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            weaponId = CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
            glovesId = CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
        )
        val expectedBySlot = mapOf(
            EquipmentSlot.HELMET to original.copy(headId = null),
            EquipmentSlot.CHEST to original.copy(topId = CharacterLoadoutCatalog.TOP_DEFAULT),
            EquipmentSlot.LEGS to original.copy(bottomId = CharacterLoadoutCatalog.BOTTOM_DEFAULT),
            EquipmentSlot.GLOVES to original.copy(glovesId = null),
            EquipmentSlot.SHOES to original.copy(shoesId = CharacterLoadoutCatalog.SHOES_DEFAULT),
            EquipmentSlot.ACCESSORY to original.copy(accessoryId = null),
            EquipmentSlot.WEAPON to original.copy(weaponId = null),
        )

        assertEquals(EquipmentSlot.entries.toSet(), expectedBySlot.keys)
        expectedBySlot.forEach { (slot, expected) ->
            assertEquals(expected, EquipmentUnequipAppearancePolicy.clearSlot(original, slot))
        }
    }

    @Test
    fun unequipAppearancePolicyDoesNotMutateItsInput() {
        val original = EquippedItems(
            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            bottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            shoesId = CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
            accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            weaponId = CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
            glovesId = CharacterLoadoutCatalog.GLOVES_LEATHER,
        )
        val snapshot = original.copy()

        EquipmentSlot.entries.forEach { slot ->
            EquipmentUnequipAppearancePolicy.clearSlot(original, slot)
        }

        assertEquals(snapshot, original)
    }

    @Test
    fun unequipResultUsesPositiveIdentifiersAndCanonicalSlot() {
        val success = UnequipEquipmentResult.Success(
            ownedEquipmentId = 41L,
            equipmentId = 17L,
            slot = EquipmentSlot.WEAPON,
        )
        val alreadyEmpty = UnequipEquipmentResult.AlreadyEmpty(EquipmentSlot.ACCESSORY)

        assertEquals(41L, success.ownedEquipmentId)
        assertEquals(17L, success.equipmentId)
        assertEquals(EquipmentSlot.WEAPON, success.slot)
        assertEquals(EquipmentSlot.ACCESSORY, alreadyEmpty.slot)
        assertThrows(IllegalArgumentException::class.java) {
            UnequipEquipmentResult.Success(
                ownedEquipmentId = 0L,
                equipmentId = 17L,
                slot = EquipmentSlot.WEAPON,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UnequipEquipmentResult.Success(
                ownedEquipmentId = 41L,
                equipmentId = -1L,
                slot = EquipmentSlot.WEAPON,
            )
        }
    }
}
