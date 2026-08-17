# Step 5: render-battle-hud-and-effects

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/battle/README.md`
- `/docs/art/monster/README.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt`
- `/app/src/main/java/com/todoquest/ui/character/LayeredCharacterSprite.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Compose와 pure formatting 테스트를 먼저 수정한다. PlayerProgressHud의 첫 줄은 level, gold group, EXP group을 하나의 Row로 렌더한다. gold group은 decorative `MonetizationOn` icon과 3dp 간격의 amount를 하나의 tagged/semantic group으로 묶는다. 항목은 weight와 `maxLines = 1`, ellipsis를 사용하며 큰 값은 결정적 한국어 `만/억/조/경` 단위로 축약한다. 화면 표시만 축약하고 HUD 전체 TalkBack 설명은 정확한 comma-formatted level·XP·gold 값을 사용한다. 기존 XP progress bar는 한 줄 아래에 둔다.

BattleMap에 height policy와 presentation state 입력을 추가하되 Preview와 독립 component의 기본 높이는 기존 190dp..320dp를 유지한다. unit placement로 계산한 sprite 상단 중앙에 동일 규칙의 player/monster HP bar를 둔다. bar width는 sprite width 비율로 계산해 map 좌우 경계 안에 clamp하고, HUD와 겹치면 HUD 아래 최소 여백을 지킨다. current/max text를 함께 표시하고 fraction과 표시 current HP를 `0..maxHp`로 clamp한다. target fraction과 숫자는 220ms tween으로 줄이며 25% 이하이면 Material error color와 low-health semantics를 사용한다.

presentation phase별 effect layer를 기존 map clip 안에 합성한다. player attack은 +X 12dp, monster attack은 -X 12dp 전진한다. hit actor에는 짧은 좌우 shake와 offscreen compositing 기반 white/red flash를 적용하고 `FlashOn` icon과 damage text를 target 위치에 표시한다. death는 확대하지 않고 flash, 아래 방향 translation, alpha fade와 `HeartBroken` KO 표시를 사용한다. spawn alert는 빈 monster slot 중앙의 `Warning`과 `새로운 몬스터 등장!`, spawn은 새 monster만 fade-in한다. pixel sprite draw는 `FilterQuality.None`과 기존 integer scale을 유지한다.

공격자와 피격자, damage, death, spawn announcement에는 한국어 string resource와 polite live region semantics를 사용한다. 배경·장식·순수 flash는 decorative다. 상태 layer와 effect가 HUD, calendar 또는 status bar 밖으로 넘어가지 않게 map shape에서 clip한다. phase별 Preview를 추가한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --tests "com.todoquest.feature.battle.BattleMapLayoutTest" --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest"
.\gradlew.bat lintDebug assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. HUD row/gold gap, HP bounds·clamp·low state와 phase별 effect test를 먼저 작성한다.
2. 320dp, fontScale 2.0, standard/compact map에서 HUD·HP·actor가 겹치거나 잘리지 않는지 확인한다.
3. task index의 step 5를 `completed`로 바꾸고 HUD·HP·effect 구현과 connected 검증을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- sprite bitmap을 smooth filter로 확대하거나 animation용 bitmap을 새로 만들지 마라. 이유: 기존 pixel-art 계약을 유지해야 한다.
- 임의 stage color를 하드코딩하지 마라. 이유: Material 3 theme token을 우선해야 한다.
- effect를 Battle Map clip 밖이나 Calendar 위 zIndex overlay로 그리지 마라. 이유: 고정 map 영역 경계를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
