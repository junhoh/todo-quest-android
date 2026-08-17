# Step 2: instrumentation-runner-isolation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/10-emulator-connected-regression-fix/index.json`
- `/phases/010-019/10-emulator-connected-regression-fix/step0.md`
- `/phases/010-019/10-emulator-connected-regression-fix/step1.md`
- `/app/build.gradle.kts`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## 작업

connected instrumentation suite가 테스트 간 Activity, process, Room database, IME, splash/window 상태를 공유하지 않도록 Android Test Orchestrator를 Gradle에서 활성화한다. 목적은 `connectedDebugAndroidTest`가 각 테스트를 별도 instrumentation invocation으로 실행하게 만들어 suite 순서와 누적 상태에 의존하지 않도록 하는 것이다.

- `/app/build.gradle.kts`의 `android` 블록에 instrumentation runner argument를 추가한다.
  - `testInstrumentationRunnerArguments["clearPackageData"] = "true"` 또는 Kotlin DSL에서 동일하게 동작하는 map assignment를 사용한다.
  - 기존 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`는 유지한다.
- `/app/build.gradle.kts`의 `android` 블록에 test options를 추가한다.
  - `testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }`
  - 기존 `tasks.configureEach { if (name == "connectedDebugAndroidTest") { doNotTrackState(...) } }`는 유지한다.
- `/app/build.gradle.kts`의 `dependencies`에 Orchestrator test utility를 추가한다.
  - `androidTestUtil("androidx.test:orchestrator:1.5.1")`
  - AndroidX Test runner/rules/ext-junit 버전은 기존 `1.7.0`, `1.3.0` 구성을 유지한다.
- Orchestrator 도입으로 테스트 실행 시간이 늘어날 수 있으므로 connected 검증 timeout은 충분히 길게 잡는다.
- production source set에는 Orchestrator 관련 dependency를 추가하지 않는다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기가 `device` 상태인지 확인한다.
2. 연결된 Android 기기가 없으면 step 2를 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. Acceptance Criteria 명령을 실행한다.
4. Gradle connected test 로그에서 Android Test Orchestrator가 사용되는지 확인한다.
5. `connectedDebugAndroidTest` 실패 시 Gradle report, `app/build/outputs/androidTest-results/connected`, logcat을 확인해 테스트 실패와 runner 설치/실행 실패를 구분한다.
6. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.
7. 성공하면 `/phases/010-019/10-emulator-connected-regression-fix/index.json`의 step 2를 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.
8. 3회 수정 후에도 실패하면 step 2를 `error`로 바꾸고 `failed_at`, `error_message`를 기록한다.

## 금지사항

- `connectedDebugAndroidTest`를 exclude하거나 smoke test만 실행하도록 축소하지 마라. 이유: 전체 suite의 누적 실행 회귀를 잡아야 한다.
- Orchestrator dependency를 `implementation`이나 `androidTestImplementation`으로 추가하지 마라. 이유: Gradle의 test utility APK로 설치되어야 한다.
- 앱 데이터를 유지해야 통과하는 테스트를 만들지 마라. 이유: `clearPackageData=true` 이후에도 각 테스트는 독립적으로 통과해야 한다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 외부 Google Calendar 연동을 추가하지 마라. 이유: MVP 제외 범위다.
- 기존 테스트를 깨뜨리지 마라.
