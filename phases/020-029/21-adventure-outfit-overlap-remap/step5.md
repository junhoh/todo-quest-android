# Step 5: assemble-overlapped-outfit

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
- `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-top-layer.png`
- `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-bottom-layer.png`
- `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-shoes-layer.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_execute.py`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`

## 작업

승인된 세 임시 레이어를 기존 모듈 시트에 결정적으로 조립한다. imagegen을 추가 호출하거나 디자인 픽셀을 새로 그리지 않는다.

작업 시작 시 현재 시트를 메모리 기준 원본으로 보존한다. Pillow의 타일 복사와 `Image.alpha_composite`만 사용해 `equipped`, `adventure-shoes-layer`, `adventure-bottom-layer`, `adventure-top-layer`, `composite` 다섯 타일을 갱신한다.

완성 캐릭터는 아래 source-over 순서로 한 번 생성해 `equipped`와 `composite`에 같은 픽셀을 복사한다.

1. `default-hair-back-layer`
2. 외부 `base-body`
3. 새 `adventure-shoes-layer`
4. 새 `adventure-bottom-layer`
5. 새 `adventure-top-layer`
6. `default-hair-front-layer`
7. `head-gear-layer`
8. `accessory-layer`

나머지 11개 타일은 원본 픽셀을 그대로 유지한다. 얼굴, 머리카락, 모자, 액세서리, 기본 복장, 안내선, 팔레트와 외부 base-body를 재생성하거나 복원하지 않는다.

최종 저장 후 다음을 계산 검증한다.

- 512×128 RGBA, 8×2의 64×64 tile map, alpha 0/255, 기존 16색
- 변경이 다섯 대상 타일 안에만 있고 비대상 11개 타일 해시가 spec baseline과 동일
- 세 레이어 타일이 승인된 임시 파일과 픽셀 단위로 동일
- `equipped`와 `composite`가 서로 동일하고 8단계 합성 결과와 동일
- top/bottom 허리 공유 영역과 bottom/shoes 발목 공유 영역이 정확히 존재
- 손, 얼굴, 중심축 x=32, 신체 크기, 다리 간격과 soleY=58 보존
- base-body 파일의 RGBA SHA-256과 alpha mask 불변

최종 시트와 완성 캐릭터를 1배율 및 nearest-neighbor 8배율로 확인한다. 허리·발목에 투명 틈이나 base 내의 노출이 없어야 하며 경계가 2픽셀 이상 진하게 보이면 실패다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 원본과 최종 시트의 타일별 해시를 비교해 다섯 대상 밖의 변경이 없는지 확인한다.
3. 최종 시트를 1배율과 8배율로 직접 검사한다.
4. base body, base sheet/spec과 Android 파일이 변경되지 않았는지 확인한다.
5. 최종 PNG 경로, 세 built-in imagegen 프롬프트, 합성 순서, 타일 보존과 테스트 결과를 작업 기록에 남긴다.
6. task index의 step 5를 `completed`로 바꾸고 최종 PNG와 검증 결과를 한국어 `summary` 한 줄로 기록한다.
7. 모든 step이 완료되면 `/phases/index.json`의 phase 21을 `completed`로 변경한다.

## 금지사항

- 다섯 대상 타일 밖 픽셀을 변경하지 마라. 이유: 사용자 지정 최소 수정 범위를 위반한다.
- 임시 레이어 디자인 픽셀을 이 step에서 수정하지 마라. 이유: 이 step은 합성만 담당한다.
- 합성 순서를 바꾸거나 레이어를 하나로 합치지 마라. 이유: 모듈형 장착 계약을 위반한다.
- 얼굴, 머리카락, 신체, 모자, 액세서리, 기본 복장과 팔레트를 재생성하지 마라. 이유: 사용자 변경 범위 밖이다.
- Android 소스나 리소스를 수정하지 마라. 이유: 이번 phase는 문서 아트와 검증 계약만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
