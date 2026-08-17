# Step 3: Battle Map Compose 레이어와 Preview 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/battle/README.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/res/drawable-nodpi/battle_map_grassland.png`
- `/app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png`
- `/app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

Compose UI test를 먼저 작성한 뒤 reusable `BattleMap`을 역할별 작은 Composable로 구현한다.

- 공개 entry는 `@Composable fun BattleMap(state: BattleMapUiState, modifier: Modifier = Modifier, theme: BattleMapTheme = ..., overlayContent: @Composable BoxScope.() -> Unit = {})` 또는 동등한 최소 API로 둔다.
- 내부를 `BattleBackground`, `BattleDecorations`, `BattleGroundLayer`, `PlayerLayer`, `MonsterLayer`, `BattleOverlayLayer`로 분리한다. 함수 호출만 분리하고 player/monster node는 같은 Box의 direct child가 되게 하여 normalized y 기반 `zIndex`가 unit type 사이에서도 적용되게 한다.
- 컨테이너는 전체 가용 너비를 사용하고 높이는 `(maxWidth / 2.4f).coerceIn(190.dp, 320.dp)`로 계산한다. `MaterialTheme.shapes.large`, 1dp outline, 약한 shadow와 clip을 사용한다.
- PNG background는 `ContentScale.Crop`으로 표시한다. resource decode를 `runCatching` 또는 동등하게 안전 처리하고 실패하면 Canvas로 옅은 하늘, 산·숲 실루엣, 잔디·흙길, 평평한 전투 지면, 작은 돌·풀을 그린다. fallback도 단색 한 장으로 끝내지 않는다.
- decorations는 safe zone의 큰 물체를 피하고 theme에 따라 끌 수 있는 별도 Canvas layer로 둔다.
- 각 ground anchor 아래에 별도 Canvas layer의 반투명 타원 shadow를 그린다. shadow는 sprite PNG에 합성하지 않는다.
- player와 monster sprite는 `ContentScale.Fit`과 source frame을 사용하며 원본 비율을 유지한다. map 높이에 비례한 base unit height를 `72dp..112dp`로 제한하고 unit `scale`을 적용한다.
- `BattleMapLayout` 결과로 offset하고 경계를 clamp한다. unit은 y가 클수록 높은 z-order다.
- Loading은 배경과 작은 progress indicator/TalkBack 한국어 설명을, Unavailable은 배경 fallback과 방해가 적은 한국어 상태 설명을 표시한다. 흰색 불투명 overlay를 사용하지 않는다.
- 새 사용자 문구와 player/monster name·HP TalkBack 설명은 모두 `strings.xml`에 둔다. 장식 배경은 decorative로 처리한다.
- HP와 stage는 state에 보존하되 health bar, name label, damage, attack effect, stage HUD를 렌더링하지 않는다. 빈 `BattleOverlayLayer`와 slot만 둔다.
- `BattleMapPreview.kt`에 ViewModel/database 없는 sample로 다음 Preview를 작성한다: player+1 monster, player+3 monsters, no monster, 320dp small phone, 일반 phone, dark mode. 현재 app theme가 dark-only임을 유지한다.
- instrumentation Compose test는 layer tag, fallback invalid resource, 0/1/3/4 monster count, bounds, loading/unavailable semantics를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug assembleDebug
git diff --check
```

## 검증 절차

1. 새 Compose test를 먼저 작성하고 compile failure 또는 assertion failure를 확인한 뒤 구현한다.
2. Preview sample이 ViewModel, Repository, Room을 참조하지 않는지 확인한다.
3. task index의 step 3을 `completed`로 바꾸고 레이어·fallback·responsive sizing 결과를 한국어 `summary`로 기록한다.

## 금지사항

- 하나의 거대한 Composable에 모든 drawing과 unit 배치를 작성하지 마라. 이유: 후속 레이어 교체와 애니메이션 확장이 어려워진다.
- background PNG에 unit shadow나 UI를 합성하지 마라. 이유: 배경과 전투 개체를 독립 교체해야 한다.
- 표시용 영문 문장을 하드코딩하지 마라. 이유: AGENTS 한국어 기본 리소스 규칙이다.
- Room DAO, WorkManager 또는 AlarmManager를 Composable에서 호출하지 마라. 이유: UI 레이어 경계를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
