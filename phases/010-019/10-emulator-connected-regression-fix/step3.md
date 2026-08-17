# Step 3: emulator-regression-verification

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/010-019/10-emulator-connected-regression-fix/index.json`
- `/phases/010-019/10-emulator-connected-regression-fix/step0.md`
- `/phases/010-019/10-emulator-connected-regression-fix/step1.md`
- `/phases/010-019/10-emulator-connected-regression-fix/step2.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## 작업

Step 0-2 변경이 실제 연결된 emulator에서 전체 Gradle 검증과 수동 렌더링 검증을 통과하는지 확인한다. 검증은 Compose semantics 통과만으로 끝내지 말고 screenshot 픽셀, SurfaceFlinger 레이어, 전체 instrumentation suite까지 확인한다.

- 연결된 Android 기기를 확인한다.
  - `adb devices`에서 emulator 또는 실제 기기가 `device` 상태여야 한다.
  - 연결 기기가 없으면 이 step을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
- 전체 Gradle 검증을 실행한다.
  - unit test, lint, assembleDebug, connectedDebugAndroidTest가 모두 통과해야 한다.
  - sandbox 네트워크 제한으로 Gradle wrapper나 dependency 다운로드가 실패하면 동일 명령을 승인된 외부 권한으로 재실행한다.
- connected test 성공 후 앱을 직접 실행하고 screenshot을 저장한다.
  - `adb shell am force-stop com.todoquest`
  - `adb shell monkey -p com.todoquest 1`
  - `Start-Sleep -Seconds 3`
  - `cmd /c "adb exec-out screencap -p > app\build\todoquest-current-screencap.png"`
- 저장된 `app/build/todoquest-current-screencap.png`에서 앱 영역에 `Todo Quest`, 캘린더, `Add` 버튼이 보이는지 확인한다.
- `adb shell dumpsys SurfaceFlinger --layers`를 확인한다.
  - `VRI-Splash Screen com.todoquest`가 composition list 상단 visible 레이어로 남아 있으면 실패다.
  - `VRI-com.todoquest/com.todoquest.MainActivity`가 `hidden by parent or layer flag` 또는 window `shown=false`, alpha 0 상태로 남아 있으면 실패다.
- 기존 blocked 메타데이터 중 디바이스 부재로 남은 항목이 현재 emulator 검증으로 해소되는지 확인한다.
  - `/phases/index.json`의 `3-schedule-management`가 아직 `blocked`이고 사유가 연결 기기 부재라면 `completed`로 바꾸고 `completed_at`을 기록한다.
  - `/phases/000-009/3-schedule-management/index.json`의 step 6 summary가 연결 기기 부재로 UI test blocked를 언급하면, 현재 connected instrumentation 통과 사실을 반영해 해당 blocked 사유를 제거하고 한국어 summary를 갱신한다.
- 이 phase 완료 시 `/phases/010-019/10-emulator-connected-regression-fix/index.json`과 `/phases/index.json`을 완료 상태로 갱신한다.

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
2. 연결된 Android 기기가 없으면 step 3을 `blocked`로 기록한다.
3. 연결된 Android 기기가 있으면 Acceptance Criteria 명령을 실행한다.
4. `connectedDebugAndroidTest` 실패 시 Gradle report, generated logcat, screenshot 저장 경로를 확인한다.
5. 수동 screenshot과 SurfaceFlinger dump로 splash layer가 사라지고 `MainActivity` surface가 표시되는지 확인한다.
6. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.
7. 성공하면 step 3과 phase를 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.
8. 3회 수정 후에도 실패하면 step 3을 `error`로 바꾸고 `failed_at`, `error_message`에 실패한 명령과 핵심 로그를 기록한다.

## 금지사항

- screenshot 없이 semantics 통과만으로 성공 처리하지 마라. 이유: 이전 blocked 원인은 접근성 트리와 실제 화면 합성 결과가 불일치한 문제였다.
- `connectedDebugAndroidTest`를 건너뛰거나 일부 테스트만 성공으로 완료 처리하지 마라. 이유: 이번 회귀는 전체 suite 누적 실행에서 재현된다.
- SurfaceFlinger에서 splash layer가 계속 visible인데 완료 처리하지 마라. 이유: 사용자가 보는 검정 화면 원인이 남아 있다.
- Android SDK, emulator, JDK를 임의 설치하지 마라. 이유: 개발 환경 준비는 별도 승인된 phase에서만 수행한다.
- 파괴적 명령을 실행하지 마라. 이유: 저장소 이력과 사용자 작업을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
