# Step 1: schedule-entry-compose-form

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/4-schedule-entry-completion/index.json`
- `/phases/000-009/4-schedule-entry-completion/step0.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

## 작업

테스트를 먼저 작성한 뒤 신규 일정 dialog를 MVP 입력 필드 전체를 입력할 수 있는 Compose UI로 확장한다. ViewModel의 public 이벤트만 호출하고 UI에서 저장 로직을 직접 구현하지 않는다.

`CalendarScreen`에서 `NewTaskDialog`를 확장한다.

- 제목 입력: single line, 기존 `new-task-title` test tag 유지
- 메모 입력: multi line, `new-task-memo` test tag 추가
- 시간 입력: `HH:mm` 텍스트 입력, `new-task-time` test tag 추가
- 난이도 선택: `Easy`, `Medium`, `Hard` 중 하나를 선택하는 컨트롤, 각 선택지에 안정적인 test tag 추가
- 반복 선택: `None`, `Daily`, `Weekly`, `Monthly` 중 하나를 선택하는 컨트롤, 각 선택지에 안정적인 test tag 추가
- 카테고리 입력: single line, `new-task-category` test tag 추가
- 저장/취소 버튼과 오류 표시 동작은 기존 흐름을 유지한다.

화면 목록의 task row는 기존 `metadataText()`가 표시하는 시간, 반복, 난이도, 카테고리 정보를 계속 보여야 한다. 메모는 목록에서 제목 아래 보조 텍스트로 표시하되, 메모가 비어 있으면 기존 metadata 표시만 유지한다. 메모 표시가 목록 밀도를 과도하게 늘리지 않도록 최대 1줄과 ellipsis를 적용한다.

`CalendarScreenTest`는 다음 사용자 흐름을 검증한다.

- Add dialog를 열고 제목, 메모, 시간, 난이도, 반복, 카테고리를 입력해 저장한다.
- 저장 후 목록에 제목과 주요 metadata가 표시된다.
- 완료 버튼은 기존처럼 동작한다.

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
3. Compose 파일이 Room DAO, Room database, AlarmManager, WorkManager를 import하지 않는지 확인한다.
4. UI가 `docs/UI_GUIDE.md`의 일정 작성 화면, 버튼 접근성, 텍스트 overflow 규칙을 따르는지 확인한다.
5. `/phases/000-009/4-schedule-entry-completion/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- Compose UI에서 Room DAO를 직접 호출하지 마라. 이유: UI 레이어는 ViewModel state를 렌더링해야 한다.
- Compose UI에서 AlarmManager나 WorkManager를 직접 호출하지 마라. 이유: 알림 예약은 이번 step 범위가 아니며 레이어 규칙을 위반한다.
- Google Calendar 연동 UI를 추가하지 마라. 이유: MVP 제외 사항이다.
- Android 권한 요청 UI를 추가하지 마라. 이유: 알림 phase의 별도 작업 범위다.
- 기존 테스트를 깨뜨리지 마라.
