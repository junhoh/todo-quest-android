package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatRewardBalanceConfig
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.usecase.CombatReward
import com.todoquest.domain.usecase.CombatRewardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CombatRewardPolicyTest {
    private val monsterConfig = MonsterBalanceConfig()
    private val characterConfig = CharacterStatBalanceConfig()

    @Test
    fun catalogPinsBothRewardVersionsAndMakesVersionTwoCurrent() {
        val versionOne = CombatRewardBalanceCatalog.configFor(1)
        val versionTwo = CombatRewardBalanceCatalog.configFor(2)

        assertEquals(2, CombatRewardBalanceCatalog.CURRENT_VERSION)
        assertEquals(CombatRewardBalanceCatalog.CURRENT_VERSION, monsterConfig.combatRewardVersion)
        assertEquals(1, monsterConfig.version)
        assertEquals(
            listOf(1L, 10L, 5L),
            listOf(versionOne.hitXpBase, versionOne.killBonusXpBase, versionOne.killGoldBase),
        )
        assertEquals(
            listOf(3L, 20L, 15L),
            listOf(versionTwo.hitXpBase, versionTwo.killBonusXpBase, versionTwo.killGoldBase),
        )
        val versionOneBands = listOf(
            versionOne.hitXpLevelBand,
            versionOne.killBonusXpLevelBand,
            versionOne.killGoldLevelBand,
        )
        val versionTwoBands = listOf(
            versionTwo.hitXpLevelBand,
            versionTwo.killBonusXpLevelBand,
            versionTwo.killGoldLevelBand,
        )
        assertEquals(listOf(10, 5, 10), versionOneBands)
        assertEquals(versionOneBands, versionTwoBands)
    }

    @Test
    fun versionOneGoldenRewardsRemainStable() {
        val levelOneHit = reward(level = 1, grade = MonsterGrade.NORMAL, isKill = false, version = 1)
        assertEquals(1L, levelOneHit.totalXpAward)
        assertEquals(0L, levelOneHit.killGoldAward)

        val levelOneKill = reward(level = 1, grade = MonsterGrade.NORMAL, isKill = true, version = 1)
        assertEquals(1L, levelOneKill.hitXpAward)
        assertEquals(10L, levelOneKill.killBonusXpAward)
        assertEquals(11L, levelOneKill.totalXpAward)
        assertEquals(5L, levelOneKill.killGoldAward)

        val levelFiftyFiveBoss = reward(
            level = 55,
            grade = MonsterGrade.BOSS,
            isKill = true,
            version = 1,
        )
        assertEquals(6L, levelFiftyFiveBoss.hitXpAward)
        assertEquals(80L, levelFiftyFiveBoss.killBonusXpAward)
        assertEquals(86L, levelFiftyFiveBoss.totalXpAward)
        assertEquals(40L, levelFiftyFiveBoss.killGoldAward)
    }

    @Test
    fun versionTwoGoldenRewardsUseTheApprovedHigherBases() {
        val levelOneHit = reward(level = 1, grade = MonsterGrade.NORMAL, isKill = false, version = 2)
        assertEquals(3L, levelOneHit.totalXpAward)
        assertEquals(0L, levelOneHit.killGoldAward)

        val levelOneKill = reward(level = 1, grade = MonsterGrade.NORMAL, isKill = true, version = 2)
        assertEquals(3L, levelOneKill.hitXpAward)
        assertEquals(20L, levelOneKill.killBonusXpAward)
        assertEquals(23L, levelOneKill.totalXpAward)
        assertEquals(15L, levelOneKill.killGoldAward)

        val levelFiftyFiveBoss = reward(
            level = 55,
            grade = MonsterGrade.BOSS,
            isKill = true,
            version = 2,
        )
        assertEquals(8L, levelFiftyFiveBoss.hitXpAward)
        assertEquals(120L, levelFiftyFiveBoss.killBonusXpAward)
        assertEquals(128L, levelFiftyFiveBoss.totalXpAward)
        assertEquals(80L, levelFiftyFiveBoss.killGoldAward)
    }

    @Test
    fun bothVersionsUseTheSameLevelBands() {
        val versionOneExpected = mapOf(1 to 1L, 10 to 1L, 11 to 2L, 50 to 5L, 51 to 6L, 55 to 6L)
        val versionTwoExpected = versionOneExpected.mapValues { (_, xp) -> xp + 2L }

        listOf(1 to versionOneExpected, 2 to versionTwoExpected).forEach { (version, expected) ->
            expected.forEach { (level, xp) ->
                assertEquals(
                    xp,
                    reward(level, MonsterGrade.NORMAL, isKill = false, version = version).hitXpAward,
                )
            }
        }
    }

    @Test
    fun gradeMultiplierAndMaximumGoldBonusApplyOnlyToKillRewards() {
        assertEquals(40L, reward(1, MonsterGrade.ELITE, true, version = 2).killBonusXpAward)
        assertEquals(80L, reward(1, MonsterGrade.BOSS, true, version = 2).killBonusXpAward)

        val boostedKill = reward(
            level = 55,
            grade = MonsterGrade.BOSS,
            isKill = true,
            version = 2,
            goldBonusBp = 5_000,
        )
        assertEquals(8L, boostedKill.hitXpAward)
        assertEquals(120L, boostedKill.killBonusXpAward)
        assertEquals(120L, boostedKill.killGoldAward)
        assertEquals(
            22L,
            reward(
                level = 1,
                grade = MonsterGrade.NORMAL,
                isKill = true,
                version = 2,
                goldBonusBp = 5_000,
            ).killGoldAward,
        )

        val boostedHit = reward(
            level = 55,
            grade = MonsterGrade.BOSS,
            isKill = false,
            version = 2,
            goldBonusBp = 5_000,
        )
        assertEquals(8L, boostedHit.totalXpAward)
        assertEquals(0L, boostedHit.killGoldAward)
    }

    @Test
    fun currentAndExplicitConfigOverloadsRouteToTheExpectedVersion() {
        val current = CombatRewardPolicy.rewardFor(
            monsterLevel = 1,
            monsterGrade = MonsterGrade.NORMAL,
            isKill = true,
            goldGainBonusBp = 0,
            monsterConfig = monsterConfig,
            characterConfig = characterConfig,
        )
        val explicitVersion = reward(1, MonsterGrade.NORMAL, isKill = true, version = 2)
        val explicitConfig = CombatRewardPolicy.rewardFor(
            monsterLevel = 1,
            monsterGrade = MonsterGrade.NORMAL,
            isKill = true,
            goldGainBonusBp = 0,
            rewardConfig = CombatRewardBalanceCatalog.configFor(2),
            monsterConfig = monsterConfig,
            characterConfig = characterConfig,
        )

        assertEquals(explicitVersion, current)
        assertEquals(explicitVersion, explicitConfig)
    }

    @Test
    fun invalidLevelGoldBonusAndUnknownVersionAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            reward(0, MonsterGrade.NORMAL, false, version = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reward(56, MonsterGrade.NORMAL, false, version = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reward(1, MonsterGrade.NORMAL, true, version = 2, goldBonusBp = 5_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reward(1, MonsterGrade.NORMAL, true, version = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reward(1, MonsterGrade.NORMAL, true, version = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonsterBalanceConfig(combatRewardVersion = 3)
        }
    }

    @Test
    fun exactArithmeticRejectsIntermediateAndTotalOverflow() {
        assertThrows(ArithmeticException::class.java) {
            CombatRewardPolicy.rewardFor(
                monsterLevel = 11,
                monsterGrade = MonsterGrade.NORMAL,
                isKill = false,
                goldGainBonusBp = 0,
                rewardConfig = CombatRewardBalanceConfig(
                    version = 1,
                    hitXpBase = Long.MAX_VALUE,
                    hitXpLevelBand = 10,
                    killBonusXpBase = 10L,
                    killBonusXpLevelBand = 5,
                    killGoldBase = 5L,
                    killGoldLevelBand = 10,
                ),
                monsterConfig = monsterConfig,
                characterConfig = characterConfig,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            CombatRewardPolicy.rewardFor(
                monsterLevel = 1,
                monsterGrade = MonsterGrade.BOSS,
                isKill = true,
                goldGainBonusBp = 0,
                rewardConfig = CombatRewardBalanceConfig(
                    version = 1,
                    hitXpBase = 1L,
                    hitXpLevelBand = 10,
                    killBonusXpBase = Long.MAX_VALUE,
                    killBonusXpLevelBand = 5,
                    killGoldBase = 5L,
                    killGoldLevelBand = 10,
                ),
                monsterConfig = monsterConfig,
                characterConfig = characterConfig,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            CombatRewardPolicy.rewardFor(
                monsterLevel = 1,
                monsterGrade = MonsterGrade.BOSS,
                isKill = true,
                goldGainBonusBp = 5_000,
                rewardConfig = CombatRewardBalanceConfig(
                    version = 1,
                    hitXpBase = 1L,
                    hitXpLevelBand = 10,
                    killBonusXpBase = 10L,
                    killBonusXpLevelBand = 5,
                    killGoldBase = Long.MAX_VALUE,
                    killGoldLevelBand = 10,
                ),
                monsterConfig = monsterConfig,
                characterConfig = characterConfig,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            CombatReward(Long.MAX_VALUE, 1L, 0L).totalXpAward
        }
    }

    private fun reward(
        level: Int,
        grade: MonsterGrade,
        isKill: Boolean,
        version: Int,
        goldBonusBp: Int = 0,
    ) = CombatRewardPolicy.rewardFor(
        monsterLevel = level,
        monsterGrade = grade,
        isKill = isKill,
        goldGainBonusBp = goldBonusBp,
        combatRewardVersion = version,
        monsterConfig = monsterConfig,
        characterConfig = characterConfig,
    )
}
