# Step 0: emulator-compositor-preflight

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/12-launch-black-screen-recovery/index.json`
- `/scripts/diagnose_android_launch.ps1`

## 작업

API 37.1 preview AVD의 전체 compositor 실패와 Todo Quest launch 실패를 분리하고, 기존 AVD를 영구 변경하지 않는 graphics backend 복구 경로를 추가한다.

- `/scripts/diagnose_android_launch.ps1`를 수정한다.
  - `-Serial <serial>`로 특정 device만 진단할 수 있게 한다.
  - 앱 실행 전에 `android.settings.SETTINGS`를 열어 system baseline screenshot을 저장하고 brightness를 측정한다.
  - 결과에 `failureDomain=environment|launch|render|none`, `baselineScreenshotOk`, `baselineBlackPixelRatio`, `baselineAverageBrightness`를 기록한다.
  - baseline부터 순수 검정이면 `EnvironmentCompositorBlack`, baseline은 정상인데 앱 screenshot만 검정이면 `AppCompositionBlack`으로 구분한다.
  - emulator version, AVD 이름, system image fingerprint, GPU renderer, HWC layer 수, transition root 수, 현재 MainActivity layer visibility를 artifact에 기록한다.
- `/scripts/recover_android_emulator_graphics.ps1`를 추가한다.
  - 시그니처는 `-AvdName <name> [-GpuModes software,swangle] [-RestartRunningAvd] [-LeaveRunning] [-BootTimeoutSeconds 240]`로 한다.
  - Android SDK의 `emulator.exe`를 사용해 `-no-snapshot-load`, `-no-snapshot-save`로 cold boot한다.
  - `software`부터 진단하고 실패할 때만 다음 backend로 진행한다.
  - 선택한 AVD 외의 emulator나 실제 USB device를 종료하지 않는다.

## Acceptance Criteria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recover_android_emulator_graphics.ps1 -AvdName Pixel_9 -GpuModes software,swangle -RestartRunningAvd -LeaveRunning
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. 복구 스크립트가 대상 AVD 이름을 확인한 뒤에만 재시작하는지 확인한다.
2. system baseline이 검정이면 앱 install/launch 실패와 분리된 환경 오류가 기록되는지 확인한다.
3. backend 하나가 통과하면 해당 emulator를 다음 step에서 사용할 수 있게 유지한다.
4. 성공하면 phase index의 step 0을 `completed`로 업데이트하고 한국어 summary를 기록한다.
5. 모든 backend에서 baseline이 검정이면 step 0을 `blocked`로 기록하고 다음 step으로 넘어가지 않는다.

## 금지사항

- AVD에 `-wipe-data`를 사용하지 마라. 이유: 사용자 emulator 데이터를 삭제할 수 있다.
- AVD `config.ini`나 snapshot 파일을 수정 또는 삭제하지 마라. 이유: 복구는 비영구 실행 옵션으로 제한한다.
- Android SDK, emulator, system image를 설치하거나 업데이트하지 마라. 이유: 개발 도구 변경은 별도 승인 phase 범위다.
- production launch 코드를 수정하지 마라. 이유: 현재 증거는 앱보다 system compositor 실패를 가리킨다.
- 기존 테스트를 깨뜨리지 마라.
