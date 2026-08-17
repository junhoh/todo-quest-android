package com.todoquest.ui.monster

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.todoquest.R
import com.todoquest.domain.model.MonsterSpecies

@Immutable
data class MonsterVisual(
    @param:DrawableRes val spriteResId: Int,
    @param:StringRes val nameResId: Int,
)

object MonsterVisualCatalog {
    fun forSpecies(species: MonsterSpecies): MonsterVisual = when (species) {
        MonsterSpecies.GOBLIN_SCOUT -> MonsterVisual(
            spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
            nameResId = R.string.battle_monster_goblin_scout_name,
        )
        MonsterSpecies.SKELETON_SOLDIER -> MonsterVisual(
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
        )
        MonsterSpecies.CORRUPTED_TREE_SPIRIT -> MonsterVisual(
            spriteResId = R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
            nameResId = R.string.battle_monster_corrupted_tree_spirit_name,
        )
        MonsterSpecies.HARPY -> MonsterVisual(
            spriteResId = R.drawable.todo_quest_harpy_front_idle,
            nameResId = R.string.battle_monster_harpy_name,
        )
        MonsterSpecies.SLIME -> MonsterVisual(
            spriteResId = R.drawable.todo_quest_slime_front_idle,
            nameResId = R.string.battle_monster_slime_name,
        )
    }
}
