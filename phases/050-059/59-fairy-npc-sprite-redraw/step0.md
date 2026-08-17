# Step 0: define-fairy-sprite-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`
- `/phases/050-059/59-fairy-npc-sprite-redraw/index.json`

## 작업

새 요정 PNG를 만들기 전에 검증 계약과 테스트를 먼저 작성한다. 이 step은 `scripts/` 검증 모듈만 수정하며 PNG, spec JSON, Android resource와 문서는 수정하지 않는다.

먼저 `/scripts/test_validate_monster_sprite.py`에 선택적 `palettePixelCounts` 검증 테스트를 추가한다. 잘못된 실제 수를 넣은 fixture가 현재 validator에서 누락되어 실패하는 것을 확인한 뒤 `/scripts/validate_monster_sprite.py`를 구현한다.

- `palettePixelCounts`는 선택적 JSON object이며 key는 `palette`에 선언된 이름, value는 음수가 아닌 정수다.
- 선언된 각 이름에 대해 alpha `255`인 해당 RGB pixel의 실제 개수와 정확히 일치해야 한다.
- 알 수 없는 palette name, boolean/음수/non-integer count는 명확한 schema 오류로 거부한다.
- 필드가 없는 기존 monster/NPC spec의 동작은 바꾸지 않는다.

`/scripts/test_validate_npc_sprite.py`에는 regenerated 산출물용 테스트와 재사용 helper를 먼저 추가한다. 아직 파일이 없을 때만 `pytest.skip`하고, 파일이 생기면 자동 활성화되어 다음을 검사한다.

- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png`는 정확히 `64×64`, mode `RGBA`, alpha 값 `0/255`만 사용하고 투명 pixel RGB는 `(0,0,0)`이다.
- 불투명 RGB는 현재 spec의 16색 palette 안에만 있으며 `#FF00FF`가 없다.
- inclusive opaque bounds의 각 좌표는 목표 `[6,14,58,54]`에서 최대 2px만 벗어날 수 있고 opaque 높이는 `39..43`이다.
- 불투명 요소는 canvas 경계에 닿지 않고 bbox 중심은 `x=32`에서 최대 1px만 벗어난다.
- 양쪽 발은 서로 겹치지 않는 x range로 분리되고 같은 sole y에 최소 2px씩 닿는다.
- 불투명 pixel은 8-neighbor 기준 한 component이며 완전히 고립된 opaque pixel이 없다.
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated-8x.png`는 정확히 `512×512`이고 모든 `8×8` block이 원본 논리 pixel과 일치하는 nearest-neighbor 확대다.

테스트에는 미적 품질을 좌표 수치로 과도하게 고정하지 않는다. 머리 크기, 팔·날개 분리, 자연스러운 2.5~3등신은 step 1의 시각 검사로 판정한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_npc_sprite.py --basetemp build\pytest-59-validation
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
git diff --check
```

## 검증 절차

1. 새 validator 테스트가 구현 전 실패하는 것을 확인한 뒤 구현한다.
2. regenerated 파일 부재 시 해당 테스트만 명시적으로 skip되고 기존 검증이 모두 통과하는지 확인한다.
3. 기존 spec에 새 필드를 강제하지 않아 회귀가 없는지 확인한다.
4. AGENTS.md의 테스트 우선 규칙을 확인한다.
5. phase index의 step 0을 `completed`로 바꾸고 검증 인터페이스와 테스트 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG, spec JSON 또는 Android resource를 만들거나 수정하지 마라. 이유: 이 step은 검증 모듈만 담당한다.
- 기존 spec에 `palettePixelCounts`를 필수로 만들지 마라. 이유: 기존 모든 monster/NPC 계약과의 호환성을 유지해야 한다.
- 미적 판단을 정확한 총 pixel 수로 미리 고정하지 마라. 이유: 완성된 이미지의 실제 상태를 사후 기록해야 한다.
- 기존 테스트를 깨뜨리지 마라.
