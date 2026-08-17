# Step 3: 문서 동기화와 전체 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/phases/030-039/30-calendar-battle-progress-hud/index.json`

## 작업

실제 구현 결과를 프로젝트 문서와 동기화하고 전체 Android·harness 검증을 실행한다.

- Calendar 순서를 `Battle Map의 상단 진행 HUD → 월간 캘린더 → 선택 날짜 일정 목록`으로 갱신하고 제거된 상단 정보 Header와 유지되는 일정 목록 날짜 문맥을 구분해 기록한다.
- Battle Map layer tree에 독립 HUD overlay, theme 기반 중립 색상, 통합 TalkBack semantics와 안전한 XP 진행률 규칙을 반영한다.
- 월간 캘린더 요일 머리글과 cell offset이 Sunday-first인 사실을 문서화한다.
- loading, XP 0, 필요 XP 0/음수, XP 초과, 골드 0/큰 값, 320dp, 가로 화면, 밝은·어두운 배경, 몬스터 0·1·다수와 dark mode가 unit test·Compose test·Preview 중 어디에서 검증되는지 `DEVELOPMENT.md`에 기록한다.
- Preview 함수는 실제 ViewModel이나 database 없이 sample data만 사용하고 `assembleDebug`에서 컴파일되어야 한다. 연결 Compose test에서 같은 component가 렌더링되는지 확인한다.
- 연결 emulator에서 전체 UI test와 특히 `CalendarScreenTest#addButtonFromMainActivityOpensKoreanTaskEditor`를 통과시킨다. 가능하면 세로·가로 앱 화면에서 HUD와 actor 비중첩, map→calendar 간격, scroll, 추가 모달을 최종 확인한다.
- Android 도구가 없다면 설치하지 말고 blocked 처리한다. 연결 emulator가 없으면 나머지 결과를 보존하되 `connectedDebugAndroidTest` 요구 때문에 blocked로 기록한다.
- 전체 성공 시 step 3, phase top-level과 root `phases/index.json`의 phase 30 항목을 `completed`로 갱신하고 한국어 `summary`를 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-30-hud
git diff --check
```

## 검증 절차

1. 모든 AC를 실행하고 unit, lint, assemble, connected UI와 harness pytest 성공을 확인한다.
2. AGENTS의 UI/Repository 경계, occurrence 보상 멱등성, 반복 원본/발생분 분리, 한국어 resource와 권한 비의존성을 최종 확인한다.
3. 제거된 Header의 고정 공간과 중복 날짜가 남지 않고 Calendar CRUD와 추가 모달이 동작하는지 확인한다.
4. step 3, phase index와 root phase status를 완료 처리한다.

## 금지사항

- 검증 실패를 무시하고 phase를 completed 처리하지 마라. 이유: 완료 상태는 모든 AC 성공을 의미한다.
- Android SDK/JDK를 임의 설치하거나 사용자 환경 변수를 영구 변경하지 마라. 이유: AGENTS 개발 프로세스 규칙이다.
- HUD 범위를 체력바, 전투 효과, Stage UI 또는 새 map 자산 구현으로 확장하지 마라. 이유: 이번 phase는 플레이어 진행 HUD와 Calendar 정렬 범위다.
- 기존 테스트를 깨뜨리지 마라.
