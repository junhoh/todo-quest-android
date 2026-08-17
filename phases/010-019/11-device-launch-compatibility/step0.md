# Step 0: device-launch-smoke-test

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/build.gradle.kts`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/res/values/styles.xml`

## 작업

`MainActivityLaunchSmokeTest`를 emulator 전용이 아니라 실제 Android 기기와 emulator 모두에서 원인 추적이 가능하도록 보강한다. 기존 검은 화면 판정 기준은 유지하고, 실패 메시지와 screenshot artifact만 device-aware로 개선한다.

- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`를 수정한다.
- `InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(...)`를 사용해 다음 device 정보를 수집하는 helper를 추가한다.
  - `getprop ro.product.model`
  - `getprop ro.build.version.sdk`
  - `getprop ro.build.version.release`
  - `getprop ro.build.fingerprint`
  - `getprop ro.kernel.qemu`
  - `getprop debug.hwui.renderer`
- screenshot 파일명은 device serial 또는 model/sdk를 포함해 여러 기기 결과가 덮어써지지 않게 한다.
  - 예: `main-activity-launch-${safeDeviceLabel}.png`
  - 파일명에는 영문, 숫자, `_`, `-`만 남긴다.
- `launchScreenshotIsNotBlackScreen` 실패 메시지에 device model, sdk, release, emulator 여부, renderer, screenshot 경로를 포함한다.
- `initialScreenDisplaysCoreCalendarSemantics`는 기존 semantics assertion을 유지한다.
- black pixel ratio, brightness threshold, center sampling 범위는 완화하지 않는다.
- production source set은 수정하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기가 `device` 상태인지 확인한다.
2. 연결된 Android 기기가 없으면 step 0을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. 연결된 Android 기기가 있으면 Acceptance Criteria 명령을 실행한다.
4. 현재 API 37.1 AVD에서 검은 화면이 재현되어 smoke test가 실패하면 테스트 보강은 성공한 것으로 보고, 실패 메시지에 device 정보와 screenshot 경로가 포함되는지 확인한다.
5. 테스트 보강 자체의 compile 오류가 없고 실패 메시지가 충분하면 step 0을 `completed`로 기록한다. 이 step은 production fix 전 선행 실패를 허용한다.
6. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.

## 금지사항

- 검은 화면 판정 threshold를 완화하지 마라. 이유: 실제 device 회귀를 놓치면 안 된다.
- `MainActivity`나 앱 launch production 코드를 이 step에서 수정하지 마라. 이유: 테스트 보강과 구현 수정을 분리해야 한다.
- emulator 이름 `Pixel_9(AVD)`에 의존하지 마라. 이유: 실제 기기와 다른 AVD에서도 같은 테스트가 동작해야 한다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
