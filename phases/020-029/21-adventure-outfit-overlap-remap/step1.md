# Step 1: replace-layer-overlap-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_execute.py`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`

## 작업

`/scripts/test_validate_character_sheet.py`에 실패 테스트를 먼저 작성한 뒤 `/scripts/validate_character_sheet.py`의 schema v3 검증을 새 계약으로 교체한다. 다음 공개 인터페이스는 변경하지 않는다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

테스트 fixture의 모험가 레이어를 새 정상 상태로 바꾼다. 정상 fixture는 top/bottom이 `x=24..40, y=41..43`에서 공유되고, bottom/shoes가 두 다리의 y=53..54에서 공유되어야 한다. bottom은 y=49..54의 x=32가 투명하고 shoes는 base-body 발 alpha를 y=53..58에서 모두 덮어야 한다.

최소한 다음 조건을 각각 독립 테스트한다.

- 정상 overlap fixture가 통과한다.
- 이전 phase의 y=42/43 무중첩 fixture는 waist overlap 누락으로 실패한다.
- top/bottom 필수 공유 픽셀 누락 또는 공유 영역 밖 중첩은 계약명·레이어명·첫 로컬 좌표와 함께 실패한다.
- bottom/shoes y=53..54 필수 공유 픽셀 누락 또는 범위 밖 중첩은 실패한다.
- bottom의 y=41..48 중앙 픽셀이 빠지거나 y=49..54의 x=32가 불투명하거나 좌우 다리 mask가 베이스와 다르면 실패한다.
- shoes가 y=53..58의 base-body 발 픽셀을 하나라도 덮지 못하거나 soleY가 58이 아니면 실패한다.
- top이 y=29..43에서 base-body로부터 1픽셀을 초과해 벗어나거나 y=24..28에서 base alpha 밖을 점유하거나 손 보호 좌표를 덮으면 실패한다.
- 숨겨진 bottom waist overlap 또는 shoes ankle overlap에 `outlineDarkNavy`가 있으면 실패한다.
- 인접 두 행에 완전한 수평 외곽선이 생기면 2픽셀 외곽선 오류로 실패한다.
- 장비 합집합에서 중앙 몸통·골반·다리·발의 base neutral underwear 또는 기본 신발이 노출되면 실패한다.
- `equipped`와 `composite`가 8단계 source-over 결과와 다르거나 서로 다르면 계속 실패한다.
- 다섯 대상 외 11개 타일 해시, 얼굴·머리·모자·액세서리, 중심축, soleY, alpha 0/255와 16색 검증이 계속 동작한다.

validator는 spec을 데이터로 읽고 이미지를 자동 수정하지 않는다. 새 선택 필드가 없는 구 schema v3 fixture와 schema v1/v2 의미를 유지한다. 오류에는 계약명, 타일 또는 레이어명과 첫 실패 로컬 좌표를 포함한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
git diff --check
```

## 검증 절차

1. 새 테스트가 validator 구현 전에 실패하는 것을 확인한다.
2. 구현 후 AC를 실행한다. 실제 모듈 시트는 아직 이전 디자인이므로 이 step에서 새 spec으로 검증하지 않는다.
3. 공개 함수와 schema v1/v2 회귀가 없는지 확인한다.
4. PNG와 Android 파일이 변경되지 않았는지 확인한다.
5. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
6. task index의 step 1을 `completed`로 바꾸고 검증 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: 테스트 우선 규칙을 지켜야 한다.
- 실제 모듈 PNG를 fixture 보정에 사용하지 마라. 이유: 아트 변경은 뒤 step의 책임이다.
- old no-overlap 검증을 유지하지 마라. 이유: 이번 요청이 해당 조건을 명시적으로 폐기했다.
- validator에서 이미지를 자동 보정하지 마라. 이유: 계약 위반을 숨기면 안 된다.
- 기존 테스트를 깨뜨리지 마라.
