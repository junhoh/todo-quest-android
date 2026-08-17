package com.todoquest.domain

import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentSlotMappingResult
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.PurchaseEligibility
import com.todoquest.domain.model.WeaponType
import com.todoquest.domain.model.toEquipmentSlot
import com.todoquest.domain.model.toEquipmentType
import com.todoquest.domain.usecase.EquipmentTypeSlotPolicy
import com.todoquest.domain.usecase.PurchaseEquipmentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEquipmentPolicyTest {
    @Test
    fun weaponTypesExposeFourStableValuesWithoutAddingEquipmentSlots() {
        assertEquals(
            listOf("LONGSWORD", "DAGGER", "SPEAR", "BLUNT"),
            WeaponType.entries.map { it.name },
        )
        assertEquals(7, EquipmentSlot.entries.size)
        assertEquals(listOf(EquipmentSlot.WEAPON), EquipmentSlot.entries.filter { it == EquipmentSlot.WEAPON })
    }

    @Test
    fun equipmentRequiresWeaponTypeOnlyForGameplayWeapons() {
        WeaponType.entries.forEach { weaponType ->
            assertEquals(weaponType, equipment(weaponType = weaponType).weaponType)
        }

        assertThrows(IllegalArgumentException::class.java) {
            equipment(weaponType = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            equipment(
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.CHEST,
                weaponType = WeaponType.LONGSWORD,
            )
        }
        assertEquals(
            null,
            equipment(
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.CHEST,
                weaponType = null,
            ).weaponType,
        )
    }

    @Test
    fun gameplayTypesAndSlotsHaveSevenTotalOneToOneMappings() {
        assertEquals(
            listOf("WEAPON", "HELMET", "CHEST", "LEGS", "GLOVES", "SHOES", "ACCESSORY"),
            EquipmentType.entries.map { it.name },
        )
        assertEquals(EquipmentType.entries.map { it.name }, EquipmentSlot.entries.map { it.name })

        EquipmentType.entries.forEach { type ->
            val slot = type.toEquipmentSlot()
            assertEquals(type.name, slot.name)
            assertEquals(type, slot.toEquipmentType())
            assertTrue(EquipmentTypeSlotPolicy.isCompatible(type, slot))
        }
        assertFalse(EquipmentTypeSlotPolicy.isCompatible(EquipmentType.CHEST, EquipmentSlot.LEGS))
    }

    @Test
    fun storageCompatibilityAliasesNormalizeAndUnknownValuesStayUnsupported() {
        val supported = mapOf(
            "WEAPON" to EquipmentSlot.WEAPON,
            "HEAD" to EquipmentSlot.HELMET,
            "HELMET" to EquipmentSlot.HELMET,
            "TOP" to EquipmentSlot.CHEST,
            "ARMOR" to EquipmentSlot.CHEST,
            "CHEST" to EquipmentSlot.CHEST,
            "BOTTOM" to EquipmentSlot.LEGS,
            "LEGS" to EquipmentSlot.LEGS,
            "GLOVES" to EquipmentSlot.GLOVES,
            "SHOES" to EquipmentSlot.SHOES,
            "ACCESSORY" to EquipmentSlot.ACCESSORY,
        )

        supported.forEach { (storageValue, expected) ->
            assertEquals(
                EquipmentSlotMappingResult.Supported(expected),
                EquipmentSlot.fromStorageValue(storageValue),
            )
        }
        assertEquals(
            EquipmentSlotMappingResult.Unsupported("PET"),
            EquipmentSlot.fromStorageValue("PET"),
        )
        assertEquals(
            EquipmentSlotMappingResult.Unsupported("UNKNOWN"),
            EquipmentSlot.fromStorageValue("UNKNOWN"),
        )
    }

    @Test
    fun purchaseDenialPriorityIsSlotSaleOwnershipLevelThenGold() {
        val mismatched = equipment(type = EquipmentType.CHEST, slot = EquipmentSlot.LEGS, isForSale = false)
        assertEquals(
            PurchaseEligibility.UnsupportedSlot(
                equipmentId = mismatched.id,
                type = EquipmentType.CHEST,
                slot = EquipmentSlot.LEGS,
            ),
            PurchaseEquipmentPolicy.evaluate(
                equipment = mismatched,
                characterLevel = 1,
                availableGold = 0,
                isOwned = true,
            ),
        )

        val stopped = equipment(isForSale = false)
        assertEquals(
            PurchaseEligibility.NotForSale(stopped.id),
            PurchaseEquipmentPolicy.evaluate(stopped, characterLevel = 1, availableGold = 0, isOwned = true),
        )

        val owned = equipment()
        assertEquals(
            PurchaseEligibility.AlreadyOwned(owned.id),
            PurchaseEquipmentPolicy.evaluate(owned, characterLevel = 1, availableGold = 0, isOwned = true),
        )

        val levelLocked = equipment(requiredLevel = 10)
        assertEquals(
            PurchaseEligibility.LevelTooLow(
                equipmentId = levelLocked.id,
                requiredLevel = 10,
                characterLevel = 9,
            ),
            PurchaseEquipmentPolicy.evaluate(levelLocked, characterLevel = 9, availableGold = 0, isOwned = false),
        )

        val expensive = equipment(price = 500)
        assertEquals(
            PurchaseEligibility.InsufficientGold(
                equipmentId = expensive.id,
                price = 500,
                availableGold = 499,
            ),
            PurchaseEquipmentPolicy.evaluate(expensive, characterLevel = 50, availableGold = 499, isOwned = false),
        )
        assertEquals(
            PurchaseEligibility.Eligible,
            PurchaseEquipmentPolicy.evaluate(expensive, characterLevel = 50, availableGold = 500, isOwned = false),
        )
    }

    private fun equipment(
        type: EquipmentType = EquipmentType.WEAPON,
        slot: EquipmentSlot = EquipmentSlot.WEAPON,
        weaponType: WeaponType? = if (type == EquipmentType.WEAPON) WeaponType.LONGSWORD else null,
        price: Long = 100,
        requiredLevel: Int = 1,
        isForSale: Boolean = true,
    ): Equipment = Equipment(
        id = 11,
        nameKey = "equipment_test_name",
        descriptionKey = "equipment_test_description",
        type = type,
        slot = slot,
        rarity = EquipmentRarity.COMMON,
        price = price,
        requiredLevel = requiredLevel,
        modifiers = emptyList(),
        imageKey = null,
        layerKey = null,
        isForSale = isForSale,
        weaponType = weaponType,
    )
}
