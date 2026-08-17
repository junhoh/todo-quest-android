package com.todoquest.domain.usecase

data class PlayerHpDamageResult(
    val currentHp: Int,
    val wasLethal: Boolean,
)

object MonsterCombatPolicy {
    fun monsterHpAfterDamage(
        currentHp: Int,
        maxHp: Int,
        finalDamage: Int,
    ): Int {
        require(maxHp >= 1) { "maxHp must be at least 1" }
        require(currentHp in 0..maxHp) { "currentHp must be within 0..maxHp" }
        require(finalDamage >= 1) { "finalDamage must be positive" }
        return (currentHp.toLong() - finalDamage.toLong())
            .coerceAtLeast(0)
            .toInt()
    }

    fun playerHpAfterDamage(
        currentHp: Int,
        maxHp: Int,
        finalDamage: Int,
    ): PlayerHpDamageResult {
        require(maxHp >= 1) { "maxHp must be at least 1" }
        require(currentHp in 0..maxHp) { "currentHp must be within 0..maxHp" }
        require(finalDamage >= 1) { "finalDamage must be positive" }
        val remainingHp = currentHp.toLong() - finalDamage.toLong()
        return PlayerHpDamageResult(
            currentHp = remainingHp.coerceAtLeast(0L).toInt(),
            wasLethal = remainingHp <= 0,
        )
    }

    fun playerHpAfterVictoryRecovery(
        currentHp: Int,
        maxHp: Int,
        hpRecovery: Int,
    ): Int {
        require(maxHp >= 1) { "maxHp must be at least 1" }
        require(currentHp in 0..maxHp) { "currentHp must be within 0..maxHp" }
        require(hpRecovery >= 0) { "hpRecovery must not be negative" }
        return Math.addExact(currentHp.toLong(), hpRecovery.toLong())
            .coerceAtMost(maxHp.toLong())
            .toInt()
    }
}
