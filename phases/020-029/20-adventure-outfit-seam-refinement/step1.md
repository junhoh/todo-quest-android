# Step 1: extend-adventure-outfit-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_execute.py`
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`

## 작업

`/scripts/test_validate_character_sheet.py`에 실패 테스트를 먼저 작성한 뒤 `/scripts/validate_character_sheet.py`를 확장한다. 다음 공개 인터페이스와 schema v1/v2 지원은 변경하지 않는다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

schema v3 테스트 fixture의 모험가 상·하의를 새 계약에 맞는 합성 가능한 임시 픽셀로 갱신한다. fixture는 테스트 전용이며 실제 프로젝트 PNG를 수정하지 않는다. 상의 fixture는 `y=42`에 `x=24..40` 연속 밑단을 갖고 `y>=43`에는 픽셀이 없어야 한다. 하의 fixture는 `y=43`의 `x=24..40` 허리선, `y=52` 밑단, `x=32`의 1px 중앙 분리선을 갖고 `y>=53`에는 픽셀이 없어야 한다.

최소한 다음 조건을 각각 독립 테스트한다.

- 정상 새 모험가 seam fixture는 통과한다.
- 상의에 `y>=43` 픽셀이 있거나 하의에 `y<=42` 또는 `y>=53` 픽셀이 있으면 타일명과 첫 로컬 좌표를 포함해 실패한다.
- 상의 `y=42` 또는 하의 `y=43`의 `x=24..40` 중 하나라도 투명하면 각각 hem 또는 waist seam 오류로 실패한다.
- 상의와 하의가 같은 로컬 좌표를 점유하거나 두 행 사이에 몸통 노출이 생기면 실패한다.
- 상의에 하의 전용 의미의 픽셀 배치가 있거나, 하의에 `bluePrimary`, `blueShadow`, `blueHighlight`, `tealAccent`, `lightCream`, 피부색 또는 허용 목록 밖 팔레트가 있으면 실패한다.
- 하의가 세 허용색을 넘거나 좌우 다리 bounding box·끝 y가 다르거나 `x=32` 중앙선이 1px가 아니면 실패한다.
- 상의가 `handProtectionContract.pixelCoordinates` 중 하나를 덮거나 완성 합성에서 손 RGBA가 base body와 다르면 실패한다.
- `equipped`와 `composite`가 합성 계약과 다르거나 서로 한 픽셀이라도 다르면 기존 검증이 계속 실패한다.
- targeted edit 외 12개 타일, 모험가 신발, 모자, 액세서리, 팔레트, 얼굴, 신체 좌표, 알파, 16색과 외곽선 검증은 계속 동작한다.

validator는 spec 데이터를 일반적으로 읽어 검증하고 이미지나 spec을 수정하거나 자동 보정하지 않는다. 오류 메시지에는 계약명, 레이어 또는 타일명과 첫 실패 로컬 좌표를 포함한다. 기존 v3 spec에 새 선택 필드가 없을 때의 의미를 깨뜨리지 않으며, 이번 프로젝트 spec에 필드가 있으면 엄격하게 적용한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
git diff --check
```

## 검증 절차

1. 새 테스트가 구현 전에 실패하는 것을 확인한다.
2. validator를 구현한 뒤 AC 명령을 실행한다. 실제 모듈 시트는 아직 구 디자인이므로 이 step의 AC에서 새 spec으로 검증하지 않는다.
3. schema v1/v2와 기존 v3 비대상 계약 회귀를 확인한다.
4. PNG와 Android 파일이 변경되지 않았는지 확인한다.
5. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
6. task index의 step 1을 `completed`로 바꾸고 추가한 seam·팔레트·손·대칭 검증을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 실제 모듈 PNG를 테스트 fixture로 보정하거나 수정하지 마라. 이유: 아트 변경은 이후 레이어 step의 책임이다.
- validator에서 이미지를 자동 수정하지 마라. 이유: 계약 위반을 보정으로 숨기면 안 된다.
- schema v1/v2 또는 기존 v3 비대상 검증을 약화하지 마라. 이유: 회귀 호환성을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
