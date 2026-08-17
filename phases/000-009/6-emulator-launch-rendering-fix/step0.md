# Step 0: splash-theme-rendering-fix

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/5-emulator-launch-diagnostics/index.json`
- `/phases/000-009/5-emulator-launch-diagnostics/step0.md`
- `/phases/000-009/5-emulator-launch-diagnostics/step1.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`

## 작업

현재 blocked 원인은 앱 프로세스와 Compose semantics는 살아 있지만 Android 17/API 37 emulator의 SurfaceFlinger에서 `Splash Screen com.todoquest` 레이어가 계속 visible 상태로 남고 `MainActivity` surface가 hidden 처리되어 앱 영역이 검정으로 보이는 것이다. 기존 `MainActivityLaunchSmokeTest.launchScreenshotIsNotBlackScreen`를 선행 실패 테스트로 유지하고, 앱 시작 theme와 splash screen 구성을 명시해서 첫 화면이 정상 렌더링되도록 수정한다.

- `/app/build.gradle.kts`에 Android 공식 splash screen 문서 기준 dependency를 추가한다.
  - `implementation("androidx.core:core-splashscreen:1.0.0")`
  - preview, alpha, beta 버전을 사용하지 않는다.
- `/app/src/main/res/values/colors.xml`을 추가한다.
  - `quest_background`는 Compose `QuestBackground`와 같은 `#15181B`로 정의한다.
  - `quest_surface`는 `#20242A`, `quest_gold`는 `#F2C14E`로 정의한다.
- `/app/src/main/res/drawable/ic_splash_todoquest.xml`을 추가한다.
  - 단색 vector drawable로 만든다.
  - 외부 이미지 파일을 받거나 생성하지 않는다.
  - 아이콘은 캘린더 또는 체크 표시 계열이면 충분하다.
- `/app/src/main/res/values/styles.xml`을 추가한다.
  - `Theme.TodoQuest`는 `@android:style/Theme.Material.NoActionBar` 기반으로 정의한다.
  - `android:windowNoTitle=true`, `android:windowActionBar=false`, `android:windowBackground=@color/quest_background`를 명시한다.
  - status bar와 navigation bar 색상은 `@color/quest_background`로 맞춘다.
  - `android:windowLightStatusBar=false`, `android:windowLightNavigationBar=false`를 명시한다.
  - `Theme.TodoQuest.Starting`은 `Theme.SplashScreen` 기반으로 정의한다.
  - `windowSplashScreenBackground=@color/quest_background`, `windowSplashScreenAnimatedIcon=@drawable/ic_splash_todoquest`, `postSplashScreenTheme=@style/Theme.TodoQuest`를 명시한다.
- `/app/src/main/AndroidManifest.xml`에서 `MainActivity`에 `android:theme="@style/Theme.TodoQuest.Starting"`을 지정한다.
- `/app/src/main/java/com/todoquest/MainActivity.kt`에서 `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`을 사용한다.
  - `onCreate`에서 `val splashScreen = installSplashScreen()`을 `super.onCreate(savedInstanceState)`보다 먼저 호출한다.
  - `super.onCreate(savedInstanceState)` 이후 `splashScreen.setOnExitAnimationListener { it.remove() }`를 설정한다.
  - 기존 `setContent { TodoQuestApp() }` 경로는 유지한다.
- 기존 UI, ViewModel, Repository, Room, completion/reward 로직은 수정하지 않는다.
- `MainActivityLaunchSmokeTest`의 검은 화면 판정 기준과 semantics assertion은 완화하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `MainActivityLaunchSmokeTest`가 계속 존재하고 screenshot 검은 화면 판정 기준이 완화되지 않았는지 확인한다.
3. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.
4. 성공하면 `/phases/000-009/6-emulator-launch-rendering-fix/index.json`의 step 0을 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.
5. 실패하면 최대 3회까지 수정 후 재검증한다.
6. 3회 수정 후에도 실패하면 step 0을 `error`로 바꾸고 `failed_at`, `error_message`를 기록한다.

## 금지사항

- `MainActivityLaunchSmokeTest`를 삭제하거나 screenshot 판정 기준을 완화하지 마라. 이유: 검은 화면 회귀를 잡는 선행 테스트다.
- `MainActivity` 대신 테스트 전용 fake Activity를 띄우지 마라. 이유: 사용자가 실행하는 실제 앱 시작 경로를 검증해야 한다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 외부 Google Calendar 연동을 추가하지 마라. 이유: MVP 제외 범위다.
- Android SDK, emulator, JDK를 임의 설치하지 마라. 이유: 개발 도구 준비는 별도 승인된 phase에서만 수행한다.
- 기존 테스트를 깨뜨리지 마라.
