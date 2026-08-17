# Step 7: align-exp-hud-labels

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/app/src/main/res/values/strings.xml`
- `/phases/030-039/30-calendar-battle-progress-hud/step3.md`
- `/phases/050-059/50-notification-and-gameplay-balance/step9.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step6.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

HUD Compose test를 production보다 먼저 작성하고 Battle presentation module만 수정한다.

`PlayerProgressHud`의 EXP group은 기존 `IntrinsicSize.Max`와 최소 104dp, compact Korean number formatting, progress bar 너비를 유지한다. label/value Row의 전체 너비를 progress bar와 동일하게 하고 `EXP` label의 왼쪽 edge를 bar 왼쪽 edge에, `현재/필요` value의 오른쪽 edge를 bar 오른쪽 edge에 맞춘다. 두 값 사이 고정 3dp 인접 배치를 제거하고 남는 공간을 두 요소 사이에 둔다.

level과 gold group, summary의 오른쪽 정렬, HUD horizontal padding, map 안쪽 8dp, theme 색, 4dp bar와 progress clamp는 변경하지 않는다. 현재/필요 값이 커지면 기존 compact unit을 사용하고 label/value가 겹치거나 HUD/map 경계를 벗어나지 않아야 한다. font scale 2.0, `0/100`, 큰 Long 값과 compact-height map을 검증한다.

merged TalkBack 설명 `레벨 N, 경험치 current/required, 골드 N`, EXP progress semantics, test tags는 유지한다. 새 사용자 문구가 필요하지 않으면 resource를 추가하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
git diff --check
~~~

## 검증 절차

1. label/bar left와 value/bar right geometry test를 먼저 실패시킨다.
2. 최소 layout 변경으로 정렬하고 기존 loading/value semantics를 유지한다.
3. 0·큰 값·font scale·compact map fixture를 포함해 AC를 실행한다.
4. Android 도구/기기가 없으면 설치하지 말고 blocked, 성공 시 step 7을 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- EXP bar 최소 폭을 줄이지 마라. 이유: 0/100과 compact 수치가 잘리지 않아야 한다.
- level·gold 위치나 Battle actor geometry를 재설계하지 마라. 이유: 요청 범위는 EXP label/value 정렬이다.
- 접근성 설명에 compact 축약값만 노출하지 마라. 이유: TalkBack은 정확한 숫자를 읽어야 한다.
- 배경 PNG에 HUD를 합성하지 마라. 이유: 기존 독립 overlay 계약을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
