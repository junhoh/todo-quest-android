# Step 1: generalize-monster-sprite-validator

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/scripts/test_execute.py`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

테스트를 먼저 작성해 실패를 확인한 뒤 `/scripts/validate_monster_sprite.py`의 고블린 전용 의미 영역 처리를 schema v1 호환 선언형 검증으로 일반화한다. 공개 인터페이스는 유지한다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sprite(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

먼저 `/scripts/test_validate_monster_sprite.py`에 다음 실패·성공 사례를 추가한다.

- 기존 고블린의 `redEyes`, `daggerMetal`, `poison` 계약은 변경 없이 계속 통과한다.
- `semanticRegions`의 임의 이름과 `paletteName` 또는 `paletteNames`를 순회하며 선택한 색의 픽셀 수, 허용 영역, x-range 비침범과 합친 불투명 bounding box 높이를 검증한다.
- 해골 fixture의 두 ground contact가 지정 x 범위에서 y=58에 최소 픽셀 수를 만족해야 한다.
- `transparentRegions`의 영역별 완전 투명 픽셀 수가 최소·선택적 최대 범위를 만족해야 한다.
- 잘못된 의미 영역 색, 검 높이, 중앙 침범, 한쪽 발 미접촉, 뭉개진 갈비뼈·다리·검 여백은 서로 구분되는 오류로 보고한다.
- spec에 새 선택 필드가 없을 때 기존 동작을 유지하고, 잘못된 필드 타입이나 존재하지 않는 palette name은 `invalid monster sprite specification`으로 반환한다.

구현은 다음 선언 규칙을 따른다.

- 모든 `semanticRegions` 항목을 이름에 의존하지 않고 순회한다.
- `paletteName`은 단일 색, `paletteNames`는 합친 색 집합으로 처리한다.
- `pixelCount`, `onlyWithinRegion`, `opaqueBoundingBoxHeight`, `mustNotOverlapXRange`가 존재할 때만 해당 검증을 적용한다.
- `groundContacts`는 각 항목의 `xRange`, `y`, `minimumOpaquePixels`를 검증한다.
- `transparentRegions`는 inclusive `region` 내부에서 alpha=0인 픽셀 수를 세고 `pixelCount.min`과 선택적 `max`를 적용한다.
- 기존 크기·RGBA·알파·팔레트·전체 bounding box·외곽선·8-connected 검증과 CLI 출력 계약은 변경하지 않는다.
- 검증기는 PNG를 수정하거나 오류를 자동 보정하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-40-skeleton-validator
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
git diff --check
```

## 검증 절차

1. 새 테스트를 먼저 작성하고 validator 수정 전 예상된 실패를 확인한다.
2. validator를 일반화하고 AC 명령을 실행한다.
3. 기존 고블린 실제 PNG와 synthetic 해골 fixture가 동일한 공개 CLI로 검증되는지 확인한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 1을 `completed`로 변경하고 호환성·신규 선언 검증 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `eyeGlow`나 `swordMetal` 이름을 새 하드코딩 분기로 추가하지 마라. 이유: 다음 몬스터도 같은 schema를 재사용해야 한다.
- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 고블린 JSON을 새 schema에 맞춘다는 이유로 불필요하게 수정하지 마라. 이유: schema v1 하위 호환을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
