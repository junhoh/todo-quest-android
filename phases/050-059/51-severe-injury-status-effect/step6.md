# Step 6: document-and-validate-severe-injury

## 읽어야 할 파일

- /AGENTS.md
- /docs/PRD.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /docs/UI_GUIDE.md
- /docs/DEVELOPMENT.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step0.md
- /phases/050-059/51-severe-injury-status-effect/step1.md
- /phases/050-059/51-severe-injury-status-effect/step2.md
- /phases/050-059/51-severe-injury-status-effect/step3.md
- /phases/050-059/51-severe-injury-status-effect/step4.md
- /phases/050-059/51-severe-injury-status-effect/step5.md

## 작업

구현된 계약을 문서화하고 전체 회귀, lint, build, connected test, Room migration, harness 자체 검증을 실행한다.

1. ARCHITECTURE.md와 필요한 게임 설계/ADR/UI 문서에 상태이상 확장 구조, Room v13 schema, occurrence 단위 회복 멱등성, 패배 lifecycle event 순서, AppClock 만료 reconciliation, UI 접근성 계약을 구현과 일치하게 기록한다.
2. 문서와 사용자 노출 문구에서 과거 즉시 부활/되살아남 흐름을 전투 불능/응급 회복/중상으로 교체한다. 단, 역사적 migration 설명이나 물리 컬럼명처럼 호환성에 필요한 식별자는 맥락을 명확히 한 뒤 유지할 수 있다.
3. 요구사항별 테스트 matrix를 점검하고 누락 테스트를 먼저 추가한다: HP=0/음수, 20% 유효 스탯과 base 불변, 최소 1, 중상 HP 50%, 회전/재진입, 재시작 지속성, 서로 다른 occurrence 3회, duplicate/rollback, 24시간 경계, 재패배 refresh/비중첩, 해제 후 스탯, XP/재화 보상 불변, 기존 완료·실패·몬스터 공격, Room 12→13 migration.
4. 전체 검증을 실행하고 실패 원인을 수정한다. Android 도구나 emulator가 사용할 수 없으면 임의 설치하지 말고 phase를 blocked로 기록한다. 환경과 무관한 코드 실패를 장치 부재로 오인하지 않는다.
5. 모든 검증이 성공한 경우 phase index의 step 6과 phase 상태를 completed로 갱신하고 한국어 summary에 핵심 변경과 실행한 검증을 기록한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp-phase51
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
~~~

## 검증 절차

1. 각 요구사항을 해당 unit/instrumentation/migration 테스트 이름에 대응시키고 assertion 없는 형식적 테스트가 없는지 검토한다.
2. 전체 unit test, lint, debug/app test APK build, connected tests를 순서대로 실행한다.
3. harness 스크립트 회귀와 phase manager validation을 실행한다.
4. git status --short, git diff --check, 문서 링크와 rg -n "부활|되살아남|PLAYER_REVIVING|battle_revive" app/src docs 결과를 확인한다.
5. phase index summary에 Room version, 핵심 멱등성 키, event 순서, UI/접근성, 실제 실행한 검증을 기록한다.

## 금지사항

- 실패한 테스트를 삭제, ignore, 완화하거나 production 검증을 우회하지 마라.
- Android 도구를 임의로 설치하거나 사용자 환경 변수를 영구 변경하지 마라.
- unrelated code나 기존 사용자 변경을 되돌리지 마라.
- destructive migration 또는 파괴적 git 명령을 실행하지 마라.
- 검증이 남았는데 phase를 completed로 표시하지 마라.
