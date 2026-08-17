package com.todoquest.feature.battle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.monster.MonsterVisualCatalog
import java.time.Instant

@Immutable
data class BattlePosition(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && x in 0f..1f) { "x must be finite and normalized" }
        require(y.isFinite() && y in 0f..1f) { "y must be finite and normalized" }
    }
}

enum class BattleUnitType {
    PLAYER,
    MONSTER,
}

@Immutable
data class ActiveStatusEffectUiModel(
    val type: StatusEffectType,
    val revision: Long,
    val remainingRecoveryCompletions: Int,
    val remainingTime: StatusEffectRemainingTimeUiState,
) {
    init {
        require(revision > 0L) { "revision must be positive" }
        require(remainingRecoveryCompletions > 0) {
            "active status effect must have remaining recovery completions"
        }
    }
}

@Immutable
sealed interface StatusEffectRemainingTimeUiState {
    data object LessThanOneHour : StatusEffectRemainingTimeUiState

    data class Hours(val value: Int) : StatusEffectRemainingTimeUiState {
        init {
            require(value > 0) { "remaining hours must be positive" }
        }
    }
}

internal fun CharacterStatusEffect.toActiveStatusEffectUiModel(
    now: Instant,
): ActiveStatusEffectUiModel {
    require(isEffectiveAt(now)) { "status effect must be active at the presentation instant" }
    val remainingMillis = expiresAtEpochMillis - now.toEpochMilli()
    val remainingTime = if (remainingMillis < MILLIS_PER_HOUR) {
        StatusEffectRemainingTimeUiState.LessThanOneHour
    } else {
        val ceilingHours = Math.addExact(remainingMillis, MILLIS_PER_HOUR - 1L) /
            MILLIS_PER_HOUR
        StatusEffectRemainingTimeUiState.Hours(Math.toIntExact(ceilingHours))
    }
    return ActiveStatusEffectUiModel(
        type = type,
        revision = revision,
        remainingRecoveryCompletions = remainingRecoveryCompletions,
        remainingTime = remainingTime,
    )
}

private const val MILLIS_PER_HOUR = 60L * 60L * 1_000L

@Immutable
data class BattleSpriteFrame(
    val sourceX: Int,
    val sourceY: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val groundAnchor: BattlePosition,
) {
    init {
        require(sourceX >= 0) { "sourceX must not be negative" }
        require(sourceY >= 0) { "sourceY must not be negative" }
        require(sourceWidth > 0) { "sourceWidth must be positive" }
        require(sourceHeight > 0) { "sourceHeight must be positive" }
    }
}

@Immutable
sealed interface BattleSpriteUiModel {
    val frame: BattleSpriteFrame

    @Immutable
    data class Resource(
        @param:DrawableRes val spriteResId: Int,
        override val frame: BattleSpriteFrame,
    ) : BattleSpriteUiModel

    @Immutable
    data class LayeredCharacter(
        val renderState: CharacterRenderState,
        override val frame: BattleSpriteFrame,
    ) : BattleSpriteUiModel
}

@Immutable
data class BattleMonsterVisual(
    @param:DrawableRes val spriteResId: Int,
    @param:StringRes val nameResId: Int,
    @param:StringRes val deathAnnouncementResId: Int,
)

object BattleMonsterVisualCatalog {
    fun forSpecies(species: MonsterSpecies): BattleMonsterVisual {
        val visual = MonsterVisualCatalog.forSpecies(species)
        return BattleMonsterVisual(
            spriteResId = visual.spriteResId,
            nameResId = visual.nameResId,
            deathAnnouncementResId = deathAnnouncementFor(species),
        )
    }

    @StringRes
    private fun deathAnnouncementFor(species: MonsterSpecies): Int = when (species) {
        MonsterSpecies.GOBLIN_SCOUT -> R.string.battle_monster_death_announcement
        MonsterSpecies.SKELETON_SOLDIER ->
            R.string.battle_monster_skeleton_soldier_death_announcement
        MonsterSpecies.CORRUPTED_TREE_SPIRIT ->
            R.string.battle_monster_corrupted_tree_spirit_death_announcement
        MonsterSpecies.HARPY -> R.string.battle_monster_harpy_death_announcement
        MonsterSpecies.SLIME -> R.string.battle_monster_slime_death_announcement
    }
}

