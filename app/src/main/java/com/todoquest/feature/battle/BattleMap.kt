package com.todoquest.feature.battle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.todoquest.R
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.ui.character.LayeredCharacterSprite
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun BattleMap(
    state: BattleMapUiState,
    modifier: Modifier = Modifier,
    theme: BattleMapTheme = BattleMapTheme(),
    heightPolicy: BattleMapHeightPolicy = BattleMapHeightPolicy.STANDARD,
    presentation: BattlePresentationState = BattlePresentationState(),
    activeStatusEffects: List<ActiveStatusEffectUiModel> = emptyList(),
    onStatusEffectClick: (StatusEffectType) -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val severeInjury = activeStatusEffects.firstOrNull {
            it.type == StatusEffectType.SEVERE_INJURY
        }
        val baseMapHeight = (maxWidth / BattleMapAspectRatio).coerceIn(
            minimumValue = heightPolicy.minimumHeight,
            maximumValue = heightPolicy.maximumHeight,
        )
        val mapHeight = if (
            severeInjury != null && heightPolicy == BattleMapHeightPolicy.COMPACT
        ) {
            maxOf(baseMapHeight, InjuryCompactMapHeight)
        } else {
            baseMapHeight
        }
        val compactHeight = heightPolicy == BattleMapHeightPolicy.COMPACT
        val baseUnitHeight = (mapHeight * BaseUnitHeightRatio).coerceIn(
            minimumValue = if (compactHeight) CompactMinimumBaseUnitHeight else MinimumBaseUnitHeight,
            maximumValue = if (compactHeight) CompactMaximumBaseUnitHeight else MaximumBaseUnitHeight,
        )
        val density = LocalDensity.current
        val mapWidthPx = with(density) { maxWidth.toPx() }
        val mapHeightPx = with(density) { mapHeight.toPx() }
        val baseUnitHeightPx = with(density) { baseUnitHeight.toPx() }
        val renderedState = presentation.sceneOverride ?: state
        val sceneUnits = (renderedState as? BattleMapUiState.Content)?.let { content ->
            listOf(content.player) + content.monsters
        }.orEmpty()
        val units = if (presentation.phase == BattleAnimationPhase.MONSTER_SPAWN_ALERT) {
            sceneUnits.filterNot { it.type == BattleUnitType.MONSTER }
        } else {
            sceneUnits
        }
        val depthOrderedUnits = BattleMapLayout.orderByDepth(units)
        val shape = MaterialTheme.shapes.large
        var hudHeightPx by remember { mutableIntStateOf(0) }
        val hudBottomPx = with(density) { BattleHudTopInset.toPx() } + hudHeightPx
        val healthBarHeightPx = with(density) {
            HealthPanelVerticalPadding.toPx() * 2f +
                HealthTextLineHeight.toPx() +
                HealthProgressSpacing.toPx() +
                HealthProgressHeight.toPx()
        }
        val actorGapPx = with(density) { HealthActorGap.toPx() }
        val statusBadgeHeightPx = with(density) {
            maxOf(
                StatusBadgeMinimumHeight.toPx(),
                StatusBadgeTextLineHeight.toPx() + StatusBadgeVerticalPadding.toPx() * 2f,
            )
        }
        val rawPlacements = units.associate { unit ->
            unit.id to calculatePlacement(
                unit = unit,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                baseUnitHeightPx = baseUnitHeightPx,
            )
        }
        val actorLayouts = rawPlacements.mapValues { (id, rawPlacement) ->
            val unit = requireNotNull(units.firstOrNull { it.id == id })
            val rawHealthPlacement = healthBarPlacement(
                spritePlacement = rawPlacement,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                barHeightPx = healthBarHeightPx,
                density = density,
            )
            if (unit.type == BattleUnitType.PLAYER && severeInjury != null) {
                val statusLayout = with(density) {
                    BattleMapLayout.calculatePlayerStatusLayout(
                        mapWidth = mapWidthPx,
                        mapHeight = mapHeightPx,
                        spritePlacement = rawPlacement,
                        hudBottom = hudBottomPx,
                        healthMinimumGap = HealthMinimumGap.toPx(),
                        healthHeight = healthBarHeightPx,
                        healthWidthRatio = HealthWidthRatio,
                        healthMinimumWidth = HealthMinimumWidth.toPx(),
                        healthMaximumWidth = HealthMaximumWidth.toPx(),
                        healthBadgeGap = StatusHealthGap.toPx(),
                        badgeHeight = statusBadgeHeightPx,
                        badgeWidth = StatusBadgeWidth.toPx(),
                        badgeActorGap = StatusActorGap.toPx(),
                    )
                }
                BattleActorLayout(
                    sprite = statusLayout.sprite,
                    health = statusLayout.health,
                    statusBadge = statusLayout.statusBadge,
                )
            } else {
                val requiredActorTop = rawHealthPlacement.top +
                    rawHealthPlacement.height + actorGapPx
                val sprite = rawPlacement.copy(
                    top = maxOf(rawPlacement.top, requiredActorTop)
                    .coerceAtMost((mapHeightPx - rawPlacement.height).coerceAtLeast(0f)),
                )
                BattleActorLayout(
                    sprite = sprite,
                    health = healthBarPlacement(
                        spritePlacement = sprite,
                        mapWidthPx = mapWidthPx,
                        mapHeightPx = mapHeightPx,
                        hudBottomPx = hudBottomPx,
                        barHeightPx = healthBarHeightPx,
                        density = density,
                    ),
                )
            }
        }
        val placements = actorLayouts.mapValues { it.value.sprite }
        val healthPlacements = actorLayouts.mapValues { it.value.health }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .shadow(elevation = 3.dp, shape = shape)
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = shape,
                )
                .testTag("battle-map"),
        ) {
            BattleBackground(
                backgroundResId = theme.backgroundResId,
                forceFallback = renderedState is BattleMapUiState.Unavailable,
            )
            if (theme.showDecorations) {
                BattleDecorations()
            }
            BattleGroundLayer(
                units = units,
                placements = placements,
            )

            depthOrderedUnits.forEach { depth ->
                val placement = requireNotNull(placements[depth.unit.id])
                val animatedModifier = animatedUnitModifier(
                    unit = depth.unit,
                    presentation = presentation,
                )
                when (depth.unit.type) {
                    BattleUnitType.PLAYER -> PlayerLayer(
                        unit = depth.unit,
                        placement = placement,
                        zIndex = UnitLayerZIndex + depth.zIndex,
                        effectModifier = animatedModifier,
                    )

                    BattleUnitType.MONSTER -> MonsterLayer(
                        unit = depth.unit,
                        placement = placement,
                        zIndex = UnitLayerZIndex + depth.zIndex,
                        effectModifier = animatedModifier,
                    )
                }
            }

            BattleHealthLayer(
                units = units,
                healthPlacements = healthPlacements,
            )
            severeInjury?.let { effect ->
                val player = units.firstOrNull { it.type == BattleUnitType.PLAYER }
                val badgePlacement = player?.let { actorLayouts[it.id]?.statusBadge }
                if (player != null && badgePlacement != null) {
                    BattleStatusEffectLayer(
                        effect = effect,
                        placement = badgePlacement,
                        onClick = { onStatusEffectClick(effect.type) },
                    )
                }
            }
            BattleEffectLayer(
                presentation = presentation,
                units = units,
                placements = placements,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
            )
            BattleRewardLayer(presentation = presentation)

            when (renderedState) {
                BattleMapUiState.Loading -> BattleLoadingState()
                is BattleMapUiState.Content -> Unit
                BattleMapUiState.Unavailable -> BattleUnavailableState()
            }
            CompositionLocalProvider(
                LocalBattleHudSizeReporter provides { size -> hudHeightPx = size.height },
            ) {
                BattleOverlayLayer(content = overlayContent)
            }
        }
    }
}

