# Step 1: Battle Map 플레이어 진행 HUD 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/030-039/30-calendar-battle-progress-hud/index.json`

## 작업

순수 진행률 테스트를 먼저 작성한 뒤 `feature/battle`에 ViewModel 없이 렌더링 가능한 독립 HUD를 구현한다.

- 다음 시그니처 수준의 internal Composable을 추가한다: `PlayerProgressHud(isLoading: Boolean, level: Int, currentExp: Long, requiredExp: Long, gold: Long, modifier: Modifier = Modifier)`.
- 진행률 계산 함수는 `requiredExp > 0`일 때만 나눗셈하고 결과를 `0f..1f`로 제한한다. 현재 XP가 음수면 0 진행률, 필요 XP가 0 또는 음수면 0 진행률, 현재 XP가 필요 XP를 넘으면 1 진행률이어야 한다.
- HUD는 상단 행에 `Lv.`와 골드 아이콘·천 단위 구분 골드, 하단 영역에 `EXP`, 현재/필요 XP 문자열과 `LinearProgressIndicator`를 둔다. 320dp 폭과 확대 글꼴에서도 값이 겹치지 않도록 경험치 영역은 라벨/값 행과 얇은 bar로 구성할 수 있다.
- Material theme의 `surface` 반투명 배경, `outline` 테두리, `onSurface` 기본 텍스트, `secondary` 경험치, `primary` 골드만 사용한다. 둥근 모서리와 최소 내부 padding을 적용하고 새 ARGB 색상 토큰은 만들지 않는다.
- 골드 아이콘은 장식으로 `contentDescription = null` 처리하고 HUD 전체에 통합 semantics를 제공한다. 정상 상태는 `레벨 1, 경험치 40/100, 골드 120`, 로딩 상태는 플레이어 정보를 불러오는 중이라는 한국어 설명을 제공한다.
- 로딩 중에는 임의 레벨·XP·골드를 표시하지 않는다. 정상 상태의 0, 초과 XP, 잘못된 필요 XP와 큰 골드는 안전하게 표시한다.
- 기존 `BattleMap.overlayContent` API를 유지한다. Preview sample에서 이 슬롯으로 HUD를 합성하고 일반 폰, 320dp 소형 폰, 가로 화면, 밝은 초원, dark mode/어두운 preview scrim, 몬스터 0·1·3마리, 큰 XP·골드와 로딩 상태를 포함한다. 앱은 현재 dark theme만 지원하므로 별도 light theme Preview는 추가하지 않는다.
- `BattleMapTest` 또는 별도 HUD Compose test에서 layer 독립성, 통합 semantics, 작은 폭의 텍스트 비중첩과 HUD가 맵 bounds 안에 있음을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.battle.*"
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest"
git diff --check
```

## 검증 절차

1. 진행률과 HUD Compose test를 먼저 실패시키고 구현 후 모든 AC를 통과시킨다.
2. HUD가 background PNG나 sprite에 합성되지 않고 `BattleOverlayLayer`의 상단 Compose layer인지 확인한다.
3. task index의 step 1을 `completed`로 바꾸고 새 HUD, Preview와 테스트를 한국어 `summary`로 기록한다.

## 금지사항

- HUD를 배경 이미지 또는 캐릭터 sprite에 합성하지 마라. 이유: 맵, 전투 개체, 효과와 HUD를 독립 교체해야 한다.
- HUD 색상을 초원 배경 전용 ARGB 값으로 하드코딩하지 마라. 이유: 밝은 설원과 어두운 던전에서도 같은 의미와 대비를 유지해야 한다.
- Battle Map의 높이를 HUD 때문에 불필요하게 늘리지 마라. 이유: Calendar의 세로 공간 효율을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
