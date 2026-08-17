# Step 6: align-calendar-with-combat-rewards

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/CompletionResult.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Calendar ViewModel과 Compose test를 먼저 수정해 신규 `COMBAT_ATTACK` 완료가 `+0 XP · +0 골드` Snackbar를 만들지 않고, task card가 폐기된 난이도별 직접 보상을 안내하지 않는 계약을 고정한다.

`CalendarViewModel`은 `CompletionResult.rewardMode`를 확인한다. `TODO_COMPLETION`이면서 실제 award가 양수인 legacy/direct 결과만 기존 `RewardGranted` event를 방출한다. `COMBAT_ATTACK`은 완료 표시만 갱신하고 보상 feedback은 step 5의 `CombatTransition` badge와 Character Flow HUD에 맡긴다. 공격이 PENDING이면 가짜 선지급 문구를 만들지 않는다.

task 상세의 `기본 N XP · N 골드`, `정시 완료 +10% · 일일 제한` 문구를 제거하고 한국어 resource로 `완료 시 몬스터 공격 1회`, `타격 EXP와 처치 추가 EXP·골드는 몬스터 레벨과 등급에 따라 지급됩니다`를 표시한다. 난이도 선택 자체와 일정 생성·수정 schema는 바꾸지 않는다. 기존 legacy `RewardGranted` Snackbar의 600ms·외부 pointer dismiss 계약은 유지한다.

테스트는 COMBAT_ATTACK 완료의 no Snackbar, TODO_COMPLETION legacy 양수 reward의 기존 Snackbar, already rewarded, combat processing failure 뒤 완료 유지·no fake reward, 새 한국어 task 안내와 접근성, Calendar 편집·실패·취소 회귀를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.feature.calendar.CalendarDayIndicatorTest" --console=plain
git diff --check
```

## 검증 절차

1. reward mode별 event와 task 안내 Compose test를 먼저 작성하고 기존 구현에서 실패하는지 확인한다.
2. AC를 실행해 Calendar 완료·실패·월 이동·snackbar dismiss와 고정 Battle Map을 확인한다.
3. ViewModel/Compose가 DAO나 WorkManager를 직접 호출하지 않는지 확인한다.
4. 연결 기기가 없으면 도구를 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 6을 `completed`로 바꾸고 reward mode별 UI feedback 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- COMBAT_ATTACK 완료에 `+0 XP · +0 골드` 또는 예상 reward를 표시하지 마라. 이유: 실제 보상은 attack transaction이 확정한다.
- 기존 Calendar reward Snackbar에 전투 보상을 합치지 마라. 이유: 사용자가 Battle Map 일회성 badge를 선택했다.
- task 난이도 필드를 삭제하거나 migration하지 마라. 이유: 이번 범위는 보상 source 변경이며 일정 schema 변경이 아니다.
- 기존 테스트를 깨뜨리지 마라.
