package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterType
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.domain.usecase.MonsterCombatPolicy
import com.todoquest.domain.usecase.MonsterStagePolicy
import com.todoquest.domain.usecase.MonsterStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterCombatPolicyTest {
    private val monsterConfig = MonsterBalanceConfig()
    private val characterConfig = CharacterStatBalanceConfig()

    @Test
    fun normalEliteAndBossExpectedHitsMatchGoldenValues() {
        val players = listOf(
            PlayerGolden(1, 20, 750, 15_250),
            PlayerGolden(10, 43, 950, 15_500),
            PlayerGolden(30, 93, 1_450, 16_000),
            PlayerGolden(50, 143, 1_950, 16_500),
        )
        val normalExpected = mapOf(
            MonsterType.BALANCED to listOf(4, 4, 4, 5),
            MonsterType.ATTACK to listOf(4, 3, 4, 4),
            MonsterType.DEFENSE to listOf(5, 4, 5, 5),
        )

        normalExpected.forEach { (type, expectedHits) ->
            players.forEachIndexed { index, player ->
                val stats = stats(type, MonsterGrade.NORMAL, player.level)
                assertEquals(
                    "$type level ${player.level}",
                    expectedHits[index],
                    expectedHits(player, stats.maxHp, stats.defense),
                )
            }
        }

        val eliteExpected = listOf(7, 6, 6, 7)
        val bossExpected = listOf(18, 15, 16, 18)
        players.forEachIndexed { index, player ->
            val eliteLevel = MonsterStagePolicy.monsterLevel(player.level, MonsterGrade.ELITE, monsterConfig)
            val bossLevel = MonsterStagePolicy.monsterLevel(player.level, MonsterGrade.BOSS, monsterConfig)
            val elite = stats(MonsterType.ATTACK, MonsterGrade.ELITE, eliteLevel)
            val boss = stats(MonsterType.BOSS, MonsterGrade.BOSS, bossLevel)
            assertEquals(eliteExpected[index], expectedHits(player, elite.maxHp, elite.defense))
            assertEquals(bossExpected[index], expectedHits(player, boss.maxHp, boss.defense))
        }
    }

    @Test
    fun monsterAttacksUseTheExistingRatioDamageAtRepresentativeLevels() {
        val playerDefense = mapOf(1 to 8, 10 to 17, 30 to 37, 50 to 57)
        val expectedDamage = mapOf(1 to 11, 10 to 25, 30 to 51, 50 to 70)

        expectedDamage.forEach { (level, expected) ->
            val monster = stats(MonsterType.BALANCED, MonsterGrade.NORMAL, level)
            assertEquals(
                expected,
                CombatCalculator.damageAfterDefense(monster.damage, playerDefense.getValue(level), characterConfig),
            )
        }
    }

    @Test
    fun hpTransitionsClampDefeatAndRecoveryWithIntegerMath() {
        assertEquals(0, MonsterCombatPolicy.monsterHpAfterDamage(3, 75, 4))
        assertEquals(71, MonsterCombatPolicy.monsterHpAfterDamage(75, 75, 4))

        val surviving = MonsterCombatPolicy.playerHpAfterDamage(100, 110, 99)
        assertEquals(1, surviving.currentHp)
        assertFalse(surviving.wasLethal)

        val lethal = MonsterCombatPolicy.playerHpAfterDamage(100, 110, 100)
        assertEquals(0, lethal.currentHp)
        assertTrue(lethal.wasLethal)

        val overkill = MonsterCombatPolicy.playerHpAfterDamage(1, 110, 2)
        assertEquals(0, overkill.currentHp)
        assertTrue(overkill.wasLethal)

        assertEquals(110, MonsterCombatPolicy.playerHpAfterVictoryRecovery(108, 110, 7))
        assertEquals(57, MonsterCombatPolicy.playerHpAfterVictoryRecovery(50, 110, 7))
    }

    @Test
    fun hpTransitionsRejectBrokenCurrentHpInvariants() {
        assertThrows(IllegalArgumentException::class.java) {
            MonsterCombatPolicy.monsterHpAfterDamage(76, 75, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterCombatPolicy.playerHpAfterDamage(-1, 110, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterCombatPolicy.playerHpAfterVictoryRecovery(111, 110, 1)
        }
    }

    private fun stats(type: MonsterType, grade: MonsterGrade, level: Int) =
        MonsterStatsCalculator.calculate(
            MonsterCatalog.definitionFor(type, monsterConfig),
            grade,
            level,
            monsterConfig,
        )

    private fun expectedHits(player: PlayerGolden, maxHp: Int, defense: Int): Int {
        val normalDamage = CombatCalculator.normalDamage(player.attack, defense, characterConfig)
        val criticalDamage = CombatCalculator.criticalDamage(
            player.attack,
            player.criticalDamageBp,
            defense,
            characterConfig,
        )
        val expectedDamageNumerator =
            normalDamage.toLong() * (characterConfig.basisPointScale - player.criticalChanceBp) +
                criticalDamage.toLong() * player.criticalChanceBp
        val hpNumerator = maxHp.toLong() * characterConfig.basisPointScale
        return ((hpNumerator + expectedDamageNumerator - 1) / expectedDamageNumerator).toInt()
    }

    private data class PlayerGolden(
        val level: Int,
        val attack: Int,
        val criticalChanceBp: Int,
        val criticalDamageBp: Int,
    )
}
