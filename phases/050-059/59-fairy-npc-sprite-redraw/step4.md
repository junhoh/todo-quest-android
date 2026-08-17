# Step 4: document-fairy-regeneration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`
- `/phases/050-059/59-fairy-npc-sprite-redraw/index.json`

## 작업

사용자의 최종 시각 비교 결과에 따라 새 regenerated 후보를 채택하지 않고 phase 59 시작 전 요정 자산을 계속 사용한다. phase 59의 중간 생성·canonical 게시 이력은 step 0~3 summary에 보존하되, 최종 저장소 상태는 phase 58 완료 커밋 `4d41ed8`의 요정 canonical·runtime·spec 및 validator/test 상태와 같아야 한다.

- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png`와 `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated-8x.png`를 제거한다.
- canonical PNG, spec JSON과 Android runtime PNG를 phase 59 전 상태로 복원한다.
- phase 59에서 추가한 미사용 `palettePixelCounts` validator와 regenerated 전용 테스트를 phase 59 전 상태로 복원한다.
- canonical과 runtime의 SHA-256은 `8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0`이고 byte-identical해야 한다.
- spec의 실제 inclusive bounds `[6,22,58,52]`, opaque height `31`, centerX `32`, soleY `52` 계약이 복원되어야 한다.
- 기존 README와 DEVELOPMENT는 이 기존 자산을 이미 설명하므로 변경하지 않는다.
- phase summary에는 새 후보를 비교했으나 사용자 선택으로 제거하고 기존 자산을 유지했다는 최종 결정을 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-59-retain-previous
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne '8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0') { throw 'Previous canonical fairy was not restored' }
if ($runtime -ne $canonical) { throw 'Runtime fairy sprite differs from canonical art' }
if (Test-Path -LiteralPath 'docs\art\npc\todo-quest-fairy-guide-front-idle-regenerated.png') { throw 'Regenerated fairy still exists' }
if (Test-Path -LiteralPath 'docs\art\npc\todo-quest-fairy-guide-front-idle-regenerated-8x.png') { throw 'Regenerated preview still exists' }
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. phase 59 runner가 더 이상 실행 중이 아닌지 확인한다.
2. 새 비교 PNG가 제거되고 canonical·runtime·spec·validator/test가 phase 59 전 상태인지 확인한다.
3. AC를 실행해 기존 요정 validator, 회귀 테스트와 byte identity를 검증한다.
4. phase index의 step 4와 phase 전체를 `completed`로 바꾸고 최종 사용자 선택을 한국어 summary로 기록한다.
5. manager `Sync` 후 `Sync -Check`로 registry와 README 동기화를 확인한다.

## 금지사항

- 새 regenerated PNG를 canonical이나 runtime에 다시 게시하지 마라. 이유: 사용자가 기존 이미지 유지를 최종 선택했다.
- Phase 56·57의 과거 기록을 수정하지 마라. 이유: 당시 산출물과 되돌림 이력을 보존해야 한다.
- 기존 요정 PNG를 재인코딩하지 마라. 이유: 검증된 기존 SHA-256과 runtime byte identity를 유지해야 한다.
- 다른 캐릭터, Kotlin, Room 또는 UI 코드를 변경하지 마라. 이유: 최종 범위는 phase 59 후보 철회뿐이다.
- 기존 테스트를 깨뜨리지 마라.
