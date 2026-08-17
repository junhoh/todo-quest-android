package com.todoquest.feature.compendium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.repository.CombatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class MonsterCompendiumViewModel(
    private val combatRepository: CombatRepository,
    private val nameResolver: MonsterNameResolver,
) : ViewModel() {
    private val collectionGeneration = MutableStateFlow(0)
    private val presentationState = MutableStateFlow(MonsterCompendiumPresentationState())
    private val mutableEffects = MutableSharedFlow<MonsterCompendiumEffect>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    private var latestEntries: List<MonsterCompendiumEntryUiModel> = emptyList()

    val effects: SharedFlow<MonsterCompendiumEffect> = mutableEffects.asSharedFlow()

    private val collectionState: Flow<MonsterCompendiumCollectionState> = collectionGeneration
        .flatMapLatest {
            combatRepository.observeDiscoveredMonsterSpecies()
                .map<Set<MonsterSpecies>, MonsterCompendiumCollectionState> { discovered ->
                    val entries = MonsterCompendiumCatalog.entries(discovered)
                    latestEntries = entries
                    presentationState.update { current ->
                        MonsterCompendiumProjection.normalizePresentationState(entries, current)
                    }
                    MonsterCompendiumCollectionState.Content(entries)
                }
                .onStart { emit(MonsterCompendiumCollectionState.Loading) }
                .catch { emit(MonsterCompendiumCollectionState.Error) }
        }

    val uiState: StateFlow<MonsterCompendiumUiState> = combine(
        collectionState,
        presentationState,
    ) { collection, presentation ->
        when (collection) {
            MonsterCompendiumCollectionState.Loading -> MonsterCompendiumUiState.Loading
            MonsterCompendiumCollectionState.Error -> MonsterCompendiumUiState.Error
            is MonsterCompendiumCollectionState.Content ->
                MonsterCompendiumProjection.createContent(
                    entries = collection.entries,
                    presentationState = presentation,
                    nameResolver = nameResolver,
                )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MonsterCompendiumUiState.Loading,
        )

    fun onEvent(event: MonsterCompendiumEvent) {
        when (event) {
            MonsterCompendiumEvent.SearchOpened -> presentationState.update {
                it.copy(isSearchActive = true)
            }
            MonsterCompendiumEvent.SearchClosed -> presentationState.update {
                it.copy(searchQuery = "", isSearchActive = false)
            }
            is MonsterCompendiumEvent.SearchQueryChanged -> presentationState.update {
                it.copy(searchQuery = event.query)
            }
            is MonsterCompendiumEvent.FilterSelected -> presentationState.update {
                it.copy(selectedFilter = event.filter)
            }
            is MonsterCompendiumEvent.SelectMonster -> selectMonster(event.species)
            MonsterCompendiumEvent.OpenSelectedMonsterDetail -> openSelectedMonsterDetail()
            MonsterCompendiumEvent.CloseMonsterDetail -> presentationState.update {
                it.copy(detailSpecies = null)
            }
            MonsterCompendiumEvent.ResetCriteria -> presentationState.update {
                it.copy(
                    searchQuery = "",
                    isSearchActive = false,
                    selectedFilter = MonsterCompendiumFilter.ALL,
                )
            }
            MonsterCompendiumEvent.Retry -> retry()
        }
    }

    fun retry() {
        collectionGeneration.update { it + 1 }
    }

    private fun selectMonster(species: MonsterSpecies) {
        when (latestEntries.firstOrNull { it.species == species }) {
            is MonsterCompendiumEntryUiModel.Discovered -> presentationState.update {
                it.copy(selectedSpecies = species)
            }
            is MonsterCompendiumEntryUiModel.Undiscovered -> {
                mutableEffects.tryEmit(MonsterCompendiumEffect.ShowUndiscoveredNotice)
            }
            null -> Unit
        }
    }

    private fun openSelectedMonsterDetail() {
        val normalized = MonsterCompendiumProjection.normalizePresentationState(
            entries = latestEntries,
            presentationState = presentationState.value,
        )
        presentationState.value = normalized.copy(
            detailSpecies = normalized.selectedSpecies,
        )
    }
}

private sealed interface MonsterCompendiumCollectionState {
    data object Loading : MonsterCompendiumCollectionState
    data object Error : MonsterCompendiumCollectionState
    data class Content(
        val entries: List<MonsterCompendiumEntryUiModel>,
    ) : MonsterCompendiumCollectionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonsterDetailViewModel(
    private val combatRepository: CombatRepository,
    private val species: MonsterSpecies,
) : ViewModel() {
    private val collectionGeneration = MutableStateFlow(0)

    val uiState: StateFlow<MonsterDetailUiState> = collectionGeneration
        .flatMapLatest {
            combatRepository.observeDiscoveredMonsterSpecies()
                .map<Set<MonsterSpecies>, MonsterDetailUiState> { discovered ->
                    if (species in discovered) {
                        MonsterCompendiumCatalog.discoveredEntry(species).let { entry ->
                            MonsterDetailUiState.Discovered(
                                species = entry.species,
                                nameResId = entry.nameResId,
                                spriteResId = entry.spriteResId,
                                descriptionResId = entry.descriptionResId,
                            )
                        }
                    } else {
                        MonsterDetailUiState.Locked(species = species)
                    }
                }
                .onStart { emit(MonsterDetailUiState.Loading) }
                .catch { emit(MonsterDetailUiState.Error) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MonsterDetailUiState.Loading,
        )

    fun onEvent(event: MonsterDetailEvent) {
        when (event) {
            MonsterDetailEvent.Retry -> retry()
        }
    }

    fun retry() {
        collectionGeneration.update { it + 1 }
    }
}
