package com.todoquest.domain.model

data class CharacterProfile(
    val level: Int,
    val totalXp: Long,
    val currentGold: Long,
) {
    fun withReward(xp: Long, gold: Long): CharacterProfile {
        val nextXp = Math.addExact(totalXp, xp)
        return copy(
            level = levelForXp(nextXp),
            totalXp = nextXp,
            currentGold = Math.addExact(currentGold, gold),
        )
    }

    companion object {
        private const val XP_PER_LEVEL = 100L
        private const val MAX_LEVEL = 50

        fun default(): CharacterProfile = CharacterProfile(
            level = 1,
            totalXp = 0L,
            currentGold = 0L,
        )

        fun levelForXp(totalXp: Long): Int =
            minOf(MAX_LEVEL.toLong(), 1L + totalXp / XP_PER_LEVEL).toInt()
    }
}
