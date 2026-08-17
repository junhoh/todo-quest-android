# Step 10: document-and-validate-balance

## 읽어야 할 파일

- /AGENTS.md
- /docs/README.md
- /docs/PRD.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /docs/UI_GUIDE.md
- /docs/DEVELOPMENT.md
- /docs/game-design/character-stats/todo-combat-rewards.md
- /app/src/main/java/com/todoquest/notification/AndroidReminderCapabilityAdapter.kt
- /app/src/main/java/com/todoquest/domain/usecase/CombatRewardPolicy.kt
- /app/src/main/java/com/todoquest/data/local/EquipmentDao.kt
- /app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt
- /phases/050-059/50-notification-and-gameplay-balance/step0.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

실제 구현 signature와 결과를 canonical 문서에 최종 동기화한다. PRD·ADR-017에는 최초 한 번 notification 안내, runtime/settings 분기, reminder 저장 시 settings CTA, exact permission 순차 처리와 거부 독립성을 기록한다. ADR-018·전투 보상 문서에는 v0/v1/v2 처리, 3/20/15 공식, level golden과 APPLIED snapshot 불변을 기록한다. ARCHITECTURE에는 preference store→UseCase→Calendar one-shot event 흐름과 versioned reward catalog·conditional equipment price seed를 반영한다.

UI_GUIDE에는 104dp EXP layout과 Shop 새 가격 표시를, DEVELOPMENT에는 새 unit/Robolectric/connected test matrix와 검증 결과를 기록한다. Room schema v12와 migration chain은 변경되지 않았음을 명시한다. phase index의 모든 이전 step이 completed인지 확인하고 전체 acceptance를 실행한다.

전체 성공 뒤 step 10과 phase를 completed로 갱신하고 한국어 summary에 첫 실행 권한 UX, reward v2, 18종 가격, EXP HUD와 실제 test 수를 기록한다. 연결 기기나 필수 도구가 없으면 설치하지 않고 blocked로 기록한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
~~~

## 검증 절차

1. 실제 public signature, strings, reward golden, catalog 가격과 HUD modifier를 문서 계약에 대조한다.
2. AC를 순서대로 실행하고 실패·오류·skip 없이 통과하는지 확인한다.
3. AGENTS.md의 UI boundary, 보상 멱등성, 권한 독립, 테스트 우선 규칙을 최종 검토한다.
4. 성공하면 step과 phase를 completed로 갱신한다.

## 금지사항

- connected test 미실행을 성공으로 기록하지 마라. 이유: permission dialog, Shop 가격과 HUD 실제 layout이 핵심 acceptance다.
- Android SDK, emulator, system image를 설치하거나 사용자 환경 변수를 변경하지 마라. 이유: 도구 준비는 별도 승인 범위다.
- scripts/execute.py를 직접 호출하지 마라. 이유: Windows 표준 진입점은 run_harness.ps1이다.
- 기존 schema JSON이나 과거 reward snapshot을 수정하지 마라. 이유: 사용자 원천 상태와 migration 역사를 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