@Composable
private fun BoxScope.BattleRewardLayer(presentation: BattlePresentationState) {
    var visibleReward by remember { mutableStateOf<BattleRewardFeedback?>(null) }
    var visibleSequenceId by remember { mutableStateOf<Long?>(null) }
    val latestPresentation by rememberUpdatedState(presentation)

    LaunchedEffect(Unit) {
        snapshotFlow {
            latestPresentation.sequenceId to latestPresentation.rewardFeedback
        }
            .filter { (sequenceId, reward) -> sequenceId != null && reward != null }
            .distinctUntilChanged()
            .collect { (sequenceId, reward) ->
                visibleSequenceId = requireNotNull(sequenceId)
                visibleReward = requireNotNull(reward)
            }
    }
    LaunchedEffect(visibleSequenceId) {
        val sequenceId = visibleSequenceId ?: return@LaunchedEffect
        delay(BattleRewardFeedbackDurationMillis)
        if (visibleSequenceId == sequenceId) {
            visibleReward = null
            visibleSequenceId = null
        }
    }

    visibleReward?.let { reward ->
        val description = if (reward.isVictory) {
            stringResource(
                R.string.battle_reward_victory,
                reward.xpAward,
                reward.goldAward,
            )
        } else {
            stringResource(R.string.battle_reward_hit, reward.xpAward)
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-28).dp)
                .zIndex(StatusLayerZIndex)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag(BattleRewardEffectTag)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.BattleBackground(
    @DrawableRes backgroundResId: Int,
    forceFallback: Boolean,
) {
    val resources = LocalContext.current.resources
    val background = remember(backgroundResId, forceFallback, resources) {
        if (forceFallback) {
            null
        } else {
            runCatching {
                ImageBitmap.imageResource(resources, backgroundResId)
            }.getOrNull()
        }
    }

    if (background != null) {
        Image(
            bitmap = background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(BackgroundLayerZIndex)
                .testTag("battle-background-image"),
        )
    } else {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(BackgroundLayerZIndex)
                .testTag("battle-background-fallback"),
        ) {
            drawFallbackSkyAndMountains()
            drawFallbackForest()
            drawFallbackFieldAndRoad()
            drawFallbackGroundDetails()
        }
    }
}