@Immutable
data class BattleUnitUiModel(
    val id: String,
    val type: BattleUnitType,
    val sprite: BattleSpriteUiModel,
    val position: BattlePosition,
    val scale: Float,
    val groundOffset: Float,
    val currentHp: Int,
    val maxHp: Int,
    @param:StringRes val nameResId: Int,
    @param:StringRes val deathAnnouncementResId: Int,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
        require(groundOffset.isFinite()) { "groundOffset must be finite" }
        require(maxHp > 0) { "maxHp must be positive" }
        require(currentHp in 0..maxHp) { "currentHp must be between zero and maxHp" }
    }
}

@Immutable
sealed interface BattleMapUiState {
    data object Loading : BattleMapUiState

    @Immutable
    data class Content(
        val player: BattleUnitUiModel,
        val monsters: List<BattleUnitUiModel>,
        val stageNumber: Int,
    ) : BattleMapUiState {
        init {
            require(player.type == BattleUnitType.PLAYER) { "player must have PLAYER type" }
            require(player.sprite is BattleSpriteUiModel.LayeredCharacter) {
                "player must use a layered character sprite"
            }
            require(monsters.size <= MAX_MONSTER_COUNT) { "at most four monsters are supported" }
            require(monsters.all { it.type == BattleUnitType.MONSTER }) {
                "monsters must all have MONSTER type"
            }
            require(monsters.all { it.sprite is BattleSpriteUiModel.Resource }) {
                "monsters must use resource sprites"
            }
            require(stageNumber > 0) { "stageNumber must be positive" }
            require((listOf(player) + monsters).map { it.id }.distinct().size == monsters.size + 1) {
                "battle unit ids must be unique"
            }
        }
    }

    data object Unavailable : BattleMapUiState

    private companion object {
        const val MAX_MONSTER_COUNT = 4
    }
}

@Immutable
data class BattleMapTheme(
    @param:DrawableRes val backgroundResId: Int = R.drawable.battle_map_grassland,
    val showDecorations: Boolean = true,
)

enum class BattleMapHeightPolicy(
    val minimumHeight: Dp,
    val maximumHeight: Dp,
) {
    STANDARD(minimumHeight = 190.dp, maximumHeight = 320.dp),
    COMPACT(minimumHeight = 150.dp, maximumHeight = 190.dp),
}

object BattleMapDefaults {
    val PLAYER_POSITION = BattlePosition(x = 0.20f, y = 0.82f)

    val PLAYER_FRAME = fullSpriteFrame()
    val MONSTER_FRAME = fullSpriteFrame()

    private fun fullSpriteFrame() = BattleSpriteFrame(
        sourceX = 0,
        sourceY = 0,
        sourceWidth = 64,
        sourceHeight = 64,
        groundAnchor = BattlePosition(x = 0.5f, y = 58f / 64f),
    )
}

object BattleMonsterSlots {
    fun forCount(count: Int): List<BattlePosition> = when (count) {
        0 -> emptyList()
        1 -> listOf(BattlePosition(0.76f, 0.82f))
        2 -> listOf(
            BattlePosition(0.66f, 0.83f),
            BattlePosition(0.84f, 0.78f),
        )
        3 -> listOf(
            BattlePosition(0.60f, 0.82f),
            BattlePosition(0.76f, 0.78f),
            BattlePosition(0.90f, 0.84f),
        )
        4 -> listOf(
            BattlePosition(0.57f, 0.77f),
            BattlePosition(0.69f, 0.86f),
            BattlePosition(0.82f, 0.76f),
            BattlePosition(0.92f, 0.85f),
        )
        else -> throw IllegalArgumentException("monster count must be between zero and four")
    }
}
