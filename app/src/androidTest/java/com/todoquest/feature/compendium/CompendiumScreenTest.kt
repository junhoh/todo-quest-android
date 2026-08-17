package com.todoquest.feature.compendium

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.R
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.ui.theme.TodoQuestTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompendiumScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compendiumHomeShowsTheSingleWorkingMonsterCategory() {
        var openCount = 0
        composeRule.setContent {
            TodoQuestTheme {
                CompendiumScreen(onOpenMonsters = { openCount += 1 })
            }
        }

        composeRule.onNodeWithText("도감").assertIsDisplayed()
        composeRule.onNodeWithText("몬스터")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun collectionSummaryUsesStateCountsProgressAndZeroTotal() {
        val state = mutableStateOf(contentState(MonsterSpecies.entries.take(2).toSet()))
        composeRule.setContent {
            TodoQuestTheme {
                MonsterCompendiumScreen(
                    state = state.value,
                    effects = MutableSharedFlow(),
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithTag("monster-compendium-summary")
            .assertContentDescriptionEquals("발견한 몬스터 2 / 5, 수집률 40%")
        composeRule.onNodeWithText("발견한 몬스터").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithText("수집률 40%").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = MonsterCompendiumUiState.Content(
                visibleEntries = emptyList(),
                discoveredCount = 0,
                totalCount = 0,
                collectionProgress = 0f,
                collectionPercent = 0,
                searchQuery = "",
                isSearchActive = false,
                selectedFilter = MonsterCompendiumFilter.ALL,
                selectedMonster = null,
                detailMonster = null,
                hasActiveCriteria = false,
            )
        }
        composeRule.onNodeWithTag("monster-compendium-summary")
            .assertContentDescriptionEquals("발견한 몬스터 0 / 0, 수집률 0%")
        composeRule.onNodeWithText("0 / 0").assertIsDisplayed()
        composeRule.onNodeWithText("수집률 0%").assertIsDisplayed()
        composeRule.onNodeWithTag("monster-compendium-preview")
            .assertHasNoClickAction()
        composeRule.onNodeWithText("아직 발견한 몬스터가 없습니다").assertIsDisplayed()
        composeRule.onNodeWithText("등록된 몬스터가 없습니다").assertIsDisplayed()
    }

    @Test
    fun gridUsesThreeColumnsAt320DpAndFiveColumnsOnWideContentWithLastCardReachable() {
        val width = mutableStateOf(320.dp)
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.requiredWidth(width.value).height(640.dp)) {
                    MonsterCompendiumScreen(
                        state = contentState(MonsterSpecies.entries.toSet()),
                        effects = MutableSharedFlow(),
                        onBack = {},
                        onEvent = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("monster-compendium-grid-3-columns").assertExists()
        composeRule.onNodeWithTag("monster-compendium-grid")
            .performScrollToIndex(7)
        composeRule.onNodeWithTag("monster-compendium-entry-SLIME").assertIsDisplayed()

        composeRule.runOnIdle { width.value = 720.dp }
        composeRule.onNodeWithTag("monster-compendium-grid-5-columns").assertExists()
        composeRule.onNodeWithTag("monster-compendium-grid")
            .performScrollToIndex(7)
        composeRule.onNodeWithTag("monster-compendium-entry-SLIME").assertExists()
    }

    @Test
    fun discoveredCardsExposeArtworkWhileUndiscoveredCardsKeepSpeciesPrivateAndClickable() {
        val events = mutableListOf<MonsterCompendiumEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                MonsterCompendiumScreen(
                    state = contentState(setOf(MonsterSpecies.GOBLIN_SCOUT)),
                    effects = MutableSharedFlow(),
                    onBack = {},
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
        composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag(
            "monster-compendium-sprite-GOBLIN_SCOUT",
            useUnmergedTree = true,
        )
            .assert(
                SemanticsMatcher.expectValue(
                    MonsterSpriteContentScaleKey,
                    MonsterSpriteContentScale.FIT,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    MonsterSpriteFilterQualityKey,
                    MonsterSpriteFilterQuality.NONE,
                ),
            )
        composeRule.onNodeWithTag("monster-compendium-entry-SKELETON_SOLDIER")
            .assertHasClickAction()
            .assertIsNotSelected()
            .assertContentDescriptionEquals("미발견 몬스터")
            .performClick()
        composeRule.onAllNodesWithText("???", useUnmergedTree = true).assertCountEquals(4)
        composeRule.onNodeWithTag(
            "monster-compendium-lock-SKELETON_SOLDIER",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText("해골 병사").assertDoesNotExist()
        composeRule.onNodeWithTag("monster-compendium-sprite-SKELETON_SOLDIER")
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    MonsterCompendiumEvent.SelectMonster(MonsterSpecies.GOBLIN_SCOUT),
                    MonsterCompendiumEvent.SelectMonster(MonsterSpecies.SKELETON_SOLDIER),
                ),
                events,
            )
        }
    }

    @Test
    fun discoveredSelectionChangesPreviewAndUsesSelectedSemanticsAndMintOutline() {
        val entries = MonsterCompendiumCatalog.entries(
            setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.SLIME),
        )
        var presentation by mutableStateOf(
            MonsterCompendiumPresentationState(selectedSpecies = MonsterSpecies.GOBLIN_SCOUT),
        )
        composeRule.setContent {
            TodoQuestTheme {
                MonsterCompendiumScreen(
                    state = contentState(entries, presentation),
                    effects = MutableSharedFlow(),
                    onBack = {},
                    onEvent = { event -> presentation = reduce(presentation, event) },
                )
            }
        }

        composeRule.onNodeWithTag("monster-compendium-preview")
            .assertContentDescriptionEquals("고블린 정찰병, 발견 완료, 상세 보기")
        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
        composeRule.onNodeWithTag("monster-compendium-entry-SLIME")
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(MonsterCardOutlineWidthKey, 2f),
            )
        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(2)
        composeRule.onNodeWithTag("monster-compendium-preview")
            .assertContentDescriptionEquals("슬라임, 발견 완료, 상세 보기")
        composeRule.onNodeWithText("슬라임").assertIsDisplayed()
    }

    @Test
    fun filtersAndSearchShareViewModelEventsAndEmptyResultsCanResetCriteria() {
        val entries = MonsterCompendiumCatalog.entries(
            setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.SLIME),
        )
        var presentation by mutableStateOf(MonsterCompendiumPresentationState())
        val events = mutableListOf<MonsterCompendiumEvent>()
        composeRule.setContent {
            TodoQuestTheme {
                MonsterCompendiumScreen(
                    state = contentState(entries, presentation),
                    effects = MutableSharedFlow(),
                    onBack = {},
                    onEvent = { event ->
                        events += event
                        presentation = reduce(presentation, event)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("monster-compendium-filter-DISCOVERED")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithContentDescription("몬스터 검색").performClick()
        composeRule.onNodeWithTag("monster-compendium-search-input")
            .performTextInput("슬라임")
        composeRule.onNodeWithTag("monster-compendium-search-input").performImeAction()
        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
        composeRule.onNodeWithTag("monster-compendium-entry-SLIME").assertExists()
        composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("검색어 지우기").performClick()
        composeRule.onNodeWithContentDescription("검색 닫기").performClick()
        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(1)
        composeRule.onNodeWithTag("monster-compendium-filter-UNDISCOVERED").performClick()
        composeRule.onNodeWithContentDescription("몬스터 검색").performClick()
        composeRule.onNodeWithTag("monster-compendium-search-input").performTextInput("슬라임")
        composeRule.onNodeWithText("검색과 필터에 맞는 몬스터가 없습니다").assertIsDisplayed()
        composeRule.onNodeWithText("필터 초기화")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("monster-compendium-filter-ALL").assertIsSelected()
        composeRule.onNodeWithText("검색과 필터에 맞는 몬스터가 없습니다").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(events.contains(MonsterCompendiumEvent.SearchOpened))
            assertTrue(events.contains(MonsterCompendiumEvent.ResetCriteria))
        }
    }

    @Test
    fun undiscoveredClickShowsReplayFreeKoreanSnackbar() {
        val effects = MutableSharedFlow<MonsterCompendiumEffect>(extraBufferCapacity = 1)
        val showScreen = mutableStateOf(true)
        composeRule.setContent {
            TodoQuestTheme {
                if (showScreen.value) {
                    MonsterCompendiumScreen(
                        state = contentState(emptySet()),
                        effects = effects,
                        onBack = {},
                        onEvent = { event ->
                            if (event is MonsterCompendiumEvent.SelectMonster) {
                                effects.tryEmit(MonsterCompendiumEffect.ShowUndiscoveredNotice)
                            }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
        composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT").performClick()
        composeRule.onNodeWithText("아직 발견하지 못한 몬스터입니다").assertIsDisplayed()

        composeRule.runOnIdle { showScreen.value = false }
        composeRule.onNodeWithText("아직 발견하지 못한 몬스터입니다").assertDoesNotExist()
        composeRule.runOnIdle { showScreen.value = true }
        composeRule.onNodeWithText("아직 발견하지 못한 몬스터입니다").assertDoesNotExist()
    }

    @Test
    fun selectedPreviewOpensAndClosesSharedDiscoveredDetailSheet() {
        val selected = MonsterCompendiumCatalog.discoveredEntry(MonsterSpecies.HARPY)
        var presentation by mutableStateOf(
            MonsterCompendiumPresentationState(selectedSpecies = MonsterSpecies.HARPY),
        )
        val entries = MonsterCompendiumCatalog.entries(setOf(MonsterSpecies.HARPY))
        composeRule.setContent {
            TodoQuestTheme {
                MonsterCompendiumScreen(
                    state = contentState(entries, presentation),
                    effects = MutableSharedFlow(),
                    onBack = {},
                    onEvent = { event -> presentation = reduce(presentation, event) },
                )
            }
        }

        composeRule.onNodeWithTag("monster-compendium-preview")
            .assertHasClickAction()
            .assertHeightIsAtLeast(160.dp)
            .performClick()
        composeRule.onNodeWithTag("monster-compendium-detail-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("monster-detail-name").assertIsDisplayed()
        composeRule.onNodeWithText(
            "좌우로 펼친 날개와 짧은 다리, 날카로운 발톱과 붉은 눈이 특징인 하피입니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("발견 완료").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("몬스터 상세 닫기")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("monster-compendium-detail-sheet")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag("monster-compendium-detail-sheet").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(selected.species, presentation.selectedSpecies) }
    }

    @Test
    fun compactDoubleFontSummaryAndNamesStayInsideBoundsWithMinimumTargets() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        MonsterCompendiumScreen(
                            state = contentState(MonsterSpecies.entries.toSet()),
                            effects = MutableSharedFlow(),
                            onBack = {},
                            onEvent = {},
                        )
                    }
                }
            }
        }

        val summary = composeRule.onNodeWithTag("monster-compendium-summary")
            .fetchSemanticsNode().boundsInRoot
        val summaryLabel = composeRule.onNodeWithTag(
            "monster-compendium-summary-label",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val summaryCount = composeRule.onNodeWithTag(
            "monster-compendium-summary-count",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        assertContains(summary, summaryLabel)
        assertContains(summary, summaryCount)
        assertFalse(summaryLabel.overlaps(summaryCount))

        composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
        val card = composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT")
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val name = composeRule.onNodeWithTag(
            "monster-compendium-name-GOBLIN_SCOUT",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        assertContains(card, name)
        composeRule.onNodeWithTag("monster-compendium-back")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun compatibilityDetailUsesSharedContentAndLockedStateNeverRevealsName() {
        val state = mutableStateOf<MonsterDetailUiState>(
            MonsterDetailUiState.Locked(species = MonsterSpecies.HARPY),
        )
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    MonsterDetailScreen(
                        state = state.value,
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("미발견 몬스터").assertIsDisplayed()
        composeRule.onNodeWithText("하피").assertDoesNotExist()
        composeRule.onNodeWithTag("monster-detail-sprite-HARPY").assertDoesNotExist()

        composeRule.runOnIdle {
            val discovered = MonsterCompendiumCatalog.discoveredEntry(MonsterSpecies.HARPY)
            state.value = MonsterDetailUiState.Discovered(
                species = discovered.species,
                nameResId = discovered.nameResId,
                spriteResId = discovered.spriteResId,
                descriptionResId = discovered.descriptionResId,
            )
        }
        composeRule.onNodeWithTag("monster-detail-content").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "monster-detail-sprite-HARPY",
            useUnmergedTree = true,
        )
            .assert(
                SemanticsMatcher.expectValue(
                    MonsterSpriteContentScaleKey,
                    MonsterSpriteContentScale.FIT,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    MonsterSpriteFilterQualityKey,
                    MonsterSpriteFilterQuality.NONE,
                ),
            )
        composeRule.onNodeWithText(
            "좌우로 펼친 날개와 짧은 다리, 날카로운 발톱과 붉은 눈이 특징인 하피입니다.",
        ).assertIsDisplayed()
    }

    private fun contentState(
        discovered: Set<MonsterSpecies>,
        presentation: MonsterCompendiumPresentationState = MonsterCompendiumPresentationState(),
    ): MonsterCompendiumUiState.Content = contentState(
        entries = MonsterCompendiumCatalog.entries(discovered),
        presentation = presentation,
    )

    private fun contentState(
        entries: List<MonsterCompendiumEntryUiModel>,
        presentation: MonsterCompendiumPresentationState,
    ): MonsterCompendiumUiState.Content {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        return MonsterCompendiumProjection.createContent(
            entries = entries,
            presentationState = presentation,
            nameResolver = MonsterNameResolver(resources::getString),
        )
    }

    private fun reduce(
        current: MonsterCompendiumPresentationState,
        event: MonsterCompendiumEvent,
    ): MonsterCompendiumPresentationState = when (event) {
        MonsterCompendiumEvent.SearchOpened -> current.copy(isSearchActive = true)
        MonsterCompendiumEvent.SearchClosed -> current.copy(searchQuery = "", isSearchActive = false)
        is MonsterCompendiumEvent.SearchQueryChanged -> current.copy(searchQuery = event.query)
        is MonsterCompendiumEvent.FilterSelected -> current.copy(selectedFilter = event.filter)
        is MonsterCompendiumEvent.SelectMonster -> current.copy(selectedSpecies = event.species)
        MonsterCompendiumEvent.OpenSelectedMonsterDetail -> current.copy(
            detailSpecies = current.selectedSpecies,
        )
        MonsterCompendiumEvent.CloseMonsterDetail -> current.copy(detailSpecies = null)
        MonsterCompendiumEvent.ResetCriteria -> current.copy(
            searchQuery = "",
            isSearchActive = false,
            selectedFilter = MonsterCompendiumFilter.ALL,
        )
        MonsterCompendiumEvent.Retry -> current
    }

    private fun assertContains(
        outer: androidx.compose.ui.geometry.Rect,
        inner: androidx.compose.ui.geometry.Rect,
    ) {
        assertTrue(inner.left >= outer.left)
        assertTrue(inner.top >= outer.top)
        assertTrue(inner.right <= outer.right)
        assertTrue(inner.bottom <= outer.bottom)
    }
}
