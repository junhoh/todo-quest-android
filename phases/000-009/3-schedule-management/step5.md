# Step 5: calendar-compose-ui-test

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/phases/000-009/3-schedule-management/step1.md`
- `/phases/000-009/3-schedule-management/step2.md`
- `/phases/000-009/3-schedule-management/step3.md`
- `/phases/000-009/3-schedule-management/step4.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`

## 작업

실제 화면 테스트를 위한 Android instrumentation test 구성을 추가하고, 핵심 캘린더 흐름을 Compose UI test로 검증한다.

Gradle 설정은 다음을 포함한다.

- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`
- `debugImplementation("androidx.compose.ui:ui-test-manifest")`
- AndroidX Test runner/rules/ext-junit 안정 버전

추가할 테스트는 다음 파일에 둔다.

- `app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

테스트 시나리오는 다음을 반드시 포함한다.

- 월간 캘린더가 표시된다.
- 일정 추가 버튼을 누르고 제목을 입력해 저장한다.
- 선택 날짜 목록에 새 일정이 표시된다.
- 완료 버튼을 누르면 완료 상태가 화면에 반영된다.

실행 규칙은 다음과 같다.

- 먼저 `adb devices`로 연결된 emulator 또는 실제 기기가 있는지 확인한다.
- 연결 대상이 있으면 `.\gradlew.bat connectedDebugAndroidTest`를 실행한다.
- 연결 대상이 없으면 이 step은 `blocked`로 기록하고 `blocked_reason`에 "connected Android device or emulator is required for Compose UI instrumentation test"를 적는다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

## 검증 절차

1. `adb devices`를 실행해 실제 화면 테스트 실행 가능 여부를 확인한다.
2. 연결 대상이 있으면 Acceptance Criteria 명령을 실행한다.
3. 연결 대상이 없으면 step 상태를 `blocked`로 기록하고 테스트 파일과 Gradle 설정은 유지한다.
4. UI test가 실제 `MainActivity`를 통해 앱을 띄우는지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`의 step 5 상태와 결과 필드를 업데이트한다.

## 금지사항

- 기기나 emulator가 없는데 실제 화면 테스트를 성공 처리하지 마라. 이유: 사용자가 명시적으로 실제 화면 테스트를 포함하기로 선택했다.
- 스크린샷 회귀 테스트 라이브러리를 추가하지 마라. 이유: 이번 phase 범위는 Compose UI instrumentation test까지다.
- 테스트를 내부 구현 세부 함수 호출로 대체하지 마라. 이유: 실제 화면 경로를 검증해야 한다.
- 네트워크나 외부 Google Calendar 상태에 의존하지 마라. 이유: MVP는 로컬 저장만 구현한다.
- 기존 테스트를 깨뜨리지 마라.
