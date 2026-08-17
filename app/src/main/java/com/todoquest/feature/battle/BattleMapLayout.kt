package com.todoquest.feature.battle

import androidx.compose.runtime.Immutable

@Immutable
data class BattlePixelOffset(
    val left: Float,
    val top: Float,
)

@Immutable
data class BattleUnitDepth(
    val unit: BattleUnitUiModel,
    val zIndex: Float,
)

@Immutable
data class BattleUnitPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Immutable
data class BattleHealthBarPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Immutable
data class BattleStatusBadgePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Immutable
data class BattlePlayerStatusLayout(
    val sprite: BattleUnitPlacement,
    val health: BattleHealthBarPlacement,
    val statusBadge: BattleStatusBadgePlacement,
)

@Immutable
data class BattleHealthValue(
    val displayedCurrentHp: Int,
    val fraction: Float,
    val isLowHealth: Boolean,
)

object BattleMapLayout {
    fun calculateTopLeft(
        mapWidth: Float,
        mapHeight: Float,
        spriteWidth: Float,
        spriteHeight: Float,
        position: BattlePosition,
        groundAnchor: BattlePosition,
        groundOffsetPx: Float = 0f,
    ): BattlePixelOffset {
        require(mapWidth.isFinite() && mapWidth > 0f) { "mapWidth must be finite and positive" }
        require(mapHeight.isFinite() && mapHeight > 0f) { "mapHeight must be finite and positive" }
        require(spriteWidth.isFinite() && spriteWidth > 0f) {
            "spriteWidth must be finite and positive"
        }
        require(spriteHeight.isFinite() && spriteHeight > 0f) {
            "spriteHeight must be finite and positive"
        }
        require(groundOffsetPx.isFinite()) { "groundOffsetPx must be finite" }

        val unclampedLeft = position.x * mapWidth - spriteWidth * groundAnchor.x
        val unclampedTop =
            position.y * mapHeight - spriteHeight * groundAnchor.y + groundOffsetPx
        val maximumLeft = (mapWidth - spriteWidth).coerceAtLeast(0f)
        val maximumTop = (mapHeight - spriteHeight).coerceAtLeast(0f)

        return BattlePixelOffset(
            left = unclampedLeft.coerceIn(0f, maximumLeft),
            top = unclampedTop.coerceIn(0f, maximumTop),
        )
    }

    fun orderByDepth(units: List<BattleUnitUiModel>): List<BattleUnitDepth> =
        units.withIndex()
            .sortedWith(
                compareBy<IndexedValue<BattleUnitUiModel>>(
                    { it.value.position.y },
                    { it.index },
                ),
            )
            .mapIndexed { depth, indexedUnit ->
                BattleUnitDepth(
                    unit = indexedUnit.value,
                    zIndex = depth.toFloat(),
                )
            }

    @Suppress("LongParameterList")
    fun calculateHealthBarPlacement(
        mapWidth: Float,
        mapHeight: Float,
        spritePlacement: BattleUnitPlacement,
        hudBottom: Float,
        minimumGap: Float,
        barHeight: Float,
        widthRatio: Float,
        minimumWidth: Float,
        maximumWidth: Float,
    ): BattleHealthBarPlacement {
        require(mapWidth.isFinite() && mapWidth > 0f) { "mapWidth must be finite and positive" }
        require(mapHeight.isFinite() && mapHeight > 0f) { "mapHeight must be finite and positive" }
        require(hudBottom.isFinite() && hudBottom >= 0f) { "hudBottom must be finite and non-negative" }
        require(minimumGap.isFinite() && minimumGap >= 0f) {
            "minimumGap must be finite and non-negative"
        }
        require(barHeight.isFinite() && barHeight > 0f) { "barHeight must be finite and positive" }
        require(widthRatio.isFinite() && widthRatio > 0f) {
            "widthRatio must be finite and positive"
        }
        require(minimumWidth.isFinite() && minimumWidth > 0f) {
            "minimumWidth must be finite and positive"
        }
        require(maximumWidth.isFinite() && maximumWidth >= minimumWidth) {
            "maximumWidth must be finite and at least minimumWidth"
        }

        val width = (spritePlacement.width * widthRatio)
            .coerceIn(minimumWidth, maximumWidth)
            .coerceAtMost(mapWidth)
        val centeredLeft = spritePlacement.left + spritePlacement.width / 2f - width / 2f
        val left = centeredLeft.coerceIn(0f, (mapWidth - width).coerceAtLeast(0f))
        val minimumTop = (hudBottom + minimumGap)
            .coerceAtMost((mapHeight - barHeight).coerceAtLeast(0f))
        val desiredTop = spritePlacement.top - minimumGap - barHeight
        val top = desiredTop.coerceIn(
            minimumValue = minimumTop,
            maximumValue = (mapHeight - barHeight).coerceAtLeast(minimumTop),
        )
        return BattleHealthBarPlacement(
            left = left,
            top = top,
            width = width,
            height = barHeight,
        )
    }

