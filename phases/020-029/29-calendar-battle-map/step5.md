# Step 5: Calendar 최상단 배치와 단일 스크롤 통합

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

Calendar Compose test를 먼저 갱신한 뒤 기존 fixed Column+nested task LazyColumn을 화면 전체 단일 scroll 구조로 바꾼다.

- `CalendarContent`의 시스템 inset 다음 첫 content item은 `BattleMap`이어야 한다.
- Battle Map 아래에는 기존 Header에서 `Todo Quest` 앱명만 제거한 날짜·level·XP·gold summary row를 둔다. 표시 정보와 한국어 resource는 유지한다.
- 이후 `MonthGrid`, 선택 날짜의 task header/message/empty state/task rows 순서를 유지한다.
- 하나의 `LazyColumn`이 Battle Map부터 모든 task row까지 소유하게 하고 nested vertical LazyColumn을 제거한다. 긴 task 목록을 eager `Column` 한 item으로 만들지 않는다.
- 기존 task scroll test가 사용할 수 있도록 outer list에 `task-lazy-list` tag를 유지하거나 모든 관련 test를 일관된 새 단일 scroll tag로 갱신한다.
- outer content padding은 기존 horizontal 16dp와 유사한 간격을 유지하고 Battle Map도 그 가용 너비 전체를 쓴다. bottom navigation padding과 `systemBarsPadding`이 중복되거나 map과 status bar가 겹치지 않게 한다.
- 작은 화면에서도 month navigation, 선택 날짜, add/edit/delete/complete/undo와 task row를 `performScrollToNode`로 접근할 수 있어야 한다.
- `CalendarScreenTest`와 `CalendarDayIndicatorTest`에 Battle Map이 summary/month/task보다 위에 있음, `Todo Quest` text 부재, calendar/task reachability, 기존 CRUD·완료 회귀를 검증하는 assertion을 먼저 추가한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug assembleDebug
git diff --check
```

## 검증 절차

1. 갱신한 UI test의 실패 또는 compile failure를 먼저 확인하고 구현한다.
2. Calendar와 task list가 한 scroll owner만 갖는지 코드와 semantics tag로 확인한다.
3. task index의 step 5를 `completed`로 바꾸고 화면 순서·scroll·Header 변경을 한국어 `summary`로 기록한다.

## 금지사항

- Battle Map 아래에 다시 독립 vertical task LazyColumn을 중첩하지 마라. 이유: 작은 화면의 측정·scroll 충돌을 막아야 한다.
- `Todo Quest` 앱명을 다른 위치에 다시 하드코딩하지 마라. 이유: 사용자가 Header 앱명 제거를 선택했다.
- 월간 캘린더 셀 비율과 occurrence 완료 동작을 변경하지 마라. 이유: 이번 변경은 상단 Battle Map과 scroll 통합 범위다.
- 기존 테스트를 깨뜨리지 마라.
