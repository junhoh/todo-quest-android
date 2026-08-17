# Step 1: 실제 탭과 최종 화면 회귀 테스트

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/scripts/recover_android_emulator_graphics.ps1`
- `/phases/030-039/31-calendar-task-editor-composition-recovery/index.json`
- Step 0에서 수정한 `/scripts/android_launch_common.ps1`

## 작업

기존 `CalendarScreenTest.addButtonFromMainActivityOpensKoreanTaskEditor`가 Compose callback과 semantics만 확인하는 사각지대를 제거하고 실제 Android pointer 입력과 최종 display screenshot까지 검증하도록 테스트 우선으로 강화한다.

- production source를 변경하기 전에 강화된 테스트를 작성하고 현재 API 37.1 AVD에서 `task-editor-dialog` hierarchy는 존재하지만 display 검증이 실패하는 재현 결과를 기록한다.
- `add-task-button`을 scroll한 뒤 semantics bounds와 Activity decor의 screen 위치로 실제 화면 좌표를 계산한다.
- `UiAutomation.executeShellCommand`로 `input touchscreen -d 0 tap <x> <y>`를 실행하고 명령 descriptor를 끝까지 읽어 입력 완료를 보장한다. 새 UIAutomator 의존성은 추가하지 않는다.
- 클릭 전에 2개의 안정화 screenshot을 캡처하고 클릭 후 최대 5초 동안 전체 display screenshot을 polling한다.
- 중앙 50% 영역에서 채널 차이가 12 이상인 pixel 비율을 계산한다. 클릭 후 변화율은 `max(0.10, 클릭 전 noise 비율 + 0.08)` 이상이어야 한다.
- 기존 `task-editor-dialog`, `할 일 추가`, `제목`, `new-task-title` semantics assertion도 유지한다.
- 클릭 전·후 PNG와 변화율·좌표·device 정보를 instrumentation `additionalTestOutputDir`에 기록해 실패 원인을 재현할 수 있게 한다.
- 기존 `openAddTaskDialog()`를 사용하는 CRUD 테스트는 이 step에서 변경하지 않는다.
- targeted test를 현재 API 37.1 preview AVD에서 실행해 display 합성 실패를 탐지하고, 실패 메시지와 전후 PNG가 생성되는지 확인한다. 이 재현 실행의 nonzero 종료는 Step 2에서 modal-aware 복구가 구현되기 전까지 예상되는 진단 결과이며 이 step의 AC 실패로 취급하지 않는다.
- 테스트 APK가 정상 컴파일되는지 확인한다. 실제 backend별 성공 gate는 host 진단과 복구가 구현되는 Step 2에서만 판정한다.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebugAndroidTest
git diff --check
```

## 검증 절차

1. 기존 runtime backend에서 새 display assertion이 문제를 재현하는지 확인하고 산출물 경로를 기록한다.
2. 재현 결과가 `task-editor-dialog` hierarchy assertion 이후 `changeRatio=0.0000` display assertion에서 실패했는지 확인한다.
3. Android test APK를 컴파일하고 production source가 변경되지 않았는지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 실제 입력·화면 검증 및 실패 산출물을 한국어 `summary`로 기록한다.

## 금지사항

- Compose `performClick()`으로 실제 입력 검증을 대체하지 마라. 이유: callback만 실행되어 WindowManager 입력 경로를 우회한다.
- screenshot 변화 임계치를 낮춰 현재 숨은 dialog frame을 통과시키지 마라. 이유: 사용자가 보는 최종 합성을 검증해야 한다.
- 앱의 `AlertDialog`를 인앱 overlay로 교체하지 마라. 이유: 승인된 범위는 환경·검증 보강이다.
- 이 step에서 runtime backend를 성공으로 판정하지 마라. 이유: launch-only 복구에는 아직 modal composition hard gate가 없다.
- AVD의 data를 wipe하거나 `config.ini`, snapshot을 변경하지 마라. 이유: 복구는 비영구 runtime option으로 제한한다.
- 기존 테스트를 깨뜨리지 마라.
