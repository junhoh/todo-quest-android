package com.todoquest.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.room.Room
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todoquest.MainActivity
import com.todoquest.R
import com.todoquest.audio.ConfiguredBattleSfxPlayer
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CharacterStatGuideStatus
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.EquipmentUnequipAppearancePolicy
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore
import com.todoquest.domain.repository.CharacterGuideRepository
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.usecase.PrepareFirstLaunchNotificationPromptUseCase
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.notification.ReminderAlarmIntents
import com.todoquest.notification.SharedPreferencesFirstLaunchNotificationPromptStore
import com.todoquest.feature.compendium.MonsterDetailUiState
import com.todoquest.feature.compendium.MonsterDetailViewModel
import com.todoquest.ui.character.CharacterBitmapComposer
import com.todoquest.ui.character.CharacterRenderState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun eligibleCharacterGuideAppearsOnFirstEntryAndDoesNotReplayAfterAcknowledgement() {
        val productionContainer = appContainer()
        val guideRepository = EligibleCharacterGuideRepository()
        val testContainer = TodoQuestAppContainer(
            database = productionContainer.database,
            clock = productionContainer.clock,
            taskRepository = productionContainer.taskRepository,
            characterRepository = productionContainer.characterRepository,
            combatRepository = productionContainer.combatRepository,
            equipmentRepository = productionContainer.equipmentRepository,
            statusEffectRepository = productionContainer.statusEffectRepository,
            characterGuideRepository = guideRepository,
            reminderRepository = productionContainer.reminderRepository,
            reminderScheduler = productionContainer.reminderScheduler,
            reminderPublisher = productionContainer.reminderPublisher,
            reminderCapabilityAdapter = productionContainer.reminderCapabilityAdapter,
            firstLaunchNotificationPromptStore =
                productionContainer.firstLaunchNotificationPromptStore,
            prepareFirstLaunchNotificationPromptUseCase =
                productionContainer.prepareFirstLaunchNotificationPromptUseCase,
        )
        installContainerAndCreateNewViewModels(testContainer)
        try {
            composeRule.onNodeWithTag("bottom-navigation-character").performClick()
            composeRule.onNodeWithTag("character-stat-guide-dialog").assertIsDisplayed()
            composeRule.onNodeWithText("닫기").performClick()
            assertEquals(1, guideRepository.acknowledgeCalls)

            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            composeRule.onNodeWithTag("bottom-navigation-character").performClick()

            composeRule.onNodeWithTag("bottom-navigation-character").assertIsSelected()
            composeRule.onNodeWithTag("character-stat-guide-dialog").assertDoesNotExist()
            assertEquals(1, guideRepository.acknowledgeCalls)
        } finally {
            installContainerAndCreateNewViewModels(
                productionContainer,
                readyTag = "character-screen-scroll",
            )
        }
    }

    @Test
    fun firstCalendarPermissionPromptIsApplicationScopedAndDoesNotReplay() {
        val productionContainer = appContainer()
        assertTrue(
            productionContainer.firstLaunchNotificationPromptStore is
                SharedPreferencesFirstLaunchNotificationPromptStore,
        )

        val store = CountingFirstLaunchNotificationPromptStore()
        val scheduler = PromptCapabilityScheduler()
        val preparePrompt = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)
        val testContainer = productionContainer.withPromptPreparation(
            store = store,
            preparePrompt = preparePrompt,
        )
        assertSame(preparePrompt, testContainer.prepareFirstLaunchNotificationPromptUseCase)

        installContainerAndCreateNewViewModels(testContainer)

        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertIsDisplayed()
        assertEquals(1, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)

        composeRule.onNodeWithText("나중에").performClick()
        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        assertEquals(1, store.consumeCalls)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("battle-map").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        assertEquals(1, store.consumeCalls)

        installContainerAndCreateNewViewModels(testContainer)

        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        assertEquals(2, store.consumeCalls)
        assertEquals(listOf(ReminderCapability.POST_NOTIFICATIONS), scheduler.capabilityCalls)
    }

    @Test
    fun promptPreparationFailuresDoNotBlockLaunchOrNavigation() {
        val productionContainer = appContainer()
        val preferenceFailureStore = ThrowingFirstLaunchNotificationPromptStore()
        val preferenceFailureUseCase = PrepareFirstLaunchNotificationPromptUseCase(
            preferenceFailureStore,
            PromptCapabilityScheduler(),
        )

        installContainerAndCreateNewViewModels(
            productionContainer.withPromptPreparation(
                store = preferenceFailureStore,
                preparePrompt = preferenceFailureUseCase,
            ),
        )

        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        assertEquals(1, preferenceFailureStore.consumeCalls)

        val capabilityFailureStore = CountingFirstLaunchNotificationPromptStore()
        val capabilityFailureScheduler = PromptCapabilityScheduler(
            failure = IllegalStateException("capability unavailable"),
        )
        installContainerAndCreateNewViewModels(
            productionContainer.withPromptPreparation(
                store = capabilityFailureStore,
                preparePrompt = PrepareFirstLaunchNotificationPromptUseCase(
                    capabilityFailureStore,
                    capabilityFailureScheduler,
                ),
            ),
        )

        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.onNodeWithTag("notification-permission-prompt-dialog").assertDoesNotExist()
        assertEquals(1, capabilityFailureStore.consumeCalls)
        assertEquals(
            listOf(ReminderCapability.POST_NOTIFICATIONS),
            capabilityFailureScheduler.capabilityCalls,
        )
    }

    @Test
    fun calendarCharacterShopCompendiumAndSettingsDestinationsNavigateInOrder() {
        assertEquals("calendar", AppDestination.Calendar.route)
        assertEquals("character", AppDestination.Character.route)
        assertEquals("shop", AppDestination.Shop.route)
        assertEquals("inventory", AppDestination.Inventory.route)
        assertEquals("compendium", AppDestination.Compendium.route)
        assertEquals("settings", AppDestination.Settings.route)
        assertEquals("compendium/monsters", AppDestination.MonsterCompendium.route)
        assertEquals(
            "compendium/monsters/{species}",
            AppDestination.MonsterDetail.route,
        )

        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        scrollCalendarTo("calendar-month-grid")
        composeRule.onNodeWithTag("calendar-month-grid").assertIsDisplayed()
        composeRule.onNodeWithText("캘린더").assertIsDisplayed()
        composeRule.onNodeWithText("캐릭터").assertIsDisplayed()
        composeRule.onNodeWithText("상점").assertIsDisplayed()
        composeRule.onNodeWithText("도감").assertIsDisplayed()
        composeRule.onNodeWithText("설정").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("캘린더").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("캐릭터").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("상점").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("도감").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("설정").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-settings").assertIsNotSelected()
        val topLevelBounds = listOf(
            "bottom-navigation-calendar",
            "bottom-navigation-character",
            "bottom-navigation-shop",
            "bottom-navigation-compendium",
            "bottom-navigation-settings",
        ).map(::boundsOf)
        assertTrue(topLevelBounds.zipWithNext().all { (left, right) -> left.right <= right.left })
        val selectedDate = alternateDateInCurrentMonth()
        scrollCalendarTo("calendar-day-$selectedDate")
        composeRule.onNodeWithTag("calendar-day-$selectedDate").performClick()
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(selectedDate)).assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()

        composeRule.onNodeWithTag("bottom-navigation-character").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsNotSelected()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("equipped-character-sprite")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("equipped-character-sprite").assertIsDisplayed()
        composeRule.onNodeWithText("레벨 1").assertIsDisplayed()
        composeRule.onNodeWithText("누적 경험치 0").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-shop").performClick()

        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsNotSelected()
        composeRule.onNodeWithTag("shop-open-inventory").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-compendium").performClick()

        composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsNotSelected()
        composeRule.onNodeWithTag("compendium-monster-category").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-settings").performClick()

        composeRule.onNodeWithTag("bottom-navigation-settings").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsNotSelected()
        composeRule.onNodeWithTag("settings-top-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-battle-sfx-row").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()

        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-character").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsNotSelected()
        composeRule.onNodeWithTag("bottom-navigation-settings").assertIsNotSelected()
        scrollCalendarTo("calendar-month-grid")
        composeRule.onNodeWithTag("calendar-month-grid").assertIsDisplayed()
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(selectedDate)).assertIsDisplayed()
    }

    @Test
    fun settingsToggleMutesOnlyNewCalendarEffectsAndRecreationDoesNotReplayAudio() {
        val application =
            composeRule.activity.application as TodoQuestInstrumentedTestApplication
        val productionContainer = appContainer()
        val isolatedDatabase = Room.inMemoryDatabaseBuilder(
            composeRule.activity,
            com.todoquest.data.local.TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        val configuredPlayer = ConfiguredBattleSfxPlayer(
            settingsRepository = application.battleSfxSettingsRepository,
            delegate = application.recordingBattleSfxPlayer,
        )
        val container = TodoQuestAppContainer(
            database = isolatedDatabase,
            clock = productionContainer.clock,
            reminderScheduler = productionContainer.reminderScheduler,
            reminderPublisher = productionContainer.reminderPublisher,
            reminderCapabilityAdapter = productionContainer.reminderCapabilityAdapter,
            firstLaunchNotificationPromptStore =
                productionContainer.firstLaunchNotificationPromptStore,
            battleSfxSettingsRepository = application.battleSfxSettingsRepository,
            battleSfxPlayer = configuredPlayer,
        )
        application.resetBattleSfxTestState()
        installContainerAndCreateNewViewModels(container)

        try {
            composeRule.onNodeWithTag("bottom-navigation-settings").performClick()
            composeRule.onNodeWithTag("settings-battle-sfx-row")
                .assertContentDescriptionEquals("효과음, 켜짐")
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !application.battleSfxSettingsRepository.isEnabled.value
            }

            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("settings-battle-sfx-row")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bottom-navigation-settings").assertIsSelected()
            composeRule.onNodeWithTag("settings-battle-sfx-row")
                .assertContentDescriptionEquals("효과음, 꺼짐")

            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            val mutedTaskId = createTask("효과음 꺼짐 전투")
            val today = container.clock.today()
            scrollCalendarTo("complete-task-$mutedTaskId-$today")
            composeRule.onNodeWithTag("complete-task-$mutedTaskId-$today").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking {
                    container.database.combatDao()
                        .getPlayerAttackEvent(mutedTaskId, today.toEpochDay())
                        ?.status == CombatEventStatus.APPLIED.name
                }
            }
            assertTrue(application.recordingBattleSfxPlayer.requests().isEmpty())
            waitForCombatEffectsToFinish()

            composeRule.onNodeWithTag("bottom-navigation-settings").performClick()
            composeRule.onNodeWithTag("settings-battle-sfx-row").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                application.battleSfxSettingsRepository.isEnabled.value
            }
            composeRule.onNodeWithTag("settings-battle-sfx-row")
                .assertContentDescriptionEquals("효과음, 켜짐")

            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            val audibleTaskId = createTask("효과음 켜짐 전투")
            scrollCalendarTo("complete-task-$audibleTaskId-$today")
            composeRule.onNodeWithTag("complete-task-$audibleTaskId-$today").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                application.recordingBattleSfxPlayer.requests().size >= 2
            }
            waitForCombatEffectsToFinish()
            val played = application.recordingBattleSfxPlayer.requests()
            assertTrue(played.all { it.eventId.isNotBlank() })
            assertTrue(played.map { it.effect }.distinct().size >= 2)

            composeRule.onNodeWithTag("bottom-navigation-character").performClick()
            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            composeRule.waitForIdle()
            assertEquals(played, application.recordingBattleSfxPlayer.requests())

            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("battle-map").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(played, application.recordingBattleSfxPlayer.requests())
        } finally {
            application.battleSfxSettingsRepository.setEnabled(true)
            application.recordingBattleSfxPlayer.clear()
            installContainerAndCreateNewViewModels(productionContainer)
            container.releaseAudio()
            isolatedDatabase.close()
        }
    }

    @Test
    fun settingsNotificationPermissionRefreshesOnResumeWithoutBlockingSfxOrNavigation() {
        val productionContainer = appContainer()
        val scheduler = SettingsCapabilityScheduler(ReminderCapabilityStatus.REQUIRED)
        val testContainer = TodoQuestAppContainer(
            database = productionContainer.database,
            clock = productionContainer.clock,
            taskRepository = productionContainer.taskRepository,
            characterRepository = productionContainer.characterRepository,
            combatRepository = productionContainer.combatRepository,
            equipmentRepository = productionContainer.equipmentRepository,
            statusEffectRepository = productionContainer.statusEffectRepository,
            characterGuideRepository = productionContainer.characterGuideRepository,
            battleSfxSettingsRepository = productionContainer.battleSfxSettingsRepository,
            battleSfxPlayer = productionContainer.battleSfxPlayer,
            reminderRepository = productionContainer.reminderRepository,
            reminderScheduler = scheduler,
            reminderPublisher = productionContainer.reminderPublisher,
            reminderCapabilityAdapter = productionContainer.reminderCapabilityAdapter,
            firstLaunchNotificationPromptStore =
                productionContainer.firstLaunchNotificationPromptStore,
            prepareFirstLaunchNotificationPromptUseCase =
                productionContainer.prepareFirstLaunchNotificationPromptUseCase,
        )
        installContainerAndCreateNewViewModels(testContainer)
        try {
            composeRule.onNodeWithTag("bottom-navigation-settings").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("권한 필요").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("settings-notification-permission-row")
                .assertContentDescriptionEquals("알림 권한, 권한 필요, 권한 허용")
                .assertIsEnabled()
            composeRule.onNodeWithTag(
                "settings-notification-permission-action",
                useUnmergedTree = true,
            ).assertIsEnabled()
            composeRule.onNodeWithTag(
                "settings-battle-sfx-switch",
                useUnmergedTree = true,
            ).assertIsEnabled()

            scheduler.enqueue(ReminderCapabilityStatus.CHANNEL_DISABLED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("알림 채널 꺼짐")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(scheduler.capabilityCalls.size >= 2)
            assertTrue(
                scheduler.capabilityCalls.all {
                    it == ReminderCapability.POST_NOTIFICATIONS
                },
            )

            composeRule.onNodeWithTag("settings-battle-sfx-row").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !productionContainer.battleSfxSettingsRepository.isEnabled.value
            }
            composeRule.onNodeWithTag("bottom-navigation-shop").performClick()
            composeRule.onNodeWithTag("shop-open-inventory").assertIsDisplayed()
            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        } finally {
            productionContainer.battleSfxSettingsRepository.setEnabled(true)
            installContainerAndCreateNewViewModels(
                productionContainer,
                readyTag = "app-root",
            )
        }
    }

    @Test
    fun compendiumSelectionSheetBackAndStateRestoreKeepCompatibilityRoutesPrivate() {
        val productionContainer = appContainer()
        val isolatedDatabase = Room.inMemoryDatabaseBuilder(
            composeRule.activity,
            com.todoquest.data.local.TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        val container = TodoQuestAppContainer(
            database = isolatedDatabase,
            clock = productionContainer.clock,
            reminderScheduler = productionContainer.reminderScheduler,
            reminderPublisher = productionContainer.reminderPublisher,
            reminderCapabilityAdapter = productionContainer.reminderCapabilityAdapter,
            firstLaunchNotificationPromptStore =
                productionContainer.firstLaunchNotificationPromptStore,
        )
        installContainerAndCreateNewViewModels(container)
        try {
            val discovered = runBlocking {
                container.combatRepository.observeDiscoveredMonsterSpecies().first()
            }
            assertEquals(setOf(MonsterSpecies.SKELETON_SOLDIER), discovered)

            val lockedRoute = AppDestination.MonsterDetail.routeFor(MonsterSpecies.GOBLIN_SCOUT)
            assertEquals("compendium/monsters/GOBLIN_SCOUT", lockedRoute)
            assertEquals(
                MonsterSpecies.GOBLIN_SCOUT,
                AppDestination.MonsterDetail.parseSpecies("GOBLIN_SCOUT"),
            )
            assertNull(AppDestination.MonsterDetail.parseSpecies("UNKNOWN_SPECIES"))
            val lockedViewModel = MonsterDetailViewModel(
                combatRepository = container.combatRepository,
                species = MonsterSpecies.GOBLIN_SCOUT,
            )
            val lockedState = runBlocking {
                lockedViewModel.uiState.first { it !is MonsterDetailUiState.Loading }
            }
            assertTrue(lockedState is MonsterDetailUiState.Locked)
            val discoveredViewModel = MonsterDetailViewModel(
                combatRepository = container.combatRepository,
                species = MonsterSpecies.SKELETON_SOLDIER,
            )
            val discoveredState = runBlocking {
                discoveredViewModel.uiState.first { it !is MonsterDetailUiState.Loading }
            }
            assertTrue(discoveredState is MonsterDetailUiState.Discovered)

            composeRule.onNodeWithTag("bottom-navigation-compendium").performClick()
            composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()
            composeRule.onNodeWithTag("compendium-monster-category").performClick()

            composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()
            composeRule.onNodeWithTag("monster-compendium-grid").assertIsDisplayed()
            composeRule.onNodeWithTag("monster-compendium-summary")
                .assertContentDescriptionEquals("발견한 몬스터 1 / 5, 수집률 20%")
            composeRule.onNodeWithTag("monster-compendium-preview")
                .assertContentDescriptionEquals("해골 병사, 발견 완료, 상세 보기")
            composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
            composeRule.onNodeWithTag("monster-compendium-entry-SKELETON_SOLDIER")
                .assertHasClickAction()
            composeRule.onNodeWithTag(
                "monster-compendium-sprite-SKELETON_SOLDIER",
                useUnmergedTree = true,
            )
                .assertExists()
            composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT")
                .assertHasClickAction()
                .assertContentDescriptionEquals("미발견 몬스터")
            composeRule.onNodeWithTag("monster-compendium-sprite-GOBLIN_SCOUT")
                .assertDoesNotExist()
            composeRule.onNodeWithText("고블린 정찰병").assertDoesNotExist()

            composeRule.onNodeWithTag("monster-compendium-entry-SKELETON_SOLDIER")
                .performClick()
            composeRule.onNodeWithTag("monster-compendium-detail-sheet").assertDoesNotExist()
            composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(2)
            composeRule.onNodeWithTag("monster-compendium-preview").performClick()
            composeRule.onNodeWithTag("monster-compendium-detail-sheet").assertIsDisplayed()
            composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()

            composeRule.activityRule.scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("monster-compendium-detail-sheet")
                    .fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("monster-compendium-grid").assertIsDisplayed()

            composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(3)
            composeRule.onNodeWithTag("monster-compendium-entry-GOBLIN_SCOUT").performClick()
            composeRule.onNodeWithText("아직 발견하지 못한 몬스터입니다").assertIsDisplayed()
            composeRule.onNodeWithTag("monster-compendium-detail-sheet").assertDoesNotExist()

            composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(1)
            composeRule.onNodeWithTag("monster-compendium-filter-DISCOVERED").performClick()
            composeRule.onNodeWithContentDescription("몬스터 검색").performClick()
            composeRule.onNodeWithTag("monster-compendium-search-input")
                .performTextInput("해골")
            composeRule.onNodeWithTag("monster-compendium-search-input").performImeAction()

            composeRule.onNodeWithTag("bottom-navigation-character").performClick()
            composeRule.onNodeWithTag("bottom-navigation-compendium").performClick()
            composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()
            composeRule.onNodeWithTag("monster-compendium-filter-DISCOVERED").assertIsSelected()
            composeRule.onNodeWithTag("monster-compendium-search-input")
                .assertTextContains("해골")
            composeRule.onNodeWithTag("monster-compendium-preview")
                .assertContentDescriptionEquals("해골 병사, 발견 완료, 상세 보기")

            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("monster-compendium-search-input")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bottom-navigation-compendium").assertIsSelected()
            composeRule.onNodeWithTag("monster-compendium-filter-DISCOVERED").assertIsSelected()
            composeRule.onNodeWithTag("monster-compendium-search-input")
                .assertTextContains("해골")

            composeRule.onNodeWithContentDescription("검색 닫기").performClick()
            composeRule.onNodeWithTag("monster-compendium-filter-ALL").performClick()
            composeRule.onNodeWithTag("monster-compendium-grid").performScrollToIndex(7)
            val lastCard = boundsOf("monster-compendium-entry-SLIME")
            val bottomNavigation = boundsOf("bottom-navigation")
            assertTrue(lastCard.bottom <= bottomNavigation.top)

            composeRule.onNodeWithTag("monster-compendium-back").performClick()
            composeRule.onNodeWithTag("compendium-monster-category").assertIsDisplayed()

            composeRule.onNodeWithTag("compendium-monster-category").performClick()
            composeRule.onNodeWithTag("monster-compendium-grid").assertIsDisplayed()
            composeRule.activityRule.scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.onNodeWithTag("compendium-monster-category").assertIsDisplayed()

            composeRule.activityRule.scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
            composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
        } finally {
            installContainerAndCreateNewViewModels(
                container = productionContainer,
                readyTag = "bottom-navigation",
            )
            isolatedDatabase.close()
        }
    }

    @Test
    fun notificationTapSelectsOccurrenceOnceAndMalformedOrDeletedTargetsOpenDefaultCalendar() {
        val container = appContainer()
        val today = container.clock.today()
        val destinationDate = alternateDateInCurrentMonth()
        val taskId = createTask("알림 목적지 일정", destinationDate)

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.runOnUiThread {
            composeRule.activity.handleReminderIntent(
                ReminderAlarmIntents.contentIntent(
                    composeRule.activity,
                    ReminderOccurrenceKey(taskId, destinationDate),
                ),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
            }.isSuccess
        }
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(destinationDate)).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("battle-map").fetchSemanticsNodes().isNotEmpty()
        }
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(destinationDate)).assertIsDisplayed()

        scrollCalendarTo("calendar-day-$today")
        composeRule.onNodeWithTag("calendar-day-$today").performClick()
        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(today)).assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.runOnUiThread {
            composeRule.activity.handleReminderIntent(
                Intent(composeRule.activity, MainActivity::class.java).apply {
                    action = ReminderAlarmIntents.ACTION_OPEN_REMINDER
                    data = Uri.parse("todoquest://calendar/999999/${Long.MAX_VALUE}")
                    putExtra(ReminderAlarmIntents.EXTRA_TASK_ID, 999_999L)
                    putExtra(ReminderAlarmIntents.EXTRA_OCCURRENCE_EPOCH_DAY, Long.MAX_VALUE)
                },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
            }.isSuccess
        }
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(today)).assertIsDisplayed()

        val deletedTaskId = createTask("삭제된 알림 일정", destinationDate)
        runBlocking { container.deleteTaskUseCase(deletedTaskId, destinationDate) }
        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.runOnUiThread {
            composeRule.activity.handleReminderIntent(
                ReminderAlarmIntents.contentIntent(
                    composeRule.activity,
                    ReminderOccurrenceKey(deletedTaskId, destinationDate),
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
            }.isSuccess
        }
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(today)).assertIsDisplayed()
    }

    @Test
    fun notificationPendingIntentOpensTheRequestedOccurrenceDate() {
        val destinationDate = alternateDateInCurrentMonth()
        val taskId = createTask("알림 PendingIntent 일정", destinationDate)

        ReminderAlarmIntents.contentPendingIntent(
            composeRule.activity,
            ReminderOccurrenceKey(taskId, destinationDate),
        ).send()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()
            }.isSuccess
        }
        scrollCalendarTo("calendar-task-header")
        composeRule.onNodeWithText(selectedDateText(destinationDate)).assertIsDisplayed()
    }

    @Test
    fun shopHasNoTopLevelBackAndInventoryBackReturnsToSelectedShopTab() {
        openShop()
        composeRule.onNodeWithTag("shop-back").assertDoesNotExist()

        composeRule.onNodeWithTag("shop-open-inventory").performClick()
        composeRule.onNodeWithTag("inventory-back").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        composeRule.onNodeWithTag("bottom-navigation-inventory").assertDoesNotExist()

        composeRule.onNodeWithTag("inventory-back").performClick()
        composeRule.onNodeWithTag("shop-open-inventory").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        composeRule.onNodeWithTag("shop-back").assertDoesNotExist()

        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.onNodeWithTag("battle-map").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-calendar").assertIsSelected()

        composeRule.onNodeWithTag("bottom-navigation-shop").performClick()
        composeRule.onNodeWithTag("shop-open-inventory").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        composeRule.onNodeWithTag("shop-back").assertDoesNotExist()
    }

    @Test
    fun shopScrollStaysBetweenFixedBarsAndBottomSystemAreaIsOpaque() {
        openShop()

        val rootBounds = boundsOf("app-root")
        val topBarBefore = boundsOf("shop-top-bar")
        val listBefore = boundsOf("shop-equipment-list")
        val bottomNavigationBefore = boundsOf("bottom-navigation")
        assertTrue(topBarBefore.bottom <= listBefore.top + POSITION_TOLERANCE_PX)
        assertTrue(listBefore.bottom <= bottomNavigationBefore.top + POSITION_TOLERANCE_PX)
        assertTrue(
            kotlin.math.abs(rootBounds.bottom - bottomNavigationBefore.bottom) <=
                POSITION_TOLERANCE_PX,
        )
        composeRule.onNodeWithTag("battle-map").assertDoesNotExist()

        val bottomNavigationBitmap = composeRule.onNodeWithTag("bottom-navigation")
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "하단 navigation과 system inset은 완전히 불투명해야 합니다.",
            bottomNavigationBitmap.hasOnlyOpaquePixels(),
        )

        val lastCardTag =
            "shop-equipment-card-${EquipmentCatalogSeeder.STEEL_MACE_ID}"
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag(lastCardTag))
        val lastCardBounds = composeRule.onNodeWithTag(lastCardTag)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val listAfter = boundsOf("shop-equipment-list")
        val topBarAfter = boundsOf("shop-top-bar")
        val bottomNavigationAfter = boundsOf("bottom-navigation")
        assertTrue(lastCardBounds.top >= listAfter.top - POSITION_TOLERANCE_PX)
        assertTrue(lastCardBounds.bottom <= listAfter.bottom + POSITION_TOLERANCE_PX)
        assertTrue(lastCardBounds.bottom <= bottomNavigationAfter.top + POSITION_TOLERANCE_PX)
        assertEquals(topBarBefore, topBarAfter)
        assertEquals(bottomNavigationBefore, bottomNavigationAfter)
    }

    @Test
    fun shopRoomFlowUpdatesPreviewPurchaseEquipReplacementsAndRecreationWithoutRefresh() {
        val container = appContainer()
        val fallbackLoadout = runBlocking {
            container.equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first()
            val characterDao = container.database.characterProfileDao()
            characterDao.upsert(
                characterDao.getProfile(CharacterMapper.DEFAULT_CHARACTER_ID)!!.copy(
                    totalXp = 500L,
                    currentGold = 10_000L,
                ),
            )
            val chest = container.purchaseEquipmentUseCase(
                CharacterMapper.DEFAULT_CHARACTER_ID,
                EquipmentCatalogSeeder.CLOTH_TOP_ID,
            ) as PurchaseEquipmentResult.Success
            val legs = container.purchaseEquipmentUseCase(
                CharacterMapper.DEFAULT_CHARACTER_ID,
                EquipmentCatalogSeeder.CLOTH_PANTS_ID,
            ) as PurchaseEquipmentResult.Success
            container.equipOwnedEquipmentUseCase(
                CharacterMapper.DEFAULT_CHARACTER_ID,
                chest.ownedEquipmentId,
                EquipmentSlot.CHEST,
            )
            container.equipOwnedEquipmentUseCase(
                CharacterMapper.DEFAULT_CHARACTER_ID,
                legs.ownedEquipmentId,
                EquipmentSlot.LEGS,
            )
            container.equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                it.equippedBySlot[EquipmentSlot.CHEST]?.ownedEquipment?.equipmentId ==
                    EquipmentCatalogSeeder.CLOTH_TOP_ID &&
                    it.equippedBySlot[EquipmentSlot.LEGS]?.ownedEquipment?.equipmentId ==
                    EquipmentCatalogSeeder.CLOTH_PANTS_ID
            }.renderedEquippedItems
        }

        openShop()

        composeRule.onNodeWithTag("shop-top-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        listOf(
            "shop-character-preview",
            "shop-character-stat-summary",
            "shop-category-row",
            "shop-equipment-card-${EquipmentCatalogSeeder.WORN_SWORD_ID}",
        ).forEach { tag ->
            composeRule.onNodeWithTag("shop-equipment-list")
                .performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-1"))
        composeRule.onNodeWithTag("shop-category-1").performClick()
        purchaseEquipment(EquipmentCatalogSeeder.WORN_SWORD_ID)
        composeRule.onNodeWithText("계속 쇼핑").performClick()
        waitForStoreCard(EquipmentCatalogSeeder.WORN_SWORD_ID, "보유 중")
        composeRule.onNodeWithTag("shop-gold-summary")
            .assertContentDescriptionEquals("보유 골드 9,936")
        composeRule.onNodeWithTag(
            "shop-equipment-detail-action-${EquipmentCatalogSeeder.WORN_SWORD_ID}",
        )
            .performClick()
        composeRule.onNodeWithTag("shop-equipment-detail-scroll")
            .performScrollToNode(hasTestTag("shop-detail-purchase"))
        composeRule.onNodeWithTag("shop-detail-purchase")
            .assertTextEquals("장착")
            .assertIsEnabled()
        dismissEquipmentDetail()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-3"))
        composeRule.onNodeWithTag("shop-category-3").performClick()
        purchaseEquipment(EquipmentCatalogSeeder.LEATHER_ARMOR_ID)
        equipPurchasedEquipment()
        val chestSnapshot = runBlocking {
            container.equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                it.equippedBySlot[EquipmentSlot.CHEST]?.ownedEquipment?.equipmentId ==
                    EquipmentCatalogSeeder.LEATHER_ARMOR_ID
            }
        }
        assertEquals(
            EquipmentCatalogSeeder.CLOTH_PANTS_ID,
            chestSnapshot.equippedBySlot[EquipmentSlot.LEGS]?.ownedEquipment?.equipmentId,
        )
        assertEquals(
            fallbackLoadout.copy(topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR),
            chestSnapshot.renderedEquippedItems,
        )
        assertShopPreview(
            chestName = "가죽 갑옷",
            legsName = "천 바지",
            attack = chestSnapshot.derivedStats.attack,
            maxHp = chestSnapshot.derivedStats.maxHp,
            defense = chestSnapshot.derivedStats.defense,
        )

        composeRule.onNodeWithTag("shop-equipment-list").performScrollToNode(
            hasTestTag("shop-equipment-card-${EquipmentCatalogSeeder.IRON_BREASTPLATE_ID}"),
        )
        composeRule.onNodeWithTag(
            "shop-equipment-detail-action-${EquipmentCatalogSeeder.IRON_BREASTPLATE_ID}",
        ).performClick()
        composeRule.onNodeWithText("동일 부위 현재 장비").assertIsDisplayed()
        composeRule.onNodeWithText("현재 3").assertExists()
        composeRule.onNodeWithText("선택 9").assertExists()
        composeRule.onNodeWithText("+6").assertExists()
        dismissEquipmentDetail()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-4"))
        composeRule.onNodeWithTag("shop-category-4").performClick()
        purchaseEquipment(EquipmentCatalogSeeder.LEATHER_PANTS_ID)
        equipPurchasedEquipment()
        val finalSnapshot = runBlocking {
            container.equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first {
                it.equippedBySlot[EquipmentSlot.LEGS]?.ownedEquipment?.equipmentId ==
                    EquipmentCatalogSeeder.LEATHER_PANTS_ID
            }
        }
        assertEquals(
            EquipmentCatalogSeeder.LEATHER_ARMOR_ID,
            finalSnapshot.equippedBySlot[EquipmentSlot.CHEST]?.ownedEquipment?.equipmentId,
        )
        assertEquals(
            fallbackLoadout.copy(
                topId = CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
                bottomId = CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            ),
            finalSnapshot.renderedEquippedItems,
        )
        assertShopPreview(
            chestName = "가죽 갑옷",
            legsName = "가죽 바지",
            attack = finalSnapshot.derivedStats.attack,
            maxHp = finalSnapshot.derivedStats.maxHp,
            defense = finalSnapshot.derivedStats.defense,
        )

        val profileBeforeRecreation = runBlocking {
            container.database.characterProfileDao()
                .getProfile(CharacterMapper.DEFAULT_CHARACTER_ID)!!
        }
        val ownedBeforeRecreation = runBlocking {
            container.database.equipmentDao()
                .getOwnedEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
        }
        val equippedBeforeRecreation = runBlocking {
            container.database.equipmentDao()
                .getCharacterEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("shop-character-preview")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("bottom-navigation-shop").assertIsSelected()
        composeRule.onNodeWithTag("shop-purchase-confirmation").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-purchase-success").assertDoesNotExist()
        composeRule.onNodeWithText("장착 완료").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-gold-summary")
            .assertContentDescriptionEquals("보유 골드 9,686")
        assertShopPreview(
            chestName = "가죽 갑옷",
            legsName = "가죽 바지",
            attack = finalSnapshot.derivedStats.attack,
            maxHp = finalSnapshot.derivedStats.maxHp,
            defense = finalSnapshot.derivedStats.defense,
        )
        assertEquals(
            profileBeforeRecreation,
            runBlocking {
                container.database.characterProfileDao()
                    .getProfile(CharacterMapper.DEFAULT_CHARACTER_ID)
            },
        )
        assertEquals(
            ownedBeforeRecreation,
            runBlocking {
                container.database.equipmentDao()
                    .getOwnedEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
            },
        )
        assertEquals(
            equippedBeforeRecreation,
            runBlocking {
                container.database.equipmentDao()
                    .getCharacterEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
            },
        )
    }

    @Test
    fun purchaseDestinationEquipAndRecreationRestoreRoomWithoutReplayingPurchase() {
        val container = appContainer()
        runBlocking {
            container.equipmentRepository.observeStore(CharacterMapper.DEFAULT_CHARACTER_ID).first()
            assertEquals(
                1,
                container.database.characterProfileDao().updateCurrentGold(
                    CharacterMapper.DEFAULT_CHARACTER_ID,
                    200L,
                ),
            )
        }
        openShop()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("200").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-3"))
        composeRule.onNodeWithTag("shop-category-3").performClick()
        purchaseEquipment(EquipmentCatalogSeeder.CLOTH_TOP_ID)
        composeRule.onNodeWithText("계속 쇼핑").performClick()
        composeRule.onNodeWithTag("shop-purchase-success").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-equipment-detail").assertDoesNotExist()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-3"))
        composeRule.onNodeWithTag("shop-category-3").assertIsSelected()

        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-category-4"))
        composeRule.onNodeWithTag("shop-category-4").performClick()
        purchaseEquipment(EquipmentCatalogSeeder.CLOTH_PANTS_ID)
        composeRule.onNodeWithText("인벤토리로 이동").performClick()
        composeRule.onNodeWithTag("inventory-back").assertIsDisplayed()

        val ownedBeforeRecreation = runBlocking {
            container.database.equipmentDao()
                .getOwnedEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
        }
        assertEquals(2, ownedBeforeRecreation.size)
        assertEquals(
            156L,
            runBlocking {
                container.database.characterProfileDao()
                    .getProfile(CharacterMapper.DEFAULT_CHARACTER_ID)!!
                    .currentGold
            },
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inventory-back").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-purchase-success").assertDoesNotExist()
        assertEquals(
            ownedBeforeRecreation,
            runBlocking {
                container.database.equipmentDao()
                    .getOwnedEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
            },
        )

        val chestOwnedId = ownedBeforeRecreation.single {
            it.equipmentId == EquipmentCatalogSeeder.CLOTH_TOP_ID
        }.id
        val legsOwnedId = ownedBeforeRecreation.single {
            it.equipmentId == EquipmentCatalogSeeder.CLOTH_PANTS_ID
        }.id
        equipInventoryItem(chestOwnedId)
        equipInventoryItem(legsOwnedId)

        val character = runBlocking {
            container.characterRepository.observeCharacter(container.clock.today()).first {
                it.derivedStats.maxHp == 122 && it.derivedStats.hpRecovery == 8
            }
        }
        assertEquals(122, character.derivedStats.maxHp)
        assertEquals(8, character.derivedStats.hpRecovery)
        val equipped = runBlocking {
            container.database.equipmentDao()
                .getCharacterEquipment(CharacterMapper.DEFAULT_CHARACTER_ID)
        }.associateBy { EquipmentSlot.valueOf(it.slot) }
        assertEquals(chestOwnedId, equipped[EquipmentSlot.CHEST]?.ownedEquipmentId)
        assertEquals(legsOwnedId, equipped[EquipmentSlot.LEGS]?.ownedEquipmentId)

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("122 / 122").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("122 / 122").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        val playerHealthDescription = composeRule.activity.getString(
            R.string.battle_unit_health_description,
            composeRule.activity.getString(R.string.battle_player_name),
            122,
            122,
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(playerHealthDescription)
                    .assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription(playerHealthDescription).assertIsDisplayed()
    }

    @Test
    fun applicationScopedUnequipUpdatesEverySharedRendererAndPreservesCombatHistory() {
        val productionContainer = appContainer()
        val isolatedDatabase = Room.inMemoryDatabaseBuilder(
            composeRule.activity,
            com.todoquest.data.local.TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        val container = TodoQuestAppContainer(
            database = isolatedDatabase,
            clock = productionContainer.clock,
            reminderScheduler = productionContainer.reminderScheduler,
            reminderPublisher = productionContainer.reminderPublisher,
            reminderCapabilityAdapter = productionContainer.reminderCapabilityAdapter,
            firstLaunchNotificationPromptStore =
                productionContainer.firstLaunchNotificationPromptStore,
        )
        installContainerAndCreateNewViewModels(container)
        try {
        val characterId = CharacterMapper.DEFAULT_CHARACTER_ID
        val today = container.clock.today()
        val setup = runBlocking {
            container.equipmentRepository.observeStore(characterId).first()
            val characterDao = container.database.characterProfileDao()
            characterDao.upsert(
                characterDao.getProfile(characterId)!!.copy(
                    totalXp = 100_000L,
                    currentGold = 10_000L,
                    unspentStatPoints = 98,
                ),
            )
            val healthyStore = container.equipmentRepository.observeStore(characterId).first {
                it.characterLevel == 50
            }
            val nowEpochMillis = container.clock.now().toEpochMilli()
            container.database.statusEffectDao().upsertStatusEffect(
                CharacterStatusEffectEntity(
                    characterId = characterId,
                    effectType = StatusEffectType.SEVERE_INJURY.name,
                    definitionVersion = 1,
                    appliedAtEpochMillis = nowEpochMillis,
                    expiresAtEpochMillis = nowEpochMillis + 24L * 60L * 60L * 1_000L,
                    remainingRecoveryCompletions = 3,
                    active = true,
                    revision = 1L,
                    lastMutationId = "test:shared-unequip:injury",
                ),
            )
            val injuredBaseline = container.equipmentRepository.observeStore(characterId).first {
                it.derivedStats.attack < healthyStore.derivedStats.attack
            }
            val purchases = linkedMapOf(
                EquipmentSlot.HELMET to EquipmentCatalogSeeder.LEATHER_HAT_ID,
                EquipmentSlot.CHEST to EquipmentCatalogSeeder.CLOTH_TOP_ID,
                EquipmentSlot.LEGS to EquipmentCatalogSeeder.CLOTH_PANTS_ID,
                EquipmentSlot.SHOES to EquipmentCatalogSeeder.TRAVELERS_BOOTS_ID,
                EquipmentSlot.WEAPON to EquipmentCatalogSeeder.WORN_SWORD_ID,
            ).mapValues { (_, equipmentId) ->
                container.purchaseEquipmentUseCase(characterId, equipmentId)
                    as PurchaseEquipmentResult.Success
            }
            purchases.forEach { (slot, purchase) ->
                container.equipOwnedEquipmentUseCase(
                    characterId,
                    purchase.ownedEquipmentId,
                    slot,
                )
            }
            val equipped = container.equipmentRepository.observeStore(characterId).first {
                it.equippedBySlot.keys.containsAll(purchases.keys)
            }
            val historicalTaskId = container.taskRepository.createTask(
                CreateTaskInput(
                    title = "해제 전 전투 snapshot",
                    memo = "",
                    startDate = today,
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                ),
            )
            container.taskRepository.completeOccurrence(historicalTaskId, today)
            container.combatRepository.processPlayerAttack(historicalTaskId, today)
            SharedUnequipSetup(
                injuredBaseline = injuredBaseline,
                equipped = equipped,
                purchases = purchases,
                historicalTaskId = historicalTaskId,
                historicalLedger = requireNotNull(
                    container.database.rewardLedgerDao().find(
                        historicalTaskId,
                        today.toEpochDay(),
                    ),
                ),
                historicalAttack = requireNotNull(
                    container.database.combatDao().getPlayerAttackEvent(
                        historicalTaskId,
                        today.toEpochDay(),
                    ),
                ),
            )
        }
        waitForCombatEffectsToFinish()

        openShop()
        val hpBeforeChestUnequip = runBlocking {
            container.database.characterProfileDao().getCurrentState(characterId)!!.currentHp
        }
        unequipShopSlot(EquipmentSlot.CHEST)
        val afterChest = runBlocking {
            container.equipmentRepository.observeStore(characterId).first {
                EquipmentSlot.CHEST !in it.equippedBySlot
            }
        }
        assertEquals(
            setup.equipped.renderedEquippedItems.copy(
                topId = CharacterLoadoutCatalog.TOP_DEFAULT,
            ),
            afterChest.renderedEquippedItems,
        )
        assertEquals(
            CombatCalculator.preserveHpRatio(
                oldHp = hpBeforeChestUnequip,
                oldMax = setup.equipped.derivedStats.maxHp,
                newMax = afterChest.derivedStats.maxHp,
                config = CharacterStatBalanceConfig(),
            ),
            runBlocking {
                container.database.characterProfileDao().getCurrentState(characterId)!!.currentHp
            },
        )

        composeRule.onNodeWithTag("shop-open-inventory").performClick()
        val weaponOwnedId = setup.purchases.getValue(EquipmentSlot.WEAPON).ownedEquipmentId
        unequipInventoryItem(weaponOwnedId)
        val afterWeapon = runBlocking {
            container.equipmentRepository.observeStore(characterId).first {
                EquipmentSlot.WEAPON !in it.equippedBySlot
            }
        }
        assertEquals(
            afterChest.renderedEquippedItems.copy(weaponId = null),
            afterWeapon.renderedEquippedItems,
        )

        runBlocking {
            listOf(
                EquipmentSlot.HELMET,
                EquipmentSlot.LEGS,
                EquipmentSlot.SHOES,
            ).forEach { slot ->
                container.unequipEquipmentUseCase(characterId, slot)
            }
        }
        val finalStore = runBlocking {
            container.equipmentRepository.observeStore(characterId).first {
                it.equippedBySlot.isEmpty()
            }
        }
        val expectedUnequippedLoadout = listOf(
            EquipmentSlot.HELMET,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.SHOES,
            EquipmentSlot.WEAPON,
        ).fold(CharacterLoadoutCatalog.defaultEquippedItems) { current, slot ->
            EquipmentUnequipAppearancePolicy.clearSlot(current, slot)
        }
        assertEquals(expectedUnequippedLoadout, finalStore.renderedEquippedItems)
        assertEquals(setup.injuredBaseline.derivedStats, finalStore.derivedStats)
        assertEquals(
            setup.purchases.values.mapTo(mutableSetOf()) { it.equipmentId },
            finalStore.ownedEquipmentIds.intersect(
                setup.purchases.values.mapTo(mutableSetOf()) { it.equipmentId },
            ),
        )
        assertTrue(
            runBlocking {
                container.database.statusEffectDao().getStatusEffect(
                    characterId,
                    StatusEffectType.SEVERE_INJURY.name,
                )
            }!!.active,
        )
        assertEquals(
            setup.historicalLedger,
            runBlocking {
                container.database.rewardLedgerDao().find(
                    setup.historicalTaskId,
                    today.toEpochDay(),
                )
            },
        )
        assertEquals(
            setup.historicalAttack,
            runBlocking {
                container.database.combatDao().getPlayerAttackEvent(
                    setup.historicalTaskId,
                    today.toEpochDay(),
                )
            },
        )

        val (newLedger, newAttack) = runBlocking {
            val newTaskId = container.taskRepository.createTask(
                CreateTaskInput(
                    title = "해제 후 전투 source",
                    memo = "",
                    startDate = today,
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                ),
            )
            container.taskRepository.completeOccurrence(newTaskId, today)
            requireNotNull(
                container.database.rewardLedgerDao().find(
                    newTaskId,
                    today.toEpochDay(),
                ),
            ) to requireNotNull(
                container.database.combatDao().getPlayerAttackEvent(
                    newTaskId,
                    today.toEpochDay(),
                ),
            )
        }
        assertEquals(finalStore.derivedStats.goldGainBonusBp, newLedger.goldGainBonusBp)
        assertEquals(finalStore.derivedStats.attack, newAttack.sourceAttack)
        assertTrue(newAttack.sourceAttack < setup.historicalAttack.sourceAttack)

        val character = runBlocking {
            container.characterRepository.observeCharacter(today).first {
                it.equippedItems == expectedUnequippedLoadout
            }
        }
        assertEquals(finalStore.derivedStats, character.derivedStats)
        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("equipped-character-sprite").assertIsDisplayed()
        assertSharedSpriteRendersLoadout("equipped-character-sprite", expectedUnequippedLoadout)
        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.onNodeWithTag("battle-player-layer").assertIsDisplayed()
        assertSharedSpriteRendersLoadout("battle-player-layer", expectedUnequippedLoadout)
        assertSevereInjuryLifecycleIsNotReplaying()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("battle-player-layer").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("해제 완료").assertDoesNotExist()
        assertSevereInjuryLifecycleIsNotReplaying()
        composeRule.onNodeWithTag("bottom-navigation-shop").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("shop-open-inventory").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("inventory-back").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("inventory-back").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("inventory-back").performClick()
        }
        composeRule.onNodeWithTag("shop-open-inventory").assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        listOf("helmet", "chest", "legs", "shoes", "weapon").forEach { slot ->
            composeRule.onNodeWithTag("shop-equipment-slot-$slot")
                .assert(hasContentDescription("비어 있음", substring = true))
        }
        } finally {
            composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
            installContainerAndCreateNewViewModels(productionContainer)
            isolatedDatabase.close()
        }
    }

    @Test
    fun fixedBattleMapScrollableCalendarAndBottomNavigationDoNotOverlap() {
        val taskId = createTask("앱 경계 보상 확인")
        val today = appContainer().clock.today()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("complete-task-$taskId-$today")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val mapBounds = boundsOf("battle-map")
        val scrollBounds = boundsOf("task-lazy-list")
        val bottomNavigationBounds = boundsOf("bottom-navigation")
        assertTrue(mapBounds.bottom <= scrollBounds.top + POSITION_TOLERANCE_PX)
        assertTrue(scrollBounds.bottom <= bottomNavigationBounds.top + POSITION_TOLERANCE_PX)
        listOf(
            "bottom-navigation-calendar",
            "bottom-navigation-character",
            "bottom-navigation-shop",
            "bottom-navigation-compendium",
            "bottom-navigation-settings",
        ).map(::boundsOf).forEach { itemBounds ->
            assertTrue(itemBounds.left >= bottomNavigationBounds.left)
            assertTrue(itemBounds.right <= bottomNavigationBounds.right)
            assertTrue(itemBounds.top >= bottomNavigationBounds.top)
            assertTrue(itemBounds.bottom <= bottomNavigationBounds.bottom)
        }

        scrollCalendarTo("complete-task-$taskId-$today")
        composeRule.onNodeWithTag("complete-task-$taskId-$today").performClick()
        composeRule.waitForIdle()
        assertTrue(
            boundsOf("calendar-snackbar-host").height <= POSITION_TOLERANCE_PX,
        )
    }

    @Test
    fun automaticDeadlineFailureRestoresAfterRecreationAndUndoKeepsCombatResult() {
        val container = appContainer()
        val today = container.clock.today()
        val initial = runBlocking { container.combatRepository.observeCombat().first() }
        val taskId = createTask("자동 마감 실패 복원")
        val reconciledAt = container.clock.now().plusSeconds(TWO_DAYS_SECONDS)

        val reconciliation = runBlocking {
            container.combatRepository.reconcileOverdue(reconciledAt)
        }
        val persisted = runBlocking { container.combatRepository.observeCombat().first() }
        val storedEvent = runBlocking {
            container.database.combatDao().getMonsterAttackEvent(taskId, today.toEpochDay())
        }
        assertEquals(1, reconciliation.monsterAttacksApplied)
        assertEquals(0, reconciliation.monsterAttacksSkipped)
        assertTrue(persisted.playerCurrentHp < initial.playerCurrentHp)
        assertEquals(initial.activeMonster.id, persisted.activeMonster.id)
        assertNotNull(storedEvent)
        assertEquals(MonsterAttackTrigger.MISSED_DEADLINE.name, storedEvent?.trigger)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("undo-fail-task-$taskId-$today")
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollCalendarTo("task-$taskId-$today")
        composeRule.onNodeWithText("자동 마감 실패 복원").assertIsDisplayed()
        composeRule.onNodeWithText("실패").assertIsDisplayed()
        composeRule.onNodeWithTag("complete-task-$taskId-$today").assertDoesNotExist()
        composeRule.onNodeWithTag("fail-task-$taskId-$today").assertDoesNotExist()
        composeRule.onNodeWithTag("undo-fail-task-$taskId-$today").assertIsDisplayed()
        waitForCombatEffectsToFinish()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        scrollCalendarTo("task-$taskId-$today")

        composeRule.onNodeWithText("자동 마감 실패 복원").assertIsDisplayed()
        composeRule.onNodeWithText("실패").assertIsDisplayed()
        composeRule.onNodeWithTag("complete-task-$taskId-$today").assertDoesNotExist()
        composeRule.onNodeWithTag("fail-task-$taskId-$today").assertDoesNotExist()
        composeRule.onNodeWithTag("undo-fail-task-$taskId-$today")
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithTag("battle-monster-layer").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(
                R.string.battle_unit_health_description,
                composeRule.activity.getString(R.string.battle_player_name),
                persisted.playerCurrentHp,
                persisted.playerMaxHp,
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("battle-attack-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-damage-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-death-effect").assertDoesNotExist()

        composeRule.onNodeWithTag("undo-fail-task-$taskId-$today").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("complete-task-$taskId-$today")
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("fail-task-$taskId-$today")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("complete-task-$taskId-$today").assertIsDisplayed()
        composeRule.onNodeWithTag("fail-task-$taskId-$today").assertIsDisplayed()
        composeRule.onNodeWithTag("undo-fail-task-$taskId-$today").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(
                R.string.battle_unit_health_description,
                composeRule.activity.getString(R.string.battle_player_name),
                persisted.playerCurrentHp,
                persisted.playerMaxHp,
            ),
        ).assertIsDisplayed()
        assertNull(
            runBlocking {
                container.database.failureLogDao().find(taskId, today.toEpochDay())
            },
        )
        assertEquals(
            storedEvent,
            runBlocking {
                container.database.combatDao().getMonsterAttackEvent(taskId, today.toEpochDay())
            },
        )
        assertEquals(
            persisted.playerCurrentHp,
            runBlocking { container.combatRepository.observeCombat().first() }.playerCurrentHp,
        )

        val repeated = runBlocking {
            container.combatRepository.reconcileOverdue(reconciledAt.plusSeconds(60))
        }
        assertEquals(0, repeated.monsterAttacksApplied)
        assertEquals(0, repeated.monsterAttacksSkipped)
        composeRule.onNodeWithTag("battle-attack-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-damage-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-death-effect").assertDoesNotExist()
    }

    @Test
    fun severeInjuryPersistsAcrossCharacterReentryAndActivityRecreationWithoutReplayingLifecycle() {
        val container = appContainer()
        val nowEpochMillis = container.clock.now().toEpochMilli()
        runBlocking {
            container.combatRepository.observeCombat().first()
            container.database.statusEffectDao().upsertStatusEffect(
                CharacterStatusEffectEntity(
                    characterId = CharacterMapper.DEFAULT_CHARACTER_ID,
                    effectType = StatusEffectType.SEVERE_INJURY.name,
                    definitionVersion = 1,
                    appliedAtEpochMillis = nowEpochMillis,
                    expiresAtEpochMillis = nowEpochMillis + 24L * 60L * 60L * 1_000L,
                    remainingRecoveryCompletions = 2,
                    active = true,
                    revision = 1L,
                    lastMutationId = "test:severe-injury:persisted",
                ),
            )
            container.statusEffectRepository.observeActiveStatusEffects(
                CharacterMapper.DEFAULT_CHARACTER_ID,
            ).first { effects -> effects.any { it.type == StatusEffectType.SEVERE_INJURY } }
        }
        installContainerAndCreateNewViewModels(container)

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("character-severe-injury-badge"))
        composeRule.onNodeWithTag("character-severe-injury-badge").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("character-severe-injury-badge")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("character-severe-injury-badge").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("battle-map").fetchSemanticsNodes().isNotEmpty()
        }
        assertSevereInjuryLifecycleIsNotReplaying()

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("character-severe-injury-badge"))
        composeRule.onNodeWithTag("character-severe-injury-badge").assertIsDisplayed()
    }

    @Test
    fun stagedBatchStatsUpdateSharedRoomCharacterAndCombatOnlyAfterSingleSave() {
        val container = appContainer()
        val today = container.clock.today()
        val characterDao = container.database.characterProfileDao()
        val initializedCombat = runBlocking { container.combatRepository.observeCombat().first() }
        val partialHp = initializedCombat.playerMaxHp / 2
        runBlocking {
            characterDao.upsert(
                characterDao.getProfile()!!.copy(
                    totalXp = 200L,
                    unspentStatPoints = 4,
                ),
            )
            characterDao.upsertCurrentState(
                characterDao.getCurrentState()!!.copy(currentHp = partialHp),
            )
        }
        val beforeCharacter = runBlocking {
            container.characterRepository.observeCharacter(today).first {
                it.character.unspentStatPoints == 4 && it.currentState.currentHp == partialHp
            }
        }
        val beforeCombat = runBlocking {
            container.combatRepository.observeCombat().first {
                it.playerCurrentHp == partialHp
            }
        }
        val beforeProfile = runBlocking { characterDao.getProfile()!! }
        val beforeCurrentState = runBlocking { characterDao.getCurrentState()!! }
        val progressDescription = composeRule.activity.getString(
            R.string.battle_player_progress_description,
            beforeCharacter.level.toString(),
            beforeCharacter.xpIntoCurrentLevel.toString(),
            beforeCharacter.xpRequiredForNextLevel.toString(),
            beforeCharacter.character.currentGold.toString(),
        )
        composeRule.onNodeWithTag("player-progress-hud", useUnmergedTree = true)
            .assertContentDescriptionEquals(progressDescription)

        composeRule.onNodeWithTag("bottom-navigation-character").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add-strength").fetchSemanticsNodes().isNotEmpty()
        }
        listOf(StatType.STRENGTH, StatType.VITALITY, StatType.FOCUS, StatType.WILLPOWER)
            .forEach { stat ->
                val tag = "add-${stat.name.lowercase()}"
                composeRule.onNodeWithTag("character-screen-scroll")
                    .performScrollToNode(hasTestTag(tag))
                composeRule.onNodeWithTag(tag).performClick()
            }

        assertEquals(beforeProfile, runBlocking { characterDao.getProfile() })
        assertEquals(beforeCurrentState, runBlocking { characterDao.getCurrentState() })
        assertEquals(beforeCombat, runBlocking { container.combatRepository.observeCombat().first() })
        val stagedCharacter = runBlocking {
            container.characterRepository.observeCharacter(today).first()
        }
        assertEquals(beforeCharacter.character, stagedCharacter.character)
        assertEquals(beforeCharacter.currentState, stagedCharacter.currentState)
        assertEquals(beforeCharacter.derivedStats, stagedCharacter.derivedStats)
        composeRule.onNodeWithText("미배분 능력치 포인트 0").assertIsDisplayed()
        listOf("힘", "활력", "집중", "의지").forEach { label ->
            composeRule.onNodeWithContentDescription(
                "$label, 확정 5, 저장 전 +1, 예상 6",
            ).assertExists()
        }
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasText("최대 체력"))
        composeRule.onNodeWithText(beforeCharacter.derivedStats.maxHp.toString())
            .assertIsDisplayed()

        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("save-stat-allocation-button"))
        composeRule.onNodeWithTag("save-stat-allocation-button").assertIsEnabled()
        composeRule.onNodeWithTag("save-stat-allocation-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { characterDao.getProfile()?.unspentStatPoints == 0 } ||
                composeRule.onAllNodesWithTag("character-error-dialog")
                    .fetchSemanticsNodes().isNotEmpty()
        }

        val visibleSaveErrors = listOf(
            "미배분 능력치 포인트가 없습니다.",
            "능력치 포인트를 배분하지 못했습니다.",
        ).filter { message ->
            composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Batch stat save failed with UI errors: $visibleSaveErrors",
            0,
            runBlocking { characterDao.getProfile()?.unspentStatPoints },
        )

        val afterProfile = runBlocking { characterDao.getProfile()!! }
        val afterCurrentState = runBlocking { characterDao.getCurrentState()!! }
        val afterCharacter = runBlocking {
            container.characterRepository.observeCharacter(today).first {
                it.character.unspentStatPoints == 0
            }
        }
        val afterCombat = runBlocking {
            container.combatRepository.observeCombat().first {
                it.playerCurrentHp == afterCurrentState.currentHp &&
                    it.playerMaxHp == afterCharacter.derivedStats.maxHp
            }
        }
        assertEquals(beforeProfile.strength + 1, afterProfile.strength)
        assertEquals(beforeProfile.vitality + 1, afterProfile.vitality)
        assertEquals(beforeProfile.focus + 1, afterProfile.focus)
        assertEquals(beforeProfile.willpower + 1, afterProfile.willpower)
        assertEquals(0, afterProfile.unspentStatPoints)
        assertNotEquals(beforeCurrentState.currentHp, afterCurrentState.currentHp)
        assertNotEquals(beforeCharacter.derivedStats, afterCharacter.derivedStats)
        assertEquals(afterCurrentState.currentHp, afterCombat.playerCurrentHp)
        assertEquals(afterCharacter.derivedStats.maxHp, afterCombat.playerMaxHp)
        composeRule.onNodeWithText("미배분 능력치 포인트 0").assertIsDisplayed()
        listOf("힘", "활력", "집중", "의지").forEach { label ->
            composeRule.onNodeWithContentDescription("$label, 확정 6").assertExists()
        }
        composeRule.onAllNodesWithText("(+1 저장 전)").assertCountEquals(0)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("미배분 능력치 포인트 0")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertSame(container.database, appContainer().database)
        assertSame(container.characterRepository, appContainer().characterRepository)
        assertSame(container.combatRepository, appContainer().combatRepository)
        assertEquals(afterProfile, runBlocking { characterDao.getProfile() })
        assertEquals(afterCurrentState, runBlocking { characterDao.getCurrentState() })

        composeRule.onNodeWithTag("bottom-navigation-calendar").performClick()
        composeRule.onNodeWithTag("player-progress-hud", useUnmergedTree = true)
            .assertContentDescriptionEquals(progressDescription)
        val afterHealthDescription = composeRule.activity.getString(
            R.string.battle_unit_health_description,
            composeRule.activity.getString(R.string.battle_player_name),
            afterCombat.playerCurrentHp,
            afterCombat.playerMaxHp,
        )
        composeRule.onNodeWithContentDescription(afterHealthDescription).assertIsDisplayed()
    }

    private fun waitForCombatEffectsToFinish() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("battle-attack-effect").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("battle-damage-effect").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("battle-death-effect").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertSevereInjuryLifecycleIsNotReplaying() {
        composeRule.onNodeWithTag("battle-player-defeated-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-status-effect").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-emergency-recovery-effect").assertDoesNotExist()
    }

    private fun scrollCalendarTo(tag: String) {
        composeRule.onNodeWithTag("task-lazy-list")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun openShop() {
        composeRule.onNodeWithTag("bottom-navigation-shop").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("shop-open-inventory")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun purchaseEquipment(equipmentId: Long) {
        val cardTag = "shop-equipment-card-$equipmentId"
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag(cardTag))
        composeRule.onNodeWithTag("shop-card-purchase-$equipmentId").performClick()
        composeRule.onNodeWithTag("shop-confirm-purchase").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("shop-purchase-success")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun equipPurchasedEquipment() {
        composeRule.onNodeWithText("바로 장착").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("장착 완료").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("확인").performClick()
        composeRule.waitForIdle()
    }

    private fun waitForStoreCard(equipmentId: Long, text: String) {
        val cardTag = "shop-equipment-card-$equipmentId"
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag(cardTag))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(cardTag).assertTextContains(text)
            }.isSuccess
        }
    }

    private fun dismissEquipmentDetail() {
        composeRule.onNodeWithTag("shop-equipment-detail")
            .performTouchInput { swipeDown() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("shop-equipment-detail")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertShopPreview(
        chestName: String,
        legsName: String,
        attack: Int,
        maxHp: Int,
        defense: Int,
    ) {
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag("shop-character-sprite", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("shop-equipment-slot-chest")
            .assert(hasContentDescription(chestName, substring = true))
            .assert(hasContentDescription("장착 중", substring = true))
        composeRule.onNodeWithTag("shop-equipment-slot-legs")
            .assert(hasContentDescription(legsName, substring = true))
            .assert(hasContentDescription("장착 중", substring = true))
        composeRule.onNodeWithTag("shop-stat-attack")
            .assertTextContains(attack.toString())
        composeRule.onNodeWithTag("shop-stat-max-hp")
            .assertTextContains(maxHp.toString())
        composeRule.onNodeWithTag("shop-stat-defense")
            .assertTextContains(defense.toString())
    }

    private fun equipInventoryItem(ownedEquipmentId: Long) {
        val equipTag = "inventory-equip-$ownedEquipmentId"
        composeRule.onNodeWithTag("inventory-list")
            .performScrollToNode(hasTestTag(equipTag))
        composeRule.onNodeWithTag(equipTag).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("장착 완료").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("확인").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("inventory-equipped-$ownedEquipmentId")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun unequipShopSlot(slot: EquipmentSlot) {
        composeRule.onNodeWithTag("shop-equipment-list")
            .performScrollToNode(hasTestTag("shop-character-preview"))
        composeRule.onNodeWithTag("shop-equipment-slot-${slot.name.lowercase()}").performClick()
        composeRule.onNodeWithTag("shop-slot-management-unequip").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("해제 완료").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("확인").performClick()
        composeRule.onNodeWithTag("shop-slot-management-close").performClick()
    }

    private fun unequipInventoryItem(ownedEquipmentId: Long) {
        val commandTag = "inventory-equip-$ownedEquipmentId"
        composeRule.onNodeWithTag("inventory-list")
            .performScrollToNode(hasTestTag(commandTag))
        composeRule.onNodeWithTag(commandTag).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("해제 완료").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("확인").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("inventory-equipped-$ownedEquipmentId")
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("inventory-list")
            .performScrollToNode(hasTestTag("inventory-equipment-$ownedEquipmentId"))
        composeRule.onNodeWithTag("inventory-equipment-$ownedEquipmentId").assertIsDisplayed()
    }

    private fun assertSharedSpriteRendersLoadout(
        tag: String,
        expectedItems: com.todoquest.domain.model.EquippedItems,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = requireNotNull(
            CharacterBitmapComposer(context.assets).compose(
                CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = expectedItems,
                ),
            ),
        )
        val captured = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "$tag must render every opaque color from the shared loadout",
            captured.opaqueColors().containsAll(expected.opaqueColors()),
        )
    }

    private fun Bitmap.opaqueColors(): Set<Int> = buildSet {
        for (y in 0 until height) {
            for (x in 0 until width) {
                getPixel(x, y).takeIf { it ushr 24 != 0 }?.let(::add)
            }
        }
    }

    private fun Bitmap.hasOnlyOpaquePixels(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) ushr 24 != 0xFF) return false
            }
        }
        return true
    }

    private fun createTask(
        title: String,
        date: LocalDate = appContainer().clock.today(),
    ): Long {
        val container = appContainer()
        return runBlocking {
            container.taskRepository.createTask(
                CreateTaskInput(
                    title = title,
                    memo = "",
                    startDate = date,
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                ),
            )
        }
    }

    private fun TodoQuestAppContainer.withPromptPreparation(
        store: FirstLaunchNotificationPromptStore,
        preparePrompt: PrepareFirstLaunchNotificationPromptUseCase,
    ): TodoQuestAppContainer = TodoQuestAppContainer(
        database = database,
        clock = clock,
        taskRepository = taskRepository,
        characterRepository = characterRepository,
        combatRepository = combatRepository,
        equipmentRepository = equipmentRepository,
        reminderRepository = reminderRepository,
        reminderScheduler = reminderScheduler,
        reminderPublisher = reminderPublisher,
        reminderCapabilityAdapter = reminderCapabilityAdapter,
        firstLaunchNotificationPromptStore = store,
        prepareFirstLaunchNotificationPromptUseCase = preparePrompt,
    )

    private fun installContainerAndCreateNewViewModels(
        container: TodoQuestAppContainer,
        readyTag: String = "battle-map",
    ) {
        val application = composeRule.activity.application as TodoQuestInstrumentedTestApplication
        application.installTestContainer(container)
        composeRule.runOnUiThread {
            composeRule.activity.viewModelStore.clear()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(readyTag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun appContainer(): TodoQuestAppContainer =
        (composeRule.activity.application as TodoQuestContainerOwner).todoQuestContainer

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode().boundsInRoot

    private fun alternateDateInCurrentMonth(): LocalDate {
        val today = LocalDate.now()
        return if (today.dayOfMonth == 1) today.plusDays(1) else today.minusDays(1)
    }

    private fun selectedDateText(date: LocalDate): String {
        val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
        return "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 ${weekday}요일"
    }

    private data class SharedUnequipSetup(
        val injuredBaseline: com.todoquest.domain.model.EquipmentStoreSnapshot,
        val equipped: com.todoquest.domain.model.EquipmentStoreSnapshot,
        val purchases: Map<EquipmentSlot, PurchaseEquipmentResult.Success>,
        val historicalTaskId: Long,
        val historicalLedger: com.todoquest.data.local.RewardLedgerEntity,
        val historicalAttack: com.todoquest.data.local.PlayerAttackEventEntity,
    )

    private class CountingFirstLaunchNotificationPromptStore :
        FirstLaunchNotificationPromptStore {
        var consumeCalls = 0
        private var consumed = false

        override fun consumeFirstLaunchCheck(): Boolean {
            consumeCalls += 1
            return (!consumed).also { consumed = true }
        }
    }

    private class ThrowingFirstLaunchNotificationPromptStore :
        FirstLaunchNotificationPromptStore {
        var consumeCalls = 0

        override fun consumeFirstLaunchCheck(): Boolean {
            consumeCalls += 1
            throw IllegalStateException("preference unavailable")
        }
    }

    private class PromptCapabilityScheduler(
        private val failure: Throwable? = null,
    ) : ReminderScheduler {
        val capabilityCalls = mutableListOf<ReminderCapability>()

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus {
            capabilityCalls += capability
            failure?.let { throw it }
            return ReminderCapabilityStatus.REQUIRED
        }

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus =
            error("First-launch prompt preparation must not schedule alarms")

        override suspend fun cancel(
            key: ReminderOccurrenceKey,
        ): ReminderScheduleStatus =
            error("First-launch prompt preparation must not cancel alarms")
    }

    private class SettingsCapabilityScheduler(
        initialStatus: ReminderCapabilityStatus,
    ) : ReminderScheduler {
        private val statuses = ArrayDeque<ReminderCapabilityStatus>().apply {
            add(initialStatus)
        }
        private var lastStatus = initialStatus
        val capabilityCalls = mutableListOf<ReminderCapability>()

        fun enqueue(status: ReminderCapabilityStatus) {
            statuses.add(status)
        }

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus {
            capabilityCalls += capability
            return if (statuses.isEmpty()) {
                lastStatus
            } else {
                statuses.removeFirst().also { lastStatus = it }
            }
        }

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus =
            error("Settings capability checks must not schedule alarms")

        override suspend fun cancel(
            key: ReminderOccurrenceKey,
        ): ReminderScheduleStatus =
            error("Settings capability checks must not cancel alarms")
    }

    private class EligibleCharacterGuideRepository : CharacterGuideRepository {
        var acknowledgeCalls = 0
        private var acknowledged = false

        override fun statAllocationGuideStatus(): CharacterStatGuideStatus =
            CharacterStatGuideStatus(
                automaticDisplayEligible = true,
                acknowledged = acknowledged,
            )

        override fun acknowledgeStatAllocationGuide(): Boolean {
            acknowledgeCalls += 1
            acknowledged = true
            return true
        }
    }

    private companion object {
        const val POSITION_TOLERANCE_PX = 1f
        const val TWO_DAYS_SECONDS = 2L * 24L * 60L * 60L
    }
}