@Composable
private fun BoxScope.BattleDecorations() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(DecorationLayerZIndex)
            .testTag("battle-decorations-layer"),
    ) {
        val grassColor = Color(0xAA496B3A)
        val pebbleColor = Color(0x996B6757)
        listOf(0.08f to 0.76f, 0.42f to 0.69f).forEach { (x, y) ->
            val origin = Offset(size.width * x, size.height * y)
            drawLine(
                color = grassColor,
                start = origin,
                end = origin + Offset(-5.dp.toPx(), -8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = grassColor,
                start = origin,
                end = origin + Offset(4.dp.toPx(), -7.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        drawOval(
            color = pebbleColor,
            topLeft = Offset(size.width * 0.46f, size.height * 0.83f),
            size = Size(8.dp.toPx(), 4.dp.toPx()),
        )
    }
}

@Composable
private fun BoxScope.BattleGroundLayer(
    units: List<BattleUnitUiModel>,
    placements: Map<String, BattleUnitPlacement>,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(GroundLayerZIndex)
            .testTag("battle-ground-layer"),
    ) {
        units.forEach { unit ->
            val placement = requireNotNull(placements[unit.id])
            val anchor = Offset(
                x = placement.left + placement.width * unit.sprite.frame.groundAnchor.x,
                y = placement.top + placement.height * unit.sprite.frame.groundAnchor.y,
            )
            val shadowWidth = placement.width * 0.56f
            val shadowHeight = (placement.height * 0.10f).coerceAtLeast(4.dp.toPx())
            drawOval(
                color = Color.Black.copy(alpha = 0.24f),
                topLeft = Offset(
                    x = anchor.x - shadowWidth / 2f,
                    y = anchor.y - shadowHeight * 0.15f,
                ),
                size = Size(width = shadowWidth, height = shadowHeight),
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerLayer(
    unit: BattleUnitUiModel,
    placement: BattleUnitPlacement,
    zIndex: Float,
    effectModifier: Modifier,
) {
    val sprite = unit.sprite as BattleSpriteUiModel.LayeredCharacter
    val name = stringResource(unit.nameResId)
    val density = LocalDensity.current
    val width = with(density) { placement.width.toDp() }
    val height = with(density) { placement.height.toDp() }
    LayeredCharacterSprite(
        renderState = sprite.renderState,
        contentDescription = name,
        verticalAnchor = sprite.frame.groundAnchor.y,
        modifier = effectModifier
            .zIndex(zIndex)
            .offset {
                IntOffset(
                    x = placement.left.roundToInt(),
                    y = placement.top.roundToInt(),
                )
            }
            .size(width = width, height = height)
            .testTag("battle-player-layer"),
    )
}

@Composable
private fun BoxScope.MonsterLayer(
    unit: BattleUnitUiModel,
    placement: BattleUnitPlacement,
    zIndex: Float,
    effectModifier: Modifier,
) {
    ResourceBattleUnitSprite(
        unit = unit,
        placement = placement,
        testTag = "battle-monster-layer",
        modifier = effectModifier.zIndex(zIndex),
    )
}

@Composable
private fun BoxScope.ResourceBattleUnitSprite(
    unit: BattleUnitUiModel,
    placement: BattleUnitPlacement,
    testTag: String,
    modifier: Modifier,
) {
    val sprite = unit.sprite as BattleSpriteUiModel.Resource
    val resources = LocalContext.current.resources
    val bitmap = remember(sprite.spriteResId, resources) {
        runCatching {
            ImageBitmap.imageResource(resources, sprite.spriteResId)
        }.getOrNull()
    }
    val frame = sprite.frame
    val name = stringResource(unit.nameResId)
    val density = LocalDensity.current
    val width = with(density) { placement.width.toDp() }
    val height = with(density) { placement.height.toDp() }

    Canvas(
        modifier = modifier
            .offset {
                IntOffset(
                    x = placement.left.roundToInt(),
                    y = placement.top.roundToInt(),
                )
            }
            .size(width = width, height = height)
            .testTag(testTag)
            .semantics {
                contentDescription = name
            },
    ) {
        val image = bitmap ?: return@Canvas
        if (
            frame.sourceX + frame.sourceWidth > image.width ||
            frame.sourceY + frame.sourceHeight > image.height
        ) {
            return@Canvas
        }
        val sourceSize = Size(
            width = frame.sourceWidth.toFloat(),
            height = frame.sourceHeight.toFloat(),
        )
        val fitScale = ContentScale.Fit.computeScaleFactor(
            srcSize = sourceSize,
            dstSize = size,
        )
        val destinationWidth = (sourceSize.width * fitScale.scaleX).roundToInt()
        val destinationHeight = (sourceSize.height * fitScale.scaleY).roundToInt()
        val destinationOffset = IntOffset(
            x = ((size.width - destinationWidth) / 2f).roundToInt(),
            y = ((size.height - destinationHeight) / 2f).roundToInt(),
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(frame.sourceX, frame.sourceY),
            srcSize = IntSize(frame.sourceWidth, frame.sourceHeight),
            dstOffset = destinationOffset,
            dstSize = IntSize(destinationWidth, destinationHeight),
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
private fun animatedUnitModifier(
    unit: BattleUnitUiModel,
    presentation: BattlePresentationState,
): Modifier {
    val density = LocalDensity.current
    val isAttacking = when (presentation.phase) {
        BattleAnimationPhase.PLAYER_ATTACKING -> unit.type == BattleUnitType.PLAYER
        BattleAnimationPhase.MONSTER_ATTACKING -> unit.type == BattleUnitType.MONSTER
        else -> false
    }
    val isHit = when (presentation.phase) {
        BattleAnimationPhase.MONSTER_HIT -> unit.type == BattleUnitType.MONSTER
        BattleAnimationPhase.PLAYER_HIT -> unit.type == BattleUnitType.PLAYER
        else -> false
    }
    val isDying = when (presentation.phase) {
        BattleAnimationPhase.MONSTER_DYING -> unit.type == BattleUnitType.MONSTER
        BattleAnimationPhase.PLAYER_DYING -> unit.type == BattleUnitType.PLAYER
        else -> false
    }
    val isSpawning = presentation.phase == BattleAnimationPhase.MONSTER_SPAWNING &&
        unit.type == BattleUnitType.MONSTER
    val isEmergencyRecovering =
        presentation.phase == BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING &&
        unit.type == BattleUnitType.PLAYER
    val advanceTarget = when {
        !isAttacking -> 0f
        unit.type == BattleUnitType.PLAYER -> with(density) { AttackAdvance.toPx() }
        else -> with(density) { -AttackAdvance.toPx() }
    }
    val advanceX by animateFloatAsState(
        targetValue = advanceTarget,
        animationSpec = tween(durationMillis = AttackAdvanceDurationMillis),
        label = "battle-attack-advance",
    )
    val shake = remember(unit.id, presentation.sequenceId) { Animatable(0f) }
    val flash = remember(unit.id, presentation.sequenceId) { Animatable(0f) }
    val deathProgress = remember(unit.id, presentation.sequenceId) { Animatable(0f) }
    val entranceAlpha = remember(unit.id, presentation.sequenceId) {
        Animatable(if (isSpawning || isEmergencyRecovering) 0f else 1f)
    }

    LaunchedEffect(presentation.sequenceId, presentation.phase, unit.id) {
        if (isHit) {
            val distance = with(density) { HitShakeDistance.toPx() }
            shake.snapTo(0f)
            listOf(distance, -distance, distance * 0.6f, -distance * 0.6f, 0f)
                .forEach { target ->
                    shake.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = HitShakeStepDurationMillis),
                    )
                }
        } else {
            shake.snapTo(0f)
        }
    }
    LaunchedEffect(presentation.sequenceId, presentation.phase, unit.id) {
        if (isHit || isDying) {
            flash.snapTo(1f)
            flash.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (isDying) DeathDurationMillis else HitFlashDurationMillis,
                ),
            )
        } else {
            flash.snapTo(0f)
        }
    }
    LaunchedEffect(presentation.sequenceId, presentation.phase, unit.id) {
        if (isDying) {
            deathProgress.snapTo(0f)
            deathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = DeathDurationMillis),
            )
        } else {
            deathProgress.snapTo(0f)
        }
    }
    LaunchedEffect(presentation.sequenceId, presentation.phase, unit.id) {
        if (isSpawning || isEmergencyRecovering) {
            entranceAlpha.snapTo(0f)
            entranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = SpawnDurationMillis),
            )
        } else {
            entranceAlpha.snapTo(1f)
        }
    }

    val errorColor = MaterialTheme.colorScheme.error
    val flashColor = if (flash.value > 0.5f) Color.White else errorColor
    val deathTranslation = with(density) { DeathDropDistance.toPx() } * deathProgress.value
    return Modifier
        .graphicsLayer {
            translationX = advanceX + shake.value
            translationY = deathTranslation
            alpha = entranceAlpha.value * (1f - deathProgress.value)
        }
        .drawWithContent {
            drawContent()
            if (flash.value > 0f) {
                drawRect(
                    color = flashColor.copy(alpha = flash.value.coerceIn(0f, 1f)),
                    blendMode = BlendMode.SrcAtop,
                )
            }
        }
}

@Composable
private fun BoxScope.BattleHealthLayer(
    units: List<BattleUnitUiModel>,
    healthPlacements: Map<String, BattleHealthBarPlacement>,
) {
    units.forEach { unit ->
        ActorHealthBar(
            unit = unit,
            placement = requireNotNull(healthPlacements[unit.id]),
            modifier = Modifier.zIndex(HealthLayerZIndex),
        )
    }
}

@Composable
private fun BoxScope.BattleStatusEffectLayer(
    effect: ActiveStatusEffectUiModel,
    placement: BattleStatusBadgePlacement,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    SevereInjuryBadge(
        effect = effect,
        onClick = onClick,
        testTag = BattleSevereInjuryBadgeTag,
        compact = true,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = placement.left.roundToInt(),
                    y = placement.top.roundToInt(),
                )
            }
            .size(
                width = with(density) { placement.width.toDp() },
                height = with(density) { placement.height.toDp() },
            )
            .zIndex(StatusEffectLayerZIndex),
    )
}

