# Step 2: Calendar HUD 통합과 Sunday-first 달력 정렬

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- `/phases/030-039/30-calendar-battle-progress-hud/index.json`

## 작업

Calendar Compose test를 먼저 갱신한 뒤 기존 정보 Header를 제거하고 HUD와 Sunday-first 달력을 통합한다.

- `CalendarContent`의 `calendar-summary` item과 `Header` Composable 전체를 제거한다. 해당 날짜·level·누적 XP·gold 텍스트, test tag, 배경, padding, 구분선 또는 고정 공간을 남기지 않는다.
- `BattleMap` 호출에서 기존 `overlayContent` 슬롯으로 `PlayerProgressHud`를 전달하고 `CalendarCharacterSummary`의 로딩·레벨·현재 구간 XP·필요 XP·골드를 단방향으로 연결한다.
- HUD는 `BattleMap` 내부 상단 중앙에 정렬하고 map의 둥근 모서리 안쪽으로 좌우·상단 8~10dp padding을 둔다. 기존 Scaffold inset, map clip, overlay z-order를 유지한다.
- HUD가 표준 및 320dp map에서 player·monster sprite를 과도하게 가리지 않는지 bounds test로 확인한다. 현재 actor의 하단 배치가 충분한 경우 좌표나 map 높이를 변경하지 않는다.
- 화면 순서를 `Battle Map(HUD 포함) → 월간 캘린더 → 일정 목록`으로 만들고 단일 `task-lazy-list` LazyColumn과 기존 14dp item spacing을 유지한다. 삭제된 Header를 대신하는 Spacer를 만들지 않는다.
- 요일 머리글을 `일`, `월`, `화`, `수`, `목`, `금`, `토` 순서로 바꾸고 `YearMonth.calendarCells()`의 leading empty cell 계산도 Sunday-first 기준으로 맞춘다. 월 제목, 이전/다음 달 버튼, 선택 강조와 날짜별 접근성 설명은 유지한다.
- 사용자 결정에 따라 캘린더 아래 `TaskListHeader`의 기존 선택 날짜는 일정 문맥용으로 유지한다. 제거한 상단 요약 날짜를 다른 위치에 새로 만들지는 않는다.
- Test는 `calendar-summary`와 `calendar-selected-date` tag 부재, HUD semantics, map→month→task 순서, 요일 x 좌표 순서, 월 첫날과 요일 열 정렬, 월 이동·날짜 선택·할 일 조회를 검증한다.
- 기존 `addButtonFromMainActivityOpensKoreanTaskEditor`를 유지하고 레이아웃 변경 뒤 실제 연결 emulator에서 추가 버튼이 `task-editor-dialog`를 여는지 재검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarDayIndicatorTest"
git diff --check
```

## 검증 절차

1. 변경된 UI test의 실패를 먼저 확인하고 Calendar UI를 구현한다.
2. 320dp 단일 scroll에서 월 탐색, 날짜 선택, add/edit/delete/complete/undo와 모달이 모두 접근 가능한지 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 Header 제거, HUD 연결과 요일 정렬을 한국어 `summary`로 기록한다.

## 금지사항

- 선택 날짜 state나 occurrence 조회 기준 날짜를 제거하지 마라. 이유: 제거 대상은 상단 중복 표시이며 캘린더 선택과 할 일 조회는 유지해야 한다.
- 일정 목록 Header의 기존 문맥용 선택 날짜를 제거하지 마라. 이유: 사용자가 상단 요약 Header만 제거하도록 범위를 확정했다.
- 두 번째 vertical scroll container를 추가하지 마라. 이유: 작은 화면의 측정과 접근성 scroll을 깨뜨릴 수 있다.
- 기존 테스트를 깨뜨리지 마라.
