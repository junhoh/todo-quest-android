# Step 4: assemble-adventure-outfit-composites

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-top-layer.png`
- `/.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-bottom-layer.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_execute.py`
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`

## 작업

이 step은 승인된 상·하의 임시 레이어를 기존 시트에 조립하고 완성 합성 두 타일을 동기화한다. imagegen을 추가 호출하거나 디자인 픽셀을 새로 그리지 않는다.

작업 시작 시 현재 `/docs/art/character/todo-quest-character-modular-sheet.png`를 메모리와 `.tmp/imagegen/20-adventure-outfit-seam-refinement/original-sheet.png`에 보존한다. Pillow의 결정적 타일 복사와 `Image.alpha_composite`만 사용해 다음 네 타일을 갱신한다.

- 둘째 행 column 3 `adventure-top-layer`: step 2의 64x64 임시 상의
- 둘째 행 column 2 `adventure-bottom-layer`: step 3의 64x64 임시 하의
- 첫째 행 column 0 `equipped`
- 둘째 행 column 7 `composite`

`equipped`와 `composite`는 spec의 source-over 순서로 동일한 64x64 결과를 한 번 생성해 두 위치에 복사한다.

1. `default-hair-back-layer`
2. 외부 `base-body`
3. 기존 `adventure-shoes-layer`
4. 새 `adventure-bottom-layer`
5. 새 `adventure-top-layer`
6. `default-hair-front-layer`
7. 기존 `head-gear-layer`
8. 기존 `accessory-layer`

나머지 12개 타일은 original sheet에서 바이트 단위로 그대로 유지한다. 특히 얼굴, 머리카락, 모자, 신발, 장갑, 액세서리, 기본 복장, 안내선과 팔레트를 복원하거나 재생성하지 말고 원본 픽셀을 그대로 둔다. `equipped`와 `composite`의 얼굴·신체 위치, bounding box, 중심축 `x=32`, 발바닥 `y=58`은 변경 전과 같아야 한다.

최종 시트를 저장한 뒤 다음을 계산 검증한다.

- 512x128 RGBA, 64x64 8x2 tile map, 알파 0/255, 기존 16색
- 변경 픽셀은 네 대상 타일 내부에만 존재하고 다른 12개 타일의 RGBA SHA-256은 original sheet와 동일
- `adventure-top-layer`와 `adventure-bottom-layer`가 임시 파일과 픽셀 단위로 동일
- `equipped`와 `composite`가 픽셀 단위로 동일하고 spec의 합성 결과와 동일
- 상의 `y=42`, 하의 `y=43`, 하의 밑단 `y=52`, 신발 시작 `y=53` 경계
- 손 보호, 얼굴 보호, 신체 위치, 바지 다리 분리와 고립 픽셀 금지

최종 결과를 1배율과 nearest-neighbor 8배 확대 상태로 직접 확인한다. 완전 장비 캐릭터가 긴 코트나 한 벌 의상이 아니라 아이보리 셔츠가 보이는 짧은 파란 재킷과 독립된 짙은 남색 바지로 읽혀야 한다. 왼쪽 `equipped`와 오른쪽 `composite`는 시각적으로도 동일해야 한다.

모든 검증 성공 후 `.tmp/imagegen/20-adventure-outfit-seam-refinement`의 중간 파일을 정리한다. 이 디렉터리의 파일은 커밋하지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. original sheet와 최종 시트의 타일별 해시를 비교해 네 대상 타일 외의 변경이 없는지 확인한다.
3. 최종 시트를 1배율과 nearest-neighbor 확대 상태로 검사한다.
4. 기준 PNG/spec, base body와 Android 소스·리소스가 변경되지 않았는지 확인한다.
5. 최종 PNG 경로, 두 built-in imagegen 프롬프트, 합성 순서, 타일 보존과 테스트 결과를 작업 기록에 남긴다.
6. task index의 step 4를 `completed`로 바꾸고 최종 PNG와 검증 결과를 한국어 `summary` 한 줄로 기록한다.
7. 모든 step이 완료되면 `/phases/index.json`의 phase 20 상태를 `completed`로 변경한다.

## 금지사항

- 네 대상 타일 밖 픽셀을 변경하지 마라. 이유: 사용자 지정 최소 수정 범위를 위반한다.
- 임시 상·하의의 디자인 픽셀을 코드로 추가·수정하지 마라. 이유: 이 step은 합성만 담당한다.
- 합성 순서를 바꾸거나 상의와 하의를 하나의 레이어로 합치지 마라. 이유: 모듈형 장착 계약을 위반한다.
- 머리카락, 얼굴, 신체, 모자, 신발, 장갑, 액세서리, 기본 복장과 팔레트를 재생성하지 마라. 이유: 사용자 변경 범위 밖이다.
- 중간 imagegen 파일을 커밋하지 마라. 이유: 최종 프로젝트 산출물만 추적해야 한다.
- 기존 테스트를 깨뜨리지 마라.
