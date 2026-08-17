# Step 0: define-layer-overlap-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`

## 작업

`/docs/art/character/character-modular-sheet-spec.json`의 schema v3와 v1/v2 호환 의미를 유지하면서 모험가 상의·하의·신발의 잘못된 무중첩 계약을 의도적 source-over 중첩 계약으로 교체한다. 이 step에서는 PNG, validator, 테스트와 Android 파일을 수정하지 않는다.

기존 `layerBoundaryContracts.adventureOutfitSeam`과 다음 의미를 완전히 제거한다: 상의 y=42 종료, 하의 y=43 시작, 상·하의 공유 좌표 금지, 하의 y<=42/y>=53 금지, 하의·신발 공유 좌표 금지, 모험가 신발 schema v2 픽셀 해시 보존.

다음 계약을 정확한 데이터로 정의한다.

- `layerBounds.adventure-top-layer`: `minY: 24`, `maxY: 43`, inclusive. y=24..28은 기존 목깃·어깨 보존 구간이고 base-body 불투명 픽셀과 직접 겹치는 픽셀만 허용한다. 주요 매핑은 y=29..43이다.
- `layerBounds.adventure-bottom-layer`: `minY: 41`, `maxY: 54`, inclusive.
- `layerBounds.adventure-shoes-layer`: `minY: 53`, `maxY: 58`, inclusive. soleY는 58이다.
- `layerBoundaryContracts.adventureWaistOverlap`: bottom 뒤 top 순서, 필수 공유 불투명 영역 `x=24..40, y=41..43`, top이 나중에 합성, 공유 영역 밖 공유 좌표 금지, 숨겨지는 bottom overlap에는 `outlineDarkNavy` 금지, 최종 top 밑단은 y=43 한 행.
- `layerBoundaryContracts.adventureAnkleOverlap`: shoes 뒤 bottom 순서, 필수 공유 불투명 영역은 y=53..54의 `x=24..31`과 `x=33..40`, bottom이 나중에 합성, 공유 영역 밖 공유 좌표 금지, 숨겨지는 shoes overlap에는 `outlineDarkNavy` 금지, 최종 bottom 밑단은 y=54 한 행.
- `equipmentMappingContracts.adventure-top-layer`: reference `base-body`, y=29..43에서 Chebyshev 최대 1픽셀 확장, y=24..28은 직접 alpha 중첩만 허용, neutral underwear를 y=29..43에서 덮음, 손 보호 좌표는 투명, 중심축 x=32와 좌우 외곽 차이 허용치 1픽셀.
- `equipmentMappingContracts.adventure-bottom-layer`: y=41..48은 `x=24..40` 전부 불투명, y=49..54는 `x=24..31`과 `x=33..40`만 불투명하여 x=32가 투명, base-body에서 최대 1픽셀 확장.
- `equipmentMappingContracts.adventure-shoes-layer`: y=53..58의 base-body 발 alpha를 모두 덮고 최대 1픽셀 확장, soleY 58.

`designContracts`의 상·하의 설명과 hem/waist 값을 새 범위로 갱신한다. 기존 상의 6색과 하의 3색 허용 팔레트는 유지한다. 신발에는 현재 레이어가 실제 사용하는 기존 팔레트 이름만 허용하는 `designContracts.adventure-shoes`를 추가하고 새 색을 추가하지 않는다.

`compositionContracts.equipped`와 `composite`는 `default-hair-back-layer`, `base-body`, `adventure-shoes-layer`, `adventure-bottom-layer`, `adventure-top-layer`, `default-hair-front-layer`, `head-gear-layer`, `accessory-layer` 순서를 유지한다.

`targetedEditContract` baseline을 step 시작 시 현재 모듈 시트에서 다시 캡처한다. 변경 허용 타일은 `equipped`, `adventure-shoes-layer`, `adventure-bottom-layer`, `adventure-top-layer`, `composite` 다섯 개다. 나머지 11개 타일의 현재 RGBA SHA-256을 보존 계약으로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); b=s['layerBounds']; c=s['layerBoundaryContracts']; assert s['schemaVersion']==3; assert 'adventureOutfitSeam' not in c; assert b['adventure-top-layer']=={'minY':24,'maxY':43,'inclusive':True}; assert b['adventure-bottom-layer']=={'minY':41,'maxY':54,'inclusive':True}; assert b['adventure-shoes-layer']=={'minY':53,'maxY':58,'inclusive':True}; assert c['adventureWaistOverlap']['yRange']==[41,43]; assert c['adventureAnkleOverlap']['yRange']==[53,54]; assert s['targetedEditContract']['tiles']==['equipped','adventure-shoes-layer','adventure-bottom-layer','adventure-top-layer','composite']; assert len(s['targetedEditContract']['preservedTiles'])==11"
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. old no-overlap 필드가 남아 있지 않고 새 중첩·매핑 계약과 기존 8단계 합성 순서가 일치하는지 확인한다.
3. PNG, validator, 테스트, base spec과 Android 파일이 변경되지 않았는지 확인한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 0을 `completed`로 바꾸고 새 계약과 보존 타일 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- schemaVersion을 올리거나 v1/v2 의미를 바꾸지 마라. 이유: 이번 수정은 schema v3 모험가 장비 계약 교정이다.
- `anchors.waistY`를 바꾸지 마라. 이유: 기본 복장과 공통 신체 anchor까지 변한다.
- PNG, validator 또는 테스트를 수정하지 마라. 이유: 이 step은 계약 데이터만 다룬다.
- 기존 팔레트에 색을 추가하지 마라. 이유: 기존 16색을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
