# Step 3: api37-graphics-regression-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/12-launch-black-screen-recovery/index.json`
- `/phases/010-019/13-api37-emulator-compositor-recovery/index.json`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step0.md`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step1.md`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step2.md`
- `/scripts/recover_android_emulator_graphics.ps1`
- `/scripts/diagnose_android_launch.ps1`

## 작업

복구된 API 37.1 AVD에서 전체 suite와 host screenshot 검증을 반복 실행하고 운영 문서와 phase 상태를 정리한다.

- `/docs/DEVELOPMENT.md`에 system baseline, app render capture, raw compositor capture의 판정 경계를 기록한다.
- 같은 cold boot에서 `connectedDebugAndroidTest`를 연속 두 번 실행한다.
- 마지막 suite 뒤 launch diagnostics를 다시 실행해 system baseline과 app screenshot이 모두 non-black인지 확인한다.
- 현재 MainActivity가 SurfaceFlinger composition에 포함되고 `hidden by parent or layer flag`가 아닌지 확인한다.
- 성공하면 phase 13과 phase 12를 `completed`로 갱신한다.
- 안정 API AVD 또는 실제 USB device 교차 검증이 없으면 phase 11은 `blocked`로 유지한다.

## Acceptance Criteria

```powershell
.\scripts\recover_android_emulator_graphics.ps1 -AvdName Pixel_9 -GpuModes software,swangle -RestartRunningAvd -LeaveRunning
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. Acceptance Criteria를 순서대로 실행한다.
2. system baseline과 app screenshot의 pixel 판정, connected app-render artifact를 비교한다.
3. SurfaceFlinger와 activity/window artifact에서 현재 launch transition이 완료됐는지 확인한다.
4. 성공하면 관련 phase metadata를 완료 상태로 업데이트하고 한국어 summary를 기록한다.
5. software와 swangle 모두 system baseline부터 검정이면 step 3과 phase 13을 `blocked`로 기록한다.

## 금지사항

- API 37.1 AVD 통과만으로 실제 device 호환성을 완료 처리하지 마라. 이유: phase 11의 cross-device 요구사항이 남아 있다.
- AVD 또는 SDK를 삭제, 재설치, downgrade하지 마라. 이유: 사용자 환경을 손상하거나 검증 기준을 바꿀 수 있다.
- screenshot threshold를 완화하지 마라. 이유: 실제 검은 화면 회귀를 숨기면 안 된다.
- 기존 테스트를 깨뜨리지 마라.
