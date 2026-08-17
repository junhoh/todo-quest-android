# Step 0: define-refined-character-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/phases/010-019/17-modular-character-design-sheet/index.json`

## 작업

`/docs/art/character/character-modular-sheet-spec.json`을 `schemaVersion: 3` 계약으로 갱신한다. 기존 기준 시트 `/docs/art/character/todo-quest-character-base-sheet.png`와 `/docs/art/character/character-base-spec.json`은 수정하지 않는다. 이 step에서는 PNG, validator, 테스트와 Android 소스를 수정하지 않는다.

시트의 크기와 논리 좌표는 기존 계약을 그대로 유지한다.

- 최종 시트: `512x128px` RGBA, `64x64px` 타일 8열 2행
- 캐릭터 중심축 `x=32`, 머리 위 기준 `y=7`, 어깨 `y=25`, 허리 `y=39`, 발목 `y=53`, 발바닥 `y=58`
- 공통 허용 영역 `[20,4,44,58]`, 최소 바깥 여백 3px
- 알파값은 `0` 또는 `255`만 허용하고, 기존 16색만 사용한다.
- 외부 실루엣은 `outlineDarkNavy: #263B5A`의 연속된 1px 외곽선이다.

`tileMap`은 16개 모든 칸을 다음 이름과 위치로 고정하고 기존 `reservedTiles` 계약은 제거한다.

- 첫째 행: column 0 `equipped`, 1 `default-outfit`, 2 `default-hair-preview`, 3 `anchors`, 4 `palette`, 5 `default-top-layer`, 6 `default-bottom-layer`, 7 `default-shoes-layer`
- 둘째 행: column 0 `default-hair-back-layer`, 1 `adventure-shoes-layer`, 2 `adventure-bottom-layer`, 3 `adventure-top-layer`, 4 `default-hair-front-layer`, 5 `head-gear-layer`, 6 `accessory-layer`, 7 `composite`

시트 밖의 독립 레이어를 `externalLayers.base-body`로 정의한다.

- 경로는 spec 파일 기준 `todo-quest-character-base-body.png`다.
- 크기는 `64x64px`, 모드는 RGBA, 공통 중심축과 발바닥 좌표를 사용한다.
- 변경 전 모듈 시트의 `body-base` 타일과 동일한 알파 실루엣, 얼굴, 신체 비율과 정면 포즈를 사용한다.
- 머리카락과 실제 기본/모험가 장비는 포함하지 않고 `underDark`, `underMid`, `underLight`의 중립 회색 내의만 허용한다.
- 변경 전 `body-base`의 SHA-256, 얼굴 보호 영역과 얼굴 특징의 정확한 로컬 픽셀 좌표·RGBA 값을 계약에 기록해 최종 시트가 바뀐 뒤에도 validator가 독립적으로 비교할 수 있게 한다.

합성 계약을 다음 source-over 순서로 정의한다. 외부 `base-body`도 다른 타일과 동일한 64x64 로컬 좌표에서 합성한다.

- `default-outfit`: `default-hair-back-layer`, `base-body`, `default-shoes-layer`, `default-bottom-layer`, `default-top-layer`, `default-hair-front-layer`
- `default-hair-preview`: `default-hair-back-layer`, `base-body`, `default-hair-front-layer`
- `anchors`: `default-hair-preview` 위에 기존 `tealAccent` 점선 좌표만 오버레이한다.
- `equipped`와 `composite`: `default-hair-back-layer`, `base-body`, `adventure-shoes-layer`, `adventure-bottom-layer`, `adventure-top-layer`, `default-hair-front-layer`, `head-gear-layer`, `accessory-layer`
- `equipped`와 `composite`는 서로 픽셀 단위로 동일해야 한다.

`layerBounds`와 경계 계약을 다음처럼 기록한다. 경계선의 공통 seam 픽셀만 양쪽 범위에 포함할 수 있다.

