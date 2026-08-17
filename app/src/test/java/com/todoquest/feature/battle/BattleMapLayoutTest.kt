package com.todoquest.feature.battle

import com.todoquest.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleMapLayoutTest {
    @Test
    fun monsterSlotsReturnExactNormalizedPresets() {
        assertEquals(emptyList<BattlePosition>(), BattleMonsterSlots.forCount(0))
        assertEquals(
            listOf(BattlePosition(0.76f, 0.82f)),
            BattleMonsterSlots.forCount(1),
        )
        assertEquals(
            listOf(BattlePosition(0.66f, 0.83f), BattlePosition(0.84f, 0.78f)),
            BattleMonsterSlots.forCount(2),
        )
        assertEquals(
            listOf(
                BattlePosition(0.60f, 0.82f),
                BattlePosition(0.76f, 0.78f),
                BattlePosition(0.90f, 0.84f),
            ),
            BattleMonsterSlots.forCount(3),
        )
        assertEquals(
            listOf(
                BattlePosition(0.57f, 0.77f),
                BattlePosition(0.69f, 0.86f),
                BattlePosition(0.82f, 0.76f),
                BattlePosition(0.92f, 0.85f),
            ),
            BattleMonsterSlots.forCount(4),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BattleMonsterSlots.forCount(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMonsterSlots.forCount(5)
        }
    }

    @Test
    fun bottomCenterAnchorConvertsToPixelTopLeft() {
        assertEquals(
            BattlePixelOffset(left = 208f, top = 352f),
            BattleMapLayout.calculateTopLeft(
                mapWidth = 1_200f,
                mapHeight = 500f,
                spriteWidth = 64f,
                spriteHeight = 64f,
                position = BattleMapDefaults.PLAYER_POSITION,
                groundAnchor = BattleMapDefaults.PLAYER_FRAME.groundAnchor,
            ),
        )
    }

    @Test
    fun signedGroundOffsetMovesSpriteDownAndUp() {
        val baseArguments = LayoutArguments()

        assertEquals(
            359f,
            baseArguments.calculate(groundOffsetPx = 7f).top,
        )
        assertEquals(
            345f,
            baseArguments.calculate(groundOffsetPx = -7f).top,
        )
    }

    @Test
    fun topLeftIsClampedAtEveryMapEdge() {
        assertEquals(
            0f,
            layoutAt(BattlePosition(0f, 0.5f)).left,
        )
        assertEquals(
            1_136f,
            layoutAt(BattlePosition(1f, 0.5f)).left,
        )
        assertEquals(
            0f,
            layoutAt(BattlePosition(0.5f, 0f)).top,
        )
        assertEquals(
            436f,
            layoutAt(BattlePosition(0.5f, 1f)).top,
        )
    }

    @Test
    fun depthOrderIsAscendingByYStableForTiesAndAssignsIncreasingZIndex() {
        val units = listOf(
            unit("front", y = 0.90f),
            unit("back-a", y = 0.50f),
            unit("back-b", y = 0.50f),
            unit("middle", y = 0.70f),
        )

        val ordered = BattleMapLayout.orderByDepth(units)

        assertEquals(listOf("back-a", "back-b", "middle", "front"), ordered.map { it.unit.id })
        assertEquals(listOf(0f, 1f, 2f, 3f), ordered.map { it.zIndex })
    }

    @Test
    fun layoutRejectsNonFiniteOrNonPositiveDimensionsAndOffset() {
        assertThrows(IllegalArgumentException::class.java) {
            LayoutArguments(mapWidth = 0f).calculate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LayoutArguments(spriteHeight = Float.NaN).calculate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LayoutArguments().calculate(groundOffsetPx = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun healthValueClampsCurrentFractionAndLowHealthBoundary() {
        assertEquals(
            BattleHealthValue(displayedCurrentHp = 0, fraction = 0f, isLowHealth = true),
            BattleMapLayout.healthValue(currentHp = -10, maxHp = 100),
        )
        assertEquals(
            BattleHealthValue(displayedCurrentHp = 25, fraction = 0.25f, isLowHealth = true),
            BattleMapLayout.healthValue(currentHp = 25, maxHp = 100),
        )
        assertEquals(
            BattleHealthValue(displayedCurrentHp = 26, fraction = 0.26f, isLowHealth = false),
            BattleMapLayout.healthValue(currentHp = 26, maxHp = 100),
        )
        assertEquals(
            BattleHealthValue(displayedCurrentHp = 100, fraction = 1f, isLowHealth = false),
            BattleMapLayout.healthValue(currentHp = 140, maxHp = 100),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapLayout.healthValue(currentHp = 0, maxHp = 0)
        }
    }

    @Test
    fun healthBarUsesSpriteCenterAndStaysInsideMapBelowHud() {
        val placement = BattleMapLayout.calculateHealthBarPlacement(
            mapWidth = 320f,
            mapHeight = 190f,
            spritePlacement = BattleUnitPlacement(
                left = -40f,
                top = 72f,
                width = 72f,
                height = 72f,
            ),
            hudBottom = 58f,
            minimumGap = 6f,
            barHeight = 18f,
            widthRatio = 0.82f,
            minimumWidth = 52f,
            maximumWidth = 88f,
        )

        assertEquals(0f, placement.left, 0f)
        assertEquals(59.04f, placement.width, 0.001f)
        assertEquals(64f, placement.top, 0f)
        assertTrue(placement.left + placement.width <= 320f)
        assertTrue(placement.top >= 58f + 6f)
        assertTrue(placement.top + placement.height <= 190f)
    }

    @Test
    fun severeInjuryBadgeReservesSpaceBetweenPlayerHealthAndSprite() {
        val spritePlacement = BattleUnitPlacement(
            left = 40f,
            top = 90f,
            width = 72f,
            height = 72f,
        )
        val layout = BattleMapLayout.calculatePlayerStatusLayout(
            mapWidth = 320f,
            mapHeight = 190f,
            spritePlacement = spritePlacement,
            hudBottom = 38f,
            healthMinimumGap = 3f,
            healthHeight = 20f,
            healthWidthRatio = 0.82f,
            healthMinimumWidth = 52f,
            healthMaximumWidth = 88f,
            healthBadgeGap = 2f,
            badgeHeight = 26f,
            badgeWidth = 76f,
            badgeActorGap = 2f,
        )

        assertEquals(spritePlacement, layout.sprite)
        assertTrue(layout.health.top >= 38f + 3f)
        assertTrue(layout.health.top + layout.health.height <= layout.statusBadge.top)
        assertTrue(layout.statusBadge.top + layout.statusBadge.height <= layout.sprite.top)
        assertTrue(layout.statusBadge.left >= 0f)
        assertTrue(layout.statusBadge.left + layout.statusBadge.width <= 320f)
    }

    @Test
    fun compactPlayerStatusLayoutKeepsSpriteBytesAndStatusBoundsInsideMap() {
        val spritePlacement = BattleUnitPlacement(
            left = 18.25f,
            top = 70.5f,
            width = 48f,
            height = 48f,
        )

        val layout = BattleMapLayout.calculatePlayerStatusLayout(
            mapWidth = 320f,
            mapHeight = 150f,
            spritePlacement = spritePlacement,
            hudBottom = 42f,
            healthMinimumGap = 3f,
            healthHeight = 18f,
            healthWidthRatio = 0.82f,
            healthMinimumWidth = 52f,
            healthMaximumWidth = 88f,
            healthBadgeGap = 2f,
            badgeHeight = 48f,
            badgeWidth = 76f,
            badgeActorGap = 2f,
        )

        assertEquals(spritePlacement, layout.sprite)
        assertTrue(layout.health.left >= 0f)
        assertTrue(layout.health.top >= 42f)
        assertTrue(layout.health.left + layout.health.width <= 320f)
        assertTrue(layout.health.top + layout.health.height <= 150f)
        assertTrue(layout.health.top + layout.health.height <= layout.statusBadge.top)
        assertTrue(layout.statusBadge.left >= 0f)
        assertTrue(layout.statusBadge.top >= 42f)
        assertTrue(layout.statusBadge.left + layout.statusBadge.width <= 320f)
        assertTrue(layout.statusBadge.top + layout.statusBadge.height <= 150f)
    }

    private fun layoutAt(position: BattlePosition): BattlePixelOffset =
        BattleMapLayout.calculateTopLeft(
            mapWidth = 1_200f,
            mapHeight = 500f,
            spriteWidth = 64f,
            spriteHeight = 64f,
            position = position,
            groundAnchor = BattleMapDefaults.PLAYER_FRAME.groundAnchor,
        )

    private fun unit(id: String, y: Float) = BattleUnitUiModel(
        id = id,
        type = BattleUnitType.MONSTER,
        sprite = BattleSpriteUiModel.Resource(
            spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
            frame = BattleMapDefaults.MONSTER_FRAME,
        ),
        position = BattlePosition(0.5f, y),
        scale = 1f,
        groundOffset = 0f,
        currentHp = 50,
        maxHp = 50,
        nameResId = R.string.character_adventurer_description,
        deathAnnouncementResId = R.string.battle_monster_death_announcement,
    )

    private data class LayoutArguments(
        val mapWidth: Float = 1_200f,
        val mapHeight: Float = 500f,
        val spriteWidth: Float = 64f,
        val spriteHeight: Float = 64f,
        val position: BattlePosition = BattleMapDefaults.PLAYER_POSITION,
        val groundAnchor: BattlePosition = BattleMapDefaults.PLAYER_FRAME.groundAnchor,
    ) {
        fun calculate(groundOffsetPx: Float = 0f) = BattleMapLayout.calculateTopLeft(
            mapWidth = mapWidth,
            mapHeight = mapHeight,
            spriteWidth = spriteWidth,
            spriteHeight = spriteHeight,
            position = position,
            groundAnchor = groundAnchor,
            groundOffsetPx = groundOffsetPx,
        )
    }
}
