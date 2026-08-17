# Step 2: synchronize-fairy-guide-art-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`
- `/scripts/test_validate_npc_sprite.py`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/step1.md`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`

## 작업

현재 canonical/runtime 자산과 문서를 동기화하고 전체 회귀를 검증한다.

- `/docs/art/npc/README.md`의 능력치 안내 요정 항목을 최종 실측 inclusive bounds, `opaqueHeight`, 플레이어 52px 대비 비율, 중심축, `soleY`, 최종 SHA-256과 phase 57 검증 명령으로 갱신한다.
- `/docs/DEVELOPMENT.md`의 Phase 56 기록은 당시 결과를 나타내는 역사적 검증 기록이므로 기존 hash와 수치를 지우지 않는다. Phase 56 다음에 `Phase 57 Fairy Guide Humanoid Proportion Fix` 섹션을 추가해 새 64px 직접 작화 방식, 최종 bounds·높이·비율·SHA-256, 정사각형 Fit 렌더링 불변과 실제 최종 검증 결과를 기록한다.
- PRD, ARCHITECTURE, ADR과 UI_GUIDE는 기능·아키텍처·표시 계약이 바뀌지 않았으므로 새로운 수치가 필요한 참조가 없는지 검색만 하고 불필요하게 수정하지 않는다.
- 비교 후보와 8배율 검토본은 `build/fairy-guide-humanoid-proportion-fix/`에 유지하고 tracked 아트나 문서 링크로 승격하지 않는다.

최종 시각 검사는 다음을 모두 확인한다.

1. PNG가 `64×64 RGBA`, alpha `0/255`, 정확히 기존 16색이다.
2. 2.5~3등신 정면 직립 실루엣으로 머리·목·몸통·허리·양다리·양부츠가 구분된다.
3. 날개와 팔이 하나의 수평선으로 연결되지 않고 안내 팔만 자연스럽게 들려 있다.
4. 몸통 중심축은 `x=32`이고 양발 끝 높이가 같다.
5. 1배율과 8배 nearest-neighbor에서 외곽선, 투명 간격, 고립 픽셀과 불규칙 노이즈가 없다.
6. Character Dialog의 정사각형 frame·Fit·무보간 테스트가 통과하고 다른 캐릭터/NPC 표시 회귀가 없다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-57-fairy-final
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat test lint assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. 문서를 실측 수치와 최종 hash로 갱신한다.
2. AC를 실행한다. 연결 기기가 없거나 Android 도구가 없으면 설치를 시도하지 않고 `blocked`로 기록한다.
3. 1배율·8배율 비교본과 Character Dialog 렌더링 테스트를 확인한다.
4. AGENTS.md의 UI/data 경계, 보상 멱등성, 반복 일정 분리, 외부 Calendar 제외와 권한 거부 독립성이 변경되지 않았는지 확인한다.
5. phase index step 2를 `completed`로 바꾸고 최종 수치·hash·Python/Gradle/connected 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Phase 56의 과거 검증 기록을 현재 수치로 소급 변경하지 마라. 이유: 당시 산출물과 검증 이력을 보존해야 한다.
- UI·도메인·데이터 계층을 수정하지 마라. 이유: 현재 정사각형 Fit 렌더링이 이미 요구사항을 충족한다.
- 비교용 build 산출물을 tracked 결과물로 추가하지 마라. 이유: 최종 디자인은 canonical 한 장과 byte-identical runtime 사본만 유지한다.
- Android 도구나 emulator를 임의 설치하지 마라. 이유: AGENTS.md의 구현 phase 도구 정책을 준수해야 한다.
- 기존 테스트를 깨뜨리지 마라.