- `default-hair-back-layer`, `default-hair-front-layer`: `y=4..30`; 뒷머리는 목선에서 끝난다.
- `default-top-layer`: `y=24..39`; 밑단은 `y=39`에 존재하고 `y>=40`에는 픽셀이 없다.
- `default-bottom-layer`: `y=39..46`; 허리는 `y=39`, 좌우 바짓단은 같은 y에서 끝난다.
- `default-shoes-layer`: `y=53..58`; 좌우 신발은 같은 크기와 바닥선을 사용한다.
- `adventure-shoes-layer`: `y=53..58`; 기존 픽셀을 그대로 유지한다.
- `adventure-bottom-layer`: `y=39..53`; 양쪽 다리 사이가 분리되고 `y=53` 외에는 신발 영역을 침범하지 않는다.
- `adventure-top-layer`: `y=24..39`; 재킷 밑단은 `y=39`에서 끝나며 긴 앞자락이 없다.
- `head-gear-layer`: `y=4..25`, `accessory-layer`: `y=4..58`; 기존 픽셀을 유지한다.

디자인 및 얼굴 보호 계약을 데이터로 기록한다.

- 머리 기본색은 `hairBlack`, 하이라이트는 `hairHighlight`, 외곽선은 `outlineDarkNavy`다.
- 기본 머리의 중앙 앞머리는 눈썹 위에서 끝나며 중앙 얼굴 영역에는 눈 사이로 내려오는 픽셀이 없다. 앞머리 아래의 좌우 눈, 코, 입과 얼굴 윤곽은 변경 전 `body-base`의 보호 픽셀과 정확히 같아야 한다.
- 각 눈 보호 픽셀의 8방향 1px 이웃에는 `default-hair-front-layer` 픽셀이 없어야 한다. 코·입 보호 픽셀에는 머리카락이 겹치면 안 된다.
- 머리 알파 실루엣은 `x=32`를 기준으로 좌우 균형을 이루고, 옆머리만 얼굴 바깥을 따라 목선까지 내려올 수 있다.
- 기본 상의는 `lightCream` 중심, `underLight` 음영, `outlineDarkNavy` 외곽선의 무장식 반팔 티셔츠다.
- 기본 하의는 `underDark` 중심, `underMid` 음영의 무장식 반바지다.
- 기본 신발은 `lightCream`과 `underLight` 중심의 동일 크기 운동화다.
- 모험가 상의는 `bluePrimary`, `blueShadow`, `blueHighlight`, 안쪽 `lightCream`을 사용한다.
- 모험가 하의는 `eyeDarkNavy` 중심에 `underDark` 음영을 사용해 상의보다 어둡게 한다.
- 좌우 소매, 반바지 밑단, 신발의 bounding box와 끝 y를 비교하는 대칭 계약을 둔다. 색 음영 자체의 완전 좌우 대칭은 강제하지 않는다.
- 고립된 불투명 픽셀, 허용되지 않은 작은 연결 요소, `#FF00FF`, 반투명 픽셀과 팔레트 밖 색은 허용하지 않는다.

기존 `paletteGrid`, 안내선의 정확한 점선 좌표, 크로마키 금지와 외곽선 계약은 값 변경 없이 유지한다. schema v1과 v2의 의미를 바꾸지 않고 v3 필드를 추가·교체한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/character/character-modular-sheet-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==3; assert s['sheet']=={'width':512,'height':128,'mode':'RGBA'}; assert len(s['tileMap'])==16; assert not s.get('reservedTiles'); assert s['externalLayers']['base-body']['path']=='todo-quest-character-base-body.png'; assert [t['name'] for t in s['tileMap'][:8]]==['equipped','default-outfit','default-hair-preview','anchors','palette','default-top-layer','default-bottom-layer','default-shoes-layer']; assert len(s['palette'])==16 and len(set(s['palette'].values()))==16; assert s['anchors']['waistY']==39 and s['anchors']['ankleY']==53 and s['soleY']==58"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 기준 PNG와 모듈 PNG, validator, 테스트 및 Android 소스가 변경되지 않았는지 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙과 사용자 고정 좌표를 확인한다.
4. `/phases/010-019/19-character-hair-outfit-refinement/index.json`의 step 0을 `completed`로 변경하고 새 tile map, 외부 base body와 합성 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 기준 시트와 기준 spec을 수정하지 마라. 이유: 얼굴·체형·포즈의 불변 기준이다.
- PNG, validator 또는 테스트를 수정하지 마라. 이유: 이 step은 schema v3 데이터 계약만 정의한다.
- 팔레트 색을 추가하거나 기존 값을 바꾸지 마라. 이유: 기존 16색 제한을 유지해야 한다.
- Android 앱 코드나 resource를 수정하지 마라. 이유: 이번 phase는 디자인 시트와 검증 도구만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
