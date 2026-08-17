# Step 1: splashscreen-launch-stability

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/10-emulator-connected-regression-fix/index.json`
- `/phases/010-019/10-emulator-connected-regression-fix/step0.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/res/values/styles.xml`
- `/app/src/main/res/values/colors.xml`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## 작업

전체 instrumentation suite에서 여러 테스트가 실제 `MainActivity`를 반복 실행할 때 `VRI-Splash Screen com.todoquest` 레이어가 남고 `MainActivity` surface가 `shown=false`가 되어 black screen으로 멈추는 회귀를 안정화한다.

- `/app/build.gradle.kts`의 `androidx.core:core-splashscreen` dependency를 안정 버전 `1.2.0`으로 올린다.
  - 2026-07-18 기준 공식 AndroidX Core release notes에서 `core-splashscreen` stable `1.2.0`이 확인된 상태다.
  - preview, alpha, beta, rc 버전을 사용하지 않는다.
- `/app/src/main/java/com/todoquest/MainActivity.kt`를 수정한다.
  - `installSplashScreen()` 호출은 `super.onCreate(savedInstanceState)`보다 먼저 유지한다.
  - 커스텀 exit animation을 쓰지 않으므로 `splashScreen.setOnExitAnimationListener { it.remove() }`를 제거한다.
  - `setContent { TodoQuestApp() }` 경로는 유지한다.
  - 필요하면 `val splashScreen` 지역 변수도 제거하고 `installSplashScreen()`만 호출한다.
- `/app/src/main/res/values/styles.xml`의 splash theme 구조는 유지한다.
  - `Theme.TodoQuest.Starting`은 `Theme.SplashScreen` 기반이어야 한다.
  - `postSplashScreenTheme`는 `@style/Theme.TodoQuest`를 유지한다.
- 기존 UI, ViewModel, Repository, Room, completion/reward 로직은 수정하지 않는다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest
git diff --check
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기가 `device` 상태인지 확인한다.
2. 연결된 Android 기기가 없으면 step 1을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. Acceptance Criteria 명령을 실행한다.
4. `MainActivityLaunchSmokeTest.launchScreenshotIsNotBlackScreen`가 계속 실제 screenshot 픽셀 기준으로 검은 화면을 판정하는지 확인한다.
5. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.
6. 성공하면 `/phases/010-019/10-emulator-connected-regression-fix/index.json`의 step 1을 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.
7. 3회 수정 후에도 실패하면 step 1을 `error`로 바꾸고 `failed_at`, `error_message`를 기록한다.

## 금지사항

- `installSplashScreen()`을 `super.onCreate()` 뒤로 옮기지 마라. 이유: AndroidX SplashScreen의 시작 Activity 설치 순서 요구사항을 위반한다.
- 커스텀 exit animation이 없는데 `setOnExitAnimationListener`를 남기지 마라. 이유: listener를 설정하면 splash 자동 제거 경로가 비활성화되어 반복 launch에서 splash layer가 남을 수 있다.
- `MainActivityLaunchSmokeTest`를 삭제하거나 screenshot 판정 기준을 완화하지 마라. 이유: black screen 회귀를 검출해야 한다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 외부 Google Calendar 연동을 추가하지 마라. 이유: MVP 제외 범위다.
- 기존 테스트를 깨뜨리지 마라.
