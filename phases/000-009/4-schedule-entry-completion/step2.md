# Step 2: calendar-day-multi-task-indicators

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/4-schedule-entry-completion/index.json`
- `/phases/000-009/4-schedule-entry-completion/step0.md`
- `/phases/000-009/4-schedule-entry-completion/step1.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

## 작업

테스트를 먼저 작성한 뒤 특정 날짜에 일정이 여러 개 있을 때 월간 캘린더 셀에서 개수와 완료 상태가 명확히 보이도록 표시를 강화한다. 데이터 계산은 기존 `CalendarDaySummary(totalCount, completedCount)`를 우선 재사용한다.

`DayCell` 표시 규칙을 다음처럼 구현한다.

- 일정이 1개 이상이면 날짜 셀 하단에 전체 일정 개수를 텍스트 또는 badge로 표시한다.
- 완료된 일정이 1개 이상이면 완료 개수를 함께 표시한다.
- 전체/완료 요약은 작은 화면에서도 날짜 숫자를 가리지 않아야 한다.
- TalkBack이 읽을 수 있도록 날짜 셀의 semantics 또는 content description에 날짜, 전체 일정 개수, 완료 개수를 포함한다.
- UI test가 안정적으로 찾을 수 있도록 날짜별 test tag는 기존 `calendar-day-$date`를 유지한다.

추천 표시 문구는 다음과 같다.

- 전체만 있는 경우: `2 tasks`
- 완료가 있는 경우: `1/2 done`

기존 `monthDaySummaries` 계산과 occurrence 완료 멱등성 로직은 변경하지 않는다.

`CalendarScreenTest`는 다음 사용자 흐름을 검증한다.

- 같은 선택 날짜에 일정 2개를 추가하면 해당 날짜 셀에 `2 tasks` 또는 동등한 count 표시가 보인다.
- 하나를 완료하면 해당 날짜 셀에 `1/2 done` 또는 동등한 완료 요약이 보인다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

기기 또는 에뮬레이터가 연결되어 있으면 다음도 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없어 `connectedDebugAndroidTest`를 실행할 수 없으면 step 상태를 `blocked`로 기록하지 말고, unit/lint/build가 통과했다면 완료 summary에 미실행 사유를 남긴다.
3. 날짜 셀에서 게임 장식이 날짜 숫자와 일정 개수 텍스트를 가리지 않는지 확인한다.
4. UI가 `docs/UI_GUIDE.md`의 월간 캘린더, 접근성, 텍스트 overflow 규칙을 따르는지 확인한다.
5. `/phases/000-009/4-schedule-entry-completion/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- `CalendarViewModel.toDaySummaries()`의 의미를 바꾸지 마라. 이유: 완료/전체 개수 계약은 이미 UI state에 존재한다.
- 완료 상태나 보상 지급 로직을 변경하지 마라. 이유: occurrence 단위 멱등 보상 규칙을 훼손할 수 있다.
- 날짜 숫자 위에 장식이나 badge를 겹치지 마라. 이유: 캘린더 가독성을 떨어뜨린다.
- 기존 테스트를 깨뜨리지 마라.
