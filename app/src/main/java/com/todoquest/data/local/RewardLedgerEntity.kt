package com.todoquest.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reward_ledger",
    indices = [
        Index(
            value = ["taskId", "occurrenceDateEpochDay"],
            unique = true,
        ),
        Index(value = ["recurrenceSeriesId"]),
        Index(value = ["rewardLocalDateEpochDay"]),
        Index(value = ["onTime", "occurrenceDateEpochDay"]),
    ],
)
data class RewardLedgerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val recurrenceSeriesId: Long,
    val xpAward: Long,
    val goldAward: Long,
    val rewardLocalDateEpochDay: Long,
    val onTime: Boolean,
    val onTimeMultiplierBp: Int,
    val rewardEfficiencyBp: Int,
    val repeatOrdinal: Int,
    val dailyOrdinal: Int,
    val goldGainBonusBp: Int,
    val combatEligible: Boolean,
    val balanceVersion: Int,
    val awardedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "TODO_COMPLETION")
    val rewardMode: String = "TODO_COMPLETION",
)
