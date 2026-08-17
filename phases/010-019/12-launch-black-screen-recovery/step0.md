# Step 0: launch-diagnostics-hygiene

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/11-device-launch-compatibility/step3.md`
- `/scripts/diagnose_android_launch.ps1`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## 작업

검은 화면 판정 전에 설치 상태, foreground 상태, stale instrumentation task를 분리해서 phase 11의 API 37 AVD 산출물처럼 원인이 섞인 artifact를 줄인다.

- `/scripts/diagnose_android_launch.ps1`를 수정한다.
  - `$SkipInstall` 사용 시 `adb shell pm path <package>`로 앱 설치 여부를 먼저 확인한다.
  - 미설치 상태면 `summary.txt`에 `installOk=False`, `failureReason=PackageNotInstalled`를 기록하고 `am start` 검은 화면 실패로 단정하지 않는다.
  - launch 전 `am force-stop <package>`와 함께 `<package>.test`도 best-effort로 force-stop한다.
  - `am start`는 `-W -S -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n <package>/<activity>`를 사용한다.
  - `dumpsys window` 또는 `dumpsys activity activities`에서 현재 focused/resumed activity가 대상 앱인지 요약 필드로 기록한다.
  - `PackageUpdateActivity`, `<package>.test`, stale `Splash Screen <package>`, `starting_reveal` 흔적은 별도 diagnostic hint로 기록한다.
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`를 수정한다.
  - screenshot 전 shell command로 foreground/top focused window 정보를 수집한다.
  - foreground가 `com.todoquest/.MainActivity`가 아니면 black pixel assertion과 구분되는 실패 메시지를 낸다.
  - 기존 검은 화면 threshold와 semantics assertion은 완화하지 않는다.
- production source set은 수정하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1 -SkipInstall
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없으면 step 0을 `blocked`로 기록하고 `blocked_reason`에 기기 부재를 한국어로 남긴다.
3. `-SkipInstall`이 앱 미설치 때문에 실패하면 black screen이 아닌 설치 상태 오류로 기록되는지 확인한다.
4. screenshot 실패가 계속되면 foreground/window/activity artifact와 black pixel artifact가 같은 원인을 가리키는지 확인한다.
5. 성공하면 `/phases/010-019/12-launch-black-screen-recovery/index.json`의 step 0을 `completed`로 업데이트하고 한국어 summary를 기록한다.

## 금지사항

- 검은 화면 판정 threshold를 완화하지 마라. 이유: 실제 화면 회귀를 놓치면 안 된다.
- production launch 코드를 이 step에서 수정하지 마라. 이유: 진단 정합성과 앱 수정 범위를 분리해야 한다.
- SurfaceFlinger 접근 실패만으로 화면 실패를 단정하지 마라. 이유: 실제 device 권한 제한일 수 있다.
- Android SDK, emulator, JDK를 임의 설치하지 마라. 이유: 개발 도구 준비는 별도 승인된 phase에서만 수행한다.
- 파괴적 명령을 실행하지 마라. 이유: 실제 device 데이터와 저장소 이력을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
