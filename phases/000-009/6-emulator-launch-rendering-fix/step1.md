# Step 1: emulator-rendering-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/000-009/5-emulator-launch-diagnostics/index.json`
- `/phases/000-009/5-emulator-launch-diagnostics/step0.md`
- `/phases/000-009/5-emulator-launch-diagnostics/step1.md`
- `/phases/000-009/6-emulator-launch-rendering-fix/index.json`
- `/phases/000-009/6-emulator-launch-rendering-fix/step0.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## 작업

Step 0의 theme와 splash screen 수정이 실제 emulator에서 검은 화면을 해결했는지 검증한다. 검증은 Compose semantics 통과만으로 끝내지 말고 screenshot 픽셀, SurfaceFlinger 레이어, 기존 instrumentation 흐름까지 확인한다.

- 연결된 Android 기기를 확인한다.
  - `adb devices`에서 emulator 또는 실제 기기가 `device` 상태여야 한다.
  - 연결 기기가 없으면 이 step을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
- 전체 검증 명령을 실행한다.
  - `MainActivityLaunchSmokeTest.initialScreenDisplaysCoreCalendarSemantics`가 통과해야 한다.
  - `MainActivityLaunchSmokeTest.launchScreenshotIsNotBlackScreen`가 통과해야 한다.
  - 기존 `CalendarScreenTest`, `CalendarDayIndicatorTest`도 통과해야 한다.
- `connectedDebugAndroidTest` 성공 후 앱을 직접 실행하고 screenshot을 저장한다.
  - `adb shell am force-stop com.todoquest`
  - `adb shell monkey -p com.todoquest 1`
  - `Start-Sleep -Seconds 3`
  - `cmd /c "adb exec-out screencap -p > app\build\todoquest-current-screencap.png"`
- 저장된 `app/build/todoquest-current-screencap.png`에서 앱 영역에 `Todo Quest`, 캘린더, `Add` 버튼이 보이는지 확인한다.
- `adb shell dumpsys SurfaceFlinger --layers`를 확인한다.
  - `VRI-Splash Screen com.todoquest`가 composition list 상단 visible 레이어로 남아 있으면 실패다.
  - `VRI-com.todoquest/com.todoquest.MainActivity`가 `hidden by parent or layer flag`로 남아 있으면 실패다.
- 성공하면 blocked였던 diagnostic phase도 해결 상태로 정리한다.
  - `/phases/000-009/5-emulator-launch-diagnostics/index.json`의 step 1을 `completed`로 바꾸고 `blocked_at`, `blocked_reason`을 삭제한다.
  - 해당 step 1의 `summary`에는 screenshot이 더 이상 검정이 아니며 splash/theme 수정으로 해결되었다고 한국어로 기록한다.
  - `/phases/index.json`의 `5-emulator-launch-diagnostics` 상태를 `completed`로 바꾸고 `blocked_at`, `blocked_reason`을 삭제한다.
  - `/phases/000-009/6-emulator-launch-rendering-fix/index.json`의 step 1을 `completed`로 바꾸고 한국어 `summary`를 기록한다.
  - `/phases/index.json`의 `6-emulator-launch-rendering-fix` 상태를 `completed`로 바꾸고 `completed_at`을 기록한다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
adb shell am force-stop com.todoquest
adb shell monkey -p com.todoquest 1
Start-Sleep -Seconds 3
cmd /c "adb exec-out screencap -p > app\build\todoquest-current-screencap.png"
adb shell dumpsys SurfaceFlinger --layers
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기 연결 상태를 확인한다.
2. 연결된 Android 기기가 없으면 step 1을 `blocked`로 기록한다.
3. 연결된 Android 기기가 있으면 Acceptance Criteria 명령을 실행한다.
4. `connectedDebugAndroidTest` 실패 시 Gradle report, logcat, `main-activity-launch.png`를 확인한다.
5. 수동 screenshot과 SurfaceFlinger dump로 splash layer가 사라지고 `MainActivity` surface가 표시되는지 확인한다.
6. 성공하면 phase 5와 phase 6 메타데이터를 완료 상태로 업데이트한다.
7. 3회 수정 후에도 실패하면 step 1을 `error`로 바꾸고 `failed_at`, `error_message`에 실패한 명령과 핵심 로그를 기록한다.

## 금지사항

- screenshot 없이 semantics 통과만으로 성공 처리하지 마라. 이유: blocked 원인은 접근성 트리와 실제 화면 합성 결과가 불일치한 문제였다.
- `MainActivityLaunchSmokeTest`를 건너뛰거나 exclude하지 마라. 이유: 이 테스트가 검은 화면 재현과 회귀 방지를 담당한다.
- 연결 기기가 없는데 `connectedDebugAndroidTest`를 성공으로 기록하지 마라. 이유: 실제 emulator 실행 검증 요구사항을 왜곡한다.
- SurfaceFlinger에서 splash layer가 계속 visible인데 완료 처리하지 마라. 이유: 사용자가 보는 검정 화면 원인이 남아 있다.
- Android 도구나 SDK를 임의 설치하지 마라. 이유: 개발 환경 준비는 별도 승인된 phase에서만 수행한다.
- 파괴적 명령을 실행하지 마라. 이유: 저장소 이력과 사용자 작업을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
