# Step 3: schedule-entry-verification

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/phases/index.json`
- `/phases/000-009/4-schedule-entry-completion/index.json`
- `/phases/000-009/4-schedule-entry-completion/step0.md`
- `/phases/000-009/4-schedule-entry-completion/step1.md`
- `/phases/000-009/4-schedule-entry-completion/step2.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`

## 작업

전체 변경 사항을 검증하고 phase 상태를 정리한다. 이 step은 기능 구현보다 검증과 누락 분석 기록에 집중한다.

검증할 항목은 다음과 같다.

- 신규 일정 생성에서 제목, 메모, 시간, 난이도, 카테고리, 반복 설정이 저장되고 목록에 반영된다.
- 반복 일정은 원본 task와 날짜별 occurrence를 분리해서 다루며, 특정 날짜 완료가 반복 원본 전체 완료로 바뀌지 않는다.
- 완료 보상은 같은 occurrence에 대해 중복 지급되지 않는다.
- 특정 날짜에 일정이 여러 개 있으면 월간 캘린더 셀에서 전체 개수와 완료 개수가 표시된다.
- UI 레이어가 Room DAO, AlarmManager, WorkManager를 직접 호출하지 않는다.
- MVP 제외 사항인 Google Calendar 연동이 추가되지 않았다.

추가 누락 분석을 summary에 남긴다.

- 일정 수정/삭제 UI는 기존 Repository 계약이 있으나 현재 화면에 노출되지 않았으므로 후속 phase 대상이다.
- 알림 시간 입력과 예약은 권한/scheduler 설계가 필요하므로 notification phase 대상이다.
- 카테고리 프리셋이나 색상 관리는 MVP 필수 저장 필드는 아니므로 후속 UX 개선 대상이다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

기기 또는 에뮬레이터가 연결되어 있으면 다음도 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기가 없어 `connectedDebugAndroidTest`를 실행할 수 없으면 완료 summary에 미실행 사유를 남긴다.
3. `git diff --check`로 whitespace 오류가 없는지 확인한다.
4. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙 위반이 없는지 확인한다.
5. 모든 step이 완료되면 `/phases/000-009/4-schedule-entry-completion/index.json`의 step 3 상태와 결과 필드를 업데이트한다.
6. 모든 step이 완료되면 `/phases/index.json`의 `4-schedule-entry-completion` 상태를 `completed`로 업데이트한다.

## 금지사항

- 검증 step에서 새 기능을 임의로 추가하지 마라. 이유: 실패 원인과 기능 변경 범위가 섞인다.
- `3-schedule-management` phase의 blocked 상태를 임의로 완료 처리하지 마라. 이유: 기존 phase의 연결 기기 차단 사유는 별도 이력이다.
- Android 도구가 설치되어 있지 않으면 임의 설치하지 마라. 이유: AGENTS.md의 개발 프로세스 규칙을 위반한다.
- 파괴적 명령을 실행하지 마라. 이유: 저장소 이력과 사용자 작업을 손상할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
