# Step 2: launch-diagnostics-script

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/11-device-launch-compatibility/step0.md`
- `/phases/010-019/11-device-launch-compatibility/step1.md`
- `/scripts/run_harness.ps1`

## 작업

실제 device와 emulator 양쪽에서 launch 검은 화면 원인을 같은 방식으로 수집할 수 있는 PowerShell 진단 스크립트를 추가한다.

- `/scripts/diagnose_android_launch.ps1`를 추가한다.
- 스크립트는 repository root를 script 위치 기준으로 계산한다.
- 파라미터를 제공한다.
  - `[string]$Package = "com.todoquest"`
  - `[string]$Activity = ".MainActivity"`
  - `[string]$OutputDir = "app\build\launch-diagnostics"`
  - `[switch]$SkipInstall`
- `adb devices`에서 `device` 상태인 serial을 모두 대상으로 실행한다.
- `$SkipInstall`이 없으면 각 serial에 대해 `.\gradlew.bat installDebug`를 한 번 실행한 뒤 launch 진단을 수행한다.
- 각 serial별 output directory는 filesystem-safe 이름을 사용한다.
- 각 serial에서 다음 artifact를 저장한다.
  - `device.txt`: serial, model, sdk, release, fingerprint, emulator 여부, renderer 관련 getprop
  - `am-start.txt`: `adb -s <serial> shell am start -W -n <package>/<activity>` 출력
  - `pid.txt`: `adb -s <serial> shell pidof <package>` 출력
  - `screen.png`: `adb -s <serial> exec-out screencap -p`
  - `window-visible.txt`: `adb -s <serial> shell dumpsys window visible`
  - `surfaceflinger-layers.txt`: 가능한 경우 `adb -s <serial> shell dumpsys SurfaceFlinger --layers`
  - `logcat.txt`: `adb -s <serial> logcat -d -t 500`
- SurfaceFlinger 권한이 제한되거나 일부 명령이 실패해도 나머지 artifact 수집은 계속하고, 실패 사유를 `summary.txt`에 기록한다.
- screenshot 중심 영역의 black pixel ratio와 average brightness를 계산해 `summary.txt`에 기록한다.
  - 계산 기준은 `MainActivityLaunchSmokeTest`와 동일하게 밝기 `(R + G + B) / 3`, black threshold `16`, black ratio failure `0.95`, average brightness failure `24`를 사용한다.
  - PowerShell에서 PNG decode가 어려우면 `System.Drawing.Bitmap`을 사용한다.
- `summary.txt`는 device별 pass/fail과 주요 판단을 사람이 바로 읽을 수 있게 쓴다.

## Acceptance Criteria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1 -SkipInstall
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. `adb devices`에서 연결된 Android 기기를 확인한다.
2. 연결 기기가 없으면 스크립트가 명확한 오류 메시지와 non-zero exit code로 종료하는지 확인한다.
3. 연결 기기가 있으면 Acceptance Criteria 명령을 실행한다.
4. `app/build/launch-diagnostics/<serial>/summary.txt`와 `screen.png`가 생성되는지 확인한다.
5. `screen.png`가 검은 화면이면 `summary.txt`가 fail로 기록하고 SurfaceFlinger/logcat artifact 경로를 남기는지 확인한다.
6. 성공하면 step 2 상태를 `completed`로 바꾸고, 한국어 summary에 추가된 스크립트와 artifact 위치를 기록한다.

## 금지사항

- 스크립트에서 Android SDK, emulator, JDK를 설치하지 마라. 이유: 개발 도구 설치는 별도 승인된 phase에서만 수행한다.
- `adb uninstall`, `pm clear`, `rm -rf`를 실행하지 마라. 이유: 실제 device 사용자 데이터를 손상할 수 있다.
- 실제 device에서 SurfaceFlinger 접근 실패를 전체 실패로 단정하지 마라. 이유: production device는 일부 dumpsys 출력이 제한될 수 있다.
- 기존 테스트를 깨뜨리지 마라.
