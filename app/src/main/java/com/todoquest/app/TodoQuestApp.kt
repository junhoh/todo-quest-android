package com.todoquest.app

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.todoquest.R
import com.todoquest.ReminderNavigationEvent
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.NoOpBattleSfxPlayer
import com.todoquest.core.AppClock
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.AllocateStatPointsUseCase
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.EquipOwnedEquipmentUseCase
import com.todoquest.domain.usecase.PurchaseEquipmentUseCase
import com.todoquest.domain.usecase.ResetStatsUseCase
import com.todoquest.domain.usecase.UndoCompleteOccurrenceUseCase
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import com.todoquest.domain.usecase.UnequipEquipmentUseCase
import com.todoquest.feature.calendar.CalendarScreen
import com.todoquest.feature.calendar.CalendarViewModel
import com.todoquest.feature.character.CharacterScreen
import com.todoquest.feature.character.CharacterViewModel
import com.todoquest.feature.compendium.CompendiumScreen
import com.todoquest.feature.compendium.MonsterNameResolver
import com.todoquest.feature.compendium.MonsterCompendiumScreen
import com.todoquest.feature.compendium.MonsterCompendiumViewModel
import com.todoquest.feature.compendium.MonsterDetailEvent
import com.todoquest.feature.compendium.MonsterDetailScreen
import com.todoquest.feature.compendium.MonsterDetailViewModel
import com.todoquest.feature.shop.InventoryScreen
import com.todoquest.feature.shop.InventoryViewModel
import com.todoquest.feature.shop.ShopScreen
import com.todoquest.feature.shop.ShopViewModel
import com.todoquest.feature.settings.SettingsScreen
import com.todoquest.feature.settings.SettingsViewModel
import com.todoquest.notification.AndroidReminderCapabilityAdapter
import com.todoquest.ui.theme.TodoQuestTheme

sealed interface AppDestination {
    val route: String

    sealed interface TopLevel : AppDestination {
        @get:StringRes
        val labelResId: Int
        val navigationTag: String
    }

    data object Calendar : TopLevel {
        override val route = "calendar"
        override val labelResId = R.string.navigation_calendar
        override val navigationTag = "bottom-navigation-calendar"
    }

    data object Character : TopLevel {
        override val route = "character"
        override val labelResId = R.string.navigation_character
        override val navigationTag = "bottom-navigation-character"
    }

    data object Shop : TopLevel {
        override val route = "shop"
        override val labelResId = R.string.navigation_shop
        override val navigationTag = "bottom-navigation-shop"
    }

    data object Compendium : TopLevel {
        override val route = "compendium"
        override val labelResId = R.string.navigation_compendium
        override val navigationTag = "bottom-navigation-compendium"
    }

    data object Settings : TopLevel {
        override val route = "settings"
        override val labelResId = R.string.navigation_settings
        override val navigationTag = "bottom-navigation-settings"
    }

    data object Inventory : AppDestination {
        override val route = "inventory"
    }

    data object MonsterCompendium : AppDestination {
        override val route = "compendium/monsters"
    }

    data object MonsterDetail : AppDestination {
        const val SPECIES_ARGUMENT = "species"
        override val route = "compendium/monsters/{$SPECIES_ARGUMENT}"

        fun routeFor(species: MonsterSpecies): String =
            "compendium/monsters/${species.name}"

        fun parseSpecies(argument: String?): MonsterSpecies? =
            MonsterSpecies.entries.firstOrNull { it.name == argument }
    }
}

@Composable
fun TodoQuestApp(
    reminderNavigationEvent: ReminderNavigationEvent? = null,
    onReminderNavigationConsumed: (Long) -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as TodoQuestContainerOwner)
        .todoQuestContainer

    TodoQuestTheme {
        AppNavigation(
            taskRepository = container.taskRepository,
            characterRepository = container.characterRepository,
            combatRepository = container.combatRepository,
            statusEffectRepository = container.statusEffectRepository,
            equipmentRepository = container.equipmentRepository,
            battleSfxSettingsRepository = container.battleSfxSettingsRepository,
            battleSfxPlayer = container.battleSfxPlayer,
            reminderScheduler = container.reminderScheduler,
            completeOccurrence = container.completeOccurrenceUseCase,
            undoCompleteOccurrence = container.undoCompleteOccurrenceUseCase,
            failOccurrence = container.failOccurrenceUseCase,
            undoFailOccurrence = container.undoFailOccurrenceUseCase,
            createTask = container.createTaskUseCase::invoke,
            updateTask = container.updateTaskUseCase::invoke,
            deleteTask = container.deleteTaskUseCase::invoke,
            loadReminderStatus = { taskId ->
                container.reminderRepository.getScheduleState(taskId)?.status
            },
            reconcileTaskReminder = container.reconcileTaskReminderUseCase::invoke,
            prepareFirstLaunchNotificationPrompt =
                container.prepareFirstLaunchNotificationPromptUseCase::invoke,
            prepareCharacterStatGuide = container.prepareCharacterStatGuideUseCase::invoke,
            acknowledgeCharacterStatGuide = container.acknowledgeCharacterStatGuideUseCase::invoke,
            reminderCapabilityAdapter = requireNotNull(container.reminderCapabilityAdapter),
            purchaseEquipment = container.purchaseEquipmentUseCase,
            equipOwnedEquipment = container.equipOwnedEquipmentUseCase,
            unequipEquipment = container.unequipEquipmentUseCase,
            clock = container.clock,
            reminderNavigationEvent = reminderNavigationEvent,
            onReminderNavigationConsumed = onReminderNavigationConsumed,
        )
    }
}

