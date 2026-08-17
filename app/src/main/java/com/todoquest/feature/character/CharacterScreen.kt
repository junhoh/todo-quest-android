package com.todoquest.feature.character

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todoquest.R
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.feature.battle.SevereInjuryBadge
import com.todoquest.feature.battle.SevereInjuryDetailsDialog
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.character.LayeredCharacterSprite
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CharacterScreen(
    viewModel: CharacterViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.onScreenEntered()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onLifecycleResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CharacterContent(
        state = state,
        onIncreaseStat = viewModel::increaseStat,
        onDecreaseStat = viewModel::decreaseStat,
        onSaveStatAllocation = viewModel::saveStatAllocation,
        onRequestStatReset = viewModel::requestStatReset,
        onDismissStatReset = viewModel::dismissStatResetConfirmation,
        onConfirmStatReset = viewModel::confirmStatReset,
        onDismissError = viewModel::dismissError,
        onShowBaseStatDescription = viewModel::showBaseStatDescription,
        onShowDerivedStatDescription = viewModel::showDerivedStatDescription,
        onDismissStatDescription = viewModel::dismissStatDescription,
        onShowStatAllocationGuide = viewModel::showStatAllocationGuide,
        onDismissStatAllocationGuide = viewModel::dismissStatAllocationGuide,
        onShowStatusEffectDetails = viewModel::showStatusEffectDetails,
        onDismissStatusEffectDetails = viewModel::dismissStatusEffectDetails,
        modifier = modifier,
    )
}

