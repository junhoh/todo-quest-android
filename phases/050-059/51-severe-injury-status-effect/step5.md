# Step 5: render-severe-injury-ui

## 읽어야 할 파일

- /AGENTS.md
- /docs/UI_GUIDE.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step4.md
- /app/src/main/res/values/strings.xml
- /app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMap.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt
- /app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt
- /app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt
- /app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt
- /app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt

## 작업

테스트를 먼저 작성하고 캐릭터 정보와 캘린더 전투 맵에 접근 가능한 중상 표시 및 상세 정보를 구현한다.

1. 플레이어 체력바/정보 근처와 BattleMap 플레이어 근처에 Material의 붕대/부상 의미 아이콘과 중상 텍스트를 함께 표시한다. 새 bitmap을 만들지 말고 프로젝트의 Material icon 의존성/스타일을 재사용한다.
2. BattleMap의 중상 badge는 플레이어 HP 아래 또는 sprite 위의 전용 geometry를 사용해 HUD, 몬스터, 전투 효과와 겹치지 않게 한다. 밝고 어두운 전투맵 모두에서 식별되도록 container/outline 대비를 제공하고 작은 화면 및 font scale 테스트를 보강한다.
3. badge 선택 시 ViewModel state를 사용하는 상세 dialog 또는 popup을 연다. 설명은 문자열 리소스에 다음 그대로 둔다: 전투에서 입은 심각한 부상으로 최대 체력과 공격력이 감소합니다.\n할 일을 완료하거나 충분히 휴식하면 회복됩니다. 효과는 최대 체력 -20%, 공격력 -20%, 회복 조건은 회복까지 할 일 N개 또는 H시간 형식이다.
4. badge 전체 semantics를 병합해 예를 들어 플레이어 상태 중상, 최대 체력과 공격력 20퍼센트 감소, 회복까지 할 일 2개로 읽히게 한다. 장식 아이콘은 TalkBack에서 중복 읽지 않게 하고 색상만으로 상태를 구분하지 않는다.
5. 패배 연출은 기존 쓰러짐/흐려짐을 재사용하되 0/최대 체력 → 전투 불능 → 중상 적용/갱신 → 응급 회복 HP → 전투 가능 순서로 보인다. 부활 문구, 부활 아이콘, 완전 회복 연출을 문자열·Preview·테스트에서 제거한다.
6. 중상 제거 event를 받으면 짧은 효과와 중상 회복 메시지를 한 번 표시한다. 애니메이션 제거 설정에서도 모든 단계의 정적 아이콘과 텍스트가 확인되어야 한다.
7. 모든 새 사용자 노출 문구는 strings.xml의 한국어 리소스로 두고 Compose/ViewModel에 표시 문자열을 하드코딩하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.character.CharacterScreenTest,com.todoquest.feature.battle.BattleMapTest,com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.app.AppNavigationTest" --console=plain
git diff --check
~~~

## 검증 절차

1. Compose 테스트에서 icon+중상 텍스트, 병합 semantics, 상세 정보, 남은 개수/시간 표시를 먼저 추가한다.
2. BattleMap geometry 테스트로 badge가 player HP, sprite, 기존 effect/HUD bounds와 교차하지 않는지 확인한다.
3. 패배 event를 재전달하거나 화면을 재생성해도 전투 불능, 응급 회복, 중상 회복 메시지가 중복 표시되지 않는지 검증한다.
4. rg -n "부활|되살아남|PLAYER_REVIVING|battle_revive" app/src 로 사용자 노출/연출 잔여물을 점검하고 호환성 목적 DB 컬럼 외에는 제거한다.
5. AC를 실행하고 phase index의 step 5를 completed와 한국어 summary로 갱신한다. Android 장치가 실제로 없으면 설치를 시도하지 말고 해당 사실과 미실행 검증을 명확히 기록한다.

## 금지사항

- 아이콘 색상만으로 중상을 표시하지 마라. 이유: 접근성 요구를 충족하지 못한다.
- 장식 아이콘과 badge 텍스트를 TalkBack이 따로 중복 읽게 두지 마라.
- Text("중상")처럼 표시용 한국어 문장을 Kotlin에 직접 하드코딩하지 마라. 이유: 문자열 리소스 규칙을 위반한다.
- 상태 badge를 전투 효과/체력바와 겹치는 임의 offset으로 배치하지 마라.
- 이미지 생성 도구로 새 bitmap을 추가하지 마라. 이유: 기존 Material icon으로 요구사항을 충족할 수 있다.
- Preview나 Compose effect에서 DB 상태를 변경하지 마라.
