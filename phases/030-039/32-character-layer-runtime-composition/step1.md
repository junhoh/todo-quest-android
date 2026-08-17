# Step 1: define-layer-schema-v4

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/art/character/character-layer-migration-report.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

`character-modular-sheet-spec.json`을 schemaVersion 4의 독립 layer source 계약으로 개편한다. 다음 필드와 의미를 고정한다.

- `canvasBounds=[0,0,63,63]`, `bodyOpaqueBounds=[20,7,44,58]`, `appearanceAllowedEnvelope=[20,4,44,58]`, `centerX=32`, `soleY=58`.
- `geometryCanonicalReference`는 `todo-quest-character-base-body.png` 외부 파일만 가리키고 raw RGBA/alpha/file hash 자리와 실제 bounds를 가진다.
- `legacyArtDirectionReference`는 legacy sheet/spec, 16색, outline/shading/design 역할과 historical anchors만 가진다. legacy `base`/`equipped` tile을 body geometry로 등록하지 않는다.
- `semanticAnchors`: headTopY 7, headBounds `[20,7,44,28]`, faceProtectedRegion `[20,7,44,28]`, neckTopY 29, neckBaseY 30, shoulderBandY `[30,35]`, shoulder anchors `[22,35]`/`[42,35]`, torso `[24,30,40,43]`, waist `[24,41,40,43]`, ankle bands와 hand regions, primaryGripAnchor `[42,42]`.
- production palette는 legacy 16색을 그대로 유지하되 `guideRed` 역할명을 `redAccent`로 바꾼다. 별도 `debugGuideColor`는 preview overlay에만 허용하고 runtime layer palette에서 제외한다.
- canonical layer 목록은 body_base, hair_back_default, hair_front_default, hands_front, default/adventure top-bottom-shoes, headgear_adventure, accessory_adventure, default sword back/held/front다. face/headgear-back/accessory-back은 현재 미사용으로 명시한다.
- z-order는 accessory_back, hair_back, headgear_back, weapon_back, body_base, shoes, bottom, top, weapon_held, hands_front, weapon_front, face_overlay, hair_front, headgear_front, accessory_front 순서다.
- 모든 runtime path는 `character/layers/*.png`, source paths는 spec 상대 경로로 기록하며 offset/crop/per-item scale 필드를 금지한다.

`exclusiveCanonicalReference`, `schemaV1SemanticsUnchanged`, `schemaV2SemanticsUnchanged`, stale `targetedEditContract`, preserved anchors hash, 존재하지 않는 `originalBodyBaseReference.tile`을 제거한다. sheet hash 직렬화는 65,536 RGBA pixels/262,144 bytes로 정의한다. 아직 생성되지 않은 layer hash와 bounds는 명시적 `pendingGeneration` 상태로 두고 임의 값을 만들지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); assert s['schemaVersion']==4; assert s['canvasBounds']==[0,0,63,63]; assert s['bodyOpaqueBounds']==[20,7,44,58]; assert s['semanticAnchors']['shoulderBandY']==[30,35]; assert s['semanticAnchors']['waistOverlapBand']==[24,41,40,43]; assert s['geometryCanonicalReference']['path']=='todo-quest-character-base-body.png'; assert 'targetedEditContract' not in s; assert 'exclusiveCanonicalReference' not in str(s); assert '65536' in s['hashSerialization']['sheetRgba']"
git diff --check
```

## 검증 절차

1. JSON을 parse하고 legacy historical anchors와 runtime semantic anchors가 별도 객체인지 확인한다.
2. redAccent와 debugGuideColor가 역할 및 검사 범위에서 분리됐는지 확인한다.
3. task index의 step 1을 `completed`로 바꾸고 schema v4 기준과 source 목록을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- legacy anchors를 semantic anchors에 복사하지 마라. 이유: current base body가 유일한 geometry 기준이다.
- 생성 전 layer hash를 추정하지 마라. 이유: 저장된 실제 픽셀에서만 계산해야 한다.
- anchors를 preserved tile로 남기지 마라. 이유: 새 의미 기반 overlay로 재생성해야 한다.
- 기존 테스트를 깨뜨리지 마라.
