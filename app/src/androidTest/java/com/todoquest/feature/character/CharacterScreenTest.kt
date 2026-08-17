package com.todoquest.feature.character

import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.R
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.feature.battle.ActiveStatusEffectUiModel
import com.todoquest.feature.battle.StatusEffectRemainingTimeUiState
import com.todoquest.ui.theme.TodoQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CharacterScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleStatAllocationGuideRendersAutomaticDialogCopy() {
        val state = mutableStateOf(
            populatedState(unspentPoints = 3).copy(
                isStatAllocationGuideVisible = true,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                    onShowStatAllocationGuide = {
                        state.value = state.value.copy(isStatAllocationGuideVisible = true)
                    },
                    onDismissStatAllocationGuide = {
                        state.value = state.value.copy(isStatAllocationGuideVisible = false)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("character-stat-guide-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("능력치 배분 안내").assertIsDisplayed()
        composeRule.onNodeWithText("안내 요정").assertIsDisplayed()
        composeRule.onNodeWithText(
            "모험가님, 레벨이 오르면 능력치 포인트를 얻어요.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "힘은 공격력을, 활력은 최대 체력과 방어력을, 집중은 치명타를, " +
                "의지는 상태 이상 저항과 회복을 높여 줘요.",
        ).assertExists()
        composeRule.onNodeWithText(
            "원하는 능력치의 + 버튼을 누른 뒤 ‘능력치 배분 저장’을 눌러야 적용돼요. " +
                "능력치 이름을 누르면 자세한 효과도 확인할 수 있어요.",
        ).assertExists()
        composeRule.onNodeWithText("지금 배분할 수 있는 포인트가 3개 있어요.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("character-stat-guide-body-scroll")
            .assertContentDescriptionEquals(
                "안내 요정. 모험가님, 레벨이 오르면 능력치 포인트를 얻어요. " +
                    "힘은 공격력을, 활력은 최대 체력과 방어력을, 집중은 치명타를, " +
                    "의지는 상태 이상 저항과 회복을 높여 줘요. " +
                    "원하는 능력치의 + 버튼을 누른 뒤 ‘능력치 배분 저장’을 눌러야 적용돼요. " +
                    "능력치 이름을 누르면 자세한 효과도 확인할 수 있어요. " +
                    "지금 배분할 수 있는 포인트가 3개 있어요.",
            )
        composeRule.onNodeWithTag("character-stat-guide-sprite-frame", useUnmergedTree = true)
            .assertWidthIsEqualTo(96.dp)
            .assertHeightIsEqualTo(96.dp)
        val rendered = composeRule.onNodeWithTag(
            "character-stat-guide-sprite",
            useUnmergedTree = true,
        ).assertIsDisplayed().captureToImage().asAndroidBitmap()
        val source = BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources,
            R.drawable.todo_quest_fairy_guide_front_idle,
        )
        val sourceColors = buildSet {
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val color = source.getPixel(x, y)
                    if (color ushr 24 == 255) add(color)
                }
            }
        }
        val background = rendered.getPixel(0, 0)
        val renderedColors = buildSet {
            for (y in 0 until rendered.height) {
                for (x in 0 until rendered.width) add(rendered.getPixel(x, y))
            }
        }
        assertTrue(renderedColors.any(sourceColors::contains))
        assertTrue(
            "최근접 확대는 원본 팔레트 외 보간색을 만들지 않아야 합니다.",
            renderedColors.all { it == background || it in sourceColors },
        )

        composeRule.onNodeWithTag("character-stat-guide-primary")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag("character-stat-guide-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("character-stat-guide-help").assertIsDisplayed()
    }

    @Test
    fun zeroPointGuideDismissesAndHelpReopensWithDecodeFallback() {
        val state = mutableStateOf(
            populatedState(unspentPoints = 0).copy(
                isStatAllocationGuideVisible = true,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                    onShowStatAllocationGuide = {
                        state.value = state.value.copy(isStatAllocationGuideVisible = true)
                    },
                    onDismissStatAllocationGuide = {
                        state.value = state.value.copy(isStatAllocationGuideVisible = false)
                    },
                    statGuideSpriteResId = 0,
                )
            }
        }

        composeRule.onNodeWithText(
            "지금은 미배분 포인트가 없어요. 퀘스트를 완료해 레벨을 올리면 새 포인트를 얻을 수 있어요.",
        ).assertExists()
        composeRule.onNodeWithTag("character-stat-guide-fallback", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("character-stat-guide-secondary")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("character-stat-guide-dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("character-stat-guide-help"))
        composeRule.onNodeWithTag("character-stat-guide-help")
            .assertContentDescriptionEquals("능력치 배분 안내 열기")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag("character-stat-guide-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("character-stat-guide-fallback", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun smallScreenLargeFontKeepsGuideCopyScrollableAndActionsReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        CharacterContent(
                            state = populatedState(unspentPoints = 0).copy(
                                isStatAllocationGuideVisible = true,
                            ),
                            onIncreaseStat = {},
                            onDecreaseStat = {},
                            onSaveStatAllocation = {},
                            onRequestStatReset = {},
                            onDismissStatReset = {},
                            onConfirmStatReset = {},
                            onDismissError = {},
                            onShowStatAllocationGuide = {},
                            onDismissStatAllocationGuide = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("안내 요정", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("character-stat-guide-body-scroll", useUnmergedTree = true)
            .performScrollToNode(
                hasText(
                    "지금은 미배분 포인트가 없어요. 퀘스트를 완료해 레벨을 올리면 새 포인트를 얻을 수 있어요.",
                ),
            )
        composeRule.onNodeWithText(
            "지금은 미배분 포인트가 없어요. 퀘스트를 완료해 레벨을 올리면 새 포인트를 얻을 수 있어요.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("character-stat-guide-primary")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("character-stat-guide-secondary")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun largeFontCanScrollFromSummaryThroughResetAction() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodoQuestTheme {
                    CharacterContent(
                        state = populatedState(),
                        onIncreaseStat = {},
                        onDecreaseStat = {},
                        onSaveStatAllocation = {},
                        onRequestStatReset = {},
                        onDismissStatReset = {},
                        onConfirmStatReset = {},
                        onDismissError = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("캐릭터").assertIsDisplayed()
        composeRule.onNodeWithText("기본 능력치").assertExists()
        composeRule.onNodeWithContentDescription("현재 경험치 45, 필요 경험치 100")
            .assertExists()
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("reset-stats-button"))
        composeRule.onNodeWithTag("reset-stats-button").assertIsDisplayed()
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasText("골드 획득 보너스"))
        composeRule.onNodeWithText("골드 획득 보너스").assertIsDisplayed()
        composeRule.onNodeWithText("7.6%").assertIsDisplayed()
    }

    @Test
    fun characterArtHasMeaningfulTalkBackDescriptionAndMaxKeepsTotalXpVisible() {
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = populatedState(isMaxLevel = true, totalXp = 7_777L),
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Todo Quest 모험가 캐릭터")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("equipped-character-sprite").assertIsDisplayed()
        composeRule.onNodeWithText("최대 레벨").assertIsDisplayed()
        composeRule.onNodeWithText("누적 경험치 7,777").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("최대 레벨, 누적 경험치 7,777")
            .assertIsDisplayed()
    }

    @Test
    fun severeInjuryBadgeMergesSemanticsAndOpensViewModelOwnedDetails() {
        val injury = ActiveStatusEffectUiModel(
            type = StatusEffectType.SEVERE_INJURY,
            revision = 1L,
            remainingRecoveryCompletions = 2,
            remainingTime = StatusEffectRemainingTimeUiState.Hours(5),
        )
        val state = mutableStateOf(
            populatedState().copy(activeStatusEffects = listOf(injury)),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                    onShowStatusEffectDetails = {
                        state.value = state.value.copy(selectedStatusEffect = injury)
                    },
                    onDismissStatusEffectDetails = {
                        state.value = state.value.copy(selectedStatusEffect = null)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("character-severe-injury-badge"))
        composeRule.onNodeWithTag("character-severe-injury-badge").assertIsDisplayed()
        composeRule.onNodeWithText("중상", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "플레이어 상태 중상, 최대 체력과 공격력 20퍼센트 감소, 회복까지 할 일 2개",
        ).assertHeightIsAtLeast(48.dp).performClick()

        composeRule.onNodeWithTag("status-effect-details-dialog").assertIsDisplayed()
        composeRule.onNodeWithText(
            "전투에서 입은 심각한 부상으로 최대 체력과 공격력이 감소합니다.\n" +
                "할 일을 완료하거나 충분히 휴식하면 회복됩니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("최대 체력 -20%, 공격력 -20%").assertIsDisplayed()
        composeRule.onNodeWithText("회복 조건은 회복까지 할 일 2개 또는 5시간").assertIsDisplayed()
    }

    @Test
    fun statButtonsDisableWithoutPointsOrAtInvestmentCap() {
        val state = mutableStateOf(
            populatedState(
                unspentPoints = 2,
                strength = 60,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithTag("add-strength").assertIsNotEnabled()
        composeRule.onNodeWithTag("add-vitality").assertIsEnabled()
        composeRule.onNodeWithTag("remove-vitality")
            .assertIsNotEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("add-vitality")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("save-stat-allocation-button").assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = populatedState(
                unspentPoints = 0,
                strength = 59,
                pendingVitality = 1,
                isSavingStatAllocation = true,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add-strength").assertIsNotEnabled()
        composeRule.onNodeWithTag("add-vitality").assertIsNotEnabled()
        composeRule.onNodeWithTag("remove-vitality").assertIsNotEnabled()
        composeRule.onNodeWithTag("save-stat-allocation-button").assertIsNotEnabled()
        composeRule.onNodeWithTag("reset-stats-button").assertIsNotEnabled()
    }

    @Test
    fun draftControlsExposePendingValuesAndInvokeSeparateCommands() {
        var increased: StatType? = null
        var decreased: StatType? = null
        var saveCalls = 0
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = populatedState(
                        unspentPoints = 2,
                        pendingVitality = 1,
                        resetUnavailableReason = CharacterUiMessage.PendingStatAllocation,
                    ),
                    onIncreaseStat = { increased = it },
                    onDecreaseStat = { decreased = it },
                    onSaveStatAllocation = { saveCalls += 1 },
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithTag("add-vitality").performClick()
        composeRule.onNodeWithTag("remove-vitality").performClick()
        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("save-stat-allocation-button"))
        composeRule.onNodeWithTag("save-stat-allocation-button").performClick()

        assertEquals(StatType.VITALITY, increased)
        assertEquals(StatType.VITALITY, decreased)
        assertEquals(1, saveCalls)
        composeRule.onNodeWithContentDescription("활력 올리기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("활력 내리기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("활력, 확정 9, 저장 전 +1, 예상 10")
            .assertIsDisplayed()
        composeRule.onNodeWithText("(+1 저장 전)").assertIsDisplayed()
        composeRule.onNodeWithText("능력치 배분 저장").assertIsDisplayed()
        composeRule.onNodeWithTag("save-stat-allocation-button")
            .assertIsEnabled()
            .assertWidthIsAtLeast(200.dp)
        composeRule.onNodeWithTag("reset-stats-button").assertIsNotEnabled()
        composeRule.onNodeWithTag("character-screen-scroll").performScrollToNode(
            hasText(
                "저장 전 배분은 능력치 배분 저장 또는 - 버튼으로 정리한 뒤 초기화할 수 있습니다.",
            ),
        )
        composeRule.onNodeWithText(
            "저장 전 배분은 능력치 배분 저장 또는 - 버튼으로 정리한 뒤 초기화할 수 있습니다.",
        ).assertIsDisplayed()
    }

    @Test
    fun pendingLabelDoesNotMoveStatControlRegionHorizontally() {
        val state = mutableStateOf(populatedState(unspentPoints = 2, pendingVitality = 0))
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }
        val before = composeRule.onNodeWithTag("stat-controls-vitality")
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            state.value = populatedState(unspentPoints = 1, pendingVitality = 1)
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag("stat-controls-vitality")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(kotlin.math.abs(before.left - after.left) < 0.6f)
        assertTrue(kotlin.math.abs(before.right - after.right) < 0.6f)
    }

    @Test
    fun tappingBaseAndDerivedStatsShowsTheirDescriptions() {
        val state = mutableStateOf(populatedState())
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                    onShowBaseStatDescription = {
                        state.value = state.value.copy(statDescription = StatDescriptionTarget.Base(it))
                    },
                    onShowDerivedStatDescription = {
                        state.value = state.value.copy(statDescription = StatDescriptionTarget.Derived(it))
                    },
                    onDismissStatDescription = {
                        state.value = state.value.copy(statDescription = null)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("base-stat-strength").performClick()
        composeRule.onNodeWithTag("stat-description-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("기본 공격력을 높이는 능력치입니다.").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()

        composeRule.onNodeWithTag("character-screen-scroll")
            .performScrollToNode(hasTestTag("derived-stat-attack"))
        composeRule.onNodeWithTag("derived-stat-attack").performClick()
        composeRule.onNodeWithTag("stat-description-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("몬스터를 공격할 때 주는 피해의 기준값입니다.").assertIsDisplayed()
    }

    @Test
    fun unspentPointsAppearImmediatelyAfterBaseStatsTitle() {
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = populatedState(unspentPoints = 2, pendingVitality = 1),
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        val momentumBounds = composeRule.onNodeWithText("기세 +5.0%")
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText("기본 능력치")
            .fetchSemanticsNode().boundsInRoot
        val pointsBounds = composeRule.onNodeWithText("미배분 능력치 포인트 2")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(momentumBounds.bottom < titleBounds.top)
        assertTrue(titleBounds.bottom < pointsBounds.top)
    }

    @Test
    fun semanticMessagesRenderAsKoreanWithoutViewModelText() {
        val state = mutableStateOf(
            populatedState(
                error = CharacterUiMessage.NoUnspentStatPoints,
                resetUnavailableReason = CharacterUiMessage.NothingToReset,
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = state.value,
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("미배분 능력치 포인트가 없습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(
                error = CharacterUiMessage.StatAtInvestmentCap(
                    type = StatType.STRENGTH,
                    investmentCap = 60,
                ),
            )
        }
        composeRule.onNodeWithText("힘 능력치는 투자 상한 60에 도달했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(
                error = CharacterUiMessage.InsufficientGold(
                    requiredGold = 1_234L,
                    availableGold = 56L,
                ),
            )
        }
        composeRule.onNodeWithText("초기화에는 1,234 골드가 필요하지만 보유 골드는 56입니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.AllocationFailed)
        }
        composeRule.onNodeWithText("능력치 포인트를 배분하지 못했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.ResetFailed)
        }
        composeRule.onNodeWithText("능력치를 초기화하지 못했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.LoadFailed)
        }
        composeRule.onNodeWithText("캐릭터 정보를 불러오지 못했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.LoadoutUpdateFailed)
        }
        composeRule.onNodeWithText("캐릭터 외형을 변경하지 못했습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.NothingToReset)
        }
        composeRule.onNodeWithText("초기화할 배분 능력치 포인트가 없습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.ResetUnavailable)
        }
        composeRule.onNodeWithText("현재 능력치를 초기화할 수 없습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = populatedState(error = CharacterUiMessage.PendingStatAllocation)
        }
        composeRule.onNodeWithText(
            "저장 전 배분은 능력치 배분 저장 또는 - 버튼으로 정리한 뒤 초기화할 수 있습니다.",
        ).assertIsDisplayed()
    }

    @Test
    fun freeResetButtonAndDialogRenderInKorean() {
        composeRule.setContent {
            TodoQuestTheme {
                CharacterContent(
                    state = populatedState(
                        isResetFree = true,
                        resetConfirmation = ResetConfirmationUiState(
                            isFree = true,
                            costGold = 0L,
                        ),
                    ),
                    onIncreaseStat = {},
                    onDecreaseStat = {},
                    onSaveStatAllocation = {},
                    onRequestStatReset = {},
                    onDismissStatReset = {},
                    onConfirmStatReset = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("능력치 초기화 · 무료").assertExists()
        composeRule.onNodeWithText("기본 능력치 4개를 무료로 초기화할까요?")
            .assertIsDisplayed()
    }

    private fun populatedState(
        isMaxLevel: Boolean = false,
        totalXp: Long = 1_145L,
        unspentPoints: Int = 3,
        strength: Int = 12,
        pendingVitality: Int = 0,
        isSavingStatAllocation: Boolean = false,
        resetConfirmation: ResetConfirmationUiState? = null,
        isResetFree: Boolean = false,
        resetUnavailableReason: CharacterUiMessage? = null,
        error: CharacterUiMessage? = null,
    ): CharacterUiState = CharacterUiState(
        isLoading = false,
        level = if (isMaxLevel) 50 else 12,
        isMaxLevel = isMaxLevel,
        totalXp = totalXp,
        xpIntoCurrentLevel = if (isMaxLevel) 100L else 45L,
        xpRequiredForNextLevel = 100L,
        xpProgress = if (isMaxLevel) 1f else 0.45f,
        gold = 620L,
        currentHp = 155,
        maxHp = 210,
        streakDays = 7,
        momentumBonus = "5.0%",
        remainingUnspentPoints = unspentPoints,
        pendingStatPoints = pendingVitality,
        hasPendingStatAllocation = pendingVitality > 0,
        isSavingStatAllocation = isSavingStatAllocation,
        baseStats = listOf(
            BaseStatUiState(
                type = StatType.STRENGTH,
                confirmedValue = strength,
                pendingIncrease = 0,
                expectedValue = strength,
            ),
            BaseStatUiState(
                type = StatType.VITALITY,
                confirmedValue = 9,
                pendingIncrease = pendingVitality,
                expectedValue = 9 + pendingVitality,
            ),
            BaseStatUiState(
                type = StatType.FOCUS,
                confirmedValue = 8,
                pendingIncrease = 0,
                expectedValue = 8,
            ),
            BaseStatUiState(
                type = StatType.WILLPOWER,
                confirmedValue = 7,
                pendingIncrease = 0,
                expectedValue = 7,
            ),
        ),
        derivedStats = listOf(
            DerivedStatUiState(DerivedStatType.MAX_HP, "210"),
            DerivedStatUiState(DerivedStatType.ATTACK, "88"),
            DerivedStatUiState(DerivedStatType.DEFENSE, "31"),
            DerivedStatUiState(DerivedStatType.CRITICAL_CHANCE, "7.6%"),
            DerivedStatUiState(DerivedStatType.CRITICAL_DAMAGE, "156.5%"),
            DerivedStatUiState(DerivedStatType.STATUS_RESISTANCE, "12.5%"),
            DerivedStatUiState(DerivedStatType.HP_RECOVERY, "14"),
            DerivedStatUiState(DerivedStatType.GOLD_GAIN_BONUS, "0.6%"),
        ),
        isResetFree = isResetFree,
        resetCostGold = 340L,
        canReset = resetUnavailableReason == null &&
            pendingVitality == 0 &&
            !isSavingStatAllocation,
        resetUnavailableReason = resetUnavailableReason,
        resetConfirmation = resetConfirmation,
        error = error,
    )
}
