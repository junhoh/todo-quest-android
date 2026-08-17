# Step 2: synchronize-fairy-canonical-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated-8x.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/59-fairy-npc-sprite-redraw/step0.md`
- `/phases/050-059/59-fairy-npc-sprite-redraw/step1.md`
- `/phases/050-059/59-fairy-npc-sprite-redraw/index.json`

## 작업

step 1의 regenerated PNG가 기계 검증과 시각 검사를 통과한 경우에만 canonical PNG와 spec JSON에 반영한다. 이 step은 `/docs/art/npc/`의 canonical 아트 계약만 수정하며 Android resource와 일반 문서는 수정하지 않는다.

먼저 regenerated 테스트와 1배율·8배율 검사를 다시 실행한다. 통과하면 `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png`의 bytes를 재인코딩 없이 `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`에 복사한다.

완성된 canonical의 alpha 255 pixel을 직접 측정해 `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`을 실제 상태와 동기화한다.

- `asset`은 canonical filename, `64×64`, `RGBA`를 유지한다.
- `expectedOpaqueBoundingBox`, `opaqueHeight`, `centerX`, `soleY`를 실측값으로 기록한다.
- `anchors.headCenter`, `anchors.shoulderY`, `anchors.waistY`, `anchors.soleY`를 실제 silhouette 기준으로 갱신한다.
- `pose.stance`는 정면 직립 안내 대기 자세를 표현하고 `bothToeTipsReachY`를 실제 공통 발끝 y로 갱신한다.
- `groundContacts.leftFoot/rightFoot`는 서로 분리된 실제 x range와 공통 y, 실제 최소 불투명 수를 기록한다.
- `semanticRegions.starAccent`, `eyesAndSmile`, `wingColors`는 실제 inclusive region으로 갱신하고 해당 색상 count를 사후 실측한 `min == max`로 기록한다.
- `transparentRegions.legGap`, `leftWingBodyGap`, `rightWingBodyGap`은 실제 투명 간격을 포함하는 region과 사후 실측한 `min == max` count로 기록한다.
- `palettePixelCounts`를 추가해 16개 palette name 각각의 alpha 255 pixel 수를 정확히 기록한다.
- palette와 `requiredPaletteNames`는 기존 16색을 유지하되, 완성본에서 쓰지 않은 색이 있다면 외곽 pixel을 억지로 추가하지 말고 실제 사용 여부에 맞춰 required/optional 목록을 조정한다.
- 목표 bounds와 실제 값이 1~2px 다르면 이미지를 늘리거나 외곽 pixel을 추가하지 않고 실제 좌표를 기록한다.

canonical과 regenerated가 byte-identical한지 검사한다. validator와 test가 PNG의 실제 좌표·palette count·spec metadata 불일치를 모두 검출해야 한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py -k "not runtime_sprite" --basetemp build\pytest-59-canonical
$regenerated=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle-regenerated.png).Hash
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
if ($regenerated -ne $canonical) { throw 'Canonical fairy differs from selected regenerated art' }
git diff --check
```

## 검증 절차

1. regenerated 파일을 다시 검증한 뒤에만 canonical을 교체한다.
2. PNG를 독립적으로 재측정해 spec의 모든 좌표와 count가 일치하는지 확인한다.
3. validator, NPC와 관련 아트 회귀 테스트를 실행한다.
4. AGENTS.md의 범위와 기존 캐릭터/몬스터 자산 불변을 확인한다.
5. phase index의 step 2를 `completed`로 바꾸고 최종 bounds·높이·sole y·SHA-256을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- regenerated 검증 전에 canonical을 덮어쓰지 마라. 이유: 원본과 새 후보 비교 가능성을 보존해야 한다.
- 목표 수치를 맞추려고 외곽 pixel을 추가하거나 이미지를 늘리지 마라. 이유: spec은 완성본의 실제 상태를 기록하는 metadata다.
- Android runtime resource를 수정하지 마라. 이유: runtime 게시를 다음 step으로 격리한다.
- README와 DEVELOPMENT 문서를 수정하지 마라. 이유: 최종 문서 동기화는 별도 step이다.
- 기존 테스트를 깨뜨리지 마라.
