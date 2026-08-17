package com.todoquest.domain

import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.usecase.DerivedStatsCalculator
import com.todoquest.domain.usecase.StatusEffectPolicy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DerivedStatsCalculatorTest {
    private val config = CharacterStatBalanceConfig()

    @Test
    fun level1AndLevel50WithoutEquipmentMatchGoldenValues() {
        assertEquals(
            DerivedStats(
                maxHp = 110,
                attack = 20,
                defense = 8,
                criticalChanceBp = 750,
                criticalDamageBp = 15_250,
                statusResistanceBp = 375,
                hpRecovery = 7,
                goldGainBonusBp = 0,
            ),
            calculate(level = 1, stats = CharacterBaseStats(5, 5, 5, 5)),
        )

        assertEquals(
            DerivedStats(
                maxHp = 654,
                attack = 143,
                defense = 57,
                criticalChanceBp = 1_950,
                criticalDamageBp = 16_500,
                statusResistanceBp = 2_175,
                hpRecovery = 40,
                goldGainBonusBp = 0,
            ),
            calculate(level = 50, stats = CharacterBaseStats(30, 30, 29, 29)),
        )
    }

    @Test
    fun level10CommonEquipmentMatchesGoldenValues() {
        val equipment = listOf(
            base(StatType.STRENGTH, 1),
            base(StatType.VITALITY, 1),
            base(StatType.FOCUS, 1),
            base(StatType.WILLPOWER, 1),
            derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 12),
            derived(DerivedStatType.ATTACK, ModifierType.FLAT, 4),
            derived(DerivedStatType.DEFENSE, ModifierType.FLAT, 2),
            derived(DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 1),
            derived(DerivedStatType.MAX_HP, ModifierType.PERCENT_ADD, 300),
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 300),
            derived(DerivedStatType.DEFENSE, ModifierType.PERCENT_ADD, 300),
            derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 100),
            derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 200),
            derived(DerivedStatType.STATUS_RESISTANCE, ModifierType.FLAT, 150),
            derived(DerivedStatType.GOLD_GAIN_BONUS, ModifierType.FLAT, 200),
        )

        assertEquals(
            DerivedStats(243, 51, 20, 1_100, 15_750, 900, 14, 200),
            calculate(10, CharacterBaseStats(10, 10, 9, 9), equipment),
        )
    }

    @Test
    fun level30RareEquipmentMatchesGoldenValues() {
        val equipment = listOf(
            base(StatType.STRENGTH, 5),
            base(StatType.VITALITY, 4),
            base(StatType.FOCUS, 4),
            base(StatType.WILLPOWER, 3),
            derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 45),
            derived(DerivedStatType.ATTACK, ModifierType.FLAT, 15),
            derived(DerivedStatType.DEFENSE, ModifierType.FLAT, 8),
            derived(DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 4),
            derived(DerivedStatType.MAX_HP, ModifierType.PERCENT_ADD, 800),
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 800),
            derived(DerivedStatType.DEFENSE, ModifierType.PERCENT_ADD, 800),
            derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 300),
            derived(DerivedStatType.CRITICAL_DAMAGE, ModifierType.FLAT, 700),
            derived(DerivedStatType.STATUS_RESISTANCE, ModifierType.FLAT, 500),
            derived(DerivedStatType.GOLD_GAIN_BONUS, ModifierType.FLAT, 800),
        )

        assertEquals(
            DerivedStats(484, 166, 45, 2_050, 17_700, 1_400, 23, 800),
            calculate(30, CharacterBaseStats(35, 13, 21, 9), equipment),
        )
    }

    @Test
    fun percentBucketsSumThenMultiplyAndFloorOnlyOnce() {
        val equipment = listOf(
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 1_000),
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 2_000),
        )
        val passive = listOf(
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 1_500),
            derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 1_000),
        )
        val temporary = listOf(
            effect(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, -1_000, "slow"),
            effect(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 500, "boost"),
        )

        val input = StatCalculationInput(
            level = 1,
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            equipmentModifiers = equipment,
            passiveAndSetModifiers = passive,
            temporaryEffects = temporary,
        )

        assertEquals(30, DerivedStatsCalculator.calculate(input, config).attack)
        assertEquals(
            DerivedStatsCalculator.calculate(input, config),
            DerivedStatsCalculator.calculate(
                input.copy(
                    equipmentModifiers = equipment.reversed(),
                    passiveAndSetModifiers = passive.reversed(),
                    temporaryEffects = temporary.reversed(),
                ),
                config,
            ),
        )
    }

    @Test
    fun bucketsFinalClampsAndNegativeEffectsFollowConfiguredLimits() {
        val bucketCapped = StatCalculationInput(
            level = 1,
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            equipmentModifiers = listOf(
                derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 10_000),
            ),
            passiveAndSetModifiers = listOf(
                derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 10_000),
            ),
            temporaryEffects = listOf(
                effect(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, 10_000, "surge"),
            ),
        )
        assertEquals(50, DerivedStatsCalculator.calculate(bucketCapped, config).attack)

        val capped = StatCalculationInput(
            level = 1,
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            equipmentModifiers = listOf(
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 10_000),
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, 10_000),
            ),
            passiveAndSetModifiers = listOf(
                derived(DerivedStatType.CRITICAL_CHANCE, ModifierType.FLAT, -10_000),
            ),
            temporaryEffects = emptyList(),
        )

        val result = DerivedStatsCalculator.calculate(capped, config)
        assertEquals(2_000, result.attack)
        assertEquals(750, result.criticalChanceBp)

        val debuffed = StatCalculationInput(
            level = 1,
            baseStats = CharacterBaseStats(5, 5, 5, 5),
            equipmentModifiers = emptyList(),
            passiveAndSetModifiers = listOf(
                derived(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, -10_000),
            ),
            temporaryEffects = listOf(
                effect(DerivedStatType.ATTACK, ModifierType.PERCENT_ADD, -10_000, "curse"),
            ),
        )
        assertEquals(5, DerivedStatsCalculator.calculate(debuffed, config).attack)
    }

    @Test
    fun hpRecoveryUsesFinalMaxHpDynamicCap() {
        val result = calculate(
            level = 1,
            stats = CharacterBaseStats(5, 5, 5, 5),
            equipment = listOf(derived(DerivedStatType.HP_RECOVERY, ModifierType.FLAT, 10_000)),
        )

        assertEquals(110, result.maxHp)
        assertEquals(33, result.hpRecovery)
    }

    @Test
    fun severeInjuryFloorsMaxHpAndAttackToEightyPercentWithoutMutatingSources() {
        val baseStats = CharacterBaseStats(5, 5, 5, 5)
        val equipment = listOf(
            derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 1),
            derived(DerivedStatType.ATTACK, ModifierType.FLAT, 1),
        )
        val statusEffect = severeInjury()
        val temporaryEffects = StatusEffectPolicy.temporaryEffectsFor(
            listOf(statusEffect, statusEffect.copy(revision = 2)),
            Instant.ofEpochMilli(statusEffect.appliedAtEpochMillis + 1),
        )
        val input = StatCalculationInput(
            level = 1,
            baseStats = baseStats,
            equipmentModifiers = equipment,
            temporaryEffects = temporaryEffects + temporaryEffects,
        )

        val withoutInjury = DerivedStatsCalculator.calculate(input.copy(temporaryEffects = emptyList()), config)
        val withInjury = DerivedStatsCalculator.calculate(input, config)

        assertEquals(111, withoutInjury.maxHp)
        assertEquals(21, withoutInjury.attack)
        assertEquals(88, withInjury.maxHp)
        assertEquals(16, withInjury.attack)
        assertSame(baseStats, input.baseStats)
        assertSame(equipment, input.equipmentModifiers)
        assertEquals(CharacterBaseStats(5, 5, 5, 5), input.baseStats)
        assertEquals(
            listOf(
                derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 1),
                derived(DerivedStatType.ATTACK, ModifierType.FLAT, 1),
            ),
            input.equipmentModifiers,
        )
    }

    @Test
    fun severeInjuryKeepsLowMaxHpAndAttackAtExistingMinimumOne() {
        val lowStatConfig = CharacterStatBalanceConfig(
            maxHpBase = 0,
            maxHpPerLevel = 0,
            maxHpPerVitality = 1,
            attackBase = 0,
            attackPerLevel = 0,
            attackPerStrength = 0,
            attackPerFocus = 1,
        )
        val statusEffect = severeInjury()

        val result = DerivedStatsCalculator.calculate(
            StatCalculationInput(
                level = 1,
                baseStats = CharacterBaseStats(1, 1, 1, 1),
                temporaryEffects = StatusEffectPolicy.temporaryEffectsFor(
                    listOf(statusEffect),
                    Instant.ofEpochMilli(statusEffect.appliedAtEpochMillis + 1),
                ),
            ),
            lowStatConfig,
        )

        assertEquals(1, result.maxHp)
        assertEquals(1, result.attack)
    }

    @Test
    fun boundedSourcesCannotOverflowLongAndOutOfBoundsSourcesAreRejected() {
        val maximumAllowedSource = List(config.maxModifiersPerSource) { index ->
            derived(
                DerivedStatType.MAX_HP,
                if (index == 0) ModifierType.PERCENT_ADD else ModifierType.FLAT,
                config.maxModifierAmount,
                itemId = index.toLong(),
            )
        }
        val result = calculate(
            level = 50,
            stats = CharacterBaseStats(60, 60, 60, 60),
            equipment = maximumAllowedSource,
        )
        assertEquals(config.maxHpMax, result.maxHp)

        assertThrows(IllegalArgumentException::class.java) {
            calculate(
                1,
                CharacterBaseStats(5, 5, 5, 5),
                maximumAllowedSource + derived(DerivedStatType.MAX_HP, ModifierType.FLAT, 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(
                1,
                CharacterBaseStats(5, 5, 5, 5),
                listOf(
                    derived(
                        DerivedStatType.MAX_HP,
                        ModifierType.FLAT,
                        config.maxModifierAmount + 1,
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(1, CharacterBaseStats(61, 5, 5, 5))
        }
    }

    @Test
    fun balanceConfigIsVersionOne() {
        assertEquals(1, config.version)
    }

    private fun calculate(
        level: Int,
        stats: CharacterBaseStats,
        equipment: List<EquipmentStatModifier> = emptyList(),
    ): DerivedStats = DerivedStatsCalculator.calculate(
        StatCalculationInput(
            level = level,
            baseStats = stats,
            equipmentModifiers = equipment,
            passiveAndSetModifiers = emptyList(),
            temporaryEffects = emptyList(),
        ),
        config,
    )

    private fun base(type: StatType, amount: Int): EquipmentStatModifier =
        EquipmentStatModifier(1, StatTarget.Base(type), ModifierType.FLAT, amount)

    private fun derived(
        type: DerivedStatType,
        modifierType: ModifierType,
        amount: Int,
        itemId: Long = 1,
    ): EquipmentStatModifier = EquipmentStatModifier(
        itemId,
        StatTarget.Derived(type),
        modifierType,
        amount,
    )

    private fun effect(
        type: DerivedStatType,
        modifierType: ModifierType,
        amount: Int,
        key: String,
    ): TemporaryStatEffect = TemporaryStatEffect(
        effectId = key.hashCode().toLong(),
        target = StatTarget.Derived(type),
        type = modifierType,
        amount = amount,
        stackingKey = key,
        startedAtEpochMillis = 0,
        endsAtEpochMillis = 1,
        remainingTriggers = null,
    )

    private fun severeInjury(): CharacterStatusEffect = CharacterStatusEffect(
        characterId = 1,
        type = StatusEffectType.SEVERE_INJURY,
        definitionVersion = 1,
        appliedAtEpochMillis = 1_000,
        expiresAtEpochMillis = 1_000 + 24L * 60L * 60L * 1_000L,
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1,
        lastMutationId = "monster:1:0",
    )
}
