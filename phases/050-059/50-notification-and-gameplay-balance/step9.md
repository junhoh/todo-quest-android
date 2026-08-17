# Step 9: expand-battle-exp-hud

## 읽어야 할 파일

- /AGENTS.md
- /docs/UI_GUIDE.md
- /app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMap.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt
- /app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt
- /app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step0.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

Compose connected test를 먼저 보강해 현재 IntrinsicSize.Min이 EXP group을 0/1...로 줄이는 회귀를 재현한다. EXP group은 최소 104dp를 확보하고 full EXP label/value의 max intrinsic width와 사용 가능한 남은 폭을 우선하도록 수정한다. bar는 group 전체가 아니라 EXP label 왼쪽부터 value 오른쪽까지의 content 경계를 유지하되 실제 0/100이 ellipsis 없이 보이고 최소 읽기 폭을 가져야 한다.

level은 왼쪽, gold와 EXP summary는 오른쪽에 유지하고 gold/EXP 12dp, icon/value와 label/value 3dp 간격을 변경하지 않는다. compact number formatting, 정확한 TalkBack 원수치, 0 progress track·outline과 requiredExp 경계도 유지한다. STANDARD/COMPACT map 높이, HUD padding, actor/HP placement와 background asset은 수정하지 않는다.

테스트는 portrait 0/100 full text와 bar width >=104dp, 실제 level 1/gold 0, 긴 compact values, 320dp/font scale 2.0, standard/compact map bounds와 full Korean content description을 포함한다. Preview sample도 잘림을 시각적으로 확인할 수 있는 현실적인 값으로 둔다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
git diff --check
~~~

## 검증 절차

1. EXP full text와 최소 bar width assertion을 먼저 추가해 실패를 확인한다.
2. PlayerProgressHud layout만 최소 수정한다.
3. AC와 portrait 시각 확인을 수행한다.
4. step 9를 completed와 한국어 summary로 갱신한다.

## 금지사항

- EXP 값을 숨기거나 marquee로 우회하지 마라. 이유: 현재/필요 수치를 즉시 읽을 수 있어야 한다.
- Battle Map 높이나 actor 좌표를 늘리지 마라. 이유: Calendar scroll viewport와 전투 배치를 보존해야 한다.
- TalkBack에 compact 축약값만 제공하지 마라. 이유: 접근성 설명은 정확한 원수치를 유지해야 한다.
- 새 bitmap이나 sound asset을 추가하지 마라. 이유: 이번 범위는 HUD layout 수정이다.
- 기존 테스트를 깨뜨리지 마라.
