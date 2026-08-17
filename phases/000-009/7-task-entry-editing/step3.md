# Step 3: task-editor-viewmodel-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/7-task-entry-editing/index.json`
- `/phases/000-009/7-task-entry-editing/step0.md`
- `/phases/000-009/7-task-entry-editing/step1.md`
- `/phases/000-009/7-task-entry-editing/step2.md`
- `/app/src/main/java/com/todoquest/domain/model/UpdateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskCategory.kt`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`

## 작업

테스트를 먼저 작성한 뒤 신규 생성 전용 폼을 생성/수정 공용 task editor state로 확장한다. Compose UI는 이 step에서 수정하지 않는다.

`CalendarUiState.kt`에 공용 editor 상태를 추가하거나 기존 `NewTaskFormUiState`를 대체한다.

- editor mode: add 또는 edit
- edit 대상: `taskId: Long?`, `effectiveDate: LocalDate?`
- form fields: `title`, `memo`, `time: LocalTime?`, `difficulty`, `category`, `recurrenceRule`
- category presets: `TaskCategory.Presets`
- delete confirmation state: `taskId`, `occurrenceDate`, `title`을 포함하거나 동등한 구조
- 기본 form 값: 제목/메모 빈 문자열, 시간 없음, 난이도 `MEDIUM`, 카테고리 `일반`, 반복 `NONE`

`CalendarViewModel` public 이벤트를 다음 의미로 정리한다. 기존 UI 테스트 호환을 위해 `showAddTaskDialog`, `hideAddTaskDialog`, `saveNewTask`, `updateNewTaskTitle` 같은 기존 메서드는 wrapper로 유지해도 된다.

- `showAddTaskDialog()`: 선택 날짜 기준 add mode editor를 연다.
- `showEditTaskDialog(taskId: Long, occurrenceDate: LocalDate)`: repository에서 task를 읽어 edit mode editor를 연다.
- `hideTaskEditor()`: editor와 오류를 닫고 form을 초기화한다.
- `updateTaskTitle(title: String)`
- `updateTaskMemo(memo: String)`
- `updateTaskTime(time: LocalTime?)`
- `updateTaskDifficulty(difficulty: TaskDifficulty)`
- `updateTaskCategory(category: String)`
- `updateTaskRecurrenceRule(recurrenceRule: RecurrenceRule)`
- `saveTaskEditor()`: add mode면 `createTask`, edit mode면 `updateTask(UpdateTaskInput)`을 호출한다.
- `requestDeleteTask(taskId: Long, occurrenceDate: LocalDate, title: String)`
- `dismissDeleteTask()`
- `confirmDeleteTask()`: `repository.deleteTask(taskId, occurrenceDate)`를 호출한다.

Validation 규칙은 다음과 같다.

- 제목은 저장 시 trim 후 비어 있으면 저장하지 않고 오류를 표시한다.
- memo는 trim해서 저장한다.
- category는 `TaskCategory.normalize()`로 저장한다.
- time은 `LocalTime?`로 관리하며 텍스트 `HH:mm` 파싱은 더 이상 ViewModel의 기본 저장 경로가 아니다.
- 한글 제목/메모는 입력 중이나 저장 시 손실되면 안 된다.

`CalendarViewModelTest`와 fake repository를 확장한다.

- 한글 제목과 메모가 add mode 저장 후 그대로 occurrence에 표시된다.
- edit mode가 기존 task 값을 form에 채운다.
- edit save가 `UpdateTaskInput`의 `effectiveDate`와 변경된 필드를 repository에 전달한다.
- delete confirmation 후 repository delete가 호출되고 목록에서 사라진다.
- 한국어 카테고리 프리셋 외 값은 `일반`로 normalize된다.
- 기존 완료/완료취소 ViewModel 테스트가 그대로 통과한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `CalendarViewModel`이 Room DAO, Room database, AlarmManager, WorkManager를 import하지 않는지 확인한다.
3. Compose UI 파일을 수정하지 않았는지 확인한다.
4. `/phases/000-009/7-task-entry-editing/index.json`의 step 3 상태와 결과 필드를 업데이트한다.

## 금지사항

- ViewModel에서 Room DAO를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 레이어 규칙을 위반한다.
- ViewModel에 `TextFieldValue` 같은 Compose UI 타입을 저장하지 마라. 이유: IME composition 처리는 UI 컴포넌트 책임이고 ViewModel state는 UI toolkit에 독립적이어야 한다.
- 시간 값을 문자열만으로 저장하지 마라. 이유: picker와 직접 입력을 같은 `LocalTime?` 계약으로 다뤄야 한다.
- 저장 시점 전에 title/memo를 계속 trim하지 마라. 이유: 한글 IME 조합 중 입력이 깨질 수 있다.
- 기존 테스트를 깨뜨리지 마라.
