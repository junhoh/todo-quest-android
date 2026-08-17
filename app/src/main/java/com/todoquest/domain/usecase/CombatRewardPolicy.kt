package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatRewardBalanceConfig
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterGrade

data class CombatReward(
    val hitXpAward: Long,
    val killBonusXpAward: Long,
    val killGoldAward: Long,
) {
    init {
        require(hitXpAward >= 0) { "hitXpAward must not be negative" }
        require(killBonusXpAward >= 0) { "killBonusXpAward must not be negative" }
        require(killGoldAward >= 0) { "killGoldAward must not be negative" }
    }

    val totalXpAward: Long
        get() = Math.addExact(hitXpAward, killBonusXpAward)
}

object CombatRewardPolicy {
    @Suppress("LongParameterList")
    fun rewardFor(
        monsterLevel: Int,
        monsterGrade: MonsterGrade,
        isKill: Boolean,
        goldGainBonusBp: Int,
        monsterConfig: MonsterBalanceConfig,
        characterConfig: CharacterStatBalanceConfig,
    ): CombatReward = rewardFor(
        monsterLevel = monsterLevel,
        monsterGrade = monsterGrade,
        isKill = isKill,
        goldGainBonusBp = goldGainBonusBp,
        combatRewardVersion = monsterConfig.combatRewardVersion,
        monsterConfig = monsterConfig,
        characterConfig = characterConfig,
    )

    @Suppress("LongParameterList")
    fun rewardFor(
        monsterLevel: Int,
        monsterGrade: MonsterGrade,
        isKill: Boolean,
        goldGainBonusBp: Int,
        combatRewardVersion: Int,
        monsterConfig: MonsterBalanceConfig,
        characterConfig: CharacterStatBalanceConfig,
    ): CombatReward = rewardFor(
        monsterLevel = monsterLevel,
        monsterGrade = monsterGrade,
        isKill = isKill,
        goldGainBonusBp = goldGainBonusBp,
        rewardConfig = CombatRewardBalanceCatalog.configFor(combatRewardVersion),
        monsterConfig = monsterConfig,
        characterConfig = characterConfig,
    )

    @Suppress("LongParameterList")
    fun rewardFor(
        monsterLevel: Int,
        monsterGrade: MonsterGrade,
        isKill: Boolean,
        goldGainBonusBp: Int,
        rewardConfig: CombatRewardBalanceConfig,
        monsterConfig: MonsterBalanceConfig,
        characterConfig: CharacterStatBalanceConfig,
    ): CombatReward {
        require(monsterLevel in monsterConfig.monsterLevelMin..monsterConfig.monsterLevelMax) {
            "monsterLevel is outside the configured range"
        }
        require(goldGainBonusBp in characterConfig.goldGainBonusMinBp..characterConfig.goldGainBonusMaxBp) {
            "goldGainBonusBp is outside the configured range"
        }
        require(monsterConfig.basisPointScale == characterConfig.basisPointScale) {
            "combat reward basis point scales must match"
        }

        val levelOffset = (monsterLevel - monsterConfig.monsterLevelMin).toLong()
        val hitXp = Math.addExact(
            rewardConfig.hitXpBase,
            levelOffset / rewardConfig.hitXpLevelBand,
        )
        if (!isKill) {
            return CombatReward(hitXp, killBonusXpAward = 0L, killGoldAward = 0L)
        }

        val gradeMultiplierBp = monsterConfig.gradeRewardMultipliersBp.getValue(monsterGrade).toLong()
        val scale = monsterConfig.basisPointScale.toLong()
        val baseKillXp = Math.addExact(
            rewardConfig.killBonusXpBase,
            levelOffset / rewardConfig.killBonusXpLevelBand,
        )
        val killXp = Math.multiplyExact(baseKillXp, gradeMultiplierBp).div(scale)
        val baseKillGold = Math.addExact(
            rewardConfig.killGoldBase,
            levelOffset / rewardConfig.killGoldLevelBand,
        )
        val goldWithGrade = Math.multiplyExact(baseKillGold, gradeMultiplierBp)
        val goldWithBonus = Math.multiplyExact(
            goldWithGrade,
            Math.addExact(scale, goldGainBonusBp.toLong()),
        )
        val killGold = goldWithBonus.div(Math.multiplyExact(scale, scale))

        return CombatReward(
            hitXpAward = hitXp,
            killBonusXpAward = killXp,
            killGoldAward = killGold,
        )
    }
}
