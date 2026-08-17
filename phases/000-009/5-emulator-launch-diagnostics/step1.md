# Step 1: diagnostic-artifacts-and-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/000-009/5-emulator-launch-diagnostics/index.json`
- `/phases/000-009/5-emulator-launch-diagnostics/step0.md`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`

## 작업

Step 0에서 추가한 앱 실행 smoke test를 포함해 전체 검증을 수행하고, 검은 화면 증상에 대한 분석 결과를 phase 메타데이터에 정리한다. 이 step은 기능 구현이 아니라 실행 결과 검증, artifact 위치 확인, 후속 판단 기록을 담당한다.

검증할 항목은 다음과 같다.

- `MainActivityLaunchSmokeTest`가 connected test 결과에 포함된다.
- 초기 화면의 `Todo Quest`, `calendar-month-grid`, `task-list`, `add-task-button`가 실제 emulator에서 표시된다.
- screenshot 기반 검은 화면 판정이 통과하거나, 실패 시 저장된 screenshot과 실패 메시지로 원인을 분석할 수 있다.
- 기존 `CalendarScreenTest`와 `CalendarDayIndicatorTest`가 계속 통과한다.
- Gradle test report와 UTP logcat 경로가 생성된다.
- screenshot artifact가 additional output 또는 테스트가 기록한 저장 경로에 남는다.

수동 검은 화면이 계속 보이지만 자동 테스트가 통과하면 summary에 다음 가능성을 기록한다.

- 앱 프로세스와 Compose 렌더링은 정상이다.
- Android emulator 창, graphics renderer, snapshot, host GPU, 또는 화면 갱신 문제일 가능성이 높다.
- 후속 조치 후보는 emulator cold boot, graphics mode 변경, snapshot 삭제, 다른 AVD 실행이다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없어 `connectedDebugAndroidTest`를 실행할 수 없으면 step 1을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. `app/build/reports/androidTests/connected/debug/index.html` 또는 `app/build/outputs/androidTest-results/connected/debug`에서 `MainActivityLaunchSmokeTest` 결과를 확인한다.
4. `app/build/outputs/connected_android_test_additional_output` 또는 테스트 실패 메시지의 screenshot 경로에서 screenshot artifact를 확인한다.
5. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙 위반이 없는지 확인한다.
6. 성공하면 `/phases/000-009/5-emulator-launch-diagnostics/index.json`의 step 1 상태와 결과 필드를 업데이트한다.
7. 두 step이 모두 완료되면 `/phases/index.json`의 `5-emulator-launch-diagnostics` 상태를 `completed`로 업데이트한다.

## 금지사항

- 검증 step에서 앱 기능을 임의로 추가하지 마라. 이유: 검은 화면 분석 범위와 기능 변경 범위가 섞인다.
- 연결 기기가 없는데 `connectedDebugAndroidTest`를 통과한 것처럼 기록하지 마라. 이유: 실제 emulator 실행 검증 요구사항을 왜곡한다.
- 테스트 실패 screenshot과 logcat을 확인하지 않고 원인을 추정하지 마라. 이유: 검은 화면은 앱 크래시, 렌더링 실패, emulator 표시 문제를 구분해야 한다.
- Android 도구가 설치되어 있지 않으면 임의 설치하지 마라. 이유: AGENTS.md의 개발 프로세스 규칙을 위반한다.
- 파괴적 명령을 실행하지 마라. 이유: 저장소 이력과 사용자 작업을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