@Composable
private fun AppNavigation(
    taskRepository: TaskRepository,
    characterRepository: CharacterRepository,
    combatRepository: CombatRepository,
    statusEffectRepository: StatusEffectRepository,
    equipmentRepository: EquipmentRepository,
    battleSfxSettingsRepository: BattleSfxSettingsRepository,
    battleSfxPlayer: BattleSfxPlayer,
    reminderScheduler: ReminderScheduler,
    completeOccurrence: CompleteOccurrenceUseCase,
    undoCompleteOccurrence: UndoCompleteOccurrenceUseCase,
    failOccurrence: FailOccurrenceUseCase,
    undoFailOccurrence: UndoFailOccurrenceUseCase,
    createTask: suspend (com.todoquest.domain.model.CreateTaskInput) ->
        com.todoquest.domain.model.TaskMutationResult,
    updateTask: suspend (com.todoquest.domain.model.UpdateTaskInput) ->
        com.todoquest.domain.model.TaskMutationResult,
    deleteTask: suspend (Long, java.time.LocalDate) -> Unit,
    loadReminderStatus: suspend (Long) -> com.todoquest.domain.model.ReminderScheduleStatus?,
    reconcileTaskReminder: suspend (Long) -> com.todoquest.domain.model.ReminderScheduleStatus,
    prepareFirstLaunchNotificationPrompt: suspend () -> Boolean,
    prepareCharacterStatGuide: () -> Boolean,
    acknowledgeCharacterStatGuide: () -> Boolean,
    reminderCapabilityAdapter: AndroidReminderCapabilityAdapter,
    purchaseEquipment: PurchaseEquipmentUseCase,
    equipOwnedEquipment: EquipOwnedEquipmentUseCase,
    unequipEquipment: UnequipEquipmentUseCase,
    clock: AppClock,
    reminderNavigationEvent: ReminderNavigationEvent?,
    onReminderNavigationConsumed: (Long) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val selectedTopLevelDestination = topLevelDestinationForRoute(currentRoute)

    LaunchedEffect(reminderNavigationEvent?.id) {
        if (reminderNavigationEvent != null) {
            navController.navigateToTopLevel(AppDestination.Calendar)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app-root"),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom-navigation"),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            ) {
                topLevelDestinations.forEach { destination ->
                    val label = stringResource(destination.labelResId)
                    NavigationBarItem(
                        selected = selectedTopLevelDestination == destination,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AppDestination.Calendar -> Icons.Default.CalendarMonth
                                    AppDestination.Character -> Icons.Default.Person
                                    AppDestination.Shop -> Icons.Default.Storefront
                                    AppDestination.Compendium -> Icons.AutoMirrored.Filled.MenuBook
                                    AppDestination.Settings -> Icons.Default.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(destination.navigationTag)
                            .semantics { contentDescription = label },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Calendar.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AppDestination.Calendar.route) {
                val calendarViewModel: CalendarViewModel = viewModel(
                    factory = calendarViewModelFactory(
                        taskRepository = taskRepository,
                        characterRepository = characterRepository,
                        combatRepository = combatRepository,
                        statusEffectRepository = statusEffectRepository,
                        completeOccurrence = completeOccurrence,
                        undoCompleteOccurrence = undoCompleteOccurrence,
                        failOccurrence = failOccurrence,
                        undoFailOccurrence = undoFailOccurrence,
                        createTask = createTask,
                        updateTask = updateTask,
                        deleteTask = deleteTask,
                        loadReminderStatus = loadReminderStatus,
                        reconcileTaskReminder = reconcileTaskReminder,
                        prepareFirstLaunchNotificationPrompt =
                            prepareFirstLaunchNotificationPrompt,
                        clock = clock,
                        battleSfxPlayer = battleSfxPlayer,
                    ),
                )
                LaunchedEffect(calendarViewModel, reminderNavigationEvent?.id) {
                    reminderNavigationEvent?.let { event ->
                        calendarViewModel.openReminderDestination(
                            taskId = event.key?.taskId,
                            occurrenceDate = event.key?.occurrenceDate,
                            onHandled = { onReminderNavigationConsumed(event.id) },
                        )
                    }
                }
                CalendarScreen(
                    viewModel = calendarViewModel,
                    reminderCapabilityAdapter = reminderCapabilityAdapter,
                )
            }
            composable(AppDestination.Character.route) {
                val characterViewModel: CharacterViewModel = viewModel(
                    factory = characterViewModelFactory(
                        characterRepository = characterRepository,
                        statusEffectRepository = statusEffectRepository,
                        clock = clock,
                        prepareCharacterStatGuide = prepareCharacterStatGuide,
                        acknowledgeCharacterStatGuide = acknowledgeCharacterStatGuide,
                    ),
                )
                CharacterScreen(viewModel = characterViewModel)
            }
            composable(AppDestination.Shop.route) {
                val shopViewModel: ShopViewModel = viewModel(
                    factory = shopViewModelFactory(
                        equipmentRepository = equipmentRepository,
                        purchaseEquipment = purchaseEquipment,
                        equipOwnedEquipment = equipOwnedEquipment,
                        unequipEquipment = unequipEquipment,
                    ),
                )
                ShopScreen(
                    viewModel = shopViewModel,
                    onOpenInventory = {
                        navController.navigate(AppDestination.Inventory.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestination.Inventory.route) {
                val inventoryViewModel: InventoryViewModel = viewModel(
                    factory = inventoryViewModelFactory(
                        equipmentRepository = equipmentRepository,
                        equipOwnedEquipment = equipOwnedEquipment,
                        unequipEquipment = unequipEquipment,
                    ),
                )
                InventoryScreen(
                    viewModel = inventoryViewModel,
                    onBack = {
                        if (!navController.navigateUp()) {
                            navController.navigate(AppDestination.Shop.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(AppDestination.Compendium.route) {
                CompendiumScreen(
                    onOpenMonsters = {
                        navController.navigate(AppDestination.MonsterCompendium.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestination.MonsterCompendium.route) {
                val applicationResources = LocalContext.current.applicationContext.resources
                val compendiumViewModel: MonsterCompendiumViewModel = viewModel(
                    factory = monsterCompendiumViewModelFactory(
                        combatRepository = combatRepository,
                        applicationResources = applicationResources,
                    ),
                )
                val state by compendiumViewModel.uiState.collectAsState()
                MonsterCompendiumScreen(
                    state = state,
                    effects = compendiumViewModel.effects,
                    onBack = {
                        if (!navController.navigateUp()) {
                            navController.navigateToTopLevel(AppDestination.Compendium)
                        }
                    },
                    onEvent = compendiumViewModel::onEvent,
                )
            }
            composable(
                route = AppDestination.MonsterDetail.route,
                arguments = listOf(
                    navArgument(AppDestination.MonsterDetail.SPECIES_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val species = AppDestination.MonsterDetail.parseSpecies(
                    backStackEntry.arguments?.getString(
                        AppDestination.MonsterDetail.SPECIES_ARGUMENT,
                    ),
                )
                if (species == null) {
                    LaunchedEffect(backStackEntry) {
                        navController.navigate(AppDestination.MonsterCompendium.route) {
                            popUpTo(AppDestination.Compendium.route)
                            launchSingleTop = true
                        }
                    }
                } else {
                    val detailViewModel: MonsterDetailViewModel = viewModel(
                        factory = monsterDetailViewModelFactory(
                            combatRepository = combatRepository,
                            species = species,
                        ),
                    )
                    val state by detailViewModel.uiState.collectAsState()
                    MonsterDetailScreen(
                        state = state,
                        onBack = {
                            if (!navController.navigateUp()) {
                                navController.navigate(AppDestination.MonsterCompendium.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onRetry = {
                            detailViewModel.onEvent(MonsterDetailEvent.Retry)
                        },
                    )
                }
            }
            composable(AppDestination.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = settingsViewModelFactory(
                        repository = battleSfxSettingsRepository,
                        reminderScheduler = reminderScheduler,
                    ),
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    reminderCapabilityAdapter = reminderCapabilityAdapter,
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: AppDestination.TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun topLevelDestinationForRoute(route: String?): AppDestination.TopLevel? = when (route) {
    AppDestination.Calendar.route -> AppDestination.Calendar
    AppDestination.Character.route -> AppDestination.Character
    AppDestination.Shop.route,
    AppDestination.Inventory.route,
    -> AppDestination.Shop

    AppDestination.Compendium.route,
    AppDestination.MonsterCompendium.route,
    AppDestination.MonsterDetail.route,
    -> AppDestination.Compendium

    AppDestination.Settings.route -> AppDestination.Settings

    else -> null
}

private fun calendarViewModelFactory(
    taskRepository: TaskRepository,
    characterRepository: CharacterRepository,
    combatRepository: CombatRepository,
    statusEffectRepository: StatusEffectRepository,
    completeOccurrence: CompleteOccurrenceUseCase,
    undoCompleteOccurrence: UndoCompleteOccurrenceUseCase,
    failOccurrence: FailOccurrenceUseCase,
    undoFailOccurrence: UndoFailOccurrenceUseCase,
    createTask: suspend (com.todoquest.domain.model.CreateTaskInput) ->
        com.todoquest.domain.model.TaskMutationResult,
    updateTask: suspend (com.todoquest.domain.model.UpdateTaskInput) ->
        com.todoquest.domain.model.TaskMutationResult,
    deleteTask: suspend (Long, java.time.LocalDate) -> Unit,
    loadReminderStatus: suspend (Long) -> com.todoquest.domain.model.ReminderScheduleStatus?,
    reconcileTaskReminder: suspend (Long) -> com.todoquest.domain.model.ReminderScheduleStatus,
    prepareFirstLaunchNotificationPrompt: suspend () -> Boolean,
    clock: AppClock,
    battleSfxPlayer: BattleSfxPlayer = NoOpBattleSfxPlayer,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            return CalendarViewModel(
                repository = taskRepository,
                characterRepository = characterRepository,
                combatRepository = combatRepository,
                statusEffectRepository = statusEffectRepository,
                completeOccurrence = completeOccurrence,
                undoCompleteOccurrence = undoCompleteOccurrence,
                failOccurrence = failOccurrence,
                undoFailOccurrence = undoFailOccurrence,
                clock = clock,
                createTaskUseCase = createTask,
                updateTaskUseCase = updateTask,
                deleteTaskUseCase = deleteTask,
                loadReminderStatus = loadReminderStatus,
                reconcileTaskReminderUseCase = reconcileTaskReminder,
                prepareFirstLaunchNotificationPrompt = prepareFirstLaunchNotificationPrompt,
                battleSfxPlayer = battleSfxPlayer,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun characterViewModelFactory(
    characterRepository: CharacterRepository,
    statusEffectRepository: StatusEffectRepository,
    clock: AppClock,
    prepareCharacterStatGuide: () -> Boolean,
    acknowledgeCharacterStatGuide: () -> Boolean,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CharacterViewModel::class.java)) {
            return CharacterViewModel(
                repository = characterRepository,
                statusEffectRepository = statusEffectRepository,
                allocateStatPoints = AllocateStatPointsUseCase(characterRepository),
                resetStats = ResetStatsUseCase(characterRepository),
                clock = clock,
                prepareCharacterStatGuide = prepareCharacterStatGuide,
                acknowledgeCharacterStatGuide = acknowledgeCharacterStatGuide,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun shopViewModelFactory(
    equipmentRepository: EquipmentRepository,
    purchaseEquipment: PurchaseEquipmentUseCase,
    equipOwnedEquipment: EquipOwnedEquipmentUseCase,
    unequipEquipment: UnequipEquipmentUseCase,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShopViewModel::class.java)) {
            return ShopViewModel(
                repository = equipmentRepository,
                purchaseEquipment = purchaseEquipment,
                equipOwnedEquipment = equipOwnedEquipment,
                unequipEquipment = unequipEquipment,
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun inventoryViewModelFactory(
    equipmentRepository: EquipmentRepository,
    equipOwnedEquipment: EquipOwnedEquipmentUseCase,
    unequipEquipment: UnequipEquipmentUseCase,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            return InventoryViewModel(
                repository = equipmentRepository,
                equipOwnedEquipment = equipOwnedEquipment,
                unequipEquipment = unequipEquipment,
                characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun monsterCompendiumViewModelFactory(
    combatRepository: CombatRepository,
    applicationResources: Resources,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MonsterCompendiumViewModel::class.java)) {
            return MonsterCompendiumViewModel(
                combatRepository = combatRepository,
                nameResolver = MonsterNameResolver { nameResId ->
                    applicationResources.getString(nameResId)
                },
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun monsterDetailViewModelFactory(
    combatRepository: CombatRepository,
    species: MonsterSpecies,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MonsterDetailViewModel::class.java)) {
            return MonsterDetailViewModel(
                combatRepository = combatRepository,
                species = species,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun settingsViewModelFactory(
    repository: BattleSfxSettingsRepository,
    reminderScheduler: ReminderScheduler,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                repository = repository,
                reminderScheduler = reminderScheduler,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private val topLevelDestinations = listOf(
    AppDestination.Calendar,
    AppDestination.Character,
    AppDestination.Shop,
    AppDestination.Compendium,
    AppDestination.Settings,
)
