package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.usecase.EquipmentModifierValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class EquipmentModifierValidatorTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun eachSlotAcceptsItsDocumentedRoleAndRejectsForeignTargets() {
        EquipmentModifierValidator.validateModifier(EquipmentSlot.HELMET, EquipmentRarity.COMMON, derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 8), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.CHEST, EquipmentRarity.COMMON, derived(DerivedStatType.MAX_HP, ModifierType.PERCENT_ADD, 200), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.LEGS, EquipmentRarity.COMMON, derived(DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 1), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.GLOVES, EquipmentRarity.COMMON, base(StatType.STRENGTH, 1), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.SHOES, EquipmentRarity.COMMON, derived(DerivedStatType.STATUS_RESISTANCE, ModifierType.FLAT, 50), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.ACCESSORY, EquipmentRarity.COMMON, derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 50), config)
        EquipmentModifierValidator.validateModifier(EquipmentSlot.WEAPON, EquipmentRarity.COMMON, derived(DerivedStatType.ATTACK, ModifierType.FLAT, 2), config)

        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateModifier(
                EquipmentSlot.HELMET,
                EquipmentRarity.COMMON,
                derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 200),
                config,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateModifier(
                EquipmentSlot.GLOVES,
                EquipmentRarity.COMMON,
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 2),
                config,
            )
        }
    }

    @Test
    fun allSevenSlotsHaveRulesAndCatalogStyleAffixesValidate() {
        assertEquals(EquipmentSlot.entries.toSet(), config.equipmentSlotRules.keys)

        EquipmentModifierValidator.validate(
            EquipmentSlot.HELMET,
            EquipmentRarity.EPIC,
            listOf(
                derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 40),
                derived(DerivedStatType.DEFENSE, ModifierType.FLAT, 7),
                base(StatType.FOCUS, 3),
                derived(DerivedStatType.STATUS_RESISTANCE, ModifierType.FLAT, 400),
            ),
            config,
        )
        EquipmentModifierValidator.validate(
            EquipmentSlot.GLOVES,
            EquipmentRarity.EPIC,
            listOf(
                base(StatType.STRENGTH, 3),
                base(StatType.FOCUS, 4),
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 400),
                derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 500),
            ),
            config,
        )
    }

    @Test
    fun rareSteelGauntletsAndWindwalkerBootsUseThreeAllowedInRangeAffixes() {
        EquipmentModifierValidator.validate(
            EquipmentSlot.GLOVES,
            EquipmentRarity.RARE,
            listOf(
                base(StatType.STRENGTH, 4),
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 400),
                derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 400),
            ),
            config,
        )
        EquipmentModifierValidator.validate(
            EquipmentSlot.SHOES,
            EquipmentRarity.RARE,
            listOf(
                base(StatType.FOCUS, 4),
                derived(DerivedStatType.DEFENSE, ModifierType.FLAT, 5),
                derived(DerivedStatType.HP_RECOVERY, ModifierType.PERCENT_ADD, 800),
            ),
            config,
        )
    }

    @Test
    fun ashSpearAndSteelMaceUseAllowedWeaponRarityAffixes() {
        EquipmentModifierValidator.validate(
            EquipmentSlot.WEAPON,
            EquipmentRarity.COMMON,
            listOf(
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 4),
            ),
            config,
        )
        EquipmentModifierValidator.validate(
            EquipmentSlot.WEAPON,
            EquipmentRarity.RARE,
            listOf(
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 12),
                base(StatType.STRENGTH, 4),
                derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 400),
            ),
            config,
        )
    }

    @Test
    fun rarityControlsAffixCountAndPerAffixRange() {
        assertEquals(
            mapOf(
                EquipmentRarity.COMMON to 1,
                EquipmentRarity.UNCOMMON to 2,
                EquipmentRarity.RARE to 3,
                EquipmentRarity.EPIC to 4,
                EquipmentRarity.LEGENDARY to 4,
            ),
            config.equipmentRarityRules.mapValues { it.value.affixCount },
        )

        EquipmentModifierValidator.validate(
            EquipmentSlot.WEAPON,
            EquipmentRarity.COMMON,
            listOf(derived(DerivedStatType.ATTACK, ModifierType.FLAT, 3)),
            config,
        )
        EquipmentModifierValidator.validate(
            EquipmentSlot.ACCESSORY,
            EquipmentRarity.LEGENDARY,
            listOf(
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 700),
                derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 800),
                derived(DerivedStatType.GOLD_GAIN_BONUS, ModifierType.FLAT, 900),
                base(StatType.FOCUS, 4),
            ),
            config,
        )

        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validate(
                EquipmentSlot.WEAPON,
                EquipmentRarity.COMMON,
                listOf(
                    derived(DerivedStatType.ATTACK, ModifierType.FLAT, 3),
                    base(StatType.STRENGTH, 1),
                ),
                config,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateModifier(
                EquipmentSlot.WEAPON,
                EquipmentRarity.RARE,
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 13),
                config,
            )
        }
    }

    @Test
    fun targetAndModifierTypeCombinationsFollowFixedAndBpContracts() {
        EquipmentModifierValidator.validateTargetAndType(base(StatType.STRENGTH, 1))
        EquipmentModifierValidator.validateTargetAndType(derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 8))
        EquipmentModifierValidator.validateTargetAndType(derived(DerivedStatType.MAX_HP, ModifierType.PERCENT_ADD, 200))
        EquipmentModifierValidator.validateTargetAndType(derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 50))

        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateTargetAndType(
                EquipmentStatModifier(1, StatTarget.Base(StatType.STRENGTH), ModifierType.PERCENT_ADD, 200),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateTargetAndType(
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.PERCENT_ADD, 200),
            )
        }
    }

    @Test
    fun chestAndLegsHaveIndependentSurvivalRules() {
        assertEquals(
            setOf(DerivedStatType.MAX_HP, DerivedStatType.DEFENSE),
            config.equipmentSlotRules.getValue(EquipmentSlot.CHEST).allowedFlatDerivedStats,
        )
        assertEquals(
            setOf(DerivedStatType.DEFENSE, DerivedStatType.HP_RECOVERY),
            config.equipmentSlotRules.getValue(EquipmentSlot.LEGS).allowedFlatDerivedStats,
        )
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateModifier(
                EquipmentSlot.CHEST,
                EquipmentRarity.COMMON,
                derived(DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 1),
                config,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentModifierValidator.validateModifier(
                EquipmentSlot.LEGS,
                EquipmentRarity.COMMON,
                derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 8),
                config,
            )
        }
    }

    @Test
    fun storeSnapshotKeepsTheOwnedLookupOptionalForExistingFixtures() {
        val equipment = equipment(id = 101L)

        val snapshot = storeSnapshot(
            equipment = listOf(equipment),
            ownedEquipmentIds = setOf(equipment.id),
        )

        assertEquals(emptyMap<Long, OwnedEquipment>(), snapshot.ownedEquipmentByEquipmentId)
        assertEquals(setOf(equipment.id), snapshot.ownedEquipmentIds)
    }

    @Test
    fun storeSnapshotOwnedLookupUsesEquipmentIdsAndPreservesOwnedRowIds() {
        val equipment = equipment(id = 101L)
        val owned = OwnedEquipment(
            id = 9_001L,
            characterId = 7L,
            equipment = equipment,
            acquiredAtEpochMillis = 123L,
        )

        val snapshot = storeSnapshot(
            characterId = 7L,
            equipment = listOf(equipment),
            ownedEquipmentIds = setOf(equipment.id),
            ownedEquipmentByEquipmentId = mapOf(equipment.id to owned),
            equippedBySlot = mapOf(
                EquipmentSlot.HELMET to EquippedEquipment(
                    characterId = 7L,
                    slot = EquipmentSlot.HELMET,
                    ownedEquipment = owned,
                ),
            ),
        )

        assertEquals(owned, snapshot.ownedEquipmentByEquipmentId[equipment.id])
        assertFalse(snapshot.ownedEquipmentByEquipmentId.containsKey(owned.id))
        assertEquals(
            owned.id,
            snapshot.equippedBySlot.getValue(EquipmentSlot.HELMET).ownedEquipment.id,
        )
    }

    @Test
    fun storeSnapshotRejectsOwnedLookupWithWrongKeyCharacterOrEquipment() {
        val equipment = equipment(id = 101L)
        val owned = OwnedEquipment(
            id = 9_001L,
            characterId = 7L,
            equipment = equipment,
            acquiredAtEpochMillis = 123L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            storeSnapshot(
                characterId = 7L,
                equipment = listOf(equipment),
                ownedEquipmentIds = setOf(equipment.id),
                ownedEquipmentByEquipmentId = mapOf(owned.id to owned),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            storeSnapshot(
                characterId = 8L,
                equipment = listOf(equipment),
                ownedEquipmentIds = setOf(equipment.id),
                ownedEquipmentByEquipmentId = mapOf(equipment.id to owned),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            storeSnapshot(
                characterId = 7L,
                equipment = listOf(equipment.copy(nameKey = "catalog_helmet")),
                ownedEquipmentIds = setOf(equipment.id),
                ownedEquipmentByEquipmentId = mapOf(equipment.id to owned),
            )
        }
    }

    private fun base(type: StatType, amount: Int): EquipmentStatModifier =
        EquipmentStatModifier(1, StatTarget.Base(type), ModifierType.FLAT, amount)

    private fun derived(
        type: DerivedStatType,
        modifierType: ModifierType,
        amount: Int,
    ): EquipmentStatModifier = EquipmentStatModifier(
        1,
        StatTarget.Derived(type),
        modifierType,
        amount,
    )

    private fun equipment(id: Long): Equipment = Equipment(
        id = id,
        nameKey = "helmet_$id",
        descriptionKey = "helmet_${id}_description",
        type = EquipmentType.HELMET,
        slot = EquipmentSlot.HELMET,
        rarity = EquipmentRarity.COMMON,
        price = 10L,
        requiredLevel = 1,
        modifiers = emptyList(),
        isForSale = true,
    )

    private fun storeSnapshot(
        characterId: Long = 1L,
        equipment: List<Equipment> = emptyList(),
        ownedEquipmentIds: Set<Long> = emptySet(),
        ownedEquipmentByEquipmentId: Map<Long, OwnedEquipment> = emptyMap(),
        equippedBySlot: Map<EquipmentSlot, EquippedEquipment> = emptyMap(),
    ): EquipmentStoreSnapshot = EquipmentStoreSnapshot(
        characterId = characterId,
        currentGold = 0L,
        characterLevel = 1,
        equipment = equipment,
        ownedEquipmentIds = ownedEquipmentIds,
        ownedEquipmentByEquipmentId = ownedEquipmentByEquipmentId,
        equippedBySlot = equippedBySlot,
        appearance = CharacterLoadoutCatalog.defaultAppearance,
        renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
        derivedStats = DerivedStats(
            maxHp = 1,
            attack = 1,
            defense = 1,
            criticalChanceBp = 0,
            criticalDamageBp = 0,
            statusResistanceBp = 0,
            hpRecovery = 1,
            goldGainBonusBp = 0,
        ),
    )
}