@Composable
internal fun CharacterContent(
    state: CharacterUiState,
    onIncreaseStat: (StatType) -> Unit,
    onDecreaseStat: (StatType) -> Unit,
    onSaveStatAllocation: () -> Unit,
    onRequestStatReset: () -> Unit,
    onDismissStatReset: () -> Unit,
    onConfirmStatReset: () -> Unit,
    onDismissError: () -> Unit,
    onShowBaseStatDescription: (StatType) -> Unit = {},
    onShowDerivedStatDescription: (DerivedStatType) -> Unit = {},
    onDismissStatDescription: () -> Unit = {},
    onShowStatAllocationGuide: () -> Unit = {},
    onDismissStatAllocationGuide: () -> Unit = {},
    onShowStatusEffectDetails: (StatusEffectType) -> Unit = {},
    onDismissStatusEffectDetails: () -> Unit = {},
    @DrawableRes statGuideSpriteResId: Int = R.drawable.todo_quest_fairy_guide_front_idle,
    modifier: Modifier = Modifier,
) {
    val screenScrollState = rememberScrollState()
    val baseStatsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val loadingDescription = stringResource(R.string.character_loading_description)
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = loadingDescription
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(screenScrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("character-screen-scroll"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CharacterSummary(
                    state = state,
                    onShowStatusEffectDetails = onShowStatusEffectDetails,
                )
                GrowthSection(state)
                BaseStatsSection(
                    state = state,
                    onIncreaseStat = onIncreaseStat,
                    onDecreaseStat = onDecreaseStat,
                    onSaveStatAllocation = onSaveStatAllocation,
                    onRequestStatReset = onRequestStatReset,
                    onShowStatDescription = onShowBaseStatDescription,
                    onShowStatAllocationGuide = onShowStatAllocationGuide,
                    modifier = Modifier.bringIntoViewRequester(
                        baseStatsBringIntoViewRequester,
                    ),
                )
                DerivedStatsSection(
                    state = state,
                    onShowStatDescription = onShowDerivedStatDescription,
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (state.isStatAllocationGuideVisible) {
        CharacterStatAllocationGuideDialog(
            availablePoints = state.remainingUnspentPoints,
            spriteResId = statGuideSpriteResId,
            onPrimaryAction = {
                onDismissStatAllocationGuide()
                coroutineScope.launch {
                    baseStatsBringIntoViewRequester.bringIntoView()
                }
            },
            onDismiss = onDismissStatAllocationGuide,
        )
    }

    state.resetConfirmation?.let { confirmation ->
        StatResetConfirmationDialog(
            confirmation = confirmation,
            onDismiss = onDismissStatReset,
            onConfirm = onConfirmStatReset,
        )
    }

    state.statDescription?.let { target ->
        StatDescriptionDialog(
            target = target,
            onDismiss = onDismissStatDescription,
        )
    }


    state.selectedStatusEffect?.let { effect ->
        SevereInjuryDetailsDialog(
            effect = effect,
            onDismiss = onDismissStatusEffectDetails,
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            modifier = Modifier.testTag("character-error-dialog"),
            title = { Text(text = stringResource(R.string.character_error_dialog_title)) },
            text = { Text(text = message.displayText()) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(text = stringResource(R.string.character_confirm))
                }
            },
        )
    }
}

@Composable
private fun CharacterSummary(
    state: CharacterUiState,
    onShowStatusEffectDetails: (StatusEffectType) -> Unit,
) {
    CharacterSection(title = stringResource(R.string.character_section)) {
        LayeredCharacterSprite(
            renderState = CharacterRenderState(
                appearance = state.appearance,
                equippedItems = state.equippedItems,
            ),
            contentDescription = stringResource(R.string.character_adventurer_description),
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .testTag("equipped-character-sprite"),
        )
        Text(
            text = if (state.isMaxLevel) {
                stringResource(R.string.character_max_level)
            } else {
                stringResource(R.string.character_level, state.level)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryValue(
                label = stringResource(R.string.character_hp),
                value = stringResource(
                    R.string.character_hp_value,
                    formatNumber(state.currentHp.toLong()),
                    formatNumber(state.maxHp.toLong()),
                ),
            )
            SummaryValue(
                label = stringResource(R.string.character_gold),
                value = formatNumber(state.gold),
            )
        }
        state.activeStatusEffects.firstOrNull {
            it.type == StatusEffectType.SEVERE_INJURY
        }?.let { effect ->
            SevereInjuryBadge(
                effect = effect,
                onClick = { onShowStatusEffectDetails(effect.type) },
                testTag = "character-severe-injury-badge",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun GrowthSection(state: CharacterUiState) {
    val xpProgressDescription = if (state.isMaxLevel) {
        stringResource(
            R.string.character_max_xp_progress_description,
            formatNumber(state.totalXp),
        )
    } else {
        stringResource(
            R.string.character_xp_progress_description,
            formatNumber(state.xpIntoCurrentLevel),
            formatNumber(state.xpRequiredForNextLevel),
        )
    }
    CharacterSection(title = stringResource(R.string.character_growth_section)) {
        Text(
            text = stringResource(
                R.string.character_total_xp,
                formatNumber(state.totalXp),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { state.xpProgress },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 8.dp)
                .semantics {
                    contentDescription = xpProgressDescription
                },
        )
        if (!state.isMaxLevel) {
            Text(
                text = stringResource(
                    R.string.character_xp_progress,
                    formatNumber(state.xpIntoCurrentLevel),
                    formatNumber(state.xpRequiredForNextLevel),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.character_streak, state.streakDays),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.character_momentum, state.momentumBonus),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BaseStatsSection(
    state: CharacterUiState,
    onIncreaseStat: (StatType) -> Unit,
    onDecreaseStat: (StatType) -> Unit,
    onSaveStatAllocation: () -> Unit,
    onRequestStatReset: () -> Unit,
    onShowStatDescription: (StatType) -> Unit,
    onShowStatAllocationGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CharacterSection(
        title = stringResource(R.string.character_base_stats_section),
        modifier = modifier.testTag("character-base-stats-section"),
        titleAction = {
            FilledTonalIconButton(
                onClick = onShowStatAllocationGuide,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("character-stat-guide-help"),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(
                        R.string.character_stat_guide_help_description,
                    ),
                )
            }
        },
    ) {
        Text(
            text = stringResource(
                R.string.character_unspent_stat_points,
                state.remainingUnspentPoints,
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        state.baseStats.forEach { stat ->
            BaseStatRow(
                stat = stat,
                canIncrease = state.remainingUnspentPoints > 0 &&
                    stat.expectedValue < CharacterInvestmentCap &&
                    !state.isSavingStatAllocation,
                canDecrease = stat.pendingIncrease > 0 && !state.isSavingStatAllocation,
                onIncrease = { onIncreaseStat(stat.type) },
                onDecrease = { onDecreaseStat(stat.type) },
                onShowDescription = { onShowStatDescription(stat.type) },
            )
        }
        Button(
            onClick = onSaveStatAllocation,
            enabled = state.hasPendingStatAllocation && !state.isSavingStatAllocation,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("save-stat-allocation-button"),
        ) {
            Text(
                text = if (state.isSavingStatAllocation) {
                    stringResource(R.string.character_save_stat_allocation_in_progress)
                } else {
                    stringResource(R.string.character_save_stat_allocation)
                },
            )
        }
        OutlinedButton(
            onClick = onRequestStatReset,
            enabled = state.canReset,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("reset-stats-button"),
        ) {
            Text(
                text = if (state.isResetFree) {
                    stringResource(R.string.character_reset_free)
                } else {
                    stringResource(
                        R.string.character_reset_paid,
                        formatNumber(state.resetCostGold),
                    )
                },
            )
        }
        state.resetUnavailableReason?.let { reason ->
            Text(
                text = reason.displayText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DerivedStatsSection(
    state: CharacterUiState,
    onShowStatDescription: (DerivedStatType) -> Unit,
) {
    CharacterSection(title = stringResource(R.string.character_derived_stats_section)) {
        state.derivedStats.forEach { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onShowStatDescription(stat.type) }
                    .testTag("derived-stat-${stat.type.tagName()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stat.type.displayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stat.displayValue,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CharacterSection(
    title: String,
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                titleAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BaseStatRow(
    stat: BaseStatUiState,
    canIncrease: Boolean,
    canDecrease: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onShowDescription: () -> Unit,
) {
    val statName = stat.type.displayName()
    val statValueDescription = if (stat.pendingIncrease > 0) {
        stringResource(
            R.string.character_stat_pending_value_description,
            statName,
            stat.confirmedValue,
            stat.pendingIncrease,
            stat.expectedValue,
        )
    } else {
        stringResource(
            R.string.character_stat_confirmed_value_description,
            statName,
            stat.confirmedValue,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(onClick = onShowDescription)
                .testTag("base-stat-${stat.type.tagName()}"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = statName,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(
            modifier = Modifier.testTag("stat-controls-${stat.type.tagName()}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onDecrease,
                enabled = canDecrease,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove-${stat.type.tagName()}"),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(
                        R.string.character_decrease_stat_description,
                        statName,
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = statValueDescription
                    }
                    .testTag("stat-value-${stat.type.tagName()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stat.expectedValue.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (stat.pendingIncrease > 0) {
                    Text(
                        text = stringResource(
                            R.string.character_pending_stat_increase,
                            stat.pendingIncrease,
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("add-${stat.type.tagName()}"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(
                        R.string.character_increase_stat_description,
                        statName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CharacterStatAllocationGuideDialog(
    availablePoints: Int,
    @DrawableRes spriteResId: Int,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val sprite = remember(resources, spriteResId) {
        runCatching { ImageBitmap.imageResource(resources, spriteResId) }.getOrNull()
    }
    val speaker = stringResource(R.string.character_stat_guide_speaker)
    val intro = stringResource(R.string.character_stat_guide_intro)
    val effects = stringResource(R.string.character_stat_guide_effects)
    val instructions = stringResource(R.string.character_stat_guide_instructions)
    val pointStatus = if (availablePoints > 0) {
        stringResource(R.string.character_stat_guide_available_points, availablePoints)
    } else {
        stringResource(R.string.character_stat_guide_no_points)
    }
    val copyDescription = "$speaker. " + listOf(
        intro,
        effects,
        instructions,
        pointStatus,
    ).joinToString(separator = " ")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 560.dp)
                .testTag("character-stat-guide-dialog"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.character_stat_guide_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FairyGuideSprite(
                        sprite = sprite,
                        modifier = Modifier.size(96.dp),
                    )
                    Text(
                        text = speaker,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag("character-stat-guide-body-scroll")
                        .semantics(mergeDescendants = true) {
                            contentDescription = copyDescription
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(text = intro, style = MaterialTheme.typography.bodyLarge)
                    Text(text = effects, style = MaterialTheme.typography.bodyLarge)
                    Text(text = instructions, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = pointStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("character-stat-guide-primary"),
                ) {
                    Text(text = stringResource(R.string.character_stat_guide_primary_action))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("character-stat-guide-secondary"),
                ) {
                    Text(text = stringResource(R.string.character_stat_guide_secondary_action))
                }
            }
        }
    }
}

@Composable
private fun FairyGuideSprite(
    sprite: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag("character-stat-guide-sprite-frame"),
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) {
            Image(
                bitmap = sprite,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("character-stat-guide-sprite"),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("character-stat-guide-fallback"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatDescriptionDialog(
    target: StatDescriptionTarget,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("stat-description-dialog"),
        title = { Text(text = target.displayName()) },
        text = { Text(text = target.description()) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.character_confirm))
            }
        },
    )
}

@Composable
private fun StatResetConfirmationDialog(
    confirmation: ResetConfirmationUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("stat-reset-confirmation"),
        title = { Text(text = stringResource(R.string.character_reset_dialog_title)) },
        text = {
            Text(
                text = if (confirmation.isFree) {
                    stringResource(R.string.character_reset_dialog_free_message)
                } else {
                    stringResource(
                        R.string.character_reset_dialog_paid_message,
                        formatNumber(confirmation.costGold),
                    )
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-stat-reset"),
            ) {
                Text(text = stringResource(R.string.character_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel-stat-reset"),
            ) {
                Text(text = stringResource(R.string.character_cancel))
            }
        },
    )
}

@Composable
private fun StatType.displayName(): String = when (this) {
    StatType.STRENGTH -> stringResource(R.string.character_stat_strength)
    StatType.VITALITY -> stringResource(R.string.character_stat_vitality)
    StatType.FOCUS -> stringResource(R.string.character_stat_focus)
    StatType.WILLPOWER -> stringResource(R.string.character_stat_willpower)
}

@Composable
private fun DerivedStatType.displayName(): String = when (this) {
    DerivedStatType.MAX_HP -> stringResource(R.string.character_derived_max_hp)
    DerivedStatType.ATTACK -> stringResource(R.string.character_derived_attack)
    DerivedStatType.DEFENSE -> stringResource(R.string.character_derived_defense)
    DerivedStatType.CRITICAL_CHANCE -> stringResource(R.string.character_derived_critical_chance)
    DerivedStatType.CRITICAL_DAMAGE -> stringResource(R.string.character_derived_critical_damage)
    DerivedStatType.STATUS_RESISTANCE ->
        stringResource(R.string.character_derived_status_resistance)
    DerivedStatType.HP_RECOVERY -> stringResource(R.string.character_derived_hp_recovery)
    DerivedStatType.GOLD_GAIN_BONUS ->
        stringResource(R.string.character_derived_gold_gain_bonus)
}

@Composable
private fun StatDescriptionTarget.displayName(): String = when (this) {
    is StatDescriptionTarget.Base -> type.displayName()
    is StatDescriptionTarget.Derived -> type.displayName()
}

@Composable
private fun StatDescriptionTarget.description(): String = when (this) {
    is StatDescriptionTarget.Base -> when (type) {
        StatType.STRENGTH -> stringResource(R.string.character_stat_strength_description)
        StatType.VITALITY -> stringResource(R.string.character_stat_vitality_description)
        StatType.FOCUS -> stringResource(R.string.character_stat_focus_description)
        StatType.WILLPOWER -> stringResource(R.string.character_stat_willpower_description)
    }

    is StatDescriptionTarget.Derived -> when (type) {
        DerivedStatType.MAX_HP -> stringResource(R.string.character_derived_max_hp_description)
        DerivedStatType.ATTACK -> stringResource(R.string.character_derived_attack_description)
        DerivedStatType.DEFENSE -> stringResource(R.string.character_derived_defense_description)
        DerivedStatType.CRITICAL_CHANCE ->
            stringResource(R.string.character_derived_critical_chance_description)
        DerivedStatType.CRITICAL_DAMAGE ->
            stringResource(R.string.character_derived_critical_damage_description)
        DerivedStatType.STATUS_RESISTANCE ->
            stringResource(R.string.character_derived_status_resistance_description)
        DerivedStatType.HP_RECOVERY ->
            stringResource(R.string.character_derived_hp_recovery_description)
        DerivedStatType.GOLD_GAIN_BONUS ->
            stringResource(R.string.character_derived_gold_gain_bonus_description)
    }
}

@Composable
private fun CharacterUiMessage.displayText(): String = when (this) {
    CharacterUiMessage.LoadFailed -> stringResource(R.string.character_error_load_failed)
    CharacterUiMessage.NoUnspentStatPoints ->
        stringResource(R.string.character_error_no_unspent_points)
    is CharacterUiMessage.StatAtInvestmentCap -> stringResource(
        R.string.character_error_stat_cap,
        type.displayName(),
        investmentCap,
    )
    CharacterUiMessage.AllocationFailed ->
        stringResource(R.string.character_error_allocation_failed)
    CharacterUiMessage.NothingToReset ->
        stringResource(R.string.character_error_nothing_to_reset)
    is CharacterUiMessage.InsufficientGold -> stringResource(
        R.string.character_error_insufficient_gold,
        formatNumber(requiredGold),
        formatNumber(availableGold),
    )
    CharacterUiMessage.ResetUnavailable ->
        stringResource(R.string.character_error_reset_unavailable)
    CharacterUiMessage.PendingStatAllocation ->
        stringResource(R.string.character_pending_stat_reset_unavailable)
    CharacterUiMessage.ResetFailed -> stringResource(R.string.character_error_reset_failed)
    CharacterUiMessage.LoadoutUpdateFailed ->
        stringResource(R.string.character_error_loadout_update_failed)
}

private fun StatType.tagName(): String = name.lowercase(Locale.US)

private fun DerivedStatType.tagName(): String = name.lowercase(Locale.US)

private fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

private const val CharacterInvestmentCap = 60
