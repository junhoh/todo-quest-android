# Step 1: extend-refinement-validator

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
- `/phases/010-019/19-character-hair-outfit-refinement/index.json`

## 작업

테스트를 먼저 작성해 schema v3 fixture가 기존 validator에서 실패하는 것을 확인한 뒤 `/scripts/validate_character_sheet.py`를 확장한다. schema v1과 v2의 기존 동작과 다음 공개 인터페이스는 변경하지 않는다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

`/scripts/test_validate_character_sheet.py`에 schema v3용 임시 외부 `base-body` PNG와 512x128 시트 fixture를 추가한다. fixture는 spec 파일과 같은 임시 디렉터리에서 외부 레이어의 상대 경로를 해석해야 한다. 기존 schema v1/v2 fixture와 모든 테스트는 그대로 통과해야 한다.

최소한 다음 조건을 각각 독립 테스트한다.

- 정상 schema v3 외부 base body와 모듈 시트는 통과한다.
- base body의 파일 부재, 64x64/RGBA 불일치, 팔레트 밖 색, 반투명 알파, 공통 영역·중심축·발바닥·알파 실루엣 위반은 실패한다.
- base body의 얼굴 보호 픽셀이나 중립 내의 팔레트가 계약과 다르면 실패한다.
- 16개 tile map 위치 중 하나가 비어 있거나 다른 위치의 픽셀을 사용하면 실패한다.
- `default-outfit`, `default-hair-preview`, `anchors`, `equipped`, `composite` 합성이 계약과 한 픽셀이라도 다르면 실패한다.
- `equipped`와 `composite`가 서로 다르면 실패한다.
- 기본/모험가 각 레이어가 `layerBounds`와 허리·발목 seam을 벗어나면 실패한다.
- 기본 앞머리가 얼굴 중앙 개방 영역, 눈 보호 픽셀의 1px 이웃, 코 또는 입 픽셀을 침범하면 실패한다.
- 기본 머리 알파 실루엣의 좌우 균형, 양쪽 소매 길이, 반바지 밑단 y, 좌우 신발 bounding box 또는 발바닥이 다르면 실패한다.
- 모험가 상의에 `y>=40` 픽셀이 있거나 모험가 하의가 `y<39` 또는 허용된 `y=53` seam 밖 신발 영역을 침범하면 실패한다.
- 계약에 지정된 필수 주색이 레이어에 없거나 모험가 상·하의 명도 구분 규칙을 위반하면 실패한다.
- 외부 실루엣에 `outlineDarkNavy`가 아닌 경계 픽셀이 있거나, 불투명 연결 요소 계약에서 고립 픽셀이 발견되면 실패한다.
- 팔레트 그리드, 안내선, 전체 크기, 알파, 16색, 크로마키와 기존 geometry 검증은 계속 동작한다.

validator는 v3에서 `externalLayers` 경로를 spec 파일의 디렉터리를 기준으로 안전하게 해석한다. 이미지나 spec을 수정하거나 자동 보정하지 않고, 오류에는 타일/외부 레이어 이름과 첫 실패 로컬 좌표를 포함한다. 좌우 균형은 계약에 지정된 알파 실루엣 또는 좌우 bounding box만 비교하고 비대칭 색 음영을 오탐하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
git diff --check
```

## 검증 절차

1. schema v3 테스트를 먼저 작성하고 구현 전 실패를 확인한다.
2. validator를 확장한 뒤 AC 명령을 실행한다.
3. schema v1 기준 시트와 schema v2 회귀 fixture가 계속 통과하는지 확인한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. `/phases/010-019/19-character-hair-outfit-refinement/index.json`의 step 1을 `completed`로 변경하고 테스트 수와 검증 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- schema v1 또는 v2 지원을 제거하거나 기존 fixture를 새 계약에 맞춰 약화하지 마라. 이유: 기존 기준 시트 회귀를 유지해야 한다.
- validator에서 이미지나 spec을 자동 수정하지 마라. 이유: 아트 실패를 보정으로 숨기면 안 된다.
- 실제 캐릭터 PNG를 생성하거나 수정하지 마라. 이유: 이 step은 검증 모듈만 다룬다.
- Android 앱 코드나 resource를 수정하지 마라. 이유: 이번 phase 범위 밖이다.
- 기존 테스트를 깨뜨리지 마라.
