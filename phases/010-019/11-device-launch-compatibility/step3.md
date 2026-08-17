# Step 3: device-launch-validation-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/11-device-launch-compatibility/step0.md`
- `/phases/010-019/11-device-launch-compatibility/step1.md`
- `/phases/010-019/11-device-launch-compatibility/step2.md`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/scripts/diagnose_android_launch.ps1`

## 작업

emulator와 실제 USB debugging device 모두에 적용되는 launch 검증 절차를 문서화하고 전체 검증을 수행한다.

- `/docs/DEVELOPMENT.md`에 Android launch 호환성 검증 섹션을 추가한다.
- 문서에 다음 내용을 포함한다.
  - 검은 화면 의심 시 `MainActivityLaunchSmokeTest`와 `scripts/diagnose_android_launch.ps1`를 먼저 실행한다.
  - 성공 기준은 `am start -W`의 `Status: ok`, screenshot에서 첫 화면 표시, smoke test 통과다.
  - SurfaceFlinger 접근 가능 환경에서는 `VRI-Splash Screen com.todoquest`가 top visible layer로 남지 않아야 한다.
  - 실제 device에서는 SurfaceFlinger 출력이 제한될 수 있으므로 screenshot과 `am start -W`, process/window 상태를 우선한다.
  - Android preview 또는 16KB page size AVD는 고위험 호환성 검증 대상으로 취급하고, 안정 API AVD 또는 실제 device에서도 반드시 교차 확인한다.
- 전체 Gradle 검증과 launch 진단을 실행한다.
- 모든 step이 완료되면 `/phases/010-019/11-device-launch-compatibility/index.json`와 `/phases/index.json`을 완료 상태로 업데이트한다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1 -SkipInstall
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없으면 connected test와 진단 스크립트 검증을 `blocked`로 기록하고, unit/lint/assemble 결과는 summary에 남긴다.
3. 연결 기기가 있으면 smoke test와 진단 스크립트 screenshot이 같은 pass/fail 판단을 내리는지 확인한다.
4. 검은 화면이 계속 재현되면 SurfaceFlinger, screenshot, logcat artifact 경로와 함께 step 3을 `error`로 기록한다.
5. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙 위반이 없는지 확인한다.
6. 성공하면 step 3과 phase index, global phases index를 `completed`로 업데이트하고 한국어 summary를 기록한다.

## 금지사항

- 실제 device 검증 없이 emulator만으로 호환성 완료를 단정하지 마라. 이유: 이번 phase의 목적은 emulator와 실제 device 공통 launch 안정성이다.
- SurfaceFlinger 접근 실패를 실제 화면 실패로 단정하지 마라. 이유: production device 권한 제한일 수 있다.
- Android SDK, emulator, JDK를 임의 설치하지 마라. 이유: 개발 도구 준비는 별도 승인된 phase에서만 수행한다.
- 파괴적 명령을 실행하지 마라. 이유: 실제 device 데이터와 저장소 이력을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
