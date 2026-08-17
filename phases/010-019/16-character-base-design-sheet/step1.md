# Step 1: add-pixel-sheet-validator

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/requirements-dev.txt`
- `/scripts/test_execute.py`

## 작업

테스트를 먼저 작성한 뒤 Pillow 기반 캐릭터 시트 검증기를 구현한다.

1. `/requirements-dev.txt`에 `Pillow==12.3.0`을 추가하고 `.venv`에 requirements를 설치한다.
2. 먼저 `/scripts/test_validate_character_sheet.py`를 작성한다. 테스트는 임시 PNG fixture를 생성하여 다음 조건을 각각 검증해야 한다.
   - 정상 시트는 통과한다.
   - 크기 또는 RGBA 모드가 틀리면 실패한다.
   - 알파값이 `0`이나 `255`가 아닌 픽셀이 있으면 실패한다.
   - 계약 팔레트 밖의 색 또는 `#FF00FF` 크로마키가 남으면 실패한다.
   - `equipped`와 `base` 타일의 bounding box, 중심축, 최소 여백 또는 `y=58` 발바닥이 다르면 실패한다.
   - `anchors` 타일의 `anchorGuidePixels` 가운데 하나라도 `guideRed`가 아니면 실패한다.
   - `palette` 타일에 계약된 16색 가운데 하나라도 없으면 실패한다.
3. 테스트가 실패하는 것을 확인한 다음 `/scripts/validate_character_sheet.py`를 구현한다.

검증기 공개 인터페이스는 다음으로 고정한다.

```python
def load_spec(path: pathlib.Path) -> dict: ...
def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]: ...
def main(argv: Sequence[str] | None = None) -> int: ...
```

CLI는 아래 형식을 사용한다. 검증 성공 시 요약을 stdout에 출력하고 exit code `0`, 실패 시 각 오류를 stderr에 출력하고 exit code `1`을 반환한다.

```powershell
.\\.venv\\Scripts\\python.exe scripts\\validate_character_sheet.py --image <png> --spec <json>
```

검증기는 픽셀을 수정하거나 자동 보정하지 않고 읽기 전용으로 검사한다.

## Acceptance Criteria

```powershell
.\\.venv\\Scripts\\python.exe -m pip install -r requirements-dev.txt
$env:PYTHONUTF8='1'
.\\.venv\\Scripts\\python.exe -m pytest scripts/test_validate_character_sheet.py scripts/test_execute.py --basetemp .\\.venv\\pytest-tmp
git diff --check
```

## 검증 절차

1. 테스트 파일을 먼저 작성하고 구현 전 실패를 확인한다.
2. 검증기를 구현하고 AC 명령을 실행한다.
3. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. `/phases/010-019/16-character-base-design-sheet/index.json`의 step 1을 `completed`로 변경하고 생성 파일과 검증 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 테스트보다 검증기 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 검증기에서 이미지를 자동 수정하지 마라. 이유: 생성 실패를 숨기지 말고 명확한 오류로 드러내야 한다.
- Android 도구를 설치하거나 앱 코드를 수정하지 마라. 이유: 이 step은 개발용 아트 검증 도구만 다룬다.
- 기존 테스트를 깨뜨리지 마라.