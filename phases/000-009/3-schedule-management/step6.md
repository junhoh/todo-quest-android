# Step 6: schedule-verification

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/phases/000-009/3-schedule-management/step1.md`
- `/phases/000-009/3-schedule-management/step2.md`
- `/phases/000-009/3-schedule-management/step3.md`
- `/phases/000-009/3-schedule-management/step4.md`
- `/phases/000-009/3-schedule-management/step5.md`
- `/scripts/execute.py`
- `/scripts/test_execute.py`

## 작업

일정관리 phase 전체를 검증하고, 남은 레이어 위반이나 테스트 누락을 정리한다. 새 기능 구현은 추가하지 않는다.

검증 대상은 다음과 같다.

- 순수 Kotlin unit test가 반복 occurrence 계산과 보상 정책을 검증한다.
- Repository/UseCase test가 completion log와 reward ledger 멱등성을 검증한다.
- ViewModel test가 UI state와 event 흐름을 검증한다.
- Compose UI instrumentation test가 실제 화면에서 생성과 완료 흐름을 검증한다.
- Compose UI가 DAO, Room database, AlarmManager, WorkManager를 직접 호출하지 않는다.
- Google Calendar 연동, 로그인, 서버 동기화, 알림 예약 구현이 포함되지 않는다.

필요하면 문서나 phase 상태의 오탈자만 수정한다. 기능 코드 변경이 필요하면 해당 원인 step으로 돌아가 수정하고 다시 이 step을 실행한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
adb devices
.\gradlew.bat connectedDebugAndroidTest
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 연결된 Android 기기 또는 emulator가 없으면 `connectedDebugAndroidTest`만 생략하지 말고 step 5가 `blocked`인지 확인한다.
3. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 다시 확인한다.
4. 모든 step이 `completed`이거나 실제 화면 테스트 환경 문제만 `blocked`인지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`과 `/phases/index.json`의 상태와 결과 필드를 업데이트한다.

## 금지사항

- 새 기능을 이 step에서 추가하지 마라. 이유: verification step은 검증과 상태 정리만 담당한다.
- 연결 기기가 없는데 `connectedDebugAndroidTest`를 통과한 것처럼 기록하지 마라. 이유: 실제 화면 테스트 요구사항을 왜곡한다.
- `rm -rf`, `git reset --hard`, `git push --force`를 실행하지 마라. 이유: AGENTS.md의 CRITICAL 개발 프로세스 규칙을 위반한다.
- 사용자 변경으로 보이는 기존 변경사항을 되돌리지 마라. 이유: 작업 트리에는 다른 변경이 있을 수 있다.
- 기존 테스트를 깨뜨리지 마라.
