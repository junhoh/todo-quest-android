# Step 2: generate-empty-and-adventure-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/README.md`
- `/app/src/main/assets/character/layers/`
- `/docs/art/character/layers/`
- `/docs/art/character/previews/`
- `/scripts/build_character_assets.py`
- `/scripts/validate_character_sheet.py`
- `/scripts/validate_character_equipment_layers.py`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step1.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

step 1의 spec을 source of truth로 사용해 중립 훈련복 fallback과 모험가 장갑 bitmap을 생성하고 canonical 산출물을 재생성한다.

`top_default`, `bottom_default`, `shoes_default`는 기존 모험가 파란 복장과 명확히 구분되는 회색·갈색의 단순 훈련복으로 다시 그린다. 실루엣, 64×64 원점, 발 ground anchor, 머리카락·손·인접 layer와의 seam은 기존 renderer와 맞춘다. `gloves_adventure`는 기존 `top_adventure`의 파란색/청록색 cuff와 연결되고 `hands_front`를 필요한 부분만 덮도록 만든다. 기존 adventure helmet/top/bottom/shoes/accessory/default sword의 픽셀은 상품 layer로 그대로 보존한다.

bitmap 생성/편집에는 `imagegen` 스킬을 사용하되 결과를 그대로 채택하지 않고 기존 pixel-art palette, 투명 배경, nearest-neighbor 64×64 규칙으로 정규화한다. `scripts/build_character_assets.py`를 통해 docs mirror, modular sheet, empty/adventure equipped preview와 확대 preview를 결정론적으로 다시 만든다. 생성된 모든 PNG를 validator로 검사하고 시각적으로 빈 상태와 7부위 모험가 세트를 각각 확인한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts/build_character_assets.py
.\.venv\Scripts\python.exe scripts/validate_character_sheet.py
.\.venv\Scripts\python.exe scripts/validate_character_equipment_layers.py
.\.venv\Scripts\python.exe -m pytest scripts/test_validate_character_sheet.py scripts/test_validate_character_equipment_layers.py scripts/test_build_character_assets.py --basetemp build/pytest-phase62-art-assets
git diff --check
```

## 검증 절차

1. runtime layer, docs mirror, generated sheet/preview를 build script로 재생성한다.
2. `top_default`·`bottom_default`·`shoes_default`만 장착한 모습이 중립 훈련복이고 나머지 slot이 비어 보이는지 확인한다.
3. 7부위 adventure 조합에서 장갑 seam, sword back/held/front 순서, accessory 전면 겹침이 올바른지 확인한다.
4. 확대 preview가 nearest-neighbor이며 원본 64×64 파일에 반투명 배경이나 잘린 픽셀이 없는지 확인한다.
5. 성공 시 task index의 step 2를 `completed`로 바꾸고 생성·보존한 자산을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 기존 adventure 자산을 중립 훈련복으로 덮어쓰지 마라. 이유: 기존 기본 복장은 신규 상점 상품으로 재사용한다.
- runtime에서 generated sheet를 잘라 사용하도록 바꾸지 마라. 이유: canonical source는 개별 layer PNG다.
- bilinear scaling, anti-aliasing 또는 불투명 배경을 추가하지 마라. 이유: 현재 pixel-art 합성 계약과 충돌한다.
- 검증을 통과하지 못한 imagegen 결과를 커밋 대상으로 남기지 마라. 이유: 생성 결과는 spec과 validator를 모두 만족해야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
