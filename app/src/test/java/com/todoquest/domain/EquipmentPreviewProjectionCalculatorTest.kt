package com.todoquest.domain

import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.usecase.DerivedStatsCalculator
import com.todoquest.domain.usecase.EquipmentPreviewProjectionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EquipmentPreviewProjectionCalculatorTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun sameSlotReplacementUsesFullStatFormulaAndPreservesOtherSlotsAndInputs() {
        val currentChest = equipment(
            id = 1,
            layerKey = CharacterLoadoutCatalog.TOP_CLOTH,
            modifiers = listOf(
                modifier(1, StatTarget.Base(StatType.STRENGTH), ModifierType.FLAT, 1),
                modifier(1, StatTarget.Base(StatType.VITALITY), ModifierType.FLAT, 1),
                modifier(1, StatTarget.Derived(DerivedStatType.ATTACK), ModifierType.FLAT, 2),
            ),
        )
        val currentHelmet = equipment(
            id = 2,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.HELMET,
            layerKey = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            modifiers = listOf(
                modifier(2, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 7),
                modifier(2, StatTarget.Derived(DerivedStatType.DEFENSE), ModifierType.FLAT, 3),
            ),
        )
        val candidate = equipment(
            id = 3,
            layerKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            modifiers = listOf(
                modifier(3, StatTarget.Base(StatType.STRENGTH), ModifierType.FLAT, 3),
                modifier(3, StatTarget.Base(StatType.VITALITY), ModifierType.FLAT, 2),
                modifier(3, StatTarget.Derived(DerivedStatType.ATTACK), ModifierType.FLAT, 5),
                modifier(3, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 20),
                modifier(3, StatTarget.Derived(DerivedStatType.DEFENSE), ModifierType.FLAT, 4),
                modifier(3, StatTarget.Derived(DerivedStatType.ATTACK), ModifierType.PERCENT_ADD, 1_000),
                modifier(3, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.PERCENT_ADD, 500),
                modifier(3, StatTarget.Derived(DerivedStatType.DEFENSE), ModifierType.PERCENT_ADD, 2_000),
            ),
        )
        val currentChestEquipped = equipped(currentChest)
        val currentHelmetEquipped = equipped(currentHelmet)
        val equippedBySlot = mapOf(
            EquipmentSlot.CHEST to currentChestEquipped,
            EquipmentSlot.HELMET to currentHelmetEquipped,
        )
        val equipmentModifiers = currentChest.modifiers + currentHelmet.modifiers
        val passiveModifiers = listOf(
            modifier(90, StatTarget.Derived(DerivedStatType.ATTACK), ModifierType.PERCENT_ADD, 500),
        )
        val temporaryEffects = listOf(
            temporaryEffect(91, DerivedStatType.ATTACK, -2_000, "injury-attack"),
            temporaryEffect(92, DerivedStatType.MAX_HP, -2_000, "injury-max-hp"),
        )
        val statInput = StatCalculationInput(
            level = 10,
            baseStats = CharacterBaseStats(strength = 10, vitality = 10, focus = 10, willpower = 10),
            equipmentModifiers = equipmentModifiers,
            passiveAndSetModifiers = passiveModifiers,
            temporaryEffects = temporaryEffects,
        )
        val renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
            topId = CharacterLoadoutCatalog.TOP_CLOTH,
        )

        val projection = EquipmentPreviewProjectionCalculator.calculate(
            candidate = candidate,
            equippedBySlot = equippedBySlot,
            renderedEquippedItems = renderedEquippedItems,
            statCalculationInput = statInput,
            config = config,
        )

        assertEquals(CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE, projection.renderedEquippedItems.topId)
        assertEquals(renderedEquippedItems.headId, projection.renderedEquippedItems.headId)
        assertEquals(renderedEquippedItems.bottomId, projection.renderedEquippedItems.bottomId)
        assertEquals(219, projection.derivedStats.maxHp)
        assertEquals(50, projection.derivedStats.attack)
        assertEquals(31, projection.derivedStats.defense)
        assertSame(equipmentModifiers, statInput.equipmentModifiers)
        assertSame(passiveModifiers, statInput.passiveAndSetModifiers)
        assertSame(temporaryEffects, statInput.temporaryEffects)
        assertSame(currentChestEquipped, equippedBySlot.getValue(EquipmentSlot.CHEST))
        assertSame(currentHelmetEquipped, equippedBySlot.getValue(EquipmentSlot.HELMET))
        assertEquals(CharacterLoadoutCatalog.TOP_CLOTH, renderedEquippedItems.topId)
    }

    @Test
    fun nullAndUnknownCandidateLayersKeepCurrentAppearanceWhileStatsStillPreview() {
        val currentChest = equipment(
            id = 10,
            layerKey = CharacterLoadoutCatalog.TOP_CLOTH,
            modifiers = listOf(
                modifier(10, StatTarget.Derived(DerivedStatType.ATTACK), ModifierType.FLAT, 1),
            ),
        )
        val equippedBySlot = mapOf(EquipmentSlot.CHEST to equipped(currentChest))
        val statInput = StatCalculationInput(
            level = 1,
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            equipmentModifiers = currentChest.modifiers,
        )
        val renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_CLOTH,
        )

        listOf(null, "unknown_top_layer").forEachIndexed { index, layerKey ->
            val candidate = equipment(
                id = 11L + index,
                layerKey = layerKey,
                modifiers = listOf(
                    modifier(
                        11L + index,
                        StatTarget.Derived(DerivedStatType.ATTACK),
                        ModifierType.FLAT,
                        9,
                    ),
                ),
            )

            val projection = EquipmentPreviewProjectionCalculator.calculate(
                candidate = candidate,
                equippedBySlot = equippedBySlot,
                renderedEquippedItems = renderedEquippedItems,
                statCalculationInput = statInput,
                config = config,
            )

            assertEquals(renderedEquippedItems, projection.renderedEquippedItems)
            assertEquals(29, projection.derivedStats.attack)
        }
    }

    @Test
    fun incompatibleTypeAndSlotReturnCurrentFallbackWithoutThrowing() {
        val currentChest = equipment(
            id = 20,
            layerKey = CharacterLoadoutCatalog.TOP_CLOTH,
            modifiers = listOf(
                modifier(20, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 15),
            ),
        )
        val equippedBySlot = mapOf(EquipmentSlot.CHEST to equipped(currentChest))
        val statInput = StatCalculationInput(
            level = 5,
            baseStats = CharacterBaseStats(7, 8, 6, 5),
            equipmentModifiers = currentChest.modifiers,
        )
        val renderedEquippedItems = CharacterLoadoutCatalog.defaultEquippedItems.copy(
            topId = CharacterLoadoutCatalog.TOP_CLOTH,
        )
        val incompatibleCandidate = equipment(
            id = 21,
            type = EquipmentType.HELMET,
            slot = EquipmentSlot.CHEST,
            layerKey = CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
            modifiers = listOf(
                modifier(21, StatTarget.Derived(DerivedStatType.MAX_HP), ModifierType.FLAT, 100),
            ),
        )

        val projection = EquipmentPreviewProjectionCalculator.calculate(
            candidate = incompatibleCandidate,
            equippedBySlot = equippedBySlot,
            renderedEquippedItems = renderedEquippedItems,
            statCalculationInput = statInput,
            config = config,
        )

        assertEquals(renderedEquippedItems, projection.renderedEquippedItems)
        assertEquals(DerivedStatsCalculator.calculate(statInput, config), projection.derivedStats)
        assertSame(currentChest.modifiers, statInput.equipmentModifiers)
    }

    private fun equipment(
        id: Long,
        type: EquipmentType = EquipmentType.CHEST,
        slot: EquipmentSlot = EquipmentSlot.CHEST,
        layerKey: String? = null,
        modifiers: List<EquipmentStatModifier> = emptyList(),
    ): Equipment = Equipment(
        id = id,
        nameKey = "equipment_$id",
        descriptionKey = "equipment_${id}_description",
        type = type,
        slot = slot,
        rarity = EquipmentRarity.RARE,
        price = 100,
        requiredLevel = 1,
        modifiers = modifiers,
        layerKey = layerKey,
        isForSale = true,
    )

    private fun equipped(equipment: Equipment): EquippedEquipment {
        val owned = OwnedEquipment(
            id = 100 + equipment.id,
            characterId = 1,
            equipment = equipment,
            acquiredAtEpochMillis = 0,
        )
        return EquippedEquipment(
            characterId = 1,
            slot = equipment.slot,
            ownedEquipment = owned,
        )
    }

    private fun modifier(
        itemId: Long,
        target: StatTarget,
        type: ModifierType,
        amount: Int,
    ): EquipmentStatModifier = EquipmentStatModifier(itemId, target, type, amount)

    private fun temporaryEffect(
        effectId: Long,
        target: DerivedStatType,
        amount: Int,
        stackingKey: String,
    ): TemporaryStatEffect = TemporaryStatEffect(
        effectId = effectId,
        target = StatTarget.Derived(target),
        type = ModifierType.PERCENT_ADD,
        amount = amount,
        stackingKey = stackingKey,
        startedAtEpochMillis = 0,
        endsAtEpochMillis = 1_000,
        remainingTriggers = null,
    )
}
