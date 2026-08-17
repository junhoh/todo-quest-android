# Step 3: calendar-viewmodel

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/phases/000-009/3-schedule-management/step1.md`
- `/phases/000-009/3-schedule-management/step2.md`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/UndoCompleteOccurrenceUseCase.kt`

## 작업

캘린더 화면이 사용할 ViewModel과 immutable UI state를 테스트 먼저 작성한 뒤 구현한다. Compose UI는 아직 변경하지 않는다.

추가할 파일은 다음과 같다.

- `feature/calendar/CalendarUiState.kt`
- `feature/calendar/CalendarViewModel.kt`

ViewModel 계약은 다음을 포함한다.

- `val uiState: StateFlow<CalendarUiState>`
- `fun selectDate(date: LocalDate)`
- `fun showAddTaskDialog()`
- `fun hideAddTaskDialog()`
- `fun updateNewTaskTitle(title: String)`
- `fun saveNewTask()`
- `fun completeOccurrence(taskId: Long, occurrenceDate: LocalDate)`
- `fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate)`

UI state는 다음을 포함한다.

- `visibleMonth: YearMonth`
- `selectedDate: LocalDate`
- `tasks: List<TaskOccurrence>`
- `monthDaySummaries`: 날짜별 전체/완료 개수
- `profile: CharacterProfile`
- `isAddTaskDialogOpen: Boolean`
- `newTaskTitle: String`
- `errorMessage: String?`

규칙은 다음과 같다.

- ViewModel은 Repository와 UseCase만 호출한다.
- ViewModel은 DAO, Room database, AlarmManager, WorkManager를 직접 알면 안 된다.
- 새 일정의 MVP 기본값은 선택 날짜, `MEDIUM`, `General`, `NONE`, 시간 없음으로 둔다.
- 빈 제목 저장은 무시하고 UI state에 error를 남긴다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. fake repository 기반 ViewModel unit test가 날짜 선택, 일정 생성, 완료 상태, 프로필 갱신을 검증하는지 확인한다.
3. ViewModel이 Room DAO나 Android scheduler를 import하지 않는지 확인한다.
4. Compose 화면 변경이 포함되지 않았는지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`의 step 3 상태와 결과 필드를 업데이트한다.

## 금지사항

- ViewModel에서 Room DAO를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 레이어 규칙을 위반한다.
- ViewModel에서 AlarmManager나 WorkManager를 직접 호출하지 마라. 이유: 알림 예약은 Repository 또는 scheduler abstraction 뒤에서 처리해야 한다.
- Compose Composable을 만들지 마라. 이유: 이 step은 UI state 계약만 다룬다.
- 일정 완료 보상 멱등성을 ViewModel에서 구현하지 마라. 이유: 멱등성은 UseCase/Repository transaction에서 보장해야 한다.
- 기존 테스트를 깨뜨리지 마라.
