package com.todoquest.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.todoquest.notification.NotificationPermissionLaunchAction
import com.todoquest.ui.theme.TodoQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enabledSettingRendersKoreanCopySemanticsAndMinimumTargets() {
        composeRule.setContent {
            TodoQuestTheme {
                SettingsContent(
                    state = SettingsUiState(battleSfxEnabled = true),
                    onSetBattleSfxEnabled = {},
                    onClearSaveError = {},
                    onNotificationPermissionAction = {},
                )
            }
        }

        composeRule.onNodeWithText("설정").assertIsDisplayed()
        composeRule.onNodeWithText("효과음").assertIsDisplayed()
        composeRule.onNodeWithText("켜짐").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-battle-sfx-row")
            .assertContentDescriptionEquals("효과음, 켜짐")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings-battle-sfx-switch", useUnmergedTree = true)
            .assertIsOn()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun switchAndRowClicksEachToggleExactlyOnce() {
        val state = mutableStateOf(SettingsUiState(battleSfxEnabled = true))
        val requests = mutableListOf<Boolean>()
        composeRule.setContent {
            TodoQuestTheme {
                SettingsContent(
                    state = state.value,
                    onSetBattleSfxEnabled = { enabled ->
                        requests += enabled
                        state.value = state.value.copy(battleSfxEnabled = enabled)
                    },
                    onClearSaveError = {},
                    onNotificationPermissionAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-battle-sfx-switch", useUnmergedTree = true)
            .performClick()
            .assertIsOff()
        composeRule.runOnIdle { assertEquals(listOf(false), requests) }

        composeRule.onNodeWithTag("settings-battle-sfx-row")
            .performClick()
        composeRule.onNodeWithTag("settings-battle-sfx-switch", useUnmergedTree = true)
            .assertIsOn()
        composeRule.runOnIdle { assertEquals(listOf(false, true), requests) }
    }

    @Test
    fun saveFailureIsShownWithKoreanSnackbar() {
        composeRule.setContent {
            TodoQuestTheme {
                SettingsContent(
                    state = SettingsUiState(saveFailed = true),
                    onSetBattleSfxEnabled = {},
                    onClearSaveError = {},
                    onNotificationPermissionAction = {},
                )
            }
        }

        composeRule.onNodeWithText("효과음 설정을 저장하지 못했습니다.")
            .assertIsDisplayed()
    }

    @Test
    fun compactDoubleFontKeepsSettingReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        SettingsContent(
                            state = SettingsUiState(battleSfxEnabled = false),
                            onSetBattleSfxEnabled = {},
                            onClearSaveError = {},
                            onNotificationPermissionAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("설정").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-content-scroll").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-battle-sfx-row")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("효과음, 꺼짐")
        composeRule.onNodeWithTag("settings-battle-sfx-switch", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertIsOff()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings-notification-permission-row")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("알림 권한, 확인 중, 확인 중")
        composeRule.onNodeWithTag(
            "settings-notification-permission-action",
            useUnmergedTree = true,
        ).assertIsDisplayed()
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun notificationPermissionStatesRenderKoreanLabelsActionsAndMergedSemantics() {
        val state = mutableStateOf(
            SettingsUiState(notificationPermission = NotificationPermissionUiState.Loading),
        )
        var actionCalls = 0
        composeRule.setContent {
            TodoQuestTheme {
                SettingsContent(
                    state = state.value,
                    onSetBattleSfxEnabled = {},
                    onClearSaveError = {},
                    onNotificationPermissionAction = { actionCalls += 1 },
                )
            }
        }

        assertNotificationPermissionPresentation(
            status = "확인 중",
            action = "확인 중",
            semantics = "알림 권한, 확인 중, 확인 중",
            enabled = false,
        )

        listOf(
            Triple(NotificationPermissionUiState.Available, "허용됨", "알림 설정"),
            Triple(NotificationPermissionUiState.Required, "권한 필요", "권한 허용"),
            Triple(
                NotificationPermissionUiState.ChannelDisabled,
                "알림 채널 꺼짐",
                "알림 채널 설정",
            ),
            Triple(NotificationPermissionUiState.CheckFailed, "상태 확인 실패", "다시 시도"),
        ).forEach { (permissionState, status, action) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(notificationPermission = permissionState)
            }
            assertNotificationPermissionPresentation(
                status = status,
                action = action,
                semantics = "알림 권한, $status, $action",
                enabled = true,
            )
            composeRule.onNodeWithTag(
                "settings-notification-permission-action",
                useUnmergedTree = true,
            ).performClick()
        }

        composeRule.runOnIdle { assertEquals(4, actionCalls) }
    }

    @Test
    fun permissionActionAndRowClicksDoNotDisableBattleSfx() {
        val state = mutableStateOf(
            SettingsUiState(
                battleSfxEnabled = true,
                notificationPermission = NotificationPermissionUiState.Required,
            ),
        )
        var permissionActions = 0
        val sfxRequests = mutableListOf<Boolean>()
        composeRule.setContent {
            TodoQuestTheme {
                SettingsContent(
                    state = state.value,
                    onSetBattleSfxEnabled = { enabled ->
                        sfxRequests += enabled
                        state.value = state.value.copy(battleSfxEnabled = enabled)
                    },
                    onClearSaveError = {},
                    onNotificationPermissionAction = { permissionActions += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("settings-notification-permission-row").performClick()
        composeRule.onNodeWithTag(
            "settings-notification-permission-action",
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithTag("settings-battle-sfx-switch", useUnmergedTree = true)
            .performClick()
            .assertIsOff()

        composeRule.runOnIdle {
            assertEquals(2, permissionActions)
            assertEquals(listOf(false), sfxRequests)
        }
    }

    @Test
    fun launcherFailuresAreIsolatedAndRefreshPermissionState() {
        var refreshCalls = 0

        launchNotificationPermissionAction(
            action = NotificationPermissionLaunchAction.RuntimePermission("permission.test"),
            launchRuntimePermission = { throw IllegalStateException("runtime launcher failed") },
            launchSettings = {},
            refresh = { refreshCalls += 1 },
        )
        launchNotificationPermissionAction(
            action = NotificationPermissionLaunchAction.AppSettings(Intent("settings.test")),
            launchRuntimePermission = {},
            launchSettings = { throw IllegalStateException("settings launcher failed") },
            refresh = { refreshCalls += 1 },
        )
        launchNotificationPermissionAction(
            action = NotificationPermissionLaunchAction.None,
            launchRuntimePermission = {},
            launchSettings = {},
            refresh = { refreshCalls += 1 },
        )

        assertEquals(3, refreshCalls)
    }

    private fun assertNotificationPermissionPresentation(
        status: String,
        action: String,
        semantics: String,
        enabled: Boolean,
    ) {
        composeRule.onNodeWithText("알림 권한").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "settings-notification-permission-status",
            useUnmergedTree = true,
        ).assertTextContains(status)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings-notification-permission-row")
            .assertContentDescriptionEquals(semantics)
            .assertHeightIsAtLeast(48.dp)
        val actionNode = composeRule.onNodeWithTag(
            "settings-notification-permission-action",
            useUnmergedTree = true,
        ).assertContentDescriptionEquals(action)
            .assertHeightIsAtLeast(48.dp)
        if (enabled) {
            actionNode.assertIsEnabled()
        } else {
            actionNode.assertIsNotEnabled()
        }
    }
}
