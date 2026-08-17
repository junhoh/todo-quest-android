# Step 1: splashless-launch-fallback

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/12-launch-black-screen-recovery/index.json`
- `/phases/010-019/12-launch-black-screen-recovery/step0.md`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/res/values/styles.xml`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/scripts/diagnose_android_launch.ps1`

## 작업

step 0으로 실제 앱 foreground 상태에서도 black screen이 재현되면 Android 17/API 37 preview의 stale splash/starting_reveal 경로를 피하도록 production launch path를 최소 변경한다.

- `/app/src/main/AndroidManifest.xml`과 `/app/src/main/java/com/todoquest/MainActivity.kt`를 수정한다.
  - `MainActivity`는 `Theme.TodoQuest`를 직접 사용한다.
  - `installSplashScreen()` 호출을 제거하고 `super.onCreate(savedInstanceState)` 이후 `setContent { TodoQuestApp() }` 경로를 유지한다.
  - `setOnExitAnimationListener { it.remove() }`는 추가하지 않는다.
- `/app/src/main/res/values/styles.xml`는 앱 theme를 유지하고, 사용하지 않는 starting splash theme는 제거하거나 더 이상 manifest에서 참조하지 않는다.
- 필요하면 `/app/build.gradle.kts`에서 `androidx.core:core-splashscreen` 의존성을 제거한다.
- `/docs/DEVELOPMENT.md`에 이번 결정의 검증 기준을 짧게 기록한다.
  - API 37/16 KB preview AVD에서는 SplashScreen compat launch transition이 stale layer를 만들 수 있어 MVP launch 경로는 splashless theme를 기본값으로 둔다.
  - 안정 API AVD 또는 실제 device에서도 `diagnose_android_launch.ps1`로 교차 확인한다.
- UI, ViewModel, Repository, Room, completion/reward 로직은 수정하지 않는다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없으면 connected test와 launch 진단은 `blocked`로 기록하고 unit/lint/assemble 결과만 summary에 남긴다.
3. 연결 기기가 있으면 screenshot이 검은 화면이 아니고 foreground가 `com.todoquest/.MainActivity`인지 확인한다.
4. SurfaceFlinger 접근 가능 환경에서는 `VRI-Splash Screen com.todoquest`가 top visible layer 또는 stale starting_reveal로 남지 않는지 확인한다.
5. 성공하면 `/phases/010-019/12-launch-black-screen-recovery/index.json`, `/phases/index.json`을 completed로 업데이트하고 한국어 summary를 기록한다.
6. phase 11이 여전히 실제 device 교차 검증 부재만으로 blocked라면 phase 11은 임의 완료 처리하지 않는다.

## 금지사항

- custom splash exit listener를 추가하지 마라. 이유: phase 10에서 반복 launch stale splash 회귀 원인으로 제거한 경로다.
- screenshot 없이 semantics 통과만으로 성공 처리하지 마라. 이유: black screen 문제는 접근성 트리와 실제 합성 결과가 불일치할 수 있다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 반복 일정 완료나 보상 로직을 수정하지 마라. 이유: occurrence 단위 멱등성 규칙과 무관한 launch 문제다.
- Android SDK, emulator, JDK를 임의 설치하지 마라. 이유: 개발 도구 준비는 별도 승인된 phase에서만 수행한다.
- 파괴적 명령을 실행하지 마라. 이유: 실제 device 데이터와 저장소 이력을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