@Composable
internal fun SevereInjuryBadge(
    effect: ActiveStatusEffectUiModel,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    require(effect.type == StatusEffectType.SEVERE_INJURY) {
        "SevereInjuryBadge only supports severe injury"
    }
    val description = stringResource(
        R.string.status_effect_severe_injury_semantics,
        effect.remainingRecoveryCompletions,
    )
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .heightIn(min = MinimumInteractiveSize)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .testTag("$testTag-visual")
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = StatusBadgePanelAlpha))
                .border(
                    width = StatusBadgeBorderWidth,
                    color = MaterialTheme.colorScheme.error,
                    shape = shape,
                )
                .padding(
                    horizontal = if (compact) 5.dp else 8.dp,
                    vertical = if (compact) StatusBadgeVerticalPadding else 5.dp,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Healing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(if (compact) 16.dp else 20.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.status_effect_severe_injury_name),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (compact) {
                    StatusBadgeTextSize
                } else {
                    MaterialTheme.typography.labelLarge.fontSize
                },
                lineHeight = if (compact) {
                    StatusBadgeTextLineHeight
                } else {
                    MaterialTheme.typography.labelLarge.lineHeight
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SevereInjuryDetailsDialog(
    effect: ActiveStatusEffectUiModel,
    onDismiss: () -> Unit,
) {
    val time = when (val remaining = effect.remainingTime) {
        StatusEffectRemainingTimeUiState.LessThanOneHour ->
            stringResource(R.string.status_effect_recovery_less_than_one_hour)
        is StatusEffectRemainingTimeUiState.Hours ->
            stringResource(R.string.status_effect_recovery_hours, remaining.value)
    }
    val tasks = stringResource(
        R.string.status_effect_recovery_tasks,
        effect.remainingRecoveryCompletions,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(StatusEffectDetailsDialogTag),
        title = { Text(text = stringResource(R.string.status_effect_severe_injury_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.status_effect_severe_injury_description))
                Text(
                    text = stringResource(R.string.status_effect_severe_injury_effect),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.status_effect_recovery_condition,
                        tasks,
                        time,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.status_effect_details_confirm))
            }
        },
    )
}

