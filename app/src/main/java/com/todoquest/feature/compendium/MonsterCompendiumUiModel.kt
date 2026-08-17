package com.todoquest.feature.compendium

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.todoquest.R
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.ui.monster.MonsterVisualCatalog

@Immutable
sealed interface MonsterCompendiumEntryUiModel {
    val species: MonsterSpecies

    @Immutable
    data class Undiscovered(
        override val species: MonsterSpecies,
    ) : MonsterCompendiumEntryUiModel

    @Immutable
    data class Discovered(
        override val species: MonsterSpecies,
        @param:StringRes val nameResId: Int,
        @param:DrawableRes val spriteResId: Int,
        @param:StringRes val descriptionResId: Int,
    ) : MonsterCompendiumEntryUiModel
}

enum class MonsterCompendiumFilter {
    ALL,
    DISCOVERED,
    UNDISCOVERED,
}

fun interface MonsterNameResolver {
    fun resolve(@StringRes nameResId: Int): String
}

@Immutable
data class MonsterCompendiumPresentationState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedFilter: MonsterCompendiumFilter = MonsterCompendiumFilter.ALL,
    val selectedSpecies: MonsterSpecies? = null,
    val detailSpecies: MonsterSpecies? = null,
)

object MonsterCompendiumCatalog {
    val orderedSpecies: List<MonsterSpecies> = listOf(
        MonsterSpecies.GOBLIN_SCOUT,
        MonsterSpecies.SKELETON_SOLDIER,
        MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        MonsterSpecies.HARPY,
        MonsterSpecies.SLIME,
    )

    fun entries(
        discoveredSpecies: Set<MonsterSpecies>,
    ): List<MonsterCompendiumEntryUiModel> = orderedSpecies.map { species ->
        if (species in discoveredSpecies) {
            discoveredEntry(species)
        } else {
            undiscoveredEntry(species)
        }
    }

    fun discoveredEntry(species: MonsterSpecies): MonsterCompendiumEntryUiModel.Discovered {
        val visual = MonsterVisualCatalog.forSpecies(species)
        return MonsterCompendiumEntryUiModel.Discovered(
            species = species,
            nameResId = visual.nameResId,
            spriteResId = visual.spriteResId,
            descriptionResId = descriptionForSpecies(species),
        )
    }

    fun undiscoveredEntry(species: MonsterSpecies): MonsterCompendiumEntryUiModel.Undiscovered =
        MonsterCompendiumEntryUiModel.Undiscovered(
            species = species,
        )

    @StringRes
    private fun descriptionForSpecies(species: MonsterSpecies): Int = when (species) {
        MonsterSpecies.GOBLIN_SCOUT -> R.string.monster_compendium_goblin_scout_description
        MonsterSpecies.SKELETON_SOLDIER ->
            R.string.monster_compendium_skeleton_soldier_description
        MonsterSpecies.CORRUPTED_TREE_SPIRIT ->
            R.string.monster_compendium_corrupted_tree_spirit_description
        MonsterSpecies.HARPY -> R.string.monster_compendium_harpy_description
        MonsterSpecies.SLIME -> R.string.monster_compendium_slime_description
    }
}

object MonsterCompendiumProjection {
    fun normalizePresentationState(
        entries: List<MonsterCompendiumEntryUiModel>,
        presentationState: MonsterCompendiumPresentationState,
    ): MonsterCompendiumPresentationState {
        val discoveredEntries = entries.filterIsInstance<MonsterCompendiumEntryUiModel.Discovered>()
        val selectedSpecies = discoveredEntries
            .firstOrNull { it.species == presentationState.selectedSpecies }
            ?.species
            ?: discoveredEntries.firstOrNull()?.species
        val detailSpecies = discoveredEntries
            .firstOrNull { it.species == presentationState.detailSpecies }
            ?.species
        return presentationState.copy(
            selectedSpecies = selectedSpecies,
            detailSpecies = detailSpecies,
        )
    }

