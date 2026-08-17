# Step 0: define-adventure-outfit-seam-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/phases/010-019/19-character-hair-outfit-refinement/index.json`
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`

## 작업

`/docs/art/character/character-modular-sheet-spec.json`의 schema v3 구조를 유지하면서 모험가 상·하의에만 적용되는 새 경계 계약을 정의한다. 이 step에서는 PNG, validator, 테스트와 Android 소스를 수정하지 않는다.

기존 공통 `anchors.waistY: 39`, 기본 복장 경계, 16칸 tile map, 합성 순서, 16색 팔레트와 외부 base body 계약은 변경하지 않는다. `anchors.waistY`는 기본 복장 기준이므로 모험가 재킷 경계로 재해석하지 않는다.

모험가 레이어 계약을 다음 값으로 갱신한다.

- `layerBounds.adventure-top-layer`: `minY: 24`, `maxY: 42`. 목·어깨·소매는 기존 위치를 사용할 수 있지만 몸통 영역은 `x=24..40, y=29..42`다. 레이어 전체에서 `y>=43` 픽셀을 금지한다.
- `layerBounds.adventure-bottom-layer`: `minY: 43`, `maxY: 52`. 레이어 전체에서 `y<=42`와 `y>=53` 픽셀을 금지한다.
- `layerBounds.adventure-shoes-layer`: 기존 `y=53..58`과 보존 해시를 그대로 유지한다.
- 상의 마지막 행은 `y=42`, 하의 첫 행은 `y=43`이다. 두 행 모두 몸통 폭 `x=24..40`에서 연속 불투명해야 하며, 두 레이어가 같은 로컬 좌표를 점유하지 않는다.
- 상의의 `y=42` 밑단은 수평에 가까운 단일 행으로 끝나고 중앙 앞자락과 좌우 코트 자락이 없다. `lightCream` 셔츠, 단추·지퍼·세로 장식도 `y=42`에서 끝난다.
- 하의의 `y=43` 허리선은 `x=24..40`에서 연속되고 상의 밑단 바로 아래를 덮어 피부나 중립 내의가 노출되지 않는다.
- 하의는 `eyeDarkNavy`, `underDark`, `outlineDarkNavy` 세 색만 허용한다. 파란 상의 계열, `lightCream`, 피부색, 신발 전용 장식색은 금지한다.
- 하의는 `x=32`를 기준으로 좌우 다리 bounding box와 끝 y가 대칭에 가깝고, `x=32`의 `outlineDarkNavy` 1px 중앙 가랑이 분리선을 사용한다. 바짓단은 `y=52`, 신발 시작은 `y=53`이다.

`layerBoundaryContracts`와 `designContracts`에 위 값을 데이터로 기록한다. 모험가 상의와 하의는 기본 복장의 공통 `y=39` seam 규칙을 공유하지 않으며, 모험가 전용 no-overlap seam을 사용한다고 명시한다. `designContracts.adventure-top`에는 허용 팔레트 `bluePrimary`, `blueShadow`, `blueHighlight`, `tealAccent`, `lightCream`, `outlineDarkNavy`를 기록하고, `designContracts.adventure-bottom`에는 위 세 색만 기록한다.

`handProtectionContract`를 추가한다. 외부 `base-body`의 `y=39..45`에서 왼쪽 `x=20..24`, 오른쪽 `x=40..44` 안에 있는 `skinLight`와 `skinShadow` 픽셀의 정확한 로컬 좌표와 RGBA를 데이터로 기록한다. `adventure-top-layer`는 이 좌표에서 투명해야 하며 `equipped`와 `composite`에서는 base body 손 픽셀이 그대로 보여야 한다. 손 보호 목록은 spec 작성 시 base body에서 결정적으로 추출하고 임의 좌표를 만들지 않는다.

`compositionContracts.equipped`와 `compositionContracts.composite`의 source-over 순서 및 픽셀 동일성 계약은 그대로 유지한다. 변경 허용 타일은 `equipped`, `adventure-bottom-layer`, `adventure-top-layer`, `composite` 네 개뿐이라는 `targetedEditContract`를 추가하고 나머지 12개 타일은 픽셀 보존 대상으로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); assert s['schemaVersion']==3; assert s['anchors']['waistY']==39; assert s['layerBounds']['adventure-top-layer']=={'minY':24,'maxY':42,'inclusive':True}; assert s['layerBounds']['adventure-bottom-layer']=={'minY':43,'maxY':52,'inclusive':True}; assert s['layerBoundaryContracts']['adventure-top-layer']['hemY']==42; assert s['layerBoundaryContracts']['adventure-bottom-layer']['waistSeamY']==43; assert s['layerBoundaryContracts']['adventure-bottom-layer']['hemY']==52; assert s['designContracts']['adventure-bottom']['allowedPaletteNames']==['eyeDarkNavy','underDark','outlineDarkNavy']; assert s['targetedEditContract']['tiles']==['equipped','adventure-bottom-layer','adventure-top-layer','composite']; assert s['handProtectionContract']['pixelCoordinates']"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. spec의 모험가 전용 seam과 기존 기본 복장 seam이 독립적으로 유지되는지 확인한다.
3. PNG, validator, 테스트, 기준 spec과 Android 파일이 변경되지 않았는지 확인한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 0을 `completed`로 바꾸고 새 경계·손 보호·대상 타일 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 공통 `anchors.waistY`를 42 또는 43으로 바꾸지 마라. 이유: 기본 복장과 기존 신체 anchor까지 변경된다.
- schemaVersion을 올리거나 v1/v2 의미를 바꾸지 마라. 이유: 이번 변경은 v3 데이터 계약의 모험가 복장 범위만 정교화한다.
- PNG, validator 또는 테스트를 수정하지 마라. 이유: 이 step은 계약 데이터만 다룬다.
- 모험가 신발, 머리카락, 모자, 액세서리와 팔레트 보존 해시를 바꾸지 마라. 이유: 사용자 변경 범위 밖이다.
- 기존 테스트를 깨뜨리지 마라.
