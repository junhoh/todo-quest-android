# Step 6: integrate-calendar-failure-layout

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/battle/`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Calendar ViewModel과 Compose 테스트를 먼저 작성한다. ViewModel은 application-scope CombatRepository events를 한 번 collect해 BattleAnimationController에 전달한다. occurrence별 processing key set을 command 시작 전에 동기적으로 설정해 연속 complete/fail/undo 요청을 막고 finally에서 해제한다. battle queue가 non-empty이거나 phase가 IDLE이 아니면 모든 occurrence outcome button을 전역 비활성화한다. task 편집·삭제와 calendar navigation은 combat animation 실패 때문에 막지 않는다.

`failOccurrence`, `undoFailOccurrence` action을 ViewModel에 추가한다. 실패 저장 성공 뒤 combat만 실패한 경우 FAILED 상태는 유지하고 한국어 retry 안내 또는 기존 diagnostic 정책으로 분리하며, cancellation은 전파한다. reward snackbar는 기존 최초 completion에서만 유지한다. CalendarUiState는 battle presentation, processing keys와 `isBattleInputLocked`를 제공하고 day summary는 total/completed/failed를 구분한다.

CalendarContent를 고정 상단 Column과 그 아래 `Modifier.weight(1f)` LazyColumn으로 바꾼다. BattleMap은 LazyColumn item이 아니며 horizontal 16dp, top 14dp와 14dp 아래 간격을 유지한다. LazyColumn에는 month grid, task header/message, add button, tasks/empty state만 넣고 bottom content padding을 유지한다. viewport 높이가 520dp 미만이면 map height policy를 150dp..190dp compact로 전달하고, 그 외에는 기존 190dp..320dp를 사용한다.

TODO TaskRow에는 48dp 이상 touch target의 complete와 fail Button을 같은 Row에 weight로 배치한다. Check/primary와 Close 또는 Dangerous/error를 사용하고 긴 title/큰 font에서도 label을 한 줄 ellipsis 처리한다. command 처리 중에는 두 버튼을 함께 disable하고 progress를 표시한다. COMPLETED는 완료 label과 완료 취소, FAILED는 error label·icon·dimmed title/memo와 실패 취소를 표시하며 terminal 상태에는 complete/fail을 보이지 않는다. 실패 취소 button도 전투 animation 중에는 disable한다.

선택 날짜 header의 기존 `완료/전체` 의미는 유지하고 실패가 있으면 별도 `실패 N` 표시를 추가한다. day cell semantics에도 실패 수를 추가하되 완료 count에 합산하지 않는다. 모든 새 text/contentDescription/error는 `values/strings.xml`의 한국어 resource를 사용한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarDayIndicatorTest,com.todoquest.feature.calendar.CalendarScreenTest"
.\gradlew.bat lintDebug assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 중복 click, processing disable, 실패/취소, battle lock과 summary test를 먼저 작성한다.
2. swipe 전후 battle-map top이 동일하고 month/task bounds만 이동하며 battle-map이 LazyColumn scroll node가 아닌지 확인한다.
3. task index의 step 6을 `completed`로 바꾸고 Calendar 고정 layout과 실패 action 연동을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Calendar Composable에서 DAO, Room, WorkManager 또는 CombatRepository command를 직접 호출하지 마라. 이유: ViewModel→UseCase/Repository 경계를 지켜야 한다.
- Battle Map을 LazyColumn stickyHeader나 높은 zIndex 겹침으로 구현하지 마라. 이유: 별도 고정 layout이어야 한다.
- FAILED를 completed count에 포함하지 마라. 이유: 기존 완료 지표 의미를 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
