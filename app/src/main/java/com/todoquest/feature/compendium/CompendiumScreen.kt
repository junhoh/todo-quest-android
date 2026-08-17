package com.todoquest.feature.compendium

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.todoquest.R
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.ui.theme.TodoQuestTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow

internal enum class MonsterSpriteContentScale {
    FIT,
}

internal enum class MonsterSpriteFilterQuality {
    NONE,
}

internal val MonsterSpriteContentScaleKey =
    SemanticsPropertyKey<MonsterSpriteContentScale>("MonsterSpriteContentScale")
internal val MonsterSpriteFilterQualityKey =
    SemanticsPropertyKey<MonsterSpriteFilterQuality>("MonsterSpriteFilterQuality")
internal val MonsterCardOutlineWidthKey =
    SemanticsPropertyKey<Float>("MonsterCardOutlineWidth")

private val CompendiumHorizontalPadding = 16.dp
private val CompendiumGridSpacing = 8.dp
private val CompendiumApproximateCardWidth = 104.dp
private val CompendiumCardAspectRatio = 0.82f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompendiumScreen(
    onOpenMonsters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compendium_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Card(
                onClick = onOpenMonsters,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .testTag("compendium-monster-category"),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.compendium_monster_category),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonsterCompendiumScreen(
    state: MonsterCompendiumUiState,
    effects: Flow<MonsterCompendiumEffect>,
    onBack: () -> Unit,
    onEvent: (MonsterCompendiumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val undiscoveredNotice = stringResource(R.string.monster_compendium_undiscovered_notice)

    LaunchedEffect(effects, lifecycleOwner, undiscoveredNotice) {
        effects
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { effect ->
                when (effect) {
                    MonsterCompendiumEffect.ShowUndiscoveredNotice -> {
                        snackbarHostState.showSnackbar(undiscoveredNotice)
                    }
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MonsterCompendiumTopAppBar(
                state = state,
                onBack = onBack,
                onEvent = onEvent,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (state) {
            MonsterCompendiumUiState.Loading -> CompendiumMessage(
                messageResId = R.string.monster_compendium_loading,
                showProgress = true,
                modifier = Modifier.padding(innerPadding),
            )

            MonsterCompendiumUiState.Error -> CompendiumMessage(
                messageResId = R.string.monster_compendium_error,
                onRetry = { onEvent(MonsterCompendiumEvent.Retry) },
                modifier = Modifier.padding(innerPadding),
            )

            is MonsterCompendiumUiState.Content -> MonsterCompendiumCollection(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    val detailMonster = (state as? MonsterCompendiumUiState.Content)?.detailMonster
    BackHandler(enabled = detailMonster != null) {
        onEvent(MonsterCompendiumEvent.CloseMonsterDetail)
    }
    if (detailMonster != null) {
        MonsterDetailSheet(
            entry = detailMonster,
            onDismiss = { onEvent(MonsterCompendiumEvent.CloseMonsterDetail) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonsterCompendiumTopAppBar(
    state: MonsterCompendiumUiState,
    onBack: () -> Unit,
    onEvent: (MonsterCompendiumEvent) -> Unit,
) {
    val content = state as? MonsterCompendiumUiState.Content
    val isSearchActive = content?.isSearchActive == true
    val focusManager = LocalFocusManager.current
    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = content.searchQuery,
                    onValueChange = {
                        onEvent(MonsterCompendiumEvent.SearchQueryChanged(it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monster-compendium-search-input"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.monster_compendium_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (content.searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    onEvent(MonsterCompendiumEvent.SearchQueryChanged(""))
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(
                                        R.string.monster_compendium_search_clear,
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                    ),
                )
            } else {
                Text(
                    text = stringResource(R.string.monster_compendium_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("monster-compendium-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.monster_compendium_back),
                )
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(
                    onClick = { onEvent(MonsterCompendiumEvent.SearchClosed) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.monster_compendium_search_close,
                        ),
                    )
                }
            } else if (content != null) {
                IconButton(
                    onClick = { onEvent(MonsterCompendiumEvent.SearchOpened) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.monster_compendium_search),
                    )
                }
            }
        },
    )
}

@Composable
private fun MonsterCompendiumCollection(
    state: MonsterCompendiumUiState.Content,
    onEvent: (MonsterCompendiumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableCardWidth = maxWidth - CompendiumHorizontalPadding * 2
        val columns = (
            (availableCardWidth + CompendiumGridSpacing) /
                (CompendiumApproximateCardWidth + CompendiumGridSpacing)
            ).toInt().coerceIn(3, 5)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("monster-compendium-grid-$columns-columns"),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("monster-compendium-grid"),
                contentPadding = PaddingValues(
                    start = CompendiumHorizontalPadding,
                    top = 12.dp,
                    end = CompendiumHorizontalPadding,
                    bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(CompendiumGridSpacing),
                verticalArrangement = Arrangement.spacedBy(CompendiumGridSpacing),
            ) {
                item(
                    key = "collection-summary",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    CollectionSummaryPanel(
                        discoveredCount = state.discoveredCount,
                        totalCount = state.totalCount,
                        progress = state.collectionProgress,
                        percent = state.collectionPercent,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                item(
                    key = "collection-filters",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    MonsterFilterRow(
                        selectedFilter = state.selectedFilter,
                        onSelect = { onEvent(MonsterCompendiumEvent.FilterSelected(it)) },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                item(
                    key = "selected-monster-preview",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SelectedMonsterPreview(
                        selectedMonster = state.selectedMonster,
                        onOpenDetail = {
                            onEvent(MonsterCompendiumEvent.OpenSelectedMonsterDetail)
                        },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                when {
                    state.totalCount == 0 -> item(
                        key = "empty-catalog",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        EmptyCollectionMessage(
                            messageResId = R.string.monster_compendium_empty_catalog,
                        )
                    }

                    state.visibleEntries.isEmpty() -> item(
                        key = "empty-results",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        EmptyCollectionMessage(
                            messageResId = R.string.monster_compendium_empty_results,
                            onReset = if (state.hasActiveCriteria) {
                                { onEvent(MonsterCompendiumEvent.ResetCriteria) }
                            } else {
                                null
                            },
                        )
                    }

                    else -> items(
                        items = state.visibleEntries,
                        key = { it.species.name },
                    ) { entry ->
                        when (entry) {
                            is MonsterCompendiumEntryUiModel.Discovered -> DiscoveredMonsterCard(
                                entry = entry,
                                selected = state.selectedMonster?.species == entry.species,
                                onClick = {
                                    onEvent(MonsterCompendiumEvent.SelectMonster(entry.species))
                                },
                            )

                            is MonsterCompendiumEntryUiModel.Undiscovered ->
                                UndiscoveredMonsterCard(
                                    entry = entry,
                                    onClick = {
                                        onEvent(
                                            MonsterCompendiumEvent.SelectMonster(entry.species),
                                        )
                                    },
                                )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionSummaryPanel(
    discoveredCount: Int,
    totalCount: Int,
    progress: Float,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        R.string.monster_compendium_collection_description,
        discoveredCount,
        totalCount,
        percent,
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monster-compendium-summary")
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.monster_compendium_collection_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("monster-compendium-summary-label"),
                    )
                    Text(
                        text = stringResource(
                            R.string.monster_compendium_collection_count,
                            discoveredCount,
                            totalCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("monster-compendium-summary-count"),
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 8.dp)
                        .testTag("monster-compendium-summary-progress"),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Text(
                    text = stringResource(
                        R.string.monster_compendium_collection_percent,
                        percent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonsterFilterRow(
    selectedFilter: MonsterCompendiumFilter,
    onSelect: (MonsterCompendiumFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monster-compendium-filter-row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = MonsterCompendiumFilter.entries,
            key = { it.name },
        ) { filter ->
            val isSelected = selectedFilter == filter
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .selectable(
                        selected = isSelected,
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { onSelect(filter) },
                    )
                    .testTag("monster-compendium-filter-${filter.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.height(36.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(filter.labelResId()),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedMonsterPreview(
    selectedMonster: MonsterCompendiumEntryUiModel.Discovered?,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewHeight = if (LocalDensity.current.fontScale >= 1.5f) 200.dp else 184.dp
    val previewModifier = modifier
        .fillMaxWidth()
        .height(previewHeight)
        .testTag("monster-compendium-preview")
    if (selectedMonster == null) {
        val lockedDescription = stringResource(R.string.monster_compendium_locked_title)
        Surface(
            modifier = previewModifier
                .semantics(mergeDescendants = true) {
                    contentDescription = lockedDescription
                },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.large,
        ) {
            LockedMonsterPreviewContent()
        }
    } else {
        val name = stringResource(selectedMonster.nameResId)
        val description = stringResource(
            R.string.monster_compendium_preview_description,
            name,
        )
        Surface(
            modifier = previewModifier
                .clickable(onClick = onOpenDetail)
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick {
                        onOpenDetail()
                        true
                    }
                },
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonsterSprite(
                    spriteResId = selectedMonster.spriteResId,
                    contentDescription = name,
                    modifier = Modifier
                        .size(136.dp)
                        .testTag("monster-compendium-preview-sprite"),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.monster_compendium_discovered_complete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedMonsterPreviewContent() {
    val lockedDescription = stringResource(R.string.monster_compendium_locked_title)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = lockedDescription
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp),
        ) {
            Text(
                text = stringResource(R.string.monster_compendium_locked_question),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(32.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.monster_compendium_locked_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.monster_compendium_locked_preview_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveredMonsterCard(
    entry: MonsterCompendiumEntryUiModel.Discovered,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val name = stringResource(entry.nameResId)
    val outlineWidth = if (selected) 2.dp else 1.dp
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CompendiumCardAspectRatio)
            .heightIn(min = 48.dp)
            .testTag("monster-compendium-entry-${entry.species.name}")
            .semantics(mergeDescendants = true) {
                this.selected = selected
                this[MonsterCardOutlineWidthKey] = outlineWidth.value
            },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = BorderStroke(
            outlineWidth,
            if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        MonsterCardContent(
            entry = entry,
            name = name,
        )
    }
}

@Composable
private fun MonsterCardContent(
    entry: MonsterCompendiumEntryUiModel.Discovered,
    name: String,
) {
    val nameSlotHeight = if (LocalDensity.current.fontScale >= 1.5f) 46.dp else 34.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MonsterSprite(
                spriteResId = entry.spriteResId,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("monster-compendium-sprite-${entry.species.name}"),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(nameSlotHeight),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monster-compendium-name-${entry.species.name}"),
            )
        }
    }
}

@Composable
private fun UndiscoveredMonsterCard(
    entry: MonsterCompendiumEntryUiModel.Undiscovered,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.monster_compendium_locked_card_description)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CompendiumCardAspectRatio)
            .heightIn(min = 48.dp)
            .testTag("monster-compendium-entry-${entry.species.name}")
            .clearAndSetSemantics {
                contentDescription = description
                selected = false
                this[MonsterCardOutlineWidthKey] = 1f
                onClick {
                    onClick()
                    true
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.monster_compendium_locked_question),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
                )
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(
                        R.string.monster_compendium_lock_description,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(24.dp)
                        .testTag("monster-compendium-lock-${entry.species.name}"),
                )
            }
            Text(
                text = stringResource(R.string.monster_compendium_locked_symbol),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyCollectionMessage(
    messageResId: Int,
    onReset: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onReset != null) {
            Button(
                onClick = onReset,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.monster_compendium_reset_filters))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonsterDetailSheet(
    entry: MonsterCompendiumEntryUiModel.Discovered,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("monster-compendium-detail-sheet"),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DiscoveredMonsterDetailContent(
                species = entry.species,
                nameResId = entry.nameResId,
                spriteResId = entry.spriteResId,
                descriptionResId = entry.descriptionResId,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(top = 8.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.monster_compendium_detail_close),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonsterDetailScreen(
    state: MonsterDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleResId = when (state) {
        is MonsterDetailUiState.Discovered -> state.nameResId
        is MonsterDetailUiState.Locked,
        MonsterDetailUiState.Loading,
        MonsterDetailUiState.Error,
        -> R.string.monster_compendium_title
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompendiumTopAppBar(titleResId = titleResId, onBack = onBack)
        },
    ) { padding ->
        when (state) {
            MonsterDetailUiState.Loading -> CompendiumMessage(
                messageResId = R.string.monster_compendium_loading,
                showProgress = true,
                modifier = Modifier.padding(padding),
            )

            MonsterDetailUiState.Error -> CompendiumMessage(
                messageResId = R.string.monster_compendium_error,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )

            is MonsterDetailUiState.Locked -> CompendiumMessage(
                messageResId = R.string.monster_compendium_locked_title,
                modifier = Modifier.padding(padding),
            )

            is MonsterDetailUiState.Discovered -> DiscoveredMonsterDetailContent(
                species = state.species,
                nameResId = state.nameResId,
                spriteResId = state.spriteResId,
                descriptionResId = state.descriptionResId,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
internal fun DiscoveredMonsterDetailContent(
    species: MonsterSpecies,
    nameResId: Int,
    spriteResId: Int,
    descriptionResId: Int,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(nameResId)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp)
            .testTag("monster-detail-content"),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("monster-detail-name"),
        )
        MonsterSprite(
            spriteResId = spriteResId,
            contentDescription = name,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 240.dp)
                .aspectRatio(1f)
                .testTag("monster-detail-sprite-${species.name}"),
        )
        Text(
            text = stringResource(R.string.monster_compendium_discovered_complete),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(descriptionResId),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MonsterSprite(
    spriteResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val bitmap = remember(resources, spriteResId) {
        ImageBitmap.imageResource(resources, spriteResId)
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        modifier = modifier.semantics {
            this[MonsterSpriteContentScaleKey] = MonsterSpriteContentScale.FIT
            this[MonsterSpriteFilterQualityKey] = MonsterSpriteFilterQuality.NONE
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompendiumTopAppBar(
    titleResId: Int,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(titleResId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("monster-compendium-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.monster_compendium_back),
                )
            }
        },
    )
}

@Composable
private fun CompendiumMessage(
    messageResId: Int,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(
                text = stringResource(messageResId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.monster_compendium_retry))
                }
            }
        }
    }
}

private fun MonsterCompendiumFilter.labelResId(): Int = when (this) {
    MonsterCompendiumFilter.ALL -> R.string.monster_compendium_filter_all
    MonsterCompendiumFilter.DISCOVERED -> R.string.monster_compendium_filter_discovered
    MonsterCompendiumFilter.UNDISCOVERED -> R.string.monster_compendium_filter_undiscovered
}

@Preview(name = "몬스터 도감", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
private fun MonsterCompendiumPreview() {
    TodoQuestTheme {
        MonsterCompendiumScreen(
            state = previewCompendiumState(),
            effects = emptyFlow(),
            onBack = {},
            onEvent = {},
        )
    }
}

@Preview(
    name = "몬스터 도감 320dp 큰 글꼴",
    widthDp = 320,
    heightDp = 640,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun MonsterCompendiumCompactPreview() {
    TodoQuestTheme {
        MonsterCompendiumScreen(
            state = previewCompendiumState(),
            effects = emptyFlow(),
            onBack = {},
            onEvent = {},
        )
    }
}

@Preview(name = "몬스터 도감 검색 결과 없음", widthDp = 412, heightDp = 760)
@Composable
private fun MonsterCompendiumEmptyResultsPreview() {
    val selected = MonsterCompendiumCatalog.discoveredEntry(MonsterSpecies.GOBLIN_SCOUT)
    TodoQuestTheme {
        MonsterCompendiumScreen(
            state = MonsterCompendiumUiState.Content(
                visibleEntries = emptyList(),
                discoveredCount = 2,
                totalCount = 5,
                collectionProgress = 0.4f,
                collectionPercent = 40,
                searchQuery = "슬라임",
                isSearchActive = true,
                selectedFilter = MonsterCompendiumFilter.UNDISCOVERED,
                selectedMonster = selected,
                detailMonster = null,
                hasActiveCriteria = true,
            ),
            effects = emptyFlow(),
            onBack = {},
            onEvent = {},
        )
    }
}

private fun previewCompendiumState(): MonsterCompendiumUiState.Content {
    val entries = MonsterCompendiumCatalog.entries(
        setOf(MonsterSpecies.GOBLIN_SCOUT, MonsterSpecies.SLIME),
    )
    return MonsterCompendiumUiState.Content(
        visibleEntries = entries,
        discoveredCount = 2,
        totalCount = 5,
        collectionProgress = 0.4f,
        collectionPercent = 40,
        searchQuery = "",
        isSearchActive = false,
        selectedFilter = MonsterCompendiumFilter.ALL,
        selectedMonster = entries.filterIsInstance<MonsterCompendiumEntryUiModel.Discovered>()
            .first(),
        detailMonster = null,
        hasActiveCriteria = false,
    )
}
