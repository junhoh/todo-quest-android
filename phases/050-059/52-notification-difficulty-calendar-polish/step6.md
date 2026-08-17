# Step 6: show-reminder-time-and-compact-actions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step3.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step5.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

ViewModel unit test와 Compose connected test를 production보다 먼저 작성하고 Calendar UI module, app dependency wiring과 Korean resources만 수정한다.

Calendar ViewModel은 occurrence의 `reminderSetting`, `reminderScheduleStatus`, occurrence date와 nullable task time을 domain reminder local trigger policy에 전달해 렌더링 가능한 typed reminder UI state를 만든다. 표시 state는 mode, trigger `LocalTime`, occurrence date 대비 `SAME_DAY` 또는 `PREVIOUS_DAY`, capability recovery reason을 포함하고 사용자 문장을 ViewModel에 저장하지 않는다. NONE은 표시 state를 만들지 않는다.

task card는 설정된 reminder만 다음 형식으로 metadata 아래 독립 한 줄에 표시한다.

- `10분 전 · 당일 08:50`
- `1시간 전 · 전날 23:30`
- `직접 설정 · 당일 07:05`

mode, `당일`/`전날`, time format과 TalkBack 설명은 모두 `strings.xml`의 한국어 resource를 사용한다. 계산 전 task time이 아니라 occurrence 기준 실제 발화 local time을 표시한다. 완료·실패 occurrence에도 원본 설정 정보는 보여 주되 `예약됨`이라고 잘못 표시하지 않는다.

`POST_NOTIFICATIONS_REQUIRED`, `NOTIFICATION_CHANNEL_DISABLED`, `EXACT_ALARM_ACCESS_REQUIRED`에는 각각 typed recovery CTA를 제공한다. ViewModel은 task id를 보존한 one-shot event로 app notification settings, reminder channel settings 또는 기존 exact rationale 흐름을 요청하고 settings 복귀 뒤 해당 task만 Repository/UseCase를 통해 reconcile한다. ERROR는 한국어 상태만 표시하고 반복 system dialog를 자동 실행하지 않는다. NONE, SCHEDULED, DELIVERED, NO_FUTURE_OCCURRENCE에는 불필요한 CTA를 만들지 않는다.

TODO의 `완료`·`실패` 버튼은 전체 폭 1/2씩 차지하는 weight를 제거하고 Row 오른쪽에 content-sized로 배치한다. 시각 container는 Material compact button 높이와 작은 icon/padding을 사용하되 실제/semantics touch target은 최소 48dp, 텍스트 label과 기존 content description을 유지한다. processing indicator, battle input lock, 중복 탭 방지와 FAILED/COMPLETED terminal action은 변경하지 않는다. 작은 폭과 font scale 2.0에서도 두 버튼이 잘리거나 겹치지 않아야 한다.

Calendar reward 안내에는 난이도에 따라 피해와 경험치가 증가한다는 한국어 resource를 추가하되 정확한 gold 증가를 암시하지 않는다. Compose/ViewModel은 DAO, AlarmManager, NotificationManager, WorkManager를 직접 호출하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.app.AppNavigationTest" --console=plain
git diff --check
~~~

## 검증 절차

1. reminder mode/day/time, capability CTA, compact button bounds·48dp semantics test를 먼저 실패시킨다.
2. UI state·ViewModel·Compose·wiring·resource를 구현한다.
3. 작은 폭과 큰 글꼴 fixture를 포함해 AC를 실행한다.
4. Android 도구/기기가 없으면 설치하지 말고 blocked, 성공 시 step 6을 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- 사용자 노출 영문 문장을 Kotlin에 하드코딩하지 마라. 이유: 한국어 string resource 규칙을 지켜야 한다.
- task time 자체를 reminder 발화 시각으로 표시하지 마라. 이유: preset은 offset과 이전 날짜 계산이 필요하다.
- 버튼 터치 영역을 48dp 미만으로 줄이지 마라. 이유: 시각적 축소와 접근성 target은 분리해야 한다.
- settings 복귀 때 모든 일정 저장을 다시 실행하지 마라. 이유: 이미 성공한 일정 transaction과 reminder reconciliation을 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
