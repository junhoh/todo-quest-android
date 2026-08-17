# Step 1: Calendar presentation 월 이동·한국어화

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- step 0에서 수정한 `/docs/UI_GUIDE.md`
- `/phases/020-029/27-calendar-navigation-korean-ui/index.json`

## 작업

Calendar feature의 ViewModel unit test와 Compose instrumentation test를 구현보다 먼저 추가·수정해 실패를 확인한 다음 월 이동과 Calendar 전체 한국어화를 구현한다.

- `CalendarViewModel`에 `showPreviousMonth()`와 `showNextMonth()` 공개 이벤트를 추가한다. 각 이벤트는 현재 `selectedDate`를 정확히 한 달 이동시키며 `LocalDate.minusMonths(1)`과 `plusMonths(1)`의 말일 보정 의미를 따른다. `visibleMonth`, occurrence 관찰 범위와 선택 날짜 일정 목록은 이동한 날짜에서 계속 파생한다.
- 2026-07-14의 이전·다음 이동, 12월↔1월 연도 경계, 1월 31일→2월 28일과 윤년 2월 29일 보정, 이동 월 occurrence 재구독을 `CalendarViewModelTest`에서 검증한다.
- `CalendarContent`에 이전 달·다음 달 callback을 추가하고 월 제목 양옆에 최소 48dp `IconButton`을 배치한다. test tag는 `calendar-previous-month`, `calendar-next-month`, TalkBack 설명은 `이전 달`, `다음 달`로 한다.
- 월 제목은 `%1$d년 %2$d월`, 선택 날짜는 한국어 년·월·일/요일 형식, 요일 머리글은 `월`부터 `일`까지 표시한다. `Locale.US`에 의존해 사용자 문구를 만드는 코드를 제거한다.
- `Add`는 `추가`로 바꾸고 버튼 click이 `CalendarViewModel.showAddTaskDialog()`로 전달돼 `TaskEditorMode.ADD` editor를 여는 상태 기반 흐름을 유지한다. `TaskEditorDialog`에 `task-editor-dialog` test tag를 추가하고 `MainActivity`에서 `추가` 클릭 후 `할 일 추가` dialog와 제목 입력 필드가 표시되는 전용 회귀 테스트를 작성한다.
- `CalendarUiState.errorMessage: String?` 표시 계약을 `CalendarUiMessage?` 같은 immutable 의미 타입으로 바꾼다. 최소한 일정 없음, 불러오기 실패, 잘못된 `HH:mm`, 제목 필수, 저장 실패, 삭제 실패, 완료 실패, 완료 취소 실패를 구분한다. ViewModel에는 Android `Context`나 resource id를 넣지 않고 caught exception 원문도 사용자에게 노출하지 않는다.
- `/app/src/main/res/values/strings.xml`에 Calendar 전용 한국어 기본 리소스를 추가하고 `values-ko`, `values-en`은 만들지 않는다. 다음 사용자 노출 영역과 TalkBack 설명을 모두 리소스로 전환한다.
  - 레벨·경험치·골드 요약, 월 제목, 선택 날짜, 완료 개수, 빈 목록, 날짜 셀 일정·완료 개수
  - 추가, 수정, 삭제, 완료, 완료 취소와 각 일정 제목을 포함한 접근성 설명
  - 기본 보상, 정시 보너스·일일 제한, 실제 지급 snackbar의 정시/비정시 결과
  - 할 일 추가·수정 dialog, 제목, 메모, 시간, 카테고리, 난이도, 반복, 저장, 취소, 삭제 확인
  - 난이도 `쉬움`, `보통`, `어려움`과 반복 `없음`, `매일`, `매주`, `매월`
  - 시간 선택 dialog와 입력/시계 전환, 시간 없음, 확인
  - `CalendarUiMessage`에 대응하는 한국어 오류 문구
- `Todo Quest`, test tag, enum 이름, 내부 key는 영문을 유지한다. 시간 `HH:mm` 형식과 한국어 카테고리 preset, task 저장·수정·삭제, occurrence 완료와 보상 로직은 변경하지 않는다.
- 기존 Calendar instrumentation 기대값을 한국어 문구와 접근성 설명으로 갱신하고 월 이전·다음 버튼을 눌렀을 때 제목, 선택 날짜와 날짜 셀이 함께 바뀌는지 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest
adb devices
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest"
git diff --check
```

## 검증 절차

1. ViewModel과 Compose 회귀 테스트를 구현보다 먼저 작성하고 기존 구현에서 assertion 또는 compilation이 실패함을 확인한다.
2. AC 명령을 실행한다. `adb devices`에 실행 가능한 기기가 없으면 임의 설치·AVD 생성을 하지 말고 step을 `blocked`로 기록한다.
3. `CalendarScreen.kt`와 `CalendarViewModel.kt`에 사용자 표시용 영문 문장이 남지 않았는지 확인한다. `Todo Quest`, test tag, enum, `HH:mm` 같은 내부/형식 문자열은 허용한다.
4. UI가 DAO, AlarmManager, WorkManager를 직접 참조하지 않고 ViewModel event만 전달하는지 확인한다.
5. `/phases/020-029/27-calendar-navigation-korean-ui/index.json`의 step 1을 `completed`로 변경하고 월 이동 API, 한국어 리소스와 Add dialog 회귀 테스트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- ViewModel에 Android `Context` 또는 문자열 resource id를 주입하지 마라. 이유: 번역 리소스 해석은 Compose UI 책임이다.
- exception의 원문을 화면에 표시하지 마라. 이유: 영문 또는 내부 구현 정보가 노출될 수 있다.
- Repository, Room DAO, 완료·보상 transaction을 수정하지 마라. 이유: 이번 step은 Calendar presentation module만 다룬다.
- `visibleMonth`와 `selectedDate`가 서로 다른 월을 가리키게 만들지 마라. 이유: 기존 occurrence 조회와 일간 목록 계약을 유지해야 한다.
- test tag와 enum을 한국어로 변경하지 마라. 이유: 사용자 표시 언어와 내부 호환성 계약은 분리해야 한다.
- 기존 테스트를 삭제하거나 무시하지 마라.
