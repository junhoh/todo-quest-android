# Step 0: polish-battle-feedback-visibility

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleMapLayoutTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/step5.md`
- `/phases/030-039/36-calendar-battle-feedback-polish/index.json`

## 작업

`feature/battle`의 Compose 테스트를 먼저 추가하고 실패를 확인한 뒤 Battle Map HUD와 transient effect의 가시성을 개선한다. `BattleMap`, `PlayerProgressHud`, `BattlePresentationState`의 기존 외부 시그니처와 phase timeline은 유지한다.

`PlayerProgressHud`는 level, gold, EXP를 기존 한 Row에 유지하되 EXP label과 current/required 값 사이의 가중치 공백을 제거하고 고정 3dp 간격만 둔다. EXP label과 값을 감싼 전용 content-width 영역을 만들고 progress bar의 좌우 경계를 이 영역과 일치시킨다. progress bar가 level 또는 gold 영역 아래까지 확장되면 안 된다. 기존 compact number, 정확한 한국어 TalkBack 설명, 진행률 clamp와 loading 상태는 유지한다. 320dp 폭과 font scale 2.0에서 level → gold → EXP 순서가 겹치지 않고 모든 요소가 map 안에 있어야 한다.

player와 monster 체력 수치는 11sp font size, 13sp line height와 semi-bold weight를 사용한다. 체력 숫자와 5dp progress bar를 최소 94% 불투명한 Material `surface` 배경과 1dp `outline` 테두리의 작은 패널 안에 둔다. player fill은 `secondary`, monster fill은 `error`, 저체력 player도 `error`를 사용하며 숫자는 `onSurface`로 표시한다. 기존 current/max clamp, 220ms tween, 저체력 semantics와 actor geometry 기반 배치를 유지하고 standard·compact map에서 HUD나 actor와 겹치지 않게 패널 높이를 placement 계산에 반영한다.

공격 phase에는 attacker/target geometry 안에서 map 경계를 지키는 고대비 `공격!` badge를 표시한다. badge는 최소 94% 불투명한 `surface`, 1dp accent 테두리, 22dp Material icon과 16sp bold 한국어 text를 사용하고 player attack은 `secondary`, monster attack은 `error` accent를 사용한다. hit badge는 같은 고대비 패널에 22dp icon과 18sp bold `-%d` damage text를 표시한다. 기존 attack translation, target shake·flash, death·spawn/revive 순서, z-index, clip, polite live region과 한국어 content description을 유지한다. 필요한 새 사용자 문구는 `strings.xml`에 한국어 resource로 추가한다.

Compose instrumentation test는 EXP label/value의 고정 간격, EXP row와 progress bar의 좌우 bounds 일치, progress bar와 gold bounds 비중첩, 커진 HP panel의 map/HUD/actor bounds, player/monster 공격 badge와 player/monster hit badge의 표시 text·semantics·map bounds를 검증한다. 색상만으로 정보를 전달하지 않으며 기존 phase announcement와 damage 숫자를 함께 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --tests "com.todoquest.feature.battle.BattleMapLayoutTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. EXP geometry, 체력 panel과 attack/hit badge의 Compose test를 먼저 작성하고 기존 구현에서 실패하는지 확인한다.
2. AC 명령을 실행하고 320dp·font scale 2.0, standard·compact map, player/monster 양방향 phase를 확인한다.
3. AGENTS.md의 한국어 resource, UI 레이어 경계와 기존 전투 transition 비영속 규칙을 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 수정 파일과 HUD·체력·effect 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 새 bitmap 또는 sound asset을 추가하지 마라. 이유: 기존 Material icon과 독립 Compose effect layer만으로 가시성을 개선하는 범위다.
- `BattleAnimationTimeline`이나 combat event 순서를 바꾸지 마라. 이유: 이번 작업은 표시 가시성 개선이며 직렬·replay 없는 transition 계약을 보존해야 한다.
- 체력이나 damage 정보를 색상만으로 전달하지 마라. 이유: 숫자·한국어 semantics와 함께 제공해야 한다.
- progress bar를 level 또는 gold 영역 아래까지 확장하지 마라. 이유: EXP label/value 길이만큼만 표시한다는 사용자 요구를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
