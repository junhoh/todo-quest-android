# Step 2: launch-render-probe-separation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/010-019/13-api37-emulator-compositor-recovery/index.json`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step0.md`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step1.md`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/scripts/diagnose_android_launch.ps1`

## 작업

connected test는 앱 window의 실제 렌더를 검증하고, 전체 device compositor 검증은 host diagnostics가 담당하도록 probe 경계를 분리한다.

- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`를 수정한다.
  - 기존 두 test를 `launchRendersInitialCalendar` 하나로 합쳐 MainActivity launch 횟수를 줄인다.
  - `dumpsys window` 문자열 기반 foreground 판정을 제거한다.
  - `ActivityScenario`가 `RESUMED`이고 Activity decor view가 attached, shown 상태인지 확인한다.
  - semantics 검증 후 Compose root를 `captureToImage()`로 캡처해 기존 black pixel threshold로 검사한다.
  - API 23~25에서는 Activity decor view를 Canvas로 그리는 fallback을 사용한다.
  - 앱 렌더 PNG와 device metadata를 additional test output에 저장한다.
  - raw device `screencap` 호출은 제거한다.
- raw screenshot과 system baseline은 `/scripts/diagnose_android_launch.ps1`에서 계속 hard gate로 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1 -SkipInstall
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. connected test의 앱 렌더 artifact가 non-black인지 확인한다.
2. host diagnostics의 system baseline과 app raw screenshot이 각각 별도 필드로 판정되는지 확인한다.
3. Activity teardown이 `PAUSED`에서 timeout되지 않는지 확인한다.
4. 성공하면 phase index의 step 2를 `completed`로 업데이트하고 한국어 summary를 기록한다.

## 금지사항

- semantics만 통과했다고 app render 성공으로 처리하지 마라. 이유: Compose surface pixel 검증이 필요하다.
- host raw screenshot 실패를 connected app-render assertion으로 다시 합치지 마라. 이유: app과 emulator compositor 실패를 분리해야 한다.
- production MainActivity, theme, reward 또는 recurrence 로직을 수정하지 마라. 이유: 이 step은 androidTest probe 경계만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
