# Step 6: task-editor-verification

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/000-009/7-task-entry-editing/index.json`
- `/phases/000-009/7-task-entry-editing/step0.md`
- `/phases/000-009/7-task-entry-editing/step1.md`
- `/phases/000-009/7-task-entry-editing/step2.md`
- `/phases/000-009/7-task-entry-editing/step3.md`
- `/phases/000-009/7-task-entry-editing/step4.md`
- `/phases/000-009/7-task-entry-editing/step5.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/UpdateTaskInput.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`

## 작업

전체 변경 사항을 검증하고 phase 상태를 정리한다. 이 step은 새 기능 구현보다 누락 검증, 회귀 확인, metadata 정리에 집중한다.

검증할 항목은 다음과 같다.

- Title과 Memo에 한글을 입력해 저장/수정해도 깨지거나 누락되지 않는다.
- 시간은 picker 드래그 선택이 기본이고, 필요 시 직접 입력 모드로 바꿔 저장할 수 있다.
- 시간 없음 상태가 저장 가능하다.
- 카테고리는 한국어 프리셋 중 하나만 선택할 수 있고 저장값도 한국어다.
- 단일 할일은 수정과 삭제가 정상 동작한다.
- 반복 할일 수정/삭제는 선택 날짜부터 미래 occurrence에만 적용된다.
- 반복 수정 후 이미 지급된 future occurrence 보상은 중복 지급되지 않는다.
- UI 레이어가 Room DAO, AlarmManager, WorkManager를 직접 호출하지 않는다.
- Google Calendar 연동이 추가되지 않았다.
- Room database는 destructive migration 없이 version 2 schema와 migration을 가진다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

기기 또는 에뮬레이터가 연결되어 있으면 다음도 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기나 emulator가 없으면 `connectedDebugAndroidTest` 미실행 사유를 summary에 기록한다.
3. `git diff --check`로 whitespace 오류가 없는지 확인한다.
4. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙 위반이 없는지 확인한다.
5. 모든 step이 완료되면 `/phases/000-009/7-task-entry-editing/index.json`의 step 6 상태와 결과 필드를 업데이트한다.
6. 모든 step이 완료되면 `/phases/index.json`의 `7-task-entry-editing` 상태를 `completed`로 업데이트한다.

## 금지사항

- 검증 step에서 새 기능을 임의로 추가하지 마라. 이유: 실패 원인과 기능 변경 범위가 섞인다.
- Android 도구가 설치되어 있지 않으면 임의 설치하지 마라. 이유: AGENTS.md의 개발 프로세스 규칙을 위반한다.
- `3-schedule-management`의 기존 blocked 이력을 임의로 수정하지 마라. 이유: 과거 phase 상태는 별도 이력이다.
- 파괴적 명령을 실행하지 마라. 이유: 저장소 이력과 사용자 작업을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
