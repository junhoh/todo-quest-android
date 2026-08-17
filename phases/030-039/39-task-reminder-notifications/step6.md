# Step 6: add-calendar-reminder-controls

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step1.md`
- `/phases/030-039/39-task-reminder-notifications/step4.md`
- `/phases/030-039/39-task-reminder-notifications/step5.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

Calendar ViewModel unit test와 Compose test를 production UI보다 먼저 작성해 reminder editor, validation, permission event와 저장 실패 격리를 고정한다. 이 step은 Calendar UI state·ViewModel·Compose와 Korean resources에 한정한다. Room, AlarmManager, receiver 구현을 수정하지 않는다.

`TaskEditorUiState`에 `reminderSetting`, persisted `reminderStatus`, custom picker open에 필요한 ViewModel 소유 상태를 추가한다. `showAddTaskDialog`의 기본은 `NONE`이고 `showEditTaskDialog`는 task의 실제 reminder 설정·상태를 복원한다. ViewModel은 `updateTaskReminderMode(mode)`, `updateTaskReminderCustomTime(time)`, notification permission 결과와 exact-alarm settings 복귀를 처리하는 명시적 command를 제공한다.

`ReminderSelector`를 time·difficulty·recurrence·category와 같은 editor scroll 안에 배치한다. 네 선택지는 문자열 resource 기반 `설정 없음`, `10분 전`, `1시간 전`, `직접 설정`이다. preset 두 개는 `form.time == null`이면 disabled semantics와 한국어 helper를 제공한다. preset 선택 상태에서 task time을 제거하면 mode를 `NONE`으로 바꾸고 알림이 해제됐다는 ViewModel message를 표시한다. `CUSTOM_TIME`은 task time과 무관하게 선택 가능하며 당일 시각 picker를 노출한다. custom time이 null이면 저장하지 않고 한국어 validation message를 표시한다.

`CreateTaskInput`과 `UpdateTaskInput`에 editor reminder 설정을 전달하고 production에서는 step 4의 create/update use case가 호출될 수 있는 dependency를 ViewModel에 추가한다. step 7 wiring 전에도 source가 compile되도록 기존 constructor 호출에 안전한 기본/overload를 유지할 수 있으나 최종 production factory는 반드시 use case를 주입해야 한다. delete도 전용 use case 경계를 받을 수 있게 한다.

task 저장 transaction이 성공했으나 reminder status가 permission-required/no-future/error이면 editor를 닫고 일정은 성공으로 유지한다. `SaveFailed`를 표시하지 말고 별도 `CalendarUiMessage`와 one-shot `CalendarEvent`로 알림 권한 안내를 표시한다. API 33+ runtime permission은 non-NONE 저장 행동 뒤 Compose `ActivityResultContracts.RequestPermission` launcher로 요청한다. 허용 후 exact capability가 없으면 한국어 설명을 거쳐 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` settings launcher를 연다. settings에서 복귀할 때 ViewModel이 재검토 use case를 호출한다. 거부·dismiss 후에도 일정 생성·완료 UI를 막지 않는다.

UI는 AlarmManager, WorkManager, DAO를 호출하지 않는다. permission launcher와 step 5의 platform intent adapter만 사용한다. 모든 label, helper, validation, permission rationale, settings CTA, schedule warning과 TalkBack description은 `strings.xml`에 둔다. editor는 320dp·font scale 2.0에서도 세로 scroll과 48dp target을 유지한다.

테스트는 기본 NONE, 시간 유/무 preset enablement, time 제거 reset, custom picker와 null validation, create/edit payload, permission allowed/denied/dismiss, exact settings 복귀, schedule error가 save success를 보존, Activity 재생성 시 one-shot system prompt 비재생, 한국어·TalkBack·compact scroll을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. ViewModel·Compose test를 먼저 작성해 selector와 permission flow 부재 실패를 확인한다.
2. UI state·ViewModel·Compose·strings를 구현하고 AC를 실행한다.
3. ViewModel과 Compose에 영문 사용자 문장, DAO, AlarmManager, WorkManager 직접 호출이 없는지 검색한다.
4. 연결 기기가 없으면 Android 도구를 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 6을 `completed`로 바꾸고 editor·권한 UI 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 시간 없는 task에서 10분 전·1시간 전을 저장하지 마라. 이유: 기준 시각이 없어 trigger를 결정할 수 없다.
- custom time을 task time offset이나 이전 날짜로 바꾸지 마라. 이유: 사용자가 occurrence 당일 독립 시각을 선택했다.
- permission 거부를 `SaveFailed`로 표시하거나 editor를 강제로 유지하지 마라. 이유: 일정 저장과 알림 capability는 독립이다.
- Compose나 ViewModel에서 AlarmManager, WorkManager, Room DAO를 직접 호출하지 마라. 이유: CRITICAL layer 규칙을 위반한다.
- 사용자 노출 문구를 Kotlin에 하드코딩하지 마라. 이유: 한국어 문자열 resource 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
