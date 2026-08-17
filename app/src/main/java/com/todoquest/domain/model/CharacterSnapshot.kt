package com.todoquest.domain.model

data class CharacterSnapshot(
    val character: PlayerCharacter,
    val appearance: CharacterAppearance,
    val equippedItems: EquippedItems,
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val xpRequiredForNextLevel: Long,
    val isMaxLevel: Boolean,
    val currentState: CharacterCurrentState,
    val derivedStats: DerivedStats,
    val currentStreak: Int,
    val momentumBonusBp: Int,
)

sealed interface AllocateStatPointsResult {
    data class Success(val allocation: StatAllocation) : AllocateStatPointsResult

    data object NoChanges : AllocateStatPointsResult

    data class InsufficientPoints(
        val requested: Int,
        val available: Int,
    ) : AllocateStatPointsResult

    data class StatCap(val type: StatType) : AllocateStatPointsResult
}

sealed interface StatResetResult {
    data class Success(val goldSpent: Long) : StatResetResult

    data object NothingToReset : StatResetResult

    data class InsufficientGold(
        val requiredGold: Long,
        val availableGold: Long,
    ) : StatResetResult
}
