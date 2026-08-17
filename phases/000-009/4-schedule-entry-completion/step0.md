# Step 0: schedule-entry-viewmodel-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/4-schedule-entry-completion/index.json`
- `/app/src/main/java/com/todoquest/domain/model/CreateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/RecurrenceRule.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskDifficulty.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`

## 작업

테스트를 먼저 작성한 뒤 신규 일정 입력 상태를 제목 전용 상태에서 MVP 입력 필드 전체를 담는 상태로 확장한다. Compose UI는 이 step에서 수정하지 않는다.

`CalendarUiState.kt`에 신규 일정 폼 상태를 표현하는 immutable data class를 추가하거나 동등한 구조로 확장한다.

- 필수 상태: `title: String`, `memo: String`, `timeText: String`, `difficulty: TaskDifficulty`, `category: String`, `recurrenceRule: RecurrenceRule`
- 기본값: 제목/메모/시간은 빈 문자열, 난이도는 `TaskDifficulty.MEDIUM`, 카테고리는 `General`, 반복은 `RecurrenceRule.NONE`
- 기존 `isAddTaskDialogOpen`, `errorMessage`는 유지한다.

`CalendarViewModel`에 다음 입력 이벤트를 추가한다.

- `fun updateNewTaskTitle(title: String)`
- `fun updateNewTaskMemo(memo: String)`
- `fun updateNewTaskTime(timeText: String)`
- `fun updateNewTaskDifficulty(difficulty: TaskDifficulty)`
- `fun updateNewTaskCategory(category: String)`
- `fun updateNewTaskRecurrenceRule(recurrenceRule: RecurrenceRule)`

`saveNewTask()`는 다음 규칙으로 `CreateTaskInput`을 생성한다.

- 제목은 trim 후 비어 있으면 저장하지 않고 `errorMessage`에 사용자에게 보일 오류를 설정한다.
- 시간은 trim 후 비어 있으면 `null`, 값이 있으면 `HH:mm` 형식만 허용해 `LocalTime`으로 변환한다.
- 잘못된 시간은 저장하지 않고 `errorMessage`에 사용자에게 보일 오류를 설정한다.
- 카테고리는 trim 후 비어 있으면 `General`로 저장한다.
- 메모는 trim 해서 저장한다.
- 날짜는 기존처럼 `selectedDate`를 사용한다.
- 저장 성공 시 dialog를 닫고 폼 상태와 오류를 기본값으로 초기화한다.
- 저장 실패 시 dialog는 닫지 말고 오류만 표시한다.

`CalendarViewModelTest`에 fake repository 기반 테스트를 추가한다.

- 제목, 메모, 시간, 난이도, 카테고리, 반복 설정이 `CreateTaskInput`과 생성된 occurrence에 반영된다.
- 빈 시간은 `null`로 저장된다.
- 잘못된 시간 입력은 repository create를 호출하지 않고 오류를 표시한다.
- dialog dismiss와 저장 성공이 폼 상태를 기본값으로 초기화한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `CalendarViewModel`이 Repository와 UseCase만 호출하고 Room DAO, Room database, AlarmManager, WorkManager를 import하지 않는지 확인한다.
3. 일정 완료와 보상 지급 로직이 변경되지 않았는지 확인한다.
4. `/phases/000-009/4-schedule-entry-completion/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- UI Composable을 수정하지 마라. 이유: 이 step은 ViewModel state 계약만 다룬다.
- ViewModel에서 Room DAO를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 레이어 규칙을 위반한다.
- ViewModel에서 AlarmManager나 WorkManager를 직접 호출하지 마라. 이유: 알림 예약은 Repository 또는 scheduler abstraction 뒤에서 처리해야 한다.
- 일정 완료 보상 로직을 변경하지 마라. 이유: occurrence 단위 멱등 보상 규칙을 훼손할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
