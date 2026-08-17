# Step 4: task-editor-compose-ui

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
- `/phases/000-009/7-task-entry-editing/step3.md`
- `/app/src/main/java/com/todoquest/domain/model/TaskCategory.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

## 작업

테스트 가능한 Compose UI를 구현한다. ViewModel public 이벤트만 호출하고 UI에서 Repository, DAO, Room database, AlarmManager, WorkManager를 직접 호출하지 않는다.

한글 입력 안정화를 위해 title/memo 입력 컴포넌트를 정리한다.

- `KoreanImeTextField` 또는 동등한 private composable을 만든다.
- 내부에서는 `rememberSaveable(stateSaver = TextFieldValue.Saver)`와 `TextFieldValue`를 사용해 IME composition을 보존한다.
- 외부 ViewModel에는 `TextFieldValue.text`만 전달한다.
- onValueChange에서 trim, normalize, format 변환을 하지 않는다.
- title은 single line, memo는 2~3줄 multi line으로 유지한다.
- 기존 test tag `new-task-title`, `new-task-memo`는 유지하거나 add/edit 공용 tag로 alias를 제공한다.

시간 입력 UI를 교체한다.

- 기본은 Material3 `TimePicker`를 여는 버튼/필드로 제공한다.
- `TimePicker`는 시각과 분을 드래그로 선택하는 기본 모드다.
- 사용자가 필요하면 같은 dialog에서 `TimeInput` 모드로 전환해 값을 직접 입력할 수 있어야 한다.
- `시간 없음` 액션으로 `time = null`을 선택할 수 있어야 한다.
- 화면에는 선택된 시간이 `HH:mm`, 없으면 `시간 없음`으로 표시된다.
- 안정적인 test tag를 추가한다: `task-time-button`, `task-time-picker-dialog`, `task-time-input-toggle`, `task-time-clear`, `task-time-confirm`.

카테고리 UI를 자유 입력에서 한국어 프리셋 선택으로 바꾼다.

- `일반`, `업무`, `공부`, `건강`, `집안일`, `개인`을 FilterChip 또는 동등한 선택 컨트롤로 표시한다.
- 선택된 카테고리는 form state에 반영한다.
- 프리셋 외 직접 입력 UI는 제공하지 않는다.
- 기존 테스트 호환이 필요하면 `new-task-category` test tag는 선택 영역 컨테이너에 유지한다.

수정/삭제 UI를 task row에 추가한다.

- 각 row에 edit icon button과 delete icon button을 추가한다.
- icon button은 접근성 content description을 가진다.
- edit은 `showEditTaskDialog(task.taskId, task.occurrenceDate)`를 호출한다.
- delete은 삭제 확인 dialog를 연다.
- 삭제 확인 dialog는 destructive 색상 또는 error color를 사용하고, 취소/삭제 버튼을 제공한다.

공용 editor dialog는 add/edit mode를 구분한다.

- add mode title: `New task`
- edit mode title: `Edit task`
- confirm button은 둘 다 저장 동작을 수행한다.
- 오류 메시지는 기존처럼 dialog 안에 표시한다.
- 완료/취소 보상 흐름 UI는 변경하지 않는다.

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
2. 연결된 Android 기기가 없어 `connectedDebugAndroidTest`를 실행할 수 없으면 완료 summary에 미실행 사유를 남긴다.
3. `CalendarScreen.kt`가 Room DAO, Room database, AlarmManager, WorkManager를 import하지 않는지 확인한다.
4. UI가 `docs/UI_GUIDE.md`의 터치 대상, 접근성 label, 텍스트 overflow, 삭제 피드백 규칙을 따르는지 확인한다.
5. `/phases/000-009/7-task-entry-editing/index.json`의 step 4 상태와 결과 필드를 업데이트한다.

## 금지사항

- Compose UI에서 Repository나 DAO를 직접 호출하지 마라. 이유: UI는 ViewModel state를 렌더링하고 event만 전달해야 한다.
- 한글 입력 필드의 onValueChange에서 trim이나 category normalize를 수행하지 마라. 이유: IME 조합 중 입력이 끊길 수 있다.
- 카테고리 직접 입력을 유지하지 마라. 이유: 이번 결정은 한국어 고정 프리셋 선택이다.
- 삭제 버튼을 확인 없이 즉시 실행하지 마라. 이유: 삭제는 중요한 destructive action이다.
- 기존 테스트를 깨뜨리지 마라.
