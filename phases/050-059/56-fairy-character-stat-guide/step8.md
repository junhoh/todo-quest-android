# Step 8: synchronize-fairy-stat-guide-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step7.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

구현된 계약을 문서와 최종 검증에 동기화한다.

- `PRD.md`에 `Character Stat Allocation Guide v1` 승인 범위를 추가한다. 신규 설치(DB 파일 없음)만 첫 Character 진입에 자동 표시하고 기존 설치는 제외하며, 모든 사용자는 도움말로 재열람할 수 있음을 명시한다.
- `ARCHITECTURE.md`에 `DB 존재 사전 캡처 → SharedPreferences CharacterGuideRepository → Prepare/Acknowledge UseCase → CharacterViewModel state → Compose Dialog` 흐름을 기록한다. Room v14와 stat transaction이 불변임을 명시한다.
- `ADR.md`에 다음 번호인 `ADR-022`를 추가해 신규 설치 eligibility의 일회성 저장, 기존 사용자 제외, 확인 전 process 종료 시 재표시, 수동 도움말과 automatic acknowledged 분리, 오류 격리를 결정으로 기록한다.
- `UI_GUIDE.md` Character 패널에 96dp 요정 Dialog, 확정 한국어 대사, primary scroll, Base stats 도움말, 320dp·font scale 2.0·TalkBack 계약을 기록한다.
- `docs/art/npc/README.md`에 fairy canonical PNG·JSON, 플레이어 reference의 비편집 역할, runtime byte identity, 검증 명령을 추가한다.
- `DEVELOPMENT.md`에 preference key, 신규 설치 판별 경계, asset hash와 회귀 명령을 기록한다. `docs/README.md`의 기존 NPC 인덱스가 새 내용을 포괄하는지 확인하고 필요한 경우만 갱신한다.
- 구현과 문서가 다른 수치·문구·경로를 갖지 않게 한다.

최종 검증을 수행하고 phase index step 8을 완료 처리한다. 모든 step이 completed이면 phase 완료 summary를 한국어로 남긴다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-56-fairy-final
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat test lint assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. 문서를 먼저 구현된 경로·문구·상태 계약과 대조한다.
2. AC 전체를 실행한다.
3. AGENTS.md의 UI/Repository 경계, 한국어 strings, 테스트 우선, 멱등성·권한 독립성 규칙을 확인한다.
4. Android 도구나 연결 기기가 없으면 임의 설치하지 말고 구체적인 `blocked_reason`을 기록한다.
5. phase index step 8과 phase 완료 상태를 갱신하고 최종 경로·가이드 정책·검증 결과를 한국어로 요약한다.

## 금지사항

- 구현되지 않은 동작을 완료 상태로 문서화하지 마라. 이유: 문서가 제품 계약의 원천이다.
- 기존 사용자를 자동 안내 대상으로 문서화하지 마라. 이유: 승인된 rollout과 다르다.
- Room v14 또는 능력치 공식을 변경했다고 쓰지 마라. 이유: 이번 기능의 불변 범위다.
- connected test 도구 부재를 숨기지 마라. 이유: 검증 불가 상태를 명확히 남겨야 한다.
- 기존 테스트를 깨뜨리지 마라.
