# Step 1: add-monster-sprite-validator

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/requirements-dev.txt`
- `/phases/020-029/22-goblin-scout-sprite/index.json`

## 작업

테스트를 먼저 작성해 실패를 확인한 뒤 단일 몬스터 PNG 전용 검증기를 구현한다. 기존 캐릭터 시트 validator는 수정하지 않는다.

먼저 `/scripts/test_validate_monster_sprite.py`를 작성한다. Pillow로 임시 64×64 fixture와 최소 유효 고블린 형태를 구성해 다음 동작을 각각 검증한다.

- 정상 fixture는 통과한다.
- 이미지 크기 또는 RGBA mode가 다르면 실패한다.
- 부분 알파, 계약 밖 색, 남은 `#FF00FF` 크로마키가 있으면 실패한다.
- 불투명 bounding box, 중심축, 최소 여백 또는 y=58 발바닥이 다르면 실패한다.
- 필수 팔레트 색이 빠지거나 `dangerRed`/`poisonAccent` 픽셀 수와 영역을 위반하면 실패한다.
- 금속 색이 단검 영역 밖이나 신체 중앙에 나타나거나 단검 높이가 8~12px가 아니면 실패한다.
- 4-neighbor 외부 경계가 `outlineDarkNavy`가 아니거나 외부 외곽선에 2×2 블록이 생기면 실패한다.
- 불투명 픽셀이 둘 이상의 8-connected 구성요소로 분리되거나 고립 픽셀이 있으면 실패한다.

테스트가 구현 전 실패하는 것을 확인한 뒤 `/scripts/validate_monster_sprite.py`를 구현한다. 공개 인터페이스는 다음으로 고정한다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sprite(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

CLI는 다음 형식을 사용한다. 검증 성공 시 stdout과 exit code `0`, 실패 시 모든 위반을 stderr와 exit code `1`로 반환한다. PNG를 수정하거나 자동 보정하지 않는다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image <png> --spec <json>
```

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-22-goblin-scout
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. 테스트 파일을 먼저 작성하고 검증기 구현 전 실패를 확인한다.
2. 검증기를 구현하고 AC 명령을 실행한다.
3. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. `/phases/020-029/22-goblin-scout-sprite/index.json`의 step 1을 `completed`로 변경하고 생성 파일과 검증 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 검증기 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 캐릭터 시트 validator에 몬스터 전용 분기를 추가하지 마라. 이유: 220개 기존 회귀와 단일 스프라이트 계약을 분리한다.
- 검증기에서 이미지를 수정하지 마라. 이유: 생성 실패를 자동 보정으로 숨기지 않는다.
- 기존 테스트를 깨뜨리지 마라.
