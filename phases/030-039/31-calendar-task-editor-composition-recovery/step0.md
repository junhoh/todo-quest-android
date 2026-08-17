# Step 0: 모달 합성 상태 판정

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/android_launch_common.ps1`
- `/scripts/test_android_launch_classification.ps1`
- `/phases/010-019/13-api37-emulator-compositor-recovery/index.json`
- `/phases/030-039/31-calendar-task-editor-composition-recovery/index.json`

## 작업

Calendar task editor의 Compose hierarchy 생성과 최종 Window/Surface 표시를 분리해서 판정하는 PowerShell 공용 로직을 테스트 우선으로 추가한다.

- `/scripts/test_calendar_modal_classification.ps1`을 먼저 추가하고 다음 fixture를 검증한다.
  - `mHasSurface=true`, `isReadyForDisplay=true`지만 `Surface: shown=false`, `mLastHidden=true`, `mShownAlpha=0.0`이고 HWC 출력에 dialog layer가 없는 경우 `DialogCompositionHidden`이다.
  - dialog Window가 표시되고 matching SurfaceFlinger layer가 HWC 출력에 포함되며 screenshot 변화가 있으면 성공이다.
  - 추가 버튼 후보가 0개 또는 2개 이상인 경우 각각 `AddButtonNotFound`, `AddButtonAmbiguous`다.
  - 실제 탭 뒤 task editor hierarchy가 없으면 `DialogNotCreated`다.
- `/scripts/android_launch_common.ps1`에 다음 시그니처 수준의 함수를 구현한다.
  - `Get-CalendarModalCompositionMetrics -WindowText <string> -SurfaceFlingerText <string> -Package <string>`
  - `Get-CalendarModalFailureClassification -AddButtonMatchCount <int> -ModalHierarchyFound <bool> -DialogWindowReady <bool> -DialogSurfaceShown <bool> -DialogLayerVisible <bool> -ScreenshotChanged <bool> -SurfaceFlingerRestricted <bool>`
- 판정 결과는 `Domain`, `Reason`을 제공한다. 버튼 탐색과 dialog 미생성은 `interaction`, 숨은 Surface는 `render`, 성공은 `none`으로 분류한다.
- SurfaceFlinger 접근이 가능한 emulator에서는 dialog layer 표시를 필수로 하고, 접근이 제한된 실제 기기에서는 Window 표시와 raw screenshot 변화가 모두 있어야 성공한다.
- 기존 launch 분류 함수와 결과를 변경하지 않는다.

## Acceptance Criteria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_android_launch_classification.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_calendar_modal_classification.ps1
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. 신규 fixture 테스트가 함수 구현 전 실패하는 것을 확인한다.
2. 최소 판정 로직을 구현하고 신규·기존 PowerShell 테스트를 모두 실행한다.
3. `AGENTS.md`의 비파괴 명령과 개발 도구 정책을 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 생성 함수와 판정 기준을 한국어 `summary`로 기록한다.

## 금지사항

- launch 성공 판정의 기존 의미를 변경하지 마라. 이유: 기존 API 37 launch 복구 계약을 깨뜨릴 수 있다.
- hierarchy 존재만으로 성공 처리하지 마라. 이유: 현재 재현 문제에서 hierarchy는 존재하지만 dialog Surface가 숨겨져 있다.
- production Android 코드를 수정하지 마라. 이유: 이 step은 진단 판정 모듈만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
