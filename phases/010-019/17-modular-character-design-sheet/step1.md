# Step 1: extend-modular-sheet-validator

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_execute.py`
- `/requirements-dev.txt`

## 작업

테스트를 먼저 작성해 실패를 확인한 뒤 `/scripts/validate_character_sheet.py`를 schema version 1과 2 모두 검증하도록 확장한다. 공개 인터페이스는 변경하지 않는다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

`/scripts/test_validate_character_sheet.py`에 schema version 2용 임시 PNG fixture와 테스트를 먼저 추가한다. 기존 schema version 1 테스트는 그대로 통과해야 한다. 최소한 다음 실패 조건을 각각 검증한다.

- 정상 512x128 RGBA modular fixture는 통과한다.
- 크기, 모드, 팔레트 밖 색, 반투명 알파, 남은 `#FF00FF`는 실패한다.
- 첫째 행의 예약 타일 세 개 중 하나라도 불투명 픽셀이 있으면 실패한다.
- 네 개의 위치 기준 타일이 `commonAllowedBox`, `x=32`, `y=58`, 최소 여백 계약을 벗어나면 실패한다.
- `rear-hair + body-base + front-hair` 합성이 `default-hair-underwear`와 다르면 실패한다.
- 전체 `layerOrder` 합성이 `equipped` 또는 `composite`와 한 픽셀이라도 다르면 실패한다.
- isolated layer가 계약의 `layerBounds` y 범위를 벗어나면 실패한다.
- 안내선의 필수 점선 픽셀이 `#5CC8A7`이 아니거나 안내선 픽셀이 `anchors` 이외 타일에 복제되면 실패한다. 캐릭터 자체에 원래 존재하는 teal 장비 픽셀은 오탐하지 않도록 안내선의 계약 좌표만 비교한다.
- 팔레트 16개 칸의 위치, `10x10px` 크기, `2px` 간격, 색 순서가 계약과 다르면 실패한다.

validator 구현 규칙:

- `schemaVersion == 1`이면 현재 검증 흐름을 유지한다.
- `schemaVersion == 2`이면 새 계약의 `tileMap`, `reservedTiles`, `guide`, `layerBounds`, `compositionContracts`, `paletteGrid`를 데이터 기반으로 읽는다.
- 알파 합성은 Pillow의 표준 source-over 규칙으로 처리하며 타일의 64x64 로컬 좌표를 유지한다.
- 오류 메시지는 실패한 타일과 첫 좌표를 포함한다.
- validator는 이미지를 수정하거나 자동 보정하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
git diff --check
```

## 검증 절차

1. schema version 2 테스트를 먼저 작성하고 구현 전 실패를 확인한다.
2. validator를 확장하고 AC 명령을 실행한다.
3. 기존 schema version 1 시트가 계속 통과하는지 확인한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. `/phases/010-019/17-modular-character-design-sheet/index.json`의 step 1을 `completed`로 변경하고 테스트와 검증 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- schema version 1 지원을 제거하거나 기존 spec을 바꾸지 마라. 이유: 기존 기준 시트 회귀 검증을 유지해야 한다.
- validator에서 PNG를 수정하지 마라. 이유: 생성 실패를 자동 보정으로 숨기면 안 된다.
- 앱 코드나 Android resource를 수정하지 마라. 이유: 이번 phase는 디자인 시트와 검증 도구만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
