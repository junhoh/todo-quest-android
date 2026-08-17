# Step 5: refine-exp-hud-and-reward-badge

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Battle feature의 unit·Compose test를 먼저 추가해 짧은 `EXP 0/100`에서 Material progress indicator의 기본 최소 폭이 label/value보다 넓어지는 회귀와 0 진행 track 가시성, reward presentation 부재를 실패로 확인한다.

`PlayerProgressHud`의 EXP group은 label과 value Row를 먼저 측정하고 그 정확한 measured width로 bar를 배치하는 전용 layout을 사용한다. bar의 왼쪽은 `EXP` label node 왼쪽, 오른쪽은 current/required value node 오른쪽과 0.6px 이내로 같아야 한다. Material `LinearProgressIndicator`의 기본 240dp 최소 폭에 의존하지 않는다. gold·EXP 사이 12dp, label/value 사이 3dp와 우측 summary 정렬은 유지한다.

bar는 6dp 높이의 theme track과 1dp `outline` 경계를 항상 그리고, progress `0f`에서는 fill 없이 track/outline만, `1f`에서는 `secondary` fill을 표시한다. progress semantics와 기존 clamp, compact 시각 숫자·정확한 TalkBack 숫자를 유지한다. 색상을 하드코딩하지 않는다.

`BattlePresentationState`에 nullable reward feedback payload를 추가한다. player attack version 1만 actual snapshot의 total XP와 gold를 매핑한다. nonlethal은 `+N EXP`, lethal은 hit+kill total을 합친 `+N EXP · +G 골드`를 하나의 고대비 badge와 polite live region으로 표시한다. effect는 `sequenceId`로 한 번만 시작해 600ms 유지하고 combat phase delay·input lock·queue 시간을 늘리지 않으며 다음 transition, recomposition, process 재시작에서 replay하지 않는다. monster attack과 legacy version 0에는 badge를 표시하지 않는다.

reward badge는 기존 map overlay와 effect placement를 사용해 standard/compact map, 320dp·font scale 2.0에서 bounds 안에 남는다. 새 bitmap·sound asset을 추가하지 않고 사용자 문구는 `strings.xml`에 둔다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
git diff --check
```

## 검증 절차

1. short/long EXP bounds, 0/100 track pixel contrast와 reward badge test를 production UI보다 먼저 작성한다.
2. AC를 실행해 0·중간·완료 progress, 큰 숫자/font scale과 normal/lethal/legacy/monster transition을 확인한다.
3. exact accessibility 값과 한국어 live region, replay 없는 event key 계약을 검토한다.
4. 연결 기기가 없으면 도구를 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 5를 `completed`로 바꾸고 EXP 측정 layout과 600ms badge를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- progress bar를 level·gold 또는 EXP group의 여유 영역까지 늘리지 마라. 이유: 실제 EXP label/value 글자 폭만 사용해야 한다.
- 0 progress를 투명하거나 배경과 동일하게 만들지 마라. 이유: 사용자가 `0/100`에서도 bar 경계를 구분해야 한다.
- reward badge를 새 animation phase나 input lock으로 구현하지 마라. 이유: 전투 queue 지연 없이 일회성 시각 feedback만 추가한다.
- 기존 테스트를 깨뜨리지 마라.