    fun healthValue(currentHp: Int, maxHp: Int): BattleHealthValue {
        require(maxHp > 0) { "maxHp must be positive" }
        val displayedCurrentHp = currentHp.coerceIn(0, maxHp)
        val fraction = displayedCurrentHp.toFloat() / maxHp.toFloat()
        return BattleHealthValue(
            displayedCurrentHp = displayedCurrentHp,
            fraction = fraction.coerceIn(0f, 1f),
            isLowHealth = displayedCurrentHp.toLong() * 4L <= maxHp.toLong(),
        )
    }

    @Suppress("LongParameterList")
    fun calculatePlayerStatusLayout(
        mapWidth: Float,
        mapHeight: Float,
        spritePlacement: BattleUnitPlacement,
        hudBottom: Float,
        healthMinimumGap: Float,
        healthHeight: Float,
        healthWidthRatio: Float,
        healthMinimumWidth: Float,
        healthMaximumWidth: Float,
        healthBadgeGap: Float,
        badgeHeight: Float,
        badgeWidth: Float,
        badgeActorGap: Float,
    ): BattlePlayerStatusLayout {
        require(healthBadgeGap.isFinite() && healthBadgeGap >= 0f) {
            "healthBadgeGap must be finite and non-negative"
        }
        require(badgeHeight.isFinite() && badgeHeight > 0f) {
            "badgeHeight must be finite and positive"
        }
        require(badgeWidth.isFinite() && badgeWidth > 0f) {
            "badgeWidth must be finite and positive"
        }
        require(badgeActorGap.isFinite() && badgeActorGap >= 0f) {
            "badgeActorGap must be finite and non-negative"
        }

        val healthWidth = calculateHealthBarPlacement(
            mapWidth = mapWidth,
            mapHeight = mapHeight,
            spritePlacement = spritePlacement,
            hudBottom = hudBottom,
            minimumGap = healthMinimumGap,
            barHeight = healthHeight,
            widthRatio = healthWidthRatio,
            minimumWidth = healthMinimumWidth,
            maximumWidth = healthMaximumWidth,
        )
        val desiredHealthTop = spritePlacement.top - badgeActorGap - badgeHeight -
            healthBadgeGap - healthHeight
        val minimumHealthTop = (hudBottom + healthMinimumGap)
            .coerceAtMost((mapHeight - healthHeight).coerceAtLeast(0f))
        val maximumStackedHealthTop =
            (mapHeight - healthHeight - badgeHeight).coerceAtLeast(0f)
        val healthTop = if (minimumHealthTop <= maximumStackedHealthTop) {
            desiredHealthTop.coerceIn(
                minimumValue = minimumHealthTop,
                maximumValue = maximumStackedHealthTop,
            )
        } else {
            minimumHealthTop
        }
        val health = healthWidth.copy(
            top = healthTop,
        )
        val width = badgeWidth.coerceAtMost(mapWidth)
        val centerX = spritePlacement.left + spritePlacement.width / 2f
        val availableHealthBadgeGap = (
            mapHeight - health.top - health.height - badgeHeight
        ).coerceAtLeast(0f)
        val appliedHealthBadgeGap = healthBadgeGap.coerceAtMost(availableHealthBadgeGap)
        val maximumBadgeTop = (mapHeight - badgeHeight).coerceAtLeast(0f)
        val badge = BattleStatusBadgePlacement(
            left = (centerX - width / 2f)
                .coerceIn(0f, (mapWidth - width).coerceAtLeast(0f)),
            top = (health.top + health.height + appliedHealthBadgeGap)
                .coerceAtMost(maximumBadgeTop),
            width = width,
            height = badgeHeight,
        )
        return BattlePlayerStatusLayout(
            sprite = spritePlacement,
            health = health,
            statusBadge = badge,
        )
    }
}