@Composable
private fun BoxScope.ActorHealthBar(
    unit: BattleUnitUiModel,
    placement: BattleHealthBarPlacement,
    modifier: Modifier,
) {
    val target = BattleMapLayout.healthValue(unit.currentHp, unit.maxHp)
    val animatedCurrent by animateIntAsState(
        targetValue = target.displayedCurrentHp,
        animationSpec = tween(durationMillis = HealthAnimationDurationMillis),
        label = "battle-health-number",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = target.fraction,
        animationSpec = tween(durationMillis = HealthAnimationDurationMillis),
        label = "battle-health-progress",
    )
    val displayed = BattleMapLayout.healthValue(animatedCurrent, unit.maxHp)
    val name = stringResource(unit.nameResId)
    val description = if (target.isLowHealth) {
        stringResource(
            R.string.battle_health_low_description,
            name,
            displayed.displayedCurrentHp,
            unit.maxHp,
        )
    } else {
        stringResource(
            R.string.battle_unit_health_description,
            name,
            displayed.displayedCurrentHp,
            unit.maxHp,
        )
    }
    val density = LocalDensity.current
    val width = with(density) { placement.width.toDp() }
    val height = with(density) { placement.height.toDp() }
    val color = when {
        target.isLowHealth -> MaterialTheme.colorScheme.error
        unit.type == BattleUnitType.PLAYER -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val shape = MaterialTheme.shapes.extraSmall
    Column(
        modifier = modifier
            .offset {
                IntOffset(
                    x = placement.left.roundToInt(),
                    y = placement.top.roundToInt(),
                )
            }
            .size(width = width, height = height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = HealthPanelAlpha))
            .border(
                width = HealthPanelBorderWidth,
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .testTag(
                if (unit.type == BattleUnitType.PLAYER) {
                    BattlePlayerHealthTag
                } else {
                    BattleMonsterHealthTag
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (target.isLowHealth) liveRegion = LiveRegionMode.Polite
            }
            .padding(
                horizontal = HealthPanelHorizontalPadding,
                vertical = HealthPanelVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                R.string.battle_health_value,
                displayed.displayedCurrentHp,
                unit.maxHp,
            ),
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = HealthTextSize,
            lineHeight = HealthTextLineHeight,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(HealthProgressSpacing))
        LinearProgressIndicator(
            progress = { animatedFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(HealthProgressHeight)
                .clip(MaterialTheme.shapes.extraSmall),
            color = color,
            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun BoxScope.BattleEffectLayer(
    presentation: BattlePresentationState,
    units: List<BattleUnitUiModel>,
    placements: Map<String, BattleUnitPlacement>,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
) {
    if (presentation.phase == BattleAnimationPhase.IDLE) return
    val player = units.firstOrNull { it.type == BattleUnitType.PLAYER }
    val monster = units.firstOrNull { it.type == BattleUnitType.MONSTER }
    val attacker = when (presentation.attacker) {
        BattleUnitType.PLAYER -> player
        BattleUnitType.MONSTER -> monster
        null -> null
    }
    val target = when (presentation.target) {
        BattleUnitType.PLAYER -> player
        BattleUnitType.MONSTER -> monster
        null -> null
    }
    val attackerName = attacker?.let { stringResource(it.nameResId) }.orEmpty()
    val targetName = target?.let { stringResource(it.nameResId) }.orEmpty()
    val damage = presentation.damage ?: 0
    val targetPlacement = target?.let { placements[it.id] }

    when (presentation.phase) {
        BattleAnimationPhase.PLAYER_ATTACKING,
        BattleAnimationPhase.MONSTER_ATTACKING,
        -> targetPlacement?.let { placement ->
            AttackEffect(
                attackerType = requireNotNull(attacker).type,
                placement = placement,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(
                    R.string.battle_attack_announcement,
                    attackerName,
                    targetName,
                ),
            )
        }

        BattleAnimationPhase.MONSTER_HIT,
        BattleAnimationPhase.PLAYER_HIT,
        -> targetPlacement?.let { placement ->
            DamageEffect(
                targetName = targetName,
                damage = damage,
                placement = placement,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
            )
        }

        BattleAnimationPhase.MONSTER_DYING,
        BattleAnimationPhase.PLAYER_DYING,
        -> targetPlacement?.let { placement ->
            DeathEffect(
                deathAnnouncementResId = requireNotNull(target).deathAnnouncementResId,
                placement = placement,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
            )
        }

        BattleAnimationPhase.MONSTER_SPAWN_ALERT -> SpawnAlertEffect(
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            hudBottomPx = hudBottomPx,
        )

        BattleAnimationPhase.MONSTER_SPAWNING -> BattleAnnouncementSemantics(
            description = stringResource(R.string.battle_spawn_announcement),
            testTag = BattleSpawnEffectTag,
        )

        BattleAnimationPhase.PLAYER_DEFEATED -> player?.let { unit ->
            PlayerLifecycleEffect(
                placement = requireNotNull(placements[unit.id]),
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(R.string.battle_player_defeated_announcement),
                label = stringResource(R.string.battle_ko_label),
                testTag = BattlePlayerDefeatedEffectTag,
                isRecovery = false,
            )
        }

        BattleAnimationPhase.STATUS_EFFECT_APPLYING -> player?.let { unit ->
            PlayerLifecycleEffect(
                placement = requireNotNull(placements[unit.id]),
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(R.string.battle_severe_injury_applied_announcement),
                label = stringResource(R.string.status_effect_severe_injury_name),
                testTag = BattleStatusEffectEffectTag,
                isRecovery = false,
            )
        }

        BattleAnimationPhase.STATUS_EFFECT_REFRESHING -> player?.let { unit ->
            PlayerLifecycleEffect(
                placement = requireNotNull(placements[unit.id]),
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(R.string.battle_severe_injury_refreshed_announcement),
                label = stringResource(R.string.battle_severe_injury_refreshed_label),
                testTag = BattleStatusEffectEffectTag,
                isRecovery = false,
            )
        }

        BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING -> player?.let { unit ->
            PlayerLifecycleEffect(
                placement = requireNotNull(placements[unit.id]),
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(R.string.battle_emergency_recovery_announcement),
                label = stringResource(R.string.battle_emergency_recovery_label),
                testTag = BattleEmergencyRecoveryEffectTag,
                isRecovery = true,
            )
        }

        BattleAnimationPhase.STATUS_EFFECT_REMOVING -> player?.let { unit ->
            PlayerLifecycleEffect(
                placement = requireNotNull(placements[unit.id]),
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                hudBottomPx = hudBottomPx,
                description = stringResource(R.string.battle_severe_injury_removed_announcement),
                label = stringResource(R.string.battle_severe_injury_removed_label),
                testTag = BattleStatusEffectRemovedEffectTag,
                isRecovery = true,
            )
        }

        BattleAnimationPhase.IDLE -> Unit
    }
}

@Composable
private fun BoxScope.AttackEffect(
    attackerType: BattleUnitType,
    placement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
    description: String,
) {
    val density = LocalDensity.current
    val accent = if (attackerType == BattleUnitType.PLAYER) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    TargetEffectBadge(
        placement = effectPlacement(
            targetPlacement = placement,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            hudBottomPx = hudBottomPx,
            minimumHeightPx = with(density) { EffectBadgeHeight.toPx() },
        ),
        description = description,
        testTag = BattleAttackEffectTag,
        accent = accent,
    ) {
        Icon(
            imageVector = Icons.Default.FlashOn,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.battle_attack_label),
            color = accent,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.BattleAnnouncementSemantics(
    description: String,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(EffectLayerZIndex)
            .testTag(testTag)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
private fun BoxScope.PlayerLifecycleEffect(
    placement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
    description: String,
    label: String,
    testTag: String,
    isRecovery: Boolean,
) {
    val density = LocalDensity.current
    val accent = if (isRecovery) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    TargetEffectBadge(
        placement = effectPlacement(
            targetPlacement = placement,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            hudBottomPx = hudBottomPx,
            minimumHeightPx = with(density) { EffectBadgeHeight.toPx() },
        ),
        description = description,
        testTag = testTag,
        accent = accent,
    ) {
        Icon(
            imageVector = if (isRecovery) Icons.Default.Healing else Icons.Default.HeartBroken,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.DamageEffect(
    targetName: String,
    damage: Int,
    placement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
) {
    val description = stringResource(R.string.battle_hit_announcement, targetName, damage)
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.error
    TargetEffectBadge(
        placement = effectPlacement(
            targetPlacement = placement,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            hudBottomPx = hudBottomPx,
            minimumHeightPx = with(density) { EffectBadgeHeight.toPx() },
        ),
        description = description,
        testTag = BattleDamageEffectTag,
        accent = accent,
    ) {
        Icon(
            imageVector = Icons.Default.FlashOn,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.battle_damage_value, damage),
            color = accent,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.DeathEffect(
    @StringRes deathAnnouncementResId: Int,
    placement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
) {
    val description = stringResource(deathAnnouncementResId)
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.error
    TargetEffectBadge(
        placement = effectPlacement(
            targetPlacement = placement,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            hudBottomPx = hudBottomPx,
            minimumHeightPx = with(density) { EffectBadgeHeight.toPx() },
        ),
        description = description,
        testTag = BattleDeathEffectTag,
        accent = accent,
    ) {
        Icon(
            imageVector = Icons.Default.HeartBroken,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.battle_ko_label),
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.SpawnAlertEffect(
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
) {
    val description = stringResource(R.string.battle_spawn_announcement)
    val density = LocalDensity.current
    val widthPx = with(density) { SpawnAlertWidth.toPx() }.coerceAtMost(mapWidthPx)
    val heightPx = with(density) { EffectBadgeHeight.toPx() }
    val monsterSlot = BattleMonsterSlots.forCount(1).single()
    val leftPx = (monsterSlot.x * mapWidthPx - widthPx / 2f)
        .coerceIn(0f, (mapWidthPx - widthPx).coerceAtLeast(0f))
    val topPx = (monsterSlot.y * mapHeightPx - heightPx - with(density) { 40.dp.toPx() })
        .coerceIn(
            hudBottomPx.coerceAtMost((mapHeightPx - heightPx).coerceAtLeast(0f)),
            (mapHeightPx - heightPx).coerceAtLeast(hudBottomPx),
        )
    val width = with(density) { widthPx.toDp() }
    val height = with(density) { heightPx.toDp() }
    Row(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(width = width, height = height)
            .zIndex(EffectLayerZIndex)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .testTag(BattleSpawnAlertEffectTag)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.TargetEffectBadge(
    placement: BattleEffectPlacement,
    description: String,
    testTag: String,
    accent: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
            .size(
                width = with(density) { placement.width.toDp() },
                height = with(density) { placement.height.toDp() },
            )
            .zIndex(EffectLayerZIndex)
            .testTag(testTag)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = EffectPanelAlpha))
                .border(
                    width = EffectPanelBorderWidth,
                    color = accent,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun BoxScope.BattleLoadingState() {
    val description = stringResource(R.string.battle_loading_description)
    CircularProgressIndicator(
        modifier = Modifier
            .align(Alignment.Center)
            .size(26.dp)
            .zIndex(StatusLayerZIndex)
            .semantics {
                contentDescription = description
            },
        color = MaterialTheme.colorScheme.secondary,
        strokeWidth = 2.dp,
    )
}

@Composable
private fun BoxScope.BattleUnavailableState() {
    val description = stringResource(R.string.battle_unavailable_description)
    Text(
        text = description,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
            .zIndex(StatusLayerZIndex)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                contentDescription = description
            },
    )
}

@Composable
private fun BoxScope.BattleOverlayLayer(
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(OverlayLayerZIndex)
            .testTag("battle-overlay-layer"),
        content = content,
    )
}

private fun calculatePlacement(
    unit: BattleUnitUiModel,
    mapWidthPx: Float,
    mapHeightPx: Float,
    baseUnitHeightPx: Float,
): BattleUnitPlacement {
    val frame = unit.sprite.frame
    val height = baseUnitHeightPx * unit.scale
    val width = height * frame.sourceWidth.toFloat() / frame.sourceHeight.toFloat()
    val offset = BattleMapLayout.calculateTopLeft(
        mapWidth = mapWidthPx,
        mapHeight = mapHeightPx,
        spriteWidth = width,
        spriteHeight = height,
        position = unit.position,
        groundAnchor = frame.groundAnchor,
        groundOffsetPx = unit.groundOffset,
    )
    return BattleUnitPlacement(
        left = offset.left,
        top = offset.top,
        width = width,
        height = height,
    )
}

private fun healthBarPlacement(
    spritePlacement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
    barHeightPx: Float,
    density: Density,
): BattleHealthBarPlacement = with(density) {
    BattleMapLayout.calculateHealthBarPlacement(
        mapWidth = mapWidthPx,
        mapHeight = mapHeightPx,
        spritePlacement = spritePlacement,
        hudBottom = hudBottomPx,
        minimumGap = HealthMinimumGap.toPx(),
        barHeight = barHeightPx,
        widthRatio = HealthWidthRatio,
        minimumWidth = HealthMinimumWidth.toPx(),
        maximumWidth = HealthMaximumWidth.toPx(),
    )
}

private fun effectPlacement(
    targetPlacement: BattleUnitPlacement,
    mapWidthPx: Float,
    mapHeightPx: Float,
    hudBottomPx: Float,
    minimumHeightPx: Float,
): BattleEffectPlacement {
    val width = (targetPlacement.width * EffectWidthRatio).coerceAtMost(mapWidthPx)
    val height = (targetPlacement.height * EffectHeightRatio)
        .coerceAtLeast(minimumHeightPx)
        .coerceAtMost(mapHeightPx)
    val left = (targetPlacement.left + targetPlacement.width / 2f - width / 2f)
        .coerceIn(0f, (mapWidthPx - width).coerceAtLeast(0f))
    val maximumTop = (mapHeightPx - height).coerceAtLeast(0f)
    val minimumTop = hudBottomPx.coerceAtMost(maximumTop)
    val top = (targetPlacement.top + targetPlacement.height * EffectTopRatio)
        .coerceIn(minimumTop, maximumTop)
    return BattleEffectPlacement(left = left, top = top, width = width, height = height)
}

private fun DrawScope.drawFallbackSkyAndMountains() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFBBD7D4), Color(0xFFD8D6A8)),
            endY = size.height * 0.60f,
        ),
    )
    val distantMountains = Path().apply {
        moveTo(0f, size.height * 0.48f)
        lineTo(size.width * 0.14f, size.height * 0.24f)
        lineTo(size.width * 0.31f, size.height * 0.47f)
        lineTo(size.width * 0.49f, size.height * 0.18f)
        lineTo(size.width * 0.68f, size.height * 0.46f)
        lineTo(size.width * 0.83f, size.height * 0.28f)
        lineTo(size.width, size.height * 0.48f)
        close()
    }
    drawPath(path = distantMountains, color = Color(0xFF718A78))
}

private fun DrawScope.drawFallbackForest() {
    val horizon = size.height * 0.49f
    drawRect(
        color = Color(0xFF3F6547),
        topLeft = Offset(0f, horizon),
        size = Size(size.width, size.height * 0.12f),
    )
    repeat(15) { index ->
        val x = size.width * index / 14f
        val radius = size.height * (0.035f + (index % 3) * 0.006f)
        drawCircle(
            color = Color(0xFF31563E),
            radius = radius,
            center = Offset(x, horizon),
        )
    }
}

private fun DrawScope.drawFallbackFieldAndRoad() {
    val fieldTop = size.height * 0.54f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF749456), Color(0xFF526F3E)),
            startY = fieldTop,
            endY = size.height,
        ),
        topLeft = Offset(0f, fieldTop),
    )
    val road = Path().apply {
        moveTo(size.width * 0.47f, fieldTop)
        lineTo(size.width * 0.56f, fieldTop)
        lineTo(size.width * 0.72f, size.height)
        lineTo(size.width * 0.24f, size.height)
        close()
    }
    drawPath(path = road, color = Color(0xFF9B8257))
    drawRect(
        color = Color(0x24526F3E),
        topLeft = Offset(0f, size.height * 0.72f),
        size = Size(size.width, size.height * 0.28f),
    )
}

private fun DrawScope.drawFallbackGroundDetails() {
    val stoneColor = Color(0xFF64675C)
    listOf(0.06f to 0.86f, 0.43f to 0.77f, 0.96f to 0.68f).forEach { (x, y) ->
        drawOval(
            color = stoneColor,
            topLeft = Offset(size.width * x, size.height * y),
            size = Size(size.width * 0.012f, size.height * 0.018f),
        )
    }
    val grassColor = Color(0xFF385B35)
    listOf(0.12f to 0.68f, 0.38f to 0.90f, 0.98f to 0.84f).forEach { (x, y) ->
        val origin = Offset(size.width * x, size.height * y)
        drawLine(
            color = grassColor,
            start = origin,
            end = origin + Offset(-size.width * 0.006f, -size.height * 0.035f),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLine(
            color = grassColor,
            start = origin,
            end = origin + Offset(size.width * 0.005f, -size.height * 0.03f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

private data class BattleEffectPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private data class BattleActorLayout(
    val sprite: BattleUnitPlacement,
    val health: BattleHealthBarPlacement,
    val statusBadge: BattleStatusBadgePlacement? = null,
)

private const val BattleMapAspectRatio = 2.4f
private const val BaseUnitHeightRatio = 0.35f
private val MinimumBaseUnitHeight: Dp = 72.dp
private val MaximumBaseUnitHeight: Dp = 112.dp
private val CompactMinimumBaseUnitHeight: Dp = 48.dp
private val CompactMaximumBaseUnitHeight: Dp = 48.dp
private val InjuryCompactMapHeight: Dp = 190.dp
private val BattleHudTopInset: Dp = 8.dp
private val HealthMinimumGap: Dp = 3.dp
private val HealthActorGap: Dp = 2.dp
private val HealthMinimumWidth: Dp = 52.dp
private val HealthMaximumWidth: Dp = 88.dp
private val HealthProgressSpacing: Dp = 1.dp
private val HealthProgressHeight: Dp = 5.dp
private val HealthTextSize = 11.sp
private val HealthTextLineHeight = 13.sp
private val HealthPanelHorizontalPadding: Dp = 3.dp
private val HealthPanelVerticalPadding: Dp = 1.dp
private val HealthPanelBorderWidth: Dp = 1.dp
private const val HealthPanelAlpha = 0.94f
private const val HealthWidthRatio = 0.82f
private const val HealthAnimationDurationMillis = 220
private val StatusHealthGap: Dp = 2.dp
private val StatusActorGap: Dp = 2.dp
private val StatusBadgeWidth: Dp = 76.dp
private val StatusBadgeMinimumHeight: Dp = 48.dp
private val StatusBadgeVerticalPadding: Dp = 2.dp
private val StatusBadgeBorderWidth: Dp = 1.dp
private val StatusBadgeTextSize = 11.sp
private val StatusBadgeTextLineHeight = 13.sp
private const val StatusBadgePanelAlpha = 0.96f
private val MinimumInteractiveSize: Dp = 48.dp
private val AttackAdvance: Dp = 12.dp
private val HitShakeDistance: Dp = 4.dp
private val DeathDropDistance: Dp = 12.dp
private const val AttackAdvanceDurationMillis = 140
private const val HitShakeStepDurationMillis = 24
private const val HitFlashDurationMillis = 160
private const val DeathDurationMillis = 300
private const val SpawnDurationMillis = 260
private const val BattleRewardFeedbackDurationMillis = 600L
private const val EffectWidthRatio = 1.35f
private const val EffectHeightRatio = 0.28f
private const val EffectTopRatio = 0.18f
private val EffectBadgeHeight: Dp = 32.dp
private val EffectPanelBorderWidth: Dp = 1.dp
private const val EffectPanelAlpha = 0.96f
private val SpawnAlertWidth: Dp = 176.dp
private const val BackgroundLayerZIndex = 0f
private const val DecorationLayerZIndex = 0.5f
private const val GroundLayerZIndex = 1f
private const val UnitLayerZIndex = 2f
private const val HealthLayerZIndex = 50f
private const val StatusEffectLayerZIndex = 55f
private const val EffectLayerZIndex = 60f
private const val StatusLayerZIndex = 90f
private const val OverlayLayerZIndex = 100f
private const val BattlePlayerHealthTag = "battle-player-health"
private const val BattleMonsterHealthTag = "battle-monster-health"
private const val BattleAttackEffectTag = "battle-attack-effect"
private const val BattleDamageEffectTag = "battle-damage-effect"
private const val BattleDeathEffectTag = "battle-death-effect"
private const val BattleSpawnAlertEffectTag = "battle-spawn-alert-effect"
private const val BattleSpawnEffectTag = "battle-spawn-effect"
private const val BattlePlayerDefeatedEffectTag = "battle-player-defeated-effect"
private const val BattleStatusEffectEffectTag = "battle-status-effect"
private const val BattleEmergencyRecoveryEffectTag = "battle-emergency-recovery-effect"
private const val BattleStatusEffectRemovedEffectTag = "battle-status-effect-removed"
private const val BattleRewardEffectTag = "battle-reward-effect"
private const val BattleSevereInjuryBadgeTag = "battle-severe-injury-badge"
private const val StatusEffectDetailsDialogTag = "status-effect-details-dialog"

internal val LocalBattleHudSizeReporter = staticCompositionLocalOf<(IntSize) -> Unit> { { } }
