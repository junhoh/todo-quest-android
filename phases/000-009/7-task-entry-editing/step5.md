# Step 5: task-editor-ui-tests

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
- `/phases/000-009/7-task-entry-editing/step4.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`

## 작업

Compose UI instrumentation 테스트를 확장해 사용자가 요청한 핵심 흐름을 검증한다. 기능 코드는 이 step에서 새로 설계하지 말고, 테스트 실패를 고치는 최소 UI/test tag 보정만 허용한다.

`CalendarScreenTest` 또는 별도 instrumentation test에 다음 시나리오를 추가한다.

- 한글 제목과 메모를 입력해 저장하면 목록에 한글 텍스트가 표시된다.
- 카테고리 `공부` 또는 `업무`를 선택해 저장하면 metadata에 선택한 한국어 카테고리가 표시된다.
- 시간 picker에서 값을 선택해 저장하면 `HH:mm` 시간이 표시된다.
- 시간 input mode로 전환해 값을 입력해 저장하면 해당 시간이 표시된다.
- 저장된 할일의 edit 버튼을 눌러 제목, 메모, 시간, 카테고리를 변경하면 목록에 수정 결과가 표시된다.
- 저장된 할일의 delete 버튼을 누른 뒤 확인하면 해당 할일이 목록에서 사라진다.
- 완료 버튼과 Undo 버튼은 edit/delete 버튼 추가 후에도 기존처럼 동작한다.

테스트 안정성 규칙은 다음과 같다.

- 시간 picker 드래그 자체가 테스트에서 불안정하면 picker state를 조작할 수 있는 접근성/test tag 흐름을 사용한다.
- 실제 Android IME composition을 완벽히 재현하기 어렵더라도 `performTextInput("한글 제목")`과 ViewModel unit test가 모두 한글 보존을 검증해야 한다.
- MainActivity 기반 테스트가 로컬 DB 상태의 영향을 받으면 test title에 timestamp를 포함하고, 삭제 테스트는 방금 만든 항목만 대상으로 한다.

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
2. 연결된 Android 기기나 emulator가 없어 instrumentation test를 실행할 수 없으면 step을 blocked로 두지 말고, unit/lint/build가 통과했다면 summary에 미실행 사유를 기록한다.
3. 테스트가 추가한 test tag가 사용자 화면에 불필요한 visible text로 드러나지 않는지 확인한다.
4. `/phases/000-009/7-task-entry-editing/index.json`의 step 5 상태와 결과 필드를 업데이트한다.

## 금지사항

- 테스트를 통과시키기 위해 content description이나 test tag에서 사용자에게 보이는 문구를 어색하게 바꾸지 마라. 이유: 접근성과 사용자 경험을 해칠 수 있다.
- 완료/보상 테스트의 기대값을 완화하지 마라. 이유: occurrence 단위 멱등 보상은 CRITICAL 규칙이다.
- UI 테스트가 불안정하다는 이유로 주요 사용자 흐름 검증을 삭제하지 마라. 이유: 이번 phase의 핵심은 입력/수정/삭제 흐름이다.
- 기존 테스트를 깨뜨리지 마라.
