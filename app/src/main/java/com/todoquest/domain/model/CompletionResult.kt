package com.todoquest.domain.model

data class CompletionResult(
    val awardedXp: Long,
    val awardedGold: Long,
    val alreadyRewarded: Boolean,
    val isOnTime: Boolean = false,
    val rewardEfficiencyBp: Int = 10_000,
    val rewardMode: CompletionRewardMode = CompletionRewardMode.TODO_COMPLETION,
)

enum class CompletionRewardMode {
    TODO_COMPLETION,
    COMBAT_ATTACK,
}