    fun createContent(
        entries: List<MonsterCompendiumEntryUiModel>,
        presentationState: MonsterCompendiumPresentationState,
        nameResolver: MonsterNameResolver,
    ): MonsterCompendiumUiState.Content {
        val normalizedState = normalizePresentationState(entries, presentationState)
        val discoveredEntries = entries.filterIsInstance<MonsterCompendiumEntryUiModel.Discovered>()
        val discoveredCount = discoveredEntries.size
        val totalCount = entries.size
        val collectionProgress = if (totalCount == 0) {
            0f
        } else {
            (discoveredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        }
        val collectionPercent = if (totalCount == 0) {
            0
        } else {
            discoveredCount * 100 / totalCount
        }
        val trimmedQuery = normalizedState.searchQuery.trim()
        val visibleEntries = entries.filter { entry ->
            entry.matches(normalizedState.selectedFilter) &&
                entry.matches(trimmedQuery, nameResolver)
        }
        return MonsterCompendiumUiState.Content(
            visibleEntries = visibleEntries,
            discoveredCount = discoveredCount,
            totalCount = totalCount,
            collectionProgress = collectionProgress,
            collectionPercent = collectionPercent,
            searchQuery = normalizedState.searchQuery,
            isSearchActive = normalizedState.isSearchActive,
            selectedFilter = normalizedState.selectedFilter,
            selectedMonster = discoveredEntries.firstOrNull {
                it.species == normalizedState.selectedSpecies
            },
            detailMonster = discoveredEntries.firstOrNull {
                it.species == normalizedState.detailSpecies
            },
            hasActiveCriteria = trimmedQuery.isNotEmpty() ||
                normalizedState.selectedFilter != MonsterCompendiumFilter.ALL,
        )
    }

    private fun MonsterCompendiumEntryUiModel.matches(
        filter: MonsterCompendiumFilter,
    ): Boolean = when (filter) {
        MonsterCompendiumFilter.ALL -> true
        MonsterCompendiumFilter.DISCOVERED ->
            this is MonsterCompendiumEntryUiModel.Discovered
        MonsterCompendiumFilter.UNDISCOVERED ->
            this is MonsterCompendiumEntryUiModel.Undiscovered
    }

    private fun MonsterCompendiumEntryUiModel.matches(
        trimmedQuery: String,
        nameResolver: MonsterNameResolver,
    ): Boolean {
        if (trimmedQuery.isEmpty()) return true
        val discoveredEntry = this as? MonsterCompendiumEntryUiModel.Discovered ?: return false
        val resolvedName = runCatching {
            nameResolver.resolve(discoveredEntry.nameResId)
        }.getOrNull() ?: return false
        return resolvedName.contains(trimmedQuery, ignoreCase = true)
    }
}

@Immutable
sealed interface MonsterCompendiumUiState {
    data object Loading : MonsterCompendiumUiState

    @Immutable
    data class Content(
        val visibleEntries: List<MonsterCompendiumEntryUiModel>,
        val discoveredCount: Int,
        val totalCount: Int,
        val collectionProgress: Float,
        val collectionPercent: Int,
        val searchQuery: String,
        val isSearchActive: Boolean,
        val selectedFilter: MonsterCompendiumFilter,
        val selectedMonster: MonsterCompendiumEntryUiModel.Discovered?,
        val detailMonster: MonsterCompendiumEntryUiModel.Discovered?,
        val hasActiveCriteria: Boolean,
    ) : MonsterCompendiumUiState

    data object Error : MonsterCompendiumUiState
}

@Immutable
sealed interface MonsterDetailUiState {
    data object Loading : MonsterDetailUiState

    @Immutable
    data class Discovered(
        val species: MonsterSpecies,
        @param:StringRes val nameResId: Int,
        @param:DrawableRes val spriteResId: Int,
        @param:StringRes val descriptionResId: Int,
    ) : MonsterDetailUiState

    @Immutable
    data class Locked(
        val species: MonsterSpecies,
    ) : MonsterDetailUiState

    data object Error : MonsterDetailUiState
}

sealed interface MonsterCompendiumEvent {
    data object SearchOpened : MonsterCompendiumEvent
    data object SearchClosed : MonsterCompendiumEvent
    data class SearchQueryChanged(val query: String) : MonsterCompendiumEvent
    data class FilterSelected(val filter: MonsterCompendiumFilter) : MonsterCompendiumEvent
    data class SelectMonster(val species: MonsterSpecies) : MonsterCompendiumEvent
    data object OpenSelectedMonsterDetail : MonsterCompendiumEvent
    data object CloseMonsterDetail : MonsterCompendiumEvent
    data object ResetCriteria : MonsterCompendiumEvent
    data object Retry : MonsterCompendiumEvent
}

sealed interface MonsterCompendiumEffect {
    data object ShowUndiscoveredNotice : MonsterCompendiumEffect
}

sealed interface MonsterDetailEvent {
    data object Retry : MonsterDetailEvent
}
