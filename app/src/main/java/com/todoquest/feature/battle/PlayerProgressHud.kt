package com.todoquest.feature.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todoquest.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Composable
internal fun PlayerProgressHud(
    isLoading: Boolean,
    level: Int,
    currentExp: Long,
    requiredExp: Long,
    gold: Long,
    modifier: Modifier = Modifier,
) {
    val reportHudSize = LocalBattleHudSizeReporter.current
    val shape = MaterialTheme.shapes.medium
    val description = if (isLoading) {
        stringResource(R.string.battle_player_progress_loading_description)
    } else {
        stringResource(
            R.string.battle_player_progress_description,
            formatExactHudNumber(level.toLong()),
            formatExactHudNumber(currentExp),
            formatExactHudNumber(requiredExp),
            formatExactHudNumber(gold),
        )
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .testTag(PlayerProgressHudTag)
            .onSizeChanged(reportHudSize)
            .clearAndSetSemantics {
                contentDescription = description
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (isLoading) {
            LoadingPlayerProgress()
        } else {
            PlayerProgressValues(
                level = level,
                currentExp = currentExp,
                requiredExp = requiredExp,
                gold = gold,
            )
        }
    }
}

@Composable
private fun LoadingPlayerProgress() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.secondary,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.battle_player_progress_loading_description),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlayerProgressValues(
    level: Int,
    currentExp: Long,
    requiredExp: Long,
    gold: Long,
) {
    val unitLabels = HudNumberUnitLabels(
        man = stringResource(R.string.battle_number_unit_man),
        eok = stringResource(R.string.battle_number_unit_eok),
        jo = stringResource(R.string.battle_number_unit_jo),
        gyeong = stringResource(R.string.battle_number_unit_gyeong),
    )
    val goldDescription = stringResource(
        R.string.battle_player_progress_gold_description,
        formatExactHudNumber(gold),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.battle_player_progress_level,
                formatCompactHudNumber(level.toLong(), unitLabels),
            ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .testTag(PlayerProgressLevelTag),
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .testTag(PlayerProgressSummaryTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .testTag(PlayerProgressGoldGroupTag)
                    .semantics(mergeDescendants = true) {
                        contentDescription = goldDescription
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag(PlayerProgressGoldIconTag),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = formatCompactHudNumber(gold, unitLabels),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(PlayerProgressGoldTag),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = PlayerProgressExpMinimumWidth)
                    .testTag(PlayerProgressExpGroupTag),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlayerProgressExpContentTag),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.battle_player_progress_exp_label),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(PlayerProgressExpLabelTag),
                    )
                    Text(
                        text = stringResource(
                            R.string.battle_player_progress_exp_value,
                            formatCompactHudNumber(currentExp, unitLabels),
                            formatCompactHudNumber(requiredExp, unitLabels),
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(PlayerProgressExpValueTag),
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                val progress = calculatePlayerProgress(currentExp, requiredExp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        .testTag(PlayerProgressBarTag)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        },
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
            }
        }
    }
}

internal fun calculatePlayerProgress(currentExp: Long, requiredExp: Long): Float {
    if (requiredExp <= 0 || currentExp <= 0) return 0f
    if (currentExp >= requiredExp) return 1f
    return (currentExp.toDouble() / requiredExp.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun formatExactHudNumber(value: Long): String =
    String.format(Locale.KOREA, "%,d", value)

internal fun formatCompactHudNumber(
    value: Long,
    unitLabels: HudNumberUnitLabels,
): String {
    val units = listOf(
        CompactHudUnit(BigDecimal("10000000000000000"), unitLabels.gyeong),
        CompactHudUnit(BigDecimal("1000000000000"), unitLabels.jo),
        CompactHudUnit(BigDecimal("100000000"), unitLabels.eok),
        CompactHudUnit(BigDecimal("10000"), unitLabels.man),
    )
    val unit = units.firstOrNull { candidate ->
        BigDecimal.valueOf(value).abs() >= candidate.divisor
    } ?: return formatExactHudNumber(value)
    val scaled = BigDecimal.valueOf(value)
        .divide(unit.divisor, 1, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()
    return scaled + unit.label
}

private data class CompactHudUnit(
    val divisor: BigDecimal,
    val label: String,
)

internal data class HudNumberUnitLabels(
    val man: String,
    val eok: String,
    val jo: String,
    val gyeong: String,
)

private const val PlayerProgressHudTag = "player-progress-hud"
private const val PlayerProgressLevelTag = "player-progress-level"
private const val PlayerProgressSummaryTag = "player-progress-summary"
private const val PlayerProgressGoldGroupTag = "player-progress-gold-group"
private const val PlayerProgressGoldIconTag = "player-progress-gold-icon"
private const val PlayerProgressGoldTag = "player-progress-gold"
private const val PlayerProgressExpGroupTag = "player-progress-exp-group"
private const val PlayerProgressExpContentTag = "player-progress-exp-content"
private const val PlayerProgressExpLabelTag = "player-progress-exp-label"
private const val PlayerProgressExpValueTag = "player-progress-exp-value"
private const val PlayerProgressBarTag = "player-progress-bar"
private val PlayerProgressExpMinimumWidth = 104.dp
