# Step 2: 모달 인지 GPU 복구와 종합 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/diagnose_android_launch.ps1`
- `/scripts/recover_android_emulator_graphics.ps1`
- `/scripts/android_launch_common.ps1`
- `/scripts/test_calendar_modal_classification.ps1`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/phases/030-039/31-calendar-task-editor-composition-recovery/index.json`

## 작업

호스트에서 실제 Calendar 추가 버튼을 탭하고 WindowManager·SurfaceFlinger·raw screenshot을 교차 판정하는 진단기를 추가한 뒤 GPU 복구의 선택적 hard gate로 연결한다.

- `/scripts/diagnose_calendar_modal.ps1`을 다음 인터페이스로 추가한다: `-Package`, `-Activity`, `-OutputDir`, `-Serial`, `-SkipBuild`, `-SkipInstall`, `-LeaveRunning`.
- 진단기는 깨끗한 app launch 뒤 UI hierarchy를 dump하고 정확히 하나인 한국어 `추가` node의 가장 가까운 clickable 조상을 찾는다. 보이지 않으면 각 dump 사이에 최대 5회의 제한된 upward swipe만 수행한다.
- 버튼 중앙에 `adb shell input touchscreen -d 0 tap`을 보내고 `할 일 추가`, `제목` hierarchy가 나타날 때까지 최대 5초 기다린다.
- 클릭 전·후 raw screenshot, UI XML, `dumpsys window windows`, `dumpsys SurfaceFlinger --layers`, logcat을 `app/build/launch-diagnostics/calendar-modal/<serial>`에 저장한다.
- `summary.txt`에 `result`, `failureDomain`, `failureReason`, `addButtonMatchCount`, `modalHierarchyFound`, `dialogWindowReady`, `dialogSurfaceShown`, `dialogLayerVisible`, `screenChangedPixelRatio`와 artifact 경로를 기록한다.
- 실패 시 숨은 dialog가 emulator 입력을 계속 점유하지 않도록 앱을 force-stop한다. 성공하고 `-LeaveRunning`일 때만 열린 modal을 유지한다.
- `/scripts/recover_android_emulator_graphics.ps1`에 `-VerifyCalendarTaskEditor` switch를 추가한다. 지정 시 launch와 modal 진단이 모두 통과한 첫 `software`, `swangle` backend만 성공으로 선택한다.
- `/docs/DEVELOPMENT.md`에서 preview compositor의 stale modal screenshot을 hierarchy/connected semantics로 성공 판정하던 문구를 제거하고 새 진단 명령, 결과 필드, `DialogCompositionHidden` 대응을 문서화한다.
- production Compose, ViewModel, Room schema와 공개 앱 API는 변경하지 않는다.

## Acceptance Criteria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_android_launch_classification.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_calendar_modal_classification.ps1
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\scripts\recover_android_emulator_graphics.ps1 -AvdName Pixel_9 -GpuModes software,swangle -RestartRunningAvd -VerifyCalendarTaskEditor -LeaveRunning
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_calendar_modal.ps1 -SkipBuild -SkipInstall -LeaveRunning
.\gradlew.bat connectedDebugAndroidTest
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. PowerShell 판정 테스트와 Android unit/lint/assemble 검증을 실행한다.
2. named `Pixel_9`만 runtime backend별로 재시작하고 launch와 Calendar modal이 모두 통과하는 첫 backend를 유지한다.
3. standalone modal 진단의 raw screenshot과 summary가 `PASS`, `failureReason=none`, visible dialog layer를 기록하는지 확인한다.
4. 전체 connected instrumentation suite와 harness pytest를 실행한다.
5. `/docs/DEVELOPMENT.md`와 실제 명령·artifact 경로가 일치하는지 확인한다.
6. task index와 root phase index를 `completed`로 바꾸고 실제 통과 backend, 테스트 수, 핵심 artifact를 한국어 `summary`로 기록한다.

## 금지사항

- 모든 runtime GPU backend에서 modal 합성이 실패했는데 phase를 완료 처리하지 마라. 이유: 사용자가 보는 회귀가 남아 있다.
- Android SDK, emulator 또는 system image를 설치·업데이트하지 마라. 이유: 구현 phase의 개발 도구 변경은 승인되지 않았다.
- AVD data를 wipe하거나 영구 설정을 변경하지 마라. 이유: 사용자 emulator 상태를 파괴하거나 변형할 수 있다.
- hierarchy나 semantics 통과만으로 modal 표시를 성공 처리하지 마라. 이유: 현재 문제의 false positive 조건이다.
- production UI를 변경하지 마라. 이유: 승인된 범위는 환경·검증 보강이다.
- 기존 테스트를 깨뜨리지 마라.
