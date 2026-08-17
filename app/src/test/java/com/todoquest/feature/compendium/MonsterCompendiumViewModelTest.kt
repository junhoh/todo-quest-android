package com.todoquest.feature.compendium

import com.todoquest.R
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.repository.CombatRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonsterCompendiumViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val nameResolver = MonsterNameResolver { nameResId ->
        monsterNames.getValue(nameResId)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun catalogKeepsExplicitOrderAndUndiscoveredEntriesOnlyRetainSpecies() {
        val entries = MonsterCompendiumCatalog.entries(
            discoveredSpecies = setOf(
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.SLIME,
            ),
        )

        assertEquals(
            listOf(
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.HARPY,
                MonsterSpecies.SLIME,
            ),
            entries.map(MonsterCompendiumEntryUiModel::species),
        )
        assertTrue(entries[0] is MonsterCompendiumEntryUiModel.Discovered)
        assertEquals(
            MonsterCompendiumEntryUiModel.Undiscovered(
                species = MonsterSpecies.SKELETON_SOLDIER,
            ),
            entries[1],
        )
        assertTrue(entries[2] is MonsterCompendiumEntryUiModel.Discovered)
        assertEquals(
            MonsterCompendiumEntryUiModel.Undiscovered(
                species = MonsterSpecies.HARPY,
            ),
            entries[3],
        )
        assertTrue(entries[4] is MonsterCompendiumEntryUiModel.Discovered)

        assertEquals(
            setOf("species"),
            MonsterCompendiumEntryUiModel.Undiscovered::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("$") }
                .toSet(),
        )
    }

    @Test
    fun catalogMapsDiscoveredSpeciesToSharedVisualAndExactDescriptionResources() {
        val entries = MonsterCompendiumCatalog.entries(MonsterSpecies.entries.toSet())
            .map { it as MonsterCompendiumEntryUiModel.Discovered }

        assertEquals(
            listOf(
                R.drawable.todo_quest_goblin_scout_front_idle,
                R.drawable.todo_quest_skeleton_soldier_front_idle,
                R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
                R.drawable.todo_quest_harpy_front_idle,
                R.drawable.todo_quest_slime_front_idle,
            ),
            entries.map(MonsterCompendiumEntryUiModel.Discovered::spriteResId),
        )
        assertEquals(
            listOf(
                R.string.monster_compendium_goblin_scout_description,
                R.string.monster_compendium_skeleton_soldier_description,
                R.string.monster_compendium_corrupted_tree_spirit_description,
                R.string.monster_compendium_harpy_description,
                R.string.monster_compendium_slime_description,
            ),
            entries.map(MonsterCompendiumEntryUiModel.Discovered::descriptionResId),
        )
    }

    @Test
    fun projectionCalculatesZeroOneAndFullCollectionProgressIncludingEmptyCatalog() {
        val zero = content(discoveredSpecies = emptySet())
        val one = content(discoveredSpecies = setOf(MonsterSpecies.SLIME))
        val full = content(discoveredSpecies = MonsterSpecies.entries.toSet())
        val emptyCatalog = MonsterCompendiumProjection.createContent(
            entries = emptyList(),
            presentationState = MonsterCompendiumPresentationState(),
            nameResolver = nameResolver,
        )

        assertCollection(zero, discovered = 0, total = 5, progress = 0f, percent = 0)
        assertCollection(one, discovered = 1, total = 5, progress = 0.2f, percent = 20)
        assertCollection(full, discovered = 5, total = 5, progress = 1f, percent = 100)
        assertCollection(emptyCatalog, discovered = 0, total = 0, progress = 0f, percent = 0)
    }

    @Test
    fun projectionAppliesAllDiscoveredAndUndiscoveredFilters() {
        val discoveredSpecies = setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.HARPY)

        val all = content(discoveredSpecies = discoveredSpecies)
        val discovered = content(
            discoveredSpecies = discoveredSpecies,
            presentationState = MonsterCompendiumPresentationState(
                selectedFilter = MonsterCompendiumFilter.DISCOVERED,
            ),
        )
        val undiscovered = content(
            discoveredSpecies = discoveredSpecies,
            presentationState = MonsterCompendiumPresentationState(
                selectedFilter = MonsterCompendiumFilter.UNDISCOVERED,
            ),
        )

        assertEquals(MonsterSpecies.entries.toList(), all.visibleEntries.map { it.species })
        assertEquals(
            listOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.HARPY),
            discovered.visibleEntries.map { it.species },
        )
        assertEquals(
            listOf(
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.SLIME,
            ),
            undiscovered.visibleEntries.map { it.species },
        )
    }

    @Test
    fun projectionCombinesTrimmedSearchWithFilterAndTreatsWhitespaceAsEmpty() {
        val discoveredSpecies = setOf(
            MonsterSpecies.GOBLIN_SCOUT,
            MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            MonsterSpecies.HARPY,
        )
        val matching = content(
            discoveredSpecies = discoveredSpecies,
            presentationState = MonsterCompendiumPresentationState(
                searchQuery = "  나무 정령  ",
                isSearchActive = true,
                selectedFilter = MonsterCompendiumFilter.DISCOVERED,
            ),
        )
        val excludedByFilter = content(
            discoveredSpecies = discoveredSpecies,
            presentationState = MonsterCompendiumPresentationState(
                searchQuery = "정령",
                isSearchActive = true,
                selectedFilter = MonsterCompendiumFilter.UNDISCOVERED,
            ),
        )
        val whitespace = content(
            discoveredSpecies = discoveredSpecies,
            presentationState = MonsterCompendiumPresentationState(
                searchQuery = "   ",
                isSearchActive = true,
            ),
        )

        assertEquals(
            listOf(MonsterSpecies.CORRUPTED_TREE_SPIRIT),
            matching.visibleEntries.map { it.species },
        )
        assertTrue(matching.hasActiveCriteria)
        assertTrue(excludedByFilter.visibleEntries.isEmpty())
        assertEquals(MonsterSpecies.entries.toList(), whitespace.visibleEntries.map { it.species })
        assertFalse(whitespace.hasActiveCriteria)
    }

    @Test
    fun searchNeverRevealsOrMatchesAnUndiscoveredSkeletonName() {
        val content = content(
            discoveredSpecies = setOf(MonsterSpecies.GOBLIN_SCOUT),
            presentationState = MonsterCompendiumPresentationState(
                searchQuery = "해골",
                isSearchActive = true,
            ),
        )

        assertTrue(content.visibleEntries.isEmpty())
        val skeleton = MonsterCompendiumCatalog.entries(setOf(MonsterSpecies.GOBLIN_SCOUT))[1]
        assertEquals(
            MonsterCompendiumEntryUiModel.Undiscovered(MonsterSpecies.SKELETON_SOLDIER),
            skeleton,
        )
        assertFalse(
            MonsterCompendiumEntryUiModel.Undiscovered::class.java.declaredFields.any {
                it.name.contains("name", ignoreCase = true)
            },
        )
    }

    @Test
    fun resolverFailureOnlyExcludesTheAffectedDiscoveredEntryFromNonEmptySearch() {
        val entries = MonsterCompendiumCatalog.entries(
            setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.HARPY),
        )
        val content = MonsterCompendiumProjection.createContent(
            entries = entries,
            presentationState = MonsterCompendiumPresentationState(
                searchQuery = "고블린",
                isSearchActive = true,
            ),
            nameResolver = MonsterNameResolver { throw IllegalStateException("resource failed") },
        )

        assertTrue(content.visibleEntries.isEmpty())
        assertEquals(MonsterSpecies.GOBLIN_SCOUT, content.selectedMonster?.species)
    }

    @Test
    fun selectionDefaultsToFirstDiscoveredPersistsAndFallsBackWhenSpeciesDisappears() =
        runTest(dispatcher) {
            val discoveries = MutableStateFlow(
                setOf(MonsterSpecies.SKELETON_SOLDIER, MonsterSpecies.HARPY),
            )
            val viewModel = MonsterCompendiumViewModel(
                combatRepository = FakeCombatRepository(ArrayDeque(listOf(discoveries))),
                nameResolver = nameResolver,
            )
            advanceUntilIdle()

            assertEquals(
                MonsterSpecies.SKELETON_SOLDIER,
                content(viewModel).selectedMonster?.species,
            )

            viewModel.onEvent(
                MonsterCompendiumEvent.SelectMonster(MonsterSpecies.HARPY),
            )
            viewModel.onEvent(
                MonsterCompendiumEvent.FilterSelected(MonsterCompendiumFilter.UNDISCOVERED),
            )
            advanceUntilIdle()
            assertEquals(MonsterSpecies.HARPY, content(viewModel).selectedMonster?.species)
            assertFalse(
                content(viewModel).visibleEntries.any { it.species == MonsterSpecies.HARPY },
            )

            discoveries.value = setOf(
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
            )
            advanceUntilIdle()
            assertEquals(
                MonsterSpecies.GOBLIN_SCOUT,
                content(viewModel).selectedMonster?.species,
            )
        }

    @Test
    fun undiscoveredSelectionEmitsReplaylessNoticeAndDiscoveredDetailOpensAndCloses() =
        runTest(dispatcher) {
            val discoveries = MutableStateFlow(setOf(MonsterSpecies.GOBLIN_SCOUT))
            val viewModel = MonsterCompendiumViewModel(
                combatRepository = FakeCombatRepository(ArrayDeque(listOf(discoveries))),
                nameResolver = nameResolver,
            )
            advanceUntilIdle()
            val effect = async(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.first()
            }

            viewModel.onEvent(
                MonsterCompendiumEvent.SelectMonster(MonsterSpecies.SKELETON_SOLDIER),
            )

            assertEquals(MonsterCompendiumEffect.ShowUndiscoveredNotice, effect.await())
            assertTrue(viewModel.effects.replayCache.isEmpty())
            assertEquals(MonsterSpecies.GOBLIN_SCOUT, content(viewModel).selectedMonster?.species)
            assertNull(content(viewModel).detailMonster)

            viewModel.onEvent(
                MonsterCompendiumEvent.SelectMonster(MonsterSpecies.GOBLIN_SCOUT),
            )
            viewModel.onEvent(MonsterCompendiumEvent.OpenSelectedMonsterDetail)
            advanceUntilIdle()
            assertEquals(MonsterSpecies.GOBLIN_SCOUT, content(viewModel).detailMonster?.species)

            viewModel.onEvent(MonsterCompendiumEvent.CloseMonsterDetail)
            advanceUntilIdle()
            assertNull(content(viewModel).detailMonster)

            viewModel.onEvent(MonsterCompendiumEvent.OpenSelectedMonsterDetail)
            discoveries.value = emptySet()
            advanceUntilIdle()
            assertNull(content(viewModel).detailMonster)
            assertNull(content(viewModel).selectedMonster)
        }

    @Test
    fun searchCloseAndCriteriaResetClearTheExpectedPresentationState() = runTest(dispatcher) {
        val viewModel = MonsterCompendiumViewModel(
            combatRepository = FakeCombatRepository(
                ArrayDeque(listOf(flowOf(setOf(MonsterSpecies.GOBLIN_SCOUT)))),
            ),
            nameResolver = nameResolver,
        )
        advanceUntilIdle()

        viewModel.onEvent(MonsterCompendiumEvent.SearchOpened)
        viewModel.onEvent(MonsterCompendiumEvent.SearchQueryChanged("고블린"))
        viewModel.onEvent(
            MonsterCompendiumEvent.FilterSelected(MonsterCompendiumFilter.DISCOVERED),
        )
        advanceUntilIdle()
        assertTrue(content(viewModel).isSearchActive)
        assertTrue(content(viewModel).hasActiveCriteria)

        viewModel.onEvent(MonsterCompendiumEvent.SearchClosed)
        advanceUntilIdle()
        assertEquals("", content(viewModel).searchQuery)
        assertFalse(content(viewModel).isSearchActive)
        assertEquals(MonsterCompendiumFilter.DISCOVERED, content(viewModel).selectedFilter)
        assertTrue(content(viewModel).hasActiveCriteria)

        viewModel.onEvent(MonsterCompendiumEvent.SearchOpened)
        viewModel.onEvent(MonsterCompendiumEvent.SearchQueryChanged("고블린"))
        viewModel.onEvent(MonsterCompendiumEvent.ResetCriteria)
        advanceUntilIdle()
        assertEquals("", content(viewModel).searchQuery)
        assertFalse(content(viewModel).isSearchActive)
        assertEquals(MonsterCompendiumFilter.ALL, content(viewModel).selectedFilter)
        assertFalse(content(viewModel).hasActiveCriteria)
    }

    @Test
    fun compendiumRetryStartsAFreshCollectionWithoutResettingPresentationState() =
        runTest(dispatcher) {
            val repository = FakeCombatRepository(
                discoveryFlows = ArrayDeque(
                    listOf(
                        flow { throw IllegalStateException("first collection failed") },
                        flowOf(setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.SLIME)),
                    ),
                ),
            )
            val viewModel = MonsterCompendiumViewModel(repository, nameResolver)
            viewModel.onEvent(MonsterCompendiumEvent.SearchOpened)
            viewModel.onEvent(MonsterCompendiumEvent.SearchQueryChanged("슬라임"))

            assertEquals(MonsterCompendiumUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(MonsterCompendiumUiState.Error, viewModel.uiState.value)

            viewModel.onEvent(MonsterCompendiumEvent.Retry)
            advanceUntilIdle()

            val content = content(viewModel)
            assertEquals(2, content.discoveredCount)
            assertEquals("슬라임", content.searchQuery)
            assertEquals(listOf(MonsterSpecies.SLIME), content.visibleEntries.map { it.species })
            assertEquals(2, repository.discoveryCollectionCount)
        }

    @Test
    fun compatibilityDetailNeverExposesResourcesUntilRequestedSpeciesIsDiscovered() =
        runTest(dispatcher) {
            val repository = FakeCombatRepository(
                discoveryFlows = ArrayDeque(
                    listOf(flowOf(setOf(MonsterSpecies.GOBLIN_SCOUT))),
                ),
            )
            val lockedViewModel = MonsterDetailViewModel(
                combatRepository = repository,
                species = MonsterSpecies.HARPY,
            )

            assertEquals(MonsterDetailUiState.Loading, lockedViewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(
                MonsterDetailUiState.Locked(species = MonsterSpecies.HARPY),
                lockedViewModel.uiState.value,
            )

            assertEquals(
                setOf("species"),
                MonsterDetailUiState.Locked::class.java.declaredFields
                    .map { it.name }
                    .filterNot { it.startsWith("$") }
                    .toSet(),
            )
        }

    @Test
    fun compatibilityDetailMapsDiscoveredSpeciesAndRetriesAfterCollectionFailure() =
        runTest(dispatcher) {
            val repository = FakeCombatRepository(
                discoveryFlows = ArrayDeque(
                    listOf(
                        flow { throw IllegalStateException("first detail collection failed") },
                        flowOf(setOf(MonsterSpecies.HARPY)),
                    ),
                ),
            )
            val viewModel = MonsterDetailViewModel(repository, MonsterSpecies.HARPY)

            advanceUntilIdle()
            assertEquals(MonsterDetailUiState.Error, viewModel.uiState.value)

            viewModel.onEvent(MonsterDetailEvent.Retry)
            advanceUntilIdle()

            assertEquals(
                MonsterDetailUiState.Discovered(
                    species = MonsterSpecies.HARPY,
                    nameResId = R.string.battle_monster_harpy_name,
                    spriteResId = R.drawable.todo_quest_harpy_front_idle,
                    descriptionResId = R.string.monster_compendium_harpy_description,
                ),
                viewModel.uiState.value,
            )
            assertEquals(2, repository.discoveryCollectionCount)
        }

    private fun content(
        discoveredSpecies: Set<MonsterSpecies>,
        presentationState: MonsterCompendiumPresentationState =
            MonsterCompendiumPresentationState(),
    ): MonsterCompendiumUiState.Content = MonsterCompendiumProjection.createContent(
        entries = MonsterCompendiumCatalog.entries(discoveredSpecies),
        presentationState = presentationState,
        nameResolver = nameResolver,
    )

    private fun content(viewModel: MonsterCompendiumViewModel): MonsterCompendiumUiState.Content =
        viewModel.uiState.value as MonsterCompendiumUiState.Content

    private fun assertCollection(
        content: MonsterCompendiumUiState.Content,
        discovered: Int,
        total: Int,
        progress: Float,
        percent: Int,
    ) {
        assertEquals(discovered, content.discoveredCount)
        assertEquals(total, content.totalCount)
        assertEquals(progress, content.collectionProgress)
        assertEquals(percent, content.collectionPercent)
    }
}

private val monsterNames = mapOf(
    R.string.battle_monster_goblin_scout_name to "고블린 정찰병",
    R.string.battle_monster_skeleton_soldier_name to "해골 병사",
    R.string.battle_monster_corrupted_tree_spirit_name to "타락한 나무 정령",
    R.string.battle_monster_harpy_name to "하피",
    R.string.battle_monster_slime_name to "슬라임",
)

private class FakeCombatRepository(
    private val discoveryFlows: ArrayDeque<Flow<Set<MonsterSpecies>>>,
) : CombatRepository {
    var discoveryCollectionCount: Int = 0
        private set

    override fun observeDiscoveredMonsterSpecies(): Flow<Set<MonsterSpecies>> {
        discoveryCollectionCount += 1
        return discoveryFlows.removeFirst()
    }

    override fun observeCombat(): Flow<CombatSnapshot> = emptyFlow()

    override suspend fun processPlayerAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): PlayerAttackResult = error("not used")

    override suspend fun processPendingPlayerAttacks(): Int = error("not used")

    override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult =
        error("not used")

    override suspend fun processFailedOccurrenceAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): MonsterAttackResult = error("not used")
}
